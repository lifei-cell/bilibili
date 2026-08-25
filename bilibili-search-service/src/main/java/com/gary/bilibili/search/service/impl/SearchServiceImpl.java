package com.gary.bilibili.search.service.impl;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.search.constant.SearchConstant;
import com.gary.bilibili.search.document.VideoDocument;
import com.gary.bilibili.search.model.SearchPage;
import com.gary.bilibili.search.service.SearchService;
import com.gary.bilibili.search.service.SearchResultCache;
import com.gary.bilibili.search.vo.VideoSearchVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final DefaultRedisScript<Long> RECORD_HOT_SEARCH_SCRIPT = new DefaultRedisScript<>(
            "redis.call('zincrby', KEYS[1], 1, ARGV[1]); "
                    + "local size = redis.call('zcard', KEYS[1]); "
                    + "if size > tonumber(ARGV[2]) then "
                    + "redis.call('zremrangebyrank', KEYS[1], 0, size - tonumber(ARGV[2]) - 1); end; "
                    + "return size;",
            Long.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate stringRedisTemplate;
    private final int hotMaxSize;
    private final SearchResultCache searchResultCache;

    public SearchServiceImpl(ElasticsearchOperations elasticsearchOperations,
                             StringRedisTemplate stringRedisTemplate,
                             @Value("${search.hot.max-size:100}") int hotMaxSize,
                             SearchResultCache searchResultCache) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.stringRedisTemplate = stringRedisTemplate;
        this.hotMaxSize = hotMaxSize;
        this.searchResultCache = searchResultCache;
    }

    @Override
    public SearchPage searchVideo(String keyword,
                                  Long categoryId,
                                  String sort,
                                  Integer page,
                                  Integer size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedSort = normalizeSort(sort);
        recordHotSearch(normalizedKeyword);
        SearchPage cached = searchResultCache.get(
                normalizedKeyword, categoryId, normalizedSort, page, size);
        if (cached != null) {
            return cached;
        }
        Criteria criteria = Criteria.where("status").is(SearchConstant.VIDEO_STATUS_PUBLISHED);
        Criteria keywordCriteria = Criteria.where("title").matches(normalizedKeyword)
                .or("description").matches(normalizedKeyword)
                .or("tags").matches(normalizedKeyword);
        criteria = criteria.and(keywordCriteria);
        if (categoryId != null) {
            criteria = criteria.and("categoryId").is(categoryId);
        }

        PageRequest pageable = PageRequest.of(page - 1, size, buildSort(normalizedSort));
        CriteriaQuery query = new CriteriaQuery(criteria, pageable);
        query.setTrackTotalHits(true);
        SearchHits<VideoDocument> hits = elasticsearchOperations.search(
                query, VideoDocument.class);

        List<VideoSearchVO> records = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<VideoDocument> hit : hits.getSearchHits()) {
            records.add(toVO(hit.getContent()));
        }
        SearchPage result = new SearchPage();
        result.setRecords(records);
        result.setTotal(hits.getTotalHits());
        searchResultCache.put(normalizedKeyword, categoryId, normalizedSort, page, size, result);
        return result;
    }

    @Override
    public List<String> getHotSearch(Integer size) {
        int limit = normalizeSize(size, SearchConstant.DEFAULT_HOT_SIZE);
        try {
            Set<String> keywords = stringRedisTemplate.opsForZSet()
                    .reverseRange(SearchConstant.HOT_SEARCH_KEY, 0, limit - 1L);
            return keywords == null ? List.of() : new ArrayList<>(keywords);
        } catch (Exception exception) {
            log.warn("Read hot search failed", exception);
            return List.of();
        }
    }

    @Override
    public List<String> getSuggestions(String keyword, Integer size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int limit = normalizeSize(size, SearchConstant.DEFAULT_SUGGEST_SIZE);
        Criteria criteria = Criteria.where("status").is(SearchConstant.VIDEO_STATUS_PUBLISHED)
                .and("title").startsWith(normalizedKeyword);
        CriteriaQuery query = new CriteriaQuery(criteria,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "viewCount")));
        SearchHits<VideoDocument> hits = elasticsearchOperations.search(
                query, VideoDocument.class);

        Set<String> suggestions = new LinkedHashSet<>();
        for (SearchHit<VideoDocument> hit : hits.getSearchHits()) {
            String title = hit.getContent().getTitle();
            if (StringUtils.hasText(title)) {
                suggestions.add(title);
            }
        }
        return new ArrayList<>(suggestions);
    }

    private Sort buildSort(String sort) {
        if (SearchConstant.SORT_HOT.equals(sort)) {
            return Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createTime"));
        }
        if (SearchConstant.SORT_NEW.equals(sort)) {
            return Sort.by(Sort.Direction.DESC, "createTime");
        }
        return Sort.unsorted();
    }

    private void recordHotSearch(String keyword) {
        try {
            stringRedisTemplate.execute(RECORD_HOT_SEARCH_SCRIPT,
                    List.of(SearchConstant.HOT_SEARCH_KEY), keyword, Integer.toString(hotMaxSize));
        } catch (Exception exception) {
            log.warn("Record hot search failed, keyword={}", keyword, exception);
        }
    }

    private VideoSearchVO toVO(VideoDocument document) {
        VideoSearchVO result = new VideoSearchVO();
        result.setId(document.getId());
        result.setTitle(document.getTitle());
        result.setDescription(document.getDescription());
        result.setTags(document.getTags());
        result.setCategoryId(document.getCategoryId());
        result.setUserId(document.getUserId());
        result.setViewCount(nullToZero(document.getViewCount()));
        result.setLikeCount(nullToZero(document.getLikeCount()));
        result.setCreateTime(document.getCreateTime());
        return result;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException("搜索关键词不能为空");
        }
        String value = keyword.trim();
        if (value.codePointCount(0, value.length()) > 100) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return SearchConstant.SORT_DEFAULT;
        }
        String value = sort.toLowerCase(Locale.ROOT);
        if (!SearchConstant.SORT_DEFAULT.equals(value)
                && !SearchConstant.SORT_HOT.equals(value)
                && !SearchConstant.SORT_NEW.equals(value)) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    private int normalizeSize(Integer size, int defaultSize) {
        if (size == null) {
            return defaultSize;
        }
        if (size < 1 || size > 50) {
            throw new BusinessException("请求参数错误");
        }
        return size;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
