<template>
  <div class="bom-query-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>物料清单查询</span>
        </div>
      </template>

      <!-- 查询表单 -->
      <div class="query-form">
        <el-form :inline="true" :model="queryForm">
          <el-form-item label="物料编码">
            <el-select
              v-model="queryForm.materialCode"
              filterable
              remote
              reserve-keyword
              placeholder="请输入物料编码或名称搜索"
              :remote-method="handleMaterialSearch"
              :loading="materialSearchLoading"
              clearable
              style="width: 300px"
              @clear="handleMaterialCodeClear"
              @change="handleMaterialCodeChange"
            >
              <el-option
                v-for="material in materialOptions"
                :key="material.code"
                :label="`${material.code} - ${material.name}`"
                :value="material.code"
              >
                <span style="font-weight: 500">{{ material.code }}</span>
                <span style="color: #8492a6; margin-left: 8px">{{ material.name }}</span>
              </el-option>
              <el-option
                v-if="materialOptions.length > 0 && materialOptions.length === materialSearchLimit"
                key="__load_more__"
                label="加载更多"
                value="__load_more__"
                disabled
                style="text-align: center"
              >
                <span
                  style="color: #409eff; cursor: pointer; display: flex; align-items: center; justify-content: center"
                  @click.stop="handleLoadMoreMaterials"
                >
                  <el-icon style="margin-right: 4px">
                    <ArrowDown />
                  </el-icon>
                  加载更多（当前显示 {{ materialOptions.length }} 条）
                </span>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="查询类型">
            <el-radio-group v-model="queryForm.queryType">
              <el-radio label="forward">正查</el-radio>
              <el-radio label="backward">反查</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="BOM版本" v-if="bomVersions.length > 0">
            <el-select
              v-model="queryForm.version"
              :placeholder="queryForm.queryType === 'forward' ? '请选择BOM版本' : '请选择BOM版本（可选）'"
              clearable
              style="width: 200px"
            >
              <el-option
                v-for="bom in bomVersions"
                :key="bom.id"
                :label="bom.version"
                :value="bom.version"
              >
                <span>{{ bom.version }}</span>
                <span v-if="bom.name" style="color: #8492a6; font-size: 12px; margin-left: 8px">
                  ({{ bom.name }})
                </span>
              </el-option>
            </el-select>
            <span v-if="queryForm.queryType === 'backward' && bomVersions.length > 0" style="color: #909399; font-size: 12px; margin-left: 8px">
              反查时版本可选
            </span>
          </el-form-item>

          <el-form-item label="需求用量" v-if="queryForm.queryType === 'forward'">
            <el-input-number
              v-model="queryForm.requiredQuantity"
              :min="0"
              :precision="6"
              :step="1"
              placeholder="请输入父物料需求用量"
              clearable
              style="width: 200px"
            />
            <span style="color: #909399; font-size: 12px; margin-left: 8px">
              输入后自动计算子物料需求数量
            </span>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleQuery" :loading="loading">
              <el-icon>
                <Search />
              </el-icon>
              查询
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 查询结果 -->
      <div v-if="queryResult" class="query-result-wrapper">
        <!-- 子物料搜索框（固定定位） -->
        <div class="material-search-box fixed-search-box">
          <el-input
            v-model="childMaterialSearchKeyword"
            placeholder="搜索子物料编码或名称快速定位"
            clearable
            style="width: 300px"
            @input="handleChildMaterialSearch"
            @clear="handleChildMaterialSearchClear"
          >
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
          <el-button
            v-if="childMaterialSearchKeyword"
            type="primary"
            size="small"
            @click="handleLocateChildMaterial"
          >
            定位
          </el-button>
          <el-switch
            v-model="filterMatchedNodesOnly"
            active-text="仅显示匹配节点"
            inactive-text="显示全部"
            style="margin-left: 15px"
            @change="handleFilterToggle"
          />
        </div>
        
        <div class="query-result">
          <div class="result-header">
            <el-divider content-position="left">
              <span style="font-weight: bold">查询结果</span>
            </el-divider>
          </div>

        <!-- 正查结果（单个树） -->
        <div v-if="queryForm.queryType === 'forward' && forwardResult" class="tree-container">
          <el-tree
            ref="forwardTreeRef"
            :data="[filteredForwardResult || forwardResult]"
            :props="treeProps"
            :default-expand-all="true"
            node-key="materialId"
            :highlight-current="true"
            style="padding: 20px"
          >
            <template #default="{ node, data }">
              <el-tooltip
                :content="getNodeTooltipContent(data)"
                placement="top"
                :show-after="300"
                effect="dark"
                raw-content
              >
                <div
                  class="tree-node"
                  :class="{ 'node-highlighted': isNodeHighlighted(data.materialId) }"
                  :data-material-id="data.materialId"
                >
                  <span class="node-label">{{ node.label }}</span>
                  <span v-if="data.bomVersion" class="node-bom-version">[{{ data.bomVersion }}]</span>
                  <span v-if="data.sequence !== null && data.sequence !== undefined" class="node-sequence">
                    序号: {{ data.sequence }}
                  </span>
                  <span v-if="data.numerator && data.denominator" class="node-quantity">
                    用量: {{ data.numerator }}/{{ data.denominator }}
                  </span>
                  <span v-if="data.scrapRate" class="node-scrap">损耗率: {{ data.scrapRate }}%</span>
                  <span v-if="data.childUnitName" class="node-unit">
                    单位: {{ data.childUnitName }}
                  </span>
                  <span v-if="data.calculatedQuantity !== null && data.calculatedQuantity !== undefined" class="node-calculated-quantity">
                    需求: {{ formatNumber(data.calculatedQuantity) }}{{ data.childUnitName ? ` ${data.childUnitName}` : '' }}
                  </span>
                </div>
              </el-tooltip>
            </template>
          </el-tree>
        </div>

        <!-- 反查结果（多个树） -->
        <div v-if="queryForm.queryType === 'backward' && backwardResult.length > 0">
          <div
            v-for="(node, index) in (filteredBackwardResult || backwardResult)"
            :key="index"
            class="tree-container"
          >
            <el-tree
              :ref="(el: any) => setBackwardTreeRef(el, index)"
              :data="[node]"
              :props="treeProps"
              :default-expand-all="true"
              node-key="materialId"
              :highlight-current="true"
              style="padding: 20px; margin-bottom: 20px"
            >
              <template #default="{ node, data }">
                <el-tooltip
                  :content="getNodeTooltipContent(data)"
                  placement="top"
                  :show-after="300"
                  effect="dark"
                  raw-content
                >
                  <div
                    class="tree-node"
                    :class="{ 'node-highlighted': isNodeHighlighted(data.materialId) }"
                    :data-material-id="data.materialId"
                  >
                    <span class="node-label">{{ node.label }}</span>
                    <span v-if="data.bomVersion" class="node-bom-version">[{{ data.bomVersion }}]</span>
                    <span v-if="data.sequence !== null && data.sequence !== undefined" class="node-sequence">
                      序号: {{ data.sequence }}
                    </span>
                    <span v-if="data.numerator && data.denominator" class="node-quantity">
                      用量: {{ data.numerator }}/{{ data.denominator }}
                    </span>
                    <span v-if="data.scrapRate" class="node-scrap">损耗率: {{ data.scrapRate }}%</span>
                    <span v-if="data.childUnitName" class="node-unit">
                      单位: {{ data.childUnitName }}
                    </span>
                    <span v-if="data.calculatedQuantity !== null && data.calculatedQuantity !== undefined" class="node-calculated-quantity">
                      需求: {{ formatNumber(data.calculatedQuantity) }}{{ data.childUnitName ? ` ${data.childUnitName}` : '' }}
                    </span>
                  </div>
                </el-tooltip>
              </template>
            </el-tree>
          </div>
        </div>

          <!-- 无结果提示 -->
          <el-empty
            v-if="queryForm.queryType === 'backward' && backwardResult.length === 0"
            description="未找到使用该物料的父级BOM"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default {
  name: 'bomQuery',
}
</script>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, ArrowDown } from '@element-plus/icons-vue'
import { bomApi } from '@/api/bom'
import { materialApi } from '@/api/material'
import type { BillOfMaterial, BomQueryNode } from '@/types/bom'
import type { Material } from '@/types/material'

const queryForm = ref({
  materialCode: '',
  queryType: 'forward' as 'forward' | 'backward',
  version: '',
  requiredQuantity: null as number | null,
})

const bomVersions = ref<BillOfMaterial[]>([])
const loading = ref(false)
const queryResult = ref(false)
const forwardResult = ref<BomQueryNode | null>(null)
const backwardResult = ref<BomQueryNode[]>([])
const originalForwardResult = ref<BomQueryNode | null>(null) // 保存原始查询结果，用于重新计算
const materialOptions = ref<Material[]>([])
const materialSearchLoading = ref(false)
const materialSearchLimit = ref(20)
const currentSearchKeyword = ref('')

// 子物料搜索相关
const childMaterialSearchKeyword = ref('')
const highlightedNodeIds = ref<Set<number>>(new Set())
const forwardTreeRef = ref<any>(null)
const backwardTreeRefs = ref<any[]>([])
const filterMatchedNodesOnly = ref(false)
const filteredForwardResult = ref<BomQueryNode | null>(null)
const filteredBackwardResult = ref<BomQueryNode[] | null>(null)

const treeProps = {
  children: 'children',
  label: (data: BomQueryNode) => {
    return `${data.materialCode} - ${data.materialName}`
  },
}

// 监听查询类型变化
watch(
  () => queryForm.value.queryType,
  () => {
    // 如果正查且有BOM版本，默认选择第一个；反查时清空版本选择
    if (queryForm.value.queryType === 'forward' && bomVersions.value.length > 0) {
      queryForm.value.version = bomVersions.value[0]?.version || ''
    } else {
      queryForm.value.version = ''
    }
  }
)

// 物料搜索处理（远程搜索）
const handleMaterialSearch = async (query: string) => {
  if (!query || query.trim().length === 0) {
    materialOptions.value = []
    materialSearchLimit.value = 20
    currentSearchKeyword.value = ''
    return
  }

  // 至少输入2个字符才搜索
  if (query.trim().length < 2) {
    return
  }

  // 如果是新的搜索关键词，重置limit
  if (currentSearchKeyword.value !== query.trim()) {
    materialSearchLimit.value = 20
    currentSearchKeyword.value = query.trim()
  }

  materialSearchLoading.value = true
  try {
    materialOptions.value = await materialApi.searchMaterials(query.trim(), materialSearchLimit.value)
  } catch (error: any) {
    ElMessage.error('搜索物料失败: ' + (error.message || '未知错误'))
    materialOptions.value = []
  } finally {
    materialSearchLoading.value = false
  }
}

// 加载更多物料
const handleLoadMoreMaterials = async () => {
  if (!currentSearchKeyword.value || currentSearchKeyword.value.trim().length < 2) {
    return
  }

  // 增加limit（每次增加30条）
  materialSearchLimit.value += 30

  materialSearchLoading.value = true
  try {
    materialOptions.value = await materialApi.searchMaterials(
      currentSearchKeyword.value.trim(),
      materialSearchLimit.value
    )
  } catch (error: any) {
    ElMessage.error('加载更多物料失败: ' + (error.message || '未知错误'))
    // 如果失败，恢复limit
    materialSearchLimit.value -= 30
  } finally {
    materialSearchLoading.value = false
  }
}

// 物料编码变化处理
const handleMaterialCodeChange = async (materialCode: string) => {
  // 忽略"加载更多"选项
  if (materialCode === '__load_more__') {
    queryForm.value.materialCode = ''
    return
  }

  if (materialCode && materialCode.trim()) {
    try {
      bomVersions.value = await bomApi.getBomVersionsByMaterialCode(materialCode.trim())
      // 如果有BOM版本，默认选择第一个
      if (bomVersions.value.length > 0) {
        queryForm.value.version = bomVersions.value[0]?.version || ''
      } else {
        queryForm.value.version = ''
      }
    } catch (error: any) {
      ElMessage.error('加载BOM版本失败: ' + (error.message || '未知错误'))
      bomVersions.value = []
      queryForm.value.version = ''
    }
  } else {
    bomVersions.value = []
    queryForm.value.version = ''
  }
}

// 物料编码清空处理
const handleMaterialCodeClear = () => {
  materialOptions.value = []
  materialSearchLimit.value = 20
  currentSearchKeyword.value = ''
  bomVersions.value = []
  queryForm.value.version = ''
  queryResult.value = false
  forwardResult.value = null
  backwardResult.value = []
}

// 查询处理
const handleQuery = async () => {
  if (!queryForm.value.materialCode || !queryForm.value.materialCode.trim()) {
    ElMessage.warning('请输入物料编码')
    return
  }

  if (queryForm.value.queryType === 'forward') {
    // 正查需要版本
    if (!queryForm.value.version) {
      ElMessage.warning('正查需要选择BOM版本')
      return
    }
  }

  loading.value = true
  queryResult.value = true
  forwardResult.value = null
  backwardResult.value = []
  originalForwardResult.value = null

  try {
    if (queryForm.value.queryType === 'forward') {
      // 正查
      const result = await bomApi.queryBomForward(
        queryForm.value.materialCode.trim(),
        queryForm.value.version
      )
      originalForwardResult.value = JSON.parse(JSON.stringify(result)) // 深拷贝保存原始数据
      forwardResult.value = result
      ElMessage.success('查询成功')
      
      // 如果输入了需求用量，计算子物料需求数量
      if (queryForm.value.requiredQuantity !== null && queryForm.value.requiredQuantity !== undefined) {
        forwardResult.value = calculateQuantities(forwardResult.value, queryForm.value.requiredQuantity)
      }
    } else {
      // 反查
      backwardResult.value = await bomApi.queryBomBackward(
        queryForm.value.materialCode.trim(),
        queryForm.value.version || undefined
      )
      if (backwardResult.value.length === 0) {
        ElMessage.info('未找到使用该物料的父级BOM')
      } else {
        ElMessage.success('查询成功')
      }
    }
  } catch (error: any) {
    ElMessage.error('查询失败: ' + (error.message || '未知错误'))
    queryResult.value = false
  } finally {
    loading.value = false
  }
}

// 重置处理
const handleReset = () => {
  queryForm.value = {
    materialCode: '',
    queryType: 'forward',
    version: '',
    requiredQuantity: null,
  }
  bomVersions.value = []
  queryResult.value = false
  forwardResult.value = null
  backwardResult.value = []
  originalForwardResult.value = null
  childMaterialSearchKeyword.value = ''
  highlightedNodeIds.value.clear()
  filterMatchedNodesOnly.value = false
  filteredForwardResult.value = null
  filteredBackwardResult.value = null
}

// 设置反查树的ref
const setBackwardTreeRef = (el: any, index: number) => {
  if (el) {
    backwardTreeRefs.value[index] = el
  }
}

// 判断节点是否高亮
const isNodeHighlighted = (materialId: number | null): boolean => {
  if (!materialId) return false
  return highlightedNodeIds.value.has(materialId)
}

// 在树中递归搜索物料
const searchInTree = (
  node: BomQueryNode,
  keyword: string,
  foundNodes: BomQueryNode[]
): void => {
  if (!keyword || !keyword.trim()) {
    return
  }

  const searchKey = keyword.toLowerCase().trim()
  const nodeCode = node.materialCode?.toLowerCase() || ''
  const nodeName = node.materialName?.toLowerCase() || ''

  // 检查当前节点是否匹配
  if (nodeCode.includes(searchKey) || nodeName.includes(searchKey)) {
    foundNodes.push(node)
  }

  // 递归搜索子节点
  if (node.children && node.children.length > 0) {
    for (const child of node.children) {
      searchInTree(child, keyword, foundNodes)
    }
  }
}

// 展开到指定节点的所有父节点
const expandToNode = (treeRef: any, targetNodeId: number, nodes: BomQueryNode[]): boolean => {
  if (!treeRef || !nodes || nodes.length === 0) {
    return false
  }

  // 递归查找路径
  const findPath = (node: BomQueryNode, targetId: number, path: BomQueryNode[]): boolean => {
    if (node.materialId === targetId) {
      path.push(node)
      return true
    }

    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
        if (findPath(child, targetId, path)) {
          path.unshift(node)
          return true
        }
      }
    }

    return false
  }

  // 查找所有匹配的节点路径并展开
  for (const rootNode of nodes) {
    const path: BomQueryNode[] = []
    if (findPath(rootNode, targetNodeId, path)) {
      // 展开路径上的所有父节点
      nextTick(() => {
        for (const nodeInPath of path) {
          if (nodeInPath.materialId !== targetNodeId && nodeInPath.materialId) {
            try {
              const nodeMap = treeRef.store?.nodesMap
              if (nodeMap && nodeMap[nodeInPath.materialId]) {
                nodeMap[nodeInPath.materialId].expand()
              }
            } catch (e) {
              // 忽略展开错误
            }
          }
        }
      })
      return true
    }
  }

  return false
}

// 子物料搜索处理
const handleChildMaterialSearch = () => {
  if (!childMaterialSearchKeyword.value || !childMaterialSearchKeyword.value.trim()) {
    highlightedNodeIds.value.clear()
    // 如果开启了过滤，关闭过滤
    if (filterMatchedNodesOnly.value) {
      filterMatchedNodesOnly.value = false
      filteredForwardResult.value = null
      filteredBackwardResult.value = null
    }
    return
  }
  
  // 如果开启了过滤模式，自动更新过滤结果
  if (filterMatchedNodesOnly.value) {
    handleFilterToggle()
  }
}

// 定位子物料
const handleLocateChildMaterial = () => {
  if (!childMaterialSearchKeyword.value || !childMaterialSearchKeyword.value.trim()) {
    ElMessage.warning('请输入要搜索的物料编码或名称')
    return
  }

  highlightedNodeIds.value.clear()

  // 如果开启了过滤模式，先关闭以便搜索全部节点
  const wasFiltering = filterMatchedNodesOnly.value
  if (wasFiltering) {
    filterMatchedNodesOnly.value = false
    filteredForwardResult.value = null
    filteredBackwardResult.value = null
  }

  if (queryForm.value.queryType === 'forward' && forwardResult.value) {
    // 正查：在单个树中搜索
    const foundNodes: BomQueryNode[] = []
    searchInTree(forwardResult.value, childMaterialSearchKeyword.value, foundNodes)

    if (foundNodes.length > 0) {
      // 高亮所有找到的节点
      foundNodes.forEach((node) => {
        if (node.materialId) {
          highlightedNodeIds.value.add(node.materialId)
        }
      })

      // 如果之前开启了过滤模式，重新应用过滤
      if (wasFiltering) {
        handleFilterToggle()
      }

      // 定位到第一个找到的节点
      if (foundNodes[0]?.materialId && forwardTreeRef.value) {
        const firstNode = foundNodes[0]
        const dataToUse = filteredForwardResult.value || forwardResult.value
        expandToNode(forwardTreeRef.value, firstNode.materialId, [dataToUse])
        
        // 滚动到节点
        setTimeout(() => {
          // 尝试多种选择器定位节点
          const nodeElement = document.querySelector(
            `.tree-node[data-material-id="${firstNode.materialId}"]`
          ) as HTMLElement
          if (nodeElement) {
            nodeElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
            // 添加高亮动画
            nodeElement.classList.add('node-locate-animation')
            setTimeout(() => {
              nodeElement.classList.remove('node-locate-animation')
            }, 2000)
          }
          
        }, 300)

        ElMessage.success(`找到 ${foundNodes.length} 个匹配的物料`)
      }
    } else {
      ElMessage.warning('未找到匹配的物料')
    }
  } else if (queryForm.value.queryType === 'backward' && backwardResult.value.length > 0) {
    // 反查：在多个树中搜索
    const foundNodes: BomQueryNode[] = []
    for (const rootNode of backwardResult.value) {
      searchInTree(rootNode, childMaterialSearchKeyword.value, foundNodes)
    }

    if (foundNodes.length > 0) {
      // 高亮所有找到的节点
      foundNodes.forEach((node) => {
        if (node.materialId) {
          highlightedNodeIds.value.add(node.materialId)
        }
      })

      // 如果之前开启了过滤模式，重新应用过滤
      if (wasFiltering) {
        handleFilterToggle()
      }

      // 定位到第一个找到的节点
      const firstNode = foundNodes[0]
      if (firstNode?.materialId) {
        // 找到包含该节点的树
        const dataToUse = filteredBackwardResult.value || backwardResult.value
        for (let i = 0; i < dataToUse.length; i++) {
          const treeRef = backwardTreeRefs.value[i]
          const nodeData = dataToUse[i]
          if (treeRef && nodeData && expandToNode(treeRef, firstNode.materialId, [nodeData])) {
            // 滚动到节点
            setTimeout(() => {
              const nodeElement = document.querySelector(
                `.tree-node[data-material-id="${firstNode.materialId}"]`
              ) as HTMLElement
              if (nodeElement) {
                nodeElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
                // 添加高亮动画
                nodeElement.classList.add('node-locate-animation')
                setTimeout(() => {
                  nodeElement.classList.remove('node-locate-animation')
                }, 2000)
              }
              
            }, 300)
            break
          }
        }

        ElMessage.success(`找到 ${foundNodes.length} 个匹配的物料`)
      }
    } else {
      ElMessage.warning('未找到匹配的物料')
    }
  }
}

// 清空子物料搜索
const handleChildMaterialSearchClear = () => {
  highlightedNodeIds.value.clear()
  filterMatchedNodesOnly.value = false
  filteredForwardResult.value = null
  filteredBackwardResult.value = null
}

// 检查节点是否匹配搜索关键词
const isNodeMatched = (node: BomQueryNode, keyword: string): boolean => {
  if (!keyword || !keyword.trim()) {
    return false
  }
  const searchKey = keyword.toLowerCase().trim()
  const nodeCode = node.materialCode?.toLowerCase() || ''
  const nodeName = node.materialName?.toLowerCase() || ''
  return nodeCode.includes(searchKey) || nodeName.includes(searchKey)
}

// 过滤树节点，只保留匹配节点及其父节点路径
const filterTreeNodes = (node: BomQueryNode, matchedNodeIds: Set<number>): BomQueryNode | null => {
  if (!node.materialId) {
    return null
  }

  // 如果当前节点是匹配节点，保留整个节点
  if (matchedNodeIds.has(node.materialId)) {
    return node
  }

  // 如果子节点中有匹配的，保留当前节点但过滤子节点
  if (node.children && node.children.length > 0) {
    const filteredChildren: BomQueryNode[] = []
    for (const child of node.children) {
      const filteredChild = filterTreeNodes(child, matchedNodeIds)
      if (filteredChild) {
        filteredChildren.push(filteredChild)
      }
    }
    if (filteredChildren.length > 0) {
      // 创建新的节点，只包含过滤后的子节点
      return {
        ...node,
        children: filteredChildren,
      }
    }
  }

  return null
}

// 收集所有匹配的节点ID
const collectMatchedNodeIds = (node: BomQueryNode, keyword: string, matchedIds: Set<number>): void => {
  if (isNodeMatched(node, keyword)) {
    if (node.materialId) {
      matchedIds.add(node.materialId)
    }
  }
  if (node.children && node.children.length > 0) {
    for (const child of node.children) {
      collectMatchedNodeIds(child, keyword, matchedIds)
    }
  }
}

// 处理过滤开关切换
const handleFilterToggle = () => {
  if (!filterMatchedNodesOnly.value) {
    // 关闭过滤，显示全部
    filteredForwardResult.value = null
    filteredBackwardResult.value = null
  } else {
    // 开启过滤，只显示匹配节点及其父节点
    if (!childMaterialSearchKeyword.value || !childMaterialSearchKeyword.value.trim()) {
      ElMessage.warning('请先搜索子物料后再开启过滤')
      filterMatchedNodesOnly.value = false
      return
    }

    const keyword = childMaterialSearchKeyword.value.trim()
    const matchedIds = new Set<number>()

    // 收集匹配的节点ID
    if (queryForm.value.queryType === 'forward' && forwardResult.value) {
      collectMatchedNodeIds(forwardResult.value, keyword, matchedIds)
      if (matchedIds.size > 0) {
        let filtered = filterTreeNodes(forwardResult.value, matchedIds)
        if (filtered && queryForm.value.requiredQuantity !== null) {
          filtered = calculateQuantities(filtered, queryForm.value.requiredQuantity)
        }
        filteredForwardResult.value = filtered
      } else {
        filteredForwardResult.value = null
        ElMessage.warning('没有找到匹配的节点')
        filterMatchedNodesOnly.value = false
      }
    } else if (queryForm.value.queryType === 'backward' && backwardResult.value.length > 0) {
      for (const rootNode of backwardResult.value) {
        collectMatchedNodeIds(rootNode, keyword, matchedIds)
      }
      if (matchedIds.size > 0) {
        filteredBackwardResult.value = backwardResult.value
          .map((node) => {
            const filtered = filterTreeNodes(node, matchedIds)
            // 反查时不需要计算用量
            return filtered
          })
          .filter((node) => node !== null) as BomQueryNode[]
      } else {
        filteredBackwardResult.value = []
        ElMessage.warning('没有找到匹配的节点')
        filterMatchedNodesOnly.value = false
      }
    }
  }
}

// 格式化数字显示
const formatNumber = (num: number | null | undefined): string => {
  if (num === null || num === undefined) {
    return '0'
  }
  // 如果数字很小，使用科学计数法
  if (Math.abs(num) < 0.000001 && num !== 0) {
    return num.toExponential(2)
  }
  // 如果数字很大，使用千分位分隔符
  if (Math.abs(num) >= 1000) {
    return num.toLocaleString('zh-CN', { maximumFractionDigits: 6 })
  }
  // 普通数字，最多保留6位小数
  return num.toFixed(6).replace(/\.?0+$/, '')
}

// 计算子物料的需求数量
// 计算公式：子物料需求 = (父物料需求 / denominator) * numerator * (1 + scrapRate / 100)
const calculateChildQuantity = (
  parentQuantity: number,
  numerator: number | null,
  denominator: number | null,
  scrapRate: number | null
): number => {
  if (!numerator || !denominator || denominator === 0) {
    return 0
  }
  
  let quantity = (parentQuantity / denominator) * numerator
  
  // 考虑损耗率
  if (scrapRate !== null && scrapRate !== undefined && scrapRate > 0) {
    quantity = quantity * (1 + scrapRate / 100)
  }
  
  return quantity
}

// 递归计算所有子物料的需求数量
const calculateQuantities = (node: BomQueryNode, parentQuantity: number): BomQueryNode => {
  // 创建新节点，避免修改原始数据
  const newNode: BomQueryNode = {
    ...node,
    parentQuantity: parentQuantity,
    calculatedQuantity: null,
    children: [],
  }
  
  // 如果是根节点，直接使用输入的用量
  if (!node.numerator || !node.denominator) {
    newNode.calculatedQuantity = parentQuantity
  } else {
    // 计算当前节点的需求数量
    newNode.calculatedQuantity = calculateChildQuantity(
      parentQuantity,
      node.numerator,
      node.denominator,
      node.scrapRate ?? null
    )
  }
  
  // 递归计算子节点
  if (node.children && node.children.length > 0) {
    const childQuantity = newNode.calculatedQuantity || parentQuantity
    newNode.children = node.children.map((child) =>
      calculateQuantities(child, childQuantity)
    )
  }
  
  return newNode
}

// 监听需求用量变化，自动重新计算
watch(
  () => queryForm.value.requiredQuantity,
  (newQuantity) => {
    if (queryForm.value.queryType === 'forward' && originalForwardResult.value) {
      // 基于原始数据重新计算
      if (newQuantity !== null && newQuantity !== undefined) {
        const calculated = calculateQuantities(
          JSON.parse(JSON.stringify(originalForwardResult.value)), 
          newQuantity
        )
        
        if (filterMatchedNodesOnly.value && filteredForwardResult.value) {
          // 如果开启了过滤，需要重新过滤并计算
          const keyword = childMaterialSearchKeyword.value.trim()
          const matchedIds = new Set<number>()
          collectMatchedNodeIds(calculated, keyword, matchedIds)
          if (matchedIds.size > 0) {
            filteredForwardResult.value = calculateQuantities(
              filterTreeNodes(calculated, matchedIds)!,
              newQuantity
            )
          }
        } else {
          forwardResult.value = calculated
        }
      } else {
        // 清空用量，恢复原始数据
        forwardResult.value = JSON.parse(JSON.stringify(originalForwardResult.value))
        if (filterMatchedNodesOnly.value) {
          // 如果开启了过滤，需要重新过滤
          const keyword = childMaterialSearchKeyword.value.trim()
          const matchedIds = new Set<number>()
          collectMatchedNodeIds(forwardResult.value!, keyword, matchedIds)
          if (matchedIds.size > 0) {
            filteredForwardResult.value = filterTreeNodes(forwardResult.value!, matchedIds)
          }
        }
      }
    }
  }
)

// 获取节点提示内容
const getNodeTooltipContent = (data: BomQueryNode): string => {
  const parts: string[] = []
  
  parts.push(`<div style="font-weight: bold; margin-bottom: 4px;">${data.materialCode} - ${data.materialName}</div>`)
  
  if (data.materialSpecification) {
    parts.push(`<div style="margin-bottom: 2px;">型号: ${data.materialSpecification}</div>`)
  }
  
  if (data.materialGroupName) {
    parts.push(`<div style="margin-bottom: 2px;">物料组: ${data.materialGroupName}</div>`)
  }
  
  if (data.bomVersion) {
    parts.push(`<div style="margin-bottom: 2px;">BOM版本: ${data.bomVersion}${data.bomName ? ` (${data.bomName})` : ''}</div>`)
  }
  
  if (data.sequence !== null && data.sequence !== undefined) {
    parts.push(`<div style="margin-bottom: 2px;">序号: ${data.sequence}</div>`)
  }
  
  if (data.numerator && data.denominator) {
    parts.push(`<div style="margin-bottom: 2px;">用量: ${data.numerator}/${data.denominator}</div>`)
  }
  
  if (data.scrapRate) {
    parts.push(`<div style="margin-bottom: 2px;">损耗率: ${data.scrapRate}%</div>`)
  }
  
  if (data.childUnitName) {
    parts.push(`<div style="margin-bottom: 2px;">单位: ${data.childUnitName}</div>`)
  }
  
  if (data.calculatedQuantity !== null && data.calculatedQuantity !== undefined) {
    parts.push(`<div style="margin-bottom: 2px; color: #f56c6c; font-weight: 600;">需求: ${formatNumber(data.calculatedQuantity)}${data.childUnitName ? ` ${data.childUnitName}` : ''}</div>`)
  }
  
  return parts.join('')
}

</script>

<style scoped>
.bom-query-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.bom-query-container :deep(.el-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.bom-query-container :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.query-form {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.query-result-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.query-result {
  flex: 1;
  overflow: auto;
  padding-top: 60px; /* 为固定搜索框留出空间 */
}

.tree-container {
  position: relative;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.node-label {
  font-weight: 500;
  color: #303133;
}

.node-bom-version {
  color: #409eff;
  font-weight: 500;
}

.node-sequence {
  color: #909399;
  font-size: 12px;
}

.node-quantity {
  color: #67c23a;
  font-size: 12px;
}

.node-scrap {
  color: #e6a23c;
  font-size: 12px;
}

.node-unit {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

.node-calculated-quantity {
  color: #f56c6c;
  font-size: 13px;
  font-weight: 600;
  margin-left: 8px;
}

.result-header {
  margin-bottom: 20px;
}

.material-search-box {
  display: flex;
  gap: 10px;
  align-items: center;
}

.fixed-search-box {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background-color: #fff;
  padding: 10px 20px;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  width: 100%;
  box-sizing: border-box;
}

.node-highlighted {
  background-color: #fff3cd !important;
  padding: 4px 8px;
  border-radius: 4px;
  border: 2px solid #ffc107;
  font-weight: 600;
}

.node-locate-animation {
  animation: locate-pulse 2s ease-in-out;
}

@keyframes locate-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(255, 193, 7, 0.7);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(255, 193, 7, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(255, 193, 7, 0);
  }
}
</style>

