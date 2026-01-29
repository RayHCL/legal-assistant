package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("consult_templates")
@Schema(description = "咨询模版字典")
public class ConsultTemplates {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "问题标题")
    private String question;

    @Schema(description = "Agent类型")
    private String agentType;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "排序号，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createdAt;

    @Schema(description = "更新时间（毫秒时间戳）")
    private Long updatedAt;
}
