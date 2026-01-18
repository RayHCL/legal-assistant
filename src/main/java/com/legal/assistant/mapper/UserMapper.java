package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM user WHERE phone = #{phone} AND is_deleted = 0")
    User selectByPhone(String phone);
}
