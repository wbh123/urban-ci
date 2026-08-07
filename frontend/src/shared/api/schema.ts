import type { components, paths, operations } from './generated/schema'

export type { components, paths, operations }

/** 从生成类型中按名称取组件 schema，避免在业务代码里写超长类型索引。 */
export type Schema<K extends keyof components['schemas']> = components['schemas'][K]
