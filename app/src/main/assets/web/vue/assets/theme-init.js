// Apply the local palette before Vue or authentication starts (CSP-safe external script).
(() => {
  const colors = { light: '#f5f7f8', dark: '#13191e', oled: '#000000', paper: '#ece4d5', gray: '#dfe3e5' }
  let mode = 'system'
  try { mode = localStorage.getItem('legado_ui_theme_v1') || mode } catch {}
  if (!(mode in colors)) mode = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  document.documentElement.dataset.theme = mode
  document.documentElement.style.backgroundColor = colors[mode]
  document.documentElement.style.colorScheme = mode === 'dark' || mode === 'oled' ? 'dark' : 'light'
})()
