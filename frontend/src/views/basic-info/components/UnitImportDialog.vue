<template>
  <el-dialog v-model="dialogVisible" title="导入单位" width="600px" @close="handleClose">
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
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">支持Excel文件（.xlsx或.xls），且不超过10MB</div>
      </template>
    </el-upload>

    <div v-if="importResult" class="import-result" style="margin-top: 20px">
      <el-alert
        :type="importResult.failureCount === 0 ? 'success' : 'warning'"
        :title="`导入完成：成功 ${importResult.successCount} 条，失败 ${importResult.failureCount} 条`"
        :closable="false"
        show-icon
      />
      <div v-if="importResult.errors.length > 0" style="margin-top: 10px">
        <el-collapse>
          <el-collapse-item title="错误详情" name="errors">
            <el-table :data="importResult.errors" border size="small" max-height="200">
              <el-table-column prop="rowNumber" label="行号" width="80" />
              <el-table-column prop="field" label="字段" width="150" />
              <el-table-column prop="message" label="错误信息" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </div>
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
import { unitApi } from '@/api/unit.ts'
import type { UnitImportResponse } from '@/types/unit.ts'

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
const importResult = ref<UnitImportResponse | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    const fileName = file.raw.name.toLowerCase()
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('只能上传Excel文件（.xlsx或.xls）')
      return
    }
    if (file.raw.size > 10 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过10MB')
      return
    }
    selectedFile.value = file.raw
    importResult.value = null
  }
}

const handleImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  importing.value = true
  try {
    const result = await unitApi.importUnits(selectedFile.value)
    importResult.value = result

    if (result.failureCount === 0) {
      ElMessage.success('导入成功')
      emit('success')
      setTimeout(() => {
        handleClose()
      }, 2000)
    } else {
      ElMessage.warning(`导入完成，但有 ${result.failureCount} 条记录失败`)
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
