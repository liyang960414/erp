<template>
  <div class="home-container">
    <el-card class="welcome-card">
      <template #header>
        <div class="card-header">
          <span>{{ $t('common.welcome') }} {{ $t('auth.loginTitle') }}</span>
        </div>
      </template>

      <div class="welcome-content">
        <h2>
          {{ $t('common.hello') }}，{{ authStore.user?.fullName || authStore.user?.username }}
        </h2>
        <p>{{ $t('auth.loginSubtitle') }}</p>

        <div class="user-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$t('user.username')">
              {{ authStore.user?.username }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('user.email')">
              {{ authStore.user?.email }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('user.roles')">
              <el-tag
                v-for="role in authStore.user?.roles"
                :key="role.id"
                style="margin-right: 8px"
                type="primary"
              >
                {{ role.name }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('user.status')">
              <el-tag v-if="authStore.user?.enabled" type="success">{{
                $t('common.enabled')
              }}</el-tag>
              <el-tag v-else type="danger">{{ $t('common.disabled') }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'home',
}
</script>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(async () => {
  if (authStore.isAuthenticated && !authStore.user) {
    await authStore.fetchUserInfo()
  }
})
</script>

<style scoped>
.home-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

.welcome-card {
  flex: 1;
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 桌面端适配 */
@media (min-width: 1024px) {
  .home-container {
    padding: 0;
  }

  .welcome-card {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }
}

.card-header {
  font-size: 18px;
  font-weight: 600;
}

.welcome-content h2 {
  margin: 20px 0;
  color: #333;
}

.user-info {
  margin-top: 30px;
}
</style>
