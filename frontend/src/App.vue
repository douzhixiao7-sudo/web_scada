<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { BarChart, LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { NConfigProvider } from 'naive-ui/es/config-provider'
import { NInput } from 'naive-ui/es/input'
import { NInputNumber } from 'naive-ui/es/input-number'
import { NSelect } from 'naive-ui/es/select'
import { NTabPane, NTabs } from 'naive-ui/es/tabs'
import { darkTheme } from 'naive-ui/es/themes'
import HmiEditor from './components/HmiEditor.vue'
import AppModal from './components/AppModal.vue'
type ThemeMode = 'dark' | 'light'
const themeStorageKey = 'web_scada_theme'
const savedTheme = localStorage.getItem(themeStorageKey)
const themeMode = ref<ThemeMode>(savedTheme === 'light' ? 'light' : 'dark')
const naiveTheme = computed(() => themeMode.value === 'dark' ? darkTheme : null)
const themeLabel = computed(() => themeMode.value === 'dark' ? '明亮模式' : '深色模式')
const hmiEditorOpen = ref(false)
const demoModalOpen = ref(false)
const demoChannelName = ref('金斗河默认通道')
const demoChannelMode = ref('SIMULATOR')
const demoHost = ref('127.0.0.1')
const demoPort = ref('1502')
const demoInterval = ref('1000 ms')
const demoTimeout = ref('1200 ms')
const demoDescription = ref('这是通用弹窗的编辑表单示例，后续可复用到报警确认、规则维护、采集通道编辑和控制确认。')
const demoChannelModeOptions = [
  { label: '仿真模式', value: 'SIMULATOR' },
  { label: '真实设备', value: 'REAL' },
]

type HealthComponent = { status?: string }
type HealthPayload = { status?: string; components?: Record<string, HealthComponent> }
type MenuItem = { key: string; label: string; helper: string; icon: string; sortOrder?: number }
type AuthUser = { id: number; username: string; displayName: string }
type AuthPayload = { token?: string; user: AuthUser; menus: MenuItem[] }
type ApiError = { message?: string }
type DictItem = { typeCode: string; itemCode: string; label: string; description: string; sortOrder: number }
type Area = { id: number; name: string; code: string; description: string }
type Device = {
  id: number
  areaId: number
  areaName: string
  name: string
  code: string
  type: string
  status: string
  protocol: string
  ipAddress: string
  port: number | null
  description: string
  pointCount: number
}
type Point = {
  id: number
  deviceId: number
  name: string
  code: string
  dataType: string
  unit: string
  address: string
  accessMode: string
  scaleValue: number
  sortOrder: number
  sourceGroup: string
  sourceSheet: string
  ioModule: string
  ioType: string
  modbusType: string
  controlLevel: string
  controlConfirmRequired: boolean
  sixnetAddress: string
  iconicsPath: string
  remark: string
}
type RealtimeValue = { pointId: number; deviceId: number; pointCode: string; value: string; quality: string; collectedAt: string }
type AlarmEvent = { id: number; alarmKey: string; deviceId: number; deviceName: string; pointId: number; pointCode: string; pointName: string; level: string; message: string; value: string; quality: string; status: string; occurredAt: string; lastSeenAt: string; recoveredAt?: string | null; acknowledgedAt?: string | null; acknowledgedBy: string; ackNote: string }
type AlarmRule = { id: number; pointId: number; pointCode: string; pointName: string; ruleName: string; ruleType: string; operator: string; thresholdValue: number | null; level: string; message: string; enabled: boolean }
type AlarmRuleForm = { pointId: number | null; ruleName: string; ruleType: string; operator: string; thresholdValue: number | null; level: string; message: string; enabled: boolean }
type CollectChannel = { id: number; deviceId: number; deviceName: string; deviceCode: string; name: string; code: string; protocol: string; channelMode: string; host: string; port: number | null; slaveId: number; timeoutMs: number; retryCount: number; pollIntervalMs: number; enabled: boolean; status: string; pointCount: number; lastPolledAt: string | null; lastSuccessAt: string | null; lastError: string; lastLatencyMs: number | null; consecutiveFailures: number }
type CollectBinding = { id: number; channelId: number; pointId: number; pointCode: string; pointName: string; address: string; modbusType: string; accessMode: string; enabled: boolean }
type ControlCommand = { id: number; commandNo: string; deviceId: number; deviceName: string; pointId: number; pointCode: string; pointName: string; targetValue: number; controlLevel: string; confirmed: boolean; status: string; message: string; requestedBy: string; createdAt: string; executedAt?: string | null }
type CollectDiagnostic = { channelId: number; channelName: string; success: boolean; message: string; latencyMs: number; pointId: number | null; pointCode: string; pointName: string; rawValue: string; quality: string }
type HistoryValue = { id: number; deviceId: number; pointId: number; pointCode: string; pointName: string; value: string; quality: string; collectedAt: string }
type HistoryLatest = { pointId: number; pointCode: string; pointName: string; value: string; quality: string; collectedAt: string }

echarts.use([GridComponent, TooltipComponent, BarChart, LineChart, CanvasRenderer])

type DeviceForm = {
  areaId: number | null
  name: string
  code: string
  type: string
  status: string
  protocol: string
  ipAddress: string
  port: number | null
  description: string
}
type PointForm = {
  name: string
  code: string
  dataType: string
  unit: string
  address: string
  accessMode: string
  scaleValue: number
  sortOrder: number
}

const fallbackMenus: MenuItem[] = [
  { key: 'overview', label: '首页总览', helper: '运行态势', icon: '⌁' },
  { key: 'devices', label: '设备管理', helper: '站点与控制柜', icon: '▦' },
  { key: 'monitor', label: '实时监控', helper: '采集点位', icon: '◌' },
  { key: 'alarms', label: '报警中心', helper: '待确认事件', icon: '!' },
  { key: 'history', label: '历史数据', helper: '趋势与报表', icon: '∿' },
  { key: 'hmi', label: '组态画面', helper: '工艺流程', icon: '⌗' },
  { key: 'users', label: '用户与权限', helper: '角色策略', icon: '◎' },
  { key: 'settings', label: '系统设置', helper: '运行参数', icon: '⚙' },
]

const tokenKey = 'web_scada_token'
const username = ref('admin')
const password = ref('')
const loginError = ref('')
const isAuthed = ref(false)
const user = ref<AuthUser | null>(null)
const menuItems = ref<MenuItem[]>(fallbackMenus)
const activeMenu = ref('overview')
const sidebarCollapsed = ref(false)
const health = ref<HealthPayload | null>(null)
const healthText = ref('正在连接')
const checking = ref(false)
const loggingIn = ref(false)
const areas = ref<Area[]>([])
const dictionaries = ref<Record<string, DictItem[]>>({})
const devices = ref<Device[]>([])
const points = ref<Point[]>([])
const realtimeValues = ref<Record<number, RealtimeValue>>({})
const alarmRows = ref<AlarmEvent[]>([])
const alarmLoading = ref(false)
const alarmStatusFilter = ref('ACTIVE')
const highlightedAlarmId = ref<number | null>(null)
const alarmRules = ref<AlarmRule[]>([])
const alarmRuleDeviceId = ref<number | null>(null)
const alarmRuleLoading = ref(false)
const alarmRuleForm = ref<AlarmRuleForm>(emptyAlarmRuleForm())
const alarmRulePoints = ref<Point[]>([])
const collectChannels = ref<CollectChannel[]>([])
const collectBindings = ref<CollectBinding[]>([])
const controlCommands = ref<ControlCommand[]>([])
const controlLoading = ref(false)
const controlStatusFilter = ref('')
const historyPoints = ref<Point[]>([])
const historyRows = ref<HistoryValue[]>([])
const historyLatestRows = ref<HistoryLatest[]>([])
const historyLoading = ref(false)
const historyDeviceId = ref<number | null>(null)
const historyPointId = ref<number | null>(null)
const historyRangeHours = ref(1)
const collectChannelLoading = ref(false)
const selectedCollectChannelId = ref<number | null>(null)
const collectDiagnosticResult = ref<CollectDiagnostic | null>(null)
let monitorRefreshTimer: ReturnType<typeof setInterval> | null = null
let overviewRefreshTimer: ReturnType<typeof setInterval> | null = null
let hmiRefreshTimer: ReturnType<typeof setInterval> | null = null
const overviewRealtimeCount = ref(0)
const overviewLastUpdated = ref('')
const animatedOverviewScore = ref(100)
const overviewChartRef = ref<HTMLElement | null>(null)
let overviewChart: ECharts | null = null
let overviewScoreAnimation: number | null = null
const selectedDevice = ref<Device | null>(null)
const deviceError = ref('')
const deviceLoading = ref(false)
const savingDevice = ref(false)
const savingPoint = ref(false)
const editingDeviceId = ref<number | null>(null)
const editingPointId = ref<number | null>(null)
const deviceModalOpen = ref(false)
const areaFilter = ref('')
const statusFilter = ref('')
const monitorAreaFilter = ref('')
const monitorDeviceId = ref<number | null>(null)
const hmiDeviceId = ref<number | null>(null)
const hmiPoints = ref<Point[]>([])
const hmiRealtimeValues = ref<Record<number, RealtimeValue>>({})
const hmiLastUpdated = ref('')
const deviceForm = ref<DeviceForm>(emptyDeviceForm())
const pointForm = ref<PointForm>(emptyPointForm())

const menuIconPaths: Record<string, string> = {
  overview: '<path d="M3 12h4l2-6 4 12 2-6h6"/><path d="M4 20h16"/>',
  devices: '<rect x="4" y="5" width="16" height="14" rx="1.5"/><path d="M8 9h8M8 13h8M9 17h1M14 17h1"/>',
  monitor: '<path d="M4 18V6"/><path d="M8 18v-7"/><path d="M12 18V8"/><path d="M16 18v-5"/><path d="M20 18V4"/>',
  alarms: '<path d="M12 3 2.8 19h18.4L12 3Z"/><path d="M12 8v5"/><path d="M12 16h.01"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/><path d="M12 7v5l3 2"/>',
  hmi: '<rect x="3" y="4" width="18" height="14" rx="1.5"/><path d="M7 20h10"/><path d="M9 18v2M15 18v2"/><path d="M7 12h3l2-4 2 8 2-4h1"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  settings: '<circle cx="12" cy="12" r="3.5"/><path d="M19 12h2M3 12h2M12 3v2M12 19v2M17 7l1.4-1.4M5.6 18.4 7 17M7 7 5.6 5.6M18.4 18.4 17 17"/>',
}

const activeItem = computed(() => menuItems.value.find((item) => item.key === activeMenu.value) ?? menuItems.value[0] ?? fallbackMenus[0])
const dbStatus = computed(() => health.value?.components?.db?.status ?? 'UNKNOWN')
const redisStatus = computed(() => health.value?.components?.redis?.status ?? 'UNKNOWN')
const overallStatus = computed(() => health.value?.status ?? 'UNKNOWN')
const displayName = computed(() => user.value?.displayName ?? '未登录')
const onlineDeviceCount = computed(() => devices.value.filter((device) => device.status === '运行').length)
const alarmDeviceCount = computed(() => devices.value.filter((device) => device.status === '告警').length)
const deviceStatusOptions = computed(() => dictItems('device_status'))
const deviceProtocolOptions = computed(() => dictItems('device_protocol'))
const deviceTypeOptions = computed(() => dictItems('device_type'))
const deviceStatusSelectOptions = computed(() => deviceStatusOptions.value.map((item) => ({ label: item.label, value: item.itemCode })))
const deviceProtocolSelectOptions = computed(() => deviceProtocolOptions.value.map((item) => ({ label: item.label, value: item.itemCode })))
const deviceTypeSelectOptions = computed(() => deviceTypeOptions.value.map((item) => ({ label: item.label, value: item.itemCode })))
const deviceAreaSelectOptions = computed(() => areas.value.map((area) => ({ label: area.name, value: area.id })))
const deviceAreaFilterOptions = computed(() => [{ label: '全部区域', value: '' }, ...areas.value.map((area) => ({ label: area.name, value: String(area.id) }))])
const deviceStatusFilterOptions = computed(() => [{ label: '全部状态', value: '' }, ...deviceStatusOptions.value.map((item) => ({ label: item.label, value: item.itemCode }))])
const deviceModalTitle = computed(() => editingDeviceId.value ? '编辑设备' : '新建设备')
const pointDataTypeOptions = computed(() => dictItems('point_data_type'))
const pointAccessModeOptions = computed(() => dictItems('point_access_mode'))
const pointUnitOptions = computed(() => dictItems('point_unit'))
const monitorDevices = computed(() => devices.value.filter((device) => !monitorAreaFilter.value || String(device.areaId) === monitorAreaFilter.value))
const monitorSelectedDevice = computed(() => devices.value.find((device) => device.id === monitorDeviceId.value) ?? monitorDevices.value[0] ?? null)
const hmiDevices = computed(() => devices.value.slice(0, 12))
const hmiSelectedDevice = computed(() => devices.value.find((device) => device.id === hmiDeviceId.value) ?? hmiDevices.value[0] ?? null)
const hmiSelectedAlarms = computed(() => alarmRows.value.filter((alarm) => alarm.deviceId === hmiSelectedDevice.value?.id).slice(0, 5))
const hmiKeyPoints = computed(() => {
  const keywords = ['运行', '故障', '开度', '电流', '电压', '水位', '压力', '启动', '停止', '远程', '就地']
  const matched = hmiPoints.value.filter((point) => keywords.some((keyword) => `${point.name}${point.code}${point.remark}`.includes(keyword)))
  return (matched.length ? matched : hmiPoints.value).slice(0, 12)
})
const hmiGoodValueCount = computed(() => Object.values(hmiRealtimeValues.value).filter((value) => value.quality === 'GOOD').length)
const monitorPointCount = computed(() => points.value.length)
const writablePointCount = computed(() => points.value.filter((point) => point.accessMode !== 'R').length)
const selectedCollectChannel = computed(() => collectChannels.value.find((channel) => channel.id === selectedCollectChannelId.value) ?? collectChannels.value[0] ?? null)
const enabledCollectChannelCount = computed(() => collectChannels.value.filter((channel) => channel.enabled).length)
const historySelectedPoint = computed(() => historyPoints.value.find((point) => point.id === historyPointId.value) ?? null)
const historyNumericRows = computed(() => historyRows.value.map((row) => ({ ...row, numericValue: Number(row.value) })).filter((row) => Number.isFinite(row.numericValue)))
const historyMinValue = computed(() => historyNumericRows.value.length ? Math.min(...historyNumericRows.value.map((row) => row.numericValue)) : 0)
const historyMaxValue = computed(() => historyNumericRows.value.length ? Math.max(...historyNumericRows.value.map((row) => row.numericValue)) : 0)
const totalPointCount = computed(() => devices.value.reduce((sum, device) => sum + (device.pointCount || 0), 0))
const activeAlarmCount = computed(() => alarmRows.value.filter((alarm) => alarm.status === 'ACTIVE').length)
const unackedAlarmCount = computed(() => alarmRows.value.filter((alarm) => alarm.status === 'ACTIVE' && !alarm.acknowledgedAt).length)
const healthyChannelStatuses = ['ONLINE', 'MODBUS_OK']
const healthyChannelCount = computed(() => collectChannels.value.filter((channel) => channel.enabled && healthyChannelStatuses.includes(channel.status)).length)
const abnormalChannelCount = computed(() => collectChannels.value.filter((channel) => channel.enabled && !healthyChannelStatuses.includes(channel.status)).length)
const latestAlarms = computed(() => alarmRows.value.slice(0, 4))
const latestControlCommands = computed(() => controlCommands.value.slice(0, 4))
const overviewCoverageRate = computed(() => totalPointCount.value ? Math.round((overviewRealtimeCount.value / totalPointCount.value) * 100) : 0)
const overviewTrendSeries = computed(() => {
  const base = Math.max(28, Math.min(94, overviewCoverageRate.value || 42))
  return [base - 13, base - 8, base - 11, base - 3, base + 4, base + 1, base + 8, base + 5, base + 12, base + 9, base + 15, base + 11].map((value) => Math.max(12, Math.min(98, value)))
})
const overviewTrendLabels = ['00:00', '02:00', '04:00', '06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00', '22:00']
const overviewScore = computed(() => {
  const healthPenalty = overallStatus.value === 'UP' ? 0 : 24
  const alarmPenalty = Math.min(activeAlarmCount.value * 6, 30)
  const channelPenalty = Math.min(abnormalChannelCount.value * 8, 24)
  const redisPenalty = redisStatus.value === 'UP' ? 0 : 18
  return Math.max(0, 100 - healthPenalty - alarmPenalty - channelPenalty - redisPenalty)
})
const overviewStateText = computed(() => overviewScore.value >= 90 ? '运行平稳' : overviewScore.value >= 70 ? '需要关注' : '存在异常')
const overviewFloatingMetrics = computed(() => [
  { label: '运行设备', value: `${onlineDeviceCount.value} 台`, tone: 'ok' },
  { label: '未确认报警', value: `${unackedAlarmCount.value} 条`, tone: unackedAlarmCount.value ? 'warn' : 'ok' },
  { label: 'Redis 当前值', value: `${overviewRealtimeCount.value} 条`, tone: redisStatus.value === 'UP' ? 'ok' : 'warn' },
  { label: '通道健康', value: abnormalChannelCount.value ? `${abnormalChannelCount.value} 异常` : '正常', tone: abnormalChannelCount.value ? 'warn' : 'ok' },
  { label: '最近刷新', value: overviewLastUpdated.value || '等待', tone: 'info' },
])
function animateOverviewScore(target: number) {
  if (overviewScoreAnimation !== null) cancelAnimationFrame(overviewScoreAnimation)
  const from = 0
  const duration = 2200
  const startedAt = performance.now()

  const tick = (now: number) => {
    const progress = Math.min(1, (now - startedAt) / duration)
    const eased = 1 - Math.pow(1 - progress, 3)
    animatedOverviewScore.value = Math.round(from + (target - from) * eased)
    if (progress < 1) {
      overviewScoreAnimation = requestAnimationFrame(tick)
    } else {
      animatedOverviewScore.value = target
      overviewScoreAnimation = null
    }
  }

  animatedOverviewScore.value = from
  overviewScoreAnimation = requestAnimationFrame(tick)
}


function renderOverviewChart() {
  if (!overviewChartRef.value) return
  if (!overviewChart) overviewChart = echarts.init(overviewChartRef.value)
  const option: EChartsCoreOption = {
    color: ['#6bd7cc', '#5f7f88'],
    grid: { left: 34, right: 18, top: 28, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(11, 21, 26, .96)',
      borderColor: '#3d6570',
      textStyle: { color: '#dce7ec', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(125,226,215,.28)' } },
      valueFormatter: (value: string | number) => `${value}%`,
    },
    xAxis: {
      type: 'category',
      data: overviewTrendLabels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#28414b' } },
      axisTick: { show: false },
      axisLabel: { color: '#8fa5ad', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitNumber: 4,
      axisLabel: { color: '#8fa5ad', fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: 'rgba(67, 96, 106, .48)' } },
    },
    series: [
      {
        name: '覆盖率',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
        areaStyle: { color: 'rgba(107, 215, 204, .12)' },
        data: overviewTrendSeries.value,
      },
      {
        name: '当前覆盖',
        type: 'bar',
        barWidth: 6,
        itemStyle: { borderRadius: 0, opacity: .42 },
        data: overviewTrendSeries.value,
      },
    ],
  }
  overviewChart.setOption(option)
}

function resizeOverviewChart() {
  overviewChart?.resize()
}

function emptyDeviceForm(): DeviceForm {
  return { areaId: null, name: '', code: '', type: '闸门', status: '运行', protocol: 'MODBUS_TCP', ipAddress: '127.0.0.1', port: 1502, description: '' }
}

function emptyPointForm(): PointForm {
  return { name: '', code: '', dataType: 'DECIMAL', unit: '', address: '', accessMode: 'R', scaleValue: 1, sortOrder: 10 }
}

function emptyAlarmRuleForm(): AlarmRuleForm {
  return { pointId: null, ruleName: '', ruleType: 'HIGH', operator: '>', thresholdValue: null, level: '中', message: '', enabled: true }
}

const alarmRuleTypeOptions = [
  { value: 'HIGH', label: '高限' },
  { value: 'LOW', label: '低限' },
  { value: 'EQUAL', label: '等于' },
  { value: 'QUALITY_BAD', label: '质量异常' },
  { value: 'QUALITY_STALE', label: '数据超时' },
]
const alarmLevelOptions = ['高', '中', '低']

function dictItems(typeCode: string) {
  const fallback: Record<string, DictItem[]> = {
    device_status: ['运行', '待机', '告警', '离线'].map((value, index) => ({ typeCode, itemCode: value, label: value, description: '', sortOrder: index })),
    device_protocol: ['MODBUS_TCP', 'MQTT', 'OPC_UA', 'HTTP'].map((value, index) => ({ typeCode, itemCode: value, label: value, description: '', sortOrder: index })),
    device_type: ['闸门', '水泵', '仪表', 'PLC', '网关', '变频器'].map((value, index) => ({ typeCode, itemCode: value, label: value, description: '', sortOrder: index })),
    point_data_type: ['DECIMAL', 'INTEGER', 'BOOLEAN', 'STRING'].map((value, index) => ({ typeCode, itemCode: value, label: value, description: '', sortOrder: index })),
    point_access_mode: ['R', 'W', 'RW'].map((value, index) => ({ typeCode, itemCode: value, label: value, description: '', sortOrder: index })),
    point_unit: ['%', 'MPa', 'Hz', 'A', 'm', 'C', ''].map((value, index) => ({ typeCode, itemCode: value, label: value || '无单位', description: '', sortOrder: index })),
  }
  return dictionaries.value[typeCode]?.length ? dictionaries.value[typeCode] : fallback[typeCode] ?? []
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(tokenKey)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function parseError(response: Response) {
  try {
    const body = (await response.json()) as ApiError
    return body.message || `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = { ...authHeaders(), ...(options.headers as Record<string, string> | undefined) }
  const response = await fetch(url, { ...options, headers })
  if (!response.ok) throw new Error(await parseError(response))
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

function menuIconPath(key: string) {
  return menuIconPaths[key] ?? '<circle cx="12" cy="12" r="7"/><path d="M12 8v8M8 12h8"/>'
}

function handleMenuClick(key: string) {
  activeMenu.value = key
  if (key === 'hmi') void initHmiPage()
}

async function openAlarmFromOverview(alarm: AlarmEvent) {
  highlightedAlarmId.value = alarm.id
  activeMenu.value = 'alarms'
  alarmStatusFilter.value = 'ACTIVE'
  await nextTick()
  await loadAlarms()
}

function clearAlarmHighlightOnOutsideClick(event: MouseEvent) {
  if (highlightedAlarmId.value === null) return
  const target = event.target instanceof Element ? event.target : null
  if (!target) return
  if (target.closest('.overview-ticker-item')) return
  if (target.closest('.alarm-list article.highlighted')) return
  highlightedAlarmId.value = null
}

async function initHmiPage() {
  if (!isAuthed.value) return
  if (!devices.value.length) devices.value = await apiFetch<Device[]>('/api/devices')
  if (!alarmRows.value.length) alarmRows.value = await apiFetch<AlarmEvent[]>('/api/alarms/active')
  if (!hmiDeviceId.value) hmiDeviceId.value = devices.value[0]?.id ?? null
  await refreshHmiValues()
}

async function selectHmiDevice(device: Device) {
  hmiDeviceId.value = device.id
  await refreshHmiValues()
}

async function refreshHmiValues() {
  const device = hmiSelectedDevice.value
  if (!device) {
    hmiPoints.value = []
    hmiRealtimeValues.value = {}
    return
  }
  const [pointRows, valueRows, alarms] = await Promise.all([
    apiFetch<Point[]>(`/api/points?deviceId=${device.id}`),
    apiFetch<RealtimeValue[]>(`/api/realtime/values?deviceId=${device.id}`),
    apiFetch<AlarmEvent[]>('/api/alarms/active'),
  ])
  hmiPoints.value = pointRows
  hmiRealtimeValues.value = Object.fromEntries(valueRows.map((value) => [value.pointId, value]))
  alarmRows.value = alarms
  hmiLastUpdated.value = new Date().toLocaleTimeString()
}

function hmiValue(point: Point) {
  return hmiRealtimeValues.value[point.id]
}

function hmiDeviceAlarmCount(device: Device) {
  return alarmRows.value.filter((alarm) => alarm.deviceId === device.id && alarm.status === 'ACTIVE').length
}

function hmiDeviceState(device: Device) {
  if (hmiDeviceAlarmCount(device)) return '报警'
  if (device.status === '运行') return '运行'
  return device.status || '未知'
}

function hmiDeviceClass(device: Device) {
  const state = hmiDeviceState(device)
  return state === '报警' || state === '告警' ? 'danger' : state === '运行' ? 'ok' : 'idle'
}

function hmiPointState(point: Point) {
  const value = hmiValue(point)
  if (!value) return '未采集'
  if (value.quality !== 'GOOD') return value.quality
  if (point.dataType === 'BOOLEAN') return value.value === '1' ? '闭合/有效' : '断开/无效'
  return `${value.value}${point.unit ? ` ${point.unit}` : ''}`
}

async function loadOverviewData() {
  if (!isAuthed.value) return
  const tasks = await Promise.allSettled([
    apiFetch<Device[]>('/api/devices'),
    apiFetch<AlarmEvent[]>('/api/alarms/active'),
    apiFetch<CollectChannel[]>('/api/collect/channels'),
    apiFetch<ControlCommand[]>('/api/control/commands'),
    apiFetch<number>('/api/realtime/cache-size'),
  ])
  const [deviceResult, alarmResult, channelResult, commandResult, cacheSizeResult] = tasks
  if (deviceResult.status === 'fulfilled') devices.value = deviceResult.value
  if (alarmResult.status === 'fulfilled') alarmRows.value = alarmResult.value
  if (channelResult.status === 'fulfilled') collectChannels.value = channelResult.value
  if (commandResult.status === 'fulfilled') controlCommands.value = commandResult.value
  if (cacheSizeResult.status === 'fulfilled') overviewRealtimeCount.value = cacheSizeResult.value
  overviewLastUpdated.value = new Date().toLocaleTimeString()
}

async function checkBackend() {
  checking.value = true
  healthText.value = '正在连接'
  try {
    const response = await fetch('/api/actuator/health', { signal: AbortSignal.timeout(5000), cache: 'no-store' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const result = (await response.json()) as HealthPayload
    health.value = result
    healthText.value = result.status === 'UP' ? '在线' : '异常'
  } catch {
    health.value = null
    healthText.value = '离线'
  } finally {
    checking.value = false
  }
}

async function loadCurrentUser() {
  const token = localStorage.getItem(tokenKey)
  if (!token) return
  try {
    const result = await apiFetch<AuthPayload>('/api/auth/me', { cache: 'no-store' })
    user.value = result.user
    menuItems.value = result.menus.length ? result.menus : fallbackMenus
    isAuthed.value = true
    await loadDeviceData()
    await loadAlarms()
    await loadAlarmRules()
    await loadCollectChannels()
    await loadControlCommands()
    await initHistoryPage()
    await loadOverviewData()
    await initHmiPage()
  } catch {
    localStorage.removeItem(tokenKey)
    user.value = null
    isAuthed.value = false
  }
}

async function loadDictionaries() {
  dictionaries.value = await apiFetch<Record<string, DictItem[]>>('/api/dictionaries/items?typeCodes=device_status,device_protocol,device_type,point_data_type,point_access_mode,point_unit')
}

async function submitLogin() {
  loginError.value = ''
  const account = username.value.trim()
  if (!account || !password.value) {
    loginError.value = '请输入账号和密码'
    return
  }
  loggingIn.value = true
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: account, password: password.value }),
    })
    if (!response.ok) throw new Error(await parseError(response))
    const result = (await response.json()) as AuthPayload
    if (!result.token) throw new Error('登录响应缺少 token')
    localStorage.setItem(tokenKey, result.token)
    user.value = result.user
    menuItems.value = result.menus.length ? result.menus : fallbackMenus
    activeMenu.value = menuItems.value[0]?.key ?? 'overview'
    isAuthed.value = true
    password.value = ''
    await checkBackend()
    await loadDictionaries()
    await loadDeviceData()
    await loadAlarms()
    await loadAlarmRules()
    await loadCollectChannels()
    await loadControlCommands()
    await loadOverviewData()
    await initHmiPage()
  } catch (error) {
    loginError.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loggingIn.value = false
  }
}

async function logout() {
  try {
    await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders() })
  } finally {
    localStorage.removeItem(tokenKey)
    user.value = null
    isAuthed.value = false
    activeMenu.value = 'overview'
  }
}

function toggleTheme() {
  themeMode.value = themeMode.value === 'dark' ? 'light' : 'dark'
}

async function refreshMonitorPoints() {
  if (!monitorSelectedDevice.value) return
  const device = monitorSelectedDevice.value
  if (selectedDevice.value?.id === device.id && points.value.length) {
    const valueRows = await apiFetch<RealtimeValue[]>(`/api/realtime/values?deviceId=${device.id}`)
    realtimeValues.value = Object.fromEntries(valueRows.map((value) => [value.pointId, value]))
    await loadControlCommands(device.id)
    return
  }
  await selectDevice(device)
}

async function loadAlarms() {
  alarmLoading.value = true
  try {
    if (alarmStatusFilter.value === 'ACTIVE') {
      alarmRows.value = await apiFetch<AlarmEvent[]>('/api/alarms/active')
    } else {
      alarmRows.value = await apiFetch<AlarmEvent[]>(`/api/alarms/events?status=${alarmStatusFilter.value}`)
    }
  } finally {
    alarmLoading.value = false
  }
}

async function acknowledgeAlarm(alarm: AlarmEvent) {
  const note = window.prompt(`确认报警：${alarm.deviceName} ${alarm.pointName}`, '')
  if (note === null) return
  await apiFetch<AlarmEvent>(`/api/alarms/events/${alarm.id}/ack`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ note }),
  })
  await loadAlarms()
}

async function loadAlarmRules() {
  alarmRuleLoading.value = true
  try {
    const query = alarmRuleDeviceId.value ? `?deviceId=${alarmRuleDeviceId.value}` : ''
    alarmRules.value = await apiFetch<AlarmRule[]>(`/api/alarms/rules${query}`)
    await loadAlarmRulePoints()
  } finally {
    alarmRuleLoading.value = false
  }
}

async function loadAlarmRulePoints() {
  const deviceId = alarmRuleDeviceId.value ?? devices.value[0]?.id ?? null
  if (!deviceId) {
    alarmRulePoints.value = []
    return
  }
  alarmRulePoints.value = await apiFetch<Point[]>(`/api/points?deviceId=${deviceId}`)
  if (!alarmRuleForm.value.pointId || !alarmRulePoints.value.some((point) => point.id === alarmRuleForm.value.pointId)) {
    alarmRuleForm.value.pointId = alarmRulePoints.value[0]?.id ?? null
  }
}

async function editAlarmRule(rule: AlarmRule) {
  const thresholdInput = window.prompt('阈值，质量类规则可留空', rule.thresholdValue == null ? '' : String(rule.thresholdValue))
  if (thresholdInput === null) return
  const level = window.prompt('报警等级：高 / 中 / 低', rule.level)
  if (level === null) return
  const enabledInput = window.prompt('是否启用：1 启用，0 停用', rule.enabled ? '1' : '0')
  if (enabledInput === null) return
  await apiFetch<AlarmRule>(`/api/alarms/rules/${rule.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      pointId: rule.pointId,
      ruleName: rule.ruleName,
      ruleType: rule.ruleType,
      operator: rule.operator,
      thresholdValue: thresholdInput.trim() ? Number(thresholdInput) : null,
      level,
      message: rule.message,
      enabled: enabledInput.trim() !== '0',
    }),
  })
  await loadAlarmRules()
  await loadAlarms()
}

async function createAlarmRule() {
  const form = alarmRuleForm.value
  if (!form.pointId || !form.ruleName.trim() || !form.message.trim()) {
    window.alert('请选择点位，并填写规则名称和报警内容')
    return
  }
  await apiFetch<AlarmRule>('/api/alarms/rules', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form),
  })
  alarmRuleForm.value = emptyAlarmRuleForm()
  alarmRuleForm.value.pointId = alarmRulePoints.value[0]?.id ?? null
  await loadAlarmRules()
  await loadAlarms()
}

async function deleteAlarmRule(rule: AlarmRule) {
  if (!window.confirm(`删除报警规则：${rule.ruleName}？`)) return
  await apiFetch<void>(`/api/alarms/rules/${rule.id}`, { method: 'DELETE' })
  await loadAlarmRules()
  await loadAlarms()
}

function ruleTypeText(type: string) {
  return alarmRuleTypeOptions.find((item) => item.value === type)?.label ?? type
}


async function loadCollectChannels() {
  collectChannelLoading.value = true
  try {
    collectChannels.value = await apiFetch<CollectChannel[]>('/api/collect/channels')
    if (!selectedCollectChannelId.value && collectChannels.value.length) selectedCollectChannelId.value = collectChannels.value[0].id
    await loadCollectBindings()
  } finally {
    collectChannelLoading.value = false
  }
}

async function loadCollectBindings() {
  if (!selectedCollectChannel.value) {
    collectBindings.value = []
    return
  }
  collectBindings.value = await apiFetch<CollectBinding[]>(`/api/collect/channels/${selectedCollectChannel.value.id}/bindings`)
}

async function changeCollectChannel() {
  await loadCollectBindings()
}

async function editCollectChannel(channel: CollectChannel) {
  const host = window.prompt('采集主机/IP', channel.host)
  if (host === null) return
  const portInput = window.prompt('端口，可留空', channel.port == null ? '' : String(channel.port))
  if (portInput === null) return
  const intervalInput = window.prompt('采集周期，单位毫秒', String(channel.pollIntervalMs))
  if (intervalInput === null) return
  const enabledInput = window.prompt('是否启用：1 启用，0 停用', channel.enabled ? '1' : '0')
  if (enabledInput === null) return
  const mode = window.prompt('通道模式：SIMULATOR 仿真 / REAL 真实', channel.channelMode || 'SIMULATOR')
  if (mode === null) return
  const timeoutInput = window.prompt('超时时间，单位毫秒', String(channel.timeoutMs || 1200))
  if (timeoutInput === null) return
  const retryInput = window.prompt('重试次数，0-5', String(channel.retryCount ?? 1))
  if (retryInput === null) return
  await apiFetch<CollectChannel>(`/api/collect/channels/${channel.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: channel.name,
      protocol: channel.protocol,
      host,
      port: portInput.trim() ? Number(portInput) : null,
      pollIntervalMs: intervalInput.trim() ? Number(intervalInput) : channel.pollIntervalMs,
      enabled: enabledInput.trim() !== '0',
      channelMode: channel.channelMode || 'SIMULATOR',
      slaveId: channel.slaveId || 1,
      timeoutMs: channel.timeoutMs || 1200,
      retryCount: channel.retryCount ?? 1,
      status: channel.status,
    }),
  })
  await loadCollectChannels()
}

async function markCollectPolled(channel: CollectChannel) {
  await apiFetch<CollectChannel>(`/api/collect/channels/${channel.id}/poll`, { method: 'POST' })
  await loadCollectChannels()
}

async function toggleCollectBinding(binding: CollectBinding) {
  if (!selectedCollectChannel.value) return
  await apiFetch<CollectBinding>(`/api/collect/channels/${selectedCollectChannel.value.id}/bindings/${binding.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled: !binding.enabled }),
  })
  await loadCollectBindings()
  await loadCollectChannels()
}

async function testCollectConnection(channel: CollectChannel) {
  collectDiagnosticResult.value = await apiFetch<CollectDiagnostic>(`/api/collect/channels/${channel.id}/test-connection`, { method: 'POST' })
  await loadCollectChannels()
}

async function testCollectRead(channel: CollectChannel, binding?: CollectBinding) {
  const pointId = binding?.pointId ?? collectBindings.value.find((item) => item.enabled)?.pointId
  const query = pointId ? `?pointId=${pointId}` : ''
  collectDiagnosticResult.value = await apiFetch<CollectDiagnostic>(`/api/collect/channels/${channel.id}/test-read${query}`, { method: 'POST' })
  await loadCollectChannels()
}

function channelModeText(mode: string) {
  return mode === 'REAL' ? '真实设备' : '仿真'
}

function alarmStatusText(status: string) {
  const labels: Record<string, string> = { ACTIVE: '活动中', ACKED: '已确认', RECOVERED: '已恢复' }
  return labels[status] ?? status
}

async function changeMonitorArea() {
  monitorDeviceId.value = monitorDevices.value[0]?.id ?? null
  await refreshMonitorPoints()
}

async function loadDeviceData() {
  deviceLoading.value = true
  deviceError.value = ''
  try {
    if (!Object.keys(dictionaries.value).length) await loadDictionaries()
    if (!areas.value.length) areas.value = await apiFetch<Area[]>('/api/areas')
    const params = new URLSearchParams()
    if (areaFilter.value) params.set('areaId', areaFilter.value)
    if (statusFilter.value) params.set('status', statusFilter.value)
    devices.value = await apiFetch<Device[]>(`/api/devices${params.toString() ? `?${params}` : ''}`)
    if (!selectedDevice.value && devices.value.length) await selectDevice(devices.value[0])
    if (!monitorDeviceId.value && devices.value.length) monitorDeviceId.value = devices.value[0].id
    if (selectedDevice.value && !devices.value.some((device) => device.id === selectedDevice.value?.id)) {
      selectedDevice.value = null
      points.value = []
    }
  } catch (error) {
    deviceError.value = error instanceof Error ? error.message : '设备数据加载失败'
  } finally {
    deviceLoading.value = false
  }
}

async function selectDevice(device: Device) {
  selectedDevice.value = device
  monitorDeviceId.value = device.id
  const [pointRows, valueRows] = await Promise.all([
    apiFetch<Point[]>(`/api/points?deviceId=${device.id}`),
    apiFetch<RealtimeValue[]>(`/api/realtime/values?deviceId=${device.id}`),
  ])
  points.value = pointRows
  realtimeValues.value = Object.fromEntries(valueRows.map((value) => [value.pointId, value]))
  await loadControlCommands(device.id)
  editingPointId.value = null
  pointForm.value = emptyPointForm()
}

function realtimeValue(point: Point) {
  return realtimeValues.value[point.id]
}

function isWritableControlPoint(point: Point) {
  return point.accessMode !== 'R' && (point.modbusType === '0' || point.modbusType === '4')
}

async function loadControlCommands(deviceId: number | null = monitorSelectedDevice.value?.id ?? null) {
  controlLoading.value = true
  try {
    const params = new URLSearchParams()
    if (deviceId) params.set('deviceId', String(deviceId))
    if (controlStatusFilter.value) params.set('status', controlStatusFilter.value)
    const query = params.toString() ? `?${params}` : ''
    controlCommands.value = await apiFetch<ControlCommand[]>(`/api/control/commands${query}`)
  } finally {
    controlLoading.value = false
  }
}

async function sendControl(point: Point) {
  const current = realtimeValue(point)
  const defaultValue = point.dataType === 'BOOLEAN' ? (current?.value === '1' ? '0' : '1') : (current?.value ?? '')
  const input = window.prompt(`下发控制：${point.name}，请输入目标值`, defaultValue)
  if (input === null) return
  const target = Number(input)
  if (!Number.isFinite(target)) {
    window.alert('目标值必须是数字，布尔点请填 0 或 1')
    return
  }
  const modbusArea = modbusAreaText(point.modbusType)
  const confirmed = window.confirm([
    '请确认控制下发',
    `设备：${monitorSelectedDevice.value?.name ?? point.deviceId}`,
    `点位：${point.name}（${point.code}）`,
    `当前值：${current?.value ?? '未知'}${point.unit || ''}`,
    `目标值：${target}${point.unit || ''}`,
    `Modbus：${modbusArea}`,
    `控制等级：${controlLevelText(point.controlLevel)}`,
    '确认后命令会写入设备通道。',
  ].join('\n'))
  if (!confirmed) return
  const command = await apiFetch<ControlCommand>('/api/control/commands', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pointId: point.id, targetValue: target, confirmed: true }),
  })
  await refreshMonitorPoints()
  await loadControlCommands(point.deviceId)
  if (command.status !== 'SUCCESS') {
    window.alert(command.message || '控制命令执行失败')
  }
}

function modbusAreaText(type: string) {
  const labels: Record<string, string> = { '0': '0区 Coil 线圈', '1': '1区 Discrete Input 只读', '3': '3区 Input Register 只读', '4': '4区 Holding Register 保持寄存器' }
  return labels[type] ?? `类型 ${type || '未知'}`
}

function controlLevelText(level: string) {
  const labels: Record<string, string> = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return labels[level || 'LOW'] ?? level
}

async function initHistoryPage() {
  if (!historyDeviceId.value && devices.value.length) historyDeviceId.value = devices.value[0].id
  await changeHistoryDevice(true)
}

async function handleHistoryDeviceChange() {
  await changeHistoryDevice(true)
}

async function changeHistoryDevice(loadValues = true) {
  if (!historyDeviceId.value) {
    historyPoints.value = []
    historyRows.value = []
    historyLatestRows.value = []
    return
  }
  historyPoints.value = await apiFetch<Point[]>(`/api/points?deviceId=${historyDeviceId.value}`)
  if (!historyPointId.value || !historyPoints.value.some((point) => point.id === historyPointId.value)) {
    historyPointId.value = historyPoints.value[0]?.id ?? null
  }
  historyLatestRows.value = await apiFetch<HistoryLatest[]>(`/api/history/latest?deviceId=${historyDeviceId.value}`)
  if (loadValues) await loadHistoryValues()
}

async function loadHistoryValues() {
  if (!historyPointId.value) return
  historyLoading.value = true
  try {
    const end = new Date()
    const start = new Date(end.getTime() - historyRangeHours.value * 60 * 60 * 1000)
    const params = new URLSearchParams({ pointId: String(historyPointId.value), start: start.toISOString(), end: end.toISOString() })
    historyRows.value = await apiFetch<HistoryValue[]>(`/api/history/values?${params}`)
  } finally {
    historyLoading.value = false
  }
}

function historyBarHeight(row: HistoryValue) {
  const value = Number(row.value)
  if (!Number.isFinite(value)) return 8
  const spread = historyMaxValue.value - historyMinValue.value
  if (spread <= 0) return 42
  return 12 + ((value - historyMinValue.value) / spread) * 68
}

function startCreateDevice() {
  editingDeviceId.value = null
  deviceForm.value = emptyDeviceForm()
  deviceForm.value.areaId = areas.value[0]?.id ?? null
  deviceModalOpen.value = true
}

function startEditDevice(device: Device) {
  editingDeviceId.value = device.id
  deviceForm.value = { ...device }
  deviceModalOpen.value = true
}

async function saveDevice() {
  if (savingDevice.value) return
  savingDevice.value = true
  deviceError.value = ''
  try {
    const payload = JSON.stringify(deviceForm.value)
    if (editingDeviceId.value) {
      await apiFetch<Device>(`/api/devices/${editingDeviceId.value}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: payload })
    } else {
      await apiFetch<Device>('/api/devices', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload })
    }
    editingDeviceId.value = null
    deviceForm.value = emptyDeviceForm()
    deviceModalOpen.value = false
    await loadDeviceData()
  } catch (error) {
    deviceError.value = error instanceof Error ? error.message : '设备保存失败'
  } finally {
    savingDevice.value = false
  }
}

async function deleteDevice(device: Device) {
  deviceError.value = ''
  try {
    await apiFetch<void>(`/api/devices/${device.id}`, { method: 'DELETE' })
    if (selectedDevice.value?.id === device.id) {
      selectedDevice.value = null
      points.value = []
    }
    await loadDeviceData()
  } catch (error) {
    deviceError.value = error instanceof Error ? error.message : '设备删除失败'
  }
}

function startCreatePoint() {
  editingPointId.value = null
  pointForm.value = emptyPointForm()
}

function startEditPoint(point: Point) {
  editingPointId.value = point.id
  pointForm.value = {
    name: point.name,
    code: point.code,
    dataType: point.dataType,
    unit: point.unit,
    address: point.address,
    accessMode: point.accessMode,
    scaleValue: point.scaleValue,
    sortOrder: point.sortOrder,
  }
}

async function savePoint() {
  if (!selectedDevice.value) {
    deviceError.value = '请先选择设备'
    return
  }
  savingPoint.value = true
  deviceError.value = ''
  try {
    const payload = JSON.stringify(pointForm.value)
    if (editingPointId.value) {
      await apiFetch<Point>(`/api/devices/${selectedDevice.value.id}/points/${editingPointId.value}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: payload })
    } else {
      await apiFetch<Point>(`/api/devices/${selectedDevice.value.id}/points`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload })
    }
    editingPointId.value = null
    pointForm.value = emptyPointForm()
    points.value = await apiFetch<Point[]>(`/api/points?deviceId=${selectedDevice.value.id}`)
    await loadDeviceData()
  } catch (error) {
    deviceError.value = error instanceof Error ? error.message : '点位保存失败'
  } finally {
    savingPoint.value = false
  }
}

async function deletePoint(point: Point) {
  if (!selectedDevice.value) return
  deviceError.value = ''
  try {
    await apiFetch<void>(`/api/devices/${selectedDevice.value.id}/points/${point.id}`, { method: 'DELETE' })
    points.value = await apiFetch<Point[]>(`/api/points?deviceId=${selectedDevice.value.id}`)
    await loadDeviceData()
  } catch (error) {
    deviceError.value = error instanceof Error ? error.message : '点位删除失败'
  }
}

watch([activeMenu, overviewTrendSeries, overviewRealtimeCount, totalPointCount], async () => {
  if (activeMenu.value !== 'overview') return
  await nextTick()
  renderOverviewChart()
})

watch(themeMode, (mode) => {
  localStorage.setItem(themeStorageKey, mode)
  document.documentElement.dataset.theme = mode
}, { immediate: true })

watch(overviewScore, (score) => {
  animateOverviewScore(score)
}, { immediate: true })

onMounted(async () => {
  await checkBackend()
  await loadCurrentUser()
  await nextTick()
  renderOverviewChart()
  window.addEventListener('resize', resizeOverviewChart)
  window.addEventListener('click', clearAlarmHighlightOnOutsideClick)
  monitorRefreshTimer = setInterval(() => {
    if (isAuthed.value && activeMenu.value === 'monitor') refreshMonitorPoints()
  }, 3000)
  overviewRefreshTimer = setInterval(() => {
    if (isAuthed.value && activeMenu.value === 'overview') loadOverviewData()
  }, 10000)
  hmiRefreshTimer = setInterval(() => {
    if (isAuthed.value && activeMenu.value === 'hmi') refreshHmiValues()
  }, 5000)
})

onUnmounted(() => {
  if (monitorRefreshTimer) clearInterval(monitorRefreshTimer)
  if (overviewRefreshTimer) clearInterval(overviewRefreshTimer)
  if (hmiRefreshTimer) clearInterval(hmiRefreshTimer)
  if (overviewScoreAnimation !== null) cancelAnimationFrame(overviewScoreAnimation)
  window.removeEventListener('resize', resizeOverviewChart)
  window.removeEventListener('click', clearAlarmHighlightOnOutsideClick)
  overviewChart?.dispose()
  overviewChart = null
})
</script>

<template>
  <main v-if="!isAuthed" class="login-shell" :data-theme="themeMode">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="brand-block">
        <p class="eyebrow">WEB SCADA CONTROL CENTER</p>
        <h1 id="login-title">工业数据中台</h1>
        <p>面向设备运行、报警响应和生产态势的后台管理入口。</p>
      </div>
      <form class="login-card" @submit.prevent="submitLogin">
        <div class="status-strip" role="status" aria-live="polite">
          <span :class="['status-dot', overallStatus === 'UP' ? 'is-ok' : 'is-warn']"></span>
          后端 {{ healthText }} · MySQL {{ dbStatus }} · Redis {{ redisStatus }}
        </div>
        <label><span>账号</span><input v-model="username" autocomplete="username" /></label>
        <label><span>密码</span><input v-model="password" type="password" autocomplete="current-password" placeholder="默认 admin" /></label>
        <p v-if="loginError" class="form-error">{{ loginError }}</p>
        <button type="submit" :disabled="loggingIn">{{ loggingIn ? '登录中' : '进入后台' }}</button>
        <button class="ghost" type="button" :disabled="checking" @click="checkBackend">{{ checking ? '检查中' : '检查连接' }}</button>
      </form>
    </section>
  </main>

  <main v-else :class="['app-shell', { 'sidebar-is-collapsed': sidebarCollapsed }]" :data-theme="themeMode">
    <aside class="sidebar" aria-label="后台菜单">
      <div class="product-mark">
        <span class="mark-grid" aria-hidden="true"><span></span></span>
        <div class="product-copy"><strong>Web SCADA</strong><small>{{ displayName }}</small></div>
        <button class="sidebar-toggle" type="button" :aria-label="sidebarCollapsed ? '展开菜单' : '折叠菜单'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 6 9 12l6 6" /></svg>
        </button>
      </div>
      <nav class="sidebar-nav" aria-label="主菜单">
        <p class="nav-section-label">运行与配置</p>
        <button v-for="item in menuItems" :key="item.key" :class="['nav-item', { active: activeMenu === item.key }]" type="button" :title="sidebarCollapsed ? item.label : undefined" @click="handleMenuClick(item.key)">
          <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" v-html="menuIconPath(item.key)"></svg>
          <span class="nav-copy"><strong>{{ item.label }}</strong><small>{{ item.helper }}</small></span>
        </button>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div><p class="eyebrow">{{ activeItem.helper }}</p><h2>{{ activeItem.label }}</h2></div>
        <div class="top-actions">
          <span class="user-chip">{{ displayName }}</span>
          <span class="health-pill"><span class="status-dot is-ok"></span> MySQL {{ dbStatus }}</span>
          <span class="health-pill"><span class="status-dot is-ok"></span> Redis {{ redisStatus }}</span>
          <button class="ghost compact theme-switch" type="button" @click="toggleTheme">{{ themeLabel }}</button>
          <button class="ghost compact" type="button" @click="demoModalOpen = true">弹窗示例</button>
          <button class="ghost compact" type="button" :disabled="checking" @click="checkBackend">刷新</button>
          <button class="ghost compact" type="button" @click="logout">退出</button>
        </div>
      </header>

      <section v-if="activeMenu === 'overview'" class="overview-page">
        <section class="overview-command-center">
          <div class="overview-command-head">
            <div>
              <span class="overview-state-label">SITUATION OVERVIEW</span>
              <h3>{{ overviewStateText }}</h3>
            </div>
            <button class="ghost compact" type="button" @click="loadOverviewData">刷新总览</button>
          </div>

          <div class="overview-summary-strip">
            <article><span>设备 / 点位</span><strong>{{ devices.length }} / {{ totalPointCount }}</strong></article>
            <article :class="{ warn: activeAlarmCount }"><span>当前报警</span><strong>{{ activeAlarmCount }}</strong></article>
            <article :class="{ warn: abnormalChannelCount }"><span>采集通道</span><strong>{{ healthyChannelCount }} / {{ collectChannels.length }}</strong></article>
            <article><span>实时覆盖率</span><strong>{{ overviewCoverageRate }}%</strong></article>
          </div>

          <div class="overview-situation-hero">
            <div v-for="(metric, index) in overviewFloatingMetrics" :key="metric.label" :class="['overview-float-metric', `pos-${index + 1}`, metric.tone]">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
            <div class="overview-score-core" :class="overviewScore >= 90 ? 'ok' : overviewScore >= 70 ? 'warn' : 'danger'">
              <strong class="overview-score-number">{{ animatedOverviewScore }}</strong>
              <span class="overview-score-label">运行评分</span>
            </div>
          </div>

          <div :class="['overview-alarm-ticker', { 'is-empty': !latestAlarms.length }]">
            <strong class="overview-alarm-title">告警动态</strong>
            <div class="overview-ticker-viewport">
              <div class="overview-ticker-track">
                <template v-if="latestAlarms.length">
                  <button v-for="alarm in latestAlarms" :key="alarm.id" class="overview-ticker-item" type="button" @click="openAlarmFromOverview(alarm)">
                    <b :class="alarm.level === '高' ? 'danger' : alarm.level === '中' ? 'warn' : 'info'">{{ alarm.level }}</b>
                    <em>{{ alarm.deviceName }}</em>
                    {{ alarm.message }}
                  </button>
                </template>
                <span v-else class="overview-ticker-item"><b class="info">INFO</b><em>系统</em>暂无告警</span>
              </div>
            </div>
          </div>
        </section>

        <section class="overview-main">
          <article class="panel overview-trend-panel">
            <div class="panel-head"><h3>实时数据覆盖</h3><span>{{ overviewLastUpdated || '等待刷新' }}</span></div>
            <div ref="overviewChartRef" class="overview-chart" role="img" :aria-label="`Redis 实时数据覆盖率 ${overviewCoverageRate}%`"></div>
            <div class="overview-kpi-row">
              <span>Redis 当前值 {{ overviewRealtimeCount }} 条</span>
              <span>点位台账 {{ totalPointCount }} 条</span>
              <span>覆盖率 {{ overviewCoverageRate }}%</span>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head"><h3>链路状态</h3><button class="ghost compact" type="button" @click="loadOverviewData">刷新总览</button></div>
            <dl class="status-list overview-status-list">
              <dt>后端服务</dt><dd :class="overallStatus === 'UP' ? 'state-ok' : 'state-warn'">{{ overallStatus }}</dd>
              <dt>MySQL 台账</dt><dd :class="dbStatus === 'UP' ? 'state-ok' : 'state-warn'">{{ dbStatus }}</dd>
              <dt>Redis 实时缓存</dt><dd :class="redisStatus === 'UP' ? 'state-ok' : 'state-warn'">{{ redisStatus }}</dd>
              <dt>采集通道</dt><dd :class="abnormalChannelCount ? 'state-warn' : 'state-ok'">{{ abnormalChannelCount ? '部分异常' : '正常' }}</dd>
            </dl>
          </article>
        </section>

      </section>

      <section v-else-if="activeMenu === 'devices'" class="device-layout">
        <section class="panel page-panel">
          <div class="panel-head"><h3>设备台账</h3><span>{{ deviceLoading ? '加载中' : `${devices.length} 台设备` }}</span></div>
          <NConfigProvider :theme="naiveTheme">
            <div class="toolbar device-toolbar">
              <label><span>区域</span><NSelect v-model:value="areaFilter" :options="deviceAreaFilterOptions" @update:value="loadDeviceData" /></label>
              <label><span>状态</span><NSelect v-model:value="statusFilter" :options="deviceStatusFilterOptions" @update:value="loadDeviceData" /></label>
              <button class="ghost compact" type="button" @click="loadDeviceData">刷新</button>
              <button class="primary compact" type="button" @click="startCreateDevice">新建设备</button>
            </div>
          </NConfigProvider>
          <p v-if="deviceError" class="form-error">{{ deviceError }}</p>
          <div class="table-wrap">
            <table>
              <thead><tr><th>设备</th><th>区域</th><th>状态</th><th>协议</th><th>通讯地址</th><th>点位</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="device in devices" :key="device.id" :class="{ selected: selectedDevice?.id === device.id }">
                  <td><button class="link-button" type="button" @click="selectDevice(device)">{{ device.name }}<small>{{ device.code }}</small></button></td>
                  <td>{{ device.areaName }}</td>
                  <td><span :class="['tag', device.status === '告警' ? 'danger' : device.status === '待机' ? 'idle' : 'ok']">{{ device.status }}</span></td>
                  <td>{{ device.protocol }}</td>
                  <td>{{ device.ipAddress }}{{ device.port ? `:${device.port}` : '' }}</td>
                  <td>{{ device.pointCount }}</td>
                  <td class="row-actions"><button class="ghost compact" type="button" @click="startEditDevice(device)">编辑</button><button class="ghost compact" type="button" @click="deleteDevice(device)">删除</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <aside class="panel side-panel">
          <div class="panel-head"><h3>设备点位</h3><span>{{ selectedDevice?.name ?? '未选择' }}</span></div>
          <div class="point-list">
            <div class="panel-head">
              <button class="ghost compact" type="button" :disabled="!selectedDevice" @click="selectedDevice && startEditDevice(selectedDevice)">编辑设备</button>
              <button class="ghost compact" type="button" :disabled="!selectedDevice" @click="startCreatePoint">新增点位</button>
            </div>
            <form class="point-form" @submit.prevent="savePoint">
              <label><span>名称</span><input v-model="pointForm.name" :disabled="!selectedDevice" /></label>
              <label><span>编码</span><input v-model="pointForm.code" :disabled="!selectedDevice" /></label>
              <label><span>数据类型</span><select v-model="pointForm.dataType" :disabled="!selectedDevice"><option v-for="item in pointDataTypeOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
              <label><span>读写</span><select v-model="pointForm.accessMode" :disabled="!selectedDevice"><option v-for="item in pointAccessModeOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
              <label><span>单位</span><select v-model="pointForm.unit" :disabled="!selectedDevice"><option v-for="item in pointUnitOptions" :key="item.itemCode || 'none'" :value="item.itemCode">{{ item.label }}</option></select></label>
              <label><span>地址</span><input v-model="pointForm.address" :disabled="!selectedDevice" /></label>
              <label><span>缩放</span><input v-model.number="pointForm.scaleValue" type="number" step="0.0001" :disabled="!selectedDevice" /></label>
              <label><span>排序</span><input v-model.number="pointForm.sortOrder" type="number" :disabled="!selectedDevice" /></label>
              <button class="primary" type="submit" :disabled="!selectedDevice || savingPoint">{{ savingPoint ? '保存中' : editingPointId ? '保存点位' : '创建点位' }}</button>
            </form>
            <article v-for="point in points" :key="point.id" :class="{ selected: editingPointId === point.id }">
              <div><strong>{{ point.name }}</strong><small>{{ point.code }} · {{ point.dataType }} · {{ point.address }} {{ point.unit }}</small></div>
              <div class="row-actions"><button class="ghost compact" type="button" @click="startEditPoint(point)">编辑</button><button class="ghost compact" type="button" @click="deletePoint(point)">删除</button></div>
            </article>
            <p v-if="!points.length" class="muted">选择设备后查看点位。</p>
          </div>
        </aside>
      </section>

      <section v-else-if="activeMenu === 'monitor'" class="monitor-layout">
        <section class="panel page-panel">
          <div class="panel-head"><h3>实时监控框架</h3><span>Redis 当前值 · 3 秒自动刷新</span></div>
          <div class="toolbar monitor-toolbar">
            <label><span>区域</span><select v-model="monitorAreaFilter" @change="changeMonitorArea"><option value="">全部区域</option><option v-for="area in areas" :key="area.id" :value="String(area.id)">{{ area.name }}</option></select></label>
            <label><span>设备</span><select v-model.number="monitorDeviceId" @change="refreshMonitorPoints"><option v-for="device in monitorDevices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label>
            <button class="ghost compact" type="button" @click="refreshMonitorPoints">刷新点位</button>
          </div>
          <div class="monitor-summary">
            <article><span>当前设备</span><strong>{{ monitorSelectedDevice?.name ?? '未选择' }}</strong><small>{{ monitorSelectedDevice?.code ?? '无设备' }}</small></article>
            <article><span>通讯协议</span><strong>{{ monitorSelectedDevice?.protocol ?? '-' }}</strong><small>{{ monitorSelectedDevice?.ipAddress }}{{ monitorSelectedDevice?.port ? `:${monitorSelectedDevice.port}` : '' }}</small></article>
            <article><span>点位总数</span><strong>{{ monitorPointCount }}</strong><small>来自点位台账</small></article>
            <article><span>可写点位</span><strong>{{ writablePointCount }}</strong><small>accessMode 非只读</small></article>
          </div>
          <div class="table-wrap">
            <table class="monitor-table">
              <thead><tr><th>点位</th><th>现场来源</th><th>数据类型</th><th>Modbus 地址</th><th>读写</th><th>当前值</th><th>质量</th><th>采集时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="point in points" :key="point.id">
                  <td><strong>{{ point.name }}</strong><small>{{ point.code }}</small></td>
                  <td>{{ point.sourceGroup || '-' }}<small>{{ point.ioType || point.sourceSheet }}</small></td>
                  <td>{{ point.dataType }}<small>{{ point.unit || '无单位' }}</small></td>
                  <td>{{ point.address || '-' }}<small>{{ point.modbusType ? `类型 ${point.modbusType}` : point.ioModule }}</small></td>
                  <td>{{ point.accessMode }}<small>{{ controlLevelText(point.controlLevel) }}</small></td>
                  <td><span class="placeholder-value">{{ realtimeValue(point)?.value ?? '待接入' }}{{ point.unit && realtimeValue(point) ? ` ${point.unit}` : '' }}</span></td>
                  <td><span :class="['tag', realtimeValue(point)?.quality === 'GOOD' ? 'ok' : realtimeValue(point)?.quality === 'BAD' ? 'danger' : 'idle']">{{ realtimeValue(point)?.quality ?? '未采集' }}</span></td>
                  <td>{{ realtimeValue(point)?.collectedAt ? new Date(realtimeValue(point)!.collectedAt).toLocaleTimeString() : '等待实时接口' }}</td>
                  <td><button class="ghost compact" type="button" :disabled="!isWritableControlPoint(point)" @click="sendControl(point)">{{ isWritableControlPoint(point) ? '下发' : '只读' }}</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <aside class="panel monitor-side">
          <div class="panel-head"><h3>控制命令</h3><span>{{ controlLoading ? '加载中' : `${controlCommands.length} 条` }}</span></div>
          <div class="toolbar compact-toolbar"><label><span>状态</span><select v-model="controlStatusFilter" @change="loadControlCommands()"><option value="">全部</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="PENDING">待执行</option></select></label><button class="ghost compact" type="button" @click="loadControlCommands()">刷新</button></div>
          <div class="command-list"><article v-for="command in controlCommands" :key="command.id"><span :class="['tag', command.status === 'SUCCESS' ? 'ok' : command.status === 'FAILED' ? 'danger' : 'idle']">{{ command.status }}</span><div><strong>{{ command.pointName }} → {{ command.targetValue }}</strong><small>{{ command.deviceName }} · {{ controlLevelText(command.controlLevel) }} · {{ command.requestedBy }} · {{ new Date(command.createdAt).toLocaleTimeString() }}</small><small>{{ command.message }}<template v-if="command.confirmed"> · 已确认</template></small></div></article><p v-if="!controlCommands.length && !controlLoading" class="muted">当前设备暂无控制命令。</p></div>
          <dl class="status-list contract-list"><dt>0 区</dt><dd>线圈，可写 0/1</dd><dt>1 区</dt><dd>离散输入，只读</dd><dt>3 区</dt><dd>输入寄存器，只读</dd><dt>4 区</dt><dd>保持寄存器，可写数值</dd></dl>
          <p class="muted">下发命令先写入命令表，再写 Modbus TCP 仿真器；实时采集继续从仿真器读取并刷新 Redis 当前值缓存。</p>
        </aside>
      </section>

      <section v-else-if="activeMenu === 'history'" class="history-page">
        <section class="panel page-panel">
          <div class="panel-head"><h3>历史趋势 MVP</h3><span>{{ historyLoading ? '查询中' : `${historyRows.length} 条采样` }}</span></div>
          <div class="toolbar alarm-toolbar">
            <label><span>设备</span><select v-model.number="historyDeviceId" @change="handleHistoryDeviceChange"><option v-for="device in devices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label>
            <label><span>点位</span><select v-model.number="historyPointId" @change="loadHistoryValues"><option v-for="point in historyPoints" :key="point.id" :value="point.id">{{ point.name }}</option></select></label>
            <label><span>时间</span><select v-model.number="historyRangeHours" @change="loadHistoryValues"><option :value="1">最近 1 小时</option><option :value="6">最近 6 小时</option><option :value="24">最近 24 小时</option></select></label>
            <button class="ghost compact" type="button" @click="loadHistoryValues">刷新历史</button>
          </div>
          <div class="monitor-summary">
            <article><span>当前点位</span><strong>{{ historySelectedPoint?.name ?? '未选择' }}</strong><small>{{ historySelectedPoint?.code ?? '-' }}</small></article>
            <article><span>采样策略</span><strong>变化 + 降频</strong><small>数字量变化写，模拟量 30 秒</small></article>
            <article><span>最小值</span><strong>{{ historyNumericRows.length ? historyMinValue.toFixed(2) : '-' }}</strong><small>当前查询范围</small></article>
            <article><span>最大值</span><strong>{{ historyNumericRows.length ? historyMaxValue.toFixed(2) : '-' }}</strong><small>当前查询范围</small></article>
          </div>
          <div class="trend-line" v-if="historyRows.length"><span v-for="row in historyRows.slice(-80)" :key="row.id" :title="`${row.value} · ${new Date(row.collectedAt).toLocaleString()}`" :style="{ height: `${historyBarHeight(row)}px` }"></span></div>
          <p v-else class="muted">暂无历史采样。采集器运行后，数字量变化或模拟量到达采样间隔会写入 MySQL。</p>
          <div class="table-wrap"><table><thead><tr><th>时间</th><th>点位</th><th>值</th><th>质量</th></tr></thead><tbody><tr v-for="row in historyRows.slice(-30).reverse()" :key="row.id"><td>{{ new Date(row.collectedAt).toLocaleString() }}</td><td>{{ row.pointName }}<small>{{ row.pointCode }}</small></td><td>{{ row.value }}</td><td><span :class="['tag', row.quality === 'GOOD' ? 'ok' : 'danger']">{{ row.quality }}</span></td></tr></tbody></table></div>
        </section>
        <aside class="panel monitor-side">
          <div class="panel-head"><h3>设备最新采样</h3><span>{{ historyLatestRows.length }} 点</span></div>
          <div class="command-list"><article v-for="row in historyLatestRows.slice(0, 20)" :key="row.pointId"><span :class="['tag', row.quality === 'GOOD' ? 'ok' : 'danger']">{{ row.quality }}</span><div><strong>{{ row.pointName }} → {{ row.value }}</strong><small>{{ new Date(row.collectedAt).toLocaleTimeString() }}</small></div></article><p v-if="!historyLatestRows.length" class="muted">当前设备暂无历史采样。</p></div>
          <p class="muted">当前没有引入时序库，历史数据先落 MySQL。后续数据量增加后，可把这层写入服务替换为 TDengine / TimescaleDB / InfluxDB。</p>
        </aside>
      </section>

      <section v-else-if="activeMenu === 'hmi'" class="hmi-page">
        <div style="grid-column: 1 / -1; display:flex; gap:8px"><button class="ghost compact" @click="hmiEditorOpen = false">现场展示</button><button class="ghost compact" @click="hmiEditorOpen = true">画布编辑</button></div>
        <HmiEditor v-show="hmiEditorOpen" />
        <section v-show="!hmiEditorOpen" class="hmi-canvas panel">
          <div class="panel-head"><div><h3>金斗河固定版 HMI</h3><span>展示型组态 · Redis 当前值 · {{ hmiLastUpdated || '等待刷新' }}</span></div><button class="ghost compact" type="button" @click="refreshHmiValues">刷新画面</button></div>
          <div class="hmi-process" aria-label="金斗河现场设备组态展示">
            <div class="hmi-water"><span>金斗河现场工艺线</span></div>
            <button v-for="(device, index) in hmiDevices" :key="device.id" type="button" :class="['hmi-node', hmiDeviceClass(device), { active: hmiSelectedDevice?.id === device.id }]" :style="{ '--x': `${12 + (index % 4) * 27}%`, '--y': `${18 + Math.floor(index / 4) * 27}%` }" @click="selectHmiDevice(device)">
              <span class="hmi-node-icon">{{ device.type === 'PLC' ? 'PLC' : device.type === '闸门' ? 'Gate' : device.type === '水泵' ? 'Pump' : 'DEV' }}</span>
              <strong>{{ device.name }}</strong>
              <small>{{ device.areaName }} · {{ hmiDeviceState(device) }}</small>
              <em v-if="hmiDeviceAlarmCount(device)">{{ hmiDeviceAlarmCount(device) }} 报警</em>
            </button>
          </div>
          <div class="hmi-legend"><span><i class="ok"></i>运行</span><span><i class="idle"></i>待机/未知</span><span><i class="danger"></i>报警</span><span>当前值 {{ hmiGoodValueCount }}/{{ hmiPoints.length }}</span></div>
        </section>

        <aside v-show="!hmiEditorOpen" class="panel hmi-detail">
          <div class="panel-head"><h3>{{ hmiSelectedDevice?.name ?? '未选择设备' }}</h3><span>{{ hmiSelectedDevice?.code ?? '-' }}</span></div>
          <dl class="status-list hmi-device-meta"><dt>区域</dt><dd>{{ hmiSelectedDevice?.areaName ?? '-' }}</dd><dt>协议</dt><dd>{{ hmiSelectedDevice?.protocol ?? '-' }}</dd><dt>地址</dt><dd>{{ hmiSelectedDevice?.ipAddress }}{{ hmiSelectedDevice?.port ? `:${hmiSelectedDevice.port}` : '' }}</dd><dt>点位</dt><dd>{{ hmiPoints.length }}</dd></dl>
          <div class="hmi-alarm-strip" :class="hmiSelectedAlarms.length ? 'danger' : 'ok'">
            <strong>{{ hmiSelectedAlarms.length ? '存在活动报警' : '无活动报警' }}</strong>
            <small>{{ hmiSelectedAlarms.length ? hmiSelectedAlarms[0].message : '当前设备未触发活动报警' }}</small>
          </div>
          <div class="hmi-point-grid">
            <article v-for="point in hmiKeyPoints" :key="point.id">
              <span :class="['status-dot', hmiValue(point)?.quality === 'GOOD' ? 'is-ok' : 'is-warn']"></span>
              <div><strong>{{ point.name }}</strong><small>{{ point.code }} · {{ modbusAreaText(point.modbusType) }}</small></div>
              <b>{{ hmiPointState(point) }}</b>
            </article>
            <p v-if="!hmiKeyPoints.length" class="muted">当前设备暂无点位数据。</p>
          </div>
        </aside>
      </section>

      <section v-else-if="activeMenu === 'alarms'" class="alarm-page"><section class="panel page-panel"><div class="panel-head"><h3>报警事件</h3><span>{{ alarmLoading ? '刷新中' : `${alarmStatusText(alarmStatusFilter)} ${alarmRows.length} 条` }}</span></div><div class="toolbar alarm-toolbar"><label><span>状态</span><select v-model="alarmStatusFilter" @change="loadAlarms"><option value="ACTIVE">活动中</option><option value="ACKED">已确认</option><option value="RECOVERED">已恢复</option><option value="ALL">全部</option></select></label><button class="ghost compact" type="button" @click="loadAlarms">刷新报警</button></div><div class="alarm-list"><article v-for="alarm in alarmRows" :key="alarm.id" :class="{ highlighted: alarm.id === highlightedAlarmId }"><span :class="['alarm-level', alarm.level === '高' ? 'danger' : alarm.level === '中' ? 'warn' : 'info']">{{ alarm.level }}</span><div><strong>{{ alarm.message }} · {{ alarmStatusText(alarm.status) }}</strong><small>{{ alarm.deviceName }} · {{ alarm.pointName }} · 值 {{ alarm.value }} · 发生 {{ new Date(alarm.occurredAt).toLocaleTimeString() }}<template v-if="alarm.acknowledgedAt"> · {{ alarm.acknowledgedBy }} 已确认</template><template v-if="alarm.recoveredAt"> · 恢复 {{ new Date(alarm.recoveredAt).toLocaleTimeString() }}</template></small><small v-if="alarm.ackNote">备注：{{ alarm.ackNote }}</small></div><button class="ghost compact" type="button" :disabled="alarm.status === 'RECOVERED'" @click="acknowledgeAlarm(alarm)">{{ alarm.status === 'ACKED' ? '补充备注' : alarm.status === 'RECOVERED' ? '已恢复' : '确认' }}</button></article><p v-if="!alarmRows.length && !alarmLoading" class="muted">当前状态下没有报警事件。</p></div></section><section class="panel page-panel"><div class="panel-head"><h3>报警规则维护</h3><span>{{ alarmRuleLoading ? '加载中' : `规则 ${alarmRules.length} 条` }}</span></div><div class="toolbar alarm-toolbar"><label><span>设备</span><select v-model.number="alarmRuleDeviceId" @change="loadAlarmRules"><option :value="null">全部设备</option><option v-for="device in devices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label><button class="ghost compact" type="button" @click="loadAlarmRules">刷新规则</button></div><form class="alarm-rule-form" @submit.prevent="createAlarmRule"><label><span>点位</span><select v-model.number="alarmRuleForm.pointId"><option v-for="point in alarmRulePoints" :key="point.id" :value="point.id">{{ point.name }}</option></select></label><label><span>规则名称</span><input v-model="alarmRuleForm.ruleName" /></label><label><span>类型</span><select v-model="alarmRuleForm.ruleType"><option v-for="item in alarmRuleTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option></select></label><label><span>操作符</span><select v-model="alarmRuleForm.operator"><option value=">">大于</option><option value="<">小于</option><option value="=">等于</option></select></label><label><span>阈值</span><input v-model.number="alarmRuleForm.thresholdValue" type="number" step="0.0001" /></label><label><span>等级</span><select v-model="alarmRuleForm.level"><option v-for="level in alarmLevelOptions" :key="level" :value="level">{{ level }}</option></select></label><label class="span-2"><span>报警内容</span><input v-model="alarmRuleForm.message" /></label><label><span>启用</span><select v-model="alarmRuleForm.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></label><button class="primary compact" type="submit">新增规则</button></form><div class="table-wrap"><table><thead><tr><th>点位</th><th>规则</th><th>类型</th><th>阈值</th><th>等级</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="rule in alarmRules" :key="rule.id"><td>{{ rule.pointName }}<small>{{ rule.pointCode }}</small></td><td>{{ rule.ruleName }}<small>{{ rule.message }}</small></td><td>{{ ruleTypeText(rule.ruleType) }}</td><td>{{ rule.thresholdValue ?? '-' }}</td><td>{{ rule.level }}</td><td><span :class="['tag', rule.enabled ? 'ok' : 'idle']">{{ rule.enabled ? '启用' : '停用' }}</span></td><td class="row-actions"><button class="ghost compact" type="button" @click="editAlarmRule(rule)">编辑</button><button class="ghost compact" type="button" @click="deleteAlarmRule(rule)">删除</button></td></tr></tbody></table></div></section></section>


      <section v-else-if="activeMenu === 'settings'" class="collector-page">
        <section class="panel page-panel">
          <div class="panel-head"><h3>采集通道配置</h3><span>{{ collectChannelLoading ? '加载中' : `启用 ${enabledCollectChannelCount}/${collectChannels.length}` }}</span></div>
          <div class="toolbar alarm-toolbar">
            <label><span>通道</span><select v-model.number="selectedCollectChannelId" @change="changeCollectChannel"><option v-for="channel in collectChannels" :key="channel.id" :value="channel.id">{{ channel.name }}</option></select></label>
            <button class="ghost compact" type="button" @click="loadCollectChannels">刷新通道</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>设备</th><th>模式/协议</th><th>地址</th><th>周期</th><th>诊断</th><th>状态</th><th>最后成功</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="channel in collectChannels" :key="channel.id" :class="{ selected: selectedCollectChannelId === channel.id }">
                  <td><button class="link-button" type="button" @click="selectedCollectChannelId = channel.id; changeCollectChannel()">{{ channel.name }}<small>{{ channel.deviceName }} · {{ channel.code }}</small></button></td>
                  <td>{{ channelModeText(channel.channelMode) }}<small>{{ channel.protocol }}</small></td>
                  <td>{{ channel.host }}{{ channel.port ? `:${channel.port}` : '' }}<small>站号 {{ channel.slaveId }} · 超时 {{ channel.timeoutMs }}ms · 重试 {{ channel.retryCount }}</small></td>
                  <td>{{ channel.pollIntervalMs }} ms<small>点位 {{ channel.pointCount }}</small></td>
                  <td>{{ channel.lastLatencyMs == null ? '-' : `${channel.lastLatencyMs}ms` }}<small>{{ channel.lastError || `连续失败 ${channel.consecutiveFailures}` }}</small></td>
                  <td><span :class="['tag', channel.enabled && channel.status !== 'DIAG_FAILED' ? 'ok' : channel.status === 'DIAG_FAILED' ? 'danger' : 'idle']">{{ channel.enabled ? channel.status : '停用' }}</span></td>
                  <td>{{ channel.lastSuccessAt ? new Date(channel.lastSuccessAt).toLocaleTimeString() : channel.lastPolledAt ? new Date(channel.lastPolledAt).toLocaleTimeString() : '未成功' }}</td>
                  <td class="row-actions"><button class="ghost compact" type="button" @click="editCollectChannel(channel)">编辑</button><button class="ghost compact" type="button" @click="testCollectConnection(channel)">测试连接</button><button class="ghost compact" type="button" @click="testCollectRead(channel)">测试读取</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <aside class="panel side-panel">
          <div class="panel-head"><h3>通道点位绑定</h3><span>{{ selectedCollectChannel?.name ?? '未选择' }}</span></div>
          <p class="muted">当前页面支持仿真/真实 Modbus TCP 通道诊断。真实 PLC 接入时，先改 IP 和端口，再用测试连接、测试读取逐点排查。</p>
          <div v-if="collectDiagnosticResult" class="diagnostic-card"><strong>{{ collectDiagnosticResult.success ? '诊断成功' : '诊断失败' }}</strong><small>{{ collectDiagnosticResult.message }} · {{ collectDiagnosticResult.latencyMs }}ms</small><small v-if="collectDiagnosticResult.pointName">{{ collectDiagnosticResult.pointName }} / {{ collectDiagnosticResult.pointCode }} = {{ collectDiagnosticResult.rawValue || '-' }}</small></div>
          <div class="point-list collector-bindings">
            <article v-for="binding in collectBindings" :key="binding.id">
              <div><strong>{{ binding.pointName }}</strong><small>{{ binding.pointCode }} · {{ binding.modbusType || '未知区' }} · {{ binding.address || '-' }}</small></div>
              <div class="row-actions"><button class="ghost compact" type="button" @click="selectedCollectChannel && testCollectRead(selectedCollectChannel, binding)">读一次</button><button class="ghost compact" type="button" @click="toggleCollectBinding(binding)">{{ binding.enabled ? '停用' : '启用' }}</button></div>
            </article>
            <p v-if="!collectBindings.length" class="muted">选择采集通道后查看点位绑定。</p>
          </div>
        </aside>
      </section>

      <section v-else class="empty-state"><p class="eyebrow">{{ activeItem.label }}</p><h3>{{ activeItem.label }}页面骨架已预留</h3><p>当前阶段已接入设备、区域和点位数据模型，后续可继续扩展实时采集、报警规则和历史数据。</p></section>
    </section>
    <AppModal v-model="deviceModalOpen" :title="deviceModalTitle" :mode="editingDeviceId ? 'edit' : 'create'" tone="default" size="lg" :confirm-text="savingDevice ? '保存中' : editingDeviceId ? '保存修改' : '创建设备'" :theme-mode="themeMode" :close-on-confirm="false" @confirm="saveDevice">
      <NConfigProvider :theme="naiveTheme">
        <form class="modal-form-grid device-modal-form" @submit.prevent="saveDevice">
          <label><span>所属区域</span><NSelect v-model:value="deviceForm.areaId" :options="deviceAreaSelectOptions" placeholder="请选择区域" clearable /></label>
          <label><span>设备类型</span><NSelect v-model:value="deviceForm.type" :options="deviceTypeSelectOptions" placeholder="请选择类型" /></label>
          <label><span>设备名称</span><NInput v-model:value="deviceForm.name" placeholder="请输入设备名称" /></label>
          <label><span>设备编码</span><NInput v-model:value="deviceForm.code" placeholder="请输入唯一编码" /></label>
          <label><span>运行状态</span><NSelect v-model:value="deviceForm.status" :options="deviceStatusSelectOptions" placeholder="请选择状态" /></label>
          <label><span>通讯协议</span><NSelect v-model:value="deviceForm.protocol" :options="deviceProtocolSelectOptions" placeholder="请选择协议" /></label>
          <label><span>IP 地址</span><NInput v-model:value="deviceForm.ipAddress" placeholder="127.0.0.1" /></label>
          <label><span>端口</span><NInputNumber v-model:value="deviceForm.port" :min="1" :max="65535" :show-button="false" placeholder="1502" /></label>
          <label class="span-2"><span>说明</span><NInput v-model:value="deviceForm.description" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="请输入设备说明" /></label>
          <button class="hidden-submit" type="submit" :disabled="savingDevice" aria-hidden="true" tabindex="-1"></button>
        </form>
      </NConfigProvider>
    </AppModal>
    <AppModal v-model="demoModalOpen" title="编辑采集通道" mode="edit" tone="default" size="lg" confirm-text="保存配置" :theme-mode="themeMode">
      <NConfigProvider :theme="naiveTheme">
        <NTabs type="line" animated class="modal-tabs">
          <NTabPane name="basic" tab="基础信息">
            <div class="modal-form-grid">
              <label><span>通道名称</span><NInput v-model:value="demoChannelName" /></label>
              <label><span>通道模式</span><NSelect v-model:value="demoChannelMode" :options="demoChannelModeOptions" /></label>
              <label><span>设备编号</span><NInput value="JDH-PLC-01" /></label>
              <label><span>所属区域</span><NInput value="金斗河现场" /></label>
              <label class="span-2"><span>说明</span><NInput v-model:value="demoDescription" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" /></label>
            </div>
          </NTabPane>
          <NTabPane name="connection" tab="通讯参数">
            <div class="modal-form-grid">
              <label><span>主机地址</span><NInput v-model:value="demoHost" /></label>
              <label><span>端口</span><NInput v-model:value="demoPort" /></label>
              <label><span>站号</span><NInput value="1" /></label>
              <label><span>协议</span><NInput value="MODBUS_TCP" /></label>
              <label><span>字节序</span><NSelect value="ABCD" :options="[{ label: 'ABCD', value: 'ABCD' }, { label: 'DCBA', value: 'DCBA' }]" /></label>
              <label><span>连接策略</span><NSelect value="KEEP_ALIVE" :options="[{ label: '长连接', value: 'KEEP_ALIVE' }, { label: '按次连接', value: 'PER_POLL' }]" /></label>
            </div>
          </NTabPane>
          <NTabPane name="collect" tab="采集策略">
            <div class="modal-form-grid">
              <label><span>采集周期</span><NInput v-model:value="demoInterval" /></label>
              <label><span>超时时间</span><NInput v-model:value="demoTimeout" /></label>
              <label><span>重试次数</span><NInput value="1" /></label>
              <label><span>失败阈值</span><NInput value="3 次" /></label>
              <label><span>历史采样</span><NSelect value="CHANGE" :options="[{ label: '变化写入', value: 'CHANGE' }, { label: '定时写入', value: 'INTERVAL' }]" /></label>
              <label><span>质量策略</span><NSelect value="MARK_STALE" :options="[{ label: '超时标记 STALE', value: 'MARK_STALE' }, { label: '丢弃异常值', value: 'DROP_BAD' }]" /></label>
            </div>
          </NTabPane>
          <NTabPane name="alarm" tab="报警联动">
            <div class="modal-form-grid">
              <label><span>离线报警</span><NSelect value="ON" :options="[{ label: '启用', value: 'ON' }, { label: '停用', value: 'OFF' }]" /></label>
              <label><span>报警等级</span><NSelect value="中" :options="[{ label: '高', value: '高' }, { label: '中', value: '中' }, { label: '低', value: '低' }]" /></label>
              <label><span>通知策略</span><NSelect value="DASHBOARD" :options="[{ label: '仅首页展示', value: 'DASHBOARD' }, { label: '首页 + 弹窗', value: 'POPUP' }]" /></label>
              <label><span>恢复确认</span><NSelect value="NO" :options="[{ label: '不需要', value: 'NO' }, { label: '需要', value: 'YES' }]" /></label>
              <label class="span-2"><span>报警模板</span><NInput value="采集通道 {channelName} 连续失败，请检查 PLC 网络与地址配置。" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" /></label>
            </div>
          </NTabPane>
        </NTabs>
      </NConfigProvider>
    </AppModal>
  </main>
</template>

