package com.gary.bilibili.common.reliability;

/** Immutable identity of one inbox claim; a takeover changes both generation and token. */
public record MessageInboxLease(String topic, String consumerGroup, String messageKey,
                                long generation, String token) {
}
