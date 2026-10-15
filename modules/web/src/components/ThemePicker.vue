<template>
  <div class="compact-appearance">
    <label class="setting-inline">{{ t('Giao diện') }}
      <CompactSelect :model-value="themeMode" :options="themeOptions.map(option => ({value:option.id,label:t(option.label)}))" :label="t('Giao diện')" @change="value => setThemeMode(value as typeof themeMode)" />
    </label>
    <details class="palette-settings">
      <summary>{{ t('Tùy chỉnh màu') }}</summary>
      <label class="setting-inline">{{ t('Chế độ') }}<select v-model="editing"><option value="light">{{ t('Sáng') }}</option><option value="dark">{{ t('Tối') }}</option></select></label>
      <div class="palette-grid"><label v-for="field in paletteFields" :key="field.key">{{ t(field.label) }}<input type="color" :aria-label="t(field.label)" v-model="draft[field.key]" /></label></div>
      <p v-if="lowContrast" role="status">{{ t('Chữ hoặc màu nhấn chưa đủ tương phản với nền. Hãy chỉnh màu trước khi áp dụng.') }}</p>
      <div class="palette-actions"><button type="button" :disabled="lowContrast" @click="savePalette(editing, draft)">{{ t('Áp dụng') }}</button><button type="button" @click="restore">{{ t('Khôi phục mặc định') }}</button></div>
    </details>
  </div>
</template>
<script setup lang="ts">
import CompactSelect from './CompactSelect.vue'
import { t } from '@/config/uiLanguage'
import {
  paletteFields,
  customPalettes,
  defaultPalettes,
  savePalette,
  resetPalette,
  type DayNight,
  themeOptions,
  themeMode,
  setThemeMode,
} from '@/config/appearance'
const editing = ref<DayNight>('light')
const draft = ref({ ...defaultPalettes.light })
watch(
  [editing, customPalettes],
  () => {
    draft.value = {
      ...(customPalettes.value[editing.value] ||
        defaultPalettes[editing.value]),
    }
  },
  { immediate: true, deep: true },
)
const restore = () => {
  resetPalette(editing.value)
  draft.value = { ...defaultPalettes[editing.value] }
}
const luminance = (hex: string) => {
  const c = [1, 3, 5]
    .map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map(v => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4))
  return c[0] * 0.2126 + c[1] * 0.7152 + c[2] * 0.0722
}
const lowContrast = computed(() =>
  ['ink', 'muted', 'accent'].some(key =>
    ['page', 'surface', 'reader'].some(bg => {
      const a = luminance(draft.value[key as 'ink']),
        b = luminance(draft.value[bg as 'page'])
      return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05) < 4.5
    }),
  ),
)
</script>
<style scoped>
.compact-appearance { width:100%; min-width:0; }
.palette-settings { margin:10px 0 16px; font-size:12px; color:var(--muted); }
summary { cursor:pointer; padding:7px 0; }
.palette-grid { display:grid; grid-template-columns:1fr 1fr; gap:8px 14px; margin:12px 0; }
.palette-grid label { display:flex; align-items:center; justify-content:space-between; gap:6px; font-size:11px; }
input[type=color] { width:32px; height:30px; padding:2px; border:var(--border-ui); border-radius:6px; background:var(--surface); }
.palette-actions { display:flex; flex-wrap:wrap; gap:6px; }
button { border:var(--border-ui); background:var(--surface); color:var(--ink); border-radius:8px; padding:7px 10px; font:inherit; cursor:pointer; }
button:disabled { opacity:.5; }
p { line-height:1.6; }
</style>
