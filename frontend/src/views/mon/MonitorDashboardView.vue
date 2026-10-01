<template>
  <div>
    <!-- 顶部统计卡（API-B20 概览，30s 轮询） -->
    <div class="stat-row mb16">
      <div class="stat-tile">
        <el-icon :size="30" color="#2a78d6"><Monitor /></el-icon>
        <div class="stat-body">
          <div class="stat-value">{{ overview ? overview.totalPoints.toLocaleString() : '-' }}</div>
          <div class="stat-label">点位总数</div>
        </div>
      </div>
      <div class="stat-tile">
        <el-icon :size="30" color="#0ca30c"><Connection /></el-icon>
        <div class="stat-body">
          <div class="stat-value">{{ overview ? `${overview.onlineRate}%` : '-' }}</div>
          <div class="stat-label">在线率</div>
        </div>
      </div>
      <div class="stat-tile">
        <el-icon :size="30" color="#4a3aa7"><DataLine /></el-icon>
        <div class="stat-body">
          <div class="stat-value">{{ overview ? overview.todaySamples.toLocaleString() : '-' }}</div>
          <div class="stat-label">今日数据量</div>
        </div>
      </div>
      <div class="stat-tile">
        <el-icon :size="30" color="#909399"><Warning /></el-icon>
        <div class="stat-body">
          <div class="stat-value">{{ overview ? overview.offlinePoints.toLocaleString() : '-' }}</div>
          <div class="stat-label">离线点位</div>
        </div>
      </div>
    </div>

    <!-- 筛选条（B01/B03 隧道→断面级联 + 灾种/关键字） -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="隧道">
          <el-select
            v-model="filters.tunnelId"
            placeholder="全部隧道"
            clearable
            style="width: 190px"
            @change="handleTunnelChange"
          >
            <el-option
              v-for="tunnel in tunnels"
              :key="tunnel.id"
              :label="`${tunnel.tunnelCode} ${tunnel.tunnelName}`"
              :value="tunnel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="断面">
          <el-select
            v-model="filters.sectionId"
            placeholder="全部断面"
            clearable
            :disabled="!filters.tunnelId"
            style="width: 170px"
            @change="loadLatest"
          >
            <el-option
              v-for="section in sections"
              :key="section.id"
              :label="`${section.sectionCode} ${section.sectionName}`"
              :value="section.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="灾种">
          <el-select v-model="filters.hazardType" placeholder="全部灾种" clearable style="width: 170px" @change="loadLatest">
            <el-option
              v-for="(label, code) in HAZARD_TYPE_META"
              :key="code"
              :label="label"
              :value="Number(code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="点位编码 / 名称"
            clearable
            style="width: 180px"
            @keyup.enter="loadLatest"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadLatest">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <!-- FR-201 数据端到端刷新 ≤5s：B16 最新值 5s 轮询，此处展示最近一次成功刷新时间 -->
          <span class="refresh-tip">
            <el-icon><Timer /></el-icon>
            数据每 5 秒自动刷新 · 最近刷新 {{ lastRefreshTime || '-' }}
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 点位实时值表格（API-B16，5s 轮询；预警行按级别色闪烁高亮） -->
    <el-card shadow="never">
      <el-table
        v-loading="loading"
        :data="latestList"
        stripe
        max-height="560"
        :row-class-name="rowClassName"
      >
        <el-table-column label="点位" min-width="200">
          <template #default="{ row }">
            <span class="point-code">{{ row.pointCode }}</span>
            <span class="point-name">{{ row.pointName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tunnelName" label="隧道" min-width="140">
          <template #default="{ row }">{{ row.tunnelName || '-' }}</template>
        </el-table-column>
        <el-table-column label="测项" width="120">
          <template #default="{ row }">{{ itemLabel(row.itemType) }}</template>
        </el-table-column>
        <el-table-column label="最新值" width="130" align="right">
          <template #default="{ row }">
            <span class="latest-value">{{ formatValue(row.value) }}</span>
            <span v-if="row.value != null && row.unit" class="unit-text">{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采样时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.ts) }}</template>
        </el-table-column>
        <el-table-column label="质量位" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.quality != null" size="small" :type="QUALITY_META[row.quality]?.tag ?? 'info'">
              {{ QUALITY_META[row.quality]?.label ?? `质量${row.quality}` }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="ONLINE_META[row.onlineStatus]?.tag ?? 'info'">
              {{ ONLINE_META[row.onlineStatus]?.label ?? '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.warnLevel" size="small" :style="levelTagStyle(row.warnLevel)">
              {{ levelLabel(row.warnLevel) }}级
            </el-tag>
            <el-tag v-else size="small" type="success" effect="plain">正常</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt16 table-foot">共 {{ latestList.length }} 个点位</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { getLatestListApi, getOverviewApi, getSectionListApi, getTunnelListApi } from '@/api/mon'
import { useAuthStore } from '@/stores/auth'
import type { OverviewVO, PointLatestVO, SectionVO, TunnelVO } from '@/types/mon'
import {
  HAZARD_TYPE_META,
  ONLINE_META,
  QUALITY_META,
  WARN_LEVEL_META,
  formatDateTime,
  formatTimeNow,
  formatValue,
  hasMonPerm,
  itemLabel,
  levelLabel,
  levelTagStyle,
} from './monMeta'

/* ---------------- 筛选与数据 ---------------- */
const filters = reactive({
  tunnelId: undefined as number | undefined,
  sectionId: undefined as number | undefined,
  hazardType: undefined as number | undefined,
  keyword: '',
})

const tunnels = ref<TunnelVO[]>([])
const sections = ref<SectionVO[]>([])
const latestList = ref<PointLatestVO[]>([])
const overview = ref<OverviewVO | null>(null)
const loading = ref(false)
const lastRefreshTime = ref('')

/** B16 最新值查询（含筛选参数；keyword 空串不传） */
async function loadLatest() {
  // 防 5s 轮询与慢响应重叠堆积：上一请求未返回时跳过本次 tick
  if (latestInFlight) return
  latestInFlight = true
  // 首次/筛选后列表为空时展示 loading，轮询静默刷新避免表格闪烁
  loading.value = latestList.value.length === 0
  try {
    const data = await getLatestListApi({
      tunnelId: filters.tunnelId,
      sectionId: filters.sectionId,
      hazardType: filters.hazardType,
      keyword: filters.keyword || undefined,
    })
    latestList.value = data ?? []
    lastRefreshTime.value = formatTimeNow()
  } catch {
    // 失败提示由 http.ts 统一处理；保留上一帧数据
  } finally {
    latestInFlight = false
    loading.value = false
  }
}

/** B20 概览统计（30s 轮询） */
async function loadOverview() {
  try {
    overview.value = await getOverviewApi()
  } catch {
    // 失败保留上一帧统计
  }
}

/** 隧道切换 → 级联加载断面（B03）并清空断面选择 */
async function handleTunnelChange() {
  filters.sectionId = undefined
  sections.value = []
  if (filters.tunnelId != null) {
    getSectionListApi(filters.tunnelId)
      .then((data) => {
        sections.value = data ?? []
      })
      .catch(() => {
        sections.value = []
      })
  }
  void loadLatest()
}

function handleReset() {
  filters.tunnelId = undefined
  filters.sectionId = undefined
  filters.hazardType = undefined
  filters.keyword = ''
  sections.value = []
  void loadLatest()
}

/** 行高亮：预警行按级别加闪烁类（warn 优先于 offline），离线行文字置灰——正常/超限/离线三态清晰 */
function rowClassName({ row }: { row: PointLatestVO }): string {
  if (row.warnLevel) return `row-warn row-warn-${row.warnLevel}`
  if (row.onlineStatus !== 1) return 'row-offline'
  return ''
}

/* ---------------- 轮询定时器（组件卸载必须清理，防泄漏） ---------------- */
const OVERVIEW_POLL_MS = 30_000 // B20 概览 30s
const LATEST_POLL_MS = 5_000 // B16 最新值 5s（FR-201 刷新 ≤5s）
let overviewTimer: number | null = null
let latestTimer: number | null = null
let latestInFlight = false

function startPolling() {
  void loadOverview()
  void loadLatest()
  // 页面隐藏（切后台标签页）时跳过轮询，节省后端负载；回到前台后下一 tick 自动恢复
  overviewTimer = window.setInterval(() => {
    if (!document.hidden) void loadOverview()
  }, OVERVIEW_POLL_MS)
  latestTimer = window.setInterval(() => {
    if (!document.hidden) void loadLatest()
  }, LATEST_POLL_MS)
}

function stopPolling() {
  if (overviewTimer != null) {
    window.clearInterval(overviewTimer)
    overviewTimer = null
  }
  if (latestTimer != null) {
    window.clearInterval(latestTimer)
    latestTimer = null
  }
}

/* ---------------- SSE 预警推送（API-C21） ----------------
 * 设计意图：
 * - EventSource 无法携带自定义请求头，token 经 ?token= 查询参数认证（AuthInterceptor 兜底，见《4》4.5.3）；
 * - 网络闪断时浏览器原生自动重连并自动携带 Last-Event-ID 请求头（续传契约），无需前端处理；
 *   onerror 且 readyState=CLOSED（连接已终止：如网关拒绝/非 event-stream 响应）时原生不再重连，
 *   5s 后手动重建兜底；重建的新连接无法自定义 Last-Event-ID 头，且后端 SseService 本期未实现按
 *   lastEventId 回放（register 仅预留参数），故前端按 eventId 去重防重复弹窗；
 * - 连续重建失败 10 次后停止并提示，避免无权限（403）等场景下的无限重连循环；
 * - 组件卸载时关闭 EventSource 并取消待执行的重建定时器（防泄漏）。
 */
interface WarnStreamPayload {
  eventId: number
  level: number
  title: string
}

let eventSource: EventSource | null = null
let reconnectTimer: number | null = null
let reconnectFailCount = 0
let closedByPage = false
const shownEventIds = new Set<number>()

function connectWarnStream() {
  const auth = useAuthStore()
  if (!auth.token || closedByPage) return
  // 无 warn:event:view 权限时不建立连接，后端 @RequirePermission 会拒绝流
  if (!hasMonPerm('warn:event:view')) return

  const url = `/api/v1/warn/stream?token=${encodeURIComponent(auth.token)}`
  eventSource = new EventSource(url)

  eventSource.onopen = () => {
    // 连接成功即清零失败计数（含原生自动重连成功的情况）
    reconnectFailCount = 0
  }

  eventSource.addEventListener('warn-event', (event) => {
    const msg = event as MessageEvent<string>
    let payload: WarnStreamPayload
    try {
      payload = JSON.parse(msg.data) as WarnStreamPayload
    } catch {
      return // 非 JSON 载荷（异常数据）静默忽略
    }
    // 断线重连补推去重：同一事件只弹一次（后端按 eventId 幂等推送）
    if (shownEventIds.has(payload.eventId)) return
    shownEventIds.add(payload.eventId)
    notifyWarn(payload)
  })

  eventSource.onerror = () => {
    if (eventSource && eventSource.readyState === EventSource.CLOSED) {
      eventSource.close()
      eventSource = null
      scheduleReconnect()
    }
    // readyState=CONNECTING 时为浏览器原生自动重连中，无需处理
  }
}

/** 5s 后手动重建连接（兜底原生不重连的终止场景） */
function scheduleReconnect() {
  if (closedByPage || reconnectTimer != null) return
  if (reconnectFailCount >= 10) {
    ElMessage.warning('预警推送连接已断开，请刷新页面恢复')
    return
  }
  reconnectFailCount += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectWarnStream()
  }, 5_000)
}

/** 预警弹窗：橙/红级 error 且不自动关闭（需人工关注），蓝/黄级 warning 10s 自动关闭 */
function notifyWarn(payload: WarnStreamPayload) {
  const meta = WARN_LEVEL_META[payload.level]
  ElNotification({
    title: `预警事件 · ${meta?.label ?? payload.level}级`,
    message: `${payload.title}（事件 ID：${payload.eventId}）`,
    type: payload.level >= 3 ? 'error' : 'warning',
    duration: payload.level >= 3 ? 0 : 10_000,
    position: 'top-right',
  })
}

function closeWarnStream() {
  closedByPage = true
  if (reconnectTimer != null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

/* ---------------- 生命周期 ---------------- */
onMounted(() => {
  // 隧道下拉（B01）→ 默认选中第一个隧道 → 级联断面（B03）
  getTunnelListApi()
    .then((data) => {
      tunnels.value = data ?? []
      if (tunnels.value.length > 0 && filters.tunnelId == null) {
        filters.tunnelId = tunnels.value[0].id
        void handleTunnelChange()
      }
    })
    .catch(() => {
      tunnels.value = []
    })
  startPolling()
  connectWarnStream()
})

onBeforeUnmount(() => {
  // 清理：轮询定时器 + SSE 连接与重建定时器（防组件卸载后回调触发与内存泄漏）
  stopPolling()
  closeWarnStream()
})
</script>

<style scoped>
/* 统计卡 */
.stat-row {
  display: flex;
  gap: 16px;
}

.stat-tile {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

/* 筛选条右侧刷新提示 */
.toolbar-right {
  float: right;
  margin-right: 0;
}

.refresh-tip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

/* 表格 */
.point-code {
  font-family: Consolas, Menlo, monospace;
  font-size: 13px;
  color: #303133;
}

.point-name {
  margin-left: 8px;
  font-size: 13px;
  color: #606266;
}

.latest-value {
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}

.unit-text {
  margin-left: 4px;
  font-size: 12px;
  color: #909399;
}

.table-foot {
  font-size: 12px;
  color: #909399;
}

.mt16 {
  margin-top: 16px;
}

/* 预警行闪烁（FR-202 超限点位变色闪烁告警）：按级别色脉冲，级别越重底色越深 */
:deep(.el-table__body tr.row-warn-1) {
  --warn-rgb: 64, 158, 255;
  animation: row-blink 1.2s ease-in-out infinite;
}

:deep(.el-table__body tr.row-warn-2) {
  --warn-rgb: 230, 162, 60;
  animation: row-blink 1.2s ease-in-out infinite;
}

:deep(.el-table__body tr.row-warn-3) {
  --warn-rgb: 180, 83, 9;
  animation: row-blink 0.9s ease-in-out infinite;
}

:deep(.el-table__body tr.row-warn-4) {
  --warn-rgb: 245, 108, 108;
  animation: row-blink 0.9s ease-in-out infinite;
}

/* 离线行：文字整体置灰，与预警行/正常行区分 */
:deep(.el-table__body tr.row-offline) {
  color: #a8abb2;
}

@keyframes row-blink {
  0%,
  100% {
    background-color: rgba(var(--warn-rgb, 245, 108, 108), 0.14);
  }

  50% {
    background-color: rgba(var(--warn-rgb, 245, 108, 108), 0.38);
  }
}
</style>
