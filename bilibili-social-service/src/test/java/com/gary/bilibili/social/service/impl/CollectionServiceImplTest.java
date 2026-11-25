package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.analytics.CreatorAnalyticsDirtyPublisher;
import com.gary.bilibili.social.dto.CollectionDTO;
import com.gary.bilibili.social.dto.CollectionFolderCreateDTO;
import com.gary.bilibili.social.entity.CollectionFolder;
import com.gary.bilibili.social.mapper.CollectionFolderMapper;
import com.gary.bilibili.social.mapper.CollectionMapper;
import com.gary.bilibili.social.model.CollectionPage;
import com.gary.bilibili.social.model.CollectionRow;
import com.gary.bilibili.common.social.VideoInteractionStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionServiceImplTest {

    private CollectionMapper collectionMapper;
    private CollectionFolderMapper collectionFolderMapper;
    private VideoInteractionStats interactionStats;
    private CreatorAnalyticsDirtyPublisher analyticsDirtyPublisher;
    private CollectionServiceImpl collectionService;

    @BeforeEach
    void setUp() {
        collectionMapper = mock(CollectionMapper.class);
        collectionFolderMapper = mock(CollectionFolderMapper.class);
        interactionStats = mock(VideoInteractionStats.class);
        analyticsDirtyPublisher = mock(CreatorAnalyticsDirtyPublisher.class);
        collectionService = new CollectionServiceImpl(
                collectionMapper, collectionFolderMapper, interactionStats,
                analyticsDirtyPublisher);
    }

    @Test
    void shouldCollectIntoDefaultFolderAndIncrementCount() {
        CollectionDTO request = buildRequest(10001L, 0L);
        when(collectionMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(collectionMapper.activate(1L, 10001L, 0L)).thenReturn(0);
        when(collectionMapper.insertActive(1L, 10001L, 0L)).thenReturn(1);
        when(collectionMapper.selectCreatedDate(1L, 10001L, 0L))
                .thenReturn(LocalDate.of(2026, 9, 22));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(collectionService.collect(request).getCollected()).isTrue();
        }

        verify(interactionStats).changeCollections(10001L, 1);
        verify(analyticsDirtyPublisher).publish(
                10001L, LocalDate.of(2026, 9, 22));
        verify(collectionFolderMapper, never()).incrementVideoCount(0L, 1L, 1);
    }

    @Test
    void shouldNotIncrementCountForRepeatedCollection() {
        CollectionDTO request = buildRequest(10001L, 0L);
        when(collectionMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(collectionMapper.activate(1L, 10001L, 0L)).thenReturn(0);
        when(collectionMapper.insertActive(1L, 10001L, 0L)).thenReturn(0);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            collectionService.collect(request);
        }

        verify(interactionStats, never()).changeCollections(10001L, 1);
        verify(analyticsDirtyPublisher, never()).publish(anyLong(), any());
    }

    @Test
    void shouldCreateFolderWithManualFields() {
        CollectionFolderCreateDTO request = new CollectionFolderCreateDTO();
        request.setName(" 学习资料 ");
        request.setIsPublic(true);
        request.setDescription("后端开发相关视频");
        doAnswer(invocation -> {
            CollectionFolder folder = invocation.getArgument(0);
            folder.setId(10L);
            return 1;
        }).when(collectionFolderMapper).insert(any(CollectionFolder.class));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(collectionService.createFolder(request))
                    .satisfies(folder -> {
                        assertThat(folder.getId()).isEqualTo(10L);
                        assertThat(folder.getName()).isEqualTo("学习资料");
                        assertThat(folder.getIsPublic()).isTrue();
                    });
        }
    }

    @Test
    void shouldReturnDefaultFolderBeforeCustomFolders() {
        CollectionFolder folder = new CollectionFolder();
        folder.setId(10L);
        folder.setName("学习资料");
        folder.setIsPublic(0);
        folder.setVideoCount(2);
        when(collectionFolderMapper.countDefaultFolderVideos(1L)).thenReturn(3L);
        when(collectionFolderMapper.selectList(any())).thenReturn(List.of(folder));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(collectionService.getFolders())
                    .extracting(item -> item.getId())
                    .containsExactly(0L, 10L);
        }
    }

    @Test
    void shouldReturnPagedCollectedVideos() {
        CollectionRow row = new CollectionRow();
        row.setId(20L);
        row.setVideoId(10001L);
        row.setTitle("SpringBoot 视频平台实战");
        when(collectionMapper.selectCollectionList(1L, null, 0L, 20))
                .thenReturn(List.of(row));
        when(collectionMapper.countCollectionList(1L, null)).thenReturn(1L);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            CollectionPage result = collectionService.getList(null, 1, 20);
            assertThat(result.getTotal()).isEqualTo(1L);
            assertThat(result.getRecords()).singleElement()
                    .satisfies(item -> assertThat(item.getVideoId()).isEqualTo(10001L));
        }
    }

    private CollectionDTO buildRequest(Long videoId, Long folderId) {
        CollectionDTO request = new CollectionDTO();
        request.setVideoId(videoId);
        request.setFolderId(folderId);
        return request;
    }
}
