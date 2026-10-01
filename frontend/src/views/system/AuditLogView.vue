<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 操作日志（API-A22） -->
        <el-tab-pane label="操作日志" name="oper">
          <el-form inline @submit.prevent>
            <el-form-item label="用户">
              <el-input
                v-model="operQuery.user"
                placeholder="用户名"
                clearable
                style="width: 160px"
                @keyup.enter="handleOperSearch"
              />
            </el-form-item>
            <el-form-item label="模块">
              <el-input
                v-model="operQuery.module"
                placeholder="如 warn / sys"
                clearable
                style="width: 160px"
                @keyup.enter="handleOperSearch"
              />
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="operQuery.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 360px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleOperSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleOperReset">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="operLoading" :data="operList" stripe>
            <el-table-column prop="operateTime" label="操作时间" width="170" />
            <el-table-column prop="user" label="操作人" width="120" />
            <el-table-column prop="module" label="模块" width="110" />
            <el-table-column prop="action" label="操作内容" min-width="200">
              <template #default="{ row }">{{ row.action || '-' }}</template>
            </el-table-column>
            <el-table-column label="接口" min-width="200">
              <template #default="{ row }">
                {{ row.method ? `${row.method} ${row.path}` : row.path || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="来源 IP" width="140">
              <template #default="{ row }">{{ row.ip || '-' }}</template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="operQuery.pageNum"
            v-model:page-size="operQuery.pageSize"
            class="mt16"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="operTotal"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="loadOperLogs"
            @current-change="loadOperLogs"
          />
        </el-tab-pane>

        <!-- 登录日志（API-A23） -->
        <el-tab-pane label="登录日志" name="login">
          <el-form inline @submit.prevent>
            <el-form-item label="用户">
              <el-input
                v-model="loginQuery.user"
                placeholder="用户名"
                clearable
                style="width: 160px"
                @keyup.enter="handleLoginSearch"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleLoginSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleLoginReset">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loginLoading" :data="loginList" stripe>
            <el-table-column prop="loginTime" label="登录时间" width="170" />
            <el-table-column prop="user" label="用户名" min-width="120" />
            <el-table-column prop="ip" label="来源 IP" min-width="140">
              <template #default="{ row }">{{ row.ip || '-' }}</template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="failReason" label="失败原因" min-width="200">
              <template #default="{ row }">{{ row.failReason || '-' }}</template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="loginQuery.pageNum"
            v-model:page-size="loginQuery.pageSize"
            class="mt16"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="loginTotal"
            :page-sizes="[10, 20, 50, 100]"
            @size-change="loadLoginLogs"
            @current-change="loadLoginLogs"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'

import { getLoginLogPageApi, getOperLogPageApi } from '@/api/sys'
import type { LoginLogVO, OperLogVO } from '@/types/api'

const activeTab = ref('oper')

/* ---------------- 操作日志（API-A22） ---------------- */
const operLoading = ref(false)
const operList = ref<OperLogVO[]>([])
const operTotal = ref(0)
const operQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  user: '',
  module: '',
  timeRange: [] as string[],
})

async function loadOperLogs() {
  operLoading.value = true
  try {
    // TODO[契约]：《4》4.5.1 API-A22 入参为 user/module/时间范围，时间范围参数名暂定 startTime/endTime，后端实现后校准
    const [startTime, endTime] = operQuery.timeRange
    const data = await getOperLogPageApi({
      pageNum: operQuery.pageNum,
      pageSize: operQuery.pageSize,
      user: operQuery.user || undefined,
      module: operQuery.module || undefined,
      startTime: startTime || undefined,
      endTime: endTime || undefined,
    })
    operList.value = data?.list ?? []
    operTotal.value = data?.total ?? 0
  } catch {
    operList.value = []
    operTotal.value = 0
  } finally {
    operLoading.value = false
  }
}

function handleOperSearch() {
  operQuery.pageNum = 1
  void loadOperLogs()
}

function handleOperReset() {
  operQuery.user = ''
  operQuery.module = ''
  operQuery.timeRange = []
  operQuery.pageNum = 1
  void loadOperLogs()
}

/* ---------------- 登录日志（API-A23） ---------------- */
const loginLoading = ref(false)
const loginList = ref<LoginLogVO[]>([])
const loginTotal = ref(0)
const loginQuery = reactive({ pageNum: 1, pageSize: 10, user: '' })

async function loadLoginLogs() {
  loginLoading.value = true
  try {
    const data = await getLoginLogPageApi({
      pageNum: loginQuery.pageNum,
      pageSize: loginQuery.pageSize,
      user: loginQuery.user || undefined,
    })
    loginList.value = data?.list ?? []
    loginTotal.value = data?.total ?? 0
  } catch {
    loginList.value = []
    loginTotal.value = 0
  } finally {
    loginLoading.value = false
  }
}

function handleLoginSearch() {
  loginQuery.pageNum = 1
  void loadLoginLogs()
}

function handleLoginReset() {
  loginQuery.user = ''
  loginQuery.pageNum = 1
  void loadLoginLogs()
}

onMounted(() => {
  void loadOperLogs()
  void loadLoginLogs()
})
</script>

<style scoped>
.mt16 {
  margin-top: 16px;
}
</style>
