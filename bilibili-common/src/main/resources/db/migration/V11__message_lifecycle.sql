ALTER TABLE reliable_event_outbox
    ADD COLUMN replay_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_replay_time DATETIME(3) NULL,
    ADD INDEX idx_outbox_archive (owner, status, published_at, id);

CREATE TABLE reliable_event_outbox_archive LIKE reliable_event_outbox;
ALTER TABLE reliable_event_outbox_archive
    ADD COLUMN archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

-- Compact, permanent receipts: deleting these would allow old MQ/DLQ messages to run again.
CREATE TABLE mq_inbox_receipt (
    consumer_group VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(191) NOT NULL,
    consumed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (consumer_group, topic, message_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE mq_consumed_message ADD INDEX idx_inbox_retention (status, consumed_at, id);

CREATE TABLE mq_replay_audit (
    request_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT NOT NULL,
    expected_replay_count INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (request_id),
    KEY idx_replay_source (source_type, source_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
