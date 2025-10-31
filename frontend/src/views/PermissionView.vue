<template>
  <div class="permission-container">
    <el-card>
      <template #header>
        <span>权限管理</span>
      </template>

      <!-- 权限表格 -->
      <el-table
        v-loading="loading"
        :data="permissions"
        style="width: 100%"
        border
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="权限名称" width="200" />
        <el-table-column prop="description" label="描述" />
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadPermissions"
          @current-change="loadPermissions"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { permissionApi } from '@/api/permission'
import type { Permission } from '@/api/permission'

const loading = ref(false)
const permissions = ref<Permission[]>([])

const pagination = ref({
  page: 1,
  size: 20,
  total: 0,
})

onMounted(() => {
  loadPermissions()
})

const loadPermissions = async () => {
  loading.value = true
  try {
    const response = await permissionApi.getPermissions({
      page: pagination.value.page - 1,
      size: pagination.value.size,
      sortBy: 'id',
      sortDir: 'ASC',
    })
    
    permissions.value = response.content
    pagination.value.total = response.totalElements
  } catch (error) {
    console.error('加载权限失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.permission-container {
  max-width: 1400px;
  margin: 0 auto;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .permission-container {
    padding: 0;
  }
}
</style>
