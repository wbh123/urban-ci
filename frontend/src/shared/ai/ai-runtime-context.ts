import type { InjectionKey, Ref } from 'vue'
import type { AiRuntimeServiceSummary } from '@/shared/api/endpoints/ai-runtime'

export type AiRuntimeDisplayState = 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'UNKNOWN'

export interface AiRuntimeContext {
  state: Ref<AiRuntimeDisplayState>
  services: Ref<AiRuntimeServiceSummary[]>
  policy: Ref<string>
  loading: Ref<boolean>
}

export const AI_RUNTIME_CONTEXT_KEY: InjectionKey<AiRuntimeContext> = Symbol('ai-runtime-context')
