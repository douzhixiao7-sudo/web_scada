# Web SCADA 验证记录

验证时间：2026-09-28 10:55（Asia/Shanghai）

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
- 字典接口：`/api/dictionaries` 返回 7 类字典；批量字典项接口返回设备状态 4 项、通讯协议 4 项、设备类型 6 项、点位数据类型 4 项、读写属性 3 项、点位单位 7 项。
- 金斗河现场点表：Excel 已转换为 `jindouhe_points.csv`，启动后初始化 2 个现场区域、10 个设备对象、270 个点位。
- Modbus TCP 仿真 PLC：服务启动后 `127.0.0.1:1502` 可连接；采集通道状态返回 `MODBUS_OK`。
- Redis 实时数据：Java Modbus 采集器读取仿真 PLC 后写入 Redis Hash `scada:realtime:values`，缓存点位数 270。`/api/realtime/values?deviceId=...` 从 Redis 返回实时值；`4#主机` 点位 35 条，首点 `DI_ZJ4_YX` 质量 `GOOD`，采集时间随调度刷新。
- 报警闭环接口：`/api/alarms/active` 基于 Redis 当前值计算，可返回活动报警；`POST /api/alarms/events/{id}/ack` 确认成功，状态变为 `ACKED`，确认人为 `admin`；`/api/alarms/events?status=ACKED` 可查询已确认事件。
- 报警规则接口：`/api/alarms/rules?enabled=true` 可查询启用规则；`PUT /api/alarms/rules/{id}` 可更新规则名称、阈值、等级、内容和启停状态；更新后 `/api/alarms/active` 仍可按规则计算活动报警。
- 采集通道接口：`/api/collect/channels` 返回 10 个通道；首个通道 `4#主机采集通道` 绑定 35 个点位；`PUT /api/collect/channels/{id}` 更新成功；`POST /api/collect/channels/{id}/poll` 可记录最后采集时间。
- Modbus 数据区字典：`/api/dictionaries/modbus_area/items` 返回 4 项，分别为 0区 Coil、1区 Discrete Input、3区 Input Register、4区 Holding Register。
- 控制下发接口：`POST /api/control/commands` 可对可写点位下发；本次验证 `4#主机启动`（`DO_ZJ4_QD`，Modbus 0 区，`W`）下发目标值 `1`，返回 `SUCCESS`；随后实时值接口读回 `1`，质量 `GOOD`。
- 历史数据接口：`/api/history/latest?deviceId=...` 可返回设备最新采样；`/api/history/values?pointId=...&start=...&end=...` 可返回单点趋势采样；本次按 MVP 调整已清空 `scada_history_value`，后续仅低频占位写入。
- 采集诊断接口：`/api/collect/channels/{id}/test-connection` 返回成功；`/api/collect/channels/{id}/test-read` 返回成功，本次读取 `4#主机运行 = 1`。
- 控制保护接口：未带 `confirmed=true` 的控制请求被拒绝；确认后的 `4#主机启动` 控制命令返回 `SUCCESS`，记录包含 `confirmed=true` 和 `controlLevel=LOW`；按 `status=SUCCESS` 筛选可查询。
- 报警规则维护接口：`POST /api/alarms/rules` 新增规则成功；按点位查询可见；`DELETE /api/alarms/rules/{id}` 删除后查询不可见。

## 当前范围

项目包含可运行框架、基础连接验证、开发期认证菜单、基于金斗河现场点表的设备/点位台账、系统字典、采集通道配置 MVP、Modbus TCP 仿真 PLC、Redis 实时当前值、实时监控页面、报警闭环、报警规则维护 MVP、控制下发与保护审计 MVP、历史数据 MVP 和采集通道诊断 MVP。当前没有真实 PLC 正式接入、历史高级筛选、报表或拖拉拽组态编辑器；首页总览和固定版 HMI 已接入真实统计/实时数据。

## 本地文件

`.local/` 和 `.run/` 包含本机配置、密码、缓存、日志或进程记录，已被 Git 忽略，不提交到 GitHub。


## 2026-09-28 首页总览验证

- `scripts/build.ps1`：后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- 首页总览已接入 `/api/devices`、`/api/alarms/active`、`/api/collect/channels`、`/api/control/commands` 和 `/api/realtime/cache-size`。
- 首页展示设备/点位、报警、采集通道、Redis 当前值覆盖率、最近报警和最近控制命令。

## 2026-09-28 固定版 HMI 验证

- `scripts/build.ps1`：后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- HMI 页面复用 `/api/devices`、`/api/points?deviceId=...`、`/api/realtime/values?deviceId=...` 和 `/api/alarms/active`。
- HMI 支持设备节点选择、活动报警标识、关键点位实时值展示和页面停留自动刷新。

## 2026-09-28 MVP 收尾验收

- `scripts/stop.ps1`：无残留项目进程时可正常返回。
- `scripts/build.ps1`：后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- `scripts/start.ps1`：前端 `http://127.0.0.1:5173` 和后端 `http://127.0.0.1:8080` 启动成功，前端代理健康检查返回 `UP`。
- 健康检查：`db=UP`，`redis=UP`。
- 登录：`admin/admin` 登录成功，`/api/auth/me` 返回当前用户 `admin`。
- 菜单：`/api/menus` 返回 8 个菜单。
- 设备点位：`/api/devices` 返回 10 台设备；首台 `4#主机` 点位 35 个。
- Redis 实时值：`/api/realtime/cache-size` 返回 270；`4#主机` 实时值返回 35 条。
- 采集通道：`/api/collect/channels` 返回 10 条通道；首个通道状态为 `MODBUS_OK`。
- 报警：`/api/alarms/active` 当前返回 32 条活动报警，报警链路可用。
- 控制命令：`/api/control/commands` 当前返回 2 条命令，控制审计查询可用。
- 历史低频占位：`/api/history/latest?deviceId=...` 可返回设备最新采样，本次 `4#主机` 返回 35 条。
- 前端页面：`http://127.0.0.1:5173/` 返回 200，Vue 应用根节点存在。

### MVP 结论

当前 MVP 已形成本机闭环：登录菜单、设备点位、Modbus TCP 仿真 PLC、Java 采集、Redis 当前值、实时监控、报警闭环、控制下发保护、低频历史占位、首页总览和固定版 HMI。真实 PLC 接入、时序库、拖拉拽组态编辑器、细粒度权限、报表和生产部署属于 MVP+。
