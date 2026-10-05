<template>
  <div class="books-wrapper">
    <div class="wrapper">
      <div
        class="book"
        v-for="book in books"
        :key="book.bookUrl"
        role="button"
        tabindex="0"
        :aria-label="`Đọc ${book.name}`"
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
              <div class="size">{{ book.totalChapterNum }} chương</div>
              <div class="dot">•</div>
              <div class="date">
                {{ dateFormat(book.lastCheckTime) }}
              </div>
            </div>
          </div>
          <div class="dur-chapter">
            Đã đọc: {{ book.durChapterTitle }}
          </div>
          <div class="last-chapter">Mới nhất: {{ book.latestChapterTitle }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { Book } from '@/book'
import { dateFormat, isLegadoUrl } from '../utils/utils'
import API from '@api'
defineProps<{
  books: Book[]
}>()

const emit = defineEmits(['bookClick'])
const handleClick = (book: Book) => emit('bookClick', book)
const getCover = ({ bookUrl, coverUrl }: Book) => {
  if (coverUrl === undefined || isLegadoUrl(coverUrl)) {
    return API.getBookCoverUrl(bookUrl)
  }
  return coverUrl
}
const proxyImage = (evt: Event, book: Book) => {
  const target = evt.target as HTMLImageElement
  target.onerror = () => { target.style.visibility = 'hidden' }
  target.src = API.getBookCoverUrl(book.bookUrl)
}

</script>

<style scoped>
.wrapper { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,360px),1fr)); gap:16px; }
.book { display:flex; gap:18px; padding:22px; min-width:0; border:var(--border-ui); border-radius:var(--radius-card); background:var(--surface); cursor:pointer; }
.book:hover { background:var(--hover); }
.cover-img { flex:0 0 72px; height:102px; border-radius:var(--radius-sm); background:var(--accent-soft); overflow:hidden; }
.cover { width:100%; height:100%; object-fit:cover; }
.info { flex:1; min-width:0; display:flex; flex-direction:column; gap:8px; }
.name { font-size:17px; line-height:1.5; font-weight:700; overflow-wrap:anywhere; color:var(--ink); }
.sub { font-size:12px; line-height:1.6; color:var(--muted); }
.author { overflow-wrap:anywhere; }
.update-info { display:flex; flex-wrap:wrap; gap:6px; }
.dot:first-child { display:none; }
.date,.size { white-space:nowrap; }
.dur-chapter,.last-chapter { font-size:12px; color:var(--muted); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
@media(max-width:750px) { .book { padding:16px; gap:14px; } .cover-img { flex-basis:58px; height:84px; } }
</style>
