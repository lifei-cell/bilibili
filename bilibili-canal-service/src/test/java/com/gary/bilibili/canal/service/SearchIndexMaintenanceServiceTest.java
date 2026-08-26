package com.gary.bilibili.canal.service;

import com.gary.bilibili.canal.document.VideoDocument;
import com.gary.bilibili.canal.repository.VideoDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexMaintenanceServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRebuildAndVerifyAgainstMysqlSourceOfTruth() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VideoDocumentRepository repository = mock(VideoDocumentRepository.class);
        VideoDocument document = document(1001L, "Reliable Outbox");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(document));
        when(repository.findAll()).thenReturn(List.of(document));
        SearchIndexMaintenanceService service =
                new SearchIndexMaintenanceService(jdbcTemplate, repository);

        SearchIndexMaintenanceService.RebuildResult result = service.rebuild();

        assertThat(result.verified()).isTrue();
        assertThat(result.mysqlCount()).isEqualTo(1);
        verify(repository).deleteAll();
        verify(repository).saveAll(any(Iterable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportMissingAndOrphanDocuments() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VideoDocumentRepository repository = mock(VideoDocumentRepository.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(document(1001L, "MySQL title")));
        when(repository.findAll()).thenReturn(List.of(document(2002L, "Orphan")));
        SearchIndexMaintenanceService service =
                new SearchIndexMaintenanceService(jdbcTemplate, repository);

        SearchIndexMaintenanceService.ReconciliationReport report = service.reconcile();

        assertThat(report.consistent()).isFalse();
        assertThat(report.missingIds()).containsExactly(1001L);
        assertThat(report.orphanIds()).containsExactly(2002L);
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
