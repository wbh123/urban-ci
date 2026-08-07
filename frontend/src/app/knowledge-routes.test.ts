/// <reference types="node" />

import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { buildConsoleMenu } from '@/shared/navigation/console-menu'

const routesSource = readFileSync(resolve(process.cwd(), 'src/app/routes.ts'), 'utf8')
const mobileLayoutSource = readFileSync(
  resolve(process.cwd(), 'src/layouts/MobileLayout.vue'),
  'utf8',
)

function consolePaths(roles: Parameters<typeof buildConsoleMenu>[0]): string[] {
  return buildConsoleMenu(roles).flatMap((group) => group.items.map((item) => item.path))
}

describe('internal knowledge QA routes', () => {
  it('exposes the mobile page only to inspectors and administrators', () => {
    expect(routesSource).toContain("name: 'mobile-knowledge'")
    expect(routesSource).toContain("allowedRoles: ['PROPERTY_INSPECTOR', 'ADMIN']")
    expect(mobileLayoutSource).toContain("path: '/mobile/knowledge'")
  })

  it('exposes the console page only to experts and administrators', () => {
    expect(routesSource).toContain("name: 'console-knowledge'")
    expect(routesSource).toContain("allowedRoles: ['EXPERT', 'ADMIN']")
    expect(consolePaths(['EXPERT'])).toContain('/console/knowledge')
    expect(consolePaths(['ADMIN'])).toContain('/console/knowledge')
    expect(consolePaths(['COMMUNITY_MANAGER'])).not.toContain('/console/knowledge')
    expect(consolePaths(['GOVERNMENT_MANAGER'])).not.toContain('/console/knowledge')
  })
})
