<template>
  <section class="archive-management-page">
    <header class="page-head">
      <div>
        <h1>小区与楼栋管理</h1>
        <p>统一维护小区、楼栋及地图定位信息。</p>
      </div>
    </header>
  </section>
</template>

<style scoped lang="scss">
.archive-management-page {
  display: grid;
  gap: var(--usp-space-5);
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--usp-space-4);
}

.page-head h1 {
  margin: 0;
  color: var(--usp-color-text-primary);
  font-size: var(--usp-font-size-2xl);
}

.page-head p {
  margin: var(--usp-space-2) 0 0;
  color: var(--usp-color-text-secondary);
}
</style>
