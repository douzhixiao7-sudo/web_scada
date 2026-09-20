<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

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
  sixnetAddress: string
  iconicsPath: string
  remark: string
}
type RealtimeValue = { pointId: number; deviceId: number; pointCode: string; value: string; quality: string; collectedAt: string }
type AlarmEvent = { id: number; alarmKey: string; deviceId: number; deviceName: string; pointId: number; pointCode: string; pointName: string; level: string; message: string; value: string; quality: string; status: string; occurredAt: string; lastSeenAt: string; recoveredAt?: string | null; acknowledgedAt?: string | null; acknowledgedBy: string; ackNote: string }
type AlarmRule = { id: number; pointId: number; pointCode: string; pointName: string; ruleName: string; ruleType: string; operator: string; thresholdValue: number | null; level: string; message: string; enabled: boolean }
type CollectChannel = { id: number; deviceId: number; deviceName: string; deviceCode: string; name: string; code: string; protocol: string; host: string; port: number | null; pollIntervalMs: number; enabled: boolean; status: string; pointCount: number; lastPolledAt: string | null }
type CollectBinding = { id: number; channelId: number; pointId: number; pointCode: string; pointName: string; address: string; modbusType: string; accessMode: string; enabled: boolean }

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
const alarmRules = ref<AlarmRule[]>([])
const alarmRuleDeviceId = ref<number | null>(null)
const alarmRuleLoading = ref(false)
const collectChannels = ref<CollectChannel[]>([])
const collectBindings = ref<CollectBinding[]>([])
const collectChannelLoading = ref(false)
const selectedCollectChannelId = ref<number | null>(null)
let monitorRefreshTimer: ReturnType<typeof setInterval> | null = null
const selectedDevice = ref<Device | null>(null)
const deviceError = ref('')
const deviceLoading = ref(false)
const savingDevice = ref(false)
const savingPoint = ref(false)
const editingDeviceId = ref<number | null>(null)
const editingPointId = ref<number | null>(null)
const areaFilter = ref('')
const statusFilter = ref('')
const monitorAreaFilter = ref('')
const monitorDeviceId = ref<number | null>(null)
const deviceForm = ref<DeviceForm>(emptyDeviceForm())
const pointForm = ref<PointForm>(emptyPointForm())

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
const pointDataTypeOptions = computed(() => dictItems('point_data_type'))
const pointAccessModeOptions = computed(() => dictItems('point_access_mode'))
const pointUnitOptions = computed(() => dictItems('point_unit'))
const monitorDevices = computed(() => devices.value.filter((device) => !monitorAreaFilter.value || String(device.areaId) === monitorAreaFilter.value))
const monitorSelectedDevice = computed(() => devices.value.find((device) => device.id === monitorDeviceId.value) ?? monitorDevices.value[0] ?? null)
const monitorPointCount = computed(() => points.value.length)
const writablePointCount = computed(() => points.value.filter((point) => point.accessMode !== 'R').length)
const selectedCollectChannel = computed(() => collectChannels.value.find((channel) => channel.id === selectedCollectChannelId.value) ?? collectChannels.value[0] ?? null)
const enabledCollectChannelCount = computed(() => collectChannels.value.filter((channel) => channel.enabled).length)

const trendBars = [42, 58, 53, 66, 71, 64, 77, 73, 81, 76, 88, 84]

function emptyDeviceForm(): DeviceForm {
  return { areaId: null, name: '', code: '', type: '闸门', status: '运行', protocol: 'MODBUS_TCP', ipAddress: '127.0.0.1', port: 1502, description: '' }
}

function emptyPointForm(): PointForm {
  return { name: '', code: '', dataType: 'DECIMAL', unit: '', address: '', accessMode: 'R', scaleValue: 1, sortOrder: 10 }
}

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

async function refreshMonitorPoints() {
  if (!monitorSelectedDevice.value) return
  const device = monitorSelectedDevice.value
  if (selectedDevice.value?.id === device.id && points.value.length) {
    const valueRows = await apiFetch<RealtimeValue[]>(`/api/realtime/values?deviceId=${device.id}`)
    realtimeValues.value = Object.fromEntries(valueRows.map((value) => [value.pointId, value]))
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
  } finally {
    alarmRuleLoading.value = false
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
      ruleName: rule.ruleName,
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
  editingPointId.value = null
  pointForm.value = emptyPointForm()
}

function realtimeValue(point: Point) {
  return realtimeValues.value[point.id]
}

function startCreateDevice() {
  editingDeviceId.value = null
  deviceForm.value = emptyDeviceForm()
  deviceForm.value.areaId = areas.value[0]?.id ?? null
}

function startEditDevice(device: Device) {
  editingDeviceId.value = device.id
  deviceForm.value = { ...device }
}

async function saveDevice() {
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

onMounted(async () => {
  await checkBackend()
  await loadCurrentUser()
  monitorRefreshTimer = setInterval(() => {
    if (isAuthed.value && activeMenu.value === 'monitor') refreshMonitorPoints()
  }, 3000)
})

onUnmounted(() => {
  if (monitorRefreshTimer) clearInterval(monitorRefreshTimer)
})
</script>

<template>
  <main v-if="!isAuthed" class="login-shell">
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

  <main v-else class="app-shell">
    <aside class="sidebar" aria-label="后台菜单">
      <div class="product-mark"><span class="mark-grid"></span><div><strong>Web SCADA</strong><small>{{ displayName }}</small></div></div>
      <nav>
        <button v-for="item in menuItems" :key="item.key" :class="['nav-item', { active: activeMenu === item.key }]" type="button" @click="activeMenu = item.key">
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span><span><strong>{{ item.label }}</strong><small>{{ item.helper }}</small></span>
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
          <button class="ghost compact" type="button" :disabled="checking" @click="checkBackend">刷新</button>
          <button class="ghost compact" type="button" @click="logout">退出</button>
        </div>
      </header>

      <section v-if="activeMenu === 'overview'" class="content-grid overview-grid">
        <article class="metric-card strong"><span>系统状态</span><strong>{{ overallStatus }}</strong><small>Actuator 健康检查</small></article>
        <article class="metric-card"><span>设备总数</span><strong>{{ devices.length }}</strong><small>来自 MySQL 设备台账</small></article>
        <article class="metric-card"><span>运行设备</span><strong>{{ onlineDeviceCount }}</strong><small>状态为运行</small></article>
        <article class="metric-card warn"><span>告警设备</span><strong>{{ alarmDeviceCount }}</strong><small>状态为告警</small></article>
        <article class="panel wide"><div class="panel-head"><h3>实时负载趋势</h3><span>模拟数据</span></div><div class="trend" aria-label="实时负载趋势模拟图"><span v-for="(bar, index) in trendBars" :key="index" :style="{ height: `${bar}%` }"></span></div></article>
        <article class="panel"><div class="panel-head"><h3>连接状态</h3><span>{{ healthText }}</span></div><dl class="status-list"><dt>后端</dt><dd>{{ overallStatus }}</dd><dt>MySQL</dt><dd>{{ dbStatus }}</dd><dt>Redis</dt><dd>{{ redisStatus }}</dd></dl></article>
      </section>

      <section v-else-if="activeMenu === 'devices'" class="device-layout">
        <section class="panel page-panel">
          <div class="panel-head"><h3>设备台账</h3><span>{{ deviceLoading ? '加载中' : `${devices.length} 台设备` }}</span></div>
          <div class="toolbar">
            <label><span>区域</span><select v-model="areaFilter" @change="loadDeviceData"><option value="">全部区域</option><option v-for="area in areas" :key="area.id" :value="String(area.id)">{{ area.name }}</option></select></label>
            <label><span>状态</span><select v-model="statusFilter" @change="loadDeviceData"><option value="">全部状态</option><option v-for="item in deviceStatusOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
            <button class="ghost compact" type="button" @click="loadDeviceData">刷新</button>
            <button class="primary compact" type="button" @click="startCreateDevice">新建设备</button>
          </div>
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
          <div class="panel-head"><h3>{{ editingDeviceId ? '编辑设备' : '设备维护' }}</h3><span>{{ selectedDevice?.name ?? '未选择' }}</span></div>
          <form class="device-form" @submit.prevent="saveDevice">
            <label><span>区域</span><select v-model.number="deviceForm.areaId"><option :value="null">请选择</option><option v-for="area in areas" :key="area.id" :value="area.id">{{ area.name }}</option></select></label>
            <label><span>名称</span><input v-model="deviceForm.name" /></label>
            <label><span>编码</span><input v-model="deviceForm.code" /></label>
            <label><span>类型</span><select v-model="deviceForm.type"><option v-for="item in deviceTypeOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
            <label><span>状态</span><select v-model="deviceForm.status"><option v-for="item in deviceStatusOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
            <label><span>协议</span><select v-model="deviceForm.protocol"><option v-for="item in deviceProtocolOptions" :key="item.itemCode" :value="item.itemCode">{{ item.label }}</option></select></label>
            <label><span>IP 地址</span><input v-model="deviceForm.ipAddress" /></label>
            <label><span>端口</span><input v-model.number="deviceForm.port" type="number" /></label>
            <label class="span-2"><span>说明</span><input v-model="deviceForm.description" /></label>
            <button class="primary" type="submit" :disabled="savingDevice">{{ savingDevice ? '保存中' : editingDeviceId ? '保存修改' : '创建设备' }}</button>
          </form>

          <div class="point-list">
            <div class="panel-head">
              <h3>设备点位</h3>
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
              <thead><tr><th>点位</th><th>现场来源</th><th>数据类型</th><th>Modbus 地址</th><th>读写</th><th>当前值</th><th>质量</th><th>采集时间</th></tr></thead>
              <tbody>
                <tr v-for="point in points" :key="point.id">
                  <td><strong>{{ point.name }}</strong><small>{{ point.code }}</small></td>
                  <td>{{ point.sourceGroup || '-' }}<small>{{ point.ioType || point.sourceSheet }}</small></td>
                  <td>{{ point.dataType }}<small>{{ point.unit || '无单位' }}</small></td>
                  <td>{{ point.address || '-' }}<small>{{ point.modbusType ? `类型 ${point.modbusType}` : point.ioModule }}</small></td>
                  <td>{{ point.accessMode }}</td>
                  <td><span class="placeholder-value">{{ realtimeValue(point)?.value ?? '待接入' }}{{ point.unit && realtimeValue(point) ? ` ${point.unit}` : '' }}</span></td>
                  <td><span :class="['tag', realtimeValue(point)?.quality === 'GOOD' ? 'ok' : realtimeValue(point)?.quality === 'BAD' ? 'danger' : 'idle']">{{ realtimeValue(point)?.quality ?? '未采集' }}</span></td>
                  <td>{{ realtimeValue(point)?.collectedAt ? new Date(realtimeValue(point)!.collectedAt).toLocaleTimeString() : '等待实时接口' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <aside class="panel monitor-side">
          <div class="panel-head"><h3>实时值接口契约</h3><span>MVP 预留</span></div>
          <dl class="status-list contract-list"><dt>pointId</dt><dd>点位 ID</dd><dt>value</dt><dd>当前值</dd><dt>quality</dt><dd>GOOD / BAD / STALE</dd><dt>collectedAt</dt><dd>采集时间</dd></dl>
          <p class="muted">当前阶段由后端内置仿真采集器按通道点位绑定生成实时值，写入 Redis 当前值缓存；暂不连接真实 PLC，不写历史库。</p>
        </aside>
      </section>

      <section v-else-if="activeMenu === 'alarms'" class="alarm-page"><section class="panel page-panel"><div class="panel-head"><h3>报警事件</h3><span>{{ alarmLoading ? '刷新中' : `${alarmStatusText(alarmStatusFilter)} ${alarmRows.length} 条` }}</span></div><div class="toolbar alarm-toolbar"><label><span>状态</span><select v-model="alarmStatusFilter" @change="loadAlarms"><option value="ACTIVE">活动中</option><option value="ACKED">已确认</option><option value="RECOVERED">已恢复</option><option value="ALL">全部</option></select></label><button class="ghost compact" type="button" @click="loadAlarms">刷新报警</button></div><div class="alarm-list"><article v-for="alarm in alarmRows" :key="alarm.id"><span :class="['alarm-level', alarm.level === '高' ? 'danger' : alarm.level === '中' ? 'warn' : 'info']">{{ alarm.level }}</span><div><strong>{{ alarm.message }} · {{ alarmStatusText(alarm.status) }}</strong><small>{{ alarm.deviceName }} · {{ alarm.pointName }} · 值 {{ alarm.value }} · 发生 {{ new Date(alarm.occurredAt).toLocaleTimeString() }}<template v-if="alarm.acknowledgedAt"> · {{ alarm.acknowledgedBy }} 已确认</template><template v-if="alarm.recoveredAt"> · 恢复 {{ new Date(alarm.recoveredAt).toLocaleTimeString() }}</template></small><small v-if="alarm.ackNote">备注：{{ alarm.ackNote }}</small></div><button class="ghost compact" type="button" :disabled="alarm.status === 'RECOVERED'" @click="acknowledgeAlarm(alarm)">{{ alarm.status === 'ACKED' ? '补充备注' : alarm.status === 'RECOVERED' ? '已恢复' : '确认' }}</button></article><p v-if="!alarmRows.length && !alarmLoading" class="muted">当前状态下没有报警事件。</p></div></section><section class="panel page-panel"><div class="panel-head"><h3>报警规则维护</h3><span>{{ alarmRuleLoading ? '加载中' : `规则 ${alarmRules.length} 条` }}</span></div><div class="toolbar alarm-toolbar"><label><span>设备</span><select v-model.number="alarmRuleDeviceId" @change="loadAlarmRules"><option :value="null">全部设备</option><option v-for="device in devices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label><button class="ghost compact" type="button" @click="loadAlarmRules">刷新规则</button></div><div class="table-wrap"><table><thead><tr><th>点位</th><th>规则</th><th>类型</th><th>阈值</th><th>等级</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="rule in alarmRules" :key="rule.id"><td>{{ rule.pointName }}<small>{{ rule.pointCode }}</small></td><td>{{ rule.ruleName }}<small>{{ rule.message }}</small></td><td>{{ rule.ruleType }}</td><td>{{ rule.thresholdValue ?? '-' }}</td><td>{{ rule.level }}</td><td><span :class="['tag', rule.enabled ? 'ok' : 'idle']">{{ rule.enabled ? '启用' : '停用' }}</span></td><td><button class="ghost compact" type="button" @click="editAlarmRule(rule)">编辑</button></td></tr></tbody></table></div></section></section>


      <section v-else-if="activeMenu === 'settings'" class="collector-page">
        <section class="panel page-panel">
          <div class="panel-head"><h3>采集通道配置</h3><span>{{ collectChannelLoading ? '加载中' : `启用 ${enabledCollectChannelCount}/${collectChannels.length}` }}</span></div>
          <div class="toolbar alarm-toolbar">
            <label><span>通道</span><select v-model.number="selectedCollectChannelId" @change="changeCollectChannel"><option v-for="channel in collectChannels" :key="channel.id" :value="channel.id">{{ channel.name }}</option></select></label>
            <button class="ghost compact" type="button" @click="loadCollectChannels">刷新通道</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>设备</th><th>协议</th><th>地址</th><th>周期</th><th>点位</th><th>状态</th><th>最后采集</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="channel in collectChannels" :key="channel.id" :class="{ selected: selectedCollectChannelId === channel.id }">
                  <td><button class="link-button" type="button" @click="selectedCollectChannelId = channel.id; changeCollectChannel()">{{ channel.name }}<small>{{ channel.deviceName }} · {{ channel.code }}</small></button></td>
                  <td>{{ channel.protocol }}</td>
                  <td>{{ channel.host }}{{ channel.port ? `:${channel.port}` : '' }}</td>
                  <td>{{ channel.pollIntervalMs }} ms</td>
                  <td>{{ channel.pointCount }}</td>
                  <td><span :class="['tag', channel.enabled ? 'ok' : 'idle']">{{ channel.enabled ? channel.status : '停用' }}</span></td>
                  <td>{{ channel.lastPolledAt ? new Date(channel.lastPolledAt).toLocaleTimeString() : '未采集' }}</td>
                  <td class="row-actions"><button class="ghost compact" type="button" @click="editCollectChannel(channel)">编辑</button><button class="ghost compact" type="button" @click="markCollectPolled(channel)">模拟心跳</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <aside class="panel side-panel">
          <div class="panel-head"><h3>通道点位绑定</h3><span>{{ selectedCollectChannel?.name ?? '未选择' }}</span></div>
          <p class="muted">当前阶段只维护采集配置和点位绑定，不启动真实 PLC 采集线程。</p>
          <div class="point-list collector-bindings">
            <article v-for="binding in collectBindings" :key="binding.id">
              <div><strong>{{ binding.pointName }}</strong><small>{{ binding.pointCode }} · {{ binding.modbusType || '未知区' }} · {{ binding.address || '-' }}</small></div>
              <button class="ghost compact" type="button" @click="toggleCollectBinding(binding)">{{ binding.enabled ? '停用' : '启用' }}</button>
            </article>
            <p v-if="!collectBindings.length" class="muted">选择采集通道后查看点位绑定。</p>
          </div>
        </aside>
      </section>

      <section v-else class="empty-state"><p class="eyebrow">{{ activeItem.label }}</p><h3>{{ activeItem.label }}页面骨架已预留</h3><p>当前阶段已接入设备、区域和点位数据模型，后续可继续扩展实时采集、报警规则和历史数据。</p></section>
    </section>
  </main>
</template>

