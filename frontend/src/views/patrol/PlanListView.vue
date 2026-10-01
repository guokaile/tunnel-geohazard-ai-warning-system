<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">巡检计划列表</span>
        <div class="toolbar-actions">
          <el-select
            v-model="filterTunnelId"
            placeholder="按隧道筛选"
            clearable
            filterable
            style="width: 220px"
            @change="loadPlans"
          >
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建计划</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="plans" stripe>
        <el-table-column prop="planNo" label="计划编号" min-width="140" />
        <el-table-column prop="planName" label="计划名称" min-width="140" />
        <el-table-column label="隧道" min-width="150">
          <template #default="{ row }">{{ tunnelName(row.tunnelId) }}</template>
        </el-table-column>
        <el-table-column label="频次" width="90">
          <template #default="{ row }">{{ FREQ_META[row.frequencyType] ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="timeSlot" label="时间点/班次" width="120" />
        <el-table-column label="巡检模板" min-width="130">
          <template #default="{ row }">{{ templateName(row.templateId) }}</template>
        </el-table-column>
        <el-table-column label="巡检人" min-width="110">
          <template #default="{ row }">{{ userName(row.inspectorId) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removePlan(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建 / 编辑计划（API-D02） -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建巡检计划' : '编辑巡检计划'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="form.planName" placeholder="如 隧道洞身日巡检" />
        </el-form-item>
        <el-form-item label="隧道" prop="tunnelId">
          <el-select v-model="form.tunnelId" placeholder="选择隧道" filterable style="width: 100%">
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="频次" prop="frequencyType">
          <el-radio-group v-model="form.frequencyType">
            <el-radio-button :value="1">每班</el-radio-button>
            <el-radio-button :value="2">每日</el-radio-button>
            <el-radio-button :value="3">每周</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="时间点" prop="timeSlot">
          <el-input v-model="form.timeSlot" placeholder="如 08:00 / 夜班" />
        </el-form-item>
        <el-form-item label="巡检模板" prop="templateId">
          <el-select v-model="form.templateId" placeholder="选择模板" filterable style="width: 100%">
            <el-option
              v-for="t in templates"
              :key="t.id"
              :label="`${t.templateName}（v${t.version}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检人" prop="inspectorId">
          <el-select v-model="form.inspectorId" placeholder="选择巡检人" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'edit'" label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import {
  createPlanApi,
  deletePlanApi,
  getPlanListApi,
  getTemplateListApi,
  updatePlanApi,
} from '@/api/patrol'
import { getTunnelListApi } from '@/api/mon'
import { getUserPageApi } from '@/api/sys'
import type { PatrolPlanVO, PatrolTemplateVO } from '@/types/patrol'
import type { TunnelVO } from '@/types/mon'
import type { SysUserVO } from '@/types/api'
import { FREQ_META } from './patrolMeta'

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const plans = ref<PatrolPlanVO[]>([])
const tunnels = ref<TunnelVO[]>([])
const templates = ref<PatrolTemplateVO[]>([])
const users = ref<SysUserVO[]>([])
const filterTunnelId = ref<number>()

function tunnelName(id: number): string {
  const t = tunnels.value.find((x) => x.id === id)
  return t ? `${t.tunnelCode} ${t.tunnelName}` : `#${id}`
}

function templateName(id: number): string {
  const t = templates.value.find((x) => x.id === id)
  return t ? t.templateName : `#${id}`
}

function userName(id: number): string {
  const u = users.value.find((x) => x.id === id)
  return u ? u.realName || u.username : `#${id}`
}

async function loadOptions() {
  const [t, tp, u] = await Promise.all([
    getTunnelListApi(),
    getTemplateListApi(),
    getUserPageApi({ pageNum: 1, pageSize: 100 }),
  ])
  tunnels.value = t
  templates.value = tp
  users.value = u.list
}

async function loadPlans() {
  loading.value = true
  try {
    plans.value = await getPlanListApi(filterTunnelId.value)
  } finally {
    loading.value = false
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number>()

interface PlanForm {
  planName: string
  tunnelId?: number
  frequencyType: number
  timeSlot: string
  templateId?: number
  inspectorId?: number
  enabled: number
}

const emptyForm = (): PlanForm => ({
  planName: '',
  tunnelId: undefined,
  frequencyType: 2,
  timeSlot: '08:00',
  templateId: undefined,
  inspectorId: undefined,
  enabled: 1,
})

const form = reactive<PlanForm>(emptyForm())

const formRules: FormRules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  tunnelId: [{ required: true, message: '请选择隧道', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请输入时间点/班次', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择巡检模板', trigger: 'change' }],
  inspectorId: [{ required: true, message: '请选择巡检人', trigger: 'change' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = undefined
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row: PatrolPlanVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    planName: row.planName,
    tunnelId: row.tunnelId,
    frequencyType: row.frequencyType,
    timeSlot: row.timeSlot,
    templateId: row.templateId,
    inspectorId: row.inspectorId,
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createPlanApi({ ...form })
      ElMessage.success('创建成功')
    } else {
      await updatePlanApi(editingId.value as number, { ...form })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadPlans()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

async function removePlan(row: PatrolPlanVO) {
  try {
    await ElMessageBox.confirm(`确定删除巡检计划「${row.planName}」吗？`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  await deletePlanApi(row.id)
  ElMessage.success('删除成功')
  await loadPlans()
}

onMounted(async () => {
  await loadOptions()
  await loadPlans()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
