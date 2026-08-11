<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkbenchDataWallMap from './WorkbenchDataWallMap.vue'
import type { DashboardBuilding, DistributionBucket, RiskOverview } from '@/shared/api/endpoints/reports'

type WallView = 'INFO' | 'MAP'

const props = defineProps<{
  overview: RiskOverview | null
  loading: boolean
  error: boolean
}>()

const emit = defineEmits<{
  exit: []
  openMap: []
  openRisk: []
}>()

const wallRoot = ref<HTMLElement | null>(null)
const fullscreen = ref(false)
const wallView = ref<WallView>('INFO')
const selectedWallBuilding = ref<DashboardBuilding | null>(null)

const summaryCards = computed(() => {
  const summary = props.overview?.summary
  if (!summary) return []
  const coverage = summary.buildingCount > 0
    ? Math.round((summary.assessedBuildingCount / summary.buildingCount) * 100)
    : 0
  return [
    { key: 'buildings', label: '楼栋总数', value: summary.buildingCount, suffix: '栋', tone: 'primary' },
    { key: 'assessed', label: '已评估', value: summary.assessedBuildingCount, suffix: '栋', tone: 'success' },
    { key: 'highRisk', label: '高风险', value: summary.highRiskCount, suffix: '栋', tone: 'danger' },
    { key: 'priority', label: '高优先级', value: summary.highPriorityCount, suffix: '栋', tone: 'warning' },
    { key: 'review', label: '待人工复核', value: summary.lowConfidenceCount, suffix: '栋', tone: 'review' },
    { key: 'coverage', label: '评估覆盖率', value: coverage, suffix: '%', tone: 'info' },
  ]
})

const generatedAt = computed(() => {
  const value = props.overview?.generatedAt
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
})

function maxCount(rows: DistributionBucket[]): number {
  return Math.max(1, ...rows.map((item) => item.count))
}

function barWidth(rows: DistributionBucket[], count: number): string {
  return `${Math.max(5, Math.round((count / maxCount(rows)) * 100))}%`
}

function tone(code: string): string {
  const normalized = code.toUpperCase()
  if (normalized.includes('HIGH') || normalized.includes('CRITICAL')) return 'danger'
  if (normalized.includes('MEDIUM') || normalized.includes('MODERATE')) return 'warning'
  if (normalized.includes('LOW') || normalized.includes('CURRENT') || normalized.includes('COMPLETE')) return 'success'
  if (normalized.includes('STALE') || normalized.includes('PARTIAL')) return 'info'
  return 'neutral'
}

function riskLabel(level?: string): string {
  const labels: Record<string, string> = {
    VERY_HIGH: '极高风险',
    HIGH: '高风险',
    MEDIUM: '中风险',
    LOW: '低风险',
  }
  return level ? labels[level] ?? level : '待评估'
}

function freshnessLabel(value: DashboardBuilding['freshness']): string {
  if (value === 'CURRENT') return '当前有效'
  if (value === 'STALE') return '结果已过期'
  return '暂无结果'
}

function showBuildingDetail(row: DashboardBuilding): void {
  selectedWallBuilding.value = row
}

function closeBuildingDetail(): void {
  selectedWallBuilding.value = null
}

function openSelectedRiskPage(): void {
  emit('openRisk')
}

async function enterFullscreen(): Promise<void> {
  if (document.fullscreenElement || !wallRoot.value?.requestFullscreen) return
  await wallRoot.value.requestFullscreen()
}

function syncFullscreen(): void {
  fullscreen.value = document.fullscreenElement === wallRoot.value
}

onMounted(() => document.addEventListener('fullscreenchange', syncFullscreen))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreen))
</script>

<template>
  <section ref="wallRoot" class="data-wall" :class="[{ 'is-fullscreen': fullscreen }, `is-${wallView.toLowerCase()}`]">
    <WorkbenchDataWallMap
      v-if="wallView === 'MAP' && overview && !loading && !error"
      class="map-backdrop"
      @open-map="emit('openMap')"
    />

    <header class="wall-header">
      <div class="wall-header-side wall-header-side--left">
        <div class="wall-view-switch" aria-label="数据大屏显示模式">
          <button type="button" :class="{ active: wallView === 'INFO' }" @click="wallView = 'INFO'">信息</button>
          <button type="button" :class="{ active: wallView === 'MAP' }" @click="wallView = 'MAP'">地图 + 信息</button>
        </div>
      </div>

      <div class="wall-title">
        <strong>城安智序 · 城市建筑安全治理态势中心</strong>
        <span>{{ wallView === 'MAP' ? '城市空间风险分布与重点治理对象' : '城市房屋安全风险筛查与治理总览' }}</span>
      </div>

      <div class="wall-header-side wall-header-side--right">
        <el-button v-if="!fullscreen" round @click="enterFullscreen">全屏显示</el-button>
        <el-button v-if="!fullscreen" type="primary" round @click="emit('exit')">返回工作台</el-button>
      </div>
    </header>

    <div v-if="loading" class="wall-state"><el-skeleton :rows="8" animated /></div>
    <div v-else-if="error" class="wall-state wall-state--error">
      <strong>风险态势暂时不可用</strong>
      <span>请稍后刷新或返回工作台继续处理其他业务。</span>
    </div>
    <div v-else-if="!overview" class="wall-state">
      <strong>暂无可展示的风险态势数据</strong>
      <span>完成楼栋评估后，大屏将自动展示统计结果。</span>
    </div>

    <template v-else-if="wallView === 'INFO'">
      <div class="wall-metrics info-metrics">
        <article v-for="item in summaryCards" :key="item.key" class="wall-metric" :data-tone="item.tone">
          <span>{{ item.label }}</span>
          <div><strong>{{ item.value.toLocaleString('zh-CN') }}</strong><small>{{ item.suffix }}</small></div>
        </article>
      </div>

      <div class="info-grid">
        <section class="wall-panel compact-info-panel">
          <div class="panel-title"><strong>风险等级分布</strong><span>当前评估结果</span></div>
          <div class="bar-list compact-bars">
            <div v-for="row in overview.riskDistribution" :key="row.code" class="bar-row">
              <span class="bar-label"><i :data-tone="tone(row.code)" />{{ row.label }}</span>
              <div class="bar-track"><span class="bar-fill" :data-tone="tone(row.code)" :style="{ width: barWidth(overview.riskDistribution, row.count) }" /></div>
              <strong>{{ row.count }}</strong>
            </div>
            <p v-if="overview.riskDistribution.length === 0" class="empty-copy">暂无风险分布数据</p>
          </div>
        </section>

        <section class="wall-panel core-panel">
          <div class="panel-title"><strong>城市治理核心态势</strong><span>{{ overview.summary.communityCount }} 个小区纳入统计</span></div>
          <div class="core-orbit">
            <div class="core-ring core-ring--outer" /><div class="core-ring core-ring--inner" />
            <div class="core-value"><span>高风险楼栋</span><strong>{{ overview.summary.highRiskCount }}</strong><small>重点关注</small></div>
            <div class="orbit-chip orbit-chip--one"><span>待复核</span><strong>{{ overview.summary.lowConfidenceCount }}</strong></div>
            <div class="orbit-chip orbit-chip--two"><span>高优先</span><strong>{{ overview.summary.highPriorityCount }}</strong></div>
            <div class="orbit-chip orbit-chip--three"><span>未评分</span><strong>{{ overview.summary.noResultCount }}</strong></div>
            <div class="orbit-chip orbit-chip--four"><span>已过期</span><strong>{{ overview.summary.staleCount }}</strong></div>
          </div>
          <button type="button" class="map-entry" @click="wallView = 'MAP'"><span>进入地图空间态势</span><b>→</b></button>
        </section>

        <section class="wall-panel">
          <div class="panel-title"><strong>更新优先级分布</strong><span>治理工作排序</span></div>
          <div class="bar-list">
            <div v-for="row in overview.priorityDistribution" :key="row.code" class="bar-row">
              <span class="bar-label"><i :data-tone="tone(row.code)" />{{ row.label }}</span>
              <div class="bar-track"><span class="bar-fill" :data-tone="tone(row.code)" :style="{ width: barWidth(overview.priorityDistribution, row.count) }" /></div>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
        </section>

        <section class="wall-panel compact-info-panel">
          <div class="panel-title"><strong>资料完整度</strong><span>评估资料覆盖情况</span></div>
          <div class="bar-list compact-bars">
            <div v-for="row in overview.completenessDistribution" :key="row.code" class="bar-row">
              <span class="bar-label"><i :data-tone="tone(row.code)" />{{ row.label }}</span>
              <div class="bar-track"><span class="bar-fill" :data-tone="tone(row.code)" :style="{ width: barWidth(overview.completenessDistribution, row.count) }" /></div>
              <strong>{{ row.count }}</strong>
            </div>
          </div>
        </section>

        <section class="wall-panel">
          <div class="panel-title"><strong>重点风险楼栋</strong><span>TOP {{ Math.min(overview.topRiskBuildings.length, 6) }}</span></div>
          <div class="rank-list">
            <button v-for="(row, index) in overview.topRiskBuildings.slice(0, 6)" :key="row.buildingId" type="button" class="rank-row" @click="showBuildingDetail(row)">
              <span class="rank-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="rank-name"><strong>{{ row.buildingName }}</strong><small>{{ row.communityName }}</small></span>
              <span class="rank-score">{{ row.riskScore == null ? '—' : row.riskScore.toFixed(1) }}</span>
            </button>
          </div>
        </section>

        <section class="wall-panel">
          <div class="panel-title"><strong>待人工复核</strong><span>{{ overview.reviewRequiredBuildings.length }} 栋</span></div>
          <div class="review-list">
            <button v-for="row in overview.reviewRequiredBuildings.slice(0, 6)" :key="row.buildingId" type="button" class="review-row" @click="showBuildingDetail(row)">
              <span><strong>{{ row.buildingName }}</strong><small>{{ row.communityName }}</small></span>
              <b>{{ row.confidenceScore == null ? '待核实' : `${Math.round(row.confidenceScore * 100)}%` }}</b>
            </button>
          </div>
        </section>
      </div>

      <footer class="info-footer">数据更新时间 {{ generatedAt }}</footer>
    </template>

    <template v-else>
      <div class="map-overlay-column map-overlay-column--left">
        <div class="map-metric-strip" aria-label="城市建筑安全核心指标">
          <article v-for="item in summaryCards" :key="item.key" class="map-metric" :data-tone="item.tone"><span>{{ item.label }}</span><strong>{{ item.value.toLocaleString('zh-CN') }}<small>{{ item.suffix }}</small></strong></article>
        </div>
        <aside class="map-overlay map-overlay--compact">
          <div class="panel-title"><strong>风险等级分布</strong><span>楼栋结构</span></div>
          <div class="bar-list compact-bars">
            <div v-for="row in overview.riskDistribution" :key="row.code" class="bar-row map-bar-row"><span class="bar-label"><i :data-tone="tone(row.code)" />{{ row.label }}</span><div class="bar-track"><span class="bar-fill" :data-tone="tone(row.code)" :style="{ width: barWidth(overview.riskDistribution, row.count) }" /></div><strong>{{ row.count }}</strong></div>
          </div>
        </aside>
        <aside class="map-overlay map-overlay--compact">
          <div class="panel-title"><strong>资料完整度</strong><span>风险判断支撑</span></div>
          <div class="bar-list compact-bars">
            <div v-for="row in overview.completenessDistribution.slice(0, 5)" :key="row.code" class="bar-row map-bar-row"><span class="bar-label"><i :data-tone="tone(row.code)" />{{ row.label }}</span><div class="bar-track"><span class="bar-fill" :data-tone="tone(row.code)" :style="{ width: barWidth(overview.completenessDistribution, row.count) }" /></div><strong>{{ row.count }}</strong></div>
          </div>
        </aside>
      </div>

      <div class="map-overlay-column map-overlay-column--right">
        <aside class="map-overlay">
          <div class="panel-title"><strong>重点风险楼栋</strong><span>TOP {{ Math.min(overview.topRiskBuildings.length, 5) }}</span></div>
          <div class="rank-list map-scroll-content">
            <button v-for="(row, index) in overview.topRiskBuildings.slice(0, 5)" :key="row.buildingId" type="button" class="rank-row" @click="showBuildingDetail(row)">
              <span class="rank-index">{{ String(index + 1).padStart(2, '0') }}</span><span class="rank-name"><strong>{{ row.buildingName }}</strong><small>{{ row.communityName }}</small></span><span class="rank-score">{{ row.riskScore == null ? '—' : row.riskScore.toFixed(1) }}</span>
            </button>
          </div>
        </aside>
        <aside class="map-overlay">
          <div class="panel-title"><strong>待人工复核</strong><span>{{ overview.reviewRequiredBuildings.length }} 栋</span></div>
          <div class="review-list map-scroll-content">
            <button v-for="row in overview.reviewRequiredBuildings.slice(0, 5)" :key="row.buildingId" type="button" class="review-row" @click="showBuildingDetail(row)">
              <span><strong>{{ row.buildingName }}</strong><small>{{ row.communityName }}</small></span><b>{{ row.confidenceScore == null ? '待核实' : `${Math.round(row.confidenceScore * 100)}%` }}</b>
            </button>
          </div>
        </aside>
      </div>
      <div class="map-footer-status">数据更新时间 {{ generatedAt }}</div>
    </template>

    <div v-if="selectedWallBuilding" class="wall-detail-overlay" aria-label="楼栋详情遮罩" @click.self="closeBuildingDetail">
      <article class="wall-detail-card">
        <button type="button" class="wall-detail-close" aria-label="关闭详情遮罩" @click="closeBuildingDetail">×</button>
        <span class="wall-detail-kicker">数据大屏 · 楼栋详情</span>
        <div class="wall-detail-heading">
          <div><strong>{{ selectedWallBuilding.buildingName }}</strong><small>{{ selectedWallBuilding.communityName }} · {{ selectedWallBuilding.buildingCode }}</small></div>
          <span :data-tone="tone(selectedWallBuilding.riskLevel ?? '')">{{ riskLabel(selectedWallBuilding.riskLevel) }}</span>
        </div>
        <div class="wall-detail-grid">
          <div><small>风险分值</small><b>{{ selectedWallBuilding.riskScore == null ? '—' : selectedWallBuilding.riskScore.toFixed(1) }}</b></div>
          <div><small>更新优先级</small><b>{{ selectedWallBuilding.priorityLevel || '—' }}</b></div>
          <div><small>置信度</small><b>{{ selectedWallBuilding.confidenceScore == null ? '—' : `${Math.round(selectedWallBuilding.confidenceScore * 100)}%` }}</b></div>
          <div><small>资料完整度</small><b>{{ selectedWallBuilding.completenessScore == null ? '—' : selectedWallBuilding.completenessScore.toFixed(1) }}</b></div>
          <div><small>结果状态</small><b>{{ freshnessLabel(selectedWallBuilding.freshness) }}</b></div>
          <div><small>治理排序</small><b>{{ selectedWallBuilding.ranking == null ? '—' : `第 ${selectedWallBuilding.ranking} 位` }}</b></div>
        </div>
        <p>点击侧栏楼栋仅在当前数据大屏查看详情，不会离开演示页面。需要进入业务页面时，请使用下方明确跳转按钮。</p>
        <div class="wall-detail-actions">
          <button type="button" @click="closeBuildingDetail">继续查看大屏</button>
          <button type="button" class="detail-navigation" @click="openSelectedRiskPage">前往更新优先级 →</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
.data-wall { --wall-border:rgba(109,222,202,.18); --wall-text:#eefcf9; --wall-muted:#8fb9b2; --map-left-width:clamp(180px,17vw,228px); --map-right-width:clamp(205px,19vw,270px); --map-header-safe:142px; --map-footer-safe:54px; position:relative; display:grid; min-height:720px; gap:12px; padding:16px 18px; overflow:hidden; border-radius:22px; background:radial-gradient(circle at 50% 42%,rgba(24,129,105,.18),transparent 34%),linear-gradient(145deg,#071918,#09231f 52%,#061513); color:var(--wall-text); box-shadow:0 24px 60px rgba(5,24,20,.22); }
.data-wall.is-fullscreen { width:100vw; height:100vh; min-height:100vh; padding:16px 20px; border-radius:0; }.data-wall.is-map { display:block; background:#06191c; }.map-backdrop { z-index:1; }
.wall-header { position:relative; z-index:25; display:grid; min-height:62px; grid-template-columns:1fr minmax(360px,auto) 1fr; align-items:start; gap:12px; }.wall-header-side { display:flex; align-items:center; gap:7px; padding-top:5px; }.wall-header-side--right { justify-content:flex-end; }.wall-title { display:grid; justify-items:center; gap:2px; text-align:center; }.wall-title strong { color:#f3fffc; font-size:clamp(19px,1.65vw,28px); font-weight:900; letter-spacing:.075em; text-shadow:0 3px 18px rgba(0,0,0,.35); }.wall-title span { color:#8fb9b2; font-size:10px; letter-spacing:.06em; }
.wall-view-switch { display:inline-flex; padding:2px; border:1px solid rgba(109,222,202,.16); border-radius:999px; background:rgba(5,32,34,.56); backdrop-filter:blur(14px); }.wall-view-switch button { min-width:44px; height:22px; padding:0 7px; border:0; border-radius:999px; background:transparent; color:#8fb9b2; font-size:8px; font-weight:800; }.wall-view-switch button + button { min-width:64px; }.wall-view-switch button.active { background:rgba(61,183,159,.24); color:#e5fffa; box-shadow:inset 0 0 0 1px rgba(119,232,212,.12); }.wall-header :deep(.el-button) { height:28px; padding:0 10px; border-color:rgba(109,222,202,.18); background:rgba(5,32,34,.54); color:#e9faf6; font-size:10px; backdrop-filter:blur(14px); }.wall-header :deep(.el-button--primary) { background:rgba(38,143,124,.78); border-color:rgba(85,207,184,.4); }
.wall-state { position:relative; z-index:30; display:grid; min-height:560px; place-content:center; gap:6px; color:var(--wall-muted); text-align:center; }.wall-state strong { color:var(--wall-text); font-size:18px; }.wall-state--error strong { color:#ff8d92; }
.wall-metrics { position:relative; z-index:10; display:grid; grid-template-columns:repeat(6,minmax(0,1fr)); gap:8px; }.wall-metric { display:grid; min-height:76px; align-content:center; gap:3px; padding:9px 12px; border:1px solid var(--wall-border); border-radius:14px; background:linear-gradient(145deg,rgba(12,48,43,.96),rgba(9,32,29,.9)); }.wall-metric > span { color:var(--wall-muted); font-size:10px; }.wall-metric > div { display:flex; align-items:baseline; gap:4px; }.wall-metric strong { font-size:clamp(21px,1.7vw,30px); line-height:1; }.wall-metric small { color:var(--wall-muted); }
.wall-metric[data-tone='danger'] strong,.map-metric[data-tone='danger'] strong { color:#ff7c82; }.wall-metric[data-tone='warning'] strong,.map-metric[data-tone='warning'] strong { color:#ffc06d; }.wall-metric[data-tone='success'] strong,.map-metric[data-tone='success'] strong { color:#70e2b2; }.wall-metric[data-tone='review'] strong,.map-metric[data-tone='review'] strong { color:#a8b9ff; }.wall-metric[data-tone='info'] strong,.map-metric[data-tone='info'] strong { color:#74c9ef; }
.info-grid { position:relative; z-index:10; display:grid; grid-template-columns:minmax(240px,.82fr) minmax(390px,1.42fr) minmax(250px,.9fr); grid-template-rows:auto minmax(225px,1fr); gap:10px; min-height:520px; align-items:start; }.wall-panel { min-width:0; overflow:hidden; padding:12px 14px; border:1px solid var(--wall-border); border-radius:16px; background:rgba(11,42,38,.88); }.compact-info-panel { align-self:start; }.core-panel { display:grid; min-height:100%; grid-template-rows:auto 1fr auto; background:radial-gradient(circle at center,rgba(33,123,102,.2),rgba(9,34,30,.9) 64%); }
.panel-title { display:flex; align-items:baseline; justify-content:space-between; gap:8px; margin-bottom:10px; }.panel-title strong { color:#effcf9; font-size:12px; }.panel-title span { color:var(--wall-muted); font-size:9px; }.bar-list { display:grid; gap:10px; }.compact-bars { gap:7px; }.bar-row { display:grid; grid-template-columns:86px minmax(36px,1fr) 30px; align-items:center; gap:7px; font-size:9px; }.map-bar-row { grid-template-columns:72px minmax(24px,1fr) 25px; gap:6px; }.bar-label { display:flex; align-items:center; gap:5px; color:#d7ebe6; }.bar-label i { width:6px; height:6px; flex:0 0 6px; border-radius:999px; background:#789e96; }.bar-track { height:6px; overflow:hidden; border-radius:999px; background:rgba(142,186,176,.12); }.bar-fill { display:block; height:100%; border-radius:inherit; background:#789e96; }.bar-label i[data-tone='danger'],.bar-fill[data-tone='danger'] { background:#ff5d66; }.bar-label i[data-tone='warning'],.bar-fill[data-tone='warning'] { background:#ffb24c; }.bar-label i[data-tone='success'],.bar-fill[data-tone='success'] { background:#50d890; }.bar-label i[data-tone='info'],.bar-fill[data-tone='info'] { background:#55afe0; }.bar-row > strong { text-align:right; font-variant-numeric:tabular-nums; }
.core-orbit { position:relative; min-height:195px; }.core-ring { position:absolute; top:50%; left:50%; border:1px solid rgba(92,198,174,.18); border-radius:50%; transform:translate(-50%,-50%); }.core-ring--outer { width:220px; height:220px; }.core-ring--inner { width:145px; height:145px; border-color:rgba(92,198,174,.3); }.core-value { position:absolute; top:50%; left:50%; display:grid; width:112px; height:112px; place-items:center; align-content:center; gap:2px; border:1px solid rgba(102,224,197,.36); border-radius:50%; background:rgba(17,78,65,.78); transform:translate(-50%,-50%); }.core-value span,.core-value small,.orbit-chip span { color:var(--wall-muted); font-size:9px; }.core-value strong { color:#ff8b8f; font-size:32px; }.orbit-chip { position:absolute; display:grid; min-width:70px; gap:1px; padding:6px 8px; border:1px solid rgba(102,224,197,.16); border-radius:11px; background:rgba(7,26,22,.78); }.orbit-chip strong { font-size:16px; }.orbit-chip--one { top:10px; left:12%; }.orbit-chip--two { top:14px; right:10%; }.orbit-chip--three { bottom:8px; left:9%; }.orbit-chip--four { right:11%; bottom:6px; }.map-entry { display:flex; width:100%; align-items:center; justify-content:space-between; padding:9px 11px; border:1px solid rgba(102,224,197,.2); border-radius:11px; background:rgba(40,122,106,.13); color:#baf8e9; }
.rank-list,.review-list { display:grid; gap:6px; }.rank-row { display:grid; grid-template-columns:26px minmax(0,1fr) 40px; align-items:center; gap:7px; padding:6px 7px; border:0; border-radius:9px; background:rgba(255,255,255,.025); color:inherit; text-align:left; cursor:pointer; }.rank-row:hover,.review-row:hover { background:rgba(71,164,142,.11); }.rank-index { color:#61d6bb; font-size:9px; font-weight:900; }.rank-name,.review-row > span { display:grid; min-width:0; gap:1px; }.rank-name strong,.rank-name small,.review-row strong,.review-row small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.rank-name small,.review-row small { color:var(--wall-muted); font-size:8px; }.rank-score { color:#ff8b8f; text-align:right; font-size:10px; font-weight:900; }.review-row { display:flex; width:100%; align-items:center; justify-content:space-between; gap:8px; padding:7px 8px; border:0; border-radius:9px; background:rgba(255,255,255,.025); color:inherit; text-align:left; cursor:pointer; }.review-row b { color:#aabaff; font-size:9px; }.empty-copy { margin:10px 0 0; color:var(--wall-muted); font-size:9px; text-align:center; }.info-footer { position:relative; z-index:10; color:var(--wall-muted); font-size:9px; text-align:right; }
.map-metric-strip { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:5px; pointer-events:auto; }.map-metric { display:grid; min-width:0; min-height:43px; align-content:center; gap:1px; padding:5px 7px; border:1px solid rgba(111,224,204,.14); border-radius:10px; background:rgba(4,31,34,.62); box-shadow:0 8px 24px rgba(0,0,0,.11); backdrop-filter:blur(15px) saturate(125%); }.map-metric[data-tone='danger'],.map-metric[data-tone='warning'] { border-color:rgba(255,181,111,.2); background:linear-gradient(145deg,rgba(42,39,31,.66),rgba(4,31,34,.62)); }.map-metric span { overflow:hidden; color:#8db7b0; font-size:7px; text-overflow:ellipsis; white-space:nowrap; }.map-metric strong { overflow:hidden; color:#f0fffc; font-size:clamp(12px,1.05vw,16px); line-height:1.05; text-overflow:ellipsis; white-space:nowrap; }.map-metric[data-tone='danger'] strong,.map-metric[data-tone='warning'] strong { font-size:clamp(13px,1.12vw,17px); }.map-metric small { margin-left:2px; color:#86aaa4; font-size:7px; font-weight:600; }
.map-overlay-column { position:absolute; z-index:18; top:var(--map-header-safe); bottom:var(--map-footer-safe); min-height:0; pointer-events:none; }.map-overlay-column--left { top:72px; bottom:var(--map-footer-safe); left:16px; display:grid; width:var(--map-left-width); height:auto; grid-template-rows:auto auto auto; gap:8px; align-content:space-between; }.map-overlay-column--right { right:16px; display:grid; width:var(--map-right-width); grid-template-rows:minmax(0,1.08fr) minmax(0,.92fr); gap:10px; }.map-overlay { display:flex; min-height:0; flex-direction:column; overflow:hidden; padding:11px 12px; border:1px solid rgba(112,225,205,.17); border-radius:15px; background:linear-gradient(145deg,rgba(5,34,37,.72),rgba(5,27,31,.60)); box-shadow:0 16px 40px rgba(0,0,0,.14); backdrop-filter:blur(17px) saturate(130%); pointer-events:auto; }.map-overlay--compact { padding:9px 10px 10px; }.map-overlay--compact .panel-title { margin-bottom:7px; }.map-overlay--compact .compact-bars { gap:6px; }.map-overlay--compact .map-bar-row { min-height:15px; }.map-overlay .panel-title { flex:0 0 auto; margin-bottom:9px; }.map-scroll-content { min-height:0; overflow:auto; overscroll-behavior:contain; scrollbar-width:thin; scrollbar-color:rgba(109,222,202,.22) transparent; }.map-footer-status { position:absolute; z-index:18; bottom:16px; left:50%; padding:4px 9px; border:1px solid rgba(111,224,204,.13); border-radius:999px; background:rgba(4,30,33,.52); color:#8db7b0; font-size:8px; transform:translateX(-50%); backdrop-filter:blur(12px); }
.wall-detail-overlay { position:absolute; inset:0; z-index:70; display:grid; place-items:center; padding:24px; background:rgba(2,13,15,.48); backdrop-filter:blur(5px); }.wall-detail-card { position:relative; width:min(520px,calc(100% - 28px)); padding:22px; border:1px solid rgba(113,231,210,.28); border-radius:20px; background:linear-gradient(145deg,rgba(7,43,43,.98),rgba(5,28,32,.97)); box-shadow:0 28px 80px rgba(0,0,0,.44); }.wall-detail-close { position:absolute; top:12px; right:12px; width:30px; height:30px; border:1px solid rgba(255,255,255,.12); border-radius:50%; background:rgba(255,255,255,.06); color:#eafffb; font-size:18px; cursor:pointer; }.wall-detail-kicker { color:#7ce2cb; font-size:10px; font-weight:900; letter-spacing:.08em; }.wall-detail-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-top:8px; }.wall-detail-heading > div { display:grid; gap:3px; min-width:0; }.wall-detail-heading strong { font-size:22px; }.wall-detail-heading small { color:var(--wall-muted); }.wall-detail-heading > span { flex:0 0 auto; padding:5px 9px; border-radius:999px; background:rgba(255,255,255,.08); font-size:10px; font-weight:900; }.wall-detail-heading > span[data-tone='danger'] { color:#ffd2d5; background:rgba(255,93,102,.16); }.wall-detail-heading > span[data-tone='warning'] { color:#ffe0aa; background:rgba(255,178,76,.15); }.wall-detail-heading > span[data-tone='success'] { color:#caffdf; background:rgba(80,216,144,.14); }.wall-detail-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:9px; margin-top:18px; }.wall-detail-grid > div { display:grid; gap:4px; padding:10px; border-radius:12px; background:rgba(255,255,255,.045); }.wall-detail-grid small { color:var(--wall-muted); font-size:9px; }.wall-detail-grid b { color:#f3fffc; font-size:14px; }.wall-detail-card p { margin:16px 0 0; color:#9fc4bd; font-size:10px; line-height:1.7; }.wall-detail-actions { display:flex; justify-content:flex-end; gap:8px; margin-top:18px; }.wall-detail-actions button { min-height:34px; padding:0 14px; border:1px solid rgba(109,222,202,.18); border-radius:10px; background:rgba(255,255,255,.055); color:#dff8f2; font-size:10px; font-weight:800; cursor:pointer; }.wall-detail-actions .detail-navigation { border-color:rgba(93,226,196,.34); background:rgba(45,151,130,.34); color:#effffb; }
@media (max-width:1180px) { .data-wall { --map-left-width:clamp(172px,19vw,204px); --map-right-width:clamp(190px,20vw,225px); }.wall-header { grid-template-columns:1fr auto 1fr; }.wall-title strong { font-size:18px; }.wall-metrics { grid-template-columns:repeat(3,minmax(0,1fr)); }.info-grid { grid-template-columns:1fr 1fr; grid-template-rows:auto; }.core-panel { grid-column:span 2; } }
@media (max-width:820px) { .data-wall { --map-left-width:min(176px,calc(50vw - 22px)); --map-right-width:min(190px,calc(50vw - 22px)); --map-header-safe:230px; min-height:760px; padding:12px; }.wall-header { grid-template-columns:1fr; justify-items:center; gap:5px; }.wall-header-side--left { order:2; padding-top:0; }.wall-title { order:1; }.wall-header-side--right { order:3; justify-content:center; flex-wrap:wrap; padding-top:0; }.wall-metrics { grid-template-columns:repeat(2,minmax(0,1fr)); }.info-grid { grid-template-columns:1fr; }.core-panel { grid-column:auto; }.map-overlay-column { top:235px; bottom:58px; }.map-overlay-column--left { top:118px; left:14px; }.map-overlay-column--right { right:14px; }.map-metric { min-height:39px; padding:4px 6px; }.wall-detail-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>