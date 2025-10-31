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

          <el-sub-menu index="/system" v-if="authStore.hasRole('ADMIN')">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>{{ $t('menu.systemSettings') }}</span>
            </template>
            <el-menu-item index="/system/settings">{{ $t('menu.basicSettings') }}</el-menu-item>
            <el-menu-item index="/system/roles">{{ $t('menu.roleManagement') }}</el-menu-item>
            <el-menu-item index="/system/permissions">{{ $t('menu.permissionManagement') }}</el-menu-item>
            <el-menu-item index="/system/audit-logs">{{ $t('menu.auditLogs') }}</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useLocaleStore } from '@/stores/locale'
import type { LocaleType } from '@/locales'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const localeStore = useLocaleStore()
const { t } = useI18n()

const activeMenu = computed(() => route.path)
const currentLocale = ref<LocaleType>(localeStore.currentLocale)

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
    ElMessage.success(t('auth.logoutSuccess'))
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 24px;
  height: 64px;
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
  color: #409EFF;
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

.aside {
  background-color: #304156;
  min-height: calc(100vh - 64px);
  overflow-y: auto;
}

.sidebar-menu {
  border-right: none;
  width: 100%;
}

/* 桌面端适配 - 侧边栏宽度优化 */
@media (min-width: 1024px) {
  .aside {
    width: 240px !important;
  }
}

.main-content {
  background-color: #f0f2f5;
  padding: 24px;
  overflow-y: auto;
  min-height: calc(100vh - 64px);
}

/* 桌面端适配 - 主内容区优化 */
@media (min-width: 1024px) {
  .main-content {
    padding: 24px 32px;
  }
}

:deep(.el-avatar) {
  background-color: #409EFF;
}
</style>

