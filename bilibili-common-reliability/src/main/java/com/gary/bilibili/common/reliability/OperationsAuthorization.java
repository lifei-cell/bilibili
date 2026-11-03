package com.gary.bilibili.common.reliability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class OperationsAuthorization {
    private final String adminToken;

    public OperationsAuthorization(@Value("${operations.admin-token:change-me-in-production}") String adminToken,
                                   Environment environment) {
        this.adminToken = adminToken;
        if (adminToken.isBlank() || (Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && "change-me-in-production".equals(adminToken))) {
            throw new IllegalStateException("OPERATIONS_ADMIN_TOKEN is required");
        }
    }

    public void authorize(String token) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }
}
