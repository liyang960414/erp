package com.sambound.erp.service.importing.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 委外订单导入 Excel 行数据
 */
@Data
public class SubReqOrderExcelRow {

    @ExcelProperty(value = {"FBillHead(SUB_SUBREQORDER)", "*单据头(序号)"})
    private String billHead;

    @ExcelProperty(value = {"FDescription#2052", "(单据头)备注#中文(简体)"})
    private String headerDescription;

    @ExcelProperty(value = {"FTreeEntity", "*明细(序号)"})
    private String treeEntity;

    @ExcelProperty(value = {"FMaterialId", "*(明细)物料编码#编码"})
    private String materialCode;

    @ExcelProperty(value = {"FMaterialId#Name", "(明细)物料编码#名称"})
    private String materialName;

    @ExcelProperty(value = {"FUnitID", "*(明细)单位#编码"})
    private String unitCode;

    @ExcelProperty(value = {"FUnitID#Name", "(明细)单位#名称"})
    private String unitName;

    @ExcelProperty(value = {"FQty", "(明细)数量"})
    private String qty;

    @ExcelProperty(value = {"FBomId", "(明细)BOM版本#编码"})
    private String bomVersion;

    @ExcelProperty(value = {"FBomId#Name", "(明细)BOM版本#名称"})
    private String bomVersionName;

    @ExcelProperty(value = {"FSupplierId", "(明细)供应商#编码"})
    private String supplierCode;

    @ExcelProperty(value = {"FSupplierId#Name", "(明细)供应商#名称"})
    private String supplierName;

    @ExcelProperty(value = {"FLot", "(明细)批号#主档"})
    private String lotMaster;

    @ExcelProperty(value = {"FLot#Text", "(明细)批号#手工"})
    private String lotManual;

    @ExcelProperty(value = {"FBaseNoStockInQty", "(明细)基本单位未入库数量"})
    private String baseNoStockInQty;

    @ExcelProperty(value = {"FNoStockInQty", "(明细)未入库数量"})
    private String noStockInQty;

    @ExcelProperty(value = {"FPickMtrlStatus", "(明细)领料状态"})
    private String pickMtrlStatus;

    @ExcelProperty(value = {"FDescription1#2052", "(明细)备注#中文(简体)"})
    private String description;
}

