<template>
  <label>
    <input type="checkbox" v-model="translationEnabled" />
    {{ t('Dịch Trung → Việt') }}
  </label>
</template>

<script setup lang="ts">
import { translationEnabled } from '@/api/translation'
import { t } from '@/config/uiLanguage'
</script>
