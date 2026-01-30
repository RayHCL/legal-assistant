package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.legal.assistant.config.MessageFileListTypeHandler;
import com.legal.assistant.dto.response.MessageFileItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "message", autoResultMap = true)
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("conversation_id")
    private Long conversationId;
    
    /**
     * 用户问题/查询内容
     */
    private String query;

    /**
     * 深度思考内容（模型推理过程，如 DeepSeek R1 / enableThinking 时的输出）
     */
    private String thinking;
    
    /**
     * AI助手回答
     */
    private String answer;
    
    /**
     * 关联文件列表（DB 存 JSON 字符串，由 TypeHandler 自动转 List，接口返回即为对象数组）
     */
    @TableField(typeHandler = MessageFileListTypeHandler.class)
    private List<MessageFileItem> files;
    
    private String parameters;  // JSON格式存储参数配置
    
    private String status;  // streaming/completed/error (针对answer的状态)



    /**
     * 用户反馈：LIKE-点赞，DISLIKE-点踩，null-未反馈或已取消
     */
    private String feedback;

    /**
     * 点踩时的反馈内容（仅当 feedback=DISLIKE 时有值）
     */
    @TableField("feedback_text")
    private String feedbackText;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
