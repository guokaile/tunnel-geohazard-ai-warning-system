<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">统计报表</span>
        <div class="toolbar-actions">
          <el-radio-group v-model="statType" @change="onTypeChange">
            <el-radio-button :value="1">日报</el-radio-button>
            <el-radio-button :value="2">周报</el-radio-button>
            <el-radio-button :value="3">月报</el-radio-button>
          </el-radio-group>
          <el-input
            v-model="period"
            :placeholder="PERIOD_PLACEHOLDER[statType]"
            style="width: 180px"
            @keyup.enter="loadStatistics"
          />
          <el-select
            v-model="tunnelId"
            placeholder="全部隧道"
            clearable
            filterable
            style="width: 200px"
            @change="loadStatistics"
          >
            <el-option
              v-for="t in tunnels"
              :key="t.id"
              :label="`${t.tunnelCode} ${t.tunnelName}`"
              :value="t.id"
            />
          </el-select>
          <el-button type="primary" :icon="Search" @click="loadStatistics">查询</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="!stat" class="empty-hint">
          选择统计周期后点击查询（日=YYYYMMDD，周=YYYYWW，月=YYYYMM）
        </div>
        <template v-else>
          <!-- 预警指标 -->
          <div class="group-title">预警与处置</div>
          <div class="stat-row mb16">
            <div v-for="tile in warnTiles" :key="tile.label" class="stat-tile">
              <div class="stat-body">
                <div class="stat-value">{{ tile.value }}</div>
                <div class="stat-label">{{ tile.label }}</div>
              </div>
            </div>
          </div>
          <!-- 巡检与隐患指标 -->
          <div class="group-title">巡检与隐患</div>
          <div class="stat-row mb16">
            <div v-for="tile in patrolTiles" :key="tile.label" class="stat-tile">
              <div class="stat-body">
                <div class="stat-value">{{ tile.value }}</div>
                <div class="stat-label">{{ tile.label }}</div>
              </div>
            </div>
          </div>
          <!-- 采样指标 -->
          <div class="group-title">监测采样</div>
          <div class="stat-row">
            <div v-for="tile in sampleTiles" :key="tile.label" class="stat-tile">
              <div class="stat-body">
                <div class="stat-value">{{ tile.value }}</div>
                <div class="stat-label">{{ tile.label }}</div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

import { getReportStatisticsApi } from '@/api/rpt'
import { getTunnelListApi } from '@/api/mon'
import type { ReportStatVO } from '@/types/rpt'
import type { TunnelVO } from '@/types/mon'

const PERIOD_PLACEHOLDER: Record<number, string> = {
  1: '如 20260930',
  2: '如 202640',
  3: '如 202609',
}

const tunnels = ref<TunnelVO[]>([])
const statType = ref<number>(1)
const period = ref('')
const tunnelId = ref<number>()
const loading = ref(false)
const stat = ref<ReportStatVO>()

/** 周期默认值：日=今天 YYYYMMDD；月=当月 YYYYMM；周=本周 YYYYWW（ISO 周一为一周首日） */
function defaultPeriod(type: number): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  if (type === 1) return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
  if (type === 3) return `${now.getFullYear()}${pad(now.getMonth() + 1)}`
  // ISO 周编号：一年首个周四所在的周为第 1 周
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const day = d.getDay() || 7
  d.setDate(d.getDate() + 4 - day)
  const yearStart = new Date(d.getFullYear(), 0, 1)
  const week = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
  return `${d.getFullYear()}${String(week).padStart(2, '0')}`
}

function onTypeChange() {
  period.value = defaultPeriod(statType.value)
  void loadStatistics()
}

function pctOf(v: number | undefined): string {
  if (v == null) return '-'
  const value = v <= 1 ? v * 100 : v
  return `${value.toFixed(1)}%`
}

function numOf(v: number | undefined): string {
  return v == null ? '-' : v.toLocaleString()
}

interface Tile {
  label: string
  value: string
}

const warnTiles = computed<Tile[]>(() => [
  { label: '预警总数', value: numOf(stat.value?.warnTotal) },
  { label: '红色预警', value: numOf(stat.value?.warnRed) },
  { label: '已确认', value: numOf(stat.value?.warnConfirmed) },
  { label: '已消警', value: numOf(stat.value?.warnClosed) },
  { label: '处置任务', value: numOf(stat.value?.disposeTotal) },
  { label: '处置闭环', value: numOf(stat.value?.disposeClosed) },
  { label: '闭环率', value: pctOf(stat.value?.closeRate) },
])

const patrolTiles = computed<Tile[]>(() => [
  { label: '巡检任务', value: numOf(stat.value?.patrolTotal) },
  { label: '已完成', value: numOf(stat.value?.patrolDone) },
  { label: '完成率', value: pctOf(stat.value?.patrolRate) },
  { label: '新增隐患', value: numOf(stat.value?.hazardNew) },
  { label: '隐患闭环', value: numOf(stat.value?.hazardClosed) },
])

const sampleTiles = computed<Tile[]>(() => [
  { label: '采样条数', value: numOf(stat.value?.sampleCount) },
  { label: '应采条数', value: numOf(stat.value?.sampleExpect) },
  { label: '采样完整率', value: pctOf(stat.value?.sampleRate) },
])

async function loadStatistics() {
  if (!period.value) return
  loading.value = true
  try {
    stat.value = await getReportStatisticsApi({
      type: statType.value,
      period: period.value,
      tunnelId: tunnelId.value,
    })
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  tunnels.value = await getTunnelListApi()
  period.value = defaultPeriod(statType.value)
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

.group-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 4px 0 12px;
}

.mb16 {
  margin-bottom: 16px;
}

.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.stat-tile {
  flex: 1;
  min-width: 140px;
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
</style>
