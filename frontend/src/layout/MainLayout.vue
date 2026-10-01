<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout-aside">
      <div class="layout-logo">
        <el-icon :size="24" color="#409eff"><Monitor /></el-icon>
        <span class="layout-logo-text">隧道地质灾害AI预警系统</span>
      </div>
      <el-menu
        router
        class="layout-menu"
        :default-active="activeMenu"
        :default-openeds="['/mon', '/system']"
        background-color="#001529"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#ffffff"
      >
        <template v-for="menu in menus" :key="menu.path">
          <el-sub-menu v-if="menu.children?.length" :index="menu.path">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <span class="layout-header-title">{{ route.meta.title ?? '' }}</span>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="layout-user">
            <el-icon><UserFilled /></el-icon>
            <span>{{ auth.user?.realName || auth.user?.username || '未登录' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Monitor, UserFilled } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

import { useAuthStore } from '@/stores/auth'

interface MenuItem {
  path: string
  title: string
  /** 全局注册的 Element Plus 图标组件名 */
  icon: string
  children?: MenuItem[]
}

const auth = useAuthStore()

/**
 * 侧边菜单：驾驶舱（实时看板）/ 实时监控组（断面图/数据查询，F2）/ 预警中心 /
 * 巡检管理（计划/模板/任务/隐患，W8 补全）/ 报表中心（驾驶舱指标/统计/报告，W8 补全）
 * + 系统管理（F5）。
 * 权限点显隐（4.7）：登录响应未返回 permissions 时视为"权限未知"→ 显示，
 * 由后端 @RequirePermission 兜底（与 F3 warnMeta.hasWarnPerm 同口径）。
 */
const menus = computed<MenuItem[]>(() => {
  const perms = auth.user?.permissions
  const permUnknown = !perms || perms.length === 0
  const monChildren: MenuItem[] = [
    // 断面图：mon:point:view 可见（任务口径；注意后端 B19 权限点为 mon:data:view，越权由后端兜底）
    ...(permUnknown || perms.includes('mon:point:view')
      ? [{ path: '/mon/board', title: '断面图', icon: 'Grid' } as MenuItem]
      : []),
    // 数据查询：mon:data:view 可见
    ...(permUnknown || perms.includes('mon:data:view')
      ? [{ path: '/mon/query', title: '数据查询', icon: 'Search' } as MenuItem]
      : []),
  ]
  const patrolChildren: MenuItem[] = [
    ...(permUnknown || perms.includes('patrol:plan:view')
      ? [{ path: '/patrol/plans', title: '巡检计划', icon: 'Calendar' } as MenuItem]
      : []),
    ...(permUnknown || perms.includes('patrol:template:view')
      ? [{ path: '/patrol/templates', title: '巡检模板', icon: 'Tickets' } as MenuItem]
      : []),
    ...(permUnknown || perms.includes('patrol:task:view')
      ? [{ path: '/patrol/tasks', title: '巡检任务', icon: 'Finished' } as MenuItem]
      : []),
    ...(permUnknown || perms.includes('patrol:hazard:view')
      ? [{ path: '/patrol/hazards', title: '隐患管理', icon: 'WarnTriangleFilled' } as MenuItem]
      : []),
  ]
  const reportChildren: MenuItem[] = [
    ...(permUnknown || perms.includes('rpt:dashboard')
      ? [{ path: '/report/dashboard', title: '驾驶舱指标', icon: 'DataLine' } as MenuItem]
      : []),
    ...(permUnknown || perms.includes('rpt:view')
      ? [{ path: '/report/statistics', title: '统计报表', icon: 'Histogram' } as MenuItem]
      : []),
    ...(permUnknown || perms.includes('rpt:view')
      ? [{ path: '/report/reports', title: '分析报告', icon: 'Document' } as MenuItem]
      : []),
  ]
  return [
    { path: '/dashboard', title: '驾驶舱', icon: 'Odometer' },
    ...(monChildren.length
      ? [{ path: '/mon', title: '实时监控', icon: 'Monitor', children: monChildren } as MenuItem]
      : []),
    { path: '/warn', title: '预警中心', icon: 'Warning' },
    ...(patrolChildren.length
      ? [{ path: '/patrol', title: '巡检管理', icon: 'Notebook', children: patrolChildren } as MenuItem]
      : []),
    ...(reportChildren.length
      ? [{ path: '/report', title: '报表中心', icon: 'DataAnalysis', children: reportChildren } as MenuItem]
      : []),
    {
      path: '/system',
      title: '系统管理',
      icon: 'Setting',
      children: [
        { path: '/system/users', title: '用户管理', icon: 'User' },
        { path: '/system/roles', title: '角色管理', icon: 'Avatar' },
        { path: '/system/logs', title: '审计日志', icon: 'Document' },
      ],
    },
  ]
})

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

async function handleCommand(command: string) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.layout-aside {
  background-color: #001529;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-logo-text {
  font-size: 14px;
}

.layout-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.layout-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary);
}

.layout-header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 1;
}

.layout-header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.layout-user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.layout-main {
  padding: 16px;
  overflow: auto;
  background-color: #f0f2f5;
}
</style>
