<template>
  <div v-if="tabsStore.hasTabs" class="tabs-bar">
    <el-tabs
      v-model="activeTab"
      type="card"
      closable
      @tab-remove="handleTabRemove"
      @tab-click="handleTabClick"
      class="tabs-container"
    >
      <el-tab-pane
        v-for="tab in tabsStore.tabs"
        :key="tab.path"
        :label="tab.title"
        :name="tab.path"
        :closable="tab.closable"
      >
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useTabsStore } from '@/stores/tabs'

const router = useRouter()
const tabsStore = useTabsStore()

const activeTab = computed({
  get: () => tabsStore.activeTab,
  set: (value) => {
    tabsStore.setActiveTab(value)
  },
})

// 监听激活标签变化,切换路由(避免循环更新)
watch(
  () => tabsStore.activeTab,
  (newPath) => {
    if (router.currentRoute.value.path !== newPath) {
      router.push(newPath).catch(() => {
        // 忽略导航重复的错误
      })
    }
  },
)

// 移除标签
const handleTabRemove = (targetName: string | number) => {
  const targetPath = targetName as string
  const wasActive = tabsStore.activeTab === targetPath
  tabsStore.removeTab(targetPath)
  // 如果删除的是当前激活的标签,导航会自动由 watch 处理
  // 但如果删除后没有标签了,需要确保有首页标签
  if (tabsStore.tabs.length === 0) {
    tabsStore.initTabs()
  }
  if (wasActive && tabsStore.activeTab) {
    router.push(tabsStore.activeTab).catch(() => {
      // 忽略导航重复的错误
    })
  }
}

// 点击标签
const handleTabClick = (tab: any) => {
  const path = tab.paneName as string
  // 只更新激活状态,路由切换由 watch 处理
  if (tabsStore.activeTab !== path) {
    tabsStore.setActiveTab(path)
  }
}
</script>

<style scoped>
.tabs-bar {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0;
  height: 40px;
  flex-shrink: 0;
  overflow: hidden;
}

.tabs-container {
  height: 100%;
}

:deep(.el-tabs__header) {
  margin: 0;
  border-bottom: none;
  height: 100%;
}

:deep(.el-tabs__nav-wrap) {
  height: 100%;
  padding: 0 12px;
}

:deep(.el-tabs__nav) {
  height: 100%;
  border: none;
}

:deep(.el-tabs__item) {
  height: 36px;
  line-height: 36px;
  padding: 0 16px;
  margin-top: 2px;
  border: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
  margin-right: 4px;
  background-color: #f5f7fa;
  color: #606266;
  font-size: 13px;
}

:deep(.el-tabs__item:hover) {
  color: #409eff;
}

:deep(.el-tabs__item.is-active) {
  background-color: #fff;
  color: #409eff;
  border-bottom-color: #fff;
}

:deep(.el-tabs__item .el-icon-close) {
  width: 14px;
  font-size: 12px;
  margin-left: 4px;
}

:deep(.el-tabs__item .el-icon-close:hover) {
  background-color: #c0c4cc;
  color: #fff;
  border-radius: 50%;
}

:deep(.el-tabs__content) {
  display: none;
}
</style>
