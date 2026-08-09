import { existsSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { describe, expect, it } from 'vitest'

const componentPaths = [
  './BuildingSummaryCard.vue',
  './BuildingLifecycleTimeline.vue',
  './RiskSummaryPanel.vue',
  './EvidenceGallery.vue',
]

describe('R4-2 building business component contract', () => {
  it('provides the four reusable business component entrypoints', () => {
    for (const componentPath of componentPaths) {
      const absolutePath = fileURLToPath(new URL(componentPath, import.meta.url))
      expect(existsSync(absolutePath), `${componentPath} should exist`).toBe(true)
    }
  })
})
