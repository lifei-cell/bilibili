package com.gary.bilibili.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gary.bilibili.search.constant.SearchConstant;
import com.gary.bilibili.search.document.VideoDocument;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VideoSearchIndexInitializer {

    private final ElasticsearchOperations operations;
    private final ElasticsearchClient client;

    public VideoSearchIndexInitializer(ElasticsearchOperations operations, ElasticsearchClient client) {
        this.operations = operations;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (aliasExists()) {
            return;
        }
        String initial = operations.indexOps(IndexCoordinates.of("video_index")).exists()
                ? "video_index" : "video_index_vinitial";
        if (!operations.indexOps(IndexCoordinates.of(initial)).exists()) {
            try {
                var mapping = operations.indexOps(VideoDocument.class);
                operations.indexOps(IndexCoordinates.of(initial))
                        .create(mapping.createSettings(), mapping.createMapping());
            } catch (RuntimeException exception) {
                if (!operations.indexOps(IndexCoordinates.of(initial)).exists()) {
                    throw exception;
                }
            }
        }
        if (!aliasExists()) {
            var add = AliasActionParameters.builder().withIndices(initial)
                    .withAliases(SearchConstant.VIDEO_INDEX).build();
            if (!operations.indexOps(VideoDocument.class)
                    .alias(new AliasActions(new AliasAction.Add(add)))) {
                throw new IllegalStateException("Cannot initialize video search alias");
            }
        }
    }

    private boolean aliasExists() {
        try {
            return client.indices().existsAlias(a -> a.name(SearchConstant.VIDEO_INDEX)).value();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect video search alias", exception);
        }
    }
}
