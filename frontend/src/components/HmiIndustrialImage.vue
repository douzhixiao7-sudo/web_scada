<script setup lang="ts">
import { computed } from 'vue'

type IndustrialKind = 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'
const props = defineProps<{ kind: IndustrialKind; active?: boolean }>()
const labels: Record<IndustrialKind, string> = { pump: '泵', gate: '闸门', motor: '电机', plc: 'PLC', gauge: '仪表' }
const source = computed(() => `/assets/hmi/industrial/${props.kind}.png`)
</script>

<template>
  <span class="industrial-image" :class="{ active }">
    <img :src="source" :alt="labels[kind]" draggable="false" />
  </span>
</template>

<style scoped>
.industrial-image { display:flex; align-items:center; justify-content:center; width:100%; min-height:0; flex:1; overflow:hidden; }
.industrial-image img { display:block; width:100%; height:100%; object-fit:contain; transform:scale(1.08); filter:saturate(.82) brightness(.88); transition:filter 120ms ease,opacity 120ms ease; opacity:.88; pointer-events:none; user-select:none; }
.industrial-image.active img { filter:saturate(1.08) brightness(1.04); opacity:1; }
</style>
