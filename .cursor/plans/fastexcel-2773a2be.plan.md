<!-- 2773a2be-b900-498a-9683-4d3e70589439 e66720c1-b506-421f-a2b5-84bdfeddd97a -->
# 迁移 Excel 导入服务到 FastExcel

## 目标

将 `UnitImportService` 和 `MaterialImportService` 从 Apache POI 迁移到 FastExcel (cn.idev.excel:fastexcel)，使用 `ReadListener` 接口实现流式读取以支持大文件导入，降低内存占用。

## 技术方案

### 1. 依赖管理

- **修正依赖**: 将 pom.xml 中的 `org.apache.fesod:fesod:1.3.0` 改为 `cn.idev.excel:fastexcel:1.3.0`
- **说明**: Apache Fesod 是 FastExcel 迁移到 Apache 后的新名称，但 1.3.0 版本仍以 `cn.idev.excel:fastexcel` 发布
- **注意**: FastExcel 底层仍使用 Apache POI，因此 POI 依赖会被传递引入，无需手动移除
- **限制**: 仅支持 `.xlsx` 格式

### 2. Excel 文件结构

根据提供的示例文件：

- **第1行**: 英文字段名（如 `FNumber`, `FName#2052`, `FUnitGroupId`）
- **第2行**: 中文说明行（如 `(单据头)编码`, `*(单据头)名称#中文(简体)`）
- **第3行开始**: 实际数据

**关键问题**: FastExcel 使用 `@ExcelProperty` 按列名或索引映射，两行表头会导致映射失败。

**解决方案**: 使用 `@ExcelProperty(value = {"第1行列名", "第2行列名"})` 同时指定两行表头，配合 `.headRowNumber(2)` 从第3行开始读取数据，无需在 Listener 中跳过。

### 3. 架构设计

#### FastExcel API 核心用法

```java
// 读取 Excel，使用列索引映射
EasyExcel.read(inputStream, DataClass.class, new DataListener())
    .sheet()
    .headRowNumber(1)  // 第1行作为表头
    .doRead();

// 实现 ReadListener
public class DataListener implements ReadListener<DataClass> {
    private int rowIndex = 0;
    
    @Override
    public void invoke(DataClass data, AnalysisContext context) {
        rowIndex++;
        if (rowIndex == 1) {
            return;  // 跳过第2行（中文说明行）
        }
        // 处理实际数据（从第3行开始）
    }
    
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 所有数据解析完成
    }
}
```

#### 新架构（流式处理）

**两遍读取策略**：

1. **第一遍**: 实现 `ReadListener` 收集所有唯一的单位组/物料组编码，预加载到数据库
2. **第二遍**: 实现 `ReadListener` 流式处理单位/物料数据，在 `invoke()` 中累积批次，达到批次大小后提交事务

**关键设计**：

- 使用 `@ExcelProperty(index = N)` 按列索引映射（N 从 0 开始）
- 在 `invoke()` 中跳过第一条数据（对应 Excel 第2行中文说明）
- 使用 `context.readRowHolder().getRowIndex()` 获取实际行号用于错误报告
- 移除 `ExcelRow` 缓存类，直接使用 FastExcel 的数据模型

### 4. 数据模型定义

#### 计量单位 (UnitRowData)

根据 `计量单位.csv`：

```java
public class UnitRowData {
    @ExcelProperty(index = 0)  // FBillHead(BD_UNIT) - 列A
    private String billHead;
    
    @ExcelProperty(index = 1)  // FNumber - 列B
    private String code;
    
    @ExcelProperty(index = 2)  // FName#2052 - 列C
    private String name;
    
    @ExcelProperty(index = 3)  // FUnitGroupId - 列D
    private String unitGroupCode;
    
    @ExcelProperty(index = 4)  // FUnitGroupId#Name - 列E
    private String unitGroupName;
    
    // 列F是 *Split*1，跳过
    
    // 列G是 SubHeadEntity，跳过
    
    @ExcelProperty(index = 7)  // FConvertType - 列H
    private String convertType;
    
    @ExcelProperty(index = 8)  // FConvertNumerator - 列I
    private String numerator;
    
    @ExcelProperty(index = 9)  // FConvertDenominator - 列J
    private String denominator;
}
```

#### 物料组 (MaterialGroupRowData)

根据 `物料组.csv`：

```java
public class MaterialGroupRowData {
    @ExcelProperty(index = 0)  // FBillHead(BOS_FORMGROUP) - 列A
    private String billHead;
    
    @ExcelProperty(index = 1)  // FParentId - 列B
    private String parentCode;
    
    @ExcelProperty(index = 2)  // FNumber - 列C
    private String code;
    
    @ExcelProperty(index = 3)  // FName#2052 - 列D
    private String name;
    
    @ExcelProperty(index = 4)  // FDescription#2052 - 列E
    private String description;
}
```

#### 物料 (MaterialRowData)

根据 `物料.csv`：

```java
public class MaterialRowData {
    @ExcelProperty(index = 0)  // FBillHead(BD_MATERIAL) - 列A
    private String billHead;
    
    @ExcelProperty(index = 1)  // FNumber - 列B
    private String code;
    
    @ExcelProperty(index = 2)  // FName#2052 - 列C
    private String name;
    
    @ExcelProperty(index = 3)  // FSpecification#2052 - 列D
    private String specification;
    
    @ExcelProperty(index = 4)  // FMnemonicCode - 列E
    private String mnemonicCode;
    
    @ExcelProperty(index = 5)  // FOldNumber - 列F
    private String oldNumber;
    
    @ExcelProperty(index = 6)  // FDescription#2052 - 列G
    private String description;
    
    @ExcelProperty(index = 7)  // FMaterialGroup - 列H
    private String materialGroupCode;
    
    @ExcelProperty(index = 8)  // FMaterialGroup#Name - 列I
    private String materialGroupName;
    
    // 其他列根据需要映射
    
    @ExcelProperty(index = 17)  // FBaseUnitId - 列R
    private String baseUnitCode;
    
    @ExcelProperty(index = 18)  // FBaseUnitId#Name - 列S
    private String baseUnitName;
}
```

### 5. ReadListener 实现策略

#### 跳过第2行（中文说明行）

```java
public class UnitDataListener implements ReadListener<UnitRowData> {
    private boolean isFirstRow = true;
    private List<UnitRowData> batch = new ArrayList<>();
    
    @Override
    public void invoke(UnitRowData data, AnalysisContext context) {
        // 第一条数据对应 Excel 第2行（中文说明），跳过
        if (isFirstRow) {
            isFirstRow = false;
            return;
        }
        
        // 实际数据处理（从 Excel 第3行开始）
        int excelRowNum = context.readRowHolder().getRowIndex() + 1;
        batch.add(data);
        
        if (batch.size() >= BATCH_SIZE) {
            processBatch(batch);
            batch.clear();
        }
    }
    
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!batch.isEmpty()) {
            processBatch(batch);
        }
    }
}
```

### 6. 核心修改点

#### UnitImportService.java

1. 移除 `createWorkbook()` 和 `ExcelRow` 类
2. 创建 `UnitRowData` 数据模型（使用列索引）
3. 实现两个 ReadListener：

   - `UnitGroupCollector`: 收集单位组信息
   - `UnitDataImporter`: 批量导入单位数据

4. 使用 `EasyExcel.read()` 替代 POI API

#### MaterialImportService.java

1. 创建 `MaterialGroupRowData` 和 `MaterialRowData` 数据模型
2. 实现 ReadListener：

   - `MaterialGroupCollector`: 收集物料组信息
   - `MaterialGroupImporter`: 导入物料组（处理树形结构）
   - `MaterialDataImporter`: 导入物料数据

3. 多 Sheet 处理：使用 `.sheet(index)` 或 `.sheet(name)`

### 7. 关键文件

- `backend/pom.xml` - 修正依赖为 `cn.idev.excel:fastexcel:1.3.0`
- `backend/src/main/java/com/sambound/erp/service/UnitImportService.java` - 重构为 ReadListener 模式
- `backend/src/main/java/com/sambound/erp/service/MaterialImportService.java` - 重构为 ReadListener 模式

### 8. 注意事项

- 不再支持 `.xls` 格式，仅支持 `.xlsx`
- **两行表头处理**: 使用 `.headRowNumber(1)` + 在 Listener 中跳过第一条数据
- **列索引映射**: 所有字段使用 `@ExcelProperty(index = N)`，从 0 开始
- **行号计算**: 错误报告时，实际行号 = `context.readRowHolder().getRowIndex() + 1`
- 事务管理：保持现有的 `TransactionTemplate` 策略
- 批次处理：在 `invoke()` 中累积，在 `doAfterAllAnalysed()` 中处理剩余数据

### To-dos

- [ ] 更新 pom.xml，移除 Apache POI 依赖
- [ ] 重构 UnitImportService 使用 FastExcel 流式读取
- [ ] 重构 MaterialImportService 使用 FastExcel 流式读取
- [ ] 验证项目编译通过，无 POI 相关引用