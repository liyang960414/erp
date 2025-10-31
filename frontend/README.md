# 前端项目文档

ERP系统前端，基于Vue 3 + TypeScript + Element Plus开发。

## 技术栈

- **Vue 3** - 渐进式JavaScript框架（Composition API）
- **TypeScript** - 类型安全的JavaScript
- **Element Plus** - 基于Vue 3的组件库
- **Vue Router** - 官方路由管理器
- **Pinia** - 状态管理库
- **Axios** - HTTP客户端
- **Vite** - 前端构建工具

## 项目结构

```
frontend/
├── src/
│   ├── api/              # API接口封装
│   │   └── auth.ts       # 认证相关API
│   ├── assets/           # 静态资源
│   ├── components/       # 公共组件
│   ├── layouts/          # 布局组件
│   │   └── MainLayout.vue  # 主布局
│   ├── router/           # 路由配置
│   │   └── index.ts      # 路由定义和守卫
│   ├── stores/           # Pinia状态管理
│   │   ├── auth.ts       # 认证store
│   │   └── counter.ts    # 示例store
│   ├── types/            # TypeScript类型定义
│   │   └── auth.ts       # 认证相关类型
│   ├── utils/            # 工具函数
│   │   └── request.ts    # Axios请求封装
│   ├── views/            # 页面组件
│   │   ├── LoginView.vue    # 登录页
│   │   ├── HomeView.vue     # 首页
│   │   ├── UserListView.vue # 用户列表
│   │   └── ...             # 其他页面
│   ├── App.vue           # 根组件
│   └── main.ts           # 应用入口
├── package.json          # 项目配置
└── vite.config.ts        # Vite配置
```

## 功能特性

### 已实现功能
- ✅ 用户登录和注销
- ✅ JWT身份验证
- ✅ 基于角色的权限控制（RBAC）
- ✅ 路由守卫
- ✅ API请求拦截
- ✅ 响应式布局
- ✅ Element Plus组件集成

### 权限控制

系统支持基于角色的权限控制：

1. **路由级权限** - 在路由meta中定义`roles`
2. **组件级权限** - 使用`v-if`配合store的`hasRole`方法
3. **API级权限** - 由后端控制

示例：
```typescript
// 路由权限
{
  path: '/users/list',
  meta: { roles: ['ADMIN'] }
}

// 组件权限
<el-menu-item v-if="authStore.hasRole('ADMIN')">
  管理员菜单
</el-menu-item>
```

## 快速开始

### 安装依赖

```bash
cd frontend
npm install
```

### 开发模式

```bash
# 使用开发环境配置（连接本地后端）
npm run dev

# 使用测试环境配置（连接测试服务器）
npm run dev:staging
```

访问 http://localhost:5173

### 构建不同环境版本

```bash
# 构建开发版本
npm run build

# 构建测试版本
npm run build:staging

# 构建生产版本
npm run build:production
```

构建后可在 `dist` 目录找到打包好的文件。

### 类型检查

```bash
npm run type-check
```

### 代码检查

```bash
npm run lint
```

## 使用说明

### 登录

1. 访问 http://localhost:5173
2. 自动跳转到登录页面
3. 使用默认账户登录：
   - 用户名：admin
   - 密码：admin123

### 认证流程

```
登录 → 获取JWT Token → 存储到localStorage → 
请求时自动携带Token → 过期/无效时自动跳转登录页
```

### 状态管理

使用Pinia进行状态管理：

```typescript
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// 检查是否登录
if (authStore.isAuthenticated) {
  console.log('已登录')
}

// 检查角色
if (authStore.hasRole('ADMIN')) {
  console.log('是管理员')
}

// 检查权限
if (authStore.hasPermission('user:write')) {
  console.log('有用户写权限')
}

// 登出
await authStore.logout()
```

### API调用

使用封装的request工具：

```typescript
import request from '@/utils/request'

// GET请求
const data = await request.get('/users/me')

// POST请求
const result = await request.post('/auth/login', { username, password })
```

### 路由导航

```typescript
import { useRouter } from 'vue-router'

const router = useRouter()

// 编程式导航
router.push('/users/list')

// 带参数
router.push({ name: 'userList', params: { id: 1 } })
```

## 配置

### 环境变量配置

项目支持多环境配置，通过 `.env` 文件管理不同环境的配置：

#### 环境文件说明

- `.env.development` - 开发环境（本地开发）
- `.env.staging` - 测试环境（测试服务器）
- `.env.production` - 生产环境（生产服务器）
- `.env.local` - 本地覆盖配置（会被 git 忽略）
- `.env.example` - 配置示例文件

#### 配置示例

**开发环境 (`.env.development`)**
```env
VITE_API_BASE_URL=http://localhost:8080/api
VITE_APP_TITLE=ERP系统 - 开发环境
VITE_DEBUG=true
```

**测试环境 (`.env.staging`)**
```env
VITE_API_BASE_URL=http://43.161.248.212:8080/api
VITE_APP_TITLE=ERP系统 - 测试环境
VITE_DEBUG=false
```

**生产环境 (`.env.production`)**
```env
VITE_API_BASE_URL=https://api.yourdomain.com/api
VITE_APP_TITLE=ERP系统
VITE_DEBUG=false
```

#### 使用不同环境

**开发模式**
```bash
# 使用开发环境配置
npm run dev

# 使用测试环境配置
npm run dev:staging
```

**构建命令**
```bash
# 构建开发版本
npm run build

# 构建测试版本
npm run build:staging

# 构建生产版本
npm run build:production
```

#### API地址配置

API地址在 `src/utils/request.ts` 中自动读取环境变量：

```typescript
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 10000,
})
```

## 开发指南

### 添加新页面

1. 在`src/views/`创建Vue组件
2. 在`src/router/index.ts`添加路由
3. 使用`MainLayout`包裹页面

示例：
```typescript
{
  path: '/new-page',
  name: 'newPage',
  component: () => import('@/views/NewPageView.vue'),
  meta: { title: '新页面', requiresAuth: true }
}
```

### 添加新API

在`src/api/`创建新的API文件：

```typescript
import request from '@/utils/request'

export const myApi = {
  getData: () => request.get('/data'),
  createData: (data: any) => request.post('/data', data),
}
```

### 添加新Store

在`src/stores/`创建新的store：

```typescript
import { defineStore } from 'pinia'

export const useMyStore = defineStore('my', () => {
  const count = ref(0)
  
  function increment() {
    count.value++
  }
  
  return { count, increment }
})
```

### 使用Element Plus组件

```vue
<template>
  <el-button type="primary" @click="handleClick">
    按钮
  </el-button>
  
  <el-icon><User /></el-icon>
</template>

<script setup lang="ts">
import { User } from '@element-plus/icons-vue'
</script>
```

## 组件说明

### LoginView.vue
登录页面，包含用户名和密码输入框。

### MainLayout.vue
主布局组件，包含：
- 顶部导航栏
- 侧边栏菜单
- 用户信息下拉菜单
- 内容区域

### HomeView.vue
首页，显示用户基本信息和欢迎内容。

## 路由守卫

系统实现了完整的路由守卫：

1. **认证守卫** - 检查用户是否登录
2. **角色守卫** - 检查用户是否有权限访问
3. **自动跳转** - 未登录跳转登录页，已登录访问登录页跳转首页

## 错误处理

全局错误处理在`request.ts`中实现：

- 400: 参数错误
- 401: 未授权，自动跳转登录
- 403: 无权限
- 404: 资源不存在
- 500: 服务器错误

## 注意事项

1. **后端服务必须运行** - 前端依赖后端API
2. **CORS配置** - 确保后端允许前端域名访问
3. **Token过期** - Token过期自动清除并跳转登录页
4. **权限检查** - 前端权限检查是辅助性的，真正的权限控制在后端

## 常见问题

### 1. 登录后还是跳回登录页

**原因**: Token未正确存储或后端认证失败
**解决**: 检查浏览器localStorage中的token，检查后端日志

### 2. 网络请求失败

**原因**: 后端未启动或API地址配置错误
**解决**: 确认后端运行在8080端口，检查`.env`配置

### 3. 权限不足

**原因**: 用户角色不符合要求
**解决**: 使用管理员账户登录（admin/admin123）

### 4. 样式异常

**原因**: Element Plus样式未加载
**解决**: 确认`main.ts`中导入了Element Plus样式

## 下一步

- [ ] 完善用户管理页面
- [ ] 实现商品管理功能
- [ ] 实现订单管理功能
- [ ] 添加数据可视化
- [ ] 实现文件上传
- [ ] 添加国际化支持

## 参考资料

- [Vue 3 官方文档](https://cn.vuejs.org/)
- [Element Plus 文档](https://element-plus.org/zh-CN/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)
- [Vue Router 文档](https://router.vuejs.org/zh/)
