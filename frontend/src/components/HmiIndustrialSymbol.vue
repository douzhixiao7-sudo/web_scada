<script setup lang="ts">
defineProps<{
  kind: 'pump' | 'gate' | 'motor' | 'plc' | 'gauge'
  stroke: string
  fill: string
  strokeWidth: number
  active?: boolean
}>()
</script>

<template>
  <svg class="industrial-symbol" viewBox="0 0 100 72" aria-hidden="true">
    <g :stroke="stroke" :stroke-width="strokeWidth" :fill="fill" stroke-linecap="round" stroke-linejoin="round">
      <template v-if="kind === 'pump'">
        <circle cx="42" cy="38" r="22" />
        <path d="M42 16v44M20 38H7M64 38h25v-15" fill="none" />
        <path d="M33 28c16 0 22 8 22 19-12 1-22-5-22-19z" :fill="active ? stroke : fill" />
        <path d="M30 64h25" fill="none" />
      </template>
      <template v-else-if="kind === 'gate'">
        <path d="M6 36h25M69 36h25" fill="none" />
        <path d="M31 22l19 14-19 14zM69 22L50 36l19 14z" :fill="active ? stroke : fill" />
        <path d="M50 36V10M39 10h22" fill="none" />
      </template>
      <template v-else-if="kind === 'motor'">
        <circle cx="49" cy="36" r="25" />
        <path d="M28 58h42M74 36h18M35 47V25l14 14 14-14v22" fill="none" />
        <rect x="40" y="5" width="18" height="8" :fill="active ? stroke : fill" />
      </template>
      <template v-else-if="kind === 'plc'">
        <rect x="13" y="10" width="74" height="52" rx="2" />
        <path d="M28 10v52M73 10v52M35 22h30M35 31h30M35 40h30M35 49h30" fill="none" />
        <circle v-for="y in [20,30,40,50]" :key="y" cx="20" :cy="y" r="2" :fill="active ? stroke : fill" />
        <circle v-for="y in [20,30,40,50]" :key="`r-${y}`" cx="80" :cy="y" r="2" />
      </template>
      <template v-else>
        <circle cx="50" cy="36" r="28" />
        <path d="M29 47a24 24 0 0 1 42 0M50 36l14-12M23 59h54" fill="none" />
        <circle cx="50" cy="36" r="3" :fill="active ? stroke : fill" />
      </template>
    </g>
  </svg>
</template>

<style scoped>
.industrial-symbol { width:100%; height:100%; overflow:visible; }
</style>
