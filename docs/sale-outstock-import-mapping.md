# 销售出库单 Excel 导入字段映射说明

本文档记录 `example/销售出库.csv` 文件在系统中的字段映射及导入规则，供配置与排查时参考。Excel 文件前两行为表头，导入时需保留。

## 单据头字段（出库主表 `sale_outstocks`）

| Excel 列（第 1 行 / 第 2 行） | 示例值 | 系统字段 | 说明 |
| --- | --- | --- | --- |
| `FBillHead(SAL_OUTSTOCK)` / `*基本信息(序号)` | `130570` | — | 用于 Excel 内部结构区分，无需写入数据库 |
| `FBillNo` / `(基本信息)单据编号` | `XOUT030485` | `sale_outstocks.bill_no` | 出库单唯一编号（系统中需唯一） |
| `FDate` / `*(基本信息)日期` | `2025/11/10` | `sale_outstocks.outstock_date` | 出库日期，格式支持 `yyyy-MM-dd` 或 `yyyy/MM/dd` |
| `FNote` / `(基本信息)备注` | `日期码：51009VAT...` | `sale_outstocks.note` | 出库单备注，可为空 |

## 明细字段（出库明细表 `sale_outstock_items`）

| Excel 列（第 1 行 / 第 2 行） | 示例值 | 系统字段 | 说明 |
| --- | --- | --- | --- |
| `FEntity` / `*明细信息(序号)` | `151921` | `sale_outstock_items.sequence` | 明细行序号，导入时转为整数 |
| `FMaterialID` / `*(明细信息)物料编码#编码` | `96.SP37573X-GE001V` | `sale_outstock_items.material_id` | 通过物料编码匹配销售订单明细的物料，校验需一致 |
| `FMaterialID#Name` / `(明细信息)物料编码#名称` | `CLM ACK Stackd...` | — | 仅用于人工校对，不写入数据库 |
| `FUnitID` / `*(明细信息)库存单位#编码` | `Pcs` | `sale_outstock_items.unit_id` | 通过单位编码匹配销售订单明细单位，需一致 |
| `FUnitID#Name` / `(明细信息)库存单位#名称` | `Pcs` | — | 仅展示用途 |
| `FRealQty` / `(明细信息)实发数量` | `554` | `sale_outstock_items.qty` | 本次出库数量，导入时去除千分位后转为数值 |
| `FEntrynote` / `(明细信息)备注` | `4500666812/10` | `sale_outstock_items.entry_note` | 明细备注，可为空 |
| `F_kd_Text` / `(明细信息)本司WO#` | `V29662-V0-0` | `sale_outstock_items.wo_number` | 本司工单号，可为空 |
| `FSOEntryId` / `(明细信息)销售订单EntryId` | `"138,766"` | `sale_outstock_items.sale_order_sequence` | 关联的销售订单明细序号（对应 `sale_order_items.sequence`），导入前须去掉千分位与引号 |

## 数据转换与校验规则

- **数值清洗**：`FRealQty`、`FSOEntryId` 等字段导入前统一去除 `,`、双引号等格式符号。
- **关联验证**：`FSOEntryId` 必须能匹配系统已有销售订单明细序号，否则记录为错误。若存在重复序号，会提示人工处理。
- **物料与单位一致性**：Excel 中的物料编码、单位编码需与关联销售订单明细一致，否则导入失败。
- **数量校验**：销售订单明细的累计出库数量不得超过销售数量，超出时阻止导入并提示。

## 状态联动逻辑

- 导入成功后，系统会累加销售订单明细的 `delivered_qty`。
- 当某销售订单明细的累计出库数量等于销售数量时，该明细状态更新为 `CLOSED`。
- 当一张销售订单下所有明细均为 `CLOSED` 时，销售订单状态同步更新为 `CLOSED`。

