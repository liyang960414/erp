<template>
  <div class="order-alert-container">
    <el-card class="alert-card">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><Bell /></el-icon>
            订单提醒
          </span>
          <el-button type="primary" size="small" text @click="handleRefresh">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <div v-loading="loading" class="alert-content">
        <div v-if="alerts.length === 0" class="empty-state">
          <el-empty description="暂无订单提醒" :image-size="100" />
        </div>

        <div v-else class="alerts-list">
          <!-- 采购料提醒 -->
          <div v-if="purchaseReminders.length > 0" class="alert-section">
            <div class="section-header purchase">
              <el-icon><ShoppingCart /></el-icon>
              <span>采购料提醒 ({{ purchaseReminders.length }})</span>
            </div>
            <div class="alerts-grid">
              <div
                v-for="alert in purchaseReminders"
                :key="`purchase-${alert.orderItemId}`"
                class="alert-item purchase"
                @click="handleViewOrder(alert.orderId)"
              >
                <div class="alert-header">
                  <span class="order-no">{{ alert.billNo }}</span>
                  <el-tag type="info" size="small">采购提醒</el-tag>
                </div>
                <div class="alert-body">
                  <div class="material-info">
                    <span class="material-name">{{ alert.materialName || '-' }}</span>
                    <span class="material-code">{{ alert.materialCode || '-' }}</span>
                  </div>
                  <div class="alert-details">
                    <div class="detail-item">
                      <span class="label">客户：</span>
                      <span class="value">{{ alert.customerName || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">数量：</span>
                      <span class="value">{{ formatNumber(alert.qty) }} {{ alert.unitName || '' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">验货日期：</span>
                      <span class="value">{{ formatDate(alert.inspectionDate) }}</span>
                    </div>
                    <div class="detail-item highlight">
                      <span class="label">剩余天数：</span>
                      <span class="value">{{ alert.daysRemaining }} 天</span>
                    </div>
                  </div>
                </div>
                <div class="alert-footer">
                  <span class="alert-message">{{ alert.alertMessage }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 生产提醒 -->
          <div v-if="productionReminders.length > 0" class="alert-section">
            <div class="section-header production">
              <el-icon><Tools /></el-icon>
              <span>生产提醒 ({{ productionReminders.length }})</span>
              <el-tag type="warning" size="small" style="margin-left: 8px">
                注意：目前仅基于时间条件判断，后续需添加生产状态判断
              </el-tag>
            </div>
            <div class="alerts-grid">
              <div
                v-for="alert in productionReminders"
                :key="`production-${alert.orderItemId}`"
                class="alert-item production"
                @click="handleViewOrder(alert.orderId)"
              >
                <div class="alert-header">
                  <span class="order-no">{{ alert.billNo }}</span>
                  <el-tag type="warning" size="small">生产提醒</el-tag>
                </div>
                <div class="alert-body">
                  <div class="material-info">
                    <span class="material-name">{{ alert.materialName || '-' }}</span>
                    <span class="material-code">{{ alert.materialCode || '-' }}</span>
                  </div>
                  <div class="alert-details">
                    <div class="detail-item">
                      <span class="label">客户：</span>
                      <span class="value">{{ alert.customerName || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">数量：</span>
                      <span class="value">{{ formatNumber(alert.qty) }} {{ alert.unitName || '' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">验货日期：</span>
                      <span class="value">{{ formatDate(alert.inspectionDate) }}</span>
                    </div>
                    <div class="detail-item highlight">
                      <span class="label">剩余天数：</span>
                      <span class="value">{{ alert.daysRemaining }} 天</span>
                    </div>
                  </div>
                </div>
                <div class="alert-footer">
                  <span class="alert-message">{{ alert.alertMessage }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 要货日期超期告警 -->
          <div v-if="deliveryOverdue.length > 0" class="alert-section">
            <div class="section-header overdue">
              <el-icon><Warning /></el-icon>
              <span>要货日期超期告警 ({{ deliveryOverdue.length }})</span>
            </div>
            <div class="alerts-grid">
              <div
                v-for="alert in deliveryOverdue"
                :key="`overdue-${alert.orderItemId}`"
                class="alert-item overdue"
                @click="handleViewOrder(alert.orderId)"
              >
                <div class="alert-header">
                  <span class="order-no">{{ alert.billNo }}</span>
                  <el-tag type="danger" size="small">超期告警</el-tag>
                </div>
                <div class="alert-body">
                  <div class="material-info">
                    <span class="material-name">{{ alert.materialName || '-' }}</span>
                    <span class="material-code">{{ alert.materialCode || '-' }}</span>
                  </div>
                  <div class="alert-details">
                    <div class="detail-item">
                      <span class="label">客户：</span>
                      <span class="value">{{ alert.customerName || '-' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">数量：</span>
                      <span class="value">{{ formatNumber(alert.qty) }} {{ alert.unitName || '' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="label">要货日期：</span>
                      <span class="value">{{ formatDateTime(alert.deliveryDate) }}</span>
                    </div>
                    <div class="detail-item highlight danger">
                      <span class="label">超期天数：</span>
                      <span class="value">{{ Math.abs(alert.daysRemaining) }} 天</span>
                    </div>
                  </div>
                </div>
                <div class="alert-footer">
                  <span class="alert-message">{{ alert.alertMessage }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'OrderAlertCard',
}
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Refresh, ShoppingCart, Tools, Warning } from '@element-plus/icons-vue'
import { saleOrderApi } from '@/api/saleOrder'
import type { OrderAlert } from '@/types/orderAlert'
import { AlertType } from '@/types/orderAlert'

const router = useRouter()

const loading = ref(false)
const alerts = ref<OrderAlert[]>([])

const purchaseReminders = computed(() =>
  alerts.value.filter((alert) => alert.alertType === AlertType.PURCHASE_REMINDER)
)

const productionReminders = computed(() =>
  alerts.value.filter((alert) => alert.alertType === AlertType.PRODUCTION_REMINDER)
)

const deliveryOverdue = computed(() =>
  alerts.value.filter((alert) => alert.alertType === AlertType.DELIVERY_OVERDUE)
)

const loadAlerts = async () => {
  loading.value = true
  try {
    alerts.value = await saleOrderApi.getOrderAlerts()
  } catch (error: any) {
    ElMessage.error('加载订单提醒失败: ' + (error.message || '未知错误'))
    alerts.value = []
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  loadAlerts()
}

const handleViewOrder = (orderId: number) => {
  router.push({
    path: '/orders',
    query: { orderId: orderId.toString() },
  })
}

const formatNumber = (value: number | string): string => {
  if (value == null) return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) return '-'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 6 })
}

const formatDate = (value: string | null | undefined): string => {
  if (!value) return '-'
  try {
    const date = new Date(value)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  } catch {
    return value
  }
}

const formatDateTime = (value: string | null | undefined): string => {
  if (!value) return '-'
  try {
    const date = new Date(value)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}

onMounted(() => {
  loadAlerts()
})
</script>

<style scoped>
.order-alert-container {
  margin-top: 20px;
}

.alert-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-content {
  min-height: 200px;
}

.empty-state {
  padding: 40px 0;
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.alert-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  padding: 12px 0;
  border-bottom: 2px solid;
}

.section-header.purchase {
  color: #409eff;
  border-bottom-color: #409eff;
}

.section-header.production {
  color: #e6a23c;
  border-bottom-color: #e6a23c;
}

.section-header.overdue {
  color: #f56c6c;
  border-bottom-color: #f56c6c;
}

.alerts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.alert-item {
  padding: 16px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  background-color: #fff;
  cursor: pointer;
  transition: all 0.3s;
}

.alert-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.alert-item.purchase {
  border-left: 4px solid #409eff;
}

.alert-item.purchase:hover {
  border-left-color: #66b1ff;
  background-color: #ecf5ff;
}

.alert-item.production {
  border-left: 4px solid #e6a23c;
}

.alert-item.production:hover {
  border-left-color: #ebb563;
  background-color: #fdf6ec;
}

.alert-item.overdue {
  border-left: 4px solid #f56c6c;
}

.alert-item.overdue:hover {
  border-left-color: #f78989;
  background-color: #fef0f0;
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.order-no {
  font-weight: 600;
  color: var(--el-color-primary);
  font-size: 14px;
}

.alert-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.material-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.material-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.material-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.alert-details {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item {
  display: flex;
  font-size: 13px;
}

.detail-item .label {
  color: var(--el-text-color-secondary);
  min-width: 80px;
}

.detail-item .value {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.detail-item.highlight .value {
  color: var(--el-color-primary);
  font-weight: 600;
}

.detail-item.highlight.danger .value {
  color: #f56c6c;
}

.alert-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.alert-message {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-style: italic;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .alerts-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .alerts-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>


