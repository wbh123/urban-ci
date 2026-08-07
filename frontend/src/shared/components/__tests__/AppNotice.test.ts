import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import AppNotice from '@/shared/components/AppNotice.vue'
import { useAppStore } from '@/stores/app'

describe('AppNotice', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function mountNotice() {
    return mount(AppNotice, {
      global: { plugins: [ElementPlus] },
    })
  }

  it('默认无通知时渲染空容器', () => {
    const wrapper = mountNotice()
    const appNotice = wrapper.find('[aria-live]')
    expect(appNotice.exists()).toBe(true)
  })

  it('添加通知后渲染对应消息', async () => {
    const store = useAppStore()
    store.notify('操作成功', 'success')
    const wrapper = mountNotice()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('操作成功')
  })

  it('多条通知正确渲染', async () => {
    const store = useAppStore()
    store.notify('第一条', 'info')
    store.notify('第二条', 'warning')
    const wrapper = mountNotice()
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('.el-alert')).toHaveLength(2)
  })
})
