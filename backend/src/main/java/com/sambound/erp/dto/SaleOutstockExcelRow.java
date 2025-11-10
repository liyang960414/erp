package com.sambound.erp.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class SaleOutstockExcelRow {

    @ExcelProperty(value = {"FBillHead(SAL_OUTSTOCK)", "*基本信息(序号)"})
    private String billHead;

    @ExcelProperty(value = {"FBillNo", "(基本信息)单据编号"})
    private String billNo;

    @ExcelProperty(value = {"FDate", "*(基本信息)日期"})
    private String outstockDate;

    @ExcelProperty(value = {"FNote", "(基本信息)备注"})
    private String note;

    @ExcelProperty(value = {"FEntity", "*明细信息(序号)"})
    private String entrySequence;

    @ExcelProperty(value = {"FMaterialID", "*(明细信息)物料编码#编码"})
    private String materialCode;

    @ExcelProperty(value = {"FMaterialID#Name", "(明细信息)物料编码#名称"})
    private String materialName;

    @ExcelProperty(value = {"FUnitID", "*(明细信息)库存单位#编码"})
    private String unitCode;

    @ExcelProperty(value = {"FUnitID#Name", "(明细信息)库存单位#名称"})
    private String unitName;

    @ExcelProperty(value = {"FRealQty", "(明细信息)实发数量"})
    private String realQty;

    @ExcelProperty(value = {"FEntrynote", "(明细信息)备注"})
    private String entryNote;

    @ExcelProperty(value = {"F_kd_Text", "(明细信息)本司WO#"})
    private String woNumber;

    @ExcelProperty(value = {"FSOEntryId", "(明细信息)销售订单EntryId"})
    private String saleOrderEntryId;
}

