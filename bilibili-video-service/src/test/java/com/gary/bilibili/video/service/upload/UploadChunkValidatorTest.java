package com.gary.bilibili.video.service.upload;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.model.UploadTask;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class UploadChunkValidatorTest {
    private final UploadChunkValidator validator = new UploadChunkValidator();

    @Test
    void validatesLastChunkSizeAndActualHash() {
        UploadTask task = new UploadTask();
        task.setFileSize(UploadConstant.CHUNK_SIZE + 3);
        task.setTotalChunks(2);
        UploadChunkDTO request = new UploadChunkDTO();
        request.setChunkIndex(1);
        request.setChunkSize(3L);
        request.setChunkMd5("900150983CD24FB0D6963F7D28E17F72");
        request.setFile(new MockMultipartFile("file", new byte[]{97, 98, 99}));
        validator.validateChunk(request, task);
        validator.verifyChunkMd5(request);
        request.setChunkMd5("00000000000000000000000000000000");
        assertThatThrownBy(() -> validator.verifyChunkMd5(request)).hasMessage("分片校验失败");
        request.setChunkSize(4L);
        assertThatThrownBy(() -> validator.validateChunk(request, task)).hasMessage("分片大小错误");
        request.setChunkIndex(2);
        assertThatThrownBy(() -> validator.validateChunk(request, task)).hasMessage("分片序号非法");
    }

    @Test
    void rejectsAnotherUsersTaskBeforeProcessingContent() {
        UploadTask task = new UploadTask();
        task.setUserId(1L);
        assertThatThrownBy(() -> validator.validateTask(task, 2L, "unused", 1))
                .isInstanceOf(BusinessException.class).hasMessage("上传任务不存在");
        assertThatThrownBy(() -> validator.validateTask(null, 1L, "unused", 1))
                .hasMessage("上传任务不存在");
    }
}
