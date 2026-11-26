package com.gary.bilibili.common.social;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
@Transactional(propagation = Propagation.MANDATORY)
public class VideoInteractionStats {

    private final JdbcTemplate jdbc;

    public VideoInteractionStats(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void changeLikes(long videoId, int delta) {
        jdbc.update("update video_stats set like_count = like_count + ? where video_id = ?", delta, videoId);
    }

    public void changeCollections(long videoId, int delta) {
        jdbc.update("update video_stats set collect_count = collect_count + ? where video_id = ?", delta, videoId);
    }

    public void changeComments(long videoId, int delta) {
        jdbc.update("update video_stats set comment_count = comment_count + ? where video_id = ?", delta, videoId);
    }
}
