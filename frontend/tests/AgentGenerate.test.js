import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import AgentGenerate from '../src/views/AgentGenerate.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('../src/api', () => ({
  generateSpec: vi.fn()
}))

vi.mock('element-plus', () => {
  const pass = defineComponent({ template: '<div><slot /></div>' })
  return {
    ElMessage: { info: vi.fn(), warning: vi.fn(), success: vi.fn() },
    ElButton: defineComponent({
      emits: ['click'],
      template: '<button @click="$emit(\'click\')"><slot /></button>'
    }),
    ElForm: defineComponent({ template: '<form><slot /></form>' }),
    ElFormItem: defineComponent({ template: '<div><slot /></div>' }),
    ElInput: defineComponent({
      props: ['modelValue'],
      emits: ['update:modelValue'],
      template:
        '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>'
    }),
    ElColorPicker: pass,
    ElTag: defineComponent({ template: '<span><slot /></span>' }),
    ElProgress: pass,
    ElAlert: defineComponent({ template: '<div><slot /></div>' }),
    ElCard: defineComponent({ template: '<div><slot /><slot name="header" /></div>' }),
    ElDescriptions: defineComponent({ template: '<div><slot /></div>' }),
    ElDescriptionsItem: defineComponent({ template: '<div><slot /></div>' }),
    ElCollapse: pass,
    ElCollapseItem: pass
  }
})

vi.mock('@element-plus/icons-vue', () => ({
  ArrowLeft: defineComponent({ template: '<span />' }),
  MagicStick: defineComponent({ template: '<span />' }),
  Loading: defineComponent({ template: '<span />' })
}))

import { generateSpec } from '../src/api'
import {
  ElButton,
  ElInput,
  ElForm,
  ElFormItem,
  ElColorPicker,
  ElTag,
  ElProgress,
  ElAlert,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElCollapse,
  ElCollapseItem
} from 'element-plus'

function mountPage() {
  return mount(AgentGenerate, {
    global: {
      components: {
        ElButton,
        ElInput,
        ElForm,
        ElFormItem,
        ElColorPicker,
        ElTag,
        ElProgress,
        ElAlert,
        ElCard,
        ElDescriptions,
        ElDescriptionsItem,
        ElCollapse,
        ElCollapseItem
      }
    }
  })
}

describe('AgentGenerate 最小反馈时长（300ms/900ms 边界）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('prompt 为空时提示且不调用接口', async () => {
    const w = mountPage()
    await w.find('.form-actions button').trigger('click')
    expect(generateSpec).not.toHaveBeenCalled()
  })

  it('快速返回（<900ms）时反馈面板至少展示 900ms 再复位', async () => {
    generateSpec.mockResolvedValue({ brandName: 'T', tokenCount: 1, brandId: 1 })
    const w = mountPage()
    await w.find('textarea').setValue('一个测试品牌')
    await w.find('.form-actions button').trigger('click')
    await flushPromises()

    // 接口已返回，但仍在最小展示时长内，面板不消失
    expect(w.find('.generating-panel').exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(899)
    expect(w.find('.generating-panel').exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()
    expect(w.find('.generating-panel').exists()).toBe(false)
  })

  it('耗时超过 900ms 时按实际耗时立即复位，不额外等待', async () => {
    generateSpec.mockImplementation(async () => {
      vi.setSystemTime(Date.now() + 1500)
      return { brandName: 'T', tokenCount: 1, brandId: 1 }
    })
    const w = mountPage()
    await w.find('textarea').setValue('一个测试品牌')
    await w.find('.form-actions button').trigger('click')
    await flushPromises()
    expect(w.find('.generating-panel').exists()).toBe(false)
  })

  it('生成失败时同样补足最小反馈时长并展示错误', async () => {
    generateSpec.mockRejectedValue(new Error('boom'))
    const w = mountPage()
    await w.find('textarea').setValue('一个测试品牌')
    await w.find('.form-actions button').trigger('click')
    await flushPromises()
    expect(w.find('.generating-panel').exists()).toBe(true)
    await vi.advanceTimersByTimeAsync(900)
    await flushPromises()
    expect(w.find('.generating-panel').exists()).toBe(false)
    expect(w.find('.error-alert').exists()).toBe(true)
  })
})
