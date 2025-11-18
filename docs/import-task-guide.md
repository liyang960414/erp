# 导入任务调度使用说明

## 依赖配置
在 `backend/src/main/resources/application.yaml` 中维护导入依赖关系与并发限制：
```yaml
erp:
  import:
    dependencies:
      material:
        - unit
      bom:
        - material
    type-concurrency:
      unit: 1
      material: 1
      bom: 1
```
依赖遵循先完成前置类型再执行后续任务的顺序。新导入类型可在此配置中扩展。

## 任务管理接口
- `GET /api/import-tasks`：分页查询任务，支持按 `importType`、`status`、`createdBy` 过滤。
- `GET /api/import-tasks/{taskId}`：查看任务详情及所有执行子项。
- `GET /api/import-tasks/{taskId}/failures`：分页获取失败记录，可按 `status` 过滤。
- `POST /api/import-tasks/{taskId}/retry`：上传修订后的 Excel 并指定失败记录 ID 重新执行。

接口均返回 `ApiResponse` 包装的数据结构。失败记录的 `status` 字段用于标识是否已修复或再次提交。

## 失败重试流程
1. 查询失败记录，定位需要修复的行。
2. 在原始模板中修订数据或生成仅包含失败行的新 Excel。
3. 通过重试接口上传修订后的文件，并在 `failureIds` 参数中提交需要重新执行的失败记录 ID。
4. 调度器会创建新的任务子项执行导入；原失败记录状态会在成功后更新为 `RESOLVED`。





