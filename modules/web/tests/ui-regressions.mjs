// Reuse the Relay project's existing esbuild/Playwright installation:
// node tests/ui-regressions.mjs /path/to/legado-relay/node_modules [asset-directory]
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFile, mkdir } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
const web = resolve(fileURLToPath(new URL('..', import.meta.url)));
const require = createRequire(resolve(process.argv[2] || 'node_modules', '../package.json'));
const { chromium, expect } = require('@playwright/test');
const { build } = require('esbuild');
const assets = resolve(process.argv[3] || resolve(web, 'dist'));
const out = process.env.UI_TEST_OUTPUT || resolve(tmpdir(), 'legado-ui-regressions');
await mkdir(out, { recursive: true });
const result = await build({ entryPoints: [resolve(web, 'src/utils/utils.ts')], bundle: true, write: false, format: 'esm', platform: 'node' });
const { dateFormat } = await import('data:text/javascript;base64,' + Buffer.from(result.outputFiles[0].text).toString('base64'));
const now = new Date(2026, 9, 4, 12, 0).getTime();
for (const value of [undefined, null, NaN, Infinity, 0, -1, 9e18]) assert.equal(dateFormat(value, now), 'Chưa cập nhật');
for (const [seconds, expected] of [[0,'Vừa xong'],[30,'Vừa xong'],[31,'31 giây trước'],[59,'59 giây trước'],[60,'1 phút trước'],[3240,'54 phút trước'],[3599,'59 phút trước'],[3600,'1 giờ trước'],[86399,'23 giờ trước'],[86400,'1 ngày trước'],[2591999,'29 ngày trước']]) assert.equal(dateFormat(now-seconds*1000,now),expected);
assert.equal(dateFormat(now+60000,now),'04/10/2026 12:01');
assert.match(dateFormat(now-2592000000,now), /^\d{2}\/\d{2}\/\d{4}$/);
const browser=await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? {channel: process.env.PLAYWRIGHT_CHANNEL} : {}) });
const errors=[];
const paragraphs=['Kỷ dự bị nhỏ hạ — Phong mộ cẩm ở khuyên can thời điểm.', 'Nàng vội bưng kín trán. '.repeat(30), 'Tiếng Việt NFC: ộ ợ ỹ; NFD: '+ 'ộ ợ ỹ'.normalize('NFD'), '<ruby>Kỷ<rt>chú thích</rt></ruby> <span>hạ</span>', '<img src=x onerror="alert(1)"><script>window.unwanted=1</script>'];
const book={bookUrl:'https://fixture.invalid/book',name:'Truyện kiểm thử tiếng Việt với tên rất dài và nhiều dấu '.repeat(3),author:'Tác giả có tên rất dài không bị ép thành cột '.repeat(2),lastCheckTime:Date.now()-3240000,totalChapterNum:12345,durChapterIndex:0,durChapterPos:0,durChapterTime:now,durChapterTitle:'Chương đang đọc',latestChapterTitle:'Chương mới nhất'};
const prefix='/d/ABEiM0RVZneImaq7zN3u_w.AAAAAAAAAAAAAAAAAAAAAA/';
const origin='http://fixture.local';
let checks=0;
try {
 for(const width of [320,360,390,768,1280]) {
  const page=await browser.newPage({viewport:{width,height:900},reducedMotion:'reduce'});
  page.on('pageerror',e=>errors.push(e.message));
  const progress=[];
  const configWrites=[];
  await page.route('**/*',async route=>{
   const request=route.request(),url=new URL(request.url());
   if(url.origin!==origin) return route.fulfill({status:404,body:''});
   const tail=url.pathname.slice(prefix.length);
   let data;
   if(tail==='getBookshelf') data=[book];
   else if(tail==='getReadConfig') data=JSON.stringify({theme:0,font:0,fontSize:18,readWidth:800,infiniteLoading:false,spacing:{paragraph:1,line:0.8,letter:0}});
   else if(tail==='getChapterList') data=[0,1].map(index=>({index,title:'Chương '+(index+1),url:'chapter-'+index}));
   else if(tail==='getBookContentEx') data=paragraphs.join('\n');
   else if(tail==='saveBookProgress') {progress.push(request.postDataJSON());data='';}
   else if(tail==='saveReadConfig') {configWrites.push(request.postData());data='';}
   else if(tail==='getBookCover') return route.fulfill({status:404,body:''});
   if(data!==undefined) return route.fulfill({json:{isSuccess:true,data,errorMsg:''}});
   const file=tail||'index.html';
   if(file.includes('..')) throw new Error('Unsafe asset path');
   try {return route.fulfill({body:await readFile(resolve(assets,file)),contentType:({'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.woff':'font/woff','.ttf':'font/ttf','.png':'image/png'})[extname(file)]||'application/octet-stream'});}
   catch {return route.fulfill({status:404,body:''});}
  });
  await page.goto(origin+prefix);
  await expect(page.locator('.book')).toBeVisible();
  assert.equal(await page.title(),'Tủ sách');
  await expect(page.locator('.date')).toHaveText('54 phút trước');
  const geometry=await page.locator('.book').evaluate(e=>{const info=e.querySelector('.info'),author=e.querySelector('.author');return {overflow:document.documentElement.scrollWidth>innerWidth,infoClipped:info.scrollHeight>info.clientHeight+1,authorWidth:author.getBoundingClientRect().width};});
  assert.equal(geometry.overflow,false,`Shelf horizontal overflow at ${width}`);
  assert.equal(geometry.infoClipped,false,`Shelf clipped metadata at ${width}`);
  assert.ok(geometry.authorWidth>90,`Author too narrow at ${width}`);
  await expect(page.locator('html')).toHaveAttribute('data-theme','paper');
  const beforeThemeProgress=progress.length;
  const beforeConfigWrites=configWrites.length;
  for (const [mode,label] of [['light','Sáng'],['dark','Tối'],['oled','Đen OLED'],['paper','Giấy ngà'],['gray','Xám dịu']]) {
    await page.getByRole('button',{name:label,exact:true}).click();
    await expect(page.locator('html')).toHaveAttribute('data-theme',mode);
    assert.equal(await page.evaluate(()=>localStorage.getItem('legado_ui_theme_v1')),mode);
    const expectedBackground={light:'rgb(255, 255, 255)',dark:'rgb(28, 37, 44)',oled:'rgb(8, 8, 8)',paper:'rgb(255, 249, 239)',gray:'rgb(240, 242, 243)'};
    await expect(page.locator('.book')).toHaveCSS('background-color',expectedBackground[mode]);
    const expectedMuted={light:'rgb(82, 101, 108)',dark:'rgb(165, 182, 193)',oled:'rgb(181, 181, 181)',paper:'rgb(110, 96, 77)',gray:'rgb(81, 94, 103)'};
    await expect(page.locator('.author')).toHaveCSS('color',expectedMuted[mode]);
    const palette=await page.locator('.book').evaluate(e=>[getComputedStyle(e.querySelector('.author')).color,getComputedStyle(e).backgroundColor]);
    const luminance=color=>{const rgb=color.match(/\d+/g).slice(0,3).map(n=>{const x=Number(n)/255;return x<=.04045?x/12.92:((x+.055)/1.055)**2.4});return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722};
    const levels=palette.map(luminance).sort((a,b)=>b-a);
    assert.ok((levels[0]+.05)/(levels[1]+.05)>=4.5,'Metadata contrast '+mode+' '+JSON.stringify(palette));
    if(width===390 || width===1280) await page.screenshot({path:resolve(out,`theme-${mode}-${width}.png`)});
  }
  assert.equal(progress.length,beforeThemeProgress,'Appearance does not write progress');
  assert.equal(configWrites.length,beforeConfigWrites,'Appearance does not write reader config');
  await page.getByRole('button',{name:'Theo hệ thống',exact:true}).click();
  await page.emulateMedia({colorScheme:'dark'});
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.emulateMedia({colorScheme:'light'});
  await expect(page.locator('html')).toHaveAttribute('data-theme','light');
  await page.getByRole('button',{name:'Tối',exact:true}).click();
  await page.emulateMedia({colorScheme:'light'});
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.reload();
  await expect(page.locator('.book')).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.getByRole('button',{name:'Giấy ngà',exact:true}).click();
  await page.screenshot({path:resolve(out,`shelf-${width}.png`)});
  await page.locator('.book').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('.chapter .title')).toBeVisible();
  assert.equal(await page.locator('.chapter script, .chapter img').count(),0,'Sanitizer removes active markup/images');
  assert.equal(await page.evaluate(()=>window.unwanted),undefined);
  await expect(page.locator('.chapter p').nth(2)).toHaveText(paragraphs[2]);
  if(width===390) {
   const cdp=await page.context().newCDPSession(page);
   await cdp.send('DOM.enable'); await cdp.send('CSS.enable');
   const {root}=await cdp.send('DOM.getDocument');
   const {nodeId}=await cdp.send('DOM.querySelector',{nodeId:root.nodeId,selector:'.chapter p'});
   await page.screenshot({path:resolve(out,'reader-font-final.png')});
   const fonts=await cdp.send('CSS.getPlatformFontsForNode',{nodeId});
   assert.ok(fonts.fonts.length && fonts.fonts.every(f=>f.familyName!=='PingFang SC'), 'Vietnamese default must use a Latin font');
   console.log('Vietnamese font:',JSON.stringify(fonts));
  }
  if(width<776) {
   await page.getByRole('button',{name:'Công cụ đọc',exact:true}).click();
   await expect(page.getByRole('button',{name:'Ẩn công cụ',exact:true})).toBeVisible();
  }
  await page.getByRole('button',{name:'Mục lục',exact:true}).click();
  await page.getByRole('textbox',{name:'Tìm chương',exact:true}).fill('Chương 2');
  await expect(page.locator('.pop-cata:visible .cata-text')).toHaveCount(1);
  await page.screenshot({path:resolve(out,`catalog-${width}.png`)});
  await page.getByRole('button',{name:'Đóng mục lục',exact:true}).click();
  await page.getByRole('button',{name:'Cài đặt',exact:true}).click();
  await expect(page.locator('.pop-setting:visible')).toBeVisible();
  await expect(page.getByRole('button',{name:'Tủ sách',exact:true})).toBeVisible();
  const box=await page.locator('.pop-setting:visible').boundingBox();
  assert.ok(box.x>=-1&&box.x+box.width<=width+1,`Settings outside viewport ${width}`);
  await expect(page.locator('.pop-setting:visible')).toHaveCSS('opacity','1');
  await page.screenshot({path:resolve(out,`settings-${width}.png`)});
  if(width===390 || width===1280) {
    await page.locator('.pop-setting:visible').getByRole('button',{name:'Tối',exact:true}).click();
    await expect(page.locator('.chapter')).toHaveCSS('background-color','rgb(24, 33, 39)');
    await page.screenshot({path:resolve(out,`settings-dark-${width}.png`)});
    await page.getByRole('button',{name:'Đóng cài đặt',exact:true}).click();
    await expect(page.locator('.pop-setting:visible')).toHaveCount(0);
    await page.screenshot({path:resolve(out,`reader-dark-${width}.png`)});
    await page.getByRole('button',{name:'Mục lục',exact:true}).click();
    await expect(page.locator('.pop-cata:visible')).toBeVisible();
    await expect(page.locator('.pop-cata:visible')).toHaveCSS('opacity','1');
    await page.screenshot({path:resolve(out,`catalog-dark-${width}.png`)});
    await page.getByRole('button',{name:'Đóng mục lục',exact:true}).click();
    await page.getByRole('button',{name:'Cài đặt',exact:true}).click();
  }
  const chapterTitle=await page.locator('.chapter .title').innerText();
  await page.locator('.font-item-input').focus();
  await page.keyboard.press('ArrowRight');
  assert.equal(await page.locator('.chapter .title').innerText(),chapterTitle,'Settings keys must not change chapters');
  await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'Tủ sách',exact:true}).click();
  await expect(page.locator('.book')).toBeVisible();
  assert.ok(progress.every(p=>p.bookUrl===book.bookUrl && Number.isFinite(p.durChapterPos)), 'Progress keeps the raw book URL and numeric position');
  await page.locator('.book').focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('.chapter .title')).toBeVisible();
  await page.reload();
  // Relay deliberately uses memory history to keep credentials out of URL/history.
  // Reload returns to the shelf; 'recent book' must restore the reading location.
  await page.locator('.shelf-wrapper, .chapter .title').first().waitFor();
  if (await page.locator('.shelf-wrapper').isVisible()) {
    await page.locator('.recent-book').click();
  }
  await expect(page.locator('.chapter .title')).toHaveText(chapterTitle);
  await expect(page.locator('.chapter p').nth(2)).toHaveText(paragraphs[2]);
  checks++;
  await page.close();
 }
 assert.deepEqual(errors,[]);
 console.log(`Passed: formatter boundaries, ${checks} viewport shelf/reader/navigation checks, NFC/NFD preservation and HTML sanitization. Screenshots: ${out}`);
} finally { await browser.close(); }
