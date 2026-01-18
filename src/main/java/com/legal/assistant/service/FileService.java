package com.legal.assistant.service;

import com.legal.assistant.dto.response.FileResponse;
import com.legal.assistant.entity.File;
import com.legal.assistant.enums.FileType;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.FileMapper;
import com.legal.assistant.utils.FileUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class FileService {
    
    @Autowired
    private FileMapper fileMapper;
    
    @Autowired
    private MinioClient minioClient;
    
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Value("${spring.servlet.multipart.max-file-size:100MB}")
    private DataSize maxFileSize;
    
    /**
     * 上传文件
     */
    @Transactional
    public FileResponse uploadFile(Long userId, MultipartFile multipartFile, Long knowledgeBaseId, String description) {
        // 验证文件
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件不能为空");
        }
        
        String originalFileName = multipartFile.getOriginalFilename();
        if (originalFileName == null || originalFileName.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件名不能为空");
        }
        
        // 验证文件类型
        if (!FileUtils.isSupportedFileType(originalFileName)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED.getCode(), 
                ErrorCode.FILE_TYPE_NOT_SUPPORTED.getMessage());
        }
        
        // 验证文件大小
        long maxSizeBytes = maxFileSize.toBytes();
        if (!FileUtils.validateFileSize(multipartFile, maxSizeBytes)) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE.getCode(), 
                ErrorCode.FILE_TOO_LARGE.getMessage());
        }
        
        FileType fileType = FileUtils.getFileType(originalFileName);
        String uniqueFileName = FileUtils.generateUniqueFileName(originalFileName);
        String minioPath = "files/" + userId + "/" + uniqueFileName;
        
        // 创建文件记录
        File file = new File();
        file.setUserId(userId);
        file.setKnowledgeBaseId(knowledgeBaseId);
        file.setFileName(originalFileName);
        file.setFileType(fileType != null ? fileType.getExtension() : "");
        file.setFileSize(multipartFile.getSize());
        file.setMinioPath(minioPath);
        file.setStatus("uploading");
        file.setCreatedAt(LocalDateTime.now());
        fileMapper.insert(file);
        
        try {
            // 上传到MinIO
            try (InputStream inputStream = multipartFile.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(minioPath)
                        .stream(inputStream, multipartFile.getSize(), -1)
                        .contentType(multipartFile.getContentType())
                        .build()
                );
            }
            
            // 更新文件状态
            file.setStatus("completed");
            fileMapper.updateById(file);
            
            log.info("文件上传成功: fileId={}, fileName={}, userId={}", file.getId(), originalFileName, userId);
            
            // 异步处理文件解析（这里简化处理，实际应该异步执行）
            // TODO: 实现文件内容提取和向量化
            
            // 构建响应
            FileResponse response = new FileResponse();
            response.setFileId(file.getId());
            response.setFileName(file.getFileName());
            response.setFileType(file.getFileType());
            response.setFileSize(file.getFileSize());
            response.setStatus(file.getStatus());
            response.setUploadTime(file.getCreatedAt());
            response.setExtractedText("");  // TODO: 提取文本内容
            
            return response;
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            file.setStatus("failed");
            fileMapper.updateById(file);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED.getCode(), 
                "文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件信息
     */
    public FileResponse getFileInfo(Long userId, Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "文件不存在");
        }
        
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限访问该文件");
        }
        
        FileResponse response = new FileResponse();
        BeanUtils.copyProperties(file, response);
        response.setFileId(file.getId());
        response.setUploadTime(file.getCreatedAt());
        response.setExtractedText("");  // TODO: 从MinIO读取提取的文本
        
        return response;
    }
    
    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "文件不存在");
        }
        
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限删除该文件");
        }
        
        // TODO: 从MinIO删除文件
        // TODO: 从向量数据库删除向量
        
        fileMapper.deleteById(fileId);
        log.info("删除文件: fileId={}, userId={}", fileId, userId);
    }
}
