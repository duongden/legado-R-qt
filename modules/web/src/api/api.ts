/** https://github.com/gedoor/legado/tree/master/app/src/main/java/io/legado/app/api */
/** https://github.com/gedoor/legado/tree/master/app/src/main/java/io/legado/app/web */

import type { webReadConfig } from '@/web'
import ajax from './axios'
import { translationQuery } from './translation'
import type {
  BaseBook,
  Book,
  BookChapter,
  BookProgress,
} from '@/book'
import type { Source } from '@/source'
import { getRelayAuthorization, getRelayBootstrap } from './relay'
import type { ProgressSnapshot, ProgressResult } from '@/store/readProgressSync'

export type LeagdoApiResponse<T> = {
  isSuccess: boolean
  errorMsg: string
  data: T
}

export let legado_http_entry_point = ''
export let legado_webSocket_entry_point = ''

let wsOnError: typeof WebSocket.prototype.onerror = () => {}
let wsOnMessage: typeof WebSocket.prototype.onmessage = () => {}
export const setWebsocketOnMessage = (callback: typeof wsOnMessage) =>
  (wsOnMessage = callback)
export const setWebsocketOnError = (callback: typeof wsOnError) => {
  //WebSocket.prototype.onerror = callback
  wsOnError = callback
}

export const setApiEntryPoint = (
  http_entry_point: string,
  webSocket_entry_point: string,
) => {
  legado_http_entry_point = new URL(http_entry_point).toString()
  legado_webSocket_entry_point = new URL(webSocket_entry_point).toString()
  ajax.defaults.baseURL = legado_http_entry_point
}

// 书架API
// Http
const getReadConfig = async (http_url = legado_http_entry_point) => {
  const { data } = await ajax.get<LeagdoApiResponse<string>>('getReadConfig', {
    baseURL: http_url.toString(),
    timeout: 3000,
  })
  if (data.isSuccess) {
    try {
      return JSON.parse(data.data) as webReadConfig
    } catch {}
  }
}
const saveReadConfig = (config: webReadConfig) =>
  ajax.post<LeagdoApiResponse<string>>('saveReadConfig', config)

/** @deprecated: 使用`API.saveBookProgressWithBeacon`以确保在页面或者直接关闭的情况下保存进度 */
const saveBookProgress = (bookProgress: BookProgress) =>
  ajax.post('saveBookProgress', bookProgress)

/**主要在直接关闭浏览器情况下可靠发送书籍进度 */
const saveBookProgressWithBeacon = (bookProgress: BookProgress) => {
  if (!bookProgress) return
  const url = new URL('saveBookProgress', legado_http_entry_point)
  const authorization = getRelayAuthorization(url)
  void fetch(url, {
    method: 'POST',
    body: JSON.stringify(bookProgress),
    keepalive: true,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(authorization ? { Authorization: authorization } : {}),
    },
  })
}

const getBookShelf = () => ajax.get<LeagdoApiResponse<Book[]>>('getBookshelf?translate=' + translationQuery())

const getReadProgress = async (bookUrl: string): Promise<ProgressSnapshot> => {
  const url = new URL('getReadProgress', legado_http_entry_point)
  url.searchParams.set('url', bookUrl)
  url.searchParams.set('translate', translationQuery())
  const response = await fetch(url, {
    cache: 'no-store', signal: AbortSignal.timeout(10000),
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const data = await response.json() as LeagdoApiResponse<ProgressSnapshot>
  if (!data.isSuccess) throw new Error(data.errorMsg)
  return data.data
}

const saveReadProgress = async (
  bookUrl: string,
  position: { baseRevision: number; durChapterIndex: number; durChapterPos: number },
): Promise<ProgressResult> => {
  const response = await fetch(new URL('saveReadProgress', legado_http_entry_point), {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ bookUrl, ...position }), keepalive: true,
    signal: AbortSignal.timeout(10000), cache: 'no-store',
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const result = await response.json() as LeagdoApiResponse<ProgressResult>
  if (!result.isSuccess) throw new Error(result.errorMsg)
  return result.data
}

const getChapterList = (/** @type {string} */ bookUrl: string) =>
  ajax.get<LeagdoApiResponse<BookChapter[]>>(
    'getChapterList?url=' + encodeURIComponent(bookUrl) + '&translate=' + translationQuery(),
  )

const getBookContent = (
  /** @type {string} */ bookUrl: string,
  /** @type {number} */ chapterIndex: number,
) => {
  const path = getRelayBootstrap() ? 'getBookContentEx' : 'getBookContent'
  return ajax.get<LeagdoApiResponse<string>>(
    path + '?url=' +
      encodeURIComponent(bookUrl) +
      '&index=' +
      chapterIndex + '&translate=' + translationQuery(),
  )
}

const deleteBook = (book: BaseBook) =>
  ajax.post<LeagdoApiResponse<string>>('deleteBook', book)

const isBookSource = /bookSource/i.test(location.href)

// 源编辑API
// Http
const getSources = () =>
  isBookSource ? ajax.get('getBookSources') : ajax.get('getRssSources')

const saveSource = (data: Source) =>
  isBookSource
    ? ajax.post<LeagdoApiResponse<string>>('saveBookSource', data)
    : ajax.post<LeagdoApiResponse<string>>('saveRssSource', data)

const saveSources = (data: Source[]) =>
  isBookSource
    ? ajax.post<LeagdoApiResponse<Source[]>>('saveBookSources', data)
    : ajax.post<LeagdoApiResponse<Source[]>>('saveRssSources', data)

const deleteSource = (data: Source[]) =>
  isBookSource
    ? ajax.post<LeagdoApiResponse<string>>('deleteBookSources', data)
    : ajax.post<LeagdoApiResponse<string>>('deleteRssSources', data)

// webSocket
const debug = (
  /** @type {string} */ sourceUrl: string,
  /** @type {string} */ searchKey: string,
  /** @type {(data: string) => void} */ onReceive: (data: string) => void,
  /** @type {() => void} */ onFinish: () => void,
) => {
  const url = new URL(
    `${isBookSource ? 'bookSource' : 'rssSource'}Debug`,
    legado_webSocket_entry_point,
  )

  const socket = new WebSocket(url)
  socket.onerror = wsOnError
  socket.onopen = () => {
    socket.send(JSON.stringify({ tag: sourceUrl, key: searchKey }))
  }
  socket.onmessage = event => {
    onReceive(event.data)
    wsOnMessage?.call(socket, event)
  }

  socket.onclose = () => {
    onFinish()
  }
}

/**
 * 从阅读获取需要特定处理的书籍封面
 * @param {string} coverUrl
 */
const getProxyCoverUrl = (coverUrl: string) => {
  if (coverUrl.startsWith(legado_http_entry_point)) return coverUrl
  return new URL(
    'cover?path=' + encodeURIComponent(coverUrl),
    legado_http_entry_point,
  ).toString()
}

const getBookCoverUrl = (bookUrl: string) =>
  new URL(
    'getBookCover?url=' + encodeURIComponent(bookUrl),
    legado_http_entry_point,
  ).toString()
/**
 * 从阅读获取需要特定处理的图片
 * @param {string} bookUrl
 * @param {string} src
 * @param {number|`${number}`} width
 */
const getProxyImageUrl = (
  bookUrl: string,
  src: string,
  width: number | `${number}`,
) => {
  if (src.startsWith(legado_http_entry_point)) return src
  return new URL(
    'image?path=' +
      encodeURIComponent(src) +
      '&url=' +
      encodeURIComponent(bookUrl) +
      '&width=' +
      width,
    legado_http_entry_point,
  ).toString()
}

export default {
  getReadProgress,
  saveReadProgress,
  getReadConfig,
  saveReadConfig,
  saveBookProgress,
  saveBookProgressWithBeacon,
  getBookShelf,
  getChapterList,
  getBookContent,
  deleteBook,

  getSources,
  saveSources,
  saveSource,
  deleteSource,
  debug,

  getProxyCoverUrl,
  getBookCoverUrl,
  getProxyImageUrl,
}
