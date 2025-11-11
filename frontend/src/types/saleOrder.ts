export interface Customer {
  id: number;
  code: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export type SaleOrderStatus = 'OPEN' | 'CLOSED';
export type SaleOrderItemStatus = 'OPEN' | 'CLOSED';

export interface SaleOrderItem {
  id: number;
  saleOrderId: number;
  sequence: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  unitId: number;
  unitCode: string;
  unitName: string;
  qty: number;
  oldQty?: number;
  inspectionDate?: string;
  deliveryDate?: string;
  bomVersion?: string;
  entryNote?: string;
  customerOrderNo?: string;
  customerLineNo?: string;
  status: SaleOrderItemStatus;
  deliveredQty: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaleOrder {
  id: number;
  billNo: string;
  orderDate: string;
  note?: string;
  woNumber?: string;
  customerId: number;
  customerCode: string;
  customerName: string;
  status: SaleOrderStatus;
  createdAt: string;
  updatedAt: string;
  items?: SaleOrderItem[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

// 后端实际返回的响应格式（嵌套结构）
export interface SaleOrderListResponse {
  content?: SaleOrder[];
  page?: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
  // 兼容直接包含分页信息的情况
  totalElements?: number;
  totalPages?: number;
  size?: number;
  number?: number;
}