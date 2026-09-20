# Web SCADA 基础工程

当前范围：Vue + Spring Boot 可运行骨架、本机 MySQL / Redis 基础连接、认证菜单、基于金斗河现场点表的设备与点位台账、系统字典、采集通道配置 MVP、Modbus TCP 仿真 PLC、Redis 实时当前值、实时监控页面、报警闭环和报警规则维护 MVP。当前没有接入真实 PLC、控制下发、历史数据或组态业务。

## 本机位置

项目位于 `E:\web_scada`。运行日志、本地连接配置、构建产物和依赖缓存不会提交 Git。

GitHub：<https://github.com/douzhixiao7-sudo/web_scada>

## 技术版本

| 组件 | 版本 / 说明 |
|---|---|
| Java | Eclipse Temurin 21.0.12.1+1 LTS |
| Maven | 3.9.16 |
| Node.js | 24.21.0 |
| Spring Boot | 4.1.1，Web MVC、Actuator、JDBC、Spring Data Redis |
| Vue / Vite | 3.5.43 / 8.3.0 |
| TypeScript / vue-tsc | 5.9.3 / 3.3.11 |
| MySQL | 本机 `127.0.0.1:3306`，项目库 `web_scada` |
| Redis | 本机 `127.0.0.1:6379` |

前端版本由 `frontend/package-lock.json` 锁定，Java 依赖由 Spring Boot BOM 管理。工具下载地址和校验值保存于 `scripts/toolchain.json`。

## 安装与运行

在项目根目录打开 PowerShell：

```powershell
# 安装 Java / Maven / Node 工具和前端依赖，并构建前后端
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup.ps1

# 已有环境，仅构建
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1

# 启动前后端
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1

# 停止前后端
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop.ps1
```

后端从 `.local/config/application-local.properties` 读取本机 MySQL / Redis 连接配置。该文件包含本机密码，已被 `.gitignore` 排除，不要提交到公开仓库。

服务绑定本机回环地址：前端 `http://127.0.0.1:5173`，后端 `http://127.0.0.1:8080/actuator/health`，前端代理 `/api/actuator/health`。健康检查包含 `db` 和 `redis`；失败时不会伪造成功。

## 认证与菜单

后端已提供最小认证闭环：`/api/auth/login`、`/api/auth/me`、`/api/auth/logout` 和 `/api/menus`。登录成功后，后端会把 session token 写入 Redis，前端使用 `Authorization: Bearer <token>` 调用受保护接口。

首次启动会自动创建基础后台表：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`、`sys_audit_log`。默认开发账号为 `admin` / `admin`，仅用于本机开发骨架，后续进入真实权限阶段时需要改成初始化密码或用户管理流程。

## 设备管理 MVP

后端已提供设备管理基础接口：`/api/areas`、`/api/devices`、`/api/devices/{id}`、`/api/points?deviceId=...`。登录后可进行设备列表查询、区域/状态筛选、新建设备、编辑设备、删除设备，并查看设备点位。

首次启动会自动创建 `scada_area`、`scada_device`、`scada_point`，并读取 `backend/src/main/resources/jindouhe_points.csv` 初始化金斗河现场点表。当前点表来自“副本金斗河自动化测点、IP分配表240425.xlsx”，已转换为 2 个现场区域、10 个设备对象和 270 个点位。区域只表示设备归属位置；`1#主机`、`4#主机`、`1#闸门`、`1#2#闸门控制柜` 这类可通信、可采集或可控制对象才是设备。

点位已支持按设备维护：查看、新增、编辑、删除、配置点位编码、采集地址、数据类型、单位、读写属性、缩放系数和排序。

## 系统字典

后端已提供基础字典表 `sys_dict_type`、`sys_dict_item`，接口为 `/api/dictionaries`、`/api/dictionaries/{typeCode}/items`、`/api/dictionaries/items?typeCodes=...`。当前已维护：设备状态、通讯协议、设备类型、点位数据类型、点位读写属性、点位单位。设备与点位表单下拉项来自字典接口，不再写死在页面里。

## 实时监控静态框架

前端已提供“实时监控”页面的 MVP 静态框架。页面基于现有设备与点位台账展示设备状态、协议、点位清单和实时数据接入契约，占位字段包括 `pointId`、`value`、`quality`、`collectedAt`。当前页面只读取 `/api/devices` 与 `/api/points?deviceId=...`，实时值显示为“待接入 / 未采集 / 等待实时接口”。

本阶段没有新增 PLC 仿真、采集调度、实时数据表或 WebSocket 推送；这些会在后续实时数据阶段单独设计和实现。



## Modbus TCP 仿真 PLC MVP

后端已内置一个轻量 Modbus TCP 仿真 PLC，项目启动后监听 `127.0.0.1:1502`，支持 0 区 Coil、1 区 Discrete Input、3 区 Input Register、4 区 Holding Register 的基础读取。采集调度器会优先通过 Java Modbus TCP 客户端读取仿真 PLC，再把采集结果写入 Redis 当前值；如果读取失败，会回退到内置仿真算法，避免实时监控页面断数。

当前阶段已经形成 `Modbus TCP 仿真 PLC -> Java Modbus 采集 -> Redis 当前值 -> 实时监控 / 报警` 的主链路。这个仿真 PLC 是本机开发期能力，后续接真实设备时可复用采集器和 Redis 后续链路。

## Redis 实时数据 MVP

后端已提供 Modbus 采集调度器。项目启动后，定时任务每秒读取启用的 `scada_collect_channel` 和 `scada_collect_binding`，优先通过 Modbus TCP 客户端读取本机仿真 PLC，并写入 Redis Hash `scada:realtime:values`。实时接口 `/api/realtime/values?deviceId=...` 从 Redis 当前值读取；报警规则计算也复用同一份 Redis 当前值。

前端实时监控页已改为展示 Redis 当前值，并在页面停留时每 3 秒自动刷新。当前阶段仍不连接真实 PLC，不写历史库；下一步可把本机仿真 PLC 切换为真实 Modbus 设备。

## 报警闭环 MVP

后端已提供报警闭环 MVP：`/api/alarms/active` 会按实时模拟值计算活动报警并同步写入 `scada_alarm_event`；`/api/alarms/events?status=...` 支持按状态查询；`POST /api/alarms/events/{id}/ack` 支持确认和备注；`/api/alarms/rules` 支持按设备、点位和启用状态查询报警规则；`PUT /api/alarms/rules/{id}` 支持维护规则名称、阈值、等级、内容和启停状态。首次启动会自动创建 `scada_alarm_rule`，并按现场点表生成质量异常、数据超时、故障信号、开度、电流、电压等默认规则。前端“报警中心”已支持状态筛选、确认备注和规则维护。

当前报警规则维护仍是 MVP：支持查询、编辑和启停已有默认规则；新增规则、删除规则、恢复策略配置、历史高级筛选和报表尚未开发。

## 采集通道配置 MVP

后端已提供采集通道配置 MVP：首次启动会创建 `scada_collect_channel` 和 `scada_collect_binding`，按设备自动生成 1 个默认采集通道，并把该设备下点位绑定到通道。接口包括 `GET /api/collect/channels`、`PUT /api/collect/channels/{id}`、`GET /api/collect/channels/{id}/bindings`、`PUT /api/collect/channels/{channelId}/bindings/{bindingId}` 和 `POST /api/collect/channels/{id}/poll`。

前端“系统设置”已增加采集通道配置页，可查看通道、编辑主机/端口/周期/启停、查看点位绑定、启停单个绑定，并用“模拟心跳”记录最后采集时间。当前阶段只做配置模型和接口，不启动真实采集线程，不连接 PLC。

## Modbus 数据区语义

系统字典已维护 `modbus_area`：`0` 表示 Coil 线圈区，可读写，常用于 DO 控制输出；`1` 表示 Discrete Input 离散输入区，只读，常用于 DI 状态输入；`3` 表示 Input Register 输入寄存器区，只读，常用于 AI/测量值；`4` 表示 Holding Register 保持寄存器区，可读写，常用于 AO/设定值。

## 工程结构

```text
backend/              Spring Boot 启动类、依赖、配置
frontend/             Vue 启动检查页、Vite 代理
scripts/              工具安装、构建、启动、停止脚本
.local/config/        本机连接配置与密码（不提交）
.run/                 日志与进程记录（不提交）
```

当前是本机开发框架，不是生产部署。已具备开发期认证菜单，但还不是完整生产权限体系，因此不要对外开放服务。前端监控页不代表已连接 PLC。

## 后续边界

TDengine、真实 PLC 接入、MQTT、OPC UA、WebSocket 业务订阅、细粒度权限、报警新增/删除、历史数据与 HMI 均未开发。下一阶段可进入真实 Modbus 设备配置、控制下发 MVP 或历史数据设计。

