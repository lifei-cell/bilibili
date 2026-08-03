package com.gary.bilibili.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.gary.bilibili.social.mapper")
@SpringBootApplication(scanBasePackages = "com.gary.bilibili")
@EnableDiscoveryClient
@EnableScheduling
public class BilibiliSocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliSocialApplication.class, args);
    }
}
