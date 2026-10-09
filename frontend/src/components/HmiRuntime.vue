<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { readState, validBinding, type LiveValue, type ReadBinding, type ReadState } from './hmiReading'
import HmiIndustrialImage from './HmiIndustrialImage.vue'

const props = defineProps<{ active: boolean; screenId: number; read: <T>(url: string, options?: RequestInit) => Promise<T> }>()
type Kind = 'value' | 'lamp' | 'button' | 'text' | 'rectangle' | 'ellipse' | 'line' | 'pipe' | 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'
type ItemStyle = { fill: string; stroke: string; strokeWidth: number; textColor: string }
type Item = { id: string; kind: Kind; label: string; x: number; y: number; width: number; height: number; binding?: ReadBinding; groupId?: string; locked?: boolean; style?: ItemStyle; route?: 'horizontal' | 'vertical' | 'elbow'; reversed?: boolean }
type Document = { version: 1; items: Item[] }
type Revision = { id: number; version: number; document: Document; publishedBy: string; createdAt: string; current: boolean }

const viewport = ref<HTMLElement | null>(null)
const revision = ref<Revision | null>(null)
const valuesByDevice = ref<Record<number, LiveValue[]>>({})
const errorsByDevice = ref<Record<number, string>>({})
const loading = ref(false)
const error = ref('')
const scale = ref(0.75)
const now = ref(Date.now())
const lastRefreshAt = ref('')
const failureStreak = ref(0)
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let observer: ResizeObserver | undefined
let generation = 0

const items = computed(() => revision.value?.document.items ?? [])
const deviceIds = computed(() => [...new Set(items.value.flatMap(item => item.binding ? [item.binding.deviceId] : []))])
const readableKinds: Kind[] = ['value', 'lamp', 'pump', 'gate', 'motor', 'plc', 'gauge']
const itemStates = computed(() => new Map(items.value.filter(isReadable).map(item => [item.id, reading(item)])))
const summary = computed(() => {
  const states = [...itemStates.value.values()]
  return {
    normal: states.filter(state => state.kind === 'normal' || state.kind === 'inactive').length,
    abnormal: states.filter(state => ['stale', 'quality', 'offline', 'invalid'].includes(state.kind)).length,
    missing: states.filter(state => state.kind === 'missing').length,
    unbound: states.filter(state => state.kind === 'unbound').length,
  }
})
const defaultStyle: ItemStyle = { fill: '#253f53', stroke: '#82a9c8', strokeWidth: 2, textColor: '#a9bed0' }
function itemStyle(item: Item) { return { ...defaultStyle, ...item.style } }
function pathPoints(item: Item) {
  const horizontal = `4,${item.height / 2} ${item.width - 10},${item.height / 2}`
  const vertical = `${item.width / 2},4 ${item.width / 2},${item.height - 10}`
  const elbow = `4,4 ${item.width / 2},4 ${item.width / 2},${item.height - 6} ${item.width - 10},${item.height - 6}`
  const points = (item.route === 'vertical' ? vertical : item.route === 'elbow' ? elbow : horizontal).split(' ')
  return (item.reversed ? points.reverse() : points).join(' ')
}

function validDocument(value: unknown): value is Document {
  if (!value || typeof value !== 'object') return false
  const document = value as Document
  return document.version === 1 && Array.isArray(document.items) && document.items.length <= 1000 && document.items.every(item =>
    typeof item.id === 'string' && ['value', 'lamp', 'button', 'text', 'rectangle', 'ellipse', 'line', 'pipe', 'pump', 'gate', 'motor', 'plc', 'gauge'].includes(item.kind) && typeof item.label === 'string' &&
    [item.x, item.y, item.width, item.height].every(Number.isFinite) && item.x >= 0 && item.y >= 0 && item.width >= 40 && item.height >= 40 &&
    item.x + item.width <= 1200 && item.y + item.height <= 720 &&
    (item.groupId === undefined || /^[a-zA-Z0-9-]+$/.test(item.groupId)) && (item.locked === undefined || typeof item.locked === 'boolean') &&
    (item.style === undefined || (/^#[0-9a-fA-F]{6}$/.test(item.style.fill) && /^#[0-9a-fA-F]{6}$/.test(item.style.stroke) && /^#[0-9a-fA-F]{6}$/.test(item.style.textColor) && Number.isFinite(item.style.strokeWidth) && item.style.strokeWidth >= 1 && item.style.strokeWidth <= 12)) &&
    (item.route === undefined || ['horizontal', 'vertical', 'elbow'].includes(item.route)) && (item.reversed === undefined || typeof item.reversed === 'boolean') &&
    (item.binding === undefined || validBinding(item.binding)))
}

function resize() {
  if (!viewport.value) return
  scale.value = Math.max(0.35, Math.min(1, (viewport.value.clientWidth - 48) / 1200))
}

async function loadPublished() {
  if (!props.active || loading.value) return
  generation++
  const run = generation
  clearTimeout(refreshTimer)
  loading.value = true
  error.value = ''
  try {
    const response = await props.read<Revision>(`/api/hmi/config/published?screenId=${props.screenId}`, { signal: AbortSignal.timeout(8000) })
    if (run !== generation || !validDocument(response.document)) throw new Error('发布版本格式无效')
    revision.value = response
    await refreshValues(run)
  } catch (reason) {
    if (run === generation) error.value = reason instanceof Error ? reason.message : '发布画面读取失败'
  } finally {
    if (run === generation) {
      loading.value = false
    }
  }
}

async function refreshValues(run = generation) {
  if (!props.active || run !== generation || !revision.value || pageHidden()) return
  clearTimeout(refreshTimer)
  let failed = false
  await Promise.all(deviceIds.value.map(async deviceId => {
    try {
      const values = await props.read<LiveValue[]>(`/api/realtime/values?deviceId=${deviceId}`, { signal: AbortSignal.timeout(8000) })
      if (run !== generation) return
      valuesByDevice.value[deviceId] = values
      errorsByDevice.value[deviceId] = ''
    } catch {
      failed = true
      if (run === generation) errorsByDevice.value[deviceId] = '读取失败'
    }
  }))
  if (run === generation) {
    now.value = Date.now()
    lastRefreshAt.value = new Date(now.value).toLocaleTimeString()
    failureStreak.value = failed ? failureStreak.value + 1 : 0
    const delay = failed ? Math.min(30000, 5000 * (2 ** Math.min(3, Math.max(0, failureStreak.value - 1)))) : 5000
    if (props.active && !pageHidden()) refreshTimer = setTimeout(() => void refreshValues(run), delay)
  }
}

function reading(item: Item) {
  const binding = item.binding
  const problem = binding ? errorsByDevice.value[binding.deviceId] ?? '' : ''
  const value = binding ? valuesByDevice.value[binding.deviceId]?.find(row => row.pointId === binding.pointId) : undefined
  return readState(binding, value, problem, now.value, ['lamp', 'pump', 'gate', 'motor', 'plc'].includes(item.kind))
}
function stateOf(item: Item): ReadState { return itemStates.value.get(item.id) ?? reading(item) }
function isReadable(item: Item) { return readableKinds.includes(item.kind) }
function pageHidden() { return document.visibilityState === 'hidden' }
function visibilityChanged() {
  clearTimeout(refreshTimer)
  if (document.visibilityState === 'visible' && props.active && revision.value) void refreshValues(generation)
}

watch(() => [props.active, props.screenId] as const, ([active]) => {
  generation++
  clearTimeout(refreshTimer)
  revision.value = null; valuesByDevice.value = {}; errorsByDevice.value = {}; error.value = ''; failureStreak.value = 0; lastRefreshAt.value = ''
  if (active) void loadPublished()
}, { immediate: true })
watch(viewport, element => {
  observer?.disconnect()
  if (element) { observer?.observe(element); resize() }
})

onMounted(() => {
  observer = new ResizeObserver(resize)
  if (viewport.value) observer.observe(viewport.value)
  resize()
  clockTimer = setInterval(() => { now.value = Date.now() }, 1000)
  document.addEventListener('visibilitychange', visibilityChanged)
})
onBeforeUnmount(() => { generation++; clearTimeout(refreshTimer); clearInterval(clockTimer); observer?.disconnect(); document.removeEventListener('visibilitychange', visibilityChanged) })
</script>

<template>
  <section class="runtime panel" aria-label="已发布组态运行画面">
    <header>
      <div><h3>运行画面</h3><span>{{ revision ? `发布版本 V${revision.version} · ${new Date(revision.createdAt).toLocaleString()}` : '等待发布版本' }}</span></div>
      <button class="ghost compact" type="button" :disabled="loading" @click="loadPublished">{{ loading ? '加载中' : '重新载入' }}</button>
    </header>
    <div v-if="error" class="runtime-state" role="alert"><strong>画面不可用</strong><span>{{ error }}</span></div>
    <div v-else-if="!revision" class="runtime-state"><strong>暂无发布画面</strong><span>请先在画布编辑中保存草稿并发布版本。</span></div>
    <template v-else>
      <div class="runtime-summary" aria-label="运行画面状态汇总">
        <span><b>{{ summary.normal }}</b> 正常</span><span :class="{ alert: summary.abnormal }"><b>{{ summary.abnormal }}</b> 异常</span><span :class="{ alert: summary.missing }"><b>{{ summary.missing }}</b> 未采集</span><span><b>{{ summary.unbound }}</b> 未绑定</span><small>{{ failureStreak ? `通信重试 · 第 ${failureStreak} 次` : `最近刷新 ${lastRefreshAt || '等待数据'}` }}</small>
      </div>
      <div ref="viewport" class="runtime-viewport">
        <div :style="{ width: `${1200 * scale}px`, height: `${720 * scale}px` }">
          <div class="runtime-stage" :style="{ transform: `scale(${scale})` }">
            <div v-for="item in items" :key="item.id" class="runtime-item" :class="[item.kind, isReadable(item) ? `state-${stateOf(item).kind}` : '']" :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px`, color: itemStyle(item).textColor, backgroundColor: item.kind === 'rectangle' || item.kind === 'ellipse' ? itemStyle(item).fill : undefined, borderColor: item.kind === 'rectangle' || item.kind === 'ellipse' ? itemStyle(item).stroke : undefined, borderWidth: item.kind === 'rectangle' || item.kind === 'ellipse' ? `${itemStyle(item).strokeWidth}px` : undefined }" :title="isReadable(item) ? (stateOf(item).time ? `${stateOf(item).status} · ${new Date(stateOf(item).time).toLocaleString()}` : stateOf(item).status) : undefined">
            <template v-if="item.kind === 'value'"><span>{{ item.label }}</span><strong>{{ stateOf(item).text }}</strong><small :class="{ error: stateOf(item).status !== '正常' }">{{ stateOf(item).status }}</small></template>
            <template v-else-if="item.kind === 'lamp'"><i :class="{ active: stateOf(item).active, idle: stateOf(item).kind === 'inactive' }"></i><span>{{ item.label }}</span><small>{{ stateOf(item).status === '正常' ? stateOf(item).text : stateOf(item).status }}</small></template>
            <button v-else-if="item.kind === 'button'" type="button" disabled :title="'控制未启用'">{{ item.label }}</button>
            <span v-else-if="item.kind === 'text'">{{ item.label }}</span>
            <template v-else-if="item.kind === 'line' || item.kind === 'pipe'">
              <svg class="process-path" :viewBox="`0 0 ${item.width} ${item.height}`" preserveAspectRatio="none" aria-hidden="true">
                <defs><marker :id="`runtime-arrow-${item.id}`" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" :fill="itemStyle(item).stroke" /></marker></defs>
                <polyline v-if="item.kind === 'pipe'" :points="pathPoints(item)" fill="none" :stroke="itemStyle(item).stroke" :stroke-width="itemStyle(item).strokeWidth + 6" stroke-linecap="square" stroke-linejoin="round" />
                <polyline :points="pathPoints(item)" fill="none" :stroke="item.kind === 'pipe' ? itemStyle(item).fill : itemStyle(item).stroke" :stroke-width="itemStyle(item).strokeWidth" stroke-linecap="square" stroke-linejoin="round" :marker-end="`url(#runtime-arrow-${item.id})`" />
              </svg>
              <span class="shape-label">{{ item.label }}</span>
            </template>
            <template v-else-if="['pump', 'gate', 'motor', 'plc', 'gauge'].includes(item.kind)">
              <HmiIndustrialImage :kind="item.kind as 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'" :active="stateOf(item).active" />
              <span class="symbol-label">{{ item.label }}</span><small :class="{ error: stateOf(item).status !== '正常' }">{{ stateOf(item).status === '正常' ? stateOf(item).text : stateOf(item).status }}</small>
            </template>
            <span v-else class="shape-label">{{ item.label }}</span>
          </div>
        </div>
        </div>
      </div>
    </template>
    <footer v-if="revision"><span>只读运行 · Redis 当前值</span><span>1200 × 720 · {{ Math.round(scale * 100) }}%</span></footer>
  </section>
</template>

<style scoped>
.runtime { grid-column:1/-1; overflow:hidden; background:#101b27; border-color:#314256; color:#e4edf5; }
.runtime header,.runtime footer { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:12px 16px; }
.runtime header { border-bottom:1px solid #314256; }.runtime footer { border-top:1px solid #314256; color:#93a8bb; font-size:12px; }
.runtime h3 { margin:0 0 3px; font-size:15px; }.runtime header span { color:#93a8bb; font-size:12px; }
.runtime-summary { display:flex; align-items:center; gap:18px; min-height:38px; padding:0 16px; color:#9fb0c1; background:#111f2c; border-bottom:1px solid #314256; font-size:12px; }.runtime-summary span { display:flex; align-items:baseline; gap:5px; white-space:nowrap; }.runtime-summary b { color:#dce7f0; font-size:14px; }.runtime-summary .alert,.runtime-summary .alert b { color:#e9b479; }.runtime-summary small { margin-left:auto; color:#8399ad; }
.runtime-viewport { min-width:0; overflow:auto; padding:16px; background:#09121b; }
.runtime-stage { position:relative; width:1200px; height:720px; transform-origin:top left; background:#152231; background-image:linear-gradient(rgba(73,96,119,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(73,96,119,.12) 1px,transparent 1px); background-size:40px 40px; }
.runtime-item { position:absolute; display:flex; align-items:center; justify-content:center; gap:8px; box-sizing:border-box; padding:8px; overflow:hidden; overflow-wrap:anywhere; }
.runtime-item.state-stale,.runtime-item.state-quality,.runtime-item.state-offline,.runtime-item.state-invalid { outline:1px solid #9b7550; outline-offset:-1px; background-image:repeating-linear-gradient(135deg,rgba(233,180,121,.035) 0,rgba(233,180,121,.035) 5px,transparent 5px,transparent 10px); }.runtime-item.state-missing { outline:1px dashed #6f8294; outline-offset:-1px; }.runtime-item.state-unbound { opacity:.68; }.runtime-item.state-stale .industrial-image,.runtime-item.state-quality .industrial-image,.runtime-item.state-offline .industrial-image,.runtime-item.state-invalid .industrial-image { filter:grayscale(.72); opacity:.62; }
.runtime-item.value { flex-wrap:wrap; background:#1c2d3e; border:1px solid #354a5e; }.runtime-item.value span { width:100%; font-size:14px; }.runtime-item.value strong { font-size:24px; }.runtime-item small { color:#9fb0c1; font-size:12px; }.runtime-item small.error { color:#e9b479; }
.runtime-item.lamp i { width:14px; height:14px; flex-shrink:0; border-radius:50%; background:#7d8fa1; }.runtime-item.lamp i.active { background:#55be96; box-shadow:0 0 0 3px rgba(85,190,150,.14); }.runtime-item.lamp i.idle { background:#8095ac; }
.runtime-item button { width:100%; height:100%; color:#9fb0c1; background:#1a2a3b; border:1px solid #354a5e; border-radius:3px; }.runtime-item.text { justify-content:flex-start; }
.runtime-item.rectangle { background:rgba(42,67,88,.34); border:1px solid #54708a; }.runtime-item.ellipse { border:2px solid #54708a; border-radius:50%; background:rgba(42,67,88,.18); }.runtime-item.line,.runtime-item.pipe { padding:0; overflow:visible; }.process-path { position:absolute; inset:0; width:100%; height:100%; overflow:visible; pointer-events:none; }.runtime-item .shape-label { position:absolute; left:8px; top:4px; padding:1px 4px; color:inherit; background:rgba(12,20,30,.75); font-size:11px; line-height:16px; }
.runtime-item.pump,.runtime-item.gate,.runtime-item.motor,.runtime-item.plc,.runtime-item.gauge { flex-direction:column; padding:6px; }.runtime-item .industrial-image { min-height:0; flex:1; }.runtime-item .symbol-label { font-size:12px; line-height:14px; }.runtime-item .symbol-label + small { font-size:10px; line-height:12px; }
.runtime-state { min-height:360px; display:grid; place-content:center; gap:8px; text-align:center; color:#93a8bb; }.runtime-state strong { color:#dce7f0; font-size:16px; }
@media(max-width:760px) { .runtime-summary { flex-wrap:wrap; gap:8px 14px; padding-block:8px; }.runtime-summary small { width:100%; margin-left:0; } }
</style>
