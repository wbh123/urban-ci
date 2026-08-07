import { describe, expect, it } from 'vitest'
import source from './ConsoleReviewDetailPage.vue?raw'

describe('ConsoleReviewDetailPage error isolation', () => {
  it('uses a separate image error state instead of replacing the task error', () => {
    expect(source).toContain("const imageErrorMessage = ref('')")
    expect(source).toContain('async function loadImage(): Promise<void>')
    expect(source).toContain('imageErrorMessage.value = toAppError(error).message')
    expect(source).toContain('v-if="imageErrorMessage"')
  })
})
