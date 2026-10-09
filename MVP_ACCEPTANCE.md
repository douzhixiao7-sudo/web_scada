# 首个 MVP 交付验收

验收日期：2026-10-09  
验收环境：Windows 本机，`127.0.0.1` 回环地址

## 启动与检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\status.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

首次运行若 Redis 未加入 `PATH`，给 `start.ps1` 传入 `-RedisServer <redis-server.exe 路径>`。脚本会把路径保存到不提交 Git 的本机配置。

## 验收范围

| 范围 | 通过标准 |
|---|---|
| 基础设施 | MySQL 3306 可连接，Redis 6379 返回 PONG |
| 应用 | 后端 Actuator 为 UP，前端首页返回 HTTP 200 |
| 认证 | 默认本机开发账号可登录并获得会话 token |
| 台账 | 已初始化现场设备和点位 |
| 数据链路 | 仿真 PLC 经 Java 采集进入 Redis，存在 GOOD 实时值 |
| 报警 | 活动报警接口可查询，规则维护闭环可用 |
| HMI | 存在画面和已发布版本，运行画面可读取实时值 |

## MVP 边界

- 当前默认连接本机仿真 PLC，真实 PLC 需要现场逐通道、逐点位联调。
- Redis 在本机开发脚本中采用无持久化模式；会话和实时值均可重建。
- 历史数据只是 MySQL 低频占位，正式历史分析等待时序数据库方案。
- 默认 `admin/admin` 仅用于本机演示，生产环境必须更换初始化账号策略。
- 当前是单机交付形态，尚未包含 HTTPS、容器编排、备份、监控告警和高可用部署。
- HMI 运行态以读取与展示为主，写控制沿用现有控制下发确认与审计，不在组态画布直接开放。

## 验收结果

2026-10-09 从完整停止状态无参数重启后，状态检查显示 MySQL TCP、Redis PONG、后端 UP、前端 HTTP 200。冒烟验收通过 8/8：10 台设备、270 个点位、270 个 GOOD 实时值、活动报警接口、2 个 HMI 画面以及 V1 已发布画面均通过。

每次交付或现场部署前应重新执行状态检查和冒烟验收。
