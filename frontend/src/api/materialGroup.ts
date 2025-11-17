import request from '@/utils/request'
import type { MaterialGroup } from '@/types/materialGroup'

export const materialGroupApi = {
  // 获取所有物料组
  getMaterialGroups(): Promise<MaterialGroup[]> {
    return request.get('/material-groups')
  },

  // 获取物料组详情
  getMaterialGroupById(id: number): Promise<MaterialGroup> {
    return request.get(`/material-groups/${id}`)
  },
}







