import { readFileSync, writeFileSync, copyFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
const file = path => fileURLToPath(new URL(path, import.meta.url))
const catalog = JSON.parse(readFileSync(file('../src/config/ui-locales.json'), 'utf8'))
const target = file('../../../app/src/main/assets/web/assets/web-i18n.js')
const source = readFileSync(target, 'utf8')
writeFileSync(target, source.replace(/const catalog = .*?;\n/, `const catalog = ${JSON.stringify(catalog)};\n`))
copyFileSync(file('../src/assets/design-system.css'), file('../../../app/src/main/assets/web/assets/design-system.css'))
