<script setup lang="ts">
import AppFilterField from './AppFilterField.vue'
import SpatialObjectSelector from './SpatialObjectSelector.vue'
import type { SpatialObjectSelection } from '@/shared/composables/useSpatialObjectSelector'

type SpatialSelectorMode = 'community' | 'building' | 'both'

withDefaults(defineProps<{
  mode?: SpatialSelectorMode
  communityId?: string
  buildingId?: string
  disabled?: boolean
  clearable?: boolean
  showGlobalSearch?: boolean
  communityStatus?: string
  width?: string
}>(), {
  mode: 'both',
  communityId: '',
  buildingId: '',
  disabled: false,
  clearable: true,
  showGlobalSearch: true,
  communityStatus: 'ACTIVE',
  width: '',
})

const emit = defineEmits<{
  'update:communityId': [value: string]
  'update:buildingId': [value: string]
  change: [selection: SpatialObjectSelection]
}>()
</script>

<template>
  <AppFilterField kind="spatial" :width="width">
    <SpatialObjectSelector
      :mode="mode"
      :community-id="communityId"
      :building-id="buildingId"
      :disabled="disabled"
      :clearable="clearable"
      :show-global-search="showGlobalSearch"
      :community-status="communityStatus"
      @update:community-id="emit('update:communityId', $event)"
      @update:building-id="emit('update:buildingId', $event)"
      @change="emit('change', $event)"
    />
  </AppFilterField>
</template>
