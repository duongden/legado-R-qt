"""Build the offline Astral Observatory template from editable SVG, CSS and JavaScript."""
import base64
import json
import math
from pathlib import Path
import random
import re

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = HERE / 'astral'


def starfield():
    rng = random.Random(20260922)
    stars = []
    for index in range(110):
        x, y = rng.uniform(0, 400), rng.uniform(0, 220)
        radius = rng.choice((.4, .55, .75, 1.1))
        stars.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{radius}" fill="{rng.choice(("#d6e4e6", "#e6cca1", "#aaa5d7"))}" opacity="{rng.uniform(.3,.85):.2f}"/>')
    for x, y in ((180, 38), (324, 83), (225, 168)):
        stars.append(f'<path class="st-twinkle" d="M{x-4} {y}h8m-4-4v8" stroke="#e6dac0" stroke-width=".7"/>')
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 220" preserveAspectRatio="xMidYMid slice" fill="none">' + ''.join(stars) + '</svg>'


def astrolabe():
    ticks = []
    for index in range(72):
        angle = index * math.tau / 72
        inner = 98 if index % 6 == 0 else 102 if index % 3 == 0 else 105
        def point(radius):
            return f'{120+math.cos(angle)*radius:.2f} {120+math.sin(angle)*radius:.2f}'
        ticks.append('M' + point(inner) + 'L' + point(109))
    return '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" fill="none" aria-hidden="true">
      <circle cx="120" cy="120" r="111" stroke="currentColor" stroke-width=".6" opacity=".6"/>
      <circle cx="120" cy="120" r="94" stroke="currentColor" stroke-width=".6" opacity=".45"/>
      <path d="''' + ''.join(ticks) + '''" stroke="currentColor" stroke-width=".7" opacity=".75"/>
      <g class="st-rotor"><ellipse cx="120" cy="120" rx="86" ry="40" transform="rotate(-34 120 120)" stroke="currentColor" stroke-width="1" opacity=".8"/>
      <ellipse cx="120" cy="120" rx="86" ry="40" transform="rotate(49 120 120)" stroke="#a8d4df" stroke-width=".6" opacity=".55"/>
      <path d="M120 26 124 34 120 42 116 34ZM120 198 124 206 120 214 116 206Z" fill="currentColor"/>
      <circle cx="193" cy="76" r="3" fill="#efdab5"/><circle cx="49" cy="165" r="2" fill="#badce1"/></g>
      <circle class="st-orbit-glow" cx="120" cy="120" r="44" stroke="#b4d5d8" stroke-width=".7"/>
      <path d="M116 116h8m-4-4v16" stroke="#f1dfb8" stroke-width=".7" opacity=".7"/>
      <path d="M120 4v13m0 206v13M4 120h13m206 0h13" stroke="currentColor" stroke-width=".8"/>
      <path d="M117 12 120 7 123 12m-6 216 3 5 3-5M12 117l-5 3 5 3m216-6 5 3-5 3" stroke="currentColor" stroke-width=".6"/>
      <path d="M44 45 53 55m134 130 9 10M45 196l9-10m132-132 10-9" stroke="currentColor" stroke-width=".5" opacity=".5"/>
    </svg>'''


def constellation():
    positions = [(10 + index * 28, (12, 7, 13, 9, 15, 6, 11, 8, 14, 6, 11, 8)[index]) for index in range(12)]
    path = 'M' + 'L'.join(f'{x} {y}' for x, y in positions)
    nodes = []
    for x, y in positions:
        nodes.append(f'<g class="st-orbit-node"><circle class="st-star-halo" cx="{x}" cy="{y}" r="6" fill="#91c6d3" opacity=".25"/><path d="M{x} {y-3}l1.2 1.8 1.8 1.2-1.8 1.2-1.2 1.8-1.2-1.8-1.8-1.2 1.8-1.2Z" fill="#e6c995"/><circle cx="{x}" cy="{y}" r="1" fill="#f8efd8"/></g>')
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 328 23" preserveAspectRatio="none"><path d="' + path + '" fill="none" stroke="#7c9eb7" stroke-width=".5" opacity=".6"/>' + ''.join(nodes) + '</svg>'


def expand(text):
    def asset(match):
        return 'data:font/ttf;base64,' + base64.b64encode((SOURCE / 'assets' / match[1]).read_bytes()).decode('ascii')
    return re.sub(r'\{\{asset:([a-zA-Z0-9_.-]+)\}\}', asset, text)


def main():
    corner = '<svg class="st-corner" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 25 25" fill="none"><path d="M1 24V1h23M5 20V5h15M5 12l7-7M2 15l13-13" stroke="currentColor" stroke-width=".6"/><path d="M3 3h4v4H3Z" fill="currentColor"/><circle cx="14" cy="14" r="1" fill="currentColor"/></svg>'
    replacements = {'footer': (SOURCE / 'footer.html').read_text(encoding='utf-8'), 'starfield': starfield(),
                    'astrolabe': astrolabe(), 'constellation': constellation(), 'corners': corner * 4}
    pages = {}
    for name in ('first', 'other'):
        source = (SOURCE / (name + '.html')).read_text(encoding='utf-8')
        for key, value in replacements.items():
            source = source.replace('{{' + key + '}}', value)
        pages[name] = source
    license_text = (SOURCE / 'assets/Cinzel-OFL.txt').read_text(encoding='utf-8').replace('*/', '* /')
    notice = '/* Original Astral Observatory artwork. Cinzel by Natanael Gama, SIL OFL 1.1.\n' + license_text + '\n*/\n'
    template = dict(schemaVersion=1, id='builtin.astral_observatory', name='星穹 · 天文馆',
                    description='鎏金星盘、月蚀行星与极光星云构成首页，续页化为深蓝观星手札。缓缓旋转的天球仪、流星、闪烁星辰与十二星区航程，搭配月白正文和精细金色书框。全部资源离线内置。',
                    firstPageHtml=pages['first'], otherPageHtml=pages['other'],
                    css=notice + expand((SOURCE / 'style.css').read_text(encoding='utf-8')),
                    javascript=(SOURCE / 'script.js').read_text(encoding='utf-8'))
    assert '{{' not in json.dumps(template, ensure_ascii=False)
    destination = ROOT / 'app/src/main/assets/epub/templates/builtin.astral_observatory.json'
    destination.write_text(json.dumps(template, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'template': str(destination), 'bytes': destination.stat().st_size}))


if __name__ == '__main__':
    main()
