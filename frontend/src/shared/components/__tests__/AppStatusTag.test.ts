import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AppStatusTag from '@/shared/components/AppStatusTag.vue'

function mountTag(props: { status: string; variant?: string; label?: string }) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return mount(AppStatusTag, { props: props as any, global: { plugins: [ElementPlus] } })
}

describe('AppStatusTag', () => {
  it('渲染 generic 变体', () => {
    const wrapper = mountTag({ status: 'SOME_STATUS' })
    expect(wrapper.text()).toContain('SOME_STATUS')
  })

  it('task 变体映射中文', () => {
    const wrapper = mountTag({ status: 'PENDING', variant: 'task' })
    expect(wrapper.text()).toContain('待开始')
  })

  it('task 变体映射多种状态', () => {
    expect(mountTag({ status: 'IN_PROGRESS', variant: 'task' }).text()).toContain('进行中')
    expect(mountTag({ status: 'COMPLETED', variant: 'task' }).text()).toContain('已完成')
    expect(mountTag({ status: 'CANCELLED', variant: 'task' }).text()).toContain('已取消')
  })

  it('severity 变体映射中文', () => {
    expect(mountTag({ status: 'LOW', variant: 'severity' }).text()).toContain('低')
    expect(mountTag({ status: 'MEDIUM', variant: 'severity' }).text()).toContain('中')
    expect(mountTag({ status: 'HIGH', variant: 'severity' }).text()).toContain('高')
  })

  it('risk 变体映射风险等级', () => {
    expect(mountTag({ status: 'LOW', variant: 'risk' }).text()).toContain('低风险')
    expect(mountTag({ status: 'MEDIUM', variant: 'risk' }).text()).toContain('中风险')
    expect(mountTag({ status: 'HIGH', variant: 'risk' }).text()).toContain('高风险')
    expect(mountTag({ status: 'NO_RESULT', variant: 'risk' }).text()).toContain('暂无结果')
  })

  it('health 变体映射中文', () => {
    expect(mountTag({ status: 'UP', variant: 'health' }).text()).toContain('正常')
    expect(mountTag({ status: 'DEGRADED', variant: 'health' }).text()).toContain('降级')
    expect(mountTag({ status: 'DOWN', variant: 'health' }).text()).toContain('不可用')
  })

  it('手动 label 覆盖映射', () => {
    const wrapper = mountTag({ status: 'UNKNOWN', label: '自定义' })
    expect(wrapper.text()).toContain('自定义')
  })
})
