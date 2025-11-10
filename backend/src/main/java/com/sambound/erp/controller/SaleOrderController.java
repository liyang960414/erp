package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.OrderAlertDTO;
import com.sambound.erp.dto.SaleOrderDTO;
import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.enums.SaleOrderStatus;
import com.sambound.erp.service.SaleOrderImportService;
import com.sambound.erp.service.SaleOrderService;
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
import java.util.List;

@RestController
@RequestMapping("/api/sale-orders")
public class SaleOrderController {
    
    private final SaleOrderService saleOrderService;
    private final SaleOrderImportService saleOrderImportService;
    
    public SaleOrderController(SaleOrderService saleOrderService,
                              SaleOrderImportService saleOrderImportService) {
        this.saleOrderService = saleOrderService;
        this.saleOrderImportService = saleOrderImportService;
    }
    
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('sale_order:import')")
    public ResponseEntity<ApiResponse<SaleOrderImportResponse>> importSaleOrders(
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
            SaleOrderImportResponse result = saleOrderImportService.importFromExcel(file);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('sale_order:read')")
    public ResponseEntity<ApiResponse<Page<SaleOrderDTO>>> getSaleOrders(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) SaleOrderStatus status,
            @RequestParam(required = false) String woNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<SaleOrderDTO> orders = saleOrderService.getSaleOrders(
                billNo, customerCode, customerName, status, woNumber, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sale_order:read')")
    public ResponseEntity<ApiResponse<SaleOrderDTO>> getSaleOrderById(@PathVariable Long id) {
        SaleOrderDTO order = saleOrderService.getSaleOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }
    
    /**
     * 获取订单提醒列表
     * 包含三种提醒类型：
     * 1. 采购料提醒：验货期前40天需要采购料
     * 2. 生产提醒：临近验货期7天内如果生产未完成需要提示（目前仅基于时间条件）
     * 3. 超期告警：要货日期超期需要告警
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('sale_order:read')")
    public ResponseEntity<ApiResponse<List<OrderAlertDTO>>> getOrderAlerts() {
        List<OrderAlertDTO> alerts = saleOrderService.getOrderAlerts();
        return ResponseEntity.ok(ApiResponse.success(alerts));
    }
}
