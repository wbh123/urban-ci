import { describe, expect, it } from 'vitest'
import { computeContainRect } from '@/pages/containRect'

describe('computeContainRect (object-fit: contain 几何)', () => {
  it('横向图 1920×1080 放入 800×600 → 800×450，offsetY=75', () => {
    const rect = computeContainRect(800, 600, 1920, 1080)
    expect(rect.rw).toBeCloseTo(800, 6)
    expect(rect.rh).toBeCloseTo(450, 6)
    expect(rect.ox).toBeCloseTo(0, 6)
    expect(rect.oy).toBeCloseTo(75, 6)
  })

  it('纵向图 1080×1920 放入 800×600 → 337.5×600，offsetX=231.25', () => {
    const rect = computeContainRect(800, 600, 1080, 1920)
    expect(rect.rw).toBeCloseTo(337.5, 6)
    expect(rect.rh).toBeCloseTo(600, 6)
    expect(rect.ox).toBeCloseTo(231.25, 6)
    expect(rect.oy).toBeCloseTo(0, 6)
  })

  it('归一化坐标映射到渲染像素不随容器缩放漂移', () => {
    // 同一图片在两种容器尺寸下，归一化坐标应映射到各自渲染区域内的对应比例位置。
    const small = computeContainRect(400, 300, 1920, 1080)
    const large = computeContainRect(800, 600, 1920, 1080)
    const point = [0.5, 0.5]
    const smallPx = { x: small.ox + point[0] * small.rw, y: small.oy + point[1] * small.rh }
    const largePx = { x: large.ox + point[0] * large.rw, y: large.oy + point[1] * large.rh }
    // 归一化比例（相对渲染区域）在缩放前后一致。
    expect(smallPx.x / small.rw).toBeCloseTo(largePx.x / large.rw, 6)
    expect(smallPx.y / small.rh).toBeCloseTo(largePx.y / large.rh, 6)
  })

  it('零尺寸输入不产生 NaN/Infinity', () => {
    const rect = computeContainRect(0, 0, 0, 0)
    expect([rect.rw, rect.rh, rect.ox, rect.oy].every(Number.isFinite)).toBe(true)
  })
})
