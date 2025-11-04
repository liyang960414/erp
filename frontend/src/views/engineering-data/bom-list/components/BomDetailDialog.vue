<template>
  <el-dialog v-model="dialogVisible" title="BOM详情" width="1200px" @close="handleClose">
    <div v-if="bom">
      <!-- BOM头信息 -->
      <el-descriptions :column="2" border style="margin-bottom: 20px">
        <el-descriptions-item label="父项物料编码">
          {{ bom.materialCode }}
        </el-descriptions-item>
        <el-descriptions-item label="父项物料名称">
          {{ bom.materialName }}
        </el-descriptions-item>
        <el-descriptions-item label="物料组编码">
          {{ bom.materialGroupCode }}
        </el-descriptions-item>
        <el-descriptions-item label="物料组名称">
          {{ bom.materialGroupName }}
        </el-descriptions-item>
        <el-descriptions-item label="BOM版本">
          {{ bom.version }}
        </el-descriptions-item>
        <el-descriptions-item label="BOM分类">
          {{ bom.category || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="BOM用途">
          {{ bom.usage || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="BOM简称">
          {{ bom.name || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          {{ bom.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatDateTime(bom.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatDateTime(bom.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- BOM明细项 -->
      <el-divider>BOM明细</el-divider>
      <el-table :data="bom.items || []" border style="width: 100%" max-height="400">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="childMaterialCode" label="子项物料编码" width="180" />
        <el-table-column
          prop="childMaterialName"
          label="子项物料名称"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="childUnitName" label="子项单位名称" width="120" />
        <el-table-column label="用量" width="120">
          <template #default="{ row }"> {{ row.numerator }} / {{ row.denominator }} </template>
        </el-table-column>
        <el-table-column prop="scrapRate" label="损耗率%" width="100">
          <template #default="{ row }">
            {{ row.scrapRate != null ? row.scrapRate : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="childBomVersion" label="子项BOM版本" width="120" />
        <el-table-column prop="memo" label="备注" min-width="150" show-overflow-tooltip />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BillOfMaterial } from '@/types/bom'

interface Props {
  modelValue: boolean
  bom: BillOfMaterial | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'update-success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const handleClose = () => {
  dialogVisible.value = false
}

const formatDateTime = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}
</script>

<style scoped></style>
