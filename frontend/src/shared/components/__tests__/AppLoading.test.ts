import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppLoading from '@/shared/components/AppLoading.vue'

describe('AppLoading', () => {
  it('visible=false 时不渲染', () => {
    const wrapper = mount(AppLoading, { props: { visible: false } })
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('visible=true 时渲染加载文本', () => {
    const wrapper = mount(AppLoading, { props: { visible: true } })
    expect(wrapper.text()).toContain('加载中…')
  })

  it('自定义加载文本', () => {
    const wrapper = mount(AppLoading, { props: { visible: true, text: '数据同步中…' } })
    expect(wrapper.text()).toContain('数据同步中…')
  })

  it('inline 模式渲染', () => {
    const wrapper = mount(AppLoading, { props: { visible: true, inline: true } })
    expect(wrapper.find('.app-loading--inline').exists()).toBe(true)
    expect(wrapper.find('.app-loading--inline').element).toBeTruthy()
  })
})
