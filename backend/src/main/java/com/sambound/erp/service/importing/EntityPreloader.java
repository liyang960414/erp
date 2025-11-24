package com.sambound.erp.service.importing;

import com.sambound.erp.entity.BillOfMaterial;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.Supplier;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.BillOfMaterialRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SupplierRepository;
import com.sambound.erp.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 统一的实体预加载工具
 * 预加载供应商、物料、单位、BOM 等实体
 * 支持批量查询和缓存
 * 统一处理缺失实体的错误记录
 */
public class EntityPreloader {

    private static final Logger logger = LoggerFactory.getLogger(EntityPreloader.class);
    private static final int BATCH_QUERY_CHUNK_SIZE = ImportServiceConfig.DEFAULT_BATCH_QUERY_CHUNK_SIZE;

    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final ImportErrorCollector errorCollector;

    public EntityPreloader(SupplierRepository supplierRepository,
                          MaterialRepository materialRepository,
                          UnitRepository unitRepository,
                          BillOfMaterialRepository bomRepository,
                          ImportErrorCollector errorCollector) {
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.errorCollector = errorCollector;
    }

    /**
     * 预加载供应商
     *
     * @param supplierCodes 供应商编码集合
     * @return 供应商缓存（code -> Supplier）
     */
    public Map<String, Supplier> preloadSuppliers(Set<String> supplierCodes) {
        if (supplierCodes == null || supplierCodes.isEmpty()) {
            logger.debug("未找到需要查询的供应商");
            return new HashMap<>();
        }

        logger.info("开始预加载 {} 个供应商", supplierCodes.size());
        long startTime = System.currentTimeMillis();

        Map<String, Supplier> supplierCache = new HashMap<>(supplierCodes.size());
        List<String> codesToQuery = new ArrayList<>(supplierCodes);

        // 分批查询，避免 IN 查询参数过多
        for (int i = 0; i < codesToQuery.size(); i += BATCH_QUERY_CHUNK_SIZE) {
            int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, codesToQuery.size());
            List<String> chunk = codesToQuery.subList(i, end);
            List<Supplier> suppliers = supplierRepository.findByCodeIn(chunk);
            for (Supplier supplier : suppliers) {
                supplierCache.put(supplier.getCode(), supplier);
            }
        }

        // 记录缺失的供应商
        for (String code : supplierCodes) {
            if (!supplierCache.containsKey(code)) {
                errorCollector.addError("供应商", 0, "供应商编码", String.format("供应商不存在: %s", code));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("供应商预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                supplierCodes.size(), supplierCache.size(), duration);
        return supplierCache;
    }

    /**
     * 预加载物料
     *
     * @param materialCodes 物料编码集合
     * @return 物料缓存（code -> Material）
     */
    public Map<String, Material> preloadMaterials(Set<String> materialCodes) {
        if (materialCodes == null || materialCodes.isEmpty()) {
            logger.debug("未找到需要查询的物料");
            return new HashMap<>();
        }

        logger.info("开始预加载 {} 个物料", materialCodes.size());
        long startTime = System.currentTimeMillis();

        Map<String, Material> materialCache = new HashMap<>(materialCodes.size());
        List<String> codesToQuery = new ArrayList<>(materialCodes);

        // 分批查询，避免 IN 查询参数过多
        // 使用 JOIN FETCH 预加载 MaterialGroup 和 baseUnit，避免 LazyInitializationException
        for (int i = 0; i < codesToQuery.size(); i += BATCH_QUERY_CHUNK_SIZE) {
            int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, codesToQuery.size());
            List<String> chunk = codesToQuery.subList(i, end);
            List<Material> materials = materialRepository.findByCodeInWithMaterialGroup(chunk);
            for (Material material : materials) {
                materialCache.put(material.getCode(), material);
                // 确保懒加载字段已初始化（在事务内）
                if (material.getMaterialGroup() != null) {
                    material.getMaterialGroup().getId();
                }
                if (material.getBaseUnit() != null) {
                    material.getBaseUnit().getId();
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("物料预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                materialCodes.size(), materialCache.size(), duration);
        return materialCache;
    }

    /**
     * 预加载单位
     *
     * @param unitCodes 单位编码集合
     * @return 单位缓存（code -> Unit）
     */
    public Map<String, Unit> preloadUnits(Set<String> unitCodes) {
        if (unitCodes == null || unitCodes.isEmpty()) {
            logger.debug("未找到需要查询的单位");
            return new HashMap<>();
        }

        logger.info("开始预加载 {} 个单位", unitCodes.size());
        long startTime = System.currentTimeMillis();

        Map<String, Unit> unitCache = new HashMap<>(unitCodes.size());
        List<String> codesToQuery = new ArrayList<>(unitCodes);

        // 分批查询，避免 IN 查询参数过多
        // 使用 JOIN FETCH 预加载 UnitGroup，避免 LazyInitializationException
        for (int i = 0; i < codesToQuery.size(); i += BATCH_QUERY_CHUNK_SIZE) {
            int end = Math.min(i + BATCH_QUERY_CHUNK_SIZE, codesToQuery.size());
            List<String> chunk = codesToQuery.subList(i, end);
            List<Unit> units = unitRepository.findByCodeInWithUnitGroup(chunk);
            for (Unit unit : units) {
                // 确保 UnitGroup 完全初始化（在事务内）
                // 访问多个字段以确保代理对象被完全初始化
                if (unit.getUnitGroup() != null) {
                    unit.getUnitGroup().getId();
                    unit.getUnitGroup().getCode();
                    unit.getUnitGroup().getName();
                }
                unitCache.put(unit.getCode(), unit);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("单位预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                unitCodes.size(), unitCache.size(), duration);
        return unitCache;
    }

    /**
     * 预加载 BOM（物料清单）
     *
     * @param materialCache 物料缓存（用于获取物料ID）
     * @param bomKeys       BOM 键集合（格式: "materialCode:version"）
     * @return BOM 缓存（key -> BillOfMaterial）
     */
    public Map<String, BillOfMaterial> preloadBoms(Map<String, Material> materialCache, Set<String> bomKeys) {
        if (bomKeys == null || bomKeys.isEmpty()) {
            logger.debug("未找到需要查询的BOM");
            return new HashMap<>();
        }

        logger.info("开始预加载 {} 个BOM", bomKeys.size());
        long startTime = System.currentTimeMillis();

        Map<String, BillOfMaterial> bomCache = new HashMap<>();

        for (String bomKey : bomKeys) {
            String[] parts = bomKey.split(":", 2);
            if (parts.length == 2) {
                String materialCode = parts[0];
                String version = parts[1];
                Material material = materialCache.get(materialCode);
                if (material != null) {
                    bomRepository.findByMaterialIdAndVersion(material.getId(), version)
                            .ifPresent(bom -> bomCache.put(bomKey, bom));
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("BOM预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                bomKeys.size(), bomCache.size(), duration);
        return bomCache;
    }

    /**
     * 批量预加载多种实体
     *
     * @param supplierCodes 供应商编码集合
     * @param materialCodes 物料编码集合
     * @param unitCodes     单位编码集合
     * @param bomKeys       BOM 键集合（格式: "materialCode:version"）
     * @return 预加载结果
     */
    public PreloadResult preloadAll(Set<String> supplierCodes,
                                    Set<String> materialCodes,
                                    Set<String> unitCodes,
                                    Set<String> bomKeys) {
        Map<String, Supplier> supplierCache = preloadSuppliers(supplierCodes);
        Map<String, Material> materialCache = preloadMaterials(materialCodes);
        Map<String, Unit> unitCache = preloadUnits(unitCodes);
        Map<String, BillOfMaterial> bomCache = preloadBoms(materialCache, bomKeys);

        return new PreloadResult(supplierCache, materialCache, unitCache, bomCache);
    }

    /**
     * 预加载结果
     */
    public static class PreloadResult {
        private final Map<String, Supplier> supplierCache;
        private final Map<String, Material> materialCache;
        private final Map<String, Unit> unitCache;
        private final Map<String, BillOfMaterial> bomCache;

        public PreloadResult(Map<String, Supplier> supplierCache,
                            Map<String, Material> materialCache,
                            Map<String, Unit> unitCache,
                            Map<String, BillOfMaterial> bomCache) {
            this.supplierCache = supplierCache;
            this.materialCache = materialCache;
            this.unitCache = unitCache;
            this.bomCache = bomCache;
        }

        public Map<String, Supplier> getSupplierCache() {
            return supplierCache;
        }

        public Map<String, Material> getMaterialCache() {
            return materialCache;
        }

        public Map<String, Unit> getUnitCache() {
            return unitCache;
        }

        public Map<String, BillOfMaterial> getBomCache() {
            return bomCache;
        }
    }
}

