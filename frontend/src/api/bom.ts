import request from '@/utils/request'
import type { BillOfMaterial, BomImportResponse, BomQueryNode } from '@/types/bom'

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

  // 根据物料编码获取该物料的所有BOM版本列表
  getBomVersionsByMaterialCode(materialCode: string): Promise<BillOfMaterial[]> {
    return request.get(`/boms/material-code/${materialCode}/versions`)
  },

  // BOM正查：根据物料编码和版本，递归查询所有子物料及其BOM
  queryBomForward(materialCode: string, version: string): Promise<BomQueryNode> {
    return request.get('/boms/query/forward', {
      params: { materialCode, version },
    })
  },

  // BOM反查：根据物料编码和版本（可选），递归查询所有父级物料及其BOM
  queryBomBackward(materialCode: string, version?: string): Promise<BomQueryNode[]> {
    return request.get('/boms/query/backward', {
      params: { materialCode, version },
    })
  },
}
