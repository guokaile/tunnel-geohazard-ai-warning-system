<template>
  <router-view />
</template>

<script setup lang="ts">
// 应用根组件：仅承载路由出口
</script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  font-family:
    'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  background-color: #f0f2f5;
}

/* 通用间距工具类 */
.mb16 {
  margin-bottom: 16px;
}

.mr4 {
  margin-right: 4px;
}
</style>
