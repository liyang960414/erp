import './assets/main.css'
import 'element-plus/dist/index.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import App from './App.vue'
import router from './router'
import i18n, { getLocale } from './locales'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)

// 根据当前 i18n locale 设置 Element Plus 的默认语言
// 注意：Element Plus 的 locale 现在由 App.vue 中的 ConfigProvider 动态控制
const currentLocale = getLocale()
app.use(ElementPlus, {
  locale: zhCn, // 默认使用中文，实际语言由 ConfigProvider 控制
})

// 全局注册所有 Element Plus 图标
for (const [iconName, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(iconName, component)
}

app.mount('#app')
