package com.gary.bilibili.common.reliability;

/** Identity of one dispatch attempt; never reuse it after a lease expires. */
public record OutboxEventLease(long id, String token) {
}
