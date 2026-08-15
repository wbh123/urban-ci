<script setup lang="ts">
/**
 * 归一化检测框 + SAM2 分割多边形画布叠加层。
 * 优先使用接口返回的原图尺寸；历史/编排链路缺少宽高时，自动读取浏览器已加载图片的
 * naturalWidth / naturalHeight 作为兜底，再根据 object-fit:contain 的实际渲染区域换算坐标。
 * 同时兼容历史演示数据中的 bbox / polygon 字段，统一转换为 boundingBox / segmentation 后绘制。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { AiDetection, AiStructuredDetection } from '@/shared/api/endpoints/ai-inference'
import { computeContainRect } from '@/pages/containRect'
import { formatDetectionLabel } from '@/pages/detectionLabel'
import { resolveReviewOverlayDetections } from '@/pages/console/reviewDetectionOverlay'

type OverlayDetection = AiDetection | AiStructuredDetection

const props = withDefaults(
  defineProps<{
    detections?: OverlayDetection[]
    imageWidth?: number
    imageHeight?: number
    imageSrc: string
    /** 是否显示 AI 标注（检测框 + 分割多边形），默认开启。 */
    visible?: boolean
  }>(),
  { detections: () => [], imageWidth: 0, imageHeight: 0, visible: true },
)

const container = ref<HTMLDivElement>()
const image = ref<HTMLImageElement>()
const canvas = ref<HTMLCanvasElement>()
const containerW = ref(0)
const containerH = ref(0)
const naturalImageW = ref(0)
const naturalImageH = ref(0)

let resizeObserver: ResizeObserver | null = null

const effectiveImageWidth = computed(() =>
  props.imageWidth && props.imageWidth > 1 ? props.imageWidth : naturalImageW.value || 1,
)
const effectiveImageHeight = computed(() =>
  props.imageHeight && props.imageHeight > 1 ? props.imageHeight : naturalImageH.value || 1,
)
const drawableDetections = computed(() => resolveReviewOverlayDetections(props.detections, []))

const renderRect = computed(() =>
  computeContainRect(
    containerW.value,
    containerH.value,
    effectiveImageWidth.value,
    effectiveImageHeight.value,
  ),
)

function syncNaturalImageSize(): void {
  const element = image.value
  if (!element) return
  if (element.naturalWidth > 0 && element.naturalHeight > 0) {
    naturalImageW.value = element.naturalWidth
    naturalImageH.value = element.naturalHeight
  }
}

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

  for (const d of drawableDetections.value) {
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

function handleImageLoad(): void {
  syncNaturalImageSize()
  requestAnimationFrame(draw)
}

onMounted(() => {
  syncNaturalImageSize()
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

watch(
  () => [
    props.detections,
    props.imageSrc,
    props.imageWidth,
    props.imageHeight,
    props.visible,
    naturalImageW.value,
    naturalImageH.value,
  ],
  () => requestAnimationFrame(draw),
  { deep: true },
)
</script>

<template>
  <div ref="container" class="detection-overlay">
    <img
      ref="image"
      :src="imageSrc"
      alt="AI 检测图片"
      class="overlay-image"
      @load="handleImageLoad"
    />
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
