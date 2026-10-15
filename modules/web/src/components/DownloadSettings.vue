<template>
  <form class="download-settings" @submit.prevent="save">
    <label>{{ t('Số chương tải trước') }}<input v-model.number="count" type="number" min="0" max="100" required /></label>
    <p>{{ t('0 để tắt. Số chương dùng chung với cài đặt trên điện thoại.') }}</p>
    <label>{{ t('Khoảng nghỉ giữa các chương (ms)') }}<input v-model.number="interval" type="number" min="0" max="60000" step="100" required /></label>
    <p>{{ t('Áp dụng cho tải chương qua Web Service. Giới hạn riêng của nguồn vẫn được giữ.') }}</p>
    <button type="submit" :disabled="saving || !loaded">{{ saving ? t('Đang lưu…') : t('Lưu cài đặt tải') }}</button>
    <button v-if="!loaded" type="button" @click="load">{{ t('Thử lại') }}</button>
    <p v-if="message" role="status">{{ message }}</p>
  </form>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import { downloadSettings, loadDownloadSettings, saveDownloadSettings } from '@/api/bookTransfer'
const count = ref(downloadSettings.value.preDownloadNum), interval = ref(downloadSettings.value.chapterIntervalMs)
const saving = ref(false), loaded = ref(false), message = ref('')
watch(downloadSettings, settings => { count.value = settings.preDownloadNum; interval.value = settings.chapterIntervalMs })
const errorMessage = (error: unknown) => error instanceof Error && error.name !== 'AxiosError' ? error.message : t('Không tải được cài đặt. Hãy thử lại.')
async function load() { try { await loadDownloadSettings(); loaded.value = true; message.value = '' } catch (e) { message.value = errorMessage(e) } }
onMounted(load)
async function save() {
  saving.value = true; message.value = ''
  try { await saveDownloadSettings({ preDownloadNum: count.value, chapterIntervalMs: interval.value }); message.value = t('Đã lưu cài đặt') }
  catch (e) { message.value = errorMessage(e) } finally { saving.value = false }
}
</script>
<style scoped>
.download-settings { font:13px/1.6 var(--font-ui); }
label { display:flex; align-items:center; justify-content:space-between; gap:12px; margin:14px 0 6px; }
input { width:90px; flex-shrink:0; padding:8px; border:var(--border-ui); border-radius:8px; background:var(--surface); color:var(--ink); font:inherit; }
p { font-size:12px; color:var(--muted); overflow-wrap:anywhere; margin:6px 0 14px; }
button { padding:8px 12px; background:var(--surface); color:var(--ink); border:var(--border-ui); border-radius:8px; font:inherit; cursor:pointer; }
button:disabled { opacity:.5; }
</style>
