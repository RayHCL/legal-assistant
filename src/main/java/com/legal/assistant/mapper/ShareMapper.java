package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.Share;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShareMapper extends BaseMapper<Share> {
    
    @Select("SELECT * FROM share WHERE share_id = #{shareId}")
    Share selectByShareId(String shareId);
}
