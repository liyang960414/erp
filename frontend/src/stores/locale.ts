import { defineStore } from 'pinia'
import { ref } from 'vue'
import { setLocale, getLocale, type LocaleType, supportedLocales } from '@/locales'

export const useLocaleStore = defineStore('locale', () => {
  const currentLocale = ref<LocaleType>(getLocale())

  const changeLocale = (locale: LocaleType) => {
    setLocale(locale)
    currentLocale.value = locale
    // 使用 ConfigProvider 后，不再需要刷新页面
  }

  return {
    currentLocale,
    changeLocale,
    supportedLocales,
  }
})
