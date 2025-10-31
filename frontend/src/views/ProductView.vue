<template>
  <div class="product-container">
    <el-card>
      <template #header>
        <span>商品管理</span>
      </template>
      <p>商品管理功能开发中...</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.product-container {
  max-width: 1400px;
  margin: 0 auto;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .product-container {
    padding: 0;
  }
}
</style>

