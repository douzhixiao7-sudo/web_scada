# Web SCADA 验证记录

验证时间：2026-09-19 23:10（Asia/Shanghai）

## 已验证

- `scripts/build.ps1`：后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- MySQL：`127.0.0.1:3306` 可连接，已创建 `web_scada` 数据库和 `scada_app` 应用账号。
- Redis：`127.0.0.1:6379` 返回 `PONG`。
- Spring Boot Actuator：`http://127.0.0.1:8080/actuator/health` 返回 `status: UP`。
- Actuator 组件：`db: UP`，`redis: UP`。
- 前端代理：`http://127.0.0.1:5173/api/actuator/health` 返回 UP。

- 认证接口：`/api/auth/login` 使用 `admin/admin` 登录成功，返回 token 和 8 个后台菜单。
- 会话接口：`/api/auth/me` 使用 Bearer token 返回当前用户 `admin`。
- 菜单接口：`/api/menus` 使用 Bearer token 返回 8 个数据库菜单。
- 退出接口：`/api/auth/logout` 可清理 Redis session。

- 设备管理接口：`/api/devices` 返回 10 个金斗河现场设备对象。
- 点位接口：`/api/points?deviceId=...` 返回现场点表点位；`4#主机` 返回 35 个点位。
- 设备 CRUD：通过 `/api/devices` 新增测试设备、编辑状态和端口、再删除，流程通过。
- 点位 CRUD：通过 `/api/devices/{deviceId}/points` 新增测试点位、编辑数据类型和地址、再删除，流程通过。
- 字典接口：`/api/dictionaries` 返回 6 类字典；批量字典项接口返回设备状态 4 项、通讯协议 4 项、设备类型 6 项、点位数据类型 4 项、读写属性 3 项、点位单位 7 项。
- 金斗河现场点表：Excel 已转换为 `jindouhe_points.csv`，启动后初始化 2 个现场区域、10 个设备对象、270 个点位。
- 实时模拟接口：`/api/realtime/values?deviceId=...` 可返回模拟实时值；`4#主机` 点位 35 条，模拟值 35 条，首点 `DI_ZJ4_YX` 质量 `GOOD`。

## 当前范围

项目包含可运行框架、基础连接验证、开发期认证菜单、基于金斗河现场点表的设备/点位台账、系统字典和实时监控模拟数据页面。当前没有真实 PLC 采集、控制下发、报警闭环、历史数据或组态功能。

## 本地文件

`.local/` 和 `.run/` 包含本机配置、密码、缓存、日志或进程记录，已被 Git 忽略，不提交到 GitHub。

