package com.gary.bilibili.canal.listener;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.gary.bilibili.canal.config.CanalProperties;
import com.gary.bilibili.canal.constant.CanalConstant;
import com.gary.bilibili.canal.message.CacheSyncEvent;
import com.gary.bilibili.common.reliability.OutboxEventService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CanalSyncListener {

    private static final Logger log = LoggerFactory.getLogger(CanalSyncListener.class);

    private final CanalConnector canalConnector;
    private final CanalProperties canalProperties;
    private final OutboxEventService outboxEventService;
    private volatile boolean connected;

    public CanalSyncListener(CanalConnector canalConnector,
                             CanalProperties canalProperties,
                             OutboxEventService outboxEventService) {
        this.canalConnector = canalConnector;
        this.canalProperties = canalProperties;
        this.outboxEventService = outboxEventService;
    }

    @Scheduled(
            fixedDelayString = "${canal.server.pull-interval:1000}",
            initialDelayString = "${canal.server.initial-delay:5000}")
    public void pullChanges() {
        long batchId = -1L;
        try {
            ensureConnected();
            Message message = canalConnector.getWithoutAck(canalProperties.getBatchSize());
            batchId = message.getId();
            if (batchId == -1L) {
                return;
            }
            if (message.getEntries().isEmpty()) {
                canalConnector.ack(batchId);
                return;
            }
            for (CanalEntry.Entry entry : message.getEntries()) {
                publishEntry(entry);
            }
            canalConnector.ack(batchId);
        } catch (Exception exception) {
            rollback(batchId);
            disconnect();
            log.error("Pull and publish Canal changes failed", exception);
        }
    }

    private void ensureConnected() {
        if (connected) {
            return;
        }
        canalConnector.connect();
        connected = true;
        canalConnector.subscribe(canalProperties.getFilter());
        canalConnector.rollback();
        log.info("Canal connector connected, destination={}, filter={}",
                canalProperties.getDestination(), canalProperties.getFilter());
    }

    private void publishEntry(CanalEntry.Entry entry) throws Exception {
        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
            return;
        }
        CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        CanalEntry.EventType eventType = rowChange.getEventType();
        if (eventType != CanalEntry.EventType.INSERT
                && eventType != CanalEntry.EventType.UPDATE
                && eventType != CanalEntry.EventType.DELETE) {
            return;
        }
        int rowIndex = 0;
        for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
            List<CanalEntry.Column> columns = eventType == CanalEntry.EventType.DELETE
                    ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();
            CacheSyncEvent event = new CacheSyncEvent();
            event.setEventId(buildEventId(entry, rowIndex++));
            event.setDatabase(entry.getHeader().getSchemaName());
            event.setTable(entry.getHeader().getTableName());
            event.setEventType(eventType.name());
            event.setData(toMap(columns));
            String aggregateId = event.getData().getOrDefault("id",
                    event.getData().getOrDefault("video_id", "unknown"));
            outboxEventService.appendStandalone(
                    event.getEventId(), event.getTable(), aggregateId,
                    "CDC_" + event.getEventType(), CanalConstant.CACHE_SYNC_TOPIC, event);
        }
    }

    private String buildEventId(CanalEntry.Entry entry, int rowIndex) {
        CanalEntry.Header header = entry.getHeader();
        String file = header.getLogfileName() == null ? "unknown" : header.getLogfileName();
        return "cdc:" + file + ":" + header.getLogfileOffset() + ":" + rowIndex;
    }

    private Map<String, String> toMap(List<CanalEntry.Column> columns) {
        Map<String, String> data = new LinkedHashMap<>();
        for (CanalEntry.Column column : columns) {
            data.put(column.getName(), column.getIsNull() ? null : column.getValue());
        }
        return data;
    }

    private void rollback(long batchId) {
        if (!connected) {
            return;
        }
        try {
            if (batchId == -1L) {
                canalConnector.rollback();
            } else {
                canalConnector.rollback(batchId);
            }
        } catch (Exception exception) {
            log.warn("Rollback Canal batch failed, batchId={}", batchId, exception);
        }
    }

    private void disconnect() {
        if (!connected) {
            return;
        }
        try {
            canalConnector.disconnect();
        } catch (Exception exception) {
            log.warn("Disconnect Canal connector failed", exception);
        } finally {
            connected = false;
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }
}
