package com.legal.assistant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legal.assistant.entity.RiskReport;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.RiskReportMapper;
import com.legal.assistant.service.PdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 报告下载接口
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@Tag(name = "报告管理", description = "风险评估报告下载")
public class ReportController {

    @Autowired
    private RiskReportMapper riskReportMapper;

    @Autowired
    private PdfService pdfService;

    /**
     * 下载报告（PDF 格式）
     */
    @GetMapping("/download/{reportId}")
    @Operation(summary = "下载风险评估报告", description = "根据报告编号下载 PDF 格式的报告文件")
    public ResponseEntity<byte[]> downloadReport(
            @Parameter(description = "报告编号，如：RPT20260122001") @PathVariable String reportId) {

        // 查询报告
        LambdaQueryWrapper<RiskReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RiskReport::getReportId, reportId);
        RiskReport report = riskReportMapper.selectOne(wrapper);

        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "报告不存在: " + reportId);
        }

        // 检查链接是否过期
        if (report.getLinkExpireTime() != null && LocalDateTime.now().isAfter(report.getLinkExpireTime())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "下载链接已过期");
        }

        // 转换为 PDF（使用 poi-tl + Aspose）
        byte[] pdfBytes;
        try {
            pdfBytes = pdfService.generateRiskReportPdf(report);
        } catch (Exception e) {
            log.error("PDF 生成失败: reportId={}", reportId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "生成 PDF 失败: " + e.getMessage());
        }

        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                new String(("风险评估报告_" + reportId + ".pdf").getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.ISO_8859_1));
        headers.setContentLength(pdfBytes.length);

        log.info("下载报告: reportId={}, userId={}, 大小={} bytes",
                reportId, report.getUserId(), pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }


}
