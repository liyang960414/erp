export type PurchaseOrderStatus = 'OPEN' | 'CLOSED'

export interface PurchaseOrderItem {
  id: number
  purchaseOrderId: number
  sequence: number
  materialId: number
  materialCode: string
  materialName: string
  bomId?: number
  bomVersion?: string
  materialDesc?: string
  unitId: number
  unitCode: string
  unitName: string
  qty: number
  planConfirm: boolean
  salUnitId?: number
  salUnitCode?: string
  salUnitName?: string
  salQty?: number
  salJoinQty?: number
  baseSalJoinQty?: number
  remarks?: string
  salBaseQty?: number
  deliveredQty: number
  createdAt: string
  updatedAt: string
}

export interface PurchaseOrder {
  id: number
  billNo: string
  orderDate: string
  supplierId: number
  supplierCode: string
  supplierName: string
  status: PurchaseOrderStatus
  note?: string
  createdAt: string
  updatedAt: string
  items?: PurchaseOrderItem[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface PurchaseOrderListResponse {
  content?: PurchaseOrder[]
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

