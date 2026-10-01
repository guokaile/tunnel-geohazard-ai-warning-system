<template>
  <div>
    <el-card shadow="never" class="mb16">
      <div class="toolbar">
        <span class="toolbar-title">驾驶舱指标</span>
        <div class="toolbar-actions">
          <el-select
            v-model="tunnelId"
            placeholder="选择隧道"
            filterable
            style="width: 240px"
            @change="loadDashboard"
          >
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
          <el-button :icon="Refresh" circle @click="loadDashboard" />
        </div>
      </div>

      <div v-if="!tunnelId" class="empty-hint">请先选择隧道</div>
      <div v-else v-loading="loading">
        <!-- 指标瓦片（FR-802 驾驶舱核心指标） -->
        <div class="stat-row mb16">
          <div class="stat-tile">
            <div class="stat-body">
              <div class="stat-value">{{ fmt(dash?.todayWarn) }}</div>
              <div class="stat-label">今日预警</div>
            </div>
          </div>
          <div class="stat-tile">
            <div class="stat-body">
              <div class="stat-value">{{ fmt(dash?.openEvents) }}</div>
              <div class="stat-label">未闭环事件</div>
            </div>
          </div>
          <div class="stat-tile">
            <div class="stat-body">
              <div class="stat-value">{{ pct(dash?.closeRate) }}</div>
              <div class="stat-label">事件闭环率</div>
            </div>
          </div>
          <div class="stat-tile">
            <div class="stat-body">
              <div class="stat-value">{{ pct(dash?.gatewayOnlineRate) }}</div>
              <div class="stat-label">网关在线率</div>
            </div>
          </div>
          <div class="stat-tile">
            <div class="stat-body">
              <div class="stat-value">{{ pct(dash?.sampleRate) }}</div>
              <div class="stat-label">采样完整率</div>
            </div>
          </div>
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <el-card shadow="never" class="chart-card">
              <div class="card-title">近 7 日预警趋势</div>
              <div ref="chartRef" class="trend-chart" />
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card shadow="never" class="chart-card">
              <div class="card-title">高危点位</div>
              <el-table :data="dash?.highRiskPoints ?? []" size="small" stripe>
                <el-table-column prop="pointCode" label="点位编码" min-width="110" />
                <el-table-column prop="pointName" label="点位名称" min-width="100" />
                <el-table-column label="最高等级" width="90">
                  <template #default="{ row }">
                    <el-tag :color="hazardLevelColor(row.maxLevel)" size="small" effect="dark">
                      {{ hazardLevelLabel(row.maxLevel) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="eventCount" label="事件数" width="80" />
                <template #empty>暂无高危点位</template>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

import { getReportDashboardApi } from '@/api/rpt'
import { getTunnelListApi } from '@/api/mon'
import type { ReportDashboardVO } from '@/types/rpt'
import type { TunnelVO } from '@/types/mon'
import { CURVE_PALETTE } from '@/views/mon/monMeta'
import { hazardLevelColor, hazardLevelLabel } from '@/views/patrol/patrolMeta'

const tunnels = ref<TunnelVO[]>([])
const tunnelId = ref<number>()
const loading = ref(false)
const dash = ref<ReportDashboardVO>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
let timer: ReturnType<typeof setInterval> | undefined

function fmt(v: number | undefined): string {
  return v == null ? '-' : v.toLocaleString()
}

/** 后端出参为 0~1 小数比例，展示为百分比（一位小数） */
function pct(v: number | undefined): string {
  if (v == null) return '-'
  const value = v <= 1 ? v * 100 : v
  return `${value.toFixed(1)}%`
}

function renderTrend() {
  if (!chart) return
  const trend = dash.value?.trend ?? []
  if (trend.length === 0) {
    chart.setOption(
      {
        title: {
          text: '暂无趋势数据',
          left: 'center',
          top: 'middle',
          textStyle: { color: '#909399', fontSize: 14 },
        },
        series: [],
        xAxis: { type: 'category' },
        yAxis: { type: 'value' },
      },
      { notMerge: true },
    )
    return
  }
  chart.setOption(
    {
      title: undefined,
      color: CURVE_PALETTE,
      // 单系列：图例省略，由卡片标题命名（dataviz 口径）
      tooltip: { trigger: 'axis' },
      grid: { left: 60, right: 24, top: 24, bottom: 40 },
      xAxis: {
        type: 'category',
        data: trend.map((p) => p.date.slice(5)),
        axisLabel: { color: '#909399' },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: '#909399' },
        splitLine: { lineStyle: { color: '#f0f2f5' } },
      },
      series: [
        {
          name: '预警数',
          type: 'line',
          data: trend.map((p) => p.warnTotal),
          showSymbol: true,
          symbolSize: 8,
          lineStyle: { width: 2, color: CURVE_PALETTE[0] },
          itemStyle: { color: CURVE_PALETTE[0] },
          // 选择性直标：仅标注末点，文字墨色不随系列色
          endLabel: { show: true, formatter: '今日', color: '#52514e', fontSize: 11 },
        },
      ],
    },
    { notMerge: true },
  )
}

async function loadDashboard() {
  if (!tunnelId.value) return
  loading.value = true
  try {
    dash.value = await getReportDashboardApi(tunnelId.value)
    renderTrend()
  } finally {
    loading.value = false
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  tunnels.value = await getTunnelListApi()
  chart = echarts.init(chartRef.value as HTMLDivElement)
  window.addEventListener('resize', handleResize)
  // FR-802：驾驶舱 ≤1min 刷新
  timer = setInterval(() => {
    if (tunnelId.value) void loadDashboard()
  }, 60_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (timer) clearInterval(timer)
  chart?.dispose()
  chart = undefined
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

.empty-hint {
  padding: 40px 0;
  text-align: center;
  color: #909399;
}

.mb16 {
  margin-bottom: 16px;
}

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
  background: #f7f8fa;
  border-radius: 6px;
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

.chart-card {
  border: none;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.trend-chart {
  height: 320px;
}
</style>
