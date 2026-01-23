package com.legal.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legal.assistant.entity.RiskReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风险评估报告Mapper
 */
@Mapper
public interface RiskReportMapper extends BaseMapper<RiskReport> {
}
