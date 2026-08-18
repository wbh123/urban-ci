import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

export type InspectionTaskCreateRequest = Schema<'InspectionTaskCreateRequest'>
export type InspectionRecordCreateRequest = Schema<'InspectionRecordCreateRequest'>
type GeneratedInspectionTask = Schema<'InspectionTask'>
export type InspectionTaskStatus = GeneratedInspectionTask['status'] | 'ONSITE_COMPLETED'
export type InspectionTask = Omit<GeneratedInspectionTask, 'status'> & {
  status: InspectionTaskStatus
}
export type InspectionRecord = Schema<'InspectionRecord'>

export type InspectionType = InspectionTaskCreateRequest['inspectionType']
export type Severity = InspectionRecord['severity']

export interface ListInspectionTasksParams {
  buildingId?: string
  status?: InspectionTaskStatus
  page?: number
  size?: number
}

export function listInspectionTasks(
  params: ListInspectionTasksParams = {},
): Promise<InspectionTask[]> {
  return apiGet<InspectionTask[]>('/api/v1/inspection-tasks', { ...params })
}

export function getInspectionTask(taskId: string): Promise<InspectionTask> {
  return apiGet<InspectionTask>(`/api/v1/inspection-tasks/${taskId}`)
}

export function createInspectionTask(
  payload: InspectionTaskCreateRequest,
): Promise<InspectionTask> {
  return apiPost<InspectionTask>('/api/v1/inspection-tasks', payload)
}

export function transitionInspectionTask(
  taskId: string,
  action: 'start' | 'onsite-complete' | 'complete' | 'cancel',
): Promise<InspectionTask> {
  return apiPost<InspectionTask>(`/api/v1/inspection-tasks/${taskId}/${action}`)
}

export function listInspectionRecords(taskId: string): Promise<InspectionRecord[]> {
  return apiGet<InspectionRecord[]>('/api/v1/inspection-records', { taskId })
}

export function createInspectionRecord(
  payload: InspectionRecordCreateRequest,
): Promise<InspectionRecord> {
  return apiPost<InspectionRecord>('/api/v1/inspection-records', payload)
}
