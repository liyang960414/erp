<template>
  <el-dialog
    v-model="dialogVisible"
    title="销售订单详情"
    width="1200px"
    :close-on-click-modal="false"
    class="order-detail-dialog"
    @close="handleClose"
  >
    <div v-if="order" class="order-detail">
      <el-descriptions :column="2" border class="order-info">
        <el-descriptions-item label="单据编号">
          <span class="detail-value bill-no">{{ order.billNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单日期">
          <span class="detail-value">{{ order.orderDate }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="客户编码">
          <span class="detail-value">{{ order.customerCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="客户名称">
          <span class="detail-value customer-name">{{ order.customerName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getOrderStatusType(order.status)" size="small">
            {{ getOrderStatusLabel(order.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="本司WO#">
          <span class="detail-value wo-number">{{ order.woNumber || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <span class="detail-value note-text">{{ order.note || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider class="detail-divider">
        <span class="divider-text">订单明细</span>
      </el-divider>

      <div v-if="order.items && order.items.length > 0" class="detail-table-wrapper">
        <el-table
          :data="order.items"
          border
          style="width: 100%"
          stripe
        >
        <el-table-column prop="sequence" label="序号" width="80" />
        <el-table-column prop="materialCode" label="物料编码" width="150" />
        <el-table-column prop="materialName" label="物料名称" min-width="200" />
        <el-table-column prop="unitCode" label="单位" width="100" />
        <el-table-column prop="qty" label="销售数量" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.qty) }}
          </template>
        </el-table-column>
        <el-table-column prop="deliveredQty" label="已出库数量" width="140" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.deliveredQty) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="明细状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getItemStatusType(row.status)" size="small">
              {{ getItemStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="oldQty" label="原数量" width="120" align="right">
          <template #default="{ row }">
            {{ row.oldQty ? formatNumber(row.oldQty) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="inspectionDate" label="验货日期" width="120">
          <template #default="{ row }">
            {{ row.inspectionDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDate" label="要货日期" width="160">
          <template #default="{ row }">
            {{ row.deliveryDate ? formatDateTime(row.deliveryDate) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="bomVersion" label="BOM版本" width="120">
          <template #default="{ row }">
            {{ row.bomVersion || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="customerOrderNo" label="客户订单号" width="150">
          <template #default="{ row }">
            {{ row.customerOrderNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="customerLineNo" label="客户行号" width="120">
          <template #default="{ row }">
            {{ row.customerLineNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="entryNote" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.entryNote || '-' }}
          </template>
        </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="该订单暂无明细" />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { SaleOrder, SaleOrderStatus, SaleOrderItemStatus } from '@/types/saleOrder'

interface Props {
  modelValue: boolean
  order: SaleOrder | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = ref(props.modelValue)
const order = ref<SaleOrder | null>(props.order)

watch(
  () => props.modelValue,
  (newValue) => {
    dialogVisible.value = newValue
  },
)

watch(
  () => props.order,
  (newValue) => {
    order.value = newValue
  },
)

watch(dialogVisible, (newValue) => {
  emit('update:modelValue', newValue)
})

const handleClose = () => {
  dialogVisible.value = false
}

const formatNumber = (value: number | string): string => {
  if (value == null) return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 6 })
}

const formatDateTime = (value: string): string => {
  if (!value) return '-'
  try {
    const date = new Date(value)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}

const getOrderStatusLabel = (status: SaleOrderStatus): string => {
  return status === 'CLOSED' ? '已关闭' : '进行中'
}

const getOrderStatusType = (status: SaleOrderStatus): 'success' | 'info' => {
  return status === 'CLOSED' ? 'success' : 'info'
}

const getItemStatusLabel = (status: SaleOrderItemStatus): string => {
  return status === 'CLOSED' ? '已关闭' : '进行中'
}

const getItemStatusType = (status: SaleOrderItemStatus): 'success' | 'warning' => {
  return status === 'CLOSED' ? 'success' : 'warning'
}
</script>

<style scoped>
.order-detail {
  padding: 0;
}

.order-detail-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
}

.order-detail-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.order-detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.order-info {
  margin-bottom: 24px;
}

.order-info :deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--el-text-color-regular);
  background-color: #fafafa;
  width: 120px;
}

.order-info :deep(.el-descriptions__content) {
  background-color: #ffffff;
}

.detail-value {
  display: inline-block;
  font-size: 14px;
}

.detail-value.bill-no {
  font-weight: 500;
  color: var(--el-color-primary);
  font-size: 15px;
}

.detail-value.customer-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.detail-value.wo-number {
  font-family: 'Courier New', monospace;
  color: var(--el-text-color-regular);
}

.detail-value.note-text {
  color: var(--el-text-color-secondary);
}

.detail-divider {
  margin: 24px 0;
}

.divider-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 0 16px;
  background-color: #ffffff;
}

.detail-table-wrapper {
  margin-top: 16px;
}

.detail-table-wrapper :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.detail-table-wrapper :deep(.el-table__header) {
  background-color: #fafafa;
}

.detail-table-wrapper :deep(.el-table__header th) {
  background-color: #fafafa;
  color: var(--el-text-color-primary);
  font-weight: 600;
  border-bottom: 2px solid var(--el-border-color-light);
}

.detail-table-wrapper :deep(.el-table__row) {
  transition: background-color 0.2s;
}

.detail-table-wrapper :deep(.el-table__row:hover) {
  background-color: #f5f7fa;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 0 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .order-detail-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 5vh auto;
  }

  .order-info :deep(.el-descriptions__label) {
    width: 100px;
  }
}
</style>
