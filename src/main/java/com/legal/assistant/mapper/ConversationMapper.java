package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
