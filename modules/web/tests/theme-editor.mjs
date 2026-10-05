import { createRequire } from 'node:module'
import { readFile } from 'node:fs/promises'
import { resolve, extname } from 'node:path'
import assert from 'node:assert/strict'
const require = createRequire(resolve(process.argv[2], '../package.json'))
const { chromium, expect } = require('@playwright/test')
const assets = resolve('modules/web/dist')
const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  for (const width of [390, 1280]) {
    const page = await browser.newPage({ viewport: { width, height: 900 } })
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route('**/*', async route => {
      const url = new URL(route.request().url())
      let file = url.pathname.replace(/^\//, '') || 'index.html'
      if (file.includes('..')) return route.abort()
      try {
        await route.fulfill({
          body: await readFile(resolve(assets, file)),
          contentType:
            {
              '.html': 'text/html',
              '.js': 'application/javascript',
              '.css': 'text/css',
            }[extname(file)] || 'application/octet-stream',
        })
      } catch {
        await route.fulfill({
          json: { isSuccess: true, data: [], errorMsg: '' },
        })
      }
    })
    for (const path of ['bookSource', 'rssSource']) {
      await page.goto('http://fixture.local/#/' + path)
      await page.reload()
      if (await page.getByRole('dialog').isVisible())
        await page
          .getByRole('dialog')
          .getByRole('button', { name: 'Lưu', exact: true })
          .click()
      await page.getByRole('button', { name: 'Giao diện', exact: true }).click()
      const picker = page.locator('.el-popover:visible')
      await picker.getByRole('button', { name: 'Tối', exact: true }).click()
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
      await expect(page.locator('#source-json')).toHaveCSS(
        'color',
        'rgb(227, 234, 240)',
      )
      await picker.locator('summary').click()
      await picker.locator('select').selectOption('dark')
      await picker
        .getByLabel('Nền bảng và ô nhập', { exact: true })
        .fill('#223344')
      const originalInk = await picker.getByLabel('Chữ chính', {exact:true}).inputValue()
      await picker.getByLabel('Chữ chính', {exact:true}).fill('#223344')
      await expect(picker.getByRole('button', {name:'Áp dụng',exact:true})).toBeDisabled()
      await picker.getByLabel('Chữ chính', {exact:true}).fill(originalInk)
      await picker.getByRole('button', { name: 'Áp dụng', exact: true }).click()
      await expect(page.locator('#source-json')).toHaveCSS(
        'background-color',
        'rgb(34, 51, 68)',
      )
      await page.reload()
      await expect(page.locator('#source-json')).toHaveCSS(
        'background-color',
        'rgb(34, 51, 68)',
      )
      assert.equal(
        await page.evaluate(
          () => document.documentElement.scrollWidth > innerWidth,
        ),
        false,
        'horizontal overflow ' + width,
      )
      await page.getByRole('button', { name: 'Giao diện', exact: true }).click()
      await picker.locator('summary').click()
      await picker.locator('select').selectOption('dark')
      await picker.getByRole('button', { name: 'Khôi phục mặc định' }).click()
      await expect(page.locator('#source-json')).toHaveCSS(
        'background-color',
        'rgb(28, 37, 44)',
      )
      await picker
        .getByRole('button', { name: 'Theo hệ thống', exact: true })
        .click()
      await page.emulateMedia({ colorScheme: 'light' })
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
      await page.emulateMedia({ colorScheme: 'dark' })
      await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
      await page.getByRole('button', { name: 'Giao diện', exact: true }).click()
      await expect(picker).toBeHidden()
      await page.screenshot({
        path: '/private/tmp/theme-' + path + '-' + width + '.png',
        fullPage: true,
      })
    }
    assert.deepEqual(errors, [])
    await page.close()
  }
  for (const path of [
    'uploadBook/index.html',
    'help/index.html',
  ]) {
    const page = await browser.newPage({
      viewport: { width: 390, height: 900 },
    })
    await page.addInitScript(() => {
      localStorage.setItem('legado_ui_theme_v1', 'dark')
      localStorage.setItem(
        'legado_ui_palettes_v1',
        JSON.stringify({
          dark: {
            page: '#112233',
            surface: '#223344',
            reader: '#112233',
            ink: '#ffffff',
            muted: '#dddddd',
            accent: '#88ccff',
          },
        }),
      )
    })
    await page.route('**/*', async route => {
      const file = new URL(route.request().url()).pathname.replace(/^\//, '')
      if (file.includes('..')) return route.abort()
      try {
        await route.fulfill({
          body: await readFile(resolve('app/src/main/assets/web', file)),
          contentType:
            {
              '.html': 'text/html',
              '.js': 'application/javascript',
              '.css': 'text/css',
            }[extname(file)] || 'application/octet-stream',
        })
      } catch {
        await route.fulfill({ status: 404, body: '' })
      }
    })
    await page.goto('http://legacy.local/' + path)
    await expect(page.locator('.web-appearance select')).toHaveCSS('border-radius','10px')
    await expect(page.locator('.web-appearance select')).toHaveCSS('min-height','40px')

    await expect(page.locator('body')).toHaveCSS(
      'background-color',
      'rgb(17, 34, 51)',
    )
    if (path === 'uploadBook/index.html') {
      await expect(page.locator('#h5_btn')).toHaveCSS('border-radius','10px')
      await expect(page.locator('#h5_btn')).toHaveCSS('background-color','rgb(136, 204, 255)')
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'Upload overflow')
    }
    await page.getByLabel('Chế độ giao diện').selectOption('light')
    await expect(page.locator('body')).toHaveCSS(
      'background-color',
      'rgb(245, 247, 248)',
    )
    await page.screenshot({
      path: '/private/tmp/theme-legacy-' + path.replaceAll('/', '-') + '.png',
      fullPage: true,
    })
    await page.close()
  }
  console.log(
    'PASS editor book/RSS: custom colors, persistence, reset, system day/night, mobile/desktop geometry',
  )
} finally {
  await browser.close()
}
