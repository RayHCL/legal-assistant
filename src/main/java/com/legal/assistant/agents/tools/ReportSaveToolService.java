package com.legal.assistant.agents.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.entity.RiskReport;
import com.legal.assistant.mapper.RiskReportMapper;
import com.legal.assistant.service.FileService;
import com.legal.assistant.utils.PdfUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 报告保存工具
 */
@Slf4j
@Component
public class ReportSaveToolService {

    @Autowired
    private RiskReportMapper riskReportMapper;

    @Autowired
    private FileService fileService;

    
    @Tool(name = "generate_download_link", description = "生成报告下载链接")
    public String generateDownloadLink(
            @ToolParam(name = "reportId", description = "报告编号") String reportId) {
        if (reportId == null || reportId.trim().isEmpty()) {
            return "错误: 报告编号为空";
        }

        RiskReport report = riskReportMapper.selectOne(
                new QueryWrapper<RiskReport>()
                        .eq("report_id", reportId));
        if (report == null) {
            return "错误: 报告不存在，reportId=" + reportId;
        }

        String reportContent = report.getFullReportContent();
        if (reportContent == null || reportContent.trim().isEmpty()) {
            return "错误: 报告内容为空";
        }

        try {
            byte[] pdfBytes = convertMarkdownToPdf(reportContent);
            String filename = "风险评估报告_" + reportId + ".pdf";
            String minioPath = fileService.uploadPdfToMinio(pdfBytes, filename);

            report.setMinioPath(minioPath);
            report.setLinkExpireTime(LocalDateTime.now().plusDays(7));
            report.setUpdatedAt(LocalDateTime.now());
            riskReportMapper.updateById(report);

            log.info("生成报告PDF并上传: reportId={}, minioPath={}", reportId, minioPath);
            return minioPath;
        } catch (Exception e) {
            log.error("生成报告PDF失败: reportId={}", reportId, e);
            return "错误: 生成PDF失败 - " + e.getMessage();
        }
    }

    private String generateReportId() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        return "RPT" + timestamp;
    }

    private byte[] convertMarkdownToPdf(String markdownContent) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream docxOutput = new ByteArrayOutputStream()) {
            String normalizedContent = markdownContent.replace("\r\n", "\n");
            String[] lines = normalizedContent.split("\n");
            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            document.write(docxOutput);

            try (ByteArrayInputStream docxInput = new ByteArrayInputStream(docxOutput.toByteArray())) {
                ByteArrayOutputStream pdfOutput = PdfUtil.doc2pdfByteArrayOutputStream(docxInput);
                if (pdfOutput == null) {
                    throw new RuntimeException("PDF转换失败");
                }
                return pdfOutput.toByteArray();
            }
        }
    }



}
