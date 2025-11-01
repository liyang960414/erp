<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? $t('user.editUser') : $t('user.addUser')"
    width="600px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item :label="$t('user.username')" prop="username">
        <el-input
          v-model="form.username"
          :disabled="isEdit"
          :placeholder="$t('user.enterUsername')"
        />
      </el-form-item>

      <el-form-item v-if="!isEdit" :label="$t('user.password')" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          :placeholder="$t('user.enterPassword')"
          show-password
        />
      </el-form-item>

      <el-form-item :label="$t('user.email')" prop="email">
        <el-input v-model="form.email" :placeholder="$t('user.enterEmail')" />
      </el-form-item>

      <el-form-item :label="$t('user.fullName')" prop="fullName">
        <el-input v-model="form.fullName" :placeholder="$t('user.enterFullName')" />
      </el-form-item>

      <el-form-item :label="$t('user.status')" prop="enabled">
        <el-switch v-model="form.enabled" />
      </el-form-item>

      <el-form-item :label="$t('user.roles')" prop="roleNames">
        <el-select
          v-model="form.roleNames"
          multiple
          :placeholder="$t('user.selectRoles')"
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

