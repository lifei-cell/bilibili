package com.gary.bilibili.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.gary.bilibili")
@EnableDiscoveryClient
public class BilibiliSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliSearchApplication.class, args);
    }
}
