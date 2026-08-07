import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AppError from '@/shared/components/AppError.vue'

describe('AppError', () => {
  function mountError(props?: Record<string, unknown>) {
    return mount(AppError, {
      props: { ...props },
      global: { plugins: [ElementPlus] },
    })
  }

  it('渲染默认错误消息', () => {
    const wrapper = mountError()
    expect(wrapper.text()).toContain('加载失败')
  })

  it('渲染自定义消息', () => {
    const wrapper = mountError({ message: '服务器内部错误' })
    expect(wrapper.text()).toContain('服务器内部错误')
  })

  it('渲染自定义重试文案', () => {
    const wrapper = mountError({ retryText: '再来一次' })
    expect(wrapper.text()).toContain('再来一次')
  })

  it('点击重试发出 retry 事件', async () => {
    const wrapper = mountError()
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })

  it('渲染 alert role', () => {
    const wrapper = mountError()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
