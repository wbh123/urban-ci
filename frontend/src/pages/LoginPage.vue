<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { toAppError } from '@/shared/api'
import type { ClientType } from '@/shared/auth/access'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

const clientType = computed<Exclude<ClientType, 'PUBLIC'>>(() =>
  route.meta.clientType === 'MOBILE' ? 'MOBILE' : 'CONSOLE',
)
const isMobile = computed(() => clientType.value === 'MOBILE')
const title = computed(() => (isMobile.value ? '移动作业端登录' : '电脑审核管理端登录'))

async function showRiskNotice(): Promise<void> {
  await ElMessageBox.alert(
    '系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。',
    '使用说明',
    {
      confirmButtonText: '我已了解',
      type: 'warning',
      customClass: 'risk-notice-dialog',
      closeOnClickModal: false,
      closeOnPressEscape: false,
      showClose: false,
    },
  )
}

async function submit(): Promise<void> {
  if (!username.value.trim() || !password.value) {
    appStore.notify('请输入用户名和密码。', 'warning')
    return
  }
  loading.value = true
  try {
    await authStore.login(username.value.trim(), password.value)
    await authStore.fetchCurrentUser().catch(() => undefined)
    if (!authStore.canEnterClient(clientType.value)) {
      authStore.clearSession()
      await router.replace({ path: '/client-mismatch', query: { expected: clientType.value } })
      return
    }

    await showRiskNotice()

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const expectedPrefix = clientType.value === 'MOBILE' ? '/mobile' : '/console'
    await router.replace(redirect.startsWith(expectedPrefix) ? redirect : authStore.defaultEntry(clientType.value))
  } catch (error) {
    appStore.notify(toAppError(error).message, 'error')
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
.login-card { width: min(100%, 420px); padding: 30px; border: 1px solid rgb(255 255 255 / 72%); border-radius: 28px; background: #fff; box-shadow: 0 24px 70px rgb(35 48 70 / 16%); }
.eyebrow { margin: 0; color: #287a6a; font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
h1 { margin: 8px 0; font-size: 28px; }
.description { margin: 0 0 22px; color: #667085; line-height: 1.6; }
form { display: grid; gap: 16px; }
label { display: grid; gap: 7px; color: #475467; font-size: 14px; }
input { min-height: 46px; padding: 10px 12px; border: 1px solid #cfd8e3; border-radius: 12px; font: inherit; }
button { min-height: 46px; border: 0; border-radius: 12px; background: #176354; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
button:disabled { opacity: .6; cursor: wait; }
.switch { width: 100%; margin-top: 12px; background: transparent; color: #176354; }
</style>

<style lang="scss">
.risk-notice-dialog {
  border-radius: 20px !important;
  overflow: hidden;
}
.risk-notice-dialog .el-message-box__content {
  color: var(--usp-color-text-secondary);
  line-height: 1.75;
}
</style>
