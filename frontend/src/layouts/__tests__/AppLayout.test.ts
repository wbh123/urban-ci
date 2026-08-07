import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createWebHistory } from 'vue-router'
import { setActivePinia, createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAppStore } from '@/stores/app'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      {
        path: '/',
        component: { template: '<div>home</div>' },
      },
      {
        path: '/workspace',
        component: { template: '<div>workspace</div>' },
      },
    ],
  })
}

describe('AppLayout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  async function mountLayout() {
    const router = createTestRouter()
    const appStore = useAppStore()
    appStore.apiMode = 'mock'
    await router.push('/')
    return mount(AppLayout, {
      global: {
        plugins: [router, ElementPlus],
        stubs: { RouterView: true },
      },
    })
  }

  it('渲染品牌名称', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.text()).toContain('城安智序')
    expect(wrapper.text()).toContain('UrbanSafe Priority')
  })

  it('渲染导航菜单', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.text()).toContain('首页')
    expect(wrapper.text()).toContain('巡检工作台')
  })

  it('显示 Mock 模式标签', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.text()).toContain('Mock 接口')
  })
})
