package com.gary.bilibili.danmu.service;

import com.gary.bilibili.danmu.mapper.DanmuMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanmuWebSocketTicketServiceTest {

    @Test
    void shouldConsumeTicketOnlyOnceAndOnlyForBoundVideo() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(startsWith("danmu:ws-ticket:")))
                .thenReturn("7:42")
                .thenReturn(null)
                .thenReturn("7:42");
        DanmuWebSocketTicketService service = new DanmuWebSocketTicketService(
                redisTemplate, mock(DanmuMapper.class), 30);

        assertThat(service.consume("one-time-secret", 42L)).isEqualTo(7L);
        assertThat(service.consume("one-time-secret", 42L)).isNull();
        assertThat(service.consume("another-secret", 99L)).isNull();
        verify(valueOperations, times(3)).getAndDelete(startsWith("danmu:ws-ticket:"));
    }
}
