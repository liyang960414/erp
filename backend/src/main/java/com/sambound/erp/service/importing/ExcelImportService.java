package com.sambound.erp.service.importing;

import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 导入服务的通用接口定义。
 *
 * @param <R> 导入结果类型
 */
@FunctionalInterface
public interface ExcelImportService<R> {

    /**
     * 从上传的 Excel 文件执行导入。
     *
     * @param file 上传的 Excel 文件
     * @return 导入结果
     */
    R importFromExcel(MultipartFile file);
}

