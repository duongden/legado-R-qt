<template>
  <section class="settings-wrapper" aria-label="Cài đặt đọc">
    <header class="panel-heading"><div><span class="eyebrow">CÁ NHÂN HÓA</span><h2>Cài đặt</h2></div><button type="button" class="close-panel" aria-label="Đóng cài đặt" @click="store.setReadSettingsVisible(false)">×</button></header>
    <div class="setting-list">
      <section class="settings-section"><h3>Giao diện</h3><ThemePicker /></section>
      <section class="settings-section"><h3>Nội dung</h3><TranslationToggle /></section>
      <section class="settings-section"><h3>Phông chữ</h3>
        <div class="font-options"><button v-for="(font,index) in fonts" :key="font" type="button" :aria-pressed="selectedFont === index" :class="{ selected:selectedFont === index }" @click="setFont(index)">{{ font }}</button></div>
        <label class="custom-font-label" for="reader-custom-font">Phông tùy chỉnh</label>
        <div class="custom-font-row"><input id="reader-custom-font" class="font-item-input" v-model="customFontName" placeholder="Tên phông trên thiết bị" /><button type="button" @click="setCustomFont">Lưu</button></div>
        <button type="button" class="text-action" @click="loadFontFromURL">Tải phông từ liên kết</button>
      </section>
      <section class="settings-section"><h3>Trình bày trang</h3>
        <div v-for="control in controls" :key="control.label" class="setting-row"><span>{{ control.label }}</span><div class="stepper"><button type="button" :aria-label="`Giảm ${control.label.toLowerCase()}`" @click="control.less">−</button><output>{{ control.value }}</output><button type="button" :aria-label="`Tăng ${control.label.toLowerCase()}`" @click="control.more">+</button></div></div>
        <label class="setting-row"><span>Tải chương liên tục</span><input type="checkbox" :checked="infiniteLoading" @change="setInfiniteLoading(!infiniteLoading)" /></label>
      </section>
    </div>
  </section>
</template>
<script setup lang="ts">
import TranslationToggle from '@/components/TranslationToggle.vue'
import ThemePicker from '@/components/ThemePicker.vue'
import '../assets/fonts/iconfont.css'
import API from '@api'
import { useDebounceFn } from '@vueuse/shared'

const store = useBookStore()
const saveConfigDebounce = useDebounceFn(
  () => API.saveReadConfig(store.config),
  500,
)
//阅读界面设置改变时保存同步配置
watch(
  () => store.config,
  () => {
    saveConfigDebounce()
  },
  {
    deep: 2, //深度为2
  },
)

//预置字体
const fonts = ['Sans-serif', 'Serif', 'KaiTi']
const setFont = (font: number) => {
  store.config.font = font
}
const selectedFont = computed(() => {
  return store.config.font
})
//自定义字体
const customFontName = ref(store.config.customFontName)
const setCustomFont = () => {
  store.config.font = -1
  store.config.customFontName = customFontName.value
}
// 加载网络字体
const loadFontFromURL = () => {
  ElMessageBox.prompt('Nhập liên kết tải phông chữ', 'Thông báo', {
    confirmButtonText: 'Xác nhận',
    cancelButtonText: 'Hủy',
    inputPattern: /^https?:.+$/,
    inputErrorMessage: 'Định dạng URL không hợp lệ',
    beforeClose: (action, instance, done) => {
      if (action === 'confirm') {
        instance.confirmButtonLoading = true
        instance.confirmButtonText = 'Đang tải…'
        const url = instance.inputValue
        if (typeof FontFace !== 'function') {
          ElMessage.error('Trình duyệt không hỗ trợ FontFace')
          return done()
        }
        const fontface = new FontFace(customFontName.value, `url("${url}")`)
        document.fonts.add(fontface)
        fontface
          .load()
          .then(function () {
            instance.confirmButtonLoading = false
            ElMessage.info('Đã tải phông chữ!')
            setCustomFont()
            done()
          })
          .catch(function (error) {
            instance.confirmButtonLoading = false
            instance.confirmButtonText = 'Xác nhận'
            ElMessage.error('Tải thất bại, hãy kiểm tra URL')
            throw error
          })
      } else {
        done()
      }
    },
  })
}

//Cỡ chữ
const fontSize = computed(() => {
  return store.config.fontSize
})
const moreFontSize = () => {
  if (store.config.fontSize < 48) store.config.fontSize += 2
}
const lessFontSize = () => {
  if (store.config.fontSize > 12) store.config.fontSize -= 2
}

//字 行 段落间距
const spacing = computed(() => {
  return store.config.spacing
})
const lessLetterSpacing = () => {
  store.config.spacing.letter = Math.max(0, store.config.spacing.letter - 0.01)
}
const moreLetterSpacing = () => {
  store.config.spacing.letter += 0.01
}
const lessLineSpacing = () => {
  store.config.spacing.line = Math.max(0.2, store.config.spacing.line - 0.1)
}
const moreLineSpacing = () => {
  store.config.spacing.line += 0.1
}
const lessParagraphSpacing = () => {
  store.config.spacing.paragraph = Math.max(0, store.config.spacing.paragraph - 0.1)
}
const moreParagraphSpacing = () => {
  store.config.spacing.paragraph += 0.1
}

//Chiều rộng trang
const readWidth = computed(() => {
  return store.config.readWidth
})
const moreReadWidth = () => {
  // 此时会截断页面
  if (store.config.readWidth + 160 + 2 * 68 > window.innerWidth) return
  store.config.readWidth += 160
}
const lessReadWidth = () => {
  if (store.config.readWidth > 640) store.config.readWidth -= 160
}

//Tốc độ lật trang
const jumpDuration = computed(() => {
  return store.config.jumpDuration
})
const moreJumpDuration = () => {
  store.config.jumpDuration += 100
}
const lessJumpDuration = () => {
  if (store.config.jumpDuration === 0) return
  store.config.jumpDuration -= 100
}

//Tải liên tục
const infiniteLoading = computed(() => {
  return store.config.infiniteLoading
})
const setInfiniteLoading = (loading: boolean) => {
  store.config.infiniteLoading = loading
}
const controls = computed(() => [
  { label: 'Cỡ chữ', value: `${fontSize.value} px`, less: lessFontSize, more: moreFontSize },
  { label: 'Giãn dòng', value: spacing.value.line.toFixed(1), less: lessLineSpacing, more: moreLineSpacing },
  { label: 'Giãn đoạn', value: spacing.value.paragraph.toFixed(1), less: lessParagraphSpacing, more: moreParagraphSpacing },
  { label: 'Giãn chữ', value: spacing.value.letter.toFixed(2), less: lessLetterSpacing, more: moreLetterSpacing },
  ...(!store.miniInterface ? [{ label: 'Chiều rộng trang', value: `${readWidth.value} px`, less: lessReadWidth, more: moreReadWidth }] : []),
  { label: 'Tốc độ lật trang', value: `${jumpDuration.value} ms`, less: lessJumpDuration, more: moreJumpDuration },
])
</script>
<style scoped>
.settings-wrapper { color:var(--ink); background:var(--surface); font:14px/1.5 var(--font-ui); }
.panel-heading { display:flex; align-items:center; justify-content:space-between; padding:22px 24px 16px; border-bottom:1px solid var(--line); }
h2 { margin:4px 0 0; font-size:22px; letter-spacing:-.5px; }.eyebrow { color:var(--muted); font-size:10px; letter-spacing:1.8px; font-weight:700; }
.close-panel { width:36px; height:36px; border:var(--border-ui); border-radius:50%; background:var(--surface); color:var(--ink); cursor:pointer; font-size:24px; }
.setting-list { padding:0 24px 24px; max-height:calc(76dvh - 105px); overflow:auto; overscroll-behavior:contain; }
.settings-section { padding:20px 0; border-bottom:1px solid var(--line); }.settings-section:last-child { border:0; padding-bottom:0; }
h3 { font-size:13px; margin:0 0 12px; color:var(--muted); font-weight:600; }
.font-options { display:flex; gap:8px; }.font-options button { flex:1; }.font-options button, .custom-font-row button { border:var(--border-ui); background:var(--surface); color:var(--ink); padding:9px 10px; border-radius:var(--radius-control); cursor:pointer; }
.font-options .selected { border-color:var(--accent); background:var(--accent-soft); color:var(--accent); }
.custom-font-label { display:block; color:var(--muted); font-size:12px; margin:16px 0 6px; }.custom-font-row { display:flex; gap:8px; }.custom-font-row input { width:0; flex:1; min-width:0; background:var(--page); border:var(--border-ui); color:var(--ink); border-radius:var(--radius-control); padding:10px; }
.text-action { background:none; border:0; color:var(--accent); cursor:pointer; padding:10px 0 0; font-size:12px; }
.setting-row { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-top:12px; }.stepper { display:flex; align-items:center; border:var(--border-ui); border-radius:var(--radius-control); overflow:hidden; flex-shrink:0; }.stepper button { border:0; background:var(--hover); color:var(--ink); width:32px; min-height:36px; cursor:pointer; font-size:18px; }.stepper output { min-width:60px; text-align:center; font-size:12px; font-variant-numeric:tabular-nums; }
@media(max-width:380px) { .panel-heading { padding:16px; }.setting-list { padding:0 16px 16px; }.setting-row { font-size:12px; } }
</style>
