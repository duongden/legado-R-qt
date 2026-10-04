"""Build the self-contained reader template from reviewable HTML/CSS/JS sources."""
from pathlib import Path
import base64
import json

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
source = HERE / 'gilded'
corner = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 70 70" fill="none" aria-hidden="true"><path d="M7 62V7H62M12 52V12H52M18 8C18 32 39 10 43 21C47 32 27 31 25 17M8 18C32 18 10 39 21 43C32 47 31 27 17 25" stroke="currentColor" stroke-width=".8"/><path d="M9 9C15 11 13 16 19 19C16 13 11 15 9 9ZM21 10C26 10 26 16 22 20C19 16 18 14 21 10ZM10 21C10 26 16 26 20 22C16 19 14 18 10 21Z" fill="currentColor"/><path d="M39 12L45 8L51 12L45 16ZM12 39L8 45L12 51L16 45Z" stroke="currentColor" stroke-width=".65"/><circle cx="7" cy="7" r="2.5" fill="currentColor"/><circle cx="59" cy="7" r="1.5" fill="currentColor"/><circle cx="7" cy="59" r="1.5" fill="currentColor"/></svg>'''
vine = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 22 220" fill="none" aria-hidden="true"><path d="M11 0V220M11 12C-5 33 27 44 11 67C-5 87 27 101 11 124C-5 146 27 159 11 184C-5 201 20 209 11 220" stroke="currentColor" stroke-width=".65"/><path d="M11 26C1 19 0 33 11 36C20 30 22 21 11 26ZM11 83C1 76 0 90 11 93C20 87 22 78 11 83ZM11 140C1 133 0 147 11 150C20 144 22 135 11 140ZM11 197C1 190 0 204 11 207C20 201 22 192 11 197Z" fill="currentColor" opacity=".65"/><path d="M11 51L8 55L11 59L14 55ZM11 109L8 113L11 117L14 113ZM11 166L8 170L11 174L14 170Z" fill="currentColor"/></svg>'''
fleur = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 20" fill="none" aria-hidden="true"><path d="M2 10H50M12 10C20-4 31 2 30 10C37-1 47 3 43 10M12 10C20 24 31 18 30 10C37 21 47 17 43 10" stroke="currentColor" stroke-width=".9"/><path d="M47 10L53 6L59 10L53 14Z" fill="currentColor"/></svg>'''
markup = (source / 'page.html').read_text(encoding='utf-8')
for name, value in {'corner': corner, 'vine': vine, 'fleur': fleur}.items():
    markup = markup.replace('{{' + name + '}}', value)
portrait = 'data:image/webp;base64,' + base64.b64encode((source / 'peony-cameo.webp').read_bytes()).decode('ascii')
template = dict(schemaVersion=1, id='builtin.gilded', name='鎏金花笺',
    description='植物版画花章、墨绿鎏金书框、章节纹章、段落编号与对话侧饰。页眉页脚、字体和排版全部由模板决定；花章离线内置，光泽动效只作用于装饰。',
    firstPageHtml=markup.replace('{{pageClass}}', 'gilded-opening'),
    otherPageHtml=markup.replace('{{pageClass}}', 'gilded-continuing'),
    css=(source / 'style.css').read_text(encoding='utf-8').replace('{{portrait}}', portrait),
    javascript=(source / 'script.js').read_text(encoding='utf-8'))
destination = ROOT / 'app/src/main/assets/epub/templates/builtin.gilded.json'
destination.write_text(json.dumps(template, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'template': str(destination), 'bytes': destination.stat().st_size}))
