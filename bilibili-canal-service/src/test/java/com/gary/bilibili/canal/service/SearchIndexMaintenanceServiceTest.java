package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.document.VideoDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.MultiGetItem;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexMaintenanceServiceTest {

    private PublishedVideoSource source;
    private ElasticsearchOperations operations;
    private VideoIndexAlias alias;
    private SearchIndexMaintenanceService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        source = mock(PublishedVideoSource.class);
        operations = mock(ElasticsearchOperations.class);
        alias = mock(VideoIndexAlias.class);
        service = new SearchIndexMaintenanceService(source, operations, alias,
                new VideoIndexWriteGate());
        when(alias.activeIndex()).thenReturn("video_index");
        when(alias.newIndexName()).thenReturn("video_index_v1234567890abcdef1234567890abcdef");
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(mock(IndexOperations.class));
        when(operations.matchAllQuery()).thenReturn(mock(Query.class));
        when(operations.idsQuery(any())).thenReturn(mock(Query.class));
        SearchHitsIterator<VideoDocument> emptyHits = mock(SearchHitsIterator.class);
        when(operations.searchForStream(any(Query.class), eq(VideoDocument.class),
                any(IndexCoordinates.class))).thenReturn(emptyHits);
    }

    @Test
    void rebuildUsesPagesAndSwitchesOnlyAfterVerified() {
        VideoDocument document = document(1001L, "MySQL title");
        VideoDocument second = document(2002L, "Second page");
        when(source.page(0, 200)).thenReturn(List.of(document), List.of(document), List.of(document));
        when(source.page(1001, 200)).thenReturn(List.of(second), List.of(second), List.of(second));
        when(source.page(2002, 200)).thenReturn(List.of());
        when(operations.multiGet(any(Query.class), eq(VideoDocument.class),
                any(IndexCoordinates.class))).thenReturn(List.of(MultiGetItem.of(document, null)),
                List.of(MultiGetItem.of(second, null)));
        when(operations.count(any(Query.class), eq(VideoDocument.class),
                any(IndexCoordinates.class))).thenReturn(2L);

        SearchIndexMaintenanceService.RebuildResult result = service.rebuild();

        assertThat(result.verified()).isTrue();
        assertThat(result.previousIndex()).isEqualTo("video_index");
        assertThat(result.activeIndex()).startsWith("video_index_v");
        verify(source, org.mockito.Mockito.times(3)).page(0, 200);
        verify(source, org.mockito.Mockito.times(3)).page(1001, 200);
        verify(alias).switchTo("video_index", result.activeIndex());
    }

    @Test
    void failedVerificationLeavesOldAliasActive() {
        VideoDocument document = document(1001L, "MySQL title");
        when(source.page(0, 200)).thenReturn(List.of(document), List.of(document), List.of(document));
        when(source.page(1001, 200)).thenReturn(List.of());
        when(operations.multiGet(any(Query.class), eq(VideoDocument.class),
                any(IndexCoordinates.class))).thenReturn(List.of(MultiGetItem.of(null, null)));
        when(operations.count(any(Query.class), eq(VideoDocument.class),
                any(IndexCoordinates.class))).thenReturn(0L);

        assertThatThrownBy(service::rebuild).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verification failed");
        verify(alias, never()).switchTo(any(), any());
    }

    @Test
    void rollbackRejectsUnrelatedIndex() {
        assertThatThrownBy(() -> service.rollback("other_index"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(alias, never()).switchTo(any(), any());
    }

    private VideoDocument document(long id, String title) {
        VideoDocument document = new VideoDocument();
        document.setId(id);
        document.setTitle(title);
        document.setTags(List.of("reliability"));
        document.setUserId(1L);
        document.setStatus(1);
        document.setViewCount(0L);
        document.setLikeCount(0L);
        return document;
    }
}
