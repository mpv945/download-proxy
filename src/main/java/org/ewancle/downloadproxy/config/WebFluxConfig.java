package org.ewancle.downloadproxy.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 1. 配置类 - WebFluxConfig.java
//@Configuration
@Component // 用 @Component 替代 @Configuration（最简洁）@Component 不会被 CGLIB 增强，是 Spring AOT 友好方案，100% 支持 Native Image 构建。
public class WebFluxConfig {

    @Bean
    public WebClient webClientNoRedirect() {
        ConnectionProvider provider = ConnectionProvider.builder("custom-pool-no-redirect")
                .maxConnections(1000)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)))
                .compress(true)
                .followRedirect(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024); // 50MB
                })
                .build();
    }

    @Bean
    public ScheduledExecutorService virtualThreadScheduler() {
        return Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofVirtual().name("image-processor-", 0).factory()
        );
    }
}
