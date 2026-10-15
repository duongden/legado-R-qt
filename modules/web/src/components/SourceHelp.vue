<script setup lang="ts">
import { t } from '@/config/uiLanguage'
const topics = [
  ['appHelp', t('Tài liệu ứng dụng')], ['ruleHelp', t('Nguồn sách')], ['rssRuleHelp', t('Nguồn RSS')],
  ['jsHelp', t('Biến và hàm JavaScript')], ['xpathHelp', t('Hướng dẫn cú pháp XPath')],
  ['regexHelp', t('Hướng dẫn biểu thức chính quy')], ['txtTocRuleHelp', t('Biểu thức mục lục TXT')],
  ['debugHelp', t('Hướng dẫn gỡ lỗi nguồn sách')], ['httpTTSHelp', t('Quy tắc đọc trực tuyến')],
  ['webDavBookHelp', t('Hướng dẫn đọc sách WebDAV')], ['webDavHelp', t('Hướng dẫn sao lưu WebDAV')],
]
</script>
<template>
  <nav class="source-help" :aria-label="t('Trợ giúp')">
    <a v-for="[id, title] in topics" :key="id" :href="`../help/index.html#${id}`" target="_blank" rel="noopener">{{ t(title) }} <span aria-hidden="true">↗</span></a>
  </nav>
</template>
<style scoped>
.source-help { display:grid; gap:8px; padding:16px 0; }
a { display:flex; justify-content:space-between; gap:12px; padding:12px; border:var(--border-ui); border-radius:8px; text-decoration:none; overflow-wrap:anywhere; }
a:hover { background:var(--hover); }
</style>
