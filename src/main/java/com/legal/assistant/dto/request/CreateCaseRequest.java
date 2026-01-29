package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "创建案件请求")
public class CreateCaseRequest {


    @Schema(description = "纠纷类型", example = "合同纠纷")
    @NotBlank(message = "纠纷类型不能为空")
    private String disputeType;

    @Schema(description = "争议金额（人民币）")
    @NotNull(message = "争议金额不为空")
    private BigDecimal disputeAmount;

    @Schema(description = "纠纷描述")
    @NotBlank(message = "纠纷描述不能为空")
    private String disputeDescription;

    @Schema(description = "咨询人诉求")
    @NotBlank(message = "咨询人诉求不能为空")
    private String consultantDemand;

    @Valid
    @NotNull(message = "当事人信息不能为空")
    @Schema(description = "当事人信息列表")
    private List<CreatePartyRequest> parties;

    @Schema(description = "关联的文件ID列表")
    private List<String> fileIds;
}