package com.sambound.erp.service;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import com.sambound.erp.dto.SaleOrderExcelRow;
import com.sambound.erp.dto.SaleOrderImportResponse;
import com.sambound.erp.entity.Customer;
import com.sambound.erp.entity.Material;
import com.sambound.erp.entity.SaleOrder;
import com.sambound.erp.entity.SaleOrderItem;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.repository.CustomerRepository;
import com.sambound.erp.repository.MaterialRepository;
import com.sambound.erp.repository.SaleOrderItemRepository;
import com.sambound.erp.repository.SaleOrderRepository;
import com.sambound.erp.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SaleOrderImportService {
    
    private static final Logger logger = LoggerFactory.getLogger(SaleOrderImportService.class);
    private static final int MAX_ERROR_COUNT = 1000;
    private static final int BATCH_SIZE = 100; // 每批处理的订单数量
    private static final int MAX_CONCURRENT_BATCHES = 10; // 最大并发批次数量，避免数据库连接池耗尽
    
    private final SaleOrderRepository saleOrderRepository;
    private final SaleOrderItemRepository saleOrderItemRepository;
    private final CustomerRepository customerRepository;
    private final MaterialRepository materialRepository;
    private final UnitRepository unitRepository;
    private final CustomerService customerService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    public SaleOrderImportService(
            SaleOrderRepository saleOrderRepository,
            SaleOrderItemRepository saleOrderItemRepository,
            CustomerRepository customerRepository,
            MaterialRepository materialRepository,
            UnitRepository unitRepository,
            CustomerService customerService,
            PlatformTransactionManager transactionManager) {
        this.saleOrderRepository = saleOrderRepository;
        this.saleOrderItemRepository = saleOrderItemRepository;
        this.customerRepository = customerRepository;
        this.materialRepository = materialRepository;
        this.unitRepository = unitRepository;
        this.customerService = customerService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(120);
        // 使用虚拟线程执行器（Java 21+ Virtual Threads）
        // 虚拟线程是轻量级线程，可以创建大量线程而不会消耗过多资源
        // 适合 I/O 密集型任务（如数据库操作），开销极小
        // 注意：虽然虚拟线程可以创建很多，但需要配合信号量限制并发，避免数据库连接池耗尽
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public SaleOrderImportResponse importFromExcel(MultipartFile file) {
        logger.info("开始导入销售订单Excel文件: {}", file.getOriginalFilename());
        
        try {
            byte[] fileBytes = file.getBytes();
            
            SaleOrderDataCollector collector = new SaleOrderDataCollector();
            FastExcel.read(new ByteArrayInputStream(fileBytes), SaleOrderExcelRow.class, collector)
                    .sheet()
                    .headRowNumber(2)  // 前两行为表头
                    .doRead();
            
            SaleOrderImportResponse result = collector.importToDatabase();
            logger.info("销售订单导入完成：总计 {} 条，成功 {} 条，失败 {} 条",
                    result.saleOrderResult().totalRows(), 
                    result.saleOrderResult().successCount(), 
                    result.saleOrderResult().failureCount());
            
            return result;
        } catch (Exception e) {
            logger.error("Excel文件导入失败", e);
            throw new RuntimeException("Excel文件导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 销售订单数据收集器：处理订单头信息只在第一行有值的情况
     */
    private class SaleOrderDataCollector implements ReadListener<SaleOrderExcelRow> {
        private final List<SaleOrderData> orderDataList = new ArrayList<>();
        private final AtomicInteger totalRows = new AtomicInteger(0);
        private SaleOrderHeader currentHeader = null;
        private String currentBillNo = null;
        
        @Override
        public void invoke(SaleOrderExcelRow data, AnalysisContext context) {
            totalRows.incrementAndGet();
            int rowNum = context.readRowHolder().getRowIndex();
            
            // 检查是否是新订单（单据编号不为空）
            String billNo = data.getBillNo();
            if (billNo != null && !billNo.trim().isEmpty()) {
                currentBillNo = billNo.trim();
                // 创建新的订单头
                currentHeader = new SaleOrderHeader(
                        rowNum,
                        currentBillNo,
                        data.getOrderDate(),
                        data.getNote(),
                        data.getWoNumber(),
                        data.getCustomerCode(),
                        data.getCustomerName()
                );
            }
            
            // 如果有订单头且明细序号不为空，添加明细项
            if (currentHeader != null && 
                data.getSaleOrderEntry() != null && !data.getSaleOrderEntry().trim().isEmpty()) {
                
                // 如果当前行的单据编号不为空，使用当前行的单据编号
                if (billNo != null && !billNo.trim().isEmpty()) {
                    currentBillNo = billNo.trim();
                    currentHeader = new SaleOrderHeader(
                            rowNum,
                            currentBillNo,
                            data.getOrderDate() != null ? data.getOrderDate() : currentHeader.orderDate,
                            data.getNote() != null ? data.getNote() : currentHeader.note,
                            data.getWoNumber() != null ? data.getWoNumber() : currentHeader.woNumber,
                            data.getCustomerCode() != null ? data.getCustomerCode() : currentHeader.customerCode,
                            data.getCustomerName() != null ? data.getCustomerName() : currentHeader.customerName
                    );
                }
                
                SaleOrderData orderData = new SaleOrderData(currentHeader, new SaleOrderItemData(
                        rowNum,
                        data.getSaleOrderEntry() != null && !data.getSaleOrderEntry().trim().isEmpty()
                                ? Integer.parseInt(data.getSaleOrderEntry().trim()) : null,
                        data.getMaterialCode(),
                        data.getUnitCode(),
                        data.getQty(),
                        data.getOldQty(),
                        data.getInspectionDate(),
                        data.getDeliveryDate(),
                        data.getBomVersion(),
                        data.getEntryNote()
                ));
                orderDataList.add(orderData);
            }
        }
        
        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            logger.info("销售订单数据收集完成，共 {} 条订单明细数据", orderDataList.size());
        }
        
        public SaleOrderImportResponse importToDatabase() {
            if (orderDataList.isEmpty()) {
                logger.info("未找到销售订单数据");
                return new SaleOrderImportResponse(
                        new SaleOrderImportResponse.SaleOrderImportResult(0, 0, 0, new ArrayList<>())
                );
            }
            
            long startTime = System.currentTimeMillis();
            
            // 按订单头（单据编号）分组
            Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups = new LinkedHashMap<>();
            for (SaleOrderData data : orderDataList) {
                orderGroups.computeIfAbsent(data.header, k -> new ArrayList<>()).add(data.item);
            }
            
            int totalOrderCount = orderGroups.size();
            int totalItemCount = orderDataList.size();
            logger.info("找到 {} 个订单，{} 条明细，开始导入到数据库", totalOrderCount, totalItemCount);
            
            // 使用线程安全的集合收集错误
            List<SaleOrderImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            
            // 预加载并批量创建客户（避免并发创建死锁）
            Map<String, Customer> customerCache = preloadAndCreateCustomers(orderGroups, errors);
            
            // 预加载物料和单位数据
            Map<String, Material> materialCache = new HashMap<>();
            Map<String, Unit> unitCache = new HashMap<>();
            preloadMaterialsAndUnits(orderGroups, materialCache, unitCache, errors);
            
            // 预先批量查询所有已存在的订单
            Map<String, SaleOrder> existingOrderMap = preloadExistingOrders(orderGroups);
            
            // 将订单分组转换为列表以便批量处理
            List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> orderList = 
                    new ArrayList<>(orderGroups.entrySet());
            
            // 使用多线程并行处理批次
            List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
            Semaphore batchSemaphore = new Semaphore(MAX_CONCURRENT_BATCHES);
            
            int totalBatches = (orderList.size() + BATCH_SIZE - 1) / BATCH_SIZE;
            logger.info("开始并行处理 {} 个批次，最大并发数: {}", totalBatches, MAX_CONCURRENT_BATCHES);
            
            // 提交所有批次任务到线程池
            for (int i = 0; i < orderList.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, orderList.size());
                List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> batch = new ArrayList<>(
                        orderList.subList(i, end));
                int batchIndex = (i / BATCH_SIZE) + 1;
                
                // 异步提交批次处理任务
                CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // 获取信号量许可，限制并发数量
                        batchSemaphore.acquire();
                        try {
                            long batchStartTime = System.currentTimeMillis();
                            logger.info("处理批次 {}/{}，订单数量: {}", batchIndex, totalBatches, batch.size());
                            
                            // 每个批次使用独立事务
                            int batchSuccess = transactionTemplate.execute(status -> {
                                return importBatchOrders(batch, customerCache, materialCache, unitCache, existingOrderMap, errors);
                            });
                            
                            long batchDuration = System.currentTimeMillis() - batchStartTime;
                            logger.info("批次 {}/{} 完成，耗时: {}ms，成功: {} 条", 
                                    batchIndex, totalBatches, batchDuration, batchSuccess);
                            
                            return new BatchResult(batchSuccess, new ArrayList<>());
                        } finally {
                            // 释放信号量许可
                            batchSemaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("批次 {} 处理被中断", batchIndex);
                        List<SaleOrderImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new SaleOrderImportResponse.ImportError(
                                        "销售订单", entry.getKey().rowNumber, "单据编号",
                                        "批次处理被中断"));
                            }
                        }
                        return new BatchResult(0, batchErrors);
                    } catch (Exception e) {
                        logger.error("批次 {} 导入失败", batchIndex, e);
                        List<SaleOrderImportResponse.ImportError> batchErrors = new ArrayList<>();
                        for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : batch) {
                            if (batchErrors.size() < MAX_ERROR_COUNT) {
                                batchErrors.add(new SaleOrderImportResponse.ImportError(
                                        "销售订单", entry.getKey().rowNumber, "单据编号",
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
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("导入超时，请检查数据量或重试", e);
            } catch (Exception e) {
                logger.error("批次处理失败", e);
                // 取消未完成的任务
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("批次处理失败: " + e.getMessage(), e);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("销售订单导入完成：总耗时 {}ms，总计 {} 条，成功 {} 条，失败 {} 条",
                    totalDuration, totalOrderCount, successCount.get(), totalOrderCount - successCount.get());
            
            return new SaleOrderImportResponse(
                    new SaleOrderImportResponse.SaleOrderImportResult(
                            totalOrderCount,
                            successCount.get(),
                            totalOrderCount - successCount.get(),
                            new ArrayList<>(errors))
            );
        }
        
        private Map<String, Customer> preloadAndCreateCustomers(
                Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups,
                List<SaleOrderImportResponse.ImportError> errors) {
            
            // 收集所有唯一的客户编码和名称
            Map<String, String> customerCodeToName = new LinkedHashMap<>();
            for (SaleOrderHeader header : orderGroups.keySet()) {
                if (header.customerCode != null && !header.customerCode.trim().isEmpty() &&
                    header.customerName != null && !header.customerName.trim().isEmpty()) {
                    String code = header.customerCode.trim();
                    String name = header.customerName.trim();
                    // 如果同一个编码有多个名称，保留第一个或使用最新的（根据业务需求）
                    customerCodeToName.putIfAbsent(code, name);
                }
            }
            
            if (customerCodeToName.isEmpty()) {
                logger.info("未找到需要创建的客户");
                return new HashMap<>();
            }
            
            logger.info("开始预加载和创建 {} 个客户", customerCodeToName.size());
            long startTime = System.currentTimeMillis();
            
            Map<String, Customer> customerCache = new HashMap<>();
            
            // 先批量查询已存在的客户
            List<String> codesToQuery = new ArrayList<>(customerCodeToName.keySet());
            for (int i = 0; i < codesToQuery.size(); i += 1000) {
                int end = Math.min(i + 1000, codesToQuery.size());
                List<String> chunk = codesToQuery.subList(i, end);
                List<Customer> existingCustomers = customerRepository.findByCodeIn(chunk);
                for (Customer customer : existingCustomers) {
                    customerCache.put(customer.getCode(), customer);
                }
            }
            
            // 批量创建不存在的客户
            List<String> codesToCreate = new ArrayList<>();
            List<String> namesToCreate = new ArrayList<>();
            for (Map.Entry<String, String> entry : customerCodeToName.entrySet()) {
                if (!customerCache.containsKey(entry.getKey())) {
                    codesToCreate.add(entry.getKey());
                    namesToCreate.add(entry.getValue());
                }
            }
            
            if (!codesToCreate.isEmpty()) {
                logger.info("需要创建 {} 个新客户", codesToCreate.size());
                // 使用事务模板批量创建客户
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        for (int i = 0; i < codesToCreate.size(); i++) {
                            try {
                                Customer customer = customerRepository.insertOrGetByCode(
                                        codesToCreate.get(i), namesToCreate.get(i));
                                customerCache.put(customer.getCode(), customer);
                            } catch (Exception e) {
                                logger.error("创建客户失败: {}", codesToCreate.get(i), e);
                                // 记录错误但不中断整个流程
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    synchronized (errors) {
                                        if (errors.size() < MAX_ERROR_COUNT) {
                                            errors.add(new SaleOrderImportResponse.ImportError(
                                                    "客户", 0, "客户编码",
                                                    String.format("创建客户失败 %s: %s", codesToCreate.get(i), e.getMessage())));
                                        }
                                    }
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    logger.error("批量创建客户失败", e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        synchronized (errors) {
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new SaleOrderImportResponse.ImportError(
                                        "客户", 0, "客户编码",
                                        "批量创建客户失败: " + e.getMessage()));
                            }
                        }
                    }
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("客户预加载完成：共 {} 个，已存在 {} 个，新建 {} 个，耗时 {}ms",
                    customerCodeToName.size(), customerCache.size() - codesToCreate.size(), 
                    codesToCreate.size(), duration);
            
            return customerCache;
        }
        
        private void preloadMaterialsAndUnits(
                Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                List<SaleOrderImportResponse.ImportError> errors) {
            
            Set<String> materialCodes = new HashSet<>();
            Set<String> unitCodes = new HashSet<>();
            
            // 收集所有物料编码和单位编码
            for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : orderGroups.entrySet()) {
                for (SaleOrderItemData item : entry.getValue()) {
                    if (item.materialCode != null && !item.materialCode.trim().isEmpty()) {
                        materialCodes.add(item.materialCode.trim());
                    }
                    if (item.unitCode != null && !item.unitCode.trim().isEmpty()) {
                        unitCodes.add(item.unitCode.trim());
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
            
            // 批量查询单位
            if (!unitCodes.isEmpty()) {
                List<String> unitCodeList = new ArrayList<>(unitCodes);
                for (int i = 0; i < unitCodeList.size(); i += 1000) {
                    int end = Math.min(i + 1000, unitCodeList.size());
                    List<String> chunk = unitCodeList.subList(i, end);
                    List<Unit> units = unitRepository.findByCodeIn(chunk);
                    for (Unit unit : units) {
                        unitCache.put(unit.getCode(), unit);
                    }
                }
            }
        }
        
        private Map<String, SaleOrder> preloadExistingOrders(
                Map<SaleOrderHeader, List<SaleOrderItemData>> orderGroups) {
            
            Set<String> billNos = new HashSet<>();
            for (SaleOrderHeader header : orderGroups.keySet()) {
                if (header.billNo != null && !header.billNo.trim().isEmpty()) {
                    billNos.add(header.billNo.trim());
                }
            }
            
            Map<String, SaleOrder> existingOrderMap = new HashMap<>();
            for (String billNo : billNos) {
                saleOrderRepository.findByBillNo(billNo).ifPresent(order -> {
                    existingOrderMap.put(billNo, order);
                });
            }
            
            return existingOrderMap;
        }
        
        private int importBatchOrders(
                List<Map.Entry<SaleOrderHeader, List<SaleOrderItemData>>> batch,
                Map<String, Customer> customerCache,
                Map<String, Material> materialCache,
                Map<String, Unit> unitCache,
                Map<String, SaleOrder> existingOrderMap,
                List<SaleOrderImportResponse.ImportError> errors) {
            
            int successCount = 0;
            
            for (Map.Entry<SaleOrderHeader, List<SaleOrderItemData>> entry : batch) {
                SaleOrderHeader header = entry.getKey();
                List<SaleOrderItemData> items = entry.getValue();
                
                try {
                    // 检查订单是否已存在
                    SaleOrder saleOrder = existingOrderMap.get(header.billNo);
                    boolean isNewOrder = (saleOrder == null);
                    
                    if (isNewOrder) {
                        // 从缓存中获取客户（已在导入前批量创建）
                        Customer customer = null;
                        if (header.customerCode != null && !header.customerCode.trim().isEmpty() &&
                            header.customerName != null && !header.customerName.trim().isEmpty()) {
                            customer = customerCache.get(header.customerCode.trim());
                            if (customer == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单", header.rowNumber, "客户编码",
                                            "客户不存在: " + header.customerCode));
                                }
                                continue;
                            }
                        }
                        
                        // 解析订单日期
                        LocalDate orderDate = null;
                        if (header.orderDate != null && !header.orderDate.trim().isEmpty()) {
                            try {
                                orderDate = LocalDate.parse(header.orderDate.trim(), DATE_FORMATTER);
                            } catch (Exception e) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单", header.rowNumber, "日期",
                                            "日期格式错误: " + header.orderDate));
                                }
                                continue;
                            }
                        } else {
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new SaleOrderImportResponse.ImportError(
                                        "销售订单", header.rowNumber, "日期",
                                        "订单日期为空"));
                            }
                            continue;
                        }
                        
                        // 创建订单
                        saleOrder = SaleOrder.builder()
                                .billNo(header.billNo)
                                .orderDate(orderDate)
                                .note(header.note)
                                .woNumber(header.woNumber)
                                .customer(customer)
                                .build();
                        saleOrder = saleOrderRepository.save(saleOrder);
                    }
                    
                    // 处理订单明细：先验证和解析，然后批量保存
                    List<SaleOrderItem> itemsToSave = new ArrayList<>();
                    for (SaleOrderItemData itemData : items) {
                        try {
                            // 验证物料
                            if (itemData.materialCode == null || itemData.materialCode.trim().isEmpty()) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单明细", itemData.rowNumber, "物料编码",
                                            "物料编码为空"));
                                }
                                continue;
                            }
                            
                            Material material = materialCache.get(itemData.materialCode.trim());
                            if (material == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单明细", itemData.rowNumber, "物料编码",
                                            "物料不存在: " + itemData.materialCode));
                                }
                                continue;
                            }
                            
                            // 验证单位
                            if (itemData.unitCode == null || itemData.unitCode.trim().isEmpty()) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单明细", itemData.rowNumber, "单位编码",
                                            "单位编码为空"));
                                }
                                continue;
                            }
                            
                            Unit unit = unitCache.get(itemData.unitCode.trim());
                            if (unit == null) {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单明细", itemData.rowNumber, "单位编码",
                                            "单位不存在: " + itemData.unitCode));
                                }
                                continue;
                            }
                            
                            // 解析数量
                            BigDecimal qty = null;
                            if (itemData.qty != null && !itemData.qty.trim().isEmpty()) {
                                try {
                                    // 处理逗号分隔的数字，如"1,000"
                                    String qtyStr = itemData.qty.trim().replace(",", "");
                                    qty = new BigDecimal(qtyStr);
                                } catch (Exception e) {
                                    if (errors.size() < MAX_ERROR_COUNT) {
                                        errors.add(new SaleOrderImportResponse.ImportError(
                                                "销售订单明细", itemData.rowNumber, "销售数量",
                                                "数量格式错误: " + itemData.qty));
                                    }
                                    continue;
                                }
                            } else {
                                if (errors.size() < MAX_ERROR_COUNT) {
                                    errors.add(new SaleOrderImportResponse.ImportError(
                                            "销售订单明细", itemData.rowNumber, "销售数量",
                                            "销售数量为空"));
                                }
                                continue;
                            }
                            
                            // 解析原数量
                            BigDecimal oldQty = null;
                            if (itemData.oldQty != null && !itemData.oldQty.trim().isEmpty()) {
                                try {
                                    String oldQtyStr = itemData.oldQty.trim().replace(",", "");
                                    oldQty = new BigDecimal(oldQtyStr);
                                } catch (Exception e) {
                                    // 原数量可以为空，忽略解析错误
                                }
                            }
                            
                            // 解析日期
                            LocalDate inspectionDate = null;
                            if (itemData.inspectionDate != null && !itemData.inspectionDate.trim().isEmpty()) {
                                try {
                                    inspectionDate = LocalDate.parse(itemData.inspectionDate.trim(), DATE_FORMATTER);
                                } catch (Exception e) {
                                    // 验货日期可以为空，忽略解析错误
                                }
                            }
                            
                            LocalDateTime deliveryDate = null;
                            if (itemData.deliveryDate != null && !itemData.deliveryDate.trim().isEmpty()) {
                                try {
                                    String deliveryDateStr = itemData.deliveryDate.trim();
                                    // 尝试两种格式
                                    if (deliveryDateStr.contains(" ")) {
                                        deliveryDate = LocalDateTime.parse(deliveryDateStr, DATETIME_FORMATTER);
                                    } else {
                                        deliveryDate = LocalDate.parse(deliveryDateStr, DATE_FORMATTER)
                                                .atStartOfDay();
                                    }
                                } catch (Exception e) {
                                    // 要货日期可以为空，忽略解析错误
                                }
                            }
                            
                            // 解析entryNote，提取客户订单号和行号
                            String entryNote = itemData.entryNote;
                            String customerOrderNo = null;
                            String customerLineNo = null;
                            String cleanedEntryNote = null;
                            
                            if (entryNote != null && !entryNote.trim().isEmpty()) {
                                // 检查是否包含格式：订单号/行号（如：4500670889/10）
                                String trimmedNote = entryNote.trim();
                                int slashIndex = trimmedNote.indexOf('/');
                                if (slashIndex > 0 && slashIndex < trimmedNote.length() - 1) {
                                    // 提取订单号和行号
                                    customerOrderNo = trimmedNote.substring(0, slashIndex).trim();
                                    customerLineNo = trimmedNote.substring(slashIndex + 1).trim();
                                    
                                    // 从entryNote中移除已提取的部分
                                    // 如果整个entryNote就是这个格式，则清空；否则保留剩余部分
                                    if (trimmedNote.equals(customerOrderNo + "/" + customerLineNo)) {
                                        cleanedEntryNote = null;
                                    } else {
                                        // 这种情况不太可能出现，但为了安全起见，移除匹配的部分
                                        cleanedEntryNote = trimmedNote.replace(customerOrderNo + "/" + customerLineNo, "").trim();
                                        if (cleanedEntryNote.isEmpty()) {
                                            cleanedEntryNote = null;
                                        }
                                    }
                                } else {
                                    // 不符合格式，保留原始内容
                                    cleanedEntryNote = trimmedNote;
                                }
                            }
                            
                            // 创建订单明细对象（暂不保存）
                            SaleOrderItem item = SaleOrderItem.builder()
                                    .saleOrder(saleOrder)
                                    .sequence(itemData.sequence != null ? itemData.sequence : 1)
                                    .material(material)
                                    .unit(unit)
                                    .qty(qty)
                                    .oldQty(oldQty)
                                    .inspectionDate(inspectionDate)
                                    .deliveryDate(deliveryDate)
                                    .bomVersion(itemData.bomVersion)
                                    .entryNote(cleanedEntryNote)
                                    .customerOrderNo(customerOrderNo)
                                    .customerLineNo(customerLineNo)
                                    .build();
                            itemsToSave.add(item);
                        } catch (Exception e) {
                            logger.error("处理订单明细失败，行号: {}", itemData.rowNumber, e);
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new SaleOrderImportResponse.ImportError(
                                        "销售订单明细", itemData.rowNumber, null,
                                        "处理失败: " + e.getMessage()));
                            }
                        }
                    }
                    
                    // 批量保存订单明细（一次性保存所有有效的明细）
                    if (!itemsToSave.isEmpty()) {
                        try {
                            saleOrderItemRepository.saveAll(itemsToSave);
                            successCount++;
                        } catch (Exception e) {
                            logger.error("批量保存订单明细失败，单据编号: {}，明细数量: {}", header.billNo, itemsToSave.size(), e);
                            // 记录批量保存失败的错误
                            if (errors.size() < MAX_ERROR_COUNT) {
                                errors.add(new SaleOrderImportResponse.ImportError(
                                        "销售订单明细", header.rowNumber, "单据编号",
                                        String.format("批量保存失败（%d条明细）: %s", itemsToSave.size(), e.getMessage())));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("保存订单失败，单据编号: {}", header.billNo, e);
                    if (errors.size() < MAX_ERROR_COUNT) {
                        errors.add(new SaleOrderImportResponse.ImportError(
                                "销售订单", header.rowNumber, "单据编号",
                                "保存失败: " + e.getMessage()));
                    }
                }
            }
            
            return successCount;
        }
    }
    
    // 内部数据类
    private record SaleOrderHeader(
            int rowNumber,
            String billNo,
            String orderDate,
            String note,
            String woNumber,
            String customerCode,
            String customerName
    ) {}
    
    private record SaleOrderItemData(
            int rowNumber,
            Integer sequence,
            String materialCode,
            String unitCode,
            String qty,
            String oldQty,
            String inspectionDate,
            String deliveryDate,
            String bomVersion,
            String entryNote
    ) {}
    
    private record SaleOrderData(
            SaleOrderHeader header,
            SaleOrderItemData item
    ) {}
    
    // 批次处理结果
    private record BatchResult(
            int successCount,
            List<SaleOrderImportResponse.ImportError> errors
    ) {}
}
