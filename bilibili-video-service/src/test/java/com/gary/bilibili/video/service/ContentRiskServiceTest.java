package com.gary.bilibili.video.service;

import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.mapper.ContentRiskEventMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentRiskServiceTest {

    private final VideoMapper videoMapper = mock(VideoMapper.class);
    private final ContentRiskEventMapper eventMapper = mock(ContentRiskEventMapper.class);
    private final ContentRiskService service = new ContentRiskService(
            videoMapper, eventMapper, "色情,赌博,诈骗,暴力,外挂");

    @Test
    void shouldRejectBlockedKeywordContent() {
        ContentRiskService.RiskDecision result = service.evaluate(1L, request("低价赌博平台", List.of("广告")));

        assertThat(result.level()).isEqualTo("HIGH");
        assertThat(result.decision()).isEqualTo("REJECT");
        assertThat(result.rules()).contains("BLOCKED_KEYWORD");
    }

    @Test
    void shouldRequireReviewForPublishVelocity() {
        when(videoMapper.countRecentPublishes(1L)).thenReturn(5L);

        ContentRiskService.RiskDecision result = service.evaluate(1L, request("正常创作", List.of("科技")));

        assertThat(result.level()).isEqualTo("MEDIUM");
        assertThat(result.decision()).isEqualTo("REVIEW");
        assertThat(result.rules()).containsExactly("PUBLISH_VELOCITY");
    }

    private VideoPublishDTO request(String title, List<String> tags) {
        VideoPublishDTO request = new VideoPublishDTO();
        request.setTitle(title);
        request.setDescription("正常简介");
        request.setTags(tags);
        return request;
    }
}
