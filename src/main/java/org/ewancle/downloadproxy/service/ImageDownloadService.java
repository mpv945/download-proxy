package org.ewancle.downloadproxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

@Service
public class ImageDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ImageDownloadService.class);

    private final WebClient webClientNoRedirect;

    public ImageDownloadService(WebClient webClientNoRedirect) {
        this.webClientNoRedirect = webClientNoRedirect;
    }

    /**
     * 从Google图库下载图片
     */
    public Mono<byte[]> downloadImage(String imageUrl, boolean enableCompression) {
        long startTime = System.nanoTime();
        return downloadImageInternal(imageUrl, enableCompression)
                .doFinally(signalType -> {
                    long duration = System.nanoTime() - startTime;
                });
    }

    /**
     * 流式下载图片
     */
    public Flux<byte[]> downloadImageStream(String imageUrl, boolean enableCompression) {
        return validateAndNormalizeUrl(imageUrl)
                .flatMapMany(url -> {
                    logger.info("开始下载图片: {}", url);

                    return webClientNoRedirect.get()
                            .uri(url)
                            .headers(this::setHeaders)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, response -> {
                                return Mono.error(new RuntimeException(
                                        "下载失败，HTTP状态码: " + response.statusCode()));
                            })
                            .bodyToFlux(byte[].class)
                            .doOnError(error -> {
                                logger.error("下载图片失败: {}", url, error);
                            })
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                    .maxBackoff(Duration.ofSeconds(10))
                                    .filter(this::isRetryableError))
                            //.transform(flux -> imageProcessingService.processImageStream(flux, enableCompression))
                            .doOnComplete(() -> logger.info("图片下载完成: {}", url));
                });
    }

    private Mono<byte[]> downloadImageInternal(String imageUrl, boolean enableCompression) {
        return validateAndNormalizeUrl(imageUrl)
                .flatMap(url -> {
                    logger.info("开始下载图片: {}", url);
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
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                    .maxBackoff(Duration.ofSeconds(10))
                                    .filter(this::isRetryableError))
                            //.flatMap(imageData -> imageProcessingService.compressImage(imageData, enableCompression))
                            .doOnSuccess(data -> logger.info("图片下载完成，大小: {} bytes", data.length));
                });
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

    /**
     * 判断是否为可重试的错误
     */
    private boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException) {
            HttpStatusCode status = ((WebClientResponseException) error).getStatusCode();
            return status.value() == 429 || // TOO_MANY_REQUESTS
                    status.value() == 503 || // SERVICE_UNAVAILABLE
                    status.value() == 504 || // GATEWAY_TIMEOUT
                    status.is5xxServerError();
        }
        return error instanceof java.io.IOException;
    }
}
