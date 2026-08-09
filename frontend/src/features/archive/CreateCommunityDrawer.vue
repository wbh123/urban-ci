<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ArchiveLocationPicker, { type ArchiveLocationSelection } from './ArchiveLocationPicker.vue'
import { suggestCommunityCode } from './archive-code'
import { findArchiveDuplicates, type ArchiveDuplicateReason } from './archive-duplicate'
import {
  createCommunity,
  saveCommunityLocation,
  toAppError,
  type CommunityListRow,
  type CreateCommunityRequest,
} from '@/shared/api'

const props = defineProps<{
  modelValue: boolean
  existingCommunities: CommunityListRow[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [communityId: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const saving = ref(false)
const selection = ref<ArchiveLocationSelection | null>(null)
const form = reactive({
  communityCode: '',
  communityName: '',
  administrativeRegion: '',
  address: '',
  remark: '',
})
const location = reactive({
  longitude: undefined as number | undefined,
  latitude: undefined as number | undefined,
  formattedAddress: '',
})

const duplicateMatches = computed(() => findArchiveDuplicates({
  code: form.communityCode,
  name: form.communityName,
  address: form.address,
}, props.existingCommunities.map((item) => ({
  id: item.id,
  code: item.communityCode,
  name: item.communityName,
  address: item.address,
}))))

const duplicateText = computed(() => duplicateMatches.value.map((match) => {
  const row = props.existingCommunities.find((item) => item.id === match.id)
  return `${row?.communityName || row?.communityCode || match.id}（${match.reasons.map(reasonLabel).join('、')}）`
}).join('；'))

watch(() => props.modelValue, (value) => {
  if (value) reset()
})

function reset(): void {
  Object.assign(form, {
    communityCode: suggestCommunityCode(),
    communityName: '',
    administrativeRegion: '',
    address: '',
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
  if (!form.communityName.trim() && next.name) form.communityName = next.name
  if (!form.address.trim() && next.formattedAddress) form.address = next.formattedAddress
}

function markManualLocation(): void {
  selection.value = null
}

async function submit(): Promise<void> {
  if (!form.communityCode.trim() || !form.communityName.trim()) {
    ElMessage.warning('请填写小区编码和小区名称。')
    return
  }
  saving.value = true
  try {
    const payload: CreateCommunityRequest = {
      communityCode: form.communityCode.trim(),
      communityName: form.communityName.trim(),
      administrativeRegion: optionalText(form.administrativeRegion),
      address: optionalText(form.address),
      status: 'ACTIVE',
      remark: optionalText(form.remark),
    }
    const created = await createCommunity(payload)
    let locationFailed = false
    if (hasLocation()) {
      try {
        await saveCommunityLocation(created.id, {
          longitude: location.longitude!,
          latitude: location.latitude!,
          formattedAddress: optionalText(location.formattedAddress),
          provider: selection.value?.provider ?? 'MANUAL',
          coordinateSystem: selection.value?.coordinateSystem ?? 'UNKNOWN',
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
    if (locationFailed) ElMessage.warning('小区档案已创建，但地图位置保存失败，可稍后补录。')
    else ElMessage.success('小区档案已创建。')
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
  <el-drawer v-model="visible" title="新增小区" size="min(820px, 96vw)" destroy-on-close>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="系统已生成可编辑的小区编码；地图只用于辅助预填，最终信息由人工确认。"
    />
    <el-form label-position="top" class="drawer-form">
      <el-divider content-position="left">地图辅助定位（可选）</el-divider>
      <ArchiveLocationPicker
        :keyword="form.communityName || form.address"
        :region="form.administrativeRegion"
        @select="applyLocation"
      />

      <el-divider content-position="left">小区档案</el-divider>
      <div class="form-grid">
        <el-form-item label="小区编码" required><el-input v-model="form.communityCode" /></el-form-item>
        <el-form-item label="小区名称" required><el-input v-model="form.communityName" /></el-form-item>
      </div>
      <el-form-item label="行政区域"><el-input v-model="form.administrativeRegion" /></el-form-item>
      <el-form-item label="详细地址"><el-input v-model="form.address" /></el-form-item>
      <el-alert
        v-if="duplicateMatches.length"
        type="warning"
        :closable="false"
        show-icon
        title="发现疑似重复小区"
        :description="`${duplicateText}。请人工核对；该提示不阻断创建。`"
      />
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
      <el-button type="primary" :loading="saving" @click="submit">确认创建</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.drawer-form { margin-top: var(--usp-space-4); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 var(--usp-space-4); }
.form-grid :deep(.el-input-number) { width: 100%; }
@media (max-width: 640px) { .form-grid { grid-template-columns: 1fr; } }
</style>
