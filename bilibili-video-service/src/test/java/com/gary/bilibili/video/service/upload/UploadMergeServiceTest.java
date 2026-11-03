package com.gary.bilibili.video.service.upload;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.dto.UploadMergeDTO;
import com.gary.bilibili.video.dto.FileMergeDTO;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadMergeServiceTest {
    private final UploadSessionManager sessions = mock(UploadSessionManager.class);
    private final UploadChunkRepository chunks = mock(UploadChunkRepository.class);
    private final UploadStorage storage = mock(UploadStorage.class);
    private final UploadTranscodeTasks transcode = mock(UploadTranscodeTasks.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final UploadTask task = new UploadTask();
    private final UploadMergeDTO request = new UploadMergeDTO();
    private UploadMergeService merger;

    @BeforeEach
    void setup() {
        task.setUploadId("up_test"); task.setUserId(1L); task.setFileMd5("hash");
        task.setFileName("test.mp4"); task.setTotalChunks(1);
        request.setUploadId("up_test"); request.setFileMd5("hash");
        request.setFileName("test.mp4"); request.setTotalChunks(1);
        when(sessions.loadTask("up_test")).thenReturn(task);
        when(sessions.acquireMergeLock("up_test")).thenReturn("token");
        when(chunks.loadAndValidateChunks(task)).thenReturn(List.of());
        when(storage.buildSourceObjectName(task)).thenReturn("source/hash.mp4");
        when(storage.sourceUrl("source/hash.mp4")).thenReturn("http://storage/videos/source/hash.mp4");
        when(transactions.getTransaction(any())).thenReturn(transaction);
        ProxyFactory proxy = new ProxyFactory(new UploadMergeService(sessions,
                new UploadChunkValidator(), chunks, storage, transcode));
        proxy.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(interceptor);
        merger = (UploadMergeService) proxy.getProxy();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void commitsHandoffAndReleasesOwnedLock(boolean legacy) {
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThat(mergeThroughFacade(legacy)).isEqualTo("http://storage/videos/source/hash.mp4");
        }
        verify(transcode).saveTranscodeTask(task, "http://storage/videos/source/hash.mp4", "source/hash.mp4");
        verify(chunks).markChunksCompleted(task);
        verify(sessions).releaseLock("upload:merge:up_test", "token");
        verify(transactions).commit(transaction);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackHandoffFailureWithoutCompletingChunks(boolean legacy) {
        doThrow(new BusinessException("database unavailable")).when(transcode).saveTranscodeTask(any(), anyString(), anyString());
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThatThrownBy(() -> mergeThroughFacade(legacy)).hasMessage("database unavailable");
        }
        verify(transactions).rollback(transaction);
        verify(chunks, never()).markChunksCompleted(any());
        verify(sessions).releaseLock("upload:merge:up_test", "token");
    }

    @Test
    void refusesIncompleteChunksBeforeStorageAndAlwaysReleasesLock() {
        when(chunks.loadAndValidateChunks(task)).thenThrow(new BusinessException("分片未全部上传"));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThatThrownBy(() -> merger.merge(request)).hasMessage("分片未全部上传");
        }
        verifyNoInteractions(storage, transcode);
        verify(transactions).rollback(transaction);
        verify(sessions).releaseLock("upload:merge:up_test", "token");
    }

    private String mergeThroughFacade(boolean legacy) {
        UploadServiceImpl facade = new UploadServiceImpl(mock(VideoMapper.class), sessions,
                new UploadChunkValidator(), chunks, storage, merger, transcode);
        if (!legacy) return facade.merge(request).getSourceUrl();
        when(sessions.findByFile(1L, "hash")).thenReturn(task);
        FileMergeDTO oldRequest = new FileMergeDTO();
        oldRequest.setMd5("hash"); oldRequest.setFileName("test.mp4"); oldRequest.setTotalChunks(1);
        return facade.mergeFile(oldRequest).getVideoUrl();
    }
}
