package com.gary.bilibili.video.service;

import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.entity.ContentRiskEvent;
import com.gary.bilibili.video.mapper.ContentRiskEventMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ContentRiskService {
    private final VideoMapper videoMapper;
    private final ContentRiskEventMapper eventMapper;
    private final List<String> blockedKeywords;

    public ContentRiskService(VideoMapper videoMapper, ContentRiskEventMapper eventMapper,
                              @Value("${governance.risk.blocked-keywords:色情,赌博,诈骗,暴力,外挂}") String keywords) {
        this.videoMapper = videoMapper;
        this.eventMapper = eventMapper;
        this.blockedKeywords = Arrays.stream(keywords.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public RiskDecision evaluate(long userId, VideoPublishDTO request) {
        int score = 0;
        List<String> rules = new ArrayList<>();
        String text = (request.getTitle() + " " + request.getDescription() + " "
                + String.join(" ", request.getTags())).toLowerCase(Locale.ROOT);
        if (blockedKeywords.stream().anyMatch(text::contains)) { score += 80; rules.add("BLOCKED_KEYWORD"); }
        if (request.getTags().size() > 10) { score += 20; rules.add("EXCESSIVE_TAGS"); }
        if (videoMapper.countRecentPublishes(userId) >= 5) { score += 40; rules.add("PUBLISH_VELOCITY"); }
        if (request.getTitle().matches(".*(.)\\1{7,}.*")) { score += 20; rules.add("REPEATED_TEXT"); }
        String level = score >= 80 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
        String decision = score >= 80 ? "REJECT" : score >= 40 ? "REVIEW" : "PASS";
        return new RiskDecision(level, score, List.copyOf(rules), decision);
    }

    public void record(long userId, long videoId, RiskDecision decision) {
        ContentRiskEvent event = new ContentRiskEvent();
        event.setTargetType("VIDEO");
        event.setTargetId(videoId);
        event.setUserId(userId);
        event.setScene("VIDEO_PUBLISH");
        event.setRiskLevel(decision.level());
        event.setScore(decision.score());
        event.setMatchedRules(String.join(",", decision.rules()));
        event.setDecision(decision.decision());
        eventMapper.insert(event);
    }

    public record RiskDecision(String level, int score, List<String> rules, String decision) { }
}
