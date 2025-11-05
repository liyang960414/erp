<template>
  <div class="audit-log-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ $t('audit.auditLog') }}</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm">
          <el-form-item :label="$t('audit.username')">
            <el-input
              v-model="searchForm.username"
              :placeholder="$t('audit.usernamePlaceholder')"
              clearable
              style="width: 150px"
            />
          </el-form-item>

          <el-form-item :label="$t('audit.action')">
            <el-select
              v-model="searchForm.action"
              :placeholder="$t('audit.actionPlaceholder')"
              clearable
              style="width: 150px"
            >
              <el-option :label="$t('audit.actionLogin')" value="LOGIN" />
              <el-option :label="$t('audit.actionRegister')" value="REGISTER" />
              <el-option :label="$t('audit.actionCreateUser')" value="CREATE_USER" />
              <el-option :label="$t('audit.actionUpdateUser')" value="UPDATE_USER" />
              <el-option :label="$t('audit.actionDeleteUser')" value="DELETE_USER" />
              <el-option :label="$t('audit.actionChangePassword')" value="CHANGE_PASSWORD" />
              <el-option :label="$t('audit.actionCreateRole')" value="CREATE_ROLE" />
              <el-option :label="$t('audit.actionUpdateRole')" value="UPDATE_ROLE" />
              <el-option :label="$t('audit.actionDeleteRole')" value="DELETE_ROLE" />
            </el-select>
          </el-form-item>

          <el-form-item :label="$t('audit.module')">
            <el-select
              v-model="searchForm.module"
              :placeholder="$t('audit.modulePlaceholder')"
              clearable
              style="width: 150px"
            >
              <el-option :label="$t('audit.moduleAuth')" value="AUTH" />
              <el-option :label="$t('audit.moduleUserManagement')" value="USER_MANAGEMENT" />
              <el-option :label="$t('audit.moduleRoleManagement')" value="ROLE_MANAGEMENT" />
            </el-select>
          </el-form-item>

          <el-form-item :label="$t('audit.status')">
            <el-select
              v-model="searchForm.status"
              :placeholder="$t('audit.statusPlaceholder')"
              clearable
              style="width: 120px"
            >
              <el-option :label="$t('audit.statusSuccess')" value="SUCCESS" />
              <el-option :label="$t('audit.statusFailure')" value="FAILURE" />
            </el-select>
          </el-form-item>

          <el-form-item :label="$t('audit.startTime')">
            <el-date-picker
              v-model="searchForm.startTime"
              type="datetime"
              :placeholder="$t('audit.startTimePlaceholder')"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 200px"
            />
          </el-form-item>

          <el-form-item :label="$t('audit.endTime')">
            <el-date-picker
              v-model="searchForm.endTime"
              type="datetime"
              :placeholder="$t('audit.endTimePlaceholder')"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 200px"
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

      <!-- 审计日志表格 -->
      <div class="table-container">
        <el-table v-loading="loading" :data="auditLogs" style="width: 100%" border stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column :label="$t('audit.username')" prop="username" width="120" />
          <el-table-column :label="$t('audit.action')" prop="action" width="150">
            <template #default="{ row }">
              <el-tag :type="getActionTagType(row.action)" size="small">
                {{ getActionText(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('audit.module')" prop="module" width="120">
            <template #default="{ row }">
              <el-tag type="info" size="small">
                {{ getModuleText(row.module) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('audit.resourceType')" prop="resourceType" width="120" />
          <el-table-column
            :label="$t('audit.description')"
            prop="description"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column :label="$t('audit.status')" prop="status" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{
                  row.status === 'SUCCESS' ? $t('audit.statusSuccess') : $t('audit.statusFailure')
                }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('audit.ipAddress')" prop="ipAddress" width="140" />
          <el-table-column :label="$t('audit.createdAt')" prop="createdAt" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'FAILURE' && row.errorMessage"
                type="warning"
                size="small"
                @click="handleViewError(row)"
              >
                {{ $t('audit.viewError') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadAuditLogs"
          @current-change="loadAuditLogs"
        />
      </div>
    </el-card>

    <!-- 错误详情对话框 -->
    <el-dialog v-model="errorDialogVisible" :title="$t('audit.errorDetails')" width="600px">
      <el-input
        v-model="errorMessage"
        type="textarea"
        :autosize="{ minRows: 5, maxRows: 10 }"
        readonly
      />
      <template #footer>
        <el-button @click="errorDialogVisible = false">
          {{ $t('common.close') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Refresh } from '@element-plus/icons-vue'
import { auditLogApi, type AuditLog } from '@/api/auditLog'

const { t } = useI18n()

const loading = ref(false)
const auditLogs = ref<AuditLog[]>([])
const errorDialogVisible = ref(false)
const errorMessage = ref('')

const searchForm = ref({
  username: '',
  action: '',
  module: '',
  status: '',
  startTime: '',
  endTime: '',
})

const pagination = ref({
  page: 1,
  size: 20,
  total: 0,
})

onMounted(() => {
  loadAuditLogs()
})

const loadAuditLogs = async () => {
  loading.value = true
  try {
    const response = await auditLogApi.getAuditLogs({
      page: pagination.value.page - 1,
      size: pagination.value.size,
      sortBy: 'id',
      sortDir: 'DESC',
      ...searchForm.value,
    })

    // 处理响应：可能是 AxiosResponse 或直接是 PageResponse
    const pageResponse = (response as any).data || response
    auditLogs.value = pageResponse.content || []
    pagination.value.total = pageResponse.totalElements || 0
  } catch (error) {
    console.error('加载审计日志失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  loadAuditLogs()
}

const handleReset = () => {
  searchForm.value = {
    username: '',
    action: '',
    module: '',
    status: '',
    startTime: '',
    endTime: '',
  }
  pagination.value.page = 1
  loadAuditLogs()
}

const handleViewError = (row: AuditLog) => {
  errorMessage.value = row.errorMessage || ''
  errorDialogVisible.value = true
}

const formatDateTime = (dateStr: string) => {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getActionText = (action: string) => {
  const actionMap: Record<string, string> = {
    LOGIN: t('audit.actionLogin'),
    REGISTER: t('audit.actionRegister'),
    CREATE_USER: t('audit.actionCreateUser'),
    UPDATE_USER: t('audit.actionUpdateUser'),
    DELETE_USER: t('audit.actionDeleteUser'),
    CHANGE_PASSWORD: t('audit.actionChangePassword'),
    CREATE_ROLE: t('audit.actionCreateRole'),
    UPDATE_ROLE: t('audit.actionUpdateRole'),
    DELETE_ROLE: t('audit.actionDeleteRole'),
  }
  return actionMap[action] || action
}

const getActionTagType = (action: string) => {
  if (action.includes('CREATE')) return 'success'
  if (action.includes('UPDATE')) return 'warning'
  if (action.includes('DELETE')) return 'danger'
  if (action === 'LOGIN' || action === 'REGISTER') return 'primary'
  return ''
}

const getModuleText = (module: string) => {
  const moduleMap: Record<string, string> = {
    AUTH: t('audit.moduleAuth'),
    USER_MANAGEMENT: t('audit.moduleUserManagement'),
    ROLE_MANAGEMENT: t('audit.moduleRoleManagement'),
  }
  return moduleMap[module] || module
}
</script>

<style scoped>
.audit-log-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.audit-log-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.audit-log-container :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.search-bar {
  margin-bottom: 24px;
  flex-shrink: 0;
}

.table-container {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .audit-log-container {
    padding: 0;
  }

  .card-header {
    font-size: 18px;
    padding: 16px 0;
  }

  .search-bar {
    margin-bottom: 24px;
  }
}
</style>
