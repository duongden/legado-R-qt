<template>
  <div
    class="chapter-wrapper"
    :style="bodyTheme"
    :class="{ night: isNight, day: !isNight }"
    @click="showToolBar = !showToolBar"
  >
    <button
      v-if="miniInterface"
      type="button"
      class="toolbar-toggle"
      :style="bodyTheme"
      :aria-expanded="showToolBar"
      aria-controls="reader-tools"
      @click.stop="showToolBar = !showToolBar"
    >{{ showToolBar ? 'Ẩn công cụ' : 'Công cụ đọc' }}</button>
    <div id="reader-tools" class="tool-bar" :style="leftBarTheme" @click.stop @keydown.stop @keyup.stop>
      <div class="tools">
        <el-popover
          :placement="miniInterface ? 'bottom' : 'right'"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="popCataVisible"
          popper-class="pop-cata"
        >
          <PopCatalog @getContent="getContent" class="popup" @click.stop @keydown.stop @keyup.stop />
          <template #reference>
            <button type="button" class="tool-icon" :class="{ 'no-point': false }">
              <div class="iconfont" aria-hidden="true">&#58905;</div>
              <div class="icon-text">Mục lục</div>
            </button>
          </template>
        </el-popover>
        <el-popover
          :placement="miniInterface ? 'bottom' : 'right'"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="readSettingsVisible"
          popper-class="pop-setting"
        >
          <read-settings class="popup" @click.stop @keydown.stop @keyup.stop />
          <template #reference>
            <button type="button" class="tool-icon" :disabled="noPoint" :class="{ 'no-point': noPoint }">
              <div class="iconfont" aria-hidden="true">&#58971;</div>
              <div class="icon-text">Cài đặt</div>
            </button>
          </template>
        </el-popover>
        <button type="button" class="tool-icon" @click="toShelf">
          <div class="iconfont" aria-hidden="true">&#58892;</div>
          <div class="icon-text">Tủ sách</div>
        </button>
        <button type="button" class="tool-icon" :disabled="noPoint" :class="{ 'no-point': noPoint }" @click="toTop">
          <div class="iconfont" aria-hidden="true">&#58914;</div>
          <div class="icon-text">Đầu trang</div>
        </button>
        <button type="button"
          class="tool-icon"
          :disabled="noPoint" :class="{ 'no-point': noPoint }"
          @click="toBottom"
        >
          <div class="iconfont" aria-hidden="true">&#58915;</div>
          <div class="icon-text">Cuối trang</div>
        </button>
      </div>
    </div>
    <div class="read-bar" :style="rightBarTheme" @click.stop @keydown.stop @keyup.stop>
      <div class="tools">
        <button type="button"
          class="tool-icon"
          :disabled="noPoint" :class="{ 'no-point': noPoint }"
          aria-label="Chương trước"
          @click="toPreChapter"
        >
          <div class="iconfont" aria-hidden="true">&#58920;</div>
          <span v-if="miniInterface">Chương trước</span>
        </button>
        <button type="button"
          class="tool-icon"
          :disabled="noPoint" :class="{ 'no-point': noPoint }"
          aria-label="Chương sau"
          @click="toNextChapter"
        >
          <span v-if="miniInterface">Chương sau</span>
          <div class="iconfont" aria-hidden="true">&#58913;</div>
        </button>
      </div>
    </div>
    <div class="chapter-bar"></div>
    <div class="chapter" ref="content" :style="chapterTheme">
      <div class="content">
        <div class="top-bar" ref="top"></div>
        <div
          v-for="data in chapterData"
          :key="data.index"
          :chapterIndex="data.index"
          ref="chapter"
        >
          <chapter-content
            ref="chapterRef"
            :chapterIndex="data.index"
            :contents="data.content"
            :title="data.title"
            :spacing="store.config.spacing"
            :fontSize="fontSize"
            :fontFamily="fontFamily"
            @readedLengthChange="onReadedLengthChange"
            v-if="showContent"
          />
        </div>
        <div class="loading" ref="loading"></div>
        <div class="bottom-bar" ref="bottom"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { translationEpoch } from '@/api/translation'
let translationViewAlive = true
const translationViewEpoch = translationEpoch.value
import jump from '@/plugins/jump'
import settings from '@/config/themeConfig'
import API from '@api'
import { useLoading } from '@/hooks/loading'
import { useThrottleFn } from '@vueuse/shared'
import { isNullOrBlank } from '@/utils/utils'

const content = ref()
// loading spinner
const { isLoading, loadingWrapper } = useLoading(content, 'Đang tải thông tin')
const store = useBookStore()

const {
  catalog,
  popCataVisible,
  readSettingsVisible,
  miniInterface,
  showContent,
  bookProgress,
  theme,
  isNight,
} = storeToRefs(store)

const chapterPos = computed({
  get: () => store.readingBook.chapterPos,
  set: value => (store.readingBook.chapterPos = value),
})
const chapterIndex = computed({
  get: () => store.readingBook.chapterIndex,
  set: value => (store.readingBook.chapterIndex = value),
})
const isSeachBook = computed({
  get: () => store.readingBook.isSeachBook,
  set: value => (store.readingBook.isSeachBook = value),
})

// 当前阅读书籍readingBook持久化
watch(
  () => store.readingBook,
  book => {
    // 保存localStorage
    // localStorage.setItem(book.bookUrl, JSON.stringify(book));
    // Đọc gần đây
    localStorage.setItem('readingRecent', JSON.stringify(book))
    //保存 sessionStorage
    sessionStorage.setItem('chapterIndex', book.chapterIndex.toString())
    sessionStorage.setItem('chapterPos', book.chapterPos.toString())
  },
  { deep: 1 },
)

// 无限滚动
const infiniteLoading = computed(() => store.config.infiniteLoading)
let scrollObserver: IntersectionObserver | null
const loading = ref()
watchEffect(() => {
  if (!infiniteLoading.value) {
    scrollObserver?.disconnect()
  } else {
    scrollObserver?.observe(loading.value)
  }
})
const loadMore = () => {
  const index = chapterData.value.slice(-1)[0].index
  if (catalog.value.length - 1 > index) {
    getContent(index + 1, false)
    store.saveBookProgress() // 保存的是Chương trước的进度，不是预载的本章进度
  }
}
// IntersectionObserver回调 底部加载
const onReachBottom = (entries: IntersectionObserverEntry[]) => {
  if (isLoading.value) return
  for (const { isIntersecting } of entries) {
    if (!isIntersecting) return
    loadMore()
  }
}

// 字体
const fontFamily = computed(() => {
  if (store.config.font >= 0) {
    return settings.fonts[store.config.font]
  }
  return store.config.customFontName
})
const fontSize = computed(() => {
  return store.config.fontSize + 'px'
})

// 主题部分
const bodyColor = computed(() => settings.themes[theme.value].body)
const chapterColor = computed(() => settings.themes[theme.value].content)
const popupColor = computed(() => settings.themes[theme.value].popup)

const readWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 130 + 'px'
  } else {
    return window.innerWidth + 'px'
  }
})
const viewportWidth = ref(window.innerWidth)
const popupWidth = computed(() => Math.min(460, viewportWidth.value - 24))
const bodyTheme = computed(() => {
  return {
    background: bodyColor.value,
  }
})
const chapterTheme = computed(() => {
  return {
    background: chapterColor.value,
    width: readWidth.value,
  }
})
const showToolBar = ref(false)
const leftBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginLeft: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 68) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})
const rightBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginRight: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 52) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})

/**
 * pc移动端判断 最大阅读宽度修正
 * 阅读宽度最小为640px 加上工具栏 68px 52px 取较大值 为 776px
 */
const onResize = () => {
  viewportWidth.value = window.innerWidth
  store.setMiniInterface(window.innerWidth < 776)
  const width = store.config.readWidth /**包含padding */
  checkPageWidth(width)
}
/** 判断阅读宽度是否超出页面或者低于默认值640 */
const checkPageWidth = (readWidth: number) => {
  if (store.miniInterface) return
  if (readWidth < 640) store.config.readWidth = 640
  if (readWidth + 2 * 68 > window.innerWidth) store.config.readWidth -= 160
}
watch(
  () => store.config.readWidth,
  width => checkPageWidth(width),
)
// 顶部底部跳转
const top = ref()
const bottom = ref()
const toTop = () => {
  jump(top.value)
}
const toBottom = () => {
  jump(bottom.value)
}

// 书架路由切换
const router = useRouter()
const toShelf = () => {
  router.push('/')
}

// 获取章节内容
const chapterData = ref<{ index: number; content: string[]; title: string }[]>(
  [],
)
const noPoint = ref(true)
const getContent = (index: number, reloadChapter = true, chapterPos = 0) => {
  if (reloadChapter) {
    //展示进度条
    store.setShowContent(false)
    //强制滚回顶层
    jump(top.value, { duration: 0 })
    //从目录，按钮切换章节时保存进度 预加载时不保存
    saveReadingBookProgressToBrowser(index, chapterPos)
    chapterData.value = []
  }
  const bookUrl = store.readingBook.bookUrl
  const { title, index: chapterIndex } = catalog.value[index]

  loadingWrapper(
    API.getBookContent(bookUrl, chapterIndex).then(
      res => {
        if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
        if (res.data.isSuccess) {
          const data = res.data.data
          const content = data.split(/\n+/)
          chapterData.value.push({ index, content, title })
          if (reloadChapter) toChapterPos(chapterPos)
        } else {
          ElMessage({ message: res.data.errorMsg, type: 'error' })
          const content = [res.data.errorMsg]
          chapterData.value.push({ index, content, title })
        }
        store.setContentLoading(true)
        noPoint.value = false
        store.setShowContent(true)
        if (!res.data.isSuccess) {
          throw res.data
        }
      },
      err => {
        if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
        const content = ['Không tải được nội dung chương!']
        chapterData.value.push({ index, content, title })
        store.setShowContent(true)
        throw err
      },
    ),
  )
}

// 章节进度跳转和计算
const chapter = ref()
const chapterRef = ref()
const toChapterPos = (pos: number) => {
  nextTick(() => {
    if (chapterRef.value.length === 1)
      chapterRef.value[0].scrollToReadedLength(pos)
  })
}

// 60秒保存一次进度
const saveBookProgressThrottle = useThrottleFn(
  () => store.saveBookProgress(),
  60000,
)

const onReadedLengthChange = (index: number, pos: number) => {
  if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
  saveReadingBookProgressToBrowser(index, pos)
  saveBookProgressThrottle()
}

// 文档标题
watchEffect(() => {
  document.title = catalog.value[chapterIndex.value]?.title || document.title
})

// 阅读记录保存浏览器
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
  // 保存pinia
  chapterIndex.value = index
  chapterPos.value = pos
}

// 进度同步
// 返回导航变化 同步请求会在获取书架前完成

/**
 * VisibilityChange https://developer.mozilla.org/zh-CN/docs/Web/API/Document/visibilitychange_event
 * 监听关闭页面 切换tab 返回桌面 等操作
 * 注意不用监听点击链接导航变化 不对Safari<14.5兼容处理
 **/
const onVisibilityChange = () => {
  const _bookProgress = bookProgress.value
  if (document.visibilityState == 'hidden' && _bookProgress) {
    store.saveBookProgress()
  }
}
// 定时同步

// 章节切换
const toNextChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value + 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: 'Chương sau',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: 'Đây là chương cuối',
      type: 'error',
    })
  }
}
const toPreChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value - 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: 'Chương trước',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: 'Đây là chương đầu',
      type: 'error',
    })
  }
}

const isReadingControl = (event: KeyboardEvent) =>
  event.target instanceof Element && !!event.target.closest(
    'button, input, textarea, select, [contenteditable="true"], [role="dialog"], .el-popper',
  )
let canJump = true
// 监听方向键
const handleKeyPress = (event: KeyboardEvent) => {
  if (!canJump || isReadingControl(event) || readSettingsVisible.value || popCataVisible.value) return
  switch (event.key) {
    case 'ArrowLeft':
      event.stopPropagation()
      event.preventDefault()
      toPreChapter()
      break
    case 'ArrowRight':
      event.stopPropagation()
      event.preventDefault()
      toNextChapter()
      break
    case 'ArrowUp':
      event.stopPropagation()
      event.preventDefault()
      if (document.documentElement.scrollTop === 0) {
        ElMessage.warning('Đã đến đầu trang')
      } else {
        canJump = false
        jump(0 - document.documentElement.clientHeight + 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
    case 'ArrowDown':
      event.stopPropagation()
      event.preventDefault()
      if (
        document.documentElement.clientHeight +
          document.documentElement.scrollTop ===
        document.documentElement.scrollHeight
      ) {
        ElMessage.warning('Đã đến cuối trang')
      } else {
        canJump = false
        jump(document.documentElement.clientHeight - 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
  }
}

// 阻止默认滚动事件
const ignoreKeyPress = (event: KeyboardEvent) => {
  if (isReadingControl(event) || readSettingsVisible.value || popCataVisible.value) return
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    event.stopPropagation()
  }
}

onMounted(async () => {
  await store.loadWebConfig()
  if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
  //获取书籍数据
  const bookUrl = sessionStorage.getItem('bookUrl')
  const name = sessionStorage.getItem('bookName')
  const author = sessionStorage.getItem('bookAuthor')
  const chapterIndex = Number(sessionStorage.getItem('chapterIndex') || 0)
  const chapterPos = Number(sessionStorage.getItem('chapterPos') || 0)
  const isSeachBook = sessionStorage.getItem('isSeachBook') === 'true'
  if (isNullOrBlank(bookUrl) || isNullOrBlank(name) || author === null) {
    ElMessage.warning('Không có thông tin sách, đang quay về tủ sách…')
    return setTimeout(toShelf, 500)
  }
  const book: typeof store.readingBook = {
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    bookUrl,
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    name,
    author,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  onResize()
  window.addEventListener('resize', onResize)
  loadingWrapper(
    store.loadWebCatalog(book).then(chapters => {
      if (!translationViewAlive || translationViewEpoch !== translationEpoch.value) return
      store.setReadingBook(book)
      getContent(chapterIndex, true, chapterPos)
      window.addEventListener('keyup', handleKeyPress)
      window.addEventListener('keydown', ignoreKeyPress)
      // 兼容Safari < 14
      document.addEventListener('visibilitychange', onVisibilityChange)
      //监听底部加载
      scrollObserver = new IntersectionObserver(onReachBottom, {
        rootMargin: '-100% 0% 20% 0%',
      })
      if (infiniteLoading.value === true) scrollObserver.observe(loading.value)
      //第二次点击同一本书 页面标题不会变化
      document.title = '...'
      document.title = (name as string) + ' | ' + chapters[chapterIndex].title
    }),
  )
})

onUnmounted(() => {
  translationViewAlive = false
  window.removeEventListener('keyup', handleKeyPress)
  window.removeEventListener('keydown', ignoreKeyPress)
  window.removeEventListener('resize', onResize)
  // 兼容Safari < 14
  document.removeEventListener('visibilitychange', onVisibilityChange)
  readSettingsVisible.value = false
  popCataVisible.value = false
  scrollObserver?.disconnect()
  scrollObserver = null
})

const addToBookShelfConfirm = async () => {
  const book = store.readingBook
  // 阅读的是搜索的书籍 并未在书架
  if (book.isSeachBook === true) {
    await ElMessageBox.confirm(`Thêm “${book.name}” vào tủ sách?`, 'Thêm vào tủ sách', {
      confirmButtonText: 'Xác nhận',
      cancelButtonText: 'Không',
      type: 'info',
      /*
        ElMessageBox.confirm默认在触发hashChange事件时自动关闭
        按下物理返回键时触发hashChange事件
        使用router.push("/")则不会触发hashChange事件
        */
      closeOnHashChange: false,
    })
      .then(() => {
        //选择是，无动作
        isSeachBook.value = false
      })
      .catch(async () => {
        //选择否，删除书籍
        await API.deleteBook(book)
      })
      .finally(() => sessionStorage.removeItem('isSeachBook'))
  }
}
onBeforeRouteLeave(async () => {
  // 弹窗时停止响应按键翻页
  window.removeEventListener('keyup', handleKeyPress)
  await addToBookShelfConfirm()
})
</script>

<style scoped>
.chapter-wrapper { min-height:100vh; color:var(--ink); }
.chapter { padding:0 65px; margin:0 auto; min-height:100vh; border-inline:1px solid var(--line); }
.content { line-height:1.8; }
.top-bar { height:56px; }
.bottom-bar { height:120px; }
.tool-bar,.read-bar { position:fixed; z-index:100; border:1px solid var(--line); border-radius:14px; box-shadow:var(--shadow); overflow:hidden; }
.tool-bar { top:24px; left:50%; }
.read-bar { bottom:24px; right:50%; }
.tools { display:flex; flex-direction:column; }
.tool-icon { border:0; background:transparent; color:var(--ink); font:inherit; cursor:pointer; width:58px; min-height:60px; padding:10px 2px; }
.tool-icon:hover { background:var(--hover); }
.tool-icon:disabled { opacity:.45; cursor:wait; }
.iconfont { font-family:iconfont; font-size:18px; }
.icon-text { font-size:11px; margin-top:6px; }
.read-bar .tool-icon { width:42px; min-height:48px; }
.toolbar-toggle { position:fixed; right:16px; bottom:78px; z-index:101; border:1px solid var(--line); border-radius:24px; padding:12px 16px; color:var(--ink); font:inherit; box-shadow:var(--shadow); cursor:pointer; }
@media(max-width:775px) {
 .chapter { width:100% !important; padding:0 22px; box-sizing:border-box; border:0; }
 .top-bar { height:76px; }
 .tool-bar { top:8px; left:8px; width:calc(100% - 18px); }
 .tools { flex-direction:row; justify-content:space-around; }
 .read-bar { right:8px; bottom:8px; width:calc(100% - 18px); }
 .read-bar .tool-icon { width:auto; padding:12px; display:flex; align-items:center; gap:10px; font-size:13px; }
}
</style>
