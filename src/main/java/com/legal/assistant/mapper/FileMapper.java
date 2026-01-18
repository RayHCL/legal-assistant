package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.File;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<File> {
}
