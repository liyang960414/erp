package com.sambound.erp.service.importing.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 物料组导入 Excel 行数据
 */
@Data
public class MaterialGroupExcelRow {

    @ExcelProperty(value = {"FBillHead(BOS_FORMGROUP)", "*单据头(实体序号)"})
    private String billHead;

    @ExcelProperty(value = {"FParentId", "(单据头)父节点"})
    private String parentCode;

    @ExcelProperty(value = {"FNumber", "*(单据头)编码"})
    private String code;

    @ExcelProperty(value = {"FName#2052", "*(单据头)名称#中文(简体)"})
    private String name;

    @ExcelProperty(value = {"FDescription#2052", "(单据头)描述#中文(简体)"})
    private String description;
}

