<template>
  <main class="source-page">
    <tool-bar />
    <div class="editor">
      <source-tab-form class="left" :config="config" />
      <source-tab-tools class="right" />
    </div>
  </main>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'

import type { SourceConfig } from '@/config/sourceConfig'

const route = useRoute()
const isBookSource = computed(() => /bookSource/i.test(route.path))
provide('isBookSource', isBookSource)
const config = computed(() => (isBookSource.value ? bookSourceConfig : rssSourceConfig) as SourceConfig)
watchEffect(() => { document.title = isBookSource.value ? t('Quản lý nguồn sách') : t('Quản lý nguồn RSS') })
</script>
<style lang="scss" scoped>
.editor {
  display: flex;
  height: calc(100dvh - 130px);
  overflow: hidden;
  gap: 24px;
  padding-top: 16px;
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
