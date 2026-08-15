import { describe, expect, it } from 'vitest'
import filterBarSource from '../AppFilterBar.vue?raw'
import queryFieldSource from '../AppQueryField.vue?raw'
import dateTimeSource from '../AppDateTime.vue?raw'
import actionButtonSource from '../AppActionButton.vue?raw'

describe('shared business controls contract', () => {
  it('provides a reusable filter bar with query and reset actions', () => {
    expect(filterBarSource).toContain("defineEmits")
    expect(filterBarSource).toContain("'query'")
    expect(filterBarSource).toContain("'reset'")
    expect(filterBarSource).toContain('<slot')
  })

  it('provides a query field that supports enter-to-query and clearing', () => {
    expect(queryFieldSource).toContain("'update:modelValue'")
    expect(queryFieldSource).toContain("'query'")
    expect(queryFieldSource).toContain('@keyup.enter')
    expect(queryFieldSource).toContain('clearable')
  })

  it('formats user-facing date time consistently', () => {
    expect(dateTimeSource).toContain('YYYY-MM-DD HH:mm')
    expect(dateTimeSource).toContain("'—'")
  })

  it('uses a real small action button instead of a link action', () => {
    expect(actionButtonSource).toContain('size="small"')
    expect(actionButtonSource).not.toContain('link')
  })
})
