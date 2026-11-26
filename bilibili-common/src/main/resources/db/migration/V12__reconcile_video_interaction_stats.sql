-- Rebuild from committed facts after taking the old social writers and synchronizers offline.
INSERT INTO video_stats (video_id)
SELECT v.id FROM video v
WHERE NOT EXISTS (SELECT 1 FROM video_stats s WHERE s.video_id = v.id);

UPDATE video_stats s
LEFT JOIN (
    SELECT target_id, COUNT(*) AS total
    FROM user_like WHERE target_type = 1 AND status = 1 GROUP BY target_id
) likes ON likes.target_id = s.video_id
LEFT JOIN (
    SELECT video_id, COUNT(*) AS total
    FROM collection WHERE status = 1 GROUP BY video_id
) collections ON collections.video_id = s.video_id
LEFT JOIN (
    SELECT video_id, COUNT(*) AS total
    FROM comment WHERE status = 0 AND deleted = 0 GROUP BY video_id
) comments ON comments.video_id = s.video_id
SET s.like_count = COALESCE(likes.total, 0),
    s.collect_count = COALESCE(collections.total, 0),
    s.comment_count = COALESCE(comments.total, 0);
