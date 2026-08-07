<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  askKnowledgeQuestion,
  toAppError,
  type KnowledgeAnswerView,
  type KnowledgeQuestionRequest,
} from '@/shared/api'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppLoading from '@/shared/components/AppLoading.vue'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const form = reactive({
  question: '',
  communityId: '',
  buildingId: '',
  topK: 5,
})
const loading = ref(false)
const errorMessage = ref('')
const answer = ref<KnowledgeAnswerView | null>(null)
const refused = computed(() => answer.value?.status === 'REFUSED')

function formatDateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

async function submitQuestion(): Promise<void> {
  const question = form.question.trim()
  if (question.length < 2) {
    ElMessage.warning('请输入至少两个字符的问题')
    return
  }

  const request: KnowledgeQuestionRequest = { question, topK: form.topK }
  if (form.communityId.trim()) request.communityId = form.communityId.trim()
  if (form.buildingId.trim()) request.buildingId = form.buildingId.trim()

  loading.value = true
  errorMessage.value = ''
  answer.value = null
  try {
    answer.value = await askKnowledgeQuestion(request)
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="knowledge-panel" :class="{ compact }">
    <el-card shadow="never" class="question-card">
      <template #header>
        <div class="card-title">
          <div>
            <strong>内部知识问答</strong>
            <span>只检索当前账号和业务范围允许访问的已审核知识片段</span>
          </div>
          <el-tag effect="plain" type="info">证据优先</el-tag>
        </div>
      </template>

      <el-form label-position="top" @submit.prevent="submitQuestion">
        <el-form-item label="问题">
          <el-input
            v-model="form.question"
            type="textarea"
            :rows="compact ? 3 : 4"
            maxlength="1000"
            show-word-limit
            placeholder="例如：发现疑似裂缝后，现场需要补充记录哪些信息？"
          />
        </el-form-item>
        <div class="scope-grid">
          <el-form-item label="社区编号（可选）">
            <el-input v-model="form.communityId" clearable placeholder="限定社区范围的 UUID" />
          </el-form-item>
          <el-form-item label="楼栋编号（可选）">
            <el-input v-model="form.buildingId" clearable placeholder="限定楼栋范围的 UUID" />
          </el-form-item>
          <el-form-item label="最多引用">
            <el-input-number v-model="form.topK" :min="1" :max="8" controls-position="right" />
          </el-form-item>
        </div>
        <el-button type="primary" :loading="loading" native-type="submit">检索并回答</el-button>
      </el-form>
    </el-card>

    <AppLoading :visible="loading" inline text="正在筛选权限范围内的知识证据…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="submitQuestion" />
    <AppEmpty v-if="!loading && !errorMessage && !answer" description="提交问题后，答案和可追溯引用将显示在这里" />

    <el-card v-if="answer" shadow="never" class="answer-card">
      <template #header>
        <div class="answer-header">
          <div>
            <strong>{{ refused ? '受控拒答' : '检索结果' }}</strong>
            <span>{{ formatDateTime(answer.generatedAt) }}</span>
          </div>
          <el-tag :type="answer.evidenceSufficient ? 'success' : 'warning'">
            {{ answer.evidenceSufficient ? '证据充分' : '证据不足' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        v-if="answer.status === 'REFUSED'"
        type="warning"
        title="当前知识范围无法形成可靠回答"
        :closable="false"
        show-icon
      />
      <p class="answer-text">{{ answer.answer }}</p>

      <div v-if="answer.citations.length" class="citation-list">
        <h3>引用依据</h3>
        <article v-for="citation in answer.citations" :key="citation.citationId" class="citation-card">
          <header>
            <div>
              <strong>{{ citation.documentTitle }}</strong>
              <span>{{ citation.documentCode }} · 版本 {{ citation.documentVersion }}</span>
            </div>
            <el-tag size="small" effect="plain">引用 {{ citation.rank }}</el-tag>
          </header>
          <p class="citation-location">
            <span v-if="citation.sectionTitle">章节：{{ citation.sectionTitle }}</span>
            <span v-if="citation.pageNumber">页码：{{ citation.pageNumber }}</span>
            <span>匹配度：{{ Number(citation.score ?? 0).toFixed(3) }}</span>
          </p>
          <blockquote>{{ citation.excerpt }}</blockquote>
        </article>
      </div>

      <dl class="trace-grid">
        <div><dt>提供者</dt><dd>{{ answer.providerCode }}</dd></div>
        <div><dt>模型/策略</dt><dd>{{ answer.modelCode }}</dd></div>
        <div><dt>问题编号</dt><dd>{{ answer.questionId }}</dd></div>
      </dl>
      <el-alert :title="answer.disclaimer" type="info" :closable="false" show-icon />
    </el-card>
  </section>
</template>

<style scoped lang="scss">
.knowledge-panel { display: grid; gap: 18px; }
.card-title, .answer-header, .citation-card header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-title > div, .answer-header > div, .citation-card header > div { display: grid; gap: 5px; }
.card-title span, .answer-header span, .citation-card header span { color: #667085; font-size: 13px; }
.scope-grid { display: grid; grid-template-columns: 1fr 1fr minmax(130px, .35fr); gap: 14px; }
.scope-grid :deep(.el-input-number) { width: 100%; }
.answer-text { margin: 0 0 18px; color: #263238; font-size: 16px; line-height: 1.8; white-space: pre-wrap; }
.citation-list { display: grid; gap: 12px; margin: 18px 0; }
.citation-list h3 { margin: 0; font-size: 16px; }
.citation-card { padding: 16px; border: 1px solid #dce7e3; border-radius: 14px; background: #f8fbfa; }
.citation-location { display: flex; flex-wrap: wrap; gap: 12px; margin: 12px 0 8px; color: #667085; font-size: 13px; }
blockquote { margin: 0; padding: 12px 14px; border-left: 3px solid #2d796b; border-radius: 0 8px 8px 0; background: #fff; color: #344054; line-height: 1.7; }
.trace-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 18px 0; }
.trace-grid div { min-width: 0; padding: 12px; border-radius: 10px; background: #f4f6f8; }
.trace-grid dt { color: #667085; font-size: 12px; }
.trace-grid dd { margin: 5px 0 0; overflow-wrap: anywhere; }
.compact .scope-grid { grid-template-columns: 1fr; }
@media (max-width: 760px) {
  .scope-grid, .trace-grid { grid-template-columns: 1fr; }
  .card-title, .answer-header, .citation-card header { align-items: flex-start; flex-direction: column; }
}
</style>
