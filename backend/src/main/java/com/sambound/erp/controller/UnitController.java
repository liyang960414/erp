package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.CreateUnitRequest;
import com.sambound.erp.dto.UnitDTO;
import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.dto.UpdateUnitRequest;
import com.sambound.erp.service.UnitService;
import com.sambound.erp.service.importing.task.ImportTaskManager;
import com.sambound.erp.service.importing.task.ImportTaskMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;
    private final ImportTaskManager importTaskManager;

    public UnitController(UnitService unitService, ImportTaskManager importTaskManager) {
        this.unitService = unitService;
        this.importTaskManager = importTaskManager;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitDTO>>> getAllUnits() {
        List<UnitDTO> units = unitService.getAllUnits();
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<UnitDTO>>> getUnitsByGroupId(@PathVariable Long groupId) {
        List<UnitDTO> units = unitService.getUnitsByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitDTO>> getUnitById(@PathVariable Long id) {
        UnitDTO unit = unitService.getUnitById(id);
        return ResponseEntity.ok(ApiResponse.success(unit));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UnitDTO>> createUnit(
            @Valid @RequestBody CreateUnitRequest request) {
        UnitDTO unit = unitService.createUnit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(unit));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UnitDTO>> updateUnit(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnitRequest request) {
        UnitDTO unit = unitService.updateUnit(id, request);
        return ResponseEntity.ok(ApiResponse.success(unit));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(@PathVariable Long id) {
        unitService.deleteUnit(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportTaskCreateResponse>> importUnits(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("文件不能为空"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("只支持Excel格式的文件（.xlsx或.xls）"));
        }

        try {
            var task = importTaskManager.createTask("unit", file, currentUsername(), Map.of());
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

