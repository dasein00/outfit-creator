// Читалка DASEIN: глава книги в WebView. Страницы — колонками CSS, прокрутка — обычным скроллом.
// Связь с приложением через объект A (JavascriptInterface): позиция, края главы, нажатия, ссылки, выделения.
(function () {
  'use strict';
  var A = window.A || {
    ready: function () {}, pos: function () {}, edge: function () {}, tap: function () {}, link: function () {}, mark: function () {},
    ext: function () {}, image: function () {}, log: function (s) { console.log(s); }, loaded: function () {},
  };
  var vp = document.getElementById('vp'), book = document.getElementById('book'), noteBox = document.getElementById('note');
  var S = { paged: true, anim: true, files: [] };
  var ch = -1, html = '', marks = [], page = 0, pages = 1, W = 1, H = 1, nodes = null, last = false, busy = false;

  function log(s) { try { A.log(String(s)); } catch (e) {} }

  // ---------- текстовые смещения ----------
  function index() {
    var list = [], tw = document.createTreeWalker(book, NodeFilter.SHOW_TEXT, null), n, pos = 0;
    while ((n = tw.nextNode())) {
      if (n.parentNode && n.parentNode.closest && n.parentNode.closest('#end')) continue;
      list.push({ n: n, s: pos });
      pos += n.data.length;
    }
    nodes = list;
    return list;
  }
  function textLen() { var l = nodes || index(); if (!l.length) return 0; var x = l[l.length - 1]; return x.s + x.n.data.length; }
  function locate(off) {
    var l = nodes || index();
    if (!l.length) return null;
    var lo = 0, hi = l.length - 1;
    while (lo < hi) { var mid = (lo + hi + 1) >> 1; if (l[mid].s <= off) lo = mid; else hi = mid - 1; }
    var it = l[lo];
    return { n: it.n, o: Math.max(0, Math.min(it.n.data.length, off - it.s)) };
  }
  function offsetOf(container, off) {
    try {
      var r = document.createRange();
      r.setStart(book, 0);
      r.setEnd(container, off);
      return r.toString().length;
    } catch (e) { return 0; }
  }
  function rangeAt(s, e) {
    var a = locate(s), b = locate(Math.max(s, e));
    if (!a || !b) return null;
    var r = document.createRange();
    r.setStart(a.n, a.o); r.setEnd(b.n, b.o);
    return r;
  }

  // ---------- раскладка ----------
  function layout() {
    W = vp.clientWidth || window.innerWidth; H = vp.clientHeight || window.innerHeight;
    lastW = W; lastH = H;
    if (S.paged) {
      // Высота страницы и ширина колонки — в пикселях от настоящего окна. 100vh в WebView бывает почти нулевым,
      // пока окно ещё не разложено, — тогда каждая «страница» выходила в одну строку под шапкой, а остальное пустым.
      var cs = getComputedStyle(book);
      var m = (parseFloat(cs.paddingLeft) || 0);
      book.style.height = H + 'px';
      book.style.columnWidth = Math.max(40, W - 2 * m) + 'px';
      book.style.columnGap = (2 * m) + 'px';
    } else {
      book.style.height = ''; book.style.columnWidth = ''; book.style.columnGap = '';
    }
    if (S.paged) pages = Math.max(1, Math.round(book.scrollWidth / W));
    else pages = Math.max(1, Math.ceil(vp.scrollHeight / H));
  }
  function pageOfRect(r) {
    if (S.paged) return Math.max(0, Math.min(pages - 1, Math.floor((r.left + vp.scrollLeft + 1) / W)));
    return Math.max(0, Math.floor((r.top + vp.scrollTop) / H));
  }
  function show(p, smooth) {
    page = Math.max(0, Math.min(pages - 1, p));
    if (S.paged) {
      if (smooth && S.anim && vp.scrollTo) vp.scrollTo({ left: page * W, behavior: 'smooth' }); else vp.scrollLeft = page * W;
    }
    report();
  }
  // Первый видимый символ страницы.
  function curOffset() {
    var all = nodes || index();
    if (!all.length) return 0;
    // Узлы из одних пробелов не имеют рамок — ищем только по узлам с текстом.
    var l = all.filter(function (x) { return /\S/.test(x.n.data); });
    if (!l.length) return 0;
    var vis = function (r) { return S.paged ? r.right > 1 : r.bottom > 1; };
    var r = document.createRange();
    var lo = 0, hi = l.length - 1, found = l.length - 1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      r.selectNodeContents(l[mid].n);
      var rects = r.getClientRects();
      var lastR = rects.length ? rects[rects.length - 1] : null;
      if (lastR && vis(lastR)) { found = mid; hi = mid - 1; } else if (!lastR) { lo = mid + 1; } else lo = mid + 1;
    }
    var n = l[found].n, a = 0, b = n.data.length;
    // Внутри узла — двоичный поиск символа.
    while (a < b) {
      var m = (a + b) >> 1;
      r.setStart(n, m); r.setEnd(n, Math.min(n.data.length, m + 1));
      var rr = r.getBoundingClientRect();
      if (rr.width === 0 && rr.height === 0) { a = m + 1; continue; }
      if (vis(rr)) b = m; else a = m + 1;
    }
    return l[found].s + a;
  }
  function goOffset(off) {
    layout();
    var p = locate(off);
    if (!p) { show(0); return; }
    var r = document.createRange();
    r.setStart(p.n, p.o); r.setEnd(p.n, Math.min(p.n.data.length, p.o + 1));
    var rect = r.getBoundingClientRect();
    if (S.paged) show(pageOfRect(rect));
    else { vp.scrollTop = Math.max(0, rect.top + vp.scrollTop - 12); report(); }
  }
  function goAnchor(id) {
    var el = id && document.getElementById(id);
    if (!el) { show(0); return; }
    layout();
    var rect = el.getBoundingClientRect();
    if (S.paged) show(pageOfRect(rect)); else { vp.scrollTop = Math.max(0, rect.top + vp.scrollTop - 12); report(); }
  }

  var reportTimer = 0;
  function report() {
    clearTimeout(reportTimer);
    reportTimer = setTimeout(function () {
      if (!S.paged) { layout(); page = Math.round(vp.scrollTop / H); }
      try { A.pos(JSON.stringify({ ch: ch, page: page, pages: pages, offset: curOffset(), len: textLen(), atEnd: atEnd() })); } catch (e) { log(e); }
    }, S.paged ? 30 : 180);
  }
  function atEnd() { return S.paged ? page >= pages - 1 : vp.scrollTop + H >= vp.scrollHeight - 4; }

  // ---------- листание ----------
  function next() {
    if (busy) return;
    hideNote();
    if (S.paged) { if (page < pages - 1) show(page + 1, true); else A.edge(1); }
    else {
      if (atEnd()) A.edge(1);
      else { vp.scrollBy ? vp.scrollBy({ top: H - lineH() * 1.5, behavior: S.anim ? 'smooth' : 'auto' }) : (vp.scrollTop += H - lineH() * 1.5); report(); }
    }
  }
  function prev() {
    if (busy) return;
    hideNote();
    if (S.paged) { if (page > 0) show(page - 1, true); else A.edge(-1); }
    else {
      if (vp.scrollTop <= 2) A.edge(-1);
      else { vp.scrollBy ? vp.scrollBy({ top: -(H - lineH() * 1.5), behavior: S.anim ? 'smooth' : 'auto' }) : (vp.scrollTop -= H - lineH() * 1.5); report(); }
    }
  }
  function lineH() { return parseFloat(getComputedStyle(book).fontSize) * (parseFloat(S.lh) || 1.5); }

  // ---------- выделения и заметки ----------
  function unwrap(sel) {
    var list = book.querySelectorAll(sel);
    for (var i = 0; i < list.length; i++) {
      var m = list[i], p = m.parentNode;
      while (m.firstChild) p.insertBefore(m.firstChild, m);
      p.removeChild(m);
      p.normalize();
    }
    nodes = null;
  }
  function wrap(s, e, cls, id) {
    if (e <= s) return;
    var l = index();
    var parts = [];
    for (var i = 0; i < l.length; i++) {
      var a = l[i].s, b = a + l[i].n.data.length;
      if (b <= s || a >= e) continue;
      parts.push({ n: l[i].n, from: Math.max(0, s - a), to: Math.min(l[i].n.data.length, e - a) });
    }
    parts.forEach(function (p) {
      var n = p.n;
      if (!n.parentNode || /^(script|style)$/i.test(n.parentNode.nodeName)) return;
      if (p.to <= p.from) return;
      if (!n.data.slice(p.from, p.to).trim() && n.parentNode.nodeName !== 'P') return;
      var mid = p.from > 0 ? n.splitText(p.from) : n;
      if (p.to - p.from < mid.data.length) mid.splitText(p.to - p.from);
      var mk = document.createElement('mark');
      mk.className = cls;
      if (id != null) mk.setAttribute('data-id', id);
      mid.parentNode.insertBefore(mk, mid);
      mk.appendChild(mid);
    });
    nodes = null;
  }
  function applyMarks() {
    unwrap('mark.hl');
    marks.forEach(function (m) { wrap(m.s, m.e, 'hl c' + (m.c || 0) + (m.n ? ' hasnote' : ''), m.id); });
  }

  // ---------- картинки: раскладка после загрузки ----------
  function imagesReady() {
    var imgs = Array.prototype.slice.call(book.querySelectorAll('img'));
    return Promise.all(imgs.map(function (im) {
      if (im.complete) return Promise.resolve();
      return new Promise(function (res) { im.onload = im.onerror = res; setTimeout(res, 2500); });
    }));
  }

  // ---------- загрузка главы ----------
  function render(at) {
    book.innerHTML = html + (last ? '<div id="end">' + (S.endText || 'Конец книги') + '</div>' : '');
    hideNote();
    nodes = null;
    applyMarks();
    page = 0;
    vp.scrollLeft = 0; vp.scrollTop = 0;
    return imagesReady().then(function () {
      layout();
      position(at);
      busy = false;
      try { A.loaded(ch); } catch (e) {}
    });
  }
  function position(at) {
    if (at === 'end') { layout(); if (S.paged) show(pages - 1); else { vp.scrollTop = vp.scrollHeight; report(); } }
    else if (typeof at === 'number') goOffset(at);
    else if (at && typeof at === 'object' && at.f != null) {
      layout();
      if (S.paged) show(Math.round(at.f * (pages - 1)));
      else { vp.scrollTop = at.f * (vp.scrollHeight - H); report(); }
    } else if (typeof at === 'string' && at && at !== 'start') goAnchor(at);
    else show(0);
  }

  window.R = {
    setup: function (s) {
      var o = curOffset();
      var changedMode = S.paged !== s.paged;
      for (var k in s) S[k] = s[k];
      var st = document.documentElement.style, t = S.theme || {};
      st.setProperty('--bg', t.bg || '#fff'); st.setProperty('--fg', t.fg || '#1f1f1f'); st.setProperty('--dim', t.dim || '#888');
      st.setProperty('--link', t.link || '#b5651d'); st.setProperty('--accent', t.accent || '#e8913a');
      st.setProperty('--fs', (S.fs || 19) + 'px'); st.setProperty('--lh', String(S.lh || 1.5));
      st.setProperty('--m', (S.m != null ? S.m : 20) + 'px'); st.setProperty('--font', S.font || 'serif');
      st.setProperty('--indent', S.indent === false ? '0' : '1.4em'); st.setProperty('--align', S.align || 'justify');
      st.setProperty('--para', (S.para != null ? S.para : 0.35) + 'em');
      st.setProperty('--pt', (S.pt != null ? S.pt : 18) + 'px'); st.setProperty('--pb', (S.pb != null ? S.pb : 18) + 'px');
      if (S.lang) document.documentElement.lang = S.lang;
      document.body.className = (S.paged ? 'paged' : 'scroll') + (S.hyph === false ? ' nohyph' : '') + (t.dark ? ' dark' : '');
      if (ch >= 0) {
        if (changedMode) { vp.scrollLeft = 0; vp.scrollTop = 0; }
        nodes = null;
        setTimeout(function () { layout(); goOffset(o); }, 30);
      }
    },
    load: function (c, url, at, opts) {
      busy = true;
      opts = opts || {};
      last = !!opts.last;
      marks = opts.marks || [];
      return fetch(url).then(function (r) { return r.text(); }).then(function (t) {
        ch = c; html = t;
        return render(at);
      }).catch(function (e) { busy = false; log('load ' + e); });
    },
    next: next, prev: prev,
    goOffset: function (o) { goOffset(o); },
    goAnchor: function (a) { goAnchor(a); },
    goFraction: function (f) { position({ f: f }); },
    goPage: function (p) { layout(); if (S.paged) show(p); else { vp.scrollTop = p * H; report(); } },
    setMarks: function (m) { var o = curOffset(); marks = m || []; applyMarks(); layout(); goOffset(o); },
    pos: function () { return JSON.stringify({ ch: ch, page: page, pages: pages, offset: curOffset(), len: textLen() }); },
    // Выделенный текст: смещения в главе и сам текст.
    selection: function () {
      var s = window.getSelection();
      if (!s || s.isCollapsed || !s.rangeCount) return '';
      var r = s.getRangeAt(0);
      if (!book.contains(r.commonAncestorContainer)) return '';
      var a = offsetOf(r.startContainer, r.startOffset), b = offsetOf(r.endContainer, r.endOffset);
      return JSON.stringify({ s: a, e: b, text: r.toString() });
    },
    clearSelection: function () { var s = window.getSelection(); if (s) s.removeAllRanges(); },
    // Поиск: k-е вхождение (с нуля) — переход и подсветка.
    find: function (q, k) {
      var text = (nodes || index()).map(function (x) { return x.n.data; }).join('').toLowerCase();
      q = String(q).toLowerCase();
      var at = -1, from = 0;
      for (var i = 0; i <= k; i++) { at = text.indexOf(q, from); if (at < 0) break; from = at + 1; }
      if (at < 0) return false;
      unwrap('mark.found');
      wrap(at, at + q.length, 'found');
      goOffset(at);
      setTimeout(function () { unwrap('mark.found'); }, 2500);
      return true;
    },
    // Текст для чтения вслух — от начала страницы (или смещения) примерно на n символов, до конца предложения.
    ttsText: function (from, n) {
      var all = (nodes || index()).map(function (x) { return x.n.data; }).join('');
      var s = from == null || from < 0 ? curOffset() : from;
      if (s >= all.length) return JSON.stringify({ s: s, text: '' });
      var e = Math.min(all.length, s + (n || 1500));
      if (e < all.length) { var m = all.slice(e, e + 300).search(/[.!?…»"]\s/); if (m >= 0) e += m + 1; }
      return JSON.stringify({ s: s, text: all.slice(s, e) });
    },
    ttsMark: function (s, e) {
      unwrap('mark.tts');
      if (e > s) {
        wrap(s, e, 'tts');
        var m = book.querySelector('mark.tts');
        if (m) {
          var r = m.getBoundingClientRect();
          if (S.paged) { var p = pageOfRect(r); if (p !== page) show(p, true); }
          else if (r.top < 0 || r.bottom > H) { vp.scrollTop += r.top - H / 3; report(); }
        }
      }
    },
    ttsClear: function () { unwrap('mark.tts'); },
    text: function () { return (nodes || index()).map(function (x) { return x.n.data; }).join(''); },
    hideNote: function () { hideNote(); },
  };

  // ---------- сноски ----------
  function hideNote() { noteBox.style.display = 'none'; noteBox.innerHTML = ''; }
  function showNote(c, id, text) {
    noteBox.innerHTML = '';
    var t = document.createElement('div');
    t.textContent = text;
    noteBox.appendChild(t);
    var go = document.createElement('a');
    go.className = 'go'; go.textContent = S.noteGo || 'Перейти к сноске →';
    go.onclick = function (e) { e.stopPropagation(); hideNote(); A.link(c, id); };
    noteBox.appendChild(go);
    noteBox.style.display = 'block';
  }
  function noteText(c, id) {
    var url = S.files && S.files[c];
    var local = c === ch ? document.getElementById(id) : null;
    var get = local ? Promise.resolve(local) : (url ? fetch(url).then(function (r) { return r.text(); }).then(function (t) {
      var d = document.createElement('div'); d.innerHTML = t; return d.querySelector('[id="' + id.replace(/"/g, '') + '"]');
    }) : Promise.resolve(null));
    return get.then(function (el) {
      if (!el) return '';
      var txt = el.textContent.replace(/\s+/g, ' ').trim();
      return txt.length > 3000 ? txt.slice(0, 3000) + '…' : txt;
    });
  }

  // ---------- нажатия и жесты ----------
  var tx = 0, ty = 0, tt = 0, swiped = false;
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length !== 1) return;
    tx = e.touches[0].clientX; ty = e.touches[0].clientY; tt = Date.now(); swiped = false;
  }, { passive: true });
  document.addEventListener('touchend', function (e) {
    var t = e.changedTouches[0]; if (!t) return;
    var dx = t.clientX - tx, dy = t.clientY - ty;
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) return;
    if (S.paged && Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.3 && Date.now() - tt < 800) {
      swiped = true;
      if (dx < 0) next(); else prev();
    }
  }, { passive: true });
  if (S.paged) vp.addEventListener('scroll', function () {}, { passive: true });
  vp.addEventListener('scroll', function () { if (!S.paged) { hideNote(); report(); } }, { passive: true });

  document.addEventListener('click', function (e) {
    if (swiped) { swiped = false; return; }
    if (noteBox.contains(e.target)) return;
    if (noteBox.style.display === 'block') { hideNote(); return; }
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) { sel.removeAllRanges(); return; }
    var a = e.target.closest && e.target.closest('a');
    if (a) {
      var href = a.getAttribute('href') || '';
      var m = /^#ch:(\d+):(.*)$/.exec(href);
      if (m) {
        e.preventDefault();
        var c = parseInt(m[1], 10), id = decodeURIComponent(m[2] || '');
        var label = (a.textContent || '').trim();
        var isNote = a.classList.contains('note') || !!a.closest('sup') || /^[\[\(]?\d{1,4}[\]\)]?$|^\*{1,3}$/.test(label);
        if (isNote && id) {
          noteText(c, id).then(function (txt) { if (txt) showNote(c, id, txt); else A.link(c, id); });
        } else A.link(c, id);
        return;
      }
      if (/^(https?:|mailto:)/i.test(href)) { e.preventDefault(); A.ext(href); return; }
      if (href.charAt(0) === '#' && href.length > 1) { e.preventDefault(); goAnchor(decodeURIComponent(href.slice(1))); return; }
      e.preventDefault();
      return;
    }
    var mk = e.target.closest && e.target.closest('mark.hl');
    if (mk && mk.getAttribute('data-id')) { A.mark(mk.getAttribute('data-id')); return; }
    if (e.target.tagName === 'IMG' && S.imageTap !== false) { A.image(e.target.getAttribute('src') || ''); return; }
    var x = e.clientX, y = e.clientY;
    if (S.paged) {
      if (x < W * 0.3) prev(); else if (x > W * 0.7) next(); else A.tap('menu');
    } else {
      if (y < H * 0.22) prev(); else if (y > H * 0.78) next(); else A.tap('menu');
    }
  });

  // Окно изменилось (разложилось после открытия, повернули экран, скрылись панели) — пересчитываем страницы,
  // оставаясь на том же месте текста. ResizeObserver ловит и случаи, когда события resize не было.
  var lastW = 0, lastH = 0, relayTimer = 0;
  function relayout() {
    if (ch < 0) return;
    var w = vp.clientWidth || window.innerWidth, h = vp.clientHeight || window.innerHeight;
    if (w === lastW && h === lastH) return;
    lastW = w; lastH = h;
    var o = curOffset();
    clearTimeout(relayTimer);
    relayTimer = setTimeout(function () { nodes = null; layout(); goOffset(o); }, 60);
  }
  window.addEventListener('resize', relayout);
  if (window.ResizeObserver) new ResizeObserver(relayout).observe(vp);

  document.addEventListener('DOMContentLoaded', function () { A.ready(); });
  if (document.readyState !== 'loading') setTimeout(function () { A.ready(); }, 0);
})();
