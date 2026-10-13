/* Мини-гримуар: Луна сегодня (фаза, освещённость, знак — расчёт без интернета), Колесо года,
   соответствия (дни, свечи, камни, травы, стихии), руны, ритуалы, Книга теней. Карточка «Луна сегодня» на главной. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays } = A;
  const G = () => window.GRIMOIRE;

  /* ---------- Луна ---------- */
  const SYN = 29.530588853;
  const jd = (d) => Date.parse(d + "T12:00:00Z") / 864e5 + 2440587.5;
  A.moon = (d) => {
    const J = jd(d), age = (((J - 2451550.1) / SYN) % 1 + 1) % 1 * SYN;
    const illum = Math.round(((1 - Math.cos((2 * Math.PI * age) / SYN)) / 2) * 100);
    const ph = G().PHASES.find((p) => age >= p.from && age < p.to) || G().PHASES[0];
    // упрощённая долгота Луны (ошибка ~1–2°), тропический зодиак
    const n = J - 2451545, rad = Math.PI / 180;
    const Lm = 218.316 + 13.176396 * n, M = 134.963 + 13.064993 * n, Ms = 357.529 + 0.98560028 * n, D = 297.85 + 12.190749 * n;
    let lon = Lm + 6.289 * Math.sin(M * rad) + 1.274 * Math.sin((2 * D - M) * rad) + 0.658 * Math.sin(2 * D * rad) - 0.186 * Math.sin(Ms * rad);
    lon = ((lon % 360) + 360) % 360;
    const SIGNS = ["Овне", "Тельце", "Близнецах", "Раке", "Льве", "Деве", "Весах", "Скорпионе", "Стрельце", "Козероге", "Водолее", "Рыбах"];
    return { age, illum, ph, day: Math.floor(age) + 1, waxing: age < SYN / 2, sign: SIGNS[Math.floor(lon / 30)], lon };
  };
  const SIGN_TIP = { "Овне": "решительные начала, спорт, смелость", "Тельце": "деньги, тело, красота, неспешность", "Близнецах": "общение, письма, учёба", "Раке": "дом, семья, забота о себе", "Льве": "творчество, самовыражение, праздник", "Деве": "порядок, здоровье, мелкие дела", "Весах": "отношения, искусство, договорённости", "Скорпионе": "глубина, тайное, трансформация", "Стрельце": "путешествия, учёба, вдохновение", "Козероге": "цели, карьера, структура", "Водолее": "друзья, идеи, свобода", "Рыбах": "интуиция, сны, отдых, творчество" };
  const moonSvg = (age, size) => {
    const s = size || 64, r = s / 2 - 2, c = s / 2, k = Math.cos((2 * Math.PI * age) / SYN), wax = age < SYN / 2;
    // освещённая часть: полукруг + эллипс
    const rx = Math.abs(k) * r, sweep = wax ? 1 : 0;
    const lit = "M" + c + " " + (c - r) + " A" + r + " " + r + " 0 0 " + sweep + " " + c + " " + (c + r) + " A" + rx + " " + r + " 0 0 " + (k > 0 ? 1 - sweep : sweep) + " " + c + " " + (c - r) + "Z";
    return '<svg viewBox="0 0 ' + s + " " + s + '" width="' + s + '" height="' + s + '" class="moon-svg"><circle cx="' + c + '" cy="' + c + '" r="' + r + '" class="moon-dark"/><path d="' + lit + '" class="moon-lit"/></svg>';
  };
  const nextSabbat = (d) => {
    const y = +d.slice(0, 4);
    const all = [y, y + 1].flatMap((yy) => G().SABBATS.map((s) => ({ s, date: yy + "-" + s[1] }))).filter((x) => x.date >= d).sort((a, b) => (a.date > b.date ? 1 : -1));
    return all[0];
  };
  const dow = (d) => new Date(d + "T12:00:00").getDay();
  const runeOf = (d) => G().RUNES[(A.diffDays("2026-01-01", d) * 5 + 3) % 24];

  /* ---------- карточка на главной ---------- */
  A.CARD_NAMES.grimoire = "Луна сегодня";
  A.todayCards.grimoire = (d) => {
    const m = A.moon(d), day = G().DAYS[dow(d)], r = runeOf(d);
    return '<div class="card"><h3>{{i:moon-star}} Луна сегодня<span class="sp"></span><a class="link" href="#/grimoire">гримуар →</a></h3><div class="row" style="gap:12px">' + moonSvg(m.age, 56) +
      '<div class="grow"><b>' + m.ph.name + "</b> · " + m.illum + "% · " + m.day + "-й лунный день<br><small class=\"muted\">Луна в " + m.sign + " — " + esc(SIGN_TIP[m.sign]) + "</small></div></div>" +
      '<p class="small" style="margin:8px 0 0"><b>Тема:</b> ' + esc(m.ph.theme) + ". " + esc(m.ph.do) + "</p>" +
      '<p class="small muted" style="margin:6px 0 0">' + day[0] + " — " + day[1] + ": " + day[3] + ' · руна дня <b style="font-size:16px">' + r[1] + "</b> " + r[0] + "</p></div>";
  };

  /* ---------- экран ---------- */
  const TABS = [["today", "Сегодня"], ["moon", "Луна"], ["wheel", "Колесо года"], ["corr", "Соответствия"], ["runes", "Руны"], ["rituals", "Ритуалы"], ["book", "Книга теней"]];
  A.view("grimoire", {
    title: "Мини-гримуар", tab: "more",
    render(el, r) {
      const sec = r.args[0] || "today", d = today(), g = G();
      let h = A.seg(TABS, sec, "gSec");
      if (sec === "today") {
        const m = A.moon(d), day = g.DAYS[dow(d)], ns = nextSabbat(d), rn = runeOf(d), rit = g.RITUALS.filter((x) => x.ph === m.ph.k || x.ph === "any");
        h += '<div class="card gr-hero"><div class="row" style="gap:14px">' + moonSvg(m.age, 84) + '<div class="grow"><div class="small muted">' + A.fmtDate(d) + '</div><h2 style="margin:2px 0">' + m.ph.name + '</h2><div class="small">' + m.illum + "% освещённости · " + m.day + "-й лунный день · Луна в " + m.sign + "</div></div></div>" +
          '<p style="margin-top:10px"><b>Тема фазы:</b> ' + esc(m.ph.theme) + '</p><p class="small"><b>Что делать:</b> ' + esc(m.ph.do) + '</p><p class="small muted"><b>Чего избегать:</b> ' + esc(m.ph.avoid) + "</p><p class=\"small\"><b>Луна в " + m.sign + ":</b> " + esc(SIGN_TIP[m.sign]) + ".</p></div>";
        h += '<div class="card"><h3>{{i:sun}} ' + day[0] + " — день: " + day[1] + '</h3><p class="small"><b>Темы:</b> ' + day[3] + '</p><p class="small"><b>Свеча:</b> ' + day[2] + " · <b>Камни:</b> " + day[4] + " · <b>Травы:</b> " + day[5] + "</p></div>";
        h += '<div class="card"><h3>Руна дня</h3><div class="row" style="gap:12px"><div class="gr-rune">' + rn[1] + '</div><div class="grow"><b>' + rn[0] + "</b> (" + rn[2] + ')<br><small>' + esc(rn[3]) + "</small></div></div></div>";
        if (rit.length) h += '<div class="card"><h3>{{i:sparkles}} Подходит сегодня</h3>' + rit.map((x) => '<div class="item" data-a="rit" data-t="' + esc(x.t) + '"><div class="tx"><b>' + esc(x.t) + "</b><small>" + x.time + " минут</small></div><span class=\"muted\">›</span></div>").join("") + "</div>";
        if (ns) h += '<div class="card tint"><p class="small"><b>Следующий праздник Колеса года:</b> ' + ns.s[0] + " — " + A.fmtDate(ns.date) + " (через " + A.diffDays(d, ns.date) + " " + A.plural(A.diffDays(d, ns.date), "день", "дня", "дней") + ").</p></div>";
        h += '<div class="btns"><button class="btn primary" data-a="bNew">{{i:notebook-pen}} Запись в Книгу теней</button><a class="btn" href="#/spreads">{{i:layout-grid}} Расклад</a></div>';
      } else if (sec === "moon") {
        const start = d.slice(0, 8) + "01", days = []; for (let i = 0; i < 31; i++) { const x = addDays(start, i); if (x.slice(5, 7) !== d.slice(5, 7)) break; days.push(x); }
        h += '<div class="card"><h3>Луна в этом месяце</h3><div class="gr-cal">' + days.map((x) => { const m = A.moon(x); return '<div class="gr-day' + (x === d ? " on" : "") + '">' + moonSvg(m.age, 28) + "<b>" + +x.slice(8) + "</b><small>" + (["new", "full", "fq", "lq"].includes(m.ph.k) ? m.ph.name.split(" ")[0] : "") + "</small></div>"; }).join("") + "</div></div>";
        h += g.PHASES.map((p) => '<div class="card"><h3>' + moonSvg(p.k === "full" ? 14.8 : p.k === "new" ? 0.5 : (p.from + p.to) / 2, 26) + " " + p.name + '</h3><p class="small"><b>' + esc(p.theme) + ".</b> " + esc(p.do) + '</p><p class="small muted">Избегать: ' + esc(p.avoid) + "</p></div>").join("");
      } else if (sec === "wheel") {
        const y = d.slice(0, 4);
        h += '<p class="small muted" style="padding:0 6px">Восемь праздников годового цикла. Даты солнцестояний и равноденствий могут смещаться на день.</p>' + g.SABBATS.map((s) => '<div class="card"><h3>' + s[0] + ' <small class="muted">· ' + A.fmtDate(y + "-" + s[1]).replace(/\s?\d{4}.*$/, "") + "</small></h3><p>" + esc(s[2]) + '</p><p class="small"><b>Как отметить:</b> ' + esc(s[3]) + '</p><p class="small muted">Символы: ' + esc(s[4]) + "</p></div>").join("");
      } else if (sec === "corr") {
        h += '<div class="card"><h3>Дни недели и планеты</h3><table class="tbl"><tr><th>День</th><th>Планета</th><th>Темы</th></tr>' + g.DAYS.map((x) => "<tr><td>" + x[0] + "</td><td>" + x[1] + '</td><td class="small">' + x[3] + "<br><span class=\"muted\">свеча: " + x[2] + "</span></td></tr>").join("") + "</table></div>";
        h += '<div class="card"><h3>Цвета свечей</h3>' + g.COLORS.map((x) => '<p class="small"><b>' + x[0] + ".</b> " + x[1] + "</p>").join("") + '<p class="small muted">Свечу не оставляйте без присмотра, ставьте на негорючую подставку вдали от штор и животных.</p></div>';
        h += '<div class="card"><h3>Стихии</h3>' + g.ELEMENTS.map((x) => '<p class="small"><b>' + x[0] + "</b> · " + x[1] + " · " + x[2] + "<br>" + x[3] + ' <span class="muted">(' + x[4] + ")</span></p>").join("") + "</div>";
        h += '<div class="card"><h3>Камни</h3>' + g.CRYSTALS.map((x) => '<p class="small"><b>' + x[0] + "</b> — " + x[1] + ' <span class="muted">· ' + x[2] + "</span></p>").join("") + "</div>";
        h += '<div class="card"><h3>Травы</h3><p class="small muted">Символические соответствия. Травы — не лекарство; внутрь — только обычные пищевые и в кулинарных количествах.</p>' + g.HERBS.map((x) => '<p class="small"><b>' + x[0] + "</b> — " + x[1] + (x[2] ? ' <span class="tag must">не внутрь</span>' : "") + "</p>").join("") + "</div>";
      } else if (sec === "runes") {
        const rn = runeOf(d);
        h += '<div class="card"><h3>Руна дня: ' + rn[0] + '</h3><div class="row" style="gap:12px"><div class="gr-rune">' + rn[1] + '</div><div class="small">' + esc(rn[3]) + '</div></div><button class="btn sm" data-a="runeDraw" style="margin-top:8px">{{i:shuffle}} Вытянуть руну</button><div id="runeOut"></div></div>';
        h += '<div class="card"><h3>Старший футарк</h3><div class="list">' + G().RUNES.map((x) => '<div class="item"><div class="gr-rune sm">' + x[1] + '</div><div class="tx"><b>' + x[0] + " · " + x[2] + '</b><small style="white-space:normal">' + esc(x[3]) + " · <i>перевёрнутая: " + esc(x[4]) + "</i></small></div></div>").join("") + "</div></div>";
      } else if (sec === "rituals") {
        h += g.RITUALS.map((x) => '<div class="card"><h3>' + esc(x.t) + ' <small class="muted">· ' + x.time + " мин" + (x.ph !== "any" ? " · " + (g.PHASES.find((p) => p.k === x.ph) || {}).name : "") + '</small></h3><p class="small"><b>Нужно:</b> ' + x.items.map(esc).join(", ") + "</p><ol class=\"small\">" + x.steps.map((s) => "<li>" + esc(s) + "</li>").join("") + "</ol></div>").join("");
      } else if (sec === "book") {
        const B = A.col("bos").slice().sort((a, b) => (b.date > a.date ? 1 : -1));
        h += '<button class="btn primary block" data-a="bNew" style="margin-bottom:12px">{{i:plus}} Новая запись</button>' + (B.length ? '<div class="card"><div class="list">' + B.map((x) => '<div class="item" data-a="bOpen" data-id="' + x.id + '"><div class="tx"><b>' + esc(x.title || "Запись") + "</b><small>" + A.fmtShort(x.date) + " · " + esc(x.moon || "") + (x.kind ? " · " + esc(x.kind) : "") + "</small></div><span class=\"muted\">›</span></div>").join("") + "</div></div>" : A.empty("Книга теней пуста. Записывайте намерения, ритуалы, сны и знаки — через пару месяцев увидите свои закономерности."));
      }
      if (sec === "today") h += '<p class="small muted" style="padding:0 6px">Гримуар — сборник символических практик для самонаблюдения и настроя. Он не заменяет врача, психолога и финансового советника.</p>';
      return h;
    },
    bind(el) {
      A.bind(el, {
        gSec(b) { A.go("grimoire/" + b.dataset.v, true); },
        rit(b) { const x = G().RITUALS.find((r) => r.t === b.dataset.t); if (x) A.sheet(x.t, '<p class="small"><b>Нужно:</b> ' + x.items.map(esc).join(", ") + "</p><ol>" + x.steps.map((s) => "<li>" + esc(s) + "</li>").join("") + "</ol>"); },
        runeDraw() { const R = G().RUNES, x = R[Math.floor(Math.random() * R.length)], rev = !x[4].startsWith("—") && Math.random() < 0.35; el.querySelector("#runeOut").innerHTML = '<div class="row" style="gap:12px;margin-top:10px"><div class="gr-rune' + (rev ? " rev" : "") + '">' + x[1] + '</div><div class="small"><b>' + x[0] + (rev ? " (перевёрнутая)" : "") + "</b><br>" + esc(rev ? x[4] : x[3]) + "</div></div>"; },
        bNew() { edit(null); },
        bOpen(b) { edit(A.byId("bos", b.dataset.id)); }
      });
    }
  });
  const edit = (x) => {
    const isNew = !x, m = A.moon(today());
    x = x || { date: today(), moon: m.ph.name + ", Луна в " + m.sign, kind: "Намерение" };
    A.formSheet(isNew ? "Новая запись" : "Запись", [
      { k: "date", label: "Дата", type: "date" }, { k: "kind", label: "Тип", type: "chips", opts: ["Намерение", "Ритуал", "Сон", "Знак", "Расклад", "Благодарность", "Заметка"] },
      { k: "title", label: "Заголовок", full: true }, { k: "text", label: "Текст", type: "textarea", rows: 8 }, { k: "result", label: "Что произошло потом", type: "textarea", rows: 3 }
    ], x, (o) => { Object.assign(x, o); if (o.date !== today() && isNew) { const mm = A.moon(o.date); x.moon = mm.ph.name + ", Луна в " + mm.sign; } A.upsert("bos", x); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("bos", x.id); A.refresh(); } });
  };
})();
