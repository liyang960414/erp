<template>
  <el-dialog
    v-model="dialogVisible"
    title="销售出库单详情"
    width="1100px"
    :close-on-click-modal="false"
    class="outstock-detail-dialog"
    @close="handleClose"
  >
    <div v-if="outstock" class="outstock-detail">
      <el-descriptions :column="2" border class="outstock-info">
        <el-descriptions-item label="单据编号">
          <span class="detail-value bill-no">{{ outstock.billNo }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="出库日期">
          <span class="detail-value">{{ outstock.outstockDate }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="明细数量">
          <span class="detail-value">{{ outstock.itemCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="总出库数量">
          <span class="detail-value">{{ formatNumber(outstock.totalQty) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <span class="detail-value note-text">{{ outstock.note || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider class="detail-divider">
        <span class="divider-text">出库明细</span>
      </el-divider>

      <div v-if="outstock.items && outstock.items.length > 0" class="detail-table-wrapper">
        <el-table :data="outstock.items" border style="width: 100%" size="small">
          <el-table-column prop="sequence" label="序号" width="80" />
        <el-table-column prop="saleOrderBillNo" label="销售单号" width="150" show-overflow-tooltip />
        <el-table-column prop="saleOrderSequence" label="销售明细序号" width="140" />
          <el-table-column prop="materialCode" label="物料编码" width="160" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="unitCode" label="单位" width="100" />
          <el-table-column prop="qty" label="出库数量" width="140" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.qty) }}
            </template>
          </el-table-column>
          <el-table-column prop="woNumber" label="本司WO#" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.woNumber || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="entryNote" label="备注" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.entryNote || '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else description="该出库单暂无明细" />
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
import type { SaleOutstock } from '@/types/saleOutstock'

interface Props {
  modelValue: boolean
  outstock: SaleOutstock | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = ref(props.modelValue)
const outstock = ref<SaleOutstock | null>(props.outstock)

watch(
  () => props.modelValue,
  (newValue) => {
    dialogVisible.value = newValue
  },
)

watch(
  () => props.outstock,
  (newValue) => {
    outstock.value = newValue
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
  if (Number.isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 6 })
}
</script>

<style scoped>
.outstock-detail {
  padding: 0;
}

.outstock-detail-dialog :deep(.el-dialog__header) {
  padding: 18px 22px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
}

.outstock-detail-dialog :deep(.el-dialog__title) {
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.outstock-detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.outstock-info {
  margin-bottom: 20px;
}

.outstock-info :deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--el-text-color-regular);
  background-color: #fafafa;
  width: 140px;
}

.outstock-info :deep(.el-descriptions__content) {
  background-color: #ffffff;
}

.detail-value {
  display: inline-block;
  font-size: 14px;
}

.detail-value.bill-no {
  font-weight: 600;
  color: var(--el-color-primary);
}

.detail-value.note-text {
  color: var(--el-text-color-secondary);
}

.detail-divider {
  margin: 22px 0 18px;
}

.divider-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding: 0 14px;
  background-color: #ffffff;
}

.detail-table-wrapper {
  margin-top: 10px;
}

.detail-table-wrapper :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

@media (max-width: 768px) {
  .outstock-detail-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 5vh auto;
  }

  .outstock-info :deep(.el-descriptions__label) {
    width: 120px;
  }
}
</style>

