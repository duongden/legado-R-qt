import { t } from '@/config/uiLanguage'
import ajax from './axios'
import { legado_http_entry_point, type LeagdoApiResponse } from './api'
import { translationQuery } from './translation'

export type ExploreSource = { id: string; name: string; group?: string; canSearch: boolean; type: number }
export type ExploreCategory = { id?: string; title: string; available: boolean; interactive: boolean }
export type ExploreReader = {
  bookUrl: string; name: string; author: string; durChapterIndex: number; durChapterPos: number; type: number
}
export type ExploreBook = {
  id: string; name: string; author: string; intro?: string; kind?: string; wordCount?: string
  latestChapterTitle?: string; sourceName: string; type: number; hasCover: boolean; reader?: ExploreReader
}
export type ExplorePage = { books: ExploreBook[]; page: number; hasMore: boolean }
export type ExploreAdded = { reader: ExploreReader; added: boolean }

async function get<T>(path: string, params: Record<string, string | number> = {}, signal?: AbortSignal): Promise<T> {
  const { data } = await ajax.get<LeagdoApiResponse<T>>(path, {
    params: { ...params, translate: translationQuery() }, signal, timeout: 65000,
  })
  if (!data.isSuccess) throw new Error(t(data.errorMsg))
  return data.data
}
export const getExploreSources = (signal?: AbortSignal) => get<ExploreSource[]>('getExploreSources', {}, signal)
export const getExploreCategories = (sourceId: string, signal?: AbortSignal) =>
  get<ExploreCategory[]>('getExploreCategories', { sourceId }, signal)
export const getExploreBooks = (params: { sourceId: string; categoryId?: string; query?: string; page: number }, signal?: AbortSignal) =>
  get<ExplorePage>('getExploreBooks', {
    sourceId: params.sourceId, page: params.page,
    ...(params.categoryId ? { categoryId: params.categoryId } : {}),
    ...(params.query ? { query: params.query } : {}),
  }, signal)
export const getExploreInfo = (id: string, signal?: AbortSignal) => get<ExploreBook>('getExploreBookInfo', { id }, signal)
export const addExploreBook = async (id: string): Promise<ExploreAdded> => {
  const { data } = await ajax.post<LeagdoApiResponse<ExploreAdded>>('addExploreBook', { id }, { timeout: 65000 })
  if (!data.isSuccess) throw new Error(t(data.errorMsg))
  return data.data
}
export const getExploreCoverUrl = (id: string) => {
  const url = new URL('getExploreCover', legado_http_entry_point)
  url.searchParams.set('id', id)
  return url.toString()
}

export const openExploreReader = (reader: ExploreReader) => {
  sessionStorage.setItem('bookUrl', reader.bookUrl)
  sessionStorage.setItem('bookName', reader.name)
  sessionStorage.setItem('bookAuthor', reader.author)
  sessionStorage.setItem('chapterIndex', String(reader.durChapterIndex))
  sessionStorage.setItem('chapterPos', String(reader.durChapterPos))
  sessionStorage.setItem('isSeachBook', 'false')
}
