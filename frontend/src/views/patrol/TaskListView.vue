<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">巡检任务列表</span>
        <div class="toolbar-actions">
          <el-select v-model="filterStatus" placeholder="按状态筛选" clearable style="width: 140px" @change="loadPage(1)">
            <el-option
              v-for="(meta, code) in TASK_STATUS_META"
              :key="code"
              :label="meta.label"
              :value="Number(code)"
            />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
            @change="loadPage(1)"
          />
          <el-button type="primary" :icon="Calendar" @click="openGenerate">生成任务</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="taskNo" label="任务编号" min-width="140" />
        <el-table-column label="隧道" min-width="130">
          <template #default="{ row }">{{ tunnelName(row.tunnelId) }}</template>
        </el-table-column>
        <el-table-column label="巡检人" min-width="100">
          <template #default="{ row }">{{ userName(row.inspectorId) }}</template>
        </el-table-column>
        <el-table-column prop="planTime" label="计划时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.planTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="TASK_STATUS_META[row.status]?.tag ?? 'info'" size="small">
              {{ TASK_STATUS_META[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ row.finishTime ? formatDateTime(row.finishTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="startTask(row)">
              开始巡检
            </el-button>
            <el-button v-if="row.status === 2" link type="primary" @click="openFill(row)">
              填报
            </el-button>
            <el-button v-if="row.status === 2" link type="success" @click="finishTask(row)">
              完成
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

    <!-- 填报巡检记录（API-D09） -->
    <el-dialog
      v-model="fillVisible"
      :title="`填报记录：${fillTask?.taskNo ?? ''}`"
      width="640px"
    >
      <div v-loading="fillLoading" class="fill-box">
        <div v-for="(item, idx) in fillItems" :key="idx" class="fill-row">
          <div class="fill-item-name">
            <div class="fill-item-title">{{ item.itemName }}</div>
            <div v-if="item.judgeStandard" class="fill-item-judge">{{ item.judgeStandard }}</div>
          </div>
          <el-select v-model="item.result" style="width: 110px; flex: none">
            <el-option
              v-for="(meta, code) in RESULT_META"
              :key="code"
              :label="meta.label"
              :value="Number(code)"
            />
          </el-select>
          <el-input v-model="item.description" placeholder="情况描述（可空）" />
        </div>
      </div>
      <template #footer>
        <el-button @click="fillVisible = false">取消</el-button>
        <el-button type="primary" :loading="fillSaving" @click="submitFill">提交</el-button>
      </template>
    </el-dialog>

    <!-- 手动生成巡检任务（演示/补漏；uk_plan_time 幂等） -->
    <el-dialog v-model="generateVisible" title="生成巡检任务" width="420px">
      <el-form label-width="90px">
        <el-form-item label="任务日期">
          <el-date-picker
            v-model="generateDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generateSaving" @click="submitGenerate">
          生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar } from '@element-plus/icons-vue'

import {
  fillTaskRecordsApi,
  finishTaskApi,
  generateTasksApi,
  getTaskPageApi,
  getTemplateDetailApi,
  startTaskApi,
} from '@/api/patrol'
import { getTunnelListApi } from '@/api/mon'
import { getUserPageApi } from '@/api/sys'
import type { PatrolTaskVO } from '@/types/patrol'
import type { TunnelVO } from '@/types/mon'
import type { SysUserVO } from '@/types/api'
import { RESULT_META, TASK_STATUS_META } from './patrolMeta'

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const tasks = ref<PatrolTaskVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref<number>()
const dateRange = ref<[string, string]>()
const tunnels = ref<TunnelVO[]>([])
const users = ref<SysUserVO[]>([])

function tunnelName(id: number): string {
  const t = tunnels.value.find((x) => x.id === id)
  return t ? `${t.tunnelCode} ${t.tunnelName}` : `#${id}`
}

function userName(id: number): string {
  const u = users.value.find((x) => x.id === id)
  return u ? u.realName || u.username : `#${id}`
}

function formatDateTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 19)
}

async function loadOptions() {
  const [t, u] = await Promise.all([
    getTunnelListApi(),
    getUserPageApi({ pageNum: 1, pageSize: 100 }),
  ])
  tunnels.value = t
  users.value = u.list
}

async function loadPage(page?: number) {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const r = await getTaskPageApi({
      status: filterStatus.value,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1],
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tasks.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

/* ---------------- 开始 / 完成 ---------------- */
async function startTask(row: PatrolTaskVO) {
  await startTaskApi(row.id)
  ElMessage.success('已开始巡检')
  await loadPage()
}

async function finishTask(row: PatrolTaskVO) {
  try {
    await ElMessageBox.confirm(`确定完成任务「${row.taskNo}」吗？完成后不可再填报。`, '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  await finishTaskApi(row.id)
  ElMessage.success('任务已完成')
  await loadPage()
}

/* ---------------- 填报 ---------------- */
const fillVisible = ref(false)
const fillLoading = ref(false)
const fillSaving = ref(false)
const fillTask = ref<PatrolTaskVO>()

interface FillItem {
  itemId?: number
  itemName: string
  judgeStandard?: string
  result: number
  description: string
}

const fillItems = ref<FillItem[]>([])

async function openFill(row: PatrolTaskVO) {
  fillTask.value = row
  fillVisible.value = true
  fillLoading.value = true
  fillItems.value = []
  try {
    const detail = await getTemplateDetailApi(row.templateId)
    fillItems.value = detail.items.map((i) => ({
      itemId: i.id,
      itemName: i.itemName,
      judgeStandard: i.judgeStandard,
      result: 1,
      description: '',
    }))
  } catch {
    fillVisible.value = false
  } finally {
    fillLoading.value = false
  }
}

async function submitFill() {
  if (!fillTask.value) return
  fillSaving.value = true
  try {
    await fillTaskRecordsApi(fillTask.value.id, {
      items: fillItems.value.map((i) => ({
        itemId: i.itemId,
        itemName: i.itemName,
        clientKey: crypto.randomUUID(),
        judgeStandardSnapshot: i.judgeStandard,
        result: i.result,
        description: i.description || undefined,
      })),
    })
    ElMessage.success('填报成功')
    fillVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    fillSaving.value = false
  }
}

/* ---------------- 手动生成任务 ---------------- */
const generateVisible = ref(false)
const generateSaving = ref(false)
const generateDate = ref('')

function openGenerate() {
  // 缺省今天：演示即时可见（正式口径由调度器每日 00:30 生成次日）
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  generateDate.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  generateVisible.value = true
}

async function submitGenerate() {
  if (!generateDate.value) {
    ElMessage.warning('请选择任务日期')
    return
  }
  generateSaving.value = true
  try {
    const r = await generateTasksApi(generateDate.value)
    ElMessage.success(`生成完成：新建 ${r.created} 条，跳过（已存在）${r.skipped} 条`)
    generateVisible.value = false
    await loadPage()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    generateSaving.value = false
  }
}

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

.fill-box {
  min-height: 80px;
}

.fill-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
}

.fill-item-name {
  flex: 1;
  min-width: 0;
}

.fill-item-title {
  font-weight: 600;
  color: #303133;
}

.fill-item-judge {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
