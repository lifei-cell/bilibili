package com.gary.bilibili.danmu.service;

import com.gary.bilibili.danmu.mapper.DanmuMapper;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class DanmuBatchPersistService {

    private static final int QUEUE_CAPACITY = 10000;

    private final DanmuMapper danmuMapper;
    private final LinkedBlockingQueue<PendingMessage> queue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final int batchSize;

    public DanmuBatchPersistService(DanmuMapper danmuMapper,
                                    @Value("${danmu.persist.batch-size:100}") int batchSize) {
        this.danmuMapper = danmuMapper;
        this.batchSize = batchSize;
    }

    public void persist(DanmuPersistMessage message) {
        PendingMessage pending = new PendingMessage(message);
        try {
            if (!queue.offer(pending, 1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Danmu persist queue is full");
            }
            pending.getCompleted().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Danmu persistence was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Danmu persistence failed", exception);
        }
    }

    @Scheduled(fixedDelayString = "${danmu.persist.flush-interval:100}")
    public void flush() {
        List<PendingMessage> pendingMessages = new ArrayList<>(batchSize);
        queue.drainTo(pendingMessages, batchSize);
        if (pendingMessages.isEmpty()) {
            return;
        }

        List<DanmuPersistMessage> messages = pendingMessages.stream()
                .map(PendingMessage::getMessage)
                .toList();
        try {
            danmuMapper.insertBatch(messages);
            pendingMessages.forEach(item -> item.getCompleted().complete(null));
        } catch (Exception exception) {
            pendingMessages.forEach(item -> item.getCompleted().completeExceptionally(exception));
        }
    }

    @PreDestroy
    public void destroy() {
        while (!queue.isEmpty()) {
            flush();
        }
    }

    private static class PendingMessage {

        private final DanmuPersistMessage message;
        private final CompletableFuture<Void> completed = new CompletableFuture<>();

        private PendingMessage(DanmuPersistMessage message) {
            this.message = message;
        }

        private DanmuPersistMessage getMessage() {
            return message;
        }

        private CompletableFuture<Void> getCompleted() {
            return completed;
        }
    }
}
