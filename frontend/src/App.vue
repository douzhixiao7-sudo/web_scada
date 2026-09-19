<script setup lang="ts">
import { onMounted, ref } from 'vue'

const status = ref('尚未检查')
const checking = ref(false)

async function checkBackend() {
  checking.value = true
  status.value = '正在检查后端连接…'
  try {
    const response = await fetch('/api/actuator/health', {
      signal: AbortSignal.timeout(5000),
      cache: 'no-store',
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const result = await response.json()
    status.value = result.status === 'UP' ? '后端已连接 · UP' : '后端健康检查未通过'
  } catch {
    status.value = '后端未连接，请检查后端进程及日志'
  } finally {
    checking.value = false
  }
}

onMounted(checkBackend)
</script>

<template>
  <main>
    <p class="eyebrow">WEB SCADA / BOOTSTRAP</p>
    <h1>工程启动检查</h1>
    <p>当前为项目基础框架，仅验证前后端启动和连接。</p>
    <dl>
      <dt>前端</dt><dd>Vue 3 + TypeScript + Vite · 已启动</dd>
      <dt>后端</dt><dd role="status" aria-live="polite">{{ status }}</dd>
      <dt>运行范围</dt><dd>本机开发环境 · MySQL / Redis 基础连接 · 未连接 PLC</dd>
    </dl>
    <button :disabled="checking" @click="checkBackend">{{ checking ? '检查中…' : '重新检查连接' }}</button>
  </main>
</template>
