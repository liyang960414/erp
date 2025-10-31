<template>
  <div class="settings-container">
    <el-card>
      <template #header>
        <span>系统设置</span>
      </template>
      <p>系统设置功能开发中...</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.settings-container {
  max-width: 1400px;
  margin: 0 auto;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .settings-container {
    padding: 0;
  }
}
</style>

