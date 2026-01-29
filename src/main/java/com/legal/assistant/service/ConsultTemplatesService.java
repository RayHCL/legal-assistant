package com.legal.assistant.service;


import com.legal.assistant.entity.ConsultTemplates;
import com.legal.assistant.enums.AgentType;

import java.util.List;

/**
 * 咨询模版服务
 */
public interface ConsultTemplatesService {

    /**
     * 随机获取指定数量的咨询模版
     * @param limit 查询数量，默认5条
     * @param agentType Agent类型，可为空
     * @return 咨询模版列表
     */
    List<ConsultTemplates> getRandomTemplates(Integer limit, AgentType agentType);
}
