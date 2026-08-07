import type { Schema } from '../schema'
import { apiGet } from '../client'

export type SystemHealthData = Schema<'SystemHealthData'>

export function getSystemHealth(): Promise<SystemHealthData> {
  return apiGet<SystemHealthData>('/api/v1/system/health')
}
