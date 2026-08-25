package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.VideoCategory;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoCategoryMapper extends BaseMapper<VideoCategory> {

    @Select("select id, parent_id, name, sort "
            + "from video_category "
            + "where status = 1 "
            + "order by sort asc, id asc")
    List<VideoCategory> selectEnabledCategories();
}
