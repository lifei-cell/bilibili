package com.gary.bilibili.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@MapperScan("com.gary.bilibili.user.mapper")
@SpringBootApplication(scanBasePackages = "com.gary.bilibili")
@EnableDiscoveryClient
public class BilibiliUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(BilibiliUserApplication.class, args);
    }
}
