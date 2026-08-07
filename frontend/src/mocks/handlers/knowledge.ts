import { http } from 'msw'
import type {
  KnowledgeAnswerView,
  KnowledgeDocumentCreateRequest,
  KnowledgeDocumentView,
  KnowledgeQuestionRequest,
} from '@/shared/api'
import { errorResponse, okResponse, requireAuth } from './helpers'

const disclaimer = '内部知识问答仅用于巡检与专业复核辅助，不作为正式房屋安全鉴定结论。'

function shouldRefuse(question: string): boolean {
  return ['未知', '无依据', '没有收录', '正式结论'].some((keyword) => question.includes(keyword))
}

export const knowledgeHandlers = [
  http.post('/api/v1/knowledge/questions', async ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated

    const body = await request.json() as KnowledgeQuestionRequest
    const question = body.question?.trim() ?? ''
    if (question.length < 2) {
      return errorResponse('VALIDATION_ERROR', '问题至少需要两个字符。', 400, [
        { field: 'question', message: '问题至少需要两个字符。' },
      ])
    }

    if (shouldRefuse(question)) {
      const refused: KnowledgeAnswerView = {
        questionId: crypto.randomUUID(),
        status: 'REFUSED',
        answer: '当前可访问知识库中没有足够证据回答该问题，请补充经过审核的文档或转交人工确认。',
        evidenceSufficient: false,
        citations: [],
        providerCode: 'LOCAL_KNOWLEDGE_QA',
        modelCode: 'DETERMINISTIC_RETRIEVAL_V1',
        generatedAt: new Date().toISOString(),
        disclaimer,
      }
      return okResponse(refused)
    }

    const answered: KnowledgeAnswerView = {
      questionId: crypto.randomUUID(),
      status: 'ANSWERED',
      answer: '根据当前可访问的巡检工作指引，发现疑似裂缝后应先记录位置、方向、长度和宽度，并补拍包含比例尺的整体照片与近景照片，再提交专业复核。',
      evidenceSufficient: true,
      citations: [
        {
          citationId: crypto.randomUUID(),
          documentId: crypto.randomUUID(),
          documentCode: 'INSPECTION_GUIDE',
          documentTitle: '建筑表观病害巡检工作指引',
          documentVersion: '2026.1',
          chunkId: crypto.randomUUID(),
          sectionTitle: '裂缝现场记录要求',
          pageNumber: 12,
          excerpt: '记录裂缝位置、走向、长度和宽度，并拍摄整体定位照片、带比例尺的近景照片。',
          rank: 1,
          score: 0.91,
        },
      ],
      providerCode: 'LOCAL_KNOWLEDGE_QA',
      modelCode: 'DETERMINISTIC_RETRIEVAL_V1',
      generatedAt: new Date().toISOString(),
      disclaimer,
    }
    return okResponse(answered)
  }),
  http.post('/api/v1/knowledge/documents', async ({ request }) => {
    const unauthenticated = requireAuth(request)
    if (unauthenticated) return unauthenticated

    const body = await request.json() as KnowledgeDocumentCreateRequest
    if (!body.documentCode || !body.title || !body.chunks?.length) {
      return errorResponse('VALIDATION_ERROR', '文档编号、标题和知识片段不能为空。', 400)
    }

    const view: KnowledgeDocumentView = {
      documentId: crypto.randomUUID(),
      documentCode: body.documentCode,
      title: body.title,
      documentVersion: body.documentVersion,
      status: body.status,
      chunkCount: body.chunks.length,
      createdAt: new Date().toISOString(),
    }
    return okResponse(view, 201)
  }),
]
