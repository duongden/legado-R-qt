import { defineStore } from 'pinia'
import { appearanceIsDark, migrateLegacyTheme } from '@/config/appearance'
import { translationEpoch } from '@/api/translation'
import API from '@api'
import type {
  BaseBook,
  Book,
  BookChapter,
  BookProgress,
} from '@/book'
import type { webReadConfig } from '@/web'
import { ElMessage } from 'element-plus/es'
import { getRelayBootstrap } from '@/api/relay'
import { ReadProgressSync, type SyncStatus } from './readProgressSync'

const default_config: webReadConfig = {
  theme: 0,
  font: 0,
  fontSize: 18,
  readWidth: 800,
  infiniteLoading: false,
  customFontName: '',
  jumpDuration: 1000,
  spacing: {
    paragraph: 1,
    line: 0.8,
    letter: 0,
  },
}
let webReadConfigLoadedDate: Date | undefined
let progressSync: ReadProgressSync | undefined

export const useBookStore = defineStore('book', {
  state: () => {
    return {
      shelf: [] as Book[],
      progressSyncStatus: 'loading' as SyncStatus,
      remoteProgressEpoch: 0,
      catalog: [] as BookChapter[],
      readingBook: { chapterPos: 0, chapterIndex: 0 } as BaseBook & {
        chapterPos: number
        chapterIndex: number
        isSeachBook?: boolean
      },
      popCataVisible: false,
      contentLoading: true,
      showContent: false,
      config: default_config,
      miniInterface: false,
      readSettingsVisible: false,
    }
  },
  getters: {
    bookProgress: (state): BookProgress | undefined => {
      if (state.catalog.length == 0) return
      const { chapterIndex, chapterPos, name, author, bookUrl } = state.readingBook
      const title = state.catalog[chapterIndex]?.title
      if (!title) return
      return {
        name,
        author,
        bookUrl,
        durChapterIndex: chapterIndex,
        durChapterPos: chapterPos,
        durChapterTime: new Date().getTime(),
        durChapterTitle: title,
      }
    },
    theme: state => {
      return state.config.theme
    },
    isNight: () => appearanceIsDark.value,
  },
  actions: {
    /** 从后端加载书架书籍，优先返回内存缓存 */
    async loadBookShelf(): Promise<Book[]> {
      const translation = translationEpoch.value
      const fetchBookshellf_promise = API.getBookShelf().then(resp => {
        if (translation !== translationEpoch.value) return this.shelf
        console.log('API.getBookShelf数据返回')
        const { isSuccess, data, errorMsg } = resp.data
        if (isSuccess === true) {
          if (
            this.shelf.length !== data.length &&
            this.shelf.length > 0 &&
            data.length > 0
          ) {
            ElMessage.info(`Đã cập nhật tủ sách`)
          }
          this.shelf = data.sort((a, b) => {
            const x = a['durChapterTime'] || 0
            const y = b['durChapterTime'] || 0
            return y - x
          })
        } else {
          if (errorMsg.includes('还没有添加小说') && this.shelf.length > 0) {
            ElMessage.info('Sách trong tủ đã bị xóa')
            return (this.shelf = [])
          }
          ElMessage.error(errorMsg ?? 'Máy chủ trả về dữ liệu sai định dạng!')
        }
        console.log('Đã cập nhật tủ sách')
        return this.shelf
      })

      return await fetchBookshellf_promise
    },
    /** 从后端加载书籍目录，优先返回内存缓存 */
    async loadWebCatalog(
      book: typeof this.readingBook,
    ): Promise<BookChapter[]> {
      const translation = translationEpoch.value
      const { bookUrl, name, chapterIndex } = book
      const fetchChapterList_promise = API.getChapterList(
        bookUrl as string,
      ).then(res => {
        if (translation !== translationEpoch.value) return this.catalog
        const { isSuccess, data, errorMsg } = res.data
        if (isSuccess === false) {
          ElMessage.error(errorMsg)
          throw new Error()
        }
        if (
          bookUrl === this.readingBook.bookUrl &&
          data.length !== this.catalog.length &&
          data.length > 0 &&
          this.catalog.length > 0
        ) {
          ElMessage.info(`Sách ${name}: đã cập nhật mục lục`)
        }
        this.catalog = data
        console.log(`Sách ${name}: đã cập nhật mục lục`)
        return this.catalog
      })
      if (
        bookUrl === this.readingBook.bookUrl &&
        this.catalog.length > 0 &&
        this.catalog.length - 1 >= chapterIndex
      ) {
        console.log(`返回书籍《${name}》 缓存的章节目录`)
        return this.catalog
      } else {
        console.log(`从阅读后端获取书籍《${name}》 章节目录数据...`)
        return await fetchChapterList_promise
      }
    },
    setPopCataVisible(visible: boolean) {
      this.popCataVisible = visible
    },
    setContentLoading(loading: boolean) {
      this.contentLoading = loading
    },
    setReadingBook(readingBook: typeof this.readingBook) {
      this.readingBook = readingBook
    },
    /** 只从从后端加载一次web阅读配置 */
    async loadWebConfig() {
      if (webReadConfigLoadedDate === undefined) {
        const _config = await API.getReadConfig()
        webReadConfigLoadedDate = new Date()
        console.log(
          `${this.$id}.loadWebConfig: ${webReadConfigLoadedDate.toLocaleString()}成功加载阅读配置`,
        )
        return this.setConfig(_config)
      }
      console.log(
        `${this.$id}.loadWebConfig: 已于${webReadConfigLoadedDate.toLocaleString()}成功加载`,
      )
    },
    setConfig(config?: webReadConfig) {
      if (config) migrateLegacyTheme(config.theme)
      this.config = Object.assign({}, this.config, config)
    },
    setReadSettingsVisible(visible: boolean) {
      this.readSettingsVisible = visible
    },
    setShowContent(visible: boolean) {
      this.showContent = visible
    },
    setMiniInterface(mini: boolean) {
      this.miniInterface = mini
    },
    async startProgressSync() {
      progressSync?.stop()
      progressSync = undefined
      if (getRelayBootstrap()) return
      const bookUrl = this.readingBook.bookUrl
      this.progressSyncStatus = 'loading'
      progressSync = new ReadProgressSync({
        read: () => API.getReadProgress(bookUrl),
        write: position => API.saveReadProgress(bookUrl, position),
      }, (snapshot, conflict) => {
        if (this.readingBook.bookUrl !== bookUrl) return
        this.readingBook.chapterIndex = snapshot.progress.durChapterIndex
        this.readingBook.chapterPos = snapshot.progress.durChapterPos
        this.remoteProgressEpoch++
        if (conflict) ElMessage.info('Đã nhận tiến trình mới hơn từ điện thoại hoặc tab khác')
      }, status => { this.progressSyncStatus = status })
      await progressSync.refresh()
    },
    async refreshReadProgress() {
      await progressSync?.refresh()
    },
    markReadProgress() {
      progressSync?.update({
        durChapterIndex: this.readingBook.chapterIndex,
        durChapterPos: this.readingBook.chapterPos,
      })
    },
    stopProgressSync() {
      progressSync?.stop()
      progressSync = undefined
    },
    hasPendingReadProgress() { return progressSync?.hasPending === true },
    /** LAN saves are acknowledged and serialized; keepalive is for page dismissal. */
    async saveBookProgress(keepalive = false) {
      if (progressSync) return progressSync.flush(keepalive)
      if (!getRelayBootstrap()) return
      if (!this.bookProgress) return Promise.resolve()
      const { bookUrl } = this.readingBook
      const shelfRaw = toRaw(this.shelf)
      const findIndex = shelfRaw.findIndex(book => book.bookUrl === bookUrl)
      if (findIndex > -1) {
        this.shelf[findIndex] = Object.assign(
          {},
          shelfRaw[findIndex],
          this.bookProgress,
        )
      }
      // 直接关闭浏览器时 http请求可能被取消
      // return API.saveBookProgress(this.bookProgress)
      return API.saveBookProgressWithBeacon(this.bookProgress)
    },
  },
})
