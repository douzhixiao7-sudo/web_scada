<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Moveable from 'vue3-moveable'

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
const selectedId = ref('')
const selected = computed(() => items.value.find(item => item.id === selectedId.value))
const stage = ref<HTMLElement | null>(null)
const target = ref<HTMLElement | null>(null)
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
async function select(id = '') {
  selectedId.value = id
  await nextTick()
  target.value = stage.value?.querySelector<HTMLElement>(`[data-item-id="${id}"]`) ?? null
  moveable.value?.updateRect()
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
  if (!selected.value || preview.value) return
  checkpoint()
  items.value = items.value.filter(item => item.id !== selectedId.value)
  void select()
}
function duplicate() {
  if (!selected.value) return
  checkpoint()
  const source = selected.value
  const copy = { ...source, id: crypto.randomUUID(), x: Math.min(1200 - source.width, source.x + 16), y: Math.min(720 - source.height, source.y + 16) }
  items.value.push(copy)
  void select(copy.id)
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
  if (preview.value || (event.target as HTMLElement).closest('input,textarea,select,button')) return
  if (event.key === 'Delete') { event.preventDefault(); remove() }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'z') { event.preventDefault(); travel(event.shiftKey) }
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
        <label>缩放 <select v-model.number="zoom" @change="select(selectedId)"><option :value="0.5">50%</option><option :value="0.75">75%</option><option :value="1">100%</option><option :value="1.25">125%</option></select></label>
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
        <p>拖入画布或点击添加。离开菜单自动保存本地草稿。</p>
        <h3>图层 <small>{{ items.length }}</small></h3>
        <button v-for="item in [...items].reverse()" :key="item.id" :class="{ chosen: selectedId === item.id }" @click="select(item.id)">{{ item.label }}</button>
      </aside>
      <div class="editor-viewport" @dragover.prevent @drop.prevent="drop">
        <div :style="{ width: `${1200 * zoom}px`, height: `${720 * zoom}px` }">
          <div ref="stage" class="editor-stage" :class="{ 'is-preview': preview }" :style="{ transform: `scale(${zoom})` }" @mousedown.self="select()">
            <p v-if="!items.length" class="editor-empty">从左侧添加第一个组件</p>
            <div v-for="item in items" :key="item.id" :data-item-id="item.id" class="editor-item" :class="[item.kind, { selected: selectedId === item.id && !preview }]" :style="{ left: `${item.x}px`, top: `${item.y}px`, width: `${item.width}px`, height: `${item.height}px` }" :tabindex="preview ? -1 : 0" :aria-label="item.label" @mousedown="!preview && select(item.id)" @focus="!preview && select(item.id)">
              <template v-if="item.kind === 'value'"><span>{{ item.label }}</span><strong>—</strong><small>未绑定</small></template>
              <template v-else-if="item.kind === 'lamp'"><i></i><span>{{ item.label }}</span><small>未知</small></template>
              <button v-else-if="item.kind === 'button'" disabled>{{ item.label }}</button>
              <span v-else>{{ item.label }}</span>
            </div>
            <Moveable v-if="!preview && target" ref="moveable" :target="target" :draggable="true" :resizable="true" :snappable="true" :snap-grid-width="8" :snap-grid-height="8" :origin="false" @drag-start="checkpoint(); $event.set([selected!.x, selected!.y])" @drag="selected && (selected.x = Math.max(0, Math.min(1200-selected.width, $event.beforeTranslate[0])), selected.y = Math.max(0, Math.min(720-selected.height, $event.beforeTranslate[1])))" @resize-start="beginResize" @resize="resize" />
          </div>
        </div>
      </div>
      <aside v-if="!preview" class="editor-properties">
        <h3>组件属性</h3>
        <template v-if="selected">
          <label>名称<input :value="selected.label" maxlength="80" @change="update('label', $event)" /></label>
          <div class="editor-fields"><label v-for="field in (['x', 'y', 'width', 'height'] as const)" :key="field">{{ { x: 'X 坐标', y: 'Y 坐标', width: '宽度', height: '高度' }[field] }}<input type="number" :value="Math.round(selected[field])" @change="update(field, $event)" /></label></div>
          <div class="editor-actions"><button @click="duplicate">复制</button><button @click="remove">删除</button></div>
          <h3>数据与动作</h3><p>下一步接入点位读取与写入配置。当前组件不下发控制。</p>
        </template>
        <p v-else>选择画布组件后编辑属性</p>
      </aside>
    </div>
    <footer role="status"><span>{{ message }}</span><span>1200 × 720 · {{ preview ? '预览 / 控制未启用' : '编辑 / 8 px 网格' }}</span></footer>
  </section>
</template>

<style scoped>
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
