package com.legal.assistant.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间工具类
 */
public class TimeUtils {
    
    /**
     * 将LocalDateTime转换为时间戳（毫秒）
     */
    public static Long toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }
    
    /**
     * 将时间戳（毫秒）转换为LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                ZoneId.of("Asia/Shanghai")
        );
    }
}
