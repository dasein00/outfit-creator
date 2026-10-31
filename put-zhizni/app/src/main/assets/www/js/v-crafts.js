/* Рукоделие: вязание спицами и крючком (приёмы по шагам с картинками, узоры — вид полотна и схема)
   и украшения своими руками (инструменты, приёмы сборки, серьги и другие изделия по шагам).
   Картинки — векторные схемы, рисуются из описаний кадров в content-crafts.js. */
(function () {
  "use strict";
  const A = window.App;
  const { esc } = A;
  const K = () => window.CRAFTS;

  /* ---------- рисование кадров ---------- */
  // Кадр — список примитивов в системе координат 200×120.
  const PR = {
    needle: (a) => '<line x1="' + a[1] + '" y1="' + a[2] + '" x2="' + a[3] + '" y2="' + a[4] + '" class="cf-needle"/>',
    loop: (a) => '<ellipse cx="' + a[1] + '" cy="' + a[2] + '" rx="' + (a[3] || 5) + '" ry="' + (a[4] || 9) + '" class="cf-loop' + (a[5] ? " " + a[5] : "") + '"/>',
    yarn: (a) => '<path d="' + a[1] + '" class="cf-yarn' + (a[2] ? " " + a[2] : "") + '"/>',
    arrow: (a) => '<path d="' + a[1] + '" class="cf-arrow" marker-end="url(#cfAr)"/>',
    hook: (a) => { const [x, y, ang] = [a[1], a[2], a[3] || 0]; return '<g transform="translate(' + x + " " + y + ") rotate(" + ang + ')"><line x1="0" y1="0" x2="-90" y2="0" class="cf-hook"/><path d="M0 0 q4 -1 4 -5 q0 -3 -3 -3" class="cf-hookend"/></g>'; },
    bead: (a) => '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="' + (a[3] || 7) + '" class="cf-bead" style="fill:' + (a[4] || "#B3A4E6") + '"/><circle cx="' + (a[1] - (a[3] || 7) / 3) + '" cy="' + (a[2] - (a[3] || 7) / 3) + '" r="' + (a[3] || 7) / 4 + '" fill="#fff" opacity=".6"/>',
    drop: (a) => { const [x, y, r, c] = [a[1], a[2], a[3] || 8, a[4] || "#E7B7C8"]; return '<path d="M' + x + " " + (y - r * 1.6) + " C" + (x + r) + " " + (y - r * 0.4) + " " + (x + r) + " " + (y + r) + " " + x + " " + (y + r) + " C" + (x - r) + " " + (y + r) + " " + (x - r) + " " + (y - r * 0.4) + " " + x + " " + (y - r * 1.6) + 'Z" class="cf-bead" style="fill:' + c + '"/>'; },
    pin: (a) => '<line x1="' + a[1] + '" y1="' + a[2] + '" x2="' + a[1] + '" y2="' + (a[2] + a[3]) + '" class="cf-wire"/><line x1="' + (a[1] - 4) + '" y1="' + (a[2] + a[3]) + '" x2="' + (a[1] + 4) + '" y2="' + (a[2] + a[3]) + '" class="cf-wire"/>',
    wire: (a) => '<path d="' + a[1] + '" class="cf-wire' + (a[2] ? " " + a[2] : "") + '"/>',
    ring: (a) => { const [cx, cy, r, gap] = [a[1], a[2], a[3] || 8, a[4] || 0]; if (!gap) return '<circle cx="' + cx + '" cy="' + cy + '" r="' + r + '" class="cf-ring"/>'; const g = gap * Math.PI / 180, x1 = cx + r * Math.sin(g / 2), y1 = cy - r * Math.cos(g / 2), x2 = cx - r * Math.sin(g / 2); return '<path d="M' + x1.toFixed(1) + " " + y1.toFixed(1) + " A" + r + " " + r + " 0 1 1 " + x2.toFixed(1) + " " + y1.toFixed(1) + '" class="cf-ring"/>'; },
    chain: (a) => { const [x, y, n, dx, dy] = [a[1], a[2], a[3], a[4] || 0, a[5] || 7]; let h = ""; for (let i = 0; i < n; i++) h += '<ellipse cx="' + (x + dx * i) + '" cy="' + (y + dy * i) + '" rx="' + (i % 2 ? 2 : 3.2) + '" ry="4" class="cf-ring thin"/>'; return h; },
    hookear: (a) => '<path d="M' + a[1] + " " + a[2] + " c0 -12 -14 -14 -14 -2 c0 6 4 10 4 18" + '" class="cf-wire"/><circle cx="' + a[1] + '" cy="' + (a[2] + 3) + '" r="3" class="cf-ring"/>',
    tool: (a) => { const [x, y, kind, ang] = [a[1], a[2], a[3], a[4] || 0]; const tip = kind === "round" ? '<path d="M0 -3 l18 -2 M0 3 l18 2" class="cf-tooljaw round"/>' : kind === "cut" ? '<path d="M0 -3 l14 1 l4 2 M0 3 l14 -1 l4 -2" class="cf-tooljaw"/>' : '<path d="M0 -3 l18 0 M0 3 l18 0" class="cf-tooljaw"/>'; return '<g transform="translate(' + x + " " + y + ") rotate(" + ang + ')"><path d="M0 -3 c-14 -2 -30 -8 -46 -12 M0 3 c-14 2 -30 8 -46 12" class="cf-toolh"/>' + tip + "</g>"; },
    tassel: (a) => { const [x, y, len, c] = [a[1], a[2], a[3] || 36, a[4] || "#D27A8E"]; let h = '<rect x="' + (x - 6) + '" y="' + y + '" width="12" height="7" rx="2" class="cf-cap"/>'; for (let i = -5; i <= 5; i++) h += '<line x1="' + (x + i * 0.9) + '" y1="' + (y + 6) + '" x2="' + (x + i * 1.6) + '" y2="' + (y + 6 + len) + '" style="stroke:' + c + '" class="cf-thread"/>'; return h; },
    hoop: (a) => '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="' + a[3] + '" class="cf-ring hoop"/>',
    stud: (a) => '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="' + (a[3] || 6) + '" class="cf-cap"/><line x1="' + a[1] + '" y1="' + a[2] + '" x2="' + (a[1] + 16) + '" y2="' + a[2] + '" class="cf-wire"/>',
    crimp: (a) => '<rect x="' + (a[1] - 4) + '" y="' + (a[2] - 3) + '" width="8" height="6" rx="1.5" class="cf-cap"/>',
    hand: (a) => '<path d="M' + a[1] + " " + a[2] + ' c-10 -2 -20 4 -26 12 c-2 3 1 6 4 4 l10 -6 M' + a[1] + " " + a[2] + ' c-12 4 -18 12 -22 20" class="cf-hand"/>',
    // подпись не вылезает за край кадра: ширину оцениваем по числу букв
    text: (a) => { const w = String(a[3]).length * 4.7, x = a[4] ? a[1] : Math.max(2, Math.min(a[1], 198 - w)); return '<text x="' + x + '" y="' + a[2] + '" class="cf-txt"' + (a[4] ? ' text-anchor="' + a[4] + '"' : "") + ">" + esc(a[3]) + "</text>"; },
    num: (a) => '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="7" class="cf-numc"/><text x="' + a[1] + '" y="' + (a[2] + 3.5) + '" class="cf-numt" text-anchor="middle">' + a[3] + "</text>"
  };
  const DEFS = '<defs><marker id="cfAr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10z" class="cf-arhead"/></marker></defs>';
  const frame = (f, w) => '<svg viewBox="0 0 200 120" class="cf-svg" style="max-width:' + (w || 320) + 'px" role="img">' + DEFS + f.map((a) => (PR[a[0]] ? PR[a[0]](a) : "")).join("") + "</svg>";
  A.craftFrame = frame;

  /* ---------- узоры спицами: вид полотна и схема ---------- */
  // Символы ряда схемы: k лицевая, p изнаночная, o накид, / 2 вместе лицевой (наклон вправо), \ протяжка (наклон влево),
  // < и > — начало перекрёста 2/2 влево и вправо (занимает 4 клетки), . — не учитывается.
  const C = 13;
  const fabric = (rows, opt = {}) => {
    const W = rows[0].length, H = rows.length, rep = opt.rep || 1;
    const R = []; for (let r = 0; r < rep; r++) rows.forEach((x) => R.push(x));
    let h = '<svg viewBox="0 0 ' + W * C * (opt.wrep || 1) + " " + R.length * C + '" class="cf-fab" role="img">';
    R.forEach((row, y) => {
      for (let wr = 0; wr < (opt.wrep || 1); wr++) for (let x = 0; x < W; x++) {
        const ch = row[x], X = (wr * W + x) * C, Y = y * C;
        if (ch === "k") h += '<path d="M' + (X + 2) + " " + (Y + 1) + " L" + (X + C / 2) + " " + (Y + C - 1) + " L" + (X + C - 2) + " " + (Y + 1) + '" class="cf-st"/>';
        else if (ch === "p") h += '<path d="M' + (X + 1) + " " + (Y + C / 2 + 2) + " Q" + (X + C / 2) + " " + (Y + 2) + " " + (X + C - 1) + " " + (Y + C / 2 + 2) + '" class="cf-st purl"/>';
        else if (ch === "o") h += '<circle cx="' + (X + C / 2) + '" cy="' + (Y + C / 2) + '" r="3.2" class="cf-hole"/>';
        else if (ch === "/") h += '<path d="M' + (X + 2) + " " + (Y + 2) + " L" + (X + C - 3) + " " + (Y + C - 1) + " L" + (X + C - 1) + " " + (Y + 1) + '" class="cf-st dec"/>';
        else if (ch === "\\") h += '<path d="M' + (X + 1) + " " + (Y + 1) + " L" + (X + 3) + " " + (Y + C - 1) + " L" + (X + C - 2) + " " + (Y + 2) + '" class="cf-st dec"/>';
        else if (ch === "<" || ch === ">") {
          const s = ch === "<" ? 1 : -1, x1 = X, x2 = X + 4 * C;
          h += '<path d="M' + (x1 + C) + " " + (Y + C) + " C" + (x1 + C) + " " + (Y + C / 2) + " " + (x2 - C) + " " + (Y + C / 2) + " " + (x2 - C) + " " + Y + '" class="cf-cable ' + (s > 0 ? "front" : "back") + '"/><path d="M' + (x2 - C) + " " + (Y + C) + " C" + (x2 - C) + " " + (Y + C / 2) + " " + (x1 + C) + " " + (Y + C / 2) + " " + (x1 + C) + " " + Y + '" class="cf-cable ' + (s > 0 ? "back" : "front") + '"/>';
        } else if (ch === "c") { /* часть перекрёста */ }
      }
    });
    return h + "</svg>";
  };
  const SYM = { k: "□", p: "—", o: "○", "/": "╱", "\\": "╲", "<": "⟨⟩", ">": "⟨⟩" };
  const chart = (rows) => {
    const W = rows[0].length;
    let h = '<svg viewBox="0 0 ' + (W * C + 22) + " " + (rows.length * C + 2) + '" class="cf-chart" role="img">';
    rows.slice().reverse().forEach((row, i) => {
      const y = i * C + 1, rn = rows.length - i;
      for (let x = 0; x < W; x++) {
        const ch = row[x], X = x * C;
        h += '<rect x="' + X + '" y="' + y + '" width="' + C + '" height="' + C + '" class="cf-cell"/>';
        if (ch === "p") h += '<line x1="' + (X + 3) + '" y1="' + (y + C / 2) + '" x2="' + (X + C - 3) + '" y2="' + (y + C / 2) + '" class="cf-sym"/>';
        else if (ch === "o") h += '<circle cx="' + (X + C / 2) + '" cy="' + (y + C / 2) + '" r="3.5" class="cf-sym"/>';
        else if (ch === "/") h += '<line x1="' + (X + 3) + '" y1="' + (y + C - 3) + '" x2="' + (X + C - 3) + '" y2="' + (y + 3) + '" class="cf-sym"/>';
        else if (ch === "\\") h += '<line x1="' + (X + 3) + '" y1="' + (y + 3) + '" x2="' + (X + C - 3) + '" y2="' + (y + C - 3) + '" class="cf-sym"/>';
        else if (ch === "<" || ch === ">") h += '<path d="M' + (X + 2) + " " + (y + C - 2) + " L" + (X + 4 * C - 2) + " " + (y + 2) + " M" + (X + 2) + " " + (y + 2) + " L" + (X + 4 * C - 2) + " " + (y + C - 2) + '" class="cf-sym' + (ch === "<" ? " l" : " r") + '"/>';
      }
      h += '<text x="' + (W * C + 4) + '" y="' + (y + C - 3) + '" class="cf-rn">' + rn + "</text>";
    });
    return h + "</svg>";
  };
  A.craftFabric = fabric;

  /* ---------- экраны ---------- */
  const TABS = [["knit", "Спицы"], ["crochet", "Крючок"], ["jewel", "Украшения"]];
  const diff = (n) => "●".repeat(n) + "○".repeat(3 - n);
  const stepsHtml = (steps) => steps.map((s, i) => '<div class="cf-step"><div class="cf-sn">' + (i + 1) + "</div><div>" + (s.f ? frame(s.f) : "") + "<p>" + esc(s.t) + "</p>" + (s.tip ? '<p class="small muted">' + esc(s.tip) + "</p>" : "") + "</div></div>").join("");
  const patternCard = (p) => '<div class="item" data-a="pat" data-id="' + p.id + '"><div class="ic" style="width:64px;height:auto;background:none">' + fabric(p.rows, { rep: 1, wrep: p.rows[0].length < 6 ? 2 : 1 }) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>';

  A.view("crafts", {
    title: (r) => (r.args[1] && window.CRAFTS ? ((K().items || {})[r.args[1]] || {}).t || "Рукоделие" : "Рукоделие"), tab: "more",
    render(el, r) {
      if (!window.CRAFTS) { A.need("js/content-crafts.js").then(() => A.refresh()); return '<div class="card"><p class="muted">Загружаю…</p></div>'; }
      const sec = r.args[0] || "knit", id = r.args[1], X = K();
      if (id && X.items[id]) return itemHtml(X.items[id]);
      let h = A.seg(TABS, sec, "cSec");
      if (sec === "knit") {
        h += '<div class="card tint"><p class="small">' + esc(X.knitIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Приёмы по шагам</h3><div class="list">' + X.knitTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:layout-grid}} Узоры: как выглядит и как вязать</h3><div class="list">' + X.knitPat.map(patternCard).join("") + "</div></div>";
        h += '<div class="card"><h3>Как читать схемы</h3><p class="small">' + esc(X.chartHow) + '</p><p class="small">□ лицевая · — изнаночная · ○ накид · ╱ две вместе лицевой (наклон вправо) · ╲ протяжка (наклон влево) · ✕ на 4 клетки — коса</p></div>';
        h += '<div class="card"><h3>Словарь вязальщицы</h3>' + X.glossary.map((g) => '<p class="small"><b>' + esc(g[0]) + "</b> — " + esc(g[1]) + "</p>").join("") + "</div>";
      } else if (sec === "crochet") {
        h += '<div class="card tint"><p class="small">' + esc(X.crochetIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Основные петли</h3><div class="list">' + X.crochetTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>Условные обозначения</h3>' + X.crochetSym.map((s) => '<p class="small"><b style="display:inline-block;min-width:26px;font-size:16px">' + esc(s[0]) + "</b> " + esc(s[1]) + "</p>").join("") + "</div>";
      } else {
        h += '<div class="card tint"><p class="small">' + esc(X.jewelIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:sparkles}} Серьги</h3><div class="list">' + X.jewelProj.filter((p) => p.kind === "серьги").map((p) => '<div class="item" data-a="it" data-id="' + p.id + '"><div class="ic" style="width:56px;height:auto;background:none">' + frame(p.pic, 56) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + p.time + " мин · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:gem}} Браслеты и колье</h3><div class="list">' + X.jewelProj.filter((p) => p.kind !== "серьги").map((p) => '<div class="item" data-a="it" data-id="' + p.id + '"><div class="ic" style="width:56px;height:auto;background:none">' + frame(p.pic, 56) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + p.time + " мин · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Приёмы сборки</h3><div class="list">' + X.jewelTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>Инструменты</h3>' + X.tools.map((t) => '<div class="row" style="gap:10px;align-items:center;margin:6px 0">' + frame(t.f, 90) + '<p class="small grow" style="margin:0"><b>' + esc(t.t) + ".</b> " + esc(t.d) + "</p></div>").join("") + "</div>";
        h += '<div class="card"><h3>Фурнитура</h3>' + X.findings.map((g) => '<p class="small"><b>' + esc(g[0]) + "</b> — " + esc(g[1]) + "</p>").join("") + "</div>";
      }
      return h;
    },
    bind(el, r) {
      if (!window.CRAFTS) return;
      A.bind(el, {
        cSec(b) { A.go("crafts/" + b.dataset.v, true); },
        it(b) { A.go("crafts/" + (r.args[0] || "knit") + "/" + b.dataset.id); },
        pat(b) { A.go("crafts/knit/" + b.dataset.id); },
        async cAsk(b) {
          const it = K().items[r.args[1]], q = el.querySelector("#cQ").value.trim(), out = el.querySelector("#cOut");
          if (!q) return A.toast("Напишите вопрос");
          if (!A.ai.ready()) { out.innerHTML = A.ai.need(); return; }
          b.disabled = true; out.innerHTML = '<p class="muted">Думаю…</p>';
          const ctx = it.t + ". " + (it.short || "") + "\n" + (it.steps || []).map((s, i) => i + 1 + ". " + s.t).join("\n") + (it.rowsText ? "\nРяды: " + it.rowsText.join(" ") : "");
          try { out.innerHTML = A.ai.fmt(await A.ai.ask("Инструкция из приложения:\n" + ctx + "\n\nВопрос рукодельницы: " + q + "\nОтветь по-русски понятно и пошагово, как опытная мастерица; если вопрос про ошибку — как её найти и исправить.", { system: "Ты — опытная вязальщица и мастер украшений, объясняешь терпеливо и очень конкретно.", max: 1800 })); }
          catch (e) { out.innerHTML = A.ai.errHtml(e); }
          b.disabled = false;
        }
      });
    }
  });

  const itemHtml = (it) => {
    let h = "";
    if (it.rows) {
      h += '<div class="card"><h3>Как выглядит</h3><div class="cf-fabwrap">' + fabric(it.rows, { rep: it.rep || 2, wrep: it.wrep || 2 }) + '</div><p class="small muted">Раппорт — ' + it.rows[0].length + " " + A.plural(it.rows[0].length, "петля", "петли", "петель") + " × " + it.rows.length + " " + A.plural(it.rows.length, "ряд", "ряда", "рядов") + (it.plus ? " " + esc(it.plus) : "") + "</p></div>";
      h += '<div class="card"><h3>Схема</h3><div class="cf-fabwrap">' + chart(it.rows) + '</div><p class="small muted">Схема читается снизу вверх; нечётные (лицевые) ряды — справа налево, чётные (изнаночные) — слева направо. Номер ряда — справа.</p></div>';
    }
    h += '<div class="card"><h3>' + esc(it.t) + "</h3>" + (it.about ? '<p class="small">' + esc(it.about) + "</p>" : "") +
      (it.need ? '<p class="small"><b>Понадобится:</b> ' + it.need.map(esc).join(", ") + "</p>" : "") +
      (it.time ? '<p class="small muted">Время: ~' + it.time + " мин · сложность " + diff(it.lvl || 1) + "</p>" : "") + "</div>";
    if (it.rowsText) h += '<div class="card"><h3>Вязать так</h3><p class="small"><b>Набор:</b> ' + esc(it.cast) + "</p>" + it.rowsText.map((t) => '<p class="small">' + esc(t) + "</p>").join("") + "</div>";
    if (it.steps) h += '<div class="card"><h3>По шагам</h3>' + stepsHtml(it.steps) + "</div>";
    if (it.mistakes) h += '<div class="card"><h3>Частые ошибки</h3>' + it.mistakes.map((m) => '<p class="small"><b>' + esc(m[0]) + ".</b> " + esc(m[1]) + "</p>").join("") + "</div>";
    if (it.uses) h += '<div class="card tint"><p class="small"><b>Где использовать:</b> ' + esc(it.uses) + "</p></div>";
    h += '<div class="card"><h3>{{i:sparkles}} Спросить мастерицу (ИИ)</h3><textarea id="cQ" rows="2" placeholder="Например: у меня петли перекручиваются — что не так?"></textarea><button class="btn block" data-a="cAsk" style="margin-top:8px">Спросить</button><div id="cOut" class="ai-out" style="margin-top:8px"></div></div>';
    return h;
  };
})();
