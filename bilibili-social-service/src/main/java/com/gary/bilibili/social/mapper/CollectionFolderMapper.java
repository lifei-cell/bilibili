package com.gary.bilibili.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.social.entity.CollectionFolder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CollectionFolderMapper extends BaseMapper<CollectionFolder> {

    @Select("select count(*) from collection where user_id = #{userId} and folder_id = 0 and status = 1")
    Long countDefaultFolderVideos(@Param("userId") Long userId);

    @Update("update collection_folder set video_count = greatest(video_count + #{delta}, 0) where id = #{folderId} and user_id = #{userId} and deleted = 0")
    int incrementVideoCount(@Param("folderId") Long folderId,
                            @Param("userId") Long userId,
                            @Param("delta") int delta);
}
