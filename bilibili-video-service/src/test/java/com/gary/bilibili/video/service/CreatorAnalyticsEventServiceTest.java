package com.gary.bilibili.video.service;

import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreatorAnalyticsEventServiceTest {

    @Test
    void duplicateViewDoesNotCreateAnotherDirtyVersion() {
        CreatorAnalyticsRepository repository = mock(CreatorAnalyticsRepository.class);
        CreatorAnalyticsEventService service = new CreatorAnalyticsEventService(repository);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 12, 0);
        when(repository.insertViewEvent("request-1", 101L, occurredAt)).thenReturn(false);

        service.recordView("request-1", 101L, occurredAt);

        verify(repository, never()).markDirtyForVideo(101L, occurredAt.toLocalDate());
    }

    @Test
    void newViewMarksItsOccurrenceDayDirty() {
        CreatorAnalyticsRepository repository = mock(CreatorAnalyticsRepository.class);
        CreatorAnalyticsEventService service = new CreatorAnalyticsEventService(repository);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 19, 23, 59);
        when(repository.insertViewEvent("late-request", 101L, occurredAt)).thenReturn(true);

        service.recordView("late-request", 101L, occurredAt);

        verify(repository).markDirtyForVideo(101L, occurredAt.toLocalDate());
    }

    @Test
    void integrationEventMarksSpecifiedDayDirty() {
        CreatorAnalyticsRepository repository = mock(CreatorAnalyticsRepository.class);
        CreatorAnalyticsEventService service = new CreatorAnalyticsEventService(repository);
        LocalDate day = LocalDate.of(2026, 9, 18);

        service.markDirty(101L, day);

        verify(repository).markDirtyForVideo(101L, day);
    }
}
