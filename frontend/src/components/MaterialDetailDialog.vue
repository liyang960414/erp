<template>
  <el-dialog
    v-model="dialogVisible"
    title="物料详情"
    width="700px"
    @close="handleClose"
  >
    <el-descriptions :column="2" border v-if="material">
      <el-descriptions-item label="物料编码">
        {{ material.code }}
      </el-descriptions-item>
      <el-descriptions-item label="物料名称">
        {{ material.name }}
      </el-descriptions-item>
      <el-descriptions-item label="规格" :span="2">
        {{ material.specification || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="助记码">
        {{ material.mnemonicCode || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="旧编号">
        {{ material.oldNumber || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="物料组编码">
        {{ material.materialGroupCode }}
      </el-descriptions-item>
      <el-descriptions-item label="物料组名称">
        {{ material.materialGroupName }}
      </el-descriptions-item>
      <el-descriptions-item label="基础单位编码">
        {{ material.baseUnitCode }}
      </el-descriptions-item>
      <el-descriptions-item label="基础单位名称">
        {{ material.baseUnitName }}
      </el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">
        {{ material.description || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">
        {{ formatDateTime(material.createdAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="更新时间">
        {{ formatDateTime(material.updatedAt) }}
      </el-descriptions-item>
    </el-descriptions>
    
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Material } from '@/types/material'

interface Props {
  modelValue: boolean
  material: Material | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
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

<style scoped>
</style>

