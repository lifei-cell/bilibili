package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.constant.CanalConstant;
import com.gary.bilibili.canal.document.VideoDocument;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.MultiGetItem;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class SearchIndexMaintenanceService {

    private static final int PAGE_SIZE = 200;
    private static final int SAMPLE_LIMIT = 100;
    private final PublishedVideoSource source;
    private final ElasticsearchOperations operations;
    private final VideoIndexAlias alias;
    private final VideoIndexWriteGate writeGate;
    private final ReentrantLock maintenanceLock = new ReentrantLock();

    public SearchIndexMaintenanceService(PublishedVideoSource source,
                                         ElasticsearchOperations operations,
                                         VideoIndexAlias alias,
                                         VideoIndexWriteGate writeGate) {
        this.source = source;
        this.operations = operations;
        this.alias = alias;
        this.writeGate = writeGate;
    }

    public RebuildResult rebuild() {
        return rebuildInto(null);
    }

    public RebuildResult rollback(String targetIndex) {
        if (!CanalConstant.LEGACY_VIDEO_INDEX.equals(targetIndex)
                && !(CanalConstant.VIDEO_INDEX_PREFIX + "initial").equals(targetIndex)
                && !targetIndex.matches("video_index_v[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid retained video index");
        }
        if (!operations.indexOps(IndexCoordinates.of(targetIndex)).exists()) {
            throw new IllegalArgumentException("Retained video index does not exist");
        }
        return rebuildInto(targetIndex);
    }

    private RebuildResult rebuildInto(String retainedIndex) {
        if (!maintenanceLock.tryLock()) {
            throw new IllegalStateException("Elasticsearch rebuild is already running");
        }
        try {
            String previous = alias.activeIndex();
            if (previous == null) {
                alias.initialize();
                previous = alias.activeIndex();
            }
            if (previous.equals(retainedIndex)) {
                throw new IllegalArgumentException("Target index is already active");
            }
            String target = retainedIndex == null ? alias.newIndexName() : retainedIndex;
            if (retainedIndex == null) {
                alias.createIndex(target);
            }
            // Queries keep using the old alias while the shadow index is populated.
            writePages(target);
            String oldIndex = previous;
            ReconciliationReport[] verified = new ReconciliationReport[1];
            writeGate.withCutover(() -> {
                if (!oldIndex.equals(alias.activeIndex())) {
                    throw new IllegalStateException("Search alias changed during rebuild");
                }
                // CDC waits while the final MySQL catch-up and atomic alias swap run.
                writePages(target);
                operations.indexOps(IndexCoordinates.of(target)).refresh();
                removeOrphans(target);
                operations.indexOps(IndexCoordinates.of(target)).refresh();
                verified[0] = reconcileIndex(target);
                if (!verified[0].consistent()) {
                    throw new IllegalStateException("Elasticsearch rebuild verification failed: " + verified[0]);
                }
                alias.switchTo(oldIndex, target);
            });
            return new RebuildResult(verified[0].mysqlCount(), verified[0].elasticsearchCount(),
                    verified[0].checkedAt(), true, oldIndex, target);
        } finally {
            maintenanceLock.unlock();
        }
    }

    public ReconciliationReport reconcile() {
        String active = alias.activeIndex();
        if (active == null) {
            throw new IllegalStateException("Video search alias is missing");
        }
        return reconcileIndex(active);
    }

    private void writePages(String target) {
        long cursor = 0;
        IndexCoordinates coordinates = IndexCoordinates.of(target);
        while (true) {
            List<VideoDocument> page = source.page(cursor, PAGE_SIZE);
            if (page.isEmpty()) {
                return;
            }
            operations.save(page, coordinates);
            cursor = page.getLast().getId();
        }
    }

    private void removeOrphans(String target) {
        List<Long> ids = new ArrayList<>(PAGE_SIZE);
        try (SearchHitsIterator<VideoDocument> hits = stream(target)) {
            while (hits.hasNext()) {
                ids.add(hits.next().getContent().getId());
                if (ids.size() == PAGE_SIZE) {
                    deleteAbsent(target, ids);
                    ids.clear();
                }
            }
        }
        if (!ids.isEmpty()) {
            deleteAbsent(target, ids);
        }
    }

    private void deleteAbsent(String target, List<Long> ids) {
        Set<Long> present = new HashSet<>(source.publishedIds(ids));
        for (Long id : ids) {
            if (!present.contains(id)) {
                operations.delete(id.toString(), IndexCoordinates.of(target));
            }
        }
    }

    private ReconciliationReport reconcileIndex(String index) {
        long mysqlCount = 0;
        long cursor = 0;
        List<Long> missing = new ArrayList<>();
        List<Long> mismatched = new ArrayList<>();
        IndexCoordinates coordinates = IndexCoordinates.of(index);
        while (true) {
            List<VideoDocument> page = source.page(cursor, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            mysqlCount += page.size();
            cursor = page.getLast().getId();
            Query ids = operations.idsQuery(page.stream().map(d -> d.getId().toString()).toList());
            List<MultiGetItem<VideoDocument>> found = operations.multiGet(ids, VideoDocument.class, coordinates);
            Map<Long, VideoDocument> indexed = new HashMap<>();
            for (MultiGetItem<VideoDocument> item : found) {
                if (item.isFailed()) {
                    throw new IllegalStateException("Elasticsearch multi-get failed: " + item.getFailure());
                }
                if (item.hasItem()) {
                    indexed.put(item.getItem().getId(), item.getItem());
                }
            }
            for (VideoDocument document : page) {
                VideoDocument actual = indexed.get(document.getId());
                if (actual == null) {
                    sample(missing, document.getId());
                } else if (!same(document, actual)) {
                    sample(mismatched, document.getId());
                }
            }
        }
        long esCount = operations.count(operations.matchAllQuery(), VideoDocument.class, coordinates);
        List<Long> orphan = new ArrayList<>();
        if (esCount != mysqlCount || !missing.isEmpty()) {
            List<Long> ids = new ArrayList<>(PAGE_SIZE);
            try (SearchHitsIterator<VideoDocument> hits = stream(index)) {
                while (hits.hasNext() && orphan.size() < SAMPLE_LIMIT) {
                    ids.add(hits.next().getContent().getId());
                    if (ids.size() == PAGE_SIZE) {
                        sampleOrphans(ids, orphan);
                        ids.clear();
                    }
                }
            }
            if (!ids.isEmpty()) {
                sampleOrphans(ids, orphan);
            }
        }
        boolean consistent = mysqlCount == esCount && missing.isEmpty()
                && orphan.isEmpty() && mismatched.isEmpty();
        return new ReconciliationReport(mysqlCount, esCount, consistent,
                missing, orphan, mismatched, LocalDateTime.now());
    }

    private SearchHitsIterator<VideoDocument> stream(String index) {
        Query query = operations.matchAllQuery();
        query.setPageable(PageRequest.of(0, PAGE_SIZE));
        return operations.searchForStream(query, VideoDocument.class, IndexCoordinates.of(index));
    }

    private void sampleOrphans(List<Long> ids, List<Long> samples) {
        Set<Long> present = new HashSet<>(source.publishedIds(ids));
        for (Long id : ids) {
            if (!present.contains(id)) {
                sample(samples, id);
            }
        }
    }

    private void sample(List<Long> samples, Long id) {
        if (samples.size() < SAMPLE_LIMIT) {
            samples.add(id);
        }
    }

    private boolean same(VideoDocument left, VideoDocument right) {
        return Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getTags(), right.getTags())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getUserId(), right.getUserId())
                && Objects.equals(left.getStatus(), right.getStatus())
                && Objects.equals(left.getViewCount(), right.getViewCount())
                && Objects.equals(left.getLikeCount(), right.getLikeCount())
                && Objects.equals(left.getCreateTime(), right.getCreateTime());
    }

    public record RebuildResult(long mysqlCount, long indexedCount, LocalDateTime completedAt,
                                boolean verified, String previousIndex, String activeIndex) {
    }

    public record ReconciliationReport(long mysqlCount, long elasticsearchCount,
                                       boolean consistent, List<Long> missingIds,
                                       List<Long> orphanIds, List<Long> mismatchedIds,
                                       LocalDateTime checkedAt) {
    }
}
