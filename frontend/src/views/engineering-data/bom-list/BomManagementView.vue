<template>
  <div class="bom-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>物料清单列表</span>
          <div class="header-actions">
            <el-button v-if="authStore.hasRole('ADMIN')" type="success" @click="handleImport">
              <el-icon>
                <Upload />
              </el-icon>
              导入Excel/CSV
            </el-button>
            <el-button type="primary" @click="handleLoadAll">
              <el-icon>
                <Refresh />
              </el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div v-if="boms.length > 0" class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索父项物料编码、名称、BOM版本..."
          clearable
          style="width: 400px"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon>
              <Search />
            </el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          <el-icon>
            <Search />
          </el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- BOM列表 -->
      <div class="table-container">
        <el-empty
          v-if="!dataLoaded && boms.length === 0"
          description="请点击「刷新」按钮加载数据"
        />
        <el-table
          v-else
          v-loading="loading"
          :data="paginatedBoms"
          style="width: 100%"
          border
          row-key="id"
        >
          <el-table-column prop="materialCode" label="父项物料编码" width="180" />
          <el-table-column
            prop="materialName"
            label="父项物料名称"
            min-width="200"
            show-overflow-tooltip
          />
          <el-table-column prop="materialGroupName" label="物料组" width="150" />
          <el-table-column prop="version" label="BOM版本" width="100" />
          <el-table-column prop="category" label="BOM分类" width="120" />
          <el-table-column prop="usage" label="BOM用途" width="120" />
          <el-table-column prop="name" label="BOM简称" width="150" show-overflow-tooltip />
          <el-table-column label="明细数量" width="100">
            <template #default="{ row }">
              {{ row.items ? row.items.length : 0 }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleViewDetail(row)">
                查看详情
              </el-button>
              <el-button
                v-if="authStore.hasRole('ADMIN')"
                type="danger"
                size="small"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="filteredBoms.length > 0" class="pagination">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="filteredBoms.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </el-card>

    <!-- 导入对话框 -->
    <BomImportDialog v-model="importDialogVisible" @import-success="handleImportSuccess" />

    <!-- 详情对话框 -->
    <BomDetailDialog
      v-model="detailDialogVisible"
      :bom="currentBom"
      @update-success="handleUpdateSuccess"
    />
  </div>
</template>

<script lang="ts">
export default {
  name: 'boms',
}
</script>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Upload, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { bomApi } from '@/api/bom'
import BomImportDialog from './components/BomImportDialog.vue'
import BomDetailDialog from './components/BomDetailDialog.vue'
import type { BillOfMaterial } from '@/types/bom'

const authStore = useAuthStore()

const boms = ref<BillOfMaterial[]>([])
const loading = ref(false)
const dataLoaded = ref(false)
const searchKeyword = ref('')
const importDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentBom = ref<BillOfMaterial | null>(null)

const pagination = ref({
  page: 1,
  size: 20,
})

// 过滤后的BOM列表
const filteredBoms = computed(() => {
  if (!searchKeyword.value.trim()) {
    return boms.value
  }

  const keyword = searchKeyword.value.toLowerCase()
  return boms.value.filter((bom) => {
    return (
      bom.materialCode.toLowerCase().includes(keyword) ||
      bom.materialName.toLowerCase().includes(keyword) ||
      bom.version.toLowerCase().includes(keyword) ||
      (bom.category && bom.category.toLowerCase().includes(keyword)) ||
      (bom.usage && bom.usage.toLowerCase().includes(keyword)) ||
      (bom.name && bom.name.toLowerCase().includes(keyword))
    )
  })
})

// 分页后的BOM列表
const paginatedBoms = computed(() => {
  const start = (pagination.value.page - 1) * pagination.value.size
  const end = start + pagination.value.size
  return filteredBoms.value.slice(start, end)
})

// 加载所有BOM
const handleLoadAll = async () => {
  loading.value = true
  try {
    boms.value = await bomApi.getBoms()
    dataLoaded.value = true
    pagination.value.page = 1
  } catch (error: any) {
    ElMessage.error('加载BOM列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 查看详情
const handleViewDetail = async (bom: BillOfMaterial) => {
  try {
    const bomDetail = await bomApi.getBomById(bom.id)
    currentBom.value = bomDetail
    detailDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error('加载BOM详情失败: ' + (error.message || '未知错误'))
  }
}

// 删除BOM
const handleDelete = async (bom: BillOfMaterial) => {
  try {
    ElMessageBox.confirm(
      `确定要删除BOM "${bom.materialCode} (${bom.version})" 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )

    await bomApi.deleteBom(bom.id)
    ElMessage.success('删除成功')
    await handleLoadAll()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

// 搜索
const handleSearch = () => {
  pagination.value.page = 1
}

// 重置搜索
const handleReset = () => {
  searchKeyword.value = ''
  pagination.value.page = 1
}

// 分页变化
const handlePageChange = () => {
  const tableContainer = document.querySelector('.table-container')
  if (tableContainer) {
    tableContainer.scrollTop = 0
  }
}

const handlePageSizeChange = () => {
  pagination.value.page = 1
  handlePageChange()
}

// 导入
const handleImport = () => {
  importDialogVisible.value = true
}

// 导入成功后的处理
const handleImportSuccess = () => {
  handleLoadAll()
}

// 更新成功后的处理
const handleUpdateSuccess = () => {
  handleLoadAll()
}

// 首次加载标志
const isFirstLoad = ref(true)

onMounted(() => {
  if (isFirstLoad.value) {
    handleLoadAll()
    isFirstLoad.value = false
  }
})

// 组件被激活时（从缓存中恢复），不重新加载数据
onActivated(() => {
  // 只在首次加载时执行，后续切换回来不重新加载
})
</script>

<style scoped>
.bom-management-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.bom-management-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.bom-management-container :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.table-container {
  flex: 1;
  overflow: auto;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
