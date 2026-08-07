import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AppEmpty from '@/shared/components/AppEmpty.vue'

describe('AppEmpty', () => {
  it('渲染默认描述', () => {
    const wrapper = mount(AppEmpty, {
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('渲染自定义描述', () => {
    const wrapper = mount(AppEmpty, {
      props: { description: '暂无记录' },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('暂无记录')
  })

  it('渲染 status role', () => {
    const wrapper = mount(AppEmpty, {
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
  })
})
