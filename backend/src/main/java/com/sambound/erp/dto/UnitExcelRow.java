package com.sambound.erp.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 单位导入 Excel 行数据
 */
@Data
public class UnitExcelRow {
    
    @ExcelProperty(value = {"FBillHead(BD_UNIT)", "*单据头(序号)"})
    private String billHead;
    
    @ExcelProperty(value = {"FNumber", "(单据头)编码"})
    private String code;
    
    @ExcelProperty(value = {"FName#2052", "*(单据头)名称#中文(简体)"})
    private String name;
    
    @ExcelProperty(value = {"FUnitGroupId", "*(单据头)所属组别#编码"})
    private String unitGroupCode;
    
    @ExcelProperty(value = {"FUnitGroupId#Name", "(单据头)所属组别#名称"})
    private String unitGroupName;
    
    @ExcelProperty(value = {"*Split*1", "间隔列"})
    private String split;
    
    @ExcelProperty(value = {"SubHeadEntity", "*转换率(序号)"})
    private String subHeadEntity;
    
    @ExcelProperty(value = {"FConvertType", "(转换率)换算类型"})
    private String convertType;
    
    @ExcelProperty(value = {"FConvertNumerator", "(转换率)换算分子"})
    private String numerator;
    
    @ExcelProperty(value = {"FConvertDenominator", "(转换率)换算分母"})
    private String denominator;
}

