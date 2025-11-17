import request from '@/utils/request'
import type { SubReqOrder, SubReqOrderListResponse } from '@/types/subReqOrder'
import type { ImportTaskCreateResponse } from '@/types/importTask'

export interface SubReqOrderQueryParams {
  billHeadSeq?: number
  status?: 'OPEN' | 'CLOSED'
  description?: string
  page?: number
  size?: number
}

export const subReqOrderApi = {
  // 分页查询委外订单
  getSubReqOrders(params?: SubReqOrderQueryParams): Promise<SubReqOrderListResponse> {
    return request.get('/sub-req-orders', { params })
  },

  // 获取订单详情（含明细）
  getSubReqOrderById(id: number): Promise<SubReqOrder> {
    return request.get(`/sub-req-orders/${id}`)
  },

  // 导入委外订单（Excel文件），返回后台任务信息
  importSubReqOrders(file: File): Promise<ImportTaskCreateResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/sub-req-orders/import', formData, {
      timeout: 1800000, // 30分钟超时，因为大文件导入可能耗时较长
    })
  },
}

