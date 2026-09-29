# 首页总览工业大屏样式 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页总览升级为深色科技驾驶舱风格，同时保持现有业务逻辑不变。

**Architecture:** 现有首页总览结构已经包含主视觉、指标卡、图表和链路状态模块。本计划只在 `frontend/src/style.css` 追加覆盖样式，利用现有类名完成视觉升级，不新增依赖、不改接口。

**Tech Stack:** Vue 3、Vite、TypeScript、ECharts、CSS。

## Global Constraints

- 仅美化首页总览页面，不调整设备、报警、实时监控等其他页面的业务逻辑。
- 保留现有总览数据结构：运行态势评分、设备/点位、当前报警、采集通道、实时数据覆盖、链路状态。
- 优先修改 `frontend/src/style.css`，仅在必要时为 `frontend/src/App.vue` 增加少量装饰性结构。
- 不新增接口。
- 不改变 ECharts 数据计算逻辑。
- 不改变登录、菜单、设备、报警等业务流程。
- 不引入新的 UI 库或图片资源。

---

### Task 1: 首页总览工业大屏 CSS 覆盖

**Files:**
- Modify: `frontend/src/style.css`

**Interfaces:**
- Consumes: 现有模板类名 `.overview-page`、`.overview-hero`、`.overview-score`、`.metric-card`、`.overview-main`、`.overview-chart`、`.overview-kpi-row`、`.overview-status-list`。
- Produces: 不新增运行时接口；输出为首页总览的视觉样式覆盖。

- [x] **Step 1: 阅读当前总览 CSS 覆盖段**

检查 `frontend/src/style.css` 末尾的 `Overview page refinement` 段，确认新的覆盖样式追加在其后方，避免改动其他页面基础样式。

- [x] **Step 2: 追加工业大屏视觉样式**

在 `frontend/src/style.css` 末尾追加 CSS，覆盖首页背景、主视觉、评分卡、指标卡、图表面板、KPI 行和链路状态列表。

- [x] **Step 3: 构建验证**

Run: `cd frontend && npm run build`

Expected: `vue-tsc --noEmit && vite build` 成功完成，没有 TypeScript 或 CSS 构建错误。

- [ ] **Step 4: 视觉检查**

如果 `npm run dev` 正在运行，打开 `http://127.0.0.1:5173`，登录后检查首页总览：主视觉应有暗色网格和青蓝光效，指标卡和状态面板应更像 SCADA 驾驶舱，窄屏布局不应溢出。
