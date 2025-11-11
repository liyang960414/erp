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
      <div class="el-upload__text">将Excel或CSV文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">
          支持Excel文件（.xlsx或.xls）和CSV文件（.csv），支持大文件（100M左右）
        </div>
      </template>
    </el-upload>

    <div v-if="taskInfo" class="import-result" style="margin-top: 20px">
      <el-alert
        type="success"
        show-icon
        :closable="false"
        :title="`导入任务已提交，任务编号：${taskInfo.taskCode}`"
        description="系统正在后台处理，请稍后刷新列表查看数据。"
      />
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
import type { ImportTaskCreateResponse } from '@/types/importTask'

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
const taskInfo = ref<ImportTaskCreateResponse | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    const fileName = file.raw.name.toLowerCase()
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls') && !fileName.endsWith('.csv')) {
      ElMessage.error('只能上传Excel文件（.xlsx或.xls）或CSV文件（.csv）')
      return
    }
    selectedFile.value = file.raw
    taskInfo.value = null
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
    taskInfo.value = result

    ElMessage.success('导入任务已提交，系统正在后台处理')
    emit('import-success')
    setTimeout(() => {
      handleClose()
    }, 2000)
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
  taskInfo.value = null
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.import-result {
  max-height: 500px;
  overflow-y: auto;
}
</style>
