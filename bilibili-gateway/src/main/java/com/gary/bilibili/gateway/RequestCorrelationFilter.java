package com.gary.bilibili.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestCorrelationFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = normalize(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
        long started = System.nanoTime();
        ServerWebExchange correlated = exchange.mutate()
                .request(builder -> builder.header(REQUEST_ID_HEADER, requestId))
                .build();
        correlated.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        return chain.filter(correlated).doFinally(signal -> log.info(
                "Gateway request completed requestId={} method={} path={} status={} durationMs={}",
                requestId, exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                exchange.getResponse().getStatusCode(),
                (System.nanoTime() - started) / 1_000_000));
    }

    private String normalize(String candidate) {
        if (StringUtils.hasText(candidate) && candidate.length() <= 64
                && candidate.matches("[A-Za-z0-9._:-]+")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
