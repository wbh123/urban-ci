import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import AppMetricCard from '@/shared/components/data/AppMetricCard.vue'
import AppDialog from '@/shared/components/overlay/AppDialog.vue'
import AppDrawer from '@/shared/components/overlay/AppDrawer.vue'

const OverlayStub = defineComponent({
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  template: `
    <section v-if="modelValue" class="overlay-stub">
      <h2>{{ title }}</h2>
      <slot />
      <slot name="footer" />
      <button type="button" class="overlay-close" @click="$emit('update:modelValue', false)">关闭</button>
    </section>
  `,
})

describe('R1 公共展示与 Overlay 组件', () => {
  it('页面头统一承载标题、说明和操作区', () => {
    const wrapper = mount(AppPageHeader, {
      props: { title: '风险总览', description: '查看辖区楼栋风险情况。' },
      slots: { actions: '<button>导出</button>' },
    })
    expect(wrapper.get('h1').text()).toBe('风险总览')
    expect(wrapper.text()).toContain('查看辖区楼栋风险情况。')
    expect(wrapper.text()).toContain('导出')
  })

  it('指标卡把数值和单位放在同一视觉行并支持语义 tone', () => {
    const wrapper = mount(AppMetricCard, {
      props: { label: '高风险楼栋', value: 12, unit: '栋', hint: '较昨日 +1', tone: 'danger' },
    })
    expect(wrapper.classes()).toContain('is-danger')
    expect(wrapper.get('.app-metric-card__value').text()).toContain('12')
    expect(wrapper.get('.app-metric-card__value').text()).toContain('栋')
    expect(wrapper.text()).toContain('较昨日 +1')
  })

  it('Dialog 使用受控 v-model 并透传内容与 footer', async () => {
    const wrapper = mount(AppDialog, {
      props: { modelValue: true, title: '编辑楼栋' },
      slots: { default: '<p>表单内容</p>', footer: '<button>保存</button>' },
      global: { stubs: { ElDialog: OverlayStub } },
    })
    expect(wrapper.text()).toContain('编辑楼栋')
    expect(wrapper.text()).toContain('表单内容')
    expect(wrapper.text()).toContain('保存')
    await wrapper.get('.overlay-close').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })

  it('Drawer 使用受控 v-model 并透传内容与 footer', async () => {
    const wrapper = mount(AppDrawer, {
      props: { modelValue: true, title: '楼栋详情' },
      slots: { default: '<p>详情内容</p>', footer: '<button>关闭详情</button>' },
      global: { stubs: { ElDrawer: OverlayStub } },
    })
    expect(wrapper.text()).toContain('楼栋详情')
    expect(wrapper.text()).toContain('详情内容')
    await wrapper.get('.overlay-close').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
})
