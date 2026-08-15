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
          <span class="panel-kicker">行动队列</span>
          <strong>我的待办</strong>
          <span>按当前职责直达需要处理的业务</span>
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
        :class="{ 'todo-item--high': item.priority === 'HIGH' }"
        @click="emit('select', item)"
      >
        <span class="todo-main">
          <span class="todo-title-row">
            <strong>{{ item.label }}</strong>
            <el-tag v-if="item.priority === 'HIGH'" size="small" type="warning" effect="dark">优先处理</el-tag>
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
  overflow: hidden;
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

.panel-kicker {
  color: var(--usp-color-primary) !important;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .1em;
}

.panel-header span:not(.panel-kicker),
.todo-item small {
  color: var(--usp-color-text-secondary);
}

.todo-list {
  display: grid;
  gap: var(--usp-space-2);
}

.todo-item {
  position: relative;
  overflow: hidden;
  width: 100%;
  justify-content: space-between;
  gap: var(--usp-space-4);
  padding: 14px 14px 14px 17px;
  border: 1px solid transparent;
  border-radius: var(--usp-radius-md);
  background: transparent;
  color: var(--usp-color-text-primary);
  text-align: left;
  cursor: pointer;
  transition: background .16s ease, border-color .16s ease, transform .16s ease;
}

.todo-item::before {
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: rgba(148, 163, 184, .55);
  content: '';
}

.todo-item--high {
  border-color: rgba(245, 158, 11, .16);
  background: linear-gradient(90deg, rgba(245, 158, 11, .08), transparent 48%);
}

.todo-item--high::before {
  background: #f59e0b;
}

.todo-item:hover {
  transform: translateX(2px);
  border-color: var(--usp-color-border);
  background-color: var(--usp-color-primary-soft);
}

.todo-main {
  display: grid;
  gap: 6px;
}

.todo-title-row {
  flex-wrap: wrap;
  gap: var(--usp-space-2);
}

.todo-arrow {
  color: var(--usp-color-primary);
  font-weight: 800;
}
</style>