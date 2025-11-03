package com.sambound.erp.service;

import com.sambound.erp.dto.CreateUnitGroupRequest;
import com.sambound.erp.dto.UnitGroupDTO;
import com.sambound.erp.dto.UpdateUnitGroupRequest;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.UnitGroupRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UnitGroupService {

    private final UnitGroupRepository unitGroupRepository;

    public UnitGroupService(UnitGroupRepository unitGroupRepository) {
        this.unitGroupRepository = unitGroupRepository;
    }

    @Cacheable(cacheNames = "unitGroups", key = "'all'")
    public List<UnitGroupDTO> getAllUnitGroups() {
        return unitGroupRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Cacheable(cacheNames = "unitGroups", key = "#id")
    public UnitGroupDTO getUnitGroupById(Long id) {
        UnitGroup unitGroup = unitGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException("单位组不存在"));
        return toDTO(unitGroup);
    }

    @Cacheable(cacheNames = "unitGroups", key = "'code:' + #code")
    public UnitGroupDTO getUnitGroupByCode(String code) {
        UnitGroup unitGroup = unitGroupRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("单位组不存在"));
        return toDTO(unitGroup);
    }

    @Transactional
    @CacheEvict(cacheNames = "unitGroups", allEntries = true)
    public UnitGroupDTO createUnitGroup(CreateUnitGroupRequest request) {
        if (unitGroupRepository.existsByCode(request.code())) {
            throw new BusinessException("单位组编码已存在");
        }

        UnitGroup unitGroup = UnitGroup.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .build();

        unitGroup = unitGroupRepository.save(unitGroup);
        return toDTO(unitGroup);
    }

    @Transactional
    @CacheEvict(cacheNames = "unitGroups", allEntries = true)
    public UnitGroupDTO updateUnitGroup(Long id, UpdateUnitGroupRequest request) {
        UnitGroup unitGroup = unitGroupRepository.findById(id)
                .orElseThrow(() -> new BusinessException("单位组不存在"));

        if (request.name() != null) {
            unitGroup.setName(request.name());
        }
        if (request.description() != null) {
            unitGroup.setDescription(request.description());
        }

        unitGroup = unitGroupRepository.save(unitGroup);
        return toDTO(unitGroup);
    }

    @Transactional
    @CacheEvict(cacheNames = "unitGroups", allEntries = true)
    public void deleteUnitGroup(Long id) {
        if (!unitGroupRepository.existsById(id)) {
            throw new BusinessException("单位组不存在");
        }
        unitGroupRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(cacheNames = "unitGroups", allEntries = true)
    public UnitGroup findOrCreateByCode(String code, String name) {
        // 使用 PostgreSQL 的 ON CONFLICT DO UPDATE 语法实现原子性的插入或获取操作
        // DO UPDATE 确保总是返回一行，避免死锁问题
        // 使用最大重试次数处理罕见的死锁情况
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                return unitGroupRepository.insertOrGetByCode(code, name != null ? name : code);
            } catch (org.springframework.dao.DataAccessException e) {
                // 检查是否是死锁异常（PostgreSQL死锁的SQLState是40P01）
                String message = e.getMessage();
                if (message != null && (message.contains("deadlock") || 
                                       message.contains("40P01") ||
                                       (e.getCause() != null && e.getCause().getMessage() != null && 
                                        e.getCause().getMessage().contains("deadlock")))) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("无法创建或获取单位组（重试" + maxRetries + "次后失败）: " + code, e);
                    }
                    // 短暂等待后重试
                    try {
                        Thread.sleep(50 * retryCount); // 递增延迟：50ms, 100ms, 150ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("创建单位组时被中断: " + code, ie);
                    }
                    continue;
                }
                // 如果是唯一约束冲突但ON CONFLICT没有处理（理论上不应该发生），尝试查询
                if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
                    return unitGroupRepository.findByCode(code)
                            .orElseThrow(() -> new RuntimeException("无法创建或获取单位组: " + code, e));
                }
                // 其他异常直接抛出
                throw new RuntimeException("无法创建或获取单位组: " + code, e);
            }
        }
        
        // 理论上不应该到达这里
        throw new RuntimeException("无法创建或获取单位组: " + code);
    }

    private UnitGroupDTO toDTO(UnitGroup unitGroup) {
        return new UnitGroupDTO(
                unitGroup.getId(),
                unitGroup.getCode(),
                unitGroup.getName(),
                unitGroup.getDescription(),
                unitGroup.getCreatedAt(),
                unitGroup.getUpdatedAt()
        );
    }
}

