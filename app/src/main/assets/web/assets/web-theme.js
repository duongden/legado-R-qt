(() => {
  const base = new URL('../', document.currentScript.src);
  const ui = window.LegadoWebUi;
  const media = matchMedia('(prefers-color-scheme: dark)');
  const options = { system: 'Theo hệ thống', light: 'Sáng', dark: 'Tối', oled: 'Đen OLED', paper: 'Giấy ngà', gray: 'Xám dịu', eink: 'E-ink' };
  let chosen = localStorage.getItem('legado_ui_theme_v1') || 'system';
  function apply() {
    const mode = chosen === 'system' ? (media.matches ? 'dark' : 'light') : chosen;
    document.documentElement.dataset.theme = mode;
    document.documentElement.style.colorScheme = ['dark','oled'].includes(mode) ? 'dark' : 'light';
    let palette = {};
    try { palette = JSON.parse(localStorage.getItem('legado_ui_palettes_v1') || '{}')[mode] || {}; } catch {}
    for (const key of ['page','surface','reader','ink','muted','accent']) {
      const value = palette[key];
      if (/^#[0-9a-f]{6}$/i.test(value)) document.documentElement.style.setProperty('--'+key,value);
      else document.documentElement.style.removeProperty('--'+key);
    }
  }
  apply(); media.addEventListener('change', apply);
  addEventListener('storage', () => { chosen = localStorage.getItem('legado_ui_theme_v1') || 'system'; apply(); });
  addEventListener('DOMContentLoaded', () => {
    const slot = document.getElementById('web-service-header');
    if (!slot || !ui) return;
    const header = document.createElement('header'); header.className = 'service-header';
    const brand = document.createElement('a'); brand.className = 'service-brand'; brand.href = new URL('vue/index.html#/',base); brand.innerHTML = 'Legado<span>Web</span>';
    const nav = document.createElement('nav');
    const routes = [['Tủ sách','vue/index.html#/'],['Khám phá','vue/index.html#/discovery'],['Nguồn sách','vue/index.html#/bookSource'],['Nguồn RSS','vue/index.html#/rssSource'],['Tải và xuất','vue/index.html#/downloads'],['Gửi sách','uploadBook/index.html'],['Trợ giúp','help/index.html#appHelp']];
    for (const [label,path] of routes) {
      const a=document.createElement('a');a.href=new URL(path,base);a.dataset.i18n=label;a.textContent=ui.t(label);
      if ((path.startsWith('help/') && location.pathname.includes('/help/')) || (path.startsWith('uploadBook/') && location.pathname.includes('/uploadBook/'))) a.className='active';
      nav.append(a);
    }
    const details=document.createElement('details');details.className='standalone-settings';
    const summary=document.createElement('summary');summary.className='service-settings-button';summary.dataset.i18nLabel='Cài đặt';summary.innerHTML='<svg viewBox="0 0 20 20" aria-hidden="true"><path d="M3 5h14M3 10h14M3 15h14M7 3v4m6 1v4m-6 1v4" /></svg><span data-i18n="Cài đặt"></span>';
    const panel=document.createElement('div');panel.className='service-settings';
    const langLabel=document.createElement('label');langLabel.className='setting-inline';langLabel.innerHTML='<span data-i18n="Ngôn ngữ"></span>';
    const lang=document.createElement('select');lang.setAttribute('aria-label',ui.t('Ngôn ngữ'));
    for(const [value,text] of [['vi','Tiếng Việt'],['zh','中文'],['en','English']]){const o=document.createElement('option');o.value=value;o.textContent=text;lang.append(o);}
    lang.value=ui.language;lang.onchange=()=>ui.setLanguage(lang.value);langLabel.append(lang);
    const themeLabel=document.createElement('label');themeLabel.className='setting-inline';themeLabel.innerHTML='<span data-i18n="Giao diện"></span>';
    const theme=document.createElement('select');theme.setAttribute('aria-label',ui.t('Giao diện'));
    for(const[value,text]of Object.entries(options)){const o=document.createElement('option');o.value=value;o.dataset.i18n=text;o.textContent=ui.t(text);theme.append(o);}
    theme.value=chosen;theme.onchange=()=>{chosen=theme.value;localStorage.setItem('legado_ui_theme_v1',chosen);apply();};themeLabel.append(theme);
    panel.append(langLabel,themeLabel);details.append(summary,panel);header.append(brand,nav,details);slot.replaceWith(header);ui.apply();
    addEventListener('legado-language-change',()=>{lang.value=ui.language;ui.apply();});
    document.addEventListener('click',e=>{if(!details.contains(e.target))details.open=false;});
    document.addEventListener('keydown',e=>{if(e.key==='Escape')details.open=false;});
  });
})();
