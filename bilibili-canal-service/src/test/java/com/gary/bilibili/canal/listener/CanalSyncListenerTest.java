package com.gary.bilibili.canal.listener;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.gary.bilibili.canal.config.CanalProperties;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.common.reliability.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanalSyncListenerTest {

    @Test
    void shouldPublishRowChangesAndAckCanalBatch() {
        CanalConnector canalConnector = mock(CanalConnector.class);
        OutboxEventService outboxEventService = mock(OutboxEventService.class);
        CanalProperties properties = new CanalProperties();
        properties.setFilter("bilibili\\.(video|video_stats)");
        properties.setBatchSize(1000);
        when(canalConnector.getWithoutAck(1000))
                .thenReturn(new Message(9L, List.of(buildVideoEntry())));
        CanalSyncListener listener = new CanalSyncListener(
                canalConnector, properties, outboxEventService);

        listener.pullChanges();

        ArgumentCaptor<CacheSyncEvent> captor = ArgumentCaptor.forClass(CacheSyncEvent.class);
        verify(outboxEventService).appendStandalone(
                anyString(), eq("video"), eq("10001"), eq("CDC_UPDATE"),
                eq("cache-sync"), captor.capture());
        assertThat(captor.getValue()).satisfies(event -> {
            assertThat(event.getEventId()).isEqualTo("cdc:mysql-bin.000001:42:0");
            assertThat(event.getTable()).isEqualTo("video");
            assertThat(event.getEventType()).isEqualTo("UPDATE");
            assertThat(event.getData().get("id")).isEqualTo("10001");
        });
        verify(canalConnector).ack(9L);
        listener.destroy();
    }

    private CanalEntry.Entry buildVideoEntry() {
        CanalEntry.Column id = CanalEntry.Column.newBuilder()
                .setName("id")
                .setValue("10001")
                .build();
        CanalEntry.Column status = CanalEntry.Column.newBuilder()
                .setName("status")
                .setValue("1")
                .build();
        CanalEntry.RowData rowData = CanalEntry.RowData.newBuilder()
                .addAfterColumns(id)
                .addAfterColumns(status)
                .build();
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.newBuilder()
                .setEventType(CanalEntry.EventType.UPDATE)
                .addRowDatas(rowData)
                .build();
        CanalEntry.Header header = CanalEntry.Header.newBuilder()
                .setSchemaName("bilibili")
                .setTableName("video")
                .setLogfileName("mysql-bin.000001")
                .setLogfileOffset(42L)
                .build();
        return CanalEntry.Entry.newBuilder()
                .setHeader(header)
                .setEntryType(CanalEntry.EntryType.ROWDATA)
                .setStoreValue(rowChange.toByteString())
                .build();
    }
}
