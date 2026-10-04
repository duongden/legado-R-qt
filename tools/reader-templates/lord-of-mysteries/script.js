(function () {
  'use strict';
  var cards = [
    ['0','愚者','THE FOOL','启程 · 自由 · 无限可能','命运始于尚未踏出的那一步。'],
    ['I','魔术师','THE MAGICIAN','意志 · 创造 · 万物初始','让尚未成形的念头拥有形状。'],
    ['II','女祭司','THE HIGH PRIESTESS','直觉 · 秘密 · 静默之门','答案藏在未曾说出的半句话里。'],
    ['III','皇后','THE EMPRESS','丰饶 · 生长 · 温柔力量','每一片荒原都有自己的春天。'],
    ['IV','皇帝','THE EMPEROR','秩序 · 守护 · 坚定意志','在风暴经过之处建立自己的国度。'],
    ['V','教皇','THE HIEROPHANT','传承 · 信念 · 古老回声','翻开旧日的书，听见新的回响。'],
    ['VI','恋人','THE LOVERS','联结 · 抉择 · 心之所向','两条命运在同一个路口交会。'],
    ['VII','战车','THE CHARIOT','征途 · 决心 · 前行之力','穿过迷雾，方向仍握在手中。'],
    ['VIII','力量','STRENGTH','勇气 · 温柔 · 内在之光','真正的力量，也可以如此安静。'],
    ['IX','隐者','THE HERMIT','独行 · 求索 · 灯火未灭','一盏灯，足以照亮下一步。'],
    ['X','命运之轮','WHEEL OF FORTUNE','流转 · 契机 · 命运回环','齿轮转动，故事来到新的刻度。'],
    ['XI','正义','JUSTICE','平衡 · 真相 · 无声天秤','所有选择，都有自己的重量。'],
    ['XII','倒吊人','THE HANGED MAN','倒悬 · 等待 · 换一种凝视','让世界倒过来，答案便显出轮廓。'],
    ['XIII','死神','DEATH','告别 · 蜕变 · 新的黎明','一页落幕，另一页正在展开。'],
    ['XIV','节制','TEMPERANCE','交融 · 平和 · 恰如其分','让相反的河流汇成同一片海。'],
    ['XV','恶魔','THE DEVIL','欲望 · 羁绊 · 看清枷锁','辨认黑暗，也是在寻找出口。'],
    ['XVI','高塔','THE TOWER','惊变 · 破局 · 真相降临','闪电之后，天空比从前更辽阔。'],
    ['XVII','星星','THE STAR','希望 · 灵感 · 长夜微光','遥远的星，也能成为航标。'],
    ['XVIII','月亮','THE MOON','梦境 · 迷雾 · 潮汐低语','沿着月光，走过尚未知晓的路。'],
    ['XIX','太阳','THE SUN','光明 · 喜悦 · 万物生辉','长夜终有尽头，故事仍在发光。'],
    ['XX','审判','JUDGEMENT','觉醒 · 召唤 · 再次启程','远处的钟声，唤醒沉睡的名字。'],
    ['XXI','世界','THE WORLD','圆满 · 联结 · 无尽旅程','走过一个世界，再翻开下一个。']
  ];
  var chapterSeed = 0, firstCard = 0, spread = 0, companions = [7, 14];
  var displayedPages = [], clockBucket = -1, phaseLabel = '绯红长夜', loadedArt = {};
  var skyStops = [
    [0,'#131729','#a24960'], [300,'#25243a','#ad7796'], [450,'#374350','#cda882'],
    [600,'#293d47','#d3bd87'], [960,'#33404a','#c09865'], [1080,'#352b3b','#c07462'],
    [1200,'#1c1d31','#a65368'], [1440,'#131729','#a24960']
  ];
  function hash(value) {
    var result = 2166136261;
    for (var i = 0; i < value.length; i++) result = Math.imul(result ^ value.charCodeAt(i), 16777619);
    // Mix the high bits too: repeated chapter metadata must not restrict the
    // draw to only even or odd cards in the 22-card deck.
    result = Math.imul(result ^ (result >>> 16), 0x85ebca6b);
    result = Math.imul(result ^ (result >>> 13), 0xc2b2ae35);
    return (result ^ (result >>> 16)) >>> 0;
  }
  function text(page, selector, value) {
    var node = page.querySelector(selector);
    if (node && node.textContent !== value) node.textContent = value;
  }
  function blend(a, b, amount) {
    return '#' + [1, 3, 5].map(function (start) {
      var x = parseInt(a.slice(start, start + 2), 16), y = parseInt(b.slice(start, start + 2), 16);
      return ('0' + Math.round(x + (y - x) * amount).toString(16)).slice(-2);
    }).join('');
  }
  function updateClock() {
    // Native live, prefetched and persisted pages all use the same clock bucket.
    // Local-clock fallback is deliberately ineligible for disk snapshots.
    var match = String(readerTemplate.fields.time || '').trim().match(/^([01]?\d|2[0-3])[:：]([0-5]\d)$/);
    var now = match ? null : new Date();
    var minute = match ? Number(match[1]) * 60 + Number(match[2]) : now.getHours() * 60 + now.getMinutes();
    minute = Math.floor(minute / 15) * 15;
    if (minute === clockBucket) return;
    clockBucket = minute;
    var day = minute >= 360 && minute < 1080;
    var phase = minute >= 300 && minute < 480 ? 'dawn' : minute >= 480 && minute < 1020 ? 'day' : minute >= 1020 && minute < 1200 ? 'dusk' : 'night';
    phaseLabel = {dawn:'晨雾初启', day:'日光手札', dusk:'暮色来信', night:'绯红长夜'}[phase];
    var root = document.documentElement, stop = 0;
    while (stop < skyStops.length - 2 && minute > skyStops[stop + 1][0]) stop++;
    var a = skyStops[stop], b = skyStops[stop + 1], amount = (minute - a[0]) / (b[0] - a[0]);
    var progress = day ? (minute - 360) / 720 : ((minute + 360) % 1440) / 720;
    root.setAttribute('data-lm-phase', phase);
    root.setAttribute('data-lm-celestial', day ? 'sun' : 'moon');
    root.setAttribute('data-lm-clock', String(minute));
    root.style.setProperty('--lm-sky-top', blend(a[1], b[1], amount));
    root.style.setProperty('--lm-sky-glow', blend(a[2], b[2], amount) + '66');
    root.style.setProperty('--lm-celestial-x', (43 + progress * 40).toFixed(3) + '%');
    root.style.setProperty('--lm-celestial-y', (29 - Math.sin(progress * Math.PI) * 18).toFixed(3) + '%');
    displayedPages.forEach(function (page) { text(page, '.lm-phase-label', phaseLabel); });
  }
  function art(node, index, large) {
    if (!node) return;
    node.setAttribute('data-lm-art-size', large ? 'art' : 'thumb');
    node.setAttribute('data-lm-art', String(index));
  }
  function prepareArt(node) {
    var key = node.getAttribute('data-lm-art-size') + '-' + node.getAttribute('data-lm-art');
    if (loadedArt[key]) return loadedArt[key];
    var value = getComputedStyle(node).backgroundImage, url = value.slice(4, -1).trim();
    if (url.charAt(0) === '"' || url.charAt(0) === "'") url = url.slice(1, -1);
    loadedArt[key] = new Promise(function (resolve, reject) {
      var image = new Image();
      image.onload = function () {
        if (typeof image.decode === 'function') image.decode().then(resolve, reject); else resolve();
      };
      image.onerror = function () { reject(new Error('Unable to decode bundled tarot art: ' + key)); };
      image.src = url;
    });
    return loadedArt[key];
  }
  readerTemplate.on('beforeLayout', function () {
    var first = true;
    Array.prototype.forEach.call(readerTemplate.source.querySelectorAll('.reader-paragraph'), function (p) {
      var value = p.textContent.trim(), scene = /^[*＊※·•—_\-\s]{3,}$/.test(value);
      p.classList.toggle('lm-scene', scene);
      p.classList.toggle('lm-dialogue', /^[“‘「『"\x60]/.test(value));
      p.classList.toggle('lm-lead', first && !!value && !scene);
      if (value && !scene) first = false;
    });
    Array.prototype.forEach.call(readerTemplate.source.querySelectorAll('.reader-chapter-title'), function (title) {
      var match = title.textContent.match(/第\s*([^章回节卷\s]{1,16})\s*[章回节卷]/);
      title.setAttribute('data-lm-chapter', match ? match[1] : 'NEW CHAPTER');
    });
    // A chapter draws a reproducible spread. Reflow and offscreen renderers
    // always draw the same cards; moving between pages never shuffles them.
    var heading = readerTemplate.source.querySelector('.reader-chapter-title');
    chapterSeed = hash(String(readerTemplate.fields.bookName || '') + '\u0000' +
      String(readerTemplate.fields.chapterTitle || '') + '\u0000' + (heading ? heading.textContent : ''));
    firstCard = chapterSeed % cards.length;
    spread = (chapterSeed >>> 8) % 3;
    companions = [(firstCard + 5 + (chapterSeed >>> 12) % 6) % cards.length,
      (firstCard + 13 + (chapterSeed >>> 18) % 8) % cards.length];
    updateClock();
  });
  function sync(page, index, count) {
    if (!page) return;
    var key = chapterSeed + '/' + index + '/' + count;
    if (page.getAttribute('data-lm-position') === key) return;
    page.setAttribute('data-lm-position', key);
    page.setAttribute('data-lm-spread', String(spread));
    var current = (firstCard + index) % cards.length, card = cards[current], progress = (index + 1) / Math.max(1, count);
    text(page, '.lm-card-name', card[1]); text(page, '.lm-card-english', card[2]);
    text(page, '.lm-mini-card .lm-card-mark', card[0]);
    art(page.querySelector('.lm-mini-card .lm-tarot-art'), current, false);
    text(page, '.lm-deck-index', card[0]); text(page, '.lm-percent', (progress * 100).toFixed(1) + '%');
    page.style.setProperty('--lm-progress', progress.toFixed(6));
    Array.prototype.forEach.call(page.querySelectorAll('.lm-deck-card'), function (node, slot) {
      var position = (current + slot - 3 + cards.length) % cards.length;
      node.setAttribute('data-lm-card', String(position));
      if (slot === 3) node.setAttribute('data-lm-selected', ''); else node.removeAttribute('data-lm-selected');
      node.querySelector('.lm-card-mark').textContent = cards[position][0];
      art(node.querySelector('.lm-tarot-art'), position, false);
    });
    if (page.querySelector('.lm-opening-spread')) {
      var opening = cards[firstCard];
      text(page, '.lm-oracle-title', opening[1]); text(page, '.lm-oracle-english', opening[2]);
      text(page, '.lm-oracle-number', opening[0]); text(page, '.lm-oracle-keywords', opening[3]);
      text(page, '.lm-motto', opening[4]); text(page, '.lm-opening-caption', 'ARCANA / ' + opening[0]);
      text(page, '.lm-phase-label', phaseLabel);
      art(page.querySelector('.lm-opening-card .lm-tarot-art'), firstCard, true);
      art(page.querySelector('.lm-companion-near .lm-tarot-art'), companions[0], true);
      art(page.querySelector('.lm-companion-far .lm-tarot-art'), companions[1], true);
    }
  }
  // Keep decorative artwork still. Only page changes and the shared 15-minute
  // clock bucket update it, leaving the renderer available for page gestures.
  readerTemplate.on('afterLayout', function (event) {
    displayedPages = event.pages;
    event.pages.forEach(function (page, index) { sync(page, index, event.pages.length); });
    // Finish all writes before reading card styles, and decode only artwork
    // actually used by this chapter. Never let the first turn start its decode.
    var pending = [], seen = {};
    event.pages.forEach(function (page) {
      Array.prototype.forEach.call(page.querySelectorAll('.lm-tarot-art'), function (node) {
        var key = node.getAttribute('data-lm-art-size') + '-' + node.getAttribute('data-lm-art');
        if (seen[key]) return;
        seen[key] = true; pending.push(prepareArt(node));
      });
    });
    readerTemplate.waitUntil(Promise.all(pending));
  });
  readerTemplate.on('pageChange', function (event) { sync(event.page, event.pageIndex, readerTemplate.pageCount); });
  readerTemplate.on('fieldsChange', updateClock, {fields: ['time']});
  readerTemplate.on('dispose', function () { displayedPages = []; loadedArt = {}; });
}());
