package org.ewancle.downloadproxy.filter;

import jakarta.annotation.PostConstruct;
import org.ewancle.downloadproxy.service.ImageDownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Component
@DependsOn("webClientNoRedirect")
public class GlobalWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(GlobalWebFilter.class);

    private final Environment env;

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    private final WebClient webClientNoRedirect;

    private final ImageDownloadService imageDownloadService;

    private static List<String> proxyHostList = List.of(
            "http://baidu.com:11250",
            "http://google.com:11250",
            "http://aliyun.com:11250"
    );

    private static boolean streamFlag = false;

    public GlobalWebFilter(Environment env, WebClient webClientNoRedirect, ImageDownloadService imageDownloadService) {
        this.env = env;
        this.webClientNoRedirect = webClientNoRedirect;
        this.imageDownloadService = imageDownloadService;
    }

    @PostConstruct
    public void init(){
        //String proxyHosts = env.getProperty("PROXY_HOSTS"); // JVM 启动参数或系统属性
        String proxyHosts = System.getenv("PROXY_HOSTS");
        System.out.println("proxyHosts = " +proxyHosts);
        if(proxyHosts != null && !proxyHosts.isEmpty()){
            proxyHostList = Arrays.asList(proxyHosts.split(","));
        }
        String streamFlagStr = System.getenv("STREAM_FLAG");
        if(streamFlagStr != null && !streamFlagStr.isEmpty()
                && (streamFlagStr.equals("false") || streamFlagStr.equals("true"))){
            streamFlag = Boolean.parseBoolean(streamFlagStr);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // 获取请求
        ServerHttpRequest request = exchange.getRequest();

        // 网站log
        if ("/favicon.ico".equals(exchange.getRequest().getPath().value())) {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT); // 204
            return exchange.getResponse().setComplete();
        }

        // 请求路径
        final String path = request.getURI().getPath();

        // 重试代理，转发请求
        return Flux.fromIterable(proxyHostList)
                //.flatMap(this::tryDirectFetchHead, 3)
                .flatMap(host -> tryDirectFetchHead(host+path).onErrorResume(e -> Mono.empty()), proxyHostList.size())
                .next() // 只取第一个成功的
                .flatMap(directUrl -> {
                    System.out.println("direct url = " + directUrl);

                    // 设置响应头
                    exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE);
                    exchange.getResponse().getHeaders().add(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
                    //exchange.getResponse().getHeaders().add("X-Content-Source", "google-images");
                    exchange.getResponse().getHeaders().add("Transfer-Encoding", "chunked");

                    if(!streamFlag){
                        Mono<DefaultDataBuffer> dataBufferMono = imageDownloadService.downloadImage(directUrl, true)
                                .map(bytes -> {
                                    DefaultDataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
                                    buffer.write(bytes);
                                    return buffer;
                                })
                                .onErrorResume(error -> {
                                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                                    return Mono.empty();
                                });
                        exchange.getResponse().setStatusCode(HttpStatus.OK);
                        return exchange.getResponse().writeWith(dataBufferMono);
                    } else {
                        Flux<DefaultDataBuffer> dataBufferFlux = imageDownloadService.downloadImageStream(directUrl, true)
                                .map(bytes -> {
                                    DefaultDataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
                                    buffer.write(bytes);
                                    return buffer;
                                })
                                .onErrorResume(error -> {
                                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                                    return Flux.empty();
                                });
                        exchange.getResponse().setStatusCode(HttpStatus.OK);
                        return exchange.getResponse().writeWith(dataBufferFlux);
                    }
                })
                /*.switchIfEmpty(
                        Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE))
                                .then(exchange.getResponse().setComplete())
                );*/
                /*.switchIfEmpty(
                        Mono.defer(() -> {
                            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                            return exchange.getResponse().setComplete();
                        })
                )*/
                ;
    }

    /**
     * 验证并标准化URL
     */
    private Mono<String> validateAndNormalizeUrl(String imageUrl) {
        return Mono.fromCallable(() -> {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("图片URL不能为空");
            }

            try {
                URI uri = URI.create(imageUrl.trim());
                if (!uri.isAbsolute()) {
                    throw new IllegalArgumentException("图片URL必须是绝对路径");
                }

                /*if (!GOOGLE_IMAGES_PATTERN.matcher(imageUrl).matches()) {
                    logger.warn("URL可能不是Google图片链接: {}", imageUrl);
                }*/

                return uri.toString();
            } catch (Exception e) {
                throw new IllegalArgumentException("无效的图片URL: " + imageUrl, e);
            }
        });
    }


    private Mono<byte[]> downloadImageInternal(String imageUrl) {
        return validateAndNormalizeUrl(imageUrl)
                .flatMap(url -> {
                    return webClientNoRedirect.get()
                            .uri(url)
                            .headers(this::setHeaders)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> {
                                return Mono.error(new RuntimeException(
                                        "下载失败，HTTP状态码: " + response.statusCode()));
                            })
                            .bodyToMono(byte[].class)
                            .doOnError(error -> {
                                logger.error("下载图片失败: {}", url, error);
                            })
                            .doOnSuccess(data -> logger.info("图片下载完成，大小: {} bytes", data.length));
                });
    }


    private Mono<String> tryDirectFetchHead(String url) {
        return webClientNoRedirect
                .head()
                .uri(url)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is2xxSuccessful()) {
                        return Mono.just(url);
                    } else if (status.is3xxRedirection()) {
                        // 明确排除重定向响应
                        System.out.println("🔁 跳过重定向 URL: " + url + " → " + status);
                        return Mono.empty();
                    } else {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    System.out.println("❌ 请求失败: " + url + " - " + e.getMessage());
                    return Mono.empty();
                });
    }


    /**
     * 设置HTTP请求头
     */
    private void setHeaders(HttpHeaders headers) {
        headers.set("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");
    }
}
