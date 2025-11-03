package com.sambound.erp.service;

import com.sambound.erp.dto.BillOfMaterialDTO;
import com.sambound.erp.dto.BomItemDTO;
import com.sambound.erp.dto.CreateBomRequest;
import com.sambound.erp.dto.UpdateBomRequest;
import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.BomItem;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.exception.BusinessException;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.BomItemRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BillOfMaterialService {

    private final BillOfMaterialRepository bomRepository;
    private final BomItemRepository bomItemRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;

    public BillOfMaterialService(
            BillOfMaterialRepository bomRepository,
            BomItemRepository bomItemRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository) {
        this.bomRepository = bomRepository;
        this.bomItemRepository = bomItemRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
    }

    public List<BillOfMaterialDTO> getAllBoms() {
        return bomRepository.findAllWithMaterial().stream()
                .map(this::toDTO)
                .toList();
    }

    public BillOfMaterialDTO getBomById(Long id) {
        BillOfMaterial bom = bomRepository.findByIdWithMaterial(id)
                .orElseThrow(() -> new BusinessException("BOM不存在"));
        return toDTOWithItems(bom);
    }

    public List<BillOfMaterialDTO> getBomsByMaterialId(Long materialId) {
        return bomRepository.findByMaterialId(materialId).stream()
                .map(this::toDTO)
                .toList();
    }

    public BillOfMaterialDTO getBomByMaterialIdAndVersion(Long materialId, String version) {
        BillOfMaterial bom = bomRepository.findByMaterialIdAndVersion(materialId, version)
                .orElseThrow(() -> new BusinessException("BOM不存在"));
        return toDTOWithItems(bom);
    }

    @Transactional
    public BillOfMaterialDTO createBom(CreateBomRequest request) {
        // 验证父项物料
        Material material = materialRepository.findById(request.materialId())
                .orElseThrow(() -> new BusinessException("父项物料不存在"));

        // 验证物料属性：只有委外和自制类型的物料可以创建 BOM
        validateMaterialForBom(material, "父项");

        // 检查版本是否已存在
        if (bomRepository.existsByMaterialIdAndVersion(request.materialId(), request.version())) {
            throw new BusinessException("该物料已存在相同版本的BOM: " + request.version());
        }

        // 创建BOM头
        BillOfMaterial bom = BillOfMaterial.builder()
                .material(material)
                .version(request.version())
                .name(request.name())
                .category(request.category())
                .usage(request.usage())
                .description(request.description())
                .build();

        bom = bomRepository.save(bom);

        // 创建BOM明细
        if (request.items() != null && !request.items().isEmpty()) {
            for (CreateBomRequest.CreateBomItemRequest itemRequest : request.items()) {
                Material childMaterial = materialRepository.findById(itemRequest.childMaterialId())
                        .orElseThrow(() -> new BusinessException("子项物料不存在: " + itemRequest.childMaterialId()));
                
                Unit childUnit = unitRepository.findById(itemRequest.childUnitId())
                        .orElseThrow(() -> new BusinessException("子项单位不存在: " + itemRequest.childUnitId()));

                // 处理子项BOM版本：如果未配置，保持为null；如果配置了值，需要验证子项物料属性
                String childBomVersion = (itemRequest.childBomVersion() != null && !itemRequest.childBomVersion().isEmpty())
                        ? itemRequest.childBomVersion() : null;
                
                // 只有在明确配置了子项BOM版本时，才验证子项物料属性
                if (childBomVersion != null) {
                    validateMaterialForChildBomVersion(childMaterial, childMaterial.getCode());
                }

                BomItem item = BomItem.builder()
                        .bom(bom)
                        .sequence(itemRequest.sequence())
                        .childMaterial(childMaterial)
                        .childUnit(childUnit)
                        .numerator(itemRequest.numerator())
                        .denominator(itemRequest.denominator())
                        .scrapRate(itemRequest.scrapRate())
                        .childBomVersion(childBomVersion)
                        .memo(itemRequest.memo())
                        .build();

                bomItemRepository.save(item);
            }
        }

        return toDTOWithItems(bom);
    }

    @Transactional
    public BillOfMaterialDTO updateBom(Long id, UpdateBomRequest request) {
        BillOfMaterial bom = bomRepository.findById(id)
                .orElseThrow(() -> new BusinessException("BOM不存在"));

        if (request.name() != null) {
            bom.setName(request.name());
        }
        if (request.category() != null) {
            bom.setCategory(request.category());
        }
        if (request.usage() != null) {
            bom.setUsage(request.usage());
        }
        if (request.description() != null) {
            bom.setDescription(request.description());
        }

        bom = bomRepository.save(bom);

        // 更新明细项
        if (request.items() != null) {
            // 删除所有现有明细项
            bomItemRepository.deleteByBomId(bom.getId());

            // 创建新的明细项
            for (UpdateBomRequest.UpdateBomItemRequest itemRequest : request.items()) {
                Material childMaterial = materialRepository.findById(itemRequest.childMaterialId())
                        .orElseThrow(() -> new BusinessException("子项物料不存在: " + itemRequest.childMaterialId()));
                
                Unit childUnit = unitRepository.findById(itemRequest.childUnitId())
                        .orElseThrow(() -> new BusinessException("子项单位不存在: " + itemRequest.childUnitId()));

                // 处理子项BOM版本：如果未配置，保持为null；如果配置了值，需要验证子项物料属性
                String childBomVersion = (itemRequest.childBomVersion() != null && !itemRequest.childBomVersion().isEmpty())
                        ? itemRequest.childBomVersion() : null;
                
                // 只有在明确配置了子项BOM版本时，才验证子项物料属性
                if (childBomVersion != null) {
                    validateMaterialForChildBomVersion(childMaterial, childMaterial.getCode());
                }

                BomItem item = BomItem.builder()
                        .bom(bom)
                        .sequence(itemRequest.sequence())
                        .childMaterial(childMaterial)
                        .childUnit(childUnit)
                        .numerator(itemRequest.numerator())
                        .denominator(itemRequest.denominator())
                        .scrapRate(itemRequest.scrapRate())
                        .childBomVersion(childBomVersion)
                        .memo(itemRequest.memo())
                        .build();

                bomItemRepository.save(item);
            }
        }

        return toDTOWithItems(bom);
    }

    @Transactional
    public void deleteBom(Long id) {
        if (!bomRepository.existsById(id)) {
            throw new BusinessException("BOM不存在");
        }
        bomRepository.deleteById(id);
    }

    /**
     * 验证物料是否可以创建BOM（只有自制和委外类型的物料可以创建BOM）
     */
    private void validateMaterialForBom(Material material, String materialName) {
        String erpClsId = material.getErpClsId();
        if (erpClsId == null || (!isValidBomMaterialType(erpClsId))) {
            throw new BusinessException(String.format(
                    "只有自制和委外类型的物料可以创建BOM，%s物料属性为: %s",
                    materialName, erpClsId != null ? erpClsId : "未设置"));
        }
    }

    /**
     * 验证物料是否可以设置子项BOM版本（只有自制和委外类型的物料可以设置子项BOM版本）
     */
    private void validateMaterialForChildBomVersion(Material material, String materialCode) {
        String erpClsId = material.getErpClsId();
        if (erpClsId == null || (!isValidBomMaterialType(erpClsId))) {
            throw new BusinessException(String.format(
                    "只有自制和委外类型的物料可以设置子项BOM版本，物料 %s 的属性为: %s",
                    materialCode, erpClsId != null ? erpClsId : "未设置"));
        }
    }

    /**
     * 判断物料属性是否为有效的BOM物料类型（自制或委外）
     * 只支持中文名称："自制"、"委外"
     */
    private boolean isValidBomMaterialType(String erpClsId) {
        if (erpClsId == null) {
            return false;
        }
        String trimmed = erpClsId.trim();
        return trimmed.equals("自制") || trimmed.equals("委外");
    }

    private BillOfMaterialDTO toDTO(BillOfMaterial bom) {
        Material material = bom.getMaterial();
        return new BillOfMaterialDTO(
                bom.getId(),
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getMaterialGroup() != null ? material.getMaterialGroup().getCode() : null,
                material.getMaterialGroup() != null ? material.getMaterialGroup().getName() : null,
                bom.getVersion(),
                bom.getName(),
                bom.getCategory(),
                bom.getUsage(),
                bom.getDescription(),
                null, // items will be loaded separately
                bom.getCreatedAt(),
                bom.getUpdatedAt()
        );
    }

    private BillOfMaterialDTO toDTOWithItems(BillOfMaterial bom) {
        BillOfMaterialDTO baseDTO = toDTO(bom);
        
        // 加载明细项
        List<BomItemDTO> items = bomItemRepository.findByBomIdWithDetails(bom.getId()).stream()
                .map(this::itemToDTO)
                .sorted(Comparator.comparing(BomItemDTO::sequence))
                .collect(Collectors.toList());

        return new BillOfMaterialDTO(
                baseDTO.id(),
                baseDTO.materialId(),
                baseDTO.materialCode(),
                baseDTO.materialName(),
                baseDTO.materialGroupCode(),
                baseDTO.materialGroupName(),
                baseDTO.version(),
                baseDTO.name(),
                baseDTO.category(),
                baseDTO.usage(),
                baseDTO.description(),
                items,
                baseDTO.createdAt(),
                baseDTO.updatedAt()
        );
    }

    private BomItemDTO itemToDTO(BomItem item) {
        return new BomItemDTO(
                item.getId(),
                item.getBom().getId(),
                item.getSequence(),
                item.getChildMaterial().getId(),
                item.getChildMaterial().getCode(),
                item.getChildMaterial().getName(),
                item.getChildUnit().getId(),
                item.getChildUnit().getCode(),
                item.getChildUnit().getName(),
                item.getNumerator(),
                item.getDenominator(),
                item.getScrapRate(),
                item.getChildBomVersion(),
                item.getMemo(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

