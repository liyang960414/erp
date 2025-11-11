package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.MaterialDTO;
import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.service.MaterialService;
import com.sambound.erp.service.importing.task.ImportTaskManager;
import com.sambound.erp.service.importing.task.ImportTaskMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;
    private final ImportTaskManager importTaskManager;

    public MaterialController(MaterialService materialService, ImportTaskManager importTaskManager) {
        this.materialService = materialService;
        this.importTaskManager = importTaskManager;
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
    public ResponseEntity<ApiResponse<ImportTaskCreateResponse>> importMaterials(
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
            var task = importTaskManager.createTask("material", file, currentUsername(), Map.of());
            ImportTaskCreateResponse response = ImportTaskMapper.toCreateResponse(task);
            return ResponseEntity.ok(ApiResponse.success(response));
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

    /**
     * 搜索物料（根据编码或名称模糊匹配）
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MaterialDTO>>> searchMaterials(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        List<MaterialDTO> materials = materialService.searchMaterials(keyword, limit);
        return ResponseEntity.ok(ApiResponse.success(materials));
    }
    private String currentUsername() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}

