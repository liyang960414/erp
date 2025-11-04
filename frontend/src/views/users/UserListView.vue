<template>
  <div class="user-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ $t('user.userManagement') }}</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            {{ $t('user.addUser') }}
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar" style="margin-bottom: 20px">
        <el-input
          v-model="searchKeyword"
          :placeholder="$t('user.searchPlaceholder')"
          clearable
          style="width: 300px"
          @clear="loadUsers"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          {{ $t('common.search') }}
        </el-button>
      </div>

      <!-- 用户表格 -->
      <div class="table-container">
        <el-table v-loading="loading" :data="users" style="width: 100%" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column :label="$t('user.username')" prop="username" width="150" />
          <el-table-column :label="$t('user.email')" prop="email" width="200" />
          <el-table-column :label="$t('user.fullName')" prop="fullName" />
          <el-table-column :label="$t('user.roles')" prop="roles" width="150">
            <template #default="{ row }">
              <el-tag
                v-for="role in row.roles"
                :key="role.id"
                style="margin-right: 5px"
                type="primary"
                size="small"
              >
                {{ role.name }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('user.status')" prop="enabled" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                {{ row.enabled ? $t('user.enable') : $t('user.disable') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" width="250" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleEdit(row)">
                {{ $t('common.edit') }}
              </el-button>
              <el-button type="warning" size="small" @click="handleToggleStatus(row)">
                {{ row.enabled ? $t('user.disable') : $t('user.enable') }}
              </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)">
                {{ $t('common.delete') }}
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
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 用户表单对话框 -->
    <UserFormDialog v-model="dialogVisible" :user="currentUser" @success="loadUsers" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import type { User } from '@/types/user'
import UserFormDialog from './components/UserFormDialog.vue'

const { t } = useI18n()

const loading = ref(false)
const users = ref<User[]>([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const currentUser = ref<User | null>(null)

const pagination = ref({
  page: 1,
  size: 10,
  total: 0,
})

onMounted(() => {
  loadUsers()
})

const loadUsers = async () => {
  loading.value = true
  try {
    const response = await userApi.getUsers({
      page: pagination.value.page - 1,
      size: pagination.value.size,
      sortBy: 'id',
      sortDir: 'DESC',
    })

    users.value = response.content
    pagination.value.total = response.totalElements
  } catch (error) {
    console.error('加载用户失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.value.page = 1
  loadUsers()
}

const handleAdd = () => {
  currentUser.value = null
  dialogVisible.value = true
}

const handleEdit = (user: User) => {
  currentUser.value = user
  dialogVisible.value = true
}

const handleToggleStatus = async (user: User) => {
  try {
    await userApi.updateUser(user.id, { enabled: !user.enabled })
    ElMessage.success(t('user.statusUpdateSuccess'))
    loadUsers()
  } catch (error) {
    console.error('更新状态失败:', error)
  }
}

const handleDelete = async (user: User) => {
  try {
    await ElMessageBox.confirm(
      t('user.deleteConfirmWithName', { name: user.username }),
      t('user.deleteConfirmTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      },
    )

    await userApi.deleteUser(user.id)
    ElMessage.success(t('user.deleteSuccess'))
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}
</script>

<style scoped>
.user-list-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.user-list-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.user-list-container :deep(.el-card__body) {
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
  display: flex;
  gap: 12px;
  align-items: center;
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
  .user-list-container {
    padding: 0;
  }

  .search-bar {
    gap: 16px;
  }

  .card-header {
    font-size: 18px;
    padding: 16px 0;
  }
}
</style>
