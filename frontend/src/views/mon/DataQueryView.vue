<template>
  <div>
    <!-- 查询条件：隧道 → 点位多选（B05 分页+关键字远程搜索）→ 时间范围 -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="隧道">
          <el-select v-model="tunnelId" placeholder="全部隧道" clearable style="width: 180px" @change="handleTunnelChange">
            <el-option
              v-for="tunnel in tunnels"
              :key="tunnel.id"
              :label="`${tunnel.tunnelCode} ${tunnel.tunnelName}`"
              :value="tunnel.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="点位（多选）">
          <!-- B05 远程搜索 + 多选：支持多测项同图对比（FR-203），上限 = 色板槽位数 -->
          <el-select
            v-model="selectedPoints"
            multiple
            filterable
            remote
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="3"
            :remote-method="searchPoints"
            :loading="pointSearchLoading"
            value-key="id"
            placeholder="输入点位编码 / 名称搜索"
            style="width: 420px"
            @focus="searchPoints('')"
          >
            <el-option
              v-for="point in pointOptions"
              :key="point.id"
              :label="`${point.pointCode} ${point.pointName}`"
              :value="point"
            >
              <span class="option-code">{{ point.pointCode }}</span>
              <span class="option-name">{{ point.pointName }}</span>
              <span class="option-item">{{ itemLabel(point.itemType) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :default-time="DEFAULT_TIME"
            :shortcuts="RANGE_SHORTCUTS"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="querying" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 时序曲线（B17，多测项对比：多 series + 图例切换 + dataZoom） -->
    <el-card shadow="never" class="mb16">
      <template #header>
        <div class="chart-header">
          <span>时序曲线</span>
          <span v-if="results.length" class="chart-sub">
            粒度：{{ granularityUsed === 'minute' ? '分钟（minute）' : '原始（raw）' }} ·
            {{ results.map((r) => r.series.length).reduce((a, b) => a + b, 0) }} 个样本点
          </span>
        </div>
      </template>
      <div ref="chartRef" class="query-chart"></div>
    </el-card>

    <!-- 统计值（B18：max/min/avg/rate，每个选中点位一组） -->
    <el-card v-if="results.length" shadow="never">
      <template #header><span>统计值</span></template>
      <el-table v-loading="querying" :data="statsRows" stripe>
        <el-table-column label="点位" min-width="200">
          <template #default="{ row }">
            <span class="point-code">{{ row.point.pointCode }}</span>
            <span class="point-name">{{ row.point.pointName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最大值" min-width="120" align="right">
          <template #default="{ row }">{{ withUnit(row.stats.max, row.point.unit) }}</template>
        </el-table-column>
        <el-table-column label="最小值" min-width="120" align="right">
          <template #default="{ row }">{{ withUnit(row.stats.min, row.point.unit) }}</template>
        </el-table-column>
        <el-table-column label="平均值" min-width="120" align="right">
          <template #default="{ row }">{{ withUnit(row.stats.avg, row.point.unit) }}</template>
        </el-table-column>
        <el-table-column label="变化速率" min-width="140" align="right">
          <template #default="{ row }">{{ withUnit(row.stats.rate, `${row.point.unit || ''}/h`) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

import { getPointPageApi, getPointSeriesApi, getPointStatsApi, getTunnelListApi } from '@/api/mon'
import type { PointStatsVO, PointVO, SeriesPointVO, TunnelVO } from '@/types/mon'
import { CURVE_MAX, CURVE_PALETTE, formatValue, itemLabel } from './monMeta'

/** 查询结果行（点位 + 时序 + 统计） */
interface CurveResult {
  point: PointVO
  series: SeriesPointVO[]
  stats: PointStatsVO
}

/** 日期范围选择器默认时分：起 00:00:00 止 23:59:59 */
const DEFAULT_TIME: Date[] = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

/** 快捷时段（FR-203 历史查询常用窗口；>7 天自动切 minute 粒度） */
const RANGE_SHORTCUTS = [
  { text: '近1小时', value: () => [new Date(Date.now() - 3600e3), new Date()] },
  { text: '近6小时', value: () => [new Date(Date.now() - 6 * 3600e3), new Date()] },
  { text: '近24小时', value: () => [new Date(Date.now() - 24 * 3600e3), new Date()] },
  { text: '近7天', value: () => [new Date(Date.now() - 7 * 24 * 3600e3), new Date()] },
  { text: '近30天', value: () => [new Date(Date.now() - 30 * 24 * 3600e3), new Date()] },
]

/** 本地时间 → ISO（后端 LocalDateTime.parse 仅接受 ISO；date-picker 的 value-format 已输出 ISO，此处仅用于默认值） */
function isoLocal(d: Date): string {
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/* ---------------- 条件区 ---------------- */
const tunnels = ref<TunnelVO[]>([])
const tunnelId = ref<number>()
const selectedPoints = ref<PointVO[]>([])
const pointOptions = ref<PointVO[]>([])
const pointSearchLoading = ref(false)
/** 时间范围（ISO 串对；默认近 1 小时，B17 raw 粒度） */
const timeRange = ref<string[] | null>([isoLocal(new Date(Date.now() - 3600e3)), isoLocal(new Date())])

/** B05 远程搜索：关键字 + 隧道过滤，每次取前 50 条 */
async function searchPoints(keyword: string) {
  pointSearchLoading.value = true
  try {
    const data = await getPointPageApi({
      pageNum: 1,
      pageSize: 50,
      keyword: keyword || undefined,
      tunnelId: tunnelId.value,
    })
    pointOptions.value = data?.list ?? []
  } catch {
    pointOptions.value = []
  } finally {
    pointSearchLoading.value = false
  }
}

function handleTunnelChange() {
  // 隧道切换后重搜点位，避免跨隧道误选
  selectedPoints.value = selectedPoints.value.filter((p) => p.tunnelId === tunnelId.value)
  void searchPoints('')
}

/* ---------------- 查询与图表 ---------------- */
const results = ref<CurveResult[]>([])
const querying = ref(false)
const granularityUsed = ref<'raw' | 'minute'>('raw')

async function handleQuery() {
  if (selectedPoints.value.length === 0) {
    ElMessage.warning('请先选择点位')
    return
  }
  if (selectedPoints.value.length > CURVE_MAX) {
    ElMessage.warning(`最多同时对比 ${CURVE_MAX} 个点位（色板槽位上限）`)
    return
  }
  if (!timeRange.value || timeRange.value.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }
  const [from, to] = timeRange.value
  const spanMs = new Date(to).getTime() - new Date(from).getTime()
  // B17 契约：raw 粒度最长 7 天（超限后端 B0116）；前端预判 >7 天自动切 minute 并提示
  const useMinute = spanMs > 7 * 24 * 3600 * 1000
  granularityUsed.value = useMinute ? 'minute' : 'raw'
  if (useMinute) {
    ElMessage.info('查询窗口超过 7 天，已自动切换为分钟（minute）粒度')
  }
  querying.value = true
  try {
    // 每个点位并发拉取时序（B17）与统计（B18）
    const list = await Promise.all(
      selectedPoints.value.map(async (point) => {
        const [series, stats] = await Promise.all([
          getPointSeriesApi(point.id, { from, to, granularity: granularityUsed.value }),
          getPointStatsApi(point.id, { from, to }),
        ])
        return { point, series: series ?? [], stats }
      }),
    )
    results.value = list
    renderChart()
  } catch {
    results.value = []
    renderChart()
  } finally {
    querying.value = false
  }
}

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

/** 统计表行（直接映射 results，列内取用） */
const statsRows = computed(() => results.value)

/** 数值 + 单位（单位空则仅数值） */
function withUnit(value: number, unit?: string | null): string {
  return unit ? `${formatValue(value)} ${unit}` : formatValue(value)
}

function renderChart() {
  if (!chart) return
  const list = results.value
  if (list.length === 0) {
    chart.setOption(
      {
        title: {
          text: '请选择点位与时间范围后点击查询',
          left: 'center',
          top: 'middle',
          textStyle: { color: '#909399', fontSize: 14 },
        },
        series: [],
        xAxis: { type: 'time' },
        yAxis: { type: 'value' },
      },
      { notMerge: true },
    )
    return
  }
  const allEmpty = list.every((r) => r.series.length === 0)
  if (allEmpty) {
    chart.setOption(
      {
        title: {
          text: '所选时段无数据',
          left: 'center',
          top: 'middle',
          textStyle: { color: '#909399', fontSize: 14 },
        },
        series: [],
        xAxis: { type: 'time' },
        yAxis: { type: 'value' },
      },
      { notMerge: true },
    )
    return
  }
  // 全部点位单位一致时，y 轴标注单位
  const units = new Set(list.map((r) => r.point.unit ?? ''))
  const axisName = units.size === 1 ? [...units][0] : undefined
  const series = list.map((r, i) => ({
    name: `${r.point.pointCode} ${r.point.pointName}`,
    type: 'line' as const,
    data: r.series.map((s) => [s.ts, s.value]),
    showSymbol: false,
    lineStyle: { width: 2, color: CURVE_PALETTE[i] },
    itemStyle: { color: CURVE_PALETTE[i] },
    emphasis: { focus: 'series' as const },
    // ≤4 条曲线时末端直接标注（选择性直标，文字用墨色不随系列色）；>4 条由图例 + 统计表兜底
    endLabel:
      list.length <= 4
        ? { show: true, formatter: r.point.pointName, color: '#52514e', fontSize: 11 }
        : undefined,
  }))
  chart.setOption(
    {
      title: undefined,
      // 固定色板槽位顺序：颜色跟随点位实体，不随选择次序变化
      color: CURVE_PALETTE,
      legend: list.length > 1 ? { data: series.map((s) => s.name), top: 4 } : undefined,
      tooltip: { trigger: 'axis' },
      grid: { left: 70, right: 30, top: list.length > 1 ? 44 : 24, bottom: 74 },
      xAxis: { type: 'time' },
      yAxis: { type: 'value', scale: true, name: axisName },
      dataZoom: [
        { type: 'inside', start: 0, end: 100 },
        { type: 'slider', height: 20, bottom: 12 },
      ],
      series,
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
  renderChart()
  getTunnelListApi()
    .then((data) => {
      tunnels.value = data ?? []
    })
    .catch(() => {
      tunnels.value = []
    })
  void searchPoints('')
})

onBeforeUnmount(() => {
  // 清理：图表实例与 resize 监听（防泄漏）
  window.removeEventListener('resize', handleResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.chart-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.chart-sub {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.query-chart {
  width: 100%;
  height: 420px;
}

.option-code {
  font-family: Consolas, Menlo, monospace;
  font-size: 13px;
  color: #303133;
}

.option-name {
  margin-left: 8px;
  font-size: 13px;
  color: #606266;
}

.option-item {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

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
</style>
