import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import vi from './vi'
import en from './en'
import id from './id'

export type LocaleType = 'zh-CN' | 'vi' | 'en' | 'id'

const LOCALE_KEY = 'locale'

// 从 localStorage 获取保存的语言，默认为中文
const getDefaultLocale = (): LocaleType => {
  const saved = localStorage.getItem(LOCALE_KEY) as LocaleType | null
  if (saved && ['zh-CN', 'vi', 'en', 'id'].includes(saved)) {
    return saved
  }
  return 'zh-CN'
}

const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  locale: getDefaultLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    vi: vi,
    en: en,
    id: id,
  },
})

export default i18n

export const setLocale = (locale: LocaleType) => {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
}

export const getLocale = (): LocaleType => {
  return i18n.global.locale.value as LocaleType
}

// 支持的语言列表
export const supportedLocales: Array<{ value: LocaleType; label: string }> = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'vi', label: 'Tiếng Việt' },
  { value: 'en', label: 'English' },
  { value: 'id', label: 'Bahasa Indonesia' },
]
