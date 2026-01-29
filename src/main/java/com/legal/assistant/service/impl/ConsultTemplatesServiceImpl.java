package com.legal.assistant.service.impl;


import com.legal.assistant.entity.ConsultTemplates;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.mapper.ConsultTemplatesMapper;
import com.legal.assistant.service.ConsultTemplatesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultTemplatesServiceImpl implements ConsultTemplatesService {

    private final ConsultTemplatesMapper consultTemplatesMapper;

    @Override
    public List<ConsultTemplates> getRandomTemplates(Integer limit, AgentType agentType) {
        int count = limit != null && limit > 0 ? limit : 5;
        List<ConsultTemplates> templates = consultTemplatesMapper.selectRandomTemplates(count, agentType.name());
        log.info("随机获取咨询模版 - agentType: {}, 请求数量: {}, 实际返回: {}", agentType, count, templates.size());
        return templates;
    }
}
