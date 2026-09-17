package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.document.VideoDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class PublishedVideoSource {

    private static final DateTimeFormatter ES_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final JdbcTemplate jdbc;

    public PublishedVideoSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<VideoDocument> page(long afterId, int limit) {
        return jdbc.query("""
                select v.id, v.title, v.description, v.tags, v.category_id, v.user_id,
                       v.status, v.create_time,
                       coalesce(s.view_count, 0) as view_count,
                       coalesce(s.like_count, 0) as like_count
                from video v left join video_stats s on s.video_id = v.id
                where v.status = 1 and v.deleted = 0 and v.id > ?
                order by v.id limit ?
                """, (rs, row) -> mapDocument(rs), afterId, limit);
    }

    public List<Long> publishedIds(List<Long> ids) {
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.queryForList("select id from video where status = 1 and deleted = 0 and id in ("
                + placeholders + ")", Long.class, ids.toArray());
    }

    private VideoDocument mapDocument(ResultSet rs) throws SQLException {
        VideoDocument document = new VideoDocument();
        document.setId(rs.getLong("id"));
        document.setTitle(rs.getString("title"));
        document.setDescription(rs.getString("description"));
        String tags = rs.getString("tags");
        document.setTags(tags == null || tags.isBlank() ? List.of() : Arrays.stream(tags.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList());
        long categoryId = rs.getLong("category_id");
        document.setCategoryId(rs.wasNull() ? null : categoryId);
        document.setUserId(rs.getLong("user_id"));
        document.setStatus(rs.getInt("status"));
        document.setViewCount(rs.getLong("view_count"));
        document.setLikeCount(rs.getLong("like_count"));
        document.setCreateTime(rs.getTimestamp("create_time")
                .toLocalDateTime().format(ES_DATE_TIME));
        return document;
    }
}
