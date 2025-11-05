import request from '@/utils/request'
import type { SaleOrder, SaleOrderImportResponse, PageResponse, SaleOrderListResponse } from '@/types/saleOrder'

export interface SaleOrderQueryParams {
  billNo?: string
  customerCode?: string
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

  // 导入销售订单（Excel文件）
  importSaleOrders(file: File): Promise<SaleOrderImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/sale-orders/import', formData, {
      timeout: 600000, // 10分钟超时，因为大文件导入可能耗时较长
    })
  },
}
