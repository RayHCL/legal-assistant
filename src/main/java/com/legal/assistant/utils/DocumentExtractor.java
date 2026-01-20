package com.legal.assistant.utils;

import com.legal.assistant.enums.FileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档内容提取器
 * 使用 Apache Tika 统一处理各种文件格式的文本提取
 * 支持：PDF、Word、Excel、PPT、图片OCR等
 */
@Slf4j
public class DocumentExtractor {

    private static volatile Tika tika;
    private static volatile Parser parser;
    private static volatile TikaConfig tikaConfig;

    /**
     * 获取 Tika 实例（单例模式）
     */
    private static Tika getTika() {
        if (tika == null) {
            synchronized (DocumentExtractor.class) {
                if (tika == null) {
                    tika = new Tika();
                    log.info("Apache Tika 初始化成功");
                }
            }
        }
        return tika;
    }

    /**
     * 获取配置了 OCR 的 Parser 实例（单例模式）
     */
    private static Parser getParser() {
        if (parser == null) {
            synchronized (DocumentExtractor.class) {
                if (parser == null) {
                    try {
                        // 加载 Tika 配置
                        tikaConfig = TikaConfig.getDefaultConfig();
                        
                        // 创建自动检测解析器
                        parser = new AutoDetectParser(tikaConfig);
                        
                        log.info("Apache Tika Parser 初始化成功（支持OCR）");
                    } catch (Exception e) {
                        log.error("初始化 Tika Parser 失败，使用默认解析器", e);
                        parser = new AutoDetectParser();
                    }
                }
            }
        }
        return parser;
    }

    /**
     * 从文件中提取文本内容
     * 
     * @param filePath 文件路径
     * @param fileType 文件类型（可选，如果不提供会自动检测）
     * @return 提取的文本内容
     */
    public static String extractText(Path filePath, FileType fileType) {
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("文件不存在: {}", filePath);
            return "";
        }

        try {
            // 使用 Tika 自动检测和解析文件
            Metadata metadata = new Metadata();
            ParseContext parseContext = new ParseContext();
            
            // 设置文件名称到 metadata，帮助类型检测
            if (filePath.getFileName() != null) {
                metadata.set("resourceName", filePath.getFileName().toString());
            }

            // 配置 PDF 解析器（如果需要）
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setExtractInlineImages(true);
            pdfConfig.setExtractUniqueInlineImagesOnly(false);
            parseContext.set(PDFParserConfig.class, pdfConfig);

            // 配置 OCR（用于图片和PDF中的图片）
            // Tika 会自动检测 Tesseract，如果系统 PATH 中没有，可以配置路径
            TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
            // 设置语言（中文简体 + 英文）
            ocrConfig.setLanguage("chi_sim+eng");
            // 启用 OCR
            ocrConfig.setSkipOcr(false);
            // 如果系统 PATH 中没有 Tesseract，可以通过环境变量 TESSERACT_PATH 设置
            // 或者使用配置文件的 tesseractPath 属性
            parseContext.set(TesseractOCRConfig.class, ocrConfig);

            // 创建内容处理器，设置最大字符数（防止内存溢出）
            int maxLength = 10 * 1024 * 1024; // 10MB
            BodyContentHandler handler = new BodyContentHandler(maxLength);

            // 解析文件
            try (InputStream inputStream = new FileInputStream(filePath.toFile())) {
                Parser parserInstance = getParser();
                parserInstance.parse(inputStream, handler, metadata, parseContext);
            }

            String extractedText = handler.toString().trim();
            
            // 记录检测到的文件类型
            String detectedType = metadata.get(Metadata.CONTENT_TYPE);
            log.info("文件类型检测: {}, 提取文本长度: {} 字符", detectedType, extractedText.length());
            return extractedText;
            
        } catch (IOException | TikaException e) {
            log.error("使用 Tika 提取文本失败: {}", filePath, e);
            return "";
        } catch (Exception e) {
            log.error("提取文本时发生未知错误: {}", filePath, e);
            return "";
        }
    }



}