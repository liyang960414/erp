package com.sambound.erp.service.importing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的数据解析工具
 * 处理日期、数字、布尔值等数据解析
 */
public class ImportDataParser {

    private static final DateTimeFormatter DATE_FORMATTER_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_2 = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DATE_FORMATTER_3 = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = new ArrayList<>();

    static {
        DATE_FORMATTERS.add(DATE_FORMATTER_1);
        DATE_FORMATTERS.add(DATE_FORMATTER_2);
        DATE_FORMATTERS.add(DATE_FORMATTER_3);
    }

    private ImportDataParser() {
        // 工具类，不允许实例化
    }

    /**
     * 清理字符串（trim、null 处理）
     *
     * @param value 原始值
     * @return 清理后的值，如果为空则返回 null
     */
    public static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 解析日期（支持多种格式）
     *
     * @param value 日期字符串
     * @return 解析后的日期，如果解析失败返回 null
     */
    public static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String dateStr = value.trim();
        // 统一替换分隔符
        if (dateStr.contains("/")) {
            dateStr = dateStr.replace("/", "-");
        } else if (dateStr.contains(".")) {
            dateStr = dateStr.replace(".", "-");
        }

        // 尝试多种格式
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }

        return null;
    }

    /**
     * 解析必填日期
     *
     * @param value     日期字符串
     * @param rowNumber 行号
     * @param field     字段名
     * @param errors    错误收集器
     * @return 解析后的日期，如果解析失败返回 null 并记录错误
     */
    public static LocalDate parseRequiredDate(String value, int rowNumber, String field, ImportErrorCollector errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.addError("导入", rowNumber, field, field + "为空");
            return null;
        }

        LocalDate date = parseDate(value);
        if (date == null) {
            errors.addError("导入", rowNumber, field, "日期格式错误: " + value);
        }
        return date;
    }

    /**
     * 解析数字（处理千分符、空值等）
     *
     * @param value 数字字符串
     * @return 解析后的数字，如果解析失败返回 null
     */
    public static BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            // 去除千分符（逗号）和其他可能的格式字符
            String cleaned = value.trim().replace(",", "").replace(" ", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析必填数字
     *
     * @param value     数字字符串
     * @param rowNumber 行号
     * @param field     字段名
     * @param errors    错误收集器
     * @return 解析后的数字，如果解析失败返回 null 并记录错误
     */
    public static BigDecimal parseRequiredDecimal(String value, int rowNumber, String field, ImportErrorCollector errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.addError("导入", rowNumber, field, field + "为空");
            return null;
        }

        BigDecimal decimal = parseDecimal(value);
        if (decimal == null) {
            errors.addError("导入", rowNumber, field, "数字格式错误: " + value);
        }
        return decimal;
    }

    /**
     * 解析整数
     *
     * @param value 整数字符串
     * @return 解析后的整数，如果解析失败返回 null
     */
    public static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            // 去除千分符（逗号）和其他可能的格式字符
            String cleaned = value.trim().replace(",", "").replace(" ", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析必填整数
     *
     * @param value     整数字符串
     * @param rowNumber 行号
     * @param field     字段名
     * @param errors    错误收集器
     * @return 解析后的整数，如果解析失败返回 null 并记录错误
     */
    public static Integer parseRequiredInteger(String value, int rowNumber, String field, ImportErrorCollector errors) {
        if (value == null || value.trim().isEmpty()) {
            errors.addError("导入", rowNumber, field, field + "为空");
            return null;
        }

        Integer integer = parseInteger(value);
        if (integer == null) {
            errors.addError("导入", rowNumber, field, "整数格式错误: " + value);
        }
        return integer;
    }

    /**
     * 解析布尔值
     * 支持: true/false, 1/0, 是/否, yes/no
     *
     * @param value 布尔值字符串
     * @return 解析后的布尔值，如果为空返回 false
     */
    public static Boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String normalized = value.trim().toUpperCase();
        return "TRUE".equals(normalized) || "1".equals(normalized) || "是".equals(normalized) || "YES".equals(normalized);
    }

    /**
     * 解析布尔值（允许 null）
     *
     * @param value 布尔值字符串
     * @return 解析后的布尔值，如果为空返回 null
     */
    public static Boolean parseOptionalBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String normalized = value.trim().toUpperCase();
        if ("TRUE".equals(normalized) || "1".equals(normalized) || "是".equals(normalized) || "YES".equals(normalized)) {
            return true;
        } else if ("FALSE".equals(normalized) || "0".equals(normalized) || "否".equals(normalized) || "NO".equals(normalized)) {
            return false;
        }
        return null;
    }
}


