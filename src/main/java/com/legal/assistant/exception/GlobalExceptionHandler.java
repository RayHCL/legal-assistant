package com.legal.assistant.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
