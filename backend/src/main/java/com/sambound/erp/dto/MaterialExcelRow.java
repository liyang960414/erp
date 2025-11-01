package com.sambound.erp.dto;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 物料导入 Excel 行数据
 */
@Data
public class MaterialExcelRow {
    
    @ExcelProperty(value = {"FBillHead(BD_MATERIAL)", "*物料(实体序号)"})
    private String billHead;
    
    @ExcelProperty(value = {"FNumber", "(物料)编码"})
    private String code;
    
    @ExcelProperty(value = {"FName#2052", "*(物料)名称#中文(简体)"})
    private String name;
    
    @ExcelProperty(value = {"FSpecification#2052", "(物料)规格型号#中文(简体)"})
    private String specification;
    
    @ExcelProperty(value = {"FMnemonicCode", "(物料)助记码"})
    private String mnemonicCode;
    
    @ExcelProperty(value = {"FOldNumber", "(物料)旧物料编码"})
    private String oldNumber;
    
    @ExcelProperty(value = {"FDescription#2052", "(物料)描述#中文(简体)"})
    private String description;
    
    @ExcelProperty(value = {"FMaterialGroup", "(物料)物料分组#编码"})
    private String materialGroupCode;
    
    @ExcelProperty(value = {"FMaterialGroup#Name", "(物料)物料分组#名称"})
    private String materialGroupName;
    
    @ExcelProperty(value = {"F_PSWZ_DirectLCost", "(物料)直接人工费"})
    private String directLCost;
    
    @ExcelProperty(value = {"F_PSWZ_OutsourcingFee", "(物料)委外加工费"})
    private String outsourcingFee;
    
    @ExcelProperty(value = {"F_PSWZ_StandardCost", "(物料)标准成本"})
    private String standardCost;
    
    @ExcelProperty(value = {"F_PSWZ_OutsourcingMCost", "(物料)委外材料费"})
    private String outsourcingMCost;
    
    @ExcelProperty(value = {"SubHeadEntity", "*基本(实体序号)"})
    private String subHeadEntity;
    
    @ExcelProperty(value = {"FBARCODE", "(基本)条码"})
    private String barcode;
    
    @ExcelProperty(value = {"FErpClsID", "*(基本)物料属性"})
    private String erpClsId;
    
    @ExcelProperty(value = {"FBaseUnitId", "*(基本)基本单位#编码"})
    private String baseUnitCode;
    
    @ExcelProperty(value = {"FBaseUnitId#Name", "(基本)基本单位#名称"})
    private String baseUnitName;
}
