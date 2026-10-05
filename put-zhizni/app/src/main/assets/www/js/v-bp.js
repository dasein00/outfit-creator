/* Давление и пульс: дневник измерений по протоколу, классификации ESH/ESC/AHA, аналитика,
   7-дневный домашний мониторинг, связь с образом жизни, советы с доказанным эффектом.
   Данные: коллекция bp = [{id, date, time, sys, dia, pulse, reads:[[s,d,p]], arm, pos, proto, sym:[], ctx:[], meds, note}]. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN, fmtDate, fmtShort, addDays } = A;
  A.edit = A.edit || {};

  /* ---------- классификации ---------- */
  // Категория — по худшему из двух чисел.
  A.BP_STD = {
    esh: { name: "ESH 2023 / РКО", note: "Европейское общество гипертонии 2023 и клинические рекомендации Минздрава РФ «Артериальная гипертензия у взрослых».",
      cats: [
        { k: "opt", n: "Оптимальное", c: "#3C9A64", s: 0, d: 0 },
        { k: "norm", n: "Нормальное", c: "#6DB577", s: 120, d: 80 },
        { k: "hnorm", n: "Высокое нормальное", c: "#C9C25B", s: 130, d: 85 },
        { k: "g1", n: "АГ 1 степени", c: "#F0A45B", s: 140, d: 90 },
        { k: "g2", n: "АГ 2 степени", c: "#E2703F", s: 160, d: 100 },
        { k: "g3", n: "АГ 3 степени", c: "#D2453E", s: 180, d: 110 }] },
    esc: { name: "ESC 2024", note: "Европейское общество кардиологов 2024: три категории и целевое систолическое 120–129 мм рт. ст. для большинства леченых пациентов.",
      cats: [
        { k: "nonel", n: "Неповышенное", c: "#3C9A64", s: 0, d: 0 },
        { k: "elev", n: "Повышенное", c: "#F0C05B", s: 120, d: 70 },
        { k: "htn", n: "Гипертензия", c: "#D2453E", s: 140, d: 90 }] },
    aha: { name: "ACC/AHA 2017", note: "Американская коллегия кардиологии и Американская ассоциация сердца, 2017 (подтверждено в 2025).",
      cats: [
        { k: "norm", n: "Нормальное", c: "#3C9A64", s: 0, d: 0 },
        { k: "elev", n: "Повышенное", c: "#C9C25B", s: 120, d: 0 },
        { k: "st1", n: "Гипертензия 1 стадии", c: "#F0A45B", s: 130, d: 80 },
        { k: "st2", n: "Гипертензия 2 стадии", c: "#E2703F", s: 140, d: 90 },
        { k: "crisis", n: "Тяжёлое повышение", c: "#B3261E", s: 181, d: 121 }] }
  };
  const stdKey = () => A.db().profile.bpStd || "esh";
  const STD = () => A.BP_STD[stdKey()];
  A.bpCat = (s, d) => {
    const cats = STD().cats;
    let i = 0;
    cats.forEach((c, j) => { if (s >= c.s && c.s > 0 || d >= c.d && c.d > 0) i = Math.max(i, j); });
    return cats[i];
  };
  // Изолированная систолическая гипертензия (ESH): САД ≥ 140 при ДАД < 90.
  const isISH = (s, d) => s >= 140 && d < 90;
  const emergency = (s, d) => s >= 180 || d >= 120;
  const hypo = (s, d) => s < 90 || d < 60;
  const target = () => { const t = A.db().profile.bpTarget || {}; return { s: t.s || 135, d: t.d || 85 }; };

  /* ---------- данные ---------- */
  const all = () => A.col("bp").slice().sort((a, b) => ((a.date + (a.time || "")) < (b.date + (b.time || "")) ? -1 : 1));
  const inDays = (n) => { const from = addDays(today(), -(n - 1)); return all().filter((x) => x.date >= from); };
  const hr = (x) => +(x.time || "12:00").slice(0, 2);
  const part = (x) => { const h = hr(x); return h >= 4 && h < 12 ? "m" : h >= 12 && h < 18 ? "d" : h >= 18 ? "e" : "n"; };
  const mean = (a) => (a.length ? a.reduce((s, v) => s + v, 0) / a.length : null);
  const sd = (a) => { if (a.length < 2) return null; const m = mean(a); return Math.sqrt(a.reduce((s, v) => s + (v - m) * (v - m), 0) / (a.length - 1)); };
  const arv = (a) => (a.length < 2 ? null : mean(a.slice(1).map((v, i) => Math.abs(v - a[i]))));
  const r0 = (v) => (v == null ? "—" : Math.round(v));
  const bpTxt = (s, d) => (s == null ? "—" : Math.round(s) + "/" + Math.round(d));
  A.bpLast = () => { const a = all(); return a[a.length - 1] || null; };

  /* ---------- ввод ---------- */
  const SYM = ["головная боль", "головокружение", "шум в ушах", "мушки перед глазами", "тошнота", "боль в груди", "одышка", "сердцебиение", "перебои", "слабость", "онемение / слабость в руке или ноге", "нарушение речи"];
  const CTX = ["после сна", "после кофе", "после нагрузки", "стресс", "после еды", "после алкоголя", "недосып", "болею"];
  const RED = ["боль в груди", "одышка", "онемение / слабость в руке или ноге", "нарушение речи"];
  const PROTO = ["5 минут спокойно посидел(а)", "30 минут без кофе, курения и нагрузки", "мочевой пузырь пуст", "сижу, спина с опорой, ноги на полу", "рука на столе, манжета на уровне сердца", "не разговариваю во время измерения"];

  A.edit.bp = (x) => {
    const isNew = !x;
    x = x || { date: today(), time: A.nowHM(), reads: [[]], arm: A.db().profile.bpArm || "L", pos: "sit", sym: [], ctx: [], meds: false };
    const reads = x.reads && x.reads.length ? x.reads : [[x.sys, x.dia, x.pulse]];
    const row = (r, i) => '<div class="bprd" data-i="' + i + '"><span class="small muted">' + (i + 1) + '</span><input type="number" inputmode="numeric" placeholder="верхнее" data-r="s" value="' + (r[0] ?? "") + '"><span>/</span><input type="number" inputmode="numeric" placeholder="нижнее" data-r="d" value="' + (r[1] ?? "") + '"><input type="number" inputmode="numeric" placeholder="пульс" data-r="p" value="' + (r[2] ?? "") + '"></div>';
    let h = '<div class="form"><div class="fld"><label>Дата</label><input type="date" name="date" value="' + x.date + '"></div><div class="fld"><label>Время</label><input type="time" name="time" value="' + esc(x.time || "") + '"></div></div>';
    h += '<label class="small muted" style="display:block;margin:8px 0 4px">Измерения: верхнее / нижнее, мм рт. ст., и пульс, уд/мин</label><div id="bpreads">' + reads.map(row).join("") + '</div><button type="button" class="btn sm" id="bpmore">+ ещё измерение через 1 минуту</button><p class="small muted">ESH и AHA: делайте 2–3 измерения с интервалом 1–2 минуты — в дневник запишется среднее.</p>';
    h += A.formHtml([
      { k: "arm", label: "Рука", type: "chips", opts: [["L", "левая"], ["R", "правая"]] },
      { k: "pos", label: "Положение", type: "chips", opts: [["sit", "сидя"], ["stand", "стоя"], ["lie", "лёжа"]] },
      { k: "proto", label: "Протокол измерения", type: "multi", opts: PROTO },
      { k: "sym", label: "Самочувствие", type: "multi", opts: SYM },
      { k: "ctx", label: "Обстоятельства", type: "multi", opts: CTX },
      { k: "meds", label: "", type: "check", text: "лекарства от давления приняты" },
      { k: "note", label: "Заметка", type: "textarea", rows: 2 }
    ], Object.assign({}, x, { proto: x.proto === true ? PROTO : Array.isArray(x.proto) ? x.proto : [] }));
    const buttons = [];
    if (!isNew) buttons.push({ label: "Удалить", cls: "danger ghost", onClick: () => { A.confirm("Удалить измерение?", () => { A.remove("bp", x.id); A.closeSheet(); A.refresh(); }, "Удалить", true); return false; } });
    buttons.push({ label: "Сохранить", cls: "primary", onClick: (w) => {
      const rs = [...w.querySelectorAll(".bprd")].map((r) => ["s", "d", "p"].map((k) => { const v = r.querySelector('[data-r="' + k + '"]').value; return v === "" ? null : Math.round(A.num(v)); })).filter((r) => r[0] != null && r[1] != null);
      if (!rs.length) { A.toast("Введите верхнее и нижнее давление"); return false; }
      if (rs.some((r) => r[0] < 50 || r[0] > 300 || r[1] < 30 || r[1] > 200 || r[1] >= r[0])) { A.toast("Проверьте значения: верхнее 50–300, нижнее 30–200 и меньше верхнего"); return false; }
      const f = A.readForm(w, [{ k: "arm", type: "chips" }, { k: "pos", type: "chips" }, { k: "proto", type: "multi" }, { k: "sym", type: "multi" }, { k: "ctx", type: "multi" }, { k: "meds", type: "check" }, { k: "note" }]);
      const ps = rs.map((r) => r[2]).filter((v) => v != null);
      Object.assign(x, f, {
        date: w.querySelector('[name="date"]').value || today(), time: w.querySelector('[name="time"]').value || A.nowHM(), reads: rs,
        sys: Math.round(mean(rs.map((r) => r[0]))), dia: Math.round(mean(rs.map((r) => r[1]))), pulse: ps.length ? Math.round(mean(ps)) : null
      });
      x.proto = f.proto.length === PROTO.length ? true : f.proto;
      A.db().profile.bpArm = x.arm;
      A.upsert("bp", x);
      A.refresh();
      const c = A.bpCat(x.sys, x.dia), red = (x.sym || []).some((s) => RED.includes(s));
      if (emergency(x.sys, x.dia) && red) setTimeout(() => alarm(x, true), 250);
      else if (emergency(x.sys, x.dia)) setTimeout(() => alarm(x, false), 250);
      else A.toast(bpTxt(x.sys, x.dia) + " — " + c.n.toLowerCase());
    } });
    const w = A.sheet(isNew ? "Новое измерение" : "Измерение · " + fmtShort(x.date), h, { buttons });
    A.wireForm(w);
    w.querySelector("#bpmore").onclick = () => { const box = w.querySelector("#bpreads"); const n = box.children.length; if (n >= 3) { A.toast("Достаточно трёх измерений"); return; } box.insertAdjacentHTML("beforeend", row([], n)); };
  };
  const alarm = (x, urgent) => {
    A.sheet(urgent ? "Срочно: вызовите скорую" : "Очень высокое давление",
      urgent ? '<p style="color:var(--bad);font-weight:600">' + bpTxt(x.sys, x.dia) + ' с боль в груди, одышкой или неврологическими симптомами — признак гипертонического криза с поражением органов.</p><p><b>Звоните 103 или 112.</b> Сядьте, не принимайте новых лекарств без указания врача, не садитесь за руль.</p>'
        : "<p>" + bpTxt(x.sys, x.dia) + " — значение ≥ 180/120. Сядьте и спокойно перемерьте через 5 минут.</p><p>Если снова так же высоко без симптомов — свяжитесь с врачом сегодня (AHA: «тяжёлое повышение без поражения органов»). Если появятся боль в груди, одышка, слабость или онемение, нарушение речи или зрения — <b>сразу 103 / 112</b>.</p>",
      { buttons: [{ label: "Понятно", cls: "primary" }] });
  };

  /* ---------- графики ---------- */
  // Точки на поле категорий: x — нижнее, y — верхнее.
  const zoneChart = (pts) => {
    const W = 320, H = 220, pl = 30, pb = 22, pt = 6, pr = 6, xs = [40, 130], ys = [70, 200];
    const X = (v) => pl + (W - pl - pr) * (A.clamp(v, xs[0], xs[1]) - xs[0]) / (xs[1] - xs[0]), Y = (v) => pt + (H - pt - pb) * (1 - (A.clamp(v, ys[0], ys[1]) - ys[0]) / (ys[1] - ys[0]));
    let s = '<svg class="chart" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="Измерения на поле категорий">';
    // Слои от лёгкой категории к тяжёлой: каждая следующая перекрывает свою область «верхнее ≥ s или нижнее ≥ d».
    const layers = STD().cats.slice();
    layers.forEach((c, i) => {
      if (i === 0) { s += '<rect x="' + pl + '" y="' + pt + '" width="' + (W - pl - pr) + '" height="' + (H - pt - pb) + '" fill="' + c.c + '" opacity=".2"/>'; return; }
      const xd = c.d > 0 ? X(c.d) : W - pr, ys0 = c.s > 0 ? Y(c.s) : H - pb;
      // область, где s ≥ c.s ИЛИ d ≥ c.d
      const d = "M" + pl + " " + pt + "H" + (W - pr) + "V" + (H - pb) + "H" + xd + "V" + ys0 + "H" + pl + "Z";
      s += '<path d="' + d + '" fill="var(--card)"/><path d="' + d + '" fill="' + c.c + '" opacity=".2"/>';
    });
    [60, 80, 100, 120].forEach((v) => (s += '<text x="' + X(v) + '" y="' + (H - 6) + '" class="ax" text-anchor="middle">' + v + "</text>"));
    [80, 100, 120, 140, 160, 180].forEach((v) => (s += '<text x="' + (pl - 4) + '" y="' + (Y(v) + 3) + '" class="ax" text-anchor="end">' + v + "</text>"));
    pts.forEach((p, i) => (s += '<circle cx="' + X(p.dia).toFixed(1) + '" cy="' + Y(p.sys).toFixed(1) + '" r="' + (i === pts.length - 1 ? 5 : 3) + '" fill="' + (i === pts.length - 1 ? "var(--ink)" : A.bpCat(p.sys, p.dia).c) + '" stroke="#fff" stroke-width="1"><title>' + esc(fmtShort(p.date) + " " + (p.time || "") + ": " + bpTxt(p.sys, p.dia)) + "</title></circle>"));
    s += '<text x="' + (W - pr - 2) + '" y="' + (H - pb - 4) + '" class="ax" text-anchor="end">нижнее →</text><text x="' + (pl + 2) + '" y="' + (pt + 9) + '" class="ax">верхнее</text>';
    return s + "</svg>";
  };
  // Тепловая карта: дни недели × время суток, цвет — среднее верхнее.
  const heat = (list) => {
    const P = [["m", "утро"], ["d", "день"], ["e", "вечер"], ["n", "ночь"]];
    let h = '<div class="bpheat"><div></div>' + A.DOW.map((d) => "<b>" + d + "</b>").join("");
    P.forEach(([k, n]) => {
      h += "<b>" + n + "</b>";
      for (let w = 1; w <= 7; w++) {
        const a = list.filter((x) => part(x) === k && A.dow(x.date) === w);
        const m = mean(a.map((x) => x.sys)), md = mean(a.map((x) => x.dia));
        h += '<span title="' + esc(n + ", " + A.DOW[w - 1] + (a.length ? ": " + bpTxt(m, md) + " (" + a.length + ")" : "")) + '" style="background:' + (m ? A.bpCat(m, md).c : "var(--bg2)") + ";opacity:" + (m ? 0.35 + Math.min(a.length, 4) * 0.16 : 1) + '">' + (m ? Math.round(m) : "") + "</span>";
      }
    });
    return h + "</div>";
  };

  /* ---------- аналитика ---------- */
  const stats = (list) => {
    const S = list.map((x) => x.sys), D = list.map((x) => x.dia), P = list.map((x) => x.pulse).filter((v) => v != null);
    const m = list.filter((x) => part(x) === "m"), e = list.filter((x) => part(x) === "e");
    const t = target();
    return {
      n: list.length, s: mean(S), d: mean(D), p: mean(P), sdS: sd(S), cvS: S.length > 1 ? sd(S) / mean(S) * 100 : null, arvS: arv(S),
      ms: mean(m.map((x) => x.sys)), md: mean(m.map((x) => x.dia)), es: mean(e.map((x) => x.sys)), ed: mean(e.map((x) => x.dia)), nm: m.length, ne: e.length,
      pp: mean(list.map((x) => x.sys - x.dia)), map: mean(list.map((x) => x.dia + (x.sys - x.dia) / 3)),
      inT: list.length ? list.filter((x) => x.sys < t.s && x.dia < t.d).length / list.length * 100 : null,
      maxS: S.length ? Math.max(...S) : null, minS: S.length ? Math.min(...S) : null, hiP: P.filter((v) => v > 100).length, loP: P.filter((v) => v < 50).length
    };
  };
  // Связь давления с данными приложения: средние верхнего в днях «с» и «без» фактора.
  const lifestyle = (list) => {
    const byDay = {}; list.forEach((x) => (byDay[x.date] = byDay[x.date] || []).push(x.sys));
    const days = Object.keys(byDay), p = A.db().profile, out = [];
    const cmp = (name, test, yes, no) => {
      const a = [], b = []; days.forEach((d) => { const r = test(d); if (r === true) a.push(mean(byDay[d])); else if (r === false) b.push(mean(byDay[d])); });
      if (a.length >= 3 && b.length >= 3) out.push({ name, yes, no, a: mean(a), b: mean(b), na: a.length, nb: b.length });
    };
    cmp("Сон", (d) => { const h = A.sleepH ? A.sleepH(d) : null; return h == null ? null : h < 6.5; }, "сон < 6,5 ч", "сон ≥ 6,5 ч");
    cmp("Шаги", (d) => { const s = (A.dayGet(d) || {}).steps; return s == null ? null : s < (p.stepsGoal || 8000); }, "шагов меньше цели", "цель по шагам выполнена");
    cmp("Стресс", (d) => { const s = (A.dayGet(d) || {}).stress; return s == null ? null : s >= 6; }, "стресс ≥ 6", "стресс < 6");
    cmp("Тренировка", (d) => (A.col("workouts").some((w) => w.date === d) ? false : true), "без тренировки", "день с тренировкой");
    cmp("Кофе", (d) => { const a = list.filter((x) => x.date === d); return a.some((x) => (x.ctx || []).includes("после кофе")) ? true : a.length ? false : null; }, "измерение после кофе", "без кофе перед замером");
    return out;
  };
  const weightLink = (list) => {
    const pts = list.map((x) => ({ w: A.lastWeight ? A.lastWeight(x.date) : null, s: x.sys })).filter((x) => x.w != null);
    if (pts.length < 8) return null;
    const mw = mean(pts.map((x) => x.w)), ms = mean(pts.map((x) => x.s));
    let num = 0, dw = 0, ds = 0; pts.forEach((x) => { num += (x.w - mw) * (x.s - ms); dw += (x.w - mw) ** 2; ds += (x.s - ms) ** 2; });
    if (!dw || !ds) return null;
    return { r: num / Math.sqrt(dw * ds), slope: num / dw, n: pts.length };
  };

  /* ---------- 7-дневный протокол ESH ---------- */
  const protoState = () => A.db().profile.bpProto || null;
  const protoView = () => {
    const st = protoState();
    let h = '<div class="card tint"><h3>{{i:calendar-days}} Домашний мониторинг: 7 дней</h3><p class="small">Протокол ESH 2023 и Минздрава РФ: 7 дней подряд, утром (до еды и лекарств) и вечером (перед ужином) по 2 измерения с интервалом 1–2 минуты. Первый день не учитывается. Гипертензия по домашним измерениям — среднее ≥ 135/85.</p>';
    if (!st) return h + '<button class="btn primary" data-a="protoStart">Начать сегодня</button></div>';
    const days = A.range(st.start, addDays(st.start, 6)), list = all().filter((x) => x.date >= st.start && x.date <= days[6]);
    h += '<div class="bpproto"><b></b><b>утро</b><b>вечер</b>' + days.map((d, i) => { const m = list.filter((x) => x.date === d && part(x) === "m"), e = list.filter((x) => x.date === d && part(x) !== "m"); const c = (a) => (a.length ? '<span style="color:' + A.bpCat(mean(a.map((x) => x.sys)), mean(a.map((x) => x.dia))).c + '">' + bpTxt(mean(a.map((x) => x.sys)), mean(a.map((x) => x.dia))) + "</span>" : d <= today() ? '<span class="muted">—</span>' : ""); return "<b>" + (i + 1) + " · " + fmtShort(d) + (i === 0 ? '<small class="muted"> пробный</small>' : "") + "</b>" + c(m) + c(e); }).join("") + "</div>";
    const use = list.filter((x) => x.date > st.start), done = today() > days[6] || use.length >= 12;
    if (use.length) {
      const s = mean(use.map((x) => x.sys)), d = mean(use.map((x) => x.dia)), high = s >= 135 || d >= 85;
      h += '<div class="grid2" style="margin-top:10px"><div class="stat"><small>Среднее (дни 2–7)</small><b>' + bpTxt(s, d) + '</b></div><div class="stat"><small>Измерений учтено</small><b>' + use.length + " / 12</b></div></div>";
      h += '<p class="small" style="margin-top:8px"><b>' + (done ? "Итог: " : "Пока: ") + (high ? "домашнее давление повышено (≥ 135/85)" : s >= 120 || d >= 70 ? "в пределах нормы для домашних измерений, но выше оптимума (ESC 2024: повышенное 120–134/70–84)" : "нормальное домашнее давление") + ".</b> " + (high && done ? "Покажите этот результат врачу: он подтверждает гипертензию надёжнее, чем измерение в кабинете." : "") + "</p>";
    }
    return h + '<div class="btns"><button class="btn sm" data-a="protoStart">Начать заново</button><button class="btn sm ghost" data-a="protoStop">Завершить</button></div></div>';
  };

  /* ---------- экраны ---------- */
  const TABS = [["sum", "Обзор"], ["an", "Аналитика"], ["log", "Дневник"], ["proto", "Протокол"], ["tips", "Советы"]];
  const per = (r) => +(r.params.per || 30);
  const perSeg = (r) => A.seg([["7", "7 дн"], ["30", "30 дн"], ["90", "90 дн"], ["365", "Год"]], String(per(r)), "per");
  const catBadge = (s, d) => { const c = A.bpCat(s, d); return '<span class="lpill" style="background:' + c.c + '">' + esc(c.n) + (isISH(s, d) && stdKey() === "esh" ? " · ИСАГ" : "") + "</span>"; };

  function renderSum(r) {
    const L = A.bpLast(), list = inDays(per(r)), st = stats(list), t = target();
    let h = '<button class="btn primary block" data-a="add" style="margin-bottom:12px">+ Измерить давление</button>';
    if (!L) return h + '<div class="card tint"><h3>{{i:heart}} Давление и пульс</h3><p>Записывайте измерения — приложение определит категорию по выбранной классификации, посчитает средние утром и вечером, вариабельность, пульсовое давление, покажет связь давления со сном, шагами и стрессом и проведёт 7-дневный протокол домашнего мониторинга.</p></div>' + techCard() + stdCard();
    const red = emergency(L.sys, L.dia);
    h += '<div class="card"' + (red ? ' style="border:2px solid var(--bad)"' : "") + '><div class="row"><div class="grow"><div class="small muted">' + fmtDate(L.date) + " · " + esc(L.time || "") + (L.reads && L.reads.length > 1 ? " · среднее из " + L.reads.length : "") + '</div><div class="big">' + bpTxt(L.sys, L.dia) + ' <small style="font-size:14px" class="muted">мм рт. ст.</small></div>' + catBadge(L.sys, L.dia) + '</div><div style="text-align:center"><div class="big" style="font-size:24px;color:var(--rose)">' + (L.pulse || "—") + '</div><small class="muted">пульс</small></div></div>' +
      (red ? '<p class="small" style="color:var(--bad)"><b>≥ 180/120.</b> Перемерьте через 5 минут. При боли в груди, одышке, слабости или онемении, нарушении речи — 103 / 112.</p>' : hypo(L.sys, L.dia) && (L.sym || []).length ? '<p class="small" style="color:var(--warn)">Низкое давление с симптомами — сядьте или лягте, выпейте воды; повторяющиеся эпизоды обсудите с врачом.</p>' : "") + "</div>";
    h += perSeg(r);
    if (list.length) {
      const hi = st.s >= t.s || st.d >= t.d;
      h += '<div class="card"><h3>{{i:activity}} Среднее за период</h3><div class="row"><div class="big">' + bpTxt(st.s, st.d) + '</div><div class="grow">' + catBadge(st.s, st.d) + '<div class="small muted">' + list.length + " измерений · пульс " + r0(st.p) + "</div></div></div>" +
        '<div class="grid3" style="margin-top:10px"><div class="stat"><small>Утро</small><b>' + (st.nm ? bpTxt(st.ms, st.md) : "—") + '</b></div><div class="stat"><small>Вечер</small><b>' + (st.ne ? bpTxt(st.es, st.ed) : "—") + '</b></div><div class="stat"><small>В цели &lt; ' + t.s + "/" + t.d + "</small><b>" + fmtN(st.inT, 0) + "%</b></div></div>" +
        '<p class="small" style="margin-top:8px">' + (hi ? "Среднее выше цели " + t.s + "/" + t.d + ". " + (list.length >= 12 ? "Покажите дневник врачу." : "Наберите больше измерений или пройдите 7-дневный протокол.") : "Среднее в пределах цели.") + "</p></div>";
      h += '<div class="card"><h3>Динамика</h3>' + A.charts.band(daily(list), [null, t.s], { series: [{ key: "s", color: "#D2555E" }, { key: "d", color: "#7DB0D6" }], lines: [{ v: t.d, label: "цель нижнего " + t.d, color: "#7DB0D6" }], title: "Давление" }) + '<div class="legend"><span><i style="background:#D2555E"></i>верхнее</span><span><i style="background:#7DB0D6"></i>нижнее</span><span><i style="background:var(--good);opacity:.4"></i>ниже цели ' + t.s + "</span></div></div>";
      const cnt = {}; list.forEach((x) => { const c = A.bpCat(x.sys, x.dia); cnt[c.k] = (cnt[c.k] || 0) + 1; });
      h += '<div class="card"><h3>Категории измерений</h3>' + A.charts.donut(STD().cats.filter((c) => cnt[c.k]).map((c) => ({ name: c.n, v: cnt[c.k], color: c.c })), list.length, "измерений") + '<p class="small muted">' + esc(STD().name) + ' · <a href="#/bp/tips">сменить классификацию</a></p></div>';
    } else h += A.empty("За выбранный период измерений нет");
    return h;
  }
  const daily = (list) => {
    const by = {}; list.forEach((x) => (by[x.date] = by[x.date] || []).push(x));
    return Object.keys(by).sort().map((d) => ({ x: fmtShort(d), s: mean(by[d].map((x) => x.sys)), d: mean(by[d].map((x) => x.dia)), p: mean(by[d].map((x) => x.pulse).filter((v) => v != null)), v: mean(by[d].map((x) => x.sys)) }));
  };

  function renderAn(r) {
    const list = inDays(per(r)), st = stats(list);
    let h = perSeg(r);
    if (list.length < 2) return h + A.empty("Для аналитики нужно хотя бы несколько измерений");
    h += '<div class="card"><h3>{{i:radar}} Поле категорий</h3>' + zoneChart(list) + '<div class="legend">' + STD().cats.map((c) => '<span><i style="background:' + c.c + '"></i>' + esc(c.n) + "</span>").join("") + '</div><p class="small muted">Каждая точка — измерение; чёрная — последнее. Категория определяется по худшему из двух чисел.</p></div>';
    h += '<div class="card"><h3>Показатели</h3><div class="grid2">' +
      stat("Пульсовое давление", fmtN(st.pp, 0) + " мм", st.pp >= 60 ? "≥ 60 — признак жёсткости артерий (ESH), особенно после 60 лет" : "норма < 60") +
      stat("Среднее АД", fmtN(st.map, 0) + " мм", "нижнее + ⅓ пульсового; норма 70–100") +
      stat("Вариабельность (SD)", fmtN(st.sdS, 1) + " мм", st.sdS > 15 ? "> 15 — высокая: связана с риском инсульта" : "стабильно") +
      stat("Средний скачок (ARV)", fmtN(st.arvS, 1) + " мм", "разница между соседними измерениями") +
      stat("Утро − вечер", st.nm && st.ne ? (st.ms - st.es > 0 ? "+" : "") + fmtN(st.ms - st.es, 0) + " мм" : "—", st.nm && st.ne && st.ms >= 135 ? "утреннее давление ≥ 135 — утренняя гипертензия" : "по верхнему") +
      stat("Размах", r0(st.minS) + "–" + r0(st.maxS), "минимум и максимум верхнего") + "</div></div>";
    const P = list.map((x) => x.pulse).filter((v) => v != null);
    if (P.length) h += '<div class="card"><h3>{{i:heart}} Пульс</h3>' + A.charts.band(daily(list).map((x) => ({ x: x.x, v: x.p })), [60, 80], { color: "#E3899A", title: "Пульс" }) + '<div class="grid3"><div class="stat"><small>Средний</small><b>' + r0(st.p) + '</b></div><div class="stat"><small>&gt; 100</small><b>' + st.hiP + '</b></div><div class="stat"><small>&lt; 50</small><b>' + st.loP + '</b></div></div><p class="small muted">Норма в покое 60–100 (AHA); ESH 2023 и ESC 2024: пульс в покое > 80 у людей с гипертензией — дополнительный фактор риска. Тренированные люди часто имеют 45–60 — это нормально без симптомов.</p></div>';
    h += '<div class="card"><h3>{{i:grid-3x3}} Когда давление выше</h3>' + heat(list) + '<p class="small muted">Цифра — среднее верхнее; цвет — категория, насыщенность — число измерений.</p></div>';
    const ls = lifestyle(inDays(Math.max(per(r), 90))), wl = weightLink(inDays(365));
    h += '<div class="card"><h3>{{i:sparkles}} Связь с образом жизни</h3>';
    if (ls.length || wl) {
      h += ls.map((x) => { const dd = Math.round(x.a - x.b) || 0; return '<div class="item"><div class="tx"><b>' + esc(x.name) + "</b><small>" + esc(x.yes) + ": " + r0(x.a) + " (" + x.na + " дн.) · " + esc(x.no) + ": " + r0(x.b) + " (" + x.nb + ' дн.)</small></div><b class="num" style="color:' + (dd >= 3 ? "var(--bad)" : dd <= -3 ? "var(--good)" : "var(--ink2)") + '">' + (dd > 0 ? "+" : "") + fmtN(dd, 0) + "</b></div>"; }).join("");
      if (wl) h += '<div class="item"><div class="tx"><b>Вес</b><small>корреляция r = ' + fmtN(wl.r, 2) + " по " + wl.n + " измерениям" + (Math.abs(wl.r) >= 0.3 ? " · ≈ " + fmtN(wl.slope, 1) + " мм на 1 кг" : "") + "</small></div></div>";
      h += '<p class="small muted">Разница среднего верхнего давления в днях с фактором и без. Это наблюдение, а не доказательство причины; нужно хотя бы 3 дня в каждой группе.</p>';
    } else h += '<p class="small muted">Отмечайте сон, шаги, стресс и тренировки в приложении — здесь появится, как они связаны с вашим давлением (нужно ≥ 3 дней в каждой группе).</p>';
    return h + "</div>";
  }
  const stat = (t, v, s) => '<div class="stat"><small>' + esc(t) + "</small><b>" + esc(v) + '</b><small>' + esc(s) + "</small></div>";

  function renderLog(r) {
    const list = inDays(per(r)).reverse();
    let h = perSeg(r) + '<div class="btns" style="margin-bottom:10px"><button class="btn sm" data-a="print">Отчёт для врача / PDF</button><button class="btn sm" data-a="csv">CSV</button></div>';
    if (!list.length) return h + A.empty("Записей нет");
    let cur = "";
    h += '<div class="card">';
    list.forEach((x) => {
      if (x.date !== cur) { cur = x.date; h += '<div class="small muted" style="margin-top:8px">' + fmtDate(x.date, { dow: true }) + "</div>"; }
      const c = A.bpCat(x.sys, x.dia);
      h += '<div class="item" data-a="edit" data-id="' + x.id + '"><div class="ic" style="background:' + c.c + ';color:#fff;font-size:12px">' + esc(x.time || "") + '</div><div class="tx"><b>' + bpTxt(x.sys, x.dia) + (x.pulse ? ' <span class="muted" style="font-weight:400">· пульс ' + x.pulse + "</span>" : "") + "</b><small>" + esc([c.n, x.arm === "R" ? "правая" : "левая", x.pos === "stand" ? "стоя" : x.pos === "lie" ? "лёжа" : "", x.reads && x.reads.length > 1 ? "среднее из " + x.reads.length : "", x.proto === true ? "по протоколу" : "", x.meds ? "после лекарств" : ""].concat(x.sym || [], x.ctx || []).filter(Boolean).join(" · ")) + (x.note ? " · " + esc(x.note) : "") + "</small></div></div>";
    });
    return h + "</div>";
  }

  const techCard = () => '<div class="card"><h3>{{i:list-checks}} Как правильно измерять</h3><ol class="small" style="padding-left:18px;margin:4px 0">' + [
    "Прибор с плечевой манжетой, валидированный (списки STRIDE BP, validatebp.org). Запястные и «умные часы» — только для ориентира.",
    "Манжета по размеру плеча, на голую кожу, нижний край на 2–3 см выше локтя.",
    "5 минут посидите спокойно; за 30 минут — без кофе, курения, нагрузки.",
    "Спина на опоре, ноги на полу, не скрещены; рука на столе, манжета на уровне сердца.",
    "Не разговаривайте и не смотрите в телефон во время измерения.",
    "2–3 измерения с интервалом 1–2 минуты, записывайте среднее.",
    "Первый раз измерьте на обеих руках; дальше — на руке с бо́льшими цифрами. Разница > 10 мм стойко — сообщите врачу.",
    "Утром — до лекарств, еды и кофе, после туалета; вечером — перед ужином."
  ].map((x) => "<li>" + x + "</li>").join("") + '</ol><p class="small muted">ESH 2023, AHA 2024 Scientific Statement по домашнему измерению АД.</p></div>';
  const stdCard = () => '<div class="card"><h3>Классификация</h3>' + A.seg(Object.entries(A.BP_STD).map(([k, v]) => [k, v.name]), stdKey(), "std") + '<p class="small muted">' + esc(STD().note) + '</p><div class="kv small">' + STD().cats.map((c, i) => '<b style="color:' + c.c + '">' + esc(c.n) + "</b><span>" + (i === 0 ? "< " + STD().cats[1].s + (STD().cats[1].d ? " и < " + STD().cats[1].d : "") : (c.s ? "≥ " + c.s : "") + (c.d ? (c.s ? " или " : "") + "≥ " + c.d : "") + (STD().cats[i + 1] ? "" : "")) + "</span>").join("") + "</div>" +
    '<div class="form" style="margin-top:10px"><div class="fld"><label>Цель: верхнее &lt;</label><input type="number" data-c="tgs" value="' + target().s + '"></div><div class="fld"><label>нижнее &lt;</label><input type="number" data-c="tgd" value="' + target().d + '"></div></div><p class="small muted">По умолчанию 135/85 — порог нормы для домашних измерений. Если вы лечитесь, ESH 2023 и ESC 2024 рекомендуют для большинства верхнее 120–129 (обсудите цель с врачом).</p></div>';

  function renderTips() {
    const L = A.bpLast();
    const TIPS = [
      ["Меньше соли", "−5…−6 мм", "ВОЗ: < 5 г соли (≈ 2 г натрия) в день. 75% соли — в хлебе, колбасах, сыре, соусах, готовой еде. Заменители соли с калием в исследовании SSaSS (NEJM, 2021) снизили риск инсульта на 14% — если нет болезни почек."],
      ["Питание DASH или средиземноморское", "до −11 мм", "Овощи и фрукты 8–10 порций, цельные злаки, бобовые, орехи, нежирная молочка, мало красного мяса и сладкого. В исследовании DASH у людей с гипертензией −11/−5,5 мм."],
      ["Снижение веса", "≈ −1 мм на кг", "Каждый сброшенный килограмм снижает давление примерно на 1 мм рт. ст.; окружность талии < 94 см у мужчин и < 80 см у женщин."],
      ["Изометрические упражнения", "−5…−8 мм", "Метаанализ Edwards et al. (BJSM, 2023): статические нагрузки (планка, присед у стены, кистевой эспандер) — самые эффективные. Например, 4 × 2 минуты, 3 раза в неделю."],
      ["Аэробная нагрузка", "−5…−8 мм", "150–300 минут умеренной активности в неделю (ходьба, велосипед, плавание) — ESH, ВОЗ. Плюс силовые 2–3 раза в неделю."],
      ["Меньше алкоголя", "≈ −4 мм", "У пьющих > 2 порций в день снижение потребления на 50% даёт около −5,5/−4 мм (Roerecke, Lancet Public Health, 2017). Безопасной дозы для сосудов нет."],
      ["Отказ от курения", "риск ↓", "Каждая сигарета поднимает давление на 15–30 минут; отказ резко снижает сердечно-сосудистый риск."],
      ["Сон 7–9 часов", "", "Недосып и храп с остановками дыхания (апноэ) — частые скрытые причины гипертензии. Громкий храп и дневная сонливость — повод сказать врачу."],
      ["Калий из еды", "−4…−5 мм", "Овощи, фрукты, бобовые, орехи, картофель в мундире. При болезнях почек и некоторых лекарствах — только с врачом."],
      ["Лекарства — по схеме", "", "Не отменяйте и не меняйте дозу сами. Принимайте в одно время; комбинированные таблетки в одной (ESH 2023) удобнее и эффективнее."]
    ];
    let h = "";
    if (L && L.sys >= 140) h += '<div class="card" style="border:2px solid var(--warn)"><h3>Когда к врачу</h3><p class="small">Среднее домашнее ≥ 135/85 или в кабинете ≥ 140/90 при повторных измерениях — повод обратиться к терапевту или кардиологу: нужно обследование органов-мишеней и решение о лечении. ≥ 160/100 — в ближайшие дни.</p></div>';
    h += '<div class="card" style="border-left:4px solid var(--bad)"><h3 style="color:var(--bad)">{{i:triangle-alert}} Срочно 103 / 112</h3><p class="small">Давление ≥ 180/120 и боль в груди, одышка, внезапная слабость или онемение половины тела, нарушение речи, зрения, сильнейшая головная боль, спутанность сознания. До приезда — сидите, не принимайте новых таблеток без указания врача.</p></div>';
    h += '<div class="card"><h3>{{i:sprout}} Что снижает давление: доказанный эффект</h3>' + TIPS.map((t) => '<div class="item"><div class="tx"><b>' + esc(t[0]) + "</b><small>" + esc(t[2]) + "</small></div>" + (t[1] ? '<b class="num" style="color:var(--good);white-space:nowrap">' + esc(t[1]) + "</b>" : "") + "</div>").join("") + '<p class="small muted">Эффекты верхнего давления по ESH 2023, ESC 2024 и указанным исследованиям; складываются частично.</p></div>';
    return h + techCard() + stdCard();
  }

  function printable() {
    const list = inDays(30), st = stats(list), p = A.db().profile;
    let h = '<div class="card"><h3>Дневник давления за 30 дней</h3><p class="small">' + esc(p.name || "") + " · " + fmtDate(today(), { year: true }) + " · классификация " + esc(STD().name) + "</p>";
    h += '<p class="small">Среднее: <b>' + bpTxt(st.s, st.d) + "</b>, утро " + bpTxt(st.ms, st.md) + ", вечер " + bpTxt(st.es, st.ed) + ", пульс " + r0(st.p) + ", пульсовое " + r0(st.pp) + ", SD верхнего " + fmtN(st.sdS, 1) + ", измерений " + st.n + "</p>";
    h += '<table class="tbl"><tr><th>Дата</th><th>Время</th><th class="r">АД</th><th class="r">Пульс</th><th>Примечание</th></tr>' + list.slice().reverse().map((x) => "<tr><td>" + fmtShort(x.date) + "</td><td>" + esc(x.time || "") + '</td><td class="r">' + bpTxt(x.sys, x.dia) + '</td><td class="r">' + (x.pulse || "") + "</td><td>" + esc([x.meds ? "после лекарств" : ""].concat(x.sym || []).filter(Boolean).join(", ")) + "</td></tr>").join("") + "</table></div>";
    return h;
  }

  A.view("bp", {
    title: (r) => (r.args[0] === "report" ? "Отчёт: давление" : "Давление и пульс"), tab: "more",
    render(el, r) {
      if (r.args[0] === "report") return '<button class="btn primary" data-a="doPrint" style="margin-bottom:10px">Печать / PDF</button>' + printable();
      const tab = r.args[0] || "sum";
      return A.seg(TABS, tab, "tab") + (tab === "an" ? renderAn(r) : tab === "log" ? renderLog(r) : tab === "proto" ? protoView() + techCard() : tab === "tips" ? renderTips() : renderSum(r));
    },
    actions(box) { box.innerHTML = '<button class="icon-btn" data-add aria-label="Новое измерение">＋</button>'; box.querySelector("[data-add]").onclick = () => A.edit.bp(); },
    bind(el, r) {
      const tab = r.args[0] || "sum";
      A.bind(el, {
        tab(b) { A.go("bp/" + b.dataset.v + (r.params.per ? "?per=" + r.params.per : ""), true); },
        per(b) { A.go("bp/" + tab + "?per=" + b.dataset.v, true); },
        add() { A.edit.bp(); }, edit(b) { const x = A.byId("bp", b.dataset.id); if (x) A.edit.bp(x); },
        std(b) { A.db().profile.bpStd = b.dataset.v; A.save(); A.refresh(); },
        tgs(i) { const p = A.db().profile; p.bpTarget = Object.assign({}, p.bpTarget, { s: A.num(i.value, 135) }); A.save(); },
        tgd(i) { const p = A.db().profile; p.bpTarget = Object.assign({}, p.bpTarget, { d: A.num(i.value, 85) }); A.save(); },
        protoStart() { A.db().profile.bpProto = { start: today() }; A.save(); A.refresh(); A.toast("Протокол начат: утром и вечером по 2 измерения"); },
        protoStop() { delete A.db().profile.bpProto; A.save(); A.refresh(); },
        print() { A.go("bp/report"); }, doPrint() { A.print("Дневник давления"); },
        csv() { A.download("davlenie-" + today() + ".csv", "text/csv", A.csv(all().map((x) => ({ date: x.date, time: x.time, sys: x.sys, dia: x.dia, pulse: x.pulse, category: A.bpCat(x.sys, x.dia).n, arm: x.arm, position: x.pos, readings: (x.reads || []).map((q) => q.join("/")).join(" "), meds: x.meds ? "да" : "", symptoms: (x.sym || []).join(", "), context: (x.ctx || []).join(", "), note: x.note })))); }
      });
    }
  });
})();
