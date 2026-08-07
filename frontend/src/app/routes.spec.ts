import { describe, expect, it } from 'vitest'
import { routes } from './routes'

function childPaths(rootPath: string): string[] {
  const root = routes.find((route) => route.path === rootPath)
  return (root?.children || []).map((route) => route.path)
}

describe('第四阶段评分路由', () => {
  it('注册管理端评分详情、排行榜和规则页面', () => {
    expect(childPaths('/console')).toEqual(expect.arrayContaining(['buildings/:buildingId/assessment', 'renewal-priorities', 'assessment-rules']))
  })
  it('移动端只注册楼栋评分摘要，不注册规则管理和排行榜', () => {
    const paths = childPaths('/mobile')
    expect(paths).toContain('buildings/:buildingId/assessment')
    expect(paths).not.toContain('assessment-rules')
    expect(paths).not.toContain('renewal-priorities')
  })
})
