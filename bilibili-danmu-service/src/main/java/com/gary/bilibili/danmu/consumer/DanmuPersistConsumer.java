package com.gary.bilibili.danmu.consumer;

import com.gary.bilibili.danmu.constant.DanmuConstant;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.service.DanmuBatchPersistService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Service
@RocketMQMessageListener(
        topic = DanmuConstant.PERSIST_TOPIC,
        consumerGroup = "danmu-persist-consumer",
        consumeThreadNumber = 20)
public class DanmuPersistConsumer implements RocketMQListener<DanmuPersistMessage> {

    private final DanmuBatchPersistService batchPersistService;

    public DanmuPersistConsumer(DanmuBatchPersistService batchPersistService) {
        this.batchPersistService = batchPersistService;
    }

    @Override
    public void onMessage(DanmuPersistMessage message) {
        batchPersistService.persist(message);
    }
}
