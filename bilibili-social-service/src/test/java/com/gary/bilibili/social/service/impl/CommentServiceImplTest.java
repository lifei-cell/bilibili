package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.dto.CommentCreateDTO;
import com.gary.bilibili.social.entity.Comment;
import com.gary.bilibili.social.mapper.CommentMapper;
import com.gary.bilibili.social.model.CommentPage;
import com.gary.bilibili.social.model.CommentRow;
import com.gary.bilibili.social.service.SocialStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentServiceImplTest {

    private CommentMapper commentMapper;
    private StringRedisTemplate stringRedisTemplate;
    private SocialStatsService socialStatsService;
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        socialStatsService = mock(SocialStatsService.class);
        when(stringRedisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        commentService = new CommentServiceImpl(
                commentMapper, stringRedisTemplate, socialStatsService);
    }

    @Test
    void shouldCreateTopLevelCommentAndIncrementVideoCount() {
        CommentCreateDTO request = buildRequest();
        when(commentMapper.countPublishedVideo(10001L)).thenReturn(1L);
        doAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(80001L);
            return 1;
        }).when(commentMapper).insert(any(Comment.class));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(commentService.create(request).getCommentId()).isEqualTo(80001L);
        }

        verify(socialStatsService).increment(
                10001L, SocialConstant.COMMENT_COUNT_FIELD, 1);
    }

    @Test
    void shouldRejectBlankCommentWithManualErrorMessage() {
        CommentCreateDTO request = buildRequest();
        request.setContent("   ");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> commentService.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("评论内容不能为空");
        }
    }

    @Test
    void shouldRejectCommentWhenRateLimitIsReached() {
        when(commentMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(stringRedisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> commentService.create(buildRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("评论发布过于频繁");
        }
    }

    @Test
    void shouldNestRepliesUnderTopLevelComment() {
        CommentRow parent = new CommentRow();
        parent.setId(80001L);
        parent.setUserId(1L);
        parent.setContent("一级评论");
        CommentRow reply = new CommentRow();
        reply.setId(80002L);
        reply.setParentId(80001L);
        reply.setUserId(2L);
        reply.setContent("二级回复");
        when(commentMapper.selectTopLevel(10001L, "hot", 0L, 20))
                .thenReturn(List.of(parent));
        when(commentMapper.selectReplies(List.of(80001L))).thenReturn(List.of(reply));
        when(commentMapper.countTopLevel(10001L)).thenReturn(1L);

        CommentPage result = commentService.getList(10001L, 1, 20, "hot");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getReplies()).singleElement()
                        .satisfies(replyItem -> assertThat(replyItem.getContent())
                                .isEqualTo("二级回复")));
    }

    @Test
    void shouldAllowVideoAuthorToDeleteCommentTree() {
        Comment comment = new Comment();
        comment.setId(80001L);
        comment.setUserId(2L);
        comment.setVideoId(10001L);
        comment.setParentId(0L);
        when(commentMapper.selectOne(any())).thenReturn(comment);
        when(commentMapper.selectVideoAuthorId(10001L)).thenReturn(1L);
        when(commentMapper.countCommentTree(80001L)).thenReturn(3L);
        when(commentMapper.markDeleted(80001L, true)).thenReturn(3);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            commentService.delete(80001L);
        }

        verify(socialStatsService).increment(
                10001L, SocialConstant.COMMENT_COUNT_FIELD, -3L);
    }

    private CommentCreateDTO buildRequest() {
        CommentCreateDTO request = new CommentCreateDTO();
        request.setVideoId(10001L);
        request.setContent("评论内容");
        request.setParentId(0L);
        request.setReplyToId(0L);
        return request;
    }
}
