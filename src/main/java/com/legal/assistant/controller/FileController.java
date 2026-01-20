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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理控制器
 * 提供通用文件上传功能，不关联知识库
 * 用于对话时临时上传文件
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件管理", description = "通用文件管理接口。支持多种文件格式（PDF、Word、Excel、PPT、WPS、图片等）")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件到系统，支持多种格式（PDF、Word、Excel、PPT、WPS、图片等）。文件会存储到MinIO。需要Token认证。")
    public Result<FileResponse> uploadFile(
            @Parameter(description = "上传的文件", required = true)
            @Schema(type = "string", format = "binary")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件描述（可选）", example = "合同文档")
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        FileResponse response = fileService.uploadFile(userId, file, description);
        return Result.success(response);
    }

}
