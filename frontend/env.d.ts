/// <reference types="vite/client" />

// Element Plus locale 模块类型声明
declare module 'element-plus/dist/locale/zh-cn.mjs' {
  import type { Language } from 'element-plus/es/locale'
  const zhCn: Language
  export default zhCn
}

declare module 'element-plus/dist/locale/vi.mjs' {
  import type { Language } from 'element-plus/es/locale'
  const vi: Language
  export default vi
}

declare module 'element-plus/dist/locale/en.mjs' {
  import type { Language } from 'element-plus/es/locale'
  const en: Language
  export default en
}

declare module 'element-plus/dist/locale/id.mjs' {
  import type { Language } from 'element-plus/es/locale'
  const id: Language
  export default id
}
