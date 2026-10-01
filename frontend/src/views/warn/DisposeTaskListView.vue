<template>
  <div>
    <!-- 筛选区（API-C16：assigneeId 可选=我的待办 / status 过滤） -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="范围">
          <el-radio-group v-model="scopeMode" @change="handleSearch">
            <el-radio-button value="mine">我的待办</el-radio-button>
            <el-radio-button value="all">全部</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="st in TASK_STATUS_OPTIONS"
              :key="st"
              :label="TASK_STATUS_META[st]?.label ?? String(st)"
              :value="st"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表区 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="taskNo" label="任务编号" width="150" />
        <el-table-column prop="eventId" label="关联事件ID" width="100" />
        <el-table-column label="责任人ID" width="100">
          <template #default="{ row }">
            <span :class="{ 'assignee-me': row.assigneeId === auth.user?.id }">{{ row.assigneeId }}</span>
            <el-tag v-if="row.assigneeId === auth.user?.id" size="small" type="primary" class="ml4">我</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="measure" label="处置措施" min-width="240" show-overflow-tooltip />
        <el-table-column label="完成时限" width="165">
          <template #default="{ row }">{{ formatDateTime(row.deadline) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="TASK_STATUS_META[row.status]?.tag ?? 'info'">
              {{ TASK_STATUS_META[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.finishTime) }}</template>
        </el-table-column>
        <!-- 任务操作显隐：状态机（1→2→3）+ 仅责任人（taskActionsOf 与 DisposeTaskService 校验一致） -->
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="hasWarnPerm('warn:event:confirm') && taskActionsOf(row, auth.user?.id).start"
              link
              type="primary"
              @click="handleStart(row)"
            >
              开始处置
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:confirm') && taskActionsOf(row, auth.user?.id).feedback"
              link
              type="warning"
              @click="openFeedback(row)"
            >
              处置反馈
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:confirm') && taskActionsOf(row, auth.user?.id).finish"
              link
              type="success"
              @click="handleFinish(row)"
            >
              完成处置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="mt16"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 处置反馈对话框（API-C18，FR-403 分次反馈：content + images 路径数组；
         multipart 上传待 W8 对象存储接入后启用，本期 images 以每行一个路径文本输入） -->
    <el-dialog v-model="feedbackVisible" title="处置反馈" width="520px">
      <el-form label-width="90px">
        <el-form-item label="反馈内容" required>
          <el-input
            v-model="feedbackForm.content"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="如：现场已停止作业，渗水量约3L/s，已布设临时排水"
          />
        </el-form-item>
        <el-form-item label="现场照片">
          <el-input
            v-model="feedbackImagesText"
            type="textarea"
            :rows="3"
            placeholder="每行一个图片路径（JSON 数组入库；文件上传待 W8 对象存储接入后启用）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import {
  feedbackDisposeTaskApi,
  finishDisposeTaskApi,
  getDisposeTaskPageApi,
  startDisposeTaskApi,
} from '@/api/warn'
import type { DisposeFeedbackForm, DisposeTaskVO } from '@/types/warn'
import { useAuthStore } from '@/stores/auth'
import { TASK_STATUS_META, formatDateTime, hasWarnPerm, taskActionsOf } from './warnMeta'

const auth = useAuthStore()

const TASK_STATUS_OPTIONS = [1, 2, 3, 4]

/* ---------------- 列表与筛选（API-C16：assigneeId 可选=我的待办） ---------------- */
const loading = ref(false)
const list = ref<DisposeTaskVO[]>([])
const total = ref(0)
const scopeMode = ref<'mine' | 'all'>('mine')
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: '' as number | '',
})

async function loadList() {
  loading.value = true
  try {
    const data = await getDisposeTaskPageApi({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      assigneeId: scopeMode.value === 'mine' ? auth.user?.id : undefined,
      status: typeof query.status === 'number' ? query.status : undefined,
    })
    list.value = data?.list ?? []
    total.value = data?.total ?? 0
  } catch {
    // 失败提示由 http.ts 统一处理
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.status = ''
  query.pageNum = 1
  void loadList()
}

/* ---------------- 开始处置（API-C17：仅责任人，1→2） ---------------- */
async function handleStart(row: DisposeTaskVO) {
  try {
    await ElMessageBox.confirm(`确定开始处置任务「${row.taskNo}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await startDisposeTaskApi(row.id)
    ElMessage.success('已开始处置，任务转入处置中')
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0403 非任务责任人 / B0402 状态不允许）
  }
}

/* ---------------- 完成处置（API-C19：2→3，事件转待复核） ---------------- */
async function handleFinish(row: DisposeTaskVO) {
  try {
    await ElMessageBox.confirm(
      `确定完成任务「${row.taskNo}」吗？完成后关联预警事件将转入「待复核」。`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await finishDisposeTaskApi(row.id)
    ElMessage.success('处置完成，事件转入待复核')
    void loadList()
  } catch {
    // 失败提示由 http.ts 统一处理
  }
}

/* ---------------- 处置反馈（API-C18，FR-403 分次反馈：仅处置中） ---------------- */
const feedbackVisible = ref(false)
const feedbackSubmitting = ref(false)
const feedbackTaskId = ref<number | null>(null)
const feedbackForm = reactive<DisposeFeedbackForm>({ content: '', images: [] })
/** 图片路径文本（每行一个），提交时转数组再序列化 */
const feedbackImagesText = ref('')

function openFeedback(row: DisposeTaskVO) {
  feedbackTaskId.value = row.id
  feedbackForm.content = ''
  feedbackImagesText.value = ''
  feedbackVisible.value = true
}

async function submitFeedback() {
  if (!feedbackForm.content.trim()) {
    ElMessage.warning('请填写反馈内容（必填，后端校验）')
    return
  }
  if (feedbackTaskId.value == null) return
  const images = feedbackImagesText.value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
  feedbackSubmitting.value = true
  try {
    await feedbackDisposeTaskApi(feedbackTaskId.value, {
      content: feedbackForm.content.trim(),
      images,
    })
    ElMessage.success('反馈已提交（可分次反馈，时间线已留痕）')
    feedbackVisible.value = false
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0402 仅处置中可反馈）
  } finally {
    feedbackSubmitting.value = false
  }
}

onMounted(() => {
  void loadList()
})
</script>

<style scoped>
.assignee-me {
  font-weight: 600;
}

.ml4 {
  margin-left: 4px;
}
</style>
