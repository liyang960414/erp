export interface UnitGroup {
  id: number
  code: string
  name: string
  description?: string
  parentId?: number
  createdAt: string
  updatedAt: string
}

export interface Unit {
  id: number
  code: string
  name: string
  unitGroup: UnitGroupSummary
  enabled: boolean
  conversionNumerator?: number
  conversionDenominator?: number
  createdAt: string
  updatedAt: string
}

export interface UnitGroupSummary {
  id: number
  code: string
  name: string
}

export interface CreateUnitGroupRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateUnitGroupRequest {
  name?: string
  description?: string
}

export interface CreateUnitRequest {
  code: string
  name: string
  unitGroupId: number
  enabled?: boolean
}

export interface UpdateUnitRequest {
  name?: string
  unitGroupId?: number
  enabled?: boolean
  conversionNumerator?: number
  conversionDenominator?: number
}

export interface UnitImportResponse {
  totalRows: number
  successCount: number
  failureCount: number
  errors: ImportError[]
}

export interface ImportError {
  rowNumber: number
  field?: string
  message: string
}
