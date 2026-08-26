package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.dto.AuditActionDTO;
import com.gary.bilibili.video.dto.ContentReportDTO;
import com.gary.bilibili.video.dto.ReportResolveDTO;
import com.gary.bilibili.video.entity.ContentAuditLog;
import com.gary.bilibili.video.entity.ContentReport;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.mapper.ContentAuditLogMapper;
import com.gary.bilibili.video.mapper.ContentReportMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.model.AdminVideoRow;
import com.gary.bilibili.video.model.AdminReportRow;
import com.gary.bilibili.video.model.GovernancePage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ContentGovernanceService {
    private final VideoMapper videoMapper;
    private final ContentReportMapper reportMapper;
    private final ContentAuditLogMapper auditLogMapper;
    private final AdminAuthorizationService authorizationService;
    private final VideoBloomFilter videoBloomFilter;
    private final VideoListCache videoListCache;
    private final StringRedisTemplate redisTemplate;

    public ContentGovernanceService(VideoMapper videoMapper, ContentReportMapper reportMapper,
                                    ContentAuditLogMapper auditLogMapper,
                                    AdminAuthorizationService authorizationService,
                                    VideoBloomFilter videoBloomFilter, VideoListCache videoListCache,
                                    StringRedisTemplate redisTemplate) {
        this.videoMapper = videoMapper;
        this.reportMapper = reportMapper;
        this.auditLogMapper = auditLogMapper;
        this.authorizationService = authorizationService;
        this.videoBloomFilter = videoBloomFilter;
        this.videoListCache = videoListCache;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public long report(ContentReportDTO request) {
        long reporterId = StpUtil.getLoginIdAsLong();
        if (!targetExists(request.getTargetType(), request.getTargetId())) throw new BusinessException("举报对象不存在");
        ContentReport report = new ContentReport();
        report.setReporterId(reporterId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReasonCode(request.getReasonCode());
        report.setDescription(request.getDescription());
        report.setEvidenceUrl(request.getEvidenceUrl());
        report.setStatus(0);
        try { reportMapper.insert(report); }
        catch (DuplicateKeyException exception) { throw new BusinessException("你已举报过该内容"); }
        return report.getId();
    }

    public GovernancePage<AdminVideoRow> videos(Integer status, int page, int size) {
        authorizationService.requireAdmin();
        long offset = (long) (page - 1) * size;
        return new GovernancePage<>(videoMapper.selectAdminQueue(status, offset, size),
                videoMapper.countAdminQueue(status));
    }

    public GovernancePage<AdminReportRow> reports(Integer status, int page, int size) {
        authorizationService.requireAdmin();
        long offset = (long) (page - 1) * size;
        return new GovernancePage<>(reportMapper.selectQueue(status, offset, size), reportMapper.countQueue(status));
    }

    @Transactional(rollbackFor = Exception.class)
    public void auditVideo(long videoId, AuditActionDTO request) {
        long adminId = authorizationService.requireAdmin();
        moderateVideo(videoId, request.getAction(), request.getRemark(), adminId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolveReport(long reportId, ReportResolveDTO request) {
        long adminId = authorizationService.requireAdmin();
        ContentReport report = reportMapper.selectOne(new LambdaQueryWrapper<ContentReport>()
                .eq(ContentReport::getId, reportId).last("limit 1"));
        if (report == null || report.getStatus() > 1) throw new BusinessException("举报已处理或不存在");
        boolean upheld = "UPHOLD".equals(request.getAction());
        if (upheld) {
            if ("VIDEO".equals(report.getTargetType())) moderateVideo(report.getTargetId(), "OFFLINE", request.getRemark(), adminId);
            else if ("COMMENT".equals(report.getTargetType())) reportMapper.blockComment(report.getTargetId());
            else reportMapper.blockDanmu(report.getTargetId());
        }
        if (reportMapper.resolve(reportId, upheld ? 2 : 3, adminId, request.getRemark()) != 1)
            throw new BusinessException("举报状态已变化");
    }

    private void moderateVideo(long videoId, String action, String remark, long adminId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || video.getDeleted() == 1) throw new BusinessException("视频不存在");
        int targetStatus = switch (action) {
            case "APPROVE", "RESTORE" -> VideoConstant.STATUS_PUBLISHED;
            case "REJECT" -> VideoConstant.STATUS_REJECTED;
            case "OFFLINE" -> VideoConstant.STATUS_OFFLINE;
            default -> throw new BusinessException("审核动作非法");
        };
        if (targetStatus == VideoConstant.STATUS_PUBLISHED && !StringUtils.hasText(video.getPlayUrl()))
            throw new BusinessException("视频转码未完成");
        if (video.getStatus() == targetStatus) return;

        int updated = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, videoId).eq(Video::getStatus, video.getStatus()).eq(Video::getDeleted, 0)
                .set(Video::getStatus, targetStatus).set(Video::getAuditRemark, remark)
                .set(Video::getAuditBy, adminId).set(Video::getAuditTime, LocalDateTime.now()));
        if (updated != 1) throw new BusinessException("视频状态已变化，请刷新后重试");

        ContentAuditLog log = new ContentAuditLog();
        log.setTargetType("VIDEO"); log.setTargetId(videoId); log.setAction(action); log.setOperatorId(adminId);
        log.setPreviousStatus(video.getStatus()); log.setCurrentStatus(targetStatus); log.setRemark(remark);
        auditLogMapper.insert(log);
        if (targetStatus == VideoConstant.STATUS_PUBLISHED) videoBloomFilter.put(videoId);
        videoListCache.invalidate();
        redisTemplate.delete(VideoConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
    }

    private boolean targetExists(String type, long id) {
        return switch (type) {
            case "VIDEO" -> reportMapper.videoExists(id) > 0;
            case "COMMENT" -> reportMapper.commentExists(id) > 0;
            case "DANMU" -> reportMapper.danmuExists(id) > 0;
            default -> false;
        };
    }
}
