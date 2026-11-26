package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.model.CreatorDailyMetric;
import com.gary.bilibili.video.model.CreatorTopVideo;
import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import com.gary.bilibili.video.vo.CreatorAnalyticsVO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatorAnalyticsServiceTest {

    @Test
    void dashboardUsesAuthenticatedCreatorAndFillsMissingDays() {
        CreatorAnalyticsRepository repository = mock(CreatorAnalyticsRepository.class);
        CreatorAnalyticsAggregator aggregator = mock(CreatorAnalyticsAggregator.class);
        CreatorAnalyticsService service = new CreatorAnalyticsService(repository, aggregator);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(2);
        when(repository.findTrend(11L, start, today)).thenReturn(List.of(
                new CreatorDailyMetric(start, 3, 2, 1, 0, 4),
                new CreatorDailyMetric(today, 7, 1, 0, 2, 1)));
        when(repository.findTopVideos(11L, start, today, 5)).thenReturn(List.of(
                new CreatorTopVideo(101L, "owned", "cover", 10, 3, 1, 2, 5)));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            CreatorAnalyticsVO result = service.getDashboard(start, today, 5);

            assertThat(result.trend()).hasSize(3);
            assertThat(result.trend().get(1).viewCount()).isZero();
            assertThat(result.totals().viewCount()).isEqualTo(10);
            assertThat(result.totals().danmuCount()).isEqualTo(5);
            assertThat(result.topVideos()).extracting(item -> item.videoId()).containsExactly(101L);
            verify(repository).findTrend(11L, start, today);
            verify(repository).findTopVideos(11L, start, today, 5);
        }
    }

    @Test
    void rebuildIsRestrictedToOwnIdentityAndBoundedRange() {
        CreatorAnalyticsRepository repository = mock(CreatorAnalyticsRepository.class);
        CreatorAnalyticsAggregator aggregator = mock(CreatorAnalyticsAggregator.class);
        CreatorAnalyticsService service = new CreatorAnalyticsService(repository, aggregator);
        LocalDate today = LocalDate.now();
        when(repository.findTrend(22L, today.minusDays(1), today)).thenReturn(List.of());
        when(repository.findTopVideos(22L, today.minusDays(1), today, 10)).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(22L);
            service.rebuild(today.minusDays(1), today, 10);

            verify(aggregator).rebuildDay(22L, today.minusDays(1));
            verify(aggregator).rebuildDay(22L, today);
            assertThatThrownBy(() -> service.rebuild(today.minusDays(31), today, 10))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("统计日期范围无效");
        }
    }
}
