<template>
  <div>
    <el-card shadow="never">
      <div class="toolbar">
        <span class="toolbar-title">分析报告列表</span>
        <el-button type="primary" :icon="DocumentAdd" @click="openGenerate">生成报告</el-button>
      </div>

      <el-table v-loading="loading" :data="reports" stripe>
        <el-table-column prop="reportNo" label="报告编号" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ REPORT_TYPE_META[row.reportType] ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="period" label="统计周期" width="120" />
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.generateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Download" @click="downloadReport(row)">
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 生成报告（API-F04） -->
    <el-dialog v-model="generateVisible" title="生成分析报告" width="440px">
      <el-form label-width="90px">
        <el-form-item label="报告类型">
          <el-radio-group v-model="genType" @change="genPeriod = defaultPeriod(genType)">
            <el-radio-button :value="1">日报</el-radio-button>
            <el-radio-button :value="2">周报</el-radio-button>
            <el-radio-button :value="3">月报</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="统计周期">
          <el-input
            v-model="genPeriod"
            :placeholder="PERIOD_PLACEHOLDER[genType]"
            @keyup.enter="submitGenerate"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentAdd, Download } from '@element-plus/icons-vue'

import { downloadReportApi, generateReportApi, getReportListApi } from '@/api/rpt'
import type { RptReportVO } from '@/types/rpt'

const REPORT_TYPE_META: Record<number, string> = {
  1: '日报',
  2: '周报',
  3: '月报',
}

const PERIOD_PLACEHOLDER: Record<number, string> = {
  1: '如 20260930',
  2: '如 202640',
  3: '如 202609',
}

const loading = ref(false)
const reports = ref<RptReportVO[]>([])

function formatDateTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 19)
}

async function loadReports() {
  loading.value = true
  try {
    reports.value = await getReportListApi()
  } finally {
    loading.value = false
  }
}

/* ---------------- 下载（API-F05） ---------------- */
async function downloadReport(row: RptReportVO) {
  const { blob, filename } = await downloadReportApi(row.id, row.reportNo)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/* ---------------- 生成（API-F04） ---------------- */
const generateVisible = ref(false)
const submitting = ref(false)
const genType = ref<number>(1)
const genPeriod = ref('')

function defaultPeriod(type: number): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  if (type === 1) return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`
  if (type === 3) return `${now.getFullYear()}${pad(now.getMonth() + 1)}`
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const day = d.getDay() || 7
  d.setDate(d.getDate() + 4 - day)
  const yearStart = new Date(d.getFullYear(), 0, 1)
  const week = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
  return `${d.getFullYear()}${String(week).padStart(2, '0')}`
}

function openGenerate() {
  genType.value = 1
  genPeriod.value = defaultPeriod(1)
  generateVisible.value = true
}

async function submitGenerate() {
  if (!genPeriod.value) {
    ElMessage.warning('请输入统计周期')
    return
  }
  submitting.value = true
  try {
    await generateReportApi(genType.value, genPeriod.value)
    ElMessage.success('报告生成成功')
    generateVisible.value = false
    await loadReports()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadReports()
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
</style>
