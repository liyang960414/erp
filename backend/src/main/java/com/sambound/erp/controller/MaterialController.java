package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.MaterialDTO;
import com.sambound.erp.dto.MaterialImportResponse;
import com.sambound.erp.service.MaterialImportService;
import com.sambound.erp.service.MaterialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialImportService materialImportService;

    public MaterialController(MaterialService materialService, MaterialImportService materialImportService) {
        this.materialService = materialService;
        this.materialImportService = materialImportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialDTO>>> getAllMaterials() {
        List<MaterialDTO> materials = materialService.getAllMaterials();
        if (materials == null) {
            materials = List.of();
        }
        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MaterialImportResponse>> importMaterials(
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
            MaterialImportResponse result = materialImportService.importFromExcel(file);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<MaterialDTO>>> getMaterialsByGroupId(@PathVariable Long groupId) {
        List<MaterialDTO> materials = materialService.getMaterialsByGroupId(groupId);
        return ResponseEntity.ok(ApiResponse.success(materials));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialDTO>> getMaterialById(@PathVariable Long id) {
        MaterialDTO material = materialService.getMaterialById(id);
        return ResponseEntity.ok(ApiResponse.success(material));
    }
}

