require.config({baseUrl:'js',paths:{marked:'marked.min',markedHighlight:'marked-highlight.umd',highlight:'highlight.min'},shim:{marked:{exports:'marked'},markedHighlight:{exports:'markedHighlight'},highlight:{exports:'hljs'}}});
require(['marked','markedHighlight','highlight'], (marked, mdhl, hljs) => {
  marked.use(mdhl.markedHighlight({langPrefix:'hljs language-',highlight(code,lang){return hljs.highlight(code,{language:hljs.getLanguage(lang)?lang:'txt'}).value;}}));
  const ui=window.LegadoWebUi, viewer=document.getElementById('mdviewer'), nav=document.getElementById('help-topics'), search=document.getElementById('help-search');
  let topics=[],request=0;
  const current=()=>{try{return decodeURIComponent(location.hash.slice(1).trim()||'appHelp');}catch{return '';}};
  function renderNav(){
    nav.replaceChildren();
    const keyword=search.value.trim().toLocaleLowerCase();
    for(const topic of topics){
      const title=topic.title[ui.language];if(!title.toLocaleLowerCase().includes(keyword))continue;
      const a=document.createElement('a');a.href='#'+topic.id;a.textContent=title;
      if(topic.id===current())a.setAttribute('aria-current','page');nav.append(a);
    }
  }
  async function load(){
    const revision=++request;renderNav();viewer.textContent=ui.t('Đang tải…');
    const topic=topics.find(t=>t.id===current());
    if(!topic){viewer.textContent=ui.t('Không tải được tài liệu.');return;}
    document.title=topic.title[ui.language]+' · Legado';
    const url=new URL('md/'+ui.language+'/'+topic.id+'.md',location.href);
    try{
      const response=await fetch(url,{cache:'no-cache'});if(!response.ok)throw Error('document');const text=await response.text();
      if(revision!==request)return;
      viewer.innerHTML=marked.parse(text);
      viewer.querySelectorAll('script,iframe,object,embed').forEach(n=>n.remove());
      viewer.querySelectorAll('*').forEach(n=>{for(const attr of [...n.attributes]){if(attr.name.startsWith('on')||((attr.name==='href'||attr.name==='src')&&/^\s*javascript:/i.test(attr.value)))n.removeAttribute(attr.name);}});
    }catch{if(revision===request)viewer.textContent=ui.t('Không tải được tài liệu.');}
  }
  search.addEventListener('input',renderNav);addEventListener('hashchange',load);addEventListener('legado-language-change',()=>{renderNav();void load();});
  fetch('topics.json').then(r=>{if(!r.ok)throw Error('topics');return r.json();}).then(data=>{topics=data;void load();}).catch(()=>{viewer.textContent=ui.t('Không tải được tài liệu.');});
});
