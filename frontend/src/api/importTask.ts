import request from '@/utils/request'
import type {
  ImportFailureStatus,
  ImportTaskCreateResponse,
  ImportTaskDetail,
  ImportTaskFailureDTO,
  ImportTaskStatus,
} from '@/types/importTask'

export interface ImportTaskQuery {
  importType?: string
  status?: ImportTaskStatus
  createdBy?: string
  page?: number
  size?: number
}

export const importTaskApi = {
  listTasks(params: ImportTaskQuery = {}): Promise<any> {
    return request.get('/import-tasks', { params })
  },

  getTaskDetail(taskId: number): Promise<ImportTaskDetail> {
    return request.get(`/import-tasks/${taskId}`)
  },

  listFailures(
    taskId: number,
    params: { status?: ImportFailureStatus; page?: number; size?: number } = {},
  ): Promise<any> {
    return request.get(`/import-tasks/${taskId}/failures`, { params })
  },

  retryTask(
    taskId: number,
    file: File,
    failureIds: number[] = [],
  ): Promise<ImportTaskDetail> {
    const formData = new FormData()
    formData.append('file', file)
    failureIds.forEach((id) => formData.append('failureIds', id.toString()))
    return request.post(`/import-tasks/${taskId}/retry`, formData, {
      timeout: 600000,
    })
  },
}




