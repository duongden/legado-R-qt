<template>
  <div class="theme-picker" role="group" aria-label="Giao diện">
    <button v-for="option in themeOptions" :key="option.id" type="button"
      :aria-pressed="themeMode === option.id" :aria-label="option.label"
      :class="{ active: themeMode === option.id }" @click="setThemeMode(option.id)">
      <span class="theme-swatch" :style="{ background: option.color }" aria-hidden="true"></span>
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>
<script setup lang="ts">
import { themeOptions, themeMode, setThemeMode } from '@/config/appearance'
</script>
<style scoped>
.theme-picker { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }
button { display:flex; flex-direction:column; align-items:center; gap:8px; min-width:0; padding:12px 4px; border:1px solid var(--line); border-radius:12px; background:var(--surface); color:var(--muted); font:inherit; font-size:12px; cursor:pointer; }
button:hover { background:var(--hover); }
button.active { border-color:var(--accent); color:var(--accent); background:var(--accent-soft); }
.theme-swatch { width:25px; height:25px; border:1px solid var(--line); border-radius:50%; }
</style>
