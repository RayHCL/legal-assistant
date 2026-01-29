package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "案件分析请求")
public class CaseAnalysisRequest {

    @NotBlank(message = "案件ID不能为空")
    @Schema(description = "案件ID", example = "caseId")
    private Long caseId;

}