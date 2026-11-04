package com.gary.bilibili.video.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP proxy: passes signed S3 traffic through, fails selected master PUTs only. */
final class TranscodeFaultProxy implements AutoCloseable {
    private static final Set<String> HOP_HEADERS = Set.of(
            "connection", "transfer-encoding", "content-length", "expect");
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient client = new OkHttpClient();
    private final String upstream;
    private volatile String failingQuality;
    final AtomicInteger rejectedWrites = new AtomicInteger();
    final AtomicInteger acceptedMediaWrites = new AtomicInteger();

    TranscodeFaultProxy(String upstream) throws IOException {
        this.upstream = upstream;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::forward);
        server.start();
    }

    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    void failMasterContaining(String quality) { failingQuality = quality; }

    private void forward(HttpExchange exchange) throws IOException {
        try (exchange) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("PUT".equals(method) && path.endsWith("/master.m3u8")
                    && failingQuality != null
                    && new String(body, StandardCharsets.UTF_8).contains(failingQuality + "/index.m3u8")) {
                rejectedWrites.incrementAndGet();
                byte[] failure = "<Error><Code>ServiceUnavailable</Code><Message>injected HLS write failure</Message></Error>"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(503, failure.length);
                exchange.getResponseBody().write(failure);
                return;
            }
            Request.Builder request = new Request.Builder().url(upstream + exchange.getRequestURI());
            // Keep the original Host: it participates in the S3 signature.
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    values.forEach(value -> request.addHeader(name, value));
                }
            });
            request.method(method, Set.of("GET", "HEAD").contains(method) ? null : RequestBody.create(body, null));
            try (Response response = client.newCall(request.build()).execute()) {
                response.headers().toMultimap().forEach((name, values) -> {
                    if (!HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                        exchange.getResponseHeaders().put(name, values);
                    }
                });
                byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
                exchange.sendResponseHeaders(response.code(), "HEAD".equals(method) || bytes.length == 0 ? -1 : bytes.length);
                if (!"HEAD".equals(method)) exchange.getResponseBody().write(bytes);
                if (response.isSuccessful() && "PUT".equals(method) && path.endsWith(".ts")) {
                    acceptedMediaWrites.incrementAndGet();
                }
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        client.dispatcher().executorService().shutdownNow();
        client.connectionPool().evictAll();
    }
}
