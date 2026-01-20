package com.legal.assistant.agents.tools;

import com.legal.assistant.entity.DocumentFile;
import com.legal.assistant.mapper.DocumentFileMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author hcl
 * @date 2026-01-20 13:48:37
 * @description 提供给Agent使用的文件工具
 */
@Slf4j
@Component
public class FileToolService {
    @Autowired
    private DocumentFileMapper documentFileMapper;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;
    @Tool(name = "getFileContent", description = "根据文件ID获取文件的内容")
    public String getFileContent(@ToolParam(name = "fileId", description = "文件ID") Long fileId) {
        log.info("开始获取文件内容: fileId={}", fileId);
        // 1. 查询文件记录
        DocumentFile documentFile = documentFileMapper.selectById(fileId);
        if (documentFile == null) {
            log.warn("文件不存在: fileId={}", fileId);
            return "错误: 文件不存在，文件ID: " + fileId;
        }

        // 2. 检查markdownPath是否为空
        String markdownPath = documentFile.getMarkdownPath();
        if (markdownPath == null || markdownPath.isEmpty()) {
            log.warn("文件的Markdown路径为空: fileId={}", fileId);
            return "错误: 文件的Markdown内容尚未生成，文件ID: " + fileId;
        }

        // 3. 从MinIO中下载Markdown文件内容
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(markdownPath)
                        .build())) {

            // 4. 读取文件内容
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("成功获取文件内容: fileId={}, fileName={}, 内容长度={}",
                    fileId, documentFile.getFileName(), content.length());

            return content;

        } catch (Exception e) {
            log.error("从MinIO获取文件内容失败: fileId={}, markdownPath={}",
                    fileId, markdownPath, e);
            return "错误: 获取文件内容失败 - " + e.getMessage();
        }
    }

}
