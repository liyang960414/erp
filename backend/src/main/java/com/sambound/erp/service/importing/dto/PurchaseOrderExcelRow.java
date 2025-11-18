package com.sambound.erp.service.importing.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 采购订单导入 Excel 行数据
 */
@Data
public class PurchaseOrderExcelRow {

    @ExcelProperty(value = {"FBillHead(PUR_PurchaseOrder)", "*基本信息(序号)"})
    private String billHead;

    @ExcelProperty(value = {"FBillNo", "(基本信息)单据编号"})
    private String billNo;

    @ExcelProperty(value = {"FDate", "*(基本信息)采购日期"})
    private String orderDate;

    @ExcelProperty(value = {"FSupplierId", "*(基本信息)供应商#编码"})
    private String supplierCode;

    @ExcelProperty(value = {"FSupplierId#Name", "(基本信息)供应商#名称"})
    private String supplierName;

    @ExcelProperty(value = {"FProviderId", "(基本信息)供货方#编码"})
    private String providerCode;

    @ExcelProperty(value = {"FProviderId#Name", "(基本信息)供货方#名称"})
    private String providerName;

    @ExcelProperty(value = {"FPOOrderEntry", "*明细信息(序号)"})
    private String purchaseOrderEntry;

    @ExcelProperty(value = {"FMaterialId", "*(明细信息)物料编码#编码"})
    private String materialCode;

    @ExcelProperty(value = {"FMaterialId#Name", "(明细信息)物料编码#名称"})
    private String materialName;

    @ExcelProperty(value = {"FBomId", "(明细信息)BOM版本#编码"})
    private String bomVersion;

    @ExcelProperty(value = {"FBomId#Name", "(明细信息)BOM版本#名称"})
    private String bomVersionName;

    @ExcelProperty(value = {"FMaterialDesc#2052", "(明细信息)物料说明#中文(简体)"})
    private String materialDesc;

    @ExcelProperty(value = {"FUnitId", "*(明细信息)采购单位#编码"})
    private String unitCode;

    @ExcelProperty(value = {"FUnitId#Name", "(明细信息)采购单位#名称"})
    private String unitName;

    @ExcelProperty(value = {"FQty", "(明细信息)采购数量"})
    private String qty;

    @ExcelProperty(value = {"FPlanConfirm", "(明细信息)计划确认"})
    private String planConfirm;

    @ExcelProperty(value = {"FSalUnitID", "(明细信息)销售单位#编码"})
    private String salUnitCode;

    @ExcelProperty(value = {"FSalUnitID#Name", "(明细信息)销售单位#名称"})
    private String salUnitName;

    @ExcelProperty(value = {"FSalQty", "(明细信息)销售数量"})
    private String salQty;

    @ExcelProperty(value = {"FSalJoinQty", "(明细信息)销售订单关联数量（采购）"})
    private String salJoinQty;

    @ExcelProperty(value = {"FBaseSalJoinQty", "(明细信息)销售订单关联数量（采购基本）"})
    private String baseSalJoinQty;

    @ExcelProperty(value = {"F_kd_Remarksbz", "(明细信息)备注2"})
    private String remarks;

    @ExcelProperty(value = {"FSalBaseQty", "(明细信息)销售基本数量"})
    private String salBaseQty;

    @ExcelProperty(value = {"FEntryDeliveryPlan", "*交货明细(序号)"})
    private String deliveryEntry;

    @ExcelProperty(value = {"FDeliveryDate_Plan", "*(交货明细)交货日期"})
    private String deliveryDate;

    @ExcelProperty(value = {"FPlanQty", "*(交货明细)数量"})
    private String planQty;

    @ExcelProperty(value = {"FSUPPLIERDELIVERYDATE", "(交货明细)供应商发货日期"})
    private String supplierDeliveryDate;

    @ExcelProperty(value = {"FPREARRIVALDATE", "(交货明细)预计到货日期"})
    private String preArrivalDate;

    @ExcelProperty(value = {"FTRLT", "(交货明细)运输提前期"})
    private String transportLeadTime;

    @ExcelProperty(value = {"FSUBREQENTRYID", "(明细信息)委外订单分录内码"})
    private String subReqOrderSequence;
}

