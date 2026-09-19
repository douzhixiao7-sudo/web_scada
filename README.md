# Web SCADA 基础工程

当前范围：Vue + Spring Boot 启动框架，以及本机 MySQL / Redis 基础连接。没有设备管理、PLC 采集、控制、报警、历史或组态业务，也没有业务表。

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

首次启动会自动创建 `scada_area`、`scada_device`、`scada_point`，并写入 4 个演示区域、4 台独立设备和基础点位。区域只表示设备归属位置；`1#进水闸门`、`2#进水闸门`、`1#加压泵`、`出口压力变送器` 这类可通信、可采集或可控制对象才是设备。当前仍是设备与点位台账 MVP，不连接真实 PLC，也不进行实时采集。

点位已支持按设备维护：查看、新增、编辑、删除、配置点位编码、采集地址、数据类型、单位、读写属性、缩放系数和排序。
## 工程结构

```text
backend/              Spring Boot 启动类、依赖、配置
frontend/             Vue 启动检查页、Vite 代理
scripts/              工具安装、构建、启动、停止脚本
.local/config/        本机连接配置与密码（不提交）
.run/                 日志与进程记录（不提交）
```

当前是本机开发框架，不是生产部署。暂未加入业务身份权限，因此不要对外开放服务。前端状态页不代表已连接 PLC。

## 后续边界

TDengine、MQTT、Modbus、WebSocket 业务订阅、认证授权与 HMI 均未开发。本次只验证服务能启动、数据库能连接以及源码可推送。


