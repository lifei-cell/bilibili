package com.gary.bilibili.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/** Adds a stable request correlation id and low-cardinality metrics for critical APIs. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestObservabilityFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);
    private static final Map<String, String> CRITICAL_APIS = Map.ofEntries(
            Map.entry("/api/user/login", "user_login"),
            Map.entry("/api/user/register", "user_register"),
            Map.entry("/api/video", "video"),
            Map.entry("/api/upload", "video_upload"),
            Map.entry("/api/danmu", "danmu"),
            Map.entry("/api/follow", "follow"),
            Map.entry("/api/like", "like"),
            Map.entry("/api/comment", "comment"),
            Map.entry("/api/collection", "collection"),
            Map.entry("/api/search", "search"));

    private final MeterRegistry meterRegistry;
    private final ObjectProvider<Tracer> tracerProvider;

    public RequestObservabilityFilter(MeterRegistry meterRegistry,
                                      ObjectProvider<Tracer> tracerProvider) {
        this.meterRegistry = meterRegistry;
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(REQUEST_ID_HEADER));
        String traceId = currentTraceId();
        long started = System.nanoTime();
        String previousRequestId = MDC.get("requestId");
        String previousTraceId = MDC.get("traceId");
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        boolean completed = false;
        try {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                int status = completed ? response.getStatus() : Math.max(500, response.getStatus());
                String api = criticalApi(request.getRequestURI());
                if (api != null) {
                    Timer.builder("bilibili.api.duration")
                            .description("Latency of critical Bilibili APIs")
                            .tag("api", api)
                            .tag("method", request.getMethod())
                            .tag("status", Integer.toString(status))
                            .register(meterRegistry)
                            .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
                log.info("HTTP request completed method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status,
                        (System.nanoTime() - started) / 1_000_000);
            } finally {
                restoreMdc("requestId", previousRequestId);
                restoreMdc("traceId", previousTraceId);
            }
        }
    }

    private String currentTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        return span == null ? UUID.randomUUID().toString().replace("-", "")
                : span.context().traceId();
    }

    private void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    private String normalizeRequestId(String candidate) {
        if (StringUtils.hasText(candidate) && candidate.length() <= 64
                && candidate.matches("[A-Za-z0-9._:-]+")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    private String criticalApi(String path) {
        return CRITICAL_APIS.entrySet().stream()
                .filter(entry -> path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
