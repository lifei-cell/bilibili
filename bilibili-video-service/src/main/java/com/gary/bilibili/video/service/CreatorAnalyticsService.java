package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.model.CreatorDailyMetric;
import com.gary.bilibili.video.model.CreatorTopVideo;
import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import com.gary.bilibili.video.vo.CreatorAnalyticsTotalVO;
import com.gary.bilibili.video.vo.CreatorAnalyticsVO;
import com.gary.bilibili.video.vo.CreatorDailyTrendVO;
import com.gary.bilibili.video.vo.CreatorTopVideoVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CreatorAnalyticsService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_QUERY_DAYS = 90;
    private static final int MAX_REBUILD_DAYS = 31;

    private final CreatorAnalyticsRepository repository;
    private final CreatorAnalyticsAggregator aggregator;

    public CreatorAnalyticsService(CreatorAnalyticsRepository repository,
                                   CreatorAnalyticsAggregator aggregator) {
        this.repository = repository;
        this.aggregator = aggregator;
    }

    public CreatorAnalyticsVO getDashboard(LocalDate requestedStart, LocalDate requestedEnd, int limit) {
        DateRange range = normalizeRange(requestedStart, requestedEnd, MAX_QUERY_DAYS);
        long creatorId = StpUtil.getLoginIdAsLong();
        List<CreatorDailyTrendVO> trend = fillTrend(
                repository.findTrend(creatorId, range.startDate(), range.endDate()), range);
        List<CreatorTopVideoVO> topVideos = repository.findTopVideos(
                        creatorId, range.startDate(), range.endDate(), limit)
                .stream().map(this::toTopVideo).toList();
        CreatorAnalyticsTotalVO totals = total(trend);
        return new CreatorAnalyticsVO(
                range.startDate(), range.endDate(), totals, trend, topVideos,
                repository.findLastAggregatedAt(creatorId, range.startDate(), range.endDate()));
    }

    public CreatorAnalyticsVO rebuild(LocalDate requestedStart, LocalDate requestedEnd, int limit) {
        DateRange range = normalizeRange(requestedStart, requestedEnd, MAX_REBUILD_DAYS);
        long creatorId = StpUtil.getLoginIdAsLong();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            aggregator.rebuildDay(creatorId, date);
        }
        return getDashboard(range.startDate(), range.endDate(), limit);
    }

    private DateRange normalizeRange(LocalDate startDate, LocalDate endDate, int maximumDays) {
        LocalDate today = LocalDate.now();
        LocalDate normalizedEnd = endDate == null ? today : endDate;
        LocalDate normalizedStart = startDate == null
                ? normalizedEnd.minusDays(DEFAULT_RANGE_DAYS - 1L) : startDate;
        long days = ChronoUnit.DAYS.between(normalizedStart, normalizedEnd) + 1;
        if (normalizedStart.isAfter(normalizedEnd) || normalizedEnd.isAfter(today)
                || days <= 0 || days > maximumDays) {
            throw new BusinessException("统计日期范围无效");
        }
        return new DateRange(normalizedStart, normalizedEnd);
    }

    private List<CreatorDailyTrendVO> fillTrend(List<CreatorDailyMetric> metrics, DateRange range) {
        Map<LocalDate, CreatorDailyMetric> byDate = new HashMap<>();
        for (CreatorDailyMetric metric : metrics) {
            byDate.put(metric.statDate(), metric);
        }
        List<CreatorDailyTrendVO> result = new ArrayList<>();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            CreatorDailyMetric metric = byDate.get(date);
            result.add(metric == null
                    ? new CreatorDailyTrendVO(date, 0, 0, 0, 0, 0)
                    : new CreatorDailyTrendVO(date, metric.viewCount(), metric.likeCount(),
                    metric.collectCount(), metric.commentCount(), metric.danmuCount()));
        }
        return result;
    }

    private CreatorAnalyticsTotalVO total(List<CreatorDailyTrendVO> trend) {
        long views = 0;
        long likes = 0;
        long collects = 0;
        long comments = 0;
        long danmus = 0;
        for (CreatorDailyTrendVO day : trend) {
            views += day.viewCount();
            likes += day.likeCount();
            collects += day.collectCount();
            comments += day.commentCount();
            danmus += day.danmuCount();
        }
        return new CreatorAnalyticsTotalVO(views, likes, collects, comments, danmus);
    }

    private CreatorTopVideoVO toTopVideo(CreatorTopVideo video) {
        return new CreatorTopVideoVO(video.videoId(), video.title(), video.coverUrl(),
                video.viewCount(), video.likeCount(), video.collectCount(),
                video.commentCount(), video.danmuCount());
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
