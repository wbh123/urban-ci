import { describe, it, expect } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import { routes } from '@/app/routes'

describe('角色化多端路由', () => {
  it('存在公共首页和两个登录入口', () => {
    expect(routes.some((route) => route.path === '/' && route.name === 'home')).toBe(true)
    expect(routes.some((route) => route.path === '/mobile/login' && route.name === 'mobile-login')).toBe(true)
    expect(routes.some((route) => route.path === '/console/login' && route.name === 'console-login')).toBe(true)
  })

  it('移动端任务页面限制为巡检人员和管理员', () => {
    const mobile = routes.find((route) => route.path === '/mobile')
    const tasks = mobile?.children?.find((route) => route.name === 'mobile-tasks')
    expect(mobile?.meta?.clientType).toBe('MOBILE')
    expect(tasks?.meta?.allowedRoles).toEqual(['PROPERTY_INSPECTOR', 'ADMIN'])
  })

  it('专业复核页面只允许专家和管理员', () => {
    const consoleRoute = routes.find((route) => route.path === '/console')
    const review = consoleRoute?.children?.find((route) => route.name === 'console-review')
    expect(consoleRoute?.meta?.clientType).toBe('CONSOLE')
    expect(review?.meta?.allowedRoles).toEqual(['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'])
    expect(review?.meta?.requiredPermissions).toEqual(['inference:review'])
  })

  it('保留旧工作台重定向和 404', () => {
    expect(routes.some((route) => route.path === '/workspace' && route.redirect === '/console/inspections')).toBe(true)
    expect(routes.find((route) => route.path === '/:pathMatch(.*)*')?.name).toBe('not-found')
  })

  it('可以创建路由对象', () => {
    const router = createRouter({ history: createWebHistory(), routes: [...routes] })
    expect(router).toBeDefined()
  })
})
