SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    nickname VARCHAR(50) NOT NULL COMMENT '昵称',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    avatar VARCHAR(500) NULL COMMENT '头像 URL',
    gender TINYINT NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
    birthday DATE NULL COMMENT '生日',
    signature VARCHAR(200) NULL COMMENT '个性签名',
    role VARCHAR(30) NOT NULL DEFAULT 'user' COMMENT 'user/admin',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1封禁',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表';

CREATE TABLE IF NOT EXISTS sys_user_auth (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    identity_type VARCHAR(30) NOT NULL COMMENT 'phone/password/third_party',
    identifier VARCHAR(100) NOT NULL COMMENT '手机号、用户名、第三方 openId',
    credential VARCHAR(255) NULL COMMENT 'BCrypt 密码或凭证摘要',
    salt VARCHAR(64) NULL COMMENT '预留盐值字段',
    verified TINYINT NOT NULL DEFAULT 0 COMMENT '0未验证 1已验证',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity (identifier, identity_type),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证表';

CREATE TABLE IF NOT EXISTS video_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类 ID，0 表示一级分类',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_status_sort (status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频分类表';

CREATE TABLE IF NOT EXISTS video (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '上传用户 ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    description TEXT NULL COMMENT '简介',
    cover_url VARCHAR(500) NULL COMMENT '封面 URL',
    source_url VARCHAR(500) NULL COMMENT '源视频 URL',
    play_url VARCHAR(500) NULL COMMENT '默认播放 URL',
    file_md5 VARCHAR(64) NOT NULL COMMENT '文件 MD5',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    duration INT NOT NULL DEFAULT 0 COMMENT '时长，单位秒',
    resolution VARCHAR(20) NULL COMMENT '默认分辨率',
    category_id BIGINT NULL COMMENT '分类 ID',
    tags VARCHAR(500) NULL COMMENT '逗号分隔标签',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0审核中 1已发布 2审核拒绝 3已下架',
    audit_remark VARCHAR(500) NULL COMMENT '审核备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_category_id (category_id),
    KEY idx_status_create_time (status, create_time),
    KEY idx_file_md5 (file_md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频主表';

CREATE TABLE IF NOT EXISTS video_stats (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    video_id BIGINT NOT NULL COMMENT '视频 ID',
    view_count BIGINT NOT NULL DEFAULT 0 COMMENT '播放量',
    like_count BIGINT NOT NULL DEFAULT 0 COMMENT '点赞数',
    coin_count BIGINT NOT NULL DEFAULT 0 COMMENT '投币数，预留',
    collect_count BIGINT NOT NULL DEFAULT 0 COMMENT '收藏数',
    share_count BIGINT NOT NULL DEFAULT 0 COMMENT '分享数',
    danmu_count BIGINT NOT NULL DEFAULT 0 COMMENT '弹幕数',
    comment_count BIGINT NOT NULL DEFAULT 0 COMMENT '评论数',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_id (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频统计表';

CREATE TABLE IF NOT EXISTS file_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    upload_id VARCHAR(64) NOT NULL COMMENT '上传任务 ID',
    user_id BIGINT NOT NULL COMMENT '上传用户 ID',
    file_md5 VARCHAR(64) NOT NULL COMMENT '文件完整 MD5',
    chunk_md5 VARCHAR(64) NULL COMMENT '分片 MD5',
    chunk_index INT NOT NULL COMMENT '分片序号，从 0 开始',
    chunk_size BIGINT NOT NULL DEFAULT 0 COMMENT '分片大小',
    total_chunks INT NOT NULL COMMENT '总分片数',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    object_name VARCHAR(500) NOT NULL COMMENT 'MinIO 临时对象名',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0上传中 1已完成',
    expire_time DATETIME NULL COMMENT '过期时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_upload_chunk (upload_id, chunk_index),
    KEY idx_file_md5 (file_md5),
    KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分片表';

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

CREATE TABLE IF NOT EXISTS danmu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '发送用户 ID',
    video_id BIGINT NOT NULL COMMENT '视频 ID',
    content VARCHAR(200) NOT NULL COMMENT '弹幕内容',
    color VARCHAR(10) NOT NULL DEFAULT '#FFFFFF' COMMENT '颜色',
    position TINYINT NOT NULL DEFAULT 0 COMMENT '0滚动 1顶部 2底部',
    font_size INT NOT NULL DEFAULT 16 COMMENT '字体大小',
    video_time INT NOT NULL DEFAULT 0 COMMENT '视频时间点，单位秒',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1屏蔽',
    send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_video_time (video_id, video_time),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='弹幕表';

CREATE TABLE IF NOT EXISTS comment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '评论用户 ID',
    video_id BIGINT NOT NULL COMMENT '视频 ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父评论 ID，0 表示一级评论',
    reply_to_id BIGINT NOT NULL DEFAULT 0 COMMENT '回复目标用户 ID',
    content TEXT NOT NULL COMMENT '评论内容',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1屏蔽',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
    PRIMARY KEY (id),
    KEY idx_video_create_time (video_id, create_time),
    KEY idx_parent_id (parent_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

CREATE TABLE IF NOT EXISTS follow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    follower_id BIGINT NOT NULL COMMENT '关注者用户 ID',
    followed_id BIGINT NOT NULL COMMENT '被关注者用户 ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1关注中 0已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follow (follower_id, followed_id),
    KEY idx_followed_id (followed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关注表';

CREATE TABLE IF NOT EXISTS user_like (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    target_type TINYINT NOT NULL COMMENT '1视频 2评论',
    target_id BIGINT NOT NULL COMMENT '目标 ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1点赞 0取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞表';

CREATE TABLE IF NOT EXISTS collection_folder (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    name VARCHAR(50) NOT NULL COMMENT '收藏夹名称',
    is_public TINYINT NOT NULL DEFAULT 0 COMMENT '0私密 1公开',
    description VARCHAR(200) NULL COMMENT '描述',
    cover_url VARCHAR(500) NULL COMMENT '封面',
    video_count INT NOT NULL DEFAULT 0 COMMENT '视频数量冗余',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0未删除 1已删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏夹表';

CREATE TABLE IF NOT EXISTS collection (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    video_id BIGINT NOT NULL COMMENT '视频 ID',
    folder_id BIGINT NOT NULL DEFAULT 0 COMMENT '收藏夹 ID，0 表示默认收藏夹',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1收藏中 0已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_collection (user_id, video_id, folder_id),
    KEY idx_folder_id (folder_id),
    KEY idx_video_id (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

INSERT IGNORE INTO video_category (id, parent_id, name, sort, status) VALUES
    (1, 0, '动画', 10, 1),
    (2, 0, '番剧', 20, 1),
    (3, 0, '国创', 30, 1),
    (4, 0, '音乐', 40, 1),
    (5, 0, '舞蹈', 50, 1),
    (6, 0, '游戏', 60, 1),
    (7, 0, '知识', 70, 1),
    (8, 0, '科技', 80, 1),
    (9, 0, '运动', 90, 1),
    (10, 0, '生活', 100, 1),
    (11, 0, '美食', 110, 1),
    (12, 0, '影视', 120, 1);

SET FOREIGN_KEY_CHECKS = 1;
