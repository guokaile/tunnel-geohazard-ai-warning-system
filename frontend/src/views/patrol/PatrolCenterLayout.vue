<template>
  <div>
    <!-- 巡检管理布局：顶部子页导航（巡检计划/巡检模板/巡检任务/隐患管理）+ 子路由出口。
         MainLayout 侧边菜单仅挂 /patrol 一级入口，子页切换由本布局承担（与预警中心同口径）。 -->
    <el-card shadow="never" class="mb16">
      <el-tabs :model-value="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="巡检计划" name="plans" />
        <el-tab-pane label="巡检模板" name="templates" />
        <el-tab-pane label="巡检任务" name="tasks" />
        <el-tab-pane label="隐患管理" name="hazards" />
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

/** 由当前路径反推激活 Tab（/patrol/plans → plans） */
const activeTab = computed(() => {
  const name = route.path.split('/').pop() ?? 'plans'
  return ['plans', 'templates', 'tasks', 'hazards'].includes(name) ? name : 'plans'
})

function handleTabChange(name: string | number) {
  void router.push(`/patrol/${String(name)}`)
}
</script>

<style scoped>
.mb16 {
  margin-bottom: 16px;
}
</style>
