import request from '@/utils/request'
import type { BillOfMaterial, BomImportResponse } from '@/types/bom'

export const bomApi = {
  // 获取所有BOM
  getBoms(): Promise<BillOfMaterial[]> {
    return request.get('/boms')
  },

  // 获取BOM详情
  getBomById(id: number): Promise<BillOfMaterial> {
    return request.get(`/boms/${id}`)
  },

  // 删除BOM
  deleteBom(id: number): Promise<void> {
    return request.delete(`/boms/${id}`)
  },

  // 导入BOM（Excel/CSV文件）
  importBoms(file: File): Promise<BomImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/boms/import', formData, {
      timeout: 600000, // 10分钟超时，因为大文件导入可能耗时较长
    })
  },
}

