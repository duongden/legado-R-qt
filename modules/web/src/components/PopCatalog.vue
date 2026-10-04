<template>
  <section class="cata-wrapper" aria-label="Mục lục">
    <header><div><small>{{ catalog.length }} CHƯƠNG</small><h2>Mục lục</h2></div><button aria-label="Đóng mục lục" @click="store.setPopCataVisible(false)">×</button></header>
    <input v-model="query" aria-label="Tìm chương" placeholder="Tìm tên chương…" />
    <p v-if="!virtualListdata.length" class="empty">Không tìm thấy chương.</p>
    <virtual-list v-else class="chapter-list" ref="virtualListRef" data-key="index" :data-sources="virtualListdata" :data-component="CatalogItem" :estimate-size="48" :extra-props="{ gotoChapter, currentChapterIndex }" />
  </section>
</template>
<script setup lang="ts">
import VirtualList from 'vue3-virtual-scroll-list'
import CatalogItem from './CatalogItem.vue'
import type { BookChapter } from '@/book'
const store = useBookStore()
const { catalog, popCataVisible } = storeToRefs(store)
const query = ref('')
const virtualListdata = computed(() => catalog.value.filter(c => c.title.toLocaleLowerCase('vi').includes(query.value.toLocaleLowerCase('vi'))))
const virtualListRef = ref()
const currentChapterIndex = computed({
  get: () => store.readingBook.chapterIndex,
  set: value => (store.readingBook.chapterIndex = value),
})
watch([popCataVisible, query, currentChapterIndex], async () => {
  if (!popCataVisible.value) return
  await nextTick()
  const index = virtualListdata.value.findIndex(c => c.index === currentChapterIndex.value)
  virtualListRef.value?.scrollToIndex(Math.max(0, index))
})
// 点击加载对应章节内容
const emit = defineEmits(['getContent'])
const gotoChapter = (chapter: BookChapter) => {
  const chapterIndex = catalog.value.indexOf(chapter)
  currentChapterIndex.value = chapterIndex
  store.setPopCataVisible(false)
  store.setContentLoading(true)
  store.saveBookProgress()
  emit('getContent', chapterIndex)
}
</script>
<style scoped>
.cata-wrapper { padding:22px; color:var(--ink); background:var(--surface); border-radius:18px; }
header { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; }
small { color:var(--muted); font-size:10px; letter-spacing:2px; }
h2 { margin:6px 0 0; font-size:22px; }
header button { border:0; background:var(--hover); color:var(--ink); border-radius:50%; width:36px; height:36px; font-size:24px; cursor:pointer; }
input { box-sizing:border-box; width:100%; padding:12px; border:1px solid var(--line); border-radius:10px; background:var(--page); color:var(--ink); margin-bottom:14px; font:inherit; }
.chapter-list { height:min(360px,45dvh); overflow:auto; }
.empty { color:var(--muted); }
</style>
