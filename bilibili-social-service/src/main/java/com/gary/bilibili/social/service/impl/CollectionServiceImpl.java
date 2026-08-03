package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.dto.CollectionDTO;
import com.gary.bilibili.social.dto.CollectionFolderCreateDTO;
import com.gary.bilibili.social.dto.CollectionFolderUpdateDTO;
import com.gary.bilibili.social.entity.CollectionFolder;
import com.gary.bilibili.social.mapper.CollectionFolderMapper;
import com.gary.bilibili.social.mapper.CollectionMapper;
import com.gary.bilibili.social.model.CollectionPage;
import com.gary.bilibili.social.model.CollectionRow;
import com.gary.bilibili.social.service.CollectionService;
import com.gary.bilibili.social.service.SocialStatsService;
import com.gary.bilibili.social.vo.CollectionFolderVO;
import com.gary.bilibili.social.vo.CollectionListVO;
import com.gary.bilibili.social.vo.CollectionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class CollectionServiceImpl implements CollectionService {

    private final CollectionMapper collectionMapper;
    private final CollectionFolderMapper collectionFolderMapper;
    private final SocialStatsService socialStatsService;

    public CollectionServiceImpl(CollectionMapper collectionMapper,
                                 CollectionFolderMapper collectionFolderMapper,
                                 SocialStatsService socialStatsService) {
        this.collectionMapper = collectionMapper;
        this.collectionFolderMapper = collectionFolderMapper;
        this.socialStatsService = socialStatsService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionVO collect(CollectionDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        long folderId = normalizeFolderId(request.getFolderId());
        if (nullToZero(collectionMapper.countPublishedVideo(request.getVideoId())) == 0) {
            throw new BusinessException("视频不存在");
        }
        assertOwnedFolder(userId, folderId);

        int changed = collectionMapper.activate(userId, request.getVideoId(), folderId);
        if (changed == 0) {
            changed = collectionMapper.insertActive(userId, request.getVideoId(), folderId);
        }
        if (changed > 0) {
            updateCollectionCount(userId, request.getVideoId(), folderId, 1);
        }
        return buildResult(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionVO uncollect(CollectionDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        long folderId = normalizeFolderId(request.getFolderId());
        int changed = collectionMapper.deactivate(userId, request.getVideoId(), folderId);
        if (changed > 0) {
            updateCollectionCount(userId, request.getVideoId(), folderId, -1);
        }
        return buildResult(false);
    }

    @Override
    public CollectionPage getList(Long folderId, Integer page, Integer size) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (folderId != null) {
            assertOwnedFolder(userId, folderId);
        }
        long offset = (long) (page - 1) * size;
        List<CollectionRow> rows = collectionMapper.selectCollectionList(
                userId, folderId, offset, size);
        List<CollectionListVO> records = new ArrayList<>(rows.size());
        for (CollectionRow row : rows) {
            CollectionListVO item = new CollectionListVO();
            item.setId(row.getId());
            item.setVideoId(row.getVideoId());
            item.setFolderId(row.getFolderId());
            item.setTitle(row.getTitle());
            item.setCoverUrl(row.getCoverUrl());
            item.setDuration(row.getDuration());
            item.setAuthorName(row.getAuthorName());
            item.setViewCount(nullToZero(row.getViewCount()));
            item.setCreateTime(row.getCreateTime());
            records.add(item);
        }
        CollectionPage result = new CollectionPage();
        result.setRecords(records);
        result.setTotal(nullToZero(collectionMapper.countCollectionList(userId, folderId)));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionFolderVO createFolder(CollectionFolderCreateDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("收藏夹名称不能为空");
        }
        CollectionFolder folder = new CollectionFolder();
        folder.setUserId(userId);
        folder.setName(request.getName().trim());
        folder.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()) ? 1 : 0);
        folder.setDescription(request.getDescription());
        folder.setVideoCount(0);
        folder.setDeleted(0);
        collectionFolderMapper.insert(folder);
        return toFolderVO(folder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFolder(Long folderId, CollectionFolderUpdateDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        CollectionFolder folder = loadOwnedFolder(userId, folderId);
        CollectionFolder update = new CollectionFolder();
        update.setId(folder.getId());
        boolean changed = false;
        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName())) {
                throw new BusinessException("收藏夹名称不能为空");
            }
            update.setName(request.getName().trim());
            changed = true;
        }
        if (request.getIsPublic() != null) {
            update.setIsPublic(Boolean.TRUE.equals(request.getIsPublic()) ? 1 : 0);
            changed = true;
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
            changed = true;
        }
        if (!changed) {
            throw new BusinessException("请求参数错误");
        }
        collectionFolderMapper.updateById(update);
    }

    @Override
    public List<CollectionFolderVO> getFolders() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<CollectionFolderVO> result = new ArrayList<>();
        CollectionFolderVO defaultFolder = new CollectionFolderVO();
        defaultFolder.setId(SocialConstant.DEFAULT_FOLDER_ID);
        defaultFolder.setName("默认收藏夹");
        defaultFolder.setIsPublic(false);
        defaultFolder.setVideoCount(toInteger(
                collectionFolderMapper.countDefaultFolderVideos(userId)));
        result.add(defaultFolder);

        List<CollectionFolder> folders = collectionFolderMapper.selectList(
                new LambdaQueryWrapper<CollectionFolder>()
                        .eq(CollectionFolder::getUserId, userId)
                        .eq(CollectionFolder::getDeleted, 0)
                        .orderByDesc(CollectionFolder::getCreateTime)
                        .orderByDesc(CollectionFolder::getId));
        for (CollectionFolder folder : folders) {
            result.add(toFolderVO(folder));
        }
        return result;
    }

    private void updateCollectionCount(Long userId, Long videoId, long folderId, int delta) {
        if (folderId != SocialConstant.DEFAULT_FOLDER_ID) {
            collectionFolderMapper.incrementVideoCount(folderId, userId, delta);
        }
        socialStatsService.increment(videoId, SocialConstant.COLLECT_COUNT_FIELD, delta);
    }

    private void assertOwnedFolder(Long userId, long folderId) {
        if (folderId == SocialConstant.DEFAULT_FOLDER_ID) {
            return;
        }
        loadOwnedFolder(userId, folderId);
    }

    private CollectionFolder loadOwnedFolder(Long userId, Long folderId) {
        CollectionFolder folder = collectionFolderMapper.selectOne(
                new LambdaQueryWrapper<CollectionFolder>()
                        .eq(CollectionFolder::getId, folderId)
                        .eq(CollectionFolder::getUserId, userId)
                        .eq(CollectionFolder::getDeleted, 0));
        if (folder == null) {
            throw new BusinessException("收藏夹不存在或无权访问");
        }
        return folder;
    }

    private CollectionFolderVO toFolderVO(CollectionFolder folder) {
        CollectionFolderVO result = new CollectionFolderVO();
        result.setId(folder.getId());
        result.setName(folder.getName());
        result.setIsPublic(Integer.valueOf(1).equals(folder.getIsPublic()));
        result.setDescription(folder.getDescription());
        result.setCoverUrl(folder.getCoverUrl());
        result.setVideoCount(folder.getVideoCount() == null ? 0 : folder.getVideoCount());
        return result;
    }

    private CollectionVO buildResult(boolean collected) {
        CollectionVO result = new CollectionVO();
        result.setCollected(collected);
        return result;
    }

    private long normalizeFolderId(Long folderId) {
        return folderId == null ? SocialConstant.DEFAULT_FOLDER_ID : folderId;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private int toInteger(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}
