export type ImportTaskStatus =
  | 'WAITING'
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export type ImportTaskItemStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type ImportFailureStatus = 'PENDING' | 'RESUBMITTED' | 'RESOLVED'

export interface ImportTaskCreateResponse {
  taskId: number
  taskCode: string
  importType: string
  status: ImportTaskStatus
  fileName: string
  createdBy: string
  createdAt: string
}

export interface ImportTaskSummary extends ImportTaskCreateResponse {
  totalCount?: number | null
  successCount?: number | null
  failureCount?: number | null
  startedAt?: string | null
  completedAt?: string | null
}

export interface ImportTaskItemSummary {
  itemId: number
  sequenceNo: number
  status: ImportTaskItemStatus
  fileName: string
  totalCount?: number | null
  successCount?: number | null
  failureCount?: number | null
  failureReason?: string | null
  createdAt: string
  startedAt?: string | null
  completedAt?: string | null
}

export interface ImportTaskDetail {
  task: ImportTaskSummary
  items: ImportTaskItemSummary[]
}

export interface ImportTaskFailureDTO {
  id: number
  section?: string | null
  rowNumber?: number | null
  field?: string | null
  message: string
  status: ImportFailureStatus
  rawPayload?: string | null
  createdAt: string
  resolvedAt?: string | null
}




