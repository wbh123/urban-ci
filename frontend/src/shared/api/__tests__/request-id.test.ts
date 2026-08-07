import { describe, it, expect, beforeEach } from 'vitest'
import {
  generateRequestId,
  resetRequestIdClock,
  REQUEST_ID_LENGTH,
  REQUEST_ID_ALPHABET,
} from '@/shared/api'

describe('generateRequestId', () => {
  beforeEach(() => {
    resetRequestIdClock()
  })

  it('生成 26 位字符串', () => {
    const id = generateRequestId(1000)
    expect(id).toHaveLength(REQUEST_ID_LENGTH)
  })

  it('仅包含 Crockford Base32 字母表', () => {
    const id = generateRequestId(1000)
    const re = new RegExp(`^[${REQUEST_ID_ALPHABET}]{${REQUEST_ID_LENGTH}}$`)
    expect(id).toMatch(re)
  })

  it('相同时间戳生成不同且递增', () => {
    const a = generateRequestId(5000)
    const b = generateRequestId(5000)
    expect(a).not.toBe(b)
    expect(b > a).toBe(true)
  })

  it('不同时间戳生成递增', () => {
    const a = generateRequestId(1000)
    const b = generateRequestId(2000)
    expect(a).not.toBe(b)
    expect(b > a).toBe(true)
  })

  it('可多次调用不报错', () => {
    for (let i = 0; i < 50; i += 1) {
      expect(generateRequestId(i)).toHaveLength(REQUEST_ID_LENGTH)
    }
  })
})
