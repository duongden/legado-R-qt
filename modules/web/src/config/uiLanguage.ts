import { ref, watch } from 'vue'
import catalog from './ui-locales.json'
export type UiLanguage = 'vi' | 'zh' | 'en'
const key = 'legado_ui_language_v1'
const saved = localStorage.getItem(key)
export const uiLanguage = ref<UiLanguage>(saved === 'zh' || saved === 'en' ? saved : 'vi')
export const t = (text: string = '', values: Record<string, string | number> = {}) => {
  const translated = (catalog[uiLanguage.value] as Record<string, string>)[text] || text
  return translated.replace(/\{(\w+)\}/g, (match, key: string) => String(values[key] ?? match))
}
export const setUiLanguage = (language: UiLanguage) => { uiLanguage.value = language }
window.addEventListener('storage', event => {
  if (event.key === key && ['vi', 'zh', 'en'].includes(event.newValue || '')) {
    uiLanguage.value = event.newValue as UiLanguage
  }
})
watch(uiLanguage, value => {
  document.documentElement.lang = value === 'zh' ? 'zh-CN' : value
  localStorage.setItem(key, value)
  window.dispatchEvent(new CustomEvent('legado-language-change', { detail: value }))
}, { immediate: true, flush: 'sync' })
