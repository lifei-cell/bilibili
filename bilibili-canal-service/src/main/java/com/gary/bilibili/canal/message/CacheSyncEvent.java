package com.gary.bilibili.canal.message;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class CacheSyncEvent implements Serializable {

    private String database;
    private String table;
    private String eventType;
    private Map<String, String> data;
}
