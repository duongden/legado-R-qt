<template>
  <div class="books-wrapper">
    <div class="wrapper" :class="layout">
      <article
        class="book"
        v-for="book in books"
        :key="book.bookUrl"
      >
        <div class="book-main"
        role="button"
        tabindex="0"
        :aria-label="`${t('Đọc')} ${book.name}`"
        @click="handleClick(book)"
        @keydown.enter.prevent="handleClick(book)"
        @keydown.space.prevent="handleClick(book)"
      >
        <div class="cover-img">
          <img
            class="cover"
            :src="getCover(book)"
            :key="book.coverUrl"
            @error.once="proxyImage($event, book)"
            alt=""
            loading="lazy"
          />
        </div>
        <div class="info">
          <div class="name">{{ book.name }}</div>
          <div class="sub">
            <div class="author">
              {{ book.author }}
            </div>
            <div class="update-info">
              <div class="dot">•</div>
              <div class="size">{{ book.totalChapterNum }} {{ t("chương") }}</div>
              <div class="dot">•</div>
              <div class="date">
                {{ dateFormat(book.lastCheckTime) }}
              </div>
            </div>
          </div>
          <div class="dur-chapter">
{{ t("Đã đọc") }}: {{ book.durChapterTitle }}
          </div>
          <div class="last-chapter">{{ t("Mới nhất") }}: {{ book.latestChapterTitle }}</div>
        </div></div>
        <button v-if="canManage && (book.type & (8 | 64)) !== 0" type="button" class="book-transfer-action" @click="emit('manage', book)">{{ t('Tải / Xuất') }}</button>
      </article>
    </div>
  </div>
</template>
<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import type { Book } from '@/book'
import { dateFormat } from '../utils/utils'
import API from '@api'
defineProps<{
  books: Book[]
  layout?: 'grid' | 'list'
  canManage?: boolean
}>()

const emit = defineEmits(['bookClick', 'manage'])
const handleClick = (book: Book) => emit('bookClick', book)
const getCover = ({ bookUrl }: Book) => API.getBookCoverUrl(bookUrl)
const proxyImage = (evt: Event, book: Book) => {
  const target = evt.target as HTMLImageElement
  target.onerror = () => { target.style.visibility = 'hidden' }
  target.src = API.getBookCoverUrl(book.bookUrl)
}

</script>

<style scoped>
.wrapper { display:grid; gap:16px; }
.wrapper.grid { grid-template-columns:repeat(auto-fill,minmax(min(100%,160px),1fr)); }
.wrapper.list { grid-template-columns:1fr; }
.grid .book { flex-direction:column; padding:14px; gap:14px; }
.grid .cover-img { flex:none; width:100%; height:auto; aspect-ratio:2/3; }
.grid .name { font-size:14px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.grid .update-info,.grid .last-chapter { display:none; }
.list .cover-img { flex-basis:58px; height:82px; }
.list .book { padding:16px; }
.book-main { display:flex; align-items:flex-start; gap:18px; flex:1; min-width:0; max-width:100%; overflow:hidden; cursor:pointer; }
.grid .book-main { flex-direction:column; align-items:stretch; width:100%; gap:14px; }
.grid .info { width:100%; }
.book-transfer-action { flex-shrink:0; border:var(--border-ui); border-radius:8px; color:var(--ink); background:var(--surface); padding:8px 10px; font:12px var(--font-ui); cursor:pointer; }
.list .book { align-items:center; }
.book { display:flex; gap:18px; padding:22px; min-width:0; max-width:100%; overflow:hidden; border:var(--border-ui); border-radius:var(--radius-card); background:var(--surface); cursor:pointer; }
.book:hover { background:var(--hover); }
.cover-img { flex:0 0 72px; height:102px; border-radius:var(--radius-sm); background:var(--accent-soft); overflow:hidden; }
.cover { width:100%; height:100%; object-fit:cover; }
.info { flex:1; min-width:0; max-width:100%; overflow:hidden; display:flex; flex-direction:column; gap:8px; }
.name { font-size:17px; line-height:1.5; font-weight:700; overflow-wrap:anywhere; color:var(--ink); }
.sub { font-size:12px; line-height:1.6; color:var(--muted); }
.author { overflow-wrap:anywhere; }
.update-info { display:flex; flex-wrap:wrap; gap:6px; }
.dot:first-child { display:none; }
.date,.size { white-space:nowrap; }
.dur-chapter,.last-chapter { font-size:12px; color:var(--muted); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
@media(max-width:750px) { .book { padding:16px; gap:14px; } .cover-img { flex-basis:58px; height:84px; } }
</style>

<style scoped>
.grid .name { min-height:42px; }
.grid .author { min-height:38px; height:38px; line-height:19px; display:-webkit-box; -webkit-box-orient:vertical; -webkit-line-clamp:2; overflow:hidden; }
.grid .book-transfer-action { width:100%; margin-top:auto; }
</style>
