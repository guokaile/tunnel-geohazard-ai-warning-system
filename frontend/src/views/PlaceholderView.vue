<template>
  <el-card shadow="never">
    <el-empty :description="`「${title}」功能建设中，敬请期待`" />
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

/** 占位页：预警中心/巡检管理/报表中心共用，标题取自路由 meta */
const title = computed(() => route.meta.title ?? '本模块')
</script>
