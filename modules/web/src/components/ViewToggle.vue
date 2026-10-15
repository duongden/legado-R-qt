<template>
  <div class="view-toggle" role="group" :aria-label="t('Chế độ xem')">
    <button type="button" :aria-pressed="libraryLayout === 'grid'" @click="libraryLayout = 'grid'">
      <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M3 3h5v5H3zm9 0h5v5h-5zM3 12h5v5H3zm9 0h5v5h-5z" /></svg>{{ t('Lưới') }}</button>
    <button type="button" :aria-pressed="libraryLayout === 'list'" @click="libraryLayout = 'list'">
      <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M3 4h14v2H3zm0 5h14v2H3zm0 5h14v2H3z" /></svg>{{ t('Danh sách') }}</button>
  </div>
</template>
<script setup lang="ts">
import { libraryLayout } from '@/config/libraryLayout'
import { t } from '@/config/uiLanguage'
</script>
