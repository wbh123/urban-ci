<script setup lang="ts">
type MetricKind = 'buildings' | 'inspections' | 'feedback' | 'reviews' | 'risk' | 'reports'

const props = defineProps<{
  kind: MetricKind
  label: string
  description: string
}>()

const emit = defineEmits<{
  open: []
}>()

const badgeCopy: Record<MetricKind, string> = {
  buildings: '栋',
  inspections: '巡',
  feedback: '民',
  reviews: '核',
  risk: '险',
  reports: '报',
}
</script>

<template>
  <button
    type="button"
    class="metric-card"
    :data-kind="props.kind"
    @click="emit('open')"
  >
    <span class="metric-topline">
      <span class="metric-badge">{{ badgeCopy[props.kind] }}</span>
      <span class="metric-kicker">工作模块</span>
    </span>
    <strong>{{ label }}</strong>
    <span class="metric-description">{{ description }}</span>
    <span class="metric-action">进入模块 <b>→</b></span>
  </button>
</template>

<style scoped lang="scss">
.metric-card {
  --metric-accent: #3b82f6;
  --metric-soft: rgba(59, 130, 246, .1);
  position: relative;
  overflow: hidden;
  display: grid;
  min-height: 154px;
  gap: var(--usp-space-2);
  padding: var(--usp-space-4);
  border: 1px solid color-mix(in srgb, var(--metric-accent) 18%, var(--usp-color-border));
  border-radius: var(--usp-radius-lg);
  background:
    radial-gradient(circle at 100% 0%, var(--metric-soft), transparent 42%),
    var(--usp-color-surface);
  color: var(--usp-color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease;
}

.metric-card::after {
  position: absolute;
  right: -18px;
  bottom: -28px;
  width: 94px;
  height: 94px;
  border: 1px solid color-mix(in srgb, var(--metric-accent) 20%, transparent);
  border-radius: 50%;
  content: '';
}

.metric-card[data-kind='inspections'] { --metric-accent: #0891b2; --metric-soft: rgba(8, 145, 178, .1); }
.metric-card[data-kind='feedback'] { --metric-accent: #7c3aed; --metric-soft: rgba(124, 58, 237, .1); }
.metric-card[data-kind='reviews'] { --metric-accent: #0f766e; --metric-soft: rgba(15, 118, 110, .1); }
.metric-card[data-kind='risk'] { --metric-accent: #dc2626; --metric-soft: rgba(220, 38, 38, .1); }
.metric-card[data-kind='reports'] { --metric-accent: #d97706; --metric-soft: rgba(217, 119, 6, .1); }

.metric-card:hover {
  transform: translateY(-3px);
  border-color: var(--metric-accent);
  box-shadow: 0 14px 30px rgba(15, 23, 42, .1);
}

.metric-topline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-badge {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 10px;
  background: var(--metric-soft);
  color: var(--metric-accent);
  font-size: 13px;
  font-weight: 900;
}

.metric-kicker,
.metric-description {
  color: var(--usp-color-text-secondary);
  font-size: 12px;
}

.metric-kicker {
  font-weight: 700;
  letter-spacing: .08em;
}

.metric-card strong {
  font-size: 20px;
}

.metric-description {
  line-height: 1.6;
}

.metric-action {
  position: relative;
  z-index: 1;
  align-self: end;
  color: var(--metric-accent);
  font-weight: 800;
  font-size: 13px;
}

.metric-action b {
  display: inline-block;
  transition: transform .16s ease;
}

.metric-card:hover .metric-action b {
  transform: translateX(3px);
}
</style>