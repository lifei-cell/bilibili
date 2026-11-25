package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.dto.VideoResubmitDTO;
import com.gary.bilibili.video.entity.ContentAuditLog;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.mapper.ContentAuditLogMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.model.GovernancePage;
import com.gary.bilibili.video.model.VideoTags;
import com.gary.bilibili.video.vo.CreatorSubmissionVO;
import com.gary.bilibili.video.vo.VideoPublishVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Service
public class CreatorSubmissionService {

    private final VideoMapper videoMapper;
    private final ContentRiskService riskService;
    private final ContentAuditLogMapper auditLogMapper;

    public CreatorSubmissionService(VideoMapper videoMapper, ContentRiskService riskService,
                                    ContentAuditLogMapper auditLogMapper) {
        this.videoMapper = videoMapper;
        this.riskService = riskService;
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(readOnly = true)
    public GovernancePage<CreatorSubmissionVO> list(Integer status, int page, int size) {
        long userId = StpUtil.getLoginIdAsLong();
        long offset = (long) (page - 1) * size;
        List<CreatorSubmissionVO> records = videoMapper.selectCreatorSubmissions(userId, status, offset, size)
                .stream().map(video -> new CreatorSubmissionVO(video.getId(), video.getTitle(),
                        video.getDescription(), video.getCoverUrl(), VideoTags.decode(video.getTags()),
                        video.getStatus(), video.getAuditRemark(), video.getUpdateTime())).toList();
        long total = videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId).eq(Video::getDeleted, 0)
                .eq(status != null, Video::getStatus, status));
        return new GovernancePage<>(records, total);
    }

    @Transactional(rollbackFor = Exception.class)
    public VideoPublishVO resubmit(long videoId, VideoResubmitDTO request) {
        long userId = StpUtil.getLoginIdAsLong();
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId).eq(Video::getUserId, userId).eq(Video::getDeleted, 0)
                .last("FOR UPDATE"));
        if (video == null) {
            throw new BusinessException("投稿不存在");
        }
        if (video.getStatus() != VideoConstant.STATUS_REJECTED) {
            throw new BusinessException("只有已驳回的投稿可以重新提交");
        }
        if (!StringUtils.hasText(video.getPlayUrl())) {
            throw new BusinessException("视频转码未完成");
        }

        String title = request.title().trim();
        String description = request.description();
        String tags = VideoTags.encode(request.tags());
        if (description.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new BusinessException("作品简介过长");
        }
        if (title.equals(video.getTitle()) && description.equals(Objects.toString(video.getDescription(), ""))
                && tags.equals(Objects.toString(video.getTags(), ""))) {
            throw new BusinessException("请先修改作品信息");
        }

        ContentRiskService.RiskDecision risk = riskService.evaluate(userId, title, description, request.tags());
        int nextStatus = risk.rejected() ? VideoConstant.STATUS_REJECTED : VideoConstant.STATUS_AUDITING;
        int changed = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId).eq(Video::getUserId, userId).eq(Video::getDeleted, 0)
                .eq(Video::getStatus, VideoConstant.STATUS_REJECTED)
                .set(Video::getTitle, title).set(Video::getDescription, description).set(Video::getTags, tags)
                .set(Video::getStatus, nextStatus).set(Video::getRiskLevel, risk.level())
                .set(Video::getAuditRemark, risk.rejectionRemark())
                .set(Video::getAuditBy, null).set(Video::getAuditTime, null));
        if (changed != 1) {
            throw new BusinessException("投稿状态已变化，请刷新后重试");
        }
        riskService.record(userId, videoId, risk);
        recordResubmission(userId, videoId, nextStatus, risk.rejectionRemark());

        VideoPublishVO result = new VideoPublishVO();
        result.setVideoId(videoId);
        result.setStatus(nextStatus);
        return result;
    }

    private void recordResubmission(long userId, long videoId, int status, String remark) {
        ContentAuditLog audit = new ContentAuditLog();
        audit.setTargetType("VIDEO");
        audit.setTargetId(videoId);
        audit.setAction("RESUBMIT");
        audit.setOperatorId(userId);
        audit.setPreviousStatus(VideoConstant.STATUS_REJECTED);
        audit.setCurrentStatus(status);
        audit.setRemark(remark);
        auditLogMapper.insert(audit);
    }
}
