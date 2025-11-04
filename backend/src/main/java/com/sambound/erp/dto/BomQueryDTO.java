package com.sambound.erp.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM查询结果DTO（支持树形结构）
 */
public record BomQueryDTO(
    // 物料信息
    Long materialId,
    String materialCode,
    String materialName,
    String materialSpecification, // 物料型号
    String materialGroupCode,
    String materialGroupName,
    
    // BOM信息
    Long bomId,
    String bomVersion,
    String bomName,
    
    // 子项信息（用于正查时显示父项与子项的关系）
    Integer sequence,
    BigDecimal numerator,
    BigDecimal denominator,
    BigDecimal scrapRate,
    String childBomVersion, // 子项配置的BOM版本
    String childUnitCode, // 子项单位编码
    String childUnitName, // 子项单位名称
    
    // 子节点列表（树形结构）
    List<BomQueryDTO> children
) {
    /**
     * 创建根节点（无子项信息）
     */
    public static BomQueryDTO createRoot(
            Long materialId,
            String materialCode,
            String materialName,
            String materialSpecification,
            String materialGroupCode,
            String materialGroupName,
            Long bomId,
            String bomVersion,
            String bomName,
            List<BomQueryDTO> children) {
        return new BomQueryDTO(
                materialId,
                materialCode,
                materialName,
                materialSpecification,
                materialGroupCode,
                materialGroupName,
                bomId,
                bomVersion,
                bomName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                children);
    }
    
    /**
     * 创建子节点（包含子项信息）
     */
    public static BomQueryDTO createChild(
            Long materialId,
            String materialCode,
            String materialName,
            String materialSpecification,
            String materialGroupCode,
            String materialGroupName,
            Long bomId,
            String bomVersion,
            String bomName,
            Integer sequence,
            BigDecimal numerator,
            BigDecimal denominator,
            BigDecimal scrapRate,
            String childBomVersion,
            String childUnitCode,
            String childUnitName,
            List<BomQueryDTO> children) {
        return new BomQueryDTO(
                materialId,
                materialCode,
                materialName,
                materialSpecification,
                materialGroupCode,
                materialGroupName,
                bomId,
                bomVersion,
                bomName,
                sequence,
                numerator,
                denominator,
                scrapRate,
                childBomVersion,
                childUnitCode,
                childUnitName,
                children);
    }
}

