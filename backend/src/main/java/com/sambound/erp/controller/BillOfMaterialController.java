package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.BillOfMaterialDTO;
import com.sambound.erp.dto.BomQueryDTO;
import com.sambound.erp.dto.CreateBomRequest;
import com.sambound.erp.dto.ImportTaskCreateResponse;
import com.sambound.erp.dto.UpdateBomRequest;
import com.sambound.erp.service.BillOfMaterialService;
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
@RequestMapping("/api/boms")
public class BillOfMaterialController {

    private final BillOfMaterialService bomService;
    private final ImportTaskManager importTaskManager;

    public BillOfMaterialController(
            BillOfMaterialService bomService,
            ImportTaskManager importTaskManager) {
        this.bomService = bomService;
        this.importTaskManager = importTaskManager;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BillOfMaterialDTO>>> getAllBoms() {
        List<BillOfMaterialDTO> boms = bomService.getAllBoms();
        return ResponseEntity.ok(ApiResponse.success(boms));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BillOfMaterialDTO>> getBomById(@PathVariable Long id) {
        BillOfMaterialDTO bom = bomService.getBomById(id);
        return ResponseEntity.ok(ApiResponse.success(bom));
    }

    @GetMapping("/material/{materialId}")
    public ResponseEntity<ApiResponse<List<BillOfMaterialDTO>>> getBomsByMaterialId(
            @PathVariable Long materialId) {
        List<BillOfMaterialDTO> boms = bomService.getBomsByMaterialId(materialId);
        return ResponseEntity.ok(ApiResponse.success(boms));
    }

    @GetMapping("/material/{materialId}/version/{version}")
    public ResponseEntity<ApiResponse<BillOfMaterialDTO>> getBomByMaterialIdAndVersion(
            @PathVariable Long materialId,
            @PathVariable String version) {
        BillOfMaterialDTO bom = bomService.getBomByMaterialIdAndVersion(materialId, version);
        return ResponseEntity.ok(ApiResponse.success(bom));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BillOfMaterialDTO>> createBom(
            @Valid @RequestBody CreateBomRequest request) {
        BillOfMaterialDTO bom = bomService.createBom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(bom));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BillOfMaterialDTO>> updateBom(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBomRequest request) {
        BillOfMaterialDTO bom = bomService.updateBom(id, request);
        return ResponseEntity.ok(ApiResponse.success(bom));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBom(@PathVariable Long id) {
        bomService.deleteBom(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportTaskCreateResponse>> importBoms(
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
        if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls") 
                && !lowerFilename.endsWith(".csv")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("只支持Excel或CSV格式的文件（.xlsx、.xls或.csv），当前文件: " + filename));
        }

        try {
            var task = importTaskManager.createTask("bom", file, currentUsername(), Map.of());
            ImportTaskCreateResponse response = ImportTaskMapper.toCreateResponse(task);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("导入失败: " + e.getMessage()));
        }
    }

    /**
     * 根据物料编码获取该物料的所有BOM版本列表
     */
    @GetMapping("/material-code/{materialCode}/versions")
    public ResponseEntity<ApiResponse<List<BillOfMaterialDTO>>> getBomVersionsByMaterialCode(
            @PathVariable String materialCode) {
        List<BillOfMaterialDTO> boms = bomService.getBomVersionsByMaterialCode(materialCode);
        return ResponseEntity.ok(ApiResponse.success(boms));
    }

    /**
     * BOM正查：根据物料编码和版本，递归查询所有子物料及其BOM
     */
    @GetMapping("/query/forward")
    public ResponseEntity<ApiResponse<BomQueryDTO>> queryBomForward(
            @RequestParam String materialCode,
            @RequestParam String version) {
        BomQueryDTO result = bomService.queryBomForward(materialCode, version);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * BOM反查：根据物料编码和版本（可选），递归查询所有父级物料及其BOM
     */
    @GetMapping("/query/backward")
    public ResponseEntity<ApiResponse<List<BomQueryDTO>>> queryBomBackward(
            @RequestParam String materialCode,
            @RequestParam(required = false) String version) {
        List<BomQueryDTO> result = bomService.queryBomBackward(materialCode, version);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    private String currentUsername() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}

