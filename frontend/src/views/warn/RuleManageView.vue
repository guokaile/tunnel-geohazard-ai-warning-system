<template>
  <div>
    <!-- 筛选区（API-C05：hazardType/itemType/status，不分页） -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="灾种">
          <el-select v-model="filter.hazardType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="ht in HAZARD_OPTIONS" :key="ht" :label="hazardLabel(ht)" :value="ht" />
          </el-select>
        </el-form-item>
        <el-form-item label="测项">
          <el-input-number
            v-model="filter.itemType"
            :min="0"
            :controls="false"
            placeholder="全部（0=全部）"
            style="width: 130px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <el-button v-if="hasWarnPerm('warn:rule:edit')" type="primary" :icon="Plus" @click="openCreate">
            新增规则
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表区（每规则最新版本） -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="ruleCode" label="规则编码" width="140" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ RULE_TYPE_META[row.ruleType] ?? row.ruleType }}</template>
        </el-table-column>
        <el-table-column label="适用对象" width="100">
          <template #default="{ row }">{{ hazardLabel(row.hazardType) }}</template>
        </el-table-column>
        <el-table-column label="测项" width="80">
          <template #default="{ row }">{{ row.itemType === 0 ? '全部' : row.itemType }}</template>
        </el-table-column>
        <el-table-column label="定级" width="80">
          <template #default="{ row }">
            <el-tag
              size="small"
              :style="{
                backgroundColor: WARN_LEVEL_META[row.warnLevel]?.color ?? '#909399',
                borderColor: WARN_LEVEL_META[row.warnLevel]?.color ?? '#909399',
                color: '#fff',
              }"
            >
              {{ levelLabel(row.warnLevel) }}级
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasWarnPerm('warn:rule:edit')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:rule:edit')"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="hasWarnPerm('warn:rule:view')" link type="warning" @click="openHistory(row)">
              历史版本
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:rule:edit')"
              link
              type="danger"
              @click="removeRule(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增 / 编辑对话框（API-C06/C07；编辑=版本+1 新行，历史留痕） -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增预警规则' : '编辑预警规则'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="form.ruleName" placeholder="如：CH4浓度超限-黄" maxlength="64" />
        </el-form-item>
        <el-form-item label="规则类型" prop="ruleType">
          <el-select v-model="form.ruleType" placeholder="请选择" style="width: 100%">
            <el-option v-for="rt in RULE_TYPE_OPTIONS" :key="rt" :label="RULE_TYPE_META[rt]" :value="rt" />
          </el-select>
        </el-form-item>
        <el-form-item label="命中定级" prop="warnLevel">
          <el-select v-model="form.warnLevel" placeholder="请选择" style="width: 100%">
            <el-option v-for="lv in LEVEL_OPTIONS" :key="lv" :label="`${levelLabel(lv)}级`" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用对象">
          <el-select v-model="form.hazardType" style="width: 100%">
            <el-option v-for="ht in RULE_HAZARD_OPTIONS" :key="ht" :label="hazardLabel(ht)" :value="ht" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用测项">
          <el-input-number v-model="form.itemType" :min="0" :controls="false" style="width: 160px" />
          <div class="form-tip">0=全部测项（默认）</div>
        </el-form-item>
        <el-form-item label="工程阶段">
          <el-input-number v-model="form.stage" :min="0" :controls="false" style="width: 160px" />
          <div class="form-tip">0=全阶段（默认）</div>
        </el-form-item>
        <el-form-item label="适用断面">
          <el-input-number v-model="form.sectionId" :min="0" :controls="false" style="width: 160px" />
          <div class="form-tip">0=全部断面（默认）</div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="9999" style="width: 160px" />
          <div class="form-tip">同点命中多规则取级别最高，同级取优先级高者</div>
        </el-form-item>
        <el-form-item label="表达式参数" prop="expressionJson">
          <el-input
            v-model="form.expressionJson"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，如：{"threshold":0.5,"on":3,"hysteresisPct":5}'
          />
          <div class="form-tip">条件表达式/参数 JSON（Aviator）；后端仅校验合法 JSON 结构，阈值需技术专家评审定稿</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="选填" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span v-if="dialogMode === 'edit'" class="dialog-tip">
          修改将生成新版本（版本号 +1），历史版本留痕（API-C07）
        </span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史抽屉（API-C10：同 rule_code 全版本，倒序） -->
    <el-drawer v-model="historyVisible" :title="historyTitle" size="640px">
      <el-table v-loading="historyLoading" :data="historyList" stripe size="small">
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ RULE_TYPE_META[row.ruleType] ?? row.ruleType }}</template>
        </el-table-column>
        <el-table-column label="定级" width="70">
          <template #default="{ row }">
            <el-tag
              size="small"
              :style="{
                backgroundColor: WARN_LEVEL_META[row.warnLevel]?.color ?? '#909399',
                borderColor: WARN_LEVEL_META[row.warnLevel]?.color ?? '#909399',
                color: '#fff',
              }"
            >
              {{ levelLabel(row.warnLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="70" />
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expressionJson" label="表达式参数" min-width="220" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'

import {
  createRuleApi,
  deleteRuleApi,
  getRuleHistoryApi,
  getRuleListApi,
  updateRuleApi,
  updateRuleStatusApi,
} from '@/api/warn'
import type { WarnRuleForm, WarnRuleVO } from '@/types/warn'
import {
  RULE_TYPE_META,
  WARN_LEVEL_META,
  hasWarnPerm,
  hazardLabel,
  levelLabel,
} from './warnMeta'

const HAZARD_OPTIONS = [1, 2, 3, 4, 5, 6]
const RULE_HAZARD_OPTIONS = [0, 1, 2, 3, 4, 5, 6]
const RULE_TYPE_OPTIONS = [1, 2, 3, 4, 5]
const LEVEL_OPTIONS = [1, 2, 3, 4]

/* ---------------- 列表与筛选（API-C05：hazardType/itemType/status，出每规则最新版本） ---------------- */
const loading = ref(false)
const list = ref<WarnRuleVO[]>([])
const filter = reactive({
  hazardType: '' as number | '',
  itemType: undefined as number | undefined,
  status: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const data = await getRuleListApi({
      hazardType: typeof filter.hazardType === 'number' ? filter.hazardType : undefined,
      itemType: filter.itemType ?? undefined,
      status: typeof filter.status === 'number' ? filter.status : undefined,
    })
    list.value = data ?? []
  } catch {
    // 失败提示由 http.ts 统一处理
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  void loadList()
}

function handleReset() {
  filter.hazardType = ''
  filter.itemType = undefined
  filter.status = ''
  void loadList()
}

/* ---------------- 新增 / 编辑（API-C06/C07；编辑=版本+1） ---------------- */
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<WarnRuleForm>({
  ruleName: '',
  hazardType: 0,
  itemType: 0,
  stage: 0,
  sectionId: 0,
  ruleType: 1,
  warnLevel: 2,
  expressionJson: '',
  priority: 100,
  remark: '',
})

const formRules: FormRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
  warnLevel: [{ required: true, message: '请选择命中定级', trigger: 'change' }],
  expressionJson: [{ required: true, message: '请输入表达式参数 JSON', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.ruleName = ''
  form.hazardType = 0
  form.itemType = 0
  form.stage = 0
  form.sectionId = 0
  form.ruleType = 1
  form.warnLevel = 2
  form.expressionJson = ''
  form.priority = 100
  form.remark = ''
  dialogVisible.value = true
}

function openEdit(row: WarnRuleVO) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.ruleName = row.ruleName
  form.hazardType = row.hazardType
  form.itemType = row.itemType
  form.stage = row.stage
  form.sectionId = row.sectionId
  form.ruleType = row.ruleType
  form.warnLevel = row.warnLevel
  form.expressionJson = row.expressionJson
  form.priority = row.priority
  form.remark = row.remark ?? ''
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // expressionJson 结构校验（与后端一致：仅校验合法 JSON，Aviator 编译在引擎内）
  try {
    JSON.parse(form.expressionJson.trim())
  } catch {
    ElMessage.warning('expressionJson 必须为合法 JSON（后端 A0002 校验）')
    return
  }
  if (dialogMode.value === 'edit') {
    try {
      await ElMessageBox.confirm(
        '修改将生成新版本（版本号 +1），历史版本留痕（API-C07）。确认修改？',
        '提示',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  submitting.value = true
  try {
    const payload: WarnRuleForm = {
      ruleName: form.ruleName.trim(),
      hazardType: form.hazardType ?? 0,
      itemType: form.itemType ?? 0,
      stage: form.stage ?? 0,
      sectionId: form.sectionId ?? 0,
      ruleType: form.ruleType,
      warnLevel: form.warnLevel,
      expressionJson: form.expressionJson.trim(),
      priority: form.priority ?? 100,
      remark: form.remark || undefined,
    }
    if (dialogMode.value === 'create') {
      const id = await createRuleApi(payload)
      ElMessage.success(`新增成功（规则 #${id}，版本 v1）`)
    } else if (editingId.value != null) {
      await updateRuleApi(editingId.value, payload)
      ElMessage.success('修改成功，已生成新版本（版本 +1）')
    }
    dialogVisible.value = false
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理（如 A0002 校验失败 / B0201 编码重复）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 启停（API-C09；停用规则不参与判定） ---------------- */
async function toggleStatus(row: WarnRuleVO) {
  const target = row.status === 1 ? 0 : 1
  const actionText = target === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确定${actionText}规则「${row.ruleName}」吗？${target === 0 ? '停用后不再参与预警判定。' : ''}`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await updateRuleStatusApi(row.id, target)
    ElMessage.success(`${actionText}成功`)
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理
  }
}

/* ---------------- 删除（API-C08；被预警事件引用禁止删除 B0204） ---------------- */
async function removeRule(row: WarnRuleVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除规则「${row.ruleCode}」及其全部历史版本吗？被预警事件引用的规则禁止删除（可改用停用）。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteRuleApi(row.id)
    ElMessage.success('删除成功')
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0204 被事件引用）
  }
}

/* ---------------- 版本历史（API-C10） ---------------- */
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref<WarnRuleVO[]>([])
const historyTitle = ref('版本历史')

async function openHistory(row: WarnRuleVO) {
  historyTitle.value = `版本历史 - ${row.ruleCode} ${row.ruleName}`
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyList.value = (await getRuleHistoryApi(row.id)) ?? []
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => {
  void loadList()
})
</script>

<style scoped>
.toolbar-right {
  float: right;
  margin-right: 0;
}

.dialog-tip {
  float: left;
  font-size: 12px;
  color: #909399;
  line-height: 32px;
}

.form-tip {
  width: 100%;
  font-size: 12px;
  color: #909399;
  line-height: 18px;
}
</style>
