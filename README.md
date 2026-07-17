# 智慧交通监控预警系统

一个基于 Vue 3 和 Vite 开发的智慧交通监控预警前端项目。系统面向城市交通管理场景，围绕监控设备、实时监控、交通异常事件、用户管理和智能问答等模块，提供交通运行状态展示、拥堵预警模拟、事件记录管理和账号管理能力。
本项目目前以前端演示为主，使用静态示例数据和浏览器 `localStorage` 保存部分用户数据，适合课程设计、前端项目展示、系统原型演示以及后续对接后端接口继续扩展。

数据集下载链接：
https://www.kaggle.com/datasets/sylviestandta/trafficwarning

## 项目特点

- 使用 Vue 3 Composition API 构建页面和交互逻辑。
- 使用 Vue Router 实现登录页、首页、控制台和业务模块之间的页面切换。
- 使用 Vite 作为开发和构建工具，启动速度快，开发体验轻量。
- 内置登录、注册、路由守卫和退出登录流程。
- 包含用户管理、监控设备管理、实时监控、交通异常记录、AI 智能助手等完整业务模块。
- 页面采用响应式布局，支持常见桌面端和较窄屏幕展示。
- 无后端依赖即可运行，方便本地演示和答辩展示。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 前端框架 | Vue 3 |
| 构建工具 | Vite |
| 路由管理 | Vue Router 4 |
| 状态与数据 | Vue `ref` / `computed` / `watch`，浏览器 `localStorage` |
| 样式方案 | Vue 单文件组件内 `scoped CSS` |
| 包管理 | npm |

## 功能模块

### 1. 登录与注册

对应组件：`src/components/LoginForm.vue`

- 支持用户登录和用户注册。
- 默认演示账号：`admin / admin123`。
- 登录成功后写入本地 `token`，并跳转到系统首页。
- 注册成功后自动登录，新用户默认角色为普通用户。
- 账号信息存储在浏览器 `localStorage` 中。
- 禁用状态账号无法登录。
- 测试登录账号：admin / admin123
：
### 2. 首页

对应组件：`src/components/FirstPage.vue`

- 展示系统名称、当前日期、核心标签和概览数据。
- 提供快速入口，可跳转至实时监控、监控设备、交通异常记录、用户管理等页面。
- 展示系统建设目标、应用价值和示例性能指标。

### 3. 系统控制台

对应组件：`src/components/Dashboard.vue`

- 展示用户总数、摄像头总数、交通记录总数、在线摄像头等统计卡片。
- 展示摄像头在线/离线状态分布。
- 预留交通记录趋势图区域，后续可接入真实图表库或后端统计接口。

### 4. 用户管理

对应组件：`src/components/UserManagement.vue`

- 支持用户列表展示、查询、分页。
- 支持新增用户，新增用户默认密码为 `123456`。
- 支持编辑用户信息、重置密码、启用/禁用账号、删除用户。
- 用户数据保存至 `localStorage` 的 `traffic_users` 字段。

### 5. 监控设备管理

对应组件：`src/components/DeviceManagement.vue`

- 展示监控设备列表，包括设备编号、设备名称、安装位置、RTSP 地址、创建者、状态和创建时间。
- 支持按设备名称、安装位置、设备状态筛选。
- 支持新增、编辑、删除监控设备。
- 支持模拟设备状态检测，随机更新在线/离线状态。

### 6. 实时监控

对应组件：`src/components/RealTimeMonitor.vue`

- 左侧展示当前选中监控点的视频占位区域。
- 右侧展示交通事件记录列表。
- 支持点击不同监控点切换实时监控详情。
- 根据平均车速计算拥堵等级：
  - I 级：畅通
  - II 级：基本通畅
  - III 级：轻度拥堵
  - IV 级：中度拥堵
  - V 级：严重拥堵
- 展示车辆数量、平均速度、异常行为检测结果等信息。

### 7. 交通异常事件记录

对应组件：`src/components/RoadStatus.vue`

- 管理拥堵预警、异常停车、车流量异常等交通事件。
- 支持按监控点/道路关键字、处理状态、时间范围筛选。
- 支持查看事件详情弹窗。
- 支持标记已处理、设为未处理和删除记录。
- 支持分页展示。

### 8. AI 智能助手

对应组件：`src/components/AiAssistant.vue`

- 提供自然语言问答式交互界面。
- 内置规则模拟回答，可回答整体交通情况、车辆统计、拥堵最严重路口、指定路口状态等问题。
- 提供快捷问题按钮，便于演示。
- 当前为前端规则模拟，后续可对接大模型接口或后端智能分析服务。

## 路由说明

路由配置文件：`src/router/index.js`

| 路径 | 路由名称 | 页面 |
| --- | --- | --- |
| `/login` | `Login` | 登录/注册页 |
| `/` | `FirstPage` | 系统首页 |
| `/dashboard` | `Dashboard` | 系统控制台 |
| `/users` | `UserManagement` | 用户管理 |
| `/devices` | `DeviceManagement` | 监控设备管理 |
| `/realtime` | `RealTimeMonitor` | 实时监控 |
| `/road-status` | `RoadStatus` | 交通异常事件记录 |
| `/assistant` | `AiAssistant` | AI 智能助手 |

除 `/login` 外，其余业务页面均配置了 `requiresAuth`。如果浏览器本地没有 `token`，访问业务页面时会自动跳转到登录页。

## 目录结构

```text
traffic-warning-system/
├── public/
│   └── favicon.ico
├── src/
│   ├── assets/
│   │   ├── base.css
│   │   ├── logo.svg
│   │   ├── main.css
│   │   └── photo.png
│   ├── components/
│   │   ├── AiAssistant.vue
│   │   ├── Dashboard.vue
│   │   ├── DeviceManagement.vue
│   │   ├── FirstPage.vue
│   │   ├── Header.vue
│   │   ├── LoginForm.vue
│   │   ├── RealTimeMonitor.vue
│   │   ├── RoadStatus.vue
│   │   ├── Sidebar.vue
│   │   ├── StatCard.vue
│   │   └── UserManagement.vue
│   ├── router/
│   │   └── index.js
│   ├── App.vue
│   └── main.js
├── index.html
├── package.json
├── package-lock.json
├── vite.config.js
└── README.md
```

## 环境要求

项目 `package.json` 中声明的 Node.js 版本要求为：

```text
^20.19.0 || >=22.12.0
```

建议使用 Node.js 20.19.0 及以上版本，或 Node.js 22.12.0 及以上版本。

查看本机版本：

```sh
node -v
npm -v
```

## 安装与运行

进入项目目录：

```sh
cd traffic-warning-system
```

安装依赖：

```sh
npm install
```

启动开发服务器：

```sh
npm run dev
```

启动成功后，浏览器访问终端显示的本地地址，通常为：

```text
http://localhost:5173/
```

## 打包与预览

生产环境打包：

```sh
npm run build
```

本地预览打包结果：

```sh
npm run preview
```

## 演示账号

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 管理员 |
| `test` | `123456` | 普通用户 |

说明：

- 登录页默认会提供 `admin / admin123` 的演示提示。
- 用户管理模块中新增的用户默认密码为 `123456`。
- 用户数据保存在浏览器本地，不同浏览器或清除缓存后数据可能不同。

## 本地数据说明

当前项目没有接入后端数据库，主要数据来源如下：

| 数据 | 来源 |
| --- | --- |
| 登录 token | `localStorage.token` |
| 当前用户 | `localStorage.traffic_current_user` |
| 用户列表 | `localStorage.traffic_users` |
| 设备列表 | 组件内静态示例数据 |
| 实时监控数据 | 组件内静态示例数据 |
| 交通事件记录 | 组件内静态示例数据 |
| AI 助手回答 | 前端规则匹配生成 |

如需恢复初始用户数据，可在浏览器开发者工具中清除相关 `localStorage` 字段后刷新页面。

## 使用流程

1. 启动项目并打开浏览器页面。
2. 使用 `admin / admin123` 登录系统。
3. 在首页查看系统概览，并通过快捷入口进入各模块。
4. 在系统控制台查看统计卡片和摄像头状态分布。
5. 在用户管理中新增、查询、编辑、禁用或删除用户。
6. 在监控设备中维护设备信息，并模拟检测设备在线状态。
7. 在实时监控中切换监控点，查看车辆数量、平均速度和拥堵等级。
8. 在交通异常记录中筛选事件、查看详情并更新处理状态。
9. 在 AI 智能助手中输入问题，体验交通状态问答。

## 可扩展方向

- 接入后端接口，实现用户、设备、交通事件的持久化管理。
- 接入真实 RTSP/HTTP 视频流，将实时监控占位区替换为视频播放器。
- 接入目标检测、车流统计、速度估计和拥堵判断模型。
- 引入 ECharts 等图表库，完善控制台趋势图、状态分布图和统计报表。
- 增加基于角色的权限控制，例如管理员和普通用户展示不同菜单与操作权限。
- 增加操作日志、事件处理流程、预警通知和消息推送。
- 将 AI 智能助手接入真实大模型接口，实现更灵活的交通分析问答。

## 常见问题

### 登录后刷新页面会退出吗？

不会。登录状态通过 `localStorage.token` 保存。只要本地 token 未被清除，刷新页面仍可访问业务页面。

### 为什么设备和交通事件刷新后会恢复默认数据？

当前设备和交通事件数据写在组件内部，属于演示静态数据，没有持久化到后端或本地存储。后续可以统一改为接口数据或 `localStorage` 数据。

### 为什么交通事件截图来自外部图片地址？

`RoadStatus.vue` 中使用了 `https://picsum.photos/seed/rec1/260/150` 作为演示截图地址。如果网络不可用，图片可能无法显示；可替换为 `src/assets` 中的本地图片资源。

### AI 智能助手是真正调用 AI 接口了吗？

当前不是。该模块通过前端规则匹配模拟回答，适合原型演示。后续可以对接后端服务或大模型 API。

## 项目脚本

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 构建生产环境文件 |
| `npm run preview` | 本地预览生产构建结果 |

## 开发说明

- 全局入口文件为 `src/main.js`。
- 根组件为 `src/App.vue`，负责根据当前路由判断是否展示登录页布局或业务系统布局。
- 顶部导航位于 `src/components/Header.vue`。
- 左侧菜单位于 `src/components/Sidebar.vue`。
- 路由守卫位于 `src/router/index.js`，通过检查 `localStorage.token` 控制页面访问权限。
- 页面样式主要写在各组件的 `<style scoped>` 中，公共样式位于 `src/assets/main.css` 和 `src/assets/base.css`。

## 许可证

本项目用于课程设计、学习实践和前端原型展示。如需用于生产环境，请补充真实后端接口、权限校验、数据安全和异常处理机制。
