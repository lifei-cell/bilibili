package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounds external media work and reaps it before temporary files are removed. */
final class MediaProcessRunner {
    private MediaProcessRunner() { }

    static int await(Process process, long timeoutSeconds, String timeoutMessage) throws InterruptedException {
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new BusinessException(timeoutMessage);
            }
            return process.exitValue();
        } finally {
            terminate(process);
        }
    }

    private static void terminate(Process process) {
        if (!process.isAlive()) return;
        List<ProcessHandle> children = process.descendants().toList();
        // Kill children first so wrappers can reap them instead of leaving orphan encoders.
        children.forEach(ProcessHandle::destroyForcibly);
        boolean interrupted = false;
        try {
            if (!children.isEmpty()) process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            interrupted = true;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                interrupted = true;
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
