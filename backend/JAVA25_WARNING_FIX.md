# Java 25 sun.misc.Unsafe 废弃警告修复指南

## 问题描述

在使用 Java 25 运行应用时，会出现以下警告信息：

```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by org.ehcache.impl.internal.concurrent.ThreadLocalRandomUtil
WARNING: Please consider reporting this to the maintainers of class org.ehcache.impl.internal.concurrent.ThreadLocalRandomUtil
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

## 问题原因

1. **Java 25 废弃警告**：Java 25 根据 JEP 471，开始废弃 `sun.misc.Unsafe` 中的内存访问方法，并在运行时显示警告。

2. **依赖链**：
   - `fastexcel` (1.3.0) → `ehcache` (3.11.1)
   - `ehcache 3.11.1` 使用了已废弃的 `sun.misc.Unsafe::objectFieldOffset` 方法

3. **影响**：
   - 当前：仅显示警告，不影响功能
   - 未来：在后续 Java 版本中，这些方法可能会被移除，导致应用无法运行

## 解决方案

### 方案 1：JVM 参数抑制警告（已实施）✅

已在 `pom.xml` 的 Spring Boot Maven 插件中配置了 JVM 参数：

```xml
<jvmArguments>
    --sun-misc-unsafe-memory-access=allow
</jvmArguments>
```

此配置会在使用 `mvn spring-boot:run` 时自动应用，抑制警告信息。

### 方案 2：手动添加 JVM 参数

如果使用其他方式启动应用（如 IDE 或直接运行 JAR），需要手动添加 JVM 参数：

**IDE（IntelliJ IDEA/Eclipse）配置：**
```
--sun-misc-unsafe-memory-access=allow
```

**命令行运行 JAR：**
```bash
java --sun-misc-unsafe-memory-access=allow -jar target/erp-*.jar
```

**系统环境变量（Windows）：**
```batch
set JAVA_TOOL_OPTIONS=--sun-misc-unsafe-memory-access=allow
```

**系统环境变量（Linux/Mac）：**
```bash
export JAVA_TOOL_OPTIONS="--sun-misc-unsafe-memory-access=allow"
```

## 长期解决方案

### 1. 等待依赖更新
- 关注 `fastexcel` 和 `ehcache` 的更新版本
- `ehcache` 维护者可能需要更新代码以使用新的 API（如 `jdk.internal.misc.Unsafe` 或其他替代方案）

### 2. 报告问题
如果警告持续存在，可以考虑：
- 向 `fastexcel` 维护者报告：请求升级 `ehcache` 依赖
- 向 `ehcache` 维护者报告：[Ehcache GitHub Issues](https://github.com/ehcache/ehcache3/issues)

### 3. 替代方案
如果问题严重影响生产环境，可以考虑：
- 更换 Excel 处理库（如果 `fastexcel` 长期未更新）
- 降级到 Java 23 或更早版本（临时方案）

## 验证修复

启动应用后，警告信息应该不再显示。如果仍然出现警告，请检查：

1. JVM 参数是否正确应用
2. 是否使用了配置了 JVM 参数的启动方式
3. 环境变量是否被其他配置覆盖

## 参考链接

- [JEP 471: Deprecate the Memory-Access Methods in sun.misc.Unsafe for Removal](https://openjdk.org/jeps/471)
- [Quality Outreach Heads-up - Deprecate the Memory-Access Methods](https://inside.java/2024/01/29/quality-heads-up/)
- [Ehcache 官方文档](https://www.ehcache.org/)

## 更新日志

- 2025-11-02: 初始版本，添加 JVM 参数配置以抑制警告





