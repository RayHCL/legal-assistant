package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.legal.assistant.entity.ConsultTemplates;
import com.legal.assistant.enums.AgentType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConsultTemplatesMapper extends BaseMapper<ConsultTemplates> {

    /**
     * 随机查询指定数量的启用状态的咨询模版
     * @param limit 查询数量
     * @param agentType Agent类型，可为空
     * @return 随机咨询模版列表
     */
    List<ConsultTemplates> selectRandomTemplates(@Param("limit") int limit, @Param("agentType") String agentType);
}
