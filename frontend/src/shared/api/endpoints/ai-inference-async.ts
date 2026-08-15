import { apiGet } from '../client'
import {
  createAiAccuracyExecution,
  createAiInference as createBaseInference,
  getAiInferenceExecution,
  listAiModels,
  type AiInferenceTask,
  type CreateAiInferenceRequest,
} from './ai-inference'

const POLL_INTERVAL_MS = 2000
const MAX_POLLS = 300

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function route(payload: CreateAiInferenceRequest): Promise<CreateAiInferenceRequest> {
  if (payload.providerCode && payload.capabilityType) return payload
  const catalog = await listAiModels()
  const model = catalog.content?.find((item) => item.modelId === payload.modelId)
  return {
    ...payload,
    providerCode: payload.providerCode ?? model?.providerCode,
    capabilityType: payload.capabilityType ?? model?.capabilityType,
  }
}

function shouldUseAccuracy(payload: CreateAiInferenceRequest): boolean {
  if (payload.inferenceProfile) return payload.inferenceProfile === 'ACCURACY'
  return payload.mode === 'REAL'
    && payload.providerCode === 'FAST_API'
    && payload.capabilityType === 'VISION_INFERENCE'
}

export async function createAiInference(
  payload: CreateAiInferenceRequest,
): Promise<AiInferenceTask> {
  const effective = await route(payload)
  if (!shouldUseAccuracy(effective)) {
    return createBaseInference(effective)
  }

  const submission = await createAiAccuracyExecution(effective)
  for (let poll = 0; poll < MAX_POLLS; poll += 1) {
    const execution = await getAiInferenceExecution(submission.taskId)
    if (execution.status === 'SUCCEEDED' || execution.status === 'REJECTED') {
      if (!execution.inferenceId) {
        throw new Error('高精度识别已结束，但未生成正式推理结果编号')
      }
      return apiGet<AiInferenceTask>(
        `/api/v1/ai-inferences/${execution.inferenceId}/rich-result`,
      )
    }
    if (execution.status === 'FAILED') {
      throw new Error(execution.errorMessage || '高精度识别执行失败')
    }
    await delay(POLL_INTERVAL_MS)
  }
  throw new Error('高精度识别仍在后台运行，请稍后在推理记录中查看结果')
}
