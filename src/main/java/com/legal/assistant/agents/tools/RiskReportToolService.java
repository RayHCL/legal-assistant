package com.legal.assistant.agents.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.entity.RiskReport;
import com.legal.assistant.mapper.RiskReportMapper;
import com.legal.assistant.service.FileService;
import com.legal.assistant.service.PdfService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 风险评估报告生成工具
 */
@Slf4j
@Component
public class RiskReportToolService {

    @Autowired
    private RiskReportMapper riskReportMapper;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private FileService fileService;

    @Value("${server.base-url:http://localhost:8080}")
    private String serverBaseUrl;


    @Tool(name = "save_risk_report_to_db", description = "保存风险评估报告到数据库。ReportGenerationAgent专用：完成风险分析后调用此工具保存报告。")
    public String saveRiskReportToDb(
            @ToolParam(name = "ourSide", description = "我方当事人名称") String ourSide,
            @ToolParam(name = "ourIdentity", description = "我方身份（原告/被告/申请人/被申请人/债权人/债务人等）") String ourIdentity,
            @ToolParam(name = "otherParty", description = "对方当事人名称") String otherParty,
            @ToolParam(name = "otherIdentity", description = "对方身份") String otherIdentity,
            @ToolParam(name = "caseReason", description = "案由（合同纠纷/劳动争议/知识产权侵权等）") String caseReason,
            @ToolParam(name = "coreDemand", description = "核心诉求，希望通过法律途径达成什么目标") String coreDemand,
            @ToolParam(name = "basicFacts", description = "基本事实，案件的来龙去脉，包含时间、地点、具体事件") String basicFacts,
            @ToolParam(name = "availableCoreEvidence", description = "现有核心证据，手中有哪些证据材料") String availableCoreEvidence,
            @ToolParam(name = "overallRiskLevel", description = "综合风险等级（较低风险/中等风险/较高风险）") String overallRiskLevel,
            @ToolParam(name = "overallRiskScore", description = "风险评分（10-100分，较低10-30，中等40-70，较高80-100）") Integer overallRiskScore,
            @ToolParam(name = "overallRiskScoreReason", description = "风险评分原因，说明为什么给出这个评分") String overallRiskScoreReason,
            @ToolParam(name = "advantagesOpportunityAnalysis", description = "优势与机会分析，列出有利因素") String advantagesOpportunityAnalysis,
            @ToolParam(name = "riskChallengeAlert", description = "风险挑战提示，格式：主要风险：[描述] 风险点：[具体] 影响：[后果]") String riskChallengeAlert,
            @ToolParam(name = "riskPoint", description = "风险点简述，提取核心风险点，每条20字以内，空格分隔") String riskPoint,
            @ToolParam(name = "actionSuggestionsSubsequentStrategies", description = "行动建议与后续策略") String actionSuggestionsSubsequentStrategies,
            @ToolParam(name = "reportDate", description = "报告日期，格式：YYYY年MM月DD日") String reportDate,
            AgentContext ctx) {

        try {
            Long userId = ctx.getUserId();
            Long conversationId = ctx.getConversationId();

            // 生成报告编号
            String reportId = generateReportId();

            // 创建报告实体
            RiskReport report = new RiskReport();
            report.setReportId(reportId);
            report.setUserId(userId);
            report.setConversationId(conversationId);
            report.setOurSide(ourSide);
            report.setOurIdentity(ourIdentity);
            report.setOtherParty(otherParty);
            report.setOtherIdentity(otherIdentity);
            report.setCaseReason(caseReason);
            report.setCoreDemand(coreDemand);
            report.setBasicFacts(basicFacts);
            report.setAvailableCoreEvidence(availableCoreEvidence);
            report.setOverallRiskLevel(overallRiskLevel);
            report.setOverallRiskScore(overallRiskScore);
            report.setOverallRiskScoreReason(overallRiskScoreReason);
            report.setAdvantagesOpportunityAnalysis(advantagesOpportunityAnalysis);
            report.setRiskChallengeAlert(riskChallengeAlert);
            report.setRiskPoint(riskPoint);
            report.setActionSuggestionsSubsequentStrategies(actionSuggestionsSubsequentStrategies);
            report.setReportDate(reportDate);
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());

            // 生成完整报告内容（Markdown格式）
            String fullReport = buildFullReport(report);
            report.setFullReportContent(fullReport);

            // 保存到数据库（不生成PDF，PDF在用户下载时生成）
            riskReportMapper.insert(report);

            log.info("保存风险评估报告成功: reportId={}, userId={}, conversationId={}",
                    reportId, userId, conversationId);

            // 返回完整报告内容（将以artifact状态展示）
            return buildFullReportResponse(report, reportId);

        } catch (Exception e) {
            log.error("保存风险评估报告失败", e);
            return "错误：保存报告失败 - " + e.getMessage();
        }
    }

    @Tool(name = "generate_risk_report", description = "生成风险评估报告（旧版，保留兼容）。使用时机：用户明确要求生成报告、信息收集完成且用户同意生成时。")
    public String generateRiskReport(
            @ToolParam(name = "ourSide", description = "我方当事人名称") String ourSide,
            @ToolParam(name = "ourIdentity", description = "我方身份（原告/被告/申请人/被申请人/债权人/债务人等）") String ourIdentity,
            @ToolParam(name = "otherParty", description = "对方当事人名称") String otherParty,
            @ToolParam(name = "otherIdentity", description = "对方身份") String otherIdentity,
            @ToolParam(name = "caseReason", description = "案由（合同纠纷/劳动争议/知识产权侵权等）") String caseReason,
            @ToolParam(name = "coreDemand", description = "核心诉求，希望通过法律途径达成什么目标") String coreDemand,
            @ToolParam(name = "basicFacts", description = "基本事实，案件的来龙去脉，包含时间、地点、具体事件") String basicFacts,
            @ToolParam(name = "availableCoreEvidence", description = "现有核心证据，手中有哪些证据材料") String availableCoreEvidence,
            @ToolParam(name = "overallRiskLevel", description = "综合风险等级（较低风险/中等风险/较高风险）") String overallRiskLevel,
            @ToolParam(name = "overallRiskScore", description = "风险评分（10-100分，较低10-30，中等40-70，较高80-100）") Integer overallRiskScore,
            @ToolParam(name = "overallRiskScoreReason", description = "风险评分原因，说明为什么给出这个评分") String overallRiskScoreReason,
            @ToolParam(name = "advantagesOpportunityAnalysis", description = "优势与机会分析，列出有利因素") String advantagesOpportunityAnalysis,
            @ToolParam(name = "riskChallengeAlert", description = "风险挑战提示，格式：主要风险：[描述] 风险点：[具体] 影响：[后果]") String riskChallengeAlert,
            @ToolParam(name = "riskPoint", description = "风险点简述，提取核心风险点，每条20字以内，空格分隔") String riskPoint,
            @ToolParam(name = "actionSuggestionsSubsequentStrategies", description = "行动建议与后续策略") String actionSuggestionsSubsequentStrategies,
            @ToolParam(name = "reportDate", description = "报告日期，格式：YYYY年MM月DD日") String reportDate,
            AgentContext ctx) {

        try {
           Long userId = ctx.getUserId();
           Long conversationId = ctx.getConversationId();

            // 生成报告编号
            String reportId = generateReportId();

            // 创建报告实体
            RiskReport report = new RiskReport();
            report.setReportId(reportId);
            report.setUserId(userId);
            report.setConversationId(conversationId);
            report.setOurSide(ourSide);
            report.setOurIdentity(ourIdentity);
            report.setOtherParty(otherParty);
            report.setOtherIdentity(otherIdentity);
            report.setCaseReason(caseReason);
            report.setCoreDemand(coreDemand);
            report.setBasicFacts(basicFacts);
            report.setAvailableCoreEvidence(availableCoreEvidence);
            report.setOverallRiskLevel(overallRiskLevel);
            report.setOverallRiskScore(overallRiskScore);
            report.setOverallRiskScoreReason(overallRiskScoreReason);
            report.setAdvantagesOpportunityAnalysis(advantagesOpportunityAnalysis);
            report.setRiskChallengeAlert(riskChallengeAlert);
            report.setRiskPoint(riskPoint);
            report.setActionSuggestionsSubsequentStrategies(actionSuggestionsSubsequentStrategies);
            report.setReportDate(reportDate);
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());

            // 生成完整报告内容（Markdown格式）
            String fullReport = buildFullReport(report);
            report.setFullReportContent(fullReport);

            // 生成PDF并上传到MinIO
            byte[] pdfBytes = pdfService.generateRiskReportPdf(report);
            String filename = "风险评估报告_" + reportId + ".pdf";
            String minioPath = fileService.uploadPdfToMinio(pdfBytes, filename);
            report.setMinioPath(minioPath);

            // 保存到数据库
            riskReportMapper.insert(report);

            log.info("生成风险评估报告成功: reportId={}, userId={}, conversationId={}, minioPath={}",
                    reportId, userId, conversationId, minioPath);

            // 返回结果（包含完整报告展示）
            return buildSuccessResponseWithFullReport(report);

        } catch (IOException e) {
            log.error("生成风险评估报告PDF失败", e);
            return "错误：生成报告PDF失败 - " + e.getMessage();
        } catch (Exception e) {
            log.error("生成风险评估报告失败", e);
            return "错误：生成报告失败 - " + e.getMessage();
        }
    }

    @Tool(name = "generate_download_link", description = "生成报告下载链接。使用时机：用户明确要求下载报告、已有reportId时。")
    public String generateDownloadLink(
            @ToolParam(name = "reportId", description = "报告编号，如：RPT20260122001") String reportId) {

        try {
            // 查询报告
            LambdaQueryWrapper<RiskReport> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RiskReport::getReportId, reportId);
            RiskReport report = riskReportMapper.selectOne(wrapper);

            if (report == null) {
                return "错误：未找到报告编号为 " + reportId + " 的报告，请检查报告编号是否正确。";
            }

            // 生成下载链接（7天有效）
            String downloadLink = generateLinkUrl(reportId);
            LocalDateTime expireTime = LocalDateTime.now().plusDays(7);

            report.setDownloadLink(downloadLink);
            report.setLinkExpireTime(expireTime);
            report.setUpdatedAt(LocalDateTime.now());

            riskReportMapper.updateById(report);

            log.info("生成下载链接成功: reportId={}, link={}", reportId, downloadLink);

            return buildDownloadLinkResponse(report, downloadLink);

        } catch (Exception e) {
            log.error("生成下载链接失败: reportId={}", reportId, e);
            return "错误：生成下载链接失败 - " + e.getMessage();
        }
    }

    /**
     * 生成报告编号
     */
    private String generateReportId() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RPT" + dateStr + uuid;
    }

    /**
     * 生成下载链接URL
     */
    private String generateLinkUrl(String reportId) {
        // 使用配置的服务器基础 URL
        return serverBaseUrl + "/api/report/download/" + reportId;
    }

    /**
     * 构建完整报告内容（Markdown格式）
     */
    private String buildFullReport(RiskReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 关于\"").append(report.getOurSide()).append("与").append(report.getOtherParty())
                .append(report.getCaseReason()).append("\"案的风险评估报告\n\n");

        sb.append("**致：**").append(report.getOurSide()).append("\n");
        sb.append("**日期：**").append(report.getReportDate()).append("\n");
        sb.append("**案由：**").append(report.getCaseReason()).append("\n\n");

        sb.append("---\n\n");

        sb.append("## 一、报告基础与声明\n\n");
        sb.append("本报告基于").append(report.getReportDate()).append("提供的案件所述信息作出。");
        sb.append("本报告旨在对案件进行初步的、方向性的风险评估，并非对诉讼结果的承诺。");
        sb.append("随着案件证据的补充和程序的推进，评估结论可能发生变化。");
        sb.append("本报告为内部法律分析文件，请注意保密。\n\n");

        sb.append("---\n\n");

        sb.append("## 二、案件核心事实梳理\n\n");
        sb.append("**").append(report.getOurIdentity()).append("：**").append(report.getOurSide()).append("\n\n");
        sb.append("**").append(report.getOtherIdentity()).append("：**").append(report.getOtherParty()).append("\n\n");
        sb.append("**核心诉求：**").append(report.getCoreDemand()).append("\n\n");

        sb.append("### 基本事实\n\n");
        sb.append(report.getBasicFacts()).append("\n\n");

        sb.append("### 现有核心证据\n\n");
        sb.append(report.getAvailableCoreEvidence()).append("\n\n");

        sb.append("---\n\n");

        sb.append("## 三、初步风险评估\n\n");
        sb.append("**综合风险：**").append(report.getOverallRiskScore()).append("分（").append(report.getOverallRiskLevel()).append("）\n\n");
        sb.append("**评分说明：**\n\n").append(report.getOverallRiskScoreReason()).append("\n\n");

        sb.append("### （一）优势与机会分析\n\n");
        sb.append(report.getAdvantagesOpportunityAnalysis()).append("\n\n");

        sb.append("### （二）风险与挑战提示\n\n");
        sb.append(report.getRiskChallengeAlert()).append("\n\n");

        sb.append("**核心风险点：**").append(report.getRiskPoint()).append("\n\n");

        sb.append("---\n\n");

        sb.append("## 四、行动建议与后续策略\n\n");
        sb.append(report.getActionSuggestionsSubsequentStrategies()).append("\n\n");

        sb.append("---\n\n");
        sb.append("*本报告由法律助手自动生成，仅供参考。建议咨询专业律师获取更详细的法律意见。*\n");

        return sb.toString();
    }

    /**
     * 构建完整报告响应（用于ReportGenerationAgent）
     */
    private String buildFullReportResponse(RiskReport report, String reportId) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 风险评估报告已生成\n\n");
        sb.append("---\n\n");
        sb.append("**【完整报告内容】**\n\n");
        sb.append(report.getFullReportContent());
        sb.append("\n---\n\n");
        sb.append("**报告编号：**").append(reportId).append("\n\n");
        sb.append("请问需要下载PDF报告吗？（回复\"下载\"即可获取临时下载链接）");
        return sb.toString();
    }

    /**
     * 构建成功响应（包含完整报告展示）- 旧版
     */
    @Deprecated
    private String buildSuccessResponseWithFullReport(RiskReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 报告已生成成功！\n\n");
        sb.append("---\n\n");
        sb.append("**【完整报告内容】**\n\n");
        sb.append(report.getFullReportContent());
        sb.append("\n---\n\n");
        sb.append("**报告编号：**").append(report.getReportId()).append("\n\n");
        sb.append("请问您需要下载此报告吗？（回复\"下载\"即可获取PDF下载链接）");
        return sb.toString();
    }

    /**
     * 构建下载链接响应
     */
    private String buildDownloadLinkResponse(RiskReport report, String downloadLink) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ 下载链接已生成！\n\n");
        sb.append("📥 **下载地址：**").append(downloadLink).append("\n\n");
        sb.append("**⏰ 链接有效期：7天**\n");
        sb.append("**📄 报告格式：PDF**\n");
        sb.append("**🔐 请妥善保管报告，注意保密**\n\n");
        sb.append("还有其他需要帮助的吗？");
        return sb.toString();
    }
}
