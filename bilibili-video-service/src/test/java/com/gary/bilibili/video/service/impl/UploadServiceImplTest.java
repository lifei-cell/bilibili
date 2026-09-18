package com.gary.bilibili.video.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadCheckDTO;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.FileChunkMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.vo.UploadCheckVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
import com.gary.bilibili.video.vo.UploadProgressVO;
import com.gary.bilibili.video.vo.VideoTranscodeStatusVO;
import io.minio.MinioClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UploadServiceImplTest {

    private FileChunkMapper fileChunkMapper;
    private VideoMapper videoMapper;
    private VideoTranscodeTaskMapper videoTranscodeTaskMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private UploadServiceImpl uploadService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "upload-test"),
                FileChunk.class);
        fileChunkMapper = mock(FileChunkMapper.class);
        videoMapper = mock(VideoMapper.class);
        videoTranscodeTaskMapper = mock(VideoTranscodeTaskMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        uploadService = new UploadServiceImpl(
                fileChunkMapper,
                videoMapper,
                videoTranscodeTaskMapper,
                mock(MinioClient.class),
                stringRedisTemplate,
                objectMapper,
                "http://minio:9000",
                "videos",
                "tmp");
    }

    @Test
    void shouldReturnPublishedVideoForInstantUpload() {
        Video video = new Video();
        video.setId(10001L);
        video.setSourceUrl("http://minio:9000/videos/source/demo.mp4");
        when(videoMapper.selectOne(any(Wrapper.class))).thenReturn(video);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            UploadCheckVO result = uploadService.check(buildCheckRequest());

            assertThat(result.getInstant()).isTrue();
            assertThat(result.getVideoId()).isEqualTo(10001L);
            assertThat(result.getUploadId()).isNull();
            assertThat(result.getUploadedChunks()).isEmpty();
        }
    }

    @Test
    void shouldRejectChunkCountThatDoesNotMatchFiveMegabyteStrategy() {
        UploadCheckDTO request = buildCheckRequest();
        request.setTotalChunks(2);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> uploadService.check(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("分片总数与文件大小不匹配");
        }
    }

    @Test
    void shouldTreatRepeatedChunkAsSuccessful() throws Exception {
        UploadTask task = buildTask();
        when(valueOperations.get(eq("upload:task:up_test")))
                .thenReturn(objectMapper.writeValueAsString(task));

        FileChunk existing = new FileChunk();
        existing.setUserId(1L);
        existing.setChunkMd5("79b281060d337b9b2b84ccf390adcf74");
        when(fileChunkMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(UploadConstant.CHUNK_SIZE);

        UploadChunkDTO request = new UploadChunkDTO();
        request.setUploadId("up_test");
        request.setFileMd5("79b281060d337b9b2b84ccf390adcf74");
        request.setChunkMd5("79b281060d337b9b2b84ccf390adcf74");
        request.setChunkIndex(0);
        request.setChunkSize(UploadConstant.CHUNK_SIZE);
        request.setTotalChunks(1);
        request.setFile(file);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            UploadChunkVO result = uploadService.uploadChunk(request);

            assertThat(result.getUploaded()).isTrue();
            assertThat(result.getChunkIndex()).isZero();
        }
    }

    @Test
    void shouldCalculateResumeProgressFromUploadedChunkIndexes() throws Exception {
        UploadTask task = buildTask();
        task.setFileSize(UploadConstant.CHUNK_SIZE * 4);
        task.setTotalChunks(4);
        when(valueOperations.get(eq("upload:task:up_test")))
                .thenReturn(objectMapper.writeValueAsString(task));

        FileChunk first = new FileChunk();
        first.setChunkIndex(0);
        FileChunk second = new FileChunk();
        second.setChunkIndex(1);
        when(fileChunkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            UploadProgressVO result = uploadService.getProgress("up_test");

            assertThat(result.getUploadedChunks()).containsExactly(0, 1);
            assertThat(result.getTotalChunks()).isEqualTo(4);
            assertThat(result.getPercent()).isEqualTo(50);
        }
    }

    @Test
    void shouldReturnOnlyTheCurrentUsersCompletedTranscodeTask() {
        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setTaskId("up_test");
        task.setUserId(1L);
        task.setStatus(3);
        task.setRetryCount(1);
        task.setRenditionStatus(4);
        task.setRenditionRetryCount(3);
        task.setRenditionErrorMessage("high batch timeout");
        task.setOutputUrl("https://minio.example.com/play/demo.mp4");
        when(videoTranscodeTaskMapper.selectByTaskId("up_test")).thenReturn(task);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            VideoTranscodeStatusVO result = uploadService.getTranscodeStatus("up_test");

            assertThat(result.getStatus()).isEqualTo("completed");
            assertThat(result.getOutputUrl()).isEqualTo("https://minio.example.com/play/demo.mp4");
            assertThat(result.getRetryCount()).isEqualTo(1);
            assertThat(result.getRenditionStatus()).isEqualTo("FAILED");
            assertThat(result.getRenditionRetryCount()).isEqualTo(3);
            assertThat(result.getRenditionErrorMessage()).isEqualTo("high batch timeout");
        }
    }

    private UploadCheckDTO buildCheckRequest() {
        UploadCheckDTO request = new UploadCheckDTO();
        request.setFileMd5("79b281060d337b9b2b84ccf390adcf74");
        request.setFileName("demo.mp4");
        request.setFileSize(UploadConstant.CHUNK_SIZE);
        request.setTotalChunks(1);
        return request;
    }

    private UploadTask buildTask() {
        UploadTask task = new UploadTask();
        task.setUploadId("up_test");
        task.setUserId(1L);
        task.setFileMd5("79b281060d337b9b2b84ccf390adcf74");
        task.setFileName("demo.mp4");
        task.setFileSize(UploadConstant.CHUNK_SIZE);
        task.setTotalChunks(1);
        return task;
    }
}
