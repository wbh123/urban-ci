<script setup lang="ts">
import type { BuildingEvidenceGalleryItem } from './building-business'

defineProps<{ items: BuildingEvidenceGalleryItem[] }>()
const emit = defineEmits<{ open: [id: string] }>()

function reviewLabel(status?: string): string {
  if (status === 'CONFIRMED') return '已确认'
  if (status === 'CORRECTED') return '已修正'
  if (status === 'REJECTED') return '已排除'
  if (status === 'UNREVIEWED') return '待复核'
  return status || '未标记'
}
</script>

<template>
  <el-card shadow="never" class="evidence-gallery">
    <template #header>
      <div class="gallery-head"><strong>证据资料</strong><span>{{ items.length }} 项</span></div>
    </template>

    <el-empty v-if="items.length === 0" description="暂无可展示证据" :image-size="72" />
    <div v-else class="evidence-grid">
      <article
        v-for="item in items"
        :key="item.id"
        class="evidence-item"
        role="button"
        tabindex="0"
        :data-evidence-id="item.id"
        @click="emit('open', item.id)"
        @keydown.enter.prevent="emit('open', item.id)"
        @keydown.space.prevent="emit('open', item.id)"
      >
        <div class="preview-shell">
          <img v-if="item.previewUrl" :src="item.previewUrl" :alt="item.title" loading="lazy">
          <div v-else class="preview-placeholder">暂无预览</div>
        </div>
        <div class="evidence-body">
          <strong>{{ item.title }}</strong>
          <small>{{ item.sourceLabel }}</small>
          <div class="evidence-tags">
            <el-tag v-if="item.reliabilityLabel" size="small" effect="plain">{{ item.reliabilityLabel }}</el-tag>
            <el-tag v-if="item.reviewStatus" size="small" effect="plain">{{ reviewLabel(item.reviewStatus) }}</el-tag>
            <el-tag v-if="item.aiAssisted" size="small" type="info">辅助分析</el-tag>
          </div>
        </div>
      </article>
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.evidence-gallery{min-width:0}.gallery-head{display:flex;align-items:center;justify-content:space-between;gap:var(--usp-space-3)}.gallery-head span{font-size:12px;color:var(--usp-color-text-secondary)}.evidence-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:var(--usp-space-3)}.evidence-item{overflow:hidden;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-md);background:var(--usp-color-surface);cursor:pointer;transition:border-color .15s ease,box-shadow .15s ease}.evidence-item:hover,.evidence-item:focus-visible{border-color:var(--usp-color-primary);box-shadow:var(--usp-shadow-sm);outline:none}.preview-shell{display:grid;place-items:center;aspect-ratio:4/3;background:var(--usp-color-bg)}.preview-shell img{width:100%;height:100%;object-fit:cover}.preview-placeholder{color:var(--usp-color-text-secondary);font-size:12px}.evidence-body{display:grid;gap:6px;padding:12px}.evidence-body small{color:var(--usp-color-text-secondary)}.evidence-tags{display:flex;gap:6px;flex-wrap:wrap}
</style>
