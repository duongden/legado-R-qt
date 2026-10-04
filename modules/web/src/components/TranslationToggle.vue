<template>
  <label>
    <input type="checkbox" v-model="translationEnabled" />
    Dịch Trung → Việt
  </label>
</template>

<script setup lang="ts">
import { translationEnabled } from '@/api/translation'
</script>
