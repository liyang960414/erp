import request from '@/utils/request'
import type { Supplier, SupplierImportResponse } from '@/types/supplier'

export const supplierApi = {
  // 获取所有供应商列表
  getAllSuppliers(): Promise<Supplier[]> {
    return request.get('/suppliers')
  },

  // 导入供应商（Excel/CSV文件）
  importSuppliers(file: File): Promise<SupplierImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    // 不设置 Content-Type，让浏览器自动添加 boundary
    return request.post('/suppliers/import', formData, {
      timeout: 600000, // 10分钟超时，因为大文件导入可能耗时较长
    })
  },
}

