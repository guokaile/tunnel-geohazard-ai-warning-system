<template>
  <div>
    <!-- 预警统计（API-C04）：折叠面板，首次展开时初始化 ECharts（折叠态容器宽高为 0，不可提前 init） -->
    <el-collapse v-model="statsActiveNames" class="mb16">
      <el-collapse-item name="stats">
        <template #title>
          <el-icon class="mr4"><DataAnalysis /></el-icon>
          预警统计
        </template>

        <el-form inline @submit.prevent>
          <el-form-item label="统计范围">
            <el-date-picker
              v-model="statsRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" :loading="statsLoading" @click="loadStats">
              统计
            </el-button>
          </el-form-item>
        </el-form>

        <el-empty v-if="!statsLoading && statsTotal === 0" description="统计区间内暂无预警事件" />
        <el-row v-else :gutter="16">
          <!-- 按级别（四级色标饼图：色标均带名称标签/图例，不单靠颜色传达） -->
          <el-col :span="8">
            <div class="chart-title">按级别分布</div>
            <div ref="levelChartRef" class="chart-box" />
          </el-col>
          <!-- 按灾种（单一主色柱状图，数值直接标注） -->
          <el-col :span="8">
            <div class="chart-title">按灾种分布</div>
            <div ref="hazardChartRef" class="chart-box" />
          </el-col>
          <!-- 按隧道（后端暂无隧道名称接口，暂显隧道 ID，TODO：mon 域接口就绪后换名称） -->
          <el-col :span="8">
            <div class="chart-title">按隧道分布</div>
            <div ref="tunnelChartRef" class="chart-box" />
          </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>

    <!-- 筛选区（API-C01 入参：tunnelId/level/status/hazardType/时间范围/keyword） -->
    <el-card shadow="never" class="mb16">
      <el-form inline @submit.prevent>
        <el-form-item label="隧道ID">
          <el-input-number
            v-model="query.tunnelId"
            :min="1"
            :controls="false"
            placeholder="全部"
            style="width: 110px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="lv in LEVEL_OPTIONS" :key="lv" :label="`${levelLabel(lv)}级`" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="st in STATUS_OPTIONS"
              :key="st"
              :label="WARN_STATUS_META[st]?.label ?? String(st)"
              :value="st"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="灾种">
          <el-select v-model="query.hazardType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="ht in HAZARD_OPTIONS" :key="ht" :label="hazardLabel(ht)" :value="ht" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="事件编号 / 标题"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
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
        <el-table-column prop="eventNo" label="事件编号" width="150" />
        <el-table-column prop="warnTitle" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.warnTitle }}</span>
            <!-- 门禁阶段标记（影子期/灰度期，独立于状态机） -->
            <el-tag v-if="row.gateStage === 1 || row.gateStage === 2" size="small" type="warning" class="ml4">
              {{ GATE_STAGE_META[row.gateStage] }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 四级色标：自定义背景色 tag（色值见 warnMeta.WARN_LEVEL_META 注释） -->
        <el-table-column label="级别" width="80">
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
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="WARN_STATUS_META[row.warnStatus]?.tag ?? 'info'">
              {{ WARN_STATUS_META[row.warnStatus]?.label ?? row.warnStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="灾种" width="130">
          <template #default="{ row }">{{ hazardLabel(row.hazardType) }}</template>
        </el-table-column>
        <el-table-column prop="tunnelId" label="隧道ID" width="80" />
        <el-table-column label="触发值" width="100">
          <template #default="{ row }">{{ row.triggerValue || '-' }}</template>
        </el-table-column>
        <el-table-column label="触发时间" width="165">
          <template #default="{ row }">{{ formatDateTime(row.triggerTime) }}</template>
        </el-table-column>
        <!-- 行操作按状态机显隐（eventActionsOf 与后端条件 UPDATE 的 WHERE 一致） -->
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:confirm') && eventActionsOf(row).confirm"
              link
              type="warning"
              @click="openConfirm(row)"
            >
              确认/误报
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:dispatch') && eventActionsOf(row).dispatch"
              link
              type="primary"
              @click="openDispatch(row)"
            >
              派单
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:updown') && eventActionsOf(row).upgrade"
              link
              type="danger"
              @click="openLevelChange(row, 'upgrade')"
            >
              升级
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:updown') && eventActionsOf(row).downgrade"
              link
              type="primary"
              @click="openLevelChange(row, 'downgrade')"
            >
              降级
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:close') && eventActionsOf(row).close"
              link
              type="success"
              @click="openClose(row)"
            >
              消警
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

    <!-- 事件详情抽屉：基本信息（C02）/ 时间线（C03）/ 通知记录（C20）三 Tab + 状态机操作区（C11~C15） -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="720px"
      destroy-on-close
      @closed="handleDrawerClosed"
    >
      <el-tabs v-model="detailTab">
        <!-- 基本信息（API-C02 详情） -->
        <el-tab-pane label="基本信息" name="info">
          <template v-if="detailEvent">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="事件编号">{{ detailEvent.eventNo }}</el-descriptions-item>
              <el-descriptions-item label="事件ID">{{ detailEvent.id }}</el-descriptions-item>
              <el-descriptions-item label="级别">
                <el-tag
                  size="small"
                  :style="{
                    backgroundColor: WARN_LEVEL_META[detailEvent.warnLevel]?.color ?? '#909399',
                    borderColor: WARN_LEVEL_META[detailEvent.warnLevel]?.color ?? '#909399',
                    color: '#fff',
                  }"
                >
                  {{ levelLabel(detailEvent.warnLevel) }}级
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag size="small" :type="WARN_STATUS_META[detailEvent.warnStatus]?.tag ?? 'info'">
                  {{ WARN_STATUS_META[detailEvent.warnStatus]?.label ?? detailEvent.warnStatus }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="灾种">{{ hazardLabel(detailEvent.hazardType) }}</el-descriptions-item>
              <el-descriptions-item label="测项">{{ detailEvent.itemType }}</el-descriptions-item>
              <el-descriptions-item label="隧道ID">{{ detailEvent.tunnelId }}</el-descriptions-item>
              <el-descriptions-item label="断面ID">{{ detailEvent.sectionId ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="点位ID">{{ detailEvent.pointId ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="规则ID">{{ detailEvent.ruleId ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="门禁阶段">
                {{ GATE_STAGE_META[detailEvent.gateStage ?? 0] ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="关联灾害登记">{{ detailEvent.hazardEventId ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="触发值">{{ detailEvent.triggerValue || '-' }}</el-descriptions-item>
              <el-descriptions-item label="触发时间">{{ formatDateTime(detailEvent.triggerTime) }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(detailEvent.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="标题" :span="2">{{ detailEvent.warnTitle }}</el-descriptions-item>
              <el-descriptions-item label="内容" :span="2">{{ detailEvent.warnContent }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <el-skeleton v-else :rows="8" animated />
        </el-tab-pane>

        <!-- 时间线（API-C03，FR-406 全生命周期留痕：生成→通知→确认→派单→反馈→消警） -->
        <el-tab-pane label="时间线" name="timeline">
          <el-timeline v-if="timeline.length" class="tl-wrap">
            <!-- hollow：系统节点（actorType=1）空心点，人工操作实心点，一眼区分自动/人工流转 -->
            <el-timeline-item
              v-for="node in timeline"
              :key="node.id"
              placement="top"
              :color="TIMELINE_NODE_META[node.nodeType]?.color ?? '#909399'"
              :hollow="node.actorType === 1"
              :timestamp="formatDateTime(node.occurTime)"
            >
              <div class="tl-row">
                <el-icon :size="14" :color="TIMELINE_NODE_META[node.nodeType]?.color ?? '#909399'">
                  <component :is="TIMELINE_NODE_META[node.nodeType]?.icon ?? 'Bell'" />
                </el-icon>
                <el-tag size="small" effect="plain">{{ timelineNodeLabel(node.nodeType) }}</el-tag>
                <span class="tl-actor">{{ node.actorName || (node.actorType === 1 ? '系统' : '人工') }}</span>
              </div>
              <div class="tl-action">{{ node.action }}</div>
              <div v-if="node.detail" class="tl-detail">{{ node.detail }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else-if="!timelineLoading" description="暂无时间线记录" />
          <el-skeleton v-else :rows="5" animated />
        </el-tab-pane>

        <!-- 通知记录（API-C20：eventId 必填；首次切入本 Tab 才加载） -->
        <el-tab-pane label="通知记录" name="notify">
          <el-form inline @submit.prevent>
            <el-form-item label="通道">
              <el-select
                v-model="notifyQuery.channelType"
                placeholder="全部"
                clearable
                style="width: 140px"
                @change="notifySearch"
              >
                <el-option v-for="ch in CHANNEL_OPTIONS" :key="ch" :label="CHANNEL_META[ch]" :value="ch" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-table v-loading="notifyLoading" :data="notifyList" stripe size="small">
            <el-table-column label="通道" width="70">
              <template #default="{ row }">{{ CHANNEL_META[row.channelType] ?? row.channelType }}</template>
            </el-table-column>
            <el-table-column prop="target" label="接收对象" width="150" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="发送状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="NOTIFY_STATUS_META[row.status]?.tag ?? 'info'">
                  {{ NOTIFY_STATUS_META[row.status]?.label ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="投递状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="DELIVER_STATE_META[row.deliverState]?.tag ?? 'info'">
                  {{ DELIVER_STATE_META[row.deliverState]?.label ?? row.deliverState }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="attemptCount" label="尝试次数" width="80" />
            <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.failReason || '-' }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="notifyQuery.pageNum"
            v-model:page-size="notifyQuery.pageSize"
            class="mt16"
            small
            background
            layout="total, prev, pager, next"
            :total="notifyTotal"
            :page-sizes="[10, 20, 50]"
            @size-change="loadNotifyLogs"
            @current-change="loadNotifyLogs"
          />
        </el-tab-pane>
      </el-tabs>

      <!-- 抽屉操作区：与行操作同一套状态机显隐规则，操作完成后自动刷新详情与时间线 -->
      <template #footer>
        <div v-if="detailEvent" class="drawer-footer">
          <template v-if="drawerHasAction">
            <el-button
              v-if="hasWarnPerm('warn:event:confirm') && drawerActions?.confirm"
              type="warning"
              @click="openConfirm(detailEvent)"
            >
              确认/误报
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:dispatch') && drawerActions?.dispatch"
              type="primary"
              @click="openDispatch(detailEvent)"
            >
              派单
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:updown') && drawerActions?.upgrade"
              type="danger"
              @click="openLevelChange(detailEvent, 'upgrade')"
            >
              升级
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:updown') && drawerActions?.downgrade"
              type="primary"
              @click="openLevelChange(detailEvent, 'downgrade')"
            >
              降级
            </el-button>
            <el-button
              v-if="hasWarnPerm('warn:event:close') && drawerActions?.close"
              type="success"
              @click="openClose(detailEvent)"
            >
              复核消警
            </el-button>
          </template>
          <span v-else class="drawer-footer-tip">
            当前状态（{{ WARN_STATUS_META[detailEvent.warnStatus]?.label ?? detailEvent.warnStatus }}）无可用操作
          </span>
        </div>
      </template>
    </el-drawer>

    <!-- 确认/误报对话框（API-C11，FR-401：result 1确认→已确认 / 2误报→误报关闭） -->
    <el-dialog v-model="confirmVisible" title="预警确认 / 误报登记" width="480px">
      <el-form label-width="90px">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="confirmForm.result">
            <el-radio :value="1">确认预警</el-radio>
            <el-radio :value="2">登记误报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="confirmForm.result === 1 ? '确认结论' : '误报说明'" required>
          <el-input
            v-model="confirmForm.conclusion"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="confirmForm.result === 1 ? '如：瓦斯浓度超限属实，已通知现场' : '误报结论必填（后端校验）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmSubmitting" @click="submitConfirm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 升级/降级对话框（API-C12/C13：级别选项按方向限定，reason 留痕） -->
    <el-dialog v-model="levelChangeVisible" :title="levelChangeMode === 'upgrade' ? '人工升级' : '人工降级'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="当前级别">
          <el-tag
            :style="{
              backgroundColor: levelSource ? WARN_LEVEL_META[levelSource.warnLevel]?.color : '#909399',
              borderColor: levelSource ? WARN_LEVEL_META[levelSource.warnLevel]?.color : '#909399',
              color: '#fff',
            }"
          >
            {{ levelSource ? `${levelLabel(levelSource.warnLevel)}级` : '-' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="调整至" required>
          <el-select v-model="levelForm.newLevel" style="width: 160px">
            <el-option v-for="opt in levelOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input
            v-model="levelForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="levelChangeMode === 'upgrade' ? '如：多指标联动异常，现场情况恶化' : '如：现场核查后风险降低'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelChangeVisible = false">取消</el-button>
        <el-button type="primary" :loading="levelSubmitting" @click="submitLevelChange">确定</el-button>
      </template>
    </el-dialog>

    <!-- 派单对话框（API-C15，FR-402：责任人/措施/时限） -->
    <el-dialog v-model="dispatchVisible" title="创建处置任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="责任人ID" required>
          <el-input-number v-model="dispatchForm.assigneeId" :min="1" :controls="false" style="width: 160px" />
          <div class="form-tip">TODO[契约]：后端未实现 sys 域用户接口（API-A06），暂以用户 ID 输入，接口就绪后改为用户选择器</div>
        </el-form-item>
        <el-form-item label="处置措施" required>
          <el-input
            v-model="dispatchForm.measure"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="如：停止该断面作业，加密监测并核查掌子面渗水情况"
          />
        </el-form-item>
        <el-form-item label="完成时限" required>
          <el-date-picker
            v-model="dispatchForm.deadline"
            type="datetime"
            placeholder="选择完成时限"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="dispatchSubmitting" @click="submitDispatch">确定</el-button>
      </template>
    </el-dialog>

    <!-- 复核消警对话框（API-C14，FR-404：reason 必填，后端 B0304 校验） -->
    <el-dialog v-model="closeVisible" title="复核消警" width="480px">
      <el-form label-width="90px">
        <el-form-item label="复核结论" required>
          <el-input
            v-model="closeForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="如：CH4浓度回落至0.1%以下并稳定2小时，现场核查无异常（必填，否则后端 B0304 拒绝）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closeSubmitting" @click="submitClose">确认消警</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

import {
  closeWarnEventApi,
  confirmWarnEventApi,
  dispatchTaskApi,
  downgradeWarnEventApi,
  getNotifyLogPageApi,
  getWarnEventDetailApi,
  getWarnEventPageApi,
  getWarnEventTimelineApi,
  getWarnStatsApi,
  upgradeWarnEventApi,
} from '@/api/warn'
import type {
  NotifyLogQuery,
  NotifyLogVO,
  WarnCloseForm,
  WarnConfirmForm,
  WarnDispatchForm,
  WarnEventVO,
  WarnLevelChangeForm,
  WarnStatsRow,
  WarnTimelineNode,
} from '@/types/warn'
import {
  CHANNEL_META,
  DELIVER_STATE_META,
  GATE_STAGE_META,
  HAZARD_TYPE_META,
  NOTIFY_STATUS_META,
  TIMELINE_NODE_META,
  WARN_LEVEL_META,
  WARN_STATUS_META,
  eventActionsOf,
  formatDateTime,
  hasWarnPerm,
  hazardLabel,
  levelLabel,
  timelineNodeLabel,
  type EventActions,
} from './warnMeta'

const LEVEL_OPTIONS = [1, 2, 3, 4]
const STATUS_OPTIONS = [1, 2, 3, 4, 5, 6]
const HAZARD_OPTIONS = [1, 2, 3, 4, 5, 6]
const CHANNEL_OPTIONS = [1, 2, 3]

/* ---------------- 列表与筛选（API-C01 分页：pageNum/pageSize + tunnelId/level/status/hazardType/时间/keyword） ---------------- */
const loading = ref(false)
const list = ref<WarnEventVO[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  tunnelId: undefined as number | undefined,
  level: '' as number | '',
  status: '' as number | '',
  hazardType: '' as number | '',
  keyword: '',
  timeRange: null as [string, string] | null,
})

async function loadList() {
  loading.value = true
  try {
    const data = await getWarnEventPageApi({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      tunnelId: query.tunnelId ?? undefined,
      level: typeof query.level === 'number' ? query.level : undefined,
      status: typeof query.status === 'number' ? query.status : undefined,
      hazardType: typeof query.hazardType === 'number' ? query.hazardType : undefined,
      keyword: query.keyword || undefined,
      from: query.timeRange?.[0],
      to: query.timeRange?.[1],
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
  query.pageNum = 1
  query.tunnelId = undefined
  query.level = ''
  query.status = ''
  query.hazardType = ''
  query.keyword = ''
  query.timeRange = null
  void loadList()
}

/* ---------------- 预警统计（API-C04：from/to 必填，后端按隧道×级别×对象聚合） ---------------- */
const statsActiveNames = ref<string[]>([])
const statsLoading = ref(false)
const statsRange = ref<[string, string]>(defaultStatsRange())
const statsByLevel = ref<Record<number, number>>({})
const statsByHazard = ref<Record<number, number>>({})
const statsByTunnel = ref<{ id: number; cnt: number }[]>([])
const statsTotal = ref(0)

/** 默认统计最近 7 天 */
function defaultStatsRange(): [string, string] {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 3600 * 1000)
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ` +
    `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  return [fmt(start), fmt(end)]
}

async function loadStats() {
  if (!statsRange.value?.[0] || !statsRange.value?.[1]) {
    ElMessage.warning('请选择统计时间范围')
    return
  }
  statsLoading.value = true
  try {
    const rows = (await getWarnStatsApi(statsRange.value[0], statsRange.value[1])) ?? []
    aggregateStats(rows)
    if (chartsInited.value) renderCharts()
  } catch {
    // 失败提示由 http.ts 统一处理
  } finally {
    statsLoading.value = false
  }
}

/** 聚合后端 GROUP BY 行 → 三个图表的数据（级别 1~4 / 灾种 1~6 / 隧道 Top10） */
function aggregateStats(rows: WarnStatsRow[]) {
  const byLevel: Record<number, number> = {}
  const byHazard: Record<number, number> = {}
  const byTunnel = new Map<number, number>()
  let totalCnt = 0
  for (const row of rows) {
    byLevel[row.level] = (byLevel[row.level] ?? 0) + row.cnt
    byHazard[row.hazardType] = (byHazard[row.hazardType] ?? 0) + row.cnt
    byTunnel.set(row.tunnelId, (byTunnel.get(row.tunnelId) ?? 0) + row.cnt)
    totalCnt += row.cnt
  }
  statsByLevel.value = byLevel
  statsByHazard.value = byHazard
  statsByTunnel.value = [...byTunnel.entries()]
    .map(([id, cnt]) => ({ id, cnt }))
    .sort((a, b) => b.cnt - a.cnt)
    .slice(0, 10)
  statsTotal.value = totalCnt
}

/* ---------------- ECharts（折叠面板首次展开时 init，避免隐藏态宽高为 0） ---------------- */
const levelChartRef = ref<HTMLDivElement | null>(null)
const hazardChartRef = ref<HTMLDivElement | null>(null)
const tunnelChartRef = ref<HTMLDivElement | null>(null)
const chartsInited = ref(false)
let levelChart: echarts.ECharts | null = null
let hazardChart: echarts.ECharts | null = null
let tunnelChart: echarts.ECharts | null = null

function resizeCharts() {
  levelChart?.resize()
  hazardChart?.resize()
  tunnelChart?.resize()
}

function initCharts() {
  if (!levelChartRef.value || !hazardChartRef.value || !tunnelChartRef.value) return
  levelChart = echarts.init(levelChartRef.value)
  hazardChart = echarts.init(hazardChartRef.value)
  tunnelChart = echarts.init(tunnelChartRef.value)
  window.addEventListener('resize', resizeCharts)
  renderCharts()
}

/** 按级别饼图：四级色标为领域状态色（蓝/黄/橙/红），图例+切片标签双标识，不单靠颜色 */
function renderLevelChart() {
  const data = LEVEL_OPTIONS.map((lv) => ({
    name: `${levelLabel(lv)}级`,
    value: statsByLevel.value[lv] ?? 0,
    itemStyle: { color: WARN_LEVEL_META[lv]?.color ?? '#909399' },
  }))
  levelChart?.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 10 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b} {c}起 ({d}%)' },
        data,
      },
    ],
  })
}

/** 按灾种柱状图：单一主色 + 数值直接标注（单序列无需图例，标题即命名） */
function renderHazardChart() {
  const names = HAZARD_OPTIONS.map((ht) => HAZARD_TYPE_META[ht] ?? String(ht))
  const values = HAZARD_OPTIONS.map((ht) => statsByHazard.value[ht] ?? 0)
  hazardChart?.setOption({
    grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: names,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266', interval: 0, rotate: 22 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        data: values,
        itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] },
        label: {
          show: true,
          position: 'top',
          color: '#606266',
          formatter: (p: { value: number }) => (Number(p.value) > 0 ? String(p.value) : ''),
        },
      },
    ],
  })
}

/** 按隧道柱状图（Top10）：后端暂无隧道名称，暂显隧道 ID */
function renderTunnelChart() {
  const rows = statsByTunnel.value
  tunnelChart?.setOption({
    grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => String(r.id)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#606266' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f0f2f5' } },
      axisLabel: { color: '#909399' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 20,
        data: rows.map((r) => r.cnt),
        itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', color: '#606266' },
      },
    ],
  })
}

function renderCharts() {
  renderLevelChart()
  renderHazardChart()
  renderTunnelChart()
}

/* ---------------- 事件详情抽屉（API-C02 详情 + API-C03 时间线 + API-C20 通知记录） ---------------- */
const drawerVisible = ref(false)
const drawerEventId = ref<number | null>(null)
const detailTab = ref('info')
const detailEvent = ref<WarnEventVO | null>(null)
const timeline = ref<WarnTimelineNode[]>([])
const timelineLoading = ref(false)

const drawerTitle = computed(() =>
  detailEvent.value ? `预警事件 ${detailEvent.value.eventNo}` : '预警事件详情',
)

/** 抽屉操作区显隐（与行操作同一套 eventActionsOf 状态机规则） */
const drawerActions = computed<EventActions | null>(() =>
  detailEvent.value ? eventActionsOf(detailEvent.value) : null,
)
const drawerHasAction = computed(() => {
  const a = drawerActions.value
  return !!a && (a.confirm || a.dispatch || a.upgrade || a.downgrade || a.close)
})

async function openDetail(row: WarnEventVO) {
  drawerEventId.value = row.id
  detailTab.value = 'info'
  // 先以列表行数据填充（列表含 warnTitle/warnContent/createTime），详情返回后再合并
  detailEvent.value = { ...row }
  // 重置通知记录加载标记：切换事件后重新懒加载（destroy-on-close 不清组件状态）
  notifyLoaded.value = false
  notifyQuery.pageNum = 1
  notifyList.value = []
  drawerVisible.value = true
  await Promise.all([loadDetail(row.id), loadTimeline(row.id)])
}

function handleDrawerClosed() {
  drawerEventId.value = null
  detailEvent.value = null
  timeline.value = []
}

async function loadDetail(id: number) {
  try {
    const detail = await getWarnEventDetailApi(id)
    // TODO[契约]：后端 API-C02 selectById 未查询 warn_title/warn_content/create_time 三列，
    // 返回值为 null——以列表行数据兜底合并展示，后端补齐后合并逻辑自动退化为详情值优先
    const current = detailEvent.value
    detailEvent.value = {
      ...detail,
      warnTitle: detail.warnTitle ?? current?.warnTitle ?? '',
      warnContent: detail.warnContent ?? current?.warnContent ?? '',
      createTime: detail.createTime ?? current?.createTime ?? '',
    }
  } catch {
    // 详情获取失败时保留列表行数据
  }
}

async function loadTimeline(id: number) {
  timelineLoading.value = true
  try {
    timeline.value = (await getWarnEventTimelineApi(id)) ?? []
  } catch {
    timeline.value = []
  } finally {
    timelineLoading.value = false
  }
}

/* 通知记录（API-C20，首次切入 Tab 才加载） */
const notifyLoading = ref(false)
const notifyList = ref<NotifyLogVO[]>([])
const notifyTotal = ref(0)
const notifyLoaded = ref(false)
const notifyQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  channelType: '' as number | '',
})

async function loadNotifyLogs() {
  if (drawerEventId.value == null) return
  notifyLoading.value = true
  try {
    const params: NotifyLogQuery = {
      pageNum: notifyQuery.pageNum,
      pageSize: notifyQuery.pageSize,
      eventId: drawerEventId.value,
      channelType: typeof notifyQuery.channelType === 'number' ? notifyQuery.channelType : undefined,
    }
    const data = await getNotifyLogPageApi(params)
    notifyList.value = data?.list ?? []
    notifyTotal.value = data?.total ?? 0
  } catch {
    notifyList.value = []
    notifyTotal.value = 0
  } finally {
    notifyLoading.value = false
  }
}

function notifySearch() {
  notifyQuery.pageNum = 1
  void loadNotifyLogs()
}

watch(detailTab, (tab) => {
  if (tab === 'notify' && drawerVisible.value && !notifyLoaded.value) {
    notifyLoaded.value = true
    void loadNotifyLogs()
  }
})

/* ---------------- 状态机操作（C11~C15，显隐与后端条件 UPDATE 的 WHERE 一致） ---------------- */

/** 任一操作成功后：刷新列表 + 统计 +（抽屉打开时）详情与时间线 +（已加载时）通知记录 */
async function afterEventAction() {
  await Promise.all([loadList(), loadStats()])
  if (drawerVisible.value && drawerEventId.value != null) {
    await Promise.all([loadDetail(drawerEventId.value), loadTimeline(drawerEventId.value)])
    if (notifyLoaded.value) void loadNotifyLogs()
  }
}

/* 确认/误报（API-C11） */
const confirmVisible = ref(false)
const confirmSubmitting = ref(false)
const confirmEventId = ref<number | null>(null)
const confirmForm = reactive<WarnConfirmForm>({ result: 1, conclusion: '' })

function openConfirm(row: WarnEventVO) {
  confirmEventId.value = row.id
  confirmForm.result = 1
  confirmForm.conclusion = ''
  confirmVisible.value = true
}

async function submitConfirm() {
  if (!confirmForm.conclusion.trim()) {
    ElMessage.warning(confirmForm.result === 1 ? '请填写确认结论' : '误报说明必填')
    return
  }
  if (confirmEventId.value == null) return
  confirmSubmitting.value = true
  try {
    await confirmWarnEventApi(confirmEventId.value, {
      result: confirmForm.result,
      conclusion: confirmForm.conclusion.trim(),
    })
    ElMessage.success(confirmForm.result === 1 ? '确认成功，事件转入已确认' : '已登记误报，事件关闭')
    confirmVisible.value = false
    await afterEventAction()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0302 状态不允许）
  } finally {
    confirmSubmitting.value = false
  }
}

/* 升级/降级（API-C12/C13，选项按当前级别限定方向） */
const levelChangeVisible = ref(false)
const levelSubmitting = ref(false)
const levelChangeMode = ref<'upgrade' | 'downgrade'>('upgrade')
const levelSource = ref<WarnEventVO | null>(null)
const levelForm = reactive<WarnLevelChangeForm>({ newLevel: 2, reason: '' })

const levelOptions = computed(() => {
  const ev = levelSource.value
  if (!ev) return [] as { value: number; label: string }[]
  const options: { value: number; label: string }[] = []
  if (levelChangeMode.value === 'upgrade') {
    for (let lv = ev.warnLevel + 1; lv <= 4; lv++) options.push({ value: lv, label: `${levelLabel(lv)}级（${lv}）` })
  } else {
    for (let lv = ev.warnLevel - 1; lv >= 1; lv--) options.push({ value: lv, label: `${levelLabel(lv)}级（${lv}）` })
  }
  return options
})

function openLevelChange(row: WarnEventVO, mode: 'upgrade' | 'downgrade') {
  levelSource.value = row
  levelChangeMode.value = mode
  levelForm.newLevel = mode === 'upgrade' ? Math.min(row.warnLevel + 1, 4) : Math.max(row.warnLevel - 1, 1)
  levelForm.reason = ''
  levelChangeVisible.value = true
}

async function submitLevelChange() {
  if (!levelForm.reason.trim()) {
    ElMessage.warning('请填写调整原因（操作留痕）')
    return
  }
  if (levelSource.value == null) return
  const api = levelChangeMode.value === 'upgrade' ? upgradeWarnEventApi : downgradeWarnEventApi
  levelSubmitting.value = true
  try {
    await api(levelSource.value.id, { newLevel: levelForm.newLevel, reason: levelForm.reason.trim() })
    ElMessage.success(`${levelChangeMode.value === 'upgrade' ? '升级' : '降级'}成功（已留痕）`)
    levelChangeVisible.value = false
    await afterEventAction()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0302 级别方向不合法）
  } finally {
    levelSubmitting.value = false
  }
}

/* 派单（API-C15，FR-402） */
const dispatchVisible = ref(false)
const dispatchSubmitting = ref(false)
const dispatchForm = reactive<WarnDispatchForm>({
  eventId: 0,
  assigneeId: 1,
  measure: '',
  deadline: '',
})

function defaultDeadline(): string {
  const d = new Date(Date.now() + 2 * 3600 * 1000)
  return (
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ` +
    `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  )
}

function openDispatch(row: WarnEventVO) {
  dispatchForm.eventId = row.id
  dispatchForm.assigneeId = 1
  dispatchForm.measure = ''
  dispatchForm.deadline = defaultDeadline()
  dispatchVisible.value = true
}

async function submitDispatch() {
  if (!dispatchForm.assigneeId) {
    ElMessage.warning('请填写责任人 ID')
    return
  }
  if (!dispatchForm.measure.trim()) {
    ElMessage.warning('请填写处置措施')
    return
  }
  if (!dispatchForm.deadline) {
    ElMessage.warning('请选择完成时限')
    return
  }
  dispatchSubmitting.value = true
  try {
    const taskId = await dispatchTaskApi({
      eventId: dispatchForm.eventId,
      assigneeId: dispatchForm.assigneeId,
      measure: dispatchForm.measure.trim(),
      deadline: dispatchForm.deadline,
    })
    ElMessage.success(`派单成功，处置任务 #${taskId} 已创建，事件转入处置中`)
    dispatchVisible.value = false
    await afterEventAction()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0302 事件状态不允许）
  } finally {
    dispatchSubmitting.value = false
  }
}

/* 复核消警（API-C14，FR-404：reason 必填） */
const closeVisible = ref(false)
const closeSubmitting = ref(false)
const closeEventId = ref<number | null>(null)
const closeForm = reactive<WarnCloseForm>({ reason: '' })

function openClose(row: WarnEventVO) {
  closeEventId.value = row.id
  closeForm.reason = ''
  closeVisible.value = true
}

async function submitClose() {
  if (!closeForm.reason.trim()) {
    ElMessage.warning('请填写复核结论（消警必填，FR-404）')
    return
  }
  if (closeEventId.value == null) return
  closeSubmitting.value = true
  try {
    await closeWarnEventApi(closeEventId.value, { reason: closeForm.reason.trim() })
    ElMessage.success('复核消警成功，事件已归档')
    closeVisible.value = false
    await afterEventAction()
  } catch {
    // 失败提示由 http.ts 统一处理（如 B0302 状态不允许 / B0304 原因缺失）
  } finally {
    closeSubmitting.value = false
  }
}

/* ---------------- 生命周期 ---------------- */
onMounted(() => {
  void loadList()
  void loadStats()
})

// 折叠面板展开时若已 init 过则 resize（v-show 隐藏期间尺寸归零，重新展开需校正）
watch(statsActiveNames, (names) => {
  if (names.includes('stats') && !chartsInited.value) {
    chartsInited.value = true
    void nextTick(initCharts)
  } else if (names.includes('stats')) {
    void nextTick(resizeCharts)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  levelChart?.dispose()
  hazardChart?.dispose()
  tunnelChart?.dispose()
})
</script>

<style scoped>
.chart-title {
  font-size: 13px;
  color: #606266;
  margin: 4px 0 8px;
}

.chart-box {
  height: 280px;
}

.tl-wrap {
  padding-left: 4px;
}

.tl-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tl-actor {
  font-size: 12px;
  color: #909399;
}

.tl-action {
  margin-top: 4px;
  font-size: 13px;
  color: #303133;
}

.tl-detail {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  white-space: pre-wrap;
}

.drawer-footer {
  text-align: left;
}

.drawer-footer-tip {
  font-size: 12px;
  color: #909399;
}

.form-tip {
  width: 100%;
  font-size: 12px;
  color: #909399;
  line-height: 18px;
}

.ml4 {
  margin-left: 4px;
}
</style>
