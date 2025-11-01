package com.sambound.erp.service;

import com.sambound.erp.dto.CreateUnitRequest;
import com.sambound.erp.dto.UnitDTO;
import com.sambound.erp.dto.UpdateUnitRequest;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.UnitGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UnitService {

    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;

    public UnitService(UnitRepository unitRepository, UnitGroupRepository unitGroupRepository) {
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
    }

    public List<UnitDTO> getAllUnits() {
        return unitRepository.findAllWithUnitGroup().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<UnitDTO> getUnitsByGroupId(Long groupId) {
        return unitRepository.findByUnitGroupId(groupId).stream()
                .map(this::toDTO)
                .toList();
    }

    public UnitDTO getUnitById(Long id) {
        Unit unit = unitRepository.findByIdWithUnitGroup(id)
                .orElseThrow(() -> new BusinessException("单位不存在"));
        return toDTO(unit);
    }

    public UnitDTO getUnitByCode(String code) {
        Unit unit = unitRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("单位不存在"));
        return toDTO(unit);
    }

    @Transactional
    public UnitDTO createUnit(CreateUnitRequest request) {
        if (unitRepository.existsByCode(request.code())) {
            throw new BusinessException("单位编码已存在");
        }

        UnitGroup unitGroup = unitGroupRepository.findById(request.unitGroupId())
                .orElseThrow(() -> new BusinessException("单位组不存在"));

        Unit unit = Unit.builder()
                .code(request.code())
                .name(request.name())
                .unitGroup(unitGroup)
                .enabled(request.enabled() != null ? request.enabled() : true)
                .build();

        unit = unitRepository.save(unit);
        return toDTO(unit);
    }

    @Transactional
    public UnitDTO updateUnit(Long id, UpdateUnitRequest request) {
        Unit unit = unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException("单位不存在"));

        if (request.name() != null) {
            unit.setName(request.name());
        }
        // 编辑时不允许修改单位组
        // if (request.unitGroupId() != null) {
        //     UnitGroup unitGroup = unitGroupRepository.findById(request.unitGroupId())
        //             .orElseThrow(() -> new BusinessException("单位组不存在"));
        //     unit.setUnitGroup(unitGroup);
        // }
        if (request.enabled() != null) {
            unit.setEnabled(request.enabled());
        }

        unit = unitRepository.save(unit);
        
        // 更新转换率（如果提供了）
        if (request.conversionNumerator() != null && request.conversionDenominator() != null) {
            updateUnitConversion(unit, request.conversionNumerator(), request.conversionDenominator());
        }
        
        return toDTO(unit);
    }
    
    /**
     * 更新单位的转换率（相对于单位组的转换率）
     */
    private void updateUnitConversion(Unit unit, BigDecimal numerator, BigDecimal denominator) {
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("换算分母必须大于0");
        }
        
        // 直接更新单位的转换率字段
        unit.setNumerator(numerator);
        unit.setDenominator(denominator);
        unitRepository.save(unit);
    }

    @Transactional
    public void deleteUnit(Long id) {
        if (!unitRepository.existsById(id)) {
            throw new BusinessException("单位不存在");
        }
        unitRepository.deleteById(id);
    }

    @Transactional
    public Unit findOrCreateByCode(String code, String name, UnitGroup unitGroup) {
        // 确保 UnitGroup 已经持久化并分配了 ID
        if (unitGroup.getId() == null) {
            throw new IllegalStateException("单位组未持久化，无法创建单位: " + code);
        }
        
        // 使用 PostgreSQL 的 ON CONFLICT DO UPDATE 语法实现原子性的插入或获取操作
        // DO UPDATE 确保总是返回一行，避免死锁问题
        // 使用最大重试次数处理罕见的死锁情况
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                Unit result = unitRepository.insertOrGetByCode(code, name, unitGroup.getId());
                
                // 使用原生SQL返回的对象可能没有正确加载关系，重新查询确保关系正确映射
                return unitRepository.findByIdWithUnitGroup(result.getId())
                        .orElse(result);
            } catch (org.springframework.dao.DataAccessException e) {
                // 检查是否是死锁异常（PostgreSQL死锁的SQLState是40P01）
                String message = e.getMessage();
                if (message != null && (message.contains("deadlock") || 
                                       message.contains("40P01") ||
                                       (e.getCause() != null && e.getCause().getMessage() != null && 
                                        e.getCause().getMessage().contains("deadlock")))) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        throw new RuntimeException("无法创建或获取单位（重试" + maxRetries + "次后失败）: " + code, e);
                    }
                    // 短暂等待后重试
                    try {
                        Thread.sleep(50 * retryCount); // 递增延迟：50ms, 100ms, 150ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("创建单位时被中断: " + code, ie);
                    }
                    continue;
                }
                // 如果是唯一约束冲突但ON CONFLICT没有处理（理论上不应该发生），尝试查询
                if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
                    return unitRepository.findByCode(code)
                            .orElseThrow(() -> new RuntimeException("无法创建或获取单位: " + code, e));
                }
                // 其他异常直接抛出
                throw new RuntimeException("无法创建或获取单位: " + code, e);
            }
        }
        
        // 理论上不应该到达这里
        throw new RuntimeException("无法创建或获取单位: " + code);
    }

    private UnitDTO toDTO(Unit unit) {
        // 直接从单位实体读取转换率（相对于单位组的转换率）
        return new UnitDTO(
                unit.getId(),
                unit.getCode(),
                unit.getName(),
                new UnitDTO.UnitGroupSummary(
                        unit.getUnitGroup().getId(),
                        unit.getUnitGroup().getCode(),
                        unit.getUnitGroup().getName()
                ),
                unit.getEnabled(),
                unit.getNumerator(),
                unit.getDenominator(),
                unit.getCreatedAt(),
                unit.getUpdatedAt()
        );
    }
}

