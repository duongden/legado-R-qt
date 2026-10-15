<template>
  <header class="service-header">
    <router-link class="service-brand" to="/">Legado<span>Web</span></router-link>
    <nav :aria-label="t('Thư viện')">
      <router-link to="/" :class="{ active: route.name === 'shelf' }">{{ t('Tủ sách') }}</router-link>
      <router-link v-if="!isRelay" to="/discovery" :class="{ active: String(route.name).startsWith('discovery') }">{{ t('Khám phá') }}</router-link>
      <router-link v-if="!isRelay" to="/bookSource">{{ t('Nguồn sách') }}</router-link>
      <router-link v-if="!isRelay" to="/rssSource">RSS</router-link>
      <router-link v-if="!isRelay" to="/downloads">{{ t('Tải và xuất') }}</router-link>
      <a v-if="!isRelay" href="../uploadBook/index.html">{{ t('Gửi sách') }}</a>
      <a v-if="!isRelay" href="../help/index.html#appHelp">{{ t('Trợ giúp') }}</a>
    </nav>
    <el-popover v-model:visible="settingsVisible" placement="bottom-end" :width="300" trigger="click" popper-class="service-settings-popover">
      <template #reference><button type="button" class="service-settings-button" :aria-label="t('Cài đặt')">
        <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M3 5h14M3 10h14M3 15h14M7 3v4m6 1v4m-6 1v4" /></svg><span>{{ t('Cài đặt') }}</span></button></template>
      <div class="service-settings"><h2>{{ t('Cài đặt') }}</h2>
        <label class="setting-inline">{{ t('Ngôn ngữ') }}<CompactSelect :model-value="uiLanguage" :options="[{value:'vi',label:'Tiếng Việt'},{value:'zh',label:'中文'},{value:'en',label:'English'}]" :label="t('Ngôn ngữ')" @change="value => setUiLanguage(value as 'vi' | 'zh' | 'en')" /></label>
        <ThemePicker /><TranslationToggle /><details v-if="!isRelay" class="download-preferences"><summary>{{ t('Cài đặt tải') }}</summary><DownloadSettings /></details>
      </div>
    </el-popover>
  </header>
</template>
<script setup lang="ts">
import { getRelayBootstrap } from '@/api/relay'
import CompactSelect from './CompactSelect.vue'
import { t, uiLanguage, setUiLanguage } from '@/config/uiLanguage'
import ThemePicker from './ThemePicker.vue'
import DownloadSettings from './DownloadSettings.vue'
import TranslationToggle from './TranslationToggle.vue'
const route = useRoute()
const isRelay = !!getRelayBootstrap()
const settingsVisible = ref(false)
watch(() => route.fullPath, () => { settingsVisible.value = false })
</script>
