package com.legal.assistant.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> handleBusinessException(BusinessException e) {
        log.error("业务异常: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", e.getCode());
        result.put("message", e.getMessage());
        result.put("data", null);
        return result;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(MethodArgumentNotValidException e) {
        log.error("参数验证异常: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        result.put("code", 400);
        result.put("message", "参数验证失败");
        result.put("errors", errors);
        return result;
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBindException(BindException e) {
        log.error("参数绑定异常: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        result.put("code", 400);
        result.put("message", "参数绑定失败");
        result.put("errors", errors);
        return result;
    }

    /**
     * 处理静态资源未找到异常（如favicon.ico）
     * 对于favicon.ico请求，静默忽略（不记录日志）
     * 对于其他资源，正常记录日志
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNoResourceFoundException(NoResourceFoundException e) {
        String resourcePath = e.getResourcePath();

        // 对于favicon.ico，静默忽略，不记录ERROR日志
        if ("favicon.ico".equals(resourcePath)) {
            // 静默返回404，不记录日志
            Map<String, Object> result = new HashMap<>();
            result.put("code", 404);
            result.put("message", "Resource not found");
            result.put("data", null);
            return result;
        }

        // 对于其他资源，记录日志
        log.warn("静态资源未找到: {}", resourcePath);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 404);
        result.put("message", "资源未找到: " + resourcePath);
        result.put("data", null);
        return result;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常", e);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "系统内部错误");
        result.put("data", null);
        return result;
    }
}
