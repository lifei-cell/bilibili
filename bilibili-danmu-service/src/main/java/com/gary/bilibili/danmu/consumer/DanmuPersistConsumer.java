package com.gary.bilibili.danmu.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
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

    private static final String CONSUMER_GROUP = "danmu-persist-consumer";

    private final DanmuBatchPersistService batchPersistService;
    private final ReliableMessageExecutor reliableMessageExecutor;

    public DanmuPersistConsumer(DanmuBatchPersistService batchPersistService,
                                ReliableMessageExecutor reliableMessageExecutor) {
        this.batchPersistService = batchPersistService;
        this.reliableMessageExecutor = reliableMessageExecutor;
    }

    @Override
    public void onMessage(DanmuPersistMessage message) {
        if (message == null || message.getId() == null) {
            return;
        }
        reliableMessageExecutor.execute(
                DanmuConstant.PERSIST_TOPIC,
                CONSUMER_GROUP,
                message.getId().toString(),
                message,
                () -> batchPersistService.persist(message));
    }
}
