/* Питание и КБЖУ, тренировки. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtDur, fmtShort, fmtDate, round } = A;
  A.edit = A.edit || {};

  /* ================= ПИТАНИЕ ================= */
  const calc = (f, g) => ({ kcal: round((f.kcal * g) / 100), p: round((f.p * g) / 100, 1), f: round((f.f * g) / 100, 1), c: round((f.c * g) / 100, 1), fib: round(((f.fib || 0) * g) / 100, 1) });

  A.edit.meal = (m) => {
    const isNew = !m.id;
    const foods = A.col("foods").slice().sort((a, b) => (b.used || 0) - (a.used || 0) || a.name.localeCompare(b.name));
    const body = '<div class="form"><div class="fld full"><label>Приём пищи</label>' + '<div class="chips" data-field="type" data-multi="0">' + A.MEALS.map(([k, n]) => '<button type="button" class="chip' + ((m.type || "breakfast") === k ? " on" : "") + '" data-v="' + k + '">' + n + "</button>").join("") + "</div></div>" +
      '<div class="fld full"><label>Продукт из базы</label><input id="fq" placeholder="Поиск: гречка, яйцо…" autocomplete="off"><div id="fl" class="list" style="max-height:200px;overflow:auto"></div></div>' +
      '<div class="fld"><label>Масса, г</label><input id="fg" type="number" inputmode="decimal" value="' + esc(m.g || 100) + '"></div><div class="fld"><label>Название</label><input id="fn" value="' + esc(m.name || "") + '"></div>' +
      '<div class="fsec full">КБЖУ порции (посчитается из базы или введите вручную)</div>' +
      ["kcal:Ккал", "p:Белки, г", "f:Жиры, г", "c:Углеводы, г", "fib:Клетчатка, г"].map((x) => { const [k, l] = x.split(":"); return '<div class="fld"><label>' + l + '</label><input id="m_' + k + '" type="number" inputmode="decimal" value="' + esc(m[k] ?? "") + '"></div>'; }).join("") + "</div>";
    let sel = m.foodId ? A.byId("foods", m.foodId) : null;
    const w = A.sheet(isNew ? "Добавить еду" : "Приём пищи", body, {
      buttons: [].concat(isNew ? [] : [{ label: "Удалить", cls: "danger ghost", onClick: () => { A.remove("meals", m.id); A.refresh(); } }], [{ label: "Сохранить", cls: "primary", onClick: (w) => {
        const g = A.num(w.querySelector("#fg").value, 0);
        const o = { id: m.id, date: m.date, type: w.querySelector('.chips[data-field="type"] .chip.on')?.dataset.v || "snack", foodId: sel ? sel.id : "", name: w.querySelector("#fn").value.trim() || (sel ? sel.name : "Еда"), g };
        ["kcal", "p", "f", "c", "fib"].forEach((k) => (o[k] = A.num(w.querySelector("#m_" + k).value, 0)));
        if (sel) sel.used = (sel.used || 0) + 1;
        A.upsert("meals", o); A.refresh();
      } }])
    });
    A.wireForm(w);
    const list = w.querySelector("#fl"), q = w.querySelector("#fq"), gi = w.querySelector("#fg");
    const fill = () => { if (!sel) return; const r = calc(sel, A.num(gi.value, 0)); ["kcal", "p", "f", "c", "fib"].forEach((k) => (w.querySelector("#m_" + k).value = r[k])); w.querySelector("#fn").value = sel.name; };
    const show = () => {
      const s = q.value.trim().toLowerCase();
      const res = foods.filter((f) => !s || f.name.toLowerCase().includes(s)).slice(0, 30);
      list.innerHTML = res.map((f) => '<div class="item" data-id="' + f.id + '" style="min-height:40px;padding:6px 2px;' + (sel && sel.id === f.id ? "background:var(--accent2);border-radius:8px" : "") + '"><div class="tx"><b>' + esc(f.name) + "</b><small>" + f.kcal + " ккал · Б " + f.p + " Ж " + f.f + " У " + f.c + " на 100 г</small></div></div>").join("") || '<div class="small muted">Нет в базе — введите КБЖУ вручную или добавьте продукт в базу.</div>';
    };
    list.addEventListener("click", (e) => { const it = e.target.closest("[data-id]"); if (!it) return; sel = A.byId("foods", it.dataset.id); fill(); show(); });
    q.addEventListener("input", show); gi.addEventListener("input", fill);
    show();
  };
  A.edit.food = (f) => {
    const isNew = !f;
    A.formSheet(isNew ? "Новый продукт" : "Продукт", [
      { k: "name", label: "Название", req: true, full: true },
      { k: "kcal", label: "Ккал на 100 г", type: "number", req: true }, { k: "p", label: "Белки", type: "number" },
      { k: "f", label: "Жиры", type: "number" }, { k: "c", label: "Углеводы", type: "number" }, { k: "fib", label: "Клетчатка", type: "number" }
    ], f || {}, (o) => { const x = Object.assign(f || {}, o); ["kcal", "p", "f", "c", "fib"].forEach((k) => (x[k] = x[k] || 0)); A.upsert("foods", x); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("foods", f.id); A.refresh(); } });
  };

  A.view("food", {
    title: "Питание",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.go(\'food/base\')" aria-label="База продуктов" title="База продуктов">📋</button>'; },
    render(el, r) {
      const p = A.db().profile;
      if (r.args[0] === "base") {
        const fs = A.col("foods").slice().sort((a, b) => a.name.localeCompare(b.name));
        return '<div class="card"><h3>База продуктов<span class="sp"></span><button class="btn sm primary" data-a="newFood">+ продукт</button></h3><p class="small muted">Значения на 100 г.</p><div class="list">' + fs.map((f) => '<div class="item" data-a="food" data-id="' + f.id + '"><div class="tx"><b>' + esc(f.name) + "</b><small>" + f.kcal + " ккал · Б " + f.p + " · Ж " + f.f + " · У " + f.c + (f.fib ? " · Кл " + f.fib : "") + "</small></div></div>").join("") + "</div></div>";
      }
      const d = r.params.d || today(), m = A.macrosDay(d);
      let h = A.dateNav(d);
      const row = (n, v, g, c) => '<div style="margin:6px 0"><div class="row small"><span class="grow">' + n + "</span><b>" + fmtN(v, 0) + " / " + g + " г</b></div>" + A.bar(v / g, c) + "</div>";
      h += '<div class="card"><div class="row">' + A.ring(m.kcal / p.kcal, fmtN(m.kcal), "/" + p.kcal + " ккал", "var(--peach)") + '<div class="grow">' + row("Белки", m.p, p.prot, "var(--sage)") + row("Жиры", m.f, p.fat, "var(--peach)") + row("Углеводы", m.c, p.carb, "var(--gold)") + row("Клетчатка", m.fib, p.fiber, "var(--sky)") + "</div></div>" +
        '<p class="small muted">План/факт: осталось ' + fmtN(Math.max(0, p.kcal - m.kcal)) + " ккал. Цели меняются в настройках. Приложение не назначает диеты.</p></div>";
      A.MEALS.forEach(([k, n]) => {
        const ms = A.col("meals").filter((x) => x.date === d && x.type === k);
        h += '<div class="card"><h3>' + n + '<span class="sp"></span><small>' + fmtN(A.sum(ms.map((x) => x.kcal))) + ' ккал</small><button class="btn sm" data-a="add" data-t="' + k + '">+</button></h3>' + ms.map((x) => '<div class="item" data-a="meal" data-id="' + x.id + '"><div class="tx"><b>' + esc(x.name) + "</b><small>" + fmtN(x.g) + " г · Б " + fmtN(x.p, 1) + " Ж " + fmtN(x.f, 1) + " У " + fmtN(x.c, 1) + '</small></div><b class="num">' + fmtN(x.kcal) + "</b></div>").join("") + "</div>";
      });
      const days = A.lastDays(7, d), days30 = A.lastDays(30, d);
      h += '<div class="card"><h3>Калории за неделю</h3>' + A.charts.bars(days.map((x) => ({ x: fmtShort(x).slice(0, 2), v: A.kcalDay(x) })), { goal: p.kcal, color: "#EFA984" }) + "</div>";
      const logged = days30.filter((x) => A.kcalDay(x) != null);
      const av = (k) => A.avg(logged.map((x) => A.macrosDay(x)[k]));
      h += '<div class="card"><h3>Среднее за 30 дней <small>(' + logged.length + " дн. с записями)</small></h3><div class=\"grid2\"><div class=\"stat\"><small>Ккал</small><b>" + fmtN(av("kcal")) + '</b></div><div class="stat"><small>Белки</small><b>' + fmtN(av("p")) + ' г</b></div><div class="stat"><small>Жиры</small><b>' + fmtN(av("f")) + ' г</b></div><div class="stat"><small>Углеводы</small><b>' + fmtN(av("c")) + " г</b></div></div></div>";
      return h;
    },
    bind(el, r) {
      const d = () => A.route().params.d || today();
      A.bind(el, {
        dnav(b) { if (b.dataset.v === "pick") return A.pickDate(d(), (v) => A.go("food?d=" + v, true)); A.go("food?d=" + addDays(d(), +b.dataset.v), true); },
        add(b) { A.edit.meal({ date: d(), type: b.dataset.t, g: 100 }); },
        meal(b) { A.edit.meal(A.byId("meals", b.dataset.id)); },
        newFood() { A.edit.food(); }, food(b) { A.edit.food(A.byId("foods", b.dataset.id)); }
      });
    }
  });

  /* ================= ТРЕНИРОВКИ ================= */
  A.wType = (k) => (A.WORKOUT_TYPES.find((x) => x[0] === k) || [0, "Тренировка"])[1];
  const volume = (w) => A.sum((w.sets || []).map((s) => (+s.reps || 0) * (+s.w || 0)));

  A.edit.workout = (w) => {
    const isNew = !w || !w.id;
    w = Object.assign({ date: today(), type: "strength", dur: 45, int: 5, dist: "", pace: "", note: "", sets: [] }, w || {});
    const sets = (w.sets || []).map((s) => Object.assign({}, s));
    const exOpts = () => A.col("exercises").map((e) => '<option value="' + e.id + '">' + esc(e.name) + "</option>").join("");
    const fields = [
      { k: "type", label: "Тип", type: "chips", opts: A.WORKOUT_TYPES }, { k: "date", label: "Дата", type: "date" },
      { k: "dur", label: "Длительность, мин", type: "number" }, { k: "int", label: "Интенсивность 1–10", type: "range", min: 1, max: 10 },
      { k: "dist", label: "Расстояние, км", type: "number", step: 0.01 }, { k: "pace", label: "Темп, мин/км", ph: "6:30" },
      { k: "kcal", label: "Сожжено ккал (если известно)", type: "number" }, { k: "note", label: "Комментарий", type: "textarea" }
    ];
    const setsHtml = () => '<div class="fsec">Упражнения и подходы</div><div id="sets">' + sets.map((s, i) => '<div class="row" style="gap:6px;margin:6px 0"><select data-i="' + i + '" data-f="ex" style="flex:2">' + exOpts().replace('value="' + s.ex + '"', 'value="' + s.ex + '" selected') + '</select><input data-i="' + i + '" data-f="reps" type="number" placeholder="повт." value="' + esc(s.reps ?? "") + '" style="flex:1"><input data-i="' + i + '" data-f="w" type="number" placeholder="кг" value="' + esc(s.w ?? "") + '" style="flex:1"><button class="icon-btn" data-del="' + i + '" aria-label="Удалить подход">✕</button></div>').join("") + '</div><div class="btns"><button class="btn sm" id="addSet">+ подход</button><button class="btn sm ghost" id="copySet">повторить последний</button></div>';
    const form = A.formSheet(isNew ? "Тренировка" : "Тренировка · " + fmtShort(w.date), fields, w, (o) => {
      Object.assign(w, o);
      w.sets = sets.filter((s) => s.ex);
      A.upsert("workouts", w); A.refresh();
    }, {
      post: '<div id="setsBox"></div>',
      onDelete: isNew ? null : () => { A.remove("workouts", w.id); A.refresh(); },
      after(wr) {
        const box = wr.querySelector("#setsBox");
        const draw = () => { box.innerHTML = setsHtml(); };
        draw();
        box.addEventListener("input", (e) => { const t = e.target; if (t.dataset.i == null) return; sets[+t.dataset.i][t.dataset.f] = t.dataset.f === "ex" ? t.value : A.num(t.value, null); });
        box.addEventListener("change", (e) => { const t = e.target; if (t.dataset.f === "ex") sets[+t.dataset.i].ex = t.value; });
        box.addEventListener("click", (e) => {
          if (e.target.id === "addSet") { const ex = A.col("exercises")[0]; sets.push({ ex: ex ? ex.id : "", reps: 10, w: "" }); draw(); }
          if (e.target.id === "copySet" && sets.length) { sets.push(Object.assign({}, sets[sets.length - 1])); draw(); }
          if (e.target.dataset.del != null) { sets.splice(+e.target.dataset.del, 1); draw(); }
        });
      }
    });
    return form;
  };
  A.edit.exercise = (e) => A.formSheet(e ? "Упражнение" : "Новое упражнение", [{ k: "name", label: "Название", req: true, full: true }, { k: "muscle", label: "Группа мышц / тип", full: true }, { k: "howto", label: "Техника, подсказки", type: "textarea" }], e || {}, (o) => { A.upsert("exercises", Object.assign(e || {}, o)); A.refresh(); }, e ? { onDelete: () => { A.remove("exercises", e.id); A.refresh(); } } : {});

  A.view("workouts", {
    title: "Тренировки",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.workout()" aria-label="Добавить">＋</button>'; },
    render(el, r) {
      const tab = r.args[0] || "log";
      let h = A.seg([["log", "Журнал"], ["stats", "Статистика"], ["lib", "Упражнения"]], tab, "tab");
      const ws = A.col("workouts").slice().sort((a, b) => (b.date > a.date ? 1 : -1));
      const exName = (id) => (A.byId("exercises", id) || { name: "?" }).name;
      if (tab === "log") {
        // календарь месяца
        const mk = today().slice(0, 7), [y, m] = mk.split("-").map(Number);
        h += '<div class="card"><h3>' + esc(A.monthTitle(mk)) + '</h3><div class="month">' + A.DOW.map((x) => '<div class="h">' + x + "</div>").join("");
        const start = A.weekStart(mk + "-01");
        for (let i = 0; i < 42; i++) { const d = addDays(start, i); if (i >= 35 && d.slice(0, 7) !== mk) break; const n = ws.filter((w) => w.date === d); h += '<div class="mday' + (d.slice(0, 7) !== mk ? " out" : "") + (d === today() ? " now" : "") + '" style="' + (n.length ? "background:var(--sage2)" : "") + '">' + A.parse(d).getDate() + (n.length ? '<small style="font-size:10px">' + n.map((w) => (w.type === "strength" ? "🏋" : w.type === "yoga" ? "🧘" : w.type === "walk" ? "🚶" : w.type === "run" || w.type === "cardio" ? "🏃" : "✦")).join("") + "</small>" : "") + "</div>"; }
        h += "</div></div>";
        h += '<div class="card"><div class="list">' + (ws.length ? ws.slice(0, 60).map((w) => '<div class="item" data-a="w" data-id="' + w.id + '"><div class="ic">🏋</div><div class="tx"><b>' + esc(A.wType(w.type)) + " · " + fmtDate(w.date) + "</b><small>" + fmtDur(w.dur) + " · интенсивность " + (w.int || "—") + (w.dist ? " · " + w.dist + " км" : "") + (w.sets && w.sets.length ? " · " + [...new Set(w.sets.map((s) => exName(s.ex)))].slice(0, 3).join(", ") : "") + "</small></div>" + (volume(w) ? '<small class="num">' + fmtN(volume(w)) + " кг</small>" : "") + "</div>").join("") : A.empty("Тренировок пока нет. Минимальная версия тоже считается: прогулка или короткая зарядка.")) + "</div></div>";
      } else if (tab === "stats") {
        const weeks = []; let ws0 = A.weekStart(today());
        for (let i = 11; i >= 0; i--) { const a = addDays(ws0, -7 * i), b = addDays(a, 6); const list = ws.filter((w) => w.date >= a && w.date <= b); weeks.push({ x: fmtShort(a), n: list.length, dur: A.sum(list.map((w) => w.dur || 0)), vol: A.sum(list.map(volume)) }); }
        h += '<div class="card"><h3>Частота (тренировок в неделю)</h3>' + A.charts.bars(weeks.map((w) => ({ x: w.x, v: w.n })), { goal: A.db().profile.workoutsWeek, color: "#7FAE8A", labelsEvery: 3 }) + "</div>";
        h += '<div class="card"><h3>Длительность, мин в неделю</h3>' + A.charts.bars(weeks.map((w) => ({ x: w.x, v: w.dur })), { color: "#9C8FD0", labelsEvery: 3 }) + "</div>";
        h += '<div class="card"><h3>Объём (повторы × вес), кг</h3>' + A.charts.bars(weeks.map((w) => ({ x: w.x, v: w.vol })), { color: "#E0A15E", labelsEvery: 3 }) + "</div>";
        const mk = today().slice(0, 7), mw = ws.filter((w) => w.date.slice(0, 7) === mk);
        const types = {}; mw.forEach((w) => (types[w.type] = (types[w.type] || 0) + 1));
        h += '<div class="grid3"><div class="stat"><small>В этом месяце</small><b>' + mw.length + '</b></div><div class="stat"><small>Общее время</small><b>' + fmtDur(A.sum(mw.map((w) => w.dur || 0))) + '</b></div><div class="stat"><small>Средняя длит.</small><b>' + fmtDur(A.avg(mw.map((w) => w.dur || 0))) + "</b></div></div>";
        if (mw.length) h += '<div class="card" style="margin-top:12px"><h3>Типы</h3>' + A.charts.donut(Object.entries(types).map(([k, v], i) => ({ name: A.wType(k), v, color: ["#7FAE8A", "#9C8FD0", "#E0A15E", "#6FA4C9", "#E58C9C", "#C4A77D", "#8DBF9E", "#8E7CC3", "#A9A3B5"][i % 9] })), mw.length, "тренировок") + "</div>";
      } else {
        h += '<div class="card"><h3>Библиотека упражнений<span class="sp"></span><button class="btn sm primary" data-a="newEx">+</button></h3><div class="list">' + A.col("exercises").map((e) => { const n = A.sum(ws.map((w) => (w.sets || []).filter((s) => s.ex === e.id).length)); const best = Math.max(0, ...ws.flatMap((w) => (w.sets || []).filter((s) => s.ex === e.id).map((s) => +s.w || 0))); return '<div class="item" data-a="ex" data-id="' + e.id + '"><div class="tx"><b>' + esc(e.name) + "</b><small>" + esc(e.muscle || "") + (n ? " · подходов: " + n : "") + (best ? " · лучший вес " + best + " кг" : "") + "</small></div></div>"; }).join("") + "</div></div>";
      }
      return h;
    },
    bind(el, r) {
      A.bind(el, {
        tab(b) { A.go("workouts/" + b.dataset.v, true); },
        w(b) { A.edit.workout(A.byId("workouts", b.dataset.id)); },
        newEx() { A.edit.exercise(); }, ex(b) { A.edit.exercise(A.byId("exercises", b.dataset.id)); }
      });
    }
  });
})();
