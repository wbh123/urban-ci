<script setup lang="ts">
import { computed } from 'vue'
import type { AiIntelligentAnalysisResult } from '@/shared/api'
import {
  parseAiAnalysisAnswer,
  sectionTone,
  type AiAnalysisSection,
} from '@/shared/ai/ai-analysis-structure'

const props = withDefaults(defineProps<{
  result: AiIntelligentAnalysisResult
  title?: string
  subtitle?: string
}>(), {
  title: 'AI 综合研判',
  subtitle: '综合业务档案、巡检证据、视觉识别与正式风险结果，仅用于辅助专业判断。',
})

const document = computed(() => parseAiAnalysisAnswer(props.result.answer || ''))
const coreSection = computed(() => document.value.sections.find((section) => section.key === 'core') ?? null)
const limitSections = computed(() => document.value.sections.filter((section) => section.key === 'limits'))
const contentSections = computed(() => document.value.sections.filter(
  (section) => section.key !== 'core' && section.key !== 'limits',
))
const toolSteps = computed(() => (props.result.steps ?? []).filter((step) => step.type === 'TOOL'))
const successfulTools = computed(() => toolSteps.value.filter((step) => step.status === 'SUCCEEDED').length)

function durationLabel(durationMs?: number | null): string {
  if (durationMs == null) return '—'
  if (durationMs < 1000) return `${durationMs} ms`
  return `${(durationMs / 1000).toFixed(durationMs >= 10_000 ? 1 : 2)} s`
}

function statusLabel(status?: string): string {
  if (status === 'SUCCEEDED') return '分析完成'
  if (status === 'PARTIAL_SUCCEEDED') return '部分完成'
  if (status === 'FAILED') return '分析失败'
  if (status === 'RUNNING') return '分析中'
  return status || '未知状态'
}

function statusType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'PARTIAL_SUCCEEDED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function sectionIcon(section: AiAnalysisSection): string {
  return ({
    building: '楼',
    inspection: '证',
    risk: '险',
    vision: '视',
    basis: '据',
    review: '核',
    limits: '限',
    core: '结',
    other: '析',
  } as Record<string, string>)[section.key] ?? '析'
}

function stepName(toolName?: string | null): string {
  if (!toolName) return '模型步骤'
  const names: Record<string, string> = {
    BuildingOverviewTool: '楼栋档案',
    InspectionEvidenceTool: '巡检证据',
    LatestVisionAnalysisTool: '历史视觉',
    RiskAssessmentTool: '风险评估',
    RenewalPriorityTool: '更新优先级',
    VisionAnalysisTool: '实时视觉',
    KnowledgeRetrievalTool: '知识检索',
    DifyReviewAssistTool: '复核辅助',
    DifyReportDraftTool: '报告草稿',
  }
  return names[toolName] ?? toolName.replace(/Tool$/, '')
}
</script>

<template>
  <article class="ai-structured-panel">
    <header class="analysis-hero">
      <div class="analysis-hero__identity">
        <div class="analysis-kicker"><span>✦</span><b>DEEPSEEK · TOOL CALLING</b></div>
        <h3>{{ title }}</h3>
        <p>{{ subtitle }}</p>
      </div>
      <div class="analysis-metrics">
        <div class="metric-cell">
          <span>运行状态</span>
          <el-tag :type="statusType(result.status)" effect="plain" round>{{ statusLabel(result.status) }}</el-tag>
        </div>
        <div class="metric-cell">
          <span>模型</span>
          <strong>{{ result.modelCode || 'DeepSeek' }}</strong>
        </div>
        <div class="metric-cell">
          <span>总耗时</span>
          <strong>{{ durationLabel(result.durationMs) }}</strong>
        </div>
        <div class="metric-cell">
          <span>工具调用</span>
          <strong>{{ successfulTools }}/{{ toolSteps.length }}</strong>
        </div>
      </div>
    </header>

    <section v-if="coreSection" class="core-conclusion">
      <div class="core-conclusion__mark">AI</div>
      <div class="core-conclusion__body">
        <span>核心结论</span>
        <template v-for="(block, blockIndex) in coreSection.blocks" :key="`core-${blockIndex}`">
          <p v-if="block.type === 'paragraph'">{{ block.text }}</p>
          <ul v-else-if="block.type === 'list' && !block.ordered">
            <li v-for="item in block.items" :key="item">{{ item }}</li>
          </ul>
          <ol v-else-if="block.type === 'list'">
            <li v-for="item in block.items" :key="item">{{ item }}</li>
          </ol>
          <div v-else class="analysis-table-wrap">
            <table>
              <thead><tr><th v-for="header in block.headers" :key="header">{{ header }}</th></tr></thead>
              <tbody><tr v-for="(row, rowIndex) in block.rows" :key="rowIndex"><td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td></tr></tbody>
            </table>
          </div>
        </template>
      </div>
    </section>

    <div class="analysis-section-grid">
      <section
        v-for="(section, sectionIndex) in contentSections"
        :key="`${section.key}-${sectionIndex}`"
        class="analysis-section-card"
        :data-tone="sectionTone(section.key)"
        :data-key="section.key"
      >
        <header>
          <div class="section-icon">{{ sectionIcon(section) }}</div>
          <div><strong>{{ section.title }}</strong><small>{{ section.key === 'vision' ? '视觉结果均为疑似线索' : '基于本次已授权数据与工具结果' }}</small></div>
        </header>
        <div class="section-content">
          <template v-for="(block, blockIndex) in section.blocks" :key="blockIndex">
            <p v-if="block.type === 'paragraph'">{{ block.text }}</p>
            <ul v-else-if="block.type === 'list' && !block.ordered">
              <li v-for="item in block.items" :key="item">{{ item }}</li>
            </ul>
            <ol v-else-if="block.type === 'list'">
              <li v-for="item in block.items" :key="item">{{ item }}</li>
            </ol>
            <div v-else class="analysis-table-wrap">
              <table>
                <thead><tr><th v-for="header in block.headers" :key="header">{{ header }}</th></tr></thead>
                <tbody><tr v-for="(row, rowIndex) in block.rows" :key="rowIndex"><td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td></tr></tbody>
              </table>
            </div>
          </template>
        </div>
      </section>
    </div>

    <section v-for="(section, sectionIndex) in limitSections" :key="`limits-${sectionIndex}`" class="limit-section">
      <div class="limit-section__icon">i</div>
      <div>
        <strong>{{ section.title || '能力限制' }}</strong>
        <template v-for="(block, blockIndex) in section.blocks" :key="blockIndex">
          <p v-if="block.type === 'paragraph'">{{ block.text }}</p>
          <ul v-else-if="block.type === 'list'">
            <li v-for="item in block.items" :key="item">{{ item }}</li>
          </ul>
          <div v-else class="analysis-table-wrap">
            <table>
              <thead><tr><th v-for="header in block.headers" :key="header">{{ header }}</th></tr></thead>
              <tbody><tr v-for="(row, rowIndex) in block.rows" :key="rowIndex"><td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td></tr></tbody>
            </table>
          </div>
        </template>
      </div>
    </section>

    <section v-if="toolSteps.length" class="execution-trace">
      <header>
        <div><strong>证据调用轨迹</strong><small>仅展示可审计工具执行状态，不展示模型私有推理过程</small></div>
        <el-tag type="success" effect="plain" round>{{ successfulTools }} 个工具成功</el-tag>
      </header>
      <div class="trace-grid">
        <div v-for="step in toolSteps" :key="`${step.seqNo}-${step.toolName}`" class="trace-item" :data-status="step.status">
          <span class="trace-dot" />
          <div><strong>{{ stepName(step.toolName) }}</strong><small>{{ step.provider || 'Spring Boot' }}</small></div>
          <div class="trace-item__status"><b>{{ step.status === 'SUCCEEDED' ? '完成' : step.status === 'FAILED' ? '受限' : step.status }}</b><small>{{ durationLabel(step.durationMs) }}</small></div>
        </div>
      </div>
    </section>

    <details class="raw-analysis">
      <summary>{{ document.structured ? '查看模型原始输出' : '当前为兼容展示 · 查看原始输出' }}</summary>
      <pre>{{ document.raw }}</pre>
    </details>
  </article>
</template>

<style scoped lang="scss">
.ai-structured-panel{display:grid;gap:14px;min-width:0}.analysis-hero{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(420px,.9fr);gap:20px;align-items:stretch;padding:20px;border:1px solid #cfe7df;border-radius:var(--usp-radius-xl);background:linear-gradient(135deg,#f5fbf9 0%,#fff 55%,#f2f8ff 100%);box-shadow:var(--usp-shadow-sm)}.analysis-hero__identity{display:grid;align-content:center;gap:7px;min-width:0}.analysis-kicker{display:flex;align-items:center;gap:7px;color:#176354;font-size:11px;letter-spacing:.08em}.analysis-kicker span{display:grid;width:24px;height:24px;place-items:center;border-radius:999px;background:#dff3ed;font-size:13px}.analysis-hero h3{margin:0;font-size:23px;letter-spacing:-.02em}.analysis-hero p{max-width:760px;margin:0;color:var(--usp-color-text-secondary);line-height:1.65}.analysis-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.metric-cell{display:grid;align-content:center;gap:6px;min-height:70px;padding:11px 13px;border:1px solid rgba(23,99,84,.12);border-radius:var(--usp-radius-lg);background:rgba(255,255,255,.78)}.metric-cell span{color:var(--usp-color-text-tertiary);font-size:10px;font-weight:700}.metric-cell strong{overflow:hidden;text-overflow:ellipsis;font-size:15px}.core-conclusion{display:grid;grid-template-columns:56px minmax(0,1fr);gap:15px;padding:18px 20px;border:1px solid #b8dfd4;border-radius:var(--usp-radius-xl);background:linear-gradient(135deg,#edf9f5,#fbfefd)}.core-conclusion__mark{display:grid;width:50px;height:50px;place-items:center;border-radius:16px;background:#176354;color:white;font-size:15px;font-weight:900;box-shadow:0 8px 20px rgba(23,99,84,.18)}.core-conclusion__body{display:grid;gap:7px}.core-conclusion__body>span{color:#176354;font-size:11px;font-weight:900;letter-spacing:.08em}.core-conclusion p,.core-conclusion ul,.core-conclusion ol{margin:0;color:#344054;line-height:1.72}.analysis-section-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.analysis-section-card{display:grid;align-content:start;min-width:0;overflow:hidden;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface);box-shadow:var(--usp-shadow-sm)}.analysis-section-card>header{display:flex;align-items:center;gap:10px;padding:13px 15px;border-bottom:1px solid var(--usp-color-border);background:linear-gradient(180deg,#fff,var(--usp-color-surface-muted))}.section-icon{display:grid;flex:0 0 34px;width:34px;height:34px;place-items:center;border-radius:11px;background:#eef3f8;color:#475467;font-size:12px;font-weight:900}.analysis-section-card[data-tone='warning'] .section-icon{background:#fff4dd;color:#9a5b00}.analysis-section-card[data-tone='danger'] .section-icon{background:#fff0f0;color:#b42318}.analysis-section-card>header>div:last-child{display:grid;gap:2px;min-width:0}.analysis-section-card>header strong{font-size:14px}.analysis-section-card>header small{color:var(--usp-color-text-tertiary);font-size:10px}.section-content{display:grid;gap:9px;padding:14px 15px}.section-content p,.section-content ul,.section-content ol{margin:0;color:var(--usp-color-text-secondary);line-height:1.68}.section-content ul,.section-content ol,.core-conclusion ul,.core-conclusion ol,.limit-section ul{padding-left:20px}.section-content li+li,.core-conclusion li+li,.limit-section li+li{margin-top:5px}.analysis-table-wrap{max-width:100%;overflow:auto;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg)}.analysis-table-wrap table{width:100%;min-width:440px;border-collapse:collapse;background:#fff;font-size:11px}.analysis-table-wrap th,.analysis-table-wrap td{padding:8px 10px;border-bottom:1px solid var(--usp-color-border);text-align:left;vertical-align:top;line-height:1.5}.analysis-table-wrap th{background:var(--usp-color-surface-muted);color:var(--usp-color-text-secondary);font-weight:800;white-space:nowrap}.analysis-table-wrap tr:last-child td{border-bottom:0}.limit-section{display:grid;grid-template-columns:38px minmax(0,1fr);gap:11px;padding:13px 15px;border:1px solid #e4e7ec;border-radius:var(--usp-radius-xl);background:#f8fafc;color:#475467}.limit-section__icon{display:grid;width:32px;height:32px;place-items:center;border-radius:999px;background:#e4e7ec;color:#667085;font-family:serif;font-weight:900}.limit-section>div:last-child{display:grid;gap:5px}.limit-section p,.limit-section ul{margin:0;line-height:1.6}.execution-trace{display:grid;gap:10px;padding:15px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-xl);background:var(--usp-color-surface)}.execution-trace>header{display:flex;align-items:center;justify-content:space-between;gap:12px}.execution-trace>header>div{display:grid;gap:2px}.execution-trace small{color:var(--usp-color-text-tertiary);font-size:10px}.trace-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.trace-item{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:8px;min-width:0;padding:9px 10px;border:1px solid var(--usp-color-border);border-radius:var(--usp-radius-lg);background:var(--usp-color-surface-muted)}.trace-dot{width:8px;height:8px;border-radius:999px;background:#12b76a;box-shadow:0 0 0 4px rgba(18,183,106,.1)}.trace-item[data-status='FAILED'] .trace-dot{background:#f79009;box-shadow:0 0 0 4px rgba(247,144,9,.1)}.trace-item>div{display:grid;gap:2px;min-width:0}.trace-item strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px}.trace-item__status{text-align:right}.trace-item__status b{font-size:10px}.raw-analysis{padding:10px 12px;border:1px dashed var(--usp-color-border);border-radius:var(--usp-radius-xl);background:#fbfcfd}.raw-analysis summary{cursor:pointer;color:var(--usp-color-text-secondary);font-size:11px;font-weight:700}.raw-analysis pre{max-height:420px;overflow:auto;margin:10px 0 0;padding:12px;border-radius:var(--usp-radius-lg);background:#101828;color:#f2f4f7;font:11px/1.65 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}@media(max-width:1180px){.analysis-hero{grid-template-columns:1fr}.trace-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:820px){.analysis-section-grid{grid-template-columns:1fr}.analysis-metrics{grid-template-columns:repeat(2,1fr)}}@media(max-width:560px){.analysis-hero{padding:15px}.analysis-metrics{grid-template-columns:1fr 1fr}.core-conclusion{grid-template-columns:1fr}.core-conclusion__mark{width:40px;height:40px}.trace-grid{grid-template-columns:1fr}.execution-trace>header{align-items:flex-start;flex-direction:column}}
</style>
