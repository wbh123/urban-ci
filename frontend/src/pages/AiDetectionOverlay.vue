<script setup lang="ts">
/**
 * 归一化检测框 + SAM2 分割多边形画布叠加层。
 * 根据原图尺寸与容器实际渲染区域计算归一化坐标在屏幕上的像素位置，
 * 正确处理 object-fit:contain 的留白偏移，并监听 ResizeObserver 跟随容器缩放，
 * 避免标注漂移。segmentation 为可选字段（旧数据忽略）；缺少 boundingBox 的纯语义项不绘制。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { AiDetection, AiStructuredDetection } from '@/shared/api/endpoints/ai-inference'
import { computeContainRect } from '@/pages/containRect'
import { formatDetectionLabel } from '@/pages/detectionLabel'

type OverlayDetection = AiDetection | AiStructuredDetection

const props = withDefaults(
  defineProps<{
    detections?: OverlayDetection[]
    imageWidth: number
    imageHeight: number
    imageSrc: string
    /** 是否显示 AI 标注（检测框 + 分割多边形），默认开启。 */
    visible?: boolean
  }>(),
  { detections: () => [], visible: true },
)

const container = ref<HTMLDivElement>()
const canvas = ref<HTMLCanvasElement>()
const containerW = ref(0)
const containerH = ref(0)

let resizeObserver: ResizeObserver | null = null

const renderRect = computed(() =>
  computeContainRect(containerW.value, containerH.value, props.imageWidth, props.imageHeight),
)

function draw() {
  const cvs = canvas.value
  if (!cvs) return
  const { rw, rh, ox, oy } = renderRect.value
  const dpr = window.devicePixelRatio || 1
  const cw = containerW.value || 1
  const ch = containerH.value || 1
  cvs.width = cw * dpr
  cvs.height = ch * dpr
  cvs.style.width = cw + 'px'
  cvs.style.height = ch + 'px'
  const ctx = cvs.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, cw, ch)

  if (!props.visible) return

  for (const d of props.detections) {
    const box = d.boundingBox
    if (!box) continue
    const left = ox + box.x * rw
    const top = oy + box.y * rh
    const width = box.width * rw
    const height = box.height * rh

    const seg = d.segmentation
    if (seg && Array.isArray(seg.points) && seg.points.length >= 3) {
      ctx.beginPath()
      seg.points.forEach((point, index) => {
        const sx = ox + Number(point[0] ?? 0) * rw
        const sy = oy + Number(point[1] ?? 0) * rh
        if (index === 0) ctx.moveTo(sx, sy)
        else ctx.lineTo(sx, sy)
      })
      ctx.closePath()
      ctx.fillStyle = 'rgba(231, 76, 60, 0.2)'
      ctx.fill()
      ctx.strokeStyle = 'rgba(231, 76, 60, 0.7)'
      ctx.lineWidth = 1.5
      ctx.stroke()
    }

    ctx.strokeStyle = '#e74c3c'
    ctx.lineWidth = 2
    ctx.strokeRect(left, top, width, height)

    const className = d.className || d.classCode || '候选病害'
    const label = formatDetectionLabel(className, d.confidence)
    const fontSize = Math.max(11, Math.min(14, rw * 0.03))
    ctx.font = `${fontSize}px system-ui, sans-serif`
    const textW = ctx.measureText(label).width
    ctx.fillStyle = 'rgba(231, 76, 60, 0.85)'
    ctx.fillRect(left, Math.max(0, top - fontSize - 6), textW + 8, fontSize + 6)
    ctx.fillStyle = '#fff'
    ctx.fillText(label, left + 4, Math.max(fontSize, top - 3))
  }
}

onMounted(() => {
  if (container.value) {
    containerW.value = container.value.clientWidth
    containerH.value = container.value.clientHeight
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        containerW.value = entry.contentRect.width
        containerH.value = entry.contentRect.height
      }
      draw()
    })
    resizeObserver.observe(container.value)
  }
  draw()
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})

watch(() => [props.detections, props.imageSrc], () => {
  requestAnimationFrame(draw)
}, { deep: true })
</script>

<template>
  <div ref="container" class="detection-overlay">
    <img :src="imageSrc" alt="AI 检测图片" class="overlay-image" />
    <canvas ref="canvas" class="overlay-canvas" />
  </div>
</template>

<style scoped>
.detection-overlay {
  position: relative;
  width: 100%;
  min-height: 280px;
  max-height: 520px;
  overflow: hidden;
  border-radius: 12px;
  background: #111;
}
.overlay-image {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 280px;
  max-height: 520px;
  object-fit: contain;
}
.overlay-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
</style>