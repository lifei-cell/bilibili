package com.gary.bilibili.video.service;

import com.gary.bilibili.video.dto.UploadCheckDTO;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.dto.UploadMergeDTO;
import com.gary.bilibili.video.dto.FileCheckDTO;
import com.gary.bilibili.video.dto.FileChunkUploadDTO;
import com.gary.bilibili.video.dto.FileMergeDTO;
import com.gary.bilibili.video.vo.FileCheckVO;
import com.gary.bilibili.video.vo.FileMergeVO;
import com.gary.bilibili.video.vo.UploadCheckVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import com.gary.bilibili.video.vo.UploadProgressVO;
import com.gary.bilibili.video.vo.VideoTranscodeStatusVO;

public interface UploadService {

    UploadCheckVO check(UploadCheckDTO request);

    UploadChunkVO uploadChunk(UploadChunkDTO request);

    UploadMergeVO merge(UploadMergeDTO request);

    UploadProgressVO getProgress(String uploadId);

    VideoTranscodeStatusVO getTranscodeStatus(String taskId);

    FileCheckVO checkFile(FileCheckDTO request);

    UploadChunkVO uploadFileChunk(FileChunkUploadDTO request);

    FileMergeVO mergeFile(FileMergeDTO request);

    UploadProgressVO getFileProgress(String fileMd5);
}
