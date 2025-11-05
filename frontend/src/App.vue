<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import vi from 'element-plus/dist/locale/vi.mjs'
import en from 'element-plus/dist/locale/en.mjs'
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
  display: flex;
  flex-direction: column;
  width: 100vw;
  height: 100vh;
  overflow: hidden; /* 禁止整体界面滚动 */
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  margin: 0;
  padding: 0;
  font-size: 14px;
  color: #333;
  overflow: hidden; /* 禁止 body 滚动 */
  height: 100vh;
}

html {
  overflow: hidden; /* 禁止 html 滚动 */
  height: 100vh;
}

/* 桌面端适配 - 最小宽度限制 */
@media (min-width: 1024px) {
  body {
    font-size: 14px;
  }
}
</style>
