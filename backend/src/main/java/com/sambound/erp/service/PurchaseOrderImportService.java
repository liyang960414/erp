package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.PurchaseOrderExcelRow;
import com.sambound.erp.dto.PurchaseOrderImportResponse;
import com.sambound.erp.entity.*;
import com.sambound.erp.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PurchaseOrderImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderImportService.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100; // 每批处理的订单数量
    private static final int MAX_CONCURRENT_BATCHES = 10; // 最大并发批次数量，避免数据库连接池耗尽
    
    // 字符串工具方法，避免重复创建对象
    private static String trimOrNull(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public PurchaseOrderImportService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PlatformTransactionManager transactionManager) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(1800); // 30分钟超时，支持大文件导入
        // 使用虚拟线程执行器（Java 21+ Virtual Threads）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public PurchaseOrderImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入采购订单Excel文件: {}，文件大小: {} MB", 
                file.getOriginalFilename(), 
                file.getSize() / (1024.0 * 1024.0));
        
        try {
            // 使用流式处理，避免将整个文件加载到内存（对于大文件很重要）
            // 使用 file.getInputStream() 而不是 file.getBytes()，FastExcel 支持流式读取
            PurchaseOrderDataCollector collector = new PurchaseOrderDataCollector();
            FastExcel.read(file.getInputStream(), PurchaseOrderExcelRow.class, collector)
                    .sheet("采购订单#基本信息(FBillHead)")
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();
            
            PurchaseOrderImportResponse result = collector.importToDatabase();
            logger.info("采购订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.purchaseOrderResult().totalRows(), 
                    result.purchaseOrderResult().successCount(), 
                    result.purchaseOrderResult().failureCount());
            
            return result;
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 采购订单数据收集器：处理订单头信息只在第一行有值的情况
     */
    private class PurchaseOrderDataCollector implements ReadListener<PurchaseOrderExcelRow> {
        private final List<PurchaseOrderData> orderDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private PurchaseOrderHeader currentHeader = null;
        private PurchaseOrderItemHeader currentItemHeader = null;
        private String currentBillNo = null;
        
        @Override
        public void invoke(PurchaseOrderExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();
            
            String billNo = trimOrNull(data.getBillNo());
            if (billNo != null) {
                currentBillNo = billNo;
                currentHeader = new PurchaseOrderHeader(
                        rowNum,
                        currentBillNo,
                        data.getOrderDate(),
                        null,
                        data.getSupplierCode(),
                        data.getSupplierName()
                );
                currentItemHeader = null;
            }
            
            String purchaseOrderEntry = trimOrNull(data.getPurchaseOrderEntry());
            if (purchaseOrderEntry != null && currentHeader != null) {
                Integer sequence = null;
                try {
                    sequence = Integer.parseInt(purchaseOrderEntry);
                } catch (NumberFormatException e) {
                    // 序号解析失败时保持为null
                }
                currentItemHeader = new PurchaseOrderItemHeader(
                        rowNum,
                        sequence,
                        data.getMaterialCode(),
                        data.getBomVersion(),
                        data.getMaterialDesc(),
                        data.getUnitCode(),
                        data.getQty(),
                        data.getPlanConfirm(),
                        data.getSalUnitCode(),
                        data.getSalQty(),
                        data.getSalJoinQty(),
                        data.getBaseSalJoinQty(),
                        data.getRemarks(),
                        data.getSalBaseQty()
                );
                orderDataList.add(new PurchaseOrderData(currentHeader, currentItemHeader));
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("采购订单数据收集完成，共 {} 条订单明细数据", orderDataList.size());
        }
        
        public PurchaseOrderImportResponse importToDatabase() {
            if (orderDataList.isEmpty()) {
                logger.info("未找到采购订单数据");
                return new PurchaseOrderImportResponse(
                        new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
                );
            }
            
            long startTime = System.currentTimeMillis();
            
            // 预估容量，减少Map扩容开销（假设平均每个订单有5条明细）
            int estimatedOrderCount = Math.max(1000, orderDataList.size() / 10);
            
            // 按订单头、明细分组
            Map<String, PurchaseOrderHeader> headerMap = new LinkedHashMap<>(estimatedOrderCount);
            Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap = new LinkedHashMap<>(estimatedOrderCount);
            
            for (PurchaseOrderData data : orderDataList) {
                if (data == null || data.header == null || data.itemHeader == null) {
                    continue;
                }
                String billNo = data.header.billNo;
                if (billNo == null) {
                    continue;
                }
                headerMap.putIfAbsent(billNo, data.header);
                Map<Integer, PurchaseOrderItemHeader> items = itemHeaderMap.computeIfAbsent(billNo, key -> new LinkedHashMap<>());
                Integer sequence = data.itemHeader.sequence;
                PurchaseOrderItemHeader itemHeader = data.itemHeader;
                if (sequence == null) {
                    sequence = items.size() + 1;
                    itemHeader = new PurchaseOrderItemHeader(
                            data.itemHeader.rowNumber,
                            sequence,
                            data.itemHeader.materialCode,
                            data.itemHeader.bomVersion,
                            data.itemHeader.materialDesc,
                            data.itemHeader.unitCode,
                            data.itemHeader.qty,
                            data.itemHeader.planConfirm,
                            data.itemHeader.salUnitCode,
                            data.itemHeader.salQty,
                            data.itemHeader.salJoinQty,
                            data.itemHeader.baseSalJoinQty,
                            data.itemHeader.remarks,
                            data.itemHeader.salBaseQty
                    );
                }
                items.putIfAbsent(sequence, itemHeader);
            }
            
            int totalOrderCount = headerMap.size();
            int totalItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
            logger.info("找到 {} 个订单，{} 条明细，开始导入到数据库", 
                    totalOrderCount, totalItemCount);
            
            if (totalOrderCount == 0) {
                logger.info("未收集到有效的采购订单");
                return new PurchaseOrderImportResponse(
                        new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
                );
            }
            
            // 使用线程安全的集合收集错误
            List<PurchaseOrderImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            
            // 预加载供应商、物料、单位、BOM数据
            Map<String, Supplier> supplierCache = preloadSuppliers(headerMap, errors);
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();
            Map<String, BillOfMaterial> bomCache = new HashMap<>();
            preloadMaterialsUnitsAndBoms(itemHeaderMap, materialCache, unitCache, bomCache, errors);
            
            // 预先批量查询所有已存在的订单
            Map<String, PurchaseOrder> existingOrderMap = preloadExistingOrders(headerMap.keySet());
            
            // 将订单分组转换为列表以便批量处理
            List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> orderList = 
                    new ArrayList<>(itemHeaderMap.entrySet());
            
            // 使用多线程并行处理批次
            List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
            Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
            
            int totalBatches = (orderList.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            logger.info("开始并行处理 {} 个批次，最大并发数: {}", totalBatches, MAX_CONCURRENT_BATCHES);
            
            // 提交所有批次任务到线程池
            for (int i = 0; i < orderList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, orderList.size());
                List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> batch = new ArrayList<>(
                        orderList.subList(i, end));
                int batchIndex = (i / BATCH_SIZE) + 1;
                
                // 异步提交批次处理任务
                CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        batchSemaphore.acquire();
                        try {
                            long batchStartTime = System.currentTimeMillis();
                            logger.info("处理批次 {}/{}，订单数量: {}", batchIndex, totalBatches, batch.size());
                            
                            int batchSuccess = transactionTemplate.execute(status -> {
                                return importBatchOrders(batch, headerMap, 
                                        supplierCache, materialCache, unitCache, bomCache, 
                                        existingOrderMap, errors);
                            });
                            
                            long batchDuration = System.currentTimeMillis() - batchStartTime;
                            logger.info("批次 {}/{} 完成，耗时: {}ms，成功: {} 条", 
                                    batchIndex, totalBatches, batchDuration, batchSuccess);
                            
                            return new BatchResult(batchSuccess, new ArrayList<>());
                        } finally {
                            batchSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("批次 {} 处理被中断", batchIndex);
                        List<PurchaseOrderImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单", headerMap.get(entry.getKey()).rowNumber, "单据编号",
                                        "批次处理被中断"));
                            }
                        }
                        return new BatchResult(0, batchErrors);
                    } catch (Exception e) {
                        logger.error("批次 {} 导入失败", batchIndex, e);
                        List<PurchaseOrderImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单", headerMap.get(entry.getKey()).rowNumber, "单据编号",
                                        "批次导入失败: " + e.getMessage()));
                            }
                        }
                        return new BatchResult(0, batchErrors);
                    }
                }, executorService);
                
                futures.add(future);
            }
            
            // 等待所有批次完成
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
                
                // 收集所有批次的结果
                for (CompletableFuture<BatchResult> future : futures) {
                    try {
                        BatchResult result = future.get();
                        successCount.addAndGet(result.successCount);
                        if (!result.errors.isEmpty()) {
                            synchronized (errors) {
                                errors.addAll(result.errors);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("获取批次结果失败", e);
                    }
                }
            } catch (TimeoutException e) {
                logger.error("导入超时", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("采购订单导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                    totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());
            
            return new PurchaseOrderImportResponse(
                    new PurchaseOrderImportResponse.PurchaseOrderImportResult(
                            totalOrderCount,
                            successCount.get(),
                            totalOrderCount - successCount.get(),
                            new ArrayList<>(errors))
            );
        }
        
        private Map<String, Supplier> preloadSuppliers(
                Map<String, PurchaseOrderHeader> headerMap,
                List<PurchaseOrderImportResponse.ImportError> errors) {
            
            Set<String> supplierCodes = new HashSet<>(Math.max(100, headerMap.size() / 2));
            for (PurchaseOrderHeader header : headerMap.values()) {
                String code = trimOrNull(header.supplierCode);
                if (code != null) {
                    supplierCodes.add(code);
                }
            }
            
            if (supplierCodes.isEmpty()) {
                logger.info("未找到需要查询的供应商");
                return new HashMap<>();
            }
            
            logger.info("开始预加载 {} 个供应商", supplierCodes.size());
            long startTime = System.currentTimeMillis();
            
            Map<String, Supplier> supplierCache = new HashMap<>(supplierCodes.size());
            
            // 批量查询供应商（不自动创建）
            List<String> codesToQuery = new ArrayList<>(supplierCodes);
            for (int i = 0; i < codesToQuery.size(); i += 1000) {
                int end = Math.min(i + 1000, codesToQuery.size());
                List<String> chunk = codesToQuery.subList(i, end);
                List<Supplier> suppliers = supplierRepository.findByCodeIn(chunk);
                for (Supplier supplier : suppliers) {
                    supplierCache.put(supplier.getCode(), supplier);
                }
            }
            
            // 检查是否有不存在的供应商
            for (String code : supplierCodes) {
                if (!supplierCache.containsKey(code)) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        synchronized (errors) {
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                        "供应商", 0, "供应商编码",
                                        String.format("供应商不存在: %s", code)));
                            }
                        }
                    }
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("供应商预加载完成：共 {} 个，找到 {} 个，耗时 {}ms",
                    supplierCodes.size(), supplierCache.size(), duration);
            
            return supplierCache;
        }
        
        private void preloadMaterialsUnitsAndBoms(
                Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, BillOfMaterial> bomCache,
                List<PurchaseOrderImportResponse.ImportError> errors) {
            
            // 预估容量
            int estimatedItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
            Set<String> materialCodes = new HashSet<>(Math.max(100, estimatedItemCount / 2));
            Set<String> unitCodes = new HashSet<>(Math.max(50, estimatedItemCount / 10));
            Set<String> salUnitCodes = new HashSet<>(Math.max(50, estimatedItemCount / 10));
            Map<String, String> bomKeys = new HashMap<>(Math.max(100, estimatedItemCount / 10)); // materialCode:version -> bom
            
            for (Map<Integer, PurchaseOrderItemHeader> items : itemHeaderMap.values()) {
                for (PurchaseOrderItemHeader item : items.values()) {
                    String materialCode = trimOrNull(item.materialCode);
                    if (materialCode != null) {
                        materialCodes.add(materialCode);
                        
                        // 处理BOM版本
                        String bomVersion = trimOrNull(item.bomVersion);
                        if (bomVersion != null) {
                            bomKeys.put(materialCode + ":" + bomVersion, materialCode + ":" + bomVersion);
                        }
                    }
                    String unitCode = trimOrNull(item.unitCode);
                    if (unitCode != null) {
                        unitCodes.add(unitCode);
                    }
                    String salUnitCode = trimOrNull(item.salUnitCode);
                    if (salUnitCode != null) {
                        salUnitCodes.add(salUnitCode);
                    }
                }
            }
            
            // 批量查询物料
            if (!materialCodes.isEmpty()) {
                List<String> materialCodeList = new ArrayList<>(materialCodes);
                for (int i = 0; i < materialCodeList.size(); i += 1000) {
                    int end = Math.min(i + 1000, materialCodeList.size());
                    List<String> chunk = materialCodeList.subList(i, end);
                    List<Material> materials = materialRepository.findByCodeIn(chunk);
                    for (Material material : materials) {
                        materialCache.put(material.getCode(), material);
                    }
                }
            }
            
            // 批量查询单位（采购单位和销售单位）
            Set<String> allUnitCodes = new HashSet<>(unitCodes.size() + salUnitCodes.size());
            allUnitCodes.addAll(unitCodes);
            allUnitCodes.addAll(salUnitCodes);
            if (!allUnitCodes.isEmpty()) {
                List<String> unitCodeList = new ArrayList<>(allUnitCodes);
                for (int i = 0; i < unitCodeList.size(); i += 1000) {
                    int end = Math.min(i + 1000, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    List<Unit> units = unitRepository.findByCodeIn(chunk);
                    for (Unit unit : units) {
                        unitCache.put(unit.getCode(), unit);
                    }
                }
            }
            
            // 批量查询BOM（根据物料ID和版本）
            if (!bomKeys.isEmpty()) {
                for (Map.Entry<String, String> entry : bomKeys.entrySet()) {
                    String[] parts = entry.getKey().split(":", 2);
                    if (parts.length == 2) {
                        String materialCode = parts[0];
                        String version = parts[1];
                        Material material = materialCache.get(materialCode);
                        if (material != null) {
                            bomRepository.findByMaterialIdAndVersion(material.getId(), version)
                                    .ifPresent(bom -> bomCache.put(entry.getKey(), bom));
                        }
                    }
                }
            }
        }
        
        private Map<String, PurchaseOrder> preloadExistingOrders(Set<String> billNos) {
            Map<String, PurchaseOrder> existingOrderMap = new HashMap<>();
            for (String billNo : billNos) {
                purchaseOrderRepository.findByBillNo(billNo).ifPresent(order -> {
                    existingOrderMap.put(billNo, order);
                });
            }
            return existingOrderMap;
        }
        
        private int importBatchOrders(
                List<Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>>> batch,
                Map<String, PurchaseOrderHeader> headerMap,
                Map<String, Supplier> supplierCache,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, BillOfMaterial> bomCache,
                Map<String, PurchaseOrder> existingOrderMap,
                List<PurchaseOrderImportResponse.ImportError> errors) {
            
            // 用于收集需要保存的实体，实现真正的批量插入
            List<PurchaseOrder> ordersToSave = new ArrayList<>();
            List<OrderItemData> itemsToSave = new ArrayList<>();
            
            // 用于跟踪订单和订单明细的映射关系
            Map<String, PurchaseOrder> orderMap = new LinkedHashMap<>();
            Map<String, Map<Integer, PurchaseOrderItem>> itemMap = new LinkedHashMap<>();
            
            // 第一遍：验证数据并准备实体对象
            for (Map.Entry<String, Map<Integer, PurchaseOrderItemHeader>> entry : batch) {
                String billNo = entry.getKey();
                PurchaseOrderHeader header = headerMap.get(billNo);
                Map<Integer, PurchaseOrderItemHeader> items = entry.getValue();
                
                try {
                    // 验证订单必须至少有一个订单明细
                    if (items == null || items.isEmpty()) {
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new PurchaseOrderImportResponse.ImportError(
                                    "采购订单", header.rowNumber, "订单明细",
                                    "采购订单必须至少有一个订单明细，订单编号: " + billNo));
                        }
                        logger.warn("订单 {} 没有订单明细，跳过导入", billNo);
                        continue;
                    }
                    
                    // 检查订单是否已存在
                    PurchaseOrder purchaseOrder = existingOrderMap.get(billNo);
                    boolean isNewOrder = (purchaseOrder == null);
                    
                    if (isNewOrder) {
                        // 验证供应商（必须存在，不自动创建）
                        Supplier supplier = null;
                        if (header.supplierCode != null && !header.supplierCode.trim().isEmpty()) {
                            supplier = supplierCache.get(header.supplierCode.trim());
                            if (supplier == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单", header.rowNumber, "供应商编码",
                                            "供应商不存在: " + header.supplierCode));
                                }
                                continue;
                            }
                        } else {
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单", header.rowNumber, "供应商编码",
                                        "供应商编码为空"));
                            }
                            continue;
                        }
                        
                        // 解析订单日期
                        LocalDate orderDate = null;
                        if (header.orderDate != null && !header.orderDate.trim().isEmpty()) {
                            try {
                                // 支持多种日期格式
                                String dateStr = header.orderDate.trim();
                                if (dateStr.contains("/")) {
                                    dateStr = dateStr.replace("/", "-");
                                }
                                orderDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                            } catch (Exception e) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单", header.rowNumber, "日期",
                                            "日期格式错误: " + header.orderDate));
                                }
                                continue;
                            }
                        } else {
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单", header.rowNumber, "日期",
                                        "订单日期为空"));
                            }
                            continue;
                        }
                        
                        // 创建订单（暂不保存）
                        purchaseOrder = PurchaseOrder.builder()
                                .billNo(header.billNo)
                                .orderDate(orderDate)
                                .supplier(supplier)
                                .note(header.note)
                                .status(PurchaseOrder.OrderStatus.OPEN)
                                .build();
                        ordersToSave.add(purchaseOrder);
                    }
                    
                    // 保存订单到映射表（无论新旧）
                    orderMap.put(billNo, purchaseOrder);
                    itemMap.put(billNo, new LinkedHashMap<>());
                    
                    // 处理订单明细
                    logger.debug("处理订单 {} 的明细，共 {} 条明细", billNo, items.size());
                    for (Map.Entry<Integer, PurchaseOrderItemHeader> itemEntry : items.entrySet()) {
                        Integer sequence = itemEntry.getKey();
                        PurchaseOrderItemHeader itemHeader = itemEntry.getValue();
                        
                        try {
                            // 验证物料
                            if (itemHeader.materialCode == null || itemHeader.materialCode.trim().isEmpty()) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单明细", itemHeader.rowNumber, "物料编码",
                                            "物料编码为空"));
                                }
                                continue;
                            }
                            
                            Material material = materialCache.get(itemHeader.materialCode.trim());
                            if (material == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单明细", itemHeader.rowNumber, "物料编码",
                                            "物料不存在: " + itemHeader.materialCode));
                                }
                                continue;
                            }
                            
                            // 验证单位
                            if (itemHeader.unitCode == null || itemHeader.unitCode.trim().isEmpty()) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单明细", itemHeader.rowNumber, "单位编码",
                                            "单位编码为空"));
                                }
                                continue;
                            }
                            
                            Unit unit = unitCache.get(itemHeader.unitCode.trim());
                            if (unit == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单明细", itemHeader.rowNumber, "单位编码",
                                            "单位不存在: " + itemHeader.unitCode));
                                }
                                continue;
                            }
                            
                            // 解析数量
                            BigDecimal qty = null;
                            if (itemHeader.qty != null && !itemHeader.qty.trim().isEmpty()) {
                                try {
                                    String qtyStr = itemHeader.qty.trim().replace(",", "");
                                    qty = new BigDecimal(qtyStr);
                                } catch (Exception e) {
                                    if (errors.size() < MAX_ERROR_COUNT) {
                                        errors.add(new PurchaseOrderImportResponse.ImportError(
                                                "采购订单明细", itemHeader.rowNumber, "采购数量",
                                                "数量格式错误: " + itemHeader.qty));
                                    }
                                    continue;
                                }
                            } else {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new PurchaseOrderImportResponse.ImportError(
                                            "采购订单明细", itemHeader.rowNumber, "采购数量",
                                            "采购数量为空"));
                                }
                                continue;
                            }
                            
                            // 解析BOM版本
                            BillOfMaterial bom = null;
                            if (itemHeader.materialCode != null && itemHeader.bomVersion != null) {
                                String bomKey = itemHeader.materialCode.trim() + ":" + itemHeader.bomVersion.trim();
                                bom = bomCache.get(bomKey);
                            }
                            
                            // 解析计划确认
                            Boolean planConfirm = false;
                            if (itemHeader.planConfirm != null && !itemHeader.planConfirm.trim().isEmpty()) {
                                String planConfirmStr = itemHeader.planConfirm.trim().toUpperCase();
                                planConfirm = "TRUE".equals(planConfirmStr) || "1".equals(planConfirmStr) || "是".equals(planConfirmStr);
                            }
                            
                            // 解析销售单位
                            Unit salUnit = null;
                            if (itemHeader.salUnitCode != null && !itemHeader.salUnitCode.trim().isEmpty()) {
                                salUnit = unitCache.get(itemHeader.salUnitCode.trim());
                            }
                            
                            // 解析销售相关数量
                            BigDecimal salQty = parseBigDecimal(itemHeader.salQty);
                            BigDecimal salJoinQty = parseBigDecimal(itemHeader.salJoinQty);
                            BigDecimal baseSalJoinQty = parseBigDecimal(itemHeader.baseSalJoinQty);
                            BigDecimal salBaseQty = parseBigDecimal(itemHeader.salBaseQty);
                            
                            // 创建订单明细（暂不保存）
                            PurchaseOrderItem item = PurchaseOrderItem.builder()
                                    .purchaseOrder(purchaseOrder)
                                    .sequence(sequence)
                                    .material(material)
                                    .bom(bom)
                                    .materialDesc(itemHeader.materialDesc)
                                    .unit(unit)
                                    .qty(qty)
                                    .planConfirm(planConfirm)
                                    .salUnit(salUnit)
                                    .salQty(salQty)
                                    .salJoinQty(salJoinQty)
                                    .baseSalJoinQty(baseSalJoinQty)
                                    .remarks(itemHeader.remarks)
                                    .salBaseQty(salBaseQty)
                                    .build();
                            
                            itemsToSave.add(new OrderItemData(billNo, sequence, item, itemHeader.rowNumber));
                            itemMap.get(billNo).put(sequence, item);
                        } catch (Exception e) {
                            logger.error("处理订单明细失败，行号: {}", itemHeader.rowNumber, e);
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单明细", itemHeader.rowNumber, null,
                                        "处理失败: " + e.getMessage()));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("处理订单失败，单据编号: {}", header.billNo, e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new PurchaseOrderImportResponse.ImportError(
                                "采购订单", header.rowNumber, "单据编号",
                                "处理失败: " + e.getMessage()));
                    }
                }
            }
            
            // 第二遍：批量保存实体
            int successCount = 0;
            
            try {
                // 1. 批量保存所有新订单
                if (!ordersToSave.isEmpty()) {
                    logger.debug("批量保存 {} 个采购订单", ordersToSave.size());
                    List<PurchaseOrder> savedOrders = purchaseOrderRepository.saveAll(ordersToSave);
                    // 更新订单映射，确保ID已设置
                    for (PurchaseOrder savedOrder : savedOrders) {
                        orderMap.put(savedOrder.getBillNo(), savedOrder);
                    }
                }
                
                // 2. 批量保存所有订单明细
                if (!itemsToSave.isEmpty()) {
                    logger.debug("批量保存 {} 条订单明细", itemsToSave.size());
                    // 更新订单明细中的订单引用（确保ID已设置）
                    for (OrderItemData itemData : itemsToSave) {
                        PurchaseOrder order = orderMap.get(itemData.billNo);
                        if (order != null) {
                            itemData.item.setPurchaseOrder(order);
                        }
                    }
                    List<PurchaseOrderItem> savedItems = purchaseOrderItemRepository.saveAll(
                            itemsToSave.stream().map(od -> od.item).toList());
                    // 更新订单明细映射，确保ID已设置
                    // 使用索引匹配，因为 saveAll 会保持顺序
                    for (int i = 0; i < savedItems.size() && i < itemsToSave.size(); i++) {
                        PurchaseOrderItem savedItem = savedItems.get(i);
                        OrderItemData itemData = itemsToSave.get(i);
                        itemMap.get(itemData.billNo).put(itemData.sequence, savedItem);
                    }
                }
                
                // 3. 批量保存所有交货明细
                // if (!deliveriesToSave.isEmpty()) { // deliveriesToSave is removed
                //     logger.debug("批量保存 {} 条交货明细", deliveriesToSave.size());
                //     // 更新交货明细中的订单明细引用（确保ID已设置）
                //     for (DeliveryData deliveryData : deliveriesToSave) {
                //         Map<Integer, PurchaseOrderItem> items = itemMap.get(deliveryData.billNo);
                //         if (items != null) {
                //             PurchaseOrderItem item = items.get(deliveryData.sequence);
                //             if (item != null) {
                //                 deliveryData.delivery.setPurchaseOrderItem(item);
                //             }
                //         }
                //     }
                //     // purchaseOrderDeliveryRepository.saveAll(
                //     //         deliveriesToSave.stream().map(dd -> dd.delivery).toList());
                // }
                
                // 4. 批量更新订单状态
                
                // 统计成功数量（只统计有订单明细的订单）
                Set<String> ordersWithItems = new HashSet<>();
                for (OrderItemData itemData : itemsToSave) {
                    ordersWithItems.add(itemData.billNo);
                }
                successCount = ordersWithItems.size();
                logger.debug("批次处理完成，成功保存 {} 个订单（共 {} 条订单明细）", 
                        successCount, itemsToSave.size());
                
            } catch (Exception e) {
                logger.error("批量保存失败", e);
                // 将批量保存错误转换为单个订单错误
                for (String billNo : orderMap.keySet()) {
                    if (errors.size() < MAX_ERROR_COUNT) {
                        PurchaseOrderHeader header = headerMap.get(billNo);
                        if (header != null) {
                            errors.add(new PurchaseOrderImportResponse.ImportError(
                                    "采购订单", header.rowNumber, "单据编号",
                                    "批量保存失败: " + e.getMessage()));
                        }
                    }
                }
            }
            
            return successCount;
        }
        
        // 内部数据类，用于批量保存时跟踪关联关系
        private record OrderItemData(
                String billNo,
                Integer sequence,
                PurchaseOrderItem item,
                int rowNumber
        ) {}
        
        private BigDecimal parseBigDecimal(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                String qtyStr = value.trim().replace(",", "");
                return new BigDecimal(qtyStr);
            } catch (Exception e) {
                return null;
            }
        }
    }
    
    // 内部数据类
    private record PurchaseOrderHeader(
            int rowNumber,
            String billNo,
            String orderDate,
            String note,
            String supplierCode,
            String supplierName
    ) {}
    
    private record PurchaseOrderItemHeader(
            int rowNumber,
            Integer sequence,
            String materialCode,
            String bomVersion,
            String materialDesc,
            String unitCode,
            String qty,
            String planConfirm,
            String salUnitCode,
            String salQty,
            String salJoinQty,
            String baseSalJoinQty,
            String remarks,
            String salBaseQty
    ) {}
    
    private record PurchaseOrderData(
            PurchaseOrderHeader header,
            PurchaseOrderItemHeader itemHeader
    ) {}
    
    // 批次处理结果
    private record BatchResult(
            int successCount,
            List<PurchaseOrderImportResponse.ImportError> errors
    ) {}
}

