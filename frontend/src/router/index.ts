/**
 * 路由：/login 免认证，其余需认证（beforeEach 守卫）；
 * 主布局 Layout 下挂驾驶舱/预警中心/巡检管理/报表中心与系统管理（F5）。
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import MainLayout from '@/layout/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 免认证 */
    public?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        // 驾驶舱 = 实时监控看板（F2：B16 最新值 5s 轮询 + B20 概览 30s + SSE 预警弹窗）
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/mon/MonitorDashboardView.vue'),
        meta: { title: '驾驶舱' },
      },
      {
        // 实时监控组（F2）：断面图（B19）/ 数据查询（B17/B18 多测项对比）
        path: 'mon',
        name: 'MonCenter',
        redirect: '/mon/board',
        meta: { title: '实时监控' },
        children: [
          {
            path: 'board',
            name: 'MonBoard',
            component: () => import('@/views/mon/SectionBoardView.vue'),
            meta: { title: '断面图' },
          },
          {
            path: 'query',
            name: 'MonQuery',
            component: () => import('@/views/mon/DataQueryView.vue'),
            meta: { title: '数据查询' },
          },
        ],
      },
      {
        // 预警中心（F3）：布局含子页导航（预警事件/处置任务/规则管理），事件列表为主页
        path: 'warn',
        name: 'WarnCenter',
        component: () => import('@/views/warn/WarnCenterLayout.vue'),
        redirect: '/warn/events',
        meta: { title: '预警中心' },
        children: [
          {
            path: 'events',
            name: 'WarnEvents',
            component: () => import('@/views/warn/WarnEventListView.vue'),
            meta: { title: '预警事件' },
          },
          {
            path: 'tasks',
            name: 'WarnTasks',
            component: () => import('@/views/warn/DisposeTaskListView.vue'),
            meta: { title: '处置任务' },
          },
          {
            path: 'rules',
            name: 'WarnRules',
            component: () => import('@/views/warn/RuleManageView.vue'),
            meta: { title: '规则管理' },
          },
        ],
      },
      {
        // 巡检管理：计划/模板/任务/隐患（后端 API-D01~D14 已就绪，前端 W8 补全）
        path: 'patrol',
        name: 'PatrolCenter',
        component: () => import('@/views/patrol/PatrolCenterLayout.vue'),
        redirect: '/patrol/plans',
        meta: { title: '巡检管理' },
        children: [
          {
            path: 'plans',
            name: 'PatrolPlans',
            component: () => import('@/views/patrol/PlanListView.vue'),
            meta: { title: '巡检计划' },
          },
          {
            path: 'templates',
            name: 'PatrolTemplates',
            component: () => import('@/views/patrol/TemplateListView.vue'),
            meta: { title: '巡检模板' },
          },
          {
            path: 'tasks',
            name: 'PatrolTasks',
            component: () => import('@/views/patrol/TaskListView.vue'),
            meta: { title: '巡检任务' },
          },
          {
            path: 'hazards',
            name: 'PatrolHazards',
            component: () => import('@/views/patrol/HazardListView.vue'),
            meta: { title: '隐患管理' },
          },
        ],
      },
      {
        // 报表中心：驾驶舱指标/统计报表/分析报告（后端 API-F01~F05 已就绪，前端 W8 补全）
        path: 'report',
        name: 'ReportCenter',
        component: () => import('@/views/report/ReportCenterLayout.vue'),
        redirect: '/report/dashboard',
        meta: { title: '报表中心' },
        children: [
          {
            path: 'dashboard',
            name: 'ReportDashboard',
            component: () => import('@/views/report/ReportDashboardView.vue'),
            meta: { title: '驾驶舱指标' },
          },
          {
            path: 'statistics',
            name: 'ReportStatistics',
            component: () => import('@/views/report/StatReportView.vue'),
            meta: { title: '统计报表' },
          },
          {
            path: 'reports',
            name: 'ReportReports',
            component: () => import('@/views/report/ReportListView.vue'),
            meta: { title: '分析报告' },
          },
        ],
      },
      {
        path: 'system',
        redirect: '/system/users',
        children: [
          {
            path: 'users',
            name: 'SystemUsers',
            component: () => import('@/views/system/UserManageView.vue'),
            meta: { title: '用户管理' },
          },
          {
            path: 'roles',
            name: 'SystemRoles',
            component: () => import('@/views/system/RoleManageView.vue'),
            meta: { title: '角色管理' },
          },
          {
            path: 'logs',
            name: 'SystemLogs',
            component: () => import('@/views/system/AuditLogView.vue'),
            meta: { title: '审计日志' },
          },
        ],
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    // 已登录访问登录页 → 回首页
    if (to.path === '/login' && auth.isLoggedIn) {
      return { path: '/' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 隧道地质灾害AI预警系统` : '隧道地质灾害AI预警系统'
})

export default router
