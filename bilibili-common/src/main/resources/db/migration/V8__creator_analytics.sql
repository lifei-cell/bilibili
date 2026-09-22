CREATE TABLE IF NOT EXISTS video_view_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    video_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_view_event_request (request_id),
    KEY idx_view_event_creator_day (creator_id, occurred_at, video_id),
    KEY idx_view_event_video_day (video_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='播放事实事件';

CREATE TABLE IF NOT EXISTS creator_video_daily_stats (
    creator_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    collect_count BIGINT NOT NULL DEFAULT 0,
    comment_count BIGINT NOT NULL DEFAULT 0,
    danmu_count BIGINT NOT NULL DEFAULT 0,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (creator_id, video_id, stat_date),
    KEY idx_creator_daily_range (creator_id, stat_date),
    KEY idx_creator_daily_top (creator_id, stat_date, view_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='创作者视频日聚合';

CREATE TABLE IF NOT EXISTS creator_analytics_dirty_day (
    creator_id BIGINT NOT NULL,
    stat_date DATE NOT NULL,
    dirty_version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    claim_token VARCHAR(64) NULL,
    locked_until DATETIME(3) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (creator_id, stat_date),
    KEY idx_dirty_claim (status, locked_until, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待重算创作者日期';
