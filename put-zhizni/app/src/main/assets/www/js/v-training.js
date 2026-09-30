/* Тренировки по образцу приложения DASEIN: программы с целями, живая тренировка с подходами и таймером отдыха,
   двойная прогрессия нагрузки (ACSM, 2009), библиотека упражнений, история и прогресс по расчётному максимуму. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN, fmtShort, fmtDate, sum } = A;
  A.edit = A.edit || {};

  const KIND = ["Вес × повторы", "Повторы", "Время"];
  const MUSCLES = ["Ноги", "Ягодицы", "Грудь", "Спина", "Плечи", "Руки", "Пресс", "Всё тело", "Кардио", "Другое"];
  const WD = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  const days = (mask) => WD.filter((_, i) => mask & (1 << i)).join(" ");
  const w = (v) => fmtN(+v || 0, 2).replace(/,00$/, "");
  const mmss = (s) => Math.floor(s / 60) + ":" + String(Math.floor(s % 60)).padStart(2, "0");
  A.glyph = (g, size = 34) => '<img src="img/glyphs/' + esc(g || "sport/15") + '.webp" width="' + size + '" height="' + size + '" alt="" style="flex:0 0 auto;object-fit:contain">';

  /* ---------- модель ---------- */
  const T = () => window.TRAINING;
  const ex = (id) => A.byId("exercises", id);
  const plan = (id) => A.byId("plans", id);
  const sessions = () => A.col("sessions");

  // Заполняет библиотеку и программы при первом запуске (и добавляет новые встроенные позже).
  A.seedTraining = () => {
    const db = A.db(); let ch = false;
    db.exercises = db.exercises || []; db.plans = db.plans || []; db.sessions = db.sessions || [];
    T().exercises.forEach((s) => {
      if (db.exercises.some((e) => e.seedKey === s.key)) return;
      const same = db.exercises.find((e) => !e.seedKey && e.name.toLowerCase() === s.name.toLowerCase());
      const o = { name: s.name, muscle: s.muscle, equip: s.equip, kind: s.kind, glyph: s.glyph, about: s.about, custom: false, seedKey: s.key, media: [] };
      if (same) Object.assign(same, o, { media: same.media || [] }); else db.exercises.push(Object.assign({ id: A.uid() }, o));
      ch = true;
    });
    db.exercises.forEach((e) => { if (e.kind == null) { e.kind = 0; e.custom = true; e.glyph = e.glyph || "sport/15"; e.media = e.media || []; ch = true; } });
    const byKey = {}; db.exercises.forEach((e) => { if (e.seedKey) byKey[e.seedKey] = e; });
    T().plans.forEach((p, i) => {
      if (db.plans.some((x) => x.seedKey === p.key)) return;
      db.plans.push({ id: A.uid(), name: p.name, about: p.about, glyph: p.glyph, mask: p.mask, sort: i, archived: false, seedKey: p.key,
        items: p.items.filter((it) => byKey[it.ex]).map((it) => ({ id: A.uid(), exId: byKey[it.ex].id, sets: it.sets, reps: it.reps, repMin: it.repMin, repMax: it.repMax, weight: it.weight, step: it.step, seconds: it.seconds, rest: it.rest, maxSets: 5, note: "" })) });
      ch = true;
    });
    if (ch) A.save();
  };

  /* ---------- прогрессия (перенос logic/Progression.kt) ---------- */
  const roundTo = (v, step) => (step > 0 ? Math.round(v / step) * step : v);
  A.progression = {
    next(t, sets, failedBefore) {
      const done = sets.filter((s) => s.done);
      if (!done.length) return { t, reason: "Подходы не отмечены — цель без изменений" };
      const all = done.length >= t.sets;
      const n = Object.assign({}, t);
      if (t.kind === 2) {
        const ok = all && done.every((s) => s.sec >= t.seconds);
        if (ok) { n.seconds = t.seconds + 5; return { t: n, reason: "Все подходы выполнены — +5 секунд" }; }
        return { t, reason: "Не все подходы выполнены — повторите цель" };
      }
      const minReps = Math.min(...done.map((s) => s.reps));
      if (t.kind === 1) {
        if (all && minReps >= t.repMax && t.sets < t.maxSets) { n.sets = t.sets + 1; n.reps = t.repMin; return { t: n, reason: "Верхняя граница во всех подходах — +1 подход, повторы с " + t.repMin }; }
        if (all && minReps >= t.repMax) { n.reps = t.reps + 1; n.repMax = t.repMax + 1; return { t: n, reason: "Максимум подходов — растим повторы" }; }
        if (all && minReps >= t.reps) { n.reps = Math.min(minReps + 1, t.repMax); return { t: n, reason: "Цель выполнена — на повтор больше, чем в самом слабом подходе" }; }
        return { t, reason: "Цель не выполнена — повторите её" };
      }
      const wmax = Math.max(...done.map((s) => +s.w || 0));
      if (all && minReps >= t.repMax) { n.weight = roundTo(wmax + t.step, t.step); n.reps = t.repMin; return { t: n, reason: "Верх диапазона во всех подходах — +" + w(t.step) + " кг, повторы с " + t.repMin }; }
      if (all && minReps >= t.reps) { n.weight = wmax; n.reps = Math.min(minReps + 1, t.repMax); return { t: n, reason: "Цель выполнена — на повтор больше, чем в самом слабом подходе" }; }
      if (minReps < t.repMin && failedBefore && wmax > 0) { n.weight = roundTo(wmax * 0.9, t.step); n.reps = t.repMin; return { t: n, reason: "Дважды ниже диапазона — разгрузка −10%" }; }
      n.weight = wmax; return { t: n, reason: "Цель не выполнена — повторите её" };
    },
    failed(t, sets) { const d = sets.filter((s) => s.done); return t.kind !== 2 && d.length > 0 && Math.min(...d.map((s) => s.reps)) < t.repMin; },
    // Оценка максимума на одно повторение по Эпли (1985).
    orm: (weight, reps) => (reps <= 1 ? weight : weight * (1 + reps / 30)),
    // Расход по MET (Ainsworth et al., 2011): силовая умеренная — 3,5 MET, интенсивная — 6 MET.
    kcal: (min, kg, vig) => Math.round(((vig ? 6 : 3.5) * kg * min) / 60)
  };
  const target = (pe, kind) => ({ kind, sets: pe.sets, reps: pe.reps, weight: +pe.weight || 0, seconds: pe.seconds || 0, repMin: pe.repMin, repMax: pe.repMax, step: +pe.step || 2.5, maxSets: pe.maxSets || 5 });
  A.describeTarget = (kind, sets, reps, weight, seconds) => (kind === 2 ? sets + " × " + seconds + " с" : kind === 1 ? sets + " × " + reps : sets + " × " + reps + " × " + w(weight) + " кг");
  const describeDone = (kind, list) => { const d = list.filter((s) => s.done); if (!d.length) return "не выполнено"; return d.map((s) => (kind === 2 ? s.sec + " с" : kind === 1 ? s.reps : s.reps + "×" + w(s.w))).join(" / "); };

  /* ---------- действия ---------- */
  A.training = {
    start(planId) {
      const p = planId ? plan(planId) : null, now = Date.now();
      const s = { id: A.uid(), planId: planId || null, title: p ? p.name : "Свободная тренировка", date: today(), startedAt: now, finishedAt: null, note: "", feel: 0, workoutId: null, sets: [] };
      if (p) p.items.forEach((pe) => { for (let i = 0; i < pe.sets; i++) s.sets.push({ id: A.uid(), exId: pe.exId, peId: pe.id, i, reps: pe.reps, w: +pe.weight || 0, sec: pe.seconds || 0, done: false, at: now + i }); });
      sessions().push(s); A.save();
      A.go("workouts/session/" + s.id);
    },
    addExercise(s, exId, n = 3) {
      const last = sessions().flatMap((x) => x.sets).filter((x) => x.exId === exId && x.done).sort((a, b) => b.at - a.at)[0];
      for (let i = 0; i < n; i++) s.sets.push({ id: A.uid(), exId, peId: null, i, reps: last ? last.reps : 10, w: last ? last.w : 0, sec: last ? last.sec : 30, done: false, at: Date.now() + i });
      A.save();
    },
    addSet(s, exId) {
      const same = s.sets.filter((x) => x.exId === exId), last = same[same.length - 1];
      s.sets.push({ id: A.uid(), exId, peId: last ? last.peId : null, i: last ? last.i + 1 : 0, reps: last ? last.reps : 10, w: last ? last.w : 0, sec: last ? last.sec : 30, done: false, at: Date.now() });
      A.save();
    },
    finish(s, feel, note) {
      const now = Date.now(), minutes = Math.max(1, Math.min(300, Math.round((now - s.startedAt) / 60000)));
      const kg = A.lastWeight() || 70;
      const vig = s.sets.some((x) => x.done && ["Кардио", "Всё тело"].includes((ex(x.exId) || {}).muscle));
      const kcal = A.progression.kcal(minutes, kg, vig);
      const notes = [];
      const p = s.planId && plan(s.planId);
      if (p) p.items.forEach((pe) => {
        const e = ex(pe.exId); if (!e) return;
        const mine = s.sets.filter((x) => x.peId === pe.id || (!x.peId && x.exId === pe.exId));
        if (!mine.some((x) => x.done)) return;
        const prevS = sessions().filter((x) => x.id !== s.id && x.planId === s.planId && x.finishedAt).sort((a, b) => b.startedAt - a.startedAt)[0];
        const prev = prevS ? prevS.sets.filter((x) => x.peId === pe.id) : [];
        const t = target(pe, e.kind);
        const nx = A.progression.next(t, mine, A.progression.failed(t, prev));
        Object.assign(pe, { sets: nx.t.sets, reps: nx.t.reps, weight: nx.t.weight, seconds: nx.t.seconds, repMax: nx.t.repMax });
        notes.push({ ex: e.name, done: describeDone(e.kind, mine), next: A.describeTarget(e.kind, nx.t.sets, nx.t.reps, nx.t.weight, nx.t.seconds), reason: nx.reason });
      });
      const doneSets = s.sets.filter((x) => x.done);
      const names = [...new Set(doneSets.map((x) => (ex(x.exId) || {}).name).filter(Boolean))];
      const wk = A.upsert("workouts", Object.assign(s.workoutId ? A.byId("workouts", s.workoutId) || {} : {}, {
        id: s.workoutId || undefined, date: s.date, type: "gym", dur: minutes, int: feel ? feel * 2 : 6, kcal, sessionId: s.id,
        sets: doneSets.map((x) => ({ ex: x.exId, reps: x.reps, w: x.w })),
        note: [s.title, note].filter(Boolean).join(". ") + " · подходов: " + doneSets.length + (names.length ? " · " + names.join(", ") : "")
      }));
      Object.assign(s, { finishedAt: now, feel, note, workoutId: wk.id });
      A.save();
      return notes;
    },
    discard(s) { if (s.workoutId) A.remove("workouts", s.workoutId); A.remove("sessions", s.id); },
    history(exId) {
      const byS = {};
      sessions().forEach((s) => { const d = s.sets.filter((x) => x.exId === exId && x.done); if (d.length) byS[s.id] = { date: s.date, sets: d, orm: Math.max(...d.map((x) => A.progression.orm(+x.w || 0, x.reps))), vol: sum(d.map((x) => (+x.w || 0) * x.reps)), reps: Math.max(...d.map((x) => x.reps)), sec: Math.max(...d.map((x) => x.sec || 0)) }; });
      return Object.values(byS).sort((a, b) => (a.date > b.date ? 1 : -1));
    }
  };

  /* ---------- таймер отдыха и время тренировки ---------- */
  let restEnd = null, tick = null;
  const ensureTick = () => {
    if (tick) return;
    tick = setInterval(() => {
      const bar = document.getElementById("restBar"), el = document.getElementById("sessT");
      if (el && el.dataset.t0) el.textContent = mmss((Date.now() - +el.dataset.t0) / 1000);
      if (bar) {
        if (!restEnd) { bar.hidden = true; }
        else {
          const left = Math.round((restEnd - Date.now()) / 1000);
          if (left <= 0) { restEnd = null; bar.hidden = true; A.vibe(300); A.toast("Отдых окончен — следующий подход!"); }
          else { bar.hidden = false; bar.querySelector("b").textContent = "Отдых " + mmss(left); }
        }
      }
      if (!el && !bar) { clearInterval(tick); tick = null; }
    }, 500);
  };

  /* ---------- выбор упражнения ---------- */
  A.pickExercise = (cb) => {
    const w2 = A.sheet("Упражнение", '<input id="exq" placeholder="Поиск"><div id="exl" class="list" style="margin-top:6px"></div>');
    const draw = () => {
      const q = w2.querySelector("#exq").value.trim().toLowerCase();
      w2.querySelector("#exl").innerHTML = A.col("exercises").filter((e) => !q || (e.name + " " + e.muscle + " " + (e.equip || "")).toLowerCase().includes(q)).sort((a, b) => a.name.localeCompare(b.name)).map((e) => '<div class="item" data-id="' + e.id + '">' + A.glyph(e.glyph, 30) + '<div class="tx"><b>' + esc(e.name) + "</b><small>" + esc([e.muscle, e.equip, KIND[e.kind || 0]].filter(Boolean).join(" · ")) + "</small></div></div>").join("");
    };
    draw();
    w2.querySelector("#exq").addEventListener("input", draw);
    w2.querySelector("#exl").addEventListener("click", (e) => { const it = e.target.closest("[data-id]"); if (!it) return; A.closeSheet(); cb(it.dataset.id); });
  };
  const glyphPicker = (cur, cb) => {
    const all = []; for (let i = 0; i < 35; i++) all.push("sport/" + String(i).padStart(2, "0")); for (let i = 0; i < 40; i++) all.push("train/" + String(i).padStart(2, "0"));
    const w2 = A.sheet("Иконка", '<div class="rgrid" style="grid-template-columns:repeat(5,1fr)">' + all.map((g) => '<button type="button" data-g="' + g + '" style="padding:6px;border-radius:12px;' + (g === cur ? "background:var(--accent2)" : "") + '">' + A.glyph(g, 44) + "</button>").join("") + "</div>");
    w2.addEventListener("click", (e) => { const b = e.target.closest("[data-g]"); if (!b) return; A.closeSheet(); cb(b.dataset.g); });
  };

  /* ---------- редакторы ---------- */
  const targetSheet = (p, pe) => {
    const e = ex(pe.exId) || { kind: 0, name: "?" };
    const f = [{ k: "sets", label: "Подходов", type: "number" }];
    if (e.kind === 2) f.push({ k: "seconds", label: "Секунд в подходе", type: "number" });
    else f.push({ k: "reps", label: "Повторов (цель)", type: "number" }, { k: "repMin", label: "Диапазон: от", type: "number" }, { k: "repMax", label: "до", type: "number" });
    if (e.kind === 0) f.push({ k: "weight", label: "Вес, кг", type: "number", step: 0.5 }, { k: "step", label: "Шаг веса, кг", type: "number", step: 0.5 });
    if (e.kind === 1) f.push({ k: "maxSets", label: "Максимум подходов", type: "number" });
    f.push({ k: "rest", label: "Отдых, секунд", type: "number" }, { k: "note", label: "Заметка", type: "textarea", rows: 2 });
    A.formSheet(e.name, f, pe, (o) => { Object.assign(pe, o); A.save(); A.refresh(); }, { saveLabel: "Сохранить", onDelete: () => { p.items.splice(p.items.indexOf(pe), 1); A.save(); A.refresh(); } });
  };
  A.edit.exercise = (e0) => {
    if (!e0) { const e = { id: A.uid(), name: "", muscle: "Другое", equip: "", kind: 0, glyph: "sport/15", about: "", custom: true, media: [] }; A.col("exercises").push(e); A.save(); return A.go("workouts/exercise/" + e.id + "?new=1"); }
    A.go("workouts/exercise/" + e0.id);
  };

  /* ---------- экраны ---------- */
  const TABS = [["plans", "Программы"], ["ex", "Упражнения"], ["history", "История"], ["progress", "Прогресс"], ["log", "Журнал"]];
  const faces = ["", "frown", "annoyed", "meh", "smile", "laugh"];

  function planList() {
    const active = sessions().find((s) => !s.finishedAt);
    let h = "";
    if (active) h += '<div class="card tint tap" data-a="open" data-v="workouts/session/' + active.id + '"><b>Идёт тренировка: ' + esc(active.title) + '</b><div class="small muted">Нажмите, чтобы продолжить</div></div>';
    h += '<div class="card"><h3>{{i:lightbulb}} Как работает прогрессия</h3><p class="small">Выберите программу и нажмите «Начать». В тренировке отмечайте подходы — после завершения приложение само повысит цель: сначала добавит повтор, а когда во всех подходах получится верх диапазона — вес (для упражнений без веса — подход). Так работает двойная прогрессия из рекомендаций Американского колледжа спортивной медицины (ACSM, 2009).</p></div>';
    h += '<button class="btn block ghost" data-a="start" data-v="" style="margin-bottom:12px">Свободная тренировка без программы</button>';
    A.col("plans").filter((p) => !p.archived).sort((a, b) => a.sort - b.sort).forEach((p) => {
      const last = sessions().filter((s) => s.planId === p.id && s.finishedAt).sort((a, b) => b.startedAt - a.startedAt)[0];
      h += '<div class="card nocollapse"><div class="row tap" data-a="open" data-v="workouts/plan/' + p.id + '">' + A.glyph(p.glyph, 40) + '<div class="grow"><b>' + esc(p.name) + '</b><div class="small muted">' + [p.items.length + " упр.", days(p.mask), last ? "было " + fmtShort(last.date) : ""].filter(Boolean).join(" · ") + '</div></div><button class="btn primary sm" data-a="start" data-v="' + p.id + '"' + (p.items.length ? "" : " disabled") + ">Начать</button></div>" + (p.about ? '<p class="small muted" style="margin-top:6px">' + esc(p.about) + "</p>" : "") + "</div>";
    });
    const arch = A.col("plans").filter((p) => p.archived);
    if (arch.length) h += '<details class="small"><summary class="muted">Архив программ (' + arch.length + ")</summary>" + arch.map((p) => '<div class="item tap" data-a="open" data-v="workouts/plan/' + p.id + '">' + A.glyph(p.glyph, 28) + '<div class="tx"><b>' + esc(p.name) + "</b></div></div>").join("") + "</details>";
    h += '<button class="fab" data-a="newPlan" aria-label="Новая программа">+</button>';
    return h;
  }
  function exList(q) {
    const counts = {}; sessions().forEach((s) => s.sets.forEach((x) => { if (x.done) counts[x.exId] = (counts[x.exId] || 0) + 1; }));
    const list = A.col("exercises").filter((e) => !q || (e.name + " " + e.muscle + " " + (e.equip || "")).toLowerCase().includes(q.toLowerCase())).sort((a, b) => a.name.localeCompare(b.name));
    let h = '<input id="exq2" data-c="exq" placeholder="Поиск упражнения" value="' + esc(q || "") + '" style="margin-bottom:10px">';
    MUSCLES.forEach((m) => {
      const g = list.filter((e) => (e.muscle || "Другое") === m || (m === "Другое" && !MUSCLES.includes(e.muscle)));
      if (!g.length) return;
      h += '<div class="sec-t">' + m + '</div><div class="card nocollapse"><div class="list">' + g.map((e) => '<div class="item tap" data-a="open" data-v="workouts/exercise/' + e.id + '">' + A.glyph(e.glyph, 34) + '<div class="tx"><b>' + esc(e.name) + "</b><small>" + esc([e.equip, KIND[e.kind || 0]].filter(Boolean).join(" · ")) + "</small></div>" + (counts[e.id] ? '<small class="muted">' + counts[e.id] + " подх.</small>" : "") + "</div>").join("") + "</div></div>";
    });
    h += '<button class="fab" data-a="newEx" aria-label="Новое упражнение">+</button>';
    return h;
  }
  function historyList() {
    let h = '<button class="btn block ghost" data-a="quickLog" style="margin-bottom:12px">{{i:footprints}} Бег, йога, прогулки — быстрая запись</button>';
    const done = sessions().filter((s) => s.finishedAt).sort((a, b) => b.startedAt - a.startedAt);
    if (!done.length) return h + A.empty("Завершённых тренировок пока нет.");
    return h + done.map((s) => { const mine = s.sets.filter((x) => x.done); const min = Math.round((s.finishedAt - s.startedAt) / 60000); return '<div class="card nocollapse tap" data-a="open" data-v="workouts/session/' + s.id + '"><div class="row"><div class="grow"><b>' + esc(s.title) + '</b><div class="small muted">' + fmtDate(s.date) + " · " + min + " мин · подходов " + mine.length + " · тоннаж " + w(sum(mine.map((x) => (+x.w || 0) * x.reps))) + " кг</div></div>" + (s.feel ? '<span style="font-size:24px;color:var(--accent)">{{i:' + faces[s.feel] + "}}</span>" : "") + "</div></div>"; }).join("");
  }
  function progressList() {
    const used = A.col("exercises").filter((e) => sessions().some((s) => s.sets.some((x) => x.exId === e.id && x.done)));
    if (!used.length) return A.empty("Здесь появятся графики, когда вы отметите первые подходы.");
    return used.map((e) => {
      const hs = A.training.history(e.id), last = hs[hs.length - 1];
      const vals = hs.map((d) => (e.kind === 2 ? d.sec : e.kind === 1 ? d.reps : d.orm));
      return '<div class="card nocollapse tap" data-a="open" data-v="workouts/exercise/' + e.id + '"><div class="row">' + A.glyph(e.glyph, 34) + '<div class="grow"><b>' + esc(e.name) + '</b><div class="small muted">' + (last ? describeDone(e.kind, last.sets) : "") + (e.kind === 0 && last ? " · 1ПМ ≈ " + fmtN(last.orm, 1) + " кг" : "") + "</div></div></div>" + (vals.length >= 2 ? A.charts.line([{ name: e.kind === 2 ? "сек" : e.kind === 1 ? "повторы" : "1ПМ, кг", color: "#8E7CC3", data: hs.map((d, i) => ({ x: fmtShort(d.date), v: vals[i] })) }], { h: 90 }) : "") + "</div>";
    }).join("");
  }

  function sessionView(s) {
    const finished = !!s.finishedAt;
    const doneN = s.sets.filter((x) => x.done).length;
    let h = '<div class="card tint nocollapse"><div class="row"><div class="grow"><b>' + esc(s.title) + '</b><div class="small muted">подходов ' + doneN + " из " + s.sets.length + " · тоннаж " + w(sum(s.sets.filter((x) => x.done).map((x) => (+x.w || 0) * x.reps))) + " кг</div></div>" + (finished ? '<span class="small muted">' + fmtDate(s.date) + "</span>" : '<b class="big" id="sessT" data-t0="' + s.startedAt + '" style="font-size:22px">' + mmss((Date.now() - s.startedAt) / 1000) + "</b>") + "</div></div>";
    const order = [...new Set(s.sets.map((x) => x.exId))];
    const p = s.planId && plan(s.planId);
    order.forEach((exId) => {
      const e = ex(exId) || { name: "Удалённое упражнение", kind: 0, glyph: "sport/15" };
      const list = s.sets.filter((x) => x.exId === exId);
      const pe = p && p.items.find((it) => it.exId === exId);
      h += '<div class="card nocollapse"><div class="row" style="margin-bottom:6px">' + A.glyph(e.glyph, 32) + '<div class="grow tap" data-a="open" data-v="workouts/exercise/' + exId + '"><b>' + esc(e.name) + "</b>" + (pe ? '<div class="small muted">цель ' + A.describeTarget(e.kind, pe.sets, pe.reps, pe.weight, pe.seconds) + (e.kind !== 2 ? " · диапазон " + pe.repMin + "–" + pe.repMax : "") + "</div>" : "") + "</div></div>";
      h += '<div class="row small muted" style="gap:6px"><span style="width:22px">#</span>' + (e.kind === 0 ? '<span class="grow">вес, кг</span>' : "") + (e.kind !== 2 ? '<span class="grow">повторы</span>' : '<span class="grow">секунды</span>') + '<span style="width:44px"></span></div>';
      list.forEach((x, i) => {
        h += '<div class="row" style="gap:6px;margin:4px 0"><b style="width:22px">' + (i + 1) + "</b>" +
          (e.kind === 0 ? '<input type="number" inputmode="decimal" step="0.5" class="grow" data-c="setv" data-id="' + x.id + '" data-f="w" value="' + (x.w ?? "") + '" style="min-height:40px">' : "") +
          (e.kind !== 2 ? '<input type="number" inputmode="numeric" class="grow" data-c="setv" data-id="' + x.id + '" data-f="reps" value="' + (x.reps ?? "") + '" style="min-height:40px">' : '<input type="number" inputmode="numeric" class="grow" data-c="setv" data-id="' + x.id + '" data-f="sec" value="' + (x.sec ?? "") + '" style="min-height:40px">') +
          '<button class="check" data-a="setDone" data-id="' + x.id + '" style="width:44px;height:40px;' + (x.done ? "background:var(--accent);border-color:var(--accent)" : "") + '">' + (x.done ? "✓" : "") + "</button></div>";
      });
      h += '<div class="btns" style="margin-top:4px"><button class="btn sm ghost" data-a="addSet" data-v="' + exId + '">+ Подход</button>' + (list.length > 1 ? '<button class="btn sm ghost" data-a="delSet" data-v="' + exId + '">− Подход</button>' : "") + "</div></div>";
    });
    h += '<button class="btn block" data-a="addEx">+ Упражнение в эту тренировку</button>';
    if (finished && s.note) h += '<div class="card" style="margin-top:12px"><b>Заметка:</b> ' + esc(s.note) + "</div>";
    h += '<button class="btn block danger ghost" data-a="discard" style="margin-top:12px">Удалить тренировку</button>';
    h += '<div id="restBar" class="card peach nocollapse" hidden style="position:sticky;bottom:8px;z-index:4;display:flex;align-items:center;gap:6px"><b class="grow" style="font-size:20px">Отдых</b><button class="btn sm" data-a="rest" data-v="-15">−15</button><button class="btn sm" data-a="rest" data-v="15">+15</button><button class="btn sm" data-a="rest" data-v="0">Пропустить</button></div>';
    return h;
  }

  function planView(p) {
    let h = '<div class="card nocollapse"><div class="row"><button class="tap" data-a="glyph" aria-label="Иконка">' + A.glyph(p.glyph, 52) + '</button><div class="grow"><input data-c="pf" data-k="name" value="' + esc(p.name) + '" placeholder="Название" style="font-weight:700"></div></div>' +
      '<textarea data-c="pf" data-k="about" rows="2" placeholder="Описание" style="margin-top:8px">' + esc(p.about || "") + '</textarea><div class="small muted" style="margin:8px 0 4px">Дни тренировок</div><div class="chips">' + WD.map((d, i) => '<button class="chip' + (p.mask & (1 << i) ? " on" : "") + '" data-a="day" data-v="' + i + '">' + d + "</button>").join("") + "</div></div>";
    h += '<div class="card nocollapse"><h3>Упражнения и цели</h3>' + (p.items.length ? p.items.map((pe, i) => { const e = ex(pe.exId) || { name: "?", kind: 0 }; return '<div class="item"><span class="muted" style="width:20px">' + (i + 1) + "</span>" + A.glyph(e.glyph, 30) + '<div class="tx tap" data-a="target" data-i="' + i + '"><b>' + esc(e.name) + "</b><small>" + A.describeTarget(e.kind, pe.sets, pe.reps, pe.weight, pe.seconds) + (e.kind !== 2 ? " · " + pe.repMin + "–" + pe.repMax + " повт." : "") + " · отдых " + pe.rest + " с" + (pe.note ? " · " + esc(pe.note) : "") + '</small></div><button class="icon-btn" data-a="mv" data-i="' + i + '" data-v="-1" aria-label="Выше">↑</button><button class="icon-btn" data-a="mv" data-i="' + i + '" data-v="1" aria-label="Ниже">↓</button></div>'; }).join("") : A.empty("Добавьте упражнения")) +
      '<button class="btn block" data-a="addItem" style="margin-top:8px">+ Упражнение</button></div>';
    h += '<button class="btn primary block" data-a="start" data-v="' + p.id + '"' + (p.items.length ? "" : " disabled") + '>Начать тренировку</button>';
    h += '<div class="btns"><button class="btn ghost" data-a="archive">' + (p.archived ? "Вернуть из архива" : "В архив") + '</button><button class="btn danger ghost" data-a="delPlan">Удалить программу</button></div>';
    return h;
  }

  function exerciseView(e) {
    const hs = A.training.history(e.id);
    let h = '<div class="card nocollapse"><div class="row"><button class="tap" data-a="glyph" aria-label="Иконка">' + A.glyph(e.glyph, 52) + '</button><div class="grow"><input data-c="ef" data-k="name" value="' + esc(e.name) + '" placeholder="Название упражнения" style="font-weight:700"></div></div>' +
      '<div class="small muted" style="margin:10px 0 4px">Тип</div><div class="chips">' + KIND.map((k, i) => '<button class="chip' + ((e.kind || 0) === i ? " on" : "") + '" data-a="kind" data-v="' + i + '">' + k + "</button>").join("") + "</div>" +
      '<div class="small muted" style="margin:10px 0 4px">Мышцы</div><div class="chips">' + MUSCLES.map((m) => '<button class="chip' + (e.muscle === m ? " on" : "") + '" data-a="muscle" data-v="' + m + '">' + m + "</button>").join("") + "</div>" +
      '<div class="form" style="margin-top:10px"><div class="fld full"><label>Инвентарь</label><input data-c="ef" data-k="equip" value="' + esc(e.equip || "") + '"></div><div class="fld full"><label>Техника выполнения</label><textarea rows="4" data-c="ef" data-k="about">' + esc(e.about || "") + "</textarea></div></div></div>";
    h += '<div class="card nocollapse"><h3>{{i:camera}} Фото техники</h3><div class="rgrid" style="grid-template-columns:repeat(3,1fr)">' + (e.media || []).map((m) => '<div style="position:relative"><img data-photo="' + m + '" style="width:100%;aspect-ratio:1;object-fit:cover;border-radius:10px" alt=""><button class="icon-btn" data-a="delMedia" data-v="' + m + '" style="position:absolute;top:0;right:0;background:var(--card);min-width:32px;min-height:32px">✕</button></div>').join("") + '</div><button class="btn sm" data-a="addMedia" style="margin-top:8px">+ Фото техники</button></div>';
    h += '<div class="card nocollapse"><h3>{{i:chart-column}} История</h3>' + (hs.length ? (hs.length >= 2 ? A.charts.line([{ name: e.kind === 2 ? "сек" : e.kind === 1 ? "повторы" : "1ПМ, кг", color: "#8E7CC3", data: hs.map((d) => ({ x: fmtShort(d.date), v: e.kind === 2 ? d.sec : e.kind === 1 ? d.reps : Math.round(d.orm * 10) / 10 })) }]) : "") + hs.slice().reverse().map((d) => '<div class="item"><div class="tx"><b>' + fmtDate(d.date) + "</b><small>" + describeDone(e.kind, d.sets) + (e.kind === 0 ? " · 1ПМ ≈ " + fmtN(d.orm, 1) + " кг · тоннаж " + w(d.vol) + " кг" : "") + "</small></div></div>").join("") : '<p class="small muted">Подходов с этим упражнением ещё нет.</p>') + "</div>";
    if (e.custom) h += '<button class="btn block danger ghost" data-a="delEx">Удалить упражнение</button>';
    return h;
  }

  A.view("workouts", {
    title: (r) => (r.args[0] === "session" ? "Тренировка" : r.args[0] === "plan" ? "Программа" : r.args[0] === "exercise" ? "Упражнение" : "Тренировки"), tab: "more",
    actions(el, r) {
      if (r.args[0] === "session") { const s = A.byId("sessions", r.args[1]); if (s && !s.finishedAt) el.innerHTML = '<button class="btn sm primary" onclick="App._finishSession()">Завершить</button>'; }
      else if (!r.args[0] || TABS.some((t) => t[0] === r.args[0])) el.innerHTML = '<button class="icon-btn" onclick="App.edit.workout()" aria-label="Быстрая запись">{{i:plus}}</button>';
    },
    render(el, r) {
      A.seedTraining();
      const sec = r.args[0] || "plans";
      if (sec === "session") { const s = A.byId("sessions", r.args[1]); if (!s) return A.empty("Тренировка не найдена"); setTimeout(ensureTick, 0); return sessionView(s); }
      if (sec === "plan") { const p = plan(r.args[1]); return p ? planView(p) : A.empty("Программа не найдена"); }
      if (sec === "exercise") { const e = ex(r.args[1]); return e ? exerciseView(e) : A.empty("Упражнение не найдено"); }
      let h = A.seg(TABS, sec, "tab");
      if (sec === "plans") h += planList();
      else if (sec === "ex") h += exList(r.params.q || "");
      else if (sec === "history") h += historyList();
      else if (sec === "progress") h += progressList();
      else if (sec === "log") h += A.views.wlog.render(el, { args: [r.params.l || "log"], params: r.params });
      return h;
    },
    bind(el, r) {
      const sec = r.args[0] || "plans";
      const s = sec === "session" ? A.byId("sessions", r.args[1]) : null;
      const p = sec === "plan" ? plan(r.args[1]) : null;
      const e = sec === "exercise" ? ex(r.args[1]) : null;
      if (sec === "log") A.views.wlog.bind(el, { args: [r.params.l || "log"], params: r.params, name: "workouts" });
      A.bind(el, {
        tab(b) { A.go("workouts/" + b.dataset.v, true); },
        open(b) { A.go(b.dataset.v); },
        start(b) { const act = sessions().find((x) => !x.finishedAt); if (act) { A.confirm("Уже идёт тренировка «" + act.title + "». Открыть её?", () => A.go("workouts/session/" + act.id), "Открыть"); return; } A.training.start(b.dataset.v || null); },
        newPlan() { const np = { id: A.uid(), name: "Новая программа", about: "", glyph: "sport/15", mask: 0, sort: A.col("plans").length, archived: false, items: [] }; A.col("plans").push(np); A.save(); A.go("workouts/plan/" + np.id); },
        newEx() { A.edit.exercise(); },
        exq(b) { A.go("workouts/ex?q=" + encodeURIComponent(b.value.trim()), true); },
        quickLog() { A.edit.workout(); },
        // тренировка
        setv(b) { const x = s.sets.find((k) => k.id === b.dataset.id); x[b.dataset.f] = A.num(b.value, 0); A.save(); },
        setDone(b) {
          const x = s.sets.find((k) => k.id === b.dataset.id); x.done = !x.done; x.at = Date.now(); A.save(); A.vibe(20);
          if (x.done && !s.finishedAt) { const pp = s.planId && plan(s.planId); const pe = pp && pp.items.find((it) => it.id === x.peId); restEnd = Date.now() + ((pe && pe.rest) || 90) * 1000; }
          A.refresh();
        },
        addSet(b) { A.training.addSet(s, b.dataset.v); A.refresh(); },
        delSet(b) { const list = s.sets.filter((k) => k.exId === b.dataset.v); s.sets.splice(s.sets.indexOf(list[list.length - 1]), 1); A.save(); A.refresh(); },
        addEx() { A.pickExercise((id) => { A.training.addExercise(s, id); A.refresh(); }); },
        discard() { A.confirm("Удалить тренировку? Запись в журнале тоже удалится.", () => { A.training.discard(s); restEnd = null; A.go("workouts/history", true); }, "Удалить", true); },
        rest(b) { const v = +b.dataset.v; if (!v) restEnd = null; else if (restEnd) restEnd += v * 1000; },
        // программа
        pf(b) { p[b.dataset.k] = b.value; A.save(); },
        day(b) { p.mask ^= 1 << +b.dataset.v; A.save(); A.refresh(); },
        glyph() { const o = p || e; glyphPicker(o.glyph, (g) => { o.glyph = g; A.save(); A.refresh(); }); },
        target(b) { targetSheet(p, p.items[+b.dataset.i]); },
        mv(b) { const i = +b.dataset.i, j = i + +b.dataset.v; if (j < 0 || j >= p.items.length) return; [p.items[i], p.items[j]] = [p.items[j], p.items[i]]; A.save(); A.refresh(); },
        addItem() { A.pickExercise((id) => { const k = (ex(id) || {}).kind || 0; const pe = { id: A.uid(), exId: id, sets: 3, reps: k === 1 ? 10 : 10, repMin: 8, repMax: 12, weight: 0, step: 2.5, seconds: k === 2 ? 30 : 0, rest: 90, maxSets: 5, note: "" }; p.items.push(pe); A.save(); A.refresh(); setTimeout(() => targetSheet(p, pe), 250); }); },
        archive() { p.archived = !p.archived; A.save(); A.refresh(); },
        delPlan() { A.confirm("Удалить программу «" + p.name + "»? История тренировок сохранится.", () => { A.remove("plans", p.id); A.go("workouts", true); }, "Удалить", true); },
        // упражнение
        ef(b) { e[b.dataset.k] = b.value; A.save(); },
        kind(b) { e.kind = +b.dataset.v; A.save(); A.refresh(); },
        muscle(b) { e.muscle = b.dataset.v; A.save(); A.refresh(); },
        async addMedia() { const url = await A.pickPhoto(1, 720); if (!url) return; const id = A.uid(); await A.photos.put(id, url); (e.media = e.media || []).push(id); A.save(); A.refresh(); },
        delMedia(b) { e.media = e.media.filter((m) => m !== b.dataset.v); A.photos.del(b.dataset.v); A.save(); A.refresh(); },
        delEx() { A.confirm("Удалить упражнение «" + e.name + "»?", () => { A.remove("exercises", e.id); A.col("plans").forEach((pp) => (pp.items = pp.items.filter((it) => it.exId !== e.id))); A.save(); A.go("workouts/ex", true); }, "Удалить", true); }
      });
      if (sec === "exercise" && r.params.new) setTimeout(() => { const i = el.querySelector('[data-k="name"]'); if (i) i.focus(); }, 100);
    }
  });

  // Завершение тренировки: оценка самочувствия, заметка, итоги прогрессии.
  A._finishSession = () => {
    const s = A.byId("sessions", A.route().args[1]); if (!s) return;
    let feel = 0;
    const w2 = A.sheet("Завершить тренировку?", '<div class="small muted">Как прошла?</div><div class="row" id="feel" style="justify-content:space-around;margin:8px 0">' + [1, 2, 3, 4, 5].map((i) => '<button type="button" data-f="' + i + '" style="font-size:30px;color:var(--ink2);padding:6px;border-radius:12px">{{i:' + faces[i] + "}}</button>").join("") + '</div><textarea id="fnote" rows="2" placeholder="Заметка"></textarea>' + (s.sets.some((x) => x.done) ? "" : '<p class="small" style="color:var(--bad)">Ни один подход не отмечен — цели программы не изменятся.</p>'), {
      buttons: [{ label: "Продолжить", cls: "ghost" }, { label: "Завершить", cls: "primary", onClick: (wr) => {
        restEnd = null;
        const notes = A.training.finish(s, feel, wr.querySelector("#fnote").value.trim());
        setTimeout(() => A.sheet("Тренировка сохранена", notes.length ? notes.map((n) => '<div class="card nocollapse" style="margin-bottom:8px"><b>' + esc(n.ex) + '</b><div class="small">Сделано: ' + esc(n.done) + '</div><div class="small" style="color:var(--accent)">В следующий раз: ' + esc(n.next) + '</div><div class="small muted">' + esc(n.reason) + "</div></div>").join("") : "<p>Записана в журнал тренировок и калории дня.</p>", { buttons: [{ label: "Готово", cls: "primary", onClick: () => A.go("workouts/history", true) }] }), 260);
      } }]
    });
    w2.querySelector("#feel").addEventListener("click", (ev) => { const b = ev.target.closest("[data-f]"); if (!b) return; feel = +b.dataset.f; w2.querySelectorAll("[data-f]").forEach((x) => (x.style.background = +x.dataset.f === feel ? "var(--accent2)" : "")); });
  };
})();
