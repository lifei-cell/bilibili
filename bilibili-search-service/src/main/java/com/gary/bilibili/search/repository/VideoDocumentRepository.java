package com.gary.bilibili.search.repository;

import com.gary.bilibili.search.document.VideoDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface VideoDocumentRepository extends ElasticsearchRepository<VideoDocument, Long> {
}
