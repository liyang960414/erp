<template>
  <el-dialog
    v-model="dialogVisible"
    title="导入物料和物料组（Excel）"
    width="800px"
    @close="handleClose"
  >
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :on-change="handleFileChange"
      :file-list="fileList"
      :limit="1"
      accept=".xlsx,.xls"
      drag
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        将Excel文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持Excel文件（.xlsx或.xls），支持大文件（100M左右）
        </div>
      </template>
    </el-upload>
    
    <div v-if="importResult" class="import-result" style="margin-top: 20px">
      <!-- 物料组导入结果 -->
      <el-card v-if="importResult.unitGroupResult.totalRows > 0" style="margin-bottom: 20px">
        <template #header>
          <span>物料组导入结果</span>
        </template>
        <el-alert
          :type="importResult.unitGroupResult.failureCount === 0 ? 'success' : 'warning'"
          :title="`物料组导入完成：成功 ${importResult.unitGroupResult.successCount} 条，失败 ${importResult.unitGroupResult.failureCount} 条`"
          :closable="false"
          show-icon
        />
        <div v-if="importResult.unitGroupResult.errors.length > 0" style="margin-top: 10px">
          <el-collapse>
            <el-collapse-item title="错误详情" name="errors">
              <el-table :data="importResult.unitGroupResult.errors" border size="small" max-height="200">
                <el-table-column prop="sheetName" label="Sheet" width="120" />
                <el-table-column prop="rowNumber" label="行号" width="80" />
                <el-table-column prop="field" label="字段" width="150" />
                <el-table-column prop="message" label="错误信息" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-card>

      <!-- 物料导入结果 -->
      <el-card v-if="importResult.materialResult.totalRows > 0">
        <template #header>
          <span>物料导入结果</span>
        </template>
        <el-alert
          :type="importResult.materialResult.failureCount === 0 ? 'success' : 'warning'"
          :title="`物料导入完成：成功 ${importResult.materialResult.successCount} 条，失败 ${importResult.materialResult.failureCount} 条`"
          :closable="false"
          show-icon
        />
        <div v-if="importResult.materialResult.errors.length > 0" style="margin-top: 10px">
          <el-collapse>
            <el-collapse-item title="错误详情" name="errors">
              <el-table :data="importResult.materialResult.errors" border size="small" max-height="200">
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
      <el-button @click="handleClose">关闭</el-button>
      <el-button
        type="primary"
        @click="handleImport"
        :loading="importing"
        :disabled="!selectedFile"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { materialApi } from '@/api/material'
import type { MaterialImportResponse } from '@/types/material'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
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
const importing = ref(false)
const importResult = ref<MaterialImportResponse | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    const fileName = file.raw.name.toLowerCase()
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('只能上传Excel文件（.xlsx或.xls）')
      return
    }
    selectedFile.value = file.raw
    importResult.value = null
  }
}

const handleImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  
  importing.value = true
  try {
    const result = await materialApi.importMaterials(selectedFile.value)
    importResult.value = result
    
    const totalSuccess = (result.unitGroupResult?.successCount || 0) + (result.materialResult?.successCount || 0)
    const totalFailure = (result.unitGroupResult?.failureCount || 0) + (result.materialResult?.failureCount || 0)
    
    if (totalFailure === 0) {
      ElMessage.success('导入成功')
      emit('success')
      setTimeout(() => {
        handleClose()
      }, 2000)
    } else {
      ElMessage.warning(`导入完成，但有 ${totalFailure} 条记录失败`)
    }
  } catch (error: any) {
    ElMessage.error('导入失败: ' + (error.message || '未知错误'))
  } finally {
    importing.value = false
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
  margin-top: 20px;
}
</style>

