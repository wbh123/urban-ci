<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    eyebrow?: string
  }>(),
  {
    description: '',
    eyebrow: '',
  },
)
</script>

<template>
  <header class="app-page-header">
    <div class="app-page-header__copy">
      <p v-if="eyebrow" class="app-page-header__eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="app-page-header__description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions" class="app-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.app-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usp-space-6);
}

.app-page-header__copy {
  min-width: 0;
}

.app-page-header__eyebrow {
  margin: 0 0 var(--usp-space-1);
  color: var(--usp-color-primary-strong);
  font-size: var(--usp-font-size-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
}

h1 {
  margin: 0;
  color: var(--usp-color-text);
  font-size: var(--usp-font-size-title);
  line-height: 1.25;
}

.app-page-header__description {
  max-width: 760px;
  margin: var(--usp-space-2) 0 0;
  color: var(--usp-color-text-secondary);
}

.app-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--usp-space-2);
}

@media (max-width: 720px) {
  .app-page-header {
    flex-direction: column;
    gap: var(--usp-space-4);
  }

  .app-page-header__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
