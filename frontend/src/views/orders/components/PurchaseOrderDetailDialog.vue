<template>
  <el-dialog
    v-model="dialogVisible"
    title="采购订单详情"
    width="1400px"
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
        <el-descriptions-item label="供应商编码">
          <span class="detail-value">{{ order.supplierCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="供应商名称">
          <span class="detail-value supplier-name">{{ order.supplierName }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag v-if="order.status === 'OPEN'" type="success" size="small">进行中</el-tag>
          <el-tag v-else-if="order.status === 'CLOSED'" type="info" size="small">已关闭</el-tag>
          <el-tag v-else type="warning" size="small">{{ order.status }}</el-tag>
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
          @expand-change="handleItemExpand"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-if="row.deliveries && row.deliveries.length > 0" class="delivery-details">
                <el-table
                  :data="row.deliveries"
                  border
                  size="small"
                  max-height="300"
                >
                  <el-table-column prop="sequence" label="序号" width="80" />
                  <el-table-column prop="deliveryDate" label="交货日期" width="120" />
                  <el-table-column prop="planQty" label="计划数量" width="120" align="right">
                    <template #default="{ row }">
                      {{ formatNumber(row.planQty) }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="supplierDeliveryDate" label="供应商发货日期" width="140">
                    <template #default="{ row }">
                      {{ row.supplierDeliveryDate || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="preArrivalDate" label="预计到货日期" width="140">
                    <template #default="{ row }">
                      {{ row.preArrivalDate || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="transportLeadTime" label="运输提前期(天)" width="140" align="right">
                    <template #default="{ row }">
                      {{ row.transportLeadTime ? row.transportLeadTime + ' 天' : '-' }}
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <el-empty v-else description="该明细暂无交货计划" />
            </template>
          </el-table-column>
          <el-table-column prop="sequence" label="序号" width="80" />
          <el-table-column prop="materialCode" label="物料编码" width="150" />
          <el-table-column prop="materialName" label="物料名称" min-width="200" />
          <el-table-column prop="unitCode" label="单位" width="100" />
          <el-table-column prop="qty" label="采购数量" width="120" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.qty) }}
            </template>
          </el-table-column>
          <el-table-column prop="deliveredQty" label="已交货数量" width="120" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.deliveredQty) }}
            </template>
          </el-table-column>
          <el-table-column prop="bomVersion" label="BOM版本" width="120">
            <template #default="{ row }">
              {{ row.bomVersion || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="planConfirm" label="计划确认" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.planConfirm" type="success" size="small">是</el-tag>
              <el-tag v-else type="info" size="small">否</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="salQty" label="销售数量" width="120" align="right">
            <template #default="{ row }">
              {{ row.salQty ? formatNumber(row.salQty) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.remarks || '-' }}
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
import type { PurchaseOrder } from '@/types/purchaseOrder'

interface Props {
  modelValue: boolean
  order: PurchaseOrder | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = ref(props.modelValue)
const order = ref<PurchaseOrder | null>(props.order)

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

const handleItemExpand = (row: any, expandedRows: any[]) => {
  // 展开/折叠明细的交货计划
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

.detail-value.supplier-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
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

.delivery-details {
  padding: 12px;
  background-color: #f9fafb;
  border-radius: 4px;
  margin: 8px 0;
}

.delivery-details :deep(.el-table) {
  background-color: #ffffff;
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

