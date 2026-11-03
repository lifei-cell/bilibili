package com.gary.bilibili.common.reliability;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageReplayRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String requestId,
        @NotNull @Min(0) Integer expectedReplayCount,
        @NotBlank @Size(min = 10, max = 500) String reason) {
    public void validate() {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{16,64}")
                || expectedReplayCount == null || expectedReplayCount < 0
                || reason == null || reason.trim().length() < 10 || reason.length() > 500) {
            throw new IllegalArgumentException("Replay requires requestId, expectedReplayCount and a meaningful reason");
        }
    }
}
