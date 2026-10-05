<template>
  <el-input
    id="source-json"
    v-model="sourceString"
    type="textarea"
    placeholder="Dữ liệu JSON của nguồn; có thể nhập trực tiếp vào ứng dụng Legado"
    :rows="30"
    @change="update"
    style="margin-bottom: 4px"
  ></el-input>
</template>
<script setup lang="ts">
import { useSourceStore } from '@/store'

const store = useSourceStore()
const sourceString = ref('')
const update = async (string: string) => {
  try {
    store.changeEditTabSource(JSON.parse(string))
  } catch {
    ElMessage({
      message: 'Nguồn đã dán không đúng định dạng',
      type: 'error',
    })
  }
}

watchEffect(async () => {
  const source = store.editTabSource
  if (Object.keys(source).length > 0) {
    sourceString.value = JSON.stringify(source, null, 4)
  } else {
    sourceString.value = ''
  }
})
</script>
<style lang="scss" scoped>
:deep(.el-input) {
  width: 100%;
}
:deep(#source-json) {
  height: calc(100vh - 120px);
}
</style>
