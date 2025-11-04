export interface BomItem {
  id: number
  bomId: number
  sequence: number
  childMaterialId: number
  childMaterialCode: string
  childMaterialName: string
  childUnitId: number
  childUnitCode: string
  childUnitName: string
  numerator: number
  denominator: number
  scrapRate?: number | null
  childBomVersion: string
  memo?: string | null
  createdAt: string
  updatedAt: string
}

export interface BillOfMaterial {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  materialGroupCode: string
  materialGroupName: string
  version: string
  name?: string | null
  category?: string | null
  usage?: string | null
  description?: string | null
  items: BomItem[]
  createdAt: string
  updatedAt: string
}

export interface BomImportResult {
  totalRows: number
  successCount: number
  failureCount: number
  errors: BomImportError[]
}

export interface BomImportResponse {
  bomResult: BomImportResult
  itemResult: BomImportResult
}

export interface BomImportError {
  sheetName: string
  rowNumber: number
  field: string | null
  message: string
}

export interface CreateBomRequest {
  materialId: number
  version: string
  name?: string
  category?: string
  usage?: string
  description?: string
  items: CreateBomItemRequest[]
}

export interface CreateBomItemRequest {
  sequence: number
  childMaterialId: number
  childUnitId: number
  numerator: number
  denominator: number
  scrapRate?: number | null
  childBomVersion?: string
  memo?: string | null
}

export interface UpdateBomRequest {
  name?: string
  category?: string
  usage?: string
  description?: string
  items: UpdateBomItemRequest[]
}

export interface UpdateBomItemRequest {
  id?: number
  sequence: number
  childMaterialId: number
  childUnitId: number
  numerator: number
  denominator: number
  scrapRate?: number | null
  childBomVersion?: string
  memo?: string | null
}

/**
 * BOM查询结果节点（支持树形结构）
 */
export interface BomQueryNode {
  materialId: number
  materialCode: string
  materialName: string
  materialSpecification: string | null // 物料型号
  materialGroupCode: string | null
  materialGroupName: string | null
  bomId: number | null
  bomVersion: string | null
  bomName: string | null
  sequence?: number | null
  numerator?: number | null
  denominator?: number | null
  scrapRate?: number | null
  childBomVersion?: string | null
  childUnitCode?: string | null // 子项单位编码
  childUnitName?: string | null // 子项单位名称
  children: BomQueryNode[]
  // 计算用量相关字段（前端计算）
  calculatedQuantity?: number | null // 计算出的需求数量
  parentQuantity?: number | null // 父物料的需求数量
}
