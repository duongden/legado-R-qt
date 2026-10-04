<template>
  <el-config-provider :locale="vi">
    <router-view :key="translationEpoch"></router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import '@/config/appearance'
import '@/assets/appearance.css'
import vi from 'element-plus/es/locale/lang/vi'
import { translationEnabled, translationEpoch } from '@/api/translation'
import { useBookStore } from '@/store'

watch(translationEnabled, () => {
  const store = useBookStore()
  store.shelf = []
  store.catalog = []
  store.readingBook.chapterPos = 0
  sessionStorage.setItem('chapterPos', '0')
})
</script>
