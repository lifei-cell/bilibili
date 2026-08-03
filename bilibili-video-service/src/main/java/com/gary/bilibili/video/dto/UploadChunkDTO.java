package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadChunkDTO {

    @NotBlank(message = "上传任务 ID 不能为空")
    private String uploadId;

    @NotBlank(message = "文件 MD5 不能为空")
    @Pattern(regexp = "(?i)^[a-f0-9]{32}$", message = "文件 MD5 格式错误")
    private String fileMd5;

    @NotBlank(message = "分片 MD5 不能为空")
    @Pattern(regexp = "(?i)^[a-f0-9]{32}$", message = "分片 MD5 格式错误")
    private String chunkMd5;

    @NotNull(message = "分片序号不能为空")
    @Min(value = 0, message = "分片序号非法")
    private Integer chunkIndex;

    @NotNull(message = "分片大小不能为空")
    @Min(value = 1, message = "分片大小必须大于 0")
    private Long chunkSize;

    @NotNull(message = "分片总数不能为空")
    @Min(value = 1, message = "分片总数必须大于 0")
    @Max(value = 10000, message = "分片总数过大")
    private Integer totalChunks;

    @NotNull(message = "分片文件不能为空")
    private MultipartFile file;
}
