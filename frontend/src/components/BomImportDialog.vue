<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入BOM（Excel/CSV）"
    width="900px"
    @close="handleClose"
  >
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :on-change="handleFileChange"
      :file-list="fileList"
      :limit="1"
      accept=".xlsx,.xls,.csv"
      drag
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        将Excel或CSV文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持Excel文件（.xlsx或.xls）和CSV文件（.csv），支持大文件（100M左右）
        </div>
      </template>
    </el-upload>
    
    <div v-if="importResult" class="import-result" style="margin-top: 20px">
      <!-- BOM导入结果 -->
      <el-card v-if="importResult.bomResult.totalRows > 0" style="margin-bottom: 20px">
        <template #header>
          <span>BOM导入结果</span>
        </template>
        <el-alert
          :type="importResult.bomResult.failureCount === 0 ? 'success' : 'warning'"
          :title="`BOM导入完成：成功 ${importResult.bomResult.successCount} 条，失败 ${importResult.bomResult.failureCount} 条`"
          :closable="false"
          show-icon
        />
        <div v-if="importResult.bomResult.errors.length > 0" style="margin-top: 10px">
          <el-collapse>
            <el-collapse-item title="错误详情" name="errors">
              <el-table :data="importResult.bomResult.errors" border size="small" max-height="200">
                <el-table-column prop="sheetName" label="Sheet" width="120" />
                <el-table-column prop="rowNumber" label="行号" width="80" />
                <el-table-column prop="field" label="字段" width="150" />
                <el-table-column prop="message" label="错误信息" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-card>

      <!-- BOM明细导入结果 -->
      <el-card v-if="importResult.itemResult.totalRows > 0">
        <template #header>
          <span>BOM明细导入结果</span>
        </template>
        <el-alert
          :type="importResult.itemResult.failureCount === 0 ? 'success' : 'warning'"
          :title="`BOM明细导入完成：成功 ${importResult.itemResult.successCount} 条，失败 ${importResult.itemResult.failureCount} 条`"
          :closable="false"
          show-icon
        />
        <div v-if="importResult.itemResult.errors.length > 0" style="margin-top: 10px">
          <el-collapse>
            <el-collapse-item title="错误详情" name="errors">
              <el-table :data="importResult.itemResult.errors" border size="small" max-height="200">
                <el-table-column prop="sheetName" label="Sheet" width="120" />
                <el-table-column prop="rowNumber" label="行号" width="80" />
                <el-table-column prop="field" label="字段" width="150" />
                <el-table-column prop="message" label="错误信息" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-card>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          导入
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { bomApi } from '@/api/bom'
import type { BomImportResponse } from '@/types/bom'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'import-success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadFile[]>([])
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const importResult = ref<BomImportResponse | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    const fileName = file.raw.name.toLowerCase()
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls') && !fileName.endsWith('.csv')) {
      ElMessage.error('只能上传Excel文件（.xlsx或.xls）或CSV文件（.csv）')
      return
    }
    selectedFile.value = file.raw
    importResult.value = null
  }
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  try {
    const result = await bomApi.importBoms(selectedFile.value)
    importResult.value = result
    
    const totalSuccess = result.bomResult.successCount + result.itemResult.successCount
    const totalFailure = result.bomResult.failureCount + result.itemResult.failureCount
    
    if (totalFailure === 0) {
      ElMessage.success('导入成功！')
      emit('import-success')
    } else {
      ElMessage.warning(`导入完成，但有 ${totalFailure} 条失败记录`)
    }
  } catch (error: any) {
    ElMessage.error('导入失败：' + (error.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

const handleClose = () => {
  dialogVisible.value = false
  fileList.value = []
  selectedFile.value = null
  importResult.value = null
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.import-result {
  max-height: 500px;
  overflow-y: auto;
}
</style>

