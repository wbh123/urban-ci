import { watch } from 'vue'
import type { Pinia } from 'pinia'
import { storeToRefs } from 'pinia'
import { useSpatialMapStore } from '@/stores/spatial-map'
import {
  priorityStatusLabel,
  projectDashboardStatusMarkers,
  riskStatusLabel,
} from './workbench-status-rings'
import './workbench-status-rings.scss'

const MAP_ROOT_SELECTOR = '.wall-map-background'
const OVERLAY_CLASS = 'workbench-status-ring-overlay'
const LEGEND_CLASS = 'workbench-status-ring-legend'

let installed = false
let scheduledFrame: number | null = null
let activeRoot: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null
let mutationObserver: MutationObserver | null = null

function markerTitle(building: {
  buildingName: string
  riskLevel?: string
  priorityLevel?: string
  freshness: 'CURRENT' | 'STALE' | 'NO_RESULT'
}): string {
  const freshness = building.freshness === 'STALE'
    ? '结果已过期'
    : building.freshness === 'NO_RESULT'
      ? '暂无评分结果'
      : '当前有效结果'
  return `${building.buildingName}｜${riskStatusLabel(building.riskLevel)}｜${priorityStatusLabel(building.priorityLevel)}｜${freshness}`
}

function createLegendItem(type: 'dot' | 'ring', color: string, label: string): HTMLElement {
  const item = document.createElement('span')
  item.className = 'workbench-status-ring-legend-item'
  const icon = document.createElement('i')
  icon.className = type === 'dot' ? 'workbench-status-ring-legend-dot' : 'workbench-status-ring-legend-ring'
  icon.style.setProperty('--legend-color', color)
  const text = document.createElement('span')
  text.textContent = label
  item.append(icon, text)
  return item
}

function ensureLegend(root: HTMLElement): void {
  if (root.querySelector(`:scope > .${LEGEND_CLASS}`)) return
  const legend = document.createElement('aside')
  legend.className = LEGEND_CLASS
  legend.setAttribute('aria-label', '楼栋风险与更新优先级图例')

  const riskRow = document.createElement('div')
  riskRow.className = 'workbench-status-ring-legend-row'
  const riskTitle = document.createElement('strong')
  riskTitle.textContent = '内圆 · 风险'
  riskRow.append(
    riskTitle,
    createLegendItem('dot', '#42d392', '低'),
    createLegendItem('dot', '#f2c94c', '中'),
    createLegendItem('dot', '#ff941f', '高'),
    createLegendItem('dot', '#ff4d5d', '极高'),
  )

  const priorityRow = document.createElement('div')
  priorityRow.className = 'workbench-status-ring-legend-row'
  const priorityTitle = document.createElement('strong')
  priorityTitle.textContent = '外圈 · 优先级'
  priorityRow.append(
    priorityTitle,
    createLegendItem('ring', '#d85cff', 'P1'),
    createLegendItem('ring', '#4f8cff', 'P2'),
    createLegendItem('ring', '#22d3d8', 'P3'),
    createLegendItem('ring', '#94a3b8', 'P4'),
  )

  const note = document.createElement('span')
  note.className = 'workbench-status-ring-legend-note'
  note.textContent = '半透明表示结果已过期；灰色表示暂无评分结果。进入单栋三维聚焦后状态圆点自动隐藏。'
  legend.append(riskRow, priorityRow, note)
  root.append(legend)
}

function ensureOverlay(root: HTMLElement): HTMLElement {
  const current = root.querySelector<HTMLElement>(`:scope > .${OVERLAY_CLASS}`)
  if (current) return current
  const overlay = document.createElement('div')
  overlay.className = OVERLAY_CLASS
  overlay.setAttribute('aria-hidden', 'true')
  root.append(overlay)
  return overlay
}

function bindResizeObserver(root: HTMLElement, render: () => void): void {
  if (activeRoot === root) return
  resizeObserver?.disconnect()
  activeRoot = root
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => render())
  resizeObserver.observe(root)
}

function relevantWallMutation(records: MutationRecord[]): boolean {
  return records.some((record) => {
    const nodes = [...record.addedNodes, ...record.removedNodes]
    return nodes.some((node) => {
      if (!(node instanceof Element)) return false
      return node.matches(MAP_ROOT_SELECTOR) || Boolean(node.querySelector(MAP_ROOT_SELECTOR))
    })
  })
}

export function installWorkbenchStatusRingOverlay(pinia: Pinia): void {
  if (installed || typeof document === 'undefined') return
  installed = true

  const store = useSpatialMapStore(pinia)
  const { riskRows, viewport } = storeToRefs(store)

  const render = () => {
    if (scheduledFrame !== null) cancelAnimationFrame(scheduledFrame)
    scheduledFrame = requestAnimationFrame(() => {
      scheduledFrame = null
      const root = document.querySelector<HTMLElement>(MAP_ROOT_SELECTOR)
      if (!root) {
        activeRoot = null
        resizeObserver?.disconnect()
        return
      }
      bindResizeObserver(root, render)
      ensureLegend(root)
      const overlay = ensureOverlay(root)
      const rect = root.getBoundingClientRect()
      const markers = projectDashboardStatusMarkers(riskRows.value, viewport.value, rect.width, rect.height)
      const fragment = document.createDocumentFragment()
      markers.forEach((marker) => {
        const element = document.createElement('span')
        element.className = 'workbench-status-ring-marker'
        element.dataset.risk = marker.riskTone
        element.dataset.priority = marker.priorityTone
        element.dataset.freshness = marker.freshnessTone
        element.style.left = `${marker.x.toFixed(1)}px`
        element.style.top = `${marker.y.toFixed(1)}px`
        element.title = markerTitle(marker.building)

        const priorityRing = document.createElement('i')
        priorityRing.className = 'workbench-status-ring-priority'
        const riskCore = document.createElement('i')
        riskCore.className = 'workbench-status-ring-risk'
        element.append(priorityRing, riskCore)
        fragment.append(element)
      })
      overlay.replaceChildren(fragment)
    })
  }

  watch([riskRows, viewport], render, { deep: true })
  mutationObserver = new MutationObserver((records) => {
    if (relevantWallMutation(records)) render()
  })
  mutationObserver.observe(document.body, { childList: true, subtree: true })
  window.addEventListener('resize', render, { passive: true })
  render()
}
