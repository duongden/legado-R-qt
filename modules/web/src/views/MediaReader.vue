<template>
  <main class="media-page">
    <router-link to="/">{{ t('← Tủ sách') }}</router-link>
    <div class="page-heading"><div><h1>{{ name }}</h1><p class="muted" role="status">{{ t(status === 'saved' ? 'Đã đồng bộ' : status === 'offline' ? 'Chưa đồng bộ · Thử lại' : 'Đang đồng bộ…') }}</p></div></div>
    <router-link v-if="content?.type === 64" class="media-transfer" :to="{name: 'downloads', query: {bookUrl}}">{{ t('Tải / Xuất') }}</router-link>
    <label class="chapter-choice">{{ t('Mục lục') }}<CompactSelect :model-value="String(index)" :options="chapters.filter(c => !c.isVolume).map(c => ({ value: String(c.index), label: `${c.title}${c.isVip ? ` · ${t('VIP')}` : ''}${c.isPay ? ` · ${t('Đã mua')}` : ''}` }))" :label="t('Mục lục')" searchable @change="value => chooseChapter(Number(value))" /></label>
    <p v-if="loading" class="empty-state" role="status">{{ t('Đang tải…') }}</p>
    <div v-else-if="error" class="empty-state" role="alert"><p>{{ error }}</p><button type="button" @click="load(index, position)">{{ t('Thử lại') }}</button></div>
    <section v-else-if="content" class="media-content" :aria-label="content.title">
      <template v-if="content.type === 64">
        <div class="media-navigation"><button type="button" :disabled="position <= 0" @click="choosePage(position - 1)">{{ t('Trang trước') }}</button><span>{{ position + 1 }} / {{ content.resources.length }}</span><button type="button" :disabled="position >= content.resources.length - 1" @click="choosePage(position + 1)">{{ t('Trang sau') }}</button></div>
        <img class="manga-page" :key="content.resources[position]" :src="resourceUrl(content.resources[position], true)" :alt="`${t('Trang')} ${position + 1}`" @error="error = t('Không tải được ảnh')" />
      </template>
      <template v-else>
        <video v-if="content.type === 4" ref="player" controls playsinline preload="metadata" @loadedmetadata="restorePosition" @timeupdate="updatePosition" @pause="save" @seeked="save" @error="playbackError" />
        <audio v-else ref="player" controls preload="metadata" @loadedmetadata="restorePosition" @timeupdate="updatePosition" @pause="save" @seeked="save" @error="playbackError" />
        <p class="muted">{{ t('Vị trí phát được lưu theo thời gian; manga lưu theo trang.') }}</p>
      </template>
    </section>
    <div class="media-navigation"><button type="button" :disabled="loading || !adjacent(-1)" @click="chooseChapter(adjacent(-1)!.index)">{{ t('Chương trước') }}</button><button type="button" :disabled="loading || !adjacent(1)" @click="chooseChapter(adjacent(1)!.index)">{{ t('Chương sau') }}</button></div>
  </main>
</template>
<script setup lang="ts">
import Hls from 'hls.js'
import API, { legado_http_entry_point, type LeagdoApiResponse } from '@/api/api'
import ajax from '@/api/axios'
import type { BookChapter } from '@/book'
import CompactSelect from '@/components/CompactSelect.vue'
import { ReadProgressSync, type ProgressSnapshot, type SyncStatus } from '@/store/readProgressSync'
import { t } from '@/config/uiLanguage'
import { translationEpoch } from '@/api/translation'
import { preloadChapters } from '@/api/bookTransfer'
import { getRelayBootstrap } from '@/api/relay'
const route = useRoute(), router = useRouter()
const bookUrl = String(route.query.bookUrl || sessionStorage.getItem('bookUrl') || '')
type MediaChapter = { type: number; title: string; resources: string[]; hls: boolean }
const chapters = ref<BookChapter[]>([]), content = ref<MediaChapter>(), player = ref<HTMLMediaElement>()
const name = ref(''), index = ref(-1), position = ref(0), status = ref<SyncStatus>('loading'), error = ref(''), loading = ref(true)
let alive = true, generation = 0, restoring = true, hls: Hls | undefined, attemptedHls = false
watch(name, value => { if (value) document.title = value })
let saveTimer: ReturnType<typeof setTimeout> | undefined, poll: ReturnType<typeof setInterval> | undefined
const resourceUrl = (id: string, image = false) => new URL(`${image ? 'webMediaImage' : 'webMediaResource'}?id=${encodeURIComponent(id)}`, legado_http_entry_point).toString()
const sync = new ReadProgressSync({ read: () => API.getReadProgress(bookUrl), write: pos => API.saveReadProgress(bookUrl, pos) }, snapshot => { void applyRemote(snapshot) }, value => { status.value = value })
function adjacent(direction: number) { return chapters.value.filter(c => !c.isVolume && (direction > 0 ? c.index > index.value : c.index < index.value)).sort((a, b) => direction * (a.index - b.index))[0] }
async function applyRemote(snapshot: ProgressSnapshot) {
  // Progress acknowledgements carry source metadata; refresh the display separately.
  const progress = snapshot.progress
  const playable = chapters.value.find(c => c.index === progress.durChapterIndex && !c.isVolume)
    || chapters.value.find(c => !c.isVolume && c.index >= progress.durChapterIndex)
    || chapters.value.find(c => !c.isVolume)
  if (loading.value && playable?.index === index.value) { position.value = progress.durChapterPos; return }
  if (playable && playable.index !== progress.durChapterIndex) {
    sync.update({ durChapterIndex: playable.index, durChapterPos: 0 })
    await load(playable.index, 0)
    await sync.flush()
    return
  }
  if (progress.durChapterIndex !== index.value || !content.value) await load(progress.durChapterIndex, progress.durChapterPos)
  else {
    position.value = content.value.type === 64 ? Math.min(progress.durChapterPos, content.value.resources.length - 1) : progress.durChapterPos
    if (player.value && Math.abs(player.value.currentTime * 1000 - position.value) > 2000) {
      restoring = true; player.value.currentTime = position.value / 1000; restoring = false
    }
  }
}
async function load(target: number, pos: number) {
  const request = ++generation
  restoring = true; hls?.destroy(); hls = undefined; attemptedHls = false
  player.value?.pause(); loading.value = true; error.value = ''; content.value = undefined
  index.value = target; position.value = pos
  try {
    const { data } = await ajax.get<LeagdoApiResponse<MediaChapter>>('getWebMediaChapter', { params: { url: bookUrl, index: target }, timeout: 65000 })
    if (!alive || request !== generation) return
    if (!data.isSuccess) throw new Error(t(data.errorMsg))
    content.value = data.data
    if (content.value.type === 64) position.value = Math.min(position.value, content.value.resources.length - 1)
    loading.value = false
    await nextTick()
    if (!alive || request !== generation) return
    if (content.value.type === 64) void preloadChapters(bookUrl, target).catch(() => {})
    if (player.value) {
      if (content.value.hls && !player.value.canPlayType('application/vnd.apple.mpegurl')) attachHls()
      else player.value.src = resourceUrl(content.value.resources[0])
    } else restoring = false
  } catch (e) { if (alive && request === generation) { error.value = e instanceof Error ? e.message : t('Không tải được nội dung'); loading.value = false } }
}
function attachHls() {
  if (!player.value || !content.value || !Hls.isSupported()) { error.value = t('Trình duyệt không phát được định dạng này. Hãy mở trên điện thoại.'); return }
  attemptedHls = true
  hls = new Hls()
  hls.loadSource(resourceUrl(content.value.resources[0])); hls.attachMedia(player.value)
  hls.on(Hls.Events.ERROR, (_, event) => { if (event.fatal) error.value = t('Không phát được nội dung. Kiểm tra nguồn hoặc định dạng trên điện thoại.') })
}
function playbackError() { if (!attemptedHls) attachHls(); else error.value = t('Không phát được nội dung. Kiểm tra nguồn hoặc định dạng trên điện thoại.') }
function restorePosition() {
  if (!player.value) return
  const seconds = position.value / 1000
  player.value.currentTime = Math.max(0, Math.min(Number.isFinite(player.value.duration) ? player.value.duration : seconds, seconds))
  restoring = false
}
function updatePosition() {
  if (restoring || loading.value || !player.value) return
  position.value = Math.min(2147483647, Math.floor(player.value.currentTime * 1000))
  sync.update({ durChapterIndex: index.value, durChapterPos: position.value })
  if (!saveTimer) saveTimer = setTimeout(() => { saveTimer = undefined; void sync.flush() }, 2000)
}
function save() { if (!restoring) { updatePosition(); void sync.flush(true) } }
async function chooseChapter(target: number) {
  if (target === index.value || loading.value) return
  player.value?.pause()
  sync.update({ durChapterIndex: target, durChapterPos: 0 })
  void load(target, 0)
  await sync.flush()
}
function choosePage(page: number) { position.value = page; sync.update({ durChapterIndex: index.value, durChapterPos: page }); void sync.flush() }
function resume() { if (!document.hidden) void sync.refresh().catch(() => {}) }
let metadataGeneration = 0
async function refreshMetadata() {
  const request = ++metadataGeneration
  const [snapshot, toc] = await Promise.all([API.getReadProgress(bookUrl), API.getChapterList(bookUrl)])
  if (!alive || request !== metadataGeneration) return
  name.value = snapshot.progress.name
  if (toc.data.isSuccess) chapters.value = toc.data.data
}
watch(translationEpoch, () => { void refreshMetadata().catch(() => {}) })
onMounted(async () => {
  if (getRelayBootstrap()) { await router.replace('/'); return }
  try {
    await refreshMetadata()
    await sync.refresh()
    poll = setInterval(resume, 5000)
    window.addEventListener('focus', resume); window.addEventListener('online', resume)
    window.addEventListener('pagehide', save); document.addEventListener('visibilitychange', visibility)
  } catch (e) { error.value = e instanceof Error ? e.message : t('Không tải được nội dung'); loading.value = false }
})
function visibility() { if (document.hidden) save(); else resume() }
onBeforeUnmount(() => {
  save(); alive = false; ++generation; hls?.destroy(); clearTimeout(saveTimer); clearInterval(poll)
  window.removeEventListener('focus', resume); window.removeEventListener('online', resume); window.removeEventListener('pagehide', save); document.removeEventListener('visibilitychange', visibility)
  // Keep the final acknowledged write alive after navigation.
  void sync.flush(true).finally(() => sync.stop())
})
</script>
<style scoped>
.media-page { max-width:1200px; margin:auto; padding:32px clamp(16px,4vw,48px) 56px; }
h1 { font-size:clamp(22px,4vw,30px); line-height:1.5; overflow-wrap:anywhere; }
.chapter-choice { display:flex; flex-direction:column; gap:8px; font-size:12px; color:var(--muted); margin:20px 0; }
.media-content { margin:20px 0; }
video { display:block; width:100%; max-height:70dvh; background:#000; border-radius:var(--radius-control); }
audio { width:100%; }
.manga-page { display:block; max-width:100%; height:auto; margin:12px auto; }
.media-navigation { display:flex; align-items:center; justify-content:center; gap:12px; margin:20px 0; font-size:13px; }
.muted { color:var(--muted); font-size:12px; }
button { padding:9px 12px; border:var(--border-ui); border-radius:var(--radius-control); background:var(--surface); color:var(--ink); font:inherit; }
button:disabled { opacity:.5; }
</style>

<style scoped>
.media-transfer { display:inline-flex; padding:9px 12px; border:var(--border-ui); border-radius:var(--radius-control); background:var(--surface); text-decoration:none; font-size:13px; }
</style>
