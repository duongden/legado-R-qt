import { computed, ref, watch, watchEffect } from 'vue'

export const themeOptions = [
  {
    id: 'system',
    label: 'Theo hệ thống',
    color: 'linear-gradient(135deg, #fafafa 50%, #20262d 50%)',
  },
  { id: 'light', label: 'Sáng', color: '#ffffff' },
  { id: 'dark', label: 'Tối', color: '#20262d' },
  { id: 'oled', label: 'Đen OLED', color: '#000000' },
  { id: 'paper', label: 'Giấy ngà', color: '#f6eddb' },
  { id: 'gray', label: 'Xám dịu', color: '#dfe3e5' },
] as const
export type ThemeMode = (typeof themeOptions)[number]['id']
const key = 'legado_ui_theme_v1'
const valid = (value: unknown): value is ThemeMode =>
  themeOptions.some(option => option.id === value)
let saved: string | null = null
try {
  saved = localStorage.getItem(key)
} catch {
  /* Private mode still supports in-memory selection. */
}
let chosen = valid(saved)
export const themeMode = ref<ThemeMode>(valid(saved) ? saved : 'system')
const media = window.matchMedia('(prefers-color-scheme: dark)')
const systemDark = ref(media.matches)
media.addEventListener('change', event => {
  systemDark.value = event.matches
})
export const resolvedTheme = computed(() =>
  themeMode.value === 'system'
    ? systemDark.value
      ? 'dark'
      : 'light'
    : themeMode.value,
)
export const appearanceIsDark = computed(() =>
  ['dark', 'oled'].includes(resolvedTheme.value),
)
const persist = () => {
  try {
    localStorage.setItem(key, themeMode.value)
  } catch {
    /* Keep working without storage. */
  }
}
export const setThemeMode = (mode: ThemeMode) => {
  if (!valid(mode)) return
  chosen = true
  themeMode.value = mode
  persist()
}
/** Preserve server-side reader settings; import the old palette only once. */
export const migrateLegacyTheme = (legacy: unknown) => {
  if (
    chosen ||
    !Number.isInteger(legacy) ||
    (legacy as number) < 0 ||
    (legacy as number) > 6
  )
    return
  const mapping: ThemeMode[] = [
    'paper',
    'paper',
    'light',
    'light',
    'light',
    'gray',
    'dark',
  ]
  setThemeMode(mapping[legacy as number])
}
watch(
  resolvedTheme,
  mode => {
    document.documentElement.style.removeProperty('background-color')
    document.documentElement.dataset.theme = mode
    document.documentElement.classList.toggle('dark', appearanceIsDark.value)
    document.documentElement.style.colorScheme = appearanceIsDark.value
      ? 'dark'
      : 'light'
  },
  { immediate: true, flush: 'sync' },
)

export const paletteFields = [
  { key: 'page', label: 'Nền trang' },
  { key: 'surface', label: 'Nền bảng và ô nhập' },
  { key: 'reader', label: 'Nền trang đọc' },
  { key: 'ink', label: 'Chữ chính' },
  { key: 'muted', label: 'Chữ phụ' },
  { key: 'accent', label: 'Màu nhấn' },
] as const
export type PaletteKey = (typeof paletteFields)[number]['key']
export type Palette = Record<PaletteKey, string>
export type DayNight = 'light' | 'dark'
export const defaultPalettes: Record<DayNight, Palette> = {
  light: {
    page: '#f5f7f8',
    surface: '#ffffff',
    reader: '#ffffff',
    ink: '#1b292d',
    muted: '#52656c',
    accent: '#176456',
  },
  dark: {
    page: '#13191e',
    surface: '#1c252c',
    reader: '#182127',
    ink: '#e3eaf0',
    muted: '#a5b6c1',
    accent: '#86d9be',
  },
}
const paletteStorageKey = 'legado_ui_palettes_v1'
const readPalettes = (): Partial<Record<DayNight, Palette>> => {
  try {
    const value = JSON.parse(localStorage.getItem(paletteStorageKey) || '{}')
    const result: Partial<Record<DayNight, Palette>> = {}
    for (const mode of ['light', 'dark'] as const) {
      if (
        paletteFields.every(field =>
          /^#[0-9a-f]{6}$/i.test(value?.[mode]?.[field.key]),
        )
      )
        result[mode] = value[mode]
    }
    return result
  } catch {
    return {}
  }
}
export const customPalettes = ref(readPalettes())
export const savePalette = (mode: DayNight, palette: Palette) => {
  if (!paletteFields.every(field => /^#[0-9a-f]{6}$/i.test(palette[field.key])))
    return
  customPalettes.value = { ...customPalettes.value, [mode]: { ...palette } }
  try {
    localStorage.setItem(
      paletteStorageKey,
      JSON.stringify(customPalettes.value),
    )
  } catch {
    /* In-memory theme remains usable. */
  }
  setThemeMode(mode)
}
export const resetPalette = (mode: DayNight) => {
  delete customPalettes.value[mode]
  try {
    localStorage.setItem(
      paletteStorageKey,
      JSON.stringify(customPalettes.value),
    )
  } catch {
    /* In-memory fallback. */
  }
}
watchEffect(() => {
  const mode = resolvedTheme.value
  const palette =
    mode === 'light' || mode === 'dark' ? customPalettes.value[mode] : undefined
  const style = document.documentElement.style
  for (const field of paletteFields) {
    if (palette) style.setProperty('--' + field.key, palette[field.key])
    else style.removeProperty('--' + field.key)
  }

})

window.addEventListener('storage', event => {
  if (event.key === key) {
    const value = event.newValue
    themeMode.value = valid(value) ? value : 'system'
    chosen = valid(value)
  }
  if (event.key === paletteStorageKey) customPalettes.value = readPalettes()
})
