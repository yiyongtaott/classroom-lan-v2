package org.lanclassroom.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 启动入口。
 * 扫描 org.lanclassroom 下所有 net / app / core 组件。
 * 启用 @Scheduled，供历史服务定时刷盘。
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "org.lanclassroom")
public class ClassroomLanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassroomLanApplication.class, args);
    }
}
