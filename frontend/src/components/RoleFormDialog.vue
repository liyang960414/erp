<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? $t('role.editRole') : $t('role.addRole')"
    width="700px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item v-if="!isEdit" :label="$t('role.roleName')" prop="name">
        <el-input
          v-model="form.name"
          :placeholder="$t('role.enterRoleName')"
        />
      </el-form-item>

      <el-form-item v-else :label="$t('role.roleName')">
        <el-input v-model="currentRoleName" disabled />
      </el-form-item>

      <el-form-item :label="$t('role.description')" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          :placeholder="$t('role.enterRoleDescription')"
        />
      </el-form-item>

      <el-form-item :label="$t('role.permissions')" prop="permissionNames">
        <el-select
          v-model="form.permissionNames"
          multiple
          :placeholder="$t('role.selectPermissions')"
          style="width: 100%"
        >
          <el-option-group
            v-for="group in groupedPermissions"
            :key="group.module"
            :label="group.module"
          >
            <el-option
              v-for="perm in group.permissions"
              :key="perm.id"
              :label="perm.name"
              :value="perm.name"
            >
              <span>{{ perm.name }}</span>
              <span style="color: #999; font-size: 12px; margin-left: 10px">
                {{ perm.description }}
              </span>
            </el-option>
          </el-option-group>
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { roleApi } from '@/api/role'
import { permissionApi } from '@/api/permission'
import type { Role } from '@/types/role'

const { t } = useI18n()

interface Props {
  modelValue: boolean
  role?: Role | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const allPermissions = ref<any[]>([])

const isEdit = computed(() => !!props.role)
const currentRoleName = ref('')

const form = reactive({
  name: '',
  description: '',
  permissionNames: [] as string[],
})

const rules = computed<FormRules>(() => ({
  name: [
    { required: !isEdit.value, message: t('role.roleNameRequired'), trigger: 'blur' },
    { min: 2, max: 50, message: t('role.roleNameLength'), trigger: 'blur' },
  ],
}))

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// 按模块分组权限
const groupedPermissions = computed(() => {
  const groups = new Map<string, any[]>()
  
  allPermissions.value.forEach(perm => {
    const module = perm.name.split(':')[0] || t('role.other')
    if (!groups.has(module)) {
      groups.set(module, [])
    }
    groups.get(module)!.push(perm)
  })
  
  return Array.from(groups.entries()).map(([module, permissions]) => ({
    module,
    permissions,
  }))
})

watch(() => props.role, (role) => {
  if (role) {
    currentRoleName.value = role.name
    form.description = role.description || ''
    form.permissionNames = role.permissions.map(p => p.name)
  } else {
    resetForm()
  }
}, { immediate: true })

onMounted(() => {
  loadPermissions()
})

const loadPermissions = async () => {
  try {
    allPermissions.value = await permissionApi.getAllPermissions()
  } catch (error) {
    console.error('加载权限失败:', error)
  }
}

const resetForm = () => {
  form.name = ''
  form.description = ''
  form.permissionNames = []
}

const handleClose = () => {
  visible.value = false
  formRef.value?.resetFields()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        if (isEdit.value && props.role) {
          await roleApi.updateRole(props.role.id, {
            description: form.description,
            permissionNames: form.permissionNames,
          })
          ElMessage.success(t('role.updateSuccess'))
        } else {
          await roleApi.createRole(form)
          ElMessage.success(t('role.createSuccess'))
        }
        
        emit('success')
        handleClose()
      } catch (error) {
        console.error('保存失败:', error)
      } finally {
        saving.value = false
      }
    }
  })
}
</script>

<style scoped>
:deep(.el-select-dropdown__item) {
  height: auto;
  padding: 8px 20px;
}
</style>

