<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    pageSizes?: number[]
  }>(),
  {
    pageSizes: () => [20, 50, 100],
  },
)

const emit = defineEmits<{
  'update:page': [value: number]
  'update:pageSize': [value: number]
  change: []
}>()

function handlePageChange(value: number): void {
  emit('update:page', value)
  emit('change')
}

function handleSizeChange(value: number): void {
  emit('update:pageSize', value)
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <div v-if="total > 0" class="table-pager">
    <el-pagination
      background
      :current-page="page"
      :page-size="pageSize"
      :page-sizes="props.pageSizes"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="handlePageChange"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<style scoped lang="scss">
.table-pager {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: flex-end;
  padding: 10px 2px 0;
  border-top: 1px solid var(--usp-color-border);
}
.table-pager :deep(.el-pagination) { flex-wrap: wrap; justify-content: flex-end; gap: 4px; }

@media (max-width: 720px) {
  .table-pager { justify-content: flex-start; overflow-x: auto; }
  .table-pager :deep(.el-pagination__jump) { display: none; }
}
</style>
