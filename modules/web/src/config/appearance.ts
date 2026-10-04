import { computed, ref, watch } from 'vue'

export const themeOptions = [
  { id: 'system', label: 'Theo hệ thống', color: 'linear-gradient(135deg, #fafafa 50%, #20262d 50%)' },
  { id: 'light', label: 'Sáng', color: '#ffffff' },
  { id: 'dark', label: 'Tối', color: '#20262d' },
  { id: 'oled', label: 'Đen OLED', color: '#000000' },
  { id: 'paper', label: 'Giấy ngà', color: '#f6eddb' },
  { id: 'gray', label: 'Xám dịu', color: '#dfe3e5' },
] as const
export type ThemeMode = typeof themeOptions[number]['id']
const key = 'legado_ui_theme_v1'
const valid = (value: unknown): value is ThemeMode => themeOptions.some(option => option.id === value)
let saved: string | null = null
try { saved = localStorage.getItem(key) } catch { /* Private mode still supports in-memory selection. */ }
let chosen = valid(saved)
export const themeMode = ref<ThemeMode>(valid(saved) ? saved : 'system')
const media = window.matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(media.matches)
media.addEventListener('change', event => { systemDark.value = event.matches })
export const resolvedTheme = computed(() => themeMode.value === 'system' ? (systemDark.value ? 'dark' : 'light') : themeMode.value)
export const appearanceIsDark = computed(() => ['dark', 'oled'].includes(resolvedTheme.value))
const persist = () => {
  try { localStorage.setItem(key, themeMode.value) } catch { /* Keep working without storage. */ }
}
export const setThemeMode = (mode: ThemeMode) => {
  if (!valid(mode)) return
  chosen = true
  themeMode.value = mode
  persist()
}
/** Preserve server-side reader settings; import the old palette only once. */
export const migrateLegacyTheme = (legacy: unknown) => {
  if (chosen || !Number.isInteger(legacy) || (legacy as number) < 0 || (legacy as number) > 6) return
  const mapping: ThemeMode[] = ['paper', 'paper', 'light', 'light', 'light', 'gray', 'dark']
  setThemeMode(mapping[legacy as number])
}
watch(resolvedTheme, mode => {
  document.documentElement.style.removeProperty('background-color')
  document.documentElement.dataset.theme = mode
  document.documentElement.classList.toggle('dark', appearanceIsDark.value)
  document.documentElement.style.colorScheme = appearanceIsDark.value ? 'dark' : 'light'
}, { immediate: true, flush: 'sync' })
