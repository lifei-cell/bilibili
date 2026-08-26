package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.DirectUploadCompleteDTO;
import com.gary.bilibili.video.dto.DirectUploadInitDTO;
import com.gary.bilibili.video.entity.DirectUploadSession;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.DirectUploadSessionMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.vo.DirectUploadInitVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DirectUploadService {
    private static final int EXPIRY_MINUTES = 15;

    private final MinioClient originClient;
    private final MinioClient publicClient;
    private final DirectUploadSessionMapper sessionMapper;
    private final VideoTranscodeTaskMapper taskMapper;
    private final String bucket;
    private final String publicEndpoint;

    public DirectUploadService(MinioClient originClient,
                               @Qualifier("publicMinioClient") MinioClient publicClient,
                               DirectUploadSessionMapper sessionMapper,
                               VideoTranscodeTaskMapper taskMapper,
                               @Value("${minio.video-bucket}") String bucket,
                               @Value("${minio.public-endpoint}") String publicEndpoint) {
        this.originClient = originClient;
        this.publicClient = publicClient;
        this.sessionMapper = sessionMapper;
        this.taskMapper = taskMapper;
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint.replaceAll("/+$", "");
    }

    public DirectUploadInitVO initiate(DirectUploadInitDTO request) {
        long userId = StpUtil.getLoginIdAsLong();
        String uploadId = "direct_" + UUID.randomUUID().toString().replace("-", "");
        String extension = safeExtension(request.getFileName());
        String objectName = "source/direct/" + userId + "/" + uploadId + extension;
        try {
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(objectName)
                    .expiry(EXPIRY_MINUTES, TimeUnit.MINUTES).build());
            DirectUploadSession session = new DirectUploadSession();
            session.setUploadId(uploadId);
            session.setUserId(userId);
            session.setFileMd5(request.getFileMd5().toLowerCase(Locale.ROOT));
            session.setFileName(request.getFileName());
            session.setContentType(request.getContentType());
            session.setFileSize(request.getFileSize());
            session.setObjectName(objectName);
            session.setStatus(0);
            session.setExpireTime(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES));
            sessionMapper.insert(session);
            DirectUploadInitVO result = new DirectUploadInitVO();
            result.setUploadId(uploadId);
            result.setUploadUrl(url);
            result.setExpiresIn(EXPIRY_MINUTES * 60);
            result.setContentType(request.getContentType());
            return result;
        } catch (Exception exception) {
            throw new BusinessException("创建直传凭证失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadMergeVO complete(DirectUploadCompleteDTO request) {
        long userId = StpUtil.getLoginIdAsLong();
        DirectUploadSession session = sessionMapper.selectByUploadId(request.getUploadId());
        if (session == null || session.getUserId() != userId || session.getStatus() != 0
                || session.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("直传会话无效或已过期");
        }
        try {
            StatObjectResponse stat = originClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(session.getObjectName()).build());
            if (stat.size() != session.getFileSize()) throw new BusinessException("直传文件大小校验失败");
            if (stat.etag() != null && !stat.etag().equalsIgnoreCase(session.getFileMd5()))
                throw new BusinessException("直传文件 MD5 校验失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("直传文件不存在");
        }
        if (sessionMapper.markCompleted(session.getUploadId(), userId) != 1)
            throw new BusinessException("直传会话状态已变化");

        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setTaskId(session.getUploadId());
        task.setUserId(userId);
        task.setFileMd5(session.getFileMd5());
        task.setFileName(session.getFileName());
        task.setFileSize(session.getFileSize());
        task.setSourceObjectName(session.getObjectName());
        task.setSourceUrl(publicEndpoint + "/" + bucket + "/" + session.getObjectName());
        task.setStatus(UploadConstant.TRANSCODE_STATUS_PENDING);
        task.setRetryCount(0);
        taskMapper.insert(task);

        UploadMergeVO result = new UploadMergeVO();
        result.setSourceUrl(task.getSourceUrl());
        result.setFileMd5(task.getFileMd5());
        result.setTranscodeTaskId(task.getTaskId());
        result.setTranscodeStatus(UploadConstant.TRANSCODE_STATE_WAITING);
        return result;
    }

    private String safeExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) return "";
        String extension = fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? "." + extension : "";
    }
}
