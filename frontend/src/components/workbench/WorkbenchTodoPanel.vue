<script setup lang="ts">
interface TodoItem {
  key: string
  label: string
  description: string
  path: string
  priority: 'HIGH' | 'NORMAL'
}

defineProps<{
  items: TodoItem[]
}>()

const emit = defineEmits<{
  select: [item: TodoItem]
}>()
</script>

<template>
  <el-card class="todo-panel" shadow="never">
    <template #header>
      <div class="panel-header">
        <div>
          <strong>我的待办</strong>
          <span>按当前职责提供可直接进入的业务入口</span>
        </div>
        <el-tag effect="plain">{{ items.length }} 项</el-tag>
      </div>
    </template>

    <div class="todo-list">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="todo-item"
        @click="emit('select', item)"
      >
        <span class="todo-main">
          <span class="todo-title-row">
            <strong>{{ item.label }}</strong>
            <el-tag v-if="item.priority === 'HIGH'" size="small" type="warning">优先</el-tag>
          </span>
          <small>{{ item.description }}</small>
        </span>
        <span class="todo-arrow">→</span>
      </button>
      <el-empty v-if="items.length === 0" description="当前没有需要处理的工作入口" />
    </div>
  </el-card>
</template>

<style scoped lang="scss">
.todo-panel {
  border-radius: var(--usp-radius-lg);
}

.panel-header,
.todo-title-row,
.todo-item {
  display: flex;
  align-items: center;
}

.panel-header {
  justify-content: space-between;
  gap: var(--usp-space-3);
}

.panel-header > div {
  display: grid;
  gap: 4px;
}

.panel-header span,
.todo-item small {
  color: var(--usp-color-text-secondary);
}

.todo-list {
  display: grid;
  gap: var(--usp-space-2);
}

.todo-item {
  width: 100%;
  justify-content: space-between;
  gap: var(--usp-space-4);
  padding: var(--usp-space-3);
  border: 1px solid transparent;
  border-radius: var(--usp-radius-md);
  background: transparent;
  color: var(--usp-color-text-primary);
  text-align: left;
  cursor: pointer;
}

.todo-item:hover {
  border-color: var(--usp-color-border);
  background: var(--usp-color-primary-soft);
}

.todo-main {
  display: grid;
  gap: 6px;
}

.todo-title-row {
  gap: var(--usp-space-2);
}

.todo-arrow {
  color: var(--usp-color-primary);
  font-weight: 800;
}
</style>
