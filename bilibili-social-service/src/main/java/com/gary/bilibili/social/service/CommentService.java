package com.gary.bilibili.social.service;

import com.gary.bilibili.social.dto.CommentCreateDTO;
import com.gary.bilibili.social.model.CommentPage;
import com.gary.bilibili.social.vo.CommentCreateVO;

public interface CommentService {

    CommentCreateVO create(CommentCreateDTO request);

    CommentPage getList(Long videoId, Integer page, Integer size, String sort);

    void delete(Long commentId);
}
