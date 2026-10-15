import { ref, watch } from 'vue'
export const libraryLayout = ref<'grid' | 'list'>(localStorage.getItem('legado_library_view_v1') === 'list' ? 'list' : 'grid')
watch(libraryLayout, value => localStorage.setItem('legado_library_view_v1', value))
