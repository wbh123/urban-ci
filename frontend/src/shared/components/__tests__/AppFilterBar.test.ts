import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import AppFilterBar from '@/shared/components/filter/AppFilterBar.vue'

const compact = ref(false)
vi.mock('@vueuse/core', () => ({ useMediaQuery: () => compact }))

const DrawerStub = defineComponent({
  props: { modelValue: Boolean, title: String },
  emits: ['update:modelValue'],
  template: '<section v-if="modelValue" class="drawer-stub"><h2>{{ title }}</h2><slot/><slot name="footer"/></section>',
})

describe('AppFilterBar', () => {
  beforeEach(() => {
    compact.value = false
  })

  it('桌面端直接展示筛选字段并统一发出 reset/submit', async () => {
    const wrapper = mount(AppFilterBar, {
      slots: { default: '<label>风险等级</label>' },
      global: { plugins: [ElementPlus], stubs: { AppDrawer: DrawerStub } },
    })
    expect(wrapper.text()).toContain('风险等级')
    await wrapper.get('[data-action="reset"]').trigger('click')
    await wrapper.get('[data-action="submit"]').trigger('click')
    expect(wrapper.emitted('reset')).toHaveLength(1)
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('窄屏用 Drawer 承载筛选条件', async () => {
    compact.value = true
    const wrapper = mount(AppFilterBar, {
      props: { activeCount: 2 },
      slots: { default: '<label>处理状态</label>' },
      global: { plugins: [ElementPlus], stubs: { AppDrawer: DrawerStub } },
    })
    expect(wrapper.text()).toContain('筛选条件')
    expect(wrapper.text()).toContain('2')
    await wrapper.get('[data-action="open-filter"]').trigger('click')
    expect(wrapper.text()).toContain('处理状态')
  })
})
