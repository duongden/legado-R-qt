(function () {
  'use strict';
  // Only classify semantic source blocks before pagination. Never split, wrap,
  // replace, duplicate or hide source characters, images or action nodes.
  readerTemplate.on('beforeLayout', function () {
    var source = readerTemplate.source, ordinal = 0;
    Array.prototype.forEach.call(source.querySelectorAll('.reader-paragraph'), function (paragraph) {
      var text = paragraph.textContent.trim();
      var scene = /^[*＊※·•—_\-\s]{3,}$/.test(text);
      paragraph.classList.toggle('gilded-scene', scene);
      paragraph.classList.toggle('gilded-dialogue', /^[“「『"]/.test(text));
      paragraph.classList.toggle('gilded-lead', ordinal === 0 && !!text && !scene);
      if (text && !scene) {
        ordinal++;
        paragraph.setAttribute('data-gilded-ordinal', ordinal < 10 ? '0' + ordinal : String(ordinal));
      } else {
        paragraph.removeAttribute('data-gilded-ordinal');
      }
    });
    Array.prototype.forEach.call(source.querySelectorAll('.reader-chapter-title'), function (title) {
      var match = title.textContent.match(/第\s*([零〇一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾\d]+)\s*[章回节卷]/);
      title.setAttribute('data-gilded-chapter', match ? match[1] : '✦');
    });
  });
  readerTemplate.on('afterLayout', function (event) {
    event.pages.forEach(function (page, index) {
      var fill = page.querySelector('.gilded-progress i');
      if (fill) fill.style.transform = 'scaleX(' + ((index + 1) / event.pages.length) + ')';
    });
  });
}());
