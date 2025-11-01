package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.CreateUnitGroupRequest;
import com.sambound.erp.dto.UnitGroupDTO;
import com.sambound.erp.dto.UpdateUnitGroupRequest;
import com.sambound.erp.service.UnitGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/unit-groups")
public class UnitGroupController {

    private final UnitGroupService unitGroupService;

    public UnitGroupController(UnitGroupService unitGroupService) {
        this.unitGroupService = unitGroupService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitGroupDTO>>> getAllUnitGroups() {
        List<UnitGroupDTO> unitGroups = unitGroupService.getAllUnitGroups();
        return ResponseEntity.ok(ApiResponse.success(unitGroups));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitGroupDTO>> getUnitGroupById(@PathVariable Long id) {
        UnitGroupDTO unitGroup = unitGroupService.getUnitGroupById(id);
        return ResponseEntity.ok(ApiResponse.success(unitGroup));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UnitGroupDTO>> createUnitGroup(
            @Valid @RequestBody CreateUnitGroupRequest request) {
        UnitGroupDTO unitGroup = unitGroupService.createUnitGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(unitGroup));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UnitGroupDTO>> updateUnitGroup(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUnitGroupRequest request) {
        UnitGroupDTO unitGroup = unitGroupService.updateUnitGroup(id, request);
        return ResponseEntity.ok(ApiResponse.success(unitGroup));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUnitGroup(@PathVariable Long id) {
        unitGroupService.deleteUnitGroup(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

