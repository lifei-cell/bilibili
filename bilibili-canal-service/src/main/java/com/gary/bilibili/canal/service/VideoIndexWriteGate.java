package com.gary.bilibili.canal.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class VideoIndexWriteGate {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public void withCdcWrite(Runnable action) {
        lock.readLock().lock();
        try {
            action.run();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void withCutover(Runnable action) {
        lock.writeLock().lock();
        try {
            action.run();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
