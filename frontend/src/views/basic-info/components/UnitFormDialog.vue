<template>
  <el-dialog
    v-model="dialogVisible"
    :title="unit ? '编辑单位' : '新增单位'"
    width="600px"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <!-- 编辑模式下显示只读信息 -->
      <template v-if="unit">
        <el-form-item label="单位编码">
          <el-input v-model="form.code" disabled placeholder="单位编码" />
        </el-form-item>

        <el-form-item label="单位组">
          <el-input :value="unit.unitGroup.name" disabled placeholder="单位组" />
        </el-form-item>
      </template>

      <!-- 新增模式下显示可编辑字段 -->
      <template v-else>
        <el-form-item label="单位编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入单位编码" />
        </el-form-item>

        <el-form-item label="单位组" prop="unitGroupId">
          <el-select v-model="form.unitGroupId" placeholder="请选择单位组" style="width: 100%">
            <el-option
              v-for="group in unitGroups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
      </template>

      <el-form-item label="单位名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入单位名称" />
      </el-form-item>

      <!-- 转换率编辑（仅编辑模式） -->
      <template v-if="unit">
        <el-form-item label="转换率" prop="conversionRate">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%">
            <el-input-number
              v-model="form.conversionNumerator"
              :precision="6"
              :min="0"
              :step="1"
              style="width: 100%"
              placeholder="分子"
            />
            <span style="color: #909399">:</span>
            <el-input-number
              v-model="form.conversionDenominator"
              :precision="6"
              :min="0.000001"
              :step="1"
              style="width: 100%"
              placeholder="分母"
            />
          </div>
          <div style="font-size: 12px; color: #909399; margin-top: 4px">
            相对于基准单位的转换率（基准单位的转换率为1:1）
          </div>
        </el-form-item>
      </template>

      <!-- 编辑模式下隐藏状态开关 -->
      <template v-if="!unit">
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting"> 确定 </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { unitApi } from '@/api/unit.ts'
import type { Unit, UnitGroup, CreateUnitRequest, UpdateUnitRequest } from '@/types/unit.ts'

interface Props {
  modelValue: boolean
  unit?: Unit | null
  unitGroups: UnitGroup[]
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<CreateUnitRequest & UpdateUnitRequest>({
  code: '',
  name: '',
  unitGroupId: 0,
  enabled: true,
  conversionNumerator: undefined,
  conversionDenominator: undefined,
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入单位编码', trigger: 'blur' },
    { max: 50, message: '单位编码长度不能超过50个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入单位名称', trigger: 'blur' },
    { max: 100, message: '单位名称长度不能超过100个字符', trigger: 'blur' },
  ],
  unitGroupId: [{ required: true, message: '请选择单位组', trigger: 'change' }],
}

const resetForm = () => {
  form.code = ''
  form.name = ''
  form.unitGroupId = 0
  form.enabled = true
  form.conversionNumerator = undefined
  form.conversionDenominator = undefined
  formRef.value?.resetFields()
}

watch(
  () => props.unit,
  (newUnit) => {
    if (newUnit) {
      form.code = newUnit.code
      form.name = newUnit.name
      form.unitGroupId = newUnit.unitGroup.id
      form.enabled = newUnit.enabled
      // 设置转换率
      form.conversionNumerator = newUnit.conversionNumerator ?? undefined
      form.conversionDenominator = newUnit.conversionDenominator ?? undefined
    } else {
      resetForm()
    }
  },
  { immediate: true },
)

const handleClose = () => {
  dialogVisible.value = false
  resetForm()
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (props.unit) {
        // 更新：只能编辑名称和转换率
        const updateData: UpdateUnitRequest = {
          name: form.name,
        }

        // 如果有设置转换率则传递
        if (form.conversionNumerator !== undefined && form.conversionDenominator !== undefined) {
          if (form.conversionDenominator <= 0) {
            ElMessage.error('转换率分母必须大于0')
            return
          }
          updateData.conversionNumerator = Number(form.conversionNumerator)
          updateData.conversionDenominator = Number(form.conversionDenominator)
        }

        await unitApi.updateUnit(props.unit.id, updateData)
        ElMessage.success('更新成功')
      } else {
        // 创建
        const createData: CreateUnitRequest = {
          code: form.code,
          name: form.name,
          unitGroupId: form.unitGroupId,
          enabled: form.enabled,
        }
        await unitApi.createUnit(createData)
        ElMessage.success('创建成功')
      }
      emit('success')
      handleClose()
    } catch (error: any) {
      ElMessage.error((props.unit ? '更新' : '创建') + '失败: ' + (error.message || '未知错误'))
    } finally {
      submitting.value = false
    }
  })
}
</script>
