package com.legal.assistant.agents.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hcl
 * @date 2026-01-20
 * @description 提供给Agent使用的时间工具
 */
@Slf4j
@Component
public class DateToolService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER_SIMPLE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Tool(name = "getCurrentTime", description = "获取当前时间，支持指定时区")
    public String getCurrentTime(
            @ToolParam(name = "timezone", description = "时区，例如：Asia/Shanghai（中国标准时间）、Asia/Tokyo（日本时间）、America/New_York（纽约时间），默认为Asia/Shanghai") String timezone) {

        try {
            ZoneId zoneId = timezone != null && !timezone.isEmpty()
                    ? ZoneId.of(timezone)
                    : ZoneId.of("Asia/Shanghai");

            LocalDateTime now = LocalDateTime.now(zoneId);
            String formattedTime = now.format(DATE_FORMATTER);

            log.info("获取当前时间: timezone={}, time={}", zoneId, formattedTime);

            return buildTimeResponse(now, zoneId);
        } catch (Exception e) {
            log.error("获取时间失败: timezone={}", timezone, e);
            return "错误: 无效的时区 - " + timezone + "。请使用标准时区格式，例如：Asia/Shanghai";
        }
    }

    @Tool(name = "getCurrentDate", description = "获取当前日期（不包含时间）")
    public String getCurrentDate(
            @ToolParam(name = "timezone", description = "时区，例如：Asia/Shanghai，默认为Asia/Shanghai") String timezone) {

        try {
            ZoneId zoneId = timezone != null && !timezone.isEmpty()
                    ? ZoneId.of(timezone)
                    : ZoneId.of("Asia/Shanghai");

            LocalDateTime now = LocalDateTime.now(zoneId);
            String formattedDate = now.format(DATE_FORMATTER_SIMPLE);

            log.info("获取当前日期: timezone={}, date={}", zoneId, formattedDate);

            return "当前日期: " + formattedDate + " (时区: " + zoneId + ")";
        } catch (Exception e) {
            log.error("获取日期失败: timezone={}", timezone, e);
            return "错误: 无效的时区 - " + timezone;
        }
    }

    /**
     * 构建详细的时间响应
     */
    private String buildTimeResponse(LocalDateTime dateTime, ZoneId zoneId) {
        Map<String, String> timeInfo = new HashMap<>();
        timeInfo.put("datetime", dateTime.format(DATE_FORMATTER));
        timeInfo.put("date", dateTime.format(DATE_FORMATTER_SIMPLE));
        timeInfo.put("year", String.valueOf(dateTime.getYear()));
        timeInfo.put("month", String.valueOf(dateTime.getMonthValue()));
        timeInfo.put("day", String.valueOf(dateTime.getDayOfMonth()));
        timeInfo.put("hour", String.valueOf(dateTime.getHour()));
        timeInfo.put("minute", String.valueOf(dateTime.getMinute()));
        timeInfo.put("second", String.valueOf(dateTime.getSecond()));
        timeInfo.put("timezone", zoneId.getId());
        timeInfo.put("dayOfWeek", dateTime.getDayOfWeek().toString());

        StringBuilder response = new StringBuilder();
        response.append("当前时间信息:\n");
        response.append("- 完整时间: ").append(timeInfo.get("datetime")).append("\n");
        response.append("- 日期: ").append(timeInfo.get("date")).append("\n");
        response.append("- 时区: ").append(timeInfo.get("timezone")).append("\n");
        response.append("- 星期: ").append(timeInfo.get("dayOfWeek")).append("\n");
        response.append("- 年: ").append(timeInfo.get("year")).append("\n");
        response.append("- 月: ").append(timeInfo.get("month")).append("\n");
        response.append("- 日: ").append(timeInfo.get("day")).append("\n");
        response.append("- 时: ").append(timeInfo.get("hour")).append("\n");
        response.append("- 分: ").append(timeInfo.get("minute")).append("\n");
        response.append("- 秒: ").append(timeInfo.get("second"));

        return response.toString();
    }
}
