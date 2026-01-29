package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.dto.response.FileResponse;
import com.legal.assistant.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;

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
    @NoAuth
    public Result<FileResponse> uploadFile(
            @Parameter(description = "上传的文件", required = true)
            @Schema(type = "string", format = "binary")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件描述（可选）", example = "合同文档")
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null){
            userId = -1L;
        }
        FileResponse response = fileService.uploadFile(userId, file, description);
        return Result.success(response);
    }

    @GetMapping("/download")
    @Operation(summary = "下载文件", description = "根据MinIO路径下载文件。用于下载风险评估报告等文件。")
    @NoAuth
    public void downloadFile(
            @Parameter(description = "MinIO文件路径", required = true, example = "risk-reports/xxx.pdf")
            @RequestParam("path") String minioPath,
            HttpServletResponse response) {
        try {
            // 从MinIO下载文件
            byte[] fileBytes = fileService.downloadFromMinio(minioPath);

            // 从路径中提取文件名
            String filename = extractFilename(minioPath);

            // 设置响应头
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + URLEncoder.encode(filename, "UTF-8") + "\"");
            response.setContentLength(fileBytes.length);

            // 写入响应
            response.getOutputStream().write(fileBytes);
            response.getOutputStream().flush();

            log.info("文件下载成功: {}", minioPath);
        } catch (Exception e) {
            log.error("文件下载失败: {}", minioPath, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("文件下载失败: " + e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 从MinIO路径中提取文件名
     */
    private String extractFilename(String minioPath) {
        if (minioPath == null || minioPath.isEmpty()) {
            return "download";
        }
        int lastSlash = minioPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < minioPath.length() - 1) {
            String filename = minioPath.substring(lastSlash + 1);
            // 移除UUID前缀（格式：timestamp_uuid_原始文件名）
            int lastUnderscore = filename.lastIndexOf('_');
            if (lastUnderscore > 0) {
                // 查找第二个下划线之后的部分作为原始文件名
                int firstUnderscore = filename.indexOf('_');
                if (firstUnderscore > 0) {
                    int secondUnderscore = filename.indexOf('_', firstUnderscore + 1);
                    if (secondUnderscore > 0 && secondUnderscore < filename.length() - 1) {
                        return filename.substring(secondUnderscore + 1);
                    }
                }
            }
            return filename;
        }
        return minioPath;
    }

}
