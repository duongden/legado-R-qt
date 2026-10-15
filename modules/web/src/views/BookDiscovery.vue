<template>
  <main class="discovery-page">
    <div class="discovery-heading"><div><p class="eyebrow">{{ t("TÌM CÂU CHUYỆN TIẾP THEO") }}</p><h1>{{ t("Khám phá") }}</h1>
      <p class="muted">{{ t("Duyệt truyện từ các nguồn trên điện thoại và thêm vào tủ sách của bạn.") }}</p></div>
      <button type="button" :disabled="sourcesLoading" @click="loadSources">{{ t("Làm mới nguồn") }}</button></div>
    <section class="source-panel" :aria-label="t('Chọn nguồn truyện')">
      <label>{{ t('Loại nội dung') }}<CompactSelect v-model="sourceType" :options="[{value:'all',label:t('Tất cả')},{value:'0',label:t('Tiểu thuyết')},{value:'2',label:t('Manga')},{value:'1',label:t('Âm thanh')},{value:'4',label:t('Phim')}]" :label="t('Loại nội dung')" @change="filterType" /></label>
      <label>{{ t("Lọc nguồn") }}<input v-model="sourceFilter" type="search" :placeholder="t('Tên nguồn hoặc nhóm…')" /></label>
      <label>{{ t("Nguồn truyện") }}<CompactSelect v-model="sourceId" :options="filteredSources.map(s => ({ value: s.id, label: `${s.name}${s.group ? ` · ${s.group}` : ''}` }))" :label="t('Nguồn truyện')" :disabled="sourcesLoading" searchable @change="selectSource()" /></label>
      <form @submit.prevent="searchBooks"><label>{{ t("Tìm trong nguồn này") }}<input v-model="queryInput" type="search" maxlength="200"
        :disabled="!selectedSource?.canSearch" :placeholder="t('Tên truyện hoặc tác giả…')" /></label>
        <button type="submit" :disabled="!selectedSource?.canSearch || !queryInput.trim() || loading">{{ t("Tìm truyện") }}</button></form>
    </section>
    <p v-if="sourceError" class="error" role="alert">{{ sourceError }}</p>
    <div v-if="!sourcesLoading && !sources.length && !sourceError" class="empty-state">
      <h2>{{ t("Chưa có nguồn Khám phá") }}</h2><p>{{ t("Bật nguồn và mục Khám phá trong ứng dụng trên điện thoại, rồi làm mới.") }}</p>
    </div>
    <section v-if="sourceId" class="categories" :aria-label="t('Danh mục Khám phá')">
      <p v-if="categoriesLoading" role="status">{{ t("Đang tải danh mục…") }}</p>
      <label v-if="categoryGroups.length > 1" class="category-dropdown"><span class="category-title">{{ t('Nhóm danh mục') }}</span>
        <CompactSelect :model-value="activeCategoryGroup?.title || ''" :options="categoryGroups.map(group => ({value:group.title,label:groupLabel(group.title)}))" :label="t('Nhóm danh mục')" searchable @change="title => chooseCategory(categoryGroups.find(group => group.title === title)!.items[0])" />
      </label>
      <label v-if="activeCategoryGroup" class="category-dropdown"><span class="category-title">{{ t('Thể loại') }}</span>
        <CompactSelect :model-value="query ? '' : categoryId" :options="activeCategoryGroup.items.map(c => ({value:c.id!,label:c.title}))" :label="t('Thể loại')" :placeholder="t('Chọn danh mục')" searchable @change="id => chooseCategory(categories.find(c => c.id === id)!)" />
      </label>
      <p v-if="!categoriesLoading && !categories.length" class="muted">{{ t("Nguồn chưa có danh mục. Bạn vẫn có thể tìm theo tên nếu nguồn hỗ trợ.") }}</p>
      <p v-if="categories.some(c => c.interactive)" class="muted">{{ t("Các bộ lọc tương tác của nguồn cần được chọn trong ứng dụng trên điện thoại.") }}</p>
    </section>
    <section :aria-label="t('Danh sách truyện')" :aria-busy="loading">
      <div class="results-heading"><ViewToggle /><h2>{{ query ? `${t('Kết quả cho')} “${query}”` : selectedCategory?.title || t('Truyện trong nguồn') }}</h2>
        <button v-if="query && categories.some(c => c.available)" type="button" @click="returnToCategories">{{ t("Xem danh mục") }}</button></div>
      <div v-if="error" class="empty-state" role="alert"><p>{{ error }}</p><button type="button" @click="retry">{{ t("Thử lại") }}</button>
        <button type="button" @click="selectSource()">{{ t("Tải lại danh mục") }}</button></div>
      <p v-else-if="loading" class="empty-state" role="status">{{ t("Đang tải truyện…") }}</p>
      <div v-else-if="books.length" class="discovery-grid" :data-layout="libraryLayout">
        <router-link v-for="book in books" :key="book.id" class="discovery-book" :to="{ name: 'discoveryBook', params: { id: book.id } }"
          :aria-label="`${t('Xem')} ${book.name}`">
          <div class="cover-placeholder"><img v-if="book.hasCover" :src="getExploreCoverUrl(book.id)" alt="" loading="lazy" @error="hideCover" />
            <span v-else aria-hidden="true">▤</span></div>
          <div class="book-description"><h3>{{ book.name }}</h3><p class="muted">{{ book.author || t('Chưa rõ tác giả') }}</p>
            <p class="muted book-meta">{{ [book.kind, book.wordCount].filter(Boolean).join(' · ') }}</p>
            <p class="book-intro">{{ book.intro }}</p>
            <div class="book-footer"><span v-if="book.reader" class="shelf-badge">{{ t("Đã có trong tủ sách") }}</span>
            <p v-else-if="book.latestChapterTitle" class="muted">{{ t("Mới nhất") }}: {{ book.latestChapterTitle }}</p></div></div>
        </router-link>
      </div>
      <p v-else-if="hasLoaded" class="empty-state">{{ page > 1 ? t('Đã hết kết quả trong nguồn này.') : t('Chưa tìm thấy truyện. Thử danh mục hoặc từ khóa khác.') }}</p>
      <div v-if="hasLoaded && !error" class="pagination" :aria-label="t('Phân trang')">
        <button type="button" :disabled="page <= 1 || loading" @click="loadPage(page - 1)">{{ t("Trang trước") }}</button>
        <span>{{ t("Trang") }} {{ page }}</span><button type="button" :disabled="!hasMore || loading" @click="loadPage(page + 1)">{{ t("Trang sau") }}</button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getRelayBootstrap } from '@/api/relay'
import { getExploreSources, getExploreCategories, getExploreBooks, getExploreCoverUrl,
  type ExploreSource, type ExploreCategory, type ExploreBook } from '@/api/discovery'
import { legado_http_entry_point } from '@/api/api'
import '@/assets/discovery.css'
import CompactSelect from '@/components/CompactSelect.vue'
import ViewToggle from '@/components/ViewToggle.vue'
import { libraryLayout } from '@/config/libraryLayout'

const router = useRouter()
const sources = ref<ExploreSource[]>([]), categories = ref<ExploreCategory[]>([]), books = ref<ExploreBook[]>([])
const sourceType = ref('all')
const sourceId = ref(''), categoryId = ref(''), sourceFilter = ref(''), queryInput = ref(''), query = ref('')
const sourcesLoading = ref(false), categoriesLoading = ref(false), loading = ref(false), hasLoaded = ref(false)
const sourceError = ref(''), error = ref(''), page = ref(1), hasMore = ref(false)
const selectedSource = computed(() => sources.value.find(s => s.id === sourceId.value))
const selectedCategory = computed(() => categories.value.find(c => c.id === categoryId.value))
const filteredSources = computed(() => sources.value.filter(s => (sourceType.value === 'all' || String(s.type) === sourceType.value) &&
  (s.id === sourceId.value || `${s.name} ${s.group || ''}`.toLocaleLowerCase().includes(sourceFilter.value.trim().toLocaleLowerCase()))))
async function filterType() { sourceId.value = filteredSources.value[0]?.id || ''; if (sourceId.value) await selectSource(); else { ++categoryRequest; ++pageRequest; categories.value = []; books.value = []; hasLoaded.value = false } }
const categoryGroups = computed(() => {
  const groups: { title: string; items: ExploreCategory[] }[] = []
  let title = t('Thể loại')
  for (const category of categories.value) {
    if (!category.available && !category.interactive && category.title.trim()) { title = category.title; continue }
    if (!category.available) continue
    let group = groups.find(g => g.title === title)
    if (!group) { group = { title, items: [] }; groups.push(group) }
    group.items.push(category)
  }
  return groups
})
const activeCategoryGroup = computed(() => categoryGroups.value.find(group => group.items.some(c => c.id === categoryId.value)) || categoryGroups.value[0])
const groupLabel = (title: string) => title.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '').trim() || title
const selectionKey = `legadoExploreSource:${legado_http_entry_point}`
const viewKey = `legadoExploreView:${legado_http_entry_point}`
type SavedView = { sourceId: string; categoryTitle?: string; query?: string; page: number }
let attemptedPage = 1
let alive = true, sourceRequest = 0, categoryRequest = 0, pageRequest = 0
const message = (e: unknown) => e instanceof Error && e.name !== 'AxiosError' ? e.message : t('Không tải được Khám phá. Hãy thử lại.')
const hideCover = (event: Event) => { (event.target as HTMLImageElement).style.visibility = 'hidden' }
async function loadSources() {
  const request = ++sourceRequest
  sourcesLoading.value = true; sourceError.value = ''
  try {
    const data = await getExploreSources()
    if (!alive || request !== sourceRequest) return
    sources.value = data
    sourcesLoading.value = false
    const remembered = sessionStorage.getItem(selectionKey)
    sourceId.value = data.find(s => s.id === sourceId.value || s.id === remembered)?.id || data[0]?.id || ''
    let saved: SavedView | undefined
    try { saved = JSON.parse(sessionStorage.getItem(viewKey) || 'null') || undefined } catch { /* Start a fresh view. */ }
    if (sourceId.value) await selectSource(saved?.sourceId === sourceId.value ? saved : undefined)
    else { categories.value = []; books.value = []; hasLoaded.value = false }
  } catch (e) { if (alive && request === sourceRequest) sourceError.value = message(e) }
  finally { if (alive && request === sourceRequest) sourcesLoading.value = false }
}
async function selectSource(saved?: SavedView) {
  const request = ++categoryRequest; ++pageRequest
  const id = sourceId.value
  categoriesLoading.value = true; loading.value = false; error.value = ''; hasLoaded.value = false
  categories.value = []; books.value = []; categoryId.value = ''; query.value = ''; queryInput.value = ''
  sessionStorage.setItem(selectionKey, id)
  try {
    const data = await getExploreCategories(id)
    if (!alive || request !== categoryRequest) return
    categories.value = data
    categoriesLoading.value = false
    const first = data.find(c => c.available && c.title === saved?.categoryTitle) || data.find(c => c.available)
    categoryId.value = first?.id || ''
    if (saved?.query && selectedSource.value?.canSearch) {
      query.value = saved.query; queryInput.value = saved.query
    }
    if (first || query.value) await loadPage(saved && saved.page >= 1 && saved.page <= 1000 ? saved.page : 1)
  } catch (e) { if (alive && request === categoryRequest) error.value = message(e) }
  finally { if (alive && request === categoryRequest) categoriesLoading.value = false }
}
async function chooseCategory(category: ExploreCategory) {
  categoryId.value = category.id || ''; query.value = ''; queryInput.value = ''
  await loadPage(1)
}
async function searchBooks() {
  if (!queryInput.value.trim()) return
  query.value = queryInput.value.trim(); await loadPage(1)
}
async function returnToCategories() {
  const category = categories.value.find(c => c.id === categoryId.value) || categories.value.find(c => c.available)
  if (category) await chooseCategory(category)
}
async function loadPage(target: number) {
  const request = ++pageRequest
  loading.value = true; error.value = ''; attemptedPage = target
  try {
    const result = await getExploreBooks({ sourceId: sourceId.value, categoryId: categoryId.value, query: query.value, page: target })
    if (!alive || request !== pageRequest) return
    books.value = result.books; page.value = result.page; hasMore.value = result.hasMore; hasLoaded.value = true
    sessionStorage.setItem(viewKey, JSON.stringify({ sourceId: sourceId.value,
      categoryTitle: selectedCategory.value?.title, query: query.value, page: result.page }))
  } catch (e) { if (alive && request === pageRequest) error.value = message(e) }
  finally { if (alive && request === pageRequest) loading.value = false }
}
const retry = () => hasLoaded.value || query.value || categoryId.value ? loadPage(attemptedPage) : selectSource()
onMounted(() => {
  document.title = t('Khám phá')
  if (getRelayBootstrap()) { void router.replace('/'); return }
  void loadSources()
})
onUnmounted(() => { alive = false; ++sourceRequest; ++categoryRequest; ++pageRequest })
</script>
