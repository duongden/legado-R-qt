import { ref } from 'vue'
import ajax from './axios'
import { legado_http_entry_point, type LeagdoApiResponse } from './api'
import { t } from '@/config/uiLanguage'
export type DownloadSettings = { preDownloadNum: number; chapterIntervalMs: number }
export type Transfer = { id: string; bookUrl: string; kind: string; state: string; completed: number; total: number; failed: number; cached: number; start: number; end: number; message?: string; filename?: string }
export type CacheSummary = { total: number; cached: number; current: number; type: number }
export const downloadSettings = ref<DownloadSettings>({ preDownloadNum: 2, chapterIntervalMs: 1000 })
const unwrap = <T>(data: LeagdoApiResponse<T>): T => { if (!data.isSuccess) throw Error(t(data.errorMsg)); return data.data }
const get = async <T>(path: string, params = {}) => unwrap((await ajax.get<LeagdoApiResponse<T>>(path, { params, timeout: 15000 })).data)
const post = async <T>(path: string, body: object) => unwrap((await ajax.post<LeagdoApiResponse<T>>(path, body, { timeout: 15000 })).data)
export const loadDownloadSettings = async () => { downloadSettings.value = await get<DownloadSettings>('getWebDownloadSettings'); return downloadSettings.value }
export const saveDownloadSettings = async (value: DownloadSettings) => { downloadSettings.value = await post<DownloadSettings>('saveWebDownloadSettings', value) }
export const getBookCache = (bookUrl: string) => get<CacheSummary>('getWebBookCache', { bookUrl })
export const getBookTransfers = (bookUrl: string) => get<Transfer[]>('getWebBookTransfers', { bookUrl })
export const startBookTransfer = (bookUrl: string, start: number, end: number, format?: 'txt' | 'epub') => post<Transfer>('startWebBookTransfer', { bookUrl, start, end, format })
export const preloadChapters = (bookUrl: string, index: number) => post<Transfer | false>('preloadWebChapters', { bookUrl, index })
export const cancelTransfer = (id: string) => post<Transfer>('cancelWebBookTransfer', { id })
export const transferFileUrl = (id: string) => { const url = new URL('downloadWebBookFile', legado_http_entry_point); url.searchParams.set('id', id); return url.toString() }
