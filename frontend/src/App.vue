<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
// @ts-ignore - Element Plus locale files
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
// @ts-ignore - Element Plus locale files
import vi from 'element-plus/dist/locale/vi.mjs'
// @ts-ignore - Element Plus locale files
import en from 'element-plus/dist/locale/en.mjs'
// @ts-ignore - Element Plus locale files
import id from 'element-plus/dist/locale/id.mjs'

const authStore = useAuthStore()
const localeStore = useLocaleStore()

// Element Plus 的 locale 映射
const elementPlusLocaleMap: Record<string, any> = {
  'zh-CN': zhCn,
  vi: vi,
  en: en,
  id: id,
}

const elementLocale = computed(() => {
  return elementPlusLocaleMap[localeStore.currentLocale] || zhCn
})

onMounted(() => {
  // 初始化用户信息
  authStore.initUser()
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  width: 100%;
  height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    sans-serif;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  margin: 0;
  padding: 0;
  font-size: 14px;
  color: #333;
}

/* 桌面端适配 - 最小宽度限制 */
@media (min-width: 1024px) {
  body {
    font-size: 14px;
  }
}
</style>
