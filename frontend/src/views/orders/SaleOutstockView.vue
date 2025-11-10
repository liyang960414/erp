<template>
  <div class="outstock-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>销售出库单管理</span>
          <div class="header-actions">
            <el-button
              v-if="authStore.hasPermission('sale_outstock:import')"
              type="warning"
              @click="handleImport"
            >
              <el-icon><Upload /></el-icon>
              导入出库单
            </el-button>
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="出库单号">
            <el-input
              v-model="searchForm.billNo"
              placeholder="请输入出库单号"
              clearable
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="出库日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 240px"
            />
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 出库单列表 -->
      <div class="table-wrapper">
        <div class="table-container">
          <el-table
            v-loading="loading"
            :data="outstocks"
            style="width: 100%"
            border
            row-key="id"
            @expand-change="handleTableExpand"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div v-if="expandedRows.has(row.id)" class="outstock-details">
                  <div v-if="loadingDetails[row.id]" class="loading-wrapper">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>加载中...</span>
                  </div>
                  <el-table
                    v-else-if="(outstockDetailsMap[row.id]?.items?.length ?? 0) > 0"
                    :data="outstockDetailsMap[row.id]?.items"
                    border
                    size="small"
                    max-height="360"
                  >
                    <el-table-column prop="sequence" label="序号" width="80" />
                    <el-table-column
                      prop="saleOrderBillNo"
                      label="销售单号"
                      width="150"
                      show-overflow-tooltip
                    />
                    <el-table-column prop="saleOrderSequence" label="销售明细序号" width="140" />
                    <el-table-column
                      prop="materialCode"
                      label="物料编码"
                      width="160"
                      show-overflow-tooltip
                    />
                    <el-table-column
                      prop="materialName"
                      label="物料名称"
                      min-width="200"
                      show-overflow-tooltip
                    />
                    <el-table-column prop="unitCode" label="单位" width="100" />
                    <el-table-column prop="qty" label="出库数量" width="140" align="right">
                      <template #default="{ row }">
                        {{ formatNumber(row.qty) }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="woNumber" label="本司WO#" width="140" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.woNumber || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="entryNote" label="备注" min-width="160" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.entryNote || '-' }}
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-empty v-else description="该出库单暂无明细" />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="billNo" label="单据编号" width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="bill-no">{{ row.billNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="outstockDate" label="出库日期" width="140" align="center">
              <template #default="{ row }">
                <span class="outstock-date">{{ row.outstockDate }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="itemCount" label="明细数量" width="120" align="center" />
            <el-table-column prop="totalQty" label="总出库数量" width="140" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.totalQty) }}
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="note-text">{{ row.note || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right" align="center">
              <template #default="{ row }">
                <el-button type="primary" size="small" link @click="handleViewDetail(row)">
                  <el-icon><View /></el-icon>
                  查看详情
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
            background
            :hide-on-single-page="false"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </el-card>

    <SaleOutstockImportDialog v-model="importDialogVisible" @success="handleImportSuccess" />
    <SaleOutstockDetailDialog
      v-model="detailDialogVisible"
      :outstock="currentOutstock"
    />
  </div>
</template>

<script lang="ts">
export default {
  name: 'saleOutstocks',
}
</script>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Refresh, Search, Loading, View } from '@element-plus/icons-vue'
import { saleOutstockApi } from '@/api/saleOutstock'
import type { SaleOutstock } from '@/types/saleOutstock'
import { useAuthStore } from '@/stores/auth'
import SaleOutstockImportDialog from './components/SaleOutstockImportDialog.vue'
import SaleOutstockDetailDialog from './components/SaleOutstockDetailDialog.vue'

const authStore = useAuthStore()

const loading = ref(false)
const outstocks = ref<SaleOutstock[]>([])
const expandedRows = ref(new Set<number>())
const loadingDetails = ref<Record<number, boolean>>({})
const outstockDetailsMap = ref<Record<number, SaleOutstock>>({})
const importDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentOutstock = ref<SaleOutstock | null>(null)

const dateRange = ref<[string, string] | null>(null)

const searchForm = reactive({
  billNo: '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const loadOutstocks = async () => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.page - 1,
      size: pagination.size,
    }

    if (searchForm.billNo) {
      params.billNo = searchForm.billNo
    }
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }

    const response = await saleOutstockApi.getSaleOutstocks(params)
    outstocks.value = response.content || []

    if (response.page?.totalElements !== undefined && response.page?.totalElements !== null) {
      pagination.total = response.page.totalElements
    } else if (response.totalElements !== undefined && response.totalElements !== null) {
      pagination.total = response.totalElements
    } else if (Array.isArray(response.content)) {
      pagination.total = response.content.length
    } else {
      pagination.total = 0
    }
  } catch (error: any) {
    ElMessage.error('加载出库单列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOutstocks()
})

const handleSearch = () => {
  pagination.page = 1
  loadOutstocks()
}

const handleReset = () => {
  searchForm.billNo = ''
  dateRange.value = null
  pagination.page = 1
  loadOutstocks()
}

const handleRefresh = () => {
  loadOutstocks()
}

const handlePageChange = (page: number) => {
  if (pagination.page !== page) {
    pagination.page = page
  }
  loadOutstocks()
  nextTick(() => {
    const tableContainer = document.querySelector('.table-container')
    if (tableContainer) {
      tableContainer.scrollTop = 0
    }
  })
}

const handlePageSizeChange = (size: number) => {
  if (pagination.size !== size) {
    pagination.size = size
  }
  pagination.page = 1
  loadOutstocks()
}

const handleImport = () => {
  importDialogVisible.value = true
}

const handleImportSuccess = () => {
  loadOutstocks()
  expandedRows.value.clear()
  outstockDetailsMap.value = {}
}

const handleTableExpand = (row: SaleOutstock, expandedRowsList: SaleOutstock[]) => {
  if (expandedRowsList.includes(row)) {
    expandedRows.value.add(row.id)
    if (!outstockDetailsMap.value[row.id] && !loadingDetails.value[row.id]) {
      loadingDetails.value[row.id] = true
      saleOutstockApi
        .getSaleOutstockById(row.id)
        .then((detail) => {
          outstockDetailsMap.value[row.id] = detail
        })
        .catch((error: any) => {
          ElMessage.error(`加载出库单详情失败: ${error.message || '未知错误'}`)
          outstockDetailsMap.value[row.id] = { ...row, items: [] } as SaleOutstock
        })
        .finally(() => {
          loadingDetails.value[row.id] = false
        })
    }
  } else {
    expandedRows.value.delete(row.id)
  }
}

const handleViewDetail = async (row: SaleOutstock) => {
  try {
    currentOutstock.value = await saleOutstockApi.getSaleOutstockById(row.id)
    detailDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error('加载出库单详情失败: ' + (error.message || '未知错误'))
  }
}

const formatNumber = (value: number | string): string => {
  if (value == null) return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (Number.isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 6 })
}
</script>

<style scoped>
.outstock-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  margin: 0;
  padding: 0;
}

.outstock-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--el-border-color-lighter);
}

.outstock-container :deep(.el-card__header) {
  padding: 18px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
}

.outstock-container :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.card-header span {
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  margin-bottom: 20px;
}

.search-form :deep(.el-form-item) {
  margin-right: 16px;
  margin-bottom: 12px;
}

.search-actions {
  margin-left: auto;
}

.table-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.table-container {
  flex: 1;
  overflow: auto;
}

.outstock-details {
  padding: 12px 0 0 0;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 120px;
  color: var(--el-text-color-secondary);
}

.bill-no {
  font-weight: 600;
  color: var(--el-color-primary);
}

.note-text {
  color: var(--el-text-color-secondary);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 0 0;
}

@media (max-width: 768px) {
  .header-actions {
    flex-direction: column;
    align-items: flex-end;
    gap: 8px;
  }

  .search-form :deep(.el-form-item) {
    width: 100%;
  }

  .search-form :deep(.el-input),
  .search-form :deep(.el-date-editor) {
    width: 100% !important;
  }
}
</style>

