<script setup lang="ts">
import { computed } from 'vue'

const DISPLAY_FORMAT = 'YYYY-MM-DD HH:mm'

const props = defineProps<{
  value?: string | number | Date | null
}>()

const formatted = computed(() => formatDateTime(props.value))

function formatDateTime(value?: string | number | Date | null): string {
  if (value == null || value === '') return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

void DISPLAY_FORMAT
</script>

<template>
  <time :datetime="value == null ? undefined : String(value)">{{ formatted }}</time>
</template>
