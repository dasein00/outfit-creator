/* Сунь-цзы: цитата дня на главной (без повторов, пока не пройдены все), иероглиф главы, мысль комментатора,
   цитатник по главам. Цитаты — свой перевод с древнекитайского (см. content-suntzu.js). */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today } = A;
  const Q = () => window.SUNTZU || [], CH = () => window.SUNTZU_CH || [], GL = () => window.SUNTZU_GLYPH || [], CM = () => window.SUNTZU_COMM || [];

  // постоянный перемешанный порядок; позиция растёт на 1 каждый день и при «ещё»
  let order = null;
  const ord = () => {
    if (order && order.length === Q().length) return order;
    const n = Q().length, o = Array.from({ length: n }, (_, i) => i); let seed = 4960;
    const rnd = () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);
    for (let i = n - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [o[i], o[j]] = [o[j], o[i]]; }
    return (order = o);
  };
  const st = () => { try { return JSON.parse(localStorage.getItem("pz-suntzu") || "{}"); } catch (e) { return {}; } };
  const save = (s) => { try { localStorage.setItem("pz-suntzu", JSON.stringify(s)); } catch (e) {} };
  const posToday = () => { const n = Q().length, d = today(); let s = st(); if (s.d !== d) { s = { d, pos: ((s.pos == null ? -1 : s.pos) + 1) % n }; save(s); } return s.pos % n; };
  const next = () => { const n = Q().length, s = st(); s.d = today(); s.pos = ((s.pos || 0) + 1) % n; save(s); return s.pos; };
  const at = (p) => Q()[ord()[p % Q().length]];

  const quoteHtml = (q, p) => {
    const g = GL()[q[0]] || [], cm = CM()[p % CM().length];
    return '<div class="sz-row"><div class="sz-glyph" title="' + esc(g[1] || "") + '">' + esc(g[0] || "兵") + '</div><div class="grow"><div class="small muted">Глава ' + q[0] + " · " + esc(CH()[q[0]] || "") + '</div><p class="sz-q">«' + esc(q[1]) + '»</p></div></div>' +
      '<p class="small" style="margin:6px 0 0"><b>В жизни:</b> ' + esc(q[2]) + "</p>" +
      (p % 3 === 0 && cm ? '<p class="small muted" style="margin:6px 0 0"><b>' + esc(cm[0]) + ":</b> " + esc(cm[1]) + "</p>" : "");
  };

  A.CARD_NAMES.suntzu = "Сунь-цзы: мысль дня";
  A.todayCards.suntzu = () => {
    const p = posToday();
    return '<div class="card sz-card"><h3>{{i:scroll-text}} Сунь-цзы · Искусство войны<span class="sp"></span><button class="link" data-a="szNext">ещё →</button></h3><div id="szQ">' + quoteHtml(at(p), p) + '</div><a class="link" href="#/suntzu" style="display:inline-block;margin-top:6px">весь цитатник →</a></div>';
  };
  // обработчик «ещё» на главной
  const _dh = A.dayHandlers;
  A.dayHandlers = (getD) => Object.assign(_dh ? _dh(getD) : {}, { szNext() { const p = next(), el = document.getElementById("szQ"); if (el) el.innerHTML = quoteHtml(at(p), p); } });

  A.view("suntzu", {
    title: "Сунь-цзы · Искусство войны", tab: "more",
    render(el, r) {
      const c = +(r.params.c || 0);
      let h = '<div class="card sz-card"><p class="small">Трактат «Искусство войны» (孫子兵法) приписывают полководцу Сунь У (V век до н. э.). Это 13 глав о том, как побеждать, по возможности не вступая в бой: через расчёт, знание себя и другого, гибкость и силу положения. Цитаты — перевод с древнекитайского, сделанный для приложения; к каждой — как применить мысль в жизни. Книгу целиком с комментариями можно читать в разделе «Книги».</p><a class="btn sm" href="#/reader">{{i:book-open}} Открыть «Книги»</a></div>';
      h += '<div class="chips" style="margin:6px 0 10px"><button class="chip' + (!c ? " on" : "") + '" data-a="szCh" data-v="0">Все · ' + Q().length + "</button>" + CH().slice(1).map((n, i) => '<button class="chip' + (c === i + 1 ? " on" : "") + '" data-a="szCh" data-v="' + (i + 1) + '">' + (i + 1) + ". " + esc(n) + "</button>").join("") + "</div>";
      const chs = c ? [c] : CH().map((_, i) => i).slice(1);
      chs.forEach((k) => {
        const L = Q().filter((q) => q[0] === k); if (!L.length) return;
        const g = GL()[k] || [];
        h += '<div class="card"><div class="sz-row"><div class="sz-glyph">' + esc(g[0] || "") + '</div><div class="grow"><h3 style="margin:0">Глава ' + k + ". " + esc(CH()[k]) + '</h3><p class="small muted" style="margin:2px 0 0">' + esc(g[0] || "") + " (" + esc(g[1] || "") + ") — " + esc(g[2] || "") + "</p></div></div>" +
          L.map((q) => '<div class="sz-item"><p class="sz-q">«' + esc(q[1]) + '»</p><p class="small muted">' + esc(q[2]) + "</p></div>").join("") + "</div>";
      });
      h += '<div class="card"><h3>Мысли комментаторов</h3>' + CM().map((x) => '<p class="small"><b>' + esc(x[0]) + ".</b> " + esc(x[1]) + "</p>").join("") + '<p class="small muted">Классические комментарии (Цао Цао, Ду Му, Чжан Юй и др.) пересказаны своими словами.</p></div>';
      return h;
    },
    bind(el) { A.bind(el, { szCh(b) { A.go("suntzu" + (+b.dataset.v ? "?c=" + b.dataset.v : ""), true); } }); }
  });
})();
