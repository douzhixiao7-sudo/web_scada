# Web SCADA 阶段记录与待办清单

更新时间：2026-09-20 12:23（Asia/Shanghai）

## 当前项目定位

本项目当前处于 MVP 骨架阶段，已经把 Web SCADA 的后台基础能力和金斗河现场点表主线跑通：工程结构、数据库连接、登录菜单、设备台账、点位台账、系统字典、采集通道配置、Modbus TCP 仿真 PLC、Redis 实时当前值、实时监控页面、报警闭环和报警规则维护 MVP。当前还没有进入真实 PLC 采集、控制下发、历史数据或组态画面阶段。

## 已完成内容

### 1. 产品与阶段拆解

- 阅读并分析原始需求文档，形成 Web SCADA 产品方向。
- 按 MVP 节奏拆分阶段，优先完成可运行工程、基础后台和设备/点位主线。
- 明确当前设备建模口径：区域只是设备归属位置；`1#进水闸门`、`1#加压泵`、`出口压力变送器` 这类可通信、可采集或可控制对象才是设备。

### 2. 工程骨架

- 项目已移动到 `E:\web_scada`。
- 后端采用 Java + Spring Boot。
- 前端采用 Vue + Vite + TypeScript。
- 增加本机脚本：安装、构建、启动、停止。
- 前端通过 Vite 代理访问后端 `/api`，避免开发期跨域问题。

### 3. 本机基础设施连接

- 已接入 MySQL，项目库为 `web_scada`。
- 已接入 Redis，用于开发期登录会话 token。
- 后端 Actuator 健康检查包含数据库与 Redis 状态。
- 本机密码和运行文件放在 `.local/`、`.run/`，已加入 Git 忽略。

### 4. GitHub 留痕

- 已初始化 Git 仓库。
- 已连接远程仓库：`https://github.com/douzhixiao7-sudo/web_scada.git`。
- 每个阶段完成后进行构建/验证，再 commit 并 push。
- 当前最新提交会随每个阶段更新；最近已完成金斗河现场点表和模拟数据链路。

### 5. 登录与后台菜单

- 后端提供 `/api/auth/login`、`/api/auth/me`、`/api/auth/logout`、`/api/menus`。
- 默认开发账号：`admin/admin`。
- 登录成功后，后端写入 Redis session，前端使用 Bearer token 调用受保护接口。
- 前端已有后台布局、菜单、首页状态面板和基础工业风 UI。

### 6. 设备管理 MVP

- 后端已建表并初始化：`scada_area`、`scada_device`、`scada_point`。
- 已提供区域、设备、点位基础接口。
- 前端支持设备列表、区域/状态筛选、新增、编辑、删除。
- 当前设备台账已切换为金斗河现场点表主线：2 个现场区域、10 个设备对象、270 个点位。

### 7. 点位管理 MVP

- 点位挂在设备下面。
- 支持按设备查看点位。
- 支持点位新增、编辑、删除。
- 点位字段包括：名称、编码、数据类型、单位、采集地址、读写属性、缩放系数、排序。

### 8. 系统字典

- 已建基础字典表：`sys_dict_type`、`sys_dict_item`。
- 已维护设备状态、通讯协议、设备类型、点位数据类型、点位读写属性、点位单位。
- 前端设备与点位表单下拉项已改为从字典接口读取。

### 9. 实时监控页面

- 已新增“实时监控”页面。
- 页面基于金斗河现场设备/点位台账展示：设备、区域、协议、地址、点位清单。
- 已展示实时值字段：`pointId`、`value`、`quality`、`collectedAt`。
- 当前实时值来自开发期模拟接口，不接真实 PLC，不写实时值表。


### 10. 金斗河现场点表与模拟数据

- 已读取用户提供的“副本金斗河自动化测点、IP分配表240425.xlsx”。
- 已将主要点表工作表转换为 `backend/src/main/resources/jindouhe_points.csv`。
- 当前转换结果：2 个现场区域、10 个设备对象、270 个点位。
- 点位保留现场字段：来源工作表、LCU 分组、IO 模块、IO 类型、Modbus 类型、Modbus 地址、SIXNET 地址、ICONICS 路径、备注。
- 后端启动时会按 CSV 初始化金斗河设备和点位，并清理上一阶段旧演示设备。
- 已新增 `/api/realtime/values?deviceId=...`，按真实点位生成开发期模拟值、质量和采集时间。
- 前端实时监控页已展示模拟值、质量、采集时间和现场来源信息。


### 11. 报警闭环 MVP 与 Modbus 数据区

- 已新增 `scada_alarm_event` 报警事件表。
- `/api/alarms/active` 会按实时模拟值计算活动报警并同步事件表。
- `/api/alarms/events?status=...` 支持按 `ACTIVE`、`ACKED`、`RECOVERED` 或 `ALL` 查询报警事件。
- `POST /api/alarms/events/{id}/ack` 支持确认报警、记录确认人和备注。
- 规则包含质量异常、数据超时、故障信号触发、开度越限、电流偏高、电压越限。
- 前端“报警中心”已支持状态筛选、确认和备注。
- 已维护 `modbus_area` 字典，明确 0/1/3/4 区语义。
- 当前 0 区表示 Coil 线圈，1 区表示 Discrete Input 离散输入，3 区表示 Input Register 输入寄存器，4 区表示 Holding Register 保持寄存器。

### 12. 报警规则可配置 MVP

- 已新增 `scada_alarm_rule` 报警规则表。
- 启动时会按金斗河现场点表生成质量异常、数据超时、故障信号、开度高限、电流高限、电压高低限等默认规则。
- `/api/alarms/rules` 支持按设备、点位、启用状态查询规则。
- `PUT /api/alarms/rules/{id}` 支持维护规则名称、阈值、等级、内容和启停状态。
- 前端“报警中心”已增加报警规则维护区域，可以筛选设备、查看规则、编辑阈值/等级/启停。

### 13. 采集通道配置 MVP

- 已新增 `scada_collect_channel` 采集通道表。
- 已新增 `scada_collect_binding` 通道点位绑定表。
- 启动时按设备自动生成默认采集通道，并把设备点位绑定到通道。
- `/api/collect/channels` 支持查询通道。
- `PUT /api/collect/channels/{id}` 支持维护主机、端口、采集周期、启停和状态。
- `/api/collect/channels/{id}/bindings` 支持查询通道点位绑定。
- `PUT /api/collect/channels/{channelId}/bindings/{bindingId}` 支持启停单个点位绑定。
- `POST /api/collect/channels/{id}/poll` 用于 MVP 模拟一次采集心跳，记录最后采集时间。
- 前端“系统设置”已增加采集通道配置页。

### 14. Redis 实时数据 MVP

- 已新增 `RealtimeValueCache`，使用 Redis Hash `scada:realtime:values` 保存点位当前值。
- 已新增内置仿真采集调度器，每秒按启用采集通道和点位绑定刷新 Redis 当前值。
- `/api/realtime/values?deviceId=...` 已从 Redis 读取当前值，不再请求时临时生成。
- 报警计算通过 `RealtimeService` 读取同一份 Redis 当前值。
- 前端实时监控页已展示 Redis 当前值，并在页面停留时每 3 秒自动刷新。
- 当前阶段不连接真实 PLC，不写历史库。

### 15. Modbus TCP 仿真 PLC MVP

- 已新增本机 Modbus TCP 仿真 PLC，监听 `127.0.0.1:1502`。
- 支持 0/1/3/4 区基础读取，对应 Coil、Discrete Input、Input Register、Holding Register。
- 已新增 Java Modbus TCP 客户端。
- 采集调度器优先通过 Modbus TCP 采集点位，再写入 Redis 当前值。
- 如果 Modbus 读取失败，会回退到内置仿真算法，避免实时页面断数。
- 采集通道状态会显示 `MODBUS_OK` 或 `FALLBACK_SIM`。

## 已验证内容

- 后端 Maven 构建成功。
- 前端 `vue-tsc --noEmit && vite build` 成功。
- 前端服务可访问：`http://127.0.0.1:5173`。
- 后端健康检查返回 `UP`。
- 前端代理 `/api/actuator/health` 返回 `UP`。
- `admin/admin` 登录成功。
- `/api/devices` 返回 10 个金斗河现场设备对象。
- `/api/points?deviceId=...` 返回现场点表点位；`4#主机` 返回 35 个点位。
- Git 忽略规则已确认不会提交 `.local/`、`.run/`、`backend/target/`、`frontend/dist/`、`frontend/node_modules/`、`dump.rdb`。

## 当前明确未做

- 已做本机 Modbus TCP 仿真 PLC 和开发期 Java Modbus TCP 采集；未接入真实 PLC。
- 未做实时值数据库表；当前值已由 Java Modbus 采集器写入 Redis。
- 未做 WebSocket / SSE 推送。
- 已完成控制下发 MVP：支持可写点位下发、命令落库、Modbus 仿真器写入和实时值读回。
- 已做报警闭环 MVP，并支持报警规则查询、编辑、启停；未做规则新增/删除、历史高级筛选和报表。
- 已完成历史数据 MVP：MySQL 采样表、采样写入策略、历史查询接口和前端趋势页面；未做报表导出。
- 未做 HMI 组态画面。
- 未做生产级权限、角色管理、密码策略和审计闭环。
- 未接入 TDengine、MQTT、Modbus、OPC UA。

## 待完成项建议

### P0：实时数据主链路

- 设计点位当前值模型。
- 增加实时值查询接口，例如 `/api/realtime/values?deviceId=...`。
- 前端实时监控页已接入实时模拟值接口；后续需要接真实采集值或实时值表。
- 定义实时值质量状态：`GOOD`、`BAD`、`STALE`。
- 预留实时值写入服务，供后续采集器、仿真器或测试接口复用。

### P1：采集与仿真准备

- 已完成采集通道模型：协议、连接参数、采集周期、启停状态。
- 已完成通道与点位绑定模型。
- 已完成本机 Modbus TCP 仿真 PLC 和 Java Modbus TCP 采集，后续可替换为真实采集器。
- 已完成采集通道诊断与真实 Modbus 接入准备：支持仿真/真实模式、连接测试、单点读取测试、耗时和失败原因。
- 明确 Modbus TCP、MQTT、OPC UA 的优先顺序。

### P1：报警中心

- 已落地 `scada_alarm_rule` 报警规则表。
- 已支持按设备/点位/启用状态查询规则。
- 已支持编辑规则名称、阈值、等级、内容和启停状态。
- 已支持报警确认、恢复状态、处理备注和历史事件查询。
- 后续需要补规则新增/删除、规则类型表单化、高级筛选、历史追溯和报表。

### P1：历史数据

- 已完成历史数据存储方案：Redis 存当前值，MySQL 暂存 MVP 历史采样。
- 已明确 MySQL 与时序库边界：MVP 采用变化写入和降频采样，后续数据量增加再迁移 TDengine / TimescaleDB / InfluxDB。
- 已实现单点趋势查询、设备最新历史采样和前端历史趋势页面。
- 后续需要补历史保留策略配置、导出、报表和更完整的趋势组件。

### P2：权限与审计

- 增加用户、角色、菜单权限维护页面。
- 增加按钮级权限。
- 增加操作日志和登录日志查询。
- 修改默认开发账号策略，避免生产环境使用 `admin/admin`。

### P2：组态与大屏

- 设计 HMI 画面数据绑定方式。
- 设计设备图元、点位绑定、状态映射、颜色规则。
- 先做固定工艺流程画面，再考虑可视化拖拽组态。

### P2：工程质量

- 增加后端测试。
- 增加前端基础测试或页面冒烟检查。
- 增加统一异常处理和接口响应规范。
- 增加开发/测试/生产配置分层。
- 增加数据库迁移工具，例如 Flyway 或 Liquibase。

## 后续阶段建议

下一阶段建议进入“控制下发保护增强”或“报警规则新增/删除”：真实 Modbus 接入诊断已具备，MVP 还需要把控制安全和报警维护补齐。



## 2026-09-21 控制下发 MVP

### 已完成

- 新增 `scada_control_command` 控制命令表，记录命令编号、设备、点位、目标值、执行状态、执行消息、操作人和执行时间。
- 新增 `/api/control/commands`：支持按设备查询最近命令，支持提交控制命令。
- 后端下发前会校验点位读写属性和 Modbus 区域：`R` 点位拒绝下发；`0` 区 Coil 和 `4` 区 Holding Register 可写；`1` 区 Discrete Input 和 `3` 区 Input Register 按只读处理。
- Modbus TCP 客户端新增 Function 5 写单线圈、Function 6 写单保持寄存器。
- 本机 Modbus TCP 仿真 PLC 新增写入支持，并保存写入后的线圈/保持寄存器值，后续采集能读回。
- 前端实时监控页新增可写点位“下发”按钮和最近控制命令展示。

### 已验证

- `scripts/build.ps1` 后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- 使用本机 MySQL `root/root` 临时覆盖启动，Redis 绑定 `127.0.0.1:6379` 后，前端代理健康检查返回 `UP`，组件 `db`、`redis` 均为 `UP`。
- `admin/admin` 登录成功。
- 选中 `4#主机启动`，点位编码 `DO_ZJ4_QD`，Modbus `0` 区，读写属性 `W`，提交目标值 `1`。
- 控制命令返回 `SUCCESS`，消息为“写入 Modbus 仿真器成功”。
- `/api/realtime/values?deviceId=...` 读回该点实时值 `1`，质量 `GOOD`。

### 仍未做

- 未做控制命令二次确认弹窗组件化、按钮级权限和危险操作分级。
- 未做控制命令撤销，因为 Modbus 单次写入本身不可撤销，只能再次下发相反值。
- 未接入真实 PLC，当前写入对象仍是本机 Modbus TCP 仿真 PLC。
- 未做控制审计高级查询和操作报表。


## 2026-09-21 历史数据 MVP

### 已完成

- 新增 `scada_history_value` 历史采样表，字段包含设备、点位、值、质量和采集时间。
- 采集调度器新增历史采样旁路：实时值继续写 Redis，历史采样按策略写 MySQL。
- 采样策略：数字量按变化写入；模拟量按约 30 秒降频写入；`BAD` / `STALE` 质量变化立即写入。
- 新增 `/api/history/values?pointId=...&start=...&end=...` 查询单点历史采样。
- 新增 `/api/history/latest?deviceId=...` 查询设备下各点最新历史采样。
- 前端“历史数据”页面新增设备、点位、时间范围筛选、趋势条形图、历史采样表和设备最新采样侧栏。

### 已验证

- `scripts/build.ps1` 后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- 前端代理健康检查返回 `UP`，组件 `db`、`redis` 均为 `UP`。
- `admin/admin` 登录成功。
- 采集器运行后 `scada_history_value` 自动写入历史采样；验证时表内已有 988 条采样。
- `/api/history/latest?deviceId=...` 可返回设备点位最新采样。
- `/api/history/values?pointId=...&start=...&end=...` 可返回单点最近一小时采样。

### 仍未做

- 未引入时序库；当前历史数据暂存在 MySQL。
- 未做历史保留天数配置和定时清理。
- 未做 CSV / Excel 导出。
- 未做专业趋势图缩放、游标、同比和多点叠加。
- 未做报表中心。


## 2026-09-21 采集通道诊断与真实 Modbus 接入准备 MVP

### 已完成

- 采集通道新增 `channel_mode`、站号、超时、重试次数、最后成功时间、最后错误、耗时、连续失败次数字段。
- 新增 `POST /api/collect/channels/{id}/test-connection`，用于测试 Modbus TCP 连接。
- 新增 `POST /api/collect/channels/{id}/test-read?pointId=...`，用于测试单点读取；不传点位时读取通道第一个启用点位。
- 前端“系统设置 / 采集通道配置”展示仿真/真实模式、站号、超时、重试、最后成功、失败原因、耗时和连续失败次数。
- 前端新增“测试连接”“测试读取”“读一次”按钮，方便真实 PLC 接入前逐通道、逐点位排查。

### 已验证

- `scripts/build.ps1` 后端 Maven package 成功，前端 `vue-tsc --noEmit && vite build` 成功。
- 启动后前端代理健康检查返回 `UP`，组件 `db`、`redis` 均为 `UP`。
- `admin/admin` 登录成功。
- `4#主机采集通道` 诊断字段返回：模式 `SIMULATOR`、超时 `1200ms`、重试 `1`。
- 测试连接返回成功。
- 测试读取返回成功，读取点位 `4#主机运行`，值 `1`。

### 仍未做

- Modbus 站号字段已预留，但当前轻量客户端仍按单站仿真链路工作，后续接多站设备时需要把 slaveId 传入 Modbus MBAP Unit Identifier。
- 未做批量点位诊断报告。
- 未做现场 PLC 地址导入/校验向导。
