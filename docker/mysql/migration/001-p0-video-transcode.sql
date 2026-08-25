USE bilibili;

CREATE TABLE IF NOT EXISTS video_transcode_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id VARCHAR(64) NOT NULL COMMENT '上传任务 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    file_md5 VARCHAR(64) NOT NULL COMMENT '文件 MD5',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    source_url VARCHAR(500) NOT NULL COMMENT '源视频地址',
    source_object_name VARCHAR(500) NULL COMMENT '源对象名',
    output_url VARCHAR(500) NULL COMMENT '转码产物地址',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待投递 1已投递 2处理中 3成功 4失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message VARCHAR(500) NULL COMMENT '最近一次错误',
    next_retry_time DATETIME NULL COMMENT '下次重试时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_transcode_dispatch (status, next_retry_time, update_time),
    KEY idx_transcode_file_md5 (file_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频转码任务表';
