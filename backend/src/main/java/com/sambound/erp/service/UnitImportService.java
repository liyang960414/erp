package com.sambound.erp.service;

import com.sambound.erp.dto.UnitImportResponse;
import com.sambound.erp.entity.Unit;
import com.sambound.erp.entity.UnitGroup;
import com.sambound.erp.repository.UnitRepository;
import com.sambound.erp.repository.UnitGroupRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UnitImportService {

    private static final Logger logger = LoggerFactory.getLogger(UnitImportService.class);

    // CSV字段名映射
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

    public UnitImportResponse importFromCsv(MultipartFile file) {
        // 检测并获取正确的字符编码
        Charset charset = detectCharset(file);
        logger.info("检测到CSV文件编码: {}", charset.name());

        // 解析CSV文件
        List<CSVRecord> dataRecords;
        Map<String, Integer> fieldIndexMap;
        
        try (Reader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {

            // 使用CSVParser解析CSV
            CSVParser parser = CSVFormat.DEFAULT.parse(reader);
            List<CSVRecord> allRecords = parser.getRecords();

            if (allRecords.isEmpty()) {
                throw new IllegalArgumentException("CSV文件为空");
            }

            // 第一行：字段名（表头）
            CSVRecord headerRecord = allRecords.get(0);
            fieldIndexMap = buildFieldIndexMap(headerRecord);

            // 验证必需字段是否存在
            validateRequiredFields(fieldIndexMap);

            // 第二行：跳过（中文说明行）
            // 从第三行（索引2）开始提取数据
            dataRecords = new ArrayList<>();
            for (int i = 2; i < allRecords.size(); i++) {
                dataRecords.add(allRecords.get(i));
            }

        } catch (Exception e) {
            logger.error("CSV文件解析失败", e);
            throw new RuntimeException("CSV文件解析失败: " + e.getMessage(), e);
        }

        if (dataRecords.isEmpty()) {
            throw new IllegalArgumentException("CSV文件没有数据行");
        }

        int totalRows = dataRecords.size();
        logger.info("开始导入 {} 条数据", totalRows);

        // 预加载所有唯一的单位组，避免并发创建时的死锁
        Map<String, UnitGroup> unitGroupCache = preloadUnitGroups(dataRecords, fieldIndexMap);
        logger.info("预加载了 {} 个单位组", unitGroupCache.size());

        // 根据数据量决定使用单线程还是多线程
        if (totalRows < MULTI_THREAD_THRESHOLD) {
            logger.info("数据量较少，使用单线程导入");
            return importSingleThread(dataRecords, fieldIndexMap, unitGroupCache);
        } else {
            logger.info("数据量较大，使用多线程并行导入");
            return importMultiThread(dataRecords, fieldIndexMap, unitGroupCache);
        }
    }

    /**
     * 预加载所有唯一的单位组，避免并发创建时的死锁
     * 在主线程的独立事务中创建所有单位组并提交，然后重新从数据库查询确保持久化
     */
    private Map<String, UnitGroup> preloadUnitGroups(List<CSVRecord> records, Map<String, Integer> fieldIndexMap) {
        // 收集所有唯一的单位组编码和名称
        Map<String, String> unitGroupMap = new HashMap<>();
        
        for (CSVRecord record : records) {
            String unitGroupCode = getFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_ID);
            String unitGroupName = getFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_NAME);
            
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
        // 使用 PROPAGATION_REQUIRES_NEW 确保独立事务，避免嵌套事务问题
        // 直接使用 Repository 方法，避免通过 Service 产生嵌套事务
        transactionTemplate.execute(status -> {
            for (Map.Entry<String, String> entry : unitGroupMap.entrySet()) {
                try {
                    // 直接使用 Repository 的原生 SQL 方法，避免嵌套事务
                    unitGroupRepository.insertOrGetByCode(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    logger.warn("预加载单位组失败: {} - {}", entry.getKey(), e.getMessage());
                    // 如果预加载失败，记录警告但继续处理其他单位组
                    // 后续导入时如果单位组不存在会抛出异常
                }
            }
            return null;
        });
        
        // 事务提交后，使用新的只读事务重新从数据库查询所有单位组到缓存
        // 确保所有单位组都有ID且已经持久化，并且对后续操作可见
        Map<String, UnitGroup> cache = new java.util.concurrent.ConcurrentHashMap<>();
        
        // 创建一个只读的 TransactionTemplate 用于查询
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
     * 单线程导入（适用于小数据量）
     */
    private UnitImportResponse importSingleThread(List<CSVRecord> records, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        List<UnitImportResponse.ImportError> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            int rowNumber = i + 3; // 实际行号（考虑表头和说明行）

            try {
                importUnitRow(record, fieldIndexMap, unitGroupCache);
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
     * 多线程并行导入（适用于大数据量）
     */
    private UnitImportResponse importMultiThread(List<CSVRecord> records, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        int totalRows = records.size();
        List<UnitImportResponse.ImportError> allErrors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        
        // 将数据分批
        List<List<CSVRecord>> batches = partitionList(records, BATCH_SIZE);
        logger.info("数据分为 {} 个批次，每批 {} 条记录", batches.size(), BATCH_SIZE);

        // 创建Future列表来跟踪所有任务
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>();

        // 提交所有批次任务
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<CSVRecord> batch = batches.get(batchIndex);
            int startRowNumber = batchIndex * BATCH_SIZE + 3; // 实际行号起始位置

            CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
                return processBatch(batch, fieldIndexMap, startRowNumber, unitGroupCache);
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
     * 处理单个批次的数据
     * 每个批次在独立事务中处理，确保数据一致性
     */
    private BatchResult processBatch(List<CSVRecord> batch, Map<String, Integer> fieldIndexMap, int startRowNumber, Map<String, UnitGroup> unitGroupCache) {
        List<UnitImportResponse.ImportError> batchErrors = new ArrayList<>();
        AtomicInteger batchSuccessCount = new AtomicInteger(0);

        // 每个批次在独立事务中处理
        transactionTemplate.execute(status -> {
            for (int i = 0; i < batch.size(); i++) {
                CSVRecord record = batch.get(i);
                int rowNumber = startRowNumber + i;

                try {
                    importUnitRow(record, fieldIndexMap, unitGroupCache);
                    batchSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    logger.warn("导入第{}行数据失败: {}", rowNumber, e.getMessage());
                    batchErrors.add(new UnitImportResponse.ImportError(
                            rowNumber,
                            null,
                            e.getMessage()
                    ));
                    // 继续处理下一行，不中断整个批次
                    // 注意：在事务中，单条记录失败不影响其他记录的处理
                }
            }
            return null;
        });

        int successCount = batchSuccessCount.get();
        logger.debug("批次处理完成：成功 {} 条，失败 {} 条", successCount, batchErrors.size());

        return new BatchResult(successCount, batchErrors);
    }

    /**
     * 将列表分割成指定大小的批次
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    /**
     * 批次处理结果
     */
    private record BatchResult(
            int successCount,
            List<UnitImportResponse.ImportError> errors
    ) {}

    private Map<String, Integer> buildFieldIndexMap(CSVRecord headerRecord) {
        Map<String, Integer> fieldIndexMap = new HashMap<>();
        for (int i = 0; i < headerRecord.size(); i++) {
            String fieldName = headerRecord.get(i).trim();
            fieldIndexMap.put(fieldName, i);
        }
        return fieldIndexMap;
    }

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
            throw new IllegalArgumentException("CSV文件缺少必需字段: " + String.join(", ", missingFields));
        }
    }

    private void importUnitRow(CSVRecord record, Map<String, Integer> fieldIndexMap, Map<String, UnitGroup> unitGroupCache) {
        // 获取字段值
        String unitCode = getFieldValue(record, fieldIndexMap, FIELD_NUMBER);
        String unitName = getFieldValue(record, fieldIndexMap, FIELD_NAME);
        String unitGroupCode = getFieldValue(record, fieldIndexMap, FIELD_UNIT_GROUP_ID);
        String numeratorStr = getFieldValue(record, fieldIndexMap, FIELD_CONVERT_NUMERATOR);
        String denominatorStr = getFieldValue(record, fieldIndexMap, FIELD_CONVERT_DENOMINATOR);

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

        // 从缓存中获取单位组，单位组应该已经在预加载阶段创建并提交到数据库
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

    private String getFieldValue(CSVRecord record, Map<String, Integer> fieldIndexMap, String fieldName) {
        Integer index = fieldIndexMap.get(fieldName);
        if (index == null || index >= record.size()) {
            return null;
        }
        String value = record.get(index);
        return value != null ? value.trim() : null;
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
     * 检测CSV文件的字符编码
     * 支持UTF-8（带/不带BOM）、GBK、GB2312等常见中文编码
     * 
     * @param file CSV文件
     * @return 检测到的字符编码
     */
    private Charset detectCharset(MultipartFile file) {
        try {
            // 读取文件字节内容（一次性读取，避免多次读取导致的问题）
            byte[] fileBytes = file.getBytes();
            
            if (fileBytes.length == 0) {
                return StandardCharsets.UTF_8;
            }
            
            // 检测UTF-8 BOM (EF BB BF)
            if (fileBytes.length >= 3 && 
                fileBytes[0] == (byte) 0xEF && 
                fileBytes[1] == (byte) 0xBB && 
                fileBytes[2] == (byte) 0xBF) {
                logger.debug("检测到UTF-8 BOM");
                return StandardCharsets.UTF_8;
            }
            
            // 按优先级尝试不同编码
            Charset[] charsets = {
                StandardCharsets.UTF_8,
                Charset.forName("GBK"),
                Charset.forName("GB2312"),
                Charset.forName("ISO-8859-1")
            };
            
            for (Charset charset : charsets) {
                try {
                    String testContent = new String(fileBytes, charset);
                    
                    // 验证是否为有效的编码
                    if (isValidEncoding(testContent, charset)) {
                        logger.debug("检测到文件编码: {}", charset.name());
                        return charset;
                    }
                } catch (Exception e) {
                    logger.debug("尝试编码 {} 失败: {}", charset.name(), e.getMessage());
                }
            }
            
            // 默认返回UTF-8
            logger.warn("无法确定文件编码，默认使用UTF-8");
            return StandardCharsets.UTF_8;
            
        } catch (Exception e) {
            logger.warn("编码检测失败，默认使用UTF-8: {}", e.getMessage());
            return StandardCharsets.UTF_8;
        }
    }
    
    /**
     * 验证字符串是否为有效的编码内容
     */
    private boolean isValidEncoding(String content, Charset charset) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // 检查是否包含UTF-8替换字符（表示解码错误）
        if (content.contains("\uFFFD")) {
            return false;
        }
        
        // 检查是否包含大量连续的问号（通常是编码错误的表现）
        if (content.matches(".*\\?{3,}.*")) {
            return false;
        }
        
        // 对于GBK/GB2312，检查是否包含明显的乱码模式
        if (charset.name().toUpperCase().startsWith("GB")) {
            // GBK编码的中文字符应该能正常显示
            // 如果包含很多单字节字符后跟问号，可能是编码错误
            if (content.matches(".*[\\x00-\\x7F]\\?.*")) {
                return false;
            }
        }
        
        // 如果内容包含中文字符，更可能是正确的编码
        if (content.matches(".*[\\u4e00-\\u9fa5].*")) {
            return true;
        }
        
        // 其他情况也认为有效（可能是纯英文内容）
        return true;
    }

}

