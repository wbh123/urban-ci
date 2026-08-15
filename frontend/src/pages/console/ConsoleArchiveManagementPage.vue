<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listCommunities, type CommunityListRow } from '@/shared/api/endpoints/communities'
import { listBuildings, type BuildingListRow } from '@/shared/api/endpoints/buildings'
import { toAppError } from '@/shared/api/error'
import { buildArchiveHints } from '@/shared/ai/archive-hints'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppFilterBar from '@/shared/components/AppFilterBar.vue'
import AppFilterField from '@/shared/components/AppFilterField.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppQueryField from '@/shared/components/AppQueryField.vue'
import AppTablePager from '@/shared/components/AppTablePager.vue'
import AiInsightCard from '@/shared/components/ai/AiInsightCard.vue'
import CreateBuildingDrawer from '@/features/archive/CreateBuildingDrawer.vue'
import CreateCommunityDrawer from '@/features/archive/CreateCommunityDrawer.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'

const router = useRouter()
const communities = ref<CommunityListRow[]>([])
const buildings = ref<BuildingListRow[]>([])
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const selectedCommunity = ref<CommunityListRow | null>(null)
const selectedBuilding = ref<BuildingListRow | null>(null)
const loading = ref(false)
const buildingLoading = ref(false)
const errorMessage = ref('')
const notice = ref('')
const communityDrawerOpen = ref(false)
const buildingDrawerOpen = ref(false)
const communityKeyword = ref('')
const communityStatus = ref('')
const buildingKeyword = ref('')
const communityPage = ref(1)
const communityPageSize = ref(20)
const communityTotal = ref(0)
const buildingPage = ref(1)
const buildingPageSize = ref(20)
const buildingTotal = ref(0)

const selectedCommunityLabel = computed(() => selectedCommunity.value?.communityName || '未选择小区')
const selectedBuildingLabel = computed(() => {
  const building = selectedBuilding.value
  if (!building) return '未选择楼栋'
  return [building.buildingCode, building.buildingName].filter(Boolean).join(' · ') || '未命名楼栋'
})
const archiveHints = computed(() => buildArchiveHints({
  community: selectedCommunity.value,
  building: selectedBuilding.value,
}))
const archiveHintSummary = computed(() => archiveHints.value[0]?.detail ?? '当前暂无档案提示。')
const archiveHintSuggestion = computed(() => archiveHints.value.length > 1
  ? archiveHints.value.slice(1).map((item) => item.detail).join('；')
  : undefined)

onMounted(loadWorkspace)

async function loadWorkspace(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    await loadCommunities()
    if (selectedCommunityId.value) await loadBuildings()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function loadCommunities(preferredCommunityId?: string): Promise<void> {
  const page = await listCommunities({
    keyword: communityKeyword.value.trim() || undefined,
    status: communityStatus.value || undefined,
    page: communityPage.value - 1,
    size: communityPageSize.value,
    sort: 'communityName,asc',
  })
  communities.value = page.content ?? []
  communityTotal.value = Number(page.page?.totalElements ?? communities.value.length)

  if (preferredCommunityId) {
    const preferred = communities.value.find((item) => item.id === preferredCommunityId)
    if (preferred) {
      selectedCommunityId.value = preferred.id
      selectedCommunity.value = preferred
    }
  }
}

async function loadBuildings(preferredBuildingId?: string): Promise<void> {
  buildings.value = []
  buildingTotal.value = 0
  if (!selectedCommunityId.value) return
  buildingLoading.value = true
  notice.value = ''
  try {
    const page = await listBuildings({
      communityId: selectedCommunityId.value,
      keyword: buildingKeyword.value.trim() || undefined,
      page: buildingPage.value - 1,
      size: buildingPageSize.value,
      sort: 'buildingCode,asc',
    })
    buildings.value = page.content ?? []
    buildingTotal.value = Number(page.page?.totalElements ?? buildings.value.length)
    if (preferredBuildingId) {
      const preferred = buildings.value.find((item) => item.id === preferredBuildingId)
      if (preferred) {
        selectedBuildingId.value = preferred.id
        selectedBuilding.value = preferred
      }
    }
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    buildingLoading.value = false
  }
}

async function runCommunityQuery(): Promise<void> {
  communityPage.value = 1
  clearCommunitySelection()
  await loadCommunities()
}

async function resetCommunityQuery(): Promise<void> {
  communityKeyword.value = ''
  communityStatus.value = ''
  communityPage.value = 1
  clearCommunitySelection()
  await loadCommunities()
}

async function runBuildingQuery(): Promise<void> {
  buildingPage.value = 1
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  await loadBuildings()
}

async function resetBuildingQuery(): Promise<void> {
  buildingKeyword.value = ''
  buildingPage.value = 1
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  await loadBuildings()
}

function clearCommunitySelection(): void {
  selectedCommunityId.value = ''
  selectedCommunity.value = null
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  buildings.value = []
  buildingTotal.value = 0
  buildingPage.value = 1
  buildingKeyword.value = ''
}

async function selectCommunity(row: CommunityListRow): Promise<void> {
  if (selectedCommunityId.value === row.id) return
  selectedCommunityId.value = row.id
  selectedCommunity.value = row
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  buildingKeyword.value = ''
  buildingPage.value = 1
  notice.value = ''
  await loadBuildings()
}

function selectBuilding(row: BuildingListRow): void {
  selectedBuildingId.value = row.id
  selectedBuilding.value = row
}

async function handleCommunityPageChange(): Promise<void> {
  await loadCommunities()
}

async function handleBuildingPageChange(): Promise<void> {
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  await loadBuildings()
}

async function handleCommunityCreated(communityId: string): Promise<void> {
  communityKeyword.value = ''
  communityStatus.value = ''
  communityPage.value = 1
  clearCommunitySelection()
  await loadCommunities(communityId)
  if (selectedCommunityId.value) await loadBuildings()
}

async function handleBuildingCreated(buildingId: string): Promise<void> {
  buildingKeyword.value = ''
  buildingPage.value = 1
  selectedBuildingId.value = ''
  selectedBuilding.value = null
  await loadBuildings(buildingId)
}

function openSpatialArchive(entityType: 'COMMUNITY' | 'BUILDING'): void {
  const entityId = entityType === 'COMMUNITY' ? selectedCommunityId.value : selectedBuildingId.value
  if (!entityId) {
    notice.value = entityType === 'COMMUNITY' ? '请先选择小区。' : '请先选择楼栋。'
    return
  }
  void router.push({
    name: 'console-spatial-archive',
    query: {
      entityType,
      entityId,
      communityId: selectedCommunityId.value,
    },
  })
}

function communityStatusLabel(status?: string | null): string {
  if (status === 'ACTIVE') return '正常'
  if (status === 'INACTIVE') return '停用'
  return status || '未知'
}

function statusType(status?: string | null): 'success' | 'info' | 'warning' {
  if (status === 'ACTIVE') return 'success'
  if (status === 'INACTIVE') return 'info'
  return 'warning'
}
</script>

<template>
  <section class="archive-management-page">
    <AppPageHeader
      eyebrow="基础档案"
      title="小区与楼栋管理"
      description="先查询并选择小区，再维护该小区所属楼栋，减少目录并排造成的上下文混乱。"
      show-user-menu
    >
      <template #actions>
        <el-button @click="loadWorkspace">刷新目录</el-button>
        <el-button type="primary" @click="communityDrawerOpen = true">新增小区</el-button>
      </template>
    </AppPageHeader>

    <el-alert v-if="notice" :title="notice" type="warning" :closable="false" show-icon />
    <AppLoading :visible="loading" inline text="加载基础档案中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="loadWorkspace" />

    <template v-if="!loading && !errorMessage">
      <section class="archive-summary" aria-label="基础档案摘要">
        <div class="summary-item">
          <span>小区查询结果</span>
          <strong>{{ communityTotal }}</strong>
        </div>
        <div class="summary-item summary-item--wide">
          <span>当前小区</span>
          <strong>{{ selectedCommunityLabel }}</strong>
        </div>
        <div class="summary-item">
          <span>所属楼栋</span>
          <strong>{{ selectedCommunityId ? buildingTotal : '—' }}</strong>
        </div>
        <div class="summary-item summary-item--wide">
          <span>当前楼栋</span>
          <strong>{{ selectedBuildingLabel }}</strong>
        </div>
      </section>

      <div class="community-ai-row">
        <section class="community-ai-row__directory query-section">
          <div class="section-heading">
            <div>
              <strong>查询小区</strong>
              <small>按名称、编码或地址搜索，再从结果中选择需要维护的小区。</small>
            </div>
          </div>
          <AppFilterBar :loading="loading" @query="runCommunityQuery" @reset="resetCommunityQuery">
            <AppFilterField kind="keyword">
              <AppQueryField
                v-model="communityKeyword"
                placeholder="搜索小区名称、编码或地址"
                width="100%"
                @query="runCommunityQuery"
              />
            </AppFilterField>
            <AppFilterField kind="status">
              <el-select v-model="communityStatus" clearable placeholder="全部状态">
                <el-option label="正常" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
              </el-select>
            </AppFilterField>
          </AppFilterBar>

          <el-card shadow="never" class="directory-card community-directory-card">
            <el-table
              v-if="communities.length"
              :data="communities"
              highlight-current-row
              :current-row-key="selectedCommunityId"
              row-key="id"
              @row-click="selectCommunity"
            >
              <el-table-column prop="communityName" label="小区名称" min-width="180" />
              <el-table-column prop="administrativeRegion" label="行政区域" min-width="130" show-overflow-tooltip />
              <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="96">
                <template #default="scope">
                  <el-tag size="small" :type="statusType(scope.row.status)">{{ communityStatusLabel(scope.row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <AppEmpty v-else description="当前条件下没有小区档案" />
            <AppTablePager
              v-if="communityTotal > 0"
              v-model:page="communityPage"
              v-model:page-size="communityPageSize"
              :total="communityTotal"
              @change="handleCommunityPageChange"
            />
          </el-card>
        </section>

        <div class="community-ai-row__ai">
          <AiInsightCard
            title="AI 档案提示"
            :summary="archiveHintSummary"
            :suggestion="archiveHintSuggestion"
            compact
          >
            <p class="archive-hint-rule">提示基于档案完整性与治理规则生成，不修改正式档案数据。</p>
            <div class="archive-hint-tags">
              <el-tag
                v-for="hint in archiveHints"
                :key="`${hint.action}-${hint.title}`"
                :type="hint.level === 'ATTENTION' ? 'warning' : 'info'"
                effect="plain"
                round
              >
                {{ hint.title }}
              </el-tag>
            </div>
          </AiInsightCard>
        </div>
      </div>

      <section v-if="selectedCommunity" class="selected-community-context">
        <div class="context-copy">
          <span>当前小区</span>
          <strong>{{ selectedCommunity.communityName }}</strong>
          <small>{{ selectedCommunity.administrativeRegion || '行政区域未填写' }}</small>
        </div>
        <div class="context-actions">
          <el-button @click="openSpatialArchive('COMMUNITY')">小区空间档案</el-button>
          <el-button type="primary" @click="buildingDrawerOpen = true">新增楼栋</el-button>
        </div>
      </section>

      <section class="building-query-section query-section">
        <div class="section-heading">
          <div>
            <strong>查询所属楼栋</strong>
            <small>{{ selectedCommunity ? `仅查询 ${selectedCommunity.communityName} 下的楼栋` : '请先从上方选择一个小区' }}</small>
          </div>
          <el-button :disabled="!selectedBuildingId" @click="openSpatialArchive('BUILDING')">楼栋空间档案</el-button>
        </div>

        <AppFilterBar
          v-if="selectedCommunity"
          :loading="buildingLoading"
          @query="runBuildingQuery"
          @reset="resetBuildingQuery"
        >
          <AppFilterField kind="keyword">
            <AppQueryField
              v-model="buildingKeyword"
              placeholder="搜索楼栋名称或编号"
              width="100%"
              @query="runBuildingQuery"
            />
          </AppFilterField>
        </AppFilterBar>

        <el-card shadow="never" class="directory-card building-directory-card">
          <div v-loading="buildingLoading" class="building-table-wrap">
            <el-table
              v-if="buildings.length"
              :data="buildings"
              highlight-current-row
              :current-row-key="selectedBuildingId"
              row-key="id"
              @row-click="selectBuilding"
            >
              <el-table-column prop="buildingCode" label="楼栋编号" width="120" />
              <el-table-column prop="buildingName" label="楼栋名称" min-width="180" />
              <el-table-column prop="constructionYear" label="建成年份" width="110" />
              <el-table-column prop="floorCount" label="层数" width="90" />
              <el-table-column prop="residentCount" label="居民数" width="100" />
              <el-table-column label="状态" width="96">
                <template #default="scope">
                  <el-tag size="small" :type="statusType(scope.row.status)">{{ communityStatusLabel(scope.row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <AppEmpty v-else-if="selectedCommunityId && !buildingLoading" description="当前小区暂无匹配楼栋" />
            <AppEmpty v-else-if="!selectedCommunityId" description="请先查询并选择小区" />
          </div>
          <AppTablePager
            v-if="buildingTotal > 0"
            v-model:page="buildingPage"
            v-model:page-size="buildingPageSize"
            :total="buildingTotal"
            @change="handleBuildingPageChange"
          />
        </el-card>
      </section>
    </template>

    <CreateCommunityDrawer
      v-model="communityDrawerOpen"
      :existing-communities="communities"
      @created="handleCommunityCreated"
    />
    <CreateBuildingDrawer
      v-model="buildingDrawerOpen"
      :community-id="selectedCommunityId"
      :community-name="selectedCommunity?.communityName ?? ''"
      :community-region="selectedCommunity?.administrativeRegion ?? ''"
      :existing-buildings="buildings"
      @created="handleBuildingCreated"
    />
  </section>
</template>

<style scoped lang="scss">
.archive-management-page { display: grid; gap: 14px; }
.archive-summary {
  display: grid;
  grid-template-columns: minmax(130px, .7fr) minmax(200px, 1.35fr) minmax(130px, .7fr) minmax(200px, 1.35fr);
  gap: 10px;
}
.summary-item {
  display: grid;
  min-width: 0;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--usp-color-border);
  border-radius: var(--usp-radius-xl);
  background: var(--usp-color-surface);
  box-shadow: var(--usp-shadow-sm);
}
.summary-item span { color: var(--usp-color-text-secondary); font-size: 11px; font-weight: 700; }
.summary-item strong { overflow: hidden; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.summary-item--wide strong { color: var(--usp-color-primary-strong); font-size: 15px; }
.community-ai-row{display:grid;grid-template-columns:minmax(0,1.75fr) minmax(300px,.85fr);gap:14px;align-items:start}
.community-ai-row__directory,.community-ai-row__ai{min-width:0}
.community-ai-row__ai{position:sticky;top:12px}
.community-ai-row__ai :deep(.ai-insight-card){height:auto;align-content:start}
.community-directory-card { min-height: 330px; }
.archive-hint-rule { margin: 0; color: var(--usp-color-text-tertiary); font-size: 12px; }
.archive-hint-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.query-section { display: grid; gap: 10px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 0 2px; }
.section-heading > div { display: grid; gap: 3px; }
.section-heading strong { font-size: 16px; }
.section-heading small { color: var(--usp-color-text-secondary); }
.directory-card { border-radius: var(--usp-radius-xl); box-shadow: var(--usp-shadow-sm); }
.directory-card :deep(.el-card__body) { display: grid; gap: 12px; padding: 14px 16px; }
.selected-community-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--usp-color-primary) 22%, var(--usp-color-border));
  border-radius: var(--usp-radius-xl);
  background: color-mix(in srgb, var(--usp-color-primary) 5%, var(--usp-color-surface));
}
.context-copy { display: grid; min-width: 0; gap: 2px; }
.context-copy span,
.context-copy small { color: var(--usp-color-text-secondary); font-size: 12px; }
.context-copy strong { overflow: hidden; color: var(--usp-color-primary-strong); font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.context-actions { display: flex; flex: 0 0 auto; gap: 8px; }
.building-table-wrap { min-height: 220px; }
.archive-management-page :deep(.el-input__wrapper),
.archive-management-page :deep(.el-select__wrapper),
.archive-management-page :deep(.el-button) { border-radius: var(--usp-radius-lg); }
.archive-management-page :deep(.el-table) { border-radius: var(--usp-radius-lg); }

@media (max-width: 960px) {
  .community-ai-row { grid-template-columns: 1fr; }
  .community-ai-row__ai { position: static; }
}
@media (max-width: 900px) {
  .archive-summary { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 680px) {
  .archive-summary { grid-template-columns: 1fr; }
  .selected-community-context,
  .section-heading { align-items: stretch; flex-direction: column; }
  .context-actions { width: 100%; }
  .context-actions :deep(.el-button) { flex: 1; }
}
</style>
