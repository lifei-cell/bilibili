package com.gary.bilibili.social.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollectionFolderUpdateDTO {

    @Size(max = 50)
    private String name;

    private Boolean isPublic;

    @Size(max = 200)
    private String description;
}
