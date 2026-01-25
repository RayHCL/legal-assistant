package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险评估报告实体
 */
@Data
@TableName("risk_report")
public class RiskReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 报告编号
     */
    private String reportId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 我方当事人
     */
    private String ourSide;

    /**
     * 我方身份
     */
    private String ourIdentity;

    /**
     * 对方当事人
     */
    private String otherParty;

    /**
     * 对方身份
     */
    private String otherIdentity;

    /**
     * 案由
     */
    private String caseReason;

    /**
     * 核心诉求
     */
    private String coreDemand;

    /**
     * 基本事实
     */
    private String basicFacts;

    /**
     * 现有核心证据
     */
    private String availableCoreEvidence;

    /**
     * 报告日期
     */
    private String reportDate;

    /**
     * 综合风险等级
     */
    private String overallRiskLevel;

    /**
     * 风险评分
     */
    private Integer overallRiskScore;

    /**
     * 风险评分原因
     */
    private String overallRiskScoreReason;

    /**
     * 优势与机会分析
     */
    private String advantagesOpportunityAnalysis;

    /**
     * 风险挑战提示
     */
    private String riskChallengeAlert;

    /**
     * 风险点简述
     */
    private String riskPoint;

    /**
     * 行动建议与后续策略
     */
    private String actionSuggestionsSubsequentStrategies;

    /**
     * 完整报告内容（Markdown格式）
     */
    private String fullReportContent;

    /**
     * MinIO存储路径（PDF文件）
     */
    private String minioPath;

    /**
     * 报告文件路径（PDF）- 已废弃，使用minioPath
     */
    @Deprecated
    private String reportFilePath;

    /**
     * 下载链接
     */
    private String downloadLink;

    /**
     * 下载链接过期时间
     */
    private LocalDateTime linkExpireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
