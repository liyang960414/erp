<template>
  <div class="supplier-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">供应商管理</span>
          <div class="header-actions">
            <el-button
              v-if="authStore.hasPermission('supplier:import')"
              type="primary"
              :icon="Upload"
              @click="handleImport"
            >
              导入供应商
            </el-button>
            <el-button type="primary" :icon="Refresh" @click="handleRefresh">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索供应商编码、名称、简称..."
          :prefix-icon="Search"
          clearable
          style="width: 300px"
          @input="handleSearch"
        />
      </div>

      <div class="table-container">
        <el-table
          v-loading="loading"
          :data="paginatedSuppliers"
          stripe
          border
          style="width: 100%"
          empty-text="暂无数据"
        >
          <el-table-column prop="code" label="编码" width="150" />
          <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="shortName" label="简称" width="150" show-overflow-tooltip />
          <el-table-column prop="englishName" label="英文名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="filteredSuppliers.length > 0" class="pagination">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="filteredSuppliers.length"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </el-card>

    <!-- 导入对话框 -->
    <SupplierImportDialog v-model="importDialogVisible" @success="handleImportSuccess" />
  </div>
</template>

<script lang="ts">
export default {
  name: 'SupplierManagement',
}
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Upload, Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { supplierApi } from '@/api/supplier'
import SupplierImportDialog from './components/SupplierImportDialog.vue'
import type { Supplier } from '@/types/supplier'

const authStore = useAuthStore()

const loading = ref(false)
const suppliers = ref<Supplier[]>([])
const searchKeyword = ref('')
const importDialogVisible = ref(false)
const dataLoaded = ref(false)

const pagination = ref({
  page: 1,
  size: 20,
})

// 过滤后的供应商列表（根据搜索关键词）
const filteredSuppliers = computed(() => {
  if (!searchKeyword.value.trim()) {
    return suppliers.value
  }

  const keyword = searchKeyword.value.toLowerCase()
  return suppliers.value.filter((supplier) => {
    return (
      supplier.code.toLowerCase().includes(keyword) ||
      supplier.name.toLowerCase().includes(keyword) ||
      (supplier.shortName && supplier.shortName.toLowerCase().includes(keyword)) ||
      (supplier.englishName && supplier.englishName.toLowerCase().includes(keyword)) ||
      (supplier.description && supplier.description.toLowerCase().includes(keyword))
    )
  })
})

// 分页后的供应商列表
const paginatedSuppliers = computed(() => {
  const start = (pagination.value.page - 1) * pagination.value.size
  const end = start + pagination.value.size
  return filteredSuppliers.value.slice(start, end)
})

// 分页处理
const handlePageSizeChange = (size: number) => {
  pagination.value.size = size
  pagination.value.page = 1
}

const handlePageChange = (page: number) => {
  pagination.value.page = page
}

// 搜索处理
const handleSearch = () => {
  pagination.value.page = 1
}

// 导入处理
const handleImport = () => {
  importDialogVisible.value = true
}

// 导入成功处理
const handleImportSuccess = () => {
  // 重新加载数据
  loadSuppliers()
}

// 刷新处理
const handleRefresh = () => {
  dataLoaded.value = false
  searchKeyword.value = ''
  pagination.value.page = 1
  loadSuppliers()
  ElMessage.success('刷新成功')
}

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

// 加载供应商列表
const loadSuppliers = async () => {
  loading.value = true
  try {
    const response = await supplierApi.getAllSuppliers()
    suppliers.value = response || []
    dataLoaded.value = true
  } catch (error: any) {
    console.error('加载供应商列表失败:', error)
    ElMessage.error('加载供应商列表失败: ' + (error.message || '未知错误'))
    suppliers.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 确保用户信息已加载（包含权限）
  if (!authStore.user && authStore.token) {
    await authStore.fetchUserInfo()
  }
  
  // 调试信息：检查权限
  if (authStore.user) {
    console.log('当前用户:', authStore.user.username)
    console.log('用户角色:', authStore.user.roles)
    console.log('是否有supplier:import权限:', authStore.hasPermission('supplier:import'))
    authStore.user.roles.forEach(role => {
      console.log(`角色 ${role.name} 的权限:`, role.permissions?.map(p => p.name))
    })
  }
  
  if (!dataLoaded.value) {
    loadSuppliers()
  }
})
</script>

<style scoped>
.supplier-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-bar {
  margin-bottom: 20px;
}

.table-container {
  margin-top: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>

