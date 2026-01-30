package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报告实体
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 报告编号
     */
    private String reportId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 完整报告内容（Markdown格式）
     */
    private String fullReportContent;

    /**
     * MinIO存储路径（PDF文件）
     */
    private String minioPath;

    /**
     * 下载链接过期时间
     */
    private LocalDateTime linkExpireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
}
