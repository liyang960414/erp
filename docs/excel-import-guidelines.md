# Excel 导入模块规范建议

## 1. 现状评估

- `AbstractImportService`：统一了事务模板与 `ExecutorService`，提供了 `importFromInputStream` 扩展点，最佳实践是利用输入流避免大文件占用内存。待补充：子类缺乏统一的结果日志输出模板、缺乏注入式配置来源。
- `AbstractImportProcessor<T>`：负责逐行解析、批次并发控制与错误收集，具备统一超时控制。待补充：`processRow` 子类普遍内联模块常量（批大小、日志标签等），缺少模板方法规范；`logger` 输出字段不一致。
- `BatchProcessor`：封装批次切分、并发执行、收集成功/失败列表。待补充：批耗时、批索引日志没有统一格式，`timeoutMinutes`、`batchSize` 等参数目前在业务 Processor 中重新定义。
- `ImportErrorCollector` 与 `ImportValidator`：集中管理错误类型与校验逻辑，已提供“错误上限”与分组统计。待补充：错误类型未暴露到 `ImportError`（只有 message），导致前端/调用方无法根据类型定制展示；错误消息多为中文常量，未附带错误码。
- `ImportSummary`/`BasicImportSummary`：定义了总行数、成功数与错误列表。待补充：缺少导入耗时、文件信息、分模块统计等额外字段；摘要对象未约定序列化格式，跨模块返回体不一致。

## 2. 代码结构与日志规范

### 2.1 基础结构模板

1. **Service 层**  
   - 继承 `AbstractImportService<R>`，在构造函数中注入 `PlatformTransactionManager` 与配置对象 `ImportProperties`（见第 4 节）。  
   - 在 `importFromInputStream` 中集中创建 `ImportContext`（含文件信息、操作者、模块 code），并委派给对应 `ImportProcessor`。  
   - 调用完成后使用统一的 `logImportResult` 输出摘要信息。

2. **Processor 层**  
   - 继承 `AbstractImportProcessor<T>`，仅关注：行解析 (`processRow`)、批次组装、调用 `BatchProcessor`。  
   - 禁止在 Processor 内部硬编码批大小/超时/错误阈值，全部通过构造函数参数或 `ImportContext` 提供。  
   - 需要实现 `onDataCollectionComplete` 触发批处理、调用 `waitForBatches` 等统一逻辑。

3. **辅助组件**  
   - `EntityPreloader`：提前加载字典/关联实体，要求统一暴露 `preload(ImportContext ctx)`，返回缓存 Map。  
   - `ImportDataParser`、`RowMapper<T>`、`RowValidator<T>`：对外暴露接口，禁止内联具体字段名，统一从枚举或配置中获取列信息。

4. **异常处理**  
   - 业务校验异常全部通过 `ImportErrorCollector` 记录并继续处理，除非达到 `maxErrorCount`。  
   - 系统异常（IO、DB）立即写入 `logger.error` 并抛出，禁止吞掉异常。  
   - `BatchProcessor` 内部异常要包装为 `RuntimeException("批次X失败: " + e.getMessage())`，在 Service 层转换成领域自定义异常（如 `ImportException`）。

### 2.2 日志分级与格式

| 级别 | 场景 | 必备字段 |
| --- | --- | --- |
| `info` | 导入开始、结束、批次总体统计 | `traceId`（可选）、模块、文件名、文件大小、总行数、成功数、失败数、耗时 |
| `debug` | 批次细节、缓存命中率、预加载耗时 | 模块、批次序号、批大小、耗时、线程名 |
| `warn` | 可恢复问题（批次被中断、达到错误上限） | 模块、批次、错误数量、限制值 |
| `error` | 系统异常、批量超时 | 模块、批次、异常堆栈、输入参数摘要 |

统一格式推荐：

```
logger.info("[ExcelImport] module={}, file={}, sizeMB={}, rows={}",
        context.getModule(), context.getFileName(), context.getFileSizeMb(), processor.getTotalRows());
```

批次日志示例：

```
logger.debug("[ExcelImport] module={}, batch={}/{}, size={}, costMs={}",
        context.getModule(), batchIndex, totalBatches, batch.size(), batchDuration);
```

关键要求：

- 统一 `ExcelImport` 前缀，便于检索。
- 所有 `error` 日志必须带 `fileName` 与 `rowNumber`（如可用），避免后续排障困难。
- 在 `ImportErrorCollector` 达到上限时输出一次 `warn`，提醒用户下载错误文件重试。

## 3. 导入结果与错误输出规范

1. **结果对象**  
   - 基础字段：`totalRows`、`successCount`、`failureCount`（默认实现已提供）  
   - 扩展字段：`fileName`、`fileSizeMb`、`durationMs`、`module`、`batchSummary`（成功批次数/失败批次数）  
   - 建议新建 `ExtendedImportSummary` 接口或在 `ImportSummary` 中增加默认方法，例如 `default long durationMs()`，由实现类覆盖。

2. **错误结构**  
   - `ImportError` 增加 `errorType`、`errorCode` 字段，`errorType` 使用 `ImportError.ErrorType`，`errorCode` 采用统一前缀（如 `IMP_VAL_001`）。  
   - `ImportErrorCollector` 在 `addError` 时可选地接收 `errorCode`，未传则根据 `ErrorType` + field 自动生成。  
   - 前端可根据 `errorType` 显示不同颜色/标签，并支持下载 CSV/Excel 错误报告。

3. **返回体/接口契约**  
   - HTTP API 统一返回 `ImportSummary` JSON，结构示例：  
     ```json
     {
       "module": "supplier",
       "fileName": "供应商导入模板.xlsx",
       "totalRows": 1200,
       "successCount": 1180,
       "durationMs": 5320,
       "errors": [
         {"rowNumber": 23, "field": "supplierCode", "errorType": "VALIDATION_ERROR", "message": "..."}
       ]
     }
     ```  
   - 当错误数超过阈值时，`errors` 仅返回前 `n` 条，同时在响应中附带 `errorFileUrl` 以供下载完整错误明细。

4. **错误导出文件**  
   - 统一列：`module,rowNumber,field,errorType,errorCode,message,rawValue`。  
   - 建议使用 `EntityPreloader` 中的缓存，原样输出原始值 `rawValue`，帮助用户修复。

## 4. 配置集中化方案

1. **集中配置对象**  
   - 新建 `ImportProperties`（Spring `@ConfigurationProperties(prefix = "erp.import")`），字段包括：  
     - `maxErrorCount`、`batchInsertSize`、`maxConcurrentBatches`、`batchTimeoutMinutes`  
     - `transactionTimeoutSeconds`、`executorType`（虚拟线程/线程池）  
     - 模块级覆盖，如 `moduleDefaults.supplier.batchInsertSize`  
   - `ImportServiceConfig` 保留默认常量，但所有 Processor/Service 注入 `ImportProperties`，优先读取配置，缺省回退到常量。

2. **装配方式**  
   - 在 `AbstractImportService` 构造器中接收 `ImportProperties`，构造 `ExecutorService` 时依据配置（例如固定线程池或虚拟线程）。  
   - `AbstractImportProcessor` 增加 `ImportContext` 参数，内部从 `context.getConfig()` 获取批大小等信息。  
   - `BatchProcessor` 则通过工厂方法 `BatchProcessor.of(context)` 创建，避免每个模块重复 new。

3. **模块覆盖策略**  
   - `ImportContext` 持有 `ImportModuleConfig`（封装某模块特定配置），在 Service 初始化时根据模块枚举加载。  
   - 支持在 `application.yml` 中配置：  
     ```yaml
     erp:
       import:
         defaults:
           batch-insert-size: 1000
         modules:
           supplier:
             batch-insert-size: 500
             max-error-count: 500
     ```  
   - 所有 Processor 通过 `context.getModuleConfig().batchInsertSize()` 读取，无需再定义常量。

4. **迁移路径**  
   - 第一步：在各 Processor 中新增构造函数参数 `ImportModuleConfig moduleConfig`，并逐步替换旧常量。  
   - 第二步：完全移除硬编码后，`ImportServiceConfig` 仅作为工具类保留默认值与公共常量。

## 5. 跨模块落地建议

1. **优先级排序**  
   - 先从配置最复杂、导入量最大的模块（如 `supplier`, `purchase`, `material`）开始统一，随后覆盖 `sale`, `task`, `unit`。  
   - 同步改造 `ExcelImportService` 等入口，确保返回体一致。

2. **落地清单**  
   - **代码模板**：为每个模块建立 `ModuleImportService` + `ModuleImportProcessor` 双类模板，引用统一基类。  
   - **日志**：添加 `ExcelImport` MDC 或 `contextId`，并通过 `ImportContext` 注入。  
   - **结果**：所有 API 返回 `ExtendedImportSummary`，配合错误下载链接。  
   - **配置**：所有批量、超时、错误上限均来源于 `ImportProperties`，禁止再次硬编码。

3. **验证步骤**  
   - 为每个模块补充集成测试：导入正常文件、触发错误上限、批处理超时。  
   - 在测试环境观察日志格式、配置覆盖效果，确保与规范一致。

4. **文档与培训**  
   - 在 `docs/` 目录补充此规范并保持更新。  
   - 组织一次分享会，讲解如何在新模块遵循模板，如何添加模块级配置。  
   - 将错误码对照表同步给前端与运营团队。

