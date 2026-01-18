package com.legal.assistant.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    
    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),
    PHONE_INVALID(1003, "手机号格式错误"),
    CODE_INVALID(1004, "验证码错误或已过期"),
    CODE_SEND_LIMIT(1005, "验证码发送次数已达上限"),
    
    // Token相关
    TOKEN_INVALID(2001, "Token无效或已过期"),
    TOKEN_MISSING(2002, "未提供Token"),
    
    // 文件相关
    FILE_TOO_LARGE(3001, "文件大小超出限制"),
    FILE_TYPE_NOT_SUPPORTED(3002, "不支持的文件类型"),
    FILE_UPLOAD_FAILED(3003, "文件上传失败"),
    
    // 会话相关
    CONVERSATION_NOT_FOUND(4001, "会话不存在"),
    CONVERSATION_DELETED(4002, "会话已删除");
    
    private final Integer code;
    private final String message;
    
    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
