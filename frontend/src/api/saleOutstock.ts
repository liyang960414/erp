import request from '@/utils/request'
import type { SaleOutstockListResponse, SaleOutstockQueryParams, SaleOutstock } from '@/types/saleOutstock'
import type { ImportTaskCreateResponse } from '@/types/importTask'

export const saleOutstockApi = {
  importSaleOutstocks(file: File): Promise<ImportTaskCreateResponse> {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/sale-outstocks/import', formData, {
      timeout: 600000,
    })
  },

  getSaleOutstocks(params?: SaleOutstockQueryParams): Promise<SaleOutstockListResponse> {
    return request.get('/sale-outstocks', { params })
  },

  getSaleOutstockById(id: number): Promise<SaleOutstock> {
    return request.get(`/sale-outstocks/${id}`)
  },
}

