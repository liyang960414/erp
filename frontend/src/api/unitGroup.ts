import request from '@/utils/request'
import type { UnitGroup, CreateUnitGroupRequest, UpdateUnitGroupRequest } from '@/types/unit'

export const unitGroupApi = {
  // 获取所有单位组
  getUnitGroups(): Promise<UnitGroup[]> {
    return request.get('/unit-groups')
  },

  // 获取单位组详情
  getUnitGroupById(id: number): Promise<UnitGroup> {
    return request.get(`/unit-groups/${id}`)
  },

  // 创建单位组
  createUnitGroup(data: CreateUnitGroupRequest): Promise<UnitGroup> {
    return request.post('/unit-groups', data)
  },

  // 更新单位组
  updateUnitGroup(id: number, data: UpdateUnitGroupRequest): Promise<UnitGroup> {
    return request.put(`/unit-groups/${id}`, data)
  },

  // 删除单位组
  deleteUnitGroup(id: number): Promise<void> {
    return request.delete(`/unit-groups/${id}`)
  },
}

