<template>
  <div>
    <!-- 报表中心布局：顶部子页导航（驾驶舱指标/统计报表/分析报告）+ 子路由出口。
         MainLayout 侧边菜单仅挂 /report 一级入口，子页切换由本布局承担。 -->
    <el-card shadow="never" class="mb16">
      <el-tabs :model-value="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="驾驶舱指标" name="dashboard" />
        <el-tab-pane label="统计报表" name="statistics" />
        <el-tab-pane label="分析报告" name="reports" />
      </el-tabs>
    </el-card>
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

/** 由当前路径反推激活 Tab（/report/dashboard → dashboard） */
const activeTab = computed(() => {
  const name = route.path.split('/').pop() ?? 'dashboard'
  return ['dashboard', 'statistics', 'reports'].includes(name) ? name : 'dashboard'
})

function handleTabChange(name: string | number) {
  void router.push(`/report/${String(name)}`)
}
</script>

<style scoped>
.mb16 {
  margin-bottom: 16px;
}
</style>
