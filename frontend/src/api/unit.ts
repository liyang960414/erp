import request from '@/utils/request'
import type {
  Unit,
  CreateUnitRequest,
  UpdateUnitRequest,
  UnitImportResponse,
} from '@/types/unit'

export const unitApi = {
  // 获取所有单位
  getUnits(): Promise<Unit[]> {
    return request.get('/units')
  },

  // 根据单位组ID获取单位列表
  getUnitsByGroupId(groupId: number): Promise<Unit[]> {
    return request.get(`/units/group/${groupId}`)
  },

  // 获取单位详情
  getUnitById(id: number): Promise<Unit> {
    return request.get(`/units/${id}`)
  },

  // 创建单位
  createUnit(data: CreateUnitRequest): Promise<Unit> {
    return request.post('/units', data)
  },

  // 更新单位
  updateUnit(id: number, data: UpdateUnitRequest): Promise<Unit> {
    return request.put(`/units/${id}`, data)
  },

  // 删除单位
  deleteUnit(id: number): Promise<void> {
    return request.delete(`/units/${id}`)
  },

  // 导入Excel文件
  importUnits(file: File): Promise<UnitImportResponse> {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/units/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      timeout: 300000, // 导入接口设置为5分钟（300秒）
    })
  },
}

