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

