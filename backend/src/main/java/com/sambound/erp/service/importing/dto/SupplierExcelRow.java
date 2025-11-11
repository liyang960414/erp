package com.sambound.erp.service.importing.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 供应商导入 Excel/CSV 行数据
 */
@Data
public class SupplierExcelRow {

    @ExcelProperty(value = {"FBillHead(BD_Supplier)", "*单据头(序号)"})
    private String billHead;

    @ExcelProperty(value = {"FNumber", "(单据头)编码"})
    private String code;

    @ExcelProperty(value = {"FName#2052", "*(单据头)名称#中文(简体)"})
    private String name;

    @ExcelProperty(value = {"FShortName#2052", "(单据头)简称#中文(简体)"})
    private String shortName;

    @ExcelProperty(value = {"FShortNameINEn#2052", "(单据头)英文名称#中文(简体)"})
    private String englishName;

    @ExcelProperty(value = {"FDescription#2052", "(单据头)描述#中文(简体)"})
    private String description;
}

