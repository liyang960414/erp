package com.sambound.erp.service;

import com.sambound.erp.dto.MaterialGroupDTO;
import com.sambound.erp.entity.MaterialGroup;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.MaterialGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaterialGroupService {

    private final MaterialGroupRepository materialGroupRepository;

    public MaterialGroupService(MaterialGroupRepository materialGroupRepository) {
        this.materialGroupRepository = materialGroupRepository;
    }

    public List<MaterialGroupDTO> getAllMaterialGroups() {
        return materialGroupRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public MaterialGroupDTO getMaterialGroupById(Long id) {
        MaterialGroup materialGroup = materialGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException("物料组不存在"));
        return toDTO(materialGroup);
    }

    public MaterialGroupDTO getMaterialGroupByCode(String code) {
        MaterialGroup materialGroup = materialGroupRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("物料组不存在"));
        return toDTO(materialGroup);
    }

    @Transactional
    public MaterialGroup findOrCreateByCode(String code, String name) {
        return findOrCreateByCode(code, name, null, null);
    }

    @Transactional
    public MaterialGroup findOrCreateByCode(String code, String name, String description, Long parentId) {
        // 使用 PostgreSQL 的 ON CONFLICT DO UPDATE 语法实现原子性的插入或获取操作
        // DO UPDATE 确保总是返回一行，避免死锁问题
        // 使用最大重试次数处理罕见的死锁情况
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                if (parentId != null || description != null) {
                    return materialGroupRepository.insertOrGetByCodeWithParent(
                        code, 
                        name != null ? name : code,
                        description,
                        parentId
                    );
                } else {
                    return materialGroupRepository.insertOrGetByCode(code, name != null ? name : code);
                }
            } catch (org.springframework.dao.DataAccessException e) {
                // 检查是否是死锁异常（PostgreSQL死锁的SQLState是40P01）
                String message = e.getMessage();
                if (message != null && (message.contains("deadlock") || 
                                       message.contains("40P01") ||
                                       (e.getCause() != null && e.getCause().getMessage() != null && 
                                        e.getCause().getMessage().contains("deadlock")))) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("无法创建或获取物料组（重试" + maxRetries + "次后失败）: " + code, e);
                    }
                    // 短暂等待后重试
                    try {
                        Thread.sleep(50 * retryCount); // 递增延迟：50ms, 100ms, 150ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("创建物料组时被中断: " + code, ie);
                    }
                    continue;
                }
                // 如果是唯一约束冲突但ON CONFLICT没有处理（理论上不应该发生），尝试查询
                if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
                    return materialGroupRepository.findByCode(code)
                            .orElseThrow(() -> new RuntimeException("无法创建或获取物料组: " + code, e));
                }
                // 其他异常直接抛出
                throw new RuntimeException("无法创建或获取物料组: " + code, e);
            }
        }
        
        // 理论上不应该到达这里
        throw new RuntimeException("无法创建或获取物料组: " + code);
    }

    private MaterialGroupDTO toDTO(MaterialGroup materialGroup) {
        return new MaterialGroupDTO(
                materialGroup.getId(),
                materialGroup.getCode(),
                materialGroup.getName(),
                materialGroup.getDescription(),
                materialGroup.getParentId(),
                materialGroup.getCreatedAt(),
                materialGroup.getUpdatedAt()
        );
    }
}

