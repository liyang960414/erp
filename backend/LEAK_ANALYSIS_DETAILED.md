# 连接泄漏问题详细分析报告

## 日志关键时间线分析

### 完整时间线

| 时间 | 事件 | 连接池状态 | 说明 |
|------|------|-----------|------|
| 00:53:18 | 开始导入Excel文件 | - | 请求开始 |
| 00:53:20 | 物料组数据收集完成（89条） | - | 数据收集阶段 |
| 00:53:26 | **物料组导入完成**（89条成功） | - | 物料组导入阶段完成 |
| 00:53:34 | 连接池检查 | **active=9, total=12** | **关键：9个连接开始占用** |
| 00:53:46 | Excel读取完成（Ehcache移除） | - | 物料数据读取完成，开始异步批次处理 |
| 00:54:04 | 连接池检查 | active=9, total=19, idle=10 | 9个连接持续占用 |
| 00:54:34 | 连接池检查 | active=9, total=19, idle=10 | 9个连接持续占用 |
| 00:55:04 | 连接池检查 | active=9, total=19, idle=10 | 9个连接持续占用 |
| 00:55:20 | **连接泄漏检测触发** | - | **106秒后检测到泄漏** |
| 00:55:28 | **应用开始优雅关闭** | - | waitForCompletion()可能超时 |
| 00:55:58 | 应用关闭完成 | - | 虚拟线程中的连接被强制关闭 |

## 问题根因分析

### 1. 连接泄漏的真实位置

**堆栈跟踪误导**：
- 堆栈跟踪显示连接泄漏发生在 `importRootMaterialGroupsWithTransaction:253`
- 但物料组导入在 00:53:26 就已经完成
- **真实情况**：连接泄漏检测是在**获取新连接时**触发的，而不是在泄漏发生的地方
- **实际泄漏位置**：异步批次处理中的虚拟线程

### 2. 关键问题

#### 问题 A：虚拟线程中的事务执行时间过长

**证据**：
- 从 00:53:34 到 00:55:20，9个活跃连接持续占用 106 秒
- 这些连接是在异步批次处理的虚拟线程中使用的
- 即使设置了60秒的事务超时，虚拟线程中的事务可能没有正确响应超时

**可能原因**：
1. 虚拟线程中的事务超时可能不生效
2. 事务中的数据库操作执行时间过长
3. `preloadAllData` 中的只读事务也可能占用连接

#### 问题 B：应用关闭时的连接清理

**证据**：
- 00:55:28 应用开始优雅关闭
- 00:55:58 虚拟线程中的连接被标记为 broken（Socket closed）
- 说明在应用关闭时，虚拟线程中的任务还在执行，连接被强制关闭

### 3. 代码层面的问题

#### 问题 1：虚拟线程中的事务缺少中断检查

```java
// 当前代码
CompletableFuture.supplyAsync(() -> {
    batchSemaphore.acquire();
    try {
        return processBatch(batchData);  // 没有检查中断
    } finally {
        batchSemaphore.release();
    }
}, executorService);
```

**问题**：
- 虚拟线程在执行事务时，如果被中断，没有及时响应
- 事务可能继续执行，导致连接长时间占用

#### 问题 2：preloadAllData 中的连接管理

```java
// preloadAllData 中有一个只读事务
TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
readOnlyTemplate.setReadOnly(true);
readOnlyTemplate.setTimeout(30);
```

**问题**：
- 这个只读事务在虚拟线程中执行
- 如果批次很大，预加载可能也需要较长时间
- 预加载完成后，连接应该立即释放，但可能没有

#### 问题 3：waitForCompletion 的异常处理

**问题**：
- 当应用关闭时，`waitForCompletion()` 可能抛出 `InterruptedException`
- 但虚拟线程中的任务可能还在执行
- 需要确保虚拟线程能够正确处理中断并回滚事务

## 修复方案

### 修复 1：在虚拟线程中添加中断检查

```java
private BatchResult processBatch(List<MaterialExcelRow> batch) {
    // 检查线程是否被中断
    if (Thread.currentThread().isInterrupted()) {
        logger.warn("批次处理被中断，取消执行");
        return new BatchResult(0, new ArrayList<>());
    }
    
    // ... 预加载数据 ...
    
    // 在事务执行前再次检查
    if (Thread.currentThread().isInterrupted()) {
        return new BatchResult(0, new ArrayList<>());
    }
    
    // ... 执行事务 ...
}
```

### 修复 2：优化 preloadAllData 中的连接管理

```java
// 确保只读事务快速完成并释放连接
TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
readOnlyTemplate.setReadOnly(true);
readOnlyTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
readOnlyTemplate.setTimeout(10); // 减少到10秒，确保快速完成

try {
    readOnlyTemplate.execute(status -> {
        // ... 查询操作 ...
        // 确保事务快速完成
        return null;
    });
} catch (Exception e) {
    logger.warn("预加载失败，将跳过未预加载的数据: {}", e.getMessage());
    // 即使失败也要继续，避免阻塞
}
```

### 修复 3：增强 waitForCompletion 的异常处理

```java
public void waitForCompletion() {
    if (futures.isEmpty()) {
        return;
    }

    try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, TimeUnit.MINUTES);
        collectResults();
    } catch (TimeoutException e) {
        logger.error("导入超时，取消所有未完成的任务");
        futures.forEach(f -> {
            f.cancel(true);  // 取消任务
        });
        // 等待一段时间让虚拟线程响应中断
        try {
            Thread.sleep(1000);  // 等待1秒
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        throw new RuntimeException("导入超时", e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.error("导入被中断，取消所有未完成的任务");
        futures.forEach(f -> f.cancel(true));
        throw new RuntimeException("导入被中断", e);
    } catch (Exception e) {
        logger.error("批次处理失败", e);
        futures.forEach(f -> f.cancel(true));
        throw new RuntimeException("导入失败: " + e.getMessage(), e);
    }
}
```

### 修复 4：在事务中添加中断检查

```java
writeTemplate.execute(status -> {
    try {
        int processedInBatch = 0;
        for (MaterialExcelRow data : batch) {
            // 检查中断
            if (Thread.currentThread().isInterrupted()) {
                logger.warn("事务执行中被中断，回滚事务");
                status.setRollbackOnly();
                throw new RuntimeException("事务被中断");
            }
            
            try {
                importMaterialRow(data, materialGroupCache, unitCache);
                // ...
            } catch (Exception e) {
                // ...
            }
        }
        entityManager.flush();
        return null;
    } catch (Exception e) {
        status.setRollbackOnly();
        throw e;
    }
});
```

### 修复 5：减少批次大小，确保事务快速完成

如果问题持续，可以考虑：
- 将 `MATERIAL_BATCH_SIZE` 从 1000 减少到 500
- 或者进一步减少到 300

## 立即需要实施的修复

### 优先级 1（关键）

1. **在虚拟线程中添加中断检查**
2. **优化 preloadAllData 的超时时间**（从30秒减少到10秒）
3. **在事务循环中添加中断检查**

### 优先级 2（重要）

4. **增强 waitForCompletion 的异常处理**
5. **如果问题持续，减少批次大小**

## 验证步骤

1. 重新编译并启动应用
2. 执行相同的物料导入
3. 观察日志：
   - 确认连接占用时间是否减少
   - 确认是否还有连接泄漏警告
   - 确认应用关闭时虚拟线程是否正确终止

## 预期效果

- ✅ 连接占用时间减少到 < 60 秒
- ✅ 不再出现连接泄漏警告
- ✅ 应用关闭时虚拟线程能够正确终止
- ✅ 事务能够正确响应中断并回滚

