import { http, delay } from 'msw'
import { okResponse, errorResponse, requireAuth, scenarioOf } from './helpers'
import { db } from '../fixtures/data'
import type {
  InspectionTaskCreateRequest,
  InspectionRecordCreateRequest,
  InspectionTaskStatus,
} from '@/shared/api'

let taskSeq = 1
let idSeq = 100

function nextTaskCode(): string {
  taskSeq += 1
  return `IT-20260714-${String(taskSeq).padStart(4, '0')}`
}

function nextUuid(): string {
  idSeq += 1
  return `00000000-0000-0000-0000-${String(idSeq).padStart(12, '0')}`
}

function now(): string {
  return new Date().toISOString()
}

const TRANSITIONS = ['start', 'complete', 'cancel'] as const
type Transition = (typeof TRANSITIONS)[number]

export const inspectionHandlers = [
  http.get('/api/v1/inspection-tasks', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const sc = scenarioOf(request)
    if (sc === 'empty') return okResponse([])
    if (sc === 'server-error') return errorResponse('INTERNAL_ERROR', '服务器内部错误。', 500)
    if (sc === 'delay') {
      await delay(800)
      return okResponse(db.tasks)
    }
    const url = new URL(request.url)
    const buildingId = url.searchParams.get('buildingId')
    const status = url.searchParams.get('status')
    let rows = db.tasks
    if (buildingId) rows = rows.filter((t) => t.buildingId === buildingId)
    if (status) rows = rows.filter((t) => t.status === (status as InspectionTaskStatus))
    return okResponse(rows)
  }),

  http.post('/api/v1/inspection-tasks', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as InspectionTaskCreateRequest
    const building = db.buildings.find((b) => b.id === body.buildingId)
    if (!building) return errorResponse('BUILDING_NOT_FOUND', '楼栋不存在。', 404)
    const task = {
      taskId: nextUuid(),
      taskCode: nextTaskCode(),
      buildingId: body.buildingId,
      buildingName: building.buildingName,
      communityId: building.communityId,
      inspectionType: body.inspectionType,
      title: body.title ?? '现场巡检',
      description: body.description,
      plannedAt: body.plannedAt,
      status: 'PENDING' as InspectionTaskStatus,
      version: 0,
      createdAt: now(),
    }
    db.tasks.push(task)
    return okResponse(task, 201)
  }),

  http.post('/api/v1/inspection-tasks/:taskId/:action', ({ request, params }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const taskId = params.taskId as string
    const action = params.action as string
    if (!TRANSITIONS.includes(action as Transition)) {
      return errorResponse('NOT_FOUND', '接口不存在。', 404)
    }
    const task = db.tasks.find((t) => t.taskId === taskId)
    if (!task) return errorResponse('INSPECTION_TASK_NOT_FOUND', '巡检任务不存在。', 404)
    const transitionAt = now()
    if (action === 'start') {
      if (task.status !== 'PENDING') {
        return errorResponse('INVALID_TASK_STATUS', '任务状态不允许开始。', 409)
      }
      task.status = 'IN_PROGRESS'
      task.startedAt = transitionAt
    } else if (action === 'complete') {
      if (task.status !== 'IN_PROGRESS') {
        return errorResponse('INVALID_TASK_STATUS', '任务状态不允许完成。', 409)
      }
      if (!db.records.some((r) => r.taskId === taskId)) {
        return errorResponse('NO_INSPECTION_RECORD', '完成任务前至少需要一条巡检记录。', 409)
      }
      task.status = 'COMPLETED'
      task.completedAt = transitionAt
    } else {
      if (!['PENDING', 'IN_PROGRESS'].includes(task.status)) {
        return errorResponse('INVALID_TASK_STATUS', '已结束任务不能取消。', 409)
      }
      task.status = 'CANCELLED'
      task.cancelledAt = transitionAt
    }
    task.version += 1
    return okResponse(task)
  }),

  http.get('/api/v1/inspection-records', ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const url = new URL(request.url)
    const taskId = url.searchParams.get('taskId')
    const rows = taskId ? db.records.filter((r) => r.taskId === taskId) : db.records
    return okResponse(rows)
  }),

  http.post('/api/v1/inspection-records', async ({ request }) => {
    const unauth = requireAuth(request)
    if (unauth) return unauth
    const body = (await request.json().catch(() => ({}))) as InspectionRecordCreateRequest
    if (!body.taskId || !body.summary) {
      return errorResponse('BAD_REQUEST', '任务ID和巡检摘要必填。', 400, [
        { field: !body.taskId ? 'taskId' : 'summary', message: '不能为空' },
      ])
    }
    const task = db.tasks.find((t) => t.taskId === body.taskId)
    if (!task) return errorResponse('INSPECTION_TASK_NOT_FOUND', '巡检任务不存在。', 404)
    if (body.severity === 'HIGH' && !body.rectificationSuggestion) {
      return errorResponse('RECTIFICATION_REQUIRED', '高风险问题必须填写整改建议。', 422, [
        { field: 'rectificationSuggestion', message: '高风险时必填' },
      ])
    }
    const createdAt = now()
    const record = {
      recordId: nextUuid(),
      taskId: body.taskId,
      buildingId: task.buildingId,
      severity: body.severity ?? 'LOW',
      summary: body.summary,
      status: 'DRAFT',
      inspectionPart: body.inspectionPart,
      issueType: body.issueType ?? 'OTHER',
      rectificationSuggestion: body.rectificationSuggestion,
      formData: body.formData ?? {},
      inspectedAt: createdAt,
      createdAt,
    }
    db.records.push(record)
    return okResponse(record, 201)
  }),
]
