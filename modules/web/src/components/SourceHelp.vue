<script setup lang="ts">
import { Link } from '@element-plus/icons-vue'
const isBookSource = inject<Ref<boolean>>('isBookSource', ref(true))
const sourceTitle = computed(() => {
  return isBookSource.value ? 'Nguồn sách' : 'Nguồn RSS'
})
const sourcePath = computed(() => {
  return isBookSource.value ? 'ruleHelp' : 'rssRuleHelp'
})
</script>
<template>
  <el-link :icon="Link" href="/help/#appHelp" target="_blank"
    >Tài liệu ứng dụng</el-link
  ><br />
  <el-link :icon="Link" :href="`/help/#${sourcePath}`" target="_blank"
    >{{ sourceTitle }}: hướng dẫn tạo</el-link
  ><br />
  <el-link :icon="Link" href="/help/#jsHelp" target="_blank"
    >Biến và hàm JavaScript</el-link
  ><br />
  <el-link :icon="Link" href="/help/#xpathHelp" target="_blank"
    >Hướng dẫn cú pháp XPath</el-link
  ><br />
  <el-link :icon="Link" href="/help/#regexHelp" target="_blank"
    >Hướng dẫn biểu thức chính quy</el-link
  ><br />
  <el-link :icon="Link" href="/help/#txtTocRuleHelp" target="_blank"
    >Biểu thức mục lục TXT</el-link
  ><br />
  <el-link :icon="Link" href="/help/#debugHelp" target="_blank"
    >Hướng dẫn gỡ lỗi nguồn sách</el-link
  ><br />
  <el-link :icon="Link" href="/help/#httpTTSHelp" target="_blank"
    >Quy tắc đọc trực tuyến</el-link
  ><br />
  <el-link :icon="Link" href="/help/#webDavBookHelp" target="_blank">
    Hướng dẫn đọc sách WebDAV</el-link
  ><br />
  <el-link :icon="Link" href="/help/#webDavHelp" target="_blank">
    Hướng dẫn sao lưu WebDAV</el-link
  ><br />
  <el-link :icon="Link" href="https://regexr-cn.com/" target="_blank"
    >Công cụ kiểm tra biểu thức chính quy</el-link
  ><br />
  <div style="margin-top: 20px">
    <span
      ><el-text
        ><code>^$()[]{}.?+*|</code> Đây là các ký tự đặc biệt trong regex Java, cần escape để khớp ký tự gốc</el-text
      ></span
    ><br />
    <span
      ><el-text><code>(?s)</code> cho phép khớp xuyên dòng</el-text></span
    ><br />
    <span
      ><el-text><code>(?m)</code> cho phép khớp theo từng dòng</el-text></span
    ><br />
    <span
      ><el-text><code>(?i)</code> bỏ qua phân biệt chữ hoa và chữ thường</el-text></span
    ><br />
  </div>
</template>

<style lang="scss" scoped>
.el-link {
  padding: 4px;
}
.el-text {
  padding-top: 20px;
}
</style>
