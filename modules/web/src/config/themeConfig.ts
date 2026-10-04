const settings = {
  themes: Array.from({ length: 7 }, () => ({
    body: 'var(--page)', content: 'var(--reader)', popup: 'var(--surface)',
  })),
  // Latin fonts first: PingFang SC can render Vietnamese marks at incorrect positions.
  // Keep preset indices and CJK fallbacks; custom font choices remain unchanged.
  fonts: [
    'Arial, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Microsoft YaHei, PingFangSC-Regular, sans-serif',

    'Georgia, Times New Roman, Simsun, serif',

    'Times New Roman, Kaiti, serif',
  ],
}
export default settings
