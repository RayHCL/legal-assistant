package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT COUNT(*) FROM message WHERE conversation_id = #{conversationId}")
    Integer countByConversationId(Long conversationId);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> selectByConversationId(Long conversationId);

    @Update("UPDATE message SET answer = #{answer}, status = #{status}, updated_at = NOW() WHERE id = #{id}")
    void updateAnswerAndStatus(@Param("id") Long id, @Param("answer") String answer, @Param("status") String status);
}
