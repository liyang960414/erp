export type SubReqOrderStatus = 'OPEN' | 'CLOSED'

export interface SubReqOrderItem {
  id: number
  subReqOrderId: number
  sequence: number
  materialId: number
  materialCode: string
  materialName: string
  unitId: number
  unitCode: string
  unitName: string
  qty: number
  bomId?: number
  bomVersion?: string
  bomVersionName?: string
  supplierId: number
  supplierCode: string
  supplierName: string
  lotMaster?: string
  lotManual?: string
  baseNoStockInQty?: number
  noStockInQty?: number
  pickMtrlStatus?: string
  description?: string
  createdAt: string
  updatedAt: string
}

export interface SubReqOrder {
  id: number
  billHeadSeq: number
  description?: string
  status: SubReqOrderStatus
  createdAt: string
  updatedAt: string
  items?: SubReqOrderItem[]
}

export interface SubReqOrderListResponse {
  content?: SubReqOrder[]
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

