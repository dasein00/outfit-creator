/* Расклады: Таро Уэйта (78 карт, иллюстрации 1909 г. — общественное достояние) и оракул Ленорман (36 карт).
   Карты можно вытянуть случайно, выбрать вручную или загрузить фото своего расклада — ИИ распознаёт карты.
   Толкование: офлайн-значения по позициям и ИИ-разбор по выбранной системе. Журнал раскладов — коллекция spreads. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today } = A;

  const MAJ_EN = ["The Fool", "The Magician", "The High Priestess", "The Empress", "The Emperor", "The Hierophant", "The Lovers", "The Chariot", "Strength", "The Hermit", "Wheel of Fortune", "Justice", "The Hanged Man", "Death", "Temperance", "The Devil", "The Tower", "The Star", "The Moon", "The Sun", "Judgement", "The World"];
  const RANK_EN = ["Ace", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Page", "Knight", "Queen", "King"];
  const SUIT_EN = { W: "Wands", C: "Cups", S: "Swords", P: "Pentacles" };
  const tarot = () => (window.LEARN && window.LEARN.TAROT) || [];
  const leno = () => window.LENORMAND.cards;
  const enName = (c) => (c.id[0] === "M" ? MAJ_EN[c.num] : RANK_EN[c.num - 1] + " of " + SUIT_EN[c.id[0]]);
  /* Ленорман по Солье: те же 36 карт, названия и толкования из книги, перевёрнутые карты, карта бланка */
  const SOLE = () => window.LENORMAND_SOLE;
  let soleCache = null;
  const sole = () => soleCache || (soleCache = leno().map((c) => Object.assign({}, c, { name: SOLE().names[c.n] || c.name, s: SOLE().cards[c.n] })));
  const deckCards = (deck) => (deck === "tarot" ? tarot() : deck === "lsole" ? sole() : leno());
  const card = (deck, id) => deckCards(deck).find((c) => c.id === id);
  const DECK_NAME = { tarot: "Таро Уэйта", lenormand: "Ленорман", lsole: "Ленорман · Солье" };
  const BLANKS = [["L29", "Дама (женщина)"], ["L28", "Джентльмен (мужчина)"]];

  const SPREADS = {
    tarot: [
      ["one", "Одна карта", ["Карта-ответ / совет"]],
      ["ppf", "Прошлое — настоящее — будущее", ["Прошлое", "Настоящее", "Будущее"]],
      ["sos", "Ситуация — препятствие — совет", ["Ситуация", "Что мешает", "Совет"]],
      ["yesno", "Да или нет", ["За", "Против", "Итог"]],
      ["love", "Отношения", ["Я", "Партнёр", "Что связывает", "Что мешает", "Куда идут отношения", "Совет"]],
      ["choice", "Выбор из двух путей", ["Путь А", "Итог пути А", "Путь Б", "Итог пути Б", "Что важно учесть"]],
      ["celtic", "Кельтский крест", ["Суть ситуации", "Что пересекает (препятствие)", "Основа, подсознательное", "Уходящее прошлое", "Сознательная цель", "Ближайшее будущее", "Вы сами", "Окружение", "Надежды и страхи", "Итог"]]
    ],
    lenormand: [
      ["l3", "Линия из 3 карт", ["Что привело", "Суть", "Развитие"]],
      ["l5", "Линия из 5 карт", ["Прошлое", "Влияние", "Суть", "Ближайшее будущее", "Итог"]],
      ["l9", "Квадрат 3×3", ["Фон", "Мысли и надежды", "Скрытое влияние", "Прошлое", "Суть", "Будущее", "Глубинная причина", "Основа", "Итог"]],
      ["gt", "Большой расклад (36 карт)", null]
    ]
  };
  const spreadList = (deck) => (deck === "lsole" ? SOLE().spreads.map((x) => [x.key, x.name, x.pos]) : SPREADS[deck]);
  const spreadOf = (deck, key, n) => {
    if (deck === "lsole") {
      const x = SOLE().spreads.find((y) => y.key === key);
      if (x) return { key, name: x.name, pos: x.pos, rows: x.rows, blank: x.blank, note: x.note };
    }
    const s = (SPREADS[deck] || []).find((x) => x[0] === key);
    if (s && s[2]) return { key, name: s[1], pos: s[2] };
    if (key === "gt") return { key, name: "Большой расклад", pos: window.LENORMAND.houses.map((h, i) => "Дом " + (i + 1) + " · " + h) };
    return { key: "free", name: "Свой расклад", pos: Array.from({ length: n || 3 }, (_, i) => "Карта " + (i + 1)) };
  };
  const SYSTEMS = {
    tarot: [["rws", "Классическое Таро Уэйта (Райдер — Уэйт — Смит)"], ["psy", "Психологическое прочтение: архетипы и вопросы к себе"], ["thoth", "Подход Тота: стихии, астрология, каббала"], ["mine", "Мой метод (конспект в настройках расклада)"]],
    lenormand: [["lclassic", "Ленорман: классика — пары, линии, центр расклада"], ["lgt", "Ленорман: дома, диагонали, карта спрашивающего"], ["lpsy", "Ленорман: мягкое психологическое прочтение"], ["mine", "Мой метод (конспект в настройках расклада)"]],
    lsole: [["lsole", "Ленорман по Солье: карта бланка, перевёрнутые карты, сочетания"], ["lpsy", "Ленорман: мягкое психологическое прочтение"], ["mine", "Мой метод (конспект в настройках расклада)"]]
  };
  const SYS_PROMPT = {
    rws: "Толкуй по классической системе Таро Уэйта (Райдер — Уэйт — Смит): опирайся на символику изображений Памелы Колман Смит, нумерологию арканов и стихии мастей (жезлы — огонь, кубки — вода, мечи — воздух, пентакли — земля). Перевёрнутые карты — ослабленное, заблокированное или внутреннее проявление.",
    psy: "Толкуй психологически: карты как архетипы и зеркало состояния. Не предсказывай события, а показывай, что происходит с человеком, какие ресурсы и слепые зоны видны, и задай 2–3 вопроса для размышления.",
    thoth: "Толкуй в духе подхода Тота: соответствия стихиям, планетам, знакам зодиака и путям Древа Жизни; соотношение стихий в раскладе (преобладание и нехватка) — важная часть вывода. Названия карт оставляй по Уэйту.",
    lclassic: "Толкуй по классической технике Ленорман: карты читаются как короткие фразы, парами (первая — тема, вторая — её характеристика), без перевёрнутых значений. Центральная карта — суть. Свяжи карты в единую историю, а не перечисляй значения по одной.",
    lgt: "Толкуй как Большой расклад Ленорман: найди карту спрашивающего (Мужчина №28 или Женщина №29), опиши ближайшее окружение, карты над и под, слева (прошлое) и справа (будущее), ключевые карты (Сердце, Кольцо, Рыбы, Якорь, Дом, Дерево), их дома (позиция 1–36 = тема карты с тем же номером) и диагонали.",
    lsole: "Толкуй по системе А. Солье («Тайна Таро Ленорман»). Правила: перевёрнутая карта ослабляет, задерживает или обращает смысл прямой. Карта бланка (Дама или Джентльмен) — сам спрашивающий: что справа от неё — будущее, слева — прошлое, выше — планы и мысли, ниже — то, что подавлено или под ногами. Сильнее всего действуют карты, соседние с картой бланка, диагонали говорят о скрытых желаниях. Пары соседних карт читай как сочетания — готовые смыслы сочетаний приведены ниже, опирайся на них. Каждая позиция расклада имеет свою тему — толкуй карту именно в ней.",
    lpsy: "Толкуй Ленорман мягко и психологически: что карты говорят о состоянии, отношениях и возможных шагах; избегай фатализма и запугивания."
  };

  /* ---------- состояние черновика ---------- */
  let D = null; // { deck, key, q, topic, cards:[{id, rev}], photo }
  const newDraft = (deck) => ({ deck, key: deck === "tarot" ? "ppf" : deck === "lsole" ? "s4" : "l3", q: "", topic: "general", cards: [], rev: true, blank: "L29" });
  const canRev = (deck) => deck === "tarot" || deck === "lsole";
  const shuffle = (a) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };

  /* ---------- отрисовка карт ---------- */
  const lenoFace = (c) => '<div class="lc"><div class="lc-n">' + c.n + '</div><div class="lc-ic">{{i:' + c.ic + '}}</div><div class="lc-name">' + esc(c.name) + '</div><div class="lc-pc">' + esc(c.pc) + "</div></div>";
  const face = (deck, x, small) => {
    const c = card(deck, x.id); if (!c) return '<div class="sp-card empty">?</div>';
    if (deck === "tarot") return '<div class="sp-card' + (x.rev ? " rev" : "") + (small ? " sm" : "") + '"><img src="' + A.tarotImg(c.id) + '" alt="' + esc(c.name) + '" loading="lazy"></div>';
    return '<div class="sp-card leno' + (x.rev ? " rev" : "") + (small ? " sm" : "") + '">' + lenoFace(c) + "</div>";
  };
  const soleLayout = (d, sp) => {
    const slot = (x, i) => '<div class="sp-slot" data-a="spOpen" data-i="' + i + '">' + face(d.deck, x, d.cards.length > 9) + "<small>" + (i + 1) + ". " + esc((sp.pos[i] || "").split(" — ")[0].split(",")[0]) + "</small></div>";
    let rows = sp.rows && sp.rows.reduce((a, b) => a + b, 0) === d.cards.length ? sp.rows : null;
    let h = sp.blank && d.blank ? '<div class="sp-blank"><div class="sp-slot" data-a="spOpen" data-i="b">' + face(d.deck, { id: d.blank }, true) + "<small>Карта бланка</small></div></div>" : "";
    if (!rows) return h + '<div class="sp-lay line">' + d.cards.map(slot).join("") + "</div>";
    let k = 0;
    return h + '<div class="sp-rows">' + rows.map((n) => '<div class="sp-lay line">' + d.cards.slice(k, (k += n)).map((x, j) => slot(x, k - n + j)).join("") + "</div>").join("") + "</div>";
  };
  const layout = (d) => {
    const sp = spreadOf(d.deck, d.key, d.cards.length);
    if (d.deck === "lsole") return soleLayout(d, sp);
    const cls = d.key === "celtic" ? "celtic" : d.key === "l9" ? "sq" : d.key === "gt" ? "gt" : "line";
    return '<div class="sp-lay ' + cls + '">' + d.cards.map((x, i) => '<div class="sp-slot s' + (i + 1) + '" data-a="spOpen" data-i="' + i + '">' + face(d.deck, x, d.key === "gt" || d.key === "celtic") + '<small>' + (d.key === "gt" ? i + 1 : esc(sp.pos[i] || "")) + "</small></div>").join("") + "</div>";
  };
  const meaning = (deck, x, topic) => {
    const c = card(deck, x.id); if (!c) return "";
    if (deck === "tarot") return (x.rev ? "<b>Перевёрнутая:</b> " + esc(c.rev) : esc(c.up)) + ".";
    if (deck === "lsole") return (x.rev ? "<b>Перевёрнутая.</b> " + esc(c.s.r) : esc(c.s.u)) + (topic === "love" ? " <b>В любви:</b> " + esc(c.s.l) : topic === "work" ? " <b>В делах:</b> " + esc(c.s.w) : topic === "health" ? " <b>Здоровье:</b> " + esc(c.s.h) : "");
    return "<b>" + esc(c.key) + ".</b> " + esc(topic === "love" ? c.love : topic === "work" ? c.work : c.m);
  };
  const cardName = (deck, x) => { const c = card(deck, x.id); return c ? (deck === "tarot" ? c.name : c.n + ". " + c.name) + (x.rev && canRev(deck) ? " (перевёрнутая)" : "") : "?"; };
  /* Сочетания по Солье: соседние карты в рядах + карта бланка с каждой картой расклада */
  const soleCombo = (a, b) => { const S = SOLE().cards; return (S[a] && S[a].c[b]) || (S[b] && S[b].c[a]) || ""; };
  const soleCombos = (d) => {
    const sp = spreadOf(d.deck, d.key, d.cards.length), out = [], seen = {};
    const add = (x, y, why) => { const a = card("lsole", x.id), b = card("lsole", y.id); if (!a || !b || a.n === b.n) return; const k = Math.min(a.n, b.n) + "-" + Math.max(a.n, b.n); if (seen[k]) return; const t = soleCombo(a.n, b.n); if (!t) return; seen[k] = 1; out.push({ a, b, t, why }); };
    if (sp.blank && d.blank) d.cards.forEach((x) => add({ id: d.blank }, x, "с картой бланка"));
    const rows = sp.rows && sp.rows.reduce((a, b) => a + b, 0) === d.cards.length ? sp.rows : [d.cards.length];
    let k = 0;
    rows.forEach((n) => { for (let i = k; i < k + n - 1; i++) add(d.cards[i], d.cards[i + 1], "соседние"); k += n; });
    return out;
  };

  /* ---------- ИИ ---------- */
  const methodNotes = (deck) => ((A.db().profile.spreadNotes || {})[deck] || "").trim();
  const aiReading = async (d, sys) => {
    const sp = spreadOf(d.deck, d.key, d.cards.length);
    const topic = { general: "общий вопрос", love: "отношения", work: "работа и деньги", health: "здоровье и самочувствие" }[d.topic] || "общий вопрос";
    const lines = d.cards.map((x, i) => (i + 1) + ". " + (sp.pos[i] || "Карта " + (i + 1)) + " — " + cardName(d.deck, x) + (d.deck === "tarot" ? " / " + enName(card("tarot", x.id)) : ""));
    if (d.deck === "lsole") {
      if (sp.blank && d.blank) lines.unshift("Карта бланка (спрашивающий): " + cardName("lsole", { id: d.blank }));
      if (sp.rows) lines.push("Раскладка по рядам сверху вниз: " + sp.rows.join(" + ") + " карт.");
      if (sp.note) lines.push("Особенность расклада: " + sp.note);
      const cb = soleCombos(d);
      if (cb.length) lines.push("Сочетания по Солье:\n" + cb.map((x) => "— " + x.a.name + " + " + x.b.name + ": " + x.t).join("\n"));
    }
    let rule = SYS_PROMPT[sys] || "";
    if (sys === "mine") rule = "Толкуй строго по методу пользователя, изложенному ниже (это его конспект книг и собственные правила):\n" + (methodNotes(d.deck === "lsole" ? "lenormand" : d.deck) || "(конспект не заполнен — используй классическую технику)");
    const prompt = "Колода: " + (d.deck === "tarot" ? "Таро Уэйта" : d.deck === "lsole" ? "Ленорман (36 карт, названия по Солье, с перевёрнутыми картами)" : "Ленорман (36 карт)") + ". Расклад: «" + sp.name + "». Тема: " + topic + ".\nВопрос: " + (d.q || "без конкретного вопроса") + "\nКарты по позициям:\n" + lines.join("\n") +
      "\n\nСистема толкования: " + rule + "\n\nСтруктура ответа: 1) Короткий вывод (2–3 предложения). 2) Позиции — по одной-две фразы на каждую, с учётом соседних карт. 3) Как карты связаны между собой. 4) Практический совет. Пиши по-русски, конкретно, без воды, без запугивания; не утверждай, что карты точно предсказывают будущее.";
    return A.ai.ask(prompt, { system: "Ты — опытный и бережный толкователь карт Таро и Ленорман.", max: d.cards.length > 12 ? 2600 : 1600 });
  };
  const recognize = async (deck, dataUrl) => {
    const list = deck === "tarot" ? "Используй английские названия Таро Уэйта: старшие — " + MAJ_EN.join(", ") + "; младшие — '<Ace|Two|…|Ten|Page|Knight|Queen|King> of <Wands|Cups|Swords|Pentacles>'. Отметь перевёрнутые карты."
      : "Это карты Ленорман: укажи номер (1–36) и название каждой карты (номер обычно напечатан на карте)." + (deck === "lsole" ? " Отметь перевёрнутые карты (reversed: true)." : "");
    const prompt = "На фото разложены гадальные карты. Перечисли все видимые карты в порядке чтения: по рядам сверху вниз, в ряду слева направо. " + list +
      ' Ответь ТОЛЬКО JSON без пояснений: {"cards":[{"name":"...","number":0,"reversed":false,"row":1}]}. Если карту не удаётся распознать, поставь name "?".';
    const t = await A.ai.askImage(prompt, dataUrl, { temperature: 0, max: 1500 });
    const m = t.match(/\{[\s\S]*\}/); if (!m) throw new Error("ИИ не вернул список карт. Попробуйте фото ровнее и ближе.");
    let j; try { j = JSON.parse(m[0]); } catch (e) { throw new Error("Не удалось разобрать ответ ИИ — попробуйте ещё раз."); }
    const norm = (s) => String(s || "").toLowerCase().replace(/^the\s+/, "").replace(/[^a-zа-яё0-9 ]/g, "").trim();
    return (j.cards || []).map((x) => {
      let c = null;
      if (deck === "tarot") { const n = norm(x.name); c = tarot().find((k) => norm(enName(k)) === n || norm(k.name) === n) || tarot().find((k) => n && norm(enName(k)).includes(n)); }
      else c = deckCards(deck).find((k) => k.n === +x.number) || deckCards(deck).find((k) => norm(k.name) === norm(x.name) || norm(k.en) === norm(x.name));
      return { id: c ? c.id : "", rev: canRev(deck) && !!x.reversed, raw: x.name };
    });
  };

  /* ---------- экраны ---------- */
  A.view("spreads", {
    title: (r) => ({ new: "Новый расклад", show: "Расклад" }[r.args[0]] || "Расклады"), tab: "more",
    render(el, r) {
      const sub = r.args[0] || "";
      if (sub === "new") {
        if (!D) D = newDraft(r.params.deck || "tarot");
        const S = spreadList(D.deck), cur = spreadOf(D.deck, D.key);
        let h = A.seg([["tarot", "Таро Уэйта"], ["lenormand", "Ленорман"], ["lsole", "Ленорман · Солье"]], D.deck, "dDeck");
        h += '<div class="card"><div class="form"><div class="fld full"><label>Вопрос</label><textarea id="dq" rows="2" placeholder="Например: как развить отношения с …?">' + esc(D.q) + "</textarea></div></div>" +
          '<h3 style="margin-top:10px">Тема</h3>' + A.seg([["general", "Общее"], ["love", "Отношения"], ["work", "Работа и деньги"]].concat(D.deck === "lsole" ? [["health", "Здоровье"]] : []), D.topic, "dTopic") +
          '<h3>Расклад</h3><div class="chips">' + S.map((s) => '<button class="chip' + (D.key === s[0] ? " on" : "") + '" data-a="dKey" data-v="' + s[0] + '">' + esc(s[1]) + " · " + (s[2] ? s[2].length : 36) + "</button>").join("") + "</div>" +
          (D.deck === "lsole" && cur.note ? '<p class="small muted" style="margin-top:8px">' + esc(cur.note) + "</p>" : "") +
          (D.deck === "lsole" && cur.blank ? '<h3 style="margin-top:10px">Карта бланка — вы</h3>' + A.seg(BLANKS, D.blank, "dBlank") + '<p class="small muted">Её не тянут: она лежит открыто, расклад читается от неё. Справа — будущее, слева — прошлое, сверху — планы, снизу — подавленное.</p>' : "") +
          (canRev(D.deck) ? '<label class="switch" style="margin-top:10px"><input type="checkbox" id="drev"' + (D.rev ? " checked" : "") + "><span></span>Учитывать перевёрнутые карты</label>" : "") + "</div>";
        h += '<div class="card"><h3>{{i:sparkles}} Как разложить</h3><div class="btns" style="margin-top:0"><button class="btn primary" data-a="dRandom">{{i:shuffle}} Вытянуть карты</button><button class="btn" data-a="dManual">{{i:layout-grid}} Выбрать вручную</button><button class="btn" data-a="dPhoto">{{i:camera}} По фото</button></div>' +
          '<p class="small muted" style="margin-top:8px">«По фото» — сфотографируйте свой расклад на столе, ИИ распознает карты и порядок (нужен бесплатный ключ ИИ). Число карт на фото определит расклад, если оно совпадает с одним из готовых.</p></div>';
        h += '<div id="dWork"></div>';
        return h;
      }
      if (sub === "show") {
        const s = A.byId("spreads", r.args[1]); if (!s) return A.empty("Расклад не найден");
        return showHtml(s);
      }
      // журнал
      const L = A.col("spreads").slice().sort((a, b) => (b.date + (b.t || "") > a.date + (a.t || "") ? 1 : -1));
      return '<div class="tw-tiles"><button class="tw-tile hot" data-a="go2" data-v="spreads/new?deck=tarot"><span class="tw-ti">{{i:wand-sparkles}}</span><b>Таро Уэйта</b><small>7 раскладов, 78 карт</small></button><button class="tw-tile hot" data-a="go2" data-v="spreads/new?deck=lenormand"><span class="tw-ti">{{i:clover}}</span><b>Ленорман</b><small>линии, квадрат, Большой расклад</small></button><button class="tw-tile hot" data-a="go2" data-v="spreads/new?deck=lsole"><span class="tw-ti">{{i:feather}}</span><b>Ленорман · Солье</b><small>13 раскладов, карта бланка, 1189 сочетаний</small></button></div>' +
        '<div class="card"><h3>{{i:scroll}} Мой метод толкования</h3><p class="small muted">Сюда можно вписать конспект книг (например, по Ленорман) и свои правила — ИИ будет толковать строго по ним, если выбрать систему «Мой метод».</p><div class="btns"><button class="btn sm" data-a="notes" data-v="tarot">Конспект: Таро</button><button class="btn sm" data-a="notes" data-v="lenormand">Конспект: Ленорман</button></div></div>' +
        '<div class="card"><h3>{{i:notebook-pen}} Журнал раскладов</h3>' + (L.length ? '<div class="list">' + L.map((s) => '<a class="item" style="text-decoration:none;color:inherit" href="#/spreads/show/' + s.id + '"><div class="tx"><b>' + esc(s.q || s.name) + "</b><small>" + A.fmtShort(s.date) + " · " + (DECK_NAME[s.deck] || "Ленорман") + " · " + esc(s.name) + " · " + s.cards.map((x) => cardName(s.deck, x).split(" (")[0]).slice(0, 3).join(", ") + (s.cards.length > 3 ? "…" : "") + '</small></div><span class="muted">›</span></a>').join("") + "</div>" : A.empty("Раскладов пока нет")) + "</div>" +
        '<a class="btn ghost block" href="#/tarot">{{i:book-open}} Учебник Таро и значения 78 карт</a>';
    },
    bind(el, r) {
      const sub = r.args[0] || "";
      const work = () => el.querySelector("#dWork");
      const sync = () => { if (!D) return; const q = el.querySelector("#dq"); if (q) D.q = q.value.trim(); const rv = el.querySelector("#drev"); if (rv) D.rev = rv.checked; };
      const need = () => { sync(); return spreadOf(D.deck, D.key, D.cards.length).pos.length; };
      const showDraft = () => { work().innerHTML = draftHtml(D); };
      A.bind(el, {
        go2(b) { D = null; A.go(b.dataset.v); },
        dDeck(b) { sync(); D = newDraft(b.dataset.v); D.q = (el.querySelector("#dq") || {}).value || ""; A.refresh(); },
        dTopic(b) { sync(); D.topic = b.dataset.v; A.refresh(); },
        dBlank(b) { sync(); D.blank = b.dataset.v; D.cards = D.cards.filter((x) => x.id !== D.blank); A.refresh(); },
        dKey(b) { sync(); D.key = b.dataset.v; D.cards = []; A.refresh(); },
        dRandom() {
          const n = need(), sp = spreadOf(D.deck, D.key), deck = deckCards(D.deck).filter((c) => !(sp.blank && c.id === D.blank));
          D.cards = shuffle(deck).slice(0, n).map((c) => ({ id: c.id, rev: canRev(D.deck) && D.rev && Math.random() < 0.3 }));
          showDraft(); work().scrollIntoView({ behavior: "smooth" });
        },
        dManual() {
          const n = need(), deck = deckCards(D.deck), sp = spreadOf(D.deck, D.key);
          if (D.cards.length !== n) D.cards = Array.from({ length: n }, () => ({ id: "", rev: false }));
          work().innerHTML = '<div class="card"><h3>Выберите карты</h3><div class="form">' + sp.pos.map((p, i) => '<div class="fld full"><label>' + (i + 1) + ". " + esc(p) + '</label><select data-i="' + i + '" class="dSel"><option value="">—</option>' + deck.map((c) => '<option value="' + c.id + '"' + (D.cards[i].id === c.id ? " selected" : "") + ">" + esc(D.deck === "tarot" ? c.name : c.n + ". " + c.name) + "</option>").join("") + "</select>" +
            (canRev(D.deck) ? '<label class="small"><input type="checkbox" class="dRev" data-i="' + i + '"' + (D.cards[i].rev ? " checked" : "") + "> перевёрнута</label>" : "") + "</div>").join("") + '</div><button class="btn primary block" data-a="dManualOk">Готово</button></div>';
          work().scrollIntoView({ behavior: "smooth" });
        },
        dManualOk() {
          el.querySelectorAll(".dSel").forEach((s) => (D.cards[+s.dataset.i].id = s.value));
          el.querySelectorAll(".dRev").forEach((s) => (D.cards[+s.dataset.i].rev = s.checked));
          if (D.cards.some((x) => !x.id)) return A.toast("Выберите карту для каждой позиции");
          showDraft();
        },
        dPhoto() {
          sync();
          if (!A.ai.ready()) { work().innerHTML = A.ai.need(); return; }
          const i = document.createElement("input"); i.type = "file"; i.accept = "image/*";
          i.onchange = async () => {
            const f = i.files[0]; if (!f) return;
            work().innerHTML = '<div class="card"><p class="muted">{{i:eye}} Распознаю карты на фото… это может занять до минуты.</p></div>';
            try {
              const url = await A.ai.shrink(f, 1400);
              const got = await recognize(D.deck, url);
              if (!got.length) throw new Error("Карты на фото не найдены");
              if (D.deck === "lsole") { const b = got.find((x) => x.id === "L28" || x.id === "L29"); const sp0 = spreadOf(D.deck, D.key); if (b && sp0.blank && got.length - 1 === sp0.pos.length) { D.blank = b.id; got.splice(got.indexOf(b), 1); } }
              D.cards = got; D.photo = await A.ai.shrink(f, 480);
              const byN = { tarot: { 1: "one", 3: "ppf", 5: "choice", 6: "love", 10: "celtic" }, lenormand: { 3: "l3", 5: "l5", 9: "l9", 36: "gt" }, lsole: { 1: "s1", 4: "s4", 5: "sday", 7: "sweek", 9: "s9", 12: "s3m", 16: "swork", 21: "smonth" } }[D.deck];
              const sp = spreadOf(D.deck, D.key);
              if (sp.pos.length !== got.length) D.key = byN[got.length] || "free";
              const unk = got.filter((x) => !x.id).length;
              work().innerHTML = '<div class="card"><h3>Распознано: ' + got.length + " " + A.plural(got.length, "карта", "карты", "карт") + "</h3>" + (unk ? '<p class="warn small">Не распознано: ' + unk + ". Выберите их вручную.</p>" : '<p class="small muted">Проверьте список — при ошибке поправьте карту.</p>') +
                '<img src="' + D.photo + '" style="width:100%;border-radius:12px;margin:6px 0">' + "</div>";
              el.querySelector('[data-a="dManual"]').click();
              const box = work(); box.insertAdjacentHTML("afterbegin", '<div class="card"><p class="small">Найдено карт: <b>' + got.length + "</b>. Расклад: <b>" + esc(spreadOf(D.deck, D.key, got.length).name) + "</b>. Ниже можно исправить распознавание.</p><img src=\"" + D.photo + "\" style=\"width:100%;border-radius:12px\" alt=\"Фото расклада\"></div>");
            } catch (e) { work().innerHTML = '<div class="card">' + (e.kind ? A.ai.errHtml(e) : '<p class="warn">' + esc(e.message) + "</p>") + "</div>"; }
          };
          i.click();
        },
        spOpen(b) { const x = D && (b.dataset.i === "b" ? { id: D.blank } : D.cards[+b.dataset.i]); if (x) cardSheet(D.deck, x); },
        async dSave() {
          sync();
          const sp = spreadOf(D.deck, D.key, D.cards.length);
          const s = A.upsert("spreads", { date: today(), t: new Date().toTimeString().slice(0, 5), deck: D.deck, key: D.key, name: sp.name, q: D.q, topic: D.topic, cards: D.cards, photo: D.photo || "", blank: D.deck === "lsole" && sp.blank ? D.blank : "" });
          D = null; A.go("spreads/show/" + s.id, true);
        },
        notes(b) {
          const deck = b.dataset.v, p = A.db().profile; p.spreadNotes = p.spreadNotes || {};
          A.formSheet("Мой метод · " + (deck === "tarot" ? "Таро" : "Ленорман"), [{ k: "t", label: "Конспект и правила толкования", type: "textarea", rows: 14, full: true }], { t: p.spreadNotes[deck] || "" }, (o) => { p.spreadNotes[deck] = o.t || ""; A.save(); A.toast("Сохранено"); });
        }
      });
      if (sub === "show") bindShow(el, A.byId("spreads", r.args[1]));
    }
  });

  const draftHtml = (d) => {
    const sp = spreadOf(d.deck, d.key, d.cards.length);
    return '<div class="card"><h3>' + esc(sp.name) + "</h3>" + layout(d) + '<p class="small muted">Нажмите на карту, чтобы прочитать её значение.</p>' + (d.deck === "lsole" ? soleCombosHtml(d) : "") + '<p></p><button class="btn primary block" data-a="dSave">{{i:check}} Сохранить и толковать</button></div>';
  };
  const cardSheet = (deck, x) => {
    const c = card(deck, x.id); if (!c) return;
    if (deck === "lsole") {
      const z = c.s;
      return A.sheet(cardName(deck, x), '<div style="display:flex;gap:12px;align-items:flex-start">' + face(deck, x, true) + '<div class="small"><p><b>Прямое:</b> ' + esc(z.u) + "</p><p><b>Перевёрнутое:</b> " + esc(z.r) + "</p></div></div>" +
        '<div class="small"><p><b>Любовь:</b> ' + esc(z.l) + "</p><p><b>Работа и деньги:</b> " + esc(z.w) + "</p><p><b>Здоровье:</b> " + esc(z.h) + "</p><p><b>Как человек:</b> " + esc(z.p) + "</p><p><b>В раскладе:</b> " + esc(z.pos) + "</p>" +
        '<details><summary><b>Сочетания с другими картами</b></summary>' + Object.keys(z.c).map((k) => "<p>+ " + esc(SOLE().names[k]) + ": " + esc(z.c[k]) + "</p>").join("") + "</details><p class='muted'>" + esc(SOLE().source) + "</p></div>");
    }
    const body = '<div style="display:flex;gap:12px;align-items:flex-start">' + face(deck, x, true) + '<div class="small">' + (deck === "tarot" ? "<p><b>Прямое:</b> " + esc(c.up) + "</p><p><b>Перевёрнутое:</b> " + esc(c.rev) + "</p><p class='muted'>" + esc(c.sym || "") + "</p>" : "<p><b>" + esc(c.key) + "</b></p><p>" + esc(c.m) + "</p><p><b>Отношения:</b> " + esc(c.love) + "</p><p><b>Дело:</b> " + esc(c.work) + "</p><p class='muted'>Игральная карта: " + esc(c.pc) + "</p>") + "</div></div>";
    A.sheet(cardName(deck, x), body);
  };
  const soleCombosHtml = (d) => {
    const cb = soleCombos(d);
    return cb.length ? cb.map((x) => '<p class="small"><b>' + esc(x.a.name) + " + " + esc(x.b.name) + "</b> <span class=\"muted\">(" + x.why + ")</span>: " + esc(x.t) + "</p>").join("") : '<p class="small muted">Для соседних карт этого расклада в книге нет готовых сочетаний — читайте карты по позициям.</p>';
  };
  const showHtml = (s) => {
    const sp = spreadOf(s.deck, s.key, s.cards.length);
    let h = '<div class="card"><div class="small muted">' + A.fmtDate(s.date) + " · " + (DECK_NAME[s.deck] || "Ленорман") + " · " + esc(sp.name) + "</div>" + (s.q ? '<h3 style="margin-top:4px">' + esc(s.q) + "</h3>" : "") + layout(s) + "</div>";
    if (s.photo) h += '<div class="card"><img src="' + s.photo + '" style="width:100%;border-radius:12px" alt="Фото расклада"></div>';
    h += '<div class="card"><h3>{{i:sparkles}} Толкование ИИ</h3><div class="form"><div class="fld full"><label>Система</label><select id="sSys">' + SYSTEMS[s.deck].map((x) => '<option value="' + x[0] + '"' + (s.sys === x[0] ? " selected" : "") + ">" + esc(x[1]) + "</option>").join("") + "</select></div></div>" +
      '<button class="btn primary block" data-a="sAi" style="margin-top:8px">' + (s.ai ? "Толковать заново" : "Толковать") + "</button>" + '<div id="sAiOut" class="ai-out" style="margin-top:10px">' + (s.ai ? A.ai.fmt(s.ai) + '<p class="small muted">Система: ' + esc((SYSTEMS[s.deck].find((x) => x[0] === s.sys) || ["", ""])[1]) + "</p>" : "") + "</div></div>";
    if (s.deck === "lsole") h += '<div class="card"><details><summary><b>{{i:feather}} Метод Солье: как читать</b></summary>' + SOLE().method.map((p) => '<p class="small">' + esc(p) + "</p>").join("") + (sp.note ? '<p class="small"><b>Этот расклад:</b> ' + esc(sp.note) + "</p>" : "") + "</details></div>";
    h += '<div class="card"><h3>{{i:book-open}} Значения по позициям</h3>' + (s.deck === "lenormand" && s.key !== "gt" ? '<p class="small muted">' + esc(window.LENORMAND.method[s.key === "l9" ? "square" : "line"]) + "</p>" : s.key === "gt" ? '<p class="small muted">' + esc(window.LENORMAND.method.gt) + "</p>" : "") +
      s.cards.map((x, i) => '<div class="item" data-a="spCard" data-i="' + i + '"><div class="ic" style="width:auto;height:auto;background:none">' + face(s.deck, x, true) + '</div><div class="tx"><b>' + esc(sp.pos[i] || "") + ": " + esc(cardName(s.deck, x)) + '</b><small style="white-space:normal">' + meaning(s.deck, x, s.topic) + "</small></div></div>").join("") + "</div>";
    if (s.deck === "lsole") h += '<div class="card"><h3>Сочетания карт</h3>' + soleCombosHtml(s) + "</div>";
    if (s.deck === "lenormand" && s.cards.length > 1 && s.key !== "gt") h += '<div class="card"><h3>Пары карт</h3><p class="small muted">' + esc(window.LENORMAND.method.pairs) + "</p>" + s.cards.slice(0, -1).map((x, i) => { const a = card("lenormand", x.id), b = card("lenormand", s.cards[i + 1].id); return a && b ? '<p class="small"><b>' + esc(a.name) + " + " + esc(b.name) + ":</b> " + esc(a.key.split(",")[0]) + " → " + esc(b.key.split(",").slice(0, 2).join(",")) + "</p>" : ""; }).join("") + "</div>";
    h += '<div class="card"><h3>Мои заметки</h3><textarea id="sNote" rows="4" placeholder="Что сбылось, что почувствовали…">' + esc(s.note || "") + '</textarea></div><div class="btns"><button class="btn danger ghost" data-a="sDel">Удалить расклад</button></div>';
    return h;
  };
  const bindShow = (el, s) => {
    if (!s) return;
    const n = el.querySelector("#sNote"); if (n) n.addEventListener("change", () => { s.note = n.value; A.upsert("spreads", s); });
    A.bind(el, {
      spOpen(b) { cardSheet(s.deck, b.dataset.i === "b" ? { id: s.blank } : s.cards[+b.dataset.i]); },
      spCard(b) { cardSheet(s.deck, s.cards[+b.dataset.i]); },
      async sAi(b) {
        if (!A.ai.ready()) { el.querySelector("#sAiOut").innerHTML = A.ai.need(); return; }
        const sys = el.querySelector("#sSys").value, out = el.querySelector("#sAiOut");
        b.disabled = true; out.innerHTML = '<p class="muted">Толкую… бесплатной модели нужно 10–60 секунд.</p>';
        try { const t = await aiReading(s, sys); s.ai = t; s.sys = sys; A.upsert("spreads", s); out.innerHTML = A.ai.fmt(t) + '<p class="small muted">Модель: ' + esc(A.ai.lastModel || "") + "</p>"; }
        catch (e) { out.innerHTML = A.ai.errHtml(e); }
        b.disabled = false;
      },
      sDel() { A.confirm("Удалить расклад?", () => { A.remove("spreads", s.id); A.go("spreads", true); }); }
    });
  };
})();
