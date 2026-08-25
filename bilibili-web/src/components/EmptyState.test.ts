import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('renders defaults and custom copy', () => {
    const defaults = mount(EmptyState)
    expect(defaults.text()).toContain('这里还没有内容')
    expect(defaults.text()).toContain('换个条件再试试看吧')

    const customized = mount(EmptyState, {
      props: { title: '暂无视频', description: '稍后再来看看' },
    })
    expect(customized.text()).toContain('暂无视频')
    expect(customized.text()).toContain('稍后再来看看')
  })
})
