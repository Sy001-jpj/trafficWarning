# traffic-warning-ai

智慧交通监控预警系统的 Python 模型服务骨架项目，基于 FastAPI 搭建。

该服务定位为独立的 AI 推理服务，后续负责交通图片/视频帧分析、车辆检测、车流量统计、平均速度估算、拥堵等级判断和异常行为识别。

当前版本是基础骨架，已经包含 FastAPI 应用入口、跨域配置、健康检查接口，以及一个模拟图片分析接口。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| Web 框架 | FastAPI |
| ASGI 服务 | Uvicorn |
| 数据模型 | Pydantic |
| 文件上传支持 | python-multipart |
| 语言版本 | Python 3.10+，推荐 Python 3.12 |

当前 `requirements.txt` 中已包含：

```text
fastapi
uvicorn[standard]
pydantic
python-multipart
```

后续接入真实模型时，可以继续补充：

```text
opencv-python
numpy
pillow
torch
ultralytics
```

## 项目职责

模型服务建议只负责 AI 分析，不直接承担业务系统职责。

推荐调用关系：

```text
Vue 前端
  |
  v
Java Spring Boot 业务后端
  |
  v
Python FastAPI 模型服务
```

模型服务主要职责：

- 接收图片、视频帧或 RTSP 地址。
- 调用车辆检测模型。
- 统计机动车和非机动车数量。
- 估算车辆平均速度。
- 判断拥堵等级。
- 识别异常停车、低速占道、排队溢出等事件。
- 将结构化分析结果返回给 Java 后端。

Java 后端负责保存结果、生成事件、做权限校验和向前端返回统一响应。

## 当前已实现内容

当前骨架已包含：

- FastAPI 应用入口 `app/main.py`。
- CORS 跨域配置。
- 健康检查接口 `GET /model/health`。
- 模拟图片分析接口 `POST /model/analyze-image`。
- 请求模型 `AnalyzeRequest`。
- 响应模型 `AnalyzeResult`。
- 路由拆分结构。

## 目录结构

```text
traffic-warning-ai/
├── app/
│   ├── routers/
│   │   ├── __init__.py
│   │   ├── health.py
│   │   └── model.py
│   ├── __init__.py
│   ├── main.py
│   └── schemas.py
├── .gitignore
├── README.md
└── requirements.txt
```

## 环境要求

建议环境：

```text
Python 3.10+
pip
```

检查版本：

```sh
python --version
pip --version
```

## 安装依赖

进入模型服务目录：

```sh
cd traffic-warning-ai
```

创建虚拟环境：

```sh
python -m venv .venv
```

激活虚拟环境。

Windows PowerShell：

```sh
.venv\Scripts\activate
```

macOS / Linux：

```sh
source .venv/bin/activate
```

安装依赖：

```sh
pip install -r requirements.txt
```

## 启动服务

开发模式启动：

```sh
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

默认访问地址：

```text
http://127.0.0.1:8000
```

FastAPI 自动接口文档：

```text
http://127.0.0.1:8000/docs
```

备用文档：

```text
http://127.0.0.1:8000/redoc
```

## 接口说明

### 健康检查

```text
GET /model/health
```

示例响应：

```json
{
  "service": "traffic-warning-ai",
  "status": "UP",
  "time": "2026-05-29T00:00:00"
}
```

该接口用于确认 Python 模型服务是否正常启动。

### 模拟图片分析

```text
POST /model/analyze-image
```

当前接口是模拟接口，不会真正调用 AI 模型。它用于提前打通 Java 后端调用 Python 模型服务的链路。

请求示例：

```json
{
  "device_id": "C001",
  "image_url": "/files/snapshots/demo.jpg"
}
```

响应示例：

```json
{
  "device_id": "C001",
  "motor_count": 16,
  "non_motor_count": 4,
  "avg_speed": 18.5,
  "congestion_level": "III",
  "congestion_label": "轻度拥堵",
  "events": [
    "车流量持续偏大"
  ],
  "snapshot_path": "/files/snapshots/demo.jpg",
  "analyzed_at": "2026-05-29T00:00:00"
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `device_id` | 设备编号 |
| `motor_count` | 机动车数量 |
| `non_motor_count` | 非机动车数量 |
| `avg_speed` | 平均速度，单位 km/h |
| `congestion_level` | 拥堵等级 |
| `congestion_label` | 拥堵等级中文描述 |
| `events` | 检测到的异常事件列表 |
| `snapshot_path` | 检测截图路径 |
| `analyzed_at` | 分析时间 |

## 拥堵等级建议

后续可以使用统一规则判断拥堵等级：

| 平均速度 | 拥堵等级 | 描述 |
| --- | --- | --- |
| `>= 35 km/h` | I | 畅通 |
| `25 - 35 km/h` | II | 基本通畅 |
| `15 - 25 km/h` | III | 轻度拥堵 |
| `10 - 15 km/h` | IV | 中度拥堵 |
| `< 10 km/h` | V | 严重拥堵 |

当前模拟接口固定返回 `III / 轻度拥堵`，后续可根据模型结果动态计算。

## CORS 说明

当前允许以下前端来源访问模型服务：

```text
http://localhost:5173
http://127.0.0.1:5173
```

如果 Vite 前端端口变化，或者 Java 后端需要直接访问该服务，通常不受浏览器 CORS 限制。生产部署时建议按实际域名调整。

## 与 Java 后端的协作方式

推荐由 Java 后端调用模型服务，而不是 Vue 前端直接调用模型服务。

推荐流程：

```text
摄像头 / 图片 / 视频帧
  |
  v
Java 后端接收业务请求
  |
  v
Python FastAPI 进行模型分析
  |
  v
Java 后端保存分析结果和交通事件
  |
  v
Vue 前端展示
```

Java 后端调用示例目标：

```text
POST http://localhost:8000/model/analyze-image
```

## 后续建议模块

### 1. 图片分析

建议接口：

```text
POST /model/analyze-image
```

后续可以支持：

- 上传图片文件。
- 传入图片 URL。
- 返回车辆检测框。
- 返回检测截图。
- 返回车辆数量和拥堵等级。

### 2. 视频帧分析

建议接口：

```text
POST /model/analyze-frame
```

适合 Java 后端或前端上传单帧图片进行分析。

### 3. 视频流分析

建议接口：

```text
POST /model/analyze-stream
```

请求参数可以包含：

```json
{
  "device_id": "C001",
  "rtsp_url": "rtsp://localhost:8554/test_video"
}
```

后续服务可以使用 OpenCV 读取 RTSP 流，定时抽帧分析。

### 4. 模型管理

后续可以增加：

```text
GET  /model/info
POST /model/reload
```

用于查看当前模型名称、版本、加载状态，以及重新加载模型。

## 推荐后续目录

随着模型功能增加，建议扩展为：

```text
app/
├── core/
│   └── config.py
├── models/
│   └── detector.py
├── routers/
│   ├── health.py
│   └── model.py
├── services/
│   ├── image_analyzer.py
│   └── stream_analyzer.py
├── utils/
│   ├── congestion.py
│   └── image_io.py
├── main.py
└── schemas.py
```

目录说明：

| 目录 | 说明 |
| --- | --- |
| `core` | 配置、环境变量、日志等 |
| `models` | 模型加载和推理封装 |
| `routers` | FastAPI 路由 |
| `services` | 业务分析流程 |
| `utils` | 图像处理、拥堵判断等工具函数 |
| `schemas.py` | 请求和响应模型 |

## 后续接入真实模型建议

建议按以下顺序推进：

1. 保留当前模拟接口，先让 Java 后端能成功调用。
2. 增加图片上传接口，支持上传本地图片。
3. 接入 OpenCV，完成图片读取和基础处理。
4. 接入 YOLO 或其他车辆检测模型。
5. 输出车辆检测框、车辆数量和截图。
6. 根据车速或车辆密度计算拥堵等级。
7. 识别异常停车、低速占道、排队溢出等事件。
8. 将分析结果交给 Java 后端保存和展示。

## 开发注意事项

- 模型服务不要直接写业务数据库，业务数据应由 Java 后端统一管理。
- 模型接口返回字段要稳定，避免频繁影响 Java 和前端。
- 真实模型加载耗时较长时，应在应用启动时加载一次，不要每次请求重复加载。
- 视频流分析是高频任务，后续应考虑异步任务、队列或后台线程。
- 大文件上传和视频处理需要设置合理的大小限制和超时时间。
- 生产环境建议关闭 `--reload`，使用进程管理工具或容器部署。

## 常见问题

### 当前接口是否真的进行了车辆识别？

没有。当前 `/model/analyze-image` 是模拟接口，只返回固定示例数据，用于打通调用链路。

### 为什么模型服务不直接给前端用？

模型服务通常是内部服务。前端直接调用会绕过业务权限、日志、数据保存和事件生成流程。推荐由 Java 后端统一调用。

### 后续接入 YOLO 需要改哪些地方？

主要需要新增模型加载和推理代码，可以放到 `app/models/` 和 `app/services/` 中，然后让 `app/routers/model.py` 调用服务层。

### 能不能同时支持图片和视频流？

可以。建议先做图片或单帧分析，再做 RTSP 视频流分析。视频流处理复杂度更高，适合作为后续增强功能。

