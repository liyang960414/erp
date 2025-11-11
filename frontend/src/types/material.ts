export interface Material {
  id: number
  code: string
  name: string
  specification?: string
  mnemonicCode?: string
  oldNumber?: string
  description?: string
  erpClsId?: string
  materialGroupId: number
  materialGroupCode: string
  materialGroupName: string
  baseUnitId: number
  baseUnitCode: string
  baseUnitName: string
  createdAt: string
  updatedAt: string
}
