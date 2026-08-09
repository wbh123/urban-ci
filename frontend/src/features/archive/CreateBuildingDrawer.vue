<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ArchiveLocationPicker, { type ArchiveLocationSelection } from './ArchiveLocationPicker.vue'
import { suggestBuildingCode } from './archive-code'
import { findArchiveDuplicates, type ArchiveDuplicateReason } from './archive-duplicate'
import {
  createBuilding,
  saveArchiveBuildingLocation,
  toAppError,
  type BuildingListRow,
  type CreateBuildingRequest,
} from '@/shared/api'

const props = defineProps<{
  modelValue: boolean
  communityId: string
  communityName: string
  communityRegion?: string
  existingBuildings: BuildingListRow[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [buildingId: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const saving = ref(false)
const selection = ref<ArchiveLocationSelection | null>(null)
const form = reactive({
  buildingCode: '',
  buildingName: '',
  address: '',
  constructionYear: undefined as number | undefined,
  floorCount: undefined as number | undefined,
  structureType: '',
  hasElevator: false,
  hasIllegalModification: false,
  hasGroundFloorBusiness: false,
  remark: '',
})
const location = reactive({
  longitude: undefined as number | undefined,
  latitude: undefined as number | undefined,
  formattedAddress: '',
})

const duplicateMatches = computed(() => findArchiveDuplicates({
  code: form.buildingCode,
  name: form.buildingName,
}, props.existingBuildings.map((item) => ({
  id: item.id,
  code: item.buildingCode,
  name: item.buildingName,
}))))

const duplicateText = computed(() => duplicateMatches.value.map((match) => {
  const row = props.existingBuildings.find((item) => item.id === match.id)
  return `${row?.buildingName || row?.buildingCode || match.id}（${match.reasons.map(reasonLabel).join('、')}）`
}).join('；'))

watch(() => props.modelValue, (value) => {
  if (value) reset()
})

function reset(): void {
  Object.assign(form, {
    buildingCode: suggestBuildingCode(),
    buildingName: '',
    address: '',
    constructionYear: undefined,
    floorCount: undefined,
    structureType: '',
    hasElevator: false,
    hasIllegalModification: false,
    hasGroundFloorBusiness: false,
    remark: '',
  })
  Object.assign(location, { longitude: undefined, latitude: undefined, formattedAddress: '' })
  selection.value = null
}

function applyLocation(next: ArchiveLocationSelection): void {
  selection.value = next
  location.longitude = next.longitude
  location.latitude = next.latitude
  location.formattedAddress = next.formattedAddress
  if (!form.buildingName.trim() && next.name) form.buildingName = next.name
  if (!form.address.trim() && next.formattedAddress) form.address = next.formattedAddress
}

function markManualLocation(): void {
  selection.value = null
}

async function submit(): Promise<void> {
  if (!props.communityId) {
    ElMessage.warning('请先选择所属小区。')
    return
  }
  if (!form.buildingCode.trim()) {
    ElMessage.warning('请填写楼栋编码。')
    return
  }
  saving.value = true
  try {
    const payload: CreateBuildingRequest = {
      communityId: props.communityId,
      buildingCode: form.buildingCode.trim(),
      buildingName: optionalText(form.buildingName),
      address: optionalText(form.address),
      constructionYear: form.constructionYear,
      floorCount: form.floorCount,
      structureType: optionalText(form.structureType),
      hasElevator: form.hasElevator,
      hasIllegalModification: form.hasIllegalModification,
      hasGroundFloorBusiness: form.hasGroundFloorBusiness,
      status: 'ACTIVE',
      remark: optionalText(form.remark),
    }
    const created = await createBuilding(payload)
    let locationFailed = false
    if (hasLocation()) {
      try {
        await saveArchiveBuildingLocation(created.id, {
          longitude: location.longitude!,
          latitude: location.latitude!,
          formattedAddress: optionalText(location.formattedAddress),
          provider: selection.value?.provider ?? 'MANUAL',
          coordinateSystem: selection.value?.coordinateSystem ?? 'GCJ02',
          matchLevel: selection.value?.matchLevel ?? 'MANUAL_POINT',
          mock: selection.value?.mock ?? false,
          metadata: selection.value?.metadata ?? {},
        })
      } catch {
        locationFailed = true
      }
    }
    visible.value = false
    emit('created', created.id)
    if (locationFailed) ElMessage.warning('楼栋档案已创建，但地图位置保存失败，可稍后补录。')
    else ElMessage.success('楼栋档案已创建。')
  } catch (error) {
    ElMessage.error(toAppError(error).message)
  } finally {
    saving.value = false
  }
}

function hasLocation(): boolean {
  return Number.isFinite(location.longitude) && Number.isFinite(location.latitude)
}

function optionalText(value: string): string | undefined {
  const normalized = value.trim()
  return normalized || undefined
}

function reasonLabel(reason: ArchiveDuplicateReason): string {
  if (reason === 'CODE') return '编码相同'
  if (reason === 'NAME') return '名称相同'
  return '地址相同'
}
</script>

<template>
  <el-drawer v-model="visible" title="新增楼栋" size="min(860px, 96vw)" destroy-on-close>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="`所属小区：${communityName || communityId || '未选择'}。系统已生成可编辑的楼栋编码。`"
    />
    <el-form label-position="top" class="drawer-form">
      <el-divider content-position="left">地图辅助定位（可选）</el-divider>
      <ArchiveLocationPicker
        :keyword="form.buildingName || form.address"
        :region="communityRegion || ''"
        @select="applyLocation"
      />

      <el-divider content-position="left">楼栋档案</el-divider>
      <div class="form-grid">
        <el-form-item label="楼栋编码" required><el-input v-model="form.buildingCode" /></el-form-item>
        <el-form-item label="楼栋名称"><el-input v-model="form.buildingName" placeholder="例如 1 栋" /></el-form-item>
      </div>
      <el-form-item label="详细地址"><el-input v-model="form.address" /></el-form-item>
      <el-alert
        v-if="duplicateMatches.length"
        type="warning"
        :closable="false"
        show-icon
        title="发现疑似重复楼栋"
        :description="`${duplicateText}。请人工核对；该提示不阻断创建。`"
      />
      <div class="form-grid three">
        <el-form-item label="建成年份"><el-input-number v-model="form.constructionYear" :min="1800" :max="2200" controls-position="right" /></el-form-item>
        <el-form-item label="楼层数"><el-input-number v-model="form.floorCount" :min="1" :max="200" controls-position="right" /></el-form-item>
        <el-form-item label="结构类型"><el-input v-model="form.structureType" placeholder="可选" /></el-form-item>
      </div>
      <div class="boolean-grid">
        <el-checkbox v-model="form.hasElevator">有电梯</el-checkbox>
        <el-checkbox v-model="form.hasIllegalModification">存在违规改造</el-checkbox>
        <el-checkbox v-model="form.hasGroundFloorBusiness">底层经营</el-checkbox>
      </div>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>

      <el-divider content-position="left">中心点（可手工修正）</el-divider>
      <div class="form-grid">
        <el-form-item label="经度"><el-input-number v-model="location.longitude" :min="-180" :max="180" :precision="6" controls-position="right" @change="markManualLocation" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="location.latitude" :min="-90" :max="90" :precision="6" controls-position="right" @change="markManualLocation" /></el-form-item>
      </div>
      <el-form-item label="定位地址"><el-input v-model="location.formattedAddress" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!communityId" @click="submit">确认创建</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-form { margin-top: var(--usp-space-4); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 var(--usp-space-4); }
.form-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.form-grid :deep(.el-input-number) { width: 100%; }
.boolean-grid { display: flex; flex-wrap: wrap; gap: var(--usp-space-4); margin-bottom: var(--usp-space-4); }
@media (max-width: 720px) { .form-grid, .form-grid.three { grid-template-columns: 1fr; } }
</style>
