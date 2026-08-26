package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.document.VideoDocument;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.StreamSupport;

@Service
public class SearchIndexMaintenanceService {

    private static final int WRITE_BATCH_SIZE = 200;
    private static final int SAMPLE_LIMIT = 100;
    private static final DateTimeFormatter ES_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final VideoDocumentRepository repository;
    private final ReentrantLock maintenanceLock = new ReentrantLock();

    public SearchIndexMaintenanceService(JdbcTemplate jdbcTemplate,
                                         VideoDocumentRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    public RebuildResult rebuild() {
        if (!maintenanceLock.tryLock()) {
            throw new IllegalStateException("Elasticsearch rebuild is already running");
        }
        try {
            List<VideoDocument> source = loadPublishedVideos();
            repository.deleteAll();
            for (int offset = 0; offset < source.size(); offset += WRITE_BATCH_SIZE) {
                int end = Math.min(source.size(), offset + WRITE_BATCH_SIZE);
                repository.saveAll(source.subList(offset, end));
            }
            ReconciliationReport report = reconcileInternal(source);
            if (!report.consistent()) {
                throw new IllegalStateException("Elasticsearch rebuild verification failed: " + report);
            }
            return new RebuildResult(source.size(), report.elasticsearchCount(),
                    report.checkedAt(), true);
        } finally {
            maintenanceLock.unlock();
        }
    }

    public ReconciliationReport reconcile() {
        return reconcileInternal(loadPublishedVideos());
    }

    private ReconciliationReport reconcileInternal(List<VideoDocument> mysqlDocuments) {
        Map<Long, VideoDocument> mysql = toMap(mysqlDocuments);
        Map<Long, VideoDocument> elasticsearch = toMap(StreamSupport.stream(
                repository.findAll().spliterator(), false).toList());

        Set<Long> missing = new LinkedHashSet<>(mysql.keySet());
        missing.removeAll(elasticsearch.keySet());
        Set<Long> orphan = new LinkedHashSet<>(elasticsearch.keySet());
        orphan.removeAll(mysql.keySet());
        List<Long> mismatched = mysql.entrySet().stream()
                .filter(entry -> elasticsearch.containsKey(entry.getKey()))
                .filter(entry -> !same(entry.getValue(), elasticsearch.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();
        boolean consistent = missing.isEmpty() && orphan.isEmpty() && mismatched.isEmpty();
        return new ReconciliationReport(mysql.size(), elasticsearch.size(), consistent,
                sample(missing), sample(orphan), sample(mismatched), LocalDateTime.now());
    }

    private List<VideoDocument> loadPublishedVideos() {
        return jdbcTemplate.query("""
                select v.id, v.title, v.description, v.tags, v.category_id, v.user_id,
                       v.status, v.create_time,
                       coalesce(s.view_count, 0) as view_count,
                       coalesce(s.like_count, 0) as like_count
                from video v
                left join video_stats s on s.video_id = v.id
                where v.status = 1 and v.deleted = 0
                order by v.id
                """, (rs, rowNum) -> mapDocument(rs));
    }

    private VideoDocument mapDocument(ResultSet rs) throws SQLException {
        VideoDocument document = new VideoDocument();
        document.setId(rs.getLong("id"));
        document.setTitle(rs.getString("title"));
        document.setDescription(rs.getString("description"));
        document.setTags(splitTags(rs.getString("tags")));
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

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private Map<Long, VideoDocument> toMap(List<VideoDocument> documents) {
        Map<Long, VideoDocument> result = new LinkedHashMap<>();
        documents.forEach(document -> result.put(document.getId(), document));
        return result;
    }

    private boolean same(VideoDocument left, VideoDocument right) {
        return Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getTags(), right.getTags())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getUserId(), right.getUserId())
                && Objects.equals(left.getStatus(), right.getStatus())
                && Objects.equals(left.getViewCount(), right.getViewCount())
                && Objects.equals(left.getLikeCount(), right.getLikeCount());
    }

    private List<Long> sample(Iterable<Long> values) {
        List<Long> result = new ArrayList<>();
        for (Long value : values) {
            if (result.size() == SAMPLE_LIMIT) {
                break;
            }
            result.add(value);
        }
        return result;
    }

    public record RebuildResult(long mysqlCount, long indexedCount,
                                LocalDateTime completedAt, boolean verified) {
    }

    public record ReconciliationReport(long mysqlCount, long elasticsearchCount,
                                       boolean consistent, List<Long> missingIds,
                                       List<Long> orphanIds, List<Long> mismatchedIds,
                                       LocalDateTime checkedAt) {
    }
}
