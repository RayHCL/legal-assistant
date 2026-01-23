package com.legal.assistant.service;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.config.ConfigureBuilder;
import com.legal.assistant.common.MarkdownRenderPolicy;
import com.legal.assistant.entity.RiskReport;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.utils.PdfUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * PDF 生成服务 - 使用 poi-tl 和 Aspose
 */
@Slf4j
@Service
public class PdfService {

    /**
     * 将风险报告转换为 PDF
     *
     * @param report 风险报告实体
     * @return PDF 字节数组
     */
    public byte[] generateRiskReportPdf(RiskReport report) throws IOException {


        if (report == null) {
            throw new BusinessException("无报告");
        }
        Map<String, Object> dataModel = buildDataModel(report);
        // 配置自定义渲染策略

        ClassPathResource templateResource = new ClassPathResource("template/风险评估报告-参考模板.docx");
        try {
            ConfigureBuilder builder = Configure.builder();
            builder.bind("basicFacts", new MarkdownRenderPolicy());
            builder.bind("availableCoreEvidence", new MarkdownRenderPolicy());
            builder.bind("advantagesOpportunityAnalysis", new MarkdownRenderPolicy());
            builder.bind("riskChallengeAlert", new MarkdownRenderPolicy());
            builder.bind("actionSuggestionsSubsequentStrategies", new MarkdownRenderPolicy());
            Configure config = builder.build();
            XWPFTemplate template = XWPFTemplate.compile(templateResource.getInputStream(),config)
                    .render(dataModel);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            template.write(baos);
            template.close();
            InputStream docxInputStream = new ByteArrayInputStream(baos.toByteArray());
            ByteArrayOutputStream pdfOutputStream = PdfUtil.doc2pdfByteArrayOutputStream(docxInputStream);
            assert pdfOutputStream != null;
            return pdfOutputStream.toByteArray();
        } catch (IOException e) {
            log.error("生成PDF报告失败，报告ID: {}", report.getId(), e);
            throw new BusinessException("生成PDF报告失败");
        }
    }

    /**
     * 构建数据模型
     */
    private Map<String, Object> buildDataModel(RiskReport report) {
        Map<String, Object> dataModel = new HashMap<>();

        // 基本信息
        dataModel.put("reportDate", report.getReportDate() != null ? report.getReportDate() : "待确定");
        dataModel.put("ourSide", report.getOurSide() != null ? report.getOurSide() : "待确定");
        dataModel.put("ourIdentity", report.getOurIdentity() != null ? report.getOurIdentity() : "待确定");
        dataModel.put("otherIdentity", report.getOtherIdentity() != null ? report.getOtherIdentity() : "待确定");
        dataModel.put("otherParty", report.getOtherParty() != null ? report.getOtherParty() : "待确定");
        dataModel.put("caseReason", report.getCaseReason() != null ? report.getCaseReason() : "待确定");

        // 核心内容
        dataModel.put("coreDemand", report.getCoreDemand() != null ? report.getCoreDemand() : "待分析");
        dataModel.put("basicFacts", report.getBasicFacts() != null ? report.getBasicFacts() : "待整理");
        dataModel.put("availableCoreEvidence", report.getAvailableCoreEvidence() != null ? report.getAvailableCoreEvidence() : "待收集");

        // 风险评估
        dataModel.put("overallRiskLevel", report.getOverallRiskLevel() != null ? report.getOverallRiskLevel() : "中风险");
        dataModel.put("overallRiskScore", report.getOverallRiskScore() != null ? report.getOverallRiskScore() : 50);
        dataModel.put("overallRiskScoreReason", report.getOverallRiskScoreReason() != null ? report.getOverallRiskScoreReason() : "待分析");

        // 分析和建议
        dataModel.put("advantagesOpportunityAnalysis",
                report.getAdvantagesOpportunityAnalysis() != null ? report.getAdvantagesOpportunityAnalysis() : "待分析");
        dataModel.put("riskChallengeAlert",
                report.getRiskChallengeAlert() != null ? report.getRiskChallengeAlert() : "待评估");
        dataModel.put("riskPoint", report.getRiskPoint() != null ? report.getRiskPoint() : "待确定");
        dataModel.put("actionSuggestionsSubsequentStrategies",
                report.getActionSuggestionsSubsequentStrategies() != null ? report.getActionSuggestionsSubsequentStrategies() : "待确定");

        return dataModel;
    }

    /**
     * 将 Markdown 转换为 PDF（保留旧方法，用于兼容）
     */
    @Deprecated
    public byte[] markdownToPdf(String markdown) throws IOException {
        throw new UnsupportedOperationException("请使用 generateRiskReportPdf(RiskReport) 方法");
    }
}
