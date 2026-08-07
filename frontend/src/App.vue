<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useAppStore } from '@/stores/app'
import AppLoading from '@/shared/components/AppLoading.vue'
import AppNotice from '@/shared/components/AppNotice.vue'

const appStore = useAppStore()
</script>

<template>
  <AppLoading :visible="appStore.globalLoading" />
  <AppNotice />
  <RouterView />
</template>
