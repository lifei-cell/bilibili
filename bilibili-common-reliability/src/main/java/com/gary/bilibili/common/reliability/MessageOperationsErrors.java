package com.gary.bilibili.common.reliability;

import com.gary.bilibili.common.result.Result;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(0)
@ConditionalOnProperty(name = "spring.datasource.url")
@RestControllerAdvice(assignableTypes = {OutboxOperationsController.class, FailedMessageAdminController.class})
public class MessageOperationsErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> authorization(ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(Result.fail(failure.getReason()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> invalid(Exception failure) {
        return ResponseEntity.badRequest().body(Result.fail("Invalid message operation request"));
    }

    @ExceptionHandler({IllegalStateException.class, DuplicateKeyException.class})
    public ResponseEntity<Result<Void>> conflict(Exception failure) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.fail("Message state changed or replay limit reached"));
    }
}
