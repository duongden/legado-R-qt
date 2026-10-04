import assert from 'node:assert/strict'
import {createRequire} from 'node:module'
import {resolve} from 'node:path'
import {fileURLToPath} from 'node:url'
const require=createRequire(resolve(process.argv[2], '../package.json'))
const {build}=require('esbuild')
const {chromium}=require('@playwright/test')
const bundled=await build({entryPoints:[fileURLToPath(new URL('../src/config/appearance.ts',import.meta.url))],bundle:true,write:false,format:'iife',globalName:'Appearance'})
const browser=await chromium.launch({headless:true,channel:process.env.PLAYWRIGHT_CHANNEL||'chrome'})
try {
 for(const [legacy,expected] of [[0,'paper'],[1,'paper'],[2,'light'],[3,'light'],[4,'light'],[5,'gray'],[6,'dark']]) {
  const page=await browser.newPage()
  await page.route('http://fixture.local/',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html></html>'}))
  await page.goto('http://fixture.local/')
  await page.addScriptTag({content:bundled.outputFiles[0].text})
  await page.evaluate(value=>Appearance.migrateLegacyTheme(value),legacy)
  assert.equal(await page.evaluate(()=>Appearance.themeMode.value),expected)
  await page.evaluate(()=>{Appearance.setThemeMode('oled');Appearance.migrateLegacyTheme(2)})
  assert.equal(await page.evaluate(()=>Appearance.themeMode.value),'oled','Explicit choice wins late config')
  await page.close()
 }
 for(const unavailable of [false,true]) {
  const page=await browser.newPage()
  await page.route('http://fixture.local/',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><html></html>'}))
  await page.goto('http://fixture.local/')
  await page.evaluate(block=>{
   if(block) {Storage.prototype.getItem=()=>{throw new Error('denied')};Storage.prototype.setItem=()=>{throw new Error('denied')}}
   else localStorage.setItem('legado_ui_theme_v1','invalid')
  },unavailable)
  await page.addScriptTag({content:bundled.outputFiles[0].text})
  assert.equal(await page.evaluate(()=>Appearance.themeMode.value),'system')
  await page.evaluate(()=>{Appearance.migrateLegacyTheme(99);Appearance.migrateLegacyTheme(null)})
  assert.equal(await page.evaluate(()=>Appearance.themeMode.value),'system')
  await page.evaluate(()=>Appearance.setThemeMode('gray'))
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'gray')
  await page.close()
 }
 console.log('Passed: all seven legacy presets, delayed config, invalid saved mode, unavailable storage.')
} finally {await browser.close()}
