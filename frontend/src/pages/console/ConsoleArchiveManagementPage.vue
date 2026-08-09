<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CreateBuildingDrawer from '@/features/archive/CreateBuildingDrawer.vue'
import CreateCommunityDrawer from '@/features/archive/CreateCommunityDrawer.vue'
import {
  listBuildings,
  listCommunities,
  toAppError,
  type BuildingListRow,
  type CommunityListRow,
} from '@/shared/api'
import AppEmpty from '@/shared/components/AppEmpty.vue'
import AppError from '@/shared/components/AppError.vue'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppPageHeader from '@/shared/components/layout/AppPageHeader.vue'
import { useAuthStore } from '@/stores/auth'

type SpatialEntityType = 'COMMUNITY' | 'BUILDING'

const router = useRouter()
const auth = useAuthStore()
const communities = ref<CommunityListRow[]>([])
const buildings = ref<BuildingListRow[]>([])
const selectedCommunityId = ref('')
const selectedBuildingId = ref('')
const loading = ref(false)
const buildingLoading = ref(false)
const errorMessage = ref('')
const notice = ref('')
const createCommunityVisible = ref(false)
const createBuildingVisible = ref(false)

const selectedCommunity = computed(
  () => communities.value.find((item) => item.id === selectedCommunityId.value) ?? null,
)
const selectedBuilding = computed(
  () => buildings.value.find((item) => item.id === selectedBuildingId.value) ?? null,
)
const canCreateCommunity = computed(() => auth.hasAnyRole(['ADMIN', 'GOVERNMENT_MANAGER']))
const canCreateBuilding = computed(() => auth.hasAnyRole(['ADMIN', 'GOVERNMENT_MANAGER', 'COMMUNITY_MANAGER']))

onMounted(loadWorkspace)

async function loadWorkspace(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    await loadCommunities()
  } catch (error) {
    errorMessage.value = toAppError(error).message
  } finally {
    loading.value = false
  }
}

async function loadCommunities(preferredCommunityId?: string): Promise<void> {
  const page = await listCommunities({ size: 100, sort: 'communityName,asc' })
  communities.value = page.content ?? []
  const preferred = preferredCommunityId
    ?? (communities.value.some((item) => item.id === selectedCommunityId.value)
      ? selectedCommunityId.value
      : '')
  selectedCommunityId.value = communities.value.some((item) => item.id === preferred)
    ? preferred
    : communities.value[0]?.id ?? ''
  await loadBuildings()
}

async function loadBuildings(preferredBuildingId?: string): Promise<void> {
  buildings.value = []
  selectedBuildingId.value = ''
  if (!selectedCommunityId.value) return
  buildingLoading.value = true
  try {
    const page = await listBuildings({
      communityId: selectedCommunityId.value,
      size: 100,
      sort: 'buildingCode,asc',
    })
    buildings.value = page.content ?? []
    selectedBuildingId.value = buildings.value.some((item) => item.id === preferredBuildingId)
      ? preferredBuildingId ?? ''
      : buildings.value[0]?.id ?? ''
  } catch (error) {
    notice.value = toAppError(error).message
  } finally {
    buildingLoading.value = false
  }
}

async function selectCommunity(row: CommunityListRow): Promise<void> {
  if (selectedCommunityId.value === row.id) return
  selectedCommunityId.value = row.id
  notice.value = ''
  await loadBuildings()
}

function selectBuilding(row: BuildingListRow): void {
  selectedBuildingId.value = row.id
}

function openCommunityDrawer(): void {
  if (!canCreateCommunity.value) {
    ElMessage.warning('当前角色可维护授权小区内的楼栋，但无权新建小区。')
    return
  }
  createCommunityVisible.value = true
}

function openBuildingDrawer(): void {
  if (!canCreateBuilding.value) {
    ElMessage.warning('当前角色无权新增楼栋。')
    return
  }
  if (!selectedCommunityId.value) {
    ElMessage.warning('请先选择所属小区。')
    return
  }
  createBuildingVisible.value = true
}

async function handleCommunityCreated(communityId: string): Promise<void> {
  await loadCommunities(communityId)
}

async function handleBuildingCreated(buildingId: string): Promise<void> {
  await loadBuildings(buildingId)
}

function goToSpatialArchive(entityType: SpatialEntityType, entityId: string): void {
  void router.push({
    name: 'console-spatial-archive',
    query: {
      entityType,
      entityId,
      communityId: selectedCommunityId.value,
    },
  })
}
</script>

<template>
  <section class="archive-management-page">
    <AppPageHeader
      eyebrow="Archive Management"
      title="小区与楼栋管理"
      description="建立业务档案后，可继续维护中心点和空间边界；地图候选只做辅助，最终数据始终由人工确认。"
    >
      <template #actions>
        <el-button @click="loadWorkspace">刷新</el-button>
        <el-button :disabled="!canCreateCommunity" @click="openCommunityDrawer">新增小区</el-button>
        <el-button type="primary" :disabled="!selectedCommunityId || !canCreateBuilding" @click="openBuildingDrawer">新增楼栋</el-button>
      </template>
    </AppPageHeader>

    <AppLoading :visible="loading" inline text="加载档案目录中…" />
    <AppError v-if="errorMessage" :message="errorMessage" @retry="loadWorkspace" />

    <template v-if="!loading && !errorMessage">
      <el-alert v-if="notice" :title="notice" type="info" :closable="false" show-icon />
      <div class="directory-grid">
        <el-card shadow="never" class="directory-card">
          <template #header>
            <div class="card-head">
              <div><strong>小区目录</strong><span>{{ communities.length }} 个</span></div>
              <el-button
                v-if="selectedCommunity"
                link
                type="primary"
                @click="goToSpatialArchive('COMMUNITY', selectedCommunity.id)"
              >进入空间档案</el-button>
            </div>
          </template>
          <el-table
            v-if="communities.length"
            :data="communities"
            stripe
            highlight-current-row
            @row-click="selectCommunity"
          >
            <el-table-column prop="communityCode" label="编码" width="160" />
            <el-table-column prop="communityName" label="小区" min-width="150" />
            <el-table-column prop="administrativeRegion" label="行政区域" min-width="160" />
            <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
          </el-table>
          <AppEmpty v-else description="当前范围暂无小区档案" />
        </el-card>

        <el-card shadow="never" class="directory-card">
          <template #header>
            <div class="card-head">
              <div>
                <strong>楼栋目录</strong>
                <span>{{ selectedCommunity?.communityName || '未选择小区' }} · {{ buildings.length }} 栋</span>
              </div>
              <el-button
                v-if="selectedBuilding"
                link
                type="primary"
                @click="goToSpatialArchive('BUILDING', selectedBuilding.id)"
              >进入空间档案</el-button>
            </div>
          </template>
          <AppLoading :visible="buildingLoading" inline text="加载楼栋中…" />
          <el-table
            v-if="!buildingLoading && buildings.length"
            :data="buildings"
            stripe
            highlight-current-row
            @row-click="selectBuilding"
          >
            <el-table-column prop="buildingCode" label="编码" width="160" />
            <el-table-column prop="buildingName" label="楼栋" min-width="150" />
            <el-table-column prop="address" label="地址" min-width="210" show-overflow-tooltip />
            <el-table-column prop="constructionYear" label="建成年份" width="110" />
          </el-table>
          <AppEmpty
            v-else-if="!buildingLoading"
            :description="selectedCommunityId ? '当前小区暂无楼栋，可直接新增' : '请先选择小区'"
          />
        </el-card>
      </div>
    </template>

    <CreateCommunityDrawer
      v-model="createCommunityVisible"
      :existing-communities="communities"
      @created="handleCommunityCreated"
    />
    <CreateBuildingDrawer
      v-model="createBuildingVisible"
      :community-id="selectedCommunityId"
      :community-name="selectedCommunity?.communityName || ''"
      :community-region="selectedCommunity?.administrativeRegion || ''"
      :existing-buildings="buildings"
      @created="handleBuildingCreated"
    />
  </section>
</template>

<style scoped lang="scss">
.archive-management-page { display: grid; gap: var(--usp-space-5); }
.directory-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--usp-space-4); align-items: start; }
.directory-card { min-width: 0; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: var(--usp-space-3); }
.card-head > div { display: flex; align-items: baseline; gap: var(--usp-space-2); min-width: 0; }
.card-head span { color: var(--usp-text-secondary); font-size: 12px; }
@media (max-width: 980px) { .directory-grid { grid-template-columns: 1fr; } }
</style>
