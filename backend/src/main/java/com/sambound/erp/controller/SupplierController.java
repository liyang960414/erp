package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.SupplierDTO;
import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.service.importing.task.ImportTaskManager;
import com.sambound.erp.service.importing.task.ImportTaskMapper;
import com.sambound.erp.service.SupplierImportService;
import com.sambound.erp.service.SupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    
    private final SupplierService supplierService;
    private final ImportTaskManager importTaskManager;
    
    public SupplierController(ImportTaskManager importTaskManager, SupplierService supplierService) {
        this.importTaskManager = importTaskManager;
        this.supplierService = supplierService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplierDTO>>> getAllSuppliers() {
        List<SupplierDTO> suppliers = supplierService.getAllSuppliers();
        return ResponseEntity.ok(ApiResponse.success(suppliers));
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('supplier:import')")
    public ResponseEntity<ApiResponse<ImportTaskCreateResponse>> importSuppliers(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("文件不能为空"));
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("文件名不能为空"));
        }
        
        // 大小写不敏感检查
        String lowerFilename = filename.toLowerCase().trim();
        if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls") 
                && !lowerFilename.endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("只支持Excel或CSV格式的文件（.xlsx、.xls或.csv），当前文件: " + filename));
        }
        
        try {
            var task = importTaskManager.createTask("supplier", file, currentUsername(), Map.of());
            ImportTaskCreateResponse response = ImportTaskMapper.toCreateResponse(task);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }

    private String currentUsername() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}

