import { systemHandlers } from './system'
import { authHandlers } from './auth'
import { mapHandlers } from './map'
import { archiveHandlers } from './archive'
import { buildingHandlers } from './buildings'
import { inspectionHandlers } from './inspection'
import { assetHandlers } from './assets'
import { feedbackHandlers } from './feedback'
import { aiInferenceHandlers } from './ai-inference'
import { aiGovernanceHandlers } from './ai-governance'
import { assessmentHandlers } from './assessment'
import { knowledgeHandlers } from './knowledge'

export const handlers = [
  ...systemHandlers,
  ...authHandlers,
  ...mapHandlers,
  ...archiveHandlers,
  ...buildingHandlers,
  ...inspectionHandlers,
  ...assetHandlers,
  ...feedbackHandlers,
  ...aiInferenceHandlers,
  ...aiGovernanceHandlers,
  ...assessmentHandlers,
  ...knowledgeHandlers,
]
