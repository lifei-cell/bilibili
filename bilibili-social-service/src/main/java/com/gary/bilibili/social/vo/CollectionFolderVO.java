package com.gary.bilibili.social.vo;

import lombok.Data;

@Data
public class CollectionFolderVO {

    private Long id;
    private String name;
    private Boolean isPublic;
    private String description;
    private String coverUrl;
    private Integer videoCount;
}
