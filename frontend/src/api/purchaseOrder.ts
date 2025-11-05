import request from '@/utils/request'
import type { PurchaseOrder, PurchaseOrderImportResponse, PageResponse, PurchaseOrderListResponse } from '@/types/purchaseOrder'

export interface PurchaseOrderQueryParams {
  billNo?: string
  supplierCode?: string
  status?: 'OPEN' | 'CLOSED'
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export const purchaseOrderApi = {
  // 分页查询采购订单
  getPurchaseOrders(params?: PurchaseOrderQueryParams): Promise<PurchaseOrderListResponse> {
    return request.get('/purchase-orders', { params })
  },

  // 获取订单详情（含明细）
  getPurchaseOrderById(id: number): Promise<PurchaseOrder> {
    return request.get(`/purchase-orders/${id}`)
  },

  // 导入采购订单（Excel文件）
  importPurchaseOrders(file: File): Promise<PurchaseOrderImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/purchase-orders/import', formData, {
      timeout: 1800000, // 30分钟超时，因为大文件导入可能耗时较长
    })
  },
}

