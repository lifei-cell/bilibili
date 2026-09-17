ALTER TABLE video_transcode_task
    ADD COLUMN claim_generation BIGINT NOT NULL DEFAULT 0 COMMENT '转码尝试代次',
    ADD COLUMN claim_token VARCHAR(64) NULL COMMENT '当前持有者令牌',
    ADD COLUMN lease_until DATETIME(6) NULL COMMENT '当前持有者租约截止时间';

CREATE INDEX idx_transcode_lease ON video_transcode_task (status, lease_until);
