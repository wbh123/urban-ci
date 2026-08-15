<script setup lang="ts">
import { computed } from 'vue'

type FilterFieldKind =
  | 'keyword'
  | 'spatial'
  | 'community'
  | 'building'
  | 'risk'
  | 'priority'
  | 'status'
  | 'type'
  | 'date-range'
  | 'compact'

const props = withDefaults(defineProps<{
  kind?: FilterFieldKind
  width?: string
}>(), {
  kind: 'status',
  width: '',
})

const widthByKind: Record<FilterFieldKind, string> = {
  keyword: '280px',
  spatial: '480px',
  community: '210px',
  building: '220px',
  risk: '150px',
  priority: '140px',
  status: '150px',
  type: '145px',
  'date-range': '250px',
  compact: '120px',
}

const fieldStyle = computed(() => ({
  '--filter-field-width': props.width || widthByKind[props.kind],
}))
</script>

<template>
  <div class="app-filter-field" :class="`app-filter-field--${kind}`" :style="fieldStyle">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.app-filter-field {
  width: min(100%, var(--filter-field-width));
  min-width: min(100%, var(--filter-field-width));
  flex: 0 1 var(--filter-field-width);
}

.app-filter-field :deep(.el-input),
.app-filter-field :deep(.el-select),
.app-filter-field :deep(.el-date-editor),
.app-filter-field :deep(.spatial-selector) {
  width: 100% !important;
}

@media (max-width: 760px) {
  .app-filter-field {
    width: 100%;
    min-width: 0;
    flex-basis: 100%;
  }
}
</style>
