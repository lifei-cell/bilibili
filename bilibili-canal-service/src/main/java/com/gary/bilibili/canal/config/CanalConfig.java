package com.gary.bilibili.canal.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
@EnableConfigurationProperties(CanalProperties.class)
public class CanalConfig {

    @Bean
    public CanalConnector canalConnector(CanalProperties properties) {
        return CanalConnectors.newSingleConnector(
                new InetSocketAddress(properties.getHost(), properties.getPort()),
                properties.getDestination(),
                properties.getUsername(),
                properties.getPassword());
    }
}
