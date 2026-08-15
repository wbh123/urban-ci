<script setup lang="ts">
import { computed, onMounted, provide, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { getAiRuntimeSummary } from '@/shared/api'
import { AI_RUNTIME_CONTEXT_KEY, type AiRuntimeDisplayState } from '@/shared/ai/ai-runtime-context'
import { useAuthStore } from '@/stores/auth'
import {
  buildConsoleMenu,
  resolveActiveConsoleMenuPath,
} from '@/shared/navigation/console-menu'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const runtimeLoading = ref(false)
const runtimeState = ref<AiRuntimeDisplayState>('UNKNOWN')
const runtimeServices = ref<Array<{ key: string; label: string; status: string }>>([])
const runtimePolicy = ref('Dify 优先 / 本地兜底')

provide(AI_RUNTIME_CONTEXT_KEY, {
  state: runtimeState,
  services: runtimeServices,
  policy: runtimePolicy,
  loading: runtimeLoading,
})

const menuGroups = computed(() => buildConsoleMenu(authStore.user?.roles ?? []))
const activeMenuPath = computed(() => resolveActiveConsoleMenuPath(route.path, menuGroups.value))
const fullWidth = computed(() => route.meta.fullWidth === true)

async function loadAiRuntime(): Promise<void> {
  runtimeLoading.value = true
  try {
    const summary = await getAiRuntimeSummary()
    runtimeState.value = summary.state
    runtimeServices.value = summary.services
    runtimePolicy.value = summary.policy
  } catch {
    runtimeState.value = 'UNKNOWN'
    runtimeServices.value = [
      { key: 'runtime', label: 'AI 辅助能力', status: 'AI 状态暂不可用' },
    ]
  } finally {
    runtimeLoading.value = false
  }
}

onMounted(() => { void loadAiRuntime() })
</script>

<template>
  <el-container class="console-shell">
    <el-aside
      class="console-aside"
      :class="{ 'is-collapsed': collapsed }"
      :style="{
        width: collapsed
          ? 'var(--usp-console-aside-collapsed-width)'
          : 'var(--usp-console-aside-width)',
      }"
    >
      <button class="console-brand" type="button" aria-label="返回管理总览" @click="router.push('/console')">
        <span class="console-brand-mark" aria-hidden="true">安</span>
        <span v-if="!collapsed" class="console-brand-copy">
          <strong>城安智序</strong>
          <small>城市房屋安全治理平台</small>
        </span>
      </button>

      <el-scrollbar class="console-nav-scroll">
        <nav class="console-nav" aria-label="电脑管理端主导航">
          <section v-for="group in menuGroups" :key="group.key" class="console-menu-group">
            <div v-if="!collapsed" class="console-menu-group__label">{{ group.label }}</div>
            <el-menu
              class="console-menu"
              :default-active="activeMenuPath"
              :collapse="collapsed"
              :collapse-transition="false"
            >
              <el-menu-item
                v-for="item in group.items"
                :key="item.path"
                :index="item.path"
                @click="router.push(item.path)"
              >
                <span class="console-menu-icon" aria-hidden="true">{{ item.icon }}</span>
                <template #title>{{ item.label }}</template>
              </el-menu-item>
            </el-menu>
          </section>
        </nav>
      </el-scrollbar>

      <button
        class="console-collapse"
        type="button"
        :aria-label="collapsed ? '展开导航栏' : '收起导航栏'"
        @click="collapsed = !collapsed"
      >
        <span aria-hidden="true">{{ collapsed ? '›' : '‹' }}</span>
        <span v-if="!collapsed">收起导航</span>
      </button>
    </el-aside>

    <el-container class="console-body">
      <el-main class="console-main" :class="{ 'console-main--wide': fullWidth }">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.console-shell { min-height: 100vh; background: var(--usp-color-bg); }
.console-aside { position: sticky; top: 0; z-index: 40; display: flex; flex-direction: column; height: 100vh; flex: 0 0 auto; overflow: hidden; background: var(--usp-color-aside); color: var(--usp-color-inverse); transition: width var(--usp-transition-fast); }
.console-brand { display: flex; align-items: center; gap: var(--usp-space-3); width: 100%; min-height: 72px; padding: 12px var(--usp-space-5); border: 0; background: transparent; color: inherit; text-align: left; }
.console-brand:focus-visible,
.console-collapse:focus-visible { outline: 2px solid var(--usp-color-primary-light); outline-offset: 2px; }
.console-brand-mark { display: grid; width: 36px; height: 36px; flex: 0 0 36px; place-items: center; border-radius: var(--usp-radius-md); background: var(--usp-color-primary); font-size: var(--usp-font-size-lg); font-weight: 800; }
.console-brand-copy { display: grid; min-width: 0; gap: 1px; }
.console-brand-copy strong { font-size: 19px; letter-spacing: .02em; }
.console-brand-copy small { overflow: hidden; color: var(--usp-color-aside-text-muted); font-size: var(--usp-font-size-xs); text-overflow: ellipsis; white-space: nowrap; }
.console-nav-scroll { flex: 1; }
.console-nav { display: grid; gap: var(--usp-space-3); padding: var(--usp-space-2) var(--usp-space-3) var(--usp-space-4); }
.console-menu-group { min-width: 0; }
.console-menu-group__label { padding: var(--usp-space-2) var(--usp-space-3); color: var(--usp-color-aside-text-muted); font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.console-menu { border-right: 0; background: transparent; }
.console-menu:not(.el-menu--collapse) { width: 100%; }
.console-menu :deep(.el-menu-item) { height: 42px; margin: 2px 0; padding: 0 var(--usp-space-3) !important; border-radius: var(--usp-radius-md); color: var(--usp-color-aside-text); line-height: 42px; }
.console-menu :deep(.el-menu-item:hover),
.console-menu :deep(.el-menu-item.is-active) { background: var(--usp-color-aside-hover); color: var(--usp-color-inverse); }
.console-menu-icon { display: inline-grid; width: 24px; flex: 0 0 24px; place-items: center; margin-right: var(--usp-space-2); font-size: 16px; font-weight: 700; }
.is-collapsed .console-brand { justify-content: center; padding-inline: var(--usp-space-2); }
.is-collapsed .console-nav { padding-inline: var(--usp-space-2); }
.is-collapsed .console-menu :deep(.el-menu-item) { justify-content: center; padding-inline: 0 !important; }
.is-collapsed .console-menu-icon { margin-right: 0; }
.console-collapse { display: flex; align-items: center; justify-content: center; gap: var(--usp-space-2); min-height: 44px; margin: var(--usp-space-3); border: 1px solid rgb(255 255 255 / 10%); border-radius: var(--usp-radius-md); background: transparent; color: var(--usp-color-aside-text); }
.console-collapse:hover { background: var(--usp-color-aside-hover); color: var(--usp-color-inverse); }
.console-body { --usp-console-header-offset: 0px; min-width: 0; overflow: visible; }
.console-main { --usp-page-gutter: var(--usp-space-6); width: 100%; max-width: var(--usp-content-max-width); margin: 0 auto; padding: 0 var(--usp-page-gutter) var(--usp-space-6); overflow: visible; }
.console-main--wide { --usp-page-gutter: var(--usp-space-5); max-width: none; padding-bottom: var(--usp-space-5); }

/* 兼容仍使用旧标题骨架的管理端页面。 */
.console-main :deep(.page-head),
.console-main :deep(.page-header),
.console-main :deep(.spatial-toolbar) {
  position: sticky;
  top: var(--usp-console-header-offset, 0px);
  z-index: 27;
  display: flex;
  min-height: 60px;
  align-items: center;
  justify-content: space-between;
  gap: var(--usp-space-4);
  margin: 0 calc(var(--usp-page-gutter) * -1) var(--usp-space-3);
  padding: 8px var(--usp-page-gutter);
  border-bottom: 1px solid rgba(226, 232, 240, .92);
  background: rgba(248, 250, 252, .96);
  backdrop-filter: blur(12px);
}
.console-main :deep(.page-head .eyebrow),
.console-main :deep(.page-header .eyebrow) { display: none; }
.console-main :deep(.page-head h1),
.console-main :deep(.page-header h1),
.console-main :deep(.spatial-toolbar h1) {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
}
.console-main :deep(.page-head > div > p:last-child),
.console-main :deep(.page-header > div > p:last-child),
.console-main :deep(.spatial-toolbar > div > p) {
  max-width: 860px;
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--usp-color-text-secondary);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 820px) {
  .console-main,
  .console-main--wide {
    --usp-page-gutter: var(--usp-space-4);
    padding-inline: var(--usp-page-gutter);
    padding-bottom: var(--usp-space-4);
  }
}
@media (max-width: 720px) {
  .console-main :deep(.page-head),
  .console-main :deep(.page-header),
  .console-main :deep(.spatial-toolbar) {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--usp-space-2);
  }
  .console-main :deep(.page-head > div > p:last-child),
  .console-main :deep(.page-header > div > p:last-child),
  .console-main :deep(.spatial-toolbar > div > p) { white-space: normal; }
}
</style>
