package com.gary.bilibili.common.reliability;

public class MessageInboxLeaseLostException extends RuntimeException {

    public MessageInboxLeaseLostException(MessageInboxLease lease) {
        super("MQ inbox lease lost: " + lease.consumerGroup() + ":" + lease.messageKey());
    }
}
