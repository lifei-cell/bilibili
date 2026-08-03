package com.gary.bilibili.canal.repository;

import com.gary.bilibili.canal.document.VideoDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface VideoDocumentRepository extends ElasticsearchRepository<VideoDocument, Long> {
}
