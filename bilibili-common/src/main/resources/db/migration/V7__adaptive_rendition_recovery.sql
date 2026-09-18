ALTER TABLE video_transcode_task
    ADD COLUMN rendition_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未开始 1生成中 2完成 3待补偿 4补偿失败 5已派发',
    ADD COLUMN rendition_retry_count INT NOT NULL DEFAULT 0 COMMENT '高档位补偿领取次数',
    ADD COLUMN rendition_next_retry_time DATETIME(6) NULL COMMENT '高档位下次补偿时间',
    ADD COLUMN rendition_error_message VARCHAR(500) NULL COMMENT '高档位最近失败原因';

UPDATE video_transcode_task
SET rendition_status = CASE
        WHEN claim_token IS NOT NULL THEN 1
        WHEN error_message IS NOT NULL AND output_url IS NOT NULL THEN 3
        ELSE 2
    END,
    rendition_error_message = CASE
        WHEN error_message IS NOT NULL AND output_url IS NOT NULL THEN error_message
        ELSE NULL
    END
WHERE status = 3;

CREATE INDEX idx_transcode_rendition_recovery
    ON video_transcode_task (status, rendition_status, rendition_next_retry_time, lease_until);
