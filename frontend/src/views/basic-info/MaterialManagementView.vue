<template>
  <div class="material-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>物料管理</span>
          <div class="header-actions">
            <el-button type="primary" :disabled="displayMode === 'flat'" @click="handleLoadAll">
              <el-icon><List /></el-icon>
              全部
            </el-button>
            <el-button
              type="primary"
              :disabled="displayMode === 'grouped'"
              @click="handleLoadGrouped"
            >
              <el-icon><Folder /></el-icon>
              分组显示
            </el-button>
            <el-button v-if="authStore.hasRole('ADMIN')" type="success" @click="handleImport">
              <el-icon><Upload /></el-icon>
              导入Excel
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div v-if="displayMode === 'flat' && materials.length > 0" class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索物料编码、名称、规格、属性、物料组、单位..."
          clearable
          style="width: 400px"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 全部显示模式 -->
      <div v-if="displayMode === 'flat'" class="table-container">
        <el-empty
          v-if="!dataLoaded && materials.length === 0"
          description="请点击「全部」按钮加载数据"
        />
        <el-table
          v-else
          v-loading="loading"
          :data="paginatedMaterials"
          style="width: 100%"
          border
          row-key="id"
        >
          <el-table-column prop="code" label="物料编码" width="150" />
          <el-table-column prop="name" label="物料名称" min-width="200" />
          <el-table-column
            prop="specification"
            label="规格"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="物料属性" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.erpClsId" size="small" :type="getErpClsIdTagType(row.erpClsId)">
                {{ row.erpClsId }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="materialGroupName" label="物料组" width="150" />
          <el-table-column prop="baseUnitName" label="基础单位" width="120" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleViewDetail(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="filteredMaterials.length > 0" class="pagination">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="filteredMaterials.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <!-- 分组显示模式 -->
      <div v-if="displayMode === 'grouped'" class="table-container">
        <el-empty
          v-if="!dataLoaded && materialGroups.length === 0"
          description="请点击「分组显示」按钮加载物料组列表"
        />
        <el-table
          v-else
          v-loading="loading"
          :data="materialGroups"
          style="width: 100%"
          border
          row-key="id"
          @expand-change="handleTableExpand"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="material-group-details">
                <div v-if="loadingMaterials[row.id]" class="loading-wrapper">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>加载中...</span>
                </div>
                <el-table
                  v-else-if="groupMaterialsMap[row.id] && groupMaterialsMap[row.id].length > 0"
                  :data="groupMaterialsMap[row.id]"
                  border
                  size="small"
                  max-height="400"
                >
                  <el-table-column prop="code" label="物料编码" width="150" />
                  <el-table-column prop="name" label="物料名称" min-width="200" />
                  <el-table-column
                    prop="specification"
                    label="规格"
                    min-width="150"
                    show-overflow-tooltip
                  />
                  <el-table-column label="物料属性" width="100">
                    <template #default="{ row }">
                      <el-tag
                        v-if="row.erpClsId"
                        size="small"
                        :type="getErpClsIdTagType(row.erpClsId)"
                      >
                        {{ row.erpClsId }}
                      </el-tag>
                      <span v-else>-</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="baseUnitName" label="基础单位" width="120" />
                  <el-table-column label="操作" width="150" fixed="right">
                    <template #default="{ row: material }">
                      <el-button type="primary" size="small" @click="handleViewDetail(material)">
                        查看详情
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="该物料组暂无物料" />
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="物料组名称" />
          <el-table-column prop="code" label="物料组编码" width="150" />
          <el-table-column label="物料数量" width="120">
            <template #default="{ row }">
              <el-tag type="info">{{ groupMaterialsMap[row.id]?.length || 0 }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 物料详情对话框 -->
    <MaterialDetailDialog v-model="detailDialogVisible" :material="currentMaterial" />

    <!-- Excel导入对话框 -->
    <MaterialImportDialog v-model="importDialogVisible" @success="handleImportSuccess" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { List, Folder, Upload, Search, Loading } from '@element-plus/icons-vue'
import { materialApi } from '@/api/material'
import { materialGroupApi } from '@/api/materialGroup'
import { useAuthStore } from '@/stores/auth'
import type { Material } from '@/types/material'
import type { MaterialGroup } from '@/types/materialGroup'
import MaterialDetailDialog from './components/MaterialDetailDialog.vue'
import MaterialImportDialog from './components/MaterialImportDialog.vue'

const authStore = useAuthStore()

const loading = ref(false)
const materials = ref<Material[]>([])
const materialGroups = ref<MaterialGroup[]>([])
const groupMaterialsMap = ref<Record<number, Material[]>>({})
const loadingMaterials = ref<Record<number, boolean>>({})
const displayMode = ref<'flat' | 'grouped' | null>(null)
const dataLoaded = ref(false)
const searchKeyword = ref('')
const detailDialogVisible = ref(false)
const importDialogVisible = ref(false)
const currentMaterial = ref<Material | null>(null)

const pagination = ref({
  page: 1,
  size: 10,
})

// 过滤后的物料列表（根据搜索关键词）
const filteredMaterials = computed(() => {
  if (!searchKeyword.value.trim()) {
    return materials.value
  }

  const keyword = searchKeyword.value.toLowerCase()
  return materials.value.filter((material) => {
    return (
      material.code.toLowerCase().includes(keyword) ||
      material.name.toLowerCase().includes(keyword) ||
      (material.specification && material.specification.toLowerCase().includes(keyword)) ||
      (material.erpClsId && material.erpClsId.toLowerCase().includes(keyword)) ||
      material.materialGroupName.toLowerCase().includes(keyword) ||
      material.baseUnitName.toLowerCase().includes(keyword)
    )
  })
})

// 分页后的物料列表
const paginatedMaterials = computed(() => {
  const start = (pagination.value.page - 1) * pagination.value.size
  const end = start + pagination.value.size
  return filteredMaterials.value.slice(start, end)
})

// 加载所有物料
const handleLoadAll = async () => {
  if (displayMode.value === 'flat' && dataLoaded.value) {
    return
  }

  displayMode.value = 'flat'
  loading.value = true
  try {
    materials.value = await materialApi.getMaterials()
    dataLoaded.value = true
    pagination.value.page = 1
  } catch (error: any) {
    ElMessage.error('加载物料列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 加载物料组列表
const handleLoadGrouped = async () => {
  if (displayMode.value === 'grouped' && materialGroups.value.length > 0) {
    return
  }

  displayMode.value = 'grouped'
  loading.value = true
  try {
    materialGroups.value = await materialGroupApi.getMaterialGroups()
    dataLoaded.value = true
    groupMaterialsMap.value = {}
  } catch (error: any) {
    ElMessage.error('加载物料组列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 查看详情
const handleViewDetail = async (material: Material) => {
  try {
    // 如果物料信息不完整，重新获取
    const materialDetail = await materialApi.getMaterialById(material.id)
    currentMaterial.value = materialDetail
    detailDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error('加载物料详情失败: ' + (error.message || '未知错误'))
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
  // 分页变化时自动滚动到顶部
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
  // 如果当前是全部显示模式，重新加载数据
  if (displayMode.value === 'flat') {
    handleLoadAll()
  } else if (displayMode.value === 'grouped') {
    // 如果是分组显示模式，清空已加载的物料组数据，需要用户重新展开
    groupMaterialsMap.value = {}
  }
}

// 获取物料属性的标签类型
const getErpClsIdTagType = (erpClsId: string): string => {
  const typeMap: Record<string, string> = {
    费用: 'warning',
    外购: 'info',
    委外: 'success',
    虚拟: 'danger',
    资产: 'primary',
    自制: 'success',
  }
  return typeMap[erpClsId] || 'info'
}

// 处理表格展开
const handleTableExpand = (row: MaterialGroup, expandedRows: MaterialGroup[]) => {
  // 如果展开且该物料组的数据尚未加载，则加载数据
  if (
    expandedRows.includes(row) &&
    !groupMaterialsMap.value[row.id] &&
    !loadingMaterials.value[row.id]
  ) {
    loadingMaterials.value[row.id] = true
    materialApi
      .getMaterialsByGroupId(row.id)
      .then((materials) => {
        groupMaterialsMap.value[row.id] = materials
      })
      .catch((error: any) => {
        ElMessage.error(`加载物料组"${row.name}"的物料失败: ${error.message || '未知错误'}`)
        groupMaterialsMap.value[row.id] = []
      })
      .finally(() => {
        loadingMaterials.value[row.id] = false
      })
  }
}
</script>

<style scoped>
.material-management-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.material-management-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.material-management-container :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.card-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.search-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.material-group-details {
  padding: 10px;
}

.loading-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  color: #909399;
}

.loading-wrapper .is-loading {
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.table-container {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-bar :deep(.el-input) {
    width: 100% !important;
  }
}
</style>
