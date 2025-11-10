export type SaleOutstockStatus = 'OPEN' | 'CLOSED' // 预留状态字段，如后端添加可直接使用

export interface SaleOutstockItem {
  id: number
  saleOutstockId: number
  sequence: number
  saleOrderId?: number
  saleOrderBillNo?: string
  saleOrderItemId?: number
  saleOrderItemSequence?: number
  saleOrderSequence?: number
  materialCode?: string
  materialName?: string
  unitCode?: string
  unitName?: string
  qty: number
  entryNote?: string
  woNumber?: string
  createdAt: string
  updatedAt: string
}

export interface SaleOutstock {
  id: number
  billNo: string
  outstockDate: string
  note?: string
  itemCount: number
  totalQty: number
  createdAt: string
  updatedAt: string
  items?: SaleOutstockItem[]
}

export interface SaleOutstockQueryParams {
  billNo?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

export interface SaleOutstockListResponse {
  content?: SaleOutstock[]
  page?: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
  totalElements?: number
  totalPages?: number
  size?: number
  number?: number
}

export interface SaleOutstockImportError {
  sheetName: string
  rowNumber: number
  field?: string
  message: string
}

export interface SaleOutstockImportResult {
  totalRows: number
  successCount: number
  failureCount: number
  errors: SaleOutstockImportError[]
}

export interface SaleOutstockImportResponse {
  result: SaleOutstockImportResult
}

