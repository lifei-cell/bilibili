package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.dto.CommentCreateDTO;
import com.gary.bilibili.social.entity.Comment;
import com.gary.bilibili.social.mapper.CommentMapper;
import com.gary.bilibili.social.model.CommentPage;
import com.gary.bilibili.social.model.CommentRow;
import com.gary.bilibili.social.service.CommentService;
import com.gary.bilibili.social.service.SocialStatsService;
import com.gary.bilibili.social.vo.CommentCreateVO;
import com.gary.bilibili.social.vo.CommentListVO;
import com.gary.bilibili.social.vo.CommentReplyVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentServiceImpl implements CommentService {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1]; local limit = tonumber(ARGV[1]); "
                    + "local window = tonumber(ARGV[2]); local now = tonumber(ARGV[3]); "
                    + "redis.call('ZREMRANGEBYSCORE', key, 0, now - window); "
                    + "if redis.call('ZCARD', key) >= limit then return 0 end; "
                    + "redis.call('ZADD', key, now, ARGV[4]); "
                    + "redis.call('PEXPIRE', key, window); return 1;",
            Long.class);

    private final CommentMapper commentMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocialStatsService socialStatsService;

    public CommentServiceImpl(CommentMapper commentMapper,
                              StringRedisTemplate stringRedisTemplate,
                              SocialStatsService socialStatsService) {
        this.commentMapper = commentMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.socialStatsService = socialStatsService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentCreateVO create(CommentCreateDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String content = normalizeContent(request.getContent());
        if (nullToZero(commentMapper.countPublishedVideo(request.getVideoId())) == 0) {
            throw new BusinessException("视频不存在");
        }
        checkRateLimit(userId);

        long parentId = request.getParentId() == null ? 0L : request.getParentId();
        long replyToId = request.getReplyToId() == null ? 0L : request.getReplyToId();
        if (parentId > 0) {
            Comment parent = loadVisibleComment(parentId);
            if (parent.getParentId() != null && parent.getParentId() != 0
                    || !request.getVideoId().equals(parent.getVideoId())) {
                throw new BusinessException("父评论不存在");
            }
            if (replyToId > 0 && nullToZero(commentMapper.countEnabledUser(replyToId)) == 0) {
                throw new BusinessException("回复用户不存在");
            }
        } else if (replyToId > 0) {
            throw new BusinessException("请求参数错误");
        }

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setVideoId(request.getVideoId());
        comment.setParentId(parentId);
        comment.setReplyToId(replyToId);
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setStatus(0);
        comment.setDeleted(0);
        commentMapper.insert(comment);
        socialStatsService.increment(request.getVideoId(), SocialConstant.COMMENT_COUNT_FIELD, 1);

        CommentCreateVO result = new CommentCreateVO();
        result.setCommentId(comment.getId());
        return result;
    }

    @Override
    public CommentPage getList(Long videoId, Integer page, Integer size, String sort) {
        String normalizedSort = normalizeSort(sort);
        long offset = (long) (page - 1) * size;
        List<CommentRow> rows = commentMapper.selectTopLevel(
                videoId, normalizedSort, offset, size);
        List<Long> parentIds = rows.stream().map(CommentRow::getId).toList();
        Map<Long, List<CommentReplyVO>> replies = loadReplies(parentIds);

        List<CommentListVO> records = new ArrayList<>(rows.size());
        for (CommentRow row : rows) {
            CommentListVO item = new CommentListVO();
            item.setId(row.getId());
            item.setUserId(row.getUserId());
            item.setNickname(row.getNickname());
            item.setAvatar(row.getAvatar());
            item.setContent(row.getContent());
            item.setLikeCount(row.getLikeCount());
            item.setCreateTime(row.getCreateTime());
            item.setReplies(replies.getOrDefault(row.getId(), Collections.emptyList()));
            records.add(item);
        }

        CommentPage result = new CommentPage();
        result.setRecords(records);
        result.setTotal(nullToZero(commentMapper.countTopLevel(videoId)));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long commentId) {
        Long userId = StpUtil.getLoginIdAsLong();
        Comment comment = loadVisibleComment(commentId);
        Long videoAuthorId = commentMapper.selectVideoAuthorId(comment.getVideoId());
        String role = commentMapper.selectUserRole(userId);
        if (!userId.equals(comment.getUserId())
                && !userId.equals(videoAuthorId)
                && !"admin".equals(role)) {
            throw new BusinessException("无权访问该资源");
        }

        boolean includeReplies = comment.getParentId() == null || comment.getParentId() == 0;
        long count = includeReplies
                ? nullToZero(commentMapper.countCommentTree(commentId)) : 1L;
        int changed = commentMapper.markDeleted(commentId, includeReplies);
        if (changed > 0) {
            socialStatsService.increment(
                    comment.getVideoId(), SocialConstant.COMMENT_COUNT_FIELD, -count);
        }
    }

    private Map<Long, List<CommentReplyVO>> loadReplies(List<Long> parentIds) {
        Map<Long, List<CommentReplyVO>> grouped = new HashMap<>();
        if (parentIds.isEmpty()) {
            return grouped;
        }
        List<CommentRow> rows = commentMapper.selectReplies(parentIds);
        for (CommentRow row : rows) {
            CommentReplyVO item = new CommentReplyVO();
            item.setId(row.getId());
            item.setUserId(row.getUserId());
            item.setNickname(row.getNickname());
            item.setAvatar(row.getAvatar());
            item.setReplyToId(row.getReplyToId());
            item.setReplyToNickname(row.getReplyToNickname());
            item.setContent(row.getContent());
            item.setLikeCount(row.getLikeCount());
            item.setCreateTime(row.getCreateTime());
            grouped.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private Comment loadVisibleComment(Long commentId) {
        Comment comment = commentMapper.selectOne(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getId, commentId)
                .eq(Comment::getStatus, 0)
                .eq(Comment::getDeleted, 0));
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        return comment;
    }

    private void checkRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        Long allowed = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(SocialConstant.COMMENT_RATE_LIMIT_KEY_PREFIX + userId),
                Integer.toString(SocialConstant.COMMENT_RATE_LIMIT_COUNT),
                Long.toString(Duration.ofSeconds(
                        SocialConstant.COMMENT_RATE_LIMIT_WINDOW_SECONDS).toMillis()),
                Long.toString(now),
                now + ":" + UUID.randomUUID());
        if (!Long.valueOf(1L).equals(allowed)) {
            throw new BusinessException("评论发布过于频繁");
        }
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("评论内容不能为空");
        }
        return content.trim();
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return "hot";
        }
        String value = sort.toLowerCase(Locale.ROOT);
        if (!"hot".equals(value) && !"new".equals(value)) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
