<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { availableEntries } from '@/shared/auth/access'

const authStore = useAuthStore()
const router = useRouter()
const entries = computed(() => availableEntries(authStore.user))

onMounted(() => {
  if (entries.value.length === 1) void router.replace(entries.value[0]!.path)
})
</script>

<template>
  <main class="entry-page">
    <section>
      <p class="eyebrow">角色入口</p>
      <h1>请选择工作入口</h1>
      <p>当前账号：{{ authStore.user?.realName || authStore.user?.username }}</p>
      <div class="entry-grid">
        <button v-for="entry in entries" :key="entry.path" type="button" @click="router.push(entry.path)">
          <strong>{{ entry.label }}</strong>
          <span>{{ entry.description }}</span>
        </button>
      </div>
      <p v-if="!entries.length" class="warning">当前账号没有可用入口，请联系管理员配置角色。</p>
    </section>
  </main>
</template>

<style scoped lang="scss">
.entry-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: #f3f6f9; }
.entry-page > section { width: min(900px, 100%); }
.eyebrow { color: #287a6a; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
h1 { font-size: clamp(30px, 5vw, 48px); margin: 8px 0; }
.entry-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 18px; margin-top: 28px; }
.entry-grid button { min-height: 180px; padding: 24px; display: grid; align-content: center; gap: 12px; text-align: left; border: 1px solid #dce4e9; border-radius: 22px; background: #fff; box-shadow: 0 14px 40px rgb(35 48 70 / 8%); cursor: pointer; }
.entry-grid strong { font-size: 22px; color: #176354; }
.entry-grid span { color: #667085; line-height: 1.6; }
.warning { padding: 14px; border-radius: 12px; background: #fff4e5; color: #9a6700; }
</style>
