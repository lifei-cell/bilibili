package com.gary.bilibili.canal.service;

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

    public MySqlNamedLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T execute(String name, Supplier<T> action) {
        return execute(name, 0, action);
    }

    public <T> T execute(String name, int timeoutSeconds, Supplier<T> action) {
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("MySQL named lock timeout must not be negative");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, name, timeoutSeconds)) {
                throw new IllegalStateException("MySQL named lock is already held: " + name);
            }
            try {
                return action.get();
            } finally {
                release(connection, name);
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
