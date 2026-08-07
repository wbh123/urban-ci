import type { Schema } from '../schema'
import { apiGet, apiPost } from '../client'

// 以下请求类型由 OpenAPI 生成。
export type InspectionTaskCreateRequest = Schema<'InspectionTaskCreateRequest'>
export type InspectionRecordCreateRequest = Schema<'InspectionRecordCreateRequest'>

export type InspectionTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type InspectionType = 'ROUTINE' | 'SPECIAL'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH'

// 适配类型：phase2 巡检接口的「列表/详情/状态流转响应」在 openapi-phase2.yaml 中仅有 example，
// 尚无正式 schema。依据响应示例定义；task.buildingName 为工作台列表展示所需，
// 后端列表是否实际返回该字段需在真实联调时确认（记为契约缺口）。
export interface InspectionTask {
  taskId: string
  taskCode: string
  buildingId: string
  buildingName?: string
  inspectionType: string
  title: string
  status: InspectionTaskStatus
  version: number
}

export interface InspectionRecord {
  recordId: string
  taskId: string
  severity: Severity
  summary: string
  status: string
  inspectionPart?: string
  issueType?: string
  rectificationSuggestion?: string
}

export interface ListInspectionTasksParams {
  buildingId?: string
  status?: InspectionTaskStatus
}

export function listInspectionTasks(
  params: ListInspectionTasksParams = {},
): Promise<InspectionTask[]> {
  return apiGet<InspectionTask[]>('/api/v1/inspection-tasks', { ...params })
}

export function createInspectionTask(
  payload: InspectionTaskCreateRequest,
): Promise<InspectionTask> {
  return apiPost<InspectionTask>('/api/v1/inspection-tasks', payload)
}

export function transitionInspectionTask(
  taskId: string,
  action: 'start' | 'complete' | 'cancel',
): Promise<void> {
  return apiPost<void>(`/api/v1/inspection-tasks/${taskId}/${action}`)
}

export function listInspectionRecords(taskId: string): Promise<InspectionRecord[]> {
  return apiGet<InspectionRecord[]>('/api/v1/inspection-records', { taskId })
}

export function createInspectionRecord(
  payload: InspectionRecordCreateRequest,
): Promise<InspectionRecord> {
  return apiPost<InspectionRecord>('/api/v1/inspection-records', payload)
}
