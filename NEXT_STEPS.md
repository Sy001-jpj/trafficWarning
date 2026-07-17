# 智慧交通监控预警系统下一步规划文档

## 1. 规划目标

当前项目已经完成 Vue 前端原型，包含登录注册、首页、系统控制台、用户管理、监控设备管理、实时监控、交通异常记录和 AI 智能助手等页面。下一步建设目标是将项目从“前端演示系统”升级为“前后端分离的完整智慧交通监控预警系统”。

后续系统建议采用混合后端架构：

```text
Vue 前端
  |
  | HTTP / WebSocket
  v
Java Spring Boot 业务后端
  |
  | HTTP / MQ
  v
Python FastAPI 模型服务
  |
  v
交通视频分析模型 / 拥堵预测模型
```

整体目标包括：

- 将前端静态数据替换为真实后端接口数据。
- 使用 Java 后端完成用户、设备、事件、统计、权限等业务管理。
- 使用 Python FastAPI 承载模型推理能力，负责车辆检测、车流统计、拥堵判断和异常行为识别。
- 使用 MySQL 保存核心业务数据。
- 后期支持实时监控数据推送、交通预警记录生成和智能分析问答。

## 2. 总体架构规划

### 2.1 前端层

前端继续使用当前 Vue 3 + Vite 项目，主要负责：

- 页面展示。
- 用户交互。
- 调用 Java 后端接口。
- 展示实时监控结果。
- 展示交通事件、设备状态和统计图表。

前端原则上不直接访问数据库，也不直接处理复杂模型推理逻辑。

### 2.2 Java 业务后端

Java 后端建议使用 Spring Boot，作为系统主业务入口，负责：

- 登录认证与权限控制。
- 用户管理。
- 监控设备管理。
- 交通异常事件管理。
- 实时分析结果管理。
- 系统控制台统计。
- 操作日志记录。
- 调用 Python 模型服务。
- 向前端提供统一 API。

Java 后端是系统的核心业务层，建议前端主要访问 Java 后端，而不是直接访问 Python 模型服务。

### 2.3 Python 模型服务

Python 后端建议使用 FastAPI，作为独立 AI 模型服务，负责：

- 接收视频帧、图片或 RTSP 流地址。
- 调用车辆检测模型。
- 统计机动车、非机动车数量。
- 估算平均速度。
- 判断拥堵等级。
- 识别异常停车、低速占道、排队溢出等事件。
- 将结构化结果返回给 Java 后端。

Python 服务只负责模型推理和分析结果生成，不建议承担用户管理、权限管理等业务职责。

### 2.4 数据存储层

建议使用：

```text
MySQL：保存用户、角色、设备、事件、分析结果等结构化数据
Redis：缓存实时状态、token 黑名单、热点数据，可后期加入
本地文件或对象存储：保存检测截图、视频片段等文件
```

第一阶段可以只使用 MySQL，Redis 和对象存储可以作为后续增强项。

## 3. 推荐项目结构

建议最终拆分为三个子项目：

```text
traffic-warning-system/
├── traffic-warning-frontend/      Vue 前端项目
├── traffic-warning-backend/       Java Spring Boot 业务后端
└── traffic-warning-ai/            Python FastAPI 模型服务
```

如果暂时不想调整目录，也可以在当前仓库下新增：

```text
traffic-warning-system/
├── frontend/
├── backend-java/
└── backend-python/
```

推荐后续命名统一，避免前端项目和总项目目录重名导致路径混乱。

## 4. Java 业务后端建设规划

### 4.1 基础框架

需要搭建 Spring Boot 后端基础工程，建议包含以下能力：

- Spring Web：提供 REST API。
- Spring Security：实现登录认证和接口权限控制。
- JWT：实现无状态登录。
- MyBatis Plus 或 Spring Data JPA：操作数据库。
- MySQL Driver：连接 MySQL 数据库。
- Validation：请求参数校验。
- Lombok：简化实体类和 DTO 编写。
- Swagger / Knife4j：生成接口文档。
- WebSocket：后续推送实时交通状态。
- 全局异常处理。
- 统一接口返回格式。
- 跨域配置。

统一响应格式示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 4.2 用户认证模块

目标：替换当前前端 `localStorage` 模拟登录逻辑。

需要实现：

- 用户注册。
- 用户登录。
- JWT token 生成。
- 当前用户信息获取。
- 密码加密存储。
- 用户启用/禁用状态校验。
- 退出登录。

建议接口：

```text
POST /api/auth/login
POST /api/auth/register
GET  /api/auth/me
POST /api/auth/logout
```

### 4.3 用户管理模块

目标：替换 `UserManagement.vue` 中的本地用户数据。

需要实现：

- 用户列表分页查询。
- 按用户名、姓名、角色、状态筛选。
- 新增用户。
- 编辑用户。
- 删除用户。
- 启用/禁用用户。
- 重置密码。
- 角色分配。

建议接口：

```text
GET    /api/users
POST   /api/users
GET    /api/users/{id}
PUT    /api/users/{id}
DELETE /api/users/{id}
PATCH  /api/users/{id}/status
PATCH  /api/users/{id}/password/reset
```

### 4.4 监控设备管理模块

目标：替换 `DeviceManagement.vue` 中的静态设备数据。

需要实现：

- 设备新增。
- 设备编辑。
- 设备删除。
- 设备分页查询。
- 按名称、位置、状态筛选。
- 设备状态更新。
- RTSP 地址保存。
- 设备与道路/路口绑定。

建议接口：

```text
GET    /api/devices
POST   /api/devices
GET    /api/devices/{id}
PUT    /api/devices/{id}
DELETE /api/devices/{id}
PATCH  /api/devices/{id}/status
POST   /api/devices/check-status
```

### 4.5 交通异常事件模块

目标：替换 `RoadStatus.vue` 中的静态事件记录。

需要实现：

- 事件列表分页查询。
- 按监控点、道路、状态、时间范围筛选。
- 事件详情查看。
- 新增事件。
- 标记已处理。
- 设为未处理。
- 删除事件。
- 保存检测截图路径。

建议事件类型：

```text
拥堵预警
异常停车
车流量持续偏大
低速占道
排队溢出
设备离线
```

建议接口：

```text
GET    /api/events
POST   /api/events
GET    /api/events/{id}
PATCH  /api/events/{id}/status
DELETE /api/events/{id}
```

### 4.6 实时监控模块

目标：让 `RealTimeMonitor.vue` 展示真实或模拟后端数据。

需要实现：

- 查询所有在线设备。
- 查询某设备最新分析结果。
- 查询车辆数量、平均速度和拥堵等级。
- 查询异常行为检测结果。
- 后期通过 WebSocket 推送最新数据。

建议接口：

```text
GET /api/monitor/devices
GET /api/monitor/devices/{deviceId}/latest
GET /api/monitor/realtime
WS  /ws/monitor
```

实时数据结构示例：

```json
{
  "deviceId": "C001",
  "deviceName": "隧道入口监控",
  "location": "城市隧道入口处",
  "status": "在线",
  "motorCount": 11,
  "nonMotorCount": 9,
  "avgSpeed": 6.5,
  "congestionLevel": "V",
  "congestionLabel": "严重拥堵",
  "events": ["疑似排队溢出", "低速长时间占道"],
  "lastUpdate": "2026-05-28 22:30:00"
}
```

### 4.7 系统控制台统计模块

目标：替换 `Dashboard.vue` 中的静态统计数据。

需要实现：

- 用户总数。
- 设备总数。
- 在线设备数。
- 离线设备数。
- 今日交通事件数。
- 未处理事件数。
- 近 7 天事件趋势。
- 摄像头状态分布。
- 拥堵等级分布。

建议接口：

```text
GET /api/dashboard/summary
GET /api/dashboard/device-status
GET /api/dashboard/event-trend
GET /api/dashboard/congestion-levels
```

### 4.8 AI 智能助手模块

目标：替换 `AiAssistant.vue` 中的前端规则回答。

第一阶段可以由 Java 后端基于数据库生成回答：

- 当前车辆总数。
- 当前平均速度。
- 当前最拥堵路口。
- 当前未处理事件数。
- 当前离线设备。

第二阶段可由 Java 调用 Python 模型服务或大模型服务。

建议接口：

```text
POST /api/assistant/chat
```

请求示例：

```json
{
  "message": "当前哪个路口最拥堵？"
}
```

响应示例：

```json
{
  "reply": "当前拥堵最严重的是人民路-解放路南进口，拥堵指数为 82，平均车速约 12 km/h。"
}
```

## 5. Python FastAPI 模型服务规划

### 5.1 基础框架

建议依赖：

```text
FastAPI
Uvicorn
Pydantic
OpenCV
NumPy
Pillow
PyTorch 或 Ultralytics YOLO
```

如果暂时没有真实模型，可以先实现模拟接口，让 Java 与 Python 的调用链路先跑通。

### 5.2 模型服务接口

建议接口：

```text
GET  /model/health
POST /model/analyze-image
POST /model/analyze-frame
POST /model/analyze-stream
```

`/model/analyze-image` 用于图片分析。

`/model/analyze-frame` 用于单帧视频分析。

`/model/analyze-stream` 用于 RTSP 视频流分析。

### 5.3 模型返回结果

返回结构建议统一：

```json
{
  "deviceId": "C001",
  "motorCount": 16,
  "nonMotorCount": 4,
  "avgSpeed": 12.5,
  "congestionLevel": "IV",
  "congestionLabel": "中度拥堵",
  "events": ["车流量持续偏大", "低速占道"],
  "snapshotPath": "/files/snapshots/C001_20260528_223000.jpg",
  "analyzedAt": "2026-05-28 22:30:00"
}
```

Java 后端拿到该结果后，负责：

- 保存分析结果。
- 判断是否需要生成交通异常事件。
- 更新设备最新状态。
- 推送给前端页面。

## 6. 数据库设计规划

### 6.1 最小可行表

第一阶段至少需要：

```text
sys_user              用户表
traffic_device        监控设备表
traffic_event         交通异常事件表
traffic_analysis      实时分析结果表
```

### 6.2 推荐完整表

后续可扩展为：

```text
sys_user              用户表
sys_role              角色表
sys_user_role         用户角色关联表
traffic_device        监控设备表
traffic_road          道路/路口表
traffic_event         交通异常事件表
traffic_analysis      实时分析结果表
traffic_snapshot      检测截图/视频片段表
operation_log         操作日志表
```

### 6.3 核心表字段建议

#### sys_user

```text
id
username
password
display_name
phone
email
role
status
created_at
updated_at
```

#### traffic_device

```text
id
device_code
device_name
location
road_id
rtsp_url
status
creator_id
created_at
updated_at
```

#### traffic_event

```text
id
device_id
road_name
event_type
event_level
status
vehicles
avg_speed
description
snapshot_url
detected_at
handled_at
created_at
updated_at
```

#### traffic_analysis

```text
id
device_id
motor_count
non_motor_count
avg_speed
flow
congestion_index
congestion_level
congestion_label
events
snapshot_url
analyzed_at
created_at
```

## 7. 前端改造规划

当前前端大量数据写在组件中，后续需要改为请求后端接口。

### 7.1 新增 API 目录

建议新增：

```text
src/api/
├── request.js
├── auth.js
├── user.js
├── device.js
├── event.js
├── dashboard.js
├── monitor.js
└── assistant.js
```

### 7.2 引入 Axios

建议使用 Axios 统一请求：

```sh
npm install axios
```

`request.js` 中统一配置：

- baseURL。
- 请求超时时间。
- token 自动携带。
- 响应错误处理。
- 401 自动跳转登录页。

### 7.3 页面改造清单

| 页面 | 当前状态 | 后续改造 |
| --- | --- | --- |
| `LoginForm.vue` | 本地模拟登录 | 调用 `/api/auth/login` 和 `/api/auth/register` |
| `UserManagement.vue` | `localStorage` 用户 | 调用用户管理接口 |
| `DeviceManagement.vue` | 组件静态设备 | 调用设备管理接口 |
| `RoadStatus.vue` | 组件静态事件 | 调用事件管理接口 |
| `RealTimeMonitor.vue` | 组件静态监控数据 | 调用实时监控接口或 WebSocket |
| `Dashboard.vue` | 静态统计数据 | 调用统计接口 |
| `AiAssistant.vue` | 前端规则回答 | 调用 `/api/assistant/chat` |

## 8. 阶段实施计划

### 第一阶段：后端基础能力搭建

目标：搭建 Java 后端基础工程，并让前端可以成功访问接口。

任务：

- 创建 Spring Boot 项目。
- 配置 MySQL。
- 配置统一响应格式。
- 配置跨域。
- 配置 Swagger / Knife4j。
- 创建基础数据库表。
- 实现健康检查接口。

验收标准：

- Java 后端可以启动。
- Swagger 接口文档可以访问。
- 前端可以成功请求后端测试接口。

### 第二阶段：登录认证与用户管理

目标：替换前端本地登录逻辑。

任务：

- 实现用户注册。
- 实现用户登录。
- 实现 JWT 鉴权。
- 实现用户列表、新增、编辑、删除、禁用、重置密码。
- 前端登录页接入真实接口。
- 前端用户管理页接入真实接口。

验收标准：

- 使用数据库账号可以登录系统。
- 未登录访问业务页面会被拦截。
- 用户管理页面数据来自数据库。

### 第三阶段：监控设备管理

目标：实现真实设备数据管理。

任务：

- 创建设备表。
- 实现设备增删改查。
- 实现设备筛选和分页。
- 实现设备状态更新接口。
- 前端设备管理页接入接口。

验收标准：

- 新增设备后刷新页面仍存在。
- 编辑、删除、筛选、分页功能正常。

### 第四阶段：交通事件记录管理

目标：实现交通异常事件的持久化管理。

任务：

- 创建事件表。
- 实现事件列表查询。
- 实现事件详情。
- 实现事件状态更新。
- 实现事件删除。
- 前端交通异常记录页接入接口。

验收标准：

- 事件数据来自数据库。
- 可以按条件筛选事件。
- 可以查看详情、标记处理和删除事件。

### 第五阶段：控制台统计与首页数据

目标：让首页和系统控制台展示真实统计。

任务：

- 实现统计接口。
- 统计设备数量、用户数量、事件数量。
- 统计设备在线率。
- 统计近 7 天事件趋势。
- 前端控制台接入接口。

验收标准：

- Dashboard 数据随着数据库变化而变化。
- 统计接口响应稳定。

### 第六阶段：Python FastAPI 模型服务

目标：打通 Java 调用 Python 模型服务的链路。

任务：

- 创建 FastAPI 项目。
- 实现 `/model/health`。
- 实现模拟 `/model/analyze-frame` 接口。
- Java 后端调用 Python 接口。
- Java 保存 Python 返回的分析结果。

验收标准：

- Python 服务可以独立启动。
- Java 可以成功调用 Python。
- 分析结果可以保存到数据库。

### 第七阶段：实时监控与预警闭环

目标：形成“设备分析 - 结果保存 - 事件生成 - 前端展示”的完整流程。

任务：

- 实现实时分析结果表。
- 根据模型结果生成交通事件。
- 实现实时监控最新数据接口。
- 可选实现 WebSocket 推送。
- 前端实时监控页接入接口。

验收标准：

- 前端可以看到后端返回的实时监控结果。
- 拥堵或异常数据可以生成交通事件。
- 交通事件可以在事件管理页面查看。

### 第八阶段：接入真实模型

目标：将模拟模型结果替换为真实算法输出。

任务：

- 接入 YOLO 或其他车辆检测模型。
- 使用 OpenCV 读取图片、视频帧或 RTSP 流。
- 实现车辆数量统计。
- 实现拥堵等级计算。
- 实现异常停车、低速占道等规则。
- 保存检测截图。

验收标准：

- 输入图片或视频帧后可以得到车辆识别结果。
- 能够输出车辆数量、平均速度、拥堵等级和异常事件。
- Java 可以保存并展示真实模型结果。

## 9. 优先级建议

建议优先级如下：

```text
P0：必须完成
- Java 后端基础框架
- 登录认证
- 用户管理
- 设备管理
- 交通事件管理
- 前端接口改造

P1：重要增强
- 控制台统计
- 实时监控接口
- Python FastAPI 模拟模型服务
- Java 调用 Python

P2：高级功能
- WebSocket 实时推送
- 真实 YOLO 检测模型
- 视频流分析
- AI 智能助手接入大模型
- 操作日志和权限细分
```

## 10. 风险与注意事项

### 10.1 不要一开始就直接接真实模型

真实视频分析涉及模型、显卡环境、视频流、帧率、性能等问题，容易拖慢整体进度。建议先用模拟模型接口打通业务闭环，再替换真实模型。

### 10.2 前端不要直接承担业务判断

例如用户权限、事件状态、设备状态、统计数据等，都应由后端统一判断和保存。前端只负责展示和交互。

### 10.3 Java 和 Python 职责要清晰

Java 负责业务数据和系统流程，Python 负责模型推理。不要让 Python 同时操作大量业务表，否则后期维护会变复杂。

### 10.4 接口返回格式要统一

前端接入接口前，需要统一响应格式、分页格式、错误码和字段命名，否则后续页面维护成本会很高。

### 10.5 注意密码和 token 安全

密码必须加密存储，不能明文保存。JWT 需要设置过期时间，生产环境还需要考虑刷新 token 和退出登录后的 token 失效问题。

## 11. 建议的最终成果

完成后，项目可以形成如下成果：

- 一套 Vue 前端管理系统。
- 一套 Java Spring Boot 业务后端。
- 一套 Python FastAPI 模型推理服务。
- 一套 MySQL 数据库表结构。
- 完整接口文档。
- 登录认证、用户管理、设备管理、事件管理、统计分析、实时监控、AI 智能助手等功能。
- 可演示的“交通视频分析 - 拥堵预警 - 事件记录 - 前端展示”闭环。

最终项目定位可以描述为：

> 本项目基于 Vue 3、Spring Boot 和 FastAPI 构建智慧交通监控预警系统。前端负责可视化展示与交互，Java 后端负责业务管理、权限控制和数据持久化，Python 模型服务负责交通视频智能分析与拥堵预警推理，实现从监控设备接入、交通状态识别、异常事件生成到管理端展示的完整业务流程。

