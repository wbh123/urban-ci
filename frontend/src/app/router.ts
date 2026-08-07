import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteMeta,
} from 'vue-router'
import { routes } from './routes'
import { useAuthStore } from '@/stores/auth'
import { hasAllPermissions } from '@/shared/auth/access'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

function lastMetaValue<K extends keyof RouteMeta>(
  to: RouteLocationNormalized,
  key: K,
): RouteMeta[K] | undefined {
  for (let index = to.matched.length - 1; index >= 0; index -= 1) {
    const value = to.matched[index]?.meta[key]
    if (value !== undefined) return value
  }
  return undefined
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const clientType = lastMetaValue(to, 'clientType') ?? 'PUBLIC'

  if (!requiresAuth) {
    if (
      authStore.isAuthenticated &&
      (to.name === 'mobile-login' || to.name === 'console-login')
    ) {
      const preferred = to.name === 'mobile-login' ? 'MOBILE' : 'CONSOLE'
      return authStore.defaultEntry(preferred)
    }
    return true
  }

  if (!authStore.isAuthenticated) {
    const loginPath = clientType === 'MOBILE' ? '/mobile/login' : '/console/login'
    return { path: loginPath, query: { redirect: to.fullPath } }
  }

  if (!authStore.user) await authStore.restore()
  if (!authStore.isAuthenticated || !authStore.user) {
    const loginPath = clientType === 'MOBILE' ? '/mobile/login' : '/console/login'
    return { path: loginPath, query: { redirect: to.fullPath } }
  }

  if (!authStore.canEnterClient(clientType)) {
    return { path: '/client-mismatch', query: { expected: clientType } }
  }

  const allowedRoles = lastMetaValue(to, 'allowedRoles')
  if (allowedRoles?.length && !authStore.hasAnyRole(allowedRoles)) {
    return { path: '/unauthorized', query: { from: to.fullPath } }
  }

  const requiredPermissions = lastMetaValue(to, 'requiredPermissions')
  if (requiredPermissions?.length && !hasAllPermissions(authStore.user, requiredPermissions)) {
    return { path: '/unauthorized', query: { from: to.fullPath } }
  }

  return true
})

router.afterEach((to) => {
  const title = lastMetaValue(to, 'title')
  document.title = title ? `${title} · 城安智序` : '城安智序'
})
