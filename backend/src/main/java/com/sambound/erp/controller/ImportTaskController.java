package com.sambound.erp.controller;

import com.sambound.erp.dto.ApiResponse;
import com.sambound.erp.dto.ImportTaskDetail;
import com.sambound.erp.dto.ImportTaskFailureDTO;
import com.sambound.erp.dto.ImportTaskSummary;
import com.sambound.erp.importing.task.ImportFailureStatus;
import com.sambound.erp.importing.task.ImportTaskStatus;
import com.sambound.erp.service.importing.task.ImportTaskManager;
import com.sambound.erp.service.importing.task.ImportTaskMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

/**
 * 导入任务管理接口，提供任务查询、失败记录查看与重试。
 */
@RestController
@RequestMapping("/api/import-tasks")
public class ImportTaskController {

    private final ImportTaskManager importTaskManager;

    public ImportTaskController(ImportTaskManager importTaskManager) {
        this.importTaskManager = importTaskManager;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ImportTaskSummary>>> listTasks(
            @RequestParam(required = false) String importType,
            @RequestParam(required = false) ImportTaskStatus status,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ImportTaskSummary> result = importTaskManager.searchTasks(importType, status, createdBy, pageable)
                .map(ImportTaskMapper::toSummary);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportTaskDetail>> getTaskDetail(@PathVariable Long taskId) {
        var task = importTaskManager.getTask(taskId);
        ImportTaskDetail detail = ImportTaskMapper.toDetail(task);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/{taskId}/failures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ImportTaskFailureDTO>>> listFailures(
            @PathVariable Long taskId,
            @RequestParam(required = false) ImportFailureStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ImportTaskFailureDTO> failures = importTaskManager.findFailures(taskId, status, pageable)
                .map(ImportTaskMapper::toFailureDTO);
        return ResponseEntity.ok(ApiResponse.success(failures));
    }

    @PostMapping("/{taskId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportTaskDetail>> retryTask(
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "failureIds", required = false) List<Long> failureIds) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("重试文件不能为空"));
        }
        if (failureIds == null) {
            failureIds = Collections.emptyList();
        }
        try {
            var task = importTaskManager.retryTask(taskId, file, currentUsername(), failureIds);
            ImportTaskDetail detail = ImportTaskMapper.toDetail(task);
            return ResponseEntity.ok(ApiResponse.success("已提交重试任务", detail));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("重试任务提交失败: " + ex.getMessage()));
        }
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}


