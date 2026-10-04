"""Build the portable Lord of the Mysteries reader theme from its editable sources."""
import base64
import json
import hashlib
import math
from pathlib import Path
import random
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = HERE / 'lord-of-mysteries'


def dial():
    ticks = []
    for index in range(60):
        angle = index * math.tau / 60
        inner = 95 if index % 5 == 0 else 101
        ticks.append(f'M{120+math.sin(angle)*inner:.2f} {120-math.cos(angle)*inner:.2f}L{120+math.sin(angle)*107:.2f} {120-math.cos(angle)*107:.2f}')
    numerals = ''.join(f'<text x="{120+math.sin(i*math.tau/12)*80:.2f}" y="{123-math.cos(i*math.tau/12)*80:.2f}" text-anchor="middle" fill="currentColor" stroke="none" font-family="Mysteries Display,serif" font-size="8">{n}</text>' for i,n in enumerate(('XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI')))
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" fill="none">' + '''
      <circle cx="120" cy="120" r="111" stroke="currentColor" stroke-width=".6"/>
      <circle cx="120" cy="120" r="91" stroke="currentColor" stroke-width=".7"/>
      <circle cx="120" cy="120" r="65" stroke="currentColor" stroke-width=".5"/>
      <path d="''' + ''.join(ticks) + '''" stroke="currentColor" stroke-width=".9"/>
      ''' + numerals + '''<g class="lm-dial-rotor" stroke="currentColor" stroke-width=".7">
        <path d="M120 57 175 151H65ZM120 183 65 89h110Z"/><circle cx="120" cy="120" r="48"/>
        <path d="M120 72v12m0 72v12M72 120h12m72 0h12"/>
      </g><path d="M120 91v29l17 12" stroke="currentColor" stroke-width="2"/>
      <circle cx="120" cy="120" r="4" fill="currentColor"/>
      <path d="M120 5v12m0 206v12M5 120h12m206 0h12" stroke="currentColor"/>
    </svg>'''


def skyline():
    rng = random.Random(1887)
    buildings = []
    for x in range(0, 430, 22):
        top = rng.randrange(79, 126)
        buildings.append(f'<path d="M{x} 160V{top}l10-9 11 9v{160-top}Z" fill="currentColor"/>')
        for y in range(top + 12, 151, 15):
            for dx in (5, 13):
                if rng.random() < .65:
                    buildings.append(f'<rect x="{x+dx}" y="{y}" width="3" height="5" fill="#a5856555"/>')
    return '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 430 160" preserveAspectRatio="xMidYMax slice">
      <path d="M0 156V119h24V93h4v-9h4v9h4v26h34V96h31V78h5v-9h4v9h5v18h32v36h20V89h35v43h15V101h21v-31h20v62h20v-26h21v27h34V91h22v42h37v-17h26v40Z" fill="#3c3d45"/>
      ''' + ''.join(buildings) + '''
      <path d="M265 160V58h4V46h4V35h4V22h4V11h3v11h4v13h4v11h4v12h4v102Z" fill="currentColor"/>
      <path d="M257 65h51v5h-51Zm5-8h41v5h-41Zm7 35h27v3h-27Z" fill="#45505a"/>
      <circle cx="282.5" cy="77" r="9" fill="#c5a684" opacity=".5"/>
      <circle cx="282.5" cy="77" r="7.4" fill="#1c2631"/><path d="M282.5 71v6l4 2" fill="none" stroke="#c5a684" stroke-width="1"/>
      <path d="M273 99h4v41h-4Zm15 0h4v41h-4Z" fill="#59606a66"/>
      <path d="M66 160v-41h3v-21h2v21h3v41m-9-40h10M182 160v-35h3v-20h2v20h3v35" fill="#161c26"/>
      <path d="M68 97c-8 0-8 9 0 9s8-9 0-9m118 7c-7 0-7 8 0 8s7-8 0-8" fill="#c7a37588"/>
    </svg>'''


def main():
    corner = '<svg class="lm-corner" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"><path d="M1 23V1h22M5 19V5h14M2 12c8 0 10-2 10-10M5 15c7 0 10-3 10-10" stroke="currentColor" stroke-width=".65"/><path d="m6 6 3 1-2 2Z" fill="currentColor"/><circle cx="17" cy="17" r="1" fill="currentColor"/></svg>'
    deck = '<span class="lm-deck-card"><span class="lm-tarot-art"></span><i class="lm-card-mark">0</i></span>' * 7
    replacements = {'footer': (SOURCE / 'footer.html').read_text(encoding='utf-8'),
                    'dial': dial(), 'skyline': skyline(), 'corners': corner * 4, 'deck': deck}
    pages = {}
    for name in ('first', 'other'):
        text = (SOURCE / (name + '.html')).read_text(encoding='utf-8')
        for key, value in replacements.items():
            text = text.replace('{{' + key + '}}', value)
        pages[name] = text
    tarot = json.loads((SOURCE / 'tarot-sources.json').read_text(encoding='utf-8'))
    assert len(tarot['cards']) == 22
    declarations = []
    for card in tarot['cards']:
        assert card['license'] == 'Public domain'
        for entry in card['assets']:
            payload = (SOURCE / 'assets' / entry['file']).read_bytes()
            assert hashlib.sha256(payload).hexdigest() == entry['sha256']
            kind = 'thumb' if '-thumb.' in entry['file'] else 'art'
            # Large data URLs belong to the artwork alone. Inheriting the whole
            # deck through :root makes revealing even plain text recalculate it.
            declarations.append(f'.lm-tarot-art[data-lm-art-size="{kind}"][data-lm-art="{card["index"]}"] '
                                f'{{ background-image: url("{{{{asset:{entry["file"]}}}}}"); }}')
    def asset(match):
        mime = 'image/webp' if match[1].endswith('.webp') else 'font/ttf'
        return 'data:' + mime + ';base64,' + base64.b64encode((SOURCE / 'assets' / match[1]).read_bytes()).decode('ascii')
    css_source = (SOURCE / 'style.css').read_text(encoding='utf-8').replace('{{tarotAssets}}', '\n'.join(declarations))
    css = re.sub(r'\{\{asset:([a-zA-Z0-9_.-]+)\}\}', asset, css_source)
    notice = '/* Non-official Lord of the Mysteries reader theme. Tarot plates: Pamela Colman Smith, Rider-Waite-Smith, 1909, public domain, via Wikimedia Commons.\nSource pages and hashes: tools/reader-templates/lord-of-mysteries/tarot-sources.json.\nCinzel by Natanael Gama, SIL OFL 1.1.\n' + (SOURCE / 'assets/Cinzel-OFL.txt').read_text(encoding='utf-8').replace('*/','* /') + '\n*/\n'
    template = dict(schemaVersion=1, id='builtin.lord_of_mysteries', name='诡秘之主 · 灰雾之上',
        description='内置二十二张莱德—韦特塔罗牌原图，按章节抽取三牌组合，首页以牌名、牌面与题词展开，续页化为神秘学手札。装饰保持静态，晨雾、日光、暮色与绯红长夜随本地时间更新，翻页塔罗与当前页同步。非官方读者主题，图片与字体全部离线内置。',
        firstPageHtml=pages['first'], otherPageHtml=pages['other'], css=notice+css,
        javascript=(SOURCE / 'script.js').read_text(encoding='utf-8'))
    assert '{{' not in json.dumps(template, ensure_ascii=False)
    target = ROOT / 'app/src/main/assets/epub/templates/builtin.lord_of_mysteries.json'
    target.write_text(json.dumps(template, ensure_ascii=False, indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'template': str(target), 'bytes': target.stat().st_size}))


if __name__ == '__main__':
    main()
