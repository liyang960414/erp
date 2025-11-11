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

    <div v-if="taskInfo" class="import-result" style="margin-top: 20px">
      <el-alert
        type="success"
        :closable="false"
        show-icon
        :title="`导入任务已提交，任务编号：${taskInfo.taskCode}`"
        description="系统正在后台处理，请稍后刷新列表查看数据。"
      />
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
import type { ImportTaskCreateResponse } from '@/types/importTask.ts'

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
const taskInfo = ref<ImportTaskCreateResponse | null>(null)

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
    taskInfo.value = null
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
    taskInfo.value = result

    ElMessage.success('导入任务已提交，系统正在后台处理')
    emit('success')
    setTimeout(() => {
      handleClose()
    }, 2000)
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
  taskInfo.value = null
  uploadRef.value?.clearFiles()
}
</script>

<style scoped>
.import-result {
  margin-top: 20px;
}
</style>
