<script setup lang="ts">
import { computed, inject } from 'vue'
import { AI_RUNTIME_CONTEXT_KEY } from '@/shared/ai/ai-runtime-context'
import AiRuntimeBadge from '@/shared/components/ai/AiRuntimeBadge.vue'
import ConsoleUserMenu from './ConsoleUserMenu.vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    eyebrow?: string
    showUserMenu?: boolean
  }>(),
  {
    description: '',
    eyebrow: '',
    showUserMenu: true,
  },
)

const runtime = inject(AI_RUNTIME_CONTEXT_KEY, null)
const showInlineAiRuntime = computed(() => props.showUserMenu && runtime !== null)
const runtimeState = computed(() => runtime?.state.value ?? 'UNKNOWN')
const runtimeServices = computed(() => runtime?.services.value ?? [])
const runtimePolicy = computed(() => runtime?.policy.value ?? 'Dify 优先 / 本地兜底')
const runtimeLoading = computed(() => runtime?.loading.value ?? false)
</script>

<template>
  <header class="app-page-header">
    <div class="app-page-header__copy">
      <div class="app-page-header__title-row">
        <span v-if="eyebrow" class="app-page-header__eyebrow">{{ eyebrow }}</span>
        <h1>{{ title }}</h1>
      </div>
      <p v-if="description" class="app-page-header__description">{{ description }}</p>
      <slot />
    </div>
    <div v-if="$slots.actions || showUserMenu" class="app-page-header__actions">
      <div v-if="$slots.actions" class="app-page-header__action-group">
        <slot name="actions" />
      </div>
      <AiRuntimeBadge
        v-if="showInlineAiRuntime"
        :state="runtimeState"
        :services="runtimeServices"
        :loading="runtimeLoading"
        :policy="runtimePolicy"
        class="app-page-header__runtime"
      />
      <ConsoleUserMenu v-if="showUserMenu" class="app-page-header__user" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.app-page-header {
  position: sticky;
  top: var(--usp-console-header-offset, 0px);
  z-index: 28;
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: var(--usp-space-4);
  margin: 0 calc(var(--usp-page-gutter, var(--usp-space-6)) * -1) var(--usp-space-3);
  padding: 9px var(--usp-page-gutter, var(--usp-space-6));
  border-bottom: 1px solid rgba(226, 232, 240, .92);
  background: rgba(248, 250, 252, .97);
  backdrop-filter: blur(12px);
}

.app-page-header__copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
}

.app-page-header__title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.app-page-header__eyebrow {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--usp-color-primary-soft);
  color: var(--usp-color-primary-strong);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .05em;
}

h1 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--usp-color-text);
  font-size: 22px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-page-header__description {
  max-width: 920px;
  margin: 0;
  overflow: hidden;
  color: var(--usp-color-text-secondary);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: var(--usp-space-3);
}
.app-page-header__action-group { display: flex; align-items: center; gap: var(--usp-space-2); }
.app-page-header__runtime,
.app-page-header__user { flex: 0 0 auto; }

@media (max-width: 720px) {
  .app-page-header {
    min-height: 0;
    align-items: flex-start;
    flex-direction: column;
    gap: var(--usp-space-2);
    padding-block: 8px;
  }

  .app-page-header__title-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  h1,
  .app-page-header__description {
    overflow: visible;
    white-space: normal;
  }

  .app-page-header__actions {
    width: 100%;
    flex-wrap: wrap;
    justify-content: space-between;
  }
  .app-page-header__action-group { flex-wrap: wrap; }
}
</style>
