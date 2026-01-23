package com.legal.assistant.utils;


import com.aspose.words.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

/**
 * Word 转 Pdf 帮助类
 * 备注:需要引入 aspose-words
 */
@Slf4j
public class PdfUtil {

    private static boolean getLicense() {
        boolean result = false;
        try {
            InputStream is = PdfUtil.class.getClassLoader().getResourceAsStream("license.xml");
            License aposeLic = new License();
            aposeLic.setLicense(is);
            result = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static ByteArrayOutputStream doc2pdfByteArrayOutputStream(InputStream inputStream) {
        // 如果未获取到许可证，则抛出业务异常
        if (!getLicense()) {
            throw new RuntimeException("未配置许可证!");
        }
        try {
            long startTime = System.currentTimeMillis();
            // 要转换的Word文档
            Document doc = new Document(inputStream);

            // --- 核心字体设置开始 ---
            FontSettings fontSettings = new FontSettings();

            // 字体存放路径
            String folders = "/app/fonts"; // 或者是你存放字体的绝对路径

            fontSettings.setFontsSources(new FontSourceBase[] {
                    new SystemFontSource(), // 保留系统自带字体
                    new FolderFontSource(folders, true) // 添加自定义字体文件夹，true 表示递归子目录
            });

            doc.setFontSettings(fontSettings);
            // --- 核心字体设置结束 ---

            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            doc.save(bao, SaveFormat.PDF);
            log.info("PDF转换完成，耗时：{}ms", System.currentTimeMillis() - startTime);

            return bao;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public static boolean doc2pdf(String inputPath, String outputPath) {
        // 如果未获取到许可证,则抛出业务异常
        if (!getLicense()) {
            throw new RuntimeException("未配置许可证!");
        }

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            long startTime = System.currentTimeMillis();

            // 验证输入文件是否存在
            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                log.error("输入文件不存在: {}", inputPath);
                return false;
            }

            // 创建输出目录(如果不存在)
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 读取Word文档
            fis = new FileInputStream(inputPath);
            Document doc = new Document(fis);

            // --- 核心字体设置开始 ---
            FontSettings fontSettings = new FontSettings();
            String folders = "/app/fonts"; // 或者是你存放字体的绝对路径

            fontSettings.setFontsSources(new FontSourceBase[] {
                    new SystemFontSource(), // 保留系统自带字体
                    new FolderFontSource(folders, true) // 添加自定义字体文件夹,true 表示递归子目录
            });

            doc.setFontSettings(fontSettings);
            // --- 核心字体设置结束 ---

            // 保存为PDF
            doc.save(outputPath, SaveFormat.PDF);

            log.info("PDF转换成功: {} -> {}, 耗时:{}ms",
                    inputPath, outputPath, System.currentTimeMillis() - startTime);

            return true;
        } catch (Exception e) {
            log.error("Word转PDF失败: {} -> {}", inputPath, outputPath, e);
            return false;
        } finally {
            // 关闭流
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.error("关闭输入流失败", e);
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    log.error("关闭输出流失败", e);
                }
            }
        }
    }

    public static void main(String[] args) {
        doc2pdf("/Users/rayhu/Desktop/123.docx", "/Users/rayhu/Desktop/123-trans.pdf");
    }
}
