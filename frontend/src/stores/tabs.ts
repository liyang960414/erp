import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'

export interface TabItem {
  path: string
  title: string
  name: string
  closable: boolean
}

const STORAGE_KEY = 'tab-views'
const HOME_PATH = '/home'

// 从 localStorage 恢复标签页
function loadTabsFromStorage(): TabItem[] {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      const tabs = JSON.parse(stored) as TabItem[]
      // 确保首页标签存在
      const hasHome = tabs.some(tab => tab.path === HOME_PATH)
      if (!hasHome) {
        tabs.unshift({
          path: HOME_PATH,
          title: '首页',
          name: 'home',
          closable: false,
        })
      }
      return tabs
    }
  } catch (error) {
    console.error('加载标签页失败:', error)
  }
  // 默认只有首页标签
  return [
    {
      path: HOME_PATH,
      title: '首页',
      name: 'home',
      closable: false,
    },
  ]
}

// 保存标签页到 localStorage
function saveTabsToStorage(tabs: TabItem[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tabs))
  } catch (error) {
    console.error('保存标签页失败:', error)
  }
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>(loadTabsFromStorage())
  const activeTab = ref<string>(HOME_PATH)

  // 计算属性: 是否有标签页
  const hasTabs = computed(() => tabs.value.length > 0)

  // 添加标签页
  function addTab(route: RouteLocationNormalized) {
    const path = route.path
    const title = (route.meta.title as string) || route.name?.toString() || path
    const name = route.name?.toString() || path

    // 检查标签是否已存在
    const existingTab = tabs.value.find(tab => tab.path === path)
    if (existingTab) {
      // 如果已存在,只更新激活状态
      activeTab.value = path
      saveTabsToStorage(tabs.value)
      return
    }

    // 如果是首页,确保它在第一个位置且不可关闭
    const closable = path !== HOME_PATH
    const newTab: TabItem = {
      path,
      title,
      name,
      closable,
    }

    if (path === HOME_PATH) {
      // 移除可能存在的旧首页标签
      tabs.value = tabs.value.filter(tab => tab.path !== HOME_PATH)
      // 添加到第一个位置
      tabs.value.unshift(newTab)
    } else {
      // 其他标签添加到末尾
      tabs.value.push(newTab)
    }

    activeTab.value = path
    saveTabsToStorage(tabs.value)
  }

  // 删除标签页
  function removeTab(path: string) {
    const tab = tabs.value.find(t => t.path === path)
    if (!tab || !tab.closable) {
      return
    }

    const index = tabs.value.findIndex(t => t.path === path)
    if (index === -1) return

    tabs.value.splice(index, 1)
    saveTabsToStorage(tabs.value)

    // 如果删除的是当前激活的标签,切换到其他标签
    if (activeTab.value === path) {
      if (tabs.value.length > 0) {
        // 优先切换到右侧的标签,如果没有则切换到左侧
        const nextTab = tabs.value[index] || tabs.value[index - 1]
        activeTab.value = nextTab?.path || HOME_PATH
      } else {
        // 如果没有标签了,切换到首页
        activeTab.value = HOME_PATH
        if (!tabs.value.some(t => t.path === HOME_PATH)) {
          tabs.value.push({
            path: HOME_PATH,
            title: '首页',
            name: 'home',
            closable: false,
          })
        }
      }
    }
  }

  // 关闭其他标签
  function closeOtherTabs(currentPath: string) {
    tabs.value = tabs.value.filter(tab => tab.path === currentPath || !tab.closable)
    activeTab.value = currentPath
    saveTabsToStorage(tabs.value)
  }

  // 关闭所有标签
  function closeAllTabs() {
    tabs.value = tabs.value.filter(tab => !tab.closable)
    if (!tabs.value.some(t => t.path === HOME_PATH)) {
      tabs.value.push({
        path: HOME_PATH,
        title: '首页',
        name: 'home',
        closable: false,
      })
    }
    activeTab.value = HOME_PATH
    saveTabsToStorage(tabs.value)
  }

  // 设置激活标签
  function setActiveTab(path: string) {
    activeTab.value = path
  }

  // 初始化标签页(确保首页存在)
  function initTabs() {
    const hasHome = tabs.value.some(tab => tab.path === HOME_PATH)
    if (!hasHome) {
      tabs.value.unshift({
        path: HOME_PATH,
        title: '首页',
        name: 'home',
        closable: false,
      })
      saveTabsToStorage(tabs.value)
    }
    if (!activeTab.value || !tabs.value.some(tab => tab.path === activeTab.value)) {
      activeTab.value = HOME_PATH
    }
  }

  // 清除所有标签(登出时使用)
  function clearTabs() {
    tabs.value = [
      {
        path: HOME_PATH,
        title: '首页',
        name: 'home',
        closable: false,
      },
    ]
    activeTab.value = HOME_PATH
    saveTabsToStorage(tabs.value)
  }

  return {
    tabs,
    activeTab,
    hasTabs,
    addTab,
    removeTab,
    closeOtherTabs,
    closeAllTabs,
    setActiveTab,
    initTabs,
    clearTabs,
  }
})
