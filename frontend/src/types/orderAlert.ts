/**
 * 订单提醒类型定义
 */

export enum AlertType {
  PURCHASE_REMINDER = 'PURCHASE_REMINDER',    // 采购料提醒
  PRODUCTION_REMINDER = 'PRODUCTION_REMINDER', // 生产提醒
  DELIVERY_OVERDUE = 'DELIVERY_OVERDUE'       // 要货日期超期告警
}

export interface OrderAlert {
  alertType: AlertType
  orderId: number
  billNo: string
  customerName?: string | null
  woNumber?: string | null
  orderItemId: number
  materialCode?: string | null
  materialName?: string | null
  qty: number
  unitCode?: string | null
  unitName?: string | null
  inspectionDate?: string | null
  deliveryDate?: string | null
  daysRemaining: number  // 剩余天数（正数）或超期天数（负数）
  alertMessage: string
}


