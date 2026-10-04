import { formatDate } from '@vueuse/shared'
export const isNullOrBlank = (string: string | null | undefined | number) =>
  string == null ||
  (string as string).length === 0 ||
  /^\s+$/.test(string as string)

export const isLegadoUrl = (/** @type {string} */ url: string) =>
  /,\s*\{/.test(url) ||
  !(
    url.startsWith('http') ||
    url.startsWith('data:') ||
    url.startsWith('blob:')
  )

/**
 * 验证输入的URL是否符合阅读后端地址规则
 * @param allowedProtocols 允许的协议，默认`["https:", "http:"]`
 */
export const validatorHttpUrl = (
  http_url: string | URL,
  allowedProtocols: string[] = ['https:', 'http:'],
) => {
  try {
    const url = new URL(http_url)
    const { protocol } = url
    if (!allowedProtocols.includes(protocol))
      throw new Error(
        `Expected protocol ${allowedProtocols.join('/')}, but ${protocol}`,
      )
    return true
  } catch {
    return false
  }
}

export const dateFormat = (t: number | null | undefined, now = Date.now()) => {
  if (typeof t !== 'number' || !Number.isFinite(t) || t <= 0 ||
      !Number.isFinite(new Date(t).getTime())) return 'Chưa cập nhật'
  if (t > now) return formatDate(new Date(t), 'DD/MM/YYYY HH:mm')
  const offset = Math.floor((now - t) / 1000)
  let str = ''

  if (offset <= 30) {
    str = 'Vừa xong'
  } else if (offset < 60) {
    str = offset + ' giây trước'
  } else if (offset < 3600) {
    str = Math.floor(offset / 60) + ' phút trước'
  } else if (offset < 86400) {
    str = Math.floor(offset / 3600) + ' giờ trước'
  } else if (offset < 2592000) {
    str = Math.floor(offset / 86400) + ' ngày trước'
  } else {
    str = formatDate(new Date(t), 'DD/MM/YYYY')
  }
  return str
}

/**
 * 懒加载正则
 */
export const lazyRegex = (pattern: string, flags?: string) => {
  let instance: RegExp | null = null
  return () => {
    if (!instance) {
      instance = new RegExp(pattern, flags)
    }
    return instance
  }
}
