import type { components } from '../generated/schema'
import { apiPost } from '../client'

export type KnowledgeQuestionRequest = components['schemas']['KnowledgeQuestionRequest']
export type KnowledgeAnswerView = components['schemas']['KnowledgeAnswerView']
export type KnowledgeCitationView = components['schemas']['KnowledgeCitationView']
export type KnowledgeDocumentCreateRequest = components['schemas']['KnowledgeDocumentCreateRequest']
export type KnowledgeDocumentView = components['schemas']['KnowledgeDocumentView']

export function askKnowledgeQuestion(
  request: KnowledgeQuestionRequest,
): Promise<KnowledgeAnswerView> {
  return apiPost<KnowledgeAnswerView>('/api/v1/knowledge/questions', request)
}

export function createKnowledgeDocument(
  request: KnowledgeDocumentCreateRequest,
): Promise<KnowledgeDocumentView> {
  return apiPost<KnowledgeDocumentView>('/api/v1/knowledge/documents', request)
}
