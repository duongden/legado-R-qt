(function () {
  'use strict';

  // Chromium discovers the breaks once for each stable flow geometry. Pages
  // still receive their own DOM, so author hooks, selection and source mapping
  // use the same interface as the compatibility paginator.
  var unsupported = 'table,ruby,math,iframe,object,embed,canvas,video,audio,script,style,link,input,textarea,select';
  var stats = {layouts: 0, pages: 0, fallback: []};
  function sourceToken(prepared, node, offset) {
    // Paged distinguishes a whole new paragraph from a continuation by its
    // token node. A zero-offset Text token would incorrectly mark its untouched
    // ancestors as split when the next region uses the compatibility engine.
    if (!offset) while (node.parentNode !== prepared.source && !node.previousSibling) node = node.parentNode;
    return {node: node, offset: offset || 0};
  }
  function key(prepared, token) {
    if (token) token = sourceToken(prepared, token.node, token.offset);
    return token ? prepared.order.get(token.node) + ':' + (token.offset || 0) : 'start';
  }
  function before(node) {
    return {node: node.parentNode, offset: Array.prototype.indexOf.call(node.parentNode.childNodes, node)};
  }
  function pointAt(node, offset, root) {
    if (offset) return {node: node, offset: offset};
    // Move empty ancestor shells with their first child. Leaving a duplicate
    // empty paragraph behind can paint an extra drop cap or paragraph margin.
    while (node.parentNode !== root && !node.previousSibling) node = node.parentNode;
    return before(node);
  }
  function markSplit(root, token, prepared, start) {
    if (!token) return;
    var child = token.node, partial = child.nodeType === Node.TEXT_NODE && token.offset > 0;
    for (var parent = child.parentNode; parent && parent !== prepared.source; child = parent, parent = parent.parentNode) {
      partial = partial || !!child.previousSibling;
      if (!partial || parent.nodeType !== Node.ELEMENT_NODE) continue;
      var ref = parent.getAttribute('data-ref');
      var copy = ref && root.querySelector('[data-ref="' + ref + '"]');
      if (!copy) continue;
      copy.setAttribute(start ? 'data-split-from' : 'data-split-to', ref);
      if (start && copy.matches('p.reader-paragraph')) copy.setAttribute('data-reader-continuation', '');
      if (copy.hasAttribute('data-legado-highlight-spacing')) {
        copy.style.setProperty(start ? 'margin-left' : 'margin-right', '0', 'important');
      }
    }
  }
  function cloneRange(root, start, end, prepared) {
    var range = document.createRange();
    range.setStart(start.point.node, start.point.offset);
    range.setEnd(end.point.node, end.point.offset);
    var fragment = range.cloneContents(), ancestor = range.commonAncestorContainer;
    if (ancestor.nodeType === Node.TEXT_NODE) ancestor = ancestor.parentNode;
    // Range.cloneContents omits common ancestors. Preserve the source wrappers
    // and their identities even when a whole page is inside one nested span.
    while (ancestor && ancestor !== root) {
      var shell = ancestor.cloneNode(false);
      shell.appendChild(fragment); fragment = shell; ancestor = ancestor.parentNode;
    }
    var holder = document.createElement('div'); holder.appendChild(fragment);
    markSplit(holder, start.token, prepared, true);
    markSplit(holder, end.token, prepared, false);
    return holder;
  }
  async function build(content, viewport, prepared, token, options, style, firstOnly) {
    options.checkpoint();
    stats.layouts++;
    var surface = content.cloneNode(false), cloned = prepared.source.cloneNode(true);
    var originals = new WeakMap(), clones = new WeakMap();
    var originalWalker = document.createTreeWalker(prepared.source, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
    var copyWalker = document.createTreeWalker(cloned, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), original, copy;
    while ((original = originalWalker.nextNode())) {
      copy = copyWalker.nextNode(); originals.set(copy, original); clones.set(original, copy);
    }
    if (token) {
      var target = clones.get(token.node);
      if (!target) throw new Error('unknown-source-break');
      var cut = document.createRange(), cutPoint = pointAt(target, token.offset || 0, cloned);
      cut.setStart(cloned, 0); cut.setEnd(cutPoint.node, cutPoint.offset); cut.deleteContents();
      markSplit(cloned, token, prepared, true);
    }
    surface.appendChild(cloned);
    var vertical = style.writingMode.indexOf('vertical') === 0;
    var bounds = viewport.getBoundingClientRect(), width = bounds.width, height = bounds.height, gap = 32;
    surface.style.setProperty('width', width + 'px', 'important');
    surface.style.setProperty('height', height + 'px', 'important');
    surface.style.setProperty('column-count', 'auto', 'important');
    surface.style.setProperty('column-gap', gap + 'px', 'important');
    surface.style.setProperty('column-fill', 'auto', 'important');
    viewport.replaceChildren(surface);
    try {
      var inlineSize;
      function sizeColumns() {
        var surfaceStyle = getComputedStyle(surface);
        inlineSize = vertical ? height - (parseFloat(surfaceStyle.paddingTop) || 0) - (parseFloat(surfaceStyle.paddingBottom) || 0) :
          width - (parseFloat(surfaceStyle.paddingLeft) || 0) - (parseFloat(surfaceStyle.paddingRight) || 0);
        surface.style.setProperty('column-width', inlineSize + 'px', 'important');
      }
      sizeColumns();
      await Promise.all(Array.prototype.map.call(surface.querySelectorAll('img'), function (image) { return options.imageReady(image, true); }));
      if (options.fontsReady) await options.fontsReady(surface);
      options.checkpoint();
      // Fit glyph overhang before discovering column breaks. The same insets
      // are copied to each page below, so cloning cannot turn a right-edge
      // punctuation box into a false fragment-overflow/Paged fallback.
      for (var attempt = 0; attempt < 8; attempt++) {
        if (!options.fitMetrics(surface, bounds, inlineSize + gap, gap)) break;
        sizeColumns();
        options.checkpoint();
      }
      var origin = surface.getBoundingClientRect(), step = inlineSize + gap;
      var fittedStyle = getComputedStyle(surface);
      var columnStart = vertical ? origin.top + (parseFloat(fittedStyle.paddingTop) || 0) : origin.left + (parseFloat(fittedStyle.paddingLeft) || 0);
      function column(rect) {
        // Select the fragment's column across the middle of the empty gutter.
        // A hanging quote's left/top edge can precede the column's origin.
        var centre = vertical ? (rect.top + rect.bottom) / 2 : (rect.left + rect.right) / 2;
        return Math.max(0, Math.floor((centre - columnStart + gap / 2) / step));
      }
      var points = [{point: {node: surface, offset: 0}, token: token || null}], lookup = new Map();
      lookup.set(key(prepared, token), 0);
      var current = 0, lastYield = performance.now(), visits = 0;
      async function checkpoint() {
        options.checkpoint();
        if (performance.now() - lastYield > 12) {
          await options.yieldTask(); options.checkpoint(); lastYield = performance.now();
        }
      }
      function add(node, offset, sourceOffset) {
        var next = {point: pointAt(node, offset, surface), token: sourceToken(prepared, originals.get(node), sourceOffset)};
        var position = key(prepared, next.token);
        if (lookup.has(position)) throw new Error('empty-column');
        points.push(next); lookup.set(position, ++current);
        if (current >= 2048) throw new Error('column-budget');
      }
      var range = document.createRange();
      var walker = document.createTreeWalker(surface, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
      while ((node = walker.nextNode())) {
        if (++visits % 64 === 0) await checkpoint();
        original = originals.get(node);
        if (!original) continue;
        if (node.nodeType === Node.ELEMENT_NODE) {
          if (!node.matches('img,svg,hr')) continue;
          var mediaColumn = column(node.getBoundingClientRect());
          if (mediaColumn > current) {
            if (mediaColumn !== current + 1) throw new Error('empty-column');
            add(node, 0, 0);
          }
        } else {
          if (!node.data.trim() || node.parentElement.closest('svg,[data-reader-text-ignore],[data-legado-image-action]')) continue;
          range.selectNodeContents(node);
          var rects = range.getClientRects(), last = current;
          for (var rectIndex = 0; rectIndex < rects.length; rectIndex++) {
            if (rects[rectIndex].width > .1 && rects[rectIndex].height > .1) last = Math.max(last, column(rects[rectIndex]));
          }
          var baseOffset = original.length - node.length;
          for (var targetColumn = current + 1; targetColumn <= last; targetColumn++) {
            // Prefix ranges remain monotonic with mixed-direction inline text.
            // Every token refers to the original Text node, never a text search.
            var low = 1, high = node.length;
            while (low < high) {
              var middle = Math.floor((low + high) / 2); range.setStart(node, 0); range.setEnd(node, middle);
              var reaches = Array.prototype.some.call(range.getClientRects(), function (rect) {
                return rect.width > .1 && rect.height > .1 && column(rect) >= targetColumn;
              });
              if (reaches) high = middle; else low = middle + 1;
            }
            var sourceOffset = options.graphemeStart(original.data, baseOffset + low - 1), offset = sourceOffset - baseOffset;
            if (offset < 0) throw new Error('partial-grapheme');
            add(node, offset, sourceOffset);
            if (firstOnly) break;
            if (current % 8 === 0) await checkpoint();
          }
        }
        if (firstOnly && current) break;
      }
      if (!firstOnly || !current) points.push({point: {node: surface, offset: surface.childNodes.length}, token: null});
      options.checkpoint();
      return {root: surface, points: points, lookup: lookup};
    } finally {
      viewport.replaceChildren(content);
    }
  }
  async function render(content, viewport, prepared, token, options) {
    var slot = viewport.parentElement;
    if (!slot || slot.getAttribute('data-reader-flow-pagination') !== 'columns') return null;
    options.checkpoint();
    var style = getComputedStyle(content), bounds = viewport.getBoundingClientRect();
    var page = viewport.closest('.reader-template-page');
    var geometry = [page && page.getAttribute('data-reader-page'), slot.className, slot.getAttribute('style'),
      bounds.width, bounds.height, style.font, style.fontSize, style.lineHeight, style.writingMode, style.direction,
      style.letterSpacing, style.wordSpacing, style.textAlign, style.textIndent, style.textTransform, style.textOrientation,
      style.padding, style.borderWidth, slot.getAttribute('data-reader-initial-layout')].join('|');
    var state = prepared.browserFlow;
    if (!state) state = prepared.browserFlow = {caches: new Map(), blocked: new Map(), builds: 0,
      unsupported: !!prepared.source.querySelector(unsupported)};
    if (state.blocked.has(geometry)) {
      content.setAttribute('data-reader-pagination-fallback', state.blocked.get(geometry)); return null;
    }
    var savedStyle = content.getAttribute('style');
    try {
      if (state.unsupported || style.direction === 'rtl' || style.transform !== 'none' ||
          !/^(horizontal-tb|vertical-rl|vertical-lr)$/.test(style.writingMode)) throw new Error('unsupported-content');
      var cache = state.caches.get(geometry), position = cache && cache.lookup.get(key(prepared, token));
      if (position == null) {
        // A script that continually changes its body region remains supported by
        // Paged. Do not rebuild an entire chapter once per changing page.
        if (++state.builds > 4) throw new Error('changing-flow');
        cache = await build(content, viewport, prepared, token, options, style, page && page.getAttribute('data-reader-page') === 'first');
        state.caches.set(geometry, cache); position = 0;
      }
      var start = cache.points[position], end = cache.points[position + 1];
      if (!end) throw new Error('missing-break');
      var fragment = cloneRange(cache.root, start, end, prepared);
      content.replaceChildren.apply(content, Array.prototype.slice.call(fragment.childNodes));
      ['padding-top', 'padding-right', 'padding-bottom', 'padding-left'].forEach(function (property) {
        var value = cache.root.style.getPropertyValue(property);
        if (value) content.style.setProperty(property, value, cache.root.style.getPropertyPriority(property));
      });
      for (var attempt = 0; attempt < 8; attempt++) {
        if (!options.fitMetrics(content, bounds)) break;
      }
      if (options.overflow(content, bounds)) throw new Error('fragment-overflow');
      options.checkpoint();
      content.setAttribute('data-reader-pagination-engine', 'columns'); stats.pages++;
      return {breakToken: end.token, browserFlow: true};
    } catch (error) {
      // Cancellation and the shared active-time deadline are fatal to this
      // attempt. They must never be disguised as a compatibility fallback.
      options.checkpoint();
      var reason = String(error.message || error);
      state.blocked.set(geometry, reason); state.caches.delete(geometry);
      if (stats.fallback.length < 16) stats.fallback.push({reason: reason, geometry: geometry});
      content.replaceChildren();
      if (savedStyle === null) content.removeAttribute('style'); else content.setAttribute('style', savedStyle);
      content.setAttribute('data-reader-pagination-fallback', reason);
      return null;
    }
  }
  window.ReaderBrowserTemplateFlow = {render: render, stats: stats};
}());
