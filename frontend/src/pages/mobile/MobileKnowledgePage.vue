<script setup lang="ts">
import KnowledgeQaPanel from '@/features/knowledge/KnowledgeQaPanel.vue'
</script>

<template>
  <section class="mobile-knowledge-page">
    <header>
      <p>受控知识检索</p>
      <h1>现场知识助手</h1>
      <span>答案必须带有已审核文档引用；证据不足时系统会明确拒答。</span>
    </header>
    <KnowledgeQaPanel compact />
    <p class="scope-note">社区和楼栋权限由后端再次校验，移动端输入范围不会扩大账号可访问数据。</p>
  </section>
</template>

<style scoped lang="scss">
.mobile-knowledge-page { display: grid; gap: 16px; }
header { display: grid; gap: 5px; }
header p { margin: 0; color: #287a6a; font-size: 13px; font-weight: 700; }
header h1 { margin: 0; font-size: 26px; }
header span { color: #667085; font-size: 13px; line-height: 1.6; }
.scope-note { margin: 0; padding: 12px; border-radius: 12px; background: #eef3f7; color: #667085; font-size: 12px; line-height: 1.6; }
</style>
