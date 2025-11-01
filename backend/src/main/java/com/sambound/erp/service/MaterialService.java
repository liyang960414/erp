package com.sambound.erp.service;

import com.sambound.erp.dto.MaterialDTO;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.MaterialGroup;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.MaterialGroupRepository;
import com.sambound.erp.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialGroupRepository materialGroupRepository;
    private final UnitRepository unitRepository;

    public MaterialService(MaterialRepository materialRepository,
                          MaterialGroupRepository materialGroupRepository,
                          UnitRepository unitRepository) {
        this.materialRepository = materialRepository;
        this.materialGroupRepository = materialGroupRepository;
        this.unitRepository = unitRepository;
    }

    public List<MaterialDTO> getAllMaterials() {
        return materialRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public MaterialDTO getMaterialById(Long id) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("物料不存在"));
        return toDTO(material);
    }

    public MaterialDTO getMaterialByCode(String code) {
        Material material = materialRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("物料不存在"));
        return toDTO(material);
    }

    public List<MaterialDTO> getMaterialsByGroupId(Long materialGroupId) {
        return materialRepository.findByMaterialGroupId(materialGroupId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public Material findOrCreateByCode(String code, String name, String materialGroupCode, String baseUnitCode) {
        // 查找物料组
        MaterialGroup materialGroup = materialGroupRepository.findByCode(materialGroupCode)
                .orElseThrow(() -> new BusinessException("物料组不存在: " + materialGroupCode));

        // 查找基础单位
        Unit baseUnit = unitRepository.findByCode(baseUnitCode)
                .orElseThrow(() -> new BusinessException("基础单位不存在: " + baseUnitCode));

        try {
            materialRepository.insertOrGetByCode(
                    code,
                    name != null ? name : code,
                    materialGroup.getId(),
                    baseUnit.getId()
            );
            // 重新查询以加载关联对象
            Material material = materialRepository.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("无法创建或获取物料: " + code));
            // 确保关联对象被设置
            material.setMaterialGroup(materialGroup);
            material.setBaseUnit(baseUnit);
            return material;
        } catch (org.springframework.dao.DataAccessException e) {
            // 如果插入失败，尝试查询
            return materialRepository.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("无法创建或获取物料: " + code, e));
        }
    }

    @Transactional
    public Material findOrCreateByCode(String code, String name, String specification,
                                      String mnemonicCode, String oldNumber, String description,
                                      String materialGroupCode, String baseUnitCode) {
        Material material = findOrCreateByCode(code, name, materialGroupCode, baseUnitCode);
        
        // 更新其他字段
        if (specification != null) {
            material.setSpecification(specification);
        }
        if (mnemonicCode != null) {
            material.setMnemonicCode(mnemonicCode);
        }
        if (oldNumber != null) {
            material.setOldNumber(oldNumber);
        }
        if (description != null) {
            material.setDescription(description);
        }
        
        return materialRepository.save(material);
    }

    private MaterialDTO toDTO(Material material) {
        return new MaterialDTO(
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getSpecification(),
                material.getMnemonicCode(),
                material.getOldNumber(),
                material.getDescription(),
                material.getMaterialGroup().getId(),
                material.getMaterialGroup().getCode(),
                material.getMaterialGroup().getName(),
                material.getBaseUnit().getId(),
                material.getBaseUnit().getCode(),
                material.getBaseUnit().getName(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}

