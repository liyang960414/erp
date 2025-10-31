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
  max-width: 1400px;
  margin: 0 auto;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .order-container {
    padding: 0;
  }
}
</style>

