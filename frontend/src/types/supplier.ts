export interface Supplier {
  id: number
  code: string
  name: string
  shortName?: string
  englishName?: string
  description?: string
  createdAt: string
  updatedAt: string
}

export interface SupplierImportError {
  sheetName: string
  rowNumber: number
  field?: string
  message: string
}

export interface SupplierImportResult {
  totalRows: number
  successCount: number
  failureCount: number
  errors: SupplierImportError[]
}

export interface SupplierImportResponse {
  supplierResult: SupplierImportResult
}

