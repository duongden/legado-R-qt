<template>
  <main class="discovery-page">
    <header class="page-breadcrumb"><nav :aria-label="t('Thư viện')"><router-link to="/discovery">{{ t("← Khám phá") }}</router-link><router-link to="/">{{ t("Tủ sách") }}</router-link></nav></header>
    <p v-if="loading" class="empty-state" role="status">{{ t("Đang tải thông tin truyện…") }}</p>
    <div v-else-if="error && !book" class="empty-state" role="alert"><p>{{ error }}</p><button type="button" @click="load">{{ t("Thử lại") }}</button>
      <p><router-link to="/discovery">{{ t("Tải lại danh sách Khám phá") }}</router-link></p></div>
    <article v-else-if="book" class="discovery-detail">
      <div class="detail-cover cover-placeholder"><img v-if="book.hasCover" :src="getExploreCoverUrl(book.id)" alt="" @error="hideCover" /><span v-else aria-hidden="true">▤</span></div>
      <div class="detail-text"><p class="eyebrow">{{ book.sourceName }}</p><h1>{{ book.name }}</h1>
        <p class="muted">{{ book.author || t('Chưa rõ tác giả') }}</p><p v-if="book.kind || book.wordCount">{{ [book.kind, book.wordCount].filter(Boolean).join(' · ') }}</p>
        <p v-if="book.latestChapterTitle" class="muted">{{ t("Mới nhất") }}: {{ book.latestChapterTitle }}</p>
        <p v-if="book.reader" class="shelf-badge">{{ t("Đã có trong tủ sách") }} · {{ t("Chương") }} {{ book.reader.durChapterIndex + 1 }}</p>
        <div class="detail-actions"><button v-if="!book.reader" type="button" :disabled="adding" @click="add(false)">{{ adding ? t('Đang thêm…') : t('Thêm vào kệ') }}</button>
          <button type="button" class="primary" :disabled="adding" @click="add(true)">{{ book.reader ? t('Đọc tiếp') : t('Thêm vào kệ và đọc') }}</button></div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p class="detail-intro">{{ book.intro || t('Nguồn chưa cung cấp giới thiệu.') }}</p>
      </div>
    </article>
  </main>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import { ref, onMounted, onUnmounted } from 'vue'
import { getRelayBootstrap } from '@/api/relay'
import { getExploreInfo, getExploreCoverUrl, addExploreBook, openExploreReader, type ExploreBook } from '@/api/discovery'
import '@/assets/discovery.css'
const route = useRoute(), router = useRouter()
const book = ref<ExploreBook>(), loading = ref(true), adding = ref(false), error = ref('')
let alive = true
const hideCover = (event: Event) => { (event.target as HTMLImageElement).style.visibility = 'hidden' }
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = await getExploreInfo(String(route.params.id))
    if (!alive) return
    book.value = result; document.title = result.name + ' · ' + t('Khám phá')
  } catch (e) { if (alive) error.value = e instanceof Error && e.name !== 'AxiosError' ? e.message : t('Không tải được truyện') }
  finally { if (alive) loading.value = false }
}
async function add(read: boolean) {
  if (!book.value || adding.value) return
  adding.value = true; error.value = ''
  try {
    const result = await addExploreBook(book.value.id)
    if (!alive) return
    book.value = { ...book.value, reader: result.reader }
    if (read) { openExploreReader(result.reader); await router.push(result.reader.type & (4 | 32 | 64) ? { path: '/media', query: { bookUrl: result.reader.bookUrl } } : '/chapter') }
    else ElMessage.success(result.added ? t('Đã thêm vào tủ sách trên điện thoại') : t('Truyện đã có trong tủ sách'))
  } catch (e) { if (alive) error.value = e instanceof Error && e.name !== 'AxiosError' ? e.message : t('Chưa thêm được truyện. Hãy thử lại.') }
  finally { if (alive) adding.value = false }
}
onMounted(() => { if (getRelayBootstrap()) { void router.replace('/'); return }; void load() })
onUnmounted(() => { alive = false })
</script>
