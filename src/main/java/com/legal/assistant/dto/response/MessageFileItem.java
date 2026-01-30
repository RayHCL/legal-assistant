package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息关联的文件项（用于 message.files JSON 存储及接口返回）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "消息关联的文件项")
public class MessageFileItem {
    @Schema(description = "文件ID")
    private Long id;
    @Schema(description = "文件名")
    private String fileName;
    @Schema(description = "文件类型/扩展名")
    private String fileType;
    @Schema(description = "文件大小（字节）")
    private Long fileSize;
    @Schema(description = "文件URL")
    private String fileUrl;
}
