package com.gary.bilibili.common.storage;

import java.io.InputStream;
import java.util.List;

/** Storage port uses only JDK types; the caller owns and closes the input stream. */
public interface ObjectStorage {
    void put(String bucket, String objectName, InputStream content, long size, String contentType) throws Exception;
    boolean exists(String bucket, String objectName) throws Exception;
    void compose(String sourceBucket, List<String> sourceObjects, String targetBucket, String targetObject) throws Exception;
}
