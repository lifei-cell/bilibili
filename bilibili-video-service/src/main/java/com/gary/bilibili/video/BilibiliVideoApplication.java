package com.gary.bilibili.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.gary.bilibili.video.mapper")
@SpringBootApplication(scanBasePackages = "com.gary.bilibili")
@EnableDiscoveryClient
@EnableScheduling
public class BilibiliVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliVideoApplication.class, args);
    }
}
