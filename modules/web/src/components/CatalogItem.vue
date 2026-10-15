<template>
  <div class="wrapper">
    <button
      type="button"
      v-for="cata in catas"
      class="cata-text"
      :key="cata.url"
      :title="cata.title"
      :aria-current="isSelected(cata.index) ? 'true' : undefined"
      :class="{ selected: isSelected(cata.index) }"
      @click="gotoChapter(cata)"
    >
      <span class="chapter-title">{{ cata.title }}</span>
      <span v-if="cata.isVip || cata.isPay" class="chapter-access">{{ cata.isPay ? t('Đã mua') : 'VIP' }}</span>
    </button>
  </div>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import type { BookChapter } from '@/book'

const props = defineProps<{
  index: number
  source: BookChapter | { index: number; catas: BookChapter[] }
  gotoChapter: (chapter: BookChapter) => void
  currentChapterIndex: number
}>()

const isSelected = (idx: number) => {
  return idx == props.currentChapterIndex
}

// PC端 一个虚拟列表中有两个章节
const catas = computed(() => {
  const source = props.source
  if ('catas' in source) return source.catas
  return [props.source as BookChapter]
})
</script>

<style scoped>
.wrapper { display:flex; }
.cata-text { width:100%; height:48px; border:0; border-bottom:1px solid var(--line); padding:0 12px; text-align:left; background:transparent; color:var(--ink); font:inherit; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; cursor:pointer; }
.cata-text:hover { background:var(--hover); }
.selected { color:var(--accent); background:var(--accent-soft); font-weight:700; }
.cata-text { display:flex; align-items:center; gap:8px; }
.chapter-title { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; }
.chapter-access { flex-shrink:0; font-size:10px; color:var(--accent); background:var(--accent-soft); padding:2px 5px; border-radius:4px; }
</style>
