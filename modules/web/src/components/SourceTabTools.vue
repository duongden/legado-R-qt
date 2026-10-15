<template>
  <el-tabs v-model="current_tab">
    <el-tab-pane
      v-for="(tab, index) in tabData"
      :key="tab[0]"
      :name="tab[0]"
      :label="tab[1]"
    >
      <source-json v-if="index == 0" />
      <source-debug v-if="index == 1" />
      <source-list v-if="index == 2" />
      <source-help v-if="index == 3" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { t } from '@/config/uiLanguage'
import { useSourceStore } from '@/store'

const store = useSourceStore()

const current_tab = computed({
  get: () => store.currentTab,
  set: val => (store.currentTab = val),
})

const tabData = computed(() => [
  ['editTab', t('Sửa nguồn')],
  ['editDebug', t('Gỡ lỗi nguồn')],
  ['editList', t('Danh sách nguồn')],
  ['editHelp', t('Trợ giúp')],
])
</script>

<style lang="scss" scoped>
:deep(.el-tabs__header) {
  margin-bottom: 5px;
}
</style>
