package com.gary.bilibili.social.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.fail(exception.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail("请先登录"));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public Result<Void> handleValidationException(Exception exception) {
        log.warn("Request validation failed", exception);
        return Result.fail("请求参数错误");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception) {
        log.warn("Request body parse failed", exception);
        return Result.fail("请求体不能为空或 JSON 格式错误");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public Result<Void> handleNotFoundException(Exception exception) {
        return Result.fail("接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("Unhandled request exception", exception);
        return Result.fail("系统繁忙，请稍后再试");
    }
}
