import { ref, watch } from 'vue'

const storageKey = 'legadoTranslateChineseVietnamese'
const initialValue = localStorage.getItem(storageKey)

// Per-page preference; Android's global switch does not control API requests.
export const translationEnabled = ref(initialValue == null ? true : initialValue === 'true')
export const translationEpoch = ref(0)
watch(translationEnabled, value => {
  localStorage.setItem(storageKey, String(value))
  translationEpoch.value += 1
}, { flush: 'sync' })
export const translationQuery = () => String(translationEnabled.value)
