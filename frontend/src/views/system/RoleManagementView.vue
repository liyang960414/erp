<template>
  <div class="role-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加角色
          </el-button>
        </div>
      </template>

      <!-- 角色表格 -->
      <div class="table-container">
        <el-table
          v-loading="loading"
          :data="roles"
          style="width: 100%"
          border
        >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="角色名称" width="150" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="permissions" label="权限" min-width="300">
          <template #default="{ row }">
            <el-tag
              v-for="perm in row.permissions"
              :key="perm.id"
              style="margin-right: 5px; margin-bottom: 5px"
              size="small"
            >
              {{ perm.name }}
            </el-tag>
            <span v-if="!row.permissions || row.permissions.length === 0" style="color: #999">
              暂无权限
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
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
          @size-change="loadRoles"
          @current-change="loadRoles"
        />
      </div>
    </el-card>

    <!-- 角色表单对话框 -->
    <RoleFormDialog
      v-model="dialogVisible"
      :role="currentRole"
      @success="loadRoles"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { roleApi } from '@/api/role'
import type { Role } from '@/types/role'
import RoleFormDialog from '@/components/RoleFormDialog.vue'

const loading = ref(false)
const roles = ref<Role[]>([])
const dialogVisible = ref(false)
const currentRole = ref<Role | null>(null)

const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

onMounted(() => {
  loadRoles()
})

const loadRoles = async () => {
  loading.value = true
  try {
    const response = await roleApi.getRoles({
      page: pagination.value.page - 1,
      size: pagination.value.size,
      sortBy: 'id',
      sortDir: 'ASC',
    })
    
    roles.value = response.content
    pagination.value.total = response.totalElements
  } catch (error) {
    console.error('加载角色失败:', error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  currentRole.value = null
  dialogVisible.value = true
}

const handleEdit = (role: Role) => {
  currentRole.value = role
  dialogVisible.value = true
}

const handleDelete = async (role: Role) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 "${role.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await roleApi.deleteRole(role.id)
    ElMessage.success('删除成功')
    loadRoles()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}
</script>

<style scoped>
.role-management-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.role-management-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.role-management-container :deep(.el-card__body) {
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
  .role-management-container {
    padding: 0;
  }

  .card-header {
    font-size: 18px;
    padding: 16px 0;
  }
}
</style>
