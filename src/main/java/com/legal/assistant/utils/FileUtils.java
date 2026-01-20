package com.legal.assistant.utils;

import com.legal.assistant.enums.FileType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
public class FileUtils {
    
    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }
    
    /**
     * 获取文件类型
     */
    public static FileType getFileType(String fileName) {
        String extension = getFileExtension(fileName);
        return FileType.fromExtension(extension);
    }
    
    /**
     * 验证文件类型是否支持
     */
    public static boolean isSupportedFileType(String fileName) {
        FileType fileType = getFileType(fileName);
        return fileType != null;
    }
    
    /**
     * 生成唯一文件名
     */
    public static String generateUniqueFileName(String originalFileName) {
        String extension = getFileExtension(originalFileName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid + (extension.isEmpty() ? "" : "." + extension);
    }
    
    /**
     * 验证文件大小
     */
    public static boolean validateFileSize(MultipartFile file, long maxSize) {
        return file.getSize() <= maxSize;
    }
    
    /**
     * 保存文件到临时目录
     */
    public static Path saveToTemp(MultipartFile file) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path tempPath = Paths.get(tempDir, generateUniqueFileName(file.getOriginalFilename()));
        Files.write(tempPath, file.getBytes());
        return tempPath;
    }

    /**
     * 保存 InputStream 到临时目录
     */
    public static Path saveToTemp(InputStream inputStream, String extension) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "");
        Path tempPath = Paths.get(tempDir, uniqueFileName + "." + extension);
        Files.copy(inputStream, tempPath);
        return tempPath;
    }
}
