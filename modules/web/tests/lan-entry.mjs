import {createRequire} from 'node:module'
import {readFile} from 'node:fs/promises'
import {resolve,extname} from 'node:path'
import assert from 'node:assert/strict'
const require=createRequire(resolve(process.argv[2],'../package.json'))
const {chromium,expect}=require('@playwright/test')
const browser=await chromium.launch({channel:'chrome',headless:true})
try {
 for(const width of [320,390,768,1280]) {
  const page=await browser.newPage({viewport:{width,height:900}})
  await page.route('**/*',async route=>{
   const path=new URL(route.request().url()).pathname
   if(path==='/getBookshelf') return route.fulfill({json:{isSuccess:true,errorMsg:'',data:[]}})
   if(path==='/getReadConfig') return route.fulfill({json:{isSuccess:true,errorMsg:'',data:'{}'}})
   const file=path==='/'?'index.html':path.slice(1)
   if(file.includes('..'))return route.abort()
   try {await route.fulfill({body:await readFile(resolve('app/src/main/assets/web',file)),contentType:({'.html':'text/html','.js':'application/javascript','.css':'text/css'})[extname(file)]||'application/octet-stream'})}
   catch {await route.fulfill({status:404,body:''})}
  })
  await page.goto('http://lan.local/')
  await expect(page).toHaveURL('http://lan.local/vue/index.html#/')
  await expect(page.getByRole('heading',{name:/Tủ sách/,level:1})).toBeVisible()
  const searchGeometry = await page.locator('.shelf-search').evaluate(search => {
    const box=search.getBoundingClientRect(), list=document.querySelector('.shelf-state').getBoundingClientRect(), icon=search.querySelector('svg').getBoundingClientRect(), input=search.querySelector('input').getBoundingClientRect()
    return {left:Math.abs(box.left-list.left),right:Math.abs(box.right-list.right),center:Math.abs((icon.top+icon.bottom-input.top-input.bottom)/2)}
  })
  assert.ok(Object.values(searchGeometry).every(delta=>delta<=1),`Search alignment ${width}: ${JSON.stringify(searchGeometry)}`)
  const nav=page.getByRole('navigation',{name:'Chức năng Web Service'})
  assert.equal(await nav.getByRole('link').count(),5)
  for (const [label,url] of [['Nguồn sách','#/bookSource'],['Nguồn RSS','#/rssSource'],['Gửi sách','../uploadBook/index.html'],['Trợ giúp','../help/index.html#appHelp']]) {
    await expect(nav.getByRole('link',{name:label,exact:true})).toHaveAttribute('href',url)
  }
  for(const [mode,label] of [['light','Sáng'],['paper','Giấy ngà'],['dark','Tối']]) {
    await page.getByRole('button',{name:label,exact:true}).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme',mode)
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Overflow '+width)
  }
  await page.screenshot({path:`/private/tmp/legado-unified-${width}.png`,fullPage:true})
  await nav.getByRole('link',{name:'Nguồn sách',exact:true}).click()
  await expect(page.locator('.editor')).toBeVisible()
  // The existing first-use shortcut dialog may open in the editor.
  if(await page.getByRole('dialog').isVisible()) await page.getByRole('dialog').getByRole('button',{name:'Lưu',exact:true}).click()
  await page.locator('.editor-header').getByRole('link',{name:'Tủ sách'}).click()
  await expect(nav).toBeVisible()
  await page.close()
 }
 console.log('PASS LAN root opens the unified shelf, all navigation links, editor return, themes and four viewport widths')
} finally {await browser.close()}
