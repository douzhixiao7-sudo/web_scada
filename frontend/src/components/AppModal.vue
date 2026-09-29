<script setup lang="ts">
import { computed } from 'vue'

type ModalTone = 'default' | 'success' | 'warning' | 'danger'
type ModalMode = 'default' | 'create' | 'edit'
type ModalSize = 'sm' | 'md' | 'lg'
type ThemeMode = 'dark' | 'light'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  subtitle?: string
  tone?: ModalTone
  mode?: ModalMode
  size?: ModalSize
  confirmText?: string
  cancelText?: string
  showCancel?: boolean
  themeMode?: ThemeMode
  closeOnConfirm?: boolean
}>(), {
  subtitle: '',
  tone: 'default',
  mode: 'default',
  size: 'md',
  confirmText: '',
  cancelText: '取消',
  showCancel: true,
  themeMode: 'dark',
  closeOnConfirm: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

function close() {
  emit('update:modelValue', false)
}

function cancel() {
  emit('cancel')
  close()
}

function confirm() {
  emit('confirm')
  if (props.closeOnConfirm) close()
}

const kickerText = computed(() => {
  if (props.mode === 'create') return 'CREATE RECORD'
  if (props.mode === 'edit') return 'EDIT RECORD'
  return 'SYSTEM DIALOG'
})

const resolvedConfirmText = computed(() => {
  if (props.confirmText) return props.confirmText
  if (props.mode === 'create') return '新增'
  if (props.mode === 'edit') return '保存'
  return '确认'
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="modelValue" :class="['modal-backdrop', `theme-${themeMode}`]" @click.self="cancel">
        <section :class="['app-modal', `tone-${tone}`, `mode-${mode}`, `size-${size}`]" role="dialog" aria-modal="true" :aria-label="title">
          <button class="modal-close" type="button" aria-label="关闭弹窗" @click="cancel">×</button>
          <div class="modal-orbit" aria-hidden="true"></div>
          <header class="modal-head">
            <span class="modal-kicker">{{ kickerText }}</span>
            <h3>{{ title }}</h3>
            <p v-if="subtitle">{{ subtitle }}</p>
          </header>
          <div class="modal-body">
            <slot />
          </div>
          <footer class="modal-actions">
            <button v-if="showCancel" class="modal-button secondary" type="button" @click="cancel">{{ cancelText }}</button>
            <button class="modal-button primary" type="button" @click="confirm">{{ resolvedConfirmText }}</button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 42%, rgba(65, 199, 185, .08), transparent 36%),
    rgba(2, 8, 12, .46);
  backdrop-filter: blur(4px);
}

.app-modal {
  position: relative;
  width: min(560px, 100%);
  overflow: hidden;
  border: 1px solid rgba(102, 225, 229, .28);
  background:
    linear-gradient(90deg, rgba(91, 218, 226, .045) 1px, transparent 1px),
    linear-gradient(0deg, rgba(91, 218, 226, .032) 1px, transparent 1px),
    linear-gradient(145deg, rgba(19, 52, 62, .96), rgba(6, 15, 22, .98) 56%, rgba(3, 8, 13, .98));
  background-size: 28px 28px, 28px 28px, auto;
  box-shadow:
    0 28px 110px rgba(0, 0, 0, .48),
    inset 0 1px 0 rgba(255,255,255,.07);
  max-height: calc(100vh - 48px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.app-modal.size-sm { width: min(420px, 100%); }
.app-modal.size-lg { width: min(760px, 100%); }

.app-modal::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-top: 2px solid rgba(111, 242, 238, .72);
  background: linear-gradient(90deg, rgba(111, 242, 238, .16), transparent 42%);
}

.tone-success { border-color: rgba(91, 215, 143, .42); }
.tone-warning { border-color: rgba(255, 209, 102, .48); }
.tone-danger { border-color: rgba(255, 125, 141, .5); }
.mode-create::before { border-top-color: rgba(91, 215, 143, .76); }
.mode-edit::before { border-top-color: rgba(117, 167, 255, .78); }

.modal-close {
  position: absolute;
  right: 14px;
  top: 12px;
  z-index: 2;
  width: 34px;
  height: 34px;
  color: #9fb9c0;
  background: rgba(255,255,255,.035);
  border: 1px solid rgba(102, 225, 229, .18);
  font-size: 22px;
  line-height: 1;
}

.modal-close:hover {
  color: #f4ffff;
  border-color: rgba(111, 242, 238, .55);
}

.modal-orbit {
  position: absolute;
  right: -90px;
  top: -90px;
  width: 230px;
  height: 230px;
  border-radius: 50%;
  border: 1px solid rgba(111, 242, 238, .12);
  box-shadow: inset 0 0 42px rgba(111, 242, 238, .06);
}

.modal-head {
  position: relative;
  z-index: 1;
  padding: 28px 30px 18px;
  border-bottom: 1px solid rgba(102, 225, 229, .14);
}

.modal-kicker {
  display: inline-flex;
  margin-bottom: 10px;
  color: #9ef6f1;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .16em;
}

.modal-head h3 {
  margin: 0;
  color: #f2ffff;
  font-size: 24px;
  line-height: 1.15;
}

.modal-head p {
  margin: 10px 0 0;
  color: #93aab2;
  line-height: 1.65;
}

.modal-body {
  position: relative;
  z-index: 1;
  padding: 24px 30px;
  color: #dceff2;
  line-height: 1.7;
  overflow-y: auto;
  max-height: min(62vh, 620px);
}

:deep(.modal-form-grid) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

:deep(.modal-tabs .n-tabs-nav) {
  margin-bottom: 18px;
}

:deep(.modal-tabs .n-tabs-tab) {
  color: #93aab2;
}

:deep(.modal-tabs .n-tabs-tab.n-tabs-tab--active) {
  color: #9ef6f1;
}

:deep(.modal-form-grid label) {
  display: grid;
  gap: 8px;
  color: #92aab2;
  font-size: 13px;
}

:deep(.modal-form-grid label.span-2) {
  grid-column: 1 / -1;
}

:deep(.modal-form-grid input),
:deep(.modal-form-grid select) {
  width: 100%;
  min-height: 40px;
  color: #e8f7f8;
  background: rgba(2, 11, 17, .72);
  border: 1px solid rgba(102, 225, 229, .22);
  padding: 0 11px;
}

:deep(.modal-form-grid .n-input),
:deep(.modal-form-grid .n-base-selection),
:deep(.modal-form-grid .n-input-number) {
  --n-border: 1px solid rgba(102, 225, 229, .22) !important;
  --n-border-hover: 1px solid rgba(111, 242, 238, .52) !important;
  --n-border-focus: 1px solid rgba(111, 242, 238, .76) !important;
  --n-color: rgba(2, 11, 17, .72) !important;
  --n-color-focus: rgba(2, 11, 17, .82) !important;
}

:deep(.modal-form-grid .n-input-number) {
  width: 100%;
}

:deep(.modal-form-grid .n-input .n-input-wrapper) {
  padding-left: 11px !important;
  padding-right: 11px !important;
}

:deep(.modal-form-grid .n-input input),
:deep(.modal-form-grid .n-input-number input) {
  min-height: auto !important;
  background: transparent !important;
  border: 0 !important;
  padding: 0 !important;
}

:deep(.modal-form-grid .n-input.n-input--textarea .n-input-wrapper) {
  padding: 10px 11px !important;
}

:deep(.modal-form-grid .n-input.n-input--textarea .n-input__placeholder),
:deep(.modal-form-grid .n-input.n-input--textarea .n-input__textarea-el) {
  padding: 0 !important;
  line-height: 1.6 !important;
}

:deep(.modal-form-grid textarea) {
  min-height: 88px;
  background: transparent !important;
  border: 0 !important;
  padding: 0 !important;
  line-height: 1.6 !important;
  resize: vertical;
}

:deep(.modal-hint) {
  margin: 14px 0 0;
  color: #7f969f;
  font-size: 13px;
}

:deep(.hidden-submit) {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.modal-actions {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 18px 30px 26px;
}

.modal-button {
  min-height: 38px;
  padding: 0 18px;
  color: #dceff2;
  background: rgba(255,255,255,.04);
  border: 1px solid rgba(102, 225, 229, .2);
}

.modal-button.primary {
  color: #041f22;
  background: linear-gradient(90deg, #6ff2ee, #9ef6d5);
  border-color: transparent;
  font-weight: 800;
}

.modal-button.secondary:hover {
  color: #f4ffff;
  border-color: rgba(111, 242, 238, .55);
}

.modal-backdrop.theme-light {
  background:
    radial-gradient(circle at 50% 40%, rgba(10, 166, 166, .12), transparent 34%),
    rgba(230, 241, 247, .48);
  backdrop-filter: blur(2px);
}

.theme-light .app-modal {
  border-color: rgba(128, 164, 184, .52);
  background:
    linear-gradient(90deg, rgba(10,166,166,.055) 1px, transparent 1px),
    linear-gradient(0deg, rgba(10,166,166,.04) 1px, transparent 1px),
    linear-gradient(145deg, rgba(255,255,255,.98), rgba(245,250,253,.98) 58%, rgba(234,244,250,.98));
  box-shadow:
    0 30px 88px rgba(54, 96, 122, .22),
    inset 0 1px 0 rgba(255,255,255,.86);
}

.theme-light .app-modal::before {
  border-top-color: rgba(10,166,166,.58);
  background: linear-gradient(90deg, rgba(10,166,166,.12), transparent 42%);
}

.theme-light .modal-close {
  color: #5b7280;
  background: rgba(255,255,255,.68);
  border-color: rgba(128, 164, 184, .38);
}

.theme-light .modal-close:hover {
  color: #073f52;
  border-color: rgba(10,166,166,.46);
}

.theme-light .modal-orbit {
  border-color: rgba(10,166,166,.16);
  box-shadow: inset 0 0 42px rgba(10,166,166,.08);
}

.theme-light .modal-head {
  border-bottom-color: rgba(128, 164, 184, .32);
}

.theme-light .modal-kicker {
  color: #067a8c;
}

.theme-light .modal-head h3 {
  color: #132c38;
}

.theme-light .modal-head p,
.theme-light .modal-body {
  color: #4f6572;
}

.theme-light :deep(.modal-tabs .n-tabs-tab),
.theme-light :deep(.modal-form-grid label),
.theme-light :deep(.modal-hint) {
  color: #5b7280;
}

.theme-light :deep(.modal-tabs .n-tabs-tab.n-tabs-tab--active) {
  color: #067a8c;
}

.theme-light :deep(.modal-form-grid input),
.theme-light :deep(.modal-form-grid select) {
  color: #132c38;
  background: rgba(255,255,255,.86);
  border-color: rgba(128, 164, 184, .5);
}

.theme-light :deep(.modal-form-grid .n-input),
.theme-light :deep(.modal-form-grid .n-base-selection),
.theme-light :deep(.modal-form-grid .n-input-number) {
  --n-border: 1px solid rgba(128, 164, 184, .5) !important;
  --n-border-hover: 1px solid rgba(10,166,166,.46) !important;
  --n-border-focus: 1px solid rgba(10,166,166,.7) !important;
  --n-color: rgba(255,255,255,.86) !important;
  --n-color-focus: rgba(255,255,255,.98) !important;
  --n-text-color: #132c38 !important;
  --n-placeholder-color: #8aa0ad !important;
}

.theme-light .modal-button {
  color: #132c38;
  background: rgba(255,255,255,.7);
  border-color: rgba(128, 164, 184, .44);
}

.theme-light .modal-button.primary {
  color: #ffffff;
  background: linear-gradient(90deg, #0aa6a6, #2563eb);
}

.theme-light .modal-button.secondary:hover {
  color: #073f52;
  border-color: rgba(10,166,166,.46);
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity .18s ease;
}

.modal-fade-enter-active .app-modal,
.modal-fade-leave-active .app-modal {
  transition: transform .2s ease, opacity .2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-from .app-modal,
.modal-fade-leave-to .app-modal {
  opacity: 0;
  transform: translateY(12px) scale(.98);
}
</style>
