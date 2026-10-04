(function () {
  'use strict';

  // This runtime belongs to the opaque-origin template frame. Native capability
  // checks and navigation live in template-host.js, outside author JavaScript.
  var init = window.__readerTemplateInit || {};
  var template = init.template || {};
  var channel = 'legado-reader-template';
  var token = init.token;
  var active = true, initialized = false, failure = null;
  var generation = 0, layoutRevision = 0, visualRevision = 0, contentRevision = 0;
  var layoutBusy = true, visualBusy = false, running = false, requested = false;
  var layoutTimer = 0, sourceImagesPending = 0, resourcesReady = false, resourcesFailed = false;
  var pages = [], pageMap = [], pageIndex = 0, committedRoot = null, candidateRoot = null;
  var visibilityPages = null, visiblePage = null;
  var activationBoundary = '', activationTargetRevision = -1;
  var fields = Object.assign({}, init.fields || {});
  var fieldBindingPages = null, fieldBindings = new Map();
  var continuous = template.type === 'scroll';
  var scrollMode = continuous || init.scrollMode === true, sourcePreparing = false;
  var scrollPositions = [], scrollPixels = [], scrollShellRenderable = false;
  var stageScrollers = new WeakMap();
  var viewport = {width: 1, height: 1};
  var source = document.createDocumentFragment();
  var records = new Map(), imageIds = new Set(), sourceObserver, pageObserver, sizeObserver;
  var hooks = Object.create(null), pendingAuthorWork = [], layoutWaiters = [];
  var fieldHookNames = new WeakMap();
  var geometry = '', commandChain = Promise.resolve(), textImageMode = String(init.textImageMode);
  var imageSequence = 0, interactionSequence = 0, lastImageTap = null, lastImageAction = 0;
  var touch = null, imagePress = null, suppressImageClickUntil = 0;
  var scrollTouch = null, scrollFrame = 0, imageOverlayNode = null, imageOverlayClose = null;
  var authorVisualPending = false, authorVisualSerial = 0, mutationTimer = 0;
  var selectionDeferredLayout = false;
  // Presentation is deliberately separate from layout/readiness. Native snapshots
  // see the settled page; only the committed foreground page may animate afterward.
  var motionState = 'settled';
  var lastMotionPage = null, lastMotionState = null, lastMotionReduced = false;
  var motionPages = null, motionPageIndex = -1, motionEnvironment = '';
  var reducedMotion = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  var stylesheetWork = new WeakMap(), backgroundWork = new Map(), backgroundPixels = new Set(), headObserver;
  var sourceRequests = new Set(), timers = new Set();
  var pageMarkupCache = new Map();
  var highlightStyles = [], highlightsInstalled = false;
  var renderBudgetMs = Number(init.renderTimeoutMillis);
  if (!Number.isFinite(renderBudgetMs) || renderBudgetMs <= 0) renderBudgetMs = 180000;
  var pausedAt = document.hidden ? performance.now() : null, pausedDuration = 0;
  function activeTime() { return (pausedAt === null ? performance.now() : pausedAt) - pausedDuration; }
  function updateActiveClock() {
    var now = performance.now();
    if (document.hidden) { if (pausedAt === null) pausedAt = now; }
    else if (pausedAt !== null) { pausedDuration += Math.max(0, now - pausedAt); pausedAt = null; }
  }
  var startupStarted = activeTime();
  var CANCELLED = {};
  var failedImage = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='24' viewBox='0 0 32 24'%3E%3Crect x='1' y='1' width='30' height='22' rx='4' fill='%23888888' fill-opacity='.18'/%3E%3Cpath d='m11 7 10 10m0-10L11 17' stroke='%23888888' stroke-width='2'/%3E%3C/svg%3E";

  function later(callback, delay) {
    var id = setTimeout(function () { timers.delete(id); if (active) callback(); }, delay);
    timers.add(id);
    return id;
  }
  function clearLater(id) { clearTimeout(id); timers.delete(id); }
  function frame() { return new Promise(function (resolve) { requestAnimationFrame(resolve); }); }
  async function twoFrames() { await frame(); await frame(); }
  function post(type, value) {
    if (active) parent.postMessage(Object.assign({}, value || {}, {channel: channel, type: type, token: token}), '*');
  }
  function bounded(value, milliseconds, message) {
    return new Promise(function (resolve, reject) {
      var timer = later(function () { reject(new Error(message)); }, milliseconds);
      Promise.resolve(value).then(function (result) { clearLater(timer); resolve(result); },
        function (error) { clearLater(timer); reject(error); });
    });
  }
  function updateViewport() {
    viewport.width = Math.max(1, Math.round(window.innerWidth || (init.viewport || {}).width || 1));
    viewport.height = Math.max(1, Math.round(window.innerHeight || (init.viewport || {}).height || 1));
  }
  function checkRun(expected) {
    if (!active || expected !== generation) throw CANCELLED;
    if (failure) throw failure;
  }
  function textNodes(element) {
    var nodes = [], walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT), node;
    while ((node = walker.nextNode())) {
      var parentElement = node.parentElement;
      if (parentElement && parentElement.closest('script,style,noscript,[data-reader-text-ignore],[data-legado-image-action]')) continue;
      nodes.push(node);
    }
    return nodes;
  }
  function readableText(element) { return textNodes(element).map(function (node) { return node.data; }).join(''); }
  function pageAt(index) { return pages[continuous ? 0 : index] || null; }
  function pageCount() { return continuous ? Math.max(1, scrollPositions.length) : pages.length; }
  function scrollingElement(stage) { return stageScrollers.get(stage) || stage; }
  function scrollViewport(stage) {
    var node = scrollingElement(stage), rect = node.getBoundingClientRect();
    var left = rect.left + node.clientLeft, top = rect.top + node.clientTop;
    return {left: left, right: left + node.clientWidth, top: top, bottom: top + node.clientHeight,
      width: node.clientWidth, height: node.clientHeight, origin: top - node.scrollTop};
  }
  function configureContinuousScroll(stage, page) {
    var frames = page.querySelectorAll('[data-reader-scroll-viewport]');
    if (!frames.length) return;
    var slots = orderedSlots(page), scroller = frames[0];
    if (frames.length !== 1 || slots.length !== 1 || scroller === slots[0] || !scroller.contains(slots[0])) {
      throw new Error('固定画框的滚动模板需要一个 data-reader-scroll-viewport 容器，包住唯一的正文区域');
    }
    stageScrollers.set(stage, scroller);
    stage.setAttribute('data-reader-scroll-layout', 'framed');
  }
  function scrollIndex(offset) {
    var low = 0, high = scrollPositions.length - 1;
    while (low < high) {
      var middle = Math.ceil((low + high) / 2);
      if (scrollPositions[middle] <= offset + 1) low = middle; else high = middle - 1;
    }
    return low;
  }
  function prepareSource() {
    var parsed = new DOMParser().parseFromString(String(init.sourceHtml || ''), 'text/html');
    Array.prototype.forEach.call(parsed.head.querySelectorAll('style,link[rel~="stylesheet"]'), function (node) {
      var imported = document.importNode(node, true);
      if (node.id === 'legado-reeden-highlight-style') {
        highlightStyles.push(imported); return;
      }
      trackStyles(imported);
      document.head.appendChild(imported);
    });
    Array.prototype.forEach.call(parsed.body.childNodes, function (node) { source.appendChild(document.importNode(node, true)); });
    var cursor = 0, plainText = String(init.plainText || '');
    Array.prototype.forEach.call(source.querySelectorAll('[data-reader-block]'), function (node) {
      var id = node.getAttribute('data-reader-block'), kind = node.getAttribute('data-reader-kind');
      if (!id || records.has(id)) throw new Error('正文块标识重复，无法建立阅读位置');
      var rawOffset = node.getAttribute('data-legado-text-offset');
      var start = rawOffset == null ? cursor : Number(rawOffset);
      var text = kind === 'placeholder' ? '' : readableText(node);
      var canonical = rawOffset != null;
      if (!Number.isSafeInteger(start) || start < 0 || start > plainText.length ||
          (canonical && (plainText.slice(start, start + text.length) !== text || plainText.charAt(start + text.length) !== '\n'))) {
        throw new Error('正文文字与阅读位置不一致，已停止模板分页');
      }
      var end = canonical ? start + text.length + 1 : start;
      records.set(id, {id: id, kind: kind, start: start, end: end, text: text, canonical: canonical});
      cursor = Math.max(cursor, end);
    });
    if (!records.size || cursor !== plainText.length) throw new Error('正文缺少完整的位置标记');
    Array.prototype.forEach.call(source.querySelectorAll('img[data-legado-image-id]'), function (image) {
      var id = image.getAttribute('data-legado-image-id');
      if (imageIds.has(id)) throw new Error('正文图片标识重复');
      imageIds.add(id);
      if (image.getAttribute('loading') === 'lazy') image.setAttribute('loading', 'eager');
    });
  }
  async function installHighlights() {
    if (highlightsInstalled) return;
    highlightsInstalled = true;
    highlightStyles.forEach(function (style) { trackStyles(style); document.head.appendChild(style); });
    if (headObserver) headObserver.takeRecords();
    await waitStyles(document.head);
  }
  async function emitHook(name, value, selectedCallbacks) {
    var callbacks = selectedCallbacks || (hooks[name] || []).slice();
    for (var index = 0; index < callbacks.length; index++) {
      await bounded(callbacks[index](value, api), 8000, '模板 ' + name + ' 回调未完成');
    }
    await drainAuthorWork();
  }
  async function drainAuthorWork() {
    var rounds = 0;
    while (pendingAuthorWork.length) {
      if (++rounds > 32) throw new Error('模板持续创建待处理任务，无法完成分页');
      await bounded(Promise.all(pendingAuthorWork.splice(0)), 8000, '模板等待的资源或脚本超时');
    }
  }
  var api = {
    source: source,
    fields: fields,
    viewport: viewport,
    on: function (name, callback, options) {
      if (typeof callback !== 'function') throw new TypeError('readerTemplate.on requires a function');
      if (name === 'fieldsChange' && options && Array.isArray(options.fields)) {
        var original = callback;
        callback = function (event, api) { return original(event, api); };
        fieldHookNames.set(callback, new Set(options.fields.map(String)));
      }
      var list = hooks[name] || (hooks[name] = []);
      list.push(callback);
      return function () { var index = list.indexOf(callback); if (index >= 0) list.splice(index, 1); };
    },
    requestLayout: function () { requestLayout('author'); },
    waitUntil: function (promise) {
      var work = Promise.resolve(promise);
      // Register a handler immediately, even when the author schedules work from
      // an asynchronous event before the next layout callback drains the queue.
      work.catch(function () {});
      pendingAuthorWork.push(work);
      return work;
    },
    get pageIndex() { return pageIndex; },
    get pageCount() { return pageCount(); },
    get pages() { return pages.slice(); },
    get currentPage() { return pageAt(pageIndex); },
    get motionState() { return pageAt(pageIndex) ? pageAt(pageIndex).getAttribute('data-reader-motion') : motionState; },
    get type() { return continuous ? 'scroll' : 'paged'; }
  };
  window.readerTemplate = api;

  function syncMotion() {
    function update(page, index) {
      if (!page) return;
      var next = layoutBusy || (reducedMotion && reducedMotion.matches) ? 'settled' :
        document.hidden || (!continuous && index !== pageIndex) ? 'paused' : motionState;
      var entry = page.getAttribute('data-reader-entry') || 'pending';
      if (next === 'running' && entry === 'pending') entry = 'playing';
      else if (next !== 'running' && entry === 'playing') entry = 'done';
      if (page.getAttribute('data-reader-entry') !== entry) page.setAttribute('data-reader-entry', entry);
      if (page.getAttribute('data-reader-motion') !== next) page.setAttribute('data-reader-motion', next);
    }
    var environment = [layoutBusy, !!(reducedMotion && reducedMotion.matches), document.hidden].join('|');
    if (motionPages !== pages || motionEnvironment !== environment) {
      pages.forEach(update);
      motionPages = pages; motionEnvironment = environment;
    } else {
      if (motionPageIndex !== pageIndex) update(pageAt(motionPageIndex), motionPageIndex);
      update(pageAt(pageIndex), pageIndex);
    }
    motionPageIndex = pageIndex;
    var page = pageAt(pageIndex);
    if (!page || !active || failure) return;
    // New page DOM is announced only after its observers are attached. The old
    // current page still receives settled while pagination stops its animation.
    if (layoutBusy && page !== lastMotionPage) return;
    var state = page.getAttribute('data-reader-motion');
    var reduce = !!(reducedMotion && reducedMotion.matches);
    if (page === lastMotionPage && state === lastMotionState && reduce === lastMotionReduced) return;
    var event = {page: page, pageIndex: pageIndex, pageCount: pageCount(), state: state,
      previousPage: lastMotionPage, previousState: lastMotionState, reducedMotion: reduce};
    // Commit the notification identity before author code can request another layout.
    lastMotionPage = page; lastMotionState = state; lastMotionReduced = reduce;
    // Presentation callbacks may return an infinite animation's Promise. Their
    // synchronous pause/static pose runs before the native frame barrier, while
    // asynchronous rejection still follows the normal template-error path.
    (hooks.motionChange || []).slice().forEach(function (callback) {
      if (!active || failure) return;
      try { Promise.resolve(callback(event, api)).catch(fail); } catch (error) { fail(error); }
    });
  }
  function setMotionState(value) {
    if (!/^(settled|running|paused)$/.test(String(value))) return;
    var changed = motionState !== value;
    motionState = value;
    syncMotion();
    // An author callback may already have changed the state by requesting layout.
    if (changed && motionState === value) post('motionState', {state: value});
  }

  function appendStyle(css, id) {
    var style = document.createElement('style');
    if (id) style.id = id;
    style.textContent = String(css || '');
    trackStyles(style);
    document.head.appendChild(style);
    return style;
  }
  async function runScripts(root) {
    var scripts = Array.prototype.slice.call(root.querySelectorAll('script'));
    for (var index = 0; index < scripts.length; index++) {
      var old = scripts[index], script = document.createElement('script');
      Array.prototype.forEach.call(old.attributes, function (attribute) { script.setAttribute(attribute.name, attribute.value); });
      script.textContent = old.textContent;
      var type = (script.getAttribute('type') || '').trim().toLowerCase();
      var executable = !type || /^(?:text|application)\/(?:java|ecma)script$/.test(type) || type === 'module';
      if (executable && (script.src || type === 'module')) {
        await bounded(new Promise(function (resolve, reject) {
          script.onload = resolve;
          script.onerror = function () { reject(new Error('模板脚本加载失败：' + (script.src || 'module'))); };
          old.replaceWith(script);
        }), 8000, '模板脚本加载超时');
      } else old.replaceWith(script);
      if (failure) throw failure;
    }
  }
  function bindFields(page, index, count) {
    Array.prototype.forEach.call(page.querySelectorAll('[data-reader-field]'), function (node) {
      var name = node.getAttribute('data-reader-field');
      var value = name === 'page' ? (index + 1) + '/' + (count || '…') :
        name === 'pageIndex' ? index + 1 : name === 'pageCount' ? count || '…' : fields[name];
      value = value == null ? '' : String(value);
      if (node.textContent !== value) node.textContent = value;
    });
  }
  function indexedFieldBindings() {
    if (fieldBindingPages === pages) return fieldBindings;
    fieldBindings = new Map();
    pages.forEach(function (page) {
      Array.prototype.forEach.call(page.querySelectorAll('[data-reader-field]'), function (node) {
        var name = node.getAttribute('data-reader-field');
        // These values belong to each already-paginated page, not the current
        // Android page label. They were bound when that page was constructed.
        if (/^(page|pageIndex|pageCount)$/.test(name)) return;
        if (!fieldBindings.has(name)) fieldBindings.set(name, []);
        fieldBindings.get(name).push({page: page, node: node});
      });
    });
    fieldBindingPages = pages;
    return fieldBindings;
  }
  async function updateFields(value) {
    value = value && typeof value === 'object' ? value : {};
    var changed = Object.keys(Object.assign({}, fields, value)).filter(function (key) {
      return key !== 'contentRevision' && fields[key] !== value[key];
    });
    Object.keys(fields).forEach(function (key) { delete fields[key]; });
    Object.assign(fields, value);
    if (!changed.length) return false;
    var bindings = indexedFieldBindings(), updates = [], affected = new Set();
    changed.forEach(function (name) {
      (bindings.get(name) || []).forEach(function (entry) {
        var text = fields[name] == null ? '' : String(fields[name]);
        if (entry.node.textContent === text) return;
        updates.push({node: entry.node, text: text}); affected.add(entry.page);
      });
    });
    var callbacks = (hooks.fieldsChange || []).filter(function (callback) {
      var names = fieldHookNames.get(callback);
      return !names || changed.some(function (name) { return names.has(name); });
    });
    var hasHook = callbacks.length > 0;
    // No DOM change and no author callback: page numbers need no chapter-wide
    // geometry scan and no extra compositor frames before the actual page turn.
    if (!updates.length && !hasHook) return false;
    var measuredPages = hasHook ? pages : Array.from(affected);
    var before = geometryOf(measuredPages);
    updates.forEach(function (entry) {
      // Reuse the Text node so progress/time updates do not replace child lists
      // throughout every template page. All bindings stay current for scripts.
      var child = entry.node.firstChild;
      if (entry.text && child && child.nodeType === Node.TEXT_NODE && !child.nextSibling) child.data = entry.text;
      else entry.node.textContent = entry.text;
    });
    if (hasHook) {
      await emitHook('fieldsChange', {fields: fields, changedFields: changed}, callbacks);
      await twoFrames();
    }
    // Native text assignments are synchronous. The command's existing visual
    // barrier waits for drawing and observers after the target page is shown.
    if (!sameGeometry(geometryOf(measuredPages), before)) requestLayout('field-geometry');
    else visualRevision++;
    return true;
  }
  function orderedSlots(page) {
    return Array.prototype.map.call(page.querySelectorAll('[data-reader-flow]'), function (node, index) {
      if (node.parentElement.closest('[data-reader-flow]')) throw new Error('正文区域不能相互嵌套');
      var value = node.getAttribute('data-reader-order');
      return {node: node, index: index, order: value != null && Number.isFinite(Number(value)) ? Number(value) : index};
    }).sort(function (a, b) { return a.order - b.order || a.index - b.index; }).map(function (entry) { return entry.node; });
  }
  function slotBox(slot) {
    var style = getComputedStyle(slot), rect = slot.getBoundingClientRect();
    var px = function (name) { return parseFloat(style[name]) || 0; };
    return {
      width: Math.max(0, slot.clientWidth - px('paddingLeft') - px('paddingRight')),
      // clientHeight rounds up fractional slots and can admit a clipped last row.
      height: Math.max(0, rect.height - px('borderTopWidth') - px('borderBottomWidth') - px('paddingTop') - px('paddingBottom')),
      left: rect.left + px('borderLeftWidth') + px('paddingLeft'),
      top: rect.top + px('borderTopWidth') + px('paddingTop'),
      font: style.font, lineHeight: style.lineHeight, letterSpacing: style.letterSpacing,
      writingMode: style.writingMode, direction: style.direction
    };
  }
  function geometryOf(list) {
    return JSON.stringify(list.map(function (page) {
      var pageRect = page.getBoundingClientRect(), stage = page.parentElement;
      var scroller = continuous ? scrollingElement(stage) : null;
      var framed = scroller && scroller !== stage;
      var slots = orderedSlots(page).map(function (slot) {
        var box = slotBox(slot);
        // Scrolling changes viewport coordinates, not pagination geometry.
        box.left -= pageRect.left; box.top -= pageRect.top;
        if (framed && scroller.contains(slot)) { box.left += scroller.scrollLeft; box.top += scroller.scrollTop; }
        return box;
      });
      if (!continuous) return slots;
      var box = framed ? scrollViewport(stage) : null;
      return {height: pageRect.height, slots: slots, scrollViewport: box ? {
        left: box.left - pageRect.left, top: box.top - pageRect.top, width: box.width, height: box.height
      } : null};
    }));
  }
  function sameGeometry(first, second) {
    if (first === second) return true;
    if (!continuous || !first || !second) return false;
    // Scrolling a long document on a fractional display scale introduces small
    // float errors in viewport-relative DOMRects. They are not layout changes.
    // Keep typography and structure exact; use the paint check's half-pixel
    // tolerance only for geometry so clocks and colors do not rebuild the DOM.
    function equal(a, b) {
      if (a === b) return true;
      if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) <= .5;
      if (!a || !b || typeof a !== 'object' || typeof b !== 'object') return false;
      var keys = Object.keys(a);
      return keys.length === Object.keys(b).length && keys.every(function (key) {
        return Object.prototype.hasOwnProperty.call(b, key) && equal(a[key], b[key]);
      });
    }
    return equal(JSON.parse(first), JSON.parse(second));
  }
  function trackStyles(root) {
    var nodes = root.matches && root.matches('style,link[rel~="stylesheet"]') ? [root] :
      Array.prototype.slice.call(root.querySelectorAll('style,link[rel~="stylesheet"]'));
    nodes.forEach(function (node) {
      if (stylesheetWork.has(node)) return;
      if (node.tagName === 'STYLE' && !/@import\b/i.test(node.textContent)) return;
      var work = new Promise(function (resolve) {
        var settled = false, timer;
        function finish(failed) {
          if (settled) return; settled = true;
          clearLater(timer); node.removeEventListener('load', loaded); node.removeEventListener('error', error);
          if (failed) resourcesFailed = true;
          resolve();
        }
        function loaded() { finish(false); }
        function error() { finish(true); }
        node.addEventListener('load', loaded); node.addEventListener('error', error);
        timer = later(function () { finish(true); }, 3500);
        // An already-connected cached link can have completed before a dynamic
        // author mutation is observed. A sheet is then usable for measurement.
        if (node.isConnected && node.tagName === 'LINK' && node.sheet) finish(false);
      });
      stylesheetWork.set(node, work);
    });
  }
  async function waitStyles(root) {
    trackStyles(root);
    var nodes = Array.prototype.slice.call(root.querySelectorAll('style,link[rel~="stylesheet"]'));
    await Promise.all(nodes.map(function (node) { return stylesheetWork.get(node); }));
  }
  async function waitBackgrounds(root) {
    var nodes = [root].concat(Array.prototype.slice.call(root.querySelectorAll('*'))), promises = [];
    function add(url) {
      if (!url) return;
      if (!backgroundWork.has(url)) {
        var image = new Image(); image.src = url;
        backgroundWork.set(url, imageReady(image, false).then(function (ready) { if (ready) backgroundPixels.add(url); }));
      }
      promises.push(backgroundWork.get(url));
    }
    for (var index = 0; index < nodes.length; index++) {
      var node = nodes[index];
      ['','::before','::after'].forEach(function (pseudo) {
        var style = getComputedStyle(node, pseudo || null), expression = style.backgroundImage + ' ' + style.borderImageSource + ' ' + style.content;
        var pattern = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)/g, match;
        while ((match = pattern.exec(expression))) add((match[1] || match[2] || match[3] || '').trim());
      });
      if (node.namespaceURI === 'http://www.w3.org/2000/svg' && node.localName === 'image') add(node.getAttribute('href') || node.getAttribute('xlink:href'));
      if (index % 200 === 199) await new Promise(function (resolve) { later(resolve, 0); });
    }
    await Promise.all(promises);
  }
  async function imageReady(image, replaceFailure) {
    var url = image.currentSrc || image.getAttribute('src') || image.getAttribute('srcset');
    if (!url) return false;
    try {
      await bounded(new Promise(function (resolve, reject) {
        var settled = false;
        function finish(error) {
          if (settled) return;
          settled = true;
          image.removeEventListener('load', loaded); image.removeEventListener('error', failed);
          if (error) reject(error); else resolve();
        }
        function failed() { finish(new Error('图片加载失败')); }
        function loaded() {
          var decoded = image.decode ? image.decode() : Promise.resolve();
          Promise.resolve(decoded).then(function () {
            finish(image.naturalWidth > 0 && image.naturalHeight > 0 ? null : new Error('图片没有可显示的像素'));
          }, failed);
        }
        image.addEventListener('load', loaded); image.addEventListener('error', failed);
        if (image.complete) loaded();
      }), 6000, '图片解码超时');
      return true;
    } catch (error) {
      resourcesFailed = true;
      if (replaceFailure && image.getAttribute('src') !== failedImage) {
        image.setAttribute('data-legado-image-state', 'failed');
        image.setAttribute('aria-label', '图片加载失败');
        image.src = failedImage;
        return imageReady(image, false);
      }
      return false;
    }
  }
  async function loadFonts(slot) {
    if (!document.fonts) return;
    // Request fonts used by the composed descendants before observing ready.
    slot.getBoundingClientRect();
    var style = getComputedStyle(slot);
    try {
      if (document.fonts.load && style.font) await bounded(document.fonts.load(style.font, '正文Aa'), 3500, '模板字体加载超时');
      await bounded(document.fonts.ready, 3500, '模板字体尚未就绪');
    } catch (_) { resourcesFailed = true; }
  }
  function prepareLayoutSource() {
    var clone = source.cloneNode(true), sequence = 0, order = new WeakMap(), elements = new Map();
    var walker = document.createTreeWalker(clone, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
    while ((node = walker.nextNode())) {
      order.set(node, sequence++);
      if (node.nodeType === Node.ELEMENT_NODE) {
        node.setAttribute('data-ref', 'reader-source-' + sequence);
        elements.set(node.getAttribute('data-ref'), node);
        if (node.id) node.setAttribute('data-id', node.id);
      }
    }
    return {source: clone, order: order, elements: elements};
  }
  function rememberRenderedSource(clone, original, sources) {
    if (clone.nodeType !== original.nodeType) throw new Error('分页器改变了正文节点结构');
    var offset = 0;
    if (clone.nodeType === Node.TEXT_NODE) {
      offset = original.length - clone.length;
      if (offset < 0 || original.data.slice(offset) !== clone.data) throw new Error('分页器改变了正文文字');
    }
    sources.set(clone, {node: original, offset: offset});
    // Paged deep-clones paragraphs and inline elements. Their nested Text nodes
    // do not receive individual renderNode hooks, so retain the entire pairing.
    var clonedChild = clone.firstChild, originalChild = original.firstChild;
    while (clonedChild) {
      if (!originalChild) throw new Error('分页器添加了未知正文节点');
      rememberRenderedSource(clonedChild, originalChild, sources);
      clonedChild = clonedChild.nextSibling; originalChild = originalChild.nextSibling;
    }
  }
  function installSourceBreaks(layout, prepared) {
    var sources = new WeakMap(), originalCreate = layout.createBreakToken;
    layout.hooks.onBreakToken.register(function (value) {
      if (value && !value.offset && prepared.order.has(value.node)) {
        // A boundary before the first child is also before its untouched
        // ancestors. Paged otherwise rebuilds a complete paragraph as a split
        // continuation and removes its first-line indent. This also keeps
        // transitions from browser columns to compatibility regions consistent.
        while (value.node.parentNode !== prepared.source && !value.node.previousSibling) value.node = value.node.parentNode;
      }
      if (value) value.equals = function (other) {
        return !!other && this.node === other.node && (this.offset == null ? 0 : this.offset) === (other.offset == null ? 0 : other.offset);
      };
      // Paged treats a zero offset as unspecified. A real break from 0 to 190 in
      // the same Text node must count as progress and extract the overflow.
      return value;
    });
    layout.hooks.renderNode.register(function (clone, original) { rememberRenderedSource(clone, original, sources); });
    layout.createBreakToken = function (overflow, rendered, source) {
      var container = overflow.startContainer, offset = overflow.startOffset;
      var node = container.nodeType === Node.TEXT_NODE ? container : container.childNodes[offset];
      var mapped = node && sources.get(node);
      if (!mapped && node && node.nodeType === Node.ELEMENT_NODE) {
        var original = prepared.elements.get(node.getAttribute('data-ref'));
        if (original) mapped = {node: original, offset: 0};
      }
      if (!mapped) return originalCreate.call(this, overflow, rendered, source);
      var absolute = mapped.offset + (container.nodeType === Node.TEXT_NODE ? offset : 0);
      if (absolute === 0 && container.nodeType === Node.TEXT_NODE) {
        // If none of this paragraph fits, remove its empty highlight shells too.
        // Climb only through the same first-child chain in the original source:
        // a continuation wrapper may have omitted earlier children, and moving
        // its token to the original parent would repeat already-read text.
        var boundary = container;
        while (boundary.parentNode !== rendered && !boundary.previousSibling) {
          var parent = sources.get(boundary.parentNode);
          if (!parent || parent.node.firstChild !== mapped.node) break;
          boundary = boundary.parentNode; mapped = parent;
        }
        if (boundary !== container) overflow.setStartBefore(boundary);
      }
      if (mapped.node.nodeType === Node.TEXT_NODE) {
        var boundary = graphemeStart(mapped.node.data, absolute);
        if (boundary < mapped.offset) throw new Error('分页断点位于未完成的字符中');
        if (container.nodeType === Node.TEXT_NODE && boundary !== absolute) overflow.setStart(container, boundary - mapped.offset);
        absolute = boundary;
      }
      // Upstream uses includes/indexOf to recover this location. Repeated
      // sentences and identical text around an image make that search ambiguous.
      // Keep source-node identity and its absolute offset instead.
      return this.breakAt(mapped.node, absolute);
    };
  }
  function tokenPosition(value, order) {
    if (!value) return null;
    var position = order.get(value.node);
    if (position == null) throw new Error('分页器返回了未知正文位置');
    return [position, Number(value.offset) || 0];
  }
  function progressed(previous, next) {
    return !next || !previous || next[0] > previous[0] || (next[0] === previous[0] && next[1] > previous[1]);
  }
  var graphemeSegmenter = typeof Intl !== 'undefined' && Intl.Segmenter ? new Intl.Segmenter(undefined, {granularity: 'grapheme'}) : null;
  function graphemeStart(text, offset) {
    if (offset <= 0) return 0;
    if (offset >= text.length) return text.length;
    if (graphemeSegmenter) {
      var previous = 0;
      for (var segment of graphemeSegmenter.segment(text)) {
        if (segment.index > offset) return previous;
        previous = segment.index;
      }
      return previous;
    }
    // Older WebViews: preserve surrogate pairs, combining marks, emoji skin
    // tones, variation selectors, regional-indicator pairs, and ZWJ sequences.
    var start = 0, index = 0, previousCode = -1, regional = 0;
    while (index < text.length) {
      var code = text.codePointAt(index);
      var mark = code >= 0x300 && code <= 0x36f || code >= 0x1ab0 && code <= 0x1aff ||
        code >= 0x1dc0 && code <= 0x1dff || code >= 0x20d0 && code <= 0x20ff ||
        code >= 0xfe00 && code <= 0xfe0f || code >= 0xfe20 && code <= 0xfe2f ||
        code >= 0x1f3fb && code <= 0x1f3ff || code >= 0xe0100 && code <= 0xe01ef;
      var isRegional = code >= 0x1f1e6 && code <= 0x1f1ff;
      if (!mark && code !== 0x200d && previousCode !== 0x200d && !(isRegional && regional % 2 === 1)) {
        if (index > offset) return start;
        start = index;
      }
      regional = isRegional ? regional + 1 : 0;
      previousCode = code; index += code > 0xffff ? 2 : 1;
    }
    return start;
  }
  function rectOutside(rect, bounds) {
    return rect.width > .1 && rect.height > .1 && (rect.bottom > bounds.bottom + .5 ||
      rect.right > bounds.right + .5 || rect.top < bounds.top - .5 || rect.left < bounds.left - .5);
  }
  function pageOverflowBounds(rendered, bounds) {
    var writing = getComputedStyle(rendered).writingMode;
    // A page break advances along the block axis only. Glyph overhang, kerning
    // and partial Range selection boxes can cross an inline edge on ANY row;
    // cutting there moves the rest of a perfectly usable page to the next one.
    // Inline/ascent fitting and the final four-edge paint check stay separate.
    return {width: bounds.width, height: bounds.height,
      left: writing === 'vertical-rl' ? bounds.left : -Infinity,
      right: writing === 'vertical-lr' ? bounds.right : Infinity,
      top: -Infinity, bottom: writing.indexOf('vertical') === 0 ? Infinity : bounds.bottom};
  }
  function pageContentOverflow(rendered, bounds) {
    return completeContentOverflow(rendered, pageOverflowBounds(rendered, bounds));
  }
  function measuredStyle(element, cache) {
    if (!cache) return getComputedStyle(element);
    var style = cache.get(element);
    if (!style) { style = getComputedStyle(element); cache.set(element, style); }
    return style;
  }
  function flowContentVisible(node, rendered, styles) {
    var element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    var style = measuredStyle(element, styles);
    if (style.visibility === 'hidden' || style.visibility === 'collapse') return false;
    // Pending pages are deliberately transparent. Only author visibility inside
    // the flow matters here; including the staging parent would skip all checks.
    for (; element; element = element.parentElement) {
      style = measuredStyle(element, styles);
      if (style.display === 'none' || parseFloat(style.opacity) <= .01) return false;
      if (element === rendered) break;
    }
    return true;
  }
  function fitFlowMetrics(rendered, bounds, columnStep, columnGap) {
    var walker = document.createTreeWalker(rendered, NodeFilter.SHOW_TEXT), node, shift = 0;
    var styles = new WeakMap();
    var flowStyle = measuredStyle(rendered, styles), horizontal = flowStyle.writingMode === 'horizontal-tb';
    var left = 0, right = 0;
    var columnStart = rendered.getBoundingClientRect().left + (parseFloat(flowStyle.paddingLeft) || 0) + (parseFloat(flowStyle.borderLeftWidth) || 0);
    var range = document.createRange();
    while ((node = walker.nextNode())) {
      if (!node.data.trim() || node.parentElement.closest('script,style,noscript,[data-reader-text-ignore]') || !flowContentVisible(node, rendered, styles)) continue;
      range.selectNodeContents(node);
      var rects = range.getClientRects();
      for (var index = 0; index < rects.length; index++) {
        var rect = rects[index];
        if (rect.width <= .1 || rect.height <= .1) continue;
        if (horizontal) {
          // A line's glyph/selection box can extend beyond its advance width:
          // CJK punctuation shaping and trailing letter-spacing both do this.
          // This is an inline fitting problem, even on the first line of a page,
          // not a reason to send that character and the rest of the page away.
          // Put the column boundary in the middle of its gutter. A leading
          // quote can extend left of its column without belonging to the
          // previous page; use the fragment centre, not that glyph edge.
          var columnOffset = columnStep ? Math.max(0,
            Math.floor(((rect.left + rect.right) / 2 - columnStart + (columnGap || 0) / 2) / columnStep)) * columnStep : 0;
          var before = bounds.left - (rect.left - columnOffset);
          var after = rect.right - columnOffset - bounds.right;
          var em = parseFloat(measuredStyle(node.parentElement, styles).fontSize) || 18;
          // Only reserve measured glyph clearance. Truly oversized/no-wrap
          // content still goes through the normal overflow validation below.
          if (before > .5 && before <= em) left = Math.max(left, before);
          if (after > .5 && after <= em) right = Math.max(right, after);
        }
        if (rect.top >= bounds.top - .5) continue;
        var block = node.parentElement;
        while (block !== rendered && /^(inline|contents)$/.test(getComputedStyle(block).display)) block = block.parentElement;
        var blockStyle = getComputedStyle(block);
        if (blockStyle.position === 'absolute' || blockStyle.position === 'fixed' || blockStyle.transform !== 'none' || block.getBoundingClientRect().top < bounds.top - .5) continue;
        shift = Math.max(shift, bounds.top - rect.top);
      }
    }
    // A font's glyph box can be taller than the author's line-height. Give the
    // first line its measured ascent clearance instead of rejecting a valid font
    // or subtracting a fixed line-height from every region.
    if (shift > .5) {
      rendered.style.paddingTop = ((parseFloat(flowStyle.paddingTop) || 0) + shift) + 'px';
    }
    // Reflow inside the existing viewport, keeping punctuation fully visible.
    // A paint-only overflow exception would instead clip it in themes whose
    // body slot has overflow:hidden. No source nodes or offsets are changed.
    if (left > .5) rendered.style.paddingLeft = ((parseFloat(flowStyle.paddingLeft) || 0) + left + .5) + 'px';
    if (right > .5) rendered.style.paddingRight = ((parseFloat(flowStyle.paddingRight) || 0) + right + .5) + 'px';
    return shift > .5 || left > .5 || right > .5;
  }
  function inlineImageLineStart(image, rect) {
    if (!image.matches('.legado-text-inline-image,.legado-text-bubble') && !image.closest('.legado-text-image-frame')) return null;
    var paragraph = image.closest('p.reader-paragraph');
    if (!paragraph) return null;
    var style = getComputedStyle(paragraph);
    if (style.writingMode.indexOf('vertical') === 0 || style.direction === 'rtl') return null;
    var walker = document.createTreeWalker(paragraph, NodeFilter.SHOW_TEXT), node;
    var range = document.createRange();
    while ((node = walker.nextNode())) {
      if (!(node.compareDocumentPosition(image) & Node.DOCUMENT_POSITION_FOLLOWING)) break;
      if (!node.data.trim()) continue;
      range.selectNodeContents(node);
      var row = Array.prototype.find.call(range.getClientRects(), function (text) {
        return text.width > .1 && Math.min(text.bottom, rect.bottom) - Math.max(text.top, rect.top) > Math.min(text.height, rect.height) * .5;
      });
      if (!row) continue;
      // Keep the complete visual row with its image. Moving only IMG would
      // leave an orphan above the first text line in the next flow region.
      var low = 1, high = node.length;
      while (low < high) {
        var middle = Math.floor((low + high) / 2); range.setEnd(node, middle);
        var reaches = Array.prototype.some.call(range.getClientRects(), function (text) {
          return text.width > .1 && text.bottom > row.top + row.height * .5;
        });
        if (reaches) high = middle; else low = middle + 1;
      }
      return {node: node, offset: graphemeStart(node.data, low - 1)};
    }
    return null;
  }
  function completeContentOverflow(rendered, bounds, paintBounds) {
    var walker = document.createTreeWalker(rendered, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
    // This search does not mutate the DOM. Reuse ancestor styles within this
    // measurement, and do not measure wrappers which have no overflow pixels.
    var styles = new WeakMap();
    var range = document.createRange();
    while ((node = walker.nextNode())) {
      var text = node.nodeType === Node.TEXT_NODE;
      if (text ? !node.data.trim() || node.parentElement.closest('script,style,noscript,[data-reader-text-ignore]') :
          !/^(img|svg|canvas|video|iframe|object|embed|hr)$/.test(String(node.localName).toLowerCase())) continue;
      if (!flowContentVisible(node, rendered, styles)) continue;
      if (text) {
        range.selectNodeContents(node);
        if (!Array.prototype.some.call(range.getClientRects(), function (rect) { return rectOutside(rect, bounds); })) continue;
        // Pagination passes only the forward block edge here. In particular,
        // a prefix's shaped inline box must not affect this binary search.
        // Final validation passes all four edges and never creates a page cut.
        var low = 0, high = node.length;
        while (low < high) {
          var middle = Math.ceil((low + high) / 2);
          range.setStart(node, 0); range.setEnd(node, middle);
          if (Array.prototype.some.call(range.getClientRects(), function (rect) { return rectOutside(rect, bounds); })) high = middle - 1;
          else low = middle;
        }
        range.setStart(node, graphemeStart(node.data, low));
      } else if (/^(img|svg|canvas|video|iframe|object|embed|hr)$/.test(String(node.localName).toLowerCase())) {
        var inline = node.localName === 'img' && (node.matches('.legado-text-inline-image,.legado-text-bubble') || node.closest('.legado-text-image-frame'));
        var mediaRect = node.getBoundingClientRect();
        var mediaBounds = paintBounds && inline ? {top: paintBounds.top, bottom: paintBounds.bottom, left: bounds.left, right: bounds.right} : bounds;
        if (!rectOutside(mediaRect, mediaBounds)) continue;
        var lineStart = node.localName === 'img' && inlineImageLineStart(node, mediaRect);
        if (lineStart) range.setStart(lineStart.node, lineStart.offset);
        else range.setStartBefore(node);
      } else continue;
      range.setEndAfter(rendered.lastChild);
      return range;
    }
    return null;
  }
  function revealFlowArtwork(rendered, page) {
    var viewportNode = rendered.parentElement, bounds = viewportNode.getBoundingClientRect();
    var outset = {top: 0, right: 0, bottom: 0, left: 0};
    Array.prototype.forEach.call(rendered.querySelectorAll('[data-legado-highlight]'), function (highlight) {
      var style = getComputedStyle(highlight);
      if (style.borderImageSource === 'none') return;
      var values = style.borderImageOutset.split(/\s+/);
      values = [values[0], values[1] || values[0], values[2] || values[0], values[3] || values[1] || values[0]];
      var sides = ['Top', 'Right', 'Bottom', 'Left'];
      values = values.map(function (value, index) {
        var number = parseFloat(value) || 0;
        return /px$/.test(value) ? number : number * (parseFloat(style['border' + sides[index] + 'Width']) || 0);
      });
      Array.prototype.forEach.call(highlight.getClientRects(), function (rect) {
        if (rect.width <= .1 || rect.height <= .1) return;
        outset.top = Math.max(outset.top, bounds.top - rect.top + values[0]);
        outset.right = Math.max(outset.right, rect.right + values[1] - bounds.right);
        outset.bottom = Math.max(outset.bottom, rect.bottom + values[2] - bounds.bottom);
        outset.left = Math.max(outset.left, bounds.left - rect.left + values[3]);
      });
    });
    Array.prototype.forEach.call(rendered.querySelectorAll('img'), function (image) {
      var rect = image.getBoundingClientRect();
      if (rect.width <= .1 || rect.height <= .1) return;
      outset.top = Math.max(outset.top, bounds.top - rect.top);
      outset.right = Math.max(outset.right, rect.right - bounds.right);
      outset.bottom = Math.max(outset.bottom, rect.bottom - bounds.bottom);
      outset.left = Math.max(outset.left, bounds.left - rect.left);
    });
    if (!Object.keys(outset).some(function (side) { return outset[side] > .1; })) return;
    var pageBounds = page.getBoundingClientRect();
    outset.top = Math.min(outset.top, Math.max(0, bounds.top - pageBounds.top));
    outset.right = Math.min(outset.right, Math.max(0, pageBounds.right - bounds.right));
    outset.bottom = Math.min(outset.bottom, Math.max(0, pageBounds.bottom - bounds.bottom));
    outset.left = Math.min(outset.left, Math.max(0, bounds.left - pageBounds.left));
    function avoid(rect, share) {
      if (rect.width <= .1 || rect.height <= .1) return;
      if (rect.right > bounds.left - outset.left && rect.left < bounds.right + outset.right) {
        if (rect.bottom <= bounds.top + .1) outset.top = Math.min(outset.top, Math.max(0, bounds.top - rect.bottom) / share);
        if (rect.top >= bounds.bottom - .1) outset.bottom = Math.min(outset.bottom, Math.max(0, rect.top - bounds.bottom) / share);
      }
      if (rect.bottom > bounds.top - outset.top && rect.top < bounds.bottom + outset.bottom) {
        if (rect.right <= bounds.left + .1) outset.left = Math.min(outset.left, Math.max(0, bounds.left - rect.right) / share);
        if (rect.left >= bounds.right - .1) outset.right = Math.min(outset.right, Math.max(0, rect.left - bounds.right) / share);
      }
    }
    // Neighbouring flows share a gutter. Chrome keeps its actual text/media
    // bounds, so decorations may use blank leading without covering a footer.
    orderedSlots(page).forEach(function (slot) {
      if (!slot.contains(rendered)) avoid(slot.getBoundingClientRect(), 2);
    });
    var walker = document.createTreeWalker(page, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
    var styles = new WeakMap(), range = document.createRange();
    while ((node = walker.nextNode())) {
      var element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
      if (node.nodeType === Node.TEXT_NODE ? !node.data.trim() :
          !/^(img|svg|canvas|video|iframe|object|embed|hr)$/.test(node.localName)) continue;
      if (element.closest('[data-reader-flow],script,style,noscript') || !flowContentVisible(node, page, styles)) continue;
      if (node.nodeType === Node.TEXT_NODE && node.data.trim()) {
        range.selectNodeContents(node);
        Array.prototype.forEach.call(range.getClientRects(), function (rect) { avoid(rect, 1); });
      } else if (node.nodeType === Node.ELEMENT_NODE && /^(img|svg|canvas|video|iframe|object|embed|hr)$/.test(node.localName)) {
        avoid(node.getBoundingClientRect(), 1);
      }
    }
    // Pagination and the final text/image overflow check have already finished.
    // Expose only the measured decoration allowance, retaining a finite clip.
    return {viewport: viewportNode, clipPath: 'inset(' + ['top', 'right', 'bottom', 'left'].map(function (side) { return -outset[side] + 'px'; }).join(' ') + ')'};
  }
  function createStage() {
    var stage = document.createElement('div');
    stage.setAttribute('data-reader-runtime-stage', 'pending');
    document.body.appendChild(stage);
    if (scrollMode) stage.addEventListener('scroll', function (event) {
      if (event.target !== scrollingElement(stage) || stage !== committedRoot || layoutBusy || scrollFrame) return;
      scrollFrame = requestAnimationFrame(function () {
        scrollFrame = 0;
        if (!active || stage !== committedRoot || layoutBusy) return;
        var next = continuous ? scrollIndex(scrollingElement(stage).scrollTop) : Math.max(0, Math.min(pages.length - 1, Math.floor((stage.scrollTop + 1) / viewport.height)));
        if (next !== pageIndex) {
          if (!continuous) setMotionState('settled');
          else activationBoundary = '';
          pageIndex = next; activationTargetRevision = layoutRevision;
          updatePageVisibility();
          syncMotion();
          if ((hooks.pageChange || []).length) {
            var serial = ++authorVisualSerial;
            authorVisualPending = true;
            emitHook('pageChange', {page: pageAt(pageIndex), pageIndex: pageIndex, pageCount: pageCount()}).then(twoFrames).then(function () {
              if (!active || serial !== authorVisualSerial) return;
              authorVisualPending = false; postState();
            }).catch(fail);
          }
          visualRevision++; postState();
        }
        // A continuous document has no page snapshots to invalidate on every
        // pixel of a fling. Its position index is measured once during layout.
        if (!continuous) { visualRevision++; postState(); }
      });
    }, {passive: true, capture: true});
    return stage;
  }

  async function renderFlow(content, viewportNode, prepared, breakToken) {
    if (window.ReaderBrowserTemplateFlow) {
      var browserResult = await window.ReaderBrowserTemplateFlow.render(content, viewportNode, prepared, breakToken, {
        graphemeStart: graphemeStart, imageReady: imageReady, fontsReady: loadFonts,
        fitMetrics: fitFlowMetrics, overflow: pageContentOverflow, checkpoint: prepared.checkpoint,
        yieldTask: function () { return new Promise(function (resolve) { later(resolve, 0); }); }
      });
      if (browserResult) return browserResult;
    }
    content.setAttribute('data-reader-pagination-engine', 'paged');
    // A fixed 1500-character batch can lay out many pages of text in a small
    // region before checking overflow. Estimate two viewports of text instead;
    // this only schedules overflow checks and never limits the chapter content.
    var style = getComputedStyle(viewportNode), fontSize = parseFloat(style.fontSize) || 18;
    var lineHeight = parseFloat(style.lineHeight) || fontSize * 1.55;
    var maxChars = Math.max(128, Math.min(1500,
      Math.ceil(viewportNode.clientWidth * viewportNode.clientHeight / fontSize / lineHeight * 2)));
    var layout = new Paged.Layout(viewportNode, null, {maxChars: maxChars});
    installSourceBreaks(layout, prepared);
    var findOverflow = layout.findOverflow;
    layout.findOverflow = function (rendered, bounds, gap) {
      // The compatibility engine also searches individual letters. Restrict
      // its search before it produces a token, not just our supplementary
      // overflow hook, or a fallback can reintroduce the same premature cut.
      return findOverflow.call(this, rendered, pageOverflowBounds(rendered, bounds || this.bounds), gap);
    };
    // CSS hyphenation may paint hyphens without changing mapped source text.
    layout.hyphenateAtBreak = function () {};
    layout.hooks.onOverflow.register(function (overflow, rendered, bounds, paginator) {
      var changed = false;
      for (var attempt = 0; attempt < 8; attempt++) {
        if (!fitFlowMetrics(rendered, bounds)) break;
        changed = true;
      }
      // Any upstream range was measured before the inline reflow. Discard it,
      // then find the real page boundary using the final line geometry.
      if (changed) overflow = paginator.findOverflow(rendered, bounds);
      var complete = pageContentOverflow(rendered, bounds);
      return complete && (!overflow || complete.compareBoundaryPoints(Range.START_TO_START, overflow) < 0) ? complete : overflow;
    });
    layout.waitForImages = async function (images) {
      await Promise.all(Array.prototype.map.call(images, function (image) { return imageReady(image, true); }));
      await loadFonts(content);
    };
    var box = viewportNode.getBoundingClientRect();
    var bounds = {top: box.top, bottom: box.bottom, left: box.left, right: box.right, width: box.width, height: box.height};
    var result = await layout.renderTo(content, prepared.source, breakToken, bounds);
    if (result.error) return result;
    // Removing a suffix changes last-line justification and punctuation shaping.
    // The pre-extraction metrics are not a guarantee about the displayed page.
    // Refit that final fragment and, only if it now crosses the block edge,
    // recut through the same source-identity mapping used by the first pass.
    for (var pass = 0; pass < 8; pass++) {
      prepared.checkpoint();
      for (var attempt = 0; attempt < 8; attempt++) {
        if (!fitFlowMetrics(content, bounds)) break;
      }
      if (!pageContentOverflow(content, bounds)) return result;
      var corrected = layout.findBreakToken(content, prepared.source, bounds, breakToken);
      if (!corrected || !progressed(tokenPosition(breakToken, prepared.order) || [0, 0], tokenPosition(corrected, prepared.order))) {
        throw new Error('正文无法在当前区域内完成分页，请检查正文尺寸或禁止换行的内容');
      }
      result.breakToken = corrected;
    }
    throw new Error('正文分页后的行尾未能稳定，请检查正文尺寸或分页后的样式修改');
  }
  async function createTemplatePage(stage, index, countHint, expected) {
      var page = document.createElement('div');
      page.className = 'reader-template-page' + (continuous ? '' : ' pagedjs_page');
      page.setAttribute('data-reader-page', continuous ? 'scroll' : index === 0 ? 'first' : 'other');
      page.setAttribute('data-reader-page-index', String(index));
      page.setAttribute('data-reader-motion', 'settled');
      page.setAttribute('data-reader-entry', 'pending');
      // Keep complete author documents and fragments alike, including resources.
      var markup = String(continuous ? template.scrollHtml : index === 0 ? template.firstPageHtml : template.otherPageHtml);
      var parsed = pageMarkupCache.get(markup);
      if (!parsed) {
        parsed = new DOMParser().parseFromString(markup, 'text/html');
        if (pageMarkupCache.size >= 2) pageMarkupCache.delete(pageMarkupCache.keys().next().value);
        pageMarkupCache.set(markup, parsed);
      }
      Array.prototype.forEach.call(parsed.head.childNodes, function (node) {
        if (node.nodeType !== Node.ELEMENT_NODE || !/^(TITLE|META|BASE)$/.test(node.tagName)) page.appendChild(document.importNode(node, true));
      });
      Array.prototype.forEach.call(parsed.body.childNodes, function (node) { page.appendChild(document.importNode(node, true)); });
      Array.prototype.forEach.call(parsed.body.attributes, function (attribute) {
        if (!/^(class|style)$/.test(attribute.name)) page.setAttribute(attribute.name, attribute.value);
        else if (attribute.name === 'class') page.className += ' ' + attribute.value;
        else page.style.cssText += attribute.value;
      });
      trackStyles(page);
      stage.appendChild(page);
      if (continuous) configureContinuousScroll(stage, page);
      bindFields(page, index, countHint);
      await waitStyles(page);
      await runScripts(page);
      await emitHook('beforePage', {page: page, pageIndex: index, first: !continuous && index === 0, type: continuous ? 'scroll' : 'paged'});
      await installHighlights();
      checkRun(expected);
      return page;
  }
  function fitContinuousBlockExtent(content) {
    var range = document.createRange();
    range.selectNodeContents(content);
    var painted = range.getBoundingClientRect(), box = content.getBoundingClientRect();
    if (painted.width <= .1 || painted.height <= .1) return false;
    var before = Math.max(0, box.top - painted.top), after = Math.max(0, painted.bottom - box.bottom);
    var style = getComputedStyle(content);
    // Fonts and inline bubbles can paint beyond their line boxes. In a natural
    // document this expands the scrollable content, rather than failing a page
    // boundary check or putting the chapter footer over the last visible glyph.
    if (before > .5) content.style.paddingTop = ((parseFloat(style.paddingTop) || 0) + before) + 'px';
    if (after > .5) content.style.paddingBottom = ((parseFloat(style.paddingBottom) || 0) + after) + 'px';
    return before > .5 || after > .5;
  }
  async function renderContinuous(stage, expected) {
    var page = await createTemplatePage(stage, 0, 1, expected), slots = orderedSlots(page);
    if (slots.length !== 1) throw new Error('滚动模板需要且只能包含一个 data-reader-flow 正文区域');
    var slot = slots[0], viewportNode = document.createElement('div'), content = document.createElement('div');
    viewportNode.className = 'reader-template-flow-viewport';
    content.className = 'reader-template-flow-content';
    content.setAttribute('data-reader-pagination-engine', 'continuous');
    // A single, intact source tree. No page extraction or CSS columns run here.
    content.appendChild(source.cloneNode(true));
    viewportNode.appendChild(content); slot.replaceChildren(viewportNode);
    if (slotBox(slot).width < 2) throw new Error('滚动模板的正文区域没有可用宽度');
    await Promise.all(Array.prototype.map.call(page.querySelectorAll('img'), function (image) { return imageReady(image, true); }));
    await loadFonts(content);
    await emitHook('afterPage', {page: page, pageIndex: 0, first: false, type: 'scroll'});
    await Promise.all(Array.prototype.map.call(page.querySelectorAll('img'), function (image) { return imageReady(image, true); }));
    await waitBackgrounds(page);
    for (var attempt = 0; attempt < 3; attempt++) {
      var inlineChanged = fitFlowMetrics(content, viewportNode.getBoundingClientRect());
      var blockChanged = fitContinuousBlockExtent(content);
      if (!inlineChanged && !blockChanged) break;
    }
    var scrollBox = scrollViewport(stage);
    if (scrollBox.width < 2 || scrollBox.height < 2) throw new Error('滚动模板的阅读框没有可用尺寸，请为滚动容器设置高度');
    checkRun(expected);
    return {pages: [page], contents: [{pageIndex: 0, node: content}]};
  }
  async function indexContinuous(result, stage, expected, deadline) {
    var content = result.contents[0].node, page = result.pages[0];
    var scroller = scrollingElement(stage), box = scrollViewport(stage);
    var max = Math.max(0, scroller.scrollHeight - box.height), positions = [0];
    // Positions serve native progress/navigation only; they never split the DOM.
    var step = Math.max(box.height, max / 2047);
    for (var y = step; y < max - 1; y += step) positions.push(y);
    if (max > 0) positions.push(max);
    var points = [{node: content, offset: 0}], target = 1, styles = new WeakMap();
    var range = document.createRange(), origin = box.origin;
    var walker = document.createTreeWalker(content, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT), node;
    var visits = 0, lastYield = performance.now();
    while ((node = walker.nextNode()) && target < positions.length) {
      if (++visits % 64 === 0) {
        checkRun(expected);
        if (activeTime() >= deadline) throw new Error('滚动模板布局耗时过长，请减少复杂样式');
        if (performance.now() - lastYield > 12) {
          await new Promise(function (resolve) { later(resolve, 0); }); lastYield = performance.now();
        }
      }
      if (!flowContentVisible(node, page, styles)) continue;
      if (node.nodeType === Node.TEXT_NODE) {
        if (!node.data.trim() || node.parentElement.closest('script,style,noscript,[data-reader-text-ignore],[data-legado-image-action]')) continue;
        range.selectNodeContents(node);
        var rects = range.getClientRects(), lastTop = -Infinity;
        for (var r = 0; r < rects.length; r++) {
          if (rects[r].width > .1 && rects[r].height > .1) lastTop = Math.max(lastTop, rects[r].top - origin);
        }
        while (target < positions.length && lastTop >= positions[target]) {
          var low = 1, high = node.length, at = positions[target];
          while (low < high) {
            var middle = Math.floor((low + high) / 2); range.setStart(node, 0); range.setEnd(node, middle);
            var reaches = Array.prototype.some.call(range.getClientRects(), function (rect) {
              return rect.width > .1 && rect.height > .1 && rect.top - origin >= at;
            });
            if (reaches) high = middle; else low = middle + 1;
          }
          points.push({node: node, offset: graphemeStart(node.data, low - 1)}); target++;
        }
      } else if (/^(img|svg|canvas|video|iframe|object|embed|hr)$/.test(node.localName)) {
        var top = node.getBoundingClientRect().top - origin;
        while (target < positions.length && top >= positions[target]) {
          points.push({node: node.parentNode, offset: Array.prototype.indexOf.call(node.parentNode.childNodes, node)}); target++;
        }
      }
    }
    while (points.length <= positions.length) points.push({node: content, offset: content.childNodes.length});
    var contents = positions.map(function (_, index) {
      range.setStart(points[index].node, points[index].offset);
      range.setEnd(points[index + 1].node, points[index + 1].offset);
      var fragment = range.cloneContents(), ancestor = range.commonAncestorContainer;
      if (ancestor.nodeType === Node.TEXT_NODE) ancestor = ancestor.parentNode;
      // Detached range copies preserve source identities for the existing
      // integrity/location mapper; the displayed source tree stays untouched.
      while (ancestor && ancestor !== content) {
        var shell = ancestor.cloneNode(false); shell.appendChild(fragment); fragment = shell; ancestor = ancestor.parentNode;
      }
      var holder = document.createElement('div'); holder.appendChild(fragment);
      return {pageIndex: index, node: holder};
    });
    var pixels = [], shellRenderable = false;
    function addRect(rect, inScroll) {
      // The frame's artwork stays at screen coordinates; only the content's
      // cached intervals move with scrollTop. Do not treat a fixed footer as
      // a glyph at the end of the chapter, or vice versa.
      if (!inScroll) { if (viewportRect(rect)) shellRenderable = true; return; }
      if (rect.width > .1 && rect.height > .1 && rect.right > box.left && rect.left < box.right) {
        pixels.push([rect.top - origin, rect.bottom - origin]);
      }
    }
    textNodes(page).forEach(function (text) {
      if (!text.data.trim() || !flowContentVisible(text, page, styles)) return;
      var inScroll = scroller.contains(text);
      range.selectNodeContents(text);
      Array.prototype.forEach.call(range.getClientRects(), function (rect) { addRect(rect, inScroll); });
    });
    Array.prototype.forEach.call(page.querySelectorAll('img,svg,canvas,video,table,hr,iframe,object,button,input'), function (element) {
      if (flowContentVisible(element, page, styles)) addRect(element.getBoundingClientRect(), scroller.contains(element));
    });
    pixels.sort(function (a, b) { return a[0] - b[0]; });
    var merged = [];
    pixels.forEach(function (pixel) {
      var previous = merged[merged.length - 1];
      if (previous && previous[1] >= pixel[0]) previous[1] = Math.max(previous[1], pixel[1]);
      else merged.push(pixel);
    });
    return {positions: positions, contents: contents, pixels: merged, shellRenderable: shellRenderable};
  }
  async function paginate(stage, expected, countHint, deadline) {
    var prepared = prepareLayoutSource(), breakToken, previousPosition;
    prepared.checkpoint = function () {
      checkRun(expected);
      if (activeTime() >= deadline) throw new Error('模板分页耗时过长，请减少复杂样式或改用基础排版');
    };
    var list = [], contents = [], started = performance.now(), lastYield = started;
    for (var index = 0; ; index++) {
      checkRun(expected);
      if (index >= 2048) throw new Error('模板分页超过页数上限，请检查正文区域尺寸或改用基础排版');
      if (activeTime() >= deadline) throw new Error('模板分页耗时过长，请减少复杂样式或改用基础排版');
      var page = await createTemplatePage(stage, index, countHint, expected); list.push(page);
      var slots = orderedSlots(page);
      if (!slots.length) throw new Error('模板页面缺少 data-reader-flow 正文区域');
      for (var slotIndex = 0; slotIndex < slots.length; slotIndex++) {
        var slot = slots[slotIndex], box = slotBox(slot);
        if (box.width < 2 || box.height < 2) throw new Error('第 ' + (index + 1) + ' 页的正文区域没有可用尺寸');
        await loadFonts(slot);
        checkRun(expected);
        // The inner viewport makes author padding/borders independent of Paged's
        // scroll-overflow calculation. Each slot may have unrelated dimensions.
        var viewportNode = document.createElement('div'), content = document.createElement('div');
        viewportNode.className = 'reader-template-flow-viewport';
        viewportNode.style.width = box.width + 'px'; viewportNode.style.height = box.height + 'px';
        viewportNode.style.setProperty('--reader-flow-height', box.height + 'px');
        content.className = 'reader-template-flow-content';
        viewportNode.appendChild(content); slot.replaceChildren(viewportNode);
        var result = await renderFlow(content, viewportNode, prepared, breakToken);
        checkRun(expected);
        if (result.error) throw new Error('正文无法放入第 ' + (index + 1) + ' 页的区域：' + result.error.message);
        var nextPosition = tokenPosition(result.breakToken, prepared.order);
        if (!progressed(previousPosition, nextPosition)) throw new Error('模板分页停在同一正文位置，已终止以避免空白页循环');
        previousPosition = nextPosition; breakToken = result.breakToken;
        contents.push({pageIndex: index, node: content});
        if (!breakToken) break;
      }
      await emitHook('afterPage', {page: page, pageIndex: index, first: index === 0});
      await Promise.all(Array.prototype.map.call(page.querySelectorAll('img'), function (image) { return imageReady(image, true); }));
      await waitBackgrounds(page);
      checkRun(expected);
      if (!breakToken) break;
      if (performance.now() - lastYield > 12) {
        await new Promise(function (resolve) { later(resolve, 0); }); lastYield = performance.now();
      }
    }
    return {pages: list, contents: contents};
  }
  function pageForOffset(offset, map) {
    var nearest = 0, distance = Infinity;
    for (var index = 0; index < map.length; index++) {
      var fragments = map[index].fragments;
      for (var part = 0; part < fragments.length; part++) {
        var range = fragments[part];
        if (range.start <= offset && offset < range.end) return index;
        var candidateDistance = Math.min(Math.abs(range.start - offset), Math.abs(range.end - offset));
        if (candidateDistance < distance) { distance = candidateDistance; nearest = index; }
      }
    }
    return nearest;
  }
  function updatePageVisibility() {
    function visibility(page, selected) {
      page.setAttribute('data-reader-active', selected ? 'true' : 'false');
      page.setAttribute('aria-hidden', scrollMode || selected ? 'false' : 'true');
      page.inert = !scrollMode && !selected;
    }
    // A new layout initializes each page once. Turning a page only touches the
    // outgoing and incoming pages, regardless of the chapter's length.
    if (visibilityPages !== pages) {
      pages.forEach(function (page) { visibility(page, false); });
      visibilityPages = pages; visiblePage = null;
    }
    var nextPage = pageAt(pageIndex);
    if (visiblePage !== nextPage) {
      if (visiblePage) visibility(visiblePage, false);
      if (nextPage) visibility(nextPage, true);
      visiblePage = nextPage;
    }
  }
  function showPage(index) {
    setMotionState('settled');
    pageIndex = Math.max(0, Math.min(pageCount() - 1, Math.floor(Number(index) || 0)));
    updatePageVisibility();
    syncMotion();
    if (scrollMode && committedRoot) scrollingElement(committedRoot).scrollTop = continuous ? scrollPositions[pageIndex] || 0 : pageIndex * viewport.height;
    activationTargetRevision = layoutRevision;
    visualRevision++;
  }
  function restorePage(anchor, fallback) {
    if (activationBoundary === 'start') return 0;
    if (activationBoundary === 'end') return pageCount() - 1;
    if (anchor) {
      for (var index = 0; index < pageMap.length; index++) {
        var found = pageMap[index].fragments.some(function (fragment) {
          return fragment.id === anchor.id && fragment.start <= anchor.start &&
            (fragment.end > anchor.start || fragment.start === fragment.end);
        });
        if (found) return index;
      }
      return pageForOffset(anchor.start, pageMap);
    }
    return fallback;
  }
  function sourceMutation(mutations) {
    if (sourcePreparing) return;
    if (mutations.every(function (mutation) {
      return mutation.type === 'attributes' && /^(data-reader-display-block|data-reader-display-image)$/.test(mutation.attributeName || '');
    })) return;
    requestLayout('source');
  }
  function connectObservers() {
    if (pageObserver) pageObserver.disconnect();
    if (sizeObserver) sizeObserver.disconnect();
    geometry = geometryOf(pages);
    if (window.ResizeObserver) {
      sizeObserver = new ResizeObserver(function () {
        if (!active || layoutBusy || !pages.length) return;
        if (!sameGeometry(geometryOf(pages), geometry)) requestLayout('region-resize');
      });
      pages.forEach(function (page) {
        if (continuous) sizeObserver.observe(page);
        if (continuous && scrollingElement(committedRoot) !== committedRoot) sizeObserver.observe(scrollingElement(committedRoot));
        orderedSlots(page).forEach(function (slot) { sizeObserver.observe(slot); });
      });
    }
    if (window.MutationObserver && committedRoot) {
      pageObserver = new MutationObserver(function (mutations) {
        var relevant = false, flowChanged = false;
        mutations.forEach(function (mutation) {
          var element = mutation.target.nodeType === Node.ELEMENT_NODE ? mutation.target : mutation.target.parentElement;
          if (!element) return;
          if (element.closest('[data-reader-field]') && mutation.attributeName !== 'data-reader-field') return;
          if (mutation.type === 'childList' || mutation.attributeName === 'data-reader-field') fieldBindingPages = null;
          if (element.classList.contains('reader-template-page') && /^(data-reader-active|data-reader-motion|data-reader-entry|aria-hidden|inert)$/.test(mutation.attributeName || '')) return;
          relevant = true;
          if (element.closest('.reader-template-flow-content,style,link[rel~="stylesheet"]')) flowChanged = true;
        });
        if (!relevant || layoutBusy) return;
        if (flowChanged) { requestLayout('template-flow-mutation'); return; }
        // A button counter, clock, or author animation in the page shell need
        // not rerun the entire chapter when every flow region keeps its shape.
        var serial = ++authorVisualSerial;
        authorVisualPending = true; visualRevision++; contentRevision++; postState();
        clearLater(mutationTimer);
        mutationTimer = later(async function () {
          try {
            await Promise.all(Array.prototype.map.call(pageAt(pageIndex).querySelectorAll('img'), function (image) { return imageReady(image, true); }));
            await twoFrames();
            if (serial !== authorVisualSerial || !active) return;
            authorVisualPending = false;
            if (!sameGeometry(geometryOf(pages), geometry)) requestLayout('template-shell-geometry'); else postState();
          } catch (error) { fail(error); }
        }, 0);
      });
      pageObserver.observe(committedRoot, {subtree: true, childList: true, characterData: true, attributes: true});
    }
  }
  function notifyLayoutWaiters(error) {
    layoutWaiters.splice(0).forEach(function (waiter) { if (error) waiter.reject(error); else waiter.resolve(); });
  }
  function waitForLayout() {
    if (failure) return Promise.reject(failure);
    if (!active) return Promise.reject(new Error('模板页面已关闭'));
    if (initialized && !layoutBusy) return Promise.resolve();
    return new Promise(function (resolve, reject) { layoutWaiters.push({resolve: resolve, reject: reject}); });
  }
  function requestLayout(reason) {
    if (!active || failure) return;
    // Source/resource updates can safely wait in the detached source while a
    // real selection still points at the committed DOM. Replacing it mid-copy
    // would discard the selection and its native action-mode ownership.
    if (committedRoot && selectionVisible() && reason !== 'viewport') { selectionDeferredLayout = true; return; }
    if (reason === 'viewport' && selectionVisible()) clearSelection();
    generation++; contentRevision++; requested = true; layoutBusy = true;
    setMotionState('settled');
    postState();
    if (!initialized || running || layoutTimer) return;
    layoutTimer = later(function () { layoutTimer = 0; layoutLoop(); }, reason === 'source-image' ? 32 : 16);
  }
  async function layoutLoop() {
    if (running || !active || failure) return;
    running = true;
    // Native startup already includes resource preparation. Keep that same budget
    // through every pagination pass/restart instead of imposing an earlier 45s
    // per-pass failure on a chapter which is still making forward progress.
    var deadline = (committedRoot ? activeTime() : startupStarted) + renderBudgetMs;
    while (active && requested && !failure) {
      if (activeTime() >= deadline) { fail(new Error('模板持续改变布局，未能在限定时间内稳定')); break; }
      requested = false;
      var expected = generation;
      var anchor = pageMap[pageIndex] && pageMap[pageIndex].fragments[0], fallback = pageIndex;
      var withinPosition = continuous && committedRoot ? scrollingElement(committedRoot).scrollTop - (scrollPositions[pageIndex] || 0) : 0;
      var stage = null;
      try {
        sourcePreparing = true;
        await emitHook('beforeLayout', {generation: expected, viewport: viewport});
        await drainAuthorWork();
        var sourceSnapshot = ReaderTemplateSourceMap.capture(source, String(init.plainText || ''), records);
        if (sourceObserver) sourceObserver.takeRecords();
        sourcePreparing = false;
        checkRun(expected);
        var countHint = pages.length || 0, result, map, continuousIndex, converged = false;
        for (var pass = 0; pass < 4; pass++) {
          if (stage) stage.remove();
          stage = createStage(); candidateRoot = stage;
          result = continuous ? await renderContinuous(stage, expected) : await paginate(stage, expected, countHint, deadline);
          var before = geometryOf(result.pages);
          result.pages.forEach(function (page, index) { bindFields(page, index, result.pages.length); });
          await emitHook('afterLayout', {pages: result.pages, pageCount: result.pages.length, generation: expected});
          await twoFrames();
          checkRun(expected);
          var after = geometryOf(result.pages);
          if (sameGeometry(before, after)) { converged = true; break; }
          countHint = result.pages.length;
        }
        if (!converged) throw new Error(continuous ? '滚动模板的正文尺寸持续改变，无法完成布局' : '页眉或页数持续改变正文区域尺寸，模板分页无法稳定');
        var artwork = [];
        result.contents.forEach(function (entry) {
          var bounds = entry.node.parentElement.getBoundingClientRect();
          var screen = continuous ? scrollViewport(stage) : null;
          // A scrolling document has no bottom page edge. Its flow is visible
          // through the template's margins, up to its actual scroll viewport.
          // Keep the bounded region check for paged templates.
          var visibleBounds = continuous ? {
            left: screen.left, right: screen.right, top: -Infinity, bottom: Infinity
          } : bounds;
          var overflow = completeContentOverflow(entry.node, visibleBounds);
          if (overflow) {
            var rect = overflow.getBoundingClientRect();
            if (continuous) throw new Error('滚动模板正文横向超出可视区域（宽度 ' + screen.width.toFixed(1) +
              'px）；请检查正文中固定宽度或禁止换行的内容');
            throw new Error('第 ' + (entry.pageIndex + 1) + ' 页正文超出模板区域；区域 ' +
              bounds.width.toFixed(1) + '×' + bounds.height.toFixed(1) +
              '，越界内容起点 ' + (rect.left - bounds.left).toFixed(1) + ',' + (rect.top - bounds.top).toFixed(1) +
              '；请检查模板正文尺寸或分页后的样式修改');
          }
          var clip = !continuous && revealFlowArtwork(entry.node, result.pages[entry.pageIndex]);
          if (clip) artwork.push(clip);
        });
        // Clip allowances do not change the flow-root's geometry. Finish every
        // page's measurements before applying these paint-only style changes.
        artwork.forEach(function (clip) {
          clip.viewport.style.overflow = 'visible';
          clip.viewport.style.clipPath = clip.clipPath;
        });
        if (continuous) {
          continuousIndex = await indexContinuous(result, stage, expected, deadline);
          map = sourceSnapshot.collect(continuousIndex.contents, continuousIndex.positions.length);
        } else map = sourceSnapshot.collect(result.contents, result.pages.length);
        checkRun(expected);
        if (pageObserver) pageObserver.disconnect();
        if (sizeObserver) sizeObserver.disconnect();
        var previous = committedRoot;
        pages = result.pages; pageMap = map; committedRoot = stage; candidateRoot = null;
        scrollPositions = continuousIndex ? continuousIndex.positions : [];
        scrollPixels = continuousIndex ? continuousIndex.pixels : [];
        scrollShellRenderable = continuousIndex ? continuousIndex.shellRenderable : false;
        committedRoot.setAttribute('data-reader-runtime-stage', 'committed');
        if (scrollMode && !continuous) pages.forEach(function (page, index) { page.style.top = (index * viewport.height) + 'px'; page.style.bottom = 'auto'; page.style.height = viewport.height + 'px'; });
        layoutRevision++; visualRevision++;
        showPage(restorePage(anchor, fallback));
        if (continuous && previous && !activationBoundary && withinPosition > 0) {
          scrollingElement(committedRoot).scrollTop += withinPosition;
          pageIndex = scrollIndex(scrollingElement(committedRoot).scrollTop);
        }
        if (previous) previous.remove();
        stage = null;
        await twoFrames();
        checkRun(expected);
        resourcesReady = sourceImagesPending === 0; layoutBusy = false; authorVisualPending = false;
        connectObservers();
        syncMotion();
        checkRun(expected);
        postState(null, true);
        if (sourceImagesPending === 0) post('stable');
        notifyLayoutWaiters();
      } catch (error) {
        sourcePreparing = false;
        if (stage) stage.remove();
        candidateRoot = null;
        if (error !== CANCELLED) fail(error);
      }
    }
    running = false;
    if (active && requested && !failure && !layoutTimer) layoutTimer = later(function () { layoutTimer = 0; layoutLoop(); }, 16);
  }
  function visible(element) {
    for (var node = element; node && node.nodeType === Node.ELEMENT_NODE; node = node.parentElement) {
      var style = getComputedStyle(node);
      if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || parseFloat(style.opacity) <= .01) return false;
    }
    return true;
  }
  function viewportRect(rect) { return rect.width > .5 && rect.height > .5 && rect.right > 0 && rect.bottom > 0 && rect.left < viewport.width && rect.top < viewport.height; }
  function viewportRenderable() {
    var page = pageAt(pageIndex);
    if (!page || layoutBusy || visualBusy || authorVisualPending) return false;
    if (continuous) {
      // Cached, merged paint intervals make readiness independent of chapter
      // length while scrolling. All Range measurements happened during layout.
      if (scrollShellRenderable) return true;
      var scroller = scrollingElement(committedRoot), top = scroller.scrollTop, low = 0, high = scrollPixels.length;
      while (low < high) {
        var middle = Math.floor((low + high) / 2);
        if (scrollPixels[middle][1] <= top) low = middle + 1; else high = middle;
      }
      return low < scrollPixels.length && scrollPixels[low][0] < top + scroller.clientHeight;
    }
    // Full author HTML is renderable too: a chapter with just a custom heading,
    // a canvas, or an image must not be mistaken for an empty body flow.
    var roots = [page];
    for (var index = 0; index < roots.length; index++) {
      var nodes = textNodes(roots[index]);
      for (var part = 0; part < nodes.length; part++) {
        var node = nodes[part];
        if (!node.data.trim() || !visible(node.parentElement)) continue;
        var range = document.createRange(); range.selectNodeContents(node);
        var rects = range.getClientRects();
        for (var rectIndex = 0; rectIndex < rects.length; rectIndex++) if (viewportRect(rects[rectIndex])) return true;
      }
      var images = roots[index].querySelectorAll('img,svg,canvas,video,table,hr,iframe,object,button,input');
      for (var imageIndex = 0; imageIndex < images.length; imageIndex++) {
        var image = images[imageIndex];
        if (image.tagName === 'IMG' && (!image.complete || !image.naturalWidth)) continue;
        if (visible(image) && viewportRect(image.getBoundingClientRect())) return true;
      }
      var elements = [page].concat(Array.prototype.slice.call(page.querySelectorAll('*')));
      for (var backgroundIndex = 0; backgroundIndex < elements.length; backgroundIndex++) {
        var element = elements[backgroundIndex];
        if (!visible(element) || !viewportRect(element.getBoundingClientRect())) continue;
        var backgrounds = getComputedStyle(element).backgroundImage;
        var found = false;
        backgroundPixels.forEach(function (url) { if (backgrounds.indexOf(url) >= 0) found = true; });
        if (found) return true;
      }
    }
    return false;
  }
  function metrics() {
    var target = activationBoundary === 'start' ? 0 : activationBoundary === 'end' ? pageCount() - 1 : pageIndex;
    var pendingResources = sourceImagesPending > 0;
    var ready = initialized && !failure && !layoutBusy && !visualBusy && !authorVisualPending && !pendingResources;
    var renderable = ready && viewportRenderable();
    return {pageCount: Math.max(1, pageCount()), pageIndex: Math.max(0, pageIndex), ready: ready,
      resourcesReady: resourcesReady && !pendingResources, resourcesFailed: resourcesFailed, layoutRevision: layoutRevision,
      visualRevision: visualRevision, contentRevision: contentRevision,
      layoutPending: layoutBusy || visualBusy || authorVisualPending || pendingResources, sourceImagesPending: sourceImagesPending,
      activationTargetRevision: activationTargetRevision, activationTargetSatisfied: ready && activationTargetRevision === layoutRevision && pageIndex === target,
      renderable: renderable, viewportRenderable: renderable};
  }
  function postState(requestId, includePages) {
    var value = {metrics: metrics(), offset: pageMap[pageIndex] ? pageMap[pageIndex].start : 0};
    if (Number.isSafeInteger(requestId)) value.requestId = requestId;
    if (includePages) value.pages = pageMap;
    post('state', value);
  }
  function fail(error) {
    if (!active || failure) return;
    failure = error instanceof Error ? error : new Error(String(error || '模板渲染失败'));
    layoutBusy = false; visualBusy = false; requested = false;
    clearLater(layoutTimer); layoutTimer = 0;
    notifyLayoutWaiters(failure);
    post('error', {message: failure.message});
  }

  async function dispatch(command) {
    var args = Array.isArray(command.args) ? command.args : [], method = command.method;
    if (method === 'setToken') {
      setMotionState('settled');
      token = args[0]; imageSequence = 0; lastImageTap = null; lastImageAction = 0;
      closeImageOverlay(); clearSelection(); finishInteraction();
    } else if (command.token !== token) return;
    if (method === 'dismissAnnotation') closeImageOverlay();
    await waitForLayout();
    if (!active || failure) return;
    // These commands do not change the page. The command chain has already
    // awaited any earlier visual commit; do not add two more frames to every
    // position report or empty selection clear.
    if (method === 'report' || method === 'setTextImageMode') {
      if (method === 'setTextImageMode') { textImageMode = String(args[0]); lastImageTap = null; }
      postState(command.requestId);
      return;
    }
    if (method === 'clearSelection') {
      var selectionChanged = clearSelection();
      if (selectionChanged) { await waitForLayout(); await twoFrames(); }
      if (active && !failure) postState(command.requestId);
      return;
    }
    if (method === 'setTemplateMotionState') {
      setMotionState(String(args[0]));
      // Only snapshot settling needs its own compositor barrier. DOWN pauses
      // presentation immediately; holding the command queue for two frames here
      // would delay the page commit queued by the first drag MOVE. That commit
      // already waits for its complete target frame before acknowledging it.
      if (String(args[0]) === 'settled') await twoFrames();
      if (active && !failure) postState(command.requestId);
      return;
    }
    visualBusy = true;
    try {
      if (method === 'setPage' || method === 'setActivationPage' || method === 'commitPage') {
        if (method === 'commitPage') {
          await updateFields(args[1]);
          await waitForLayout();
        }
        var requestedBoundary = method === 'commitPage' ? args[2] : method === 'setActivationPage' ? args[0] : '';
        activationBoundary = /^(start|end)$/.test(String(requestedBoundary)) ? requestedBoundary : '';
        var target = activationBoundary === 'start' ? 0 : activationBoundary === 'end' ? pageCount() - 1 :
          method === 'setActivationPage' ? args[1] : args[0];
        showPage(target);
        await emitHook('pageChange', {page: pageAt(pageIndex), pageIndex: pageIndex, pageCount: pageCount()});
      } else if (method === 'setReaderChromeData') {
        if (!await updateFields(args[0])) {
          visualBusy = false;
          postState(command.requestId);
          return;
        }
      } else if (method === 'goToFragment') {
        var id = String(args[0] || '').replace(/^#/, ''), match = /^__legado_text_(\d+)$/.exec(id), found = -1;
        if (match) found = pageForOffset(Number(match[1]), pageMap);
        else for (var index = 0; index < pageMap.length; index++) {
          if (pageMap[index].fragments.some(function (entry) { return entry.id === id; })) { found = index; break; }
        }
        if (found >= 0) {
          activationBoundary = ''; showPage(found);
          await emitHook('pageChange', {page: pageAt(pageIndex), pageIndex: pageIndex, pageCount: pageCount()});
        }
      } else if (method !== 'setToken' && method !== 'dismissAnnotation') throw new Error('未知模板命令：' + method);
      // Commands are serialized, including report. No later report may release
      // an earlier setPage/setToken before its actual DOM and image frame commit.
      for (;;) {
        await waitForLayout();
        var expected = generation;
        await twoFrames();
        if (!layoutBusy && generation === expected) break;
      }
    } finally { visualBusy = false; }
    if (active && !failure) postState(command.requestId, method === 'setToken');
  }
  window.addEventListener('message', function (event) {
    if (!active || event.source !== parent) return;
    var command = event.data;
    if (!command || command.channel !== channel) return;
    if (command.type === 'heartbeat') {
      // A queued page command can legitimately wait for asynchronous pagination.
      // Respond outside that queue; this acknowledges liveness, never readiness.
      if (Number.isSafeInteger(command.token) && Number.isSafeInteger(command.sequence) && command.sequence > 0) {
        parent.postMessage({channel: channel, type: 'heartbeat', token: command.token, sequence: command.sequence}, '*');
      }
      return;
    }
    if (command.type !== 'command') return;
    commandChain = commandChain.then(function () { return dispatch(command); }).catch(fail);
  });

  function resourceUrl(raw) {
    try {
      var url = new URL(raw, init.baseUrl), base = new URL(init.baseUrl);
      return /^https?:$/.test(url.protocol) && url.origin === base.origin && /^\/text-image\//.test(url.pathname) ? url.href : null;
    } catch (_) { return null; }
  }
  function imageState(url) {
    return new Promise(function (resolve) {
      var xhr = new XMLHttpRequest(), settled = false;
      sourceRequests.add(xhr);
      function finish(value) { if (settled) return; settled = true; sourceRequests.delete(xhr); resolve(value); }
      xhr.open('GET', url + '/state', true); xhr.timeout = 3000;
      xhr.onload = function () { var value = null; if (xhr.status === 200) { try { value = JSON.parse(xhr.responseText); } catch (_) {} } finish(value); };
      xhr.onerror = xhr.ontimeout = xhr.onabort = function () { finish(null); };
      try { xhr.send(); } catch (_) { finish(null); }
    });
  }
  async function resolveSourceImage(image) {
    var url = resourceUrl(image.getAttribute('data-legado-image-resource')), started = Date.now(), loadingSince = 0, attempt = 0;
    function markFailed() {
      image.setAttribute('data-legado-image-state', 'failed');
      image.setAttribute('aria-label', '图片加载失败'); image.setAttribute('title', '图片加载失败，刷新章节可重试');
      image.src = failedImage; resourcesFailed = true;
    }
    if (!url) { markFailed(); return; }
    while (active && !failure && source.contains(image)) {
      if (Date.now() - started > 120000 || (loadingSince && Date.now() - loadingSince > 30000)) { markFailed(); return; }
      var state = await imageState(url);
      if (!active || failure) return;
      if (state && state.state === 'pending') {
        if (state.queued === true) loadingSince = 0; else if (!loadingSince) loadingSince = Date.now();
        await new Promise(function (resolve) { later(resolve, Math.min(750, 100 + (attempt++) * 100)); });
        continue;
      }
      if (!state || state.state !== 'ready') { markFailed(); return; }
      var bubble = state.bubble === true, scale = bubble ? Number(state.scale) : 1;
      image.classList.toggle('legado-text-bubble', bubble);
      image.style.fontSize = scale >= .5 && scale <= 1.5 && scale !== 1 ? (scale * 100) + '%' : '';
      image.src = url;
      var decoded = await imageReady(image, false);
      if (decoded) {
        image.setAttribute('data-legado-image-state', 'ready'); image.removeAttribute('aria-label'); return;
      }
      // Native cache entries can expire between /state and the body fetch.
      var retry = await imageState(url);
      if (retry && retry.state === 'ready') { image.src = url + '?retry=1'; decoded = await imageReady(image, false); }
      if (decoded) { image.setAttribute('data-legado-image-state', 'ready'); image.removeAttribute('aria-label'); }
      else markFailed();
      return;
    }
  }
  function startSourceImages() {
    var images = Array.prototype.slice.call(source.querySelectorAll('img[data-legado-image-resource]')), next = 0;
    sourceImagesPending = images.length;
    var workers = [];
    async function worker() {
      while (active && !failure && next < images.length) {
        var image = images[next++];
        try { await resolveSourceImage(image); } catch (_) { resourcesFailed = true; }
        if (!active) return;
        sourceImagesPending = Math.max(0, sourceImagesPending - 1);
        requestLayout('source-image');
      }
    }
    for (var index = 0; index < Math.min(4, images.length); index++) workers.push(worker());
    postState();
    return Promise.all(workers);
  }
  var selectionReported = false;
  function releaseSelectionLayout() {
    if (selectionDeferredLayout) { selectionDeferredLayout = false; requestLayout('selection-end'); }
  }
  function clearSelection() {
    clearLater(selectionTimer);
    var selection = getSelection(), hadSelection = selectionReported || !!(selection && !selection.isCollapsed);
    selectionReported = false;
    if (selection && selection.rangeCount) selection.removeAllRanges();
    if (hadSelection) post('selection', {text: '', rects: [], viewportWidth: viewport.width, viewportHeight: viewport.height});
    releaseSelectionLayout();
    return hadSelection;
  }
  var selectionTimer = 0;
  document.addEventListener('selectionchange', function () {
    clearLater(selectionTimer);
    selectionTimer = later(function () {
      var selection = getSelection(), text = selection ? selection.toString() : '', rects = [];
      if (!text && !selectionReported) { releaseSelectionLayout(); return; }
      if (selection && text) for (var index = 0; index < selection.rangeCount; index++) {
        Array.prototype.forEach.call(selection.getRangeAt(index).getClientRects(), function (rect) {
          if (rects.length < 512 && viewportRect(rect)) rects.push({left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom});
        });
      }
      selectionReported = !!text;
      post('selection', {text: text, rects: rects, viewportWidth: viewport.width, viewportHeight: viewport.height});
      if (!text) releaseSelectionLayout();
    }, 20);
  });
  function imageTarget(target) {
    var element = target && target.nodeType === Node.ELEMENT_NODE ? target : target && target.parentElement;
    if (!element || !element.closest || element.closest('#legado-epub-image-overlay')) return null;
    var image = element.closest('img'), action = element.closest('a[data-legado-image-action]');
    if (!image && action) image = action.querySelector('img[data-legado-image-id]');
    return image && committedRoot && committedRoot.contains(image) ? image : null;
  }
  function closeImageOverlay() {
    if (imageOverlayClose) imageOverlayClose();
  }
  function openImageOverlay(url) {
    if (!imageOverlayNode) {
      var overlay = document.createElement('div'); overlay.id = 'legado-epub-image-overlay';
      overlay.setAttribute('role', 'dialog'); overlay.setAttribute('aria-label', '查看图片');
      var image = document.createElement('img'); image.alt = '';
      var button = document.createElement('button'); button.type = 'button'; button.textContent = '关闭'; button.setAttribute('aria-label', '关闭图片');
      overlay.appendChild(image); overlay.appendChild(button);
      var scale = 1, startDistance = 0, startX = 0, startY = 0, panX = 0, panY = 0, moved = false;
      function apply() { image.style.transform = 'translate(' + panX + 'px,' + panY + 'px) scale(' + scale + ')'; }
      imageOverlayClose = function () {
        if (!overlay.classList.contains('legado-visible')) return;
        overlay.classList.remove('legado-visible'); image.removeAttribute('src');
        scale = 1; panX = panY = 0; apply(); finishInteraction(); post('annotationState', {visible: false});
      };
      button.addEventListener('click', function (event) { event.preventDefault(); event.stopPropagation(); imageOverlayClose(); });
      overlay.addEventListener('click', function (event) {
        event.preventDefault(); event.stopPropagation();
        if (!moved) imageOverlayClose();
      });
      overlay.addEventListener('touchstart', function (event) {
        moved = false;
        if (event.touches.length === 2) {
          var dx = event.touches[0].clientX - event.touches[1].clientX, dy = event.touches[0].clientY - event.touches[1].clientY;
          startDistance = Math.sqrt(dx * dx + dy * dy);
        } else if (event.touches.length === 1) {
          startX = event.touches[0].clientX; startY = event.touches[0].clientY;
        }
      }, {passive: true});
      overlay.addEventListener('touchmove', function (event) {
        if (event.touches.length === 2 && startDistance > 0) {
          var dx = event.touches[0].clientX - event.touches[1].clientX, dy = event.touches[0].clientY - event.touches[1].clientY;
          var distance = Math.sqrt(dx * dx + dy * dy);
          scale = Math.max(1, Math.min(4, scale * distance / startDistance)); startDistance = distance;
          moved = true; apply(); event.preventDefault();
        } else if (event.touches.length === 1 && scale > 1.02) {
          var x = event.touches[0].clientX, y = event.touches[0].clientY;
          panX += x - startX; panY += y - startY; startX = x; startY = y;
          moved = true; apply(); event.preventDefault();
        }
      }, {passive: false});
      overlay.addEventListener('touchend', function () { startDistance = 0; }, {passive: true});
      overlay.addEventListener('touchcancel', function () { startDistance = 0; moved = true; }, {passive: true});
      document.documentElement.appendChild(overlay); imageOverlayNode = overlay;
    }
    clearSelection();
    imageOverlayNode.querySelector('img').src = url;
    imageOverlayNode.classList.add('legado-visible');
    post('annotationState', {visible: true});
  }
  function sendImage(image) {
    var url = image && (image.currentSrc || image.src);
    if (url) { openImageOverlay(url); post('image', {url: url}); }
  }
  function clearImagePress() { if (imagePress) clearLater(imagePress.timer); imagePress = null; }
  function reportInteraction(value) {
    if (!touch || touch.active === value) return;
    touch.active = value; post('embeddedInteraction', {interactionId: touch.id, active: value});
  }
  function finishInteraction() {
    reportInteraction(false);
    touch = null; clearImagePress();
  }
  function interactiveTarget(element) {
    if (!element || !element.closest) return null;
    var hard = !!element.closest('#legado-epub-image-overlay,video,audio,button,input,select,textarea,summary,label,iframe,embed,object,[contenteditable]:not([contenteditable="false"]),[role="button"],[role="slider"],[role="spinbutton"],[role="textbox"],[draggable="true"]');
    var image = imageTarget(element), sourceImage = image && image.hasAttribute('data-legado-image-id');
    // Generated source-image anchors own taps, never the entire drag starting at
    // DOWN. This matches the ordinary reader's bubble/page-turn gesture contract.
    if (!sourceImage && element.closest('a[href],[onclick]')) hard = true;
    var scrollers = [], node = element;
    while (node && node !== committedRoot && node !== document.documentElement) {
      // The template's primary reading frame has the same gesture ownership as
      // the former outer stage. Only additional embedded widgets claim drags.
      if (node === scrollingElement(committedRoot)) { node = node.parentElement; continue; }
      var style = getComputedStyle(node), overflowX = Math.max(0, node.scrollWidth - node.clientWidth), overflowY = Math.max(0, node.scrollHeight - node.clientHeight);
      var x = /^(auto|scroll|overlay)$/.test(style.overflowX) && overflowX > 1;
      var y = /^(auto|scroll|overlay)$/.test(style.overflowY) && overflowY > 1;
      if (x || y) scrollers.push({node: node, rtl: style.direction === 'rtl', x: x, y: y, overflowX: overflowX, overflowY: overflowY});
      node = node.parentElement;
    }
    return hard || scrollers.length ? {hard: hard, scrollers: scrollers} : null;
  }
  function scrollableForGesture(entry, dx, dy) {
    if (Math.abs(dx) > Math.abs(dy) && entry.x) {
      var minimum = entry.rtl ? -entry.overflowX : 0, maximum = entry.rtl ? 0 : entry.overflowX;
      return -dx < 0 ? entry.node.scrollLeft > minimum + 1 : -dx > 0 && entry.node.scrollLeft < maximum - 1;
    }
    if (Math.abs(dy) >= Math.abs(dx) && entry.y) return -dy < 0 ? entry.node.scrollTop > 1 : -dy > 0 && entry.node.scrollTop < entry.overflowY - 1;
    return false;
  }
  function overlayVisible() { return !!(imageOverlayNode && imageOverlayNode.classList.contains('legado-visible')); }
  function selectionVisible() { var selection = getSelection(); return !!(selection && !selection.isCollapsed && selection.toString()); }
  document.addEventListener('touchstart', function (event) {
    finishInteraction(); scrollTouch = null;
    if (!event.isTrusted || event.touches.length !== 1) return;
    var element = event.target.nodeType === Node.ELEMENT_NODE ? event.target : event.target.parentElement;
    var image = imageTarget(element), point = event.touches[0];
    var interactive = interactiveTarget(element);
    if (interactive) {
      touch = {id: ++interactionSequence, x: point.clientX, y: point.clientY, active: false, hard: interactive.hard, scrollers: interactive.scrollers};
      if (interactive.hard) reportInteraction(true);
    }
    var scroller = committedRoot && scrollingElement(committedRoot);
    if (scrollMode && scroller && scroller.contains(element) && !overlayVisible() && !selectionVisible() && !(interactive && interactive.hard)) {
      activationBoundary = '';
      scrollTouch = {x: point.clientX, y: point.clientY, scroller: scroller, top: scroller.scrollTop <= 1,
        bottom: scroller.scrollTop >= scroller.scrollHeight - scroller.clientHeight - 1};
    }
    if (image) {
      imagePress = {image: image, x: point.clientX, y: point.clientY, opened: false};
      var state = imagePress;
      state.timer = later(function () {
        if (imagePress !== state) return;
        state.opened = true; suppressImageClickUntil = Date.now() + 700; sendImage(image);
      }, 500);
    }
  }, {capture: true, passive: true});
  document.addEventListener('touchmove', function (event) {
    var point = event.touches[0];
    if (touch && !touch.hard && point) {
      var dx = point.clientX - touch.x, dy = point.clientY - touch.y;
      if (Math.max(Math.abs(dx), Math.abs(dy)) >= 4) reportInteraction(touch.scrollers.some(function (entry) { return scrollableForGesture(entry, dx, dy); }));
    }
    if (imagePress && (!point || Math.abs(point.clientX - imagePress.x) >= 12 || Math.abs(point.clientY - imagePress.y) >= 12)) clearImagePress();
    if (event.touches.length !== 1) scrollTouch = null;
  }, {capture: true, passive: true});
  document.addEventListener('touchend', function (event) {
    var opened = imagePress && imagePress.opened;
    var boundaryTouch = scrollTouch, owned = touch && touch.active, point = event.changedTouches[0];
    scrollTouch = null;
    if (event.isTrusted && boundaryTouch && boundaryTouch.scroller === scrollingElement(committedRoot) && point && !opened && !owned && !overlayVisible() && !selectionVisible() && !layoutBusy) {
      var dx = point.clientX - boundaryTouch.x, dy = point.clientY - boundaryTouch.y;
      if (Math.abs(dy) > 48 && Math.abs(dy) > Math.abs(dx) * 1.2) {
        var scroller = boundaryTouch.scroller;
        var top = scroller.scrollTop <= 1, bottom = scroller.scrollTop >= scroller.scrollHeight - scroller.clientHeight - 1;
        if (dy > 0 && boundaryTouch.top && top) post('boundary', {direction: -1});
        else if (dy < 0 && boundaryTouch.bottom && bottom) post('boundary', {direction: 1});
      }
    }
    finishInteraction();
    if (opened) { suppressImageClickUntil = Date.now() + 700; event.preventDefault(); event.stopPropagation(); }
  }, {capture: true, passive: false});
  document.addEventListener('touchcancel', function () { scrollTouch = null; finishInteraction(); }, {capture: true, passive: true});
  window.addEventListener('blur', finishInteraction);
  document.addEventListener('contextmenu', function (event) {
    var image = imageTarget(event.target);
    if (!image || !event.isTrusted) return;
    event.preventDefault(); event.stopPropagation();
    if (Date.now() < suppressImageClickUntil) return;
    suppressImageClickUntil = Date.now() + 700; clearImagePress(); sendImage(image);
  }, true);
  document.addEventListener('click', function (event) {
    var image = imageTarget(event.target);
    if (image && Date.now() < suppressImageClickUntil) { event.preventDefault(); event.stopPropagation(); return; }
    var action = image && image.closest('a[data-legado-image-action]');
    if (image && (action || textImageMode === '1')) {
      event.preventDefault(); event.stopPropagation();
      if (!event.isTrusted || textImageMode === '3' || layoutBusy || visualBusy) return;
      if (textImageMode === '1') { sendImage(image); return; }
      var id = action && action.getAttribute('data-legado-image-action');
      if (!/^image-\d+$/.test(id || '') || !imageIds.has(id)) return;
      var now = Date.now();
      if (textImageMode === '4') {
        var previous = lastImageTap;
        lastImageTap = {id: id, at: now, page: pageIndex, revision: layoutRevision};
        if (!previous || previous.id !== id || now - previous.at > 300 || previous.page !== pageIndex || previous.revision !== layoutRevision) return;
        lastImageTap = null;
      }
      if (now - lastImageAction < 300) return;
      lastImageAction = now; post('sourceImage', {imageId: id, sequence: ++imageSequence}); return;
    }
    // Author click handlers run normally. Only navigation itself is delegated to
    // the host; preventDefault is observed after the event has reached the target.
    var element = event.target.nodeType === Node.ELEMENT_NODE ? event.target : event.target.parentElement;
    var anchor = element && element.closest('a[href]');
    if (anchor && !event.defaultPrevented) {
      var href = anchor.getAttribute('href') || '';
      // JavaScript URLs execute normally in the sandbox and never cross the
      // Android navigation bridge. Author preventDefault is respected as well.
      if (!/^\s*javascript:/i.test(href)) { event.preventDefault(); post('link', {url: anchor.href}); }
    }
  });

  window.addEventListener('resize', function () { updateViewport(); requestLayout('viewport'); });
  document.addEventListener('visibilitychange', function () { updateActiveClock(); syncMotion(); });
  if (reducedMotion) {
    if (reducedMotion.addEventListener) reducedMotion.addEventListener('change', syncMotion);
    else if (reducedMotion.addListener) reducedMotion.addListener(syncMotion);
  }
  window.addEventListener('error', function (event) {
    if (event.error || event.message) fail(new Error('模板脚本错误：' + (event.message || event.error.message)));
  });
  window.addEventListener('unhandledrejection', function (event) { fail(new Error('模板异步脚本错误：' + String(event.reason && event.reason.message || event.reason))); });
  window.addEventListener('pagehide', function () {
    setMotionState('paused');
    // Disposal callbacks are cleanup, not pagination prerequisites. Their errors
    // must not keep a detached document or a pending command alive.
    (hooks.dispose || []).splice(0).forEach(function (callback) {
      try { Promise.resolve(callback({page: pageAt(pageIndex)}, api)).catch(function () {}); } catch (_) {}
    });
    lastMotionPage = null;
    if (reducedMotion) {
      if (reducedMotion.removeEventListener) reducedMotion.removeEventListener('change', syncMotion);
      else if (reducedMotion.removeListener) reducedMotion.removeListener(syncMotion);
    }
    finishInteraction(); active = false; generation++;
    timers.forEach(clearTimeout); timers.clear();
    sourceRequests.forEach(function (xhr) { xhr.abort(); }); sourceRequests.clear();
    if (sourceObserver) sourceObserver.disconnect(); if (pageObserver) pageObserver.disconnect(); if (sizeObserver) sizeObserver.disconnect();
    if (headObserver) headObserver.disconnect();
    notifyLayoutWaiters(new Error('模板页面已关闭'));
  }, {once: true});

  post('boot');
  (async function () {
    if (!continuous && (!window.Paged || typeof Paged.Layout !== 'function')) throw new Error('模板分页组件未加载');
    if (!window.ReaderTemplateSourceMap) throw new Error('模板正文位置组件未加载');
    if (continuous ? !template.scrollHtml : !template.firstPageHtml || !template.otherPageHtml) throw new Error(continuous ? '模板缺少滚动 HTML' : '模板缺少首页或续页 HTML');
    updateViewport();
    var base = document.createElement('base'); base.href = init.baseUrl || location.href; document.head.prepend(base);
    document.body.setAttribute('data-legado-text-reader', 'true');
    document.body.setAttribute('data-reader-theme', 'template');
    document.body.setAttribute('data-reader-scroll', scrollMode ? 'true' : 'false');
    document.body.setAttribute('data-reader-template-type', continuous ? 'scroll' : 'paged');
    appendStyle('html,body{margin:0;width:100%;height:100%;overflow:hidden;}[data-reader-runtime-stage]{position:fixed;inset:0;overflow:hidden;}[data-reader-runtime-stage="pending"]{opacity:0!important;z-index:-1;pointer-events:none!important;}'+
      'body[data-reader-scroll="true"] [data-reader-runtime-stage="committed"]{overflow-y:auto;overflow-x:hidden;overscroll-behavior:contain;}'+
      'body[data-reader-template-type="scroll"] [data-reader-runtime-stage]{overflow-y:auto;overflow-x:hidden;}'+
      '.reader-template-page{position:absolute;inset:0;width:100%;height:100%;box-sizing:border-box;overflow:hidden;}'+
      'body[data-reader-scroll="false"] [data-reader-runtime-stage="committed"]>.reader-template-page[data-reader-active="false"]{visibility:hidden;pointer-events:none;}'+
      '.reader-template-flow-viewport{display:flow-root;position:relative;overflow:hidden;box-sizing:content-box;min-width:0;min-height:0;}'+
      '.reader-template-flow-content{display:flow-root;box-sizing:border-box;width:100%;min-width:0;}'+
      '.reader-template-flow-content img{max-width:100%;max-height:calc(var(--reader-flow-height) - 1em);object-fit:contain;}'+
      'body[data-reader-template-type="scroll"] .reader-template-page{position:relative!important;inset:auto!important;height:auto!important;min-height:100%;overflow:visible;}'+
      'body[data-reader-template-type="scroll"] [data-reader-flow]{height:auto!important;max-height:none!important;overflow:visible;column-count:auto;column-width:auto;}'+
      'body[data-reader-template-type="scroll"] .reader-template-flow-viewport{width:100%;height:auto!important;max-height:none!important;overflow:visible;column-count:auto;column-width:auto;}'+
      'body[data-reader-template-type="scroll"] .reader-template-flow-content{height:auto!important;max-height:none!important;column-count:auto!important;column-width:auto!important;}'+
      'body[data-reader-template-type="scroll"] .reader-template-flow-content img{max-height:none;}'+
      'body[data-reader-template-type="scroll"] [data-reader-runtime-stage][data-reader-scroll-layout="framed"]{overflow:hidden!important;}'+
      'body[data-reader-template-type="scroll"] [data-reader-scroll-layout="framed"]>.reader-template-page{height:100%!important;min-height:0;overflow:hidden;}'+
      'body[data-reader-template-type="scroll"] [data-reader-scroll-viewport]{min-width:0;min-height:0;overflow-y:auto!important;overflow-x:hidden!important;overscroll-behavior:contain;overflow-anchor:none;scroll-behavior:auto!important;}'+
      '.reader-template-flow-content figure{margin:.4em 0;}'+
      '.reader-template-flow-content [data-split-from]{margin-top:0;text-indent:0;}[data-reader-field]{font-variant-numeric:tabular-nums;}'+
      '#legado-epub-image-overlay{position:fixed!important;inset:0!important;z-index:2147483647!important;display:none;align-items:center;justify-content:center;background:rgba(0,0,0,.94);overflow:hidden;touch-action:none;}'+
      '#legado-epub-image-overlay.legado-visible{display:flex;}#legado-epub-image-overlay>img{max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain;transform-origin:center;}'+
      '#legado-epub-image-overlay>button{position:absolute;right:12px;top:12px;font-size:16px;padding:8px 14px;color:white;background:#333;border:1px solid #999;border-radius:6px;}', 'reader-template-runtime-style');
    appendStyle(init.baseCss, 'reader-template-base-style');
    prepareSource();
    appendStyle(template.css, 'reader-template-author-style');
    if (template.javascript) {
      var script = document.createElement('script'); script.textContent = String(template.javascript); document.head.appendChild(script);
    }
    await drainAuthorWork();
    if (failure) throw failure;
    await waitStyles(document.head);
    await waitBackgrounds(document.body);
    await Promise.all(Array.prototype.map.call(source.querySelectorAll('img'), function (image) { return imageReady(image, true); }));
    if (window.MutationObserver) {
      sourceObserver = new MutationObserver(sourceMutation);
      sourceObserver.observe(source, {subtree: true, childList: true, characterData: true, attributes: true});
      headObserver = new MutationObserver(function (mutations) {
        if (mutations.some(function (mutation) {
          var element = mutation.target.nodeType === Node.ELEMENT_NODE ? mutation.target : mutation.target.parentElement;
          return element && (element.tagName === 'STYLE' || element.tagName === 'LINK' || mutation.type === 'childList');
        })) {
          waitStyles(document.head).then(function () { requestLayout('author-stylesheet'); }).catch(fail);
        }
      });
      headObserver.observe(document.head, {subtree: true, childList: true, characterData: true, attributes: true});
    }
    if (document.fonts && document.fonts.addEventListener) {
      document.fonts.addEventListener('loadingdone', function () { if (!layoutBusy) requestLayout('fonts'); });
      document.fonts.addEventListener('loadingerror', function () { resourcesFailed = true; if (!layoutBusy) requestLayout('fonts'); });
    }
    // Metadata can change both bubble width and its image pixels. Resolve it
    // before the first pagination, so neither ready/stable nor a command ack can
    // expose a placeholder page whose geometry will change immediately afterward.
    await startSourceImages();
    if (!active || failure) return;
    initialized = true;
    requestLayout('initial');
  })().catch(fail);
})();
