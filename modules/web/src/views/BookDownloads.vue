<template>
  <main class="library-page downloads-page">
    <div class="page-heading"><div><h1>{{ t('Tải và xuất sách') }}</h1><p class="muted">{{ t('Tải chương vào điện thoại, xuất TXT/EPUB và tải tệp về thiết bị đang dùng.') }}</p></div><button type="button" :disabled="loading" @click="refresh()">{{ t('Làm mới') }}</button></div>
    <label class="book-selector">{{ t('Chọn sách') }}<select v-model="bookUrl" :disabled="booksLoading"><option value="">{{ t('Chọn sách trong tủ') }}</option><option v-for="book in books" :key="book.bookUrl" :value="book.bookUrl">{{ book.name }} · {{ book.author }}</option></select></label>
    <p v-if="error" class="transfer-error" role="alert">{{ error }}</p>
    <section v-if="bookUrl && summary" class="transfer-panel">
      <h2>{{ t('Bộ nhớ đệm trên điện thoại') }}</h2><p>{{ t('Đã có {cached}/{total} chương', summary) }}</p>
      <form @submit.prevent="start()"><div class="chapter-range"><label>{{ t('Từ chương') }}<input v-model.number="from" type="number" min="1" :max="summary.total" required /></label><label>{{ t('Đến chương') }}<input v-model.number="to" type="number" :min="from" :max="summary.total" required /></label></div>
        <div class="transfer-actions"><button type="submit" :disabled="busy || !summary.total">{{ t('Tải khoảng chương') }}</button><button type="button" :disabled="busy || !summary.total" @click="startAll">{{ t('Tải toàn bộ') }}</button></div></form>
      <h2>{{ t('Xuất sách') }}</h2><p v-if="isManga">{{ t('Manga được tải kèm ảnh và xuất EPUB để đọc ngoại tuyến.') }}</p><p>{{ t('Chương còn thiếu được tải trước khi xuất. Chương tải lỗi sẽ chặn xuất để tránh tệp thiếu nội dung.') }}</p><p class="muted">{{ t('Xuất bằng cài đặt của điện thoại. Tệp nằm trên điện thoại tối đa một giờ; bấm tải tệp để lưu về browser.') }}</p>
      <div class="transfer-actions"><button v-if="!isManga" type="button" :disabled="busy || !summary.total" @click="start('txt')">{{ t('Xuất TXT') }}</button><button type="button" :disabled="busy || !summary.total" @click="start('epub')">{{ t('Xuất EPUB') }}</button></div>
    </section>
    <section class="transfer-panel"><h2>{{ t('Tác vụ của sách') }}</h2><p v-if="!jobs.length" class="muted">{{ t('Chưa có tác vụ.') }}</p>
      <article v-for="job in jobs" :key="job.id" class="transfer-job"><div><strong>{{ job.kind === 'preload' ? t('Tải trước') : job.kind === 'cache' ? t('Tải chương') : t('Xuất sách') + ' · ' + job.kind.toUpperCase() }}</strong><p>{{ stateText(job.state) }} · {{ job.completed }}/{{ job.total }} <span v-if="job.failed">· {{ t('Lỗi') }}: {{ job.failed }}</span></p><p v-if="job.message" role="status">{{ t(job.message) }}</p></div>
        <div class="transfer-actions"><button v-if="active(job)" type="button" :disabled="busy" @click="cancel(job.id)">{{ t('Hủy tác vụ') }}</button><button v-if="job.state === 'failed'" type="button" :disabled="busy" @click="retry(job)">{{ t('Thử lại') }}</button><a v-if="job.filename && job.state === 'done'" :href="transferFileUrl(job.id)" :download="job.filename">{{ t('Tải tệp') }} · {{ job.kind.toUpperCase() }}</a></div>
      </article>
    </section>
    <section class="transfer-panel"><h2>{{ t('Cài đặt tải') }}</h2><DownloadSettings /></section>
  </main>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import API from '@api'
import type { Book } from '@/book'
import { getRelayBootstrap } from '@/api/relay'
import { getBookCache, getBookTransfers, startBookTransfer, cancelTransfer, transferFileUrl, downloadSettings, type CacheSummary, type Transfer } from '@/api/bookTransfer'
import DownloadSettings from '@/components/DownloadSettings.vue'
const route = useRoute(), router = useRouter()
const books = ref<Book[]>([]), bookUrl = ref(typeof route.query.bookUrl === 'string' ? route.query.bookUrl : '')
const summary = ref<CacheSummary>(), jobs = ref<Transfer[]>([]), error = ref('')
const isManga = computed(() => !!((summary.value?.type || books.value.find(b => b.bookUrl === bookUrl.value)?.type || 0) & 64))
const from = ref(1), to = ref(1), loading = ref(false), busy = ref(false), booksLoading = ref(true)
let alive = true, revision = 0, refreshing = false
const active = (job: Transfer) => ['queued', 'downloading', 'exporting'].includes(job.state)
const stateText = (state: string) => t(({ queued: 'Đang chờ', downloading: 'Đang tải chương…', exporting: 'Đang xuất tệp…', done: 'Hoàn tất', failed: 'Thất bại', cancelled: 'Đã hủy' } as Record<string, string>)[state] || state)
const message = (e: unknown) => e instanceof Error && e.name !== 'AxiosError' ? e.message : t('Không kết nối được với điện thoại. Hãy thử lại.')
async function refresh(reset = false) {
  const url = bookUrl.value, request = revision
  if (!url) return
  loading.value = true
  try {
    const [cache, list] = await Promise.all([getBookCache(url), getBookTransfers(url)])
    if (!alive || request !== revision) return
    summary.value = cache; jobs.value = list.reverse(); error.value = ''
    if (reset) { from.value = Math.min(cache.total, cache.current + 1) || 1; to.value = Math.min(cache.total, from.value + Math.max(1, downloadSettings.value.preDownloadNum) - 1) || 1 }
  } catch (e) { if (alive && request === revision) error.value = message(e) }
  finally { if (alive && request === revision) loading.value = false }
}
watch(bookUrl, () => { revision++; summary.value = undefined; jobs.value = []; error.value = ''; void router.replace({ query: { bookUrl: bookUrl.value } }); void refresh(true) })
watch(() => route.query.bookUrl, url => { if (typeof url === 'string' && url !== bookUrl.value) bookUrl.value = url })
async function run(action: () => Promise<unknown>) { busy.value = true; error.value = ''; try { await action(); await refresh() } catch (e) { error.value = message(e) } finally { busy.value = false } }
const start = (format?: 'txt' | 'epub') => run(() => startBookTransfer(bookUrl.value, from.value - 1, to.value - 1, format))
const startAll = () => run(() => startBookTransfer(bookUrl.value, 0, (summary.value?.total || 1) - 1))
const cancel = (id: string) => run(() => cancelTransfer(id))
const retry = (job: Transfer) => run(() => startBookTransfer(job.bookUrl, job.start, job.end, job.kind === 'txt' || job.kind === 'epub' ? job.kind : undefined))
const poll = setInterval(async () => {
  if (!alive || refreshing || document.visibilityState === 'hidden' || !jobs.value.some(active)) return
  refreshing = true; try { await refresh() } finally { refreshing = false }
}, 5000)
onMounted(async () => {
  if (getRelayBootstrap()) { void router.replace('/'); return }
  try { const response = await API.getBookShelf(); if (!response.data.isSuccess) throw Error(t('Tủ sách đang trống hoặc chưa kết nối được với điện thoại.')); books.value = response.data.data.filter(b => (b.type & (8 | 64)) !== 0); if (!bookUrl.value) bookUrl.value = books.value[0]?.bookUrl || ''; else await refresh(true) }
  catch (e) { error.value = message(e) } finally { booksLoading.value = false }
})
watchEffect(() => { document.title = t('Tải và xuất sách') })
// The service keeps jobs running when navigating away; re-open this page to see them.
onUnmounted(() => { alive = false; revision++; clearInterval(poll) })
</script>
<style scoped>
.downloads-page { max-width:1200px; }
h2 { font-size:18px; margin:0 0 12px; }
.transfer-panel { border:var(--border-ui); border-radius:12px; background:var(--surface); padding:20px; margin-top:24px; }
.transfer-panel p { font-size:13px; color:var(--muted); overflow-wrap:anywhere; }
.transfer-panel h2:not(:first-child) { margin-top:28px; }
.book-selector { display:grid; gap:8px; font-size:13px; }
select,input { padding:10px; border:var(--border-ui); border-radius:8px; background:var(--surface); color:var(--ink); font:inherit; max-width:100%; min-width:0; }
.book-selector select { width:100%; }
.chapter-range { display:flex; flex-wrap:wrap; gap:20px; margin:20px 0; }
.chapter-range label { display:grid; gap:8px; font-size:13px; }
.chapter-range input { width:130px; }
.transfer-actions { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.transfer-actions a { padding:8px 12px; border:var(--border-ui); border-radius:8px; text-decoration:none; }
.transfer-job { display:flex; justify-content:space-between; align-items:center; gap:16px; flex-wrap:wrap; padding:16px 0; border-bottom:var(--border-ui); }
.transfer-job:last-child { border-bottom:0; padding-bottom:0; }
.transfer-error { padding:14px; border:var(--border-ui); border-radius:8px; overflow-wrap:anywhere; }
.download-settings { max-width:520px; }
@media(max-width:520px) { .transfer-panel { padding:18px; } }
</style>
