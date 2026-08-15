import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import AppNotice from '@/shared/components/AppNotice.vue'
import { useAppStore } from '@/stores/app'

describe('AppNotice', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
  })

  function mountNotice() {
    return mount(AppNotice, {
      global: { plugins: [ElementPlus] },
    })
  }

  it('默认无通知时渲染空容器', () => {
    const wrapper = mountNotice()
    expect(wrapper.find('[aria-live]').exists()).toBe(true)
  })

  it('添加通知后渲染圆角浮层消息', async () => {
    const store = useAppStore()
    store.notify('操作成功', 'success')
    const wrapper = mountNotice()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('操作成功')
    expect(wrapper.find('.notice-card--success').exists()).toBe(true)
  })

  it('多条通知正确渲染', async () => {
    const store = useAppStore()
    store.notify('第一条', 'info')
    store.notify('第二条', 'warning')
    const wrapper = mountNotice()
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.notice-card')).toHaveLength(2)
  })

  it('默认三秒后自动关闭通知', async () => {
    const store = useAppStore()
    store.notify('三秒提示', 'info')
    expect(store.notices).toHaveLength(1)
    vi.advanceTimersByTime(3000)
    expect(store.notices).toHaveLength(0)
  })
})
