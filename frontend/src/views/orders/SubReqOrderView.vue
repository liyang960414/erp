<template>
  <div class="order-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>委外订单管理</span>
          <div class="header-actions">
            <el-button v-if="authStore.hasPermission('sub_req_order:import')" type="success" @click="handleImport">
              <el-icon><Upload /></el-icon>
              导入Excel
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
          <el-form-item label="单据头序号">
            <el-input-number
              v-model="searchForm.billHeadSeq"
              placeholder="请输入单据头序号"
              clearable
              :min="1"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="订单状态">
            <el-select
              v-model="searchForm.status"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="进行中" value="OPEN" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="searchForm.description"
              placeholder="请输入备注"
              clearable
              style="width: 200px"
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

      <!-- 订单列表 -->
      <div class="table-wrapper">
        <div class="table-container">
          <el-table
            v-loading="loading"
            :data="orders"
            style="width: 100%"
            border
            row-key="id"
            @expand-change="handleTableExpand"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div v-if="expandedRows.has(row.id)" class="order-details">
                  <div v-if="loadingDetails[row.id]" class="loading-wrapper">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>加载中...</span>
                  </div>
                  <el-table
                    v-else-if="(orderDetailsMap[row.id]?.items?.length ?? 0) > 0"
                    :data="orderDetailsMap[row.id]?.items"
                    border
                    size="small"
                    max-height="400"
                  >
                    <el-table-column prop="sequence" label="序号" width="80" />
                    <el-table-column prop="materialCode" label="物料编码" width="150" />
                    <el-table-column prop="materialName" label="物料名称" min-width="200" />
                    <el-table-column prop="unitCode" label="单位" width="100" />
                    <el-table-column prop="qty" label="数量" width="120" align="right">
                      <template #default="{ row }">
                        {{ formatNumber(row.qty) }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="supplierName" label="供应商" min-width="200" />
                    <el-table-column prop="bomVersion" label="BOM版本" width="120">
                      <template #default="{ row }">
                        {{ row.bomVersion || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="lotMaster" label="批号主档" width="150">
                      <template #default="{ row }">
                        {{ row.lotMaster || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="lotManual" label="批号手工" width="150">
                      <template #default="{ row }">
                        {{ row.lotManual || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="baseNoStockInQty" label="基本单位未入库数量" width="150" align="right">
                      <template #default="{ row }">
                        {{ formatNumber(row.baseNoStockInQty) }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="noStockInQty" label="未入库数量" width="120" align="right">
                      <template #default="{ row }">
                        {{ formatNumber(row.noStockInQty) }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="pickMtrlStatus" label="领料状态" width="100">
                      <template #default="{ row }">
                        {{ row.pickMtrlStatus || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column prop="description" label="备注" min-width="150" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.description || '-' }}
                      </template>
                    </el-table-column>
                  </el-table>
                  <el-empty v-else description="该订单暂无明细" />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="billHeadSeq" label="单据头序号" width="140" align="center">
              <template #default="{ row }">
                <span class="bill-head-seq">{{ row.billHeadSeq }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="备注" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="description-text">{{ row.description || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="订单状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'OPEN'" type="success" size="small">进行中</el-tag>
                <el-tag v-else-if="row.status === 'CLOSED'" type="info" size="small">已关闭</el-tag>
                <el-tag v-else type="warning" size="small">{{ row.status }}</el-tag>
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

        <!-- 分页 -->
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

    <!-- 导入对话框 -->
    <SubReqOrderImportDialog v-model="importDialogVisible" @success="handleImportSuccess" />

    <!-- 详情对话框 -->
    <SubReqOrderDetailDialog
      v-model="detailDialogVisible"
      :order="currentOrder"
    />
  </div>
</template>

<script lang="ts">
export default {
  name: 'subReqOrders',
}
</script>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Refresh, Search, Loading, View } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { subReqOrderApi } from '@/api/subReqOrder'
import type { SubReqOrder } from '@/types/subReqOrder'
import SubReqOrderImportDialog from './components/SubReqOrderImportDialog.vue'
import SubReqOrderDetailDialog from './components/SubReqOrderDetailDialog.vue'

const authStore = useAuthStore()

const loading = ref(false)
const orders = ref<SubReqOrder[]>([])
const expandedRows = ref(new Set<number>())
const loadingDetails = ref<Record<number, boolean>>({})
const orderDetailsMap = ref<Record<number, SubReqOrder>>({})
const importDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentOrder = ref<SubReqOrder | null>(null)

const searchForm = reactive({
  billHeadSeq: undefined as number | undefined,
  status: undefined as 'OPEN' | 'CLOSED' | undefined,
  description: '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 首次加载标志
const isFirstLoad = ref(true)

onMounted(() => {
  if (isFirstLoad.value) {
    loadOrders()
    isFirstLoad.value = false
  }
})

onActivated(() => {
  // 只在首次加载时执行，后续切换回来不重新加载
})

const loadOrders = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page - 1,
      size: pagination.size,
    }

    if (searchForm.billHeadSeq) {
      params.billHeadSeq = searchForm.billHeadSeq
    }
    if (searchForm.status) {
      params.status = searchForm.status
    }
    if (searchForm.description) {
      params.description = searchForm.description
    }

    const response = await subReqOrderApi.getSubReqOrders(params)
    
    // 处理嵌套的响应结构
    orders.value = response.content || []
    
    // 优先从嵌套的page对象中获取分页信息
    if (response.page?.totalElements !== undefined && response.page?.totalElements !== null) {
      pagination.total = response.page.totalElements
    } else if (response.totalElements !== undefined && response.totalElements !== null) {
      // 兼容直接包含totalElements的情况
      pagination.total = response.totalElements
    } else if (response.total !== undefined && response.total !== null) {
      pagination.total = response.total
    } else if (Array.isArray(response.content)) {
      // 如果没有total信息，使用content长度（这种情况不应该发生，但作为fallback）
      console.warn('API响应缺少totalElements字段，使用content长度作为fallback')
      pagination.total = response.content.length
    } else {
      pagination.total = 0
    }
  } catch (error: any) {
    ElMessage.error('加载订单列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadOrders()
}

const handleReset = () => {
  searchForm.billHeadSeq = undefined
  searchForm.status = undefined
  searchForm.description = ''
  pagination.page = 1
  loadOrders()
}

const handleRefresh = () => {
  loadOrders()
}

const handlePageChange = (page: number) => {
  // 确保页码已更新（虽然 v-model 会自动更新，但显式设置更保险）
  if (pagination.page !== page) {
    pagination.page = page
  }
  loadOrders()
  // 滚动到顶部
  nextTick(() => {
    const tableContainer = document.querySelector('.table-container')
    if (tableContainer) {
      tableContainer.scrollTop = 0
    }
  })
}

const handlePageSizeChange = (size: number) => {
  // 确保每页条数已更新
  if (pagination.size !== size) {
    pagination.size = size
  }
  pagination.page = 1
  loadOrders()
}

const handleImport = () => {
  importDialogVisible.value = true
}

const handleImportSuccess = () => {
  loadOrders()
}

const handleTableExpand = (row: SubReqOrder, expandedRowsList: SubReqOrder[]) => {
  // 展开行
  if (expandedRowsList.includes(row)) {
    expandedRows.value.add(row.id)
    // 只有在数据未加载且不在加载中时才发起请求
    if (!orderDetailsMap.value[row.id] && !loadingDetails.value[row.id]) {
      loadingDetails.value[row.id] = true
      subReqOrderApi
        .getSubReqOrderById(row.id)
        .then((order) => {
          orderDetailsMap.value[row.id] = order
        })
        .catch((error: any) => {
          ElMessage.error(`加载订单详情失败: ${error.message || '未知错误'}`)
          orderDetailsMap.value[row.id] = { ...row, items: [] } as SubReqOrder
        })
        .finally(() => {
          loadingDetails.value[row.id] = false
        })
    }
  } else {
    // 折叠行
    expandedRows.value.delete(row.id)
  }
}

const handleViewDetail = async (order: SubReqOrder) => {
  try {
    currentOrder.value = await subReqOrderApi.getSubReqOrderById(order.id)
    detailDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error('加载订单详情失败: ' + (error.message || '未知错误'))
  }
}

const formatNumber = (value: number | string | undefined): string => {
  if (value == null || value === undefined) return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 6 })
}

const formatDateTime = (value: string): string => {
  if (!value) return '-'
  try {
    const date = new Date(value)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}
</script>

<style scoped>
.order-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  margin: 0;
  padding: 0;
}

.order-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--el-border-color-lighter);
}

.order-container :deep(.el-card__header) {
  padding: 18px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
}

.order-container :deep(.el-card__body) {
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
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.search-form {
  margin: 0;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 16px;
}

.search-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.search-actions {
  margin-left: auto !important;
}

.table-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.table-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  min-height: 0;
}

.table-container :deep(.el-table) {
  border-radius: 8px;
  overflow: visible;
}

.table-container :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

.table-container :deep(.el-table__header) {
  background-color: #fafafa;
}

.table-container :deep(.el-table__header th) {
  background-color: #fafafa;
  color: var(--el-text-color-primary);
  font-weight: 600;
  border-bottom: 2px solid var(--el-border-color-light);
}

.table-container :deep(.el-table__row) {
  transition: background-color 0.2s;
}

.table-container :deep(.el-table__row:hover) {
  background-color: #f5f7fa;
}

.table-container :deep(.el-table__row.el-table__row--expanded) {
  background-color: #f9fafb;
}

.bill-head-seq {
  font-weight: 500;
  color: var(--el-color-primary);
}

.description-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.order-details {
  padding: 16px;
  background-color: #fafbfc;
  border-radius: 6px;
  margin: 8px 0;
}

.order-details :deep(.el-table) {
  background-color: #ffffff;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.loading-wrapper .el-icon {
  font-size: 20px;
}

.pagination {
  margin-top: 0;
  padding: 16px 0;
  display: flex !important;
  justify-content: flex-end;
  align-items: center;
  background-color: #ffffff;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  min-height: 40px;
  width: 100%;
  max-width: 100%;
  visibility: visible !important;
  opacity: 1 !important;
  position: relative;
  z-index: 1;
}

.pagination :deep(.el-pagination) {
  display: flex !important;
  visibility: visible !important;
  opacity: 1 !important;
  width: 100%;
  max-width: 100%;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.pagination :deep(.el-pagination__sizes),
.pagination :deep(.el-pagination__jump) {
  margin: 0 8px;
}

/* 响应式：小屏幕时简化分页布局 */
@media (max-width: 768px) {
  .pagination :deep(.el-pagination) {
    font-size: 12px;
  }

  .pagination :deep(.el-pagination .el-select) {
    width: 80px;
  }

  .pagination :deep(.el-pagination__jump) {
    margin-left: 8px;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .search-bar {
    padding: 16px;
  }

  .search-form :deep(.el-form-item) {
    margin-right: 8px;
    margin-bottom: 12px;
  }

  .search-actions {
    margin-left: 0 !important;
    width: 100%;
  }

  .header-actions {
    flex-direction: column;
    width: 100%;
    gap: 8px;
  }

  .header-actions .el-button {
    width: 100%;
  }
}

/* 优化表格在移动端的显示 */
@media (max-width: 768px) {
  .table-container :deep(.el-table) {
    font-size: 12px;
  }

  .table-container :deep(.el-table th),
  .table-container :deep(.el-table td) {
    padding: 8px 4px;
  }
}
</style>

