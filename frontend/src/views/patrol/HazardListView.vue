<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">隐患管理列表</span>
        <div class="toolbar-actions">
          <el-select v-model="filterTunnelId" placeholder="按隧道筛选" clearable filterable style="width: 200px" @change="loadPage(1)">
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
          <el-select v-model="filterStatus" placeholder="按状态筛选" clearable style="width: 130px" @change="loadPage(1)">
            <el-option
              v-for="(meta, code) in HAZARD_STATUS_META"
              :key="code"
              :label="meta.label"
              :value="Number(code)"
            />
          </el-select>
          <el-select v-model="filterLevel" placeholder="按等级筛选" clearable style="width: 130px" @change="loadPage(1)">
            <el-option
              v-for="(meta, code) in WARN_LEVEL_META"
              :key="code"
              :label="`${meta.label}级`"
              :value="Number(code)"
            />
          </el-select>
          <el-button type="primary" :icon="Plus" @click="openRegister">隐患登记</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="hazards" stripe>
        <el-table-column prop="hazardNo" label="隐患编号" min-width="130" />
        <el-table-column prop="title" label="隐患标题" min-width="170" show-overflow-tooltip />
        <el-table-column label="隧道" min-width="120">
          <template #default="{ row }">{{ tunnelName(row.tunnelId) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ HAZARD_SOURCE_META[row.source] ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="等级" width="90">
          <template #default="{ row }">
            <el-tag :color="hazardLevelColor(row.hazardLevel)" size="small" effect="dark">
              {{ hazardLevelLabel(row.hazardLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="HAZARD_STATUS_META[row.status]?.tag ?? 'info'" size="small">
              {{ HAZARD_STATUS_META[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.discoverTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1 || row.status === 2" link type="primary" @click="openAssign(row)">
              转处置
            </el-button>
            <el-button v-if="row.status === 1 || row.status === 2" link type="success" @click="openClose(row)">
              闭环
            </el-button>
            <el-button v-if="row.status !== 3" link type="warning" @click="openConvert(row)">
              转灾害
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadPage()"
          @size-change="loadPage(1)"
        />
      </div>
    </el-card>

    <!-- 隐患登记（API-D12） -->
    <el-dialog v-model="registerVisible" title="隐患登记" width="560px">
      <el-form ref="registerRef" :model="registerForm" :rules="registerRules" label-width="90px">
        <el-form-item label="隧道" prop="tunnelId">
          <el-select v-model="registerForm.tunnelId" placeholder="选择隧道" filterable style="width: 100%">
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="断面">
          <el-select v-model="registerForm.sectionId" placeholder="选择断面（可空）" clearable filterable style="width: 100%">
            <el-option
              v-for="s in sections"
              :key="s.id"
              :label="`${s.sectionCode} ${s.sectionName}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-radio-group v-model="registerForm.source">
            <el-radio-button :value="1">巡检发现</el-radio-button>
            <el-radio-button :value="2">人工上报</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="隐患标题" prop="title">
          <el-input v-model="registerForm.title" placeholder="如 洞身段渗水点扩大" />
        </el-form-item>
        <el-form-item label="等级" prop="hazardLevel">
          <el-select v-model="registerForm.hazardLevel" style="width: 100%">
            <el-option
              v-for="(meta, code) in WARN_LEVEL_META"
              :key="code"
              :label="`${meta.label}级`"
              :value="Number(code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="灾种">
          <el-select v-model="registerForm.hazardType" placeholder="选择灾种（可空）" clearable style="width: 100%">
            <el-option
              v-for="(label, code) in HAZARD_TYPE_META"
              :key="code"
              :label="label"
              :value="Number(code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="registerForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRegister">提交</el-button>
      </template>
    </el-dialog>

    <!-- 转处置（API-D13） -->
    <el-dialog v-model="assignVisible" title="隐患转处置" width="420px">
      <el-form label-width="90px">
        <el-form-item label="处置人" required>
          <el-select v-model="assignHandlerId" placeholder="选择处置人" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 闭环（API-D14） -->
    <el-dialog v-model="closeVisible" title="隐患闭环" width="420px">
      <el-form label-width="90px">
        <el-form-item label="闭环说明" required>
          <el-input v-model="closeRemark" type="textarea" :rows="3" placeholder="闭环说明必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClose">确定</el-button>
      </template>
    </el-dialog>

    <!-- 转灾害登记（评审 3.5） -->
    <el-dialog v-model="convertVisible" title="隐患转灾害登记" width="560px">
      <el-form ref="convertRef" :model="convertForm" :rules="convertRules" label-width="90px">
        <el-form-item label="隧道" prop="tunnelId">
          <el-select v-model="convertForm.tunnelId" placeholder="选择隧道" filterable style="width: 100%">
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="断面">
          <el-select v-model="convertForm.sectionId" placeholder="选择断面（可空）" clearable filterable style="width: 100%">
            <el-option
              v-for="s in sections"
              :key="s.id"
              :label="`${s.sectionCode} ${s.sectionName}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="灾种" prop="hazardType">
          <el-select v-model="convertForm.hazardType" style="width: 100%">
            <el-option
              v-for="(label, code) in HAZARD_TYPE_META"
              :key="code"
              :label="label"
              :value="Number(code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="事件时间" prop="eventTime">
          <el-date-picker
            v-model="convertForm.eventTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-select v-model="convertForm.level" style="width: 100%">
            <el-option
              v-for="(meta, code) in WARN_LEVEL_META"
              :key="code"
              :label="`${meta.label}级`"
              :value="Number(code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="灾害描述" prop="consequence">
          <el-input v-model="convertForm.consequence" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConvert">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import {
  assignHazardApi,
  closeHazardApi,
  convertHazardToDisasterApi,
  getHazardPageApi,
  registerHazardApi,
} from '@/api/patrol'
import { getSectionListApi, getTunnelListApi } from '@/api/mon'
import { getUserPageApi } from '@/api/sys'
import type { PatrolHazardVO } from '@/types/patrol'
import type { SectionVO, TunnelVO } from '@/types/mon'
import type { SysUserVO } from '@/types/api'
import { HAZARD_TYPE_META, WARN_LEVEL_META } from '@/views/warn/warnMeta'
import {
  HAZARD_SOURCE_META,
  HAZARD_STATUS_META,
  hazardLevelColor,
  hazardLevelLabel,
} from './patrolMeta'

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const hazards = ref<PatrolHazardVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterTunnelId = ref<number>()
const filterStatus = ref<number>()
const filterLevel = ref<number>()
const tunnels = ref<TunnelVO[]>([])
const sections = ref<SectionVO[]>([])
const users = ref<SysUserVO[]>([])

function tunnelName(id: number): string {
  const t = tunnels.value.find((x) => x.id === id)
  return t ? `${t.tunnelCode} ${t.tunnelName}` : `#${id}`
}

function formatDateTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 19)
}

async function loadOptions() {
  const [t, s, u] = await Promise.all([
    getTunnelListApi(),
    getSectionListApi(),
    getUserPageApi({ pageNum: 1, pageSize: 100 }),
  ])
  tunnels.value = t
  sections.value = s
  users.value = u.list
}

async function loadPage(page?: number) {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const r = await getHazardPageApi({
      tunnelId: filterTunnelId.value,
      status: filterStatus.value,
      level: filterLevel.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    hazards.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

/* ---------------- 登记 ---------------- */
const registerVisible = ref(false)
const submitting = ref(false)
const registerRef = ref<FormInstance>()

interface RegisterForm {
  tunnelId?: number
  sectionId?: number
  source: number
  title: string
  hazardLevel: number
  hazardType?: number
  description: string
}

const emptyRegister = (): RegisterForm => ({
  tunnelId: undefined,
  sectionId: undefined,
  source: 1,
  title: '',
  hazardLevel: 1,
  hazardType: undefined,
  description: '',
})

const registerForm = reactive<RegisterForm>(emptyRegister())

const registerRules: FormRules = {
  tunnelId: [{ required: true, message: '请选择隧道', trigger: 'change' }],
  title: [{ required: true, message: '请输入隐患标题', trigger: 'blur' }],
}

function openRegister() {
  Object.assign(registerForm, emptyRegister())
  registerVisible.value = true
}

async function submitRegister() {
  const valid = await registerRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await registerHazardApi({
      tunnelId: registerForm.tunnelId,
      sectionId: registerForm.sectionId ?? null,
      source: registerForm.source,
      title: registerForm.title,
      description: registerForm.description || null,
      hazardLevel: registerForm.hazardLevel,
      hazardType: registerForm.hazardType ?? null,
    })
    ElMessage.success('隐患登记成功')
    registerVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------------- 转处置 / 闭环 ---------------- */
const assignVisible = ref(false)
const assignHandlerId = ref<number>()
const assignId = ref<number>()
const closeVisible = ref(false)
const closeRemark = ref('')
const closeId = ref<number>()

function openAssign(row: PatrolHazardVO) {
  assignId.value = row.id
  assignHandlerId.value = undefined
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignHandlerId.value) {
    ElMessage.warning('请选择处置人')
    return
  }
  submitting.value = true
  try {
    await assignHazardApi(assignId.value as number, assignHandlerId.value)
    ElMessage.success('已转处置')
    assignVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

function openClose(row: PatrolHazardVO) {
  closeId.value = row.id
  closeRemark.value = ''
  closeVisible.value = true
}

async function submitClose() {
  if (!closeRemark.value.trim()) {
    ElMessage.warning('闭环说明必填')
    return
  }
  submitting.value = true
  try {
    await closeHazardApi(closeId.value as number, closeRemark.value)
    ElMessage.success('隐患已闭环')
    closeVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------------- 转灾害 ---------------- */
const convertVisible = ref(false)
const convertId = ref<number>()
const convertRef = ref<FormInstance>()

interface ConvertForm {
  tunnelId?: number
  sectionId?: number
  hazardType: number
  eventTime: string
  consequence: string
  level: number
}

const emptyConvert = (): ConvertForm => ({
  tunnelId: undefined,
  sectionId: undefined,
  hazardType: 3,
  eventTime: '',
  consequence: '',
  level: 2,
})

const convertForm = reactive<ConvertForm>(emptyConvert())

const convertRules: FormRules = {
  tunnelId: [{ required: true, message: '请选择隧道', trigger: 'change' }],
  hazardType: [{ required: true, message: '请选择灾种', trigger: 'change' }],
  eventTime: [{ required: true, message: '请选择事件时间', trigger: 'change' }],
  consequence: [{ required: true, message: '请填写灾害描述', trigger: 'blur' }],
}

function openConvert(row: PatrolHazardVO) {
  convertId.value = row.id
  Object.assign(convertForm, emptyConvert(), {
    tunnelId: row.tunnelId,
    sectionId: row.sectionId,
    hazardType: row.hazardType ?? 3,
    eventTime: formatDateTime(row.discoverTime).replace(' ', 'T'),
  })
  convertVisible.value = true
}

async function submitConvert() {
  const valid = await convertRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await convertHazardToDisasterApi(convertId.value as number, {
      tunnelId: convertForm.tunnelId,
      sectionId: convertForm.sectionId ?? null,
      hazardType: convertForm.hazardType,
      eventTime: convertForm.eventTime,
      consequence: convertForm.consequence,
      level: convertForm.level,
    })
    ElMessage.success('已转灾害登记')
    convertVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

/* 登记/转灾害弹窗打开时按隧道刷新断面候选 */
watch(
  () => [registerForm.tunnelId, convertForm.tunnelId],
  async () => {
    const tid = registerVisible.value ? registerForm.tunnelId : convertVisible.value ? convertForm.tunnelId : undefined
    if (tid != null) {
      sections.value = await getSectionListApi(tid)
    }
  },
)

onMounted(async () => {
  await loadOptions()
  await loadPage()
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
