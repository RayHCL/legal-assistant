package com.legal.assistant.service;

import com.legal.assistant.dto.response.FileResponse;
import com.legal.assistant.entity.DocumentFile;
import com.legal.assistant.enums.FileType;
import com.legal.assistant.mapper.DocumentFileMapper;
import com.legal.assistant.utils.DocumentExtractor;
import com.legal.assistant.utils.FileUtils;
import com.legal.assistant.utils.TimeUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
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

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${server.base-url:http://localhost:8080}")
    private String serverBaseUrl;

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
        documentFile.setUserId(userId);
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
        response.setUploadTime(TimeUtils.toTimestamp(documentFile.getCreatedAt()));
        response.setExtractedText(extractedText.length() > 500 ?
                extractedText.substring(0, 500) : extractedText);
        // 构建文件下载URL
        response.setFileUrl(buildDownloadUrl(minioPath));

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

    /**
     * 上传PDF到MinIO（用于风险评估报告）
     * @param pdfBytes PDF字节数组
     * @param filename 文件名
     * @return MinIO对象路径
     */
    public String uploadPdfToMinio(byte[] pdfBytes, String filename) {
        try {
            String objectName = "risk-reports/" + System.currentTimeMillis() + "_" +
                    java.util.UUID.randomUUID() + "_" + filename;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(pdfBytes), pdfBytes.length, -1)
                            .contentType("application/pdf")
                            .build()
            );

            log.info("PDF报告上传到MinIO成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("上传PDF到MinIO失败: {}", filename, e);
            throw new RuntimeException("上传PDF失败: " + e.getMessage());
        }
    }

    /**
     * 构建后端服务的文件下载URL
     * @param minioPath MinIO对象路径
     * @return 后端服务的下载URL
     */
    public String buildDownloadUrl(String minioPath) {
        // 移除末尾的斜杠（如果有）
        String baseUrl = serverBaseUrl.endsWith("/")
                ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1)
                : serverBaseUrl;
        // 对路径进行URL编码
        try {
            String encodedPath = URLEncoder.encode(minioPath,StandardCharsets.UTF_8);
            return baseUrl + "/api/file/download?path=" + encodedPath;
        } catch (Exception e) {
            log.error("构建下载URL失败: {}", minioPath, e);
            return baseUrl + "/api/file/download?path=" + minioPath;
        }
    }

    /**
     * 构建MinIO直接访问的HTTP URL（内部使用）
     * @param objectName 对象名称
     * @return MinIO的HTTP URL
     */
    public String buildMinioDirectUrl(String objectName) {
        // 移除endpoint末尾的斜杠（如果有）
        String baseUrl = minioEndpoint.endsWith("/") 
                ? minioEndpoint.substring(0, minioEndpoint.length() - 1) 
                : minioEndpoint;
        return baseUrl + "/" + bucketName + "/" + objectName;
    }

    /**
     * 上传Markdown内容到MinIO（用于风险评估报告）
     * @param markdownContent Markdown内容
     * @param filename 文件名
     * @return MinIO对象路径
     */
    public String riskUploadMarkdownToMinio(String markdownContent, String filename) {
        try {
            String baseName = filename.replaceAll("\\.[^.]+$", "");
            String objectName = "risk-reports/" + System.currentTimeMillis() + "_" +
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

            log.info("Markdown报告上传到MinIO成功: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("上传Markdown到MinIO失败: {}", filename, e);
            throw new RuntimeException("上传Markdown失败: " + e.getMessage());
        }
    }

    /**
     * 从MinIO下载文件
     * @param minioPath MinIO对象路径
     * @return 文件字节数组
     */
    public byte[] downloadFromMinio(String minioPath) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(minioPath)
                            .build()
            ).readAllBytes();
        } catch (Exception e) {
            log.error("从MinIO下载文件失败: minioPath={}", minioPath, e);
            throw new RuntimeException("下载文件失败: " + e.getMessage());
        }
    }
}
