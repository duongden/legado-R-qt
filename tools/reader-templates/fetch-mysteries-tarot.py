"""Import the public-domain 1909 Rider-Waite-Smith major arcana from Wikimedia Commons."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
import hashlib
import json
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / 'lord-of-mysteries'
sys.path.insert(0, str(ROOT / 'output/mysteries-preview-deps'))
from PIL import Image

NAMES = ('Fool', 'Magician', 'High Priestess', 'Empress', 'Emperor', 'Hierophant', 'Lovers',
         'Chariot', 'Strength', 'Hermit', 'Wheel of Fortune', 'Justice', 'Hanged Man', 'Death',
         'Temperance', 'Devil', 'Tower', 'Star', 'Moon', 'Sun', 'Judgement', 'World')
HEADERS = {'User-Agent': 'LegadoReaderTheme/1.0 (public-domain tarot asset import)'}


def fetch(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=20) as response:
                return response.read()
        except Exception as error:
            if attempt == 3:
                raise
            delay = 15 if isinstance(error, urllib.error.HTTPError) and error.code == 429 else attempt + 1
            time.sleep(delay)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    titles = [f'File:RWS Tarot {index:02d} {name}.jpg' for index, name in enumerate(NAMES)]
    params = dict(action='query', format='json', prop='imageinfo', iiprop='url|extmetadata', titles='|'.join(titles))
    response = json.loads(fetch('https://commons.wikimedia.org/w/api.php?' + urllib.parse.urlencode(params)))
    pages = {page['title']: page for page in response['query']['pages'].values()}
    originals = ROOT / 'output/reader-mysteries-tarot-originals'
    originals.mkdir(exist_ok=True)

    def import_card(item):
        index, title = item
        info = pages[title]['imageinfo'][0]
        metadata = info['extmetadata']
        assert metadata['Copyrighted']['value'] == 'False'
        assert metadata['LicenseShortName']['value'] == 'Public domain'
        source_url = urllib.parse.urlsplit(info['url'])._replace(query='').geturl()
        assert urllib.parse.urlsplit(source_url).hostname == 'upload.wikimedia.org'
        original = originals / f'tarot-{index:02d}.jpg'
        data = original.read_bytes() if original.exists() else fetch(source_url)
        with Image.open(BytesIO(data)) as image:
            image.load()
            assert image.width >= 300 and image.height >= 500
            rgb = image.convert('RGB')
            assets = []
            for suffix, width, quality in (('', 360, 85), ('-thumb', 64, 78)):
                card = rgb.copy()
                card.thumbnail((width, width * 2), Image.Resampling.LANCZOS)
                target = SOURCE / 'assets' / f'tarot-{index:02d}{suffix}.webp'
                card.save(target, 'WEBP', quality=quality, method=6)
                encoded = target.read_bytes()
                assets.append(dict(file=target.name, bytes=len(encoded), sha256=digest(encoded),
                                   width=card.width, height=card.height))
        if not original.exists():
            original.write_bytes(data)
        print(f'Imported tarot {index:02d}: {NAMES[index]}', flush=True)
        return dict(index=index, title=title, url=source_url,
                    pageUrl='https://commons.wikimedia.org/wiki/' + urllib.parse.quote(title.replace(' ', '_'), safe=':_'),
                    artist='Pamela Colman Smith', publicationYear=1909, license='Public domain',
                    originalSha256=digest(data), originalBytes=len(data), assets=assets)

    with ThreadPoolExecutor(max_workers=2) as executor:
        cards = list(executor.map(import_card, enumerate(titles)))
    record = dict(collection='Rider-Waite-Smith major arcana', artist='Pamela Colman Smith',
                  source='Wikimedia Commons', license='Public domain',
                  downloadedAtUtc=datetime.now(timezone.utc).isoformat(), cards=cards)
    (SOURCE / 'tarot-sources.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(cards=len(cards), offlineAssets=sum(len(card['assets']) for card in cards),
                          assetBytes=sum(asset['bytes'] for card in cards for asset in card['assets'])), ensure_ascii=True))


if __name__ == '__main__':
    main()
