<template>
  <div>
    <!-- 预警中心布局：顶部子页导航（预警事件/处置任务/规则管理）+ 子路由出口。
         MainLayout 侧边菜单仅挂 /warn 一级入口，子页切换由本布局承担（保持 MainLayout 不动）。 -->
    <el-card shadow="never" class="mb16">
      <el-tabs :model-value="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="预警事件" name="events" />
        <el-tab-pane label="处置任务" name="tasks" />
        <el-tab-pane label="规则管理" name="rules" />
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

/** 由当前路径反推激活 Tab（/warn/events → events） */
const activeTab = computed(() => {
  const name = route.path.split('/').pop() ?? 'events'
  return ['events', 'tasks', 'rules'].includes(name) ? name : 'events'
})

function handleTabChange(name: string | number) {
  void router.push(`/warn/${String(name)}`)
}
</script>
