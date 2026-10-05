<template>
  <header class="editor-header">
    <a href="#/">Tủ sách</a
    ><strong>{{ isBookSource ? 'Nguồn sách' : 'Nguồn RSS' }}</strong
    ><el-popover trigger="click" :width="340"
      ><template #reference><el-button>Giao diện</el-button></template
      ><theme-picker
    /></el-popover>
  </header>
  <div class="editor">
    <source-tab-form class="left" :config="config" />
    <tool-bar />
    <source-tab-tools class="right" />
  </div>
</template>
<script setup lang="ts">
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'

import type { SourceConfig } from '@/config/sourceConfig'

let config: SourceConfig
const isBookSource = ref<boolean>(/bookSource/i.test(location.href))
provide('isBookSource', isBookSource)
if (isBookSource.value) {
  config = bookSourceConfig as SourceConfig
  document.title = 'Quản lý nguồn sách'
} else {
  config = rssSourceConfig as SourceConfig
  document.title = 'Quản lý nguồn RSS'
}
</script>
<style lang="scss" scoped>
.editor {
  display: flex;
  height: calc(100vh - 64px);
  overflow: hidden;
  .left {
    flex: 1;
    min-width: 0;
    margin-left: 20px;
  }
  .right {
    flex: 1;
    min-width: 0;
    width: 360px;
    margin-right: 20px;
  }
}
.editor-header {
  height: 64px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 12px 20px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}
.editor-header a {
  color: var(--accent);
}
.editor-header strong {
  flex: 1;
}
@media (max-width: 900px) {
  .editor {
    display: flex;
    flex-direction: column;
    height: auto;
    overflow: visible;
    padding: 12px;
    gap: 16px;
  }
  .editor .left,
  .editor .right {
    width: 100%;
    margin: 0;
    flex: auto;
  }
}
</style>
