import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: () => import('@/pages/HomePage.vue'), meta: { title: '城安智序', clientType: 'PUBLIC' } },
  {
    path: '/citizen', component: () => import('@/layouts/CitizenLayout.vue'), meta: { title: '公众问题反馈', clientType: 'PUBLIC' },
    children: [
      { path: '', name: 'citizen-home', component: () => import('@/pages/citizen/CitizenHomePage.vue'), meta: { title: '公众问题反馈' } },
      { path: 'report', name: 'citizen-report', component: () => import('@/pages/citizen/CitizenReportPage.vue'), meta: { title: '提交问题反馈' } },
      { path: 'track', name: 'citizen-track', component: () => import('@/pages/citizen/CitizenTrackPage.vue'), meta: { title: '查询处理进度' } },
      { path: 'reports/:reportCode', name: 'citizen-report-detail', component: () => import('@/pages/citizen/CitizenReportDetailPage.vue'), meta: { title: '反馈处理进度' } },
    ],
  },
  { path: '/entry', name: 'role-entry', component: () => import('@/pages/RoleEntryPage.vue'), meta: { title: '选择工作入口', requiresAuth: true, clientType: 'PUBLIC' } },
  { path: '/mobile/login', name: 'mobile-login', component: () => import('@/pages/LoginPage.vue'), meta: { title: '移动作业端登录', clientType: 'MOBILE' } },
  { path: '/console/login', name: 'console-login', component: () => import('@/pages/LoginPage.vue'), meta: { title: '电脑管理端登录', clientType: 'CONSOLE' } },
  { path: '/unauthorized', name: 'unauthorized', component: () => import('@/pages/UnauthorizedPage.vue'), meta: { title: '无权访问', clientType: 'PUBLIC' } },
  { path: '/client-mismatch', name: 'client-mismatch', component: () => import('@/pages/ClientMismatchPage.vue'), meta: { title: '入口不匹配', clientType: 'PUBLIC' } },
  {
    path: '/mobile', component: () => import('@/layouts/MobileLayout.vue'),
    meta: { requiresAuth: true, clientType: 'MOBILE', allowedRoles: ['PROPERTY_INSPECTOR', 'DISPOSAL_OPERATOR', 'ADMIN'] },
    children: [
      { path: '', name: 'mobile-home', component: () => import('@/pages/mobile/MobileHomePage.vue'), meta: { title: '移动作业台' } },
      { path: 'tasks', name: 'mobile-tasks', component: () => import('@/pages/mobile/MobileTasksPage.vue'), meta: { title: '我的巡检任务', allowedRoles: ['PROPERTY_INSPECTOR', 'ADMIN'] } },
      { path: 'knowledge', name: 'mobile-knowledge', component: () => import('@/pages/mobile/MobileKnowledgePage.vue'), meta: { title: '现场知识助手', allowedRoles: ['PROPERTY_INSPECTOR', 'ADMIN'] } },
      { path: 'tasks/:taskId', name: 'mobile-task-detail', component: () => import('@/pages/mobile/MobileTaskDetailPage.vue'), meta: { title: '巡检任务详情', allowedRoles: ['PROPERTY_INSPECTOR', 'ADMIN'] } },
      { path: 'disposal', name: 'mobile-disposal', component: () => import('@/pages/mobile/MobileDisposalPage.vue'), meta: { title: '问题处置', allowedRoles: ['DISPOSAL_OPERATOR', 'ADMIN'] } },
      { path: 'buildings/:buildingId/assessment', name: 'mobile-building-assessment', component: () => import('@/pages/mobile/MobileBuildingAssessmentPage.vue'), meta: { title: '楼栋评分摘要', allowedRoles: ['PROPERTY_INSPECTOR', 'DISPOSAL_OPERATOR', 'ADMIN'] } },
    ],
  },
  {
    path: '/console', component: () => import('@/layouts/ConsoleLayout.vue'),
    meta: { requiresAuth: true, clientType: 'CONSOLE', allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] },
    children: [
      { path: '', name: 'console-dashboard', component: () => import('@/pages/console/ConsoleDashboardPage.vue'), meta: { title: '管理总览' } },
      { path: 'map', name: 'console-spatial-map', component: () => import('@/pages/console/ConsoleSpatialMapPage.vue'), meta: { title: '地图展示', fullWidth: true, allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'inspections', name: 'console-inspections', component: () => import('@/pages/console/ConsoleInspectionPage.vue'), meta: { title: '巡检组织管理', allowedRoles: ['COMMUNITY_MANAGER', 'ADMIN'], requiredPermissions: ['inspection:manage'] } },
      { path: 'feedback', name: 'console-feedback', component: () => import('@/pages/console/ConsoleFeedbackPage.vue'), meta: { title: '公众反馈管理', allowedRoles: ['COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'spatial-archive', name: 'console-spatial-archive', component: () => import('@/pages/console/ConsoleSpatialArchivePage.vue'), meta: { title: '空间档案', allowedRoles: ['COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'review', name: 'console-review', component: () => import('@/pages/console/ConsoleReviewQueuePage.vue'), meta: { title: '专业复核队列', allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'], requiredPermissions: ['inference:review'] } },
      { path: 'knowledge', name: 'console-knowledge', component: () => import('@/pages/console/ConsoleKnowledgePage.vue'), meta: { title: '内部知识问答', allowedRoles: ['EXPERT', 'ADMIN'] } },
      { path: 'review/:inferenceId', name: 'console-review-detail', component: () => import('@/pages/console/ConsoleReviewDetailPage.vue'), meta: { title: '人工智能结果复核', allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'], requiredPermissions: ['inference:review'] } },
      { path: 'buildings/:buildingId/assessment', name: 'console-building-assessment', component: () => import('@/pages/console/ConsoleBuildingAssessmentPage.vue'), meta: { title: '楼栋评分详情', allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'renewal-priorities', name: 'console-renewal-priorities', component: () => import('@/pages/console/ConsoleRiskReportsPage.vue'), meta: { title: '风险总览与楼栋报告', allowedRoles: ['GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'assessment-rules', name: 'console-assessment-rules', component: () => import('@/pages/console/ConsoleAssessmentRulesPage.vue'), meta: { title: '评分规则版本', allowedRoles: ['EXPERT', 'PROFESSIONAL_REVIEWER', 'COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'] } },
      { path: 'system-status', name: 'console-system-status', component: () => import('@/pages/console/ConsoleSystemStatusPage.vue'), meta: { title: '人工智能运行状态', allowedRoles: ['ADMIN'] } },
      { path: 'legacy-workspace', name: 'legacy-workspace', component: () => import('@/pages/InspectionWorkspacePage.vue'), meta: { title: '兼容工作台', allowedRoles: ['ADMIN'] } },
    ],
  },
  { path: '/workspace', redirect: '/console/inspections' },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/pages/NotFoundPage.vue'), meta: { title: '页面不存在', clientType: 'PUBLIC' } },
]
