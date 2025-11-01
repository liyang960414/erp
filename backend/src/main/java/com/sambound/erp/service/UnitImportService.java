package com.sambound.erp.service;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.repository.UnitGroupRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UnitImportService {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportService.class);

    // Excel字段名映射
    private static final String FIELD_NUMBER = "FNumber";
    private static final String FIELD_NAME = "FName#2052"; // 中文名称字段
    private static final String FIELD_UNIT_GROUP_ID = "FUnitGroupId";
    private static final String FIELD_UNIT_GROUP_NAME = "FUnitGroupId#Name";
    private static final String FIELD_CONVERT_NUMERATOR = "FConvertNumerator";
    private static final String FIELD_CONVERT_DENOMINATOR = "FConvertDenominator";

    // 批次大小：每批处理100条记录
    private static final int BATCH_SIZE = 100;
    // 最小数据量阈值：小于此数量使用单线程，大于此数量使用多线程
    private static final int MULTI_THREAD_THRESHOLD = 200;
    
    private final UnitService unitService;
    private final UnitRepository unitRepository;
    private final UnitGroupRepository unitGroupRepository;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;
    private final ExecutorService executorService;

    public UnitImportService(
            UnitService unitService,
            UnitRepository unitRepository,
            UnitGroupRepository unitGroupRepository,
            PlatformTransactionManager transactionManager) {
        this.unitService = unitService;
        this.unitRepository = unitRepository;
        this.unitGroupRepository = unitGroupRepository;
        this.transactionManager = transactionManager;
        // 创建TransactionTemplate用于程序式事务管理
        // 使用 PROPAGATION_REQUIRES_NEW 确保独立事务，避免嵌套事务问题
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // 使用虚拟线程执行器（Java 25特性）
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    public UnitImportResponse importFromExcel(MultipartFile file) {
        // 解析Excel文件
        List<ExcelRow> dataRecords;
        Map<String, Integer> fieldIndexMap;
        
        try (Workbook workbook = createWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0); // 读取第一个工作表
            
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Excel文件为空");
            }
            
            // 第一行：字段名（表头）
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel文件表头不能为空");
            }
            fieldIndexMap = buildExcelFieldIndexMap(headerRow);
            
            // 验证必需字段是否存在
            validateRequiredFields(fieldIndexMap);
            
            // 第二行：跳过（中文说明行）
            // 从第三行（索引2）开始提取数据
            dataRecords = new ArrayList<>();
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && !isRowEmpty(row)) {
                    dataRecords.add(new ExcelRow(row));
                }
            }
            
        } catch (Exception e) {
            logger.error("Excel文件解析失败", e);
            throw new RuntimeException("Excel文件解析失败: " + e.getMessage(), e);
        }
        
        if (dataRecords.isEmpty()) {
            throw new IllegalArgumentException("Excel文件没有数据行");
        }
        
        int totalRows = dataRecords.size();
        logger.info("开始导入 {} 条数据", totalRows);
        
        // 预加载所有唯一的单位组，避免并发创建时的死锁
        Map<String, UnitGroup> unitGroupCache = preloadUnitGroupsFromExcel(dataRecords, fieldIndexMap);
        logger.info("预加载了 {} 个单位组", unitGroupCache.size());
        
        // 根据数据量决定使用单线程还是多线程
        if (totalRows < MULTI_THREAD_THRESHOLD) {
            logger.info("数据量较少，使用单线程导入");
            return importSingleThreadFromExcel(dataRecords, fieldIndexMap, unitGroupCache);
        } else {
            logger.info("数据量较大，使用多线程并行导入");
            return importMultiThreadFromExcel(dataRecords, fieldIndexMap, unitGroupCache);
        }
    }
    
    /**
     * Excel行数据包装类
     */
    private static class ExcelRow {
        private final Row row;
        private final Map<Integer, String> cellCache = new HashMap<>();
        
        public ExcelRow(Row row) {
            this.row = row;
        }
        
        public String getCellValue(int index) {
            // 使用缓存避免重复解析
            if (cellCache.containsKey(index)) {
                return cellCache.get(index);
            }
            
            Cell cell = row.getCell(index);
            if (cell == null) {
                cellCache.put(index, null);
                return null;
            }
            
            String value = getCellValueAsString(cell);
            cellCache.put(index, value);
            return value;
        }
        
        private String getCellValueAsString(Cell cell) {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        // 处理数值，避免科学计数法
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == Math.floor(numericValue)) {
                            // 整数
                            return String.valueOf((long) numericValue);
                        } else {
                            // 浮点数
                            return String.valueOf(numericValue);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        // 尝试计算公式结果
                        DataFormatter formatter = new DataFormatter();
                        return formatter.formatCellValue(cell).trim();
                    } catch (Exception e) {
                        return cell.getCellFormula();
                    }
                default:
                    return "";
            }
        }
    }
    
    /**
     * 创建Workbook对象
     */
    private Workbook createWorkbook(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        if (filename.endsWith(".xlsx")) {
            return new XSSFWorkbook(file.getInputStream());
        } else if (filename.endsWith(".xls")) {
            return new HSSFWorkbook(file.getInputStream());
        } else {
            throw new IllegalArgumentException("不支持的Excel文件格式");
        }
    }
    
    /**
     * 构建Excel字段索引映射
     */
    private Map<String, Integer> buildExcelFieldIndexMap(Row headerRow) {
        Map<String, Integer> fieldIndexMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String fieldName = cell.getStringCellValue().trim();
                if (!fieldName.isEmpty()) {
                    fieldIndexMap.put(fieldName, i);
                }
            }
        }
        return fieldIndexMap;
    }
    
    /**
     * 判断行是否为空
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 验证必需字段是否存在
     */
    private void validateRequiredFields(Map<String, Integer> fieldIndexMap) {
        List<String> requiredFields = Arrays.asList(
                FIELD_NUMBER,
                FIELD_NAME,
                FIELD_UNIT_GROUP_ID
        );

        List<String> missingFields = new ArrayList<>();
        for (String field : requiredFields) {
            if (!fieldIndexMap.containsKey(field)) {
                missingFields.add(field);
            }
        }

        if (!missingFields.isEmpty()) {
            throw new IllegalArgumentException("Excel文件缺少必需字段: " + String.join(", ", missingFields));
        }
    }
    
    /**
     * 解析数值字符串，移除千位分隔符和其他非数字字符（保留小数点和负号）
     * 
     * @param value 原始数值字符串
     * @return BigDecimal对象
     * @throws NumberFormatException 如果无法解析为有效数字
     */
    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new NumberFormatException("数值字符串为空");
        }
        
        // 移除空格、千位分隔符（逗号）和其他非数字字符（保留小数点、负号、E/e用于科学计数法）
        String cleaned = value.trim()
                .replaceAll("\\s+", "")  // 移除所有空格
                .replaceAll("[,，]", ""); // 移除千位分隔符（中文和英文逗号）
        
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            logger.warn("无法解析数值: {}", value);
            throw new NumberFormatException("无法解析数值: " + value + "，清理后为: " + cleaned);
        }
    }
    
    /**
     * 批次处理结果
     */
    private record BatchResult(
            int successCount,
            List<UnitImportResponse.ImportError> errors
    ) {}
    
    /**
     * 预加载Excel中的所有单位组
     */
    private Map<String, UnitGroup> preloadUnitGroupsFromExcel(List<ExcelRow> records, Map<String, Integer> fieldIndexMap) {
        // 收集所有唯一的单位组编码和名称
        Map<String, String> unitGroupMap = new HashMap<>();
        
        for (ExcelRow record : records) {
            String unitGroupCode = getExcelFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_ID);
            String unitGroupName = getExcelFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_NAME);
            
            if (unitGroupCode != null && !unitGroupCode.trim().isEmpty()) {
                String code = unitGroupCode.trim();
                String name = unitGroupName != null && !unitGroupName.trim().isEmpty() 
                        ? unitGroupName.trim() 
                        : code;
                // 如果同一个编码有多个名称，保留第一个遇到的名称
                unitGroupMap.putIfAbsent(code, name);
            }
        }
        
        // 在独立事务中创建所有单位组并提交
        transactionTemplate.execute(status -> {
            for (Map.Entry<String, String> entry : unitGroupMap.entrySet()) {
                try {
                    unitGroupRepository.insertOrGetByCode(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.warn("预加载单位组失败: {} - {}", entry.getKey(), e.getMessage());
                }
            }
            return null;
        });
        
        // 事务提交后，重新从数据库查询所有单位组到缓存
        Map<String, UnitGroup> cache = new java.util.concurrent.ConcurrentHashMap<>();
        
        TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
        readOnlyTemplate.setReadOnly(true);
        readOnlyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        
        readOnlyTemplate.execute(readStatus -> {
            for (String code : unitGroupMap.keySet()) {
                unitGroupRepository.findByCode(code).ifPresent(unitGroup -> {
                    if (unitGroup.getId() != null) {
                        cache.put(code, unitGroup);
                    } else {
                        logger.warn("单位组 {} 查询结果没有ID，跳过", code);
                    }
                });
            }
            return null;
        });
        
        return cache;
    }
    
    /**
     * 单线程导入Excel数据
     */
    private UnitImportResponse importSingleThreadFromExcel(List<ExcelRow> records, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        List<UnitImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < records.size(); i++) {
            ExcelRow record = records.get(i);
            int rowNumber = i + 3; // 实际行号（考虑表头和说明行）

            try {
                importUnitRowFromExcel(record, fieldIndexMap, unitGroupCache);
                successCount.incrementAndGet();
            } catch (Exception e) {
                logger.warn("导入第{}行数据失败: {}", rowNumber, e.getMessage());
                errors.add(new UnitImportResponse.ImportError(
                        rowNumber,
                        null,
                        e.getMessage()
                ));
            }
        }

        int totalRows = records.size();
        int failureCount = totalRows - successCount.get();
        return new UnitImportResponse(totalRows, successCount.get(), failureCount, errors);
    }
    
    /**
     * 多线程并行导入Excel数据
     */
    private UnitImportResponse importMultiThreadFromExcel(List<ExcelRow> records, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        int totalRows = records.size();
        List<UnitImportResponse.ImportError> allErrors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        
        // 将数据分批
        List<List<ExcelRow>> batches = partitionExcelList(records, BATCH_SIZE);
        logger.info("数据分为 {} 个批次，每批 {} 条记录", batches.size(), BATCH_SIZE);

        // 创建Future列表来跟踪所有任务
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();

        // 提交所有批次任务
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<ExcelRow> batch = batches.get(batchIndex);
            int startRowNumber = batchIndex * BATCH_SIZE + 3; // 实际行号起始位置

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                return processExcelBatch(batch, fieldIndexMap, startRowNumber, unitGroupCache);
            }, executorService);

            futures.add(future);
        }

        // 等待所有任务完成并收集结果
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            // 设置超时时间为10分钟
            allFutures.get(10, TimeUnit.MINUTES);

            // 收集所有批次的结果
            for (CompletableFuture<BatchResult> future : futures) {
                try {
                    BatchResult result = future.get();
                    totalSuccessCount.addAndGet(result.successCount());
                    allErrors.addAll(result.errors());
                } catch (Exception e) {
                    logger.error("获取批次处理结果失败", e);
                }
            }

        } catch (TimeoutException e) {
            logger.error("导入超时", e);
            throw new RuntimeException("导入超时，请检查数据量或重试", e);
        } catch (Exception e) {
            logger.error("多线程导入失败", e);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }

        int failureCount = totalRows - totalSuccessCount.get();
        logger.info("导入完成：总计 {} 条，成功 {} 条，失败 {} 条", 
                totalRows, totalSuccessCount.get(), failureCount);
        
        return new UnitImportResponse(totalRows, totalSuccessCount.get(), failureCount, allErrors);
    }
    
    /**
     * 处理单个批次的Excel数据
     */
    private BatchResult processExcelBatch(List<ExcelRow> batch, Map<String, Integer> fieldIndexMap, int startRowNumber, Map<String, UnitGroup> unitGroupCache) {
        List<UnitImportResponse.ImportError> batchErrors = new ArrayList<>();
        AtomicInteger batchSuccessCount = new AtomicInteger(0);

        // 每个批次在独立事务中处理
        transactionTemplate.execute(status -> {
            for (int i = 0; i < batch.size(); i++) {
                ExcelRow record = batch.get(i);
                int rowNumber = startRowNumber + i;

                try {
                    importUnitRowFromExcel(record, fieldIndexMap, unitGroupCache);
                    batchSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("导入第{}行数据失败: {}", rowNumber, e.getMessage());
                    batchErrors.add(new UnitImportResponse.ImportError(
                            rowNumber,
                            null,
                            e.getMessage()
                    ));
                }
            }
            return null;
        });

        int successCount = batchSuccessCount.get();
        logger.debug("批次处理完成：成功 {} 条，失败 {} 条", successCount, batchErrors.size());

        return new BatchResult(successCount, batchErrors);
    }
    
    /**
     * 将Excel行列表分割成指定大小的批次
     */
    private <T> List<List<T>> partitionExcelList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }
    
    /**
     * 从Excel行中获取字段值
     */
    private String getExcelFieldValue(ExcelRow record, Map<String, Integer> fieldIndexMap, String fieldName) {
        Integer index = fieldIndexMap.get(fieldName);
        if (index == null) {
            return null;
        }
        String value = record.getCellValue(index);
        return value != null && !value.isEmpty() ? value.trim() : null;
    }
    
    /**
     * 导入Excel行数据
     */
    private void importUnitRowFromExcel(ExcelRow record, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        // 获取字段值
        String unitCode = getExcelFieldValue(record, fieldIndexMap, FIELD_NUMBER);
        String unitName = getExcelFieldValue(record, fieldIndexMap, FIELD_NAME);
        String unitGroupCode = getExcelFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_ID);
        String numeratorStr = getExcelFieldValue(record, fieldIndexMap, FIELD_CONVERT_NUMERATOR);
        String denominatorStr = getExcelFieldValue(record, fieldIndexMap, FIELD_CONVERT_DENOMINATOR);

        // 验证必填字段
        if (unitCode == null || unitCode.trim().isEmpty()) {
            throw new IllegalArgumentException("单位编码不能为空");
        }
        if (unitName == null || unitName.trim().isEmpty()) {
            throw new IllegalArgumentException("单位名称不能为空");
        }
        if (unitGroupCode == null || unitGroupCode.trim().isEmpty()) {
            throw new IllegalArgumentException("单位组编码不能为空");
        }

        // 从缓存中获取单位组
        String code = unitGroupCode.trim();
        UnitGroup unitGroup = unitGroupCache.get(code);
        
        if (unitGroup == null) {
            throw new IllegalStateException("单位组未预加载，请确保单位组编码正确: " + code);
        }

        // 确保 UnitGroup 已经持久化并分配了 ID
        if (unitGroup.getId() == null) {
            throw new IllegalStateException("单位组未正确保存: " + unitGroupCode);
        }

        // 创建或获取单位
        Unit unit = unitService.findOrCreateByCode(
                unitCode.trim(),
                unitName.trim(),
                unitGroup
        );

        // 确保 Unit 已经持久化并分配了 ID
        if (unit.getId() == null) {
            throw new IllegalStateException("单位未正确保存: " + unitCode);
        }

        // 如果有转换率信息，直接设置到单位实体
        if (numeratorStr != null && !numeratorStr.trim().isEmpty()) {
            try {
                BigDecimal numerator = parseDecimal(numeratorStr);
                BigDecimal denominator = denominatorStr != null && !denominatorStr.trim().isEmpty()
                        ? parseDecimal(denominatorStr)
                        : BigDecimal.ONE;

                if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("换算分母必须大于0");
                }

                // 直接将转换率设置到单位实体（相对于单位组的转换率）
                unit.setNumerator(numerator);
                unit.setDenominator(denominator);
                unitRepository.save(unit);
            } catch (NumberFormatException e) {
                logger.warn("单位 {} 的转换率格式错误: {}", unitCode, e.getMessage());
                // 转换率格式错误不影响单位导入，只记录警告
            }
        }
    }


}

