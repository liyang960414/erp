package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.PurchaseOrderDTO;
import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.entity.PurchaseOrder;
import com.sambound.erp.service.PurchaseOrderService;
import com.sambound.erp.service.importing.task.ImportTaskManager;
import com.sambound.erp.service.importing.task.ImportTaskMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final ImportTaskManager importTaskManager;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService,
                                  ImportTaskManager importTaskManager) {
        this.purchaseOrderService = purchaseOrderService;
        this.importTaskManager = importTaskManager;
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('purchase_order:import')")
    public ResponseEntity<ApiResponse<ImportTaskCreateResponse>> importPurchaseOrders(
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
        if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("只支持Excel格式的文件（.xlsx或.xls），当前文件: " + filename));
        }
        
        try {
            var task = importTaskManager.createTask("purchase-order", file, currentUsername(), Map.of());
            ImportTaskCreateResponse response = ImportTaskMapper.toCreateResponse(task);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('purchase_order:read')")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderDTO>>> getPurchaseOrders(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) PurchaseOrder.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PurchaseOrderDTO> orders = purchaseOrderService.getPurchaseOrders(
                billNo, supplierCode, status, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase_order:read')")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> getPurchaseOrderById(@PathVariable Long id) {
        PurchaseOrderDTO order = purchaseOrderService.getPurchaseOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }
    private String currentUsername() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}

