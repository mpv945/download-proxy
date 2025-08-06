package org.ewancle.downloadproxy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;

@SpringBootApplication
public class DownloadProxyApplication {

    public static void main(String[] args) {

        SpringApplication app = new SpringApplication(DownloadProxyApplication.class);
        // 从环境变量或命令行参数获取端口
        // windows:PowerShell  $env:SERVER_PORT="8084"
        // Linux: export SERVER_PORT=8084
        String port = System.getenv("SERVER_PORT");
        if (port != null && !port.isEmpty()) {
            app.setDefaultProperties(Collections.singletonMap("server.port", port));
        }
        app.run(args);
    }

    @Component
    class InitBean {
        @PostConstruct
        public void init() {
            System.out.println("初始化处理开始");
        }
    }

    @Component
    class StartupListener{
        @EventListener(ApplicationReadyEvent.class)
        public void onApplicationReady(ApplicationReadyEvent event) {
            System.out.println("应用启动完成");
        }
    }

}
