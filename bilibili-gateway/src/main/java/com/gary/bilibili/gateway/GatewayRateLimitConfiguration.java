package com.gary.bilibili.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration(proxyBeanMethods = false)
public class GatewayRateLimitConfiguration {

    @Bean
    public KeyResolver clientIpKeyResolver(
            @Value("${gateway.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        return exchange -> {
            if (trustForwardedFor) {
                String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
                if (StringUtils.hasText(forwarded)) {
                    String client = forwarded.split(",", 2)[0].trim();
                    if (StringUtils.hasText(client) && client.length() <= 64) {
                        return Mono.just(client);
                    }
                }
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null || remoteAddress.getAddress() == null) {
                return Mono.just("unknown");
            }
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}
