package com.gary.bilibili.danmu.config;

import com.gary.bilibili.danmu.constant.DanmuConstant;
import com.gary.bilibili.danmu.netty.DanmuBroadcastSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration(proxyBeanMethods = false)
public class DanmuRedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer danmuRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DanmuBroadcastSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(DanmuConstant.BROADCAST_CHANNEL));
        return container;
    }
}
