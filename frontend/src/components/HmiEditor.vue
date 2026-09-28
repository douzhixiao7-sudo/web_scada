<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Moveable from 'vue3-moveable'
import Selecto from 'vue3-selecto'
import type { OnDrag, OnDragStart, OnDragGroup, OnDragGroupStart } from 'vue3-moveable'

type Kind = 'value' | 'lamp' | 'button' | 'text'
type Item = { id: string; kind: Kind; label: string; x: number; y: number; width: number; height: number }
type Document = { version: 1; items: Item[] }
const library: { kind: Kind; label: string; path: string }[] = [
  { kind: 'value', label: '数值显示', path: 'M4 5h16v14H4z M8 9h8 M8 13h4' },
  { kind: 'lamp', label: '状态指示灯', path: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M9 12h6' },
  { kind: 'button', label: '操作按钮', path: 'M3 6h18v12H3z M9 12h6 M12 9v6' },
  { kind: 'text', label: '文字标签', path: 'M4 5h16 M12 5v14 M8 19h8' },
]
const storageKey = 'scada.hmi.editor.draft.v1'
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
const message = ref('本地草稿 · 尚未接入点位读写')
const saved = ref(JSON.stringify(items.value))
const dirty = computed(() => saved.value !== JSON.stringify(items.value))
const past = ref<string[]>([])
const future = ref<string[]>([])
function checkpoint() {
  past.value.push(JSON.stringify(items.value))
  if (past.value.length > 50) past.value.shift()
  future.value = []
}
async function select(ids: string | string[] = []) {
  selectedIds.value = typeof ids === 'string' ? (ids ? [ids] : []) : ids
  await nextTick()
  targets.value = Array.from(stage.value?.querySelectorAll<HTMLElement>('[data-item-id]') ?? []).filter(element => selectedIds.value.includes(element.dataset.itemId!))
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
  if (!selected.value) return
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
  groupStart = selection.value.map(item => ({ item, x: item.x, y: item.y }))
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
  if (selection.value.length < 2) return
  checkpoint()
  const left = Math.min(...selection.value.map(item => item.x))
  const right = Math.max(...selection.value.map(item => item.x + item.width))
  const top = Math.min(...selection.value.map(item => item.y))
  const bottom = Math.max(...selection.value.map(item => item.y + item.height))
  selection.value.forEach(item => {
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
  if (!selection.value.length) return
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
function add(kind: Kind, x = 80, y = 80) {
  if (preview.value) return
  checkpoint()
  const item: Item = { id: crypto.randomUUID(), kind, label: library.find(entry => entry.kind === kind)!.label, x: Math.max(0, Math.min(1040, Math.round(x / 8) * 8)), y: Math.max(0, Math.min(640, Math.round(y / 8) * 8)), width: 160, height: 80 }
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
  if (!selected.value) return
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
function remove() {
  if (!selection.value.length || preview.value) return
  checkpoint()
  items.value = items.value.filter(item => !selectedIds.value.includes(item.id))
  void select()
}
function duplicate() {
  if (!selection.value.length || preview.value) return
  checkpoint()
  const dx = Math.min(16, 1200 - Math.max(...selection.value.map(item => item.x + item.width)))
  const dy = Math.min(16, 720 - Math.max(...selection.value.map(item => item.y + item.height)))
  const copies = selection.value.map(source => ({ ...source, id: crypto.randomUUID(), x: source.x + dx, y: source.y + dy }))
  items.value.push(...copies)
  void select(copies.map(item => item.id))
}
function resize(event: { target: HTMLElement | SVGElement; width: number; height: number; drag: { beforeTranslate: number[] } }) {
  if (!selected.value) return
  const item = selected.value
  item.x = Math.max(0, Math.min(1160, event.drag.beforeTranslate[0]))
  item.y = Math.max(0, Math.min(680, event.drag.beforeTranslate[1]))
  item.width = Math.max(40, Math.min(1200 - item.x, event.width))
  item.height = Math.max(40, Math.min(720 - item.y, event.height))
  Object.assign(event.target.style, { width: `${item.width}px`, height: `${item.height}px`, left: `${item.x}px`, top: `${item.y}px` })
}
function beginResize(event: { setMin: (size: number[]) => void; dragStart: false | { set: (position: number[]) => void } }) {
  if (!selected.value) return
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
function save() {
  try {
    const document: Document = { version: 1, items: items.value }
    localStorage.setItem(storageKey, JSON.stringify(document))
    saved.value = JSON.stringify(items.value)
    message.value = '已保存到当前浏览器 · ' + new Date().toLocaleTimeString()
  } catch { message.value = '保存失败：浏览器存储不可用，请保留当前页面。' }
}
function load() {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return
    const document = JSON.parse(raw)
    if (document.version !== 1 || !Array.isArray(document.items) || document.items.length > 1000 || !document.items.every((item: Item) =>
      typeof item.id === 'string' && /^[a-zA-Z0-9-]+$/.test(item.id) && library.some(entry => entry.kind === item.kind) && typeof item.label === 'string' && item.label.length <= 80 &&
      [item.x, item.y, item.width, item.height].every(Number.isFinite) && item.x >= 0 && item.y >= 0 && item.width >= 40 && item.height >= 40 && item.x + item.width <= 1200 && item.y + item.height <= 720
    ) || new Set(document.items.map((item: Item) => item.id)).size !== document.items.length) throw new Error('Invalid draft')
    items.value = document.items
    saved.value = JSON.stringify(items.value)
    message.value = '已恢复本地草稿 · 尚未接入点位读写'
  } catch { message.value = '草稿无法读取，原存储未覆盖。请检查后再保存。' }
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
  if (direction && selection.value.length && !command && !event.altKey) {
    event.preventDefault()
    if (!event.repeat) checkpoint()
    const step = event.shiftKey ? 10 : 1
    moveSelection(direction[0] * step, direction[1] * step)
    void nextTick(() => moveable.value?.updateRect())
  }
}
onMounted(() => { load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { if (dirty.value) save(); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section class="editor" aria-label="组态编辑器" @keydown="keydown">
    <header class="editor-toolbar">
      <strong>组态画布 <small>{{ dirty ? '未保存' : '本地草稿' }}</small></strong>
      <div class="editor-actions">
        <button :disabled="preview || !past.length" @click="travel()">撤销</button>
        <button :disabled="preview || !future.length" @click="travel(true)">重做</button>
        <label>缩放 <select v-model.number="zoom" @change="select([...selectedIds])"><option :value="0.5">50%</option><option :value="0.75">75%</option><option :value="1">100%</option><option :value="1.25">125%</option></select></label>
        <button @click="preview = !preview; select()">{{ preview ? '返回编辑' : '预览' }}</button>
        <button class="save" @click="save">保存草稿</button>
      </div>
    </header>
    <div class="editor-workspace" :class="{ preview }">
      <aside v-if="!preview" class="editor-library">
        <h3>基础组件</h3>
        <button v-for="entry in library" :key="entry.kind" draggable="true" @dragstart="$event.dataTransfer?.setData('application/x-scada-component', entry.kind)" @click="add(entry.kind)">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="entry.path" /></svg>{{ entry.label }}
        </button>
        <p>空白处拖动框选，Shift 加选。方向键微调，Shift + 方向键移动 10 px。离开菜单自动保存。</p>
        <h3>图层 <small>{{ items.length }}</small></h3>
        <button v-for="item in [...items].reverse()" :key="item.id" :class="{ chosen: selectedIds.includes(item.id) }" @click="pick(item.id, $event.shiftKey)">{{ item.label }}</button>
      </aside>
      <div class="editor-viewport" @dragover.prevent @drop.prevent="drop">
        <div :style="{ width: `${1200 * zoom}px`, height: `${720 * zoom}px` }">
          <div ref="stage" class="editor-stage" :class="{ 'is-preview': preview }" :style="{ transform: `scale(${zoom})` }" tabindex="0" aria-label="编辑画布">
            <p v-if="!items.length" class="editor-empty">从左侧添加第一个组件</p>
            <div v-for="item in items" :key="item.id" :data-item-id="item.id" class="editor-item" :class="[item.kind, { selected: selectedIds.includes(item.id) && !preview }]" :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px` }" :tabindex="preview ? -1 : 0" :aria-label="item.label" @keydown.enter.prevent="!preview && pick(item.id, $event.shiftKey)" @keydown.space.prevent="!preview && pick(item.id, $event.shiftKey)">
              <template v-if="item.kind === 'value'"><span>{{ item.label }}</span><strong>—</strong><small>未绑定</small></template>
              <template v-else-if="item.kind === 'lamp'"><i></i><span>{{ item.label }}</span><small>未知</small></template>
              <button v-else-if="item.kind === 'button'" disabled>{{ item.label }}</button>
              <span v-else>{{ item.label }}</span>
            </div>
            <Selecto v-if="!preview && stage" ref="selector" :drag-container="stage" :selectable-targets="['.editor-stage .editor-item']" :hit-rate="0" :select-by-click="true" :select-from-inside="false" @drag-start="startSelection" @select="selectedElements" />
            <Moveable v-if="!preview && targets.length" ref="moveable" :target="targets.length === 1 ? targets[0] : targets" :draggable="true" :pass-drag-area="true" :resizable="targets.length === 1" :snappable="true" :snap-grid-width="8" :snap-grid-height="8" :origin="false" @drag-start="beginDrag" @drag="drag" @drag-group-start="beginGroupDrag" @drag-group="dragGroup" @resize-start="beginResize" @resize="resize" />
          </div>
        </div>
      </div>
      <aside v-if="!preview" class="editor-properties">
        <h3>组件属性</h3>
        <template v-if="selected">
          <label>名称<input :value="selected.label" maxlength="80" @change="update('label', $event)" /></label>
          <div class="editor-fields"><label v-for="field in (['x', 'y', 'width', 'height'] as const)" :key="field">{{ { x: 'X 坐标', y: 'Y 坐标', width: '宽度', height: '高度' }[field] }}<input type="number" :value="Math.round(selected[field])" @change="update(field, $event)" /></label></div>
          <h3>数据与动作</h3><p>下一步接入点位读取与写入配置。当前组件不下发控制。</p>
        </template>
        <p v-else-if="selection.length">已选择 {{ selection.length }} 个组件，可批量移动、对齐和调整图层。</p>
        <p v-else>选择画布组件后编辑属性</p>
        <template v-if="selection.length">
          <div class="editor-actions"><button @click="duplicate">复制</button><button @click="remove">删除</button></div>
          <h3>对齐</h3>
          <div class="editor-commands">
            <button v-for="option in ([['left', '左对齐'], ['center', '水平居中'], ['right', '右对齐'], ['top', '顶对齐'], ['middle', '垂直居中'], ['bottom', '底对齐']] as const)" :key="option[0]" :disabled="selection.length < 2" @click="align(option[0])">{{ option[1] }}</button>
          </div>
          <h3>图层顺序</h3>
          <div class="editor-commands"><button @click="layer('up')">上移一层</button><button @click="layer('down')">下移一层</button><button @click="layer('front')">置顶</button><button @click="layer('back')">置底</button></div>
        </template>
      </aside>
    </div>
    <footer role="status"><span>{{ message }}</span><span>1200 × 720 · {{ preview ? '预览 / 控制未启用' : '编辑 / 8 px 网格' }}</span></footer>
  </section>
</template>

<style scoped>
.editor-commands { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
.editor { --surface:#121e2b; --line:#35465a; --muted:#a6b6c8; --accent:#78b7ef; grid-column:1/-1; min-width:0; color:#e4edf5; background:var(--surface); border:1px solid var(--line); border-radius:4px; overflow:hidden; }
.editor-toolbar,.editor-actions,.editor footer { display:flex; align-items:center; gap:8px; }
.editor-toolbar { justify-content:space-between; flex-wrap:wrap; padding:12px 16px; border-bottom:1px solid var(--line); }
.editor strong { font-size:14px; }.editor small,.editor p { color:var(--muted); font-size:12px; }.editor-toolbar small { margin-left:12px; font-weight:400; }
.editor button,.editor input,.editor select { font:inherit; font-size:13px; color:inherit; background:#1a2a3b; border:1px solid var(--line); border-radius:3px; padding:7px 10px; min-width:0; }
.editor button { cursor:pointer; }.editor button:hover:not(:disabled),.editor button.chosen { border-color:var(--accent); background:#243c53; }.editor button:disabled { opacity:.5; cursor:default; }.editor :focus-visible { outline:2px solid var(--accent); outline-offset:2px; }.editor .save { border-color:#669ccc; }.editor-actions label { white-space:nowrap; display:flex; align-items:center; gap:6px; font-size:12px; }
.editor-workspace { display:grid; grid-template-columns:156px minmax(0,1fr) 208px; min-height:540px; }.editor-workspace.preview { grid-template-columns:minmax(0,1fr); }.editor-library,.editor-properties { padding:14px 12px; max-height:650px; overflow:auto; }.editor-library { border-right:1px solid var(--line); }.editor-properties { border-left:1px solid var(--line); }.editor h3 { font-size:13px; margin:0 0 14px; }.editor h3:not(:first-child) { margin-top:24px; }.editor-library button { display:flex; gap:8px; align-items:center; width:100%; text-align:left; margin-bottom:8px; overflow:hidden; overflow-wrap:anywhere; }.editor svg { width:18px; height:18px; flex-shrink:0; fill:none; stroke:currentColor; stroke-width:1.5; }.editor p { line-height:1.7; }
.editor-viewport { overflow:auto; padding:24px; background:#0c141e; min-width:0; max-height:650px; }.editor-stage { position:relative; width:1200px; height:720px; transform-origin:top left; background-color:#152231; background-image:radial-gradient(#33475b 1px,transparent 1px); background-size:8px 8px; }.editor-stage.is-preview { background-image:none; }.editor-empty { position:absolute; top:40%; width:100%; text-align:center; pointer-events:none; }
.editor-item { position:absolute; display:flex; gap:8px; align-items:center; justify-content:center; padding:8px; box-sizing:border-box; border:1px solid transparent; user-select:none; overflow:hidden; overflow-wrap:anywhere; }.editor-item.selected { border-color:var(--accent); }.editor-item.value { flex-wrap:wrap; background:#1c2d3e; }.editor-item.value strong { font-size:24px; }.editor-item.value span { width:100%; font-size:14px; }.editor-item.lamp i { width:14px; height:14px; background:#8392a3; border-radius:50%; flex-shrink:0; }.editor-item button { width:100%; height:100%; pointer-events:none; }.editor-item.text { justify-content:flex-start; }.editor-properties label { display:grid; gap:6px; font-size:12px; margin-bottom:12px; }.editor-fields { display:grid; grid-template-columns:1fr 1fr; gap:8px; }.editor input { width:100%; box-sizing:border-box; }.editor footer { justify-content:space-between; flex-wrap:wrap; padding:10px 14px; border-top:1px solid var(--line); color:var(--muted); font-size:12px; }
@media(max-width:1000px) { .editor-workspace { grid-template-columns:130px minmax(0,1fr); }.editor-properties { grid-column:1/-1; border-left:0; border-top:1px solid var(--line); }.editor-fields { grid-template-columns:repeat(4,1fr); } }
</style>
