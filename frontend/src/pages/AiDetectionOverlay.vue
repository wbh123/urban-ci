<script setup lang="ts">
/**
 * 归一化检测框画布叠加层。
 * 根据原图尺寸与容器实际渲染区域计算检测框在屏幕上的像素位置，
 * 正确处理 object-fit:contain 的留白偏移。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { AiDetection } from '@/shared/api/endpoints/ai-inference'

const props = defineProps<{
  detections: AiDetection[]
  imageWidth: number
  imageHeight: number
  imageSrc: string
}>()

const container = ref<HTMLDivElement>()
const canvas = ref<HTMLCanvasElement>()
const containerW = ref(0)
const containerH = ref(0)

let resizeObserver: ResizeObserver | null = null

// 计算 object-fit:contain 下的渲染区域与偏移
const renderRect = computed(() => {
  const cw = containerW.value || 1
  const ch = containerH.value || 1
  const iw = props.imageWidth || 1
  const ih = props.imageHeight || 1
  const imageAspect = iw / ih
  const containerAspect = cw / ch

  let rw: number, rh: number, ox: number, oy: number
  if (imageAspect > containerAspect) {
    rw = cw
    rh = cw / imageAspect
    ox = 0
    oy = (ch - rh) / 2
  } else {
    rh = ch
    rw = ch * imageAspect
    ox = (cw - rw) / 2
    oy = 0
  }
  return { rw, rh, ox, oy }
})

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

  for (const d of props.detections) {
    const box = d.boundingBox
    const left = ox + box.x * rw
    const top = oy + box.y * rh
    const width = box.width * rw
    const height = box.height * rh

    // 检测框
    ctx.strokeStyle = '#e74c3c'
    ctx.lineWidth = 2
    ctx.strokeRect(left, top, width, height)

    // 标签背景
    const label = `${d.className} ${Math.round(d.confidence * 100)}%`
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
})

watch([containerW, containerH], () => {
  requestAnimationFrame(draw)
})
</script>

<template>
  <div
    ref="container"
    class="ao-container"
  >
    <img
      :src="imageSrc"
      class="ao-image"
      alt="巡检现场图片"
    >
    <canvas
      ref="canvas"
      class="ao-canvas"
    />
  </div>
</template>

<style scoped lang="scss">
.ao-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-height: 420px;
  background: #1a1a1a;
  border-radius: var(--usp-radius);
  overflow: hidden;
}
.ao-image {
  max-width: 100%;
  max-height: 420px;
  object-fit: contain;
}
.ao-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
</style>
