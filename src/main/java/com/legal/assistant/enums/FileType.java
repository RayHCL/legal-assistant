package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum FileType {
    PDF("pdf", "PDF文档"),
    DOC("doc", "Word文档"),
    DOCX("docx", "Word文档"),
    TXT("txt", "文本文件"),
    XLS("xls", "Excel表格"),
    XLSX("xlsx", "Excel表格"),
    PPT("ppt", "PowerPoint演示文稿"),
    PPTX("pptx", "PowerPoint演示文稿"),
    JPG("jpg", "图片"),
    JPEG("jpeg", "图片"),
    PNG("png", "图片"),
    BMP("bmp", "图片"),
    GIF("gif", "图片"),
    TIFF("tiff", "图片"),
    WEBP("webp", "图片");
    
    private final String extension;
    private final String description;
    
    FileType(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }
    
    public static FileType fromExtension(String ext) {
        if (ext == null) {
            return null;
        }
        ext = ext.toLowerCase();
        for (FileType type : values()) {
            if (type.extension.equals(ext)) {
                return type;
            }
        }
        return null;
    }
}
