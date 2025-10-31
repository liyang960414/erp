<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑用户' : '添加用户'"
    width="600px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :disabled="isEdit"
          placeholder="请输入用户名"
        />
      </el-form-item>

      <el-form-item v-if="!isEdit" label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入密码"
          show-password
        />
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="请输入邮箱" />
      </el-form-item>

      <el-form-item label="全名" prop="fullName">
        <el-input v-model="form.fullName" placeholder="请输入全名" />
      </el-form-item>

      <el-form-item label="状态" prop="enabled">
        <el-switch v-model="form.enabled" />
      </el-form-item>

      <el-form-item label="角色" prop="roleNames">
        <el-select
          v-model="form.roleNames"
          multiple
          placeholder="请选择角色"
          style="width: 100%"
        >
          <el-option
            v-for="role in allRoles"
            :key="role.id"
            :label="role.name"
            :value="role.name"
          >
            <span>{{ role.name }}</span>
            <span style="color: #999; font-size: 12px; margin-left: 10px">
              {{ role.description }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/role'
import type { User } from '@/types/user'
import type { Role } from '@/types/role'

const { t } = useI18n()

interface Props {
  modelValue: boolean
  user?: User | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const allRoles = ref<Role[]>([])

const isEdit = computed(() => !!props.user)

const form = reactive({
  username: '',
  password: '',
  email: '',
  fullName: '',
  enabled: true,
  roleNames: [] as string[],
})

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: t('user.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: t('user.usernameLength'), trigger: 'blur' },
  ],
  password: [
    { required: !isEdit.value, message: t('user.passwordRequired'), trigger: 'blur' },
    { min: 6, message: t('user.passwordMinLength'), trigger: 'blur' },
  ],
  email: [
    { required: true, message: t('user.enterEmail'), trigger: 'blur' },
    { type: 'email', message: t('user.emailInvalid'), trigger: 'blur' },
  ],
}))

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

watch(() => props.user, (user) => {
  if (user) {
    form.username = user.username
    form.email = user.email
    form.fullName = user.fullName || ''
    form.enabled = user.enabled
    form.roleNames = user.roles.map(r => r.name)
  } else {
    resetForm()
  }
}, { immediate: true })

onMounted(() => {
  loadRoles()
})

const loadRoles = async () => {
  try {
    allRoles.value = await roleApi.getAllRoles()
  } catch (error) {
    console.error('加载角色失败:', error)
  }
}

const resetForm = () => {
  form.username = ''
  form.password = ''
  form.email = ''
  form.fullName = ''
  form.enabled = true
  form.roleNames = []
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
        if (isEdit.value && props.user) {
          await userApi.updateUser(props.user.id, {
            email: form.email,
            fullName: form.fullName,
            enabled: form.enabled,
            roleNames: form.roleNames,
          })
          ElMessage.success(t('user.updateSuccess'))
        } else {
          await userApi.createUser(form)
          ElMessage.success(t('user.createSuccess'))
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

