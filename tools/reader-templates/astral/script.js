(function () {
  'use strict';
  var sectors = ['猎户之境', '天琴之弦', '天鹅之羽', '仙女之梦', '飞马之翼', '英仙之光', '北冕之环', '天鹰之瞳', '巨蛇之尾', '凤凰之焰', '南十字航标', '无垠星海'];
  readerTemplate.on('beforeLayout', function () {
    var first = true;
    Array.prototype.forEach.call(readerTemplate.source.querySelectorAll('.reader-paragraph'), function (paragraph) {
      var text = paragraph.textContent.trim(), scene = /^[*＊※·•—_\-\s]{3,}$/.test(text);
      paragraph.classList.toggle('st-scene', scene);
      paragraph.classList.toggle('st-dialogue', /^[“‘「『"`]/.test(text));
      paragraph.classList.toggle('st-lead', first && !!text && !scene);
      if (text && !scene) first = false;
    });
    Array.prototype.forEach.call(readerTemplate.source.querySelectorAll('.reader-chapter-title'), function (title) {
      var match = title.textContent.match(/第\s*([^章回节卷\s]{1,16})\s*[章回节卷]/);
      title.setAttribute('data-st-chapter', match ? match[1] : 'NEW CHAPTER');
    });
  });
  function sync(page, index, count) {
    if (!page) return;
    var sector = index % 12, key = index + '/' + count;
    if (page.getAttribute('data-st-position') === key) return;
    page.setAttribute('data-st-position', key);
    var label = page.querySelector('.st-sector-name'), number = page.querySelector('.st-sector-number');
    if (label) label.textContent = sectors[sector];
    if (number) number.textContent = ('0' + (sector + 1)).slice(-2);
    var percent = page.querySelector('.st-percent');
    if (percent) percent.textContent = ((index + 1) * 100 / Math.max(1, count)).toFixed(1);
    Array.prototype.forEach.call(page.querySelectorAll('.st-orbit-node'), function (node, i) {
      if (i === sector) node.setAttribute('data-st-current', '');
      else node.removeAttribute('data-st-current');
    });
  }
  var active = null, animations = [];
  function stop() { animations.forEach(function (a) { a.cancel(); }); animations = []; active = null; }
  function animate(node, frames, duration, delay, easing) {
    if (node && typeof node.animate === 'function') animations.push(node.animate(frames,
      {duration: duration, delay: delay || 0, easing: easing || 'ease-in-out', iterations: Infinity}));
  }
  function start(page) {
    active = page;
    if (readerTemplate.viewport.height <= 440) return;
    animate(page.querySelector('.st-rotor'), [{transform:'rotate(0deg)'},{transform:'rotate(360deg)'}], 120000, 0, 'linear');
    animate(page.querySelector('.st-orbit-glow'), [{opacity:.35},{opacity:.85},{opacity:.35}], 8000);
    animate(page.querySelector('.st-aurora'), [{opacity:.45},{opacity:.9},{opacity:.45}], 13000);
    animate(page.querySelector('.st-meteor'), [
      {transform:'translate(0,0) rotate(-32deg)',opacity:0,offset:0},
      {transform:'translate(-3px,2px) rotate(-32deg)',opacity:0,offset:.72},
      {transform:'translate(-17px,11px) rotate(-32deg)',opacity:.9,offset:.77},
      {transform:'translate(-126px,79px) rotate(-32deg)',opacity:0,offset:.91},
      {transform:'translate(-126px,79px) rotate(-32deg)',opacity:0,offset:1}
    ], 11000);
    Array.prototype.forEach.call(page.querySelectorAll('.st-twinkle'), function (star, index) {
      animate(star, [{opacity:.35},{opacity:1},{opacity:.35}], 4000 + index * 1300, -index * 900);
    });
  }
  readerTemplate.on('afterLayout', function (event) { stop(); event.pages.forEach(function (page, index) { sync(page, index, event.pages.length); }); });
  readerTemplate.on('pageChange', function (event) { sync(event.page, event.pageIndex, readerTemplate.pageCount); });
  readerTemplate.on('motionChange', function (event) {
    if (active && active !== event.page) stop();
    if (!event.page || event.state === 'settled' || event.reducedMotion) { stop(); return; }
    if (event.state === 'paused') { animations.forEach(function (a) { a.pause(); }); return; }
    if (active === event.page) animations.forEach(function (a) { a.play(); });
    else start(event.page);
  });
  readerTemplate.on('dispose', stop);
}());
