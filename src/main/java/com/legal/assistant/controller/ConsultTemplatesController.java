package com.legal.assistant.controller;


import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.entity.ConsultTemplates;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.service.ConsultTemplatesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Validated
@Tag(name = "咨询模版", description = "常见问题咨询模版接口")
public class ConsultTemplatesController {

    private final ConsultTemplatesService consultTemplatesService;

    @GetMapping("/random")
    @Operation(summary = "随机获取咨询模版", description = "随机获取指定数量的启用状态的咨询模版，支持按Agent类型筛选")
    @NoAuth
    public Result<List<ConsultTemplates>> getRandomTemplates(
            @Parameter(description = "获取数量，1-20", example = "5")
            @RequestParam(required = false) @Min(1) @Max(20) Integer limit,
            @Parameter(description = "Agent类型筛选", schema = @Schema(implementation = AgentType.class))
            @RequestParam(required = false) AgentType agentType) {
        List<ConsultTemplates> templates = consultTemplatesService.getRandomTemplates(limit, agentType);
        return Result.success(templates);
    }
}