ALTER TABLE video_transcode_task
    ADD COLUMN cover_url VARCHAR(500) NULL COMMENT '自动截帧封面' AFTER output_url,
    ADD COLUMN variants_json JSON NULL COMMENT 'HLS清晰度与带宽元数据' AFTER cover_url;

ALTER TABLE video
    ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH' AFTER audit_remark,
    ADD COLUMN audit_by BIGINT NULL COMMENT '最近审核人' AFTER risk_level,
    ADD COLUMN audit_time DATETIME NULL COMMENT '最近审核时间' AFTER audit_by;

CREATE TABLE IF NOT EXISTS direct_upload_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    upload_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    file_md5 VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    object_name VARCHAR(500) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待上传 1已确认 2已过期',
    expire_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_direct_upload_id (upload_id),
    KEY idx_direct_expire (status, expire_time),
    KEY idx_direct_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预签名直传会话';

CREATE TABLE IF NOT EXISTS content_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL COMMENT 'VIDEO/COMMENT/DANMU',
    target_id BIGINT NOT NULL,
    reason_code VARCHAR(30) NOT NULL,
    description VARCHAR(500) NULL,
    evidence_url VARCHAR(500) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2成立 3驳回',
    handled_by BIGINT NULL,
    handle_remark VARCHAR(500) NULL,
    handled_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reporter_target (reporter_id, target_type, target_id),
    KEY idx_report_queue (status, create_time),
    KEY idx_report_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容举报';

CREATE TABLE IF NOT EXISTS content_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL COMMENT 'APPROVE/REJECT/OFFLINE/RESTORE',
    operator_id BIGINT NOT NULL,
    previous_status INT NULL,
    current_status INT NOT NULL,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_target (target_type, target_id, create_time),
    KEY idx_audit_operator (operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变内容审核日志';

CREATE TABLE IF NOT EXISTS content_risk_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    scene VARCHAR(30) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    score INT NOT NULL,
    matched_rules VARCHAR(500) NULL,
    decision VARCHAR(20) NOT NULL COMMENT 'PASS/REVIEW/REJECT',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_risk_queue (risk_level, create_time),
    KEY idx_risk_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容风控决策记录';
