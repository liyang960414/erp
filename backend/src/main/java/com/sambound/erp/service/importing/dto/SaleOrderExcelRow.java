package com.sambound.erp.service.importing.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 销售订单导入 Excel 行数据
 */
@Data
public class SaleOrderExcelRow {

    @ExcelProperty(value = {"FBillHead(SAL_SaleOrder)", "*基本信息(序号)"})
    private String billHead;

    @ExcelProperty(value = {"FBillNo", "(基本信息)单据编号"})
    private String billNo;

    @ExcelProperty(value = {"FDate", "*(基本信息)日期"})
    private String orderDate;

    @ExcelProperty(value = {"FNote", "(基本信息)备注"})
    private String note;

    @ExcelProperty(value = {"F_kd_Textwo", "(基本信息)本司WO#"})
    private String woNumber;

    @ExcelProperty(value = {"F_kd_AssistantKH", "(基本信息)最终客户#编码"})
    private String customerCode;

    @ExcelProperty(value = {"F_kd_AssistantKH#Name", "(基本信息)最终客户#名称"})
    private String customerName;

    @ExcelProperty(value = {"FSaleOrderEntry", "*订单明细(序号)"})
    private String saleOrderEntry;

    @ExcelProperty(value = {"FUnitID", "*(订单明细)销售单位#编码"})
    private String unitCode;

    @ExcelProperty(value = {"FUnitID#Name", "(订单明细)销售单位#名称"})
    private String unitName;

    @ExcelProperty(value = {"FQty", "(订单明细)销售数量"})
    private String qty;

    @ExcelProperty(value = {"FMaterialId", "*(订单明细)物料编码#编码"})
    private String materialCode;

    @ExcelProperty(value = {"FMaterialId#Name", "(订单明细)物料编码#名称"})
    private String materialName;

    @ExcelProperty(value = {"F_kd_Dateyh", "(订单明细)验货日期"})
    private String inspectionDate;

    @ExcelProperty(value = {"FDeliveryDate", "*(订单明细)要货日期"})
    private String deliveryDate;

    @ExcelProperty(value = {"FOldQty", "(订单明细)原数量"})
    private String oldQty;

    @ExcelProperty(value = {"FBomId", "(订单明细)BOM版本#编码"})
    private String bomVersion;

    @ExcelProperty(value = {"FBomId#Name", "(订单明细)BOM版本#名称"})
    private String bomVersionName;

    @ExcelProperty(value = {"FEntryNote", "(订单明细)备注"})
    private String entryNote;
}

