<template>
  <div class="unit-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>单位管理</span>
          <div class="header-actions">
            <el-radio-group v-model="displayMode" size="default" class="display-mode-switch">
              <el-radio-button value="grouped">分组显示</el-radio-button>
              <el-radio-button value="flat">全部显示</el-radio-button>
            </el-radio-group>
            <el-button type="success" @click="handleImport">
              <el-icon><Upload /></el-icon>
              导入Excel
            </el-button>
            <el-button type="primary" @click="handleAddUnit">
              <el-icon><Plus /></el-icon>
              新增单位
            </el-button>
          </div>
        </div>
      </template>

      <!-- 分组显示模式 -->
      <div class="table-container">
        <el-table
          v-if="displayMode === 'grouped'"
          v-loading="loading"
          :data="groupedUnits"
          style="width: 100%"
          border
          row-key="groupCode"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="unit-group-details">
                <el-table :data="row.units" border size="small" max-height="400">
                  <el-table-column prop="code" label="单位编码" width="150" />
                  <el-table-column prop="name" label="单位名称" />
                  <el-table-column label="状态" width="100">
                    <template #default="{ row: unit }">
                      <el-tag :type="unit.enabled ? 'success' : 'danger'" size="small">
                        {{ unit.enabled ? '启用' : '禁用' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row: unit }">
                      <el-button type="primary" size="small" @click="handleEditUnit(unit)">
                        编辑
                      </el-button>
                      <el-button type="danger" size="small" @click="handleDeleteUnit(unit)">
                        删除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="groupName" label="单位组名称" />
          <el-table-column prop="groupCode" label="单位组编码" width="150" />
          <el-table-column label="单位数量" width="120">
            <template #default="{ row }">
              <el-tag type="info">{{ row.units.length }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <!-- 全部显示模式 -->
        <el-table v-else v-loading="loading" :data="units" style="width: 100%" border row-key="id">
          <el-table-column prop="code" label="单位编码" width="150" />
          <el-table-column prop="name" label="单位名称" />
          <el-table-column prop="unitGroup.name" label="单位组名称" width="150" />
          <el-table-column prop="unitGroup.code" label="单位组编码" width="150" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                {{ row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleEditUnit(row)"> 编辑 </el-button>
              <el-button type="danger" size="small" @click="handleDeleteUnit(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 单位表单对话框 -->
    <UnitFormDialog
      v-model="unitDialogVisible"
      :unit="currentUnit"
      :unit-groups="unitGroups"
      @success="loadUnits"
    />

    <!-- Excel导入对话框 -->
    <UnitImportDialog v-model="importDialogVisible" @success="loadUnits" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { unitApi } from '@/api/unit'
import { unitGroupApi } from '@/api/unitGroup'
import type { Unit, UnitGroup } from '@/types/unit'
import UnitFormDialog from './components/UnitFormDialog.vue'
import UnitImportDialog from './components/UnitImportDialog.vue'

interface GroupedUnit {
  groupCode: string
  groupName: string
  units: Unit[]
}

const loading = ref(false)
const units = ref<Unit[]>([])
const unitGroups = ref<UnitGroup[]>([])
const unitDialogVisible = ref(false)
const importDialogVisible = ref(false)
const currentUnit = ref<Unit | null>(null)
const displayMode = ref<'grouped' | 'flat'>('grouped')

const groupedUnits = computed<GroupedUnit[]>(() => {
  const groups = new Map<string, GroupedUnit>()

  units.value.forEach((unit) => {
    const groupCode = unit.unitGroup.code
    if (!groups.has(groupCode)) {
      groups.set(groupCode, {
        groupCode,
        groupName: unit.unitGroup.name,
        units: [],
      })
    }
    groups.get(groupCode)!.units.push(unit)
  })

  return Array.from(groups.values()).sort((a, b) => a.groupCode.localeCompare(b.groupCode))
})

const loadUnits = async () => {
  loading.value = true
  try {
    units.value = await unitApi.getUnits()
  } catch (error: any) {
    ElMessage.error('加载单位列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const loadUnitGroups = async () => {
  try {
    unitGroups.value = await unitGroupApi.getUnitGroups()
  } catch (error: any) {
    ElMessage.error('加载单位组列表失败: ' + (error.message || '未知错误'))
  }
}

const handleAddUnit = () => {
  currentUnit.value = null
  unitDialogVisible.value = true
}

const handleEditUnit = (unit: Unit) => {
  currentUnit.value = unit
  unitDialogVisible.value = true
}

const handleDeleteUnit = async (unit: Unit) => {
  try {
    await ElMessageBox.confirm(`确定要删除单位 "${unit.name}" 吗？`, '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    await unitApi.deleteUnit(unit.id)
    ElMessage.success('删除成功')
    loadUnits()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

const handleImport = () => {
  importDialogVisible.value = true
}

onMounted(() => {
  loadUnits()
  loadUnitGroups()
})
</script>

<style scoped>
.unit-management-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.unit-management-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.unit-management-container :deep(.el-card__body) {
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

.display-mode-switch {
  flex-shrink: 0;
}

.unit-group-details {
  padding: 10px;
}

.table-container {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.card-header {
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

  .header-actions :deep(.el-radio-group) {
    width: 100%;
    display: flex;
  }

  .header-actions :deep(.el-radio-group .el-radio-button) {
    flex: 1;
  }

  .header-actions :deep(.el-radio-group .el-radio-button__inner) {
    width: 100%;
  }
}
</style>
