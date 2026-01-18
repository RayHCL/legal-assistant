package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "文件响应")
public class FileResponse {
    @Schema(description = "文件ID", example = "1")
    private Long fileId;
    
    @Schema(description = "文件名", example = "合同.pdf")
    private String fileName;
    
    @Schema(description = "文件类型", example = "pdf")
    private String fileType;
    
    @Schema(description = "文件大小（字节）", example = "1024000")
    private Long fileSize;
    
    @Schema(description = "处理状态：uploading（上传中）、processing（处理中）、completed（已完成）、failed（失败）", example = "completed")
    private String status;
    
    @Schema(description = "上传时间", example = "2024-01-17T17:00:00")
    private LocalDateTime uploadTime;
    
    @Schema(description = "提取的文本预览（前500字符）", example = "这是一份合同文档的内容...")
    private String extractedText;
}
