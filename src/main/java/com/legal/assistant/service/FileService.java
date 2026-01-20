package com.legal.assistant.service;

import com.legal.assistant.dto.response.FileResponse;
import com.legal.assistant.entity.DocumentFile;
import com.legal.assistant.enums.FileType;
import com.legal.assistant.mapper.DocumentFileMapper;
import com.legal.assistant.utils.DocumentExtractor;
import com.legal.assistant.utils.FileUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * @author hcl
 * @date 2026-01-20 09:49:26
 * @description
 * 描述：文件服务
 */
@Slf4j
@Service

public class FileService {

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Value("${minio.bucket-name}")
    private String bucketName;
    @Autowired
    private  MinioClient minioClient;
    @Autowired
    private  DocumentFileMapper documentFileMapper;




    /**
     * 上传文件
     * @param userId
     * @param file
     * @param description
     * @return
     */
    public FileResponse uploadFile(Long userId, MultipartFile file, String description) {

        //1.检查文件的类型是是不是我支持的
        if (!FileUtils.isSupportedFileType(file.getOriginalFilename())) {
            throw new RuntimeException("不支持的文件类型");
        }
        if (!FileUtils.validateFileSize(file, 1024 * 1024 * 20)) {
            throw new RuntimeException("文件大小超出限制");
        }

        //2.将文件存储到minio中
        String originalFilename = file.getOriginalFilename();
        FileType fileType = FileUtils.getFileType(originalFilename);
        String minioPath = uploadToMinio(file, originalFilename);

        //3.提取文件的内容，不同类型的需要不同的解析去解析内容，统一解析成markdown文件，也上传到minio中
        String extractedText = extractContent(file, fileType);
        String markdownPath = uploadMarkdownToMinio(originalFilename, extractedText);

        //4.保存文件记录到数据库
        DocumentFile documentFile = new DocumentFile();
        documentFile.setUserId(1L);
        documentFile.setFileName(originalFilename);
        documentFile.setFileType(fileType.getExtension());
        documentFile.setFileSize(file.getSize());
        documentFile.setMinioPath(minioPath);
        documentFile.setMarkdownPath(markdownPath);
        documentFile.setStatus("completed");
        documentFile.setCreatedAt(LocalDateTime.now());

        documentFileMapper.insert(documentFile);

        //5.构造返回结果
        FileResponse response = new FileResponse();
        response.setFileId(documentFile.getId());
        response.setFileName(documentFile.getFileName());
        response.setFileType(documentFile.getFileType());
        response.setFileSize(documentFile.getFileSize());
        response.setStatus(documentFile.getStatus());
        response.setUploadTime(documentFile.getCreatedAt());
        response.setExtractedText(extractedText.length() > 500 ?
                extractedText.substring(0, 500) : extractedText);

        return response;
    }

    /**
     * 上传文件到MinIO
     */
    private String uploadToMinio(MultipartFile file, String filename) {
        try {
            String extension = FileUtils.getFileExtension(filename);
            String objectName = "original/" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID() + "." + extension;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("文件上传到MinIO成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("上传文件到MinIO失败: {}", filename, e);
            throw new RuntimeException("上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 上传Markdown内容到MinIO
     */
    private String uploadMarkdownToMinio(String originalFilename, String markdownContent) {
        try {
            String baseName = originalFilename.replaceAll("\\.[^.]+$", "");
            String objectName = "markdown/" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID() + "_" + baseName + ".md";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(markdownContent.getBytes(StandardCharsets.UTF_8)),
                                    markdownContent.getBytes(StandardCharsets.UTF_8).length, -1)
                            .contentType("text/markdown")
                            .build()
            );

            log.info("Markdown文件上传到MinIO成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("上传Markdown到MinIO失败: {}", originalFilename, e);
            throw new RuntimeException("上传Markdown失败: " + e.getMessage());
        }
    }

    /**
     * 提取文件内容并转换为Markdown格式
     * 使用 Apache Tika 统一处理所有文件格式（包括文档和图片OCR）
     */
    private String extractContent(MultipartFile file, FileType fileType) {
        try {
            Path tempFile = FileUtils.saveToTemp(file);

            // 使用 Apache Tika 统一提取文本（支持文档和图片OCR）
            String text = DocumentExtractor.extractText(tempFile, fileType);

            // 删除临时文件
            Files.deleteIfExists(tempFile);

            // 转换为Markdown格式
            return convertToMarkdown(text, fileType, file.getOriginalFilename());
        } catch (Exception e) {
            log.error("提取文件内容失败: {}", file.getOriginalFilename(), e);
            return "";
        }
    }

    /**
     * 将提取的文本转换为Markdown格式
     */
    private String convertToMarkdown(String text, FileType fileType, String filename) {
        if (text == null || text.isEmpty()) {
            return "# " + filename + "\n\n无法提取文件内容。";
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(filename).append("\n\n");
        markdown.append("**文件类型**: ").append(fileType.getDescription()).append("\n\n");
        markdown.append("---\n\n");
        markdown.append("## 文件内容\n\n");

        // 如果文本包含换行符，保持原有格式
        if (text.contains("\n")) {
            markdown.append(text);
        } else {
            // 如果没有换行符，按段落分割
            String[] paragraphs = text.split("(?<=。 )|(?<=\\n )|(?<=\n)");
            for (String para : paragraphs) {
                if (!para.trim().isEmpty()) {
                    markdown.append(para.trim()).append("\n\n");
                }
            }
        }

        return markdown.toString();
    }
}
