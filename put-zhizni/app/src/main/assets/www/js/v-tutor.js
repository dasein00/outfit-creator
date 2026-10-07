/* Репетитор: английский, испанский, французский.
   Словари (js/content-tutor-*.js) подгружаются по требованию и работают без интернета.
   Запоминание — интервальные повторения (упрощённый SM-2, как в Anki), дневная порция новых слов
   по теме / случайно / по частотности (идея ReWord и Lingualeo), серия дней и опыт (как в Duolingo).
   Произношение — синтез речи Android, перевод фраз — модели ML Kit на устройстве. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays } = A;

  const LANGS = { en: { name: "Английский", short: "EN", flag: "🇬🇧", tts: "en-US", v: "TUTOR_EN" }, es: { name: "Испанский", short: "ES", flag: "🇪🇸", tts: "es-ES", v: "TUTOR_ES" }, fr: { name: "Французский", short: "FR", flag: "🇫🇷", tts: "fr-FR", v: "TUTOR_FR" } };
  const LEVELS = [[1, "A1", "начальный"], [2, "A2", "базовый"], [3, "B1", "средний"], [4, "B2", "выше среднего"], [5, "C1", "продвинутый"]];
  const ORDER = [["topic", "По теме"], ["freq", "Самые частые"], ["random", "Случайные"]];
  const TOPIC_IC = { basics: "hand", people: "users", body: "heart", food: "utensils", home: "house", clothes: "shirt", city: "landmark", travel: "plane", work: "briefcase", money: "wallet", study: "graduation-cap", time: "alarm-clock", nature: "leaf", animals: "cat", feelings: "smile", talk: "message-square-text", hobby: "gamepad-2", art: "palette", tech: "laptop", verbs: "zap", adj: "pen-line", grammar: "book-a" };

  /* ---------- состояние ---------- */
  const st = () => {
    const db = A.db();
    if (!db.tutor) db.tutor = { lang: "en", cfg: {}, srs: {}, known: {}, daily: {}, my: {}, hist: [], xp: {}, rate: 0.9 };
    const t = db.tutor;
    ["cfg", "srs", "known", "daily", "my", "xp"].forEach((k) => { t[k] = t[k] || {}; });
    t.hist = t.hist || [];
    return t;
  };
  const cfg = (l) => { const t = st(); return (t.cfg[l] = Object.assign({ level: 2, topic: "", order: "topic", n: 10 }, t.cfg[l] || {})); };
  const srs = (l) => { const t = st(); return (t.srs[l] = t.srs[l] || {}); };
  const known = (l) => { const t = st(); return (t.known[l] = t.known[l] || {}); };
  const myWords = (l) => { const t = st(); return (t.my[l] = t.my[l] || []); };
  const curLang = () => (LANGS[st().lang] ? st().lang : "en");

  /* ---------- словари ---------- */
  const loading = {};
  const dict = (l) => window[LANGS[l].v] || null;
  const loadLang = (l) => {
    if (dict(l)) return Promise.resolve(dict(l));
    return loading[l] || (loading[l] = new Promise((res, rej) => {
      const s = document.createElement("script");
      s.src = "js/content-tutor-" + l + ".js";
      s.onload = () => res(dict(l));
      s.onerror = () => { delete loading[l]; rej(new Error("Словарь не загрузился")); };
      document.head.appendChild(s);
    }));
  };
  // запись словаря: [слово, перевод, пример, перевод примера, тема, уровень, транскрипция, ранг]
  const W = (x) => ({ w: x[0], ru: x[1], ex: x[2], exr: x[3], t: x[4], lv: x[5], ipa: x[6], rk: x[7] });
  const norm = (s) => String(s || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ё/g, "е").trim();
  const bare = (w) => norm(w.replace(/\s*\([^)]*\)\s*$/, "").replace(/^(el|la|los|las|le|les)\s+/i, "").replace(/^l'/i, "").replace(/^[¿¡]+|[?!]+$/g, ""));
  const keyOf = (w) => w; // ключ слова в повторениях — само слово как в словаре
  const index = {};
  const idx = (l) => {
    if (index[l]) return index[l];
    const D = dict(l), by = new Map(), fw = [], rv = [];
    D.w.forEach((x, i) => {
      by.set(x[0], i);
      fw.push([bare(x[0]), i]);
      String(x[1]).split("·")[0].split(/[,;]/).forEach((p) => { const k = norm(p.replace(/\([^)]*\)/g, "")); if (k && k.length > 1) rv.push([k, i]); });
    });
    const srt = (a) => a.sort((p, q) => (p[0] < q[0] ? -1 : p[0] > q[0] ? 1 : D.w[p[1]][7] - D.w[q[1]][7]));
    return (index[l] = { by, fw: srt(fw), rv: srt(rv) });
  };
  const lower = (arr, k) => { let lo = 0, hi = arr.length; while (lo < hi) { const m = (lo + hi) >> 1; if (arr[m][0] < k) lo = m + 1; else hi = m; } return lo; };
  // подсказки по мере ввода: точное совпадение и самые частые слова с этим началом
  const suggest = (l, q, rev, n = 8) => {
    const k = norm(q);
    if (!k || !dict(l)) return [];
    const I = idx(l), arr = rev ? I.rv : I.fw, D = dict(l), seen = new Set(), out = [];
    for (let i = lower(arr, k); i < arr.length && arr[i][0].startsWith(k) && out.length < 60; i++) if (!seen.has(arr[i][1])) { seen.add(arr[i][1]); out.push(arr[i]); }
    out.sort((a, b) => (b[0] === k) - (a[0] === k) || a[0].length - b[0].length || D.w[a[1]][7] - D.w[b[1]][7]);
    return out.slice(0, n).map((x) => W(D.w[x[1]]));
  };
  const find = (l, w) => { if (!dict(l)) return null; const i = idx(l).by.get(w); return i == null ? null : W(dict(l).w[i]); };
  const lookup = (l, token) => { const s = suggest(l, token, false, 1)[0]; return s && bare(s.w) === norm(token) ? s : null; };

  /* ---------- произношение ---------- */
  const spoken = (w) => String(w).replace(/\s*\([^)]*\)/g, "").replace(/[|·].*$/, "").trim();
  A.say = (text, l) => {
    const t = spoken(text), L = LANGS[l] || LANGS.en, rate = st().rate || 0.9;
    if (!t) return;
    if (A.native && A.native.speak) { try { A.native.speak(t, L.tts, rate); return; } catch (e) {} }
    if (window.speechSynthesis) { const u = new SpeechSynthesisUtterance(t); u.lang = L.tts; u.rate = rate; speechSynthesis.cancel(); speechSynthesis.speak(u); }
    else A.toast("Синтез речи недоступен");
  };
  const sayBtn = (text, l, cls) => '<button class="icon-btn ' + (cls || "") + '" data-a="tSay" data-l="' + l + '" data-t="' + esc(text) + '" aria-label="Произнести">{{i:volume-2}}</button>';

  /* ---------- интервальные повторения (SM-2) ---------- */
  const grade = (l, w, q) => {
    const S = srs(l), c = S[w] || { e: 2.5, i: 0, n: 0, d: today(), lap: 0 };
    if (q < 3) { c.n = 0; c.i = 1; c.lap = (c.lap || 0) + 1; c.d = today(); }
    else {
      c.n += 1;
      c.i = c.n === 1 ? 1 : c.n === 2 ? 3 : Math.round(c.i * c.e * (q === 3 ? 0.8 : q === 5 ? 1.3 : 1));
      c.e = Math.max(1.3, c.e + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));
      c.d = addDays(today(), c.i);
    }
    c.last = today();
    S[w] = c;
    addXp(q >= 3 ? 2 : 1);
    A.save();
  };
  const startLearning = (l, w) => { const S = srs(l); if (!S[w]) { S[w] = { e: 2.5, i: 0, n: 0, d: today(), lap: 0, add: today() }; addXp(5); A.save(); } };
  const due = (l) => Object.keys(srs(l)).filter((w) => srs(l)[w].d <= today()).sort((a, b) => (srs(l)[a].d > srs(l)[b].d ? 1 : -1));
  const learned = (l) => Object.keys(srs(l)).filter((w) => srs(l)[w].i >= 21).length;
  const addXp = (n) => { const t = st(), d = today(); t.xp[d] = (t.xp[d] || 0) + n; };
  const streak = () => { const x = st().xp; let n = 0, d = today(); if (!x[d]) d = addDays(d, -1); while (x[d]) { n++; d = addDays(d, -1); } return n; };

  /* ---------- дневная порция ---------- */
  const pool = (l) => {
    const D = dict(l), c = cfg(l), S = srs(l), K = known(l);
    let ws = D.w.filter((x) => x[5] <= c.level && !S[x[0]] && !K[x[0]]);
    if (c.order === "topic" && c.topic) ws = ws.filter((x) => x[4] === c.topic);
    if (c.order === "topic" && !c.topic) ws = ws.filter((x) => x[4]);
    if (c.order === "freq") ws.sort((a, b) => a[7] - b[7]);
    return ws;
  };
  const daily = (l) => {
    const t = st(), d = today(), c = cfg(l);
    let o = t.daily[l];
    if (!o || o.date !== d || o.sig !== c.order + c.topic + c.level + c.n) {
      const keep = o && o.date === d ? o.done : [];
      let ws = pool(l);
      if (c.order === "random" || (c.order === "topic" && !c.topic)) {
        // случайные — но воспроизводимо в пределах дня
        let seed = d.split("-").join("") * 7 + l.charCodeAt(0);
        const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
        ws = ws.slice(); for (let i = ws.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [ws[i], ws[j]] = [ws[j], ws[i]]; }
      }
      o = t.daily[l] = { date: d, sig: c.order + c.topic + c.level + c.n, ids: keep.concat(ws.slice(0, Math.max(0, c.n - keep.length)).map((x) => x[0])), done: keep };
      A.save();
    }
    return o;
  };

  /* ---------- карточка слова ---------- */
  const lvName = (lv) => (LEVELS.find((x) => x[0] === lv) || LEVELS[2])[1];
  const wordCard = (x, l, opts = {}) => {
    const top = x.t && dict(l) ? dict(l).topics[x.t] : "";
    return '<div class="tw-card">' +
      '<div class="tw-head"><div class="tw-w">' + esc(x.w) + "</div>" + sayBtn(x.w, l, "tw-say") + "</div>" +
      (x.ipa ? '<div class="tw-ipa">[' + esc(x.ipa) + "]</div>" : "") +
      (opts.hide ? '<button class="btn ghost tw-reveal" data-a="tReveal">{{i:eye}} Показать перевод</button><div class="tw-hidden" hidden>' : "<div>") +
      '<div class="tw-ru">' + esc(String(x.ru).split(" · en:")[0]) + "</div>" +
      (String(x.ru).includes(" · en:") ? '<div class="small muted">англ.: ' + esc(String(x.ru).split(" · en:")[1]) + "</div>" : "") +
      (x.ex ? '<div class="tw-ex"><span>' + esc(x.ex) + "</span>" + sayBtn(x.ex, l, "sm") + (x.exr ? "<small>" + esc(x.exr) + "</small>" : "") + "</div>" : "") +
      "</div>" +
      '<div class="tw-tags">' + (x.lv ? '<span class="tag">' + lvName(x.lv) + "</span>" : "") + (top ? '<span class="tag">' + esc(top) + "</span>" : "") + (x.rk && x.rk < 99999 ? '<span class="tag">№' + (x.rk + 1) + " по частоте</span>" : "") + "</div>" +
      "</div>";
  };

  /* ---------- «Слово дня» на экране «Сегодня» ---------- */
  A.CARD_NAMES.word = "Слово дня";
  const wodPick = (l, i) => {
    const D = dict(l), core = D.w.filter((x) => x[4] && x[2]);
    const n = i != null ? i : (A.diffDays("2026-01-01", today()) * 37) % core.length;
    return [W(core[((n % core.length) + core.length) % core.length]), n];
  };
  const wodHtml = (l, i) => {
    const [x, n] = wodPick(l, i);
    return '<div class="tw-wod"><div class="tw-head"><div class="tw-w">' + esc(x.w) + "</div>" + sayBtn(x.w, l, "tw-say") + "</div>" + (x.ipa ? '<div class="tw-ipa">[' + esc(x.ipa) + "]</div>" : "") +
      '<div class="tw-ru">' + esc(x.ru) + '</div><div class="tw-ex"><span>' + esc(x.ex) + "</span>" + sayBtn(x.ex, l, "sm") + "<small>" + esc(x.exr) + "</small></div>" +
      '<div class="btns"><button class="btn sm" data-a="wodNext" data-l="' + l + '" data-i="' + n + '">{{i:shuffle}} Другое</button><button class="btn sm" data-a="wodAdd" data-l="' + l + '" data-w="' + esc(x.w) + '">{{i:list-plus}} Учить</button><a class="btn sm ghost" href="#/tutor">Репетитор</a></div></div>';
  };
  A.todayCards.word = () => {
    const l = curLang(), L = LANGS[l];
    const head = '<h3>{{i:languages}} Слово дня<span class="sp"></span><button class="link" data-a="wodLang" aria-label="Сменить язык">' + L.flag + " " + esc(L.name) + "</button></h3>";
    if (dict(l)) return '<div class="card tw-cardbox">' + head + '<div id="wodBox">' + wodHtml(l) + "</div></div>";
    loadLang(l).then(() => { const b = document.getElementById("wodBox"); if (b) b.innerHTML = wodHtml(l); }).catch(() => {});
    return '<div class="card tw-cardbox">' + head + '<div id="wodBox"><p class="small muted">Загружаю словарь…</p></div></div>';
  };
  const baseHandlers = A.dayHandlers;
  A.dayHandlers = (getD) => Object.assign(baseHandlers(getD), {
    tSay(b) { A.say(b.dataset.t, b.dataset.l); },
    wodNext(b) { const box = document.getElementById("wodBox"); if (box) box.innerHTML = wodHtml(b.dataset.l, +b.dataset.i + 1 + Math.floor(Math.random() * 50)); },
    wodLang() { const ks = Object.keys(LANGS); st().lang = ks[(ks.indexOf(curLang()) + 1) % ks.length]; A.save(); loadLang(curLang()).then(() => A.refresh()); },
    wodAdd(b) { startLearning(b.dataset.l, b.dataset.w); A.toast("Слово добавлено в повторения"); }
  });

  /* ---------- экраны ---------- */
  const SUB = { "": "Репетитор", learn: "Новые слова", quiz: "Тренировка", tr: "Переводчик", dict: "Словарь", my: "Мои слова", set: "Настройки обучения" };
  let S = null; // текущая сессия тренировки

  A.view("tutor", {
    title: (r) => SUB[r.args[0] || ""] || "Репетитор",
    tab: "more",
    render(el, r) {
      const l = curLang();
      if (!dict(l)) { loadLang(l).then(() => A.refresh()).catch((e) => { el.innerHTML = A.empty(e.message); }); return '<div class="card"><p class="muted">Загружаю словарь…</p></div>'; }
      const sub = r.args[0] || "";
      return (PAGES[sub] || PAGES[""])(l, r);
    },
    bind(el, r) {
      const l = curLang(), sub = r.args[0] || "";
      if (!dict(l)) return; // словарь ещё грузится — экран перерисуется сам
      A.bind(el, Object.assign({
        tSay(b) { A.say(b.dataset.t, b.dataset.l || l); },
        tReveal(b) { const h = b.parentNode.querySelector(".tw-hidden"); if (h) h.hidden = false; b.remove(); },
        tLang(b) { st().lang = b.dataset.v; A.save(); loadLang(b.dataset.v).then(() => A.refresh()); },
        tGo(b) { A.go("tutor/" + b.dataset.v); }
      }, (BIND[sub] || (() => ({})))(el, l, r)));
      if (BIND_AFTER[sub]) BIND_AFTER[sub](el, l, r);
    }
  });

  const langSeg = (l) => A.seg(Object.keys(LANGS).map((k) => [k, LANGS[k].flag + " " + LANGS[k].name]), l, "tLang");
  const tile = (v, ic, title, sub, cls) => '<button class="tw-tile ' + (cls || "") + '" data-a="tGo" data-v="' + v + '"><span class="tw-ti">{{i:' + ic + "}}</span><b>" + esc(title) + "</b><small>" + esc(sub) + "</small></button>";

  const PAGES = {
    "": (l) => {
      const D = dict(l), c = cfg(l), dl = daily(l), dn = due(l).length, all = Object.keys(srs(l)).length;
      const left = dl.ids.filter((w) => !dl.done.includes(w)).length;
      const xpT = st().xp[today()] || 0;
      let h = langSeg(l);
      h += '<div class="card"><div class="grid3">' +
        '<div class="stat"><small>{{i:flame}} Серия</small><b>' + streak() + "</b><small>" + A.plural(streak(), "день", "дня", "дней") + "</small></div>" +
        '<div class="stat"><small>{{i:star}} Опыт сегодня</small><b>' + xpT + "</b><small>XP</small></div>" +
        '<div class="stat"><small>{{i:award}} Выучено</small><b>' + learned(l) + "</b><small>из " + all + " в работе</small></div></div>" +
        '<p class="small muted" style="margin-top:8px">Уровень: <b>' + lvName(c.level) + "</b> · " + (c.order === "topic" ? "тема: <b>" + esc(c.topic ? D.topics[c.topic] : "все темы") + "</b>" : esc(ORDER.find((o) => o[0] === c.order)[1].toLowerCase())) + " · " + c.n + ' слов в день · <button class="link" data-a="tGo" data-v="set">изменить</button></p></div>';
      h += '<div class="tw-tiles">' +
        tile("learn", "book-open-check", "Слова дня", left ? "осталось " + left + " из " + dl.ids.length : "порция на сегодня пройдена", left ? "hot" : "") +
        tile("quiz?m=review", "repeat", "Повторение", dn ? dn + " " + A.plural(dn, "слово ждёт", "слова ждут", "слов ждут") : "на сегодня всё", dn ? "hot" : "") +
        tile("quiz?m=choice", "layers", "Выбор перевода", "слово → перевод") +
        tile("quiz?m=reverse", "arrow-right-left", "Обратный перевод", "перевод → слово") +
        tile("quiz?m=spell", "spell-check", "Правописание", "напишите слово") +
        tile("quiz?m=listen", "headphones", "Аудирование", "узнайте на слух") +
        tile("tr", "languages", "Переводчик", "слова, фразы, предложения") +
        tile("dict", "book-a", "Словарь", D.w.length.toLocaleString("ru-RU") + " слов офлайн") +
        tile("my", "bookmark", "Мои слова", myWords(l).length + " сохранено") +
        '<button class="tw-tile" data-a="go2" data-v="' + { en: "english", es: "spanish", fr: "french" }[l] + '"><span class="tw-ti">{{i:graduation-cap}}</span><b>Грамматика с нуля</b><small>' + ({ en: window.LANG.EN, es: window.LANG.ES, fr: window.LANG.FR }[l] || { lessons: [] }).lessons.length + " уроков A1–A2</small></button>" +
        "</div>";
      h += '<div class="card"><h3>{{i:target}} Темы</h3><div class="tw-topics">' + Object.keys(D.topics).map((k) => {
        const tot = D.w.filter((x) => x[4] === k).length; if (!tot) return "";
        const got = D.w.filter((x) => x[4] === k && (srs(l)[x[0]] || known(l)[x[0]])).length;
        return '<button class="tw-topic' + (c.order === "topic" && c.topic === k ? " on" : "") + '" data-a="tTopic" data-v="' + k + '"><span>{{i:' + (TOPIC_IC[k] || "book") + "}}</span><b>" + esc(D.topics[k]) + "</b><small>" + got + "/" + tot + "</small>" + A.bar(got / tot) + "</button>";
      }).join("") + "</div></div>";
      h += '<p class="small muted" style="padding:0 6px">Словарь и тренировки работают без интернета. Произношение — голосом телефона: если слово не звучит, установите голоса в <button class="link" data-a="ttsInst">настройках синтеза речи</button>.</p>';
      return h;
    },

    set: (l) => {
      const D = dict(l), c = cfg(l);
      let h = langSeg(l) + '<div class="card"><h3>{{i:graduation-cap}} Уровень владения</h3><p class="small muted">Уровень объединяет сложность и распространённость: A1 — тысяча самых частых слов, C1 — редкая и книжная лексика.</p>' +
        A.seg(LEVELS.map((x) => [x[0], x[1]]), c.level, "sLevel") + '<p class="small" style="margin-top:6px">' + esc(LEVELS[c.level - 1][2]) + " · в словаре " + D.w.filter((x) => x[5] <= c.level).length + " слов до этого уровня</p></div>";
      h += '<div class="card"><h3>{{i:shuffle}} Какие слова давать</h3>' + A.seg(ORDER, c.order, "sOrder") +
        (c.order === "topic" ? '<div class="chips" style="margin-top:10px"><button class="chip' + (!c.topic ? " on" : "") + '" data-a="sTopic" data-v="">Все темы</button>' + Object.keys(D.topics).filter((k) => D.w.some((x) => x[4] === k)).map((k) => '<button class="chip' + (c.topic === k ? " on" : "") + '" data-a="sTopic" data-v="' + k + '">{{i:' + (TOPIC_IC[k] || "book") + "}} " + esc(D.topics[k]) + "</button>").join("") + "</div>" : "") + "</div>";
      h += '<div class="card"><h3>{{i:list-todo}} Новых слов в день</h3>' + A.seg([[5, "5"], [10, "10"], [15, "15"], [20, "20"], [30, "30"]], c.n, "sN") + "</div>";
      h += '<div class="card"><h3>{{i:volume-2}} Скорость произношения</h3>' + A.seg([[0.6, "медленно"], [0.9, "обычно"], [1.1, "быстро"]], st().rate || 0.9, "sRate") +
        '<div class="btns"><button class="btn sm" data-a="tSay" data-t="' + esc(LANGS[l] === LANGS.en ? "Hello! How are you?" : l === "es" ? "¡Hola! ¿Qué tal?" : "Bonjour ! Comment ça va ?") + '">{{i:play}} Проверить голос</button><button class="btn sm ghost" data-a="ttsInst">Голоса телефона</button></div></div>';
      h += '<div class="card"><h3>{{i:rotate-ccw}} Сброс</h3><p class="small muted">Удалит прогресс повторений по языку «' + esc(LANGS[l].name) + '».</p><button class="btn sm ghost" data-a="sReset">Сбросить прогресс</button></div>';
      return h;
    },

    learn: (l) => {
      const dl = daily(l), left = dl.ids.filter((w) => !dl.done.includes(w));
      if (!dl.ids.length) return A.empty("Для выбранных уровня и темы новых слов не осталось. Поменяйте тему или уровень.", '<button class="btn primary" data-a="tGo" data-v="set">Настройки</button>');
      if (!left.length) return '<div class="card tw-done"><div class="tw-big">{{i:party-popper}}</div><h3>Порция на сегодня пройдена</h3><p class="muted">Слова попали в интервальные повторения: завтра часть из них вернётся.</p><div class="btns" style="justify-content:center"><button class="btn primary" data-a="tGo" data-v="quiz?m=review">Закрепить сейчас</button><button class="btn" data-a="more">Ещё ' + cfg(l).n + " слов</button></div></div>";
      const x = find(l, left[0]) || W([left[0], "", "", "", null, 3, "", 99999]);
      return '<div class="tw-prog">' + A.bar(dl.done.length / dl.ids.length) + "<small>" + (dl.done.length + 1) + " / " + dl.ids.length + "</small></div>" + wordCard(x, l) +
        '<div class="btns tw-actions"><button class="btn" data-a="lKnow" data-w="' + esc(x.w) + '">{{i:check}} Уже знаю</button><button class="btn primary" data-a="lLearn" data-w="' + esc(x.w) + '">{{i:list-plus}} Учить</button></div>' +
        '<p class="small muted" style="text-align:center">«Уже знаю» — слово больше не появится. «Учить» — оно попадёт в повторения.</p>';
    },

    quiz: (l, r) => '<div id="tq"></div>',

    tr: (l, r) => {
      const t = st(), dir = t.trDir || "to";
      const from = dir === "to" ? "ru" : l;
      return langSeg(l) + '<div class="card"><div class="tw-dir"><b>' + (dir === "to" ? "Русский" : esc(LANGS[l].name)) + '</b><button class="icon-btn" data-a="trSwap" aria-label="Поменять направление">{{i:arrow-right-left}}</button><b>' + (dir === "to" ? esc(LANGS[l].name) : "Русский") + "</b></div>" +
        '<div class="tw-inp"><textarea id="trIn" rows="2" placeholder="' + (from === "ru" ? "Введите слово или фразу по-русски" : "Type a word or a phrase") + '" autocomplete="off" autocapitalize="off" spellcheck="false">' + esc(t.trLast || "") + '</textarea><div id="trSug" class="tw-sug" hidden></div></div>' +
        '<div class="btns"><button class="btn primary" data-a="trGo">{{i:languages}} Перевести</button>' + (dir === "from" ? '<button class="btn" data-a="trSayIn">{{i:volume-2}}</button>' : "") + '<button class="btn ghost" data-a="trClear">{{i:x}}</button></div></div>' +
        '<div id="trOut"></div>' +
        (t.hist.filter((x) => x.l === l).length ? '<div class="card"><h3>{{i:clipboard-list}} Недавние<span class="sp"></span><button class="link" data-a="trHistClr">очистить</button></h3>' + t.hist.filter((x) => x.l === l).slice(0, 12).map((x, i) => '<div class="item tap" data-a="trHist" data-q="' + esc(x.q) + '" data-d="' + x.d + '"><div class="tx"><b>' + esc(x.q) + "</b><small>" + esc(x.a) + "</small></div></div>").join("") + "</div>" : "") +
        '<p class="small muted" style="padding:0 6px">Отдельные слова переводятся по встроенному словарю. Фразы и предложения — моделью перевода на телефоне: её нужно один раз скачать (~30 МБ на язык), дальше всё работает без интернета.</p>';
    },

    dict: (l, r) => {
      const D = dict(l), q = r.params.q || "", lv = +(r.params.lv || 0), tp = r.params.t || "";
      let ws;
      if (q) ws = suggest(l, q, /[а-яё]/i.test(q), 60);
      else ws = D.w.filter((x) => (!lv || x[5] === lv) && (!tp || x[4] === tp)).slice(0, 300).map(W);
      return '<div class="card"><div class="tw-inp"><input id="dq" type="search" placeholder="Поиск: слово на любом языке" value="' + esc(q) + '" autocomplete="off"></div>' +
        '<div class="chips" style="margin-top:8px"><button class="chip' + (!lv ? " on" : "") + '" data-a="dLv" data-v="0">Все уровни</button>' + LEVELS.map((x) => '<button class="chip' + (lv === x[0] ? " on" : "") + '" data-a="dLv" data-v="' + x[0] + '">' + x[1] + "</button>").join("") + "</div>" +
        '<div class="chips" style="margin-top:6px"><button class="chip' + (!tp ? " on" : "") + '" data-a="dT" data-v="">Все темы</button>' + Object.keys(D.topics).filter((k) => D.w.some((x) => x[4] === k)).map((k) => '<button class="chip' + (tp === k ? " on" : "") + '" data-a="dT" data-v="' + k + '">' + esc(D.topics[k]) + "</button>").join("") + "</div></div>" +
        '<div class="card"><div class="list">' + (ws.length ? ws.map((x) => row(x, l)).join("") : A.empty("Ничего не нашлось")) + "</div>" + (!q && ws.length === 300 ? '<p class="small muted">Показаны первые 300 — уточните поиск.</p>' : "") + "</div>";
    },

    my: (l) => {
      const M = myWords(l);
      return langSeg(l) + '<div class="card"><h3>{{i:bookmark}} Мои слова<span class="sp"></span><button class="btn sm" data-a="myAdd">{{i:plus}} Добавить</button></h3>' +
        (M.length ? '<div class="list">' + M.map((x, i) => '<div class="item"><div class="tx"><b>' + esc(x.w) + "</b><small>" + esc(x.ru) + "</small></div>" + sayBtn(x.w, l) + '<button class="icon-btn" data-a="myDel" data-i="' + i + '">{{i:x}}</button></div>').join("") + "</div>" : A.empty("Сохраняйте слова из переводчика и словаря — они попадут сюда и в повторения.")) + "</div>";
    }
  };
  const row = (x, l) => {
    const s = srs(l)[x.w], k = known(l)[x.w];
    return '<div class="item"><div class="tx tap" data-a="dOpen" data-w="' + esc(x.w) + '"><b>' + esc(x.w) + (x.ipa ? ' <span class="muted small">[' + esc(x.ipa) + "]</span>" : "") + "</b><small>" + esc(String(x.ru).split(" · en:")[0]) + "</small></div>" + sayBtn(x.w, l) +
      (s || k ? '<span class="tag">' + (k ? "знаю" : s.i >= 21 ? "выучено" : "учу") + "</span>" : '<button class="icon-btn" data-a="dAdd" data-w="' + esc(x.w) + '" aria-label="Учить">{{i:list-plus}}</button>') + "</div>";
  };
  const openWord = (l, x) => A.sheet(x.w, wordCard(x, l), { buttons: [{ label: srs(l)[x.w] ? "Уже в повторениях" : "Учить", cls: "primary", onClick: () => { startLearning(l, x.w); A.toast("Добавлено в повторения"); } }] });

  const BIND = {
    "": (el, l) => ({
      tTopic(b) { const c = cfg(l); c.order = "topic"; c.topic = b.dataset.v; A.save(); A.go("tutor/learn"); },
      go2(b) { A.go(b.dataset.v); },
      ttsInst() { if (A.native && A.native.ttsInstall) A.native.ttsInstall(); else A.toast("Доступно в приложении на телефоне"); }
    }),
    set: (el, l) => ({
      sLevel(b) { cfg(l).level = +b.dataset.v; A.save(); A.refresh(); },
      sOrder(b) { cfg(l).order = b.dataset.v; A.save(); A.refresh(); },
      sTopic(b) { cfg(l).topic = b.dataset.v; A.save(); A.refresh(); },
      sN(b) { cfg(l).n = +b.dataset.v; A.save(); A.refresh(); },
      sRate(b) { st().rate = +b.dataset.v; A.save(); A.refresh(); },
      ttsInst() { if (A.native && A.native.ttsInstall) A.native.ttsInstall(); else A.toast("Доступно в приложении на телефоне"); },
      sReset() { A.confirm("Сбросить прогресс по языку «" + LANGS[l].name + "»?", () => { const t = st(); t.srs[l] = {}; t.known[l] = {}; delete t.daily[l]; A.save(); A.toast("Прогресс сброшен"); A.refresh(); }); }
    }),
    learn: (el, l) => ({
      lKnow(b) { known(l)[b.dataset.w] = today(); markDone(l, b.dataset.w); },
      lLearn(b) { startLearning(l, b.dataset.w); markDone(l, b.dataset.w); },
      more() { const o = daily(l), extra = pool(l).filter((x) => !o.ids.includes(x[0])).slice(0, cfg(l).n).map((x) => x[0]); o.ids = o.ids.concat(extra); A.save(); A.refresh(); }
    }),
    dict: (el, l, r) => ({
      dLv(b) { A.go("tutor/dict?lv=" + b.dataset.v + "&t=" + (r.params.t || ""), true); },
      dT(b) { A.go("tutor/dict?lv=" + (r.params.lv || 0) + "&t=" + b.dataset.v, true); },
      dOpen(b) { const x = find(l, b.dataset.w); if (x) openWord(l, x); },
      dAdd(b) { startLearning(l, b.dataset.w); A.toast("Добавлено в повторения"); b.outerHTML = '<span class="tag">учу</span>'; }
    }),
    my: (el, l) => ({
      myAdd() { A.formSheet("Новое слово", [{ k: "w", label: "Слово или фраза (" + LANGS[l].name.toLowerCase() + ")", full: true }, { k: "ru", label: "Перевод", full: true }], {}, (o) => { if (!o.w) return; addMy(l, o.w, o.ru || ""); A.refresh(); }); },
      myDel(b) { const M = myWords(l), x = M[+b.dataset.i]; M.splice(+b.dataset.i, 1); if (x) delete srs(l)[x.w]; A.save(); A.refresh(); }
    }),
    tr: (el, l) => trHandlers(el, l)
  };
  const BIND_AFTER = { quiz: (el, l, r) => startQuiz(el, l, r.params.m || "review"), tr: (el, l) => trWire(el, l), dict: (el, l, r) => {
    const i = el.querySelector("#dq"); let tm;
    i.addEventListener("input", () => { clearTimeout(tm); tm = setTimeout(() => A.go("tutor/dict?q=" + encodeURIComponent(i.value.trim()), true), 350); });
    if (r.params.q) { i.focus(); i.setSelectionRange(i.value.length, i.value.length); }
  } };
  const markDone = (l, w) => { const o = daily(l); if (!o.done.includes(w)) o.done.push(w); A.save(); A.refresh(); };
  const addMy = (l, w, ru) => {
    const M = myWords(l);
    if (!M.some((x) => x.w === w)) M.unshift({ w, ru, date: today() });
    startLearning(l, w);
    A.save(); A.toast("Сохранено в «Мои слова»");
  };
  // перевод слова, которого нет в общем словаре (из «Моих слов»)
  const anyWord = (l, w) => find(l, w) || (() => { const m = myWords(l).find((x) => x.w === w); return m ? W([m.w, m.ru, "", "", null, 3, "", 99999]) : null; })();

  /* ---------- тренировки ---------- */
  const QN = { review: "Повторение", choice: "Выбор перевода", reverse: "Обратный перевод", spell: "Правописание", listen: "Аудирование" };
  const shuffle = (a) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const ruShort = (x) => String(x.ru).split(" · en:")[0].split(/[;]/)[0].replace(/́/g, "");
  const startQuiz = (el, l, m) => {
    const box = el.querySelector("#tq"), D = dict(l);
    let words;
    if (m === "review") words = due(l).slice(0, 30);
    else {
      const inWork = Object.keys(srs(l));
      words = shuffle(inWork.length >= 4 ? inWork : inWork.concat(D.w.filter((x) => x[4] && x[5] <= cfg(l).level).slice(0, 40).map((x) => x[0]))).slice(0, 15);
    }
    words = words.map((w) => anyWord(l, w)).filter(Boolean);
    if (!words.length) { box.innerHTML = A.empty(m === "review" ? "На сегодня повторений нет. Возьмите новые слова дня." : "Сначала добавьте слова в изучение.", '<button class="btn primary" data-a="tGo" data-v="learn">Слова дня</button>'); return; }
    S = { l, m, words, i: 0, ok: 0, bad: [] };
    drawQ(box);
  };
  const options = (x, l, rev) => {
    const D = dict(l);
    const same = D.w.filter((y) => y[0] !== x.w && (x.t ? y[4] === x.t : Math.abs(y[5] - (x.lv || 3)) <= 1)).slice(0, 400);
    const pick = shuffle(same).slice(0, 3).map(W);
    return shuffle([x].concat(pick)).map((y) => [y.w, rev ? y.w : ruShort(y)]);
  };
  const drawQ = (box) => {
    if (!S) return;
    const { l, m } = S;
    if (S.i >= S.words.length) {
      const pct = Math.round((S.ok / S.words.length) * 100);
      box.innerHTML = '<div class="card tw-done"><div class="tw-big">{{i:' + (pct >= 80 ? "trophy" : "sprout") + '}}</div><h3>' + QN[m] + ": " + S.ok + " из " + S.words.length + "</h3><p class=\"muted\">" + (pct >= 80 ? "Отлично! " : "Хорошее начало. ") + "Сегодня: " + (st().xp[today()] || 0) + " XP, серия " + streak() + " " + A.plural(streak(), "день", "дня", "дней") + ".</p>" +
        (S.bad.length ? '<div class="list" style="text-align:left">' + S.bad.map((x) => '<div class="item"><div class="tx"><b>' + esc(x.w) + "</b><small>" + esc(ruShort(x)) + "</small></div>" + sayBtn(x.w, l) + "</div>").join("") + "</div>" : "") +
        '<div class="btns" style="justify-content:center"><button class="btn primary" data-a="tGo" data-v="">Готово</button><button class="btn" data-a="qAgain">Ещё раз</button></div></div>';
      return;
    }
    const x = S.words[S.i], mm = m === "review" ? ["card", "choice", "reverse", "spell"][(S.i + (srs(l)[x.w] ? srs(l)[x.w].n : 0)) % 4] : m;
    S.cur = { x, mm, answered: false };
    let h = '<div class="tw-prog">' + A.bar(S.i / S.words.length) + "<small>" + (S.i + 1) + " / " + S.words.length + "</small></div>";
    if (mm === "card") {
      h += wordCard(x, l, { hide: true }) + '<div class="btns tw-grade"><button class="btn" data-a="qGrade" data-q="1">Не помню</button><button class="btn" data-a="qGrade" data-q="3">Трудно</button><button class="btn primary" data-a="qGrade" data-q="4">Помню</button><button class="btn" data-a="qGrade" data-q="5">Легко</button></div>';
    } else if (mm === "choice" || mm === "listen") {
      h += '<div class="tw-card tw-q">' + (mm === "listen" ? '<button class="tw-play" data-a="qPlay">{{i:headphones}}</button><small class="muted">Нажмите, чтобы прослушать ещё раз</small>' : '<div class="tw-head"><div class="tw-w">' + esc(x.w) + "</div>" + sayBtn(x.w, l, "tw-say") + "</div>" + (x.ipa ? '<div class="tw-ipa">[' + esc(x.ipa) + "]</div>" : "")) + "</div>" +
        '<div class="tw-opts">' + options(x, l, mm === "listen").map(([w, t]) => '<button class="btn tw-opt" data-a="qPick" data-w="' + esc(w) + '">' + esc(t) + "</button>").join("") + "</div>";
    } else if (mm === "reverse") {
      h += '<div class="tw-card tw-q"><div class="tw-ru">' + esc(ruShort(x)) + "</div>" + (x.exr ? '<small class="muted">' + esc(x.exr) + "</small>" : "") + "</div>" +
        '<div class="tw-opts">' + options(x, l, true).map(([w, t]) => '<button class="btn tw-opt" data-a="qPick" data-w="' + esc(w) + '">' + esc(t) + "</button>").join("") + "</div>";
    } else if (mm === "spell") {
      const ans = spoken(x.w);
      h += '<div class="tw-card tw-q"><div class="tw-ru">' + esc(ruShort(x)) + '</div><small class="muted">' + ans.length + " " + A.plural(ans.length, "знак", "знака", "знаков") + " · начинается на «" + esc(ans[0]) + "»</small>" + sayBtn(x.w, l) + "</div>" +
        '<div class="tw-inp"><input id="qSp" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Напишите слово"></div>' + (l !== "en" ? '<div class="chips tw-acc">' + (l === "es" ? "á é í ó ú ñ ü ¿ ¡" : "é è ê à â ç ô î û ë ï œ").split(" ").map((c) => '<button class="chip" data-a="qAcc" data-v="' + c + '">' + c + "</button>").join("") + "</div>" : "") +
        '<div class="btns"><button class="btn primary" data-a="qSpell">Проверить</button><button class="btn ghost" data-a="qSkip">Не знаю</button></div>';
    }
    h += '<div id="qFb"></div>';
    box.innerHTML = h;
    if (mm === "listen") setTimeout(() => A.say(x.w, l), 250);
    const sp = box.querySelector("#qSp");
    if (sp) { sp.focus(); sp.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); box.querySelector('[data-a="qSpell"]').click(); } }); }
  };
  const feedback = (box, ok, x) => {
    S.cur.answered = true;
    if (ok) S.ok++; else S.bad.push(x);
    grade(S.l, x.w, ok ? 4 : 1);
    A.vibe && A.vibe(ok ? 10 : 40);
    box.querySelector("#qFb").innerHTML = '<div class="tw-fb ' + (ok ? "ok" : "bad") + '"><b>' + (ok ? "{{i:check}} Верно" : "{{i:x}} Правильно: " + esc(x.w)) + "</b><small>" + esc(x.w) + " — " + esc(ruShort(x)) + "</small>" + (x.ex ? "<small><i>" + esc(x.ex) + "</i></small>" : "") + '<button class="btn primary" data-a="qNext">Дальше</button></div>';
    if (S.cur.mm !== "listen") A.say(x.w, S.l);
    const n = box.querySelector('[data-a="qNext"]'); if (n) n.scrollIntoView({ block: "nearest" });
  };
  // обработчики тренировки
  BIND.quiz = (el, l) => ({
    qGrade(b) { const x = S.cur.x; const q = +b.dataset.q; if (q >= 3) S.ok++; else S.bad.push(x); grade(l, x.w, q); S.i++; drawQ(el.querySelector("#tq")); },
    qPick(b) {
      if (!S || S.cur.answered) return;
      const ok = b.dataset.w === S.cur.x.w;
      el.querySelectorAll(".tw-opt").forEach((o) => { o.disabled = true; if (o.dataset.w === S.cur.x.w) o.classList.add("ok"); else if (o === b) o.classList.add("bad"); });
      feedback(el.querySelector("#tq"), ok, S.cur.x);
    },
    qSpell() {
      if (!S || S.cur.answered) return;
      const v = el.querySelector("#qSp").value, x = S.cur.x;
      const want = spoken(x.w), soft = (s) => norm(s).replace(/[^a-zа-я' ]/g, "");
      const ok = v.trim().toLowerCase() === want.toLowerCase() || bare(v) === bare(want);
      const near = !ok && soft(v) === soft(want);
      feedback(el.querySelector("#tq"), ok || near, x);
      if (near) el.querySelector(".tw-fb b").textContent = "Почти верно — обратите внимание на знаки: " + want;
    },
    qSkip() { if (!S || S.cur.answered) return; feedback(el.querySelector("#tq"), false, S.cur.x); },
    qAcc(b) { const i = el.querySelector("#qSp"); if (!i) return; const p = i.selectionStart || i.value.length; i.value = i.value.slice(0, p) + b.dataset.v + i.value.slice(p); i.focus(); i.setSelectionRange(p + 1, p + 1); },
    qPlay() { A.say(S.cur.x.w, l); },
    qNext() { S.i++; drawQ(el.querySelector("#tq")); },
    qAgain() { startQuiz(el, l, S.m); }
  });

  /* ---------- переводчик ---------- */
  let trTimer = null;
  const tokens = (s) => s.match(/[\p{L}'’-]+/gu) || [];
  const gloss = (l, text, dir) => tokens(text).map((tk) => {
    if (dir === "from") { const x = lookup(l, tk); return [tk, x ? ruShort(x) : "?"]; }
    const s = suggest(l, tk, true, 1)[0]; return [tk, s && norm(String(s.ru).split(/[,;·]/)[0]).startsWith(norm(tk).slice(0, Math.max(3, tk.length - 2))) ? spoken(s.w) : "?"];
  });
  const trHandlers = (el, l) => ({
    trSwap() { const t = st(); t.trDir = (t.trDir || "to") === "to" ? "from" : "to"; t.trLast = ""; A.save(); A.refresh(); },
    trClear() { st().trLast = ""; A.save(); el.querySelector("#trIn").value = ""; el.querySelector("#trOut").innerHTML = ""; el.querySelector("#trSug").hidden = true; el.querySelector("#trIn").focus(); },
    trGo() { translate(el, l, el.querySelector("#trIn").value.trim()); },
    trSayIn() { A.say(el.querySelector("#trIn").value, l); },
    trPick(b) { const x = find(l, b.dataset.w) || anyWord(l, b.dataset.w); el.querySelector("#trSug").hidden = true; if (!x) return; el.querySelector("#trIn").value = (st().trDir || "to") === "to" ? b.dataset.q : spoken(x.w); showWord(el, l, x); },
    trHist(b) { const t = st(); if ((t.trDir || "to") !== b.dataset.d) { t.trDir = b.dataset.d; t.trLast = b.dataset.q; A.save(); A.refresh(); setTimeout(() => translate(document.querySelector("#main > div"), l, b.dataset.q), 0); return; } el.querySelector("#trIn").value = b.dataset.q; translate(el, l, b.dataset.q); },
    trHistClr() { const t = st(); t.hist = t.hist.filter((x) => x.l !== l); A.save(); A.refresh(); },
    trSave(b) { addMy(l, b.dataset.w, b.dataset.r); },
    trLearn(b) { startLearning(l, b.dataset.w); A.toast("Добавлено в повторения"); },
    trDl(b) { b.disabled = true; b.textContent = "Скачиваю модель…"; translate(el, l, el.querySelector("#trIn").value.trim(), true); },
    trOpenW(b) { const x = find(l, b.dataset.w); if (x) openWord(l, x); }
  });
  const trWire = (el, l) => {
    const inp = el.querySelector("#trIn"), sug = el.querySelector("#trSug");
    const dir = st().trDir || "to";
    const upd = () => {
      const v = inp.value.trim(), n = v.split(/\s+/).length;
      st().trLast = inp.value;
      if (!v || n > 3) { sug.hidden = true; return; }
      // сначала ищем фразу целиком (у словаря есть устойчивые выражения), потом — последнее слово
      let list = n > 1 ? suggest(l, v, dir === "to", 8) : [];
      if (!list.length) list = suggest(l, v.split(/\s+/).pop(), dir === "to", 8);
      if (!list.length) { sug.hidden = true; return; }
      sug.innerHTML = list.map((x) => '<button class="tw-sg" data-a="trPick" data-w="' + esc(x.w) + '" data-q="' + esc(dir === "to" ? String(x.ru).split(/[,;·]/)[0].replace(/́/g, "").trim() : x.w) + '"><b>' + esc(x.w) + "</b><small>" + esc(ruShort(x)) + "</small></button>").join("");
      sug.hidden = false;
    };
    inp.addEventListener("input", () => { clearTimeout(trTimer); trTimer = setTimeout(upd, 120); });
    inp.addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); sug.hidden = true; translate(el, l, inp.value.trim()); } });
    inp.addEventListener("blur", () => { A.save(); setTimeout(() => { sug.hidden = true; }, 200); });
    inp.addEventListener("focus", () => { if (inp.value.trim()) upd(); });
  };
  const showWord = (el, l, x) => {
    el.querySelector("#trOut").innerHTML = wordCard(x, l) + '<div class="btns"><button class="btn primary" data-a="trSave" data-w="' + esc(x.w) + '" data-r="' + esc(ruShort(x)) + '">{{i:bookmark}} В мои слова</button></div>';
    pushHist(l, (st().trDir || "to") === "to" ? ruShort(x) : spoken(x.w), (st().trDir || "to") === "to" ? spoken(x.w) : ruShort(x));
  };
  const pushHist = (l, q, a) => { const t = st(), d = t.trDir || "to"; t.hist = [{ l, q, a, d }].concat(t.hist.filter((h) => !(h.l === l && h.q === q && h.d === d))).slice(0, 60); A.save(); };
  const translate = async (el, l, text, allowDl) => {
    const out = el.querySelector("#trOut"), dir = st().trDir || "to";
    if (!text) return;
    el.querySelector("#trSug").hidden = true;
    const words = tokens(text);
    // одно слово — словарная статья
    if (words.length === 1 && !allowDl) {
      const x = dir === "from" ? lookup(l, text) : (suggest(l, text, true, 5).find((s) => String(s.ru).split(/[,;·]/).some((p) => norm(p.replace(/\([^)]*\)/g, "")) === norm(text))) || null);
      if (x) { showWord(el, l, x); return; }
    }
    const from = dir === "to" ? "ru" : l, to = dir === "to" ? l : "ru";
    const gl = gloss(l, text, dir);
    const glossHtml = '<div class="tw-gloss">' + gl.map(([a, b]) => '<span><b>' + esc(a) + "</b><small>" + esc(b) + "</small></span>").join("") + "</div>";
    if (A.native && A.native.mtTranslate) {
      out.innerHTML = '<div class="card"><p class="muted">Перевожу…</p></div>';
      const r = await A.nativeCall("mt", () => A.native.mtTranslate(text, from, to, !!allowDl));
      let o = {}; try { o = JSON.parse(r); } catch (e) {}
      if (o.ok) {
        const res = o.text;
        out.innerHTML = '<div class="card tw-res"><div class="tw-head"><div class="tw-ru">' + esc(res) + "</div>" + (to !== "ru" ? sayBtn(res, l) : sayBtn(text, l)) + '</div><p class="small muted">Перевод на устройстве. По словам:</p>' + glossHtml +
          '<div class="btns"><button class="btn sm" data-a="trSave" data-w="' + esc(to === "ru" ? text : res) + '" data-r="' + esc(to === "ru" ? res : text) + '">{{i:bookmark}} В мои слова</button></div></div>';
        pushHist(l, text, res);
        return;
      }
      if (o.err === "no-model" || o.err === "download") {
        out.innerHTML = '<div class="card"><h3>{{i:download}} Нужна модель перевода</h3><p class="small">Для перевода фраз ' + (o.err === "download" ? "не удалось скачать модель — проверьте интернет. " : "один раз скачайте модель «" + esc(LANGS[l].name) + " ⇄ русский» (около 30 МБ). ") + "После этого переводчик работает без интернета.</p>" +
          '<button class="btn primary" data-a="trDl">Скачать модель</button><p class="small muted" style="margin-top:10px">Пока — перевод по словам из словаря:</p>' + glossHtml + "</div>";
        return;
      }
    }
    out.innerHTML = '<div class="card tw-res"><p class="small muted">Перевод по словам из встроенного словаря' + (A.native ? "" : " (полный перевод фраз доступен в приложении на телефоне)") + ":</p>" + glossHtml + "</div>";
  };
})();
