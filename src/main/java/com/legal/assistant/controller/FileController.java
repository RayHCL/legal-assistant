package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.response.FileResponse;
import com.legal.assistant.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件管理", description = "文件上传、查询、删除等文件管理相关接口。支持多种文件格式（PDF、Word、Excel、图片等）")
public class FileController {
    
    @Autowired
    private FileService fileService;
    
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件到系统，支持多种格式（PDF、Word、Excel、图片等）。文件会自动存储到MinIO，并提取内容进行向量化处理。需要Token认证。")
    public Result<FileResponse> uploadFile(
            @Parameter(description = "上传的文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "所属知识库ID（可选）", example = "1")
            @RequestParam(required = false) Long knowledgeBaseId,
            @Parameter(description = "文件描述（可选）", example = "合同文档")
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        FileResponse response = fileService.uploadFile(userId, file, knowledgeBaseId, description);
        return Result.success(response);
    }
    
    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件信息", description = "获取指定文件的详细信息，包括文件名、类型、大小、状态等。需要Token认证。")
    public Result<FileResponse> getFileInfo(
            @Parameter(description = "文件ID", required = true, example = "1")
            @PathVariable Long fileId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        FileResponse response = fileService.getFileInfo(userId, fileId);
        return Result.success(response);
    }
    
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件", description = "删除指定的文件，包括从MinIO和向量数据库中删除相关数据。需要Token认证。")
    public Result<Void> deleteFile(
            @Parameter(description = "文件ID", required = true, example = "1")
            @PathVariable Long fileId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        fileService.deleteFile(userId, fileId);
        return Result.success();
    }
}
