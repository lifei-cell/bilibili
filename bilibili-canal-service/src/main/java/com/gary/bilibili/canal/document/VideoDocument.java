package com.gary.bilibili.canal.document;

import com.gary.bilibili.canal.constant.CanalConstant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.util.List;

@Data
@Document(indexName = CanalConstant.VIDEO_INDEX)
public class VideoDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private Long userId;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Long)
    private Long viewCount;

    @Field(type = FieldType.Long)
    private Long likeCount;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private String createTime;
}
