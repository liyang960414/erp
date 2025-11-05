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
    
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderDeliveryRepository purchaseOrderDeliveryRepository;
    private final SupplierRepository supplierRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final BillOfMaterialRepository bomRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public PurchaseOrderImportService(
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository purchaseOrderItemRepository,
            PurchaseOrderDeliveryRepository purchaseOrderDeliveryRepository,
            SupplierRepository supplierRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            BillOfMaterialRepository bomRepository,
            PurchaseOrderService purchaseOrderService,
            PlatformTransactionManager transactionManager) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.purchaseOrderDeliveryRepository = purchaseOrderDeliveryRepository;
        this.supplierRepository = supplierRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.bomRepository = bomRepository;
        this.purchaseOrderService = purchaseOrderService;
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
            
            // 检查是否是新订单（单据编号不为空）
            String billNo = data.getBillNo();
            if (billNo != null && !billNo.trim().isEmpty()) {
                currentBillNo = billNo.trim();
                // 创建新的订单头
                currentHeader = new PurchaseOrderHeader(
                        rowNum,
                        currentBillNo,
                        data.getOrderDate(),
                        null, // CSV文件中没有备注字段
                        data.getSupplierCode(),
                        data.getSupplierName()
                );
                currentItemHeader = null; // 重置明细头
            }
            
            // 检查是否是新订单明细（订单明细序号不为空）
            String purchaseOrderEntry = data.getPurchaseOrderEntry();
            boolean isNewItem = false;
            if (purchaseOrderEntry != null && !purchaseOrderEntry.trim().isEmpty()) {
                if (currentHeader != null) {
                    // 创建新的明细头
                    currentItemHeader = new PurchaseOrderItemHeader(
                            rowNum,
                            purchaseOrderEntry != null && !purchaseOrderEntry.trim().isEmpty()
                                    ? Integer.parseInt(purchaseOrderEntry.trim()) : null,
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
                    isNewItem = true;
                    logger.debug("检测到新订单明细，行号: {}, 订单编号: {}, 明细序号: {}, 物料编码: {}", 
                            rowNum, currentBillNo, purchaseOrderEntry, data.getMaterialCode());
                } else {
                    logger.warn("检测到订单明细但没有订单头，行号: {}, 明细序号: {}", rowNum, purchaseOrderEntry);
                }
            }
            
            // 如果有订单头、明细头，处理交货明细
            if (currentHeader != null && currentItemHeader != null) {
                String deliveryEntry = data.getDeliveryEntry();
                if (deliveryEntry != null && !deliveryEntry.trim().isEmpty()) {
                    // 有交货明细，添加交货明细项
                    PurchaseOrderDeliveryData deliveryData = new PurchaseOrderDeliveryData(
                            rowNum,
                            deliveryEntry != null && !deliveryEntry.trim().isEmpty()
                                    ? Integer.parseInt(deliveryEntry.trim()) : null,
                            data.getDeliveryDate(),
                            data.getPlanQty(),
                            data.getSupplierDeliveryDate(),
                            data.getPreArrivalDate(),
                            data.getTransportLeadTime()
                    );
                    
                    PurchaseOrderData orderData = new PurchaseOrderData(
                            currentHeader, 
                            currentItemHeader, 
                            deliveryData
                    );
                    orderDataList.add(orderData);
                } else if (isNewItem) {
                    // 新订单明细但没有交货明细，也添加记录（使用null作为占位符）
                    // 这样确保订单明细被收集，即使没有交货明细
                    PurchaseOrderDeliveryData deliveryData = null;
                    PurchaseOrderData orderData = new PurchaseOrderData(
                            currentHeader, 
                            currentItemHeader, 
                            deliveryData
                    );
                    orderDataList.add(orderData);
                    logger.debug("添加订单明细（无交货明细），行号: {}, 订单编号: {}, 明细序号: {}", 
                            rowNum, currentBillNo, currentItemHeader.sequence);
                }
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("采购订单数据收集完成，共 {} 条订单交货明细数据", orderDataList.size());
        }
        
        public PurchaseOrderImportResponse importToDatabase() {
            if (orderDataList.isEmpty()) {
                logger.info("未找到采购订单数据");
                return new PurchaseOrderImportResponse(
                        new PurchaseOrderImportResponse.PurchaseOrderImportResult(0, 0, 0, new ArrayList<>())
                );
            }
            
            long startTime = System.currentTimeMillis();
            
            // 按订单头、明细分组
            Map<String, Map<Integer, List<PurchaseOrderDeliveryData>>> orderGroups = new LinkedHashMap<>();
            Map<String, PurchaseOrderHeader> headerMap = new LinkedHashMap<>();
            Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap = new LinkedHashMap<>();
            
            for (PurchaseOrderData data : orderDataList) {
                String billNo = data.header.billNo;
                Integer sequence = data.itemHeader.sequence;
                
                headerMap.putIfAbsent(billNo, data.header);
                itemHeaderMap.computeIfAbsent(billNo, k -> new LinkedHashMap<>()).putIfAbsent(sequence, data.itemHeader);
                // 只有交货明细不为null时才添加到分组中
                if (data.delivery != null) {
                    orderGroups.computeIfAbsent(billNo, k -> new LinkedHashMap<>())
                            .computeIfAbsent(sequence, k -> new ArrayList<>())
                            .add(data.delivery);
                } else {
                    // 如果没有交货明细，也要确保订单明细被记录（创建空的交货明细列表）
                    orderGroups.computeIfAbsent(billNo, k -> new LinkedHashMap<>())
                            .computeIfAbsent(sequence, k -> new ArrayList<>());
                }
            }
            
            int totalOrderCount = orderGroups.size();
            int totalItemCount = itemHeaderMap.values().stream().mapToInt(Map::size).sum();
            int totalDeliveryCount = orderDataList.stream()
                    .mapToInt(data -> data.delivery != null ? 1 : 0)
                    .sum();
            logger.info("找到 {} 个订单，{} 条明细，{} 条交货明细，开始导入到数据库", 
                    totalOrderCount, totalItemCount, totalDeliveryCount);
            
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
            Map<String, PurchaseOrder> existingOrderMap = preloadExistingOrders(orderGroups.keySet());
            
            // 将订单分组转换为列表以便批量处理
            List<Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>>> orderList = 
                    new ArrayList<>(orderGroups.entrySet());
            
            // 使用多线程并行处理批次
            List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
            Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
            
            int totalBatches = (orderList.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            logger.info("开始并行处理 {} 个批次，最大并发数: {}", totalBatches, MAX_CONCURRENT_BATCHES);
            
            // 提交所有批次任务到线程池
            for (int i = 0; i < orderList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, orderList.size());
                List<Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>>> batch = new ArrayList<>(
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
                                return importBatchOrders(batch, headerMap, itemHeaderMap, 
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
                        for (Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>> entry : batch) {
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
                        for (Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>> entry : batch) {
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
            
            Set<String> supplierCodes = new HashSet<>();
            for (PurchaseOrderHeader header : headerMap.values()) {
                if (header.supplierCode != null && !header.supplierCode.trim().isEmpty()) {
                    supplierCodes.add(header.supplierCode.trim());
                }
            }
            
            if (supplierCodes.isEmpty()) {
                logger.info("未找到需要查询的供应商");
                return new HashMap<>();
            }
            
            logger.info("开始预加载 {} 个供应商", supplierCodes.size());
            long startTime = System.currentTimeMillis();
            
            Map<String, Supplier> supplierCache = new HashMap<>();
            
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
            
            Set<String> materialCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();
            Set<String> salUnitCodes = new HashSet<>();
            Map<String, String> bomKeys = new HashMap<>(); // materialCode:version -> bom
            
            for (Map<Integer, PurchaseOrderItemHeader> items : itemHeaderMap.values()) {
                for (PurchaseOrderItemHeader item : items.values()) {
                    if (item.materialCode != null && !item.materialCode.trim().isEmpty()) {
                        materialCodes.add(item.materialCode.trim());
                    }
                    if (item.unitCode != null && !item.unitCode.trim().isEmpty()) {
                        unitCodes.add(item.unitCode.trim());
                    }
                    if (item.salUnitCode != null && !item.salUnitCode.trim().isEmpty()) {
                        salUnitCodes.add(item.salUnitCode.trim());
                    }
                    if (item.materialCode != null && item.bomVersion != null) {
                        bomKeys.put(item.materialCode.trim() + ":" + item.bomVersion.trim(), 
                                item.materialCode.trim() + ":" + item.bomVersion.trim());
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
            Set<String> allUnitCodes = new HashSet<>(unitCodes);
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
                List<Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>>> batch,
                Map<String, PurchaseOrderHeader> headerMap,
                Map<String, Map<Integer, PurchaseOrderItemHeader>> itemHeaderMap,
                Map<String, Supplier> supplierCache,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, BillOfMaterial> bomCache,
                Map<String, PurchaseOrder> existingOrderMap,
                List<PurchaseOrderImportResponse.ImportError> errors) {
            
            int successCount = 0;
            
            for (Map.Entry<String, Map<Integer, List<PurchaseOrderDeliveryData>>> entry : batch) {
                String billNo = entry.getKey();
                PurchaseOrderHeader header = headerMap.get(billNo);
                Map<Integer, PurchaseOrderItemHeader> items = itemHeaderMap.get(billNo);
                Map<Integer, List<PurchaseOrderDeliveryData>> deliveries = entry.getValue();
                
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
                        
                        // 创建订单
                        purchaseOrder = PurchaseOrder.builder()
                                .billNo(header.billNo)
                                .orderDate(orderDate)
                                .supplier(supplier)
                                .note(header.note)
                                .status(PurchaseOrder.OrderStatus.OPEN)
                                .build();
                        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
                    }
                    
                    // 处理订单明细和交货明细
                    logger.debug("处理订单 {} 的明细，共 {} 条明细", billNo, items.size());
                    int savedItemCount = 0; // 记录成功保存的订单明细数量
                    for (Map.Entry<Integer, PurchaseOrderItemHeader> itemEntry : items.entrySet()) {
                        Integer sequence = itemEntry.getKey();
                        PurchaseOrderItemHeader itemHeader = itemEntry.getValue();
                        List<PurchaseOrderDeliveryData> deliveryList = deliveries.getOrDefault(sequence, new ArrayList<>());
                        logger.debug("处理订单明细，订单编号: {}, 明细序号: {}, 物料编码: {}, 交货明细数量: {}", 
                                billNo, sequence, itemHeader.materialCode, deliveryList.size());
                        
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
                            
                            // 创建订单明细
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
                            item = purchaseOrderItemRepository.save(item);
                            savedItemCount++; // 订单明细保存成功，计数+1
                            logger.debug("订单明细保存成功，订单编号: {}, 明细序号: {}, 物料编码: {}, 订单明细ID: {}", 
                                    billNo, sequence, itemHeader.materialCode, item.getId());
                            
                            // 处理交货明细
                            List<PurchaseOrderDelivery> deliveriesToSave = new ArrayList<>();
                            for (PurchaseOrderDeliveryData deliveryData : deliveryList) {
                                try {
                                    // 解析交货日期
                                    LocalDate deliveryDate = null;
                                    if (deliveryData.deliveryDate != null && !deliveryData.deliveryDate.trim().isEmpty()) {
                                        try {
                                            String dateStr = deliveryData.deliveryDate.trim();
                                            if (dateStr.contains("/")) {
                                                dateStr = dateStr.replace("/", "-");
                                            }
                                            deliveryDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                                        } catch (Exception e) {
                                            if (errors.size() < MAX_ERROR_COUNT) {
                                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                                        "采购订单交货明细", deliveryData.rowNumber, "交货日期",
                                                        "日期格式错误: " + deliveryData.deliveryDate));
                                            }
                                            continue;
                                        }
                                    } else {
                                        if (errors.size() < MAX_ERROR_COUNT) {
                                            errors.add(new PurchaseOrderImportResponse.ImportError(
                                                    "采购订单交货明细", deliveryData.rowNumber, "交货日期",
                                                    "交货日期为空"));
                                        }
                                        continue;
                                    }
                                    
                                    // 解析计划数量
                                    BigDecimal planQty = null;
                                    if (deliveryData.planQty != null && !deliveryData.planQty.trim().isEmpty()) {
                                        try {
                                            String planQtyStr = deliveryData.planQty.trim().replace(",", "");
                                            planQty = new BigDecimal(planQtyStr);
                                        } catch (Exception e) {
                                            if (errors.size() < MAX_ERROR_COUNT) {
                                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                                        "采购订单交货明细", deliveryData.rowNumber, "计划数量",
                                                        "数量格式错误: " + deliveryData.planQty));
                                            }
                                            continue;
                                        }
                                    } else {
                                        if (errors.size() < MAX_ERROR_COUNT) {
                                            errors.add(new PurchaseOrderImportResponse.ImportError(
                                                    "采购订单交货明细", deliveryData.rowNumber, "计划数量",
                                                    "计划数量为空"));
                                        }
                                        continue;
                                    }
                                    
                                    // 解析其他日期
                                    LocalDate supplierDeliveryDate = parseDate(deliveryData.supplierDeliveryDate);
                                    LocalDate preArrivalDate = parseDate(deliveryData.preArrivalDate);
                                    
                                    // 解析运输提前期
                                    Integer transportLeadTime = null;
                                    if (deliveryData.transportLeadTime != null && !deliveryData.transportLeadTime.trim().isEmpty()) {
                                        try {
                                            transportLeadTime = Integer.parseInt(deliveryData.transportLeadTime.trim());
                                        } catch (Exception e) {
                                            // 运输提前期可以为空，忽略解析错误
                                        }
                                    }
                                    
                                    // 创建交货明细
                                    PurchaseOrderDelivery delivery = PurchaseOrderDelivery.builder()
                                            .purchaseOrderItem(item)
                                            .sequence(deliveryData.sequence != null ? deliveryData.sequence : 1)
                                            .deliveryDate(deliveryDate)
                                            .planQty(planQty)
                                            .supplierDeliveryDate(supplierDeliveryDate)
                                            .preArrivalDate(preArrivalDate)
                                            .transportLeadTime(transportLeadTime)
                                            .build();
                                    deliveriesToSave.add(delivery);
                                } catch (Exception e) {
                                    logger.error("处理交货明细失败，行号: {}", deliveryData.rowNumber, e);
                                    if (errors.size() < MAX_ERROR_COUNT) {
                                        errors.add(new PurchaseOrderImportResponse.ImportError(
                                                "采购订单交货明细", deliveryData.rowNumber, null,
                                                "处理失败: " + e.getMessage()));
                                    }
                                }
                            }
                            
                            // 批量保存交货明细（交货明细是可选的，订单明细即使没有交货明细也会被保存）
                            if (!deliveriesToSave.isEmpty() && purchaseOrder != null) {
                                purchaseOrderDeliveryRepository.saveAll(deliveriesToSave);
                                logger.debug("保存交货明细，订单编号: {}, 明细序号: {}, 交货明细数量: {}", 
                                        billNo, sequence, deliveriesToSave.size());
                            } else if (deliveryList.isEmpty() && purchaseOrder != null) {
                                // 订单明细没有交货明细，这是允许的，只记录日志
                                logger.debug("订单明细没有交货明细（这是允许的），订单编号: {}, 明细序号: {}", 
                                        billNo, sequence);
                            }
                            
                            // 检查并更新订单状态（无论是否有交货明细）
                            if (purchaseOrder != null) {
                                purchaseOrderService.checkAndUpdateOrderStatus(purchaseOrder.getId());
                            }
                        } catch (Exception e) {
                            logger.error("处理订单明细失败，行号: {}", itemHeader.rowNumber, e);
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new PurchaseOrderImportResponse.ImportError(
                                        "采购订单明细", itemHeader.rowNumber, null,
                                        "处理失败: " + e.getMessage()));
                            }
                        }
                    }
                    
                    // 验证订单必须至少有一个有效的订单明细被保存
                    if (savedItemCount == 0) {
                        logger.error("订单 {} 创建后没有任何有效的订单明细被保存，订单ID: {}", 
                                billNo, purchaseOrder != null ? purchaseOrder.getId() : null);
                        if (errors.size() < MAX_ERROR_COUNT) {
                            errors.add(new PurchaseOrderImportResponse.ImportError(
                                    "采购订单", header.rowNumber, "订单明细",
                                    String.format("订单 %s 创建后没有任何有效的订单明细被保存，可能所有订单明细都验证失败", billNo)));
                        }
                        // 如果订单是新创建的且没有明细，可以考虑删除订单，但为了数据完整性，保留订单并记录错误
                        // 这样用户可以看到问题并手动处理
                    } else {
                        logger.debug("订单 {} 成功保存了 {} 条订单明细", billNo, savedItemCount);
                        successCount++;
                    }
                } catch (Exception e) {
                    logger.error("保存订单失败，单据编号: {}", header.billNo, e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new PurchaseOrderImportResponse.ImportError(
                                "采购订单", header.rowNumber, "单据编号",
                                "保存失败: " + e.getMessage()));
                    }
                }
            }
            
            return successCount;
        }
        
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
        
        private LocalDate parseDate(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            try {
                String dateStr = value.trim();
                if (dateStr.contains("/")) {
                    dateStr = dateStr.replace("/", "-");
                }
                return LocalDate.parse(dateStr, DATE_FORMATTER);
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
    
    private record PurchaseOrderDeliveryData(
            int rowNumber,
            Integer sequence,
            String deliveryDate,
            String planQty,
            String supplierDeliveryDate,
            String preArrivalDate,
            String transportLeadTime
    ) {}
    
    private record PurchaseOrderData(
            PurchaseOrderHeader header,
            PurchaseOrderItemHeader itemHeader,
            PurchaseOrderDeliveryData delivery  // 可以为null，表示订单明细没有交货明细
    ) {}
    
    // 批次处理结果
    private record BatchResult(
            int successCount,
            List<PurchaseOrderImportResponse.ImportError> errors
    ) {}
}

