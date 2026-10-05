<template>
  <div class="index-wrapper">
    <aside class="navigation-wrapper">
      <div class="navigation-title-wrapper"><span class="brand-mark" aria-hidden="true">L</span><div><div class="navigation-title">Legado</div><div class="navigation-sub-title">Một khoảng lặng để đọc</div></div></div>
      <nav v-if="!isRelay" class="service-navigation" aria-label="Chức năng Web Service">
        <router-link to="/" aria-current="page">Tủ sách</router-link>
        <router-link to="/bookSource">Nguồn sách</router-link>
        <a href="../uploadBook/index.html">Gửi sách</a>
        <router-link to="/rssSource">Nguồn RSS</router-link>
        <a href="../help/index.html#appHelp">Trợ giúp</a>
      </nav>
      <div class="bottom-wrapper">
        <section class="recent-wrapper"><h2 class="recent-title">Đọc gần đây</h2><button class="recent-book" type="button" :disabled="!readingRecent.bookUrl" @click="toDetail(readingRecent.bookUrl, readingRecent.name, readingRecent.author, readingRecent.chapterIndex, readingRecent.chapterPos, readingRecent.isSeachBook, true)"><span>{{ readingRecent.name }}</span><span v-if="readingRecent.bookUrl" class="continue-label">Đọc tiếp →</span></button></section>
        <section class="setting-wrapper"><h2 class="setting-title">Cài đặt chung</h2><TranslationToggle /><h3>Giao diện</h3><ThemePicker /><button class="setting-connect" type="button" :disabled="newConnect" @click="setLegadoRetmoteUrl"><span class="connection-dot" :class="connectType" aria-hidden="true"></span>{{ connectType === 'danger' ? 'Kết nối gián đoạn' : connectStatus.startsWith('Đã kết nối') ? 'Đã kết nối' : 'Đang kết nối…' }}</button></section>
      </div>
      <div class="sidebar-footer">Không gian đọc của bạn</div>
    </aside>
    <main class="shelf-wrapper" ref="shelfWrapper">
      <header class="shelf-heading"><div><span class="eyebrow">THƯ VIỆN CỦA BẠN</span><h1>Tủ sách<span class="book-count">{{ shelf.length }}</span></h1></div><button type="button" class="refresh-shelf" :disabled="shelfPending" @click="loadShelf">Làm mới</button></header>
      <label class="shelf-search"><svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" focusable="false"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></svg><input v-model="query" type="search" aria-label="Tìm trong tủ sách" placeholder="Tìm tên sách, tác giả…" /></label>
      <p class="shelf-caption">Những câu chuyện đang chờ bạn tiếp tục.</p>
      <div v-if="shelfPending && !shelf.length" class="shelf-state" role="status">Đang mở thư viện…</div>
      <div v-else-if="shelfError" class="shelf-state" role="alert"><h2>Chưa kết nối được với thư viện</h2><p>Kiểm tra dịch vụ Web trên điện thoại rồi thử lại.</p><button type="button" @click="loadShelf">Thử lại</button></div>
      <div v-else-if="!books.length" class="shelf-state"><span class="empty-symbol" aria-hidden="true">▤</span><h2>{{ query ? 'Không tìm thấy sách' : 'Tủ sách đang trống' }}</h2><p>{{ query ? 'Thử một tên sách hoặc tác giả khác.' : 'Thêm sách trong ứng dụng để đọc tại đây.' }}</p></div>
      <book-items v-else :books="books" @bookClick="handleBookClick" />
    </main>
  </div>
</template>
<script setup lang="ts">
import TranslationToggle from '@/components/TranslationToggle.vue'
import ThemePicker from '@/components/ThemePicker.vue'
import { getRelayBootstrap } from '@/api/relay'
const isRelay = !!getRelayBootstrap()
import '@/assets/bookshelf.css'
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

const store = useBookStore()

/** shortcuts of `store.setConfig` */
const applyReadConfig = (config?: webReadConfig) => {
  try {
    if (config !== undefined) store.setConfig(config)
  } catch {
    ElMessage.info('Không đọc được cấu hình giao diện')
  }
}

const readingRecent = ref<typeof store.readingBook>({
  name: 'Chưa có lịch sử đọc',
  author: '',
  bookUrl: '',
  chapterIndex: 0,
  chapterPos: 0,
  isSeachBook: false,
})

const shelfWrapper = ref<HTMLElement>()
const { loadingWrapper } = useLoading(
  shelfWrapper,
  'Đang tải thông tin sách',
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
const { connectStatus, connectType, newConnect } = storeToRefs(connectionStore)

const setLegadoRetmoteUrl = () => {
  ElMessageBox.prompt(
    'Nhập địa chỉ máy chủ (ví dụ: http://127.0.0.1:9527 hoặc địa chỉ truy cập từ xa)',
    'Thông báo',
    {
      confirmButtonText: 'Xác nhận',
      cancelButtonText: 'Hủy',
      inputPlaceholder: legado_http_entry_point,
      inputValidator: value => validatorHttpUrl(value),
      inputErrorMessage: 'Định dạng không hợp lệ',
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          connectionStore.setNewConnect(true)
          instance.confirmButtonLoading = true
          instance.confirmButtonText = 'Đang kiểm tra…'
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
              instance.confirmButtonText = 'Xác nhận'
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
  if (bookName === 'Chưa có lịch sử đọc') return
  // 最近书籍不再书架上 自动搜索
  if (
    fromReadRecentClick &&
    shelf.value.every(book => book.bookUrl !== bookUrl)
  ) {
    ElMessage.info('Sách này không còn trong tủ sách')
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
    path: '/chapter',
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
.index-wrapper { display:grid; grid-template-columns:280px minmax(0,1fr); min-height:100vh; background:var(--page); color:var(--ink); }
.navigation-wrapper { background:var(--surface); border-right:1px solid var(--line); padding:32px 24px; display:flex; flex-direction:column; gap:32px; min-width:0; }
.navigation-title-wrapper { display:flex; gap:12px; align-items:center; }.brand-mark { display:grid; place-items:center; border-radius:var(--radius-control); width:40px; height:40px; background:var(--accent); color:var(--surface); font:700 28px Georgia,serif; }.navigation-title { font-weight:700; font-size:22px; letter-spacing:-.7px; }.navigation-sub-title { color:var(--muted); font-size:11px; margin-top:4px; }
.bottom-wrapper { display:grid; gap:28px; }.recent-title,.setting-title { font-size:12px; color:var(--muted); margin:0 0 12px; text-transform:uppercase; letter-spacing:.7px; }
.recent-book { width:100%; padding:16px; border:var(--border-ui); border-radius:var(--radius-card); background:var(--page); color:var(--ink); cursor:pointer; font:600 14px/1.6 var(--font-ui); text-align:left; overflow-wrap:anywhere; }.recent-book>span:first-child { display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }.recent-book:disabled { cursor:default; color:var(--muted); }.continue-label { display:block; color:var(--accent); font-size:12px; margin-top:10px; }
h3 { font-size:12px; font-weight:500; color:var(--muted); margin:22px 0 10px; }.setting-connect { display:flex; gap:8px; align-items:center; margin-top:20px; padding:8px 0; color:var(--muted); font-size:12px; background:none; border:0; cursor:pointer; }.connection-dot { width:7px; height:7px; background:var(--accent); border-radius:50%; }.connection-dot.danger { background:#c25248; }.sidebar-footer { margin-top:auto; padding-top:40px; font-size:11px; color:var(--muted); }
.shelf-wrapper { min-width:0; padding:48px clamp(24px,4vw,72px); }.shelf-heading { display:flex; justify-content:space-between; align-items:center; gap:16px; }.eyebrow { font-size:10px; font-weight:700; color:var(--muted); letter-spacing:2px; }h1 { font:700 clamp(28px,4vw,38px)/1.2 var(--font-ui); margin:10px 0 0; letter-spacing:-1px; display:flex; align-items:center; gap:12px; }.book-count { border:var(--border-ui); font:500 12px var(--font-ui); border-radius:100px; padding:5px 9px; letter-spacing:0; color:var(--muted); }
.refresh-shelf,.shelf-state button { border:var(--border-ui); border-radius:var(--radius-control); background:var(--surface); color:var(--ink); padding:10px 14px; cursor:pointer; }.refresh-shelf:disabled { opacity:.5; }
.shelf-search { display:flex; align-items:center; gap:10px; background:var(--surface); border:var(--border-ui); border-radius:var(--radius-control); padding:0 16px; margin-top:28px; width:100%; box-sizing:border-box; min-height:48px; }.search-icon { display:block; flex:0 0 20px; width:20px; height:20px; color:var(--muted); }.shelf-search input { border:0; background:none; color:var(--ink); padding:12px 0; flex:1; width:0; min-width:0; font:14px/22px var(--font-ui); box-sizing:border-box; }.shelf-search input::placeholder { color:var(--muted); }.shelf-caption { color:var(--muted); font-size:13px; margin:20px 0 24px; }.shelf-state { text-align:center; color:var(--muted); padding:64px 16px; border:1px dashed var(--line); border-radius:var(--radius-card); }.shelf-state h2 { font-size:18px; color:var(--ink); }.shelf-state p { font-size:14px; }.empty-symbol { font-size:40px; }
@media(max-width:900px) { .index-wrapper { grid-template-columns:230px minmax(0,1fr); }.navigation-wrapper { padding:24px 16px; }.shelf-wrapper { padding:32px 20px; } }
@media(max-width:750px) { .index-wrapper { display:block; }.navigation-wrapper { padding:20px; gap:20px; border-right:0; border-bottom:1px solid var(--line); }.bottom-wrapper { grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:16px; }.setting-wrapper :deep(.theme-picker) { grid-template-columns:repeat(3,minmax(0,1fr)); gap:4px; }.setting-wrapper :deep(.theme-picker button) { font-size:10px; padding:8px 2px; }.setting-wrapper :deep(.theme-swatch) { width:18px; height:18px; }.sidebar-footer { display:none; }.shelf-wrapper { padding:28px 16px; }.setting-wrapper h3 { margin-top:16px; }.recent-book { padding:12px; font-size:13px; }.navigation-title { font-size:20px; }.setting-connect { font-size:11px; } }
.service-navigation { display:grid; gap:8px; }
.service-navigation a { display:block; padding:10px 12px; border:var(--border-ui); border-radius:var(--radius-control); color:var(--ink); background:var(--surface); text-decoration:none; font:600 14px/1.5 var(--font-ui); }
.service-navigation a[aria-current=page] { color:var(--accent); border-color:var(--accent); background:var(--accent-soft); }
.service-navigation a:hover { background:var(--hover); }
@media(max-width:750px) { .service-navigation { grid-template-columns:repeat(2,minmax(0,1fr)); } }
</style>
