<template>
  <main class="library-page" ref="shelfWrapper">
    <div class="page-heading"><div><h1>{{ t("Tủ sách") }} <span class="book-count">{{ shelf.length }}</span></h1><p class="muted">{{ t("Những câu chuyện đang chờ bạn tiếp tục.") }}</p></div>
      <button type="button" :disabled="shelfPending" @click="loadShelf">{{ t("Làm mới") }}</button></div>
    <div class="library-toolbar"><label class="library-search"><svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="8.5" cy="8.5" r="5.5"/><path d="m13 13 4 4"/></svg><input v-model="query" type="search" :aria-label="t('Tìm trong tủ sách')" :placeholder="t('Tìm tên sách, tác giả…')" /></label><ViewToggle /></div>
    <div class="library-secondary"><button v-if="readingRecent.bookUrl" type="button" class="recent-reading" @click="toDetail(readingRecent.bookUrl, readingRecent.name, readingRecent.author, readingRecent.chapterIndex, readingRecent.chapterPos, readingRecent.isSeachBook, true)"><span>{{ t("Đọc tiếp") }}</span><strong>{{ readingRecent.name }}</strong></button>
      <button type="button" class="text-button connection-button" :disabled="newConnect" @click="setLegadoRetmoteUrl">{{ connectType === 'danger' ? t('Kết nối gián đoạn') : t('Kết nối') }}</button></div>
    <div v-if="shelfPending && !shelf.length" class="empty-state" role="status">{{ t("Đang mở thư viện…") }}</div>
    <div v-else-if="shelfError" class="empty-state" role="alert"><h2>{{ t("Chưa kết nối được với thư viện") }}</h2><p>{{ t("Kiểm tra dịch vụ Web trên điện thoại rồi thử lại.") }}</p><button type="button" @click="loadShelf">{{ t("Thử lại") }}</button></div>
    <div v-else-if="!books.length" class="empty-state"><h2>{{ query ? t('Không tìm thấy sách') : t('Tủ sách đang trống') }}</h2><p>{{ query ? t('Thử một tên sách hoặc tác giả khác.') : t('Thêm sách trong ứng dụng để đọc tại đây.') }}</p><router-link v-if="!isRelay" to="/discovery">{{ t("Khám phá") }}</router-link></div>
    <BookItems v-else :books="books" :layout="libraryLayout" :can-manage="!isRelay" @manage="openTransfer" @bookClick="handleBookClick" />
  </main>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import { getRelayBootstrap } from '@/api/relay'
const isRelay = !!getRelayBootstrap()
import '@/assets/bookshelf.css'
import ViewToggle from '@/components/ViewToggle.vue'
import { libraryLayout } from '@/config/libraryLayout'
import { useBookStore } from '@/store'
import { useLoading } from '@/hooks/loading'
import { baseURL_localStorage_key } from '@/api/axios'
import API, {
  legado_http_entry_point,
  parseLeagdoHttpUrlWithDefault,
  setApiEntryPoint,
} from '@api'
import { validatorHttpUrl } from '@/utils/utils'
import type { Book } from '@/book'
import type { webReadConfig } from '@/web'

const openTransfer = (book: Book) => router.push({ name: 'downloads', query: { bookUrl: book.bookUrl } })
const store = useBookStore()

/** shortcuts of `store.setConfig` */
const applyReadConfig = (config?: webReadConfig) => {
  try {
    if (config !== undefined) store.setConfig(config)
  } catch {
    ElMessage.info(t('Không đọc được cấu hình giao diện'))
  }
}

const readingRecent = ref<typeof store.readingBook>({
  name: t('Chưa có lịch sử đọc'),
  author: '',
  bookUrl: '',
  chapterIndex: 0,
  chapterPos: 0,
  isSeachBook: false,
})

const shelfWrapper = ref<HTMLElement>()
const { loadingWrapper } = useLoading(
  shelfWrapper,
  t('Đang tải thông tin sách'),
)

// 书架书籍
const shelf = computed(() => store.shelf)
const query = ref('')
const books = computed(() => {
  const term = query.value.trim().toLocaleLowerCase('vi')
  return term ? shelf.value.filter(book => `${book.name} ${book.author}`.toLocaleLowerCase('vi').includes(term)) : shelf.value
})
const shelfPending = ref(true)
const shelfError = ref(false)

//连接状态
const connectionStore = useConnectionStore()
const { connectType, newConnect } = storeToRefs(connectionStore)

const setLegadoRetmoteUrl = () => {
  ElMessageBox.prompt(
    t('Nhập địa chỉ máy chủ (ví dụ: http://127.0.0.1:9527 hoặc địa chỉ truy cập từ xa)'),
    t('Thông báo'),
    {
      confirmButtonText: t('Xác nhận'),
      cancelButtonText: t('Hủy'),
      inputPlaceholder: legado_http_entry_point,
      inputValidator: value => validatorHttpUrl(value),
      inputErrorMessage: t('Định dạng không hợp lệ'),
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          connectionStore.setNewConnect(true)
          instance.confirmButtonLoading = true
          instance.confirmButtonText = t('Đang kiểm tra…')
          // instance.inputValue
          const url = new URL(instance.inputValue).toString()
          API.getReadConfig(url)
            .then(function (config) {
              connectionStore.setNewConnect(false)
              applyReadConfig(config)
              instance.confirmButtonLoading = false
              setApiEntryPoint(...parseLeagdoHttpUrlWithDefault(url))
              if (url === location.origin) {
                localStorage.removeItem(baseURL_localStorage_key)
              } else {
                localStorage.setItem(baseURL_localStorage_key, url)
              }
              store.loadBookShelf()
              done()
            })
            .catch(function (error) {
              connectionStore.setNewConnect(false)
              instance.confirmButtonLoading = false
              instance.confirmButtonText = t('Xác nhận')
              throw error
            })
        } else {
          done()
        }
      },
    },
  )
}

const router = useRouter()
const handleBookClick = (book: Book) => {
  const {
    bookUrl,
    name,
    author,
    durChapterIndex = 0,
    durChapterPos = 0,
  } = book

  toDetail(bookUrl, name, author, durChapterIndex, durChapterPos, false)
}
const toDetail = (
  bookUrl: string,
  bookName: string,
  bookAuthor: string,
  chapterIndex: number,
  chapterPos: number,
  isSeachBook: boolean | undefined = false,
  fromReadRecentClick = false,
) => {
  if (bookName === t('Chưa có lịch sử đọc')) return
  // 最近书籍不再书架上 自动搜索
  if (
    fromReadRecentClick &&
    shelf.value.every(book => book.bookUrl !== bookUrl)
  ) {
    ElMessage.info(t('Sách này không còn trong tủ sách'))
    return
  }
  sessionStorage.setItem('bookUrl', bookUrl)
  sessionStorage.setItem('bookName', bookName)
  sessionStorage.setItem('bookAuthor', bookAuthor)
  sessionStorage.setItem('chapterIndex', String(chapterIndex))
  sessionStorage.setItem('chapterPos', String(chapterPos))
  sessionStorage.setItem('isSeachBook', String(isSeachBook))
  readingRecent.value = {
    name: bookName,
    author: bookAuthor,
    bookUrl,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  localStorage.setItem('readingRecent', JSON.stringify(readingRecent.value))
  router.push({
    path: !isRelay && ((shelf.value.find(b => b.bookUrl === bookUrl)?.type || 8) & (4 | 32 | 64)) ? '/media' : '/chapter',
    query: { bookUrl },
  })
}

const loadShelf = async () => {
  shelfPending.value = true
  shelfError.value = false
  try {
    await store.loadWebConfig()
    await store.saveBookProgress()
    await store.loadBookShelf()
  } catch { shelfError.value = true }
  finally { shelfPending.value = false }
}

onMounted(() => {
  //获取Đọc gần đây书籍
  const readingRecentStr = localStorage.getItem('readingRecent')
  if (readingRecentStr != null) {
    readingRecent.value = JSON.parse(readingRecentStr)
    if (typeof readingRecent.value.chapterIndex == 'undefined') {
      readingRecent.value.chapterIndex = 0
    }
  }
  console.log('bookshelf mounted')
  loadingWrapper(loadShelf())
})
</script>
<style scoped>
.library-search { display:flex; align-items:center; gap:10px; flex:1; min-width:140px; border:var(--border-ui); border-radius:10px; background:var(--surface); padding:0 12px; }
.library-search svg { width:18px; height:18px; flex-shrink:0; fill:none; stroke:var(--muted); stroke-width:1.6; }
.library-search input { border:0; outline:0; min-width:0; width:100%; padding:11px 0; background:transparent; color:var(--ink); font:inherit; }
.library-secondary { display:flex; gap:12px; align-items:center; justify-content:space-between; margin:16px 0 24px; }
.recent-reading { display:flex; align-items:center; gap:10px; min-width:0; font-size:12px; max-width:80%; }
.recent-reading strong { max-width:45ch; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:500; }
.recent-reading span { color:var(--muted); flex-shrink:0; }
.connection-button { margin-left:auto; }
.book-count { color:var(--muted); font-size:13px; font-weight:400; vertical-align:middle; }
</style>
