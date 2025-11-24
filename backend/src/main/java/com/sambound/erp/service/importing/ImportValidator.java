package com.sambound.erp.service.importing;

import java.util.Collection;
import java.util.Map;

/**
 * 通用验证工具
 * 提供必填字段验证、格式验证、关联实体存在性验证等功能
 */
public class ImportValidator {

    private final ImportErrorCollector errorCollector;

    public ImportValidator(ImportErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
    }

    /**
     * 验证必填字段
     *
     * @param value     字段值
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @return 是否通过验证
     */
    public boolean validateRequired(String value, String section, int rowNumber, String field) {
        if (value == null || value.trim().isEmpty()) {
            errorCollector.addValidationError(section, rowNumber, field, field + "不能为空");
            return false;
        }
        return true;
    }

    /**
     * 验证必填字段（对象）
     *
     * @param value     字段值
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @return 是否通过验证
     */
    public boolean validateRequired(Object value, String section, int rowNumber, String field) {
        if (value == null) {
            errorCollector.addValidationError(section, rowNumber, field, field + "不能为空");
            return false;
        }
        return true;
    }

    /**
     * 验证关联实体是否存在
     *
     * @param entity    实体对象
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @param code      实体编码（用于错误消息）
     * @return 是否通过验证
     */
    public boolean validateEntityExists(Object entity, String section, int rowNumber, String field, String code) {
        if (entity == null) {
            errorCollector.addDataError(section, rowNumber, field, String.format("%s不存在: %s", field, code));
            return false;
        }
        return true;
    }

    /**
     * 验证关联实体是否存在（从缓存中查找）
     *
     * @param cache     实体缓存
     * @param code      实体编码
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @param entityName 实体名称（用于错误消息）
     * @return 是否通过验证
     */
    public <T> boolean validateEntityExistsInCache(Map<String, T> cache, String code,
                                                   String section, int rowNumber, String field, String entityName) {
        if (code == null || code.trim().isEmpty()) {
            errorCollector.addValidationError(section, rowNumber, field, entityName + "编码不能为空");
            return false;
        }

        T entity = cache.get(code.trim());
        if (entity == null) {
            errorCollector.addDataError(section, rowNumber, field, String.format("%s不存在: %s", entityName, code));
            return false;
        }
        return true;
    }

    /**
     * 验证编码格式（非空且已trim）
     *
     * @param code      编码
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @return 清理后的编码，如果验证失败返回 null
     */
    public String validateCode(String code, String section, int rowNumber, String field) {
        if (!validateRequired(code, section, rowNumber, field)) {
            return null;
        }
        return code.trim();
    }

    /**
     * 验证集合不为空
     *
     * @param collection 集合
     * @param section    模块名称
     * @param rowNumber  行号
     * @param field      字段名
     * @return 是否通过验证
     */
    public boolean validateNotEmpty(Collection<?> collection, String section, int rowNumber, String field) {
        if (collection == null || collection.isEmpty()) {
            errorCollector.addValidationError(section, rowNumber, field, field + "不能为空");
            return false;
        }
        return true;
    }

    /**
     * 验证数字范围
     *
     * @param value     数字值
     * @param min       最小值
     * @param max       最大值
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @return 是否通过验证
     */
    public boolean validateRange(Number value, Number min, Number max, String section, int rowNumber, String field) {
        if (value == null) {
            return true; // 空值由必填验证处理
        }

        double doubleValue = value.doubleValue();
        double minValue = min.doubleValue();
        double maxValue = max.doubleValue();

        if (doubleValue < minValue || doubleValue > maxValue) {
            errorCollector.addValidationError(section, rowNumber, field,
                    String.format("%s必须在%s和%s之间", field, min, max));
            return false;
        }
        return true;
    }

    /**
     * 验证字符串长度
     *
     * @param value     字符串值
     * @param maxLength 最大长度
     * @param section   模块名称
     * @param rowNumber 行号
     * @param field     字段名
     * @return 是否通过验证
     */
    public boolean validateMaxLength(String value, int maxLength, String section, int rowNumber, String field) {
        if (value == null) {
            return true; // 空值由必填验证处理
        }

        if (value.length() > maxLength) {
            errorCollector.addValidationError(section, rowNumber, field,
                    String.format("%s长度不能超过%d个字符", field, maxLength));
            return false;
        }
        return true;
    }
}



