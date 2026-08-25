ALTER TABLE video
    ADD INDEX idx_video_public_new (status, deleted, create_time, id),
    ADD INDEX idx_video_category_public (category_id, status, deleted, create_time, id),
    ADD INDEX idx_video_user_public (user_id, status, deleted, create_time, id);

ALTER TABLE video_stats
    ADD INDEX idx_video_stats_hot (view_count, video_id);
