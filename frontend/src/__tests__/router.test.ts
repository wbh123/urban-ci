import { describe, it, expect } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import { routes } from '@/app/routes'

function childRoute(parentPath: string, name: string) {
  return routes.find((route) => route.path === parentPath)?.children?.find((route) => route.name === name)
}

describe('角色化多端路由', () => {
  it('保留公共首页、公众反馈和两个登录入口', () => {
    expect(routes.some((route) => route.path === '/' && route.name === 'home')).toBe(true)
    const citizen = routes.find((route) => route.path === '/citizen')
    expect(citizen?.meta?.clientType).toBe('PUBLIC')
    expect(citizen?.children?.some((route) => route.name === 'citizen-report')).toBe(true)
    expect(citizen?.children?.some((route) => route.name === 'citizen-track')).toBe(true)
    expect(routes.some((route) => route.path === '/mobile/login' && route.name === 'mobile-login')).toBe(true)
    expect(routes.some((route) => route.path === '/console/login' && route.name === 'console-login')).toBe(true)
  })

  it('移动端根路由只允许巡检、处置和管理员角色', () => {
    const mobile = routes.find((route) => route.path === '/mobile')
    expect(mobile?.meta?.clientType).toBe('MOBILE')
    expect(mobile?.meta?.allowedRoles).toEqual(['PROPERTY_INSPECTOR', 'DISPOSAL_OPERATOR', 'ADMIN'])
  })

  it('巡检人员只能通过移动端巡检相关路由工作', () => {
    expect(childRoute('/mobile', 'mobile-tasks')?.meta?.allowedRoles).toEqual(['PROPERTY_INSPECTOR', 'ADMIN'])
    expect(childRoute('/mobile', 'mobile-knowledge')?.meta?.allowedRoles).toEqual(['PROPERTY_INSPECTOR', 'ADMIN'])
  })

  it('问题处置人员只能进入移动端处置路由', () => {
    expect(childRoute('/mobile', 'mobile-disposal')?.meta?.allowedRoles).toEqual(['DISPOSAL_OPERATOR', 'ADMIN'])
  })

  it('社区管理员拥有巡检组织和公众反馈入口但不拥有系统状态入口', () => {
    expect(childRoute('/console', 'console-inspections')?.meta?.allowedRoles).toEqual(['COMMUNITY_MANAGER', 'ADMIN'])
    expect(childRoute('/console', 'console-feedback')?.meta?.allowedRoles).toEqual(['COMMUNITY_MANAGER', 'GOVERNMENT_MANAGER', 'ADMIN'])
    expect(childRoute('/console', 'console-system-status')?.meta?.allowedRoles).toEqual(['ADMIN'])
  })

  it('专业复核页面只允许专家、专业复核和管理员', () => {
    const review = childRoute('/console', 'console-review')
    expect(review?.meta?.allowedRoles).toEqual(['EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN'])
    expect(review?.meta?.requiredPermissions).toEqual(['inference:review'])
  })

  it('住建管理人员拥有风险总览入口', () => {
    expect(childRoute('/console', 'console-renewal-priorities')?.meta?.allowedRoles).toEqual(['GOVERNMENT_MANAGER', 'ADMIN'])
  })

  it('管理员系统状态仍保持管理员专属', () => {
    expect(childRoute('/console', 'console-system-status')?.meta?.allowedRoles).toEqual(['ADMIN'])
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
