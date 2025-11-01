<template>
  <div class="order-container">
    <el-card>
      <template #header>
        <span>订单管理</span>
      </template>
      <p>订单管理功能开发中...</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.order-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.order-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .order-container {
    padding: 0;
  }
}
</style>

