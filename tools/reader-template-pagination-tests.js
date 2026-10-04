(function () {
  'use strict';

  // Run inside a browser fixture with the actual runtime functions exposed by
  // the fixture loader. No production test API or duplicate paginator is used.
  window.runReaderTemplatePaginationTests = function (runtime) {
    var results = [];
    function assert(value, message) { if (!value) throw new Error(message); }
    function test(name, body) {
      var region = document.createElement('div');
      region.style.cssText = 'position:fixed;left:-10000px;top:0;width:80px;height:300px;overflow:hidden;';
      var content = document.createElement('div');
      content.style.cssText = 'width:100%;font:18px/30px monospace;writing-mode:horizontal-tb;';
      region.appendChild(content); document.body.appendChild(region);
      try {
        body(region, content);
        results.push({name: name, passed: true});
      } catch (error) { results.push({name: name, passed: false, error: String(error)}); }
      finally { region.remove(); }
    }
    function paragraph(root, text) {
      var node = document.createElement('p');
      node.style.cssText = 'margin:0;padding:0;white-space:pre;text-indent:0;';
      node.textContent = text; root.appendChild(node); return node;
    }
    function clipQuote(region, paragraph) {
      var range = document.createRange(), node = paragraph.firstChild;
      range.setStart(node, node.length - 1); range.setEnd(node, node.length);
      var quote = range.getBoundingClientRect(), bounds = region.getBoundingClientRect();
      // Fonts differ across desktop Chromium and Android. Place the viewport
      // through the actual quote, leaving every preceding character inside.
      region.style.width = (quote.left - bounds.left + quote.width / 2) + 'px';
    }
    [0, 1, 2].forEach(function (row) {
      test('inline quote overflow on row ' + (row + 1) + ' is not a page boundary', function (region, content) {
        for (var index = 0; index < row; index++) paragraph(content, 'text');
        clipQuote(region, paragraph(content, 'abcdefg”'));
        var bounds = region.getBoundingClientRect();
        assert(runtime.completeContentOverflow(content, bounds), 'fixture must exercise real horizontal overflow');
        assert(!runtime.pageContentOverflow(content, bounds), 'an inline glyph edge moved the remaining page away');
      });
    });
    test('the first row below the page remains the break, even with an earlier wide quote', function (region, content) {
      region.style.height = '60px';
      clipQuote(region, paragraph(content, 'abcdefg”')); paragraph(content, 'text');
      var next = paragraph(content, 'next');
      var overflow = runtime.pageContentOverflow(content, region.getBoundingClientRect());
      assert(overflow && overflow.startContainer === next.firstChild && overflow.startOffset === 0,
        'break must point to the third row, not the quote on the first row');
    });
    ['vertical-rl', 'vertical-lr'].forEach(function (writing) {
      test(writing + ' separates the inline edge from its forward block edge', function (region, content) {
        content.style.writingMode = writing;
        content.style.height = '100%';
        var text = paragraph(content, '”');
        text.style.cssText += 'position:absolute;left:30px;top:302px;';
        var bounds = region.getBoundingClientRect();
        assert(runtime.completeContentOverflow(content, bounds), 'vertical inline overflow fixture is inside the viewport');
        assert(!runtime.pageContentOverflow(content, bounds), 'vertical inline overflow became a page break');
        text.style.top = '0'; text.style.left = writing === 'vertical-rl' ? '-30px' : '90px';
        assert(runtime.pageContentOverflow(content, bounds), 'forward block overflow was missed');
      });
    });
    return results;
  };
}());
