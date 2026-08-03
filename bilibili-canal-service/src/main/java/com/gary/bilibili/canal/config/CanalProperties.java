package com.gary.bilibili.canal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "canal.server")
public class CanalProperties {

    private String host = "canal";
    private Integer port = 11111;
    private String destination = "example";
    private String username = "";
    private String password = "";
    private String filter = "bilibili\\.(video|video_stats|sys_user|follow|user_like)";
    private Integer batchSize = 1000;
}
