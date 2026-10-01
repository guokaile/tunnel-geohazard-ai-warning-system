<template>
  <div>
    <!-- 筛选条：隧道（B01）→ 断面（B03）级联，断面选定后加载 B19 并 5s 轮询 -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="隧道">
          <el-select
            v-model="tunnelId"
            placeholder="请选择隧道"
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
          <el-select v-model="sectionId" placeholder="请选择断面" style="width: 190px" @change="handleSectionChange">
            <el-option
              v-for="section in sections"
              :key="section.id"
              :label="`${section.sectionCode} ${section.sectionName}`"
              :value="section.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="toolbar-right">
          <span class="refresh-tip">
            <el-icon><Timer /></el-icon>
            断面数据每 5 秒自动刷新 · 最近刷新 {{ lastRefreshTime || '-' }}
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16">
      <!-- 断面图（ECharts 自定义图形：马蹄形断面轮廓 + 测点分布 + 超限点闪烁） -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="board-header">
              <span class="board-title">{{ board?.sectionName || '断面图' }}</span>
              <span v-if="board" class="board-sub">
                {{ board.sectionCode }} · {{ board.mileageFrom || '-' }} ~ {{ board.mileageTo || '-' }} ·
                {{ board.geoZone || '-' }}
              </span>
            </div>
          </template>
          <div ref="chartRef" class="board-chart"></div>
          <!-- 色标图例：正常/离线 + 蓝黄橙红四级（带文字标签，不单靠颜色传达） -->
          <div class="chart-legend">
            <span class="legend-item"><i class="legend-dot" :style="{ backgroundColor: NORMAL_COLOR }" />正常</span>
            <span class="legend-item"><i class="legend-dot" :style="{ backgroundColor: OFFLINE_COLOR }" />离线</span>
            <span v-for="meta in legendLevels" :key="meta.level" class="legend-item">
              <i class="legend-dot" :style="{ backgroundColor: meta.color }" />{{ meta.label }}级
            </span>
            <span class="legend-tip">超限点位以涟漪动画闪烁告警</span>
          </div>
        </el-card>
      </el-col>

      <!-- 断面点位明细（B19 points，随断面图 5s 轮询） -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>点位明细（{{ board?.points.length ?? 0 }}）</span>
          </template>
          <el-table v-loading="loading" :data="board?.points ?? []" stripe max-height="560" size="small">
            <el-table-column label="点位" min-width="150">
              <template #default="{ row }">
                <span class="point-code">{{ row.pointCode }}</span>
                <span class="point-name">{{ row.pointName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="最新值" width="100" align="right">
              <template #default="{ row }">
                {{ formatValue(row.value) }}<span v-if="row.value != null && row.unit" class="unit-text">{{ row.unit }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
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
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'

import { getSectionBoardApi, getSectionListApi, getTunnelListApi } from '@/api/mon'
import type { PointLatestVO, SectionBoardVO, SectionVO, TunnelVO } from '@/types/mon'
import {
  NORMAL_COLOR,
  OFFLINE_COLOR,
  ONLINE_META,
  WARN_LEVEL_META,
  formatTimeNow,
  formatValue,
  levelLabel,
  levelTagStyle,
} from './monMeta'

const tunnels = ref<TunnelVO[]>([])
const sections = ref<SectionVO[]>([])
const tunnelId = ref<number>()
const sectionId = ref<number>()
const board = ref<SectionBoardVO | null>(null)
const loading = ref(false)
const lastRefreshTime = ref('')

/** 图例四级色标（固定顺序 蓝→红） */
const legendLevels = computed(() =>
  [1, 2, 3, 4].map((level) => ({ level, label: WARN_LEVEL_META[level].label, color: WARN_LEVEL_META[level].color })),
)

/* ---------------- B19 数据加载（5s 轮询） ---------------- */
const BOARD_POLL_MS = 5_000 // FR-202 超限状态即时联动：与看板同频 5s
let boardTimer: number | null = null
let boardInFlight = false

async function loadBoard() {
  if (sectionId.value == null) return
  if (boardInFlight) return // 防轮询与慢响应重叠
  boardInFlight = true
  loading.value = board.value == null
  try {
    const data = await getSectionBoardApi(sectionId.value)
    board.value = data ?? null
    lastRefreshTime.value = formatTimeNow()
    renderChart()
  } catch {
    // 失败提示由 http.ts 统一处理；保留上一帧
  } finally {
    boardInFlight = false
    loading.value = false
  }
}

function startBoardPolling() {
  if (boardTimer != null) return
  boardTimer = window.setInterval(() => {
    if (!document.hidden) void loadBoard() // 页面隐藏跳过轮询，回前台自动恢复
  }, BOARD_POLL_MS)
}

function stopBoardPolling() {
  if (boardTimer != null) {
    window.clearInterval(boardTimer)
    boardTimer = null
  }
}

async function handleTunnelChange() {
  sectionId.value = undefined
  board.value = null
  sections.value = []
  if (tunnelId.value != null) {
    getSectionListApi(tunnelId.value)
      .then((data) => {
        sections.value = data ?? []
      })
      .catch(() => {
        sections.value = []
      })
  }
  renderChart()
}

function handleSectionChange() {
  void loadBoard()
}

/* ---------------- 断面图（ECharts 自定义图形） ---------------- */
const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

/** 布局后的测点（含推算坐标与标签位置） */
interface LayoutPoint {
  x: number
  y: number
  labelPos: 'top' | 'left' | 'right'
  point: PointLatestVO
}

/**
 * 马蹄形断面测点布局（FR-202 测点与断面位置一一对应）。
 *
 * 契约缺口说明：《4》4.5.2 B19 出参无测点坐标字段（PointLatestVo 仅值/状态，installPosition 亦未返回），
 * 空间位置无法来自后端。本期按点位序号沿马蹄形轮廓（左边墙→拱顶→右边墙）等距布点——
 * 布局仅取决于序号，稳定可复现，供演示与验收；后端补充坐标字段后，仅需替换本函数为真实坐标映射。
 */
function layoutSectionPoints(points: PointLatestVO[]): LayoutPoint[] {
  const n = points.length
  // 轮廓：左边墙 (-1,0)→(-1,2)，拱顶圆弧（圆心(0,2) 半径1，左→右扫过顶部），右边墙 (1,2)→(1,0)
  const leftWall = 2
  const arch = Math.PI
  const perimeter = leftWall + arch + 2
  return points.map((point, i) => {
    const t = n <= 1 ? 0.5 : ((i + 0.5) / n) * perimeter // 沿轮廓弧长位置
    let x: number
    let y: number
    let labelPos: 'top' | 'left' | 'right'
    if (t <= leftWall) {
      // 左边墙（自下而上）
      x = -1
      y = t
      labelPos = 'left'
    } else if (t <= leftWall + arch) {
      // 拱顶圆弧：角度 π（左）→ 0（右）
      const angle = Math.PI * (1 - (t - leftWall) / arch)
      x = Math.cos(angle)
      y = 2 + Math.sin(angle)
      labelPos = 'top'
    } else {
      // 右边墙（自上而下）
      x = 1
      y = perimeter - t
      labelPos = 'right'
    }
    return { x: Number(x.toFixed(3)), y: Number(y.toFixed(3)), labelPos, point }
  })
}

/** 三态取色：预警（按级别色）/ 离线（无值）/ 正常（有值无预警）。
 *  注：演示数据 onlineStatus 恒为 0（未随最新值更新），故"离线"视觉以"无最新值"表达，
 *  在线状态仍按 onlineStatus 字段在表格 tag 中如实展示。 */
function pointColor(point: PointLatestVO): string {
  if (point.warnLevel) return WARN_LEVEL_META[point.warnLevel]?.color ?? OFFLINE_COLOR
  if (point.value == null) return OFFLINE_COLOR
  return NORMAL_COLOR
}

/** 断面轮廓线（马蹄形 + 虚线仰拱底边） */
function outlineData(): number[][] {
  const data: number[][] = []
  data.push([-1, 0], [-1, 2]) // 左边墙
  const steps = 24
  for (let i = 0; i <= steps; i += 1) {
    const angle = Math.PI * (1 - i / steps) // π→0
    data.push([Number(Math.cos(angle).toFixed(3)), Number((2 + Math.sin(angle)).toFixed(3))])
  }
  data.push([1, 2], [1, 0]) // 右边墙
  return data
}

function renderChart() {
  if (!chart) return
  const points = board.value?.points ?? []
  const layout = layoutSectionPoints(points)

  // 点位按状态分入两个系列：超限点走 effectScatter（涟漪闪烁告警，FR-202），其余走 scatter
  const normalData = layout
    .filter((p) => !p.point.warnLevel)
    .map((p) => ({
      value: [p.x, p.y],
      point: p.point,
      itemStyle: { color: pointColor(p.point) },
      // 标签方位按测点在断面上的位置（左墙靠左、右墙靠右、拱顶在上），避免遮挡
      label: { position: p.labelPos },
    }))
  const warnData = layout
    .filter((p) => !!p.point.warnLevel)
    .map((p) => ({
      value: [p.x, p.y],
      point: p.point,
      itemStyle: { color: pointColor(p.point) },
      label: { position: p.labelPos },
    }))

  const labelCfg = {
    show: true,
    fontSize: 10,
    color: '#303133', // 标签文字用墨色而非系列色（可读性规范）
    formatter: (params: unknown) => {
      const data = (params as { data: LayoutPoint }).data
      return `${data.point.pointName}\n${formatValue(data.point.value)}${data.point.value != null && data.point.unit ? data.point.unit : ''}`
    },
  }

  const tooltipFormatter = (params: unknown) => {
    const data = (params as { data: LayoutPoint }).data
    if (!data?.point) return ''
    const p = data.point
    const status = ONLINE_META[p.onlineStatus]?.label ?? '-'
    const level = p.warnLevel ? `${levelLabel(p.warnLevel)}级` : '正常'
    const quality = p.quality != null ? ['正常', '超范围', '跳变', '缺失'][p.quality] ?? p.quality : '-'
    return [
      `<b>${p.pointName}</b>（${p.pointCode}）`,
      `最新值：${formatValue(p.value)}${p.value != null && p.unit ? p.unit : ''}`,
      `采样时间：${p.ts ? p.ts.replace('T', ' ').replace(/\.\d+$/, '') : '-'}`,
      `质量位：${quality}`,
      `在线状态：${status}`,
      `预警级别：${level}`,
    ].join('<br/>')
  }

  chart.setOption(
    {
      title: points.length
        ? undefined
        : { text: '暂无点位数据', left: 'center', top: 'middle', textStyle: { color: '#909399', fontSize: 14 } },
      grid: { left: 30, right: 30, top: 20, bottom: 20 },
      xAxis: { type: 'value', min: -2.6, max: 2.6, show: false },
      yAxis: { type: 'value', min: -0.6, max: 3.8, show: false },
      tooltip: { trigger: 'item', formatter: tooltipFormatter },
      series: [
        {
          name: '断面轮廓',
          type: 'line',
          silent: true,
          symbol: 'none',
          data: outlineData(),
          lineStyle: { color: '#909399', width: 2 },
          tooltip: { show: false },
        },
        {
          name: '仰拱线',
          type: 'line',
          silent: true,
          symbol: 'none',
          data: [
            [-1, 0],
            [1, 0],
          ],
          lineStyle: { color: '#c0c4cc', width: 1.5, type: 'dashed' },
          tooltip: { show: false },
        },
        {
          name: '测点',
          type: 'scatter',
          symbolSize: 13,
          data: normalData,
          label: { ...labelCfg, position: 'top' },
          emphasis: { scale: 1.4 },
        },
        {
          name: '超限告警',
          type: 'effectScatter',
          symbolSize: 13,
          data: warnData,
          // 涟漪动画实现"超限点位闪烁告警"（FR-202）：描边涟漪周期扩散
          rippleEffect: { period: 3.5, scale: 2.8, brushType: 'stroke' },
          label: { ...labelCfg, position: 'top' },
          zlevel: 1,
        },
      ],
    },
    { notMerge: true },
  )
}

function handleResize() {
  chart?.resize()
}

/* ---------------- 生命周期 ---------------- */
onMounted(() => {
  chart = echarts.init(chartRef.value as HTMLDivElement)
  window.addEventListener('resize', handleResize)
  getTunnelListApi()
    .then((data) => {
      tunnels.value = data ?? []
      if (tunnels.value.length > 0 && tunnelId.value == null) {
        tunnelId.value = tunnels.value[0].id
        void handleTunnelChange()
      }
    })
    .catch(() => {
      tunnels.value = []
    })
  startBoardPolling()
  renderChart()
})

onBeforeUnmount(() => {
  // 清理：轮询定时器 + 图表实例 + resize 监听（防泄漏）
  stopBoardPolling()
  window.removeEventListener('resize', handleResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.board-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.board-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.board-sub {
  font-size: 12px;
  color: #909399;
}

.board-chart {
  width: 100%;
  height: 480px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 4px 0;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-tip {
  margin-left: auto;
  color: #909399;
}

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

.point-code {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  color: #303133;
}

.point-name {
  margin-left: 6px;
  font-size: 12px;
  color: #606266;
}

.unit-text {
  margin-left: 2px;
  font-size: 11px;
  color: #909399;
}
</style>
