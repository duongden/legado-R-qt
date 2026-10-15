<template>
  <div ref="root" class="compact-select" @keydown.esc.prevent="opened = false" @focusout="event => { if (!root?.contains(event.relatedTarget as Node)) opened = false }">
    <button ref="trigger" type="button" class="select-trigger" :disabled="disabled || !options.length"
      :aria-label="label" aria-haspopup="listbox" :aria-expanded="opened" @click="opened = !opened"
      @keydown.down.prevent="openAndFocus"><span>{{ options.find(o => o.value === modelValue)?.label || placeholder || label }}</span><span aria-hidden="true">⌄</span></button>
    <div v-if="opened" class="select-popup" :class="{ upward }" :style="{ maxHeight: `${popupHeight}px` }">
      <input v-if="searchable" v-model="query" type="search" :aria-label="t('Lọc nguồn')" :placeholder="t('Tìm kiếm')" />
      <div role="listbox" :aria-label="label" @keydown="navigate">
        <button v-for="option in filtered" :key="option.value" type="button" role="option"
          :aria-selected="option.value === modelValue" @click="select(option.value)">{{ option.label }}</button>
        <p v-if="!filtered.length">{{ t('Không tìm thấy') }}</p>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
const props = defineProps<{ modelValue: string; options: { value: string; label: string }[]; label: string; placeholder?: string; disabled?: boolean; searchable?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; change: [value: string] }>()
const upward = ref(false), popupHeight = ref(280)
const opened = ref(false), query = ref(''), root = ref<HTMLElement>(), trigger = ref<HTMLButtonElement>()
const filtered = computed(() => props.options.filter(o => o.label.toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
function select(value: string) { emit('update:modelValue', value); emit('change', value); opened.value = false; trigger.value?.focus() }
async function openAndFocus() { opened.value = true; await nextTick(); root.value?.querySelector<HTMLButtonElement>('[role=option]')?.focus() }
function navigate(event: KeyboardEvent) {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const items = [...(root.value?.querySelectorAll<HTMLButtonElement>('[role=option]') || [])]
  const current = items.indexOf(document.activeElement as HTMLButtonElement)
  const index = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
  items[index]?.focus()
}
function outside(event: Event) { if (!root.value?.contains(event.target as Node)) opened.value = false }
watch(opened, value => {
  if (!value) return
  query.value = ''
  const rect = trigger.value?.getBoundingClientRect()
  if (rect) { const below = window.innerHeight - rect.bottom - 12, above = rect.top - 12; upward.value = below < 220 && above > below; popupHeight.value = Math.min(320, Math.max(60, upward.value ? above : below)) }
})
watch(() => props.disabled, () => { opened.value = false })
onMounted(() => document.addEventListener('pointerdown', outside))
onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<style scoped>
.compact-select { position:relative; min-width:0; width:100%; color:var(--ink); font-size:14px; }
.select-trigger { min-height:42px; padding:9px 12px; border:var(--border-ui); border-radius:var(--radius-control); background:var(--page); color:var(--ink); font:inherit; cursor:pointer; display:flex; align-items:center; justify-content:space-between; gap:10px; width:100%; text-align:left; min-width:0; }
.select-trigger span:first-child { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.select-popup { position:absolute; z-index:40; top:calc(100% + 6px); left:0; right:0; max-height:min(320px,50dvh); overflow:auto; padding:6px; border:var(--border-ui); border-radius:var(--radius-control); background:var(--surface); box-shadow:0 8px 24px #0002; }
.select-popup.upward { top:auto; bottom:calc(100% + 6px); }
.select-popup input { width:100%; min-width:0; min-height:36px; padding:8px 10px; margin-bottom:6px; border:var(--border-ui); border-radius:6px; background:var(--page); color:var(--ink); font:inherit; }
.select-popup button { padding:8px 10px; color:var(--ink); background:var(--surface); font:inherit; cursor:pointer; display:block; width:100%; min-height:36px; text-align:left; border:0; border-radius:6px; white-space:normal; overflow-wrap:anywhere; line-height:1.5; }
.select-popup button[aria-selected=true] { background:var(--accent-soft); color:var(--accent); }
.select-popup button:hover { background:var(--hover); }
.select-popup button:focus-visible { outline:2px solid var(--accent); outline-offset:-2px; }
.select-popup p { margin:8px; }
:global([data-theme=eink]) .select-popup { box-shadow:none; }
</style>
