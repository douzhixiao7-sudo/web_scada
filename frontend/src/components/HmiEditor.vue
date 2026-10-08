<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Moveable from 'vue3-moveable'
import Selecto from 'vue3-selecto'
import type { OnDrag, OnDragStart, OnDragGroup, OnDragGroupStart } from 'vue3-moveable'
import { readState, validBinding, type ReadBinding, type LiveValue } from './hmiReading'
import HmiIndustrialImage from './HmiIndustrialImage.vue'

const props = defineProps<{ active: boolean; screenId: number; read: <T>(url: string, options?: RequestInit) => Promise<T> }>()
type ReadDevice = { id: number; name: string }
type ReadPoint = { id: number; deviceId: number; name: string; unit: string; accessMode: string }
const readDevices = ref<ReadDevice[]>([])
const pointsByDevice = ref<Record<number, ReadPoint[]>>({})
const liveByDevice = ref<Record<number, LiveValue[]>>({})
const readErrors = ref<Record<number, string>>({})
const catalogError = ref('')
const refreshing = ref(false)
const now = ref(Date.now())
let generation = 0
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined

type Kind = 'value' | 'lamp' | 'button' | 'text' | 'rectangle' | 'ellipse' | 'line' | 'pipe' | 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'
type ItemStyle = { fill: string; stroke: string; strokeWidth: number; textColor: string }
type Item = { id: string; kind: Kind; label: string; x: number; y: number; width: number; height: number; binding?: ReadBinding; groupId?: string; locked?: boolean; style?: ItemStyle; route?: 'horizontal' | 'vertical' | 'elbow'; reversed?: boolean }
type Document = { version: 1; items: Item[] }
type ServerConfig = { document: Document; draftVersion: number; publishedRevisionId: number | null; publishedVersion: number | null; updatedBy: string; updatedAt: string }
type ServerRevision = { id: number; version: number; document: Document; publishedBy: string; createdAt: string; current: boolean }
type HmiTemplate = { id: number; name: string; document: Document; updatedBy: string; updatedAt: string }
const library: { kind: Kind; label: string; path: string; group: 'basic' | 'industrial' }[] = [
  { kind: 'value', label: '数值显示', path: 'M4 5h16v14H4z M8 9h8 M8 13h4', group: 'basic' },
  { kind: 'lamp', label: '状态指示灯', path: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M9 12h6', group: 'basic' },
  { kind: 'button', label: '操作按钮', path: 'M3 6h18v12H3z M9 12h6 M12 9v6', group: 'basic' },
  { kind: 'text', label: '文字标签', path: 'M4 5h16 M12 5v14 M8 19h8', group: 'basic' },
  { kind: 'rectangle', label: '矩形容器', path: 'M4 5h16v14H4z', group: 'basic' },
  { kind: 'ellipse', label: '圆形图元', path: 'M4 12a8 6 0 1 0 16 0 8 6 0 1 0-16 0', group: 'basic' },
  { kind: 'line', label: '工艺连线', path: 'M3 12h18 M17 8l4 4-4 4', group: 'basic' },
  { kind: 'pipe', label: '工艺管道', path: 'M3 9h18 M3 15h18', group: 'basic' },
  { kind: 'pump', label: '泵', path: 'M5 12a7 7 0 1 0 14 0 7 7 0 1 0-14 0 M19 12h3v-5', group: 'industrial' },
  { kind: 'gate', label: '闸门', path: 'M3 12h5l4-4v8l4-4-4-4v8l4-4h5 M12 8V3', group: 'industrial' },
  { kind: 'motor', label: '电机', path: 'M4 12a8 8 0 1 0 16 0 8 8 0 1 0-16 0 M8 15V9l4 4 4-4v6', group: 'industrial' },
  { kind: 'plc', label: 'PLC', path: 'M4 4h16v16H4z M8 4v16 M16 4v16 M10 8h4 M10 12h4 M10 16h4', group: 'industrial' },
  { kind: 'gauge', label: '仪表', path: 'M4 15a8 8 0 0 1 16 0 M12 15l4-6 M6 19h12', group: 'industrial' },
]
const storageKey = computed(() => `scada.hmi.editor.draft.v1.${props.screenId}`)
const items = ref<Item[]>([])
const selectedIds = ref<string[]>([])
const selection = computed(() => items.value.filter(item => selectedIds.value.includes(item.id)))
const selected = computed(() => selection.value.length === 1 ? selection.value[0] : undefined)
const stage = ref<HTMLElement | null>(null)
const targets = ref<HTMLElement[]>([])
const selector = ref<InstanceType<typeof Selecto> | null>(null)
const moveable = ref<InstanceType<typeof Moveable> | null>(null)
const zoom = ref(0.75)
const preview = ref(false)
const message = ref('本地草稿 · 只读绑定 / 控制未启用')
const saved = ref(JSON.stringify(items.value))
const dirty = computed(() => saved.value !== JSON.stringify(items.value))
const past = ref<string[]>([])
const future = ref<string[]>([])
const serverDraftVersion = ref<number | null>(null)
const publishedVersion = ref<number | null>(null)
const versions = ref<ServerRevision[]>([])
const selectedVersionId = ref<number | null>(null)
const saving = ref(false)
const templates = ref<HmiTemplate[]>([])
const newTemplateName = ref('')
const templateEditName = ref('')
const templateEditingId = ref<number | null>(null)
const templateDeleteConfirmId = ref<number | null>(null)
const templateBusy = ref(false)
const templateError = ref('')
const boundDevices = computed(() => [...new Set(items.value.flatMap(item => item.binding ? [item.binding.deviceId] : []))].sort((a, b) => a - b))
const selectedPoints = computed(() => selected.value?.binding ? pointsByDevice.value[selected.value.binding.deviceId] ?? [] : [])
const defaultStyle: ItemStyle = { fill: '#253f53', stroke: '#82a9c8', strokeWidth: 2, textColor: '#a9bed0' }
const industrialKinds: Kind[] = ['pump', 'gate', 'motor', 'plc', 'gauge']
function industrialAsset(kind: Kind) { return `/assets/hmi/industrial/${kind}.png` }
function itemStyle(item: Item) { return { ...defaultStyle, ...item.style } }
function pathPoints(item: Item) {
  const horizontal = `4,${item.height / 2} ${item.width - 10},${item.height / 2}`
  const vertical = `${item.width / 2},4 ${item.width / 2},${item.height - 10}`
  const elbow = `4,4 ${item.width / 2},4 ${item.width / 2},${item.height - 6} ${item.width - 10},${item.height - 6}`
  const points = (item.route === 'vertical' ? vertical : item.route === 'elbow' ? elbow : horizontal).split(' ')
  return (item.reversed ? points.reverse() : points).join(' ')
}

async function refreshReadings(forceCatalog = false) {
  if (!props.active || refreshing.value) return
  clearTimeout(refreshTimer)
  refreshing.value = true
  const run = generation
  try {
    const devices = forceCatalog || !readDevices.value.length
      ? await props.read<ReadDevice[]>('/api/devices', { signal: AbortSignal.timeout(8000) })
      : readDevices.value
    if (run !== generation) return
    readDevices.value = devices
    catalogError.value = ''
    await Promise.all(boundDevices.value.map(async deviceId => {
      try {
        if (!devices.some(device => device.id === deviceId)) { readErrors.value[deviceId] = '设备不存在'; return }
        const points = forceCatalog || !pointsByDevice.value[deviceId]
          ? await props.read<ReadPoint[]>(`/api/points?deviceId=${deviceId}`, { signal: AbortSignal.timeout(8000) })
          : pointsByDevice.value[deviceId]
        const values = await props.read<LiveValue[]>(`/api/realtime/values?deviceId=${deviceId}`, { signal: AbortSignal.timeout(8000) })
        if (run !== generation) return
        pointsByDevice.value[deviceId] = points
        liveByDevice.value[deviceId] = values
        readErrors.value[deviceId] = ''
      } catch {
        if (run === generation) readErrors.value[deviceId] = '读取失败'
      }
    }))
  } catch {
    if (run === generation) catalogError.value = '连接异常，请检查登录或后端服务'
  } finally {
    if (run === generation) {
      refreshing.value = false
      now.value = Date.now()
      if (props.active) refreshTimer = setTimeout(refreshReadings, 5000)
    }
  }
}
function bindingDevice(event: Event) {
  if (!selected.value || selected.value.locked) return
  checkpoint()
  const deviceId = Number((event.target as HTMLSelectElement).value)
  if (!deviceId) delete selected.value.binding
  else selected.value.binding = { deviceId, pointId: null, decimals: 2, unit: '', staleSeconds: 30, activeValue: '1', activeText: '运行', inactiveText: '停止' }
  void refreshReadings()
}
function bindingField(field: keyof Omit<ReadBinding, 'deviceId'>, event: Event) {
  const binding = selected.value?.binding
  if (!binding || selected.value?.locked) return
  const text = (event.target as HTMLInputElement).value
  const next = { ...binding }
  if (field === 'pointId') {
    next.pointId = text ? Number(text) : null
    next.unit = selectedPoints.value.find(point => point.id === next.pointId)?.unit?.slice(0, 40) ?? ''
  } else if (field === 'decimals' || field === 'staleSeconds') next[field] = Number(text)
  else next[field] = text.slice(0, 40)
  if (!validBinding(next)) { message.value = '格式无效：小数位 0–6，过期时间 5–3600 秒'; return }
  checkpoint()
  selected.value!.binding = next
}
function reading(item: Item) {
  const binding = item.binding
  const point = binding && pointsByDevice.value[binding.deviceId]?.find(point => point.id === binding.pointId)
  const problem = !binding ? '' : catalogError.value || readErrors.value[binding.deviceId] ||
    (!pointsByDevice.value[binding.deviceId] ? '加载中' : !point && binding.pointId ? '点位不存在' : point?.accessMode === 'WRITE_ONLY' ? '点位不可读' : '')
  return readState(binding, binding && liveByDevice.value[binding.deviceId]?.find(value => value.pointId === binding.pointId), problem, now.value, ['lamp', 'pump', 'gate', 'motor', 'plc'].includes(item.kind))
}
watch(() => props.active, active => {
  generation++
  refreshing.value = false
  clearTimeout(refreshTimer)
  if (active) { void refreshReadings(); void loadTemplates() }
}, { immediate: true })
watch(() => boundDevices.value.join(','), () => { if (props.active) void refreshReadings() })
watch(() => props.screenId, (screenId, previous) => { void switchScreen(screenId, previous) }, { immediate: true })
function checkpoint() {
  past.value.push(JSON.stringify(items.value))
  if (past.value.length > 50) past.value.shift()
  future.value = []
}
async function select(ids: string | string[] = []) {
  const requested = typeof ids === 'string' ? (ids ? [ids] : []) : ids
  const groups = new Set(items.value.filter(item => requested.includes(item.id) && item.groupId).map(item => item.groupId))
  selectedIds.value = [...new Set([...requested, ...items.value.filter(item => item.groupId && groups.has(item.groupId)).map(item => item.id)])]
  await nextTick()
  targets.value = Array.from(stage.value?.querySelectorAll<HTMLElement>('[data-item-id]') ?? []).filter(element => selectedIds.value.includes(element.dataset.itemId!) && !items.value.find(item => item.id === element.dataset.itemId)?.locked)
  selector.value?.setSelectedTargets(targets.value)
  moveable.value?.updateRect()
}
function pick(id: string, additive = false) {
  void select(additive ? selectedIds.value.includes(id) ? selectedIds.value.filter(value => value !== id) : [...selectedIds.value, id] : [id])
}
let selectionBase: string[] = []
let additiveSelection = false
function startSelection(event: { inputEvent: MouseEvent; stop: () => void }) {
  selectionBase = [...selectedIds.value]
  additiveSelection = event.inputEvent.shiftKey
  const element = event.inputEvent.target as HTMLElement
  if (element.closest('.moveable-control-box')) { event.stop(); return }
  if (!event.inputEvent.shiftKey && targets.value.some(target => target.contains(element))) {
    event.stop()
    if (!moveable.value?.isDragging()) moveable.value?.dragStart(event.inputEvent)
  }
}
function selectedElements(event: { selected: (HTMLElement | SVGElement)[] }) {
  const hits = event.selected.map(element => element.dataset.itemId!).filter(Boolean)
  void select(additiveSelection ? [...selectionBase.filter(id => !hits.includes(id)), ...hits.filter(id => !selectionBase.includes(id))] : hits)
}
function syncPosition(item: Item, element?: HTMLElement | SVGElement) {
  if (element) Object.assign(element.style, { left: `${item.x}px`, top: `${item.y}px` })
}
function beginDrag(event: OnDragStart) {
  if (event.inputEvent?.shiftKey) { event.stopDrag(); return }
  if (!selected.value || selected.value.locked) return
  checkpoint()
  event.set([selected.value.x, selected.value.y])
}
function drag(event: OnDrag) {
  if (!selected.value) return
  selected.value.x = Math.max(0, Math.min(1200 - selected.value.width, event.beforeTranslate[0]))
  selected.value.y = Math.max(0, Math.min(720 - selected.value.height, event.beforeTranslate[1]))
  syncPosition(selected.value, event.target)
}
let groupStart: { item: Item; x: number; y: number }[] = []
function beginGroupDrag(event: OnDragGroupStart) {
  if (event.inputEvent?.shiftKey) { event.stopDrag(); return }
  checkpoint()
  groupStart = selection.value.filter(item => !item.locked).map(item => ({ item, x: item.x, y: item.y }))
  event.events.forEach(child => child.set([0, 0]))
}
function moveSelection(dx: number, dy: number, origins = selection.value.map(item => ({ item, x: item.x, y: item.y }))) {
  if (!origins.length) return
  dx = Math.max(-Math.min(...origins.map(value => value.x)), Math.min(1200 - Math.max(...origins.map(value => value.x + value.item.width)), dx))
  dy = Math.max(-Math.min(...origins.map(value => value.y)), Math.min(720 - Math.max(...origins.map(value => value.y + value.item.height)), dy))
  origins.forEach(({ item, x, y }) => {
    item.x = x + dx
    item.y = y + dy
    syncPosition(item, targets.value.find(element => element.dataset.itemId === item.id))
  })
}
function dragGroup(event: OnDragGroup) {
  const translation = event.events[0]?.beforeTranslate
  if (translation) moveSelection(translation[0], translation[1], groupStart)
}
function align(mode: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom') {
  const movable = selection.value.filter(item => !item.locked)
  if (movable.length < 2) return
  checkpoint()
  const left = Math.min(...movable.map(item => item.x))
  const right = Math.max(...movable.map(item => item.x + item.width))
  const top = Math.min(...movable.map(item => item.y))
  const bottom = Math.max(...movable.map(item => item.y + item.height))
  movable.forEach(item => {
    if (mode === 'left') item.x = left
    if (mode === 'center') item.x = (left + right - item.width) / 2
    if (mode === 'right') item.x = right - item.width
    if (mode === 'top') item.y = top
    if (mode === 'middle') item.y = (top + bottom - item.height) / 2
    if (mode === 'bottom') item.y = bottom - item.height
  })
  void nextTick(() => moveable.value?.updateRect())
}
function layer(mode: 'front' | 'back' | 'up' | 'down') {
  if (!selection.value.length || selection.value.some(item => item.locked)) return
  checkpoint()
  const chosen = (item: Item) => selectedIds.value.includes(item.id)
  if (mode === 'front') items.value = [...items.value.filter(item => !chosen(item)), ...selection.value]
  else if (mode === 'back') items.value = [...selection.value, ...items.value.filter(item => !chosen(item))]
  else if (mode === 'up') {
    for (let index = items.value.length - 2; index >= 0; index--) {
      if (chosen(items.value[index]) && !chosen(items.value[index + 1])) [items.value[index], items.value[index + 1]] = [items.value[index + 1], items.value[index]]
    }
  } else {
    for (let index = 1; index < items.value.length; index++) {
      if (chosen(items.value[index]) && !chosen(items.value[index - 1])) [items.value[index], items.value[index - 1]] = [items.value[index - 1], items.value[index]]
    }
  }
  void select([...selectedIds.value])
}
function add(kind: Kind, x?: number, y?: number) {
  if (preview.value) return
  checkpoint()
  const cascade = (items.value.length % 8) * 16
  const left = x ?? 80 + cascade
  const top = y ?? 80 + cascade
  const compact = kind === 'line' || kind === 'pipe'
  const symbol = ['pump', 'gate', 'motor', 'plc', 'gauge'].includes(kind)
  const item: Item = { id: crypto.randomUUID(), kind, label: library.find(entry => entry.kind === kind)!.label, x: Math.max(0, Math.min(1040, Math.round(left / 8) * 8)), y: Math.max(0, Math.min(640, Math.round(top / 8) * 8)), width: compact ? 240 : symbol ? 144 : 160, height: compact ? 40 : symbol ? 144 : 80, style: { ...defaultStyle }, route: compact ? 'horizontal' : undefined }
  items.value.push(item)
  void select(item.id)
}
function drop(event: DragEvent) {
  const kind = event.dataTransfer?.getData('application/x-scada-component') as Kind
  if (!library.some(entry => entry.kind === kind) || !stage.value) return
  const rect = stage.value.getBoundingClientRect()
  add(kind, (event.clientX - rect.left) / zoom.value, (event.clientY - rect.top) / zoom.value)
}
function update(field: 'x' | 'y' | 'width' | 'height' | 'label', event: Event) {
  if (!selected.value || selected.value.locked) return
  const value = (event.target as HTMLInputElement).value
  checkpoint()
  if (field === 'label') selected.value.label = value.slice(0, 80)
  else {
    const number = Number(value)
    if (!Number.isFinite(number)) return
    const max = field === 'x' ? 1200 - selected.value.width : field === 'y' ? 720 - selected.value.height : field === 'width' ? 1200 - selected.value.x : 720 - selected.value.y
    selected.value[field] = Math.max(field === 'width' || field === 'height' ? 40 : 0, Math.min(max, number))
  }
  void nextTick(() => moveable.value?.updateRect())
}
function updateStyle(field: keyof ItemStyle, event: Event) {
  if (!selected.value || selected.value.locked) return
  const value = (event.target as HTMLInputElement).value
  checkpoint()
  const style = itemStyle(selected.value)
  if (field === 'strokeWidth') style.strokeWidth = Math.max(1, Math.min(12, Number(value) || 1))
  else style[field] = value
  selected.value.style = style
}
function updatePath(field: 'route' | 'reversed', event: Event) {
  if (!selected.value || selected.value.locked || (selected.value.kind !== 'line' && selected.value.kind !== 'pipe')) return
  checkpoint()
  if (field === 'route') selected.value.route = (event.target as HTMLSelectElement).value as Item['route']
  else selected.value.reversed = (event.target as HTMLInputElement).checked || undefined
}
function remove() {
  if (!selection.value.length || preview.value || selection.value.some(item => item.locked)) return
  checkpoint()
  items.value = items.value.filter(item => !selectedIds.value.includes(item.id))
  void select()
}
function duplicate() {
  if (!selection.value.length || preview.value || selection.value.some(item => item.locked)) return
  checkpoint()
  const dx = Math.min(16, 1200 - Math.max(...selection.value.map(item => item.x + item.width)))
  const dy = Math.min(16, 720 - Math.max(...selection.value.map(item => item.y + item.height)))
  const groupIds = new Map<string, string>()
  const copies = selection.value.map(source => ({ ...source, id: crypto.randomUUID(), groupId: source.groupId ? groupIds.get(source.groupId) ?? (() => { const id = crypto.randomUUID(); groupIds.set(source.groupId!, id); return id })() : undefined, x: source.x + dx, y: source.y + dy }))
  items.value.push(...copies)
  void select(copies.map(item => item.id))
}
function resize(event: { target: HTMLElement | SVGElement; width: number; height: number; drag: { beforeTranslate: number[] } }) {
  if (!selected.value || selected.value.locked) return
  const item = selected.value
  item.x = Math.max(0, Math.min(1160, event.drag.beforeTranslate[0]))
  item.y = Math.max(0, Math.min(680, event.drag.beforeTranslate[1]))
  item.width = Math.max(40, Math.min(1200 - item.x, event.width))
  item.height = Math.max(40, Math.min(720 - item.y, event.height))
  Object.assign(event.target.style, { width: `${item.width}px`, height: `${item.height}px`, left: `${item.x}px`, top: `${item.y}px` })
}
function beginResize(event: { setMin: (size: number[]) => void; dragStart: false | { set: (position: number[]) => void } }) {
  if (!selected.value || selected.value.locked) return
  checkpoint()
  event.setMin([40, 40])
  if (event.dragStart) event.dragStart.set([selected.value.x, selected.value.y])
}
function travel(redo = false) {
  const from = redo ? future : past
  const to = redo ? past : future
  const snapshot = from.value.pop()
  if (!snapshot) return
  to.value.push(JSON.stringify(items.value))
  items.value = JSON.parse(snapshot)
  void select()
}
function groupSelection() {
  if (selection.value.length < 2 || selection.value.some(item => item.locked)) return
  checkpoint()
  const groupId = crypto.randomUUID()
  selection.value.forEach(item => { item.groupId = groupId })
  void select([...selectedIds.value])
}
function ungroupSelection() {
  if (!selection.value.some(item => item.groupId) || selection.value.some(item => item.locked)) return
  checkpoint()
  selection.value.forEach(item => { delete item.groupId })
  void select([...selectedIds.value])
}
function toggleLock() {
  if (!selection.value.length) return
  checkpoint()
  const lock = !selection.value.every(item => item.locked)
  selection.value.forEach(item => { item.locked = lock || undefined })
  void select([...selectedIds.value])
}
async function loadTemplates() {
  try { templates.value = await props.read<HmiTemplate[]>('/api/hmi/templates'); templateError.value = '' }
  catch (error) { templateError.value = error instanceof Error ? error.message : '模板读取失败' }
}
function templateDocument(): Document {
  const left = Math.min(...selection.value.map(item => item.x))
  const top = Math.min(...selection.value.map(item => item.y))
  const copies = selection.value.map(item => ({ ...JSON.parse(JSON.stringify(item)), x: item.x - left, y: item.y - top, locked: undefined }))
  return { version: 1, items: copies }
}
async function createTemplate() {
  if (!selection.value.length || templateBusy.value) return
  templateBusy.value = true; templateError.value = ''
  try {
    await props.read<HmiTemplate>('/api/hmi/templates', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: newTemplateName.value.trim(), document: templateDocument() }) })
    newTemplateName.value = ''; await loadTemplates(); message.value = '组件模板已保存到服务端'
  } catch (error) { templateError.value = error instanceof Error ? error.message : '模板保存失败' }
  finally { templateBusy.value = false }
}
function addTemplate(template: HmiTemplate) {
  if (preview.value || !validDocument(template.document)) return
  checkpoint()
  const groupIds = new Map<string, string>()
  const cascade = (items.value.length % 8) * 16
  const width = Math.max(...template.document.items.map(item => item.x + item.width))
  const height = Math.max(...template.document.items.map(item => item.y + item.height))
  const left = Math.min(1200 - width, 80 + cascade)
  const top = Math.min(720 - height, 80 + cascade)
  const copies = template.document.items.map(source => ({ ...JSON.parse(JSON.stringify(source)), id: crypto.randomUUID(), groupId: source.groupId ? groupIds.get(source.groupId) ?? (() => { const id = crypto.randomUUID(); groupIds.set(source.groupId!, id); return id })() : undefined, locked: undefined, x: source.x + left, y: source.y + top }))
  items.value.push(...copies)
  void select(copies.map(item => item.id))
  message.value = `已添加模板：${template.name}`
}
function editTemplate(template: HmiTemplate) { templateEditingId.value = template.id; templateEditName.value = template.name; templateDeleteConfirmId.value = null; templateError.value = '' }
async function renameTemplate() {
  const template = templates.value.find(item => item.id === templateEditingId.value)
  if (!template || templateBusy.value) return
  templateBusy.value = true; templateError.value = ''
  try {
    await props.read<HmiTemplate>(`/api/hmi/templates/${template.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: templateEditName.value.trim(), document: template.document }) })
    templateEditingId.value = null; templateEditName.value = ''; await loadTemplates()
  } catch (error) { templateError.value = error instanceof Error ? error.message : '模板重命名失败' }
  finally { templateBusy.value = false }
}
async function deleteTemplate() {
  const id = templateEditingId.value
  if (!id || templateBusy.value) return
  if (templateDeleteConfirmId.value !== id) { templateDeleteConfirmId.value = id; return }
  templateBusy.value = true; templateError.value = ''
  try { await props.read<void>(`/api/hmi/templates/${id}`, { method: 'DELETE' }); templateEditingId.value = null; templateEditName.value = ''; templateDeleteConfirmId.value = null; await loadTemplates() }
  catch (error) { templateError.value = error instanceof Error ? error.message : '模板删除失败' }
  finally { templateBusy.value = false }
}
function documentValue(): Document { return { version: 1, items: items.value } }
function validDocument(value: unknown): value is Document {
  if (!value || typeof value !== 'object') return false
  const document = value as Document
  return document.version === 1 && Array.isArray(document.items) && document.items.length <= 1000 && document.items.every((item: Item) =>
    typeof item.id === 'string' && /^[a-zA-Z0-9-]+$/.test(item.id) && library.some(entry => entry.kind === item.kind) && typeof item.label === 'string' && item.label.length <= 80 &&
    [item.x, item.y, item.width, item.height].every(Number.isFinite) && item.x >= 0 && item.y >= 0 && item.width >= 40 && item.height >= 40 && item.x + item.width <= 1200 && item.y + item.height <= 720 &&
    (item.groupId === undefined || /^[a-zA-Z0-9-]+$/.test(item.groupId)) && (item.locked === undefined || typeof item.locked === 'boolean') &&
    (item.style === undefined || (/^#[0-9a-fA-F]{6}$/.test(item.style.fill) && /^#[0-9a-fA-F]{6}$/.test(item.style.stroke) && /^#[0-9a-fA-F]{6}$/.test(item.style.textColor) && Number.isFinite(item.style.strokeWidth) && item.style.strokeWidth >= 1 && item.style.strokeWidth <= 12)) &&
    (item.route === undefined || ['horizontal', 'vertical', 'elbow'].includes(item.route)) && (item.reversed === undefined || typeof item.reversed === 'boolean') && (item.binding === undefined || validBinding(item.binding))
  ) && new Set(document.items.map((item: Item) => item.id)).size === document.items.length
}
function configUrl(path: string) { return `/api/hmi/config/${path}?screenId=${props.screenId}` }
function saveLocal(text = '已保存到当前浏览器', key = storageKey.value) {
  try {
    localStorage.setItem(key, JSON.stringify(documentValue()))
    saved.value = JSON.stringify(items.value)
    message.value = text + ' · ' + new Date().toLocaleTimeString()
  } catch { message.value = '保存失败：浏览器存储不可用，请保留当前页面。' }
}
async function save() {
  if (saving.value) return
  const previousSaved = saved.value
  saveLocal('正在保存服务端草稿')
  saving.value = true
  try {
    const response = await props.read<ServerConfig>(configUrl('draft'), {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ document: documentValue(), expectedDraftVersion: serverDraftVersion.value }),
    })
    serverDraftVersion.value = response.draftVersion
    publishedVersion.value = response.publishedVersion
    message.value = '服务端草稿已保存 · ' + new Date().toLocaleTimeString()
  } catch (error) {
    saved.value = previousSaved
    message.value = `服务端保存失败，本地草稿已保留：${error instanceof Error ? error.message : '未知错误'}`
  }
  finally { saving.value = false }
}
async function loadVersions() {
  versions.value = await props.read<ServerRevision[]>(configUrl('versions'))
  if (!versions.value.some(version => version.id === selectedVersionId.value)) selectedVersionId.value = versions.value[0]?.id ?? null
}
async function publish() {
  if (saving.value) return
  saving.value = true
  try {
    const revision = await props.read<ServerRevision>(configUrl('publish'), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ document: documentValue(), expectedDraftVersion: serverDraftVersion.value }),
    })
    const config = await props.read<ServerConfig>(configUrl('draft'))
    serverDraftVersion.value = config.draftVersion
    publishedVersion.value = revision.version
    saveLocal(`已发布 V${revision.version}`)
    await loadVersions()
  } catch (error) { message.value = `发布失败：${error instanceof Error ? error.message : '未知错误'}` }
  finally { saving.value = false }
}
async function restoreVersion() {
  if (!selectedVersionId.value || saving.value) return
  saving.value = true
  try {
    const config = await props.read<ServerConfig>(configUrl(`versions/${selectedVersionId.value}/restore`), { method: 'POST' })
    if (!validDocument(config.document)) throw new Error('服务端版本格式无效')
    items.value = config.document.items
    serverDraftVersion.value = config.draftVersion
    publishedVersion.value = config.publishedVersion
    past.value = []; future.value = []
    void select()
    saveLocal(`已恢复 V${versions.value.find(version => version.id === selectedVersionId.value)?.version} 到草稿`)
  } catch (error) { message.value = `恢复失败：${error instanceof Error ? error.message : '未知错误'}` }
  finally { saving.value = false }
}
function loadLocal() {
  try {
    const raw = localStorage.getItem(storageKey.value)
    if (!raw) return false
    const document = JSON.parse(raw) as Document
    if (!validDocument(document)) throw new Error('Invalid draft')
    items.value = document.items
    saved.value = JSON.stringify(items.value)
    message.value = '已恢复本地草稿 · 只读绑定 / 控制未启用'
    return true
  } catch { message.value = '本地草稿无法读取，原存储未覆盖。'; return false }
}
async function loadServer(hadLocal: boolean) {
  const screenId = props.screenId
  try {
    const config = await props.read<ServerConfig>(configUrl('draft'))
    if (screenId !== props.screenId) return
    if (!validDocument(config.document)) throw new Error('服务端草稿格式无效')
    serverDraftVersion.value = config.draftVersion
    publishedVersion.value = config.publishedVersion
    if (config.document.items.length || !hadLocal) {
      items.value = config.document.items
      saved.value = JSON.stringify(items.value)
      saveLocal('已加载服务端草稿')
    } else {
      saved.value = JSON.stringify(config.document.items)
      message.value = '服务端草稿为空，已保留浏览器草稿；点击保存即可迁移到服务端。'
    }
    await loadVersions()
  } catch (error) { message.value = `服务端草稿读取失败，继续使用本地草稿：${error instanceof Error ? error.message : '未知错误'}` }
}
async function switchScreen(screenId: number, previous?: number) {
  if (previous && dirty.value) saveLocal('已自动保存浏览器草稿', `scada.hmi.editor.draft.v1.${previous}`)
  generation++; clearTimeout(refreshTimer); refreshing.value = false
  pointsByDevice.value = {}; liveByDevice.value = {}; readErrors.value = {}; catalogError.value = ''
  items.value = []; selectedIds.value = []; targets.value = []; past.value = []; future.value = []; versions.value = []
  selectedVersionId.value = null; serverDraftVersion.value = null; publishedVersion.value = null
  const hadLocal = loadLocal()
  await loadServer(hadLocal)
  if (props.active && screenId === props.screenId) void refreshReadings(true)
}
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value) { event.preventDefault(); event.returnValue = '' } }
function keydown(event: KeyboardEvent) {
  if (preview.value || (event.target as HTMLElement).closest('input,textarea,select,[contenteditable="true"]')) return
  const command = event.ctrlKey || event.metaKey
  if (command && event.key.toLowerCase() === 'a') { event.preventDefault(); void select(items.value.map(item => item.id)); return }
  if (event.key === 'Escape') { event.preventDefault(); void select(); return }
  if (command && event.key.toLowerCase() === 'z') { event.preventDefault(); travel(event.shiftKey); return }
  if ((event.target as HTMLElement).closest('button')) return
  if (event.key === 'Delete') { event.preventDefault(); remove() }
  const directions: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const direction = directions[event.key]
  if (direction && selection.value.length && !selection.value.some(item => item.locked) && !command && !event.altKey) {
    event.preventDefault()
    if (!event.repeat) checkpoint()
    const step = event.shiftKey ? 10 : 1
    moveSelection(direction[0] * step, direction[1] * step)
    void nextTick(() => moveable.value?.updateRect())
  }
}
onMounted(() => { clockTimer = setInterval(() => { now.value = Date.now() }, 1000); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { generation++; clearTimeout(refreshTimer); clearInterval(clockTimer); if (dirty.value) saveLocal(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section class="editor" aria-label="组态编辑器" @keydown="keydown">
    <header class="editor-toolbar">
      <strong>组态画布 <small>{{ dirty ? '未保存' : `服务端草稿 #${serverDraftVersion ?? '—'}` }} · 发布 {{ publishedVersion ? `V${publishedVersion}` : '无' }}</small></strong>
      <div class="editor-actions">
        <button :disabled="refreshing" @click="refreshReadings(true)">{{ refreshing ? '读取中' : '刷新数据' }}</button>
        <button :disabled="preview || !past.length" @click="travel()">撤销</button>
        <button :disabled="preview || !future.length" @click="travel(true)">重做</button>
        <label>缩放 <select v-model.number="zoom" @change="select([...selectedIds])"><option :value="0.5">50%</option><option :value="0.75">75%</option><option :value="1">100%</option><option :value="1.25">125%</option></select></label>
        <button @click="preview = !preview; select()">{{ preview ? '返回编辑' : '预览' }}</button>
        <select v-model="selectedVersionId" aria-label="历史版本"><option :value="null">无历史版本</option><option v-for="version in versions" :key="version.id" :value="version.id">V{{ version.version }}{{ version.current ? ' · 当前发布' : '' }}</option></select>
        <button :disabled="saving || !selectedVersionId" @click="restoreVersion">恢复到草稿</button>
        <button :disabled="saving" class="save" @click="save">{{ saving ? '处理中' : '保存草稿' }}</button>
        <button :disabled="saving" class="publish" @click="publish">发布版本</button>
      </div>
    </header>
    <div class="editor-workspace" :class="{ preview }">
      <aside v-if="!preview" class="editor-library">
        <h3>基础组件</h3>
        <button v-for="entry in library.filter(item => item.group === 'basic')" :key="entry.kind" draggable="true" @dragstart="$event.dataTransfer?.setData('application/x-scada-component', entry.kind)" @click="add(entry.kind)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="entry.path" /></svg>{{ entry.label }}
        </button>
        <h3>工业符号</h3>
        <button v-for="entry in library.filter(item => item.group === 'industrial')" :key="entry.kind" draggable="true" @dragstart="$event.dataTransfer?.setData('application/x-scada-component', entry.kind)" @click="add(entry.kind)">
          <img class="library-industrial-image" :src="industrialAsset(entry.kind)" alt="" draggable="false" />{{ entry.label }}
        </button>
        <h3>自定义模板 <small>{{ templates.length }}</small></h3>
        <p v-if="!templates.length">尚未保存模板</p>
        <div v-for="template in templates" :key="template.id" class="editor-template-row"><button :title="`添加 ${template.name}`" @click="addTemplate(template)">{{ template.name }}</button><button title="管理模板" @click="editTemplate(template)">管理</button></div>
        <div v-if="templateEditingId" class="editor-template-edit"><input v-model="templateEditName" maxlength="128" aria-label="管理模板名称" /><div><button :disabled="templateBusy || !templateEditName.trim()" @click="renameTemplate">重命名</button><button :disabled="templateBusy" @click="deleteTemplate">{{ templateDeleteConfirmId === templateEditingId ? '确认删除' : '删除' }}</button></div><button @click="templateEditingId = null; templateEditName = ''">取消</button></div>
        <p v-if="templateError" class="read-error" role="alert">{{ templateError }}</p>
        <p>空白处拖动框选，Shift 加选。方向键微调，Shift + 方向键移动 10 px。离开菜单自动保存。</p>
        <h3>图层 <small>{{ items.length }}</small></h3>
        <button v-for="item in [...items].reverse()" :key="item.id" :class="{ chosen: selectedIds.includes(item.id) }" @click="pick(item.id, $event.shiftKey)">{{ item.label }}</button>
      </aside>
      <div class="editor-viewport" @dragover.prevent @drop.prevent="drop">
        <div :style="{ width: `${1200 * zoom}px`, height: `${720 * zoom}px` }">
          <div ref="stage" class="editor-stage" :class="{ 'is-preview': preview }" :style="{ transform: `scale(${zoom})` }" tabindex="0" aria-label="编辑画布">
            <p v-if="!items.length" class="editor-empty">从左侧添加第一个组件</p>
            <div v-for="item in items" :key="item.id" :data-item-id="item.id" class="editor-item" :class="[item.kind, { selected: selectedIds.includes(item.id) && !preview, locked: item.locked }]" :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px`, color: itemStyle(item).textColor, backgroundColor: item.kind === 'rectangle' || item.kind === 'ellipse' ? itemStyle(item).fill : undefined, borderColor: item.kind === 'rectangle' || item.kind === 'ellipse' ? itemStyle(item).stroke : undefined, borderWidth: item.kind === 'rectangle' || item.kind === 'ellipse' ? `${itemStyle(item).strokeWidth}px` : undefined }" :tabindex="preview ? -1 : 0" :aria-label="`${item.label}${item.locked ? '（已锁定）' : ''}`" @keydown.enter.prevent="!preview && pick(item.id, $event.shiftKey)" @keydown.space.prevent="!preview && pick(item.id, $event.shiftKey)">
              <template v-if="item.kind === 'value'"><span>{{ item.label }}</span><strong>{{ reading(item).text }}</strong><small :class="{ 'read-error': reading(item).status !== '正常' }" :title="reading(item).time">{{ reading(item).status }}</small></template>
              <template v-else-if="item.kind === 'lamp'"><i :class="{ 'lamp-active': reading(item).active, 'lamp-idle': reading(item).status === '正常' && !reading(item).active }"></i><span>{{ item.label }}</span><small :title="reading(item).time">{{ reading(item).status === '正常' ? reading(item).text : reading(item).status }}</small></template>
              <button v-else-if="item.kind === 'button'" disabled>{{ item.label }}</button>
              <span v-else-if="item.kind === 'text'">{{ item.label }}</span>
              <template v-else-if="item.kind === 'line' || item.kind === 'pipe'">
                <svg class="process-path" :viewBox="`0 0 ${item.width} ${item.height}`" preserveAspectRatio="none" aria-hidden="true">
                  <defs><marker :id="`arrow-${item.id}`" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" :fill="itemStyle(item).stroke" /></marker></defs>
                  <polyline v-if="item.kind === 'pipe'" :points="pathPoints(item)" fill="none" :stroke="itemStyle(item).stroke" :stroke-width="itemStyle(item).strokeWidth + 6" stroke-linecap="square" stroke-linejoin="round" />
                  <polyline :points="pathPoints(item)" fill="none" :stroke="item.kind === 'pipe' ? itemStyle(item).fill : itemStyle(item).stroke" :stroke-width="itemStyle(item).strokeWidth" stroke-linecap="square" stroke-linejoin="round" :marker-end="`url(#arrow-${item.id})`" />
                </svg>
                <span class="shape-label">{{ item.label }}</span>
              </template>
              <template v-else-if="['pump', 'gate', 'motor', 'plc', 'gauge'].includes(item.kind)">
                <HmiIndustrialImage :kind="item.kind as 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'" :active="reading(item).active" />
                <span class="symbol-label">{{ item.label }}</span><small :class="{ 'read-error': reading(item).status !== '正常' }">{{ reading(item).status === '正常' ? reading(item).text : reading(item).status }}</small>
              </template>
              <span v-else class="shape-label">{{ item.label }}</span>
            </div>
            <!-- Viewport-positioned marquee must not inherit the canvas transform. -->
            <Teleport to="body">
              <Selecto v-if="!preview && stage" ref="selector" :drag-container="stage" :selectable-targets="['.editor-stage .editor-item']" :hit-rate="0" :select-by-click="true" :select-from-inside="false" @drag-start="startSelection" @select="selectedElements" />
            </Teleport>
            <Moveable v-if="!preview && targets.length" ref="moveable" :target="targets.length === 1 ? targets[0] : targets" :draggable="true" :pass-drag-area="true" :resizable="targets.length === 1" :snappable="true" :snap-grid-width="8" :snap-grid-height="8" :origin="false" @drag-start="beginDrag" @drag="drag" @drag-group-start="beginGroupDrag" @drag-group="dragGroup" @resize-start="beginResize" @resize="resize" />
          </div>
        </div>
      </div>
      <aside v-if="!preview" class="editor-properties">
        <h3>组件属性</h3>
        <template v-if="selected">
          <label>名称<input :disabled="selected.locked" :value="selected.label" maxlength="80" @change="update('label', $event)" /></label>
          <div class="editor-fields"><label v-for="field in (['x', 'y', 'width', 'height'] as const)" :key="field">{{ { x: 'X 坐标', y: 'Y 坐标', width: '宽度', height: '高度' }[field] }}<input type="number" :disabled="selected.locked" :value="Math.round(selected[field])" @change="update(field, $event)" /></label></div>
          <template v-if="selected.kind === 'value' || selected.kind === 'lamp' || ['pump', 'gate', 'motor', 'plc', 'gauge'].includes(selected.kind)">
            <h3>读取绑定</h3>
            <label>设备<select :disabled="selected.locked" :value="selected.binding?.deviceId ?? ''" @change="bindingDevice"><option value="">未绑定</option><option v-for="device in readDevices" :key="device.id" :value="device.id">{{ device.name }}</option></select></label>
            <template v-if="selected.binding">
              <label>点位<select :disabled="selected.locked" :value="selected.binding.pointId ?? ''" @change="bindingField('pointId', $event)"><option value="">选择点位</option><option v-for="point in selectedPoints" :key="point.id" :value="point.id" :disabled="point.accessMode === 'WRITE_ONLY'">{{ point.name }}</option></select></label>
              <template v-if="selected.kind === 'value' || selected.kind === 'gauge'">
                <label>小数位<input type="number" min="0" max="6" :disabled="selected.locked" :value="selected.binding.decimals" @change="bindingField('decimals', $event)" /></label>
                <label>显示单位<input maxlength="40" :disabled="selected.locked" :value="selected.binding.unit" @change="bindingField('unit', $event)" /></label>
              </template>
              <template v-else>
                <label>有效值<input maxlength="40" :disabled="selected.locked" :value="selected.binding.activeValue" @change="bindingField('activeValue', $event)" /></label>
                <label>有效状态文字<input maxlength="40" :disabled="selected.locked" :value="selected.binding.activeText" @change="bindingField('activeText', $event)" /></label>
                <label>其他值状态文字<input maxlength="40" :disabled="selected.locked" :value="selected.binding.inactiveText" @change="bindingField('inactiveText', $event)" /></label>
              </template>
              <label>数据过期时间（秒）<input type="number" min="5" max="3600" :disabled="selected.locked" :value="selected.binding.staleSeconds" @change="bindingField('staleSeconds', $event)" /></label>
              <p role="status">{{ reading(selected).status }}<br />采集时间：{{ reading(selected).time || '—' }}</p>
            </template>
          </template>
          <p v-else>{{ selected.kind === 'button' ? '控制未启用，不下发设备命令。' : ['rectangle', 'ellipse', 'line', 'pipe'].includes(selected.kind) ? '工业基础图元，不绑定点位。' : '静态文字，不绑定点位。' }}</p>
          <template v-if="['rectangle', 'ellipse', 'line', 'pipe', 'text'].includes(selected.kind)">
            <h3>外观</h3>
            <div class="editor-color-fields"><label>填充<input type="color" :disabled="selected.locked" :value="itemStyle(selected).fill" @input="updateStyle('fill', $event)" /></label><label>边框 / 线<input type="color" :disabled="selected.locked" :value="itemStyle(selected).stroke" @input="updateStyle('stroke', $event)" /></label><label>文字<input type="color" :disabled="selected.locked" :value="itemStyle(selected).textColor" @input="updateStyle('textColor', $event)" /></label><label>线宽<input type="number" min="1" max="12" :disabled="selected.locked" :value="itemStyle(selected).strokeWidth" @input="updateStyle('strokeWidth', $event)" /></label></div>
          </template>
          <p v-else-if="industrialKinds.includes(selected.kind)">设备图片保持统一材质，运行状态由点位数据和状态文字反馈。</p>
          <template v-if="selected.kind === 'line' || selected.kind === 'pipe'">
            <h3>路径</h3>
            <label>走向<select :disabled="selected.locked" :value="selected.route ?? 'horizontal'" @change="updatePath('route', $event)"><option value="horizontal">水平</option><option value="vertical">垂直</option><option value="elbow">正交折线</option></select></label>
            <label class="editor-check"><input type="checkbox" :disabled="selected.locked" :checked="selected.reversed" @change="updatePath('reversed', $event)" />反转方向</label>
          </template>
          <p v-if="catalogError" class="read-error" role="alert">{{ catalogError }}</p>
        </template>
        <p v-else-if="selection.length">已选择 {{ selection.length }} 个组件，可批量移动、对齐和调整图层。</p>
        <p v-else>选择画布组件后编辑属性</p>
        <template v-if="selection.length">
          <div class="editor-actions"><button :disabled="selection.some(item => item.locked)" @click="duplicate">复制</button><button :disabled="selection.some(item => item.locked)" @click="remove">删除</button><button @click="toggleLock">{{ selection.every(item => item.locked) ? '解锁' : '锁定' }}</button></div>
          <h3>保存为模板</h3>
          <label>模板名称<input v-model="newTemplateName" maxlength="128" placeholder="例如：主机运行单元" /></label><button :disabled="templateBusy || !newTemplateName.trim()" @click="createTemplate">{{ templateBusy ? '保存中' : '保存模板' }}</button>
          <h3>组合</h3>
          <div class="editor-commands"><button :disabled="selection.length < 2 || selection.some(item => item.locked)" @click="groupSelection">组合</button><button :disabled="!selection.some(item => item.groupId) || selection.some(item => item.locked)" @click="ungroupSelection">取消组合</button></div>
          <h3>对齐</h3>
          <div class="editor-commands">
            <button v-for="option in ([['left', '左对齐'], ['center', '水平居中'], ['right', '右对齐'], ['top', '顶对齐'], ['middle', '垂直居中'], ['bottom', '底对齐']] as const)" :key="option[0]" :disabled="selection.filter(item => !item.locked).length < 2" @click="align(option[0])">{{ option[1] }}</button>
          </div>
          <h3>图层顺序</h3>
          <div class="editor-commands"><button :disabled="selection.some(item => item.locked)" @click="layer('up')">上移一层</button><button :disabled="selection.some(item => item.locked)" @click="layer('down')">下移一层</button><button :disabled="selection.some(item => item.locked)" @click="layer('front')">置顶</button><button :disabled="selection.some(item => item.locked)" @click="layer('back')">置底</button></div>
        </template>
      </aside>
    </div>
    <footer role="status"><span>{{ message }}</span><span>1200 × 720 · {{ preview ? '预览 / 控制未启用' : '编辑 / 8 px 网格' }}</span></footer>
  </section>
</template>

<style scoped>
.editor .read-error { color:#e9b479; }.editor-item.lamp i.lamp-active { background:#55be96; }.editor-item.lamp i.lamp-idle { background:#8095ac; }
.editor-commands { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
.editor { --surface:#121e2b; --line:#35465a; --muted:#a6b6c8; --accent:#78b7ef; grid-column:1/-1; min-width:0; color:#e4edf5; background:var(--surface); border:1px solid var(--line); border-radius:4px; overflow:hidden; }
.editor-toolbar,.editor-actions,.editor footer { display:flex; align-items:center; gap:8px; }
.editor-toolbar { justify-content:space-between; flex-wrap:wrap; padding:12px 16px; border-bottom:1px solid var(--line); }
.editor strong { font-size:14px; }.editor small,.editor p { color:var(--muted); font-size:12px; }.editor-toolbar small { margin-left:12px; font-weight:400; }
.editor button,.editor input,.editor select { font:inherit; font-size:13px; color:inherit; background:#1a2a3b; border:1px solid var(--line); border-radius:3px; padding:7px 10px; min-width:0; }
.editor button { cursor:pointer; }.editor button:hover:not(:disabled),.editor button.chosen { border-color:var(--accent); background:#243c53; }.editor button:disabled { opacity:.5; cursor:default; }.editor :focus-visible { outline:2px solid var(--accent); outline-offset:2px; }.editor .save { border-color:#669ccc; }.editor .publish { border-color:#55a989; background:#1a3a36; }.editor-actions label { white-space:nowrap; display:flex; align-items:center; gap:6px; font-size:12px; }
.editor-workspace { display:grid; grid-template-columns:156px minmax(0,1fr) 208px; min-height:540px; }.editor-workspace.preview { grid-template-columns:minmax(0,1fr); }.editor-library,.editor-properties { padding:14px 12px; max-height:650px; overflow:auto; }.editor-library { border-right:1px solid var(--line); }.editor-properties { border-left:1px solid var(--line); }.editor h3 { font-size:13px; margin:0 0 14px; }.editor h3:not(:first-child) { margin-top:24px; }.editor-library button { display:flex; gap:8px; align-items:center; width:100%; text-align:left; margin-bottom:8px; overflow:hidden; overflow-wrap:anywhere; }.editor svg { width:18px; height:18px; flex-shrink:0; fill:none; stroke:currentColor; stroke-width:1.5; }.editor p { line-height:1.7; }
.editor-viewport { overflow:auto; padding:24px; background:#0c141e; min-width:0; max-height:650px; }.editor-stage { position:relative; width:1200px; height:720px; transform-origin:top left; background-color:#152231; background-image:radial-gradient(#33475b 1px,transparent 1px); background-size:8px 8px; }.editor-stage.is-preview { background-image:none; }.editor-empty { position:absolute; top:40%; width:100%; text-align:center; pointer-events:none; }
.editor-item { position:absolute; display:flex; gap:8px; align-items:center; justify-content:center; padding:8px; box-sizing:border-box; border:1px solid transparent; user-select:none; overflow:hidden; overflow-wrap:anywhere; }.editor-item.selected { border-color:var(--accent); }.editor-item.value { flex-wrap:wrap; background:#1c2d3e; }.editor-item.value strong { font-size:24px; }.editor-item.value span { width:100%; font-size:14px; }.editor-item.lamp i { width:14px; height:14px; background:#8392a3; border-radius:50%; flex-shrink:0; }.editor-item button { width:100%; height:100%; pointer-events:none; }.editor-item.text { justify-content:flex-start; }.editor-properties label { display:grid; gap:6px; font-size:12px; margin-bottom:12px; }.editor-fields { display:grid; grid-template-columns:1fr 1fr; gap:8px; }.editor input { width:100%; box-sizing:border-box; }.editor footer { justify-content:space-between; flex-wrap:wrap; padding:10px 14px; border-top:1px solid var(--line); color:var(--muted); font-size:12px; }
.editor-item.rectangle { background:rgba(42,67,88,.34); border:1px solid #54708a; }.editor-item.ellipse { border:2px solid #54708a; border-radius:50%; background:rgba(42,67,88,.18); }.editor-item.line,.editor-item.pipe { padding:0; overflow:visible; }.process-path { position:absolute; inset:0; width:100%; height:100%; overflow:visible; pointer-events:none; }.editor-item .shape-label { position:absolute; left:8px; top:4px; padding:1px 4px; color:inherit; background:rgba(12,20,30,.75); font-size:11px; line-height:16px; }.editor-item.locked { cursor:not-allowed; }.editor-item.locked.selected { border-color:#e9b479; }.editor-color-fields { display:grid; grid-template-columns:1fr 1fr; gap:8px; }.editor-color-fields input[type='color'] { height:32px; padding:3px; }.editor-check { display:flex!important; grid-template-columns:18px 1fr; align-items:center; }.editor-check input { width:auto; }
.editor-item.pump,.editor-item.gate,.editor-item.motor,.editor-item.plc,.editor-item.gauge { flex-direction:column; padding:6px; }.editor-item .industrial-image { min-height:0; flex:1; }.editor-item .symbol-label { font-size:12px; line-height:14px; }.editor-item .symbol-label + small { font-size:10px; color:var(--muted); line-height:12px; }
.library-industrial-image { width:28px; height:28px; object-fit:contain; flex:0 0 28px; }
.editor-template-row { display:grid; grid-template-columns:minmax(0,1fr) auto; gap:5px; }.editor-template-row button:last-child { width:auto; padding-inline:6px; }.editor-template-edit { display:grid; gap:6px; margin:8px 0 14px; }.editor-template-edit input { width:100%; box-sizing:border-box; }.editor-template-edit div { display:grid; grid-template-columns:1fr 1fr; gap:5px; }.editor-template-edit button { margin:0; justify-content:center; }
@media(max-width:1000px) { .editor-workspace { grid-template-columns:130px minmax(0,1fr); }.editor-properties { grid-column:1/-1; border-left:0; border-top:1px solid var(--line); }.editor-fields { grid-template-columns:repeat(4,1fr); } }
</style>
