<template>
  <div class="theme-picker" role="group" aria-label="Giao diện">
    <button
      v-for="option in themeOptions"
      :key="option.id"
      type="button"
      :aria-pressed="themeMode === option.id"
      :aria-label="option.label"
      :class="{ active: themeMode === option.id }"
      @click="setThemeMode(option.id)"
    >
      <span
        class="theme-swatch"
        :style="{ background: option.color }"
        aria-hidden="true"
      ></span>
      <span>{{ option.label }}</span>
    </button>
  </div>
  <details class="palette-settings">
    <summary>Tùy chỉnh màu Sáng / Tối</summary>
    <label
      >Chế độ
      <select v-model="editing">
        <option value="light">Sáng</option>
        <option value="dark">Tối</option>
      </select></label
    >
    <label v-for="field in paletteFields" :key="field.key"
      >{{ field.label
      }}<input
        type="color"
        :aria-label="field.label"
        v-model="draft[field.key]"
    /></label>
    <p v-if="lowContrast" role="status">
      Chữ hoặc màu nhấn chưa đủ tương phản với nền. Hãy chỉnh màu trước khi áp dụng.
    </p>
    <div class="palette-actions">
      <button type="button" :disabled="lowContrast" @click="savePalette(editing, draft)">Áp dụng</button
      ><button type="button" @click="restore">Khôi phục mặc định</button>
    </div>
    <small
      >Lưu trên trình duyệt này. Theo hệ thống sẽ dùng màu Sáng / Tối tương
      ứng.</small
    >
  </details>
</template>
<script setup lang="ts">
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
.theme-picker {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 12px 4px;
  border: var(--border-ui);
  border-radius:var(--radius-control);
  background: var(--surface);
  color: var(--muted);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
button:disabled { opacity:.55; cursor:not-allowed; }
button:hover {
  background: var(--hover);
}
button.active {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}
.theme-swatch {
  width: 25px;
  height: 25px;
  border: var(--border-ui);
  border-radius: 50%;
}
.palette-settings {
  margin-top: 14px;
  color: var(--ink);
  font-size: 13px;
}
summary {
  cursor: pointer;
  padding: 10px 0;
}
label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 10px 0;
}
select,
input {
  color: var(--ink);
  background: var(--surface);
  border: var(--border-ui);
  border-radius:var(--radius-control);
  min-height: 32px;
}
.palette-actions {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
small {
  display: block;
  line-height: 1.5;
  color: var(--muted);
}
p {
  line-height: 1.5;
}
</style>
