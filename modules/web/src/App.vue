<template>
  <el-config-provider :locale="elementLocale">
    <ServiceHeader />
    <router-view :key="translationEpoch"></router-view>
  </el-config-provider>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import '@/config/appearance'
import '@/assets/appearance.css'
import vi from 'element-plus/es/locale/lang/vi'
import en from 'element-plus/es/locale/lang/en'
import zh from 'element-plus/es/locale/lang/zh-cn'
import { computed } from 'vue'
import { uiLanguage } from '@/config/uiLanguage'
import ServiceHeader from '@/components/ServiceHeader.vue'
const elementLocale = computed(() => ({ vi, en, zh })[uiLanguage.value])
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
