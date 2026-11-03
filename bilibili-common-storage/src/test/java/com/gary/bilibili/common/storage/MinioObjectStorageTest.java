package com.gary.bilibili.common.storage;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MinioObjectStorageTest {
    @Test
    void onlyMissingObjectResponsesAreConvertedToFalse() throws Exception {
        MinioClient client = mock(MinioClient.class);
        MinioObjectStorage storage = new MinioObjectStorage(client);
        ErrorResponse response = mock(ErrorResponse.class);
        ErrorResponseException error = mock(ErrorResponseException.class);
        when(error.errorResponse()).thenReturn(response);
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(error);
        when(response.code()).thenReturn("NoSuchKey");
        assertThat(storage.exists("videos", "source/test")).isFalse();
        when(response.code()).thenReturn("NoSuchObject");
        assertThat(storage.exists("videos", "source/test")).isFalse();
        when(response.code()).thenReturn("AccessDenied");
        assertThatThrownBy(() -> storage.exists("videos", "source/test")).isSameAs(error);
    }

    @Test
    void transportFailureIsNotReportedAsMissing() throws Exception {
        MinioClient client = mock(MinioClient.class);
        IOException offline = new IOException("offline");
        when(client.statObject(any(StatObjectArgs.class))).thenThrow(offline);
        assertThatThrownBy(() -> new MinioObjectStorage(client).exists("videos", "source/test"))
                .isSameAs(offline);
    }
}
