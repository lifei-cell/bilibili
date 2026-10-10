ALTER TABLE mq_consumed_message
    ADD COLUMN claim_generation BIGINT NOT NULL DEFAULT 0 AFTER attempt_count,
    ADD COLUMN claim_token VARCHAR(36) NULL AFTER claim_generation;
