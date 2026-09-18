package com.gary.bilibili.canal.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MySqlNamedLockTest {

    @Test
    void holdsTheSameConnectionUntilTheActionCompletes() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select get_lock(?, ?)")).thenReturn(acquire);
        when(connection.prepareStatement("select release_lock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MySqlNamedLock lock = new MySqlNamedLock(dataSource, registry);

        assertThat(lock.execute("lock-name", () -> "done")).isEqualTo("done");
        verify(acquire).setString(1, "lock-name");
        verify(acquire).setInt(2, 0);
        verify(release).setString(1, "lock-name");
        verify(release).executeQuery();
        verify(connection).close();
        assertThat(registry.get("bilibili.index.lock.wait").tag("operation", "cutover").timer().count()).isEqualTo(1);
        assertThat(registry.get("bilibili.index.lock.hold").tag("operation", "cutover").timer().count()).isEqualTo(1);
    }

    @Test
    void rejectsAContendedLockWithoutRunningTheAction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select get_lock(?, ?)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MySqlNamedLock lock = new MySqlNamedLock(dataSource, registry);

        assertThatThrownBy(() -> lock.execute("lock-name", () -> {
            throw new AssertionError("contended lock must not run the action");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already held");
        verify(connection, never()).prepareStatement("select release_lock(?)");
        assertThat(registry.get("bilibili.index.lock.wait").tag("operation", "cutover").timer().count()).isEqualTo(1);
        assertThat(registry.find("bilibili.index.lock.hold").timer()).isNull();
    }

    @Test
    void supportsWaitingForAContendedLock() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select get_lock(?, ?)")).thenReturn(acquire);
        when(connection.prepareStatement("select release_lock(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MySqlNamedLock lock = new MySqlNamedLock(dataSource, registry);

        assertThat(lock.execute("lock-name", 30, () -> "done")).isEqualTo("done");
        verify(acquire).setInt(2, 30);
        assertThat(registry.get("bilibili.index.lock.wait").tag("operation", "cdc").timer().count()).isEqualTo(1);
    }
}
