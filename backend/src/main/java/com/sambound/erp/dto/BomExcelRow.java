package com.sambound.erp.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * BOM导入 Excel 行数据
 */
@Data
public class BomExcelRow {
    
    // ============ 单据头字段（父项BOM信息） ============
    
    @ExcelProperty(value = {"FBillHead(ENG_BOM)", "*单据头(序号)"})
    private String billHead;
    
    @ExcelProperty(value = {"FNumber", "(单据头)BOM版本/phiên bản"})
    private String version;
    
    @ExcelProperty(value = {"FName#2052", "(单据头)BOM简称#中文(简体)"})
    private String name;
    
    @ExcelProperty(value = {"FBOMCATEGORY", "*(单据头)BOM分类"})
    private String category;
    
    @ExcelProperty(value = {"FBOMUSE", "*(单据头)BOM用途"})
    private String usage;
    
    @ExcelProperty(value = {"FGroup", "(单据头)BOM分组#编码"})
    private String groupCode;
    
    @ExcelProperty(value = {"FGroup#Name", "(单据头)BOM分组#名称"})
    private String groupName;
    
    @ExcelProperty(value = {"FMATERIALID", "*(单据头)父项物料编码/Mã vật liệu#编码"})
    private String materialCode;
    
    @ExcelProperty(value = {"FMATERIALID#Name", "(单据头)父项物料编码/Mã vật liệu#名称"})
    private String materialName;
    
    @ExcelProperty(value = {"FUNITID", "*(单据头)父项物料单位#编码"})
    private String unitCode;
    
    @ExcelProperty(value = {"FUNITID#Name", "(单据头)父项物料单位#名称"})
    private String unitName;
    
    @ExcelProperty(value = {"FDescription#2052", "(单据头)描述#中文(简体)"})
    private String description;
    
    // ============ 子项明细字段 ============
    
    @ExcelProperty(value = {"FTreeEntity", "*子项明细(序号)"})
    private String treeEntity;
    
    @ExcelProperty(value = {"FReplaceGroup", "(子项明细)项次"})
    private String sequence;
    
    @ExcelProperty(value = {"FMATERIALIDCHILD", "*(子项明细)子项物料编码/Mã vật liệu#编码"})
    private String childMaterialCode;
    
    @ExcelProperty(value = {"FMATERIALIDCHILD#Name", "(子项明细)子项物料编码/Mã vật liệu#名称"})
    private String childMaterialName;
    
    @ExcelProperty(value = {"FCHILDUNITID", "*(子项明细)子项单位/Đơn vị#编码"})
    private String childUnitCode;
    
    @ExcelProperty(value = {"FCHILDUNITID#Name", "(子项明细)子项单位/Đơn vị#名称"})
    private String childUnitName;
    
    @ExcelProperty(value = {"FNUMERATOR", "(子项明细)用量:分子/lượng dùng"})
    private String numerator;
    
    @ExcelProperty(value = {"FDENOMINATOR", "(子项明细)用量:分母/lượng dùng"})
    private String denominator;
    
    @ExcelProperty(value = {"FSCRAPRATE", "(子项明细)变动损耗率%/tỉ lệ hụt"})
    private String scrapRate;
    
    @ExcelProperty(value = {"FBOMID", "(子项明细)子项BOM版本#编码"})
    private String childBomVersion;
    
    @ExcelProperty(value = {"FBOMID#Name", "(子项明细)子项BOM版本#名称"})
    private String childBomVersionName;
    
    @ExcelProperty(value = {"FMEMO#2052", "(子项明细)备注/chú thích#中文(简体)"})
    private String memo;
}

