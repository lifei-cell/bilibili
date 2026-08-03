package com.gary.bilibili.canal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.gary.bilibili")
@EnableDiscoveryClient
@EnableScheduling
public class BilibiliCanalApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliCanalApplication.class, args);
    }
}
