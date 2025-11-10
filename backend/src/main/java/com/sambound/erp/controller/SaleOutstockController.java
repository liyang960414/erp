package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.SaleOutstockDTO;
import com.sambound.erp.dto.SaleOutstockImportResponse;
import com.sambound.erp.service.SaleOutstockImportService;
import com.sambound.erp.service.SaleOutstockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sale-outstocks")
public class SaleOutstockController {

    private final SaleOutstockService saleOutstockService;
    private final SaleOutstockImportService saleOutstockImportService;

    public SaleOutstockController(
            SaleOutstockService saleOutstockService,
            SaleOutstockImportService saleOutstockImportService) {
        this.saleOutstockService = saleOutstockService;
        this.saleOutstockImportService = saleOutstockImportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sale_outstock:read')")
    public ResponseEntity<ApiResponse<Page<SaleOutstockDTO>>> getSaleOutstocks(
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SaleOutstockDTO> outstocks = saleOutstockService.getSaleOutstocks(
                billNo, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(outstocks));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sale_outstock:read')")
    public ResponseEntity<ApiResponse<SaleOutstockDTO>> getSaleOutstockById(@PathVariable Long id) {
        SaleOutstockDTO dto = saleOutstockService.getSaleOutstockById(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('sale_outstock:import')")
    public ResponseEntity<ApiResponse<SaleOutstockImportResponse>> importSaleOutstocks(
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

        String lowerFilename = filename.toLowerCase().trim();
        if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("只支持Excel格式的文件（.xlsx或.xls），当前文件: " + filename));
        }

        try {
            SaleOutstockImportResponse result = saleOutstockImportService.importFromExcel(file);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }
}

