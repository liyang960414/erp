<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入供应商"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
      :limit="1"
      accept=".xlsx,.xls,.csv"
      drag
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 Excel 或 CSV 格式文件 (.xlsx, .xls, .csv)</div>
      </template>
    </el-upload>

    <div v-if="uploading" class="upload-progress">
      <el-progress :percentage="progress" :status="progressStatus" />
      <p v-if="progressMessage">{{ progressMessage }}</p>
    </div>

    <div v-if="importResult" class="import-result">
      <el-divider>导入结果</el-divider>
      <div class="result-summary">
        <div class="statistic-group">
          <el-statistic title="总计" :value="importResult.supplierResult.totalRows" />
          <el-statistic title="成功" :value="importResult.supplierResult.successCount">
            <template #suffix>
              <el-tag type="success" size="small" style="margin-left: 8px">成功</el-tag>
            </template>
          </el-statistic>
          <el-statistic title="失败" :value="importResult.supplierResult.failureCount">
            <template #suffix>
              <el-tag type="danger" size="small" style="margin-left: 8px">失败</el-tag>
            </template>
          </el-statistic>
        </div>
      </div>

      <div v-if="importResult.supplierResult.errors.length > 0" class="error-list">
        <el-alert
          title="错误详情"
          type="error"
          :closable="false"
          show-icon
        >
          <el-scrollbar max-height="300px">
            <el-table :data="importResult.supplierResult.errors" size="small" border>
              <el-table-column prop="rowNumber" label="行号" width="80" />
              <el-table-column prop="field" label="字段" width="120" />
              <el-table-column prop="message" label="错误信息" show-overflow-tooltip />
            </el-table>
          </el-scrollbar>
        </el-alert>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile || uploading"
          @click="handleImport"
        >
          开始导入
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, type UploadInstance, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { supplierApi } from '@/api/supplier'
import type { SupplierImportResponse } from '@/types/supplier'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = ref(props.modelValue)
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const progress = ref(0)
const progressStatus = ref<'success' | 'exception' | 'warning' | ''>('')
const progressMessage = ref('')
const importResult = ref<SupplierImportResponse | null>(null)

watch(
  () => props.modelValue,
  (newValue) => {
    dialogVisible.value = newValue
  },
)

watch(dialogVisible, (newValue) => {
  emit('update:modelValue', newValue)
})

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
  importResult.value = null
  progress.value = 0
  progressStatus.value = ''
  progressMessage.value = ''
}

const handleFileRemove = () => {
  selectedFile.value = null
  importResult.value = null
  progress.value = 0
  progressStatus.value = ''
  progressMessage.value = ''
}

const handleImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  progress.value = 0
  progressStatus.value = ''
  progressMessage.value = '正在上传文件...'

  try {
    progress.value = 30
    progressMessage.value = '正在解析文件...'

    const result = await supplierApi.importSuppliers(selectedFile.value)

    progress.value = 100
    progressStatus.value = 'success'
    progressMessage.value = '导入完成'

    importResult.value = result.data

    if (result.data.supplierResult.failureCount === 0) {
      ElMessage.success(
        `导入成功！共导入 ${result.data.supplierResult.successCount} 条供应商`,
      )
      emit('success')
      setTimeout(() => {
        handleClose()
      }, 2000)
    } else {
      ElMessage.warning(
        `导入完成！成功 ${result.data.supplierResult.successCount} 条，失败 ${result.data.supplierResult.failureCount} 条`,
      )
    }
  } catch (error: any) {
    progress.value = 0
    progressStatus.value = 'exception'
    progressMessage.value = '导入失败'

    const errorMessage =
      error.response?.data?.message || error.message || '未知错误'
    ElMessage.error('导入失败: ' + errorMessage)
  } finally {
    uploading.value = false
  }
}

const handleClose = () => {
  dialogVisible.value = false
  selectedFile.value = null
  importResult.value = null
  progress.value = 0
  progressStatus.value = ''
  progressMessage.value = ''
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}
</script>

<style scoped>
.upload-progress {
  margin-top: 20px;
}

.upload-progress p {
  margin-top: 10px;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.import-result {
  margin-top: 20px;
}

.result-summary {
  margin: 20px 0;
}

.statistic-group {
  display: flex;
  flex-direction: row;
  gap: 32px;
  justify-content: space-around;
  flex-wrap: wrap;
}

.statistic-group :deep(.el-statistic) {
  flex: 1;
  min-width: 150px;
  text-align: center;
}

.statistic-group :deep(.el-statistic__head) {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}

.statistic-group :deep(.el-statistic__number) {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.error-list {
  margin-top: 20px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .statistic-group {
    flex-direction: column;
    gap: 20px;
  }

  .statistic-group :deep(.el-statistic) {
    min-width: 100%;
  }
}
</style>

