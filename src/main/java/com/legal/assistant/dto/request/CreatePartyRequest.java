package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建当事人请求")
public class CreatePartyRequest {



    @Schema(description = "诉讼地位", example = "`plaintiff` / 原告  `defendant` / 被告")
    @NotBlank(message = "诉讼地位不能为空")
    private String litigationStatus;

    @Schema(description = "当事人类型", example = "“自然人” naturalPerson、“法人” legalPerson、“非法人组织” unincorporatedOrganization")
    @NotBlank(message = "当事人类型不能为空")
    private String partyType;

    @NotBlank(message = "当事人名称不能为空")
    @Schema(description = "当事人名称，或公司名称", example = "张三")
    private String partyName;

    @Schema(description = "咨询人标识", example = "1")
    @NotNull(message = "咨询人标识不能为空")
    private Integer consultantIdentifier;
}