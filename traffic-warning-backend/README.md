# traffic-warning-backend

智慧交通监控预警系统的 Java 业务后端骨架项目，基于 Spring Boot 搭建。

该服务定位为整个系统的业务中心，后续负责用户认证、用户管理、监控设备管理、交通异常事件管理、实时分析结果管理、系统控制台统计，以及调用 Python FastAPI 模型服务。

当前版本是基础骨架，已经包含启动类、统一响应结构、跨域配置和健康检查接口。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 核心框架 | Spring Boot 3.3.5 |
| Web 服务 | Spring Web |
| 参数校验 | Spring Validation |
| 构建工具 | Maven |
| Java 版本 | Java 17+ |
| 测试框架 | Spring Boot Test |

当前 `pom.xml` 中已引入：

```text
spring-boot-starter-web
spring-boot-starter-validation
spring-boot-starter-test
```

后续接入数据库、登录认证和接口文档时，可继续补充 MyBatis Plus、MySQL Driver、Spring Security、JWT、Knife4j 等依赖。

## 项目职责

Java 后端建议作为前端主要访问入口：

```text
Vue 前端
  |
  | HTTP / WebSocket
  v
Java Spring Boot 业务后端
  |
  | HTTP
  v
Python FastAPI 模型服务
```

后端主要职责：

- 统一向前端提供业务 API。
- 管理用户、角色、设备、交通事件等业务数据。
- 负责登录认证、权限控制和接口鉴权。
- 负责数据库读写和业务事务。
- 调用 Python 模型服务，获取车辆检测、拥堵分析、异常识别结果。
- 保存模型分析结果，并生成交通异常事件。
- 后续通过 WebSocket 向前端推送实时监控数据。

## 当前已实现内容

当前骨架已包含：

- Spring Boot 启动入口。
- 基础 Maven 工程结构。
- 统一接口返回结构 `ApiResponse`。
- CORS 跨域配置。
- 健康检查接口 `GET /api/health`。
- 基础测试类。
- 应用配置文件 `application.yml`。

## 目录结构

```text
traffic-warning-backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── trafficwarning/
│   │   │           └── backend/
│   │   │               ├── TrafficWarningBackendApplication.java
│   │   │               ├── common/
│   │   │               │   └── ApiResponse.java
│   │   │               ├── config/
│   │   │               │   └── CorsConfig.java
│   │   │               └── controller/
│   │   │                   └── HealthController.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/
│               └── trafficwarning/
│                   └── backend/
│                       └── TrafficWarningBackendApplicationTests.java
├── .gitignore
├── pom.xml
└── README.md
```

## 环境要求

建议环境：

```text
JDK 17 或更高版本
Maven 3.8 或更高版本
```

检查版本：

```sh
java -version
mvn -v
```

说明：`pom.xml` 中声明的 Java 版本是 17。当前本地如果使用更高版本 JDK，通常也可以运行，但正式开发建议使用 JDK 17 或 21 这类长期支持版本。

## 配置说明

配置文件位置：

```text
src/main/resources/application.yml
```

当前配置：

```yaml
server:
  port: 8080

spring:
  application:
    name: traffic-warning-backend

traffic-warning:
  ai-service-url: http://localhost:8000
```

配置含义：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | Java 后端服务端口 |
| `spring.application.name` | Spring Boot 应用名称 |
| `traffic-warning.ai-service-url` | Python FastAPI 模型服务地址 |

## 启动项目

进入后端目录：

```sh
cd traffic-warning-backend
```

启动服务：

```sh
mvn spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

## 测试项目

执行测试：

```sh
mvn test
```

如果本机 Maven 默认仓库权限受限，可以临时使用项目内 Maven 仓库：

```sh
mvn "-Dmaven.repo.local=.m2" test
```

`.m2/` 已加入 `.gitignore`，不会作为项目代码提交。

## 接口说明

### 健康检查

```text
GET /api/health
```

示例响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "service": "traffic-warning-backend",
    "status": "UP",
    "time": "2026-05-29T00:00:00"
  }
}
```

该接口用于确认 Java 后端是否正常启动。

## 统一返回格式

项目当前提供了 `ApiResponse<T>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

建议后续所有业务接口都使用该结构，便于前端统一处理成功、失败、登录过期和权限不足等情况。

## 跨域说明

当前 `CorsConfig` 允许以下来源访问 `/api/**` 接口：

```text
http://localhost:*
http://127.0.0.1:*
```

这样 Vue 前端在 `5173`、`5174` 等 Vite 端口启动时，都可以访问 Java 后端接口。

生产环境部署时，建议将允许来源改为固定域名，避免跨域策略过宽。

## 后续建议模块

建议按照以下顺序继续开发。

### 1. 登录认证模块

建议接口：

```text
POST /api/auth/login
POST /api/auth/register
GET  /api/auth/me
POST /api/auth/logout
```

建议补充：

- Spring Security。
- JWT。
- 密码加密。
- 登录状态校验。
- 用户启用/禁用校验。

### 2. 用户管理模块

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

### 3. 监控设备管理模块

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

### 4. 交通异常事件模块

建议接口：

```text
GET    /api/events
POST   /api/events
GET    /api/events/{id}
PATCH  /api/events/{id}/status
DELETE /api/events/{id}
```

### 5. 实时监控模块

建议接口：

```text
GET /api/monitor/devices
GET /api/monitor/devices/{deviceId}/latest
GET /api/monitor/realtime
```

后续可以增加：

```text
WS /ws/monitor
```

用于向前端推送实时车辆数量、平均速度、拥堵等级和异常事件。

### 6. 控制台统计模块

建议接口：

```text
GET /api/dashboard/summary
GET /api/dashboard/device-status
GET /api/dashboard/event-trend
GET /api/dashboard/congestion-levels
```

### 7. 模型服务调用模块

Java 后端后续可以通过 `traffic-warning.ai-service-url` 配置调用 Python FastAPI 服务。

示例调用目标：

```text
POST http://localhost:8000/model/analyze-image
```

建议流程：

```text
前端请求 Java 后端
  -> Java 后端校验权限和业务参数
  -> Java 后端调用 Python 模型服务
  -> Java 后端保存分析结果
  -> Java 后端返回统一业务响应
```

## 建议数据库表

最小可行版本建议先实现：

```text
sys_user
traffic_device
traffic_event
traffic_analysis
```

后续可扩展：

```text
sys_role
sys_user_role
traffic_road
traffic_snapshot
operation_log
```

## 开发注意事项

- 不要在 Controller 中直接写复杂业务逻辑，建议后续拆分 `controller`、`service`、`mapper/repository`、`entity`、`dto`。
- 密码不要明文存储，后续应使用 BCrypt 等方式加密。
- 前端传入的数据需要做参数校验。
- 业务异常应统一处理，不建议在接口中直接返回杂乱错误结构。
- Java 后端负责业务数据落库，Python 服务只负责模型推理。
- 接口路径建议统一以 `/api` 开头。

## 推荐后续目录

随着业务增加，建议扩展为：

```text
com.trafficwarning.backend
├── common
├── config
├── controller
├── dto
├── entity
├── exception
├── mapper
├── service
└── vo
```

其中：

| 目录 | 说明 |
| --- | --- |
| `controller` | 接收 HTTP 请求 |
| `service` | 业务逻辑 |
| `mapper` / `repository` | 数据访问 |
| `entity` | 数据库实体 |
| `dto` | 请求参数对象 |
| `vo` | 返回给前端的视图对象 |
| `common` | 通用返回、常量、工具 |
| `config` | Spring 配置 |
| `exception` | 全局异常处理 |

