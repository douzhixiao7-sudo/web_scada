<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { readState, validBinding, type LiveValue, type ReadBinding } from './hmiReading'

const props = defineProps<{ active: boolean; read: <T>(url: string, options?: RequestInit) => Promise<T> }>()
type Kind = 'value' | 'lamp' | 'button' | 'text'
type Item = { id: string; kind: Kind; label: string; x: number; y: number; width: number; height: number; binding?: ReadBinding }
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
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let observer: ResizeObserver | undefined
let generation = 0

const items = computed(() => revision.value?.document.items ?? [])
const deviceIds = computed(() => [...new Set(items.value.flatMap(item => item.binding ? [item.binding.deviceId] : []))])

function validDocument(value: unknown): value is Document {
  if (!value || typeof value !== 'object') return false
  const document = value as Document
  return document.version === 1 && Array.isArray(document.items) && document.items.length <= 1000 && document.items.every(item =>
    typeof item.id === 'string' && ['value', 'lamp', 'button', 'text'].includes(item.kind) && typeof item.label === 'string' &&
    [item.x, item.y, item.width, item.height].every(Number.isFinite) && item.x >= 0 && item.y >= 0 && item.width >= 40 && item.height >= 40 &&
    item.x + item.width <= 1200 && item.y + item.height <= 720 && (item.binding === undefined || validBinding(item.binding)))
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
    const response = await props.read<Revision>('/api/hmi/config/published', { signal: AbortSignal.timeout(8000) })
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
  if (!props.active || run !== generation || !revision.value) return
  clearTimeout(refreshTimer)
  await Promise.all(deviceIds.value.map(async deviceId => {
    try {
      const values = await props.read<LiveValue[]>(`/api/realtime/values?deviceId=${deviceId}`, { signal: AbortSignal.timeout(8000) })
      if (run !== generation) return
      valuesByDevice.value[deviceId] = values
      errorsByDevice.value[deviceId] = ''
    } catch {
      if (run === generation) errorsByDevice.value[deviceId] = '读取失败'
    }
  }))
  if (run === generation) {
    now.value = Date.now()
    if (props.active) refreshTimer = setTimeout(() => void refreshValues(run), 5000)
  }
}

function reading(item: Item) {
  const binding = item.binding
  const problem = binding ? errorsByDevice.value[binding.deviceId] ?? '' : ''
  const value = binding ? valuesByDevice.value[binding.deviceId]?.find(row => row.pointId === binding.pointId) : undefined
  return readState(binding, value, problem, now.value, item.kind === 'lamp')
}

watch(() => props.active, active => {
  generation++
  clearTimeout(refreshTimer)
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
})
onBeforeUnmount(() => { generation++; clearTimeout(refreshTimer); clearInterval(clockTimer); observer?.disconnect() })
</script>

<template>
  <section class="runtime panel" aria-label="已发布组态运行画面">
    <header>
      <div><h3>运行画面</h3><span>{{ revision ? `发布版本 V${revision.version} · ${new Date(revision.createdAt).toLocaleString()}` : '等待发布版本' }}</span></div>
      <button class="ghost compact" type="button" :disabled="loading" @click="loadPublished">{{ loading ? '加载中' : '重新载入' }}</button>
    </header>
    <div v-if="error" class="runtime-state" role="alert"><strong>画面不可用</strong><span>{{ error }}</span></div>
    <div v-else-if="!revision" class="runtime-state"><strong>暂无发布画面</strong><span>请先在画布编辑中保存草稿并发布版本。</span></div>
    <div v-else ref="viewport" class="runtime-viewport">
      <div :style="{ width: `${1200 * scale}px`, height: `${720 * scale}px` }">
        <div class="runtime-stage" :style="{ transform: `scale(${scale})` }">
          <div v-for="item in items" :key="item.id" class="runtime-item" :class="item.kind" :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px` }">
            <template v-if="item.kind === 'value'"><span>{{ item.label }}</span><strong>{{ reading(item).text }}</strong><small :class="{ error: reading(item).status !== '正常' }" :title="reading(item).time">{{ reading(item).status }}</small></template>
            <template v-else-if="item.kind === 'lamp'"><i :class="{ active: reading(item).active, idle: reading(item).status === '正常' && !reading(item).active }"></i><span>{{ item.label }}</span><small :title="reading(item).time">{{ reading(item).status === '正常' ? reading(item).text : reading(item).status }}</small></template>
            <button v-else-if="item.kind === 'button'" type="button" disabled :title="'控制未启用'">{{ item.label }}</button>
            <span v-else>{{ item.label }}</span>
          </div>
        </div>
      </div>
    </div>
    <footer v-if="revision"><span>只读运行 · Redis 当前值</span><span>1200 × 720 · {{ Math.round(scale * 100) }}%</span></footer>
  </section>
</template>

<style scoped>
.runtime { grid-column:1/-1; overflow:hidden; background:#101b27; border-color:#314256; color:#e4edf5; }
.runtime header,.runtime footer { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:12px 16px; }
.runtime header { border-bottom:1px solid #314256; }.runtime footer { border-top:1px solid #314256; color:#93a8bb; font-size:12px; }
.runtime h3 { margin:0 0 3px; font-size:15px; }.runtime header span { color:#93a8bb; font-size:12px; }
.runtime-viewport { min-width:0; overflow:auto; padding:16px; background:#09121b; }
.runtime-stage { position:relative; width:1200px; height:720px; transform-origin:top left; background:#152231; background-image:linear-gradient(rgba(73,96,119,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(73,96,119,.12) 1px,transparent 1px); background-size:40px 40px; }
.runtime-item { position:absolute; display:flex; align-items:center; justify-content:center; gap:8px; box-sizing:border-box; padding:8px; overflow:hidden; overflow-wrap:anywhere; }
.runtime-item.value { flex-wrap:wrap; background:#1c2d3e; border:1px solid #354a5e; }.runtime-item.value span { width:100%; font-size:14px; }.runtime-item.value strong { font-size:24px; }.runtime-item small { color:#9fb0c1; font-size:12px; }.runtime-item small.error { color:#e9b479; }
.runtime-item.lamp i { width:14px; height:14px; flex-shrink:0; border-radius:50%; background:#7d8fa1; }.runtime-item.lamp i.active { background:#55be96; box-shadow:0 0 0 3px rgba(85,190,150,.14); }.runtime-item.lamp i.idle { background:#8095ac; }
.runtime-item button { width:100%; height:100%; color:#9fb0c1; background:#1a2a3b; border:1px solid #354a5e; border-radius:3px; }.runtime-item.text { justify-content:flex-start; }
.runtime-state { min-height:360px; display:grid; place-content:center; gap:8px; text-align:center; color:#93a8bb; }.runtime-state strong { color:#dce7f0; font-size:16px; }
</style>
