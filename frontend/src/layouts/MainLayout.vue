<template>
  <el-container class="layout-container">
    <!-- 顶部导航栏 -->
    <el-header class="header">
      <div class="header-left">
        <h2 class="logo">{{ $t('auth.loginTitle') }}</h2>
      </div>
      <div class="header-right">
        <!-- 语言切换 -->
        <el-select
          v-model="currentLocale"
          size="small"
          style="width: 150px; margin-right: 20px"
          @change="handleLocaleChange"
        >
          <el-option
            v-for="locale in localeStore.supportedLocales"
            :key="locale.value"
            :label="locale.label"
            :value="locale.value"
          />
        </el-select>

        <el-dropdown @command="handleCommand">
          <span class="user-dropdown">
            <el-avatar :size="32">
              {{ authStore.user?.username?.charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="username">{{ authStore.user?.username }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>
                {{ $t('auth.profile') }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                {{ $t('auth.logout') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container>
      <!-- 侧边栏 -->
      <el-aside width="240px" class="aside">
        <el-menu
          :default-active="activeMenu"
          router
          class="sidebar-menu"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
        >
          <el-menu-item index="/home">
            <el-icon><HomeFilled /></el-icon>
            <span>{{ $t('menu.home') }}</span>
          </el-menu-item>

          <el-sub-menu index="/users" v-if="authStore.hasRole('ADMIN')">
            <template #title>
              <el-icon><User /></el-icon>
              <span>{{ $t('menu.userManagement') }}</span>
            </template>
            <el-menu-item index="/users/list">{{ $t('menu.userList') }}</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="/products">
            <el-icon><Goods /></el-icon>
            <span>{{ $t('menu.productManagement') }}</span>
          </el-menu-item>

          <el-menu-item index="/orders">
            <el-icon><ShoppingBag /></el-icon>
            <span>{{ $t('menu.orderManagement') }}</span>
          </el-menu-item>

          <el-sub-menu index="/basic-info">
            <template #title>
              <el-icon><Document /></el-icon>
              <span>{{ $t('menu.basicInfoManagement') }}</span>
            </template>
            <el-menu-item index="/basic-info/units">{{ $t('menu.unitManagement') }}</el-menu-item>
            <el-menu-item index="/basic-info/materials">{{
              $t('menu.materialManagement')
            }}</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="/engineering-data">
            <template #title>
              <el-icon><Tools /></el-icon>
              <span>{{ $t('menu.engineeringDataManagement') }}</span>
            </template>
            <el-sub-menu index="/engineering-data/bom-list">
              <template #title>
                <span>{{ $t('menu.bomList') }}</span>
              </template>
              <el-menu-item index="/engineering-data/bom-list/boms">{{
                $t('menu.bomListManagement')
              }}</el-menu-item>
            </el-sub-menu>
          </el-sub-menu>

          <el-sub-menu index="/system" v-if="authStore.hasRole('ADMIN')">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>{{ $t('menu.systemSettings') }}</span>
            </template>
            <el-menu-item index="/system/settings">{{ $t('menu.basicSettings') }}</el-menu-item>
            <el-menu-item index="/system/roles">{{ $t('menu.roleManagement') }}</el-menu-item>
            <el-menu-item index="/system/permissions">{{
              $t('menu.permissionManagement')
            }}</el-menu-item>
            <el-menu-item index="/system/audit-logs">{{ $t('menu.auditLogs') }}</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="main-content">
        <!-- 标签栏 -->
        <TabsBar />
        <div class="main-content-wrapper">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  User,
  Goods,
  ShoppingBag,
  Setting,
  ArrowDown,
  SwitchButton,
  Document,
  Tools,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
import { useTabsStore } from '@/stores/tabs'
import TabsBar from '@/components/TabsBar.vue'
import type { LocaleType } from '@/locales'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const localeStore = useLocaleStore()
const tabsStore = useTabsStore()
const { t } = useI18n()

const activeMenu = computed(() => route.path)
const currentLocale = ref<LocaleType>(localeStore.currentLocale)

// 初始化标签页
onMounted(() => {
  tabsStore.initTabs()
})

const handleLocaleChange = (locale: LocaleType) => {
  localeStore.changeLocale(locale)
}

const handleCommand = async (command: string) => {
  if (command === 'profile') {
    ElMessage.info(t('auth.profileDeveloping'))
  } else if (command === 'logout') {
    await ElMessageBox.confirm(t('auth.logoutConfirm'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })

    await authStore.logout()
    tabsStore.clearTabs()
    ElMessage.success(t('auth.logoutSuccess'))
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden; /* 禁止主布局容器滚动 */
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 24px;
  height: 64px;
  flex-shrink: 0; /* 防止头部被压缩 */
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: center;
}

.logo {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #409eff;
  line-height: 1.5;
}

@media (min-width: 1024px) {
  .logo {
    font-size: 22px;
  }
}

.header-right {
  display: flex;
  align-items: center;
}

.user-dropdown {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-dropdown:hover {
  background-color: #f5f7fa;
}

.username {
  margin: 0 8px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

/* 内部容器使用 flex 横向布局 */
.layout-container :deep(.el-container) {
  display: flex;
  flex-direction: row;
  flex: 1; /* 占据剩余空间 */
  overflow: hidden; /* 禁止内部容器滚动 */
  min-height: 0; /* 允许 flex 子元素缩小 */
}

.aside {
  display: flex;
  flex-direction: column;
  background-color: #304156;
  overflow: hidden; /* 禁止侧边栏滚动 */
  flex-shrink: 0; /* 防止侧边栏被压缩 */
}

.sidebar-menu {
  border-right: none;
  width: 100%;
  height: 100%;
  overflow-y: auto; /* 菜单内容可以滚动 */
}

/* 桌面端适配 - 侧边栏宽度优化 */
@media (min-width: 1024px) {
  .aside {
    width: 240px !important;
  }
}

.main-content {
  display: flex;
  flex-direction: column;
  flex: 1; /* 占据剩余空间 */
  background-color: #f0f2f5;
  padding: 0; /* 移除 padding，让 wrapper 控制 */
  overflow: hidden; /* 禁止主内容区直接滚动 */
  min-height: 0; /* 允许 flex 子元素缩小 */
}

.main-content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1; /* 填充整个主内容区 */
  padding: 24px;
  overflow-y: auto; /* 仅 wrapper 允许垂直滚动 */
  overflow-x: hidden; /* 禁止横向滚动 */
  min-height: 0; /* 允许 flex 子元素缩小 */
}

/* 桌面端适配 - 主内容区优化 */
@media (min-width: 1024px) {
  .main-content-wrapper {
    padding: 24px 32px;
  }
}

:deep(.el-avatar) {
  background-color: #409eff;
}
</style>
