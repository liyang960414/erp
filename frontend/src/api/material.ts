import request from '@/utils/request'
import type { Material, MaterialImportResponse } from '@/types/material'

export const materialApi = {
  // 获取所有物料
  getMaterials(): Promise<Material[]> {
    return request.get('/materials')
  },

  // 获取物料详情
  getMaterialById(id: number): Promise<Material> {
    return request.get(`/materials/${id}`)
  },

  // 按物料组ID获取物料
  getMaterialsByGroupId(groupId: number): Promise<Material[]> {
    return request.get(`/materials/group/${groupId}`)
  },

  // 导入物料（Excel文件）
  importMaterials(file: File): Promise<MaterialImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/materials/import', formData, {
      timeout: 600000, // 10分钟超时，因为大文件导入可能耗时较长
    })
  },

  // 搜索物料（根据编码或名称模糊匹配）
  searchMaterials(keyword: string, limit: number = 20): Promise<Material[]> {
    return request.get('/materials/search', {
      params: { keyword, limit },
    })
  },
}
