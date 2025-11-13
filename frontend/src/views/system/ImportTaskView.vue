<template>
  <div class="import-task-page">
    <el-card class="task-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('importTask.title') }}</span>
        </div>
      </template>

      <div class="search-bar">
        <el-form :inline="true" :model="searchForm">
          <el-form-item :label="$t('importTask.filters.importType')">
            <el-input
              v-model="searchForm.importType"
              :placeholder="$t('importTask.filters.importTypePlaceholder')"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item :label="$t('importTask.filters.status')">
            <el-select
              v-model="searchForm.status"
              :placeholder="$t('importTask.filters.statusPlaceholder')"
              clearable
              style="width: 180px"
            >
              <el-option
                v-for="option in statusOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('importTask.filters.createdBy')">
            <el-input
              v-model="searchForm.createdBy"
              :placeholder="$t('importTask.filters.createdByPlaceholder')"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              {{ $t('common.search') }}
            </el-button>
            <el-button @click="handleReset">
              <el-icon><Refresh /></el-icon>
              {{ $t('common.reset') }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-container">
        <el-table
          v-loading="loading"
          :data="tasks"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="taskId" :label="$t('importTask.table.id')" width="90" />
          <el-table-column prop="taskCode" :label="$t('importTask.table.taskCode')" width="160" />
          <el-table-column prop="importType" :label="$t('importTask.table.importType')" min-width="140" />
          <el-table-column
            prop="fileName"
            :label="$t('importTask.table.fileName')"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="status"
            :label="$t('importTask.table.status')"
            width="130"
          >
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="createdBy"
            :label="$t('importTask.table.createdBy')"
            width="140"
          />
          <el-table-column
            prop="createdAt"
            :label="$t('importTask.table.createdAt')"
            width="180"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="startedAt"
            :label="$t('importTask.table.startedAt')"
            width="180"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.startedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="completedAt"
            :label="$t('importTask.table.completedAt')"
            width="180"
          >
            <template #default="{ row }">
              {{ formatDateTime(row.completedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="totalCount"
            :label="$t('importTask.table.totalCount')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.totalCount ?? '--' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="successCount"
            :label="$t('importTask.table.successCount')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.successCount ?? '--' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="failureCount"
            :label="$t('importTask.table.failureCount')"
            width="110"
          >
            <template #default="{ row }">
              {{ row.failureCount ?? '--' }}
            </template>
          </el-table-column>
          <el-table-column
            fixed="right"
            :label="$t('common.actions')"
            width="220"
          >
            <template #default="{ row }">
              <el-button
                text
                type="primary"
                size="small"
                @click="openDetailDrawer(row.taskId)"
              >
                {{ $t('importTask.actions.viewDetail') }}
              </el-button>
              <el-divider direction="vertical" />
              <el-button
                text
                type="primary"
                size="small"
                @click="openFailuresDrawer(row.taskId)"
              >
                {{ $t('importTask.actions.viewFailures') }}
              </el-button>
              <el-divider direction="vertical" />
              <el-button
                text
                type="danger"
                size="small"
                @click="openRetryDialog(row.taskId)"
              >
                {{ $t('importTask.actions.retry') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadTasks"
          @current-change="loadTasks"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="detailDrawerVisible"
      :title="$t('importTask.detail.title')"
      size="60%"
    >
      <div v-loading="detailLoading" class="detail-container">
        <template v-if="taskDetail">
          <el-descriptions
            :column="2"
            border
            size="small"
            class="detail-descriptions"
          >
            <el-descriptions-item :label="$t('importTask.detail.taskId')">
              {{ taskDetail.task.taskId }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.taskCode')">
              {{ taskDetail.task.taskCode }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.importType')">
              {{ taskDetail.task.importType }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.fileName')">
              {{ taskDetail.task.fileName }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.status')">
              <el-tag :type="statusTagType(taskDetail.task.status)">
                {{ getStatusText(taskDetail.task.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.createdBy')">
              {{ taskDetail.task.createdBy }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.createdAt')">
              {{ formatDateTime(taskDetail.task.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.startedAt')">
              {{ formatDateTime(taskDetail.task.startedAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.completedAt')">
              {{ formatDateTime(taskDetail.task.completedAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.totalCount')">
              {{ taskDetail.task.totalCount ?? '--' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.successCount')">
              {{ taskDetail.task.successCount ?? '--' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('importTask.detail.failureCount')">
              {{ taskDetail.task.failureCount ?? '--' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-table">
            <h4>{{ $t('importTask.detail.itemsTitle') }}</h4>
            <el-table
              :data="taskDetail.items"
              border
              stripe
              style="width: 100%"
            >
              <el-table-column prop="sequenceNo" :label="$t('importTask.detail.sequenceNo')" width="100" />
              <el-table-column prop="fileName" :label="$t('importTask.detail.itemFileName')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="status" :label="$t('importTask.detail.itemStatus')" width="140">
                <template #default="{ row }">
                  <el-tag :type="itemStatusTagType(row.status)">
                    {{ getItemStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalCount" :label="$t('importTask.detail.itemTotal')" width="120" />
              <el-table-column prop="successCount" :label="$t('importTask.detail.itemSuccess')" width="120" />
              <el-table-column prop="failureCount" :label="$t('importTask.detail.itemFailure')" width="120" />
              <el-table-column prop="failureReason" :label="$t('importTask.detail.itemFailureReason')" min-width="220" show-overflow-tooltip />
              <el-table-column prop="createdAt" :label="$t('importTask.detail.itemCreatedAt')" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column prop="startedAt" :label="$t('importTask.detail.itemStartedAt')" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.startedAt) }}
                </template>
              </el-table-column>
              <el-table-column prop="completedAt" :label="$t('importTask.detail.itemCompletedAt')" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.completedAt) }}
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer
      v-model="failuresDrawerVisible"
      :title="$t('importTask.failures.title')"
      size="60%"
    >
      <div v-loading="failureLoading" class="failure-container">
        <template v-if="currentFailureTaskId">
          <div class="failure-toolbar">
            <el-form :inline="true">
              <el-form-item :label="$t('importTask.failures.status')">
                <el-select
                  v-model="failureFilters.status"
                  :placeholder="$t('importTask.failures.statusPlaceholder')"
                  clearable
                  style="width: 180px"
                  @change="loadFailures"
                >
                  <el-option
                    v-for="option in failureStatusOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button @click="resetFailureFilters">
                  <el-icon><Refresh /></el-icon>
                  {{ $t('common.reset') }}
                </el-button>
              </el-form-item>
            </el-form>
            <div class="failure-actions">
              <span v-if="selectedFailureIds.length > 0" class="selection-info">
                {{
                  $t('importTask.failures.selectionInfo', { count: selectedFailureIds.length })
                }}
              </span>
              <el-button
                :disabled="selectedFailureIds.length === 0"
                type="primary"
                @click="openRetryDialog(currentFailureTaskId, selectedFailureIds)"
              >
                {{ $t('importTask.failures.retrySelected') }}
              </el-button>
              <el-button
                type="danger"
                @click="openRetryDialog(currentFailureTaskId)"
              >
                {{ $t('importTask.failures.retryAll') }}
              </el-button>
            </div>
          </div>

          <el-table
            :data="failureList"
            border
            stripe
            style="width: 100%"
            @selection-change="handleFailureSelectionChange"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="id" :label="$t('importTask.failures.id')" width="90" />
            <el-table-column prop="section" :label="$t('importTask.failures.section')" width="120" />
            <el-table-column prop="rowNumber" :label="$t('importTask.failures.rowNumber')" width="100" />
            <el-table-column prop="field" :label="$t('importTask.failures.field')" width="140" />
            <el-table-column prop="status" :label="$t('importTask.failures.status')" width="140">
              <template #default="{ row }">
                <el-tag :type="failureStatusTagType(row.status)">
                  {{ getFailureStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="$t('importTask.failures.message')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="rawPayload" :label="$t('importTask.failures.rawPayload')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="createdAt" :label="$t('importTask.failures.createdAt')" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="resolvedAt" :label="$t('importTask.failures.resolvedAt')" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.resolvedAt) }}
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="failurePagination.page"
              v-model:page-size="failurePagination.size"
              :total="failurePagination.total"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadFailures"
              @current-change="loadFailures"
            />
          </div>
        </template>
        <el-empty
          v-else
          :description="$t('importTask.failures.empty')"
        />
      </div>
    </el-drawer>

    <el-dialog
      v-model="retryDialogVisible"
      :title="$t('importTask.retry.title')"
      width="500px"
    >
      <el-form label-width="120px">
        <el-form-item :label="$t('importTask.retry.fileLabel')" required>
          <input type="file" @change="handleRetryFileChange" />
        </el-form-item>
        <el-form-item v-if="retryFailureIds.length > 0">
          <el-alert
            type="info"
            :title="$t('importTask.retry.selectedCount', { count: retryFailureIds.length })"
            show-icon
          />
        </el-form-item>
        <el-form-item>
          <el-text type="info">
            {{ $t('importTask.retry.tip') }}
          </el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retryDialogVisible = false">
          {{ $t('importTask.retry.cancel') }}
        </el-button>
        <el-button type="primary" :loading="retryLoading" @click="submitRetry">
          {{ $t('importTask.retry.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { importTaskApi } from '@/api/importTask'
import type {
  ImportTaskSummary,
  ImportTaskStatus,
  ImportTaskDetail,
  ImportTaskItemStatus,
  ImportTaskFailureDTO,
  ImportFailureStatus,
} from '@/types/importTask'
import { useI18n } from 'vue-i18n'

interface PageResponse<T> {
  content: T[]
  totalElements: number
  size: number
  number: number
}

const { t } = useI18n()

const loading = ref(false)
const tasks = ref<ImportTaskSummary[]>([])
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const searchForm = reactive<{
  importType: string
  status: ImportTaskStatus | ''
  createdBy: string
}>({
  importType: '',
  status: '',
  createdBy: '',
})

const statusOptions = computed(() =>
  (['WAITING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'] as ImportTaskStatus[]).map(
    (value) => ({
      value,
      label: getStatusText(value),
    }),
  ),
)

const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const taskDetail = ref<ImportTaskDetail | null>(null)

const failuresDrawerVisible = ref(false)
const failureLoading = ref(false)
const currentFailureTaskId = ref<number | null>(null)
const failureList = ref<ImportTaskFailureDTO[]>([])
const failurePagination = reactive({
  page: 1,
  size: 50,
  total: 0,
})
const failureFilters = reactive<{
  status: ImportFailureStatus | ''
}>({
  status: '',
})
const selectedFailureIds = ref<number[]>([])

const failureStatusOptions = computed(() =>
  (['PENDING', 'RESUBMITTED', 'RESOLVED'] as ImportFailureStatus[]).map((value) => ({
    value,
    label: getFailureStatusText(value),
  })),
)

const retryDialogVisible = ref(false)
const retryLoading = ref(false)
const retryTaskId = ref<number | null>(null)
const retryFailureIds = ref<number[]>([])
const retryFile = ref<File | null>(null)

const loadTasks = async () => {
  loading.value = true
  try {
    const response = await importTaskApi.listTasks({
      importType: searchForm.importType || undefined,
      status: searchForm.status || undefined,
      createdBy: searchForm.createdBy || undefined,
      page: pagination.page - 1,
      size: pagination.size,
    })

    const pageResponse: PageResponse<ImportTaskSummary> =
      (response && (response as any).content ? response : (response as any)?.data) ||
      response || {
        content: [],
        totalElements: 0,
        size: pagination.size,
        number: pagination.page - 1,
      }

    tasks.value = pageResponse.content || []
    pagination.total = pageResponse.totalElements || 0
    pagination.size = pageResponse.size || pagination.size
  } catch (error) {
    console.error('Failed to load import tasks:', error)
    ElMessage.error(t('importTask.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadTasks()
}

const handleReset = () => {
  searchForm.importType = ''
  searchForm.status = ''
  searchForm.createdBy = ''
  pagination.page = 1
  loadTasks()
}

const openDetailDrawer = async (taskId: number) => {
  detailDrawerVisible.value = true
  detailLoading.value = true
  try {
    const response = await importTaskApi.getTaskDetail(taskId)
    const detail = (response as ImportTaskDetail) || (response as any)?.data
    taskDetail.value = detail
  } catch (error) {
    console.error('Failed to load task detail:', error)
    ElMessage.error(t('importTask.message.detailFailed'))
  } finally {
    detailLoading.value = false
  }
}

const openFailuresDrawer = async (taskId: number) => {
  failuresDrawerVisible.value = true
  currentFailureTaskId.value = taskId
  failurePagination.page = 1
  selectedFailureIds.value = []
  await loadFailures()
}

const loadFailures = async () => {
  if (!currentFailureTaskId.value) {
    return
  }
  failureLoading.value = true
  try {
    const response = await importTaskApi.listFailures(currentFailureTaskId.value, {
      status: failureFilters.status || undefined,
      page: failurePagination.page - 1,
      size: failurePagination.size,
    })

    const pageResponse: PageResponse<ImportTaskFailureDTO> =
      (response && (response as any).content ? response : (response as any)?.data) ||
      response || {
        content: [],
        totalElements: 0,
        size: failurePagination.size,
        number: failurePagination.page - 1,
      }

    failureList.value = pageResponse.content || []
    failurePagination.total = pageResponse.totalElements || 0
    failurePagination.size = pageResponse.size || failurePagination.size
  } catch (error) {
    console.error('Failed to load task failures:', error)
    ElMessage.error(t('importTask.message.failureLoadFailed'))
  } finally {
    failureLoading.value = false
  }
}

const resetFailureFilters = () => {
  failureFilters.status = ''
  failurePagination.page = 1
  loadFailures()
}

const handleFailureSelectionChange = (rows: ImportTaskFailureDTO[]) => {
  selectedFailureIds.value = rows.map((row) => row.id)
}

const openRetryDialog = (taskId: number, failureIds: number[] = []) => {
  retryTaskId.value = taskId
  retryFailureIds.value = failureIds
  retryDialogVisible.value = true
}

const handleRetryFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    retryFile.value = input.files[0]
  } else {
    retryFile.value = null
  }
}

const submitRetry = async () => {
  if (!retryTaskId.value) {
    return
  }
  if (!retryFile.value) {
    ElMessage.warning(t('importTask.retry.fileRequired'))
    return
  }

  retryLoading.value = true
  try {
    await importTaskApi.retryTask(retryTaskId.value, retryFile.value, retryFailureIds.value)
    ElMessage.success(t('importTask.message.retrySuccess'))
    retryDialogVisible.value = false
    await loadTasks()
    if (failuresDrawerVisible.value) {
      await loadFailures()
    }
  } catch (error) {
    console.error('Failed to submit retry task:', error)
    ElMessage.error(t('importTask.message.retryFailed'))
  } finally {
    retryLoading.value = false
  }
}

watch(retryDialogVisible, (visible) => {
  if (!visible) {
    retryTaskId.value = null
    retryFailureIds.value = []
    retryFile.value = null
  }
})

watch(failuresDrawerVisible, (visible) => {
  if (!visible) {
    currentFailureTaskId.value = null
    failureList.value = []
    selectedFailureIds.value = []
  }
})

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--'
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getStatusText = (status: ImportTaskStatus) => {
  const key = `importTask.status.${status.toLowerCase()}`
  return t(key)
}

const statusTagType = (status: ImportTaskStatus) => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    case 'QUEUED':
    case 'WAITING':
      return 'info'
    case 'CANCELLED':
      return 'default'
    default:
      return ''
  }
}

const getItemStatusText = (status: ImportTaskItemStatus) => {
  const key = `importTask.itemStatus.${status.toLowerCase()}`
  return t(key)
}

const itemStatusTagType = (status: ImportTaskItemStatus) => {
  switch (status) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'RUNNING':
      return 'warning'
    case 'PENDING':
      return 'info'
    case 'CANCELLED':
      return 'default'
    default:
      return ''
  }
}

const getFailureStatusText = (status: ImportFailureStatus) => {
  const key = `importTask.failureStatus.${status.toLowerCase()}`
  return t(key)
}

const failureStatusTagType = (status: ImportFailureStatus) => {
  switch (status) {
    case 'RESOLVED':
      return 'success'
    case 'RESUBMITTED':
      return 'warning'
    case 'PENDING':
      return 'danger'
    default:
      return ''
  }
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.import-task-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.task-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.task-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
}

.search-bar {
  margin-bottom: 20px;
  flex-shrink: 0;
}

.table-container {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

.detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-descriptions {
  margin-bottom: 16px;
}

.detail-table {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.detail-table h4 {
  margin-bottom: 12px;
}

.failure-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.failure-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
}

.failure-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selection-info {
  color: #409eff;
}

@media (min-width: 1024px) {
  .card-header {
    font-size: 18px;
  }
}
</style>

