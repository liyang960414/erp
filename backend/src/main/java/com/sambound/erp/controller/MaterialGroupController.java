package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.MaterialGroupDTO;
import com.sambound.erp.service.MaterialGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/material-groups")
public class MaterialGroupController {

    private final MaterialGroupService materialGroupService;

    public MaterialGroupController(MaterialGroupService materialGroupService) {
        this.materialGroupService = materialGroupService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialGroupDTO>>> getAllMaterialGroups() {
        List<MaterialGroupDTO> materialGroups = materialGroupService.getAllMaterialGroups();
        return ResponseEntity.ok(ApiResponse.success(materialGroups));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialGroupDTO>> getMaterialGroupById(@PathVariable Long id) {
        MaterialGroupDTO materialGroup = materialGroupService.getMaterialGroupById(id);
        return ResponseEntity.ok(ApiResponse.success(materialGroup));
    }
}








