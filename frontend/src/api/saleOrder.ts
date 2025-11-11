import request from '@/utils/request'
import type { SaleOrder, SaleOrderListResponse, SaleOrderStatus } from '@/types/saleOrder'
import type { ImportTaskCreateResponse } from '@/types/importTask'
import type { OrderAlert } from '@/types/orderAlert'

export interface SaleOrderQueryParams {
  billNo?: string
  customerCode?: string
  customerName?: string
  woNumber?: string
  status?: SaleOrderStatus
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export const saleOrderApi = {
  // 分页查询销售订单
  getSaleOrders(params?: SaleOrderQueryParams): Promise<SaleOrderListResponse> {
    return request.get('/sale-orders', { params })
  },

  // 获取订单详情（含明细）
  getSaleOrderById(id: number): Promise<SaleOrder> {
    return request.get(`/sale-orders/${id}`)
  },

  // 导入销售订单（Excel文件），返回后台任务信息
  importSaleOrders(file: File): Promise<ImportTaskCreateResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/sale-orders/import', formData, {
      timeout: 600000, // 10分钟超时，因为大文件导入可能耗时较长
    })
  },

  // 获取订单提醒列表
  getOrderAlerts(): Promise<OrderAlert[]> {
    return request.get('/sale-orders/alerts')
  },
}
