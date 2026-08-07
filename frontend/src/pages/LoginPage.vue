<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { toAppError } from '@/shared/api'
import type { ClientType } from '@/shared/auth/access'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const errorMessage = ref('')

const clientType = computed<Exclude<ClientType, 'PUBLIC'>>(() =>
  route.meta.clientType === 'MOBILE' ? 'MOBILE' : 'CONSOLE',
)
const isMobile = computed(() => clientType.value === 'MOBILE')
const title = computed(() => (isMobile.value ? '移动作业端登录' : '电脑审核管理端登录'))

async function submit(): Promise<void> {
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入用户名和密码。'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    await authStore.login(username.value.trim(), password.value)
    await authStore.fetchCurrentUser().catch(() => undefined)
    if (!authStore.canEnterClient(clientType.value)) {
      authStore.clearSession()
      await router.replace({ path: '/client-mismatch', query: { expected: clientType.value } })
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const expectedPrefix = clientType.value === 'MOBILE' ? '/mobile' : '/console'
    await router.replace(redirect.startsWith(expectedPrefix) ? redirect : authStore.defaultEntry(clientType.value))
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page" :class="{ 'login-page--mobile': isMobile }">
    <section class="login-card">
      <p class="eyebrow">UrbanSafe Priority</p>
      <h1>{{ title }}</h1>
      <p class="description">
        {{ isMobile ? '面向现场采集和问题处置人员。' : '面向审核、社区管理、住建管理和系统管理人员。' }}
      </p>
      <form @submit.prevent="submit">
        <label>
          用户名
          <input v-model="username" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <button class="switch" type="button" @click="router.push(isMobile ? '/console/login' : '/mobile/login')">
        {{ isMobile ? '使用电脑管理端' : '使用移动作业端' }}
      </button>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: radial-gradient(circle at top left, #dff2ec, #eef3f7 48%, #e8edf4); }
.login-page--mobile { background: linear-gradient(180deg, #176354 0 32%, #f4f7f6 32%); }
.login-card { width: min(100%, 420px); padding: 30px; border-radius: 24px; background: #fff; box-shadow: 0 24px 70px rgb(35 48 70 / 16%); }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
h1 { margin: 8px 0; font-size: 28px; }
.description { margin: 0 0 22px; color: #667085; line-height: 1.6; }
form { display: grid; gap: 16px; }
label { display: grid; gap: 7px; color: #475467; font-size: 14px; }
input { min-height: 46px; padding: 10px 12px; border: 1px solid #cfd8e3; border-radius: 12px; font: inherit; }
button { min-height: 46px; border: 0; border-radius: 12px; background: #176354; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
button:disabled { opacity: .6; cursor: wait; }
.error { margin: 0; padding: 10px 12px; border-radius: 10px; background: #fef3f2; color: #b42318; }
.switch { width: 100%; margin-top: 12px; background: transparent; color: #176354; }
</style>
