/* Анализы крови: ввод результатов, расшифровка по референсам, динамика, индексы, чек-ап, отчёт для врача.
   Данные: коллекция labs = [{id, date, lab, note, vals: {код: число}}]. Свои нормы лаборатории — profile.labRefs. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN, fmtDate, fmtShort } = A;

  const ITEM = {}; A.LABS.forEach((x) => (ITEM[x.k] = x));
  const GROUP = {}; A.LAB_GROUPS.forEach((g) => (GROUP[g[0]] = g));

  /* ---------- пол, возраст, нормы ---------- */
  const body = () => A.db().profile.body || {};
  const sex = () => (body().sex === "m" ? "m" : "f");
  const age = () => +body().age || null;
  A.labRef = (it) => {
    const own = (A.db().profile.labRefs || {})[it.k];
    if (own && (own[0] != null || own[1] != null)) return own;
    const r = it.ref;
    if (typeof r === "function") return r(sex(), age());
    if (Array.isArray(r)) return r;
    return r ? r[sex()] || r.f : [null, null];
  };
  const dec = (it) => (it.dec != null ? it.dec : 0);
  const fv = (it, v) => fmtN(v, dec(it));
  const refText = (it) => { const [lo, hi] = A.labRef(it); return lo != null && hi != null ? fv(it, lo) + "–" + fv(it, hi) : hi != null ? "< " + fv(it, hi) : lo != null ? "> " + fv(it, lo) : "—"; };

  // Статус значения: уровень (ok / warn / bad), сторона (lo / hi / null), текст.
  A.labStatus = (it, v) => {
    const [lo, hi] = A.labRef(it), c = it.crit || [];
    let side = null, lvl = "ok";
    if (lo != null && v < lo) { side = "lo"; lvl = "warn"; }
    if (hi != null && v > hi) { side = "hi"; lvl = "warn"; }
    if ((c[0] != null && v < c[0]) || (c[1] != null && v > c[1])) lvl = "bad";
    const z = it.zones ? it.zones(v) : null;
    if (z && (z[0] === "bad" || (z[0] === "warn" && lvl === "ok"))) lvl = z[0];
    const word = lvl === "bad" && (c[0] != null && v < c[0] || c[1] != null && v > c[1]) ? "критично" : side === "lo" ? "ниже нормы" : side === "hi" ? "выше нормы" : lvl === "ok" ? "норма" : "внимание";
    return { lvl, side, word, zone: z ? z[1] : "", crit: word === "критично" };
  };
  const COL = { ok: "var(--good)", warn: "var(--warn)", bad: "var(--bad)" };
  const pill = (st) => '<span class="lpill" style="background:' + COL[st.lvl] + '">' + esc(st.word) + "</span>";

  /* ---------- данные ---------- */
  const sessions = () => A.col("labs").slice().sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
  const history = (k) => sessions().filter((s) => s.vals && s.vals[k] != null).map((s) => ({ d: s.date, v: s.vals[k], id: s.id })).reverse();
  const latest = () => { const o = {}; sessions().forEach((s) => Object.entries(s.vals || {}).forEach(([k, v]) => { if (v != null && !o[k]) o[k] = { v, d: s.date, id: s.id }; })); return o; };
  const prevOf = (k) => { const h = history(k); return h.length > 1 ? h[h.length - 2] : null; };

  /* ---------- графические элементы ---------- */
  // Шкала: зелёная зона нормы, маркер значения.
  A.labGauge = (it, v) => {
    let [lo, hi] = A.labRef(it);
    const a = lo != null ? lo : 0, b = hi != null ? hi : (lo || 1) * 2;
    const span = Math.max(b - a, Math.abs(b) * 0.1 || 1);
    const min = Math.min(lo != null ? a - span * 0.5 : 0, v), max = Math.max(b + span * 0.5, v);
    const x = (t) => ((t - min) / (max - min || 1)) * 100;
    const st = A.labStatus(it, v);
    return '<div class="lgauge"><div class="lg-ok" style="left:' + x(a).toFixed(1) + "%;width:" + Math.max(1, x(b) - x(a)).toFixed(1) + '%"></div><div class="lg-m" style="left:' + A.clamp(x(v), 0, 100).toFixed(1) + "%;background:" + COL[st.lvl] + '"></div></div>';
  };
  // Линия с полосой нормы: pts [{x, v}], band [lo, hi], extra линии-ориентиры [{v, label, color}].
  A.charts.band = (pts, band, opt = {}) => {
    const W = 320, H = opt.h || 150, pl = 34, pr = 8, pt = 10, pb = 20;
    const vals = pts.map((p) => p.v).filter((v) => v != null);
    if (!vals.length) return '<div class="muted small">Нет данных для графика</div>';
    const lines = opt.lines || [];
    // Шкала — по значениям; граница нормы попадает на ось, только если она рядом с данными.
    const vMin = Math.min(...vals), vMax = Math.max(...vals), span = Math.max(vMax - vMin, Math.abs(vMax) * 0.3, 1);
    const near = (x) => x != null && x >= vMin - span && x <= vMax + span;
    const pool = vals.concat(band ? band.filter(near) : []).concat(lines.map((l) => l.v).filter(near));
    let mn = Math.min(...pool), mx = Math.max(...pool);
    if (opt.min != null) mn = Math.min(mn, opt.min);
    const pad = (mx - mn || Math.abs(mx) || 1) * 0.12; mn -= pad; mx += pad;
    if (vMin >= 0 && mn < 0) mn = 0;
    const n = pts.length, X = (i) => pl + (W - pl - pr) * (n <= 1 ? 0.5 : i / (n - 1)), Y = (v) => pt + (H - pt - pb) * (1 - (v - mn) / (mx - mn));
    let s = '<svg class="chart" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' + esc(opt.title || "Динамика") + '">';
    if (band) { const y1 = Y(Math.min(band[1] != null ? band[1] : mx, mx)), y0 = Y(Math.max(band[0] != null ? band[0] : mn, mn)); s += '<rect x="' + pl + '" y="' + y1.toFixed(1) + '" width="' + (W - pl - pr) + '" height="' + Math.max(0, y0 - y1).toFixed(1) + '" fill="var(--good)" opacity=".13"/>'; }
    [0, 0.5, 1].forEach((k) => { const v = mn + (mx - mn) * k; s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + Y(v) + '" y2="' + Y(v) + '" class="grid"/><text x="' + (pl - 4) + '" y="' + (Y(v) + 3) + '" class="ax" text-anchor="end">' + esc(fmtN(v, mx - mn < 10 ? 1 : 0)) + "</text>"; });
    lines.filter((l) => l.v >= mn && l.v <= mx).forEach((l) => { s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + Y(l.v) + '" y2="' + Y(l.v) + '" stroke="' + l.color + '" stroke-dasharray="4 3" stroke-width="1.2"/><text x="' + (W - pr) + '" y="' + (Y(l.v) - 3) + '" class="ax" text-anchor="end" fill="' + l.color + '">' + esc(l.label) + "</text>"; });
    const every = Math.ceil(n / 6);
    pts.forEach((p, i) => { if (i % every === 0 || i === n - 1) s += '<text x="' + X(i) + '" y="' + (H - 5) + '" class="ax" text-anchor="middle">' + esc(p.x) + "</text>"; });
    (opt.series || [{ key: "v", color: opt.color || "var(--accent)" }]).forEach((se) => {
      let d = "", pen = false;
      pts.forEach((p, i) => { const v = p[se.key]; if (v == null) { pen = false; return; } d += (pen ? "L" : "M") + X(i).toFixed(1) + " " + Y(v).toFixed(1); pen = true; });
      s += '<path d="' + d + '" fill="none" stroke="' + se.color + '" stroke-width="2" stroke-linejoin="round"/>';
      pts.forEach((p, i) => { const v = p[se.key]; if (v != null) s += '<circle cx="' + X(i).toFixed(1) + '" cy="' + Y(v).toFixed(1) + '" r="3.2" fill="' + (p.c && se.key === "v" ? p.c : se.color) + '"><title>' + esc(p.x + ": " + fmtN(v, opt.dec || 1)) + "</title></circle>"; });
    });
    return s + "</svg>";
  };

  /* ---------- расчётные индексы ---------- */
  const indexValues = () => {
    const ss = sessions(), out = [];
    A.LAB_INDEX.forEach((ix) => {
      for (const s of ss) {
        const v = s.vals || {};
        if (!ix.need.every((k) => v[k] != null)) continue;
        if (ix.skip && ix.skip(v)) break;
        const x = ix.calc(v, sex(), age());
        if (x != null && isFinite(x)) out.push({ ix, x, d: s.date });
        break;
      }
    });
    return out;
  };

  /* ---------- распознавание текста из бланка ---------- */
  const LETTER = /[a-zа-яё]/i;
  A.parseLabText = (text) => {
    const found = {};
    const al = []; A.LABS.forEach((it) => it.al.forEach((a) => al.push([a.toLowerCase(), it.k])));
    al.sort((a, b) => b[0].length - a[0].length);
    String(text || "").split(/\r?\n|;/).forEach((raw) => {
      const line = raw.toLowerCase().replace(/ё/g, "е").replace(/25\s*-?\s*\(?\s*oh\s*\)?(?:\s*d[23]?(?![a-zа-я0-9.,]))?/gi, " витамин d ");
      for (const [a0, k] of al) {
        const a = a0.replace(/ё/g, "е");
        let i = line.indexOf(a);
        while (i >= 0 && ((i > 0 && LETTER.test(line[i - 1])) || LETTER.test(line[i + a.length] || ""))) i = line.indexOf(a, i + 1);
        if (i < 0) continue;
        const m = line.slice(i + a.length).match(/(-?\d+(?:[.,]\d+)?)/);
        if (m && found[k] == null) found[k] = parseFloat(m[1].replace(",", "."));
        break;
      }
    });
    return found;
  };

  /* ---------- редактор результатов ---------- */
  A.edit.labSession = (s, pre) => {
    const isNew = !s;
    s = s || { date: today(), lab: (sessions()[0] || {}).lab || "", vals: Object.assign({}, pre || {}) };
    const has = (g) => A.LABS.some((it) => it.g === g && s.vals[it.k] != null);
    let h = '<div class="form"><div class="fld"><label>Дата сдачи</label><input type="date" name="date" value="' + esc(s.date) + '"></div><div class="fld"><label>Лаборатория</label><input name="lab" value="' + esc(s.lab || "") + '" placeholder="Invitro, Гемотест…"></div></div>';
    h += '<p class="small muted">Заполните только то, что сдавали. Единицы — как в бланке; если лаборатория даёт другие единицы, выберите их рядом с полем.</p>';
    A.LAB_GROUPS.forEach((g, gi) => {
      const items = A.LABS.filter((it) => it.g === g[0] && (!it.sex || it.sex === sex()));
      h += '<details class="lgrp"' + (isNew ? (gi < 2 || has(g[0]) ? " open" : "") : has(g[0]) ? " open" : "") + "><summary>" + g[2] + " " + esc(g[1]) + '</summary><div class="lrows">';
      items.forEach((it) => {
        const v = s.vals[it.k];
        h += '<label class="lrow"><span>' + esc(it.n) + '<small>норма ' + esc(refText(it)) + "</small></span>" +
          '<input type="number" inputmode="decimal" step="any" data-k="' + it.k + '" value="' + (v != null ? v : "") + '">' +
          (it.conv ? '<select data-u="' + it.k + '"><option value="1">' + esc(it.u) + "</option>" + it.conv.map((c) => '<option value="' + c[1] + '">' + esc(c[0]) + "</option>").join("") + "</select>" : '<em>' + esc(it.u) + "</em>") + "</label>";
      });
      h += "</div></details>";
    });
    h += '<div class="form"><div class="fld full"><label>Заметка</label><textarea name="note" rows="2">' + esc(s.note || "") + "</textarea></div></div>";
    const buttons = [];
    if (!isNew) buttons.push({ label: "Удалить", cls: "danger ghost", onClick: () => { A.confirm("Удалить результаты от " + fmtDate(s.date) + "?", () => { A.remove("labs", s.id); A.closeSheet(); A.refresh(); }, "Удалить", true); return false; } });
    buttons.push({ label: "Сохранить", cls: "primary", onClick: (w) => {
      const vals = {};
      w.querySelectorAll("input[data-k]").forEach((i) => {
        if (i.value === "") return;
        let v = A.num(i.value, null); if (v == null) return;
        const u = w.querySelector('select[data-u="' + i.dataset.k + '"]');
        if (u && +u.value !== 1) v = v / +u.value;
        vals[i.dataset.k] = Math.round(v * 1000) / 1000;
      });
      if (!Object.keys(vals).length) { A.toast("Введите хотя бы один показатель"); return false; }
      s.date = w.querySelector('[name="date"]').value || today();
      s.lab = w.querySelector('[name="lab"]').value.trim();
      s.note = w.querySelector('[name="note"]').value.trim();
      s.vals = vals;
      A.upsert("labs", s);
      const bad = Object.entries(vals).filter(([k, v]) => ITEM[k] && A.labStatus(ITEM[k], v).lvl !== "ok").length;
      A.toast("Сохранено. " + (bad ? "Вне нормы: " + bad : "Все показатели в норме"));
      A.refresh();
    } });
    A.sheet(isNew ? "Новые результаты" : "Результаты · " + fmtShort(s.date), h, { buttons });
  };
  A.edit.labPaste = () => {
    A.sheet("Вставить из бланка", '<p class="small muted">Скопируйте текст результатов из PDF или письма лаборатории и вставьте сюда. Приложение найдёт знакомые показатели и числа — проверьте их перед сохранением.</p><textarea id="lpaste" rows="9" style="width:100%" placeholder="Гемоглобин 128 г/л&#10;Ферритин 18 нг/мл&#10;Глюкоза 5,4 ммоль/л"></textarea>', {
      buttons: [{ label: "Отмена", cls: "ghost" }, { label: "Распознать", cls: "primary", onClick: (w) => {
        const f = A.parseLabText(w.querySelector("#lpaste").value);
        const n = Object.keys(f).length;
        if (!n) { A.toast("Показатели не найдены — проверьте текст"); return false; }
        setTimeout(() => { A.toast("Найдено показателей: " + n); A.edit.labSession(null, f); }, 260);
      } }]
    });
  };
  const editRef = (it) => {
    const own = (A.db().profile.labRefs || {})[it.k] || [];
    A.formSheet("Нормы лаборатории · " + it.n, [
      { k: "lo", label: "Нижняя граница, " + it.u, type: "number", step: "any" },
      { k: "hi", label: "Верхняя граница, " + it.u, type: "number", step: "any" }
    ], { lo: own[0], hi: own[1] }, (o) => {
      const p = A.db().profile; p.labRefs = p.labRefs || {};
      if (o.lo == null && o.hi == null) delete p.labRefs[it.k]; else p.labRefs[it.k] = [o.lo, o.hi];
      A.save(); A.refresh();
    }, { pre: '<p class="small muted">Впишите интервал из вашего бланка — расшифровка будет опираться на него. Оставьте пустым, чтобы вернуть стандартный (' + esc(refText(Object.assign({}, it))) + ").</p>" });
  };

  /* ---------- экраны ---------- */
  const TABS = [["sum", "Сводка"], ["trend", "Динамика"], ["idx", "Индексы"], ["check", "Чек-ап"], ["ref", "Справочник"]];
  const line = (it, L, opts = {}) => {
    const st = A.labStatus(it, L.v), pv = prevOf(it.k);
    const arrow = pv ? (L.v > pv.v ? "↑" : L.v < pv.v ? "↓" : "→") : "";
    return '<div class="item lrowv" data-a="ana" data-k="' + it.k + '"><div class="tx"><b>' + esc(it.n) + "</b><small>норма " + esc(refText(it)) + " · " + fmtShort(L.d) + (opts.zone && st.zone ? " · " + esc(st.zone) : "") + "</small>" + A.labGauge(it, L.v) + '</div><div class="lval"><b class="num" style="color:' + COL[st.lvl] + '">' + fv(it, L.v) + (arrow ? ' <small class="muted">' + arrow + "</small>" : "") + "</b><small>" + esc(it.u) + "</small>" + pill(st) + "</div></div>";
  };

  function renderSum() {
    const L = latest(), keys = Object.keys(L).filter((k) => ITEM[k]);
    let h = '<div class="btns" style="margin-bottom:12px"><button class="btn primary" data-a="add">+ Результаты</button><button class="btn" data-a="paste">Вставить из бланка</button><button class="btn" data-a="report">Отчёт для врача</button></div>';
    if (!keys.length) {
      return h + '<div class="card tint"><h3>{{i:droplet}} Анализы крови</h3><p>Записывайте результаты анализов — приложение сравнит их с нормами по полу и возрасту, покажет, что вне нормы и почему, посчитает индексы (СКФ, HOMA-IR, FIB-4, насыщение трансферрина и другие) и построит динамику.</p><p class="small muted">Пол и возраст берутся из «Здоровье → Тело». Расшифровка — справочная и не заменяет врача.</p></div>' + prepCard();
    }
    const st = keys.map((k) => [k, A.labStatus(ITEM[k], L[k].v)]);
    const nOk = st.filter((x) => x[1].lvl === "ok").length, nW = st.filter((x) => x[1].lvl === "warn").length, nB = st.filter((x) => x[1].lvl === "bad").length;
    const last = sessions()[0];
    h += '<div class="card"><h3>{{i:clipboard-list}} Последние результаты</h3><div class="row" style="gap:14px">' + A.charts.donut([{ name: "в норме", v: nOk, color: "#5DAE7B" }, { name: "внимание", v: nW, color: "#F0A45B" }, { name: "отклонение", v: nB, color: "#D2555E" }], keys.length, "показателей") + '</div><p class="small muted">Последняя сдача: ' + fmtDate(last.date, { year: true }) + (last.lab ? " · " + esc(last.lab) : "") + " · всего визитов: " + sessions().length + "</p></div>";
    const crit = st.filter((x) => x[1].crit);
    if (crit.length) h += '<div class="card" style="border:2px solid var(--bad)"><h3 style="color:var(--bad)">{{i:triangle-alert}} Критические значения</h3><p>' + crit.map((x) => "<b>" + esc(ITEM[x[0]].n) + "</b>: " + fv(ITEM[x[0]], L[x[0]].v) + " " + esc(ITEM[x[0]].u)).join("<br>") + '</p><p class="small">Такие значения требуют обращения к врачу в ближайшее время; при плохом самочувствии — вызовите скорую (103 / 112).</p></div>';
    const att = st.filter((x) => x[1].lvl !== "ok").sort((a, b) => (a[1].lvl === "bad" ? -1 : 1) - (b[1].lvl === "bad" ? -1 : 1));
    if (att.length) {
      h += '<div class="card"><h3>{{i:target}} Требует внимания</h3>' + att.map(([k, s]) => { const it = ITEM[k], adv = s.side === "lo" ? it.lo : it.hi; return line(it, L[k], { zone: true }) + (adv && adv.d && adv.d !== "—" ? '<p class="small ladv"><b>Что делать:</b> ' + esc(adv.d) + "</p>" : ""); }).join("") + "</div>";
    }
    const iv = indexValues();
    if (iv.length) h += '<div class="card"><h3>{{i:chart-line}} Индексы</h3>' + iv.slice(0, 4).map((r) => { const z = r.ix.zone(r.x); return '<div class="item" data-a="tab" data-v="idx"><div class="tx"><b>' + esc(r.ix.n) + "</b><small>" + esc(z[1]) + '</small></div><b class="num" style="color:' + COL[z[0]] + '">' + fmtN(r.x, r.x < 10 ? 2 : 0) + "</b></div>"; }).join("") + "</div>";
    A.LAB_GROUPS.forEach((g) => {
      const ks = A.LABS.filter((it) => it.g === g[0] && L[it.k]);
      if (ks.length) h += '<div class="card"><h3>' + g[2] + " " + esc(g[1]) + "</h3>" + ks.map((it) => line(it, L[it.k])).join("") + "</div>";
    });
    h += '<div class="card"><h3>{{i:calendar-days}} История сдачи</h3>' + sessions().map((s) => { const v = Object.entries(s.vals || {}).filter(([k]) => ITEM[k]); const bad = v.filter(([k, x]) => A.labStatus(ITEM[k], x).lvl !== "ok").length; return '<div class="item" data-a="sess" data-id="' + s.id + '"><div class="tx"><b>' + fmtDate(s.date, { year: true }) + "</b><small>" + esc(s.lab || "лаборатория не указана") + " · показателей: " + v.length + (s.note ? " · " + esc(s.note) : "") + '</small></div><b class="num" style="color:' + (bad ? "var(--warn)" : "var(--good)") + '">' + (bad ? bad + " вне нормы" : "норма") + "</b></div>"; }).join("") + "</div>";
    return h;
  }
  const prepCard = () => '<div class="card"><h3>{{i:list-checks}} Как подготовиться к анализам</h3><ul class="small" style="padding-left:18px;margin:4px 0">' + A.LAB_PREP.map((x) => "<li>" + esc(x) + "</li>").join("") + "</ul></div>";

  function renderTrend(r) {
    const L = latest(), keys = A.LABS.filter((it) => history(it.k).length).map((it) => it.k);
    if (!keys.length) return A.empty("Динамика появится после первых результатов");
    const k = r.params.k && ITEM[r.params.k] ? r.params.k : keys.find((x) => A.labStatus(ITEM[x], L[x].v).lvl !== "ok") || keys[0];
    let h = '<div class="card"><div class="fld"><select data-c="trk">' + A.LAB_GROUPS.map((g) => { const ks = keys.filter((x) => ITEM[x].g === g[0]); return ks.length ? '<optgroup label="' + esc(g[1]) + '">' + ks.map((x) => '<option value="' + x + '"' + (x === k ? " selected" : "") + ">" + esc(ITEM[x].n) + " (" + history(x).length + ")</option>").join("") + "</optgroup>" : ""; }).join("") + "</select></div></div>";
    return h + trendCard(ITEM[k]);
  }
  const trendCard = (it) => {
    const hs = history(it.k);
    const pts = hs.map((p) => ({ x: fmtShort(p.d) + (hs.length > 6 ? "" : "." + p.d.slice(2, 4)), v: p.v, c: COL[A.labStatus(it, p.v).lvl] }));
    const s = A.statsOf(hs.map((p) => p.v));
    let h = '<div class="card"><h3>' + esc(it.n) + ", " + esc(it.u) + "</h3>" + A.charts.band(pts, A.labRef(it), { title: it.n, dec: dec(it) }) + '<p class="small muted">Зелёная полоса — норма ' + esc(refText(it)) + ".</p>";
    if (hs.length > 1) {
      const first = hs[0], last = hs[hs.length - 1], ch = last.v - first.v, pct = first.v ? (ch / first.v) * 100 : 0;
      h += '<div class="grid3"><div class="stat"><small>Изменение</small><b>' + (ch > 0 ? "+" : "") + fv(it, ch) + '</b><small>' + (pct > 0 ? "+" : "") + fmtN(pct, 0) + "%</small></div><div class=\"stat\"><small>Минимум</small><b>" + fv(it, s.min) + '</b></div><div class="stat"><small>Максимум</small><b>' + fv(it, s.max) + "</b></div></div>";
    }
    return h + "</div>";
  };

  function renderIdx() {
    const iv = indexValues(), got = new Set(iv.map((r) => r.ix.k));
    let h = '<div class="card tint"><p class="small">Индексы считаются из показателей одной сдачи. Для СКФ и FIB-4 нужен возраст в профиле тела' + (age() ? " (сейчас " + age() + ")" : " — <a href=\"#/health/body\">укажите</a>") + ".</p></div>";
    iv.forEach((r) => { const z = r.ix.zone(r.x); h += '<div class="card"><h3>' + esc(r.ix.n) + '<span class="sp"></span><b class="num" style="color:' + COL[z[0]] + '">' + fmtN(r.x, r.x < 10 ? 2 : 0) + (r.ix.u ? ' <small class="muted">' + esc(r.ix.u) + "</small>" : "") + '</b></h3><p><span class="lpill" style="background:' + COL[z[0]] + '">' + esc(z[1]) + '</span></p><p class="small muted">' + esc(r.ix.about) + " · " + fmtDate(r.d, { year: true }) + "</p></div>"; });
    const miss = A.LAB_INDEX.filter((ix) => !got.has(ix.k));
    if (miss.length) h += '<div class="card"><h3>Можно посчитать</h3>' + miss.map((ix) => '<div class="item"><div class="tx"><b>' + esc(ix.n) + "</b><small>нужно: " + esc(ix.need.map((k) => ITEM[k].n).join(", ")) + (ix.k === "tsat" ? " и ОЖСС или трансферрин" : "") + "</small></div></div>").join("") + "</div>";
    return h;
  }

  function renderCheck() {
    const L = latest(), t = today();
    let h = "";
    A.LAB_CHECKUP.forEach((c) => {
      const items = c.items.filter((k) => ITEM[k] && (!ITEM[k].sex || ITEM[k].sex === sex()));
      if (!items.length) return;
      const old = items.filter((k) => !L[k] || A.diffDays(L[k].d, t) > 365);
      h += '<div class="card"><h3>' + (old.length ? "{{i:bell}}" : "{{i:circle-check}}") + " " + esc(c.t) + '</h3><p class="small">' + esc(c.d) + '</p><div class="chips">' + items.map((k) => '<span class="tag" style="' + (old.includes(k) ? "" : "background:var(--sage2);color:#3C7A50") + '">' + esc(ITEM[k].n.split(" (")[0]) + (L[k] ? " · " + fmtShort(L[k].d) : " · не сдавали") + "</span>").join("") + "</div>" + (old.length ? '<p class="small muted" style="margin-top:8px">Давно не сдавали или нет данных: ' + old.length + "</p>" : "") + "</div>";
    });
    return h + prepCard();
  }

  function renderRef(r) {
    const q = (r.params.q || "").toLowerCase();
    let h = '<div class="card"><input type="search" data-c="q" data-live value="' + esc(r.params.q || "") + '" placeholder="Поиск: ферритин, ТТГ, АЛТ…" style="width:100%"></div>';
    A.LAB_GROUPS.forEach((g) => {
      const ks = A.LABS.filter((it) => it.g === g[0] && (!q || (it.n + " " + it.al.join(" ")).toLowerCase().includes(q)));
      if (ks.length) h += '<div class="card"><h3>' + g[2] + " " + esc(g[1]) + "</h3>" + ks.map((it) => '<div class="item" data-a="ana" data-k="' + it.k + '"><div class="tx"><b>' + esc(it.n) + "</b><small>" + esc(it.about) + '</small></div><small class="num muted" style="text-align:right">' + esc(refText(it)) + "<br>" + esc(it.u) + "</small></div>").join("") + "</div>";
    });
    return h;
  }

  function renderAnalyte(k) {
    const it = ITEM[k]; if (!it) return A.empty("Показатель не найден");
    const hs = history(k), L = hs[hs.length - 1];
    let h = "";
    if (L) {
      const st = A.labStatus(it, L.v);
      h += '<div class="card"><div class="row"><div class="big" style="color:' + COL[st.lvl] + '">' + fv(it, L.v) + ' <small style="font-size:15px" class="muted">' + esc(it.u) + "</small></div>" + pill(st) + '</div><div class="small muted">' + fmtDate(L.d, { year: true }) + " · норма " + esc(refText(it)) + "</div>" + A.labGauge(it, L.v) + (st.zone ? '<p class="small"><b>' + esc(st.zone) + "</b></p>" : "") +
        (it.conv ? '<p class="small muted">' + it.conv.map((c) => "= " + fmtN(L.v * c[1], 1) + " " + esc(c[0])).join(" · ") + "</p>" : "") + "</div>";
      if (hs.length > 1) h += trendCard(it);
    }
    h += '<div class="card"><h3>{{i:book-open}} О показателе</h3><p>' + esc(it.about) + "</p>";
    const st = L ? A.labStatus(it, L.v) : null;
    const blocks = st && st.side === "lo" ? [["lo", "Ниже нормы"], ["hi", "Выше нормы"]] : [["hi", "Выше нормы"], ["lo", "Ниже нормы"]];
    blocks.forEach(([s, t]) => { const x = it[s]; if (!x || (x.c === "—" && x.d === "—")) return; h += '<div class="ladvbox' + (st && st.side === s ? " cur" : "") + '"><b>' + t + (st && st.side === s ? " — ваш случай" : "") + "</b>" + (x.c && x.c !== "—" ? '<p class="small"><i>Возможные причины:</i> ' + esc(x.c) + "</p>" : "") + (x.d && x.d !== "—" ? '<p class="small"><i>Что делать:</i> ' + esc(x.d) + "</p>" : "") + "</div>"; });
    h += (it.src ? '<p class="small muted">Основание: ' + esc(it.src) + "</p>" : "") + '<button class="btn sm" data-a="ref" data-k="' + k + '">Нормы моей лаборатории</button></div>';
    if (hs.length) h += '<div class="card"><h3>История</h3>' + hs.slice().reverse().map((p) => { const s2 = A.labStatus(it, p.v); return '<div class="item" data-a="sess" data-id="' + p.id + '"><div class="tx"><b>' + fmtDate(p.d, { year: true }) + '</b></div><b class="num" style="color:' + COL[s2.lvl] + '">' + fv(it, p.v) + " " + esc(it.u) + "</b></div>"; }).join("") + "</div>";
    h += '<p class="small muted" style="padding:0 6px">Расшифровка — справочная информация по клиническим рекомендациям. Диагноз и лечение назначает врач.</p>';
    return h;
  }

  function renderReport() {
    const L = latest(), p = A.db().profile, keys = A.LABS.filter((it) => L[it.k]);
    let h = '<div class="btns" style="margin-bottom:10px"><button class="btn primary" data-a="print">Печать / PDF</button><button class="btn" data-a="csv">CSV</button></div>';
    h += '<div class="card"><h3>Сводка анализов для врача</h3><p class="small">' + esc(p.name || "") + " · " + (sex() === "m" ? "муж." : "жен.") + (age() ? ", " + age() + " лет" : "") + " · сформировано " + fmtDate(today(), { year: true }) + "</p>";
    h += '<div style="overflow-x:auto"><table class="tbl"><tr><th>Показатель</th><th class="r">Значение</th><th class="r">Норма</th><th class="r">Дата</th><th class="r">Пред.</th></tr>' + keys.map((it) => { const st = A.labStatus(it, L[it.k].v), pv = prevOf(it.k); return "<tr><td>" + esc(it.n) + '</td><td class="r" style="color:' + COL[st.lvl] + ';font-weight:600">' + fv(it, L[it.k].v) + " " + esc(it.u) + (st.side ? (st.side === "hi" ? " ↑" : " ↓") : "") + '</td><td class="r">' + esc(refText(it)) + '</td><td class="r">' + fmtShort(L[it.k].d) + '</td><td class="r">' + (pv ? fv(it, pv.v) : "—") + "</td></tr>"; }).join("") + "</table></div>";
    const iv = indexValues();
    if (iv.length) h += '<p class="small" style="margin-top:10px"><b>Индексы:</b> ' + iv.map((r) => esc(r.ix.n) + " " + fmtN(r.x, r.x < 10 ? 2 : 0) + " (" + esc(r.ix.zone(r.x)[1]) + ")").join("; ") + "</p>";
    return h + "</div>";
  }

  A.view("labs", {
    title: (r) => (r.args[0] === "a" && ITEM[r.args[1]] ? ITEM[r.args[1]].n : r.args[0] === "report" ? "Отчёт для врача" : "Анализы крови"), tab: "more",
    render(el, r) {
      if (r.args[0] === "a") return renderAnalyte(r.args[1]);
      if (r.args[0] === "report") return renderReport();
      const tab = r.args[0] || "sum";
      const body = tab === "trend" ? renderTrend(r) : tab === "idx" ? renderIdx() : tab === "check" ? renderCheck() : tab === "ref" ? renderRef(r) : renderSum();
      return A.seg(TABS, tab, "tab") + body;
    },
    actions(box) { box.innerHTML = '<button class="icon-btn" data-add aria-label="Добавить результаты">＋</button>'; box.querySelector("[data-add]").onclick = () => A.edit.labSession(); },
    bind(el, r) {
      A.bind(el, {
        tab(b) { A.go("labs/" + b.dataset.v, true); },
        add() { A.edit.labSession(); }, paste() { A.edit.labPaste(); }, report() { A.go("labs/report"); },
        ana(b) { A.go("labs/a/" + b.dataset.k); },
        sess(b) { const s = A.byId("labs", b.dataset.id); if (s) A.edit.labSession(s); },
        ref(b) { editRef(ITEM[b.dataset.k]); },
        trk(s) { A.go("labs/trend?k=" + s.value, true); },
        q(i) { clearTimeout(A._lq); A._lq = setTimeout(() => { A.go("labs/ref?q=" + encodeURIComponent(i.value), true); setTimeout(() => { const x = document.querySelector('[data-c="q"]'); if (x) { x.focus(); x.setSelectionRange(x.value.length, x.value.length); } }, 30); }, 350); },
        print() { A.print("Анализы — отчёт для врача"); },
        csv() { const rows = []; sessions().forEach((s) => Object.entries(s.vals || {}).forEach(([k, v]) => { const it = ITEM[k]; if (it) rows.push({ date: s.date, lab: s.lab, test: it.n, value: v, unit: it.u, ref: refText(it), status: A.labStatus(it, v).word }); })); A.download("analizy-" + today() + ".csv", "text/csv", A.csv(rows, ["date", "lab", "test", "value", "unit", "ref", "status"])); }
      });
    }
  });
})();
