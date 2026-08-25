package com.gary.bilibili.search.service.impl;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.search.document.VideoDocument;
import com.gary.bilibili.search.model.SearchPage;
import com.gary.bilibili.search.service.SearchResultCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchServiceImplTest {

    private ElasticsearchOperations elasticsearchOperations;
    private ZSetOperations<String, String> zSetOperations;
    private SearchHits<VideoDocument> searchHits;
    private StringRedisTemplate stringRedisTemplate;
    private SearchResultCache searchResultCache;
    private SearchServiceImpl searchService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        searchResultCache = mock(SearchResultCache.class);
        zSetOperations = mock(ZSetOperations.class);
        searchHits = mock(SearchHits.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(elasticsearchOperations.search(any(Query.class), eq(VideoDocument.class)))
                .thenReturn(searchHits);
        when(searchHits.getSearchHits()).thenReturn(List.of());
        searchService = new SearchServiceImpl(
                elasticsearchOperations, stringRedisTemplate, 100, searchResultCache);
    }

    @Test
    void shouldSearchPublishedVideosAndRecordHotKeyword() {
        VideoDocument document = new VideoDocument();
        document.setId(10001L);
        document.setTitle("SpringBoot 视频平台实战");
        document.setViewCount(1000L);
        SearchHit<VideoDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);
        when(searchHits.getSearchHits()).thenReturn(List.of(hit));
        when(searchHits.getTotalHits()).thenReturn(1L);

        SearchPage result = searchService.searchVideo(
                " SpringBoot ", 1L, "hot", 1, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(10001L);
                    assertThat(item.getViewCount()).isEqualTo(1000L);
                });
        verify(stringRedisTemplate).execute(any(RedisScript.class),
                eq(List.of("search:hot")), eq("SpringBoot"), eq("100"));
        verify(searchResultCache).put("SpringBoot", 1L, "hot", 1, 20, result);
    }

    @Test
    void shouldReturnCachedSearchResultWithoutElasticsearchRoundTrip() {
        SearchPage cached = new SearchPage();
        cached.setRecords(List.of());
        cached.setTotal(8L);
        when(searchResultCache.get("SpringBoot", null, "default", 1, 20)).thenReturn(cached);

        SearchPage result = searchService.searchVideo(" SpringBoot ", null, "default", 1, 20);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    void shouldRejectBlankSearchKeyword() {
        assertThatThrownBy(() -> searchService.searchVideo("   ", null, "default", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("搜索关键词不能为空");
    }

    @Test
    void shouldReturnHotKeywordsByScoreOrder() {
        when(zSetOperations.reverseRange("search:hot", 0, 9))
                .thenReturn(Set.of("Java", "SpringBoot"));

        assertThat(searchService.getHotSearch(10))
                .containsExactlyInAnyOrder("Java", "SpringBoot");
    }

    @Test
    void shouldReturnDistinctTitleSuggestions() {
        VideoDocument first = new VideoDocument();
        first.setTitle("SpringBoot 入门");
        VideoDocument second = new VideoDocument();
        second.setTitle("SpringBoot 入门");
        SearchHit<VideoDocument> firstHit = mock(SearchHit.class);
        SearchHit<VideoDocument> secondHit = mock(SearchHit.class);
        when(firstHit.getContent()).thenReturn(first);
        when(secondHit.getContent()).thenReturn(second);
        when(searchHits.getSearchHits()).thenReturn(List.of(firstHit, secondHit));

        assertThat(searchService.getSuggestions("Spring", 10))
                .containsExactly("SpringBoot 入门");
    }

    @Test
    void shouldRejectUnsupportedSort() {
        assertThatThrownBy(() -> searchService.searchVideo("Java", null, "unknown", 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求参数错误");
    }
}
