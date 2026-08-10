import { describe, expect, it } from 'vitest'
import {
  freshnessTone,
  priorityStatusTone,
  projectStatusMarker,
  riskStatusTone,
} from './workbench-status-rings'

describe('workbench status ring semantics', () => {
  it('covers every risk level used by the dashboard', () => {
    expect(['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'].map(riskStatusTone)).toEqual([
      'low', 'medium', 'high', 'very-high',
    ])
    expect(riskStatusTone(undefined)).toBe('unknown')
  })

  it('covers every renewal priority level used by the dashboard', () => {
    expect(['P1', 'P2', 'P3', 'P4'].map(priorityStatusTone)).toEqual([
      'p1', 'p2', 'p3', 'p4',
    ])
    expect(priorityStatusTone(undefined)).toBe('unknown')
  })

  it('distinguishes current, stale and no-result states', () => {
    expect(freshnessTone('CURRENT')).toBe('current')
    expect(freshnessTone('STALE')).toBe('stale')
    expect(freshnessTone('NO_RESULT')).toBe('no-result')
  })

  it('projects a Wuhan building coordinate into the current map viewport', () => {
    const position = projectStatusMarker(
      114.30,
      30.55,
      { west: 114.20, south: 30.45, east: 114.40, north: 30.65, zoom: 13 },
      1000,
      800,
    )
    expect(position).not.toBeNull()
    expect(position!.x).toBeGreaterThan(450)
    expect(position!.x).toBeLessThan(550)
    expect(position!.y).toBeGreaterThan(350)
    expect(position!.y).toBeLessThan(450)
  })

  it('does not project coordinates outside the viewport', () => {
    expect(projectStatusMarker(
      116.40,
      39.90,
      { west: 114.20, south: 30.45, east: 114.40, north: 30.65, zoom: 13 },
      1000,
      800,
    )).toBeNull()
  })
})
