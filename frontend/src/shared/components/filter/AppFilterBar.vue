<script setup lang="ts">
import { ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import AppDrawer from '@/shared/components/overlay/AppDrawer.vue'

withDefaults(
  defineProps<{
    title?: string
    activeCount?: number
  }>(),
  {
    title: '筛选条件',
    activeCount: 0,
  },
)

const emit = defineEmits<{
  reset: []
  submit: []
}>()

const isCompact = useMediaQuery('(max-width: 768px)')
const mobileOpen = ref(false)

function reset(): void {
  emit('reset')
}

function submit(): void {
  emit('submit')
  mobileOpen.value = false
}
</script>

<template>
  <section class="app-filter-bar">
    <template v-if="isCompact">
      <el-button data-action="open-filter" class="app-filter-bar__trigger" @click="mobileOpen = true">
        {{ title }}
        <span v-if="activeCount > 0" class="app-filter-bar__count">{{ activeCount }}</span>
      </el-button>
      <AppDrawer v-model="mobileOpen" :title="title" size="min(480px, 94vw)">
        <div class="app-filter-bar__fields is-drawer">
          <slot />
        </div>
        <template #footer>
          <slot name="actions" :reset="reset" :submit="submit">
            <el-button data-action="reset" @click="reset">重置</el-button>
            <el-button data-action="submit" type="primary" @click="submit">查询</el-button>
          </slot>
        </template>
      </AppDrawer>
    </template>

    <template v-else>
      <div class="app-filter-bar__fields">
        <slot />
      </div>
      <div class="app-filter-bar__actions">
        <slot name="actions" :reset="reset" :submit="submit">
          <el-button data-action="reset" @click="reset">重置</el-button>
          <el-button data-action="submit" type="primary" @click="submit">查询</el-button>
        </slot>
      </div>
    </template>
  </section>
</template>

<style scoped lang="scss">
.app-filter-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--usp-space-4);
  padding: var(--usp-space-4);
  background: var(--usp-color-surface);
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-lg);
}

.app-filter-bar__fields {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--usp-space-3);
  min-width: 0;
}

.app-filter-bar__fields.is-drawer {
  display: grid;
  align-items: stretch;
}

.app-filter-bar__actions {
  display: flex;
  flex: 0 0 auto;
  gap: var(--usp-space-2);
}

.app-filter-bar__trigger {
  width: 100%;
  justify-content: space-between;
}

.app-filter-bar__count {
  display: inline-grid;
  min-width: 20px;
  height: 20px;
  place-items: center;
  margin-left: var(--usp-space-2);
  padding-inline: 5px;
  border-radius: 999px;
  background: var(--usp-color-primary-soft);
  color: var(--usp-color-primary-strong);
  font-size: var(--usp-font-size-xs);
  font-weight: 700;
}
</style>
