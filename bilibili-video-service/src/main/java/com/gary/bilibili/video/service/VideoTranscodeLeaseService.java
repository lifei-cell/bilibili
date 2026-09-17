package com.gary.bilibili.video.service;

import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Renews the database lease during blocking FFmpeg work and fences publication. */
@Service
public class VideoTranscodeLeaseService {

    private final VideoTranscodeTaskMapper taskMapper;
    private final int leaseSeconds;

    public VideoTranscodeLeaseService(VideoTranscodeTaskMapper taskMapper,
                                      @Value("${video.transcode.lease-seconds:120}") int leaseSeconds) {
        this.taskMapper = taskMapper;
        this.leaseSeconds = Math.max(30, leaseSeconds);
    }

    public Lease start(String taskId, long generation, String token) {
        Lease lease = new Lease(taskId, generation, token);
        lease.scheduler.scheduleWithFixedDelay(lease::heartbeat,
                leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        return lease;
    }

    public final class Lease implements AutoCloseable {
        private final String taskId;
        private final long generation;
        private final String token;
        private final AtomicBoolean held = new AtomicBoolean(true);
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "transcode-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });

        private Lease(String taskId, long generation, String token) {
            this.taskId = taskId;
            this.generation = generation;
            this.token = token;
        }

        public void assertHeld() {
            if (!held.get()) {
                throw new LeaseLostException(taskId);
            }
            try {
                if (taskMapper.renewLease(taskId, generation, token, leaseSeconds) != 1) {
                    held.set(false);
                    throw new LeaseLostException(taskId);
                }
            } catch (RuntimeException exception) {
                held.set(false);
                throw exception;
            }
        }

        private void heartbeat() {
            if (!held.get()) {
                return;
            }
            try {
                assertHeld();
            } catch (RuntimeException exception) {
                held.set(false);
            }
        }

        @Override
        public void close() {
            scheduler.shutdownNow();
        }
    }

    public static class LeaseLostException extends IllegalStateException {
        public LeaseLostException(String taskId) {
            super("Video transcode lease lost: " + taskId);
        }
    }
}
