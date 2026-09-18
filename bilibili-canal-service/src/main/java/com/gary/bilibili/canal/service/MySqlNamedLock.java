package com.gary.bilibili.canal.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Holds a MySQL named lock on one connection while a cross-instance operation runs. */
@Component
public class MySqlNamedLock {

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;

    public MySqlNamedLock(DataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    public <T> T execute(String name, Supplier<T> action) {
        return execute(name, 0, "cutover", action);
    }

    public <T> T execute(String name, int timeoutSeconds, Supplier<T> action) {
        return execute(name, timeoutSeconds, "cdc", action);
    }

    public <T> T execute(String name, int timeoutSeconds, String operation, Supplier<T> action) {
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("MySQL named lock timeout must not be negative");
        }
        try (Connection connection = dataSource.getConnection()) {
            long waitStarted = System.nanoTime();
            boolean acquired;
            try {
                acquired = acquire(connection, name, timeoutSeconds);
            } finally {
                Timer.builder("bilibili.index.lock.wait")
                        .description("Time spent waiting for the shared video index MySQL named lock")
                        .tag("operation", operation)
                        .register(meterRegistry)
                        .record(System.nanoTime() - waitStarted, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
            if (!acquired) {
                throw new IllegalStateException("MySQL named lock is already held: " + name);
            }
            long holdStarted = System.nanoTime();
            try {
                return action.get();
            } finally {
                try {
                    release(connection, name);
                } finally {
                    Timer.builder("bilibili.index.lock.hold")
                            .description("Time holding the shared video index MySQL named lock")
                            .tag("operation", operation)
                            .register(meterRegistry)
                            .record(System.nanoTime() - holdStarted, java.util.concurrent.TimeUnit.NANOSECONDS);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot execute MySQL named lock: " + name, exception);
        }
    }

    private boolean acquire(Connection connection, String name, int timeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, ?)")) {
            statement.setString(1, name);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
            statement.setString(1, name);
            statement.executeQuery();
        }
    }
}
