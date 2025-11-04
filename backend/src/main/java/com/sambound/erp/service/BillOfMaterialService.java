package com.sambound.erp.service;

import com.sambound.erp.dto.BillOfMaterialDTO;
import com.sambound.erp.dto.BomItemDTO;
import com.sambound.erp.dto.BomQueryDTO;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * 根据物料编码获取该物料的所有BOM版本列表
     */
    public List<BillOfMaterialDTO> getBomVersionsByMaterialCode(String materialCode) {
        Material material = materialRepository.findByCode(materialCode)
                .orElseThrow(() -> new BusinessException("物料不存在: " + materialCode));
        
        List<BillOfMaterial> boms = bomRepository.findByMaterialCode(materialCode);
        return boms.stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * BOM正查：根据物料编码和版本，递归查询所有子物料及其BOM
     */
    public BomQueryDTO queryBomForward(String materialCode, String version) {
        // 查找物料
        Material material = materialRepository.findByCode(materialCode)
                .orElseThrow(() -> new BusinessException("物料不存在: " + materialCode));
        
        // 查找BOM
        BillOfMaterial bom = bomRepository.findByMaterialIdAndVersion(material.getId(), version)
                .orElseThrow(() -> new BusinessException(
                        String.format("物料 %s 不存在版本 %s 的BOM", materialCode, version)));
        
        // 递归查询子物料
        Set<Long> visited = new HashSet<>();
        List<BomQueryDTO> children = queryChildrenRecursive(bom.getId(), visited);
        
        // 构建根节点
        return BomQueryDTO.createRoot(
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getSpecification(),
                material.getMaterialGroup() != null ? material.getMaterialGroup().getCode() : null,
                material.getMaterialGroup() != null ? material.getMaterialGroup().getName() : null,
                bom.getId(),
                bom.getVersion(),
                bom.getName(),
                children
        );
    }

    /**
     * BOM反查：根据物料编码和版本（可选），递归查询所有父级物料及其BOM
     */
    public List<BomQueryDTO> queryBomBackward(String materialCode, String version) {
        // 查找物料
        Material material = materialRepository.findByCode(materialCode)
                .orElseThrow(() -> new BusinessException("物料不存在: " + materialCode));
        
        // 如果指定了版本，验证该物料是否有该版本的BOM
        if (version != null && !version.trim().isEmpty()) {
            bomRepository.findByMaterialIdAndVersion(material.getId(), version)
                    .orElseThrow(() -> new BusinessException(
                            String.format("物料 %s 不存在版本 %s 的BOM", materialCode, version)));
        }
        
        // 递归查询父级
        Set<Long> visited = new HashSet<>();
        List<BomQueryDTO> parents = queryParentsRecursive(material.getId(), visited);
        
        return parents;
    }

    /**
     * 递归查询子物料及其BOM
     */
    private List<BomQueryDTO> queryChildrenRecursive(Long bomId, Set<Long> visited) {
        // 防止循环引用
        if (visited.contains(bomId)) {
            return new ArrayList<>();
        }
        visited.add(bomId);
        
        // 获取BOM的所有明细项
        List<BomItem> items = bomItemRepository.findByBomIdWithDetails(bomId);
        List<BomQueryDTO> children = new ArrayList<>();
        
        for (BomItem item : items) {
            Material childMaterial = item.getChildMaterial();
            String childBomVersion = item.getChildBomVersion();
            
            // 如果子物料没有配置BOM版本，使用V000作为默认版本
            if (childBomVersion == null || childBomVersion.trim().isEmpty()) {
                childBomVersion = "V000";
            }
            
            // 查找子物料的BOM
            BillOfMaterial childBom = bomRepository.findByMaterialIdAndVersion(
                    childMaterial.getId(), childBomVersion).orElse(null);
            
            List<BomQueryDTO> grandChildren = new ArrayList<>();
            if (childBom != null) {
                // 递归查询子物料的子物料
                grandChildren = queryChildrenRecursive(childBom.getId(), visited);
            }
            
            // 构建子节点
            BomQueryDTO childNode = BomQueryDTO.createChild(
                    childMaterial.getId(),
                    childMaterial.getCode(),
                    childMaterial.getName(),
                    childMaterial.getSpecification(),
                    childMaterial.getMaterialGroup() != null ? childMaterial.getMaterialGroup().getCode() : null,
                    childMaterial.getMaterialGroup() != null ? childMaterial.getMaterialGroup().getName() : null,
                    childBom != null ? childBom.getId() : null,
                    childBom != null ? childBom.getVersion() : null,
                    childBom != null ? childBom.getName() : null,
                    item.getSequence(),
                    item.getNumerator(),
                    item.getDenominator(),
                    item.getScrapRate(),
                    item.getChildBomVersion(),
                    item.getChildUnit() != null ? item.getChildUnit().getCode() : null,
                    item.getChildUnit() != null ? item.getChildUnit().getName() : null,
                    grandChildren
            );
            
            children.add(childNode);
        }
        
        // 按序号排序
        children.sort(Comparator.comparing(BomQueryDTO::sequence, Comparator.nullsLast(Comparator.naturalOrder())));
        
        return children;
    }

    /**
     * 递归查询父级物料及其BOM
     * @param materialId 当前物料ID（用于查找该物料在父级BOM中的用量信息）
     */
    private List<BomQueryDTO> queryParentsRecursive(Long materialId, Set<Long> visited) {
        // 防止循环引用
        if (visited.contains(materialId)) {
            return new ArrayList<>();
        }
        visited.add(materialId);
        
        // 查找所有使用该物料作为子项的BOM
        List<BillOfMaterial> parentBoms = bomRepository.findByChildMaterialId(materialId);
        
        if (parentBoms.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<BomQueryDTO> parents = new ArrayList<>();
        
        for (BillOfMaterial parentBom : parentBoms) {
            Material parentMaterial = parentBom.getMaterial();
            
            // 查询该物料在父级BOM中的用量信息
            List<BomItem> bomItems = bomItemRepository.findByBomIdAndChildMaterialId(
                    parentBom.getId(), materialId);
            
            // 如果找到用量信息，使用第一个（理论上一个BOM中一个物料应该只有一条明细）
            BomItem bomItem = bomItems.isEmpty() ? null : bomItems.get(0);
            
            // 递归查询父级物料的父级
            List<BomQueryDTO> grandParents = queryParentsRecursive(parentMaterial.getId(), visited);
            
            // 构建父节点，包含用量信息
            BomQueryDTO parentNode;
            if (bomItem != null) {
                // 有用量信息，使用createChild方法
                parentNode = BomQueryDTO.createChild(
                        parentMaterial.getId(),
                        parentMaterial.getCode(),
                        parentMaterial.getName(),
                        parentMaterial.getSpecification(),
                        parentMaterial.getMaterialGroup() != null ? parentMaterial.getMaterialGroup().getCode() : null,
                        parentMaterial.getMaterialGroup() != null ? parentMaterial.getMaterialGroup().getName() : null,
                        parentBom.getId(),
                        parentBom.getVersion(),
                        parentBom.getName(),
                        bomItem.getSequence(),
                        bomItem.getNumerator(),
                        bomItem.getDenominator(),
                        bomItem.getScrapRate(),
                        bomItem.getChildBomVersion(),
                        bomItem.getChildUnit() != null ? bomItem.getChildUnit().getCode() : null,
                        bomItem.getChildUnit() != null ? bomItem.getChildUnit().getName() : null,
                        grandParents
                );
            } else {
                // 没有找到用量信息（理论上不应该发生），使用createRoot方法
                parentNode = BomQueryDTO.createRoot(
                        parentMaterial.getId(),
                        parentMaterial.getCode(),
                        parentMaterial.getName(),
                        parentMaterial.getSpecification(),
                        parentMaterial.getMaterialGroup() != null ? parentMaterial.getMaterialGroup().getCode() : null,
                        parentMaterial.getMaterialGroup() != null ? parentMaterial.getMaterialGroup().getName() : null,
                        parentBom.getId(),
                        parentBom.getVersion(),
                        parentBom.getName(),
                        grandParents
                );
            }
            
            parents.add(parentNode);
        }
        
        return parents;
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

