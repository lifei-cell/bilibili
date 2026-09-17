package com.gary.bilibili.canal.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.gary.bilibili.canal.constant.CanalConstant;
import com.gary.bilibili.canal.document.VideoDocument;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class VideoIndexAlias {

    private final ElasticsearchOperations operations;
    private final ElasticsearchClient client;

    public VideoIndexAlias(ElasticsearchOperations operations, ElasticsearchClient client) {
        this.operations = operations;
        this.client = client;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (activeIndex() != null) {
            return;
        }
        String initial = operations.indexOps(IndexCoordinates.of(CanalConstant.LEGACY_VIDEO_INDEX)).exists()
                ? CanalConstant.LEGACY_VIDEO_INDEX : CanalConstant.VIDEO_INDEX_PREFIX + "initial";
        if (!operations.indexOps(IndexCoordinates.of(initial)).exists()) {
            try {
                createIndex(initial);
            } catch (RuntimeException exception) {
                if (!operations.indexOps(IndexCoordinates.of(initial)).exists()) {
                    throw exception;
                }
            }
        }
        // A concurrent initializer may have attached the alias already.
        if (activeIndex() == null) {
            addAlias(initial);
        }
    }

    public String activeIndex() {
        try {
            if (!client.indices().existsAlias(a -> a.name(CanalConstant.VIDEO_INDEX)).value()) {
                return null;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect video search alias", exception);
        }
        Map<String, ?> aliases = operations.indexOps(VideoDocument.class)
                .getAliases(CanalConstant.VIDEO_INDEX);
        if (aliases.size() > 1) {
            throw new IllegalStateException("Video search alias must resolve to one index: " + aliases.keySet());
        }
        return aliases.keySet().stream().findFirst().orElse(null);
    }

    public String newIndexName() {
        return CanalConstant.VIDEO_INDEX_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    public void createIndex(String name) {
        IndexOperations mapping = operations.indexOps(VideoDocument.class);
        boolean created = operations.indexOps(IndexCoordinates.of(name))
                .create(mapping.createSettings(), mapping.createMapping());
        if (!created) {
            throw new IllegalStateException("Cannot create search index " + name);
        }
    }

    public void switchTo(String expected, String target) {
        if (!expected.equals(activeIndex())) {
            throw new IllegalStateException("Search alias changed during rebuild");
        }
        AliasActionParameters remove = AliasActionParameters.builder()
                .withIndices(expected).withAliases(CanalConstant.VIDEO_INDEX).build();
        AliasActionParameters add = AliasActionParameters.builder()
                .withIndices(target).withAliases(CanalConstant.VIDEO_INDEX).build();
        boolean updated = operations.indexOps(VideoDocument.class).alias(new AliasActions(
                new AliasAction.Remove(remove), new AliasAction.Add(add)));
        if (!updated || !target.equals(activeIndex())) {
            throw new IllegalStateException("Search alias cutover was not acknowledged");
        }
    }

    private void addAlias(String target) {
        AliasActionParameters add = AliasActionParameters.builder()
                .withIndices(target).withAliases(CanalConstant.VIDEO_INDEX).build();
        if (!operations.indexOps(VideoDocument.class).alias(
                new AliasActions(new AliasAction.Add(add)))) {
            throw new IllegalStateException("Cannot initialize video search alias");
        }
    }
}
