/* Статистика: обзор недели/месяца, сравнительный анализ, месячный отчёт, год на одной странице, PRIME SCORE. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtDur, fmtShort, sum, avg, pad, money } = A;

  const monthRange = (mk) => { const [y, m] = mk.split("-").map(Number); const a = mk + "-01", b = mk + "-" + pad(A.daysInMonth(y, m)); return [a, b > today() ? today() : b, b]; };

  // Сводка за период: всё, что входит в месячный отчёт.
  A.periodSummary = (from, to) => {
    const days = A.range(from, to);
    const mv = (k) => days.map(A.METRICS[k].get);
    const txs = A.col("tx").filter((t) => t.date >= from && t.date <= to);
    const ws = A.col("workouts").filter((w) => w.date >= from && w.date <= to);
    const habitsPct = avg(days.map(A.habitsDayPct).filter((x) => x != null));
    const learnMin = sum(A.col("learn").filter((x) => x.date >= from && x.date <= to).map((x) => x.min));
    const byLearn = (s) => sum(A.col("learn").filter((x) => x.date >= from && x.date <= to && x.subject === s).map((x) => x.min));
    const md = days.map(A.macrosDay).filter((m) => m.n);
    return {
      days: days.length,
      sleep: avg(mv("sleep")), mood: avg(mv("mood")), energy: avg(mv("energy")), stress: avg(mv("stress")), anx: avg(mv("anx")),
      steps: avg(mv("steps")), stepsSum: sum(mv("steps")), water: avg(mv("water")),
      workouts: ws.length, workoutMin: sum(ws.map((w) => w.dur || 0)),
      kcal: avg(md.map((m) => m.kcal)), p: avg(md.map((m) => m.p)), f: avg(md.map((m) => m.f)), c: avg(md.map((m) => m.c)),
      habits: habitsPct,
      books: A.col("books").filter((b) => b.end && b.end >= from && b.end <= to).length,
      pages: sum(mv("pages")), readMin: sum(mv("read")),
      movies: A.col("movies").filter((m) => m.date >= from && m.date <= to).length,
      episodes: sum(A.col("series").map((s) => (s.log || []).filter((d) => d >= from && d <= to).length)),
      learnMin, piano: sum(mv("piano")), tarot: byLearn("Таро"), bazi: byLearn("Бацзы"), ketu: byLearn("Кету"),
      tarotCards: Object.keys(A.db().tarot.daily).filter((d) => d >= from && d <= to).length,
      income: sum(txs.filter((t) => t.kind === "in").map((t) => t.amt)), expense: sum(txs.filter((t) => t.kind === "out").map((t) => t.amt)),
      savings: sum(A.col("savings").map((g) => +g.cur || 0)),
      hobbyMin: sum(A.col("hsess").filter((x) => x.date >= from && x.date <= to).map((x) => x.min)),
      prime: avg(days.map((d) => A.primeScore(d).score).filter((x) => x != null)),
      tasksDone: A.col("tasks").filter((t) => t.done && (t.doneAt || "").slice(0, 10) >= from && (t.doneAt || "").slice(0, 10) <= to).length,
      habitsDone: Object.entries(A.db().hlog).filter(([k, v]) => v === "done" && k.split("|")[1] >= from && k.split("|")[1] <= to).length,
      goalsDone: A.col("goals").filter((g) => g.doneAt && g.doneAt >= from && g.doneAt <= to).length,
      cooked: A.col("cookLog").filter((x) => x.date >= from && x.date <= to).length,
      weightCh: (() => { const p = A.weightPoints(from, to); return p.length > 1 ? p[p.length - 1].v - p[0].v : null; })(),
      burn: sum(days.map(A.burnDay))
    };
  };

  const tile = (ic, name, val, sub) => '<div class="stat"><small>' + ic + " " + esc(name) + "</small><b>" + val + "</b>" + (sub ? '<small>' + sub + "</small>" : "") + "</div>";
  const summaryTiles = (s) => '<div class="grid3">' + [
    tile("🌙", "Сон, сред.", s.sleep != null ? fmtDur(s.sleep * 60) : "—"), tile("💜", "Настроение", fmtN(s.mood, 1)), tile("⚡", "Энергия", fmtN(s.energy, 1)),
    tile("🌀", "Стресс", fmtN(s.stress, 1)), tile("🌫", "Тревожность", fmtN(s.anx, 1)), tile("👟", "Шаги, сред.", fmtN(s.steps)),
    tile("🏋", "Тренировки", s.workouts, fmtDur(s.workoutMin)), tile("🍎", "Калории, сред.", fmtN(s.kcal)), tile("🥗", "Б/Ж/У, сред.", s.kcal ? fmtN(s.p) + "/" + fmtN(s.f) + "/" + fmtN(s.c) : "—"),
    tile("💧", "Вода, сред.", s.water != null ? fmtN(s.water) + " мл" : "—"), tile("✅", "Привычки", s.habits != null ? Math.round(s.habits * 100) + "%" : "—"), tile("📖", "Книги / стр.", s.books + " / " + fmtN(s.pages)),
    tile("🎬", "Фильмы / серии", s.movies + " / " + s.episodes), tile("🎓", "Обучение", fmtDur(s.learnMin + s.piano)), tile("🎹", "Пианино", fmtDur(s.piano)),
    tile("🃏", "Таро", fmtDur(s.tarot), s.tarotCards + " карт дня"), tile("☯", "Бацзы", fmtDur(s.bazi)), tile("🌿", "Кету", fmtDur(s.ketu)),
    tile("⬆", "Доходы", money(s.income)), tile("⬇", "Расходы", money(s.expense)), tile("🏦", "Накопления", money(s.savings))
  ].join("") + "</div>";

  const PAIRS = [
    ["sleep", "mood", "Сон ↔ настроение"], ["sleep", "energy", "Сон ↔ энергия"], ["steps", "energy", "Шаги ↔ энергия"], ["workout", "mood", "Тренировки ↔ настроение"],
    ["stress", "sleep", "Стресс ↔ сон"], ["anx", "sleep", "Тревожность ↔ сон"], ["temp", "steps", "Погода (температура) ↔ шаги"], ["read", "free", "Чтение ↔ свободное время"],
    ["piano", "mood", "Практика пианино ↔ настроение"], ["learn", "energy", "Учёба ↔ энергия"], ["habits", "mood", "Привычки ↔ настроение"], ["water", "energy", "Вода ↔ энергия"]
  ];

  const TABS = [["day", "День"], ["week", "Неделя"], ["month", "Месяц"], ["year", "Год"], ["corr", "Связи"], ["prime", "PRIME SCORE"]];

  A.view("stats", {
    title: "Статистика", tab: "more",
    actions(el, r) { el.innerHTML = '<button class="icon-btn" onclick="App.print(\'Путь жизни — отчёт\')" aria-label="Печать / PDF" title="Печать / PDF">⎙</button>'; },
    render(el, r) {
      const tab = r.args[0] || "month";
      let h = '<div class="noprint">' + A.seg(TABS, tab, "tab") + "</div>";
      const t = today();
      if (tab === "week") {
        const ws = r.params.w || A.weekStart(t), we = addDays(ws, 6);
        const s = A.periodSummary(ws, we > t ? t : we);
        h += '<div class="datenav noprint"><button class="icon-btn" data-a="wnav" data-v="-7">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + fmtShort(ws) + " – " + fmtShort(we) + '</div><button class="icon-btn" data-a="wnav" data-v="7">›</button></div>';
        h += summaryTiles(s);
        const days = A.range(ws, we);
        h += '<div class="card" style="margin-top:12px"><h3>Состояние по дням</h3>' + A.charts.line([{ name: "Настроение", color: "#8CC474", data: days.map((d) => ({ x: A.DOW[A.dow(d) - 1], v: A.METRICS.mood.get(d) })) }, { name: "Энергия", color: "#F0A45B", data: days.map((d) => ({ x: A.DOW[A.dow(d) - 1], v: A.energyAvg(d) })) }, { name: "Стресс", color: "#D2555E", data: days.map((d) => ({ x: A.DOW[A.dow(d) - 1], v: A.METRICS.stress.get(d) })) }], { min: 0, max: 10 }) + "</div>";
        const pr = A.periodSummary(addDays(ws, -7), addDays(ws, -1));
        const arrow = (a, b2, inv) => a == null || b2 == null ? "" : ' <span class="trend ' + ((a > b2) !== !!inv ? "down" : "up") + '" style="font-size:14px">' + (a > b2 ? "↑" : a < b2 ? "↓" : "→") + "</span>";
        const X = (d) => A.DOW[A.dow(d) - 1];
        h += '<div class="card"><h3>👟 Тренд шагов' + arrow(s.steps, pr.steps, true) + '</h3>' + A.charts.bars(days.map((d) => ({ x: X(d), v: A.METRICS.steps.get(d) })), { goal: A.db().profile.stepsGoal, color: "#5FA774" }) + '<p class="small muted">Среднее ' + fmtN(s.steps) + " (прошлая неделя " + fmtN(pr.steps) + ")</p></div>";
        h += '<div class="card"><h3>🍎 Тренд калорий</h3>' + A.charts.bars(days.map((d) => ({ x: X(d), v: A.kcalDay(d) })), { goal: A.db().profile.kcal, color: "#EFA984" }) + '<p class="small muted">Среднее ' + fmtN(s.kcal) + " · потрачено активностью за неделю " + fmtN(s.burn) + " ккал</p></div>";
        const wt = A.weightTrend(7, we > t ? t : we);
        h += '<div class="card"><h3>⚖ Тренд веса <span class="trend ' + (wt.arrow === "↑" ? "up" : wt.arrow === "↓" ? "down" : "flat") + '" style="font-size:16px">' + wt.arrow + "</span></h3>" + A.charts.line([{ name: "Вес", color: "#6C9FD3", data: days.map((d) => ({ x: X(d), v: (A.dayGet(d) || {}).weight ?? null })) }]) + '<p class="small muted">' + esc(wt.word) + (s.weightCh != null ? " · за неделю " + (s.weightCh > 0 ? "+" : "") + s.weightCh.toFixed(1) + " кг" : "") + "</p></div>";
        h += '<div class="card"><h3>🌙 Тренд сна' + arrow(s.sleep, pr.sleep) + '</h3>' + A.charts.bars(days.map((d) => ({ x: X(d), v: A.sleepH(d) })), { goal: A.db().profile.sleepGoal || 8, color: "#9C8FD8", fmt: (v) => fmtDur(v * 60) }) + "</div>";
        h += '<div class="card"><h3>✅ Тренд привычек' + arrow(s.habits, pr.habits) + '</h3>' + A.charts.bars(days.map((d) => ({ x: X(d), v: A.METRICS.habits.get(d) })), { goal: 100, color: "#7F9C7A", fmt: (v) => v + "%" }) + "</div>";
        h += highlightsCard(s, ws, we, "Достижения недели");
      } else if (tab === "month") {
        const mk = r.params.m || t.slice(0, 7);
        const [from, to] = monthRange(mk);
        const s = A.periodSummary(from, to);
        const R = A.db().reviews[mk] || {};
        h += '<div class="datenav noprint"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
        h += '<div class="card hero"><div class="small muted">Месяц в одном экране</div><div class="date">' + esc(A.monthTitle(mk)) + "</div>" + (s.prime != null ? '<div class="tag" style="font-size:13px;padding:4px 12px;margin-top:8px">◈ PRIME SCORE ' + Math.round(s.prime) + "</div>" : "") + '<div class="phrase">' + esc(A.db().profile.principle) + "</div></div>";
        h += summaryTiles(s);
        // распределение времени по категориям календаря
        const cats = {}; A.range(from, monthRange(mk)[2]).forEach((d) => A.eventsOn(d).forEach((e) => (cats[e.cat] = (cats[e.cat] || 0) + A.eventMin(e))));
        const totalH = sum(Object.values(cats)) / 60;
        if (totalH) h += '<div class="card" style="margin-top:12px"><h3>Распределение времени за месяц (по плану)</h3>' + A.charts.donut(Object.entries(cats).sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ name: A.cat(k).name, v, color: A.cat(k).color })), fmtN(totalH), "часов") + "</div>";
        h += '<div class="card"><h3>Настроение месяца</h3>' + A.charts.bars(A.range(from, to).map((d) => ({ x: String(A.parse(d).getDate()), v: A.METRICS.mood.get(d), c: A.METRICS.mood.get(d) != null ? A.scaleColor(A.METRICS.mood.get(d)) : null })), { color: "#8CC474", labelsEvery: 5 }) + "</div>";
        const hs = A.col("habits").filter((x) => !x.archived);
        h += '<div class="card"><h3>Привычки-опоры</h3>' + hs.map((x) => { const st = A.habitStats(x, from, to); return '<div style="margin:6px 0"><div class="row small"><span class="grow">' + esc((x.icon || "") + " " + x.name) + "</span><b>" + (st.pct == null ? "—" : Math.round(st.pct * 100) + "%") + "</b></div>" + A.bar(st.pct || 0, "var(--sage)") + "</div>"; }).join("") + "</div>";
        h += highlightsCard(s, from, to, "Ключевые достижения месяца");
        h += reflectCard(mk);
        if (R.best || R.improve || R.change) h += '<div class="card tint"><h3>Рефлексия</h3>' + [["Лучше всего", R.best], ["Улучшить", R.improve], ["Изменить", R.change]].filter((x) => x[1]).map((x) => "<p><b>" + x[0] + ":</b> " + esc(x[1]) + "</p>").join("") + "</div>";
        h += '<div class="btns noprint"><a class="btn" href="#/goals/review?m=' + mk + '">Заполнить рефлексию</a><button class="btn primary" data-a="print">Печать / PDF</button></div>';
      } else if (tab === "year") {
        const y = +(r.params.y || t.slice(0, 4));
        h += '<div class="datenav noprint"><button class="icon-btn" data-a="ynav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">Год на одной странице · ' + y + '</div><button class="icon-btn" data-a="ynav" data-v="1">›</button></div>';
        const months = [];
        for (let m = 1; m <= 12; m++) { const mk = y + "-" + pad(m); if (mk > t.slice(0, 7)) { months.push(null); continue; } const [a, b] = monthRange(mk); months.push(A.periodSummary(a, b)); }
        const chart = (name, key, color, fmt, goal) => '<div class="card"><h3>' + name + "</h3>" + A.charts.bars(months.map((s, i) => ({ x: A.MON_SHORT[i], v: s ? s[key] : null })), { color, fmt, goal }) + "</div>";
        const ys = A.periodSummary(y + "-01-01", (y + "-12-31") > t ? t : y + "-12-31");
        h += summaryTiles(ys);
        h += '<div style="margin-top:12px"></div>' + highlightsCard(ys, y + "-01-01", y + "-12-31", "Ключевые достижения года") + reflectCard(String(y));
        h += '<div class="print-grid" style="margin-top:12px">' + chart("Сон, ч", "sleep", "#9C8FD8", (v) => fmtDur(v * 60), A.db().profile.sleepMin) + chart("Настроение", "mood", "#8CC474") + chart("Шаги, сред.", "steps", "#5FA774", null, A.db().profile.stepsGoal) + chart("Тренировки", "workouts", "#7FAE8A") +
          chart("Привычки, доля", "habits", "#C9A45C", (v) => Math.round(v * 100) + "%") + chart("Обучение, мин", "learnMin", "#6FA4C9") + chart("Чтение, страниц", "pages", "#EFA984") + chart("Расходы", "expense", "#E3899A", money) + "</div>";
        h += '<div class="card"><h3>Год по месяцам</h3><div style="overflow-x:auto"><table class="tbl"><tr><th></th>' + A.MON_SHORT.map((m) => '<th class="r">' + m + "</th>").join("") + "</tr>" +
          [["Сон", "sleep", 1], ["Настр.", "mood", 1], ["Энергия", "energy", 1], ["Шаги", "steps", 0], ["Трен.", "workouts", 0], ["Книги", "books", 0], ["Доход", "income", 0], ["Расход", "expense", 0]].map(([n, k, p]) => "<tr><td>" + n + "</td>" + months.map((s) => '<td class="r">' + (s && s[k] != null ? fmtN(s[k] >= 10000 ? s[k] / 1000 : s[k], p) + (s[k] >= 10000 ? "к" : "") : "·") + "</td>").join("") + "</tr>").join("") + "</table></div></div>";
      } else if (tab === "corr") {
        const n = +(r.params.n || 90);
        const days = A.lastDays(n);
        h += A.seg([["30", "30 дней"], ["90", "90 дней"], ["365", "Год"]], String(n), "n");
        h += '<div class="card tint"><p class="small">Сравнительный анализ по коэффициенту корреляции Пирсона. Формулировки нейтральные: «наблюдается связь» — не значит, что одно вызывает другое.</p></div>';
        h += '<div class="card"><div class="list">' + PAIRS.map(([a, b, name]) => {
          const res = A.pearson(days.map(A.METRICS[a].get), days.map(A.METRICS[b].get));
          const pre = res.n < 14;
          return '<div class="item"><div class="tx"><b>' + esc(name) + "</b><small>" + esc(A.corrWords(res.r)) + " · пар данных: " + res.n + (pre && res.n >= 3 ? " · ⚠ предварительно, данных мало" : "") + "</small></div>" + (res.r != null ? '<b class="num" style="color:' + (Math.abs(res.r) >= 0.3 ? "var(--accent)" : "var(--ink2)") + '">' + (res.r > 0 ? "+" : "") + res.r.toFixed(2) + "</b>" : "") + "</div>";
        }).join("") + "</div></div>";
        const out = {}; A.col("tx").filter((x) => x.kind === "out" && x.date >= days[0]).forEach((x) => (out[x.cat] = (out[x.cat] || 0) + x.amt));
        if (Object.keys(out).length) h += '<div class="card"><h3>Расходы ↔ категории</h3>' + A.charts.donut(Object.entries(out).sort((a, b) => b[1] - a[1]).map(([k, v], i) => ({ name: k, v, color: ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6", "#B6A6D9", "#9CC5A1", "#F3C29F", "#D8C08A", "#EBB0BC", "#A9CBE5", "#C9C3D3"][i % 13] })), fmtN(sum(Object.values(out))), "₽") + "</div>";
        const P = A.db().piano, ps = A.col("psess");
        h += '<div class="card"><h3>Практика пианино ↔ прогресс</h3><p class="small">Минут практики: <b>' + fmtN(sum(ps.map((s) => s.min))) + "</b> · пройдено уроков: <b>" + Object.keys(P.done).length + "</b>" + (Object.keys(P.done).length ? " · в среднем " + fmtN(sum(ps.map((s) => s.min)) / Object.keys(P.done).length) + " мин на урок" : "") + "</p></div>";
        const subj = {}; A.col("learn").forEach((x) => (subj[x.subject] = (subj[x.subject] || 0) + x.min));
        if (Object.keys(subj).length) h += '<div class="card"><h3>Учёба ↔ затраченное время</h3>' + Object.entries(subj).sort((a, b) => b[1] - a[1]).map(([k, v]) => '<div class="row"><span class="grow">' + esc(k) + "</span><b>" + fmtDur(v) + "</b></div>").join("") + "</div>";
      } else if (tab === "prime") {
        const d = r.params.d || t, ps = A.primeScore(d), w = A.db().profile.weights;
        h += A.dateNav(d);
        h += '<div class="card hero"><div class="small muted">PRIME SCORE</div><div class="big" style="font-size:46px">' + (ps.score ?? "—") + '</div><div class="phrase">Прозрачный показатель выполнения выбранных тобой областей. Он не оценивает ценность или личность человека.</div></div>';
        h += '<div class="card"><h3>Формула</h3><div class="formula">PRIME SCORE = Σ (вес × балл области) ÷ Σ весов<br>учитываются только области с данными за день</div>' +
          '<table class="tbl" style="margin-top:8px"><tr><th>Область</th><th class="r">Вес</th><th class="r">Балл</th></tr>' + ps.parts.map((p) => "<tr><td>" + esc(p.name) + '<br><small class="muted">' + esc(A.PS_AREAS.find((a) => a[0] === p.k)[2]) + '</small></td><td class="r"><select data-c="w" data-k="' + p.k + '" style="width:64px;min-height:34px;padding:2px 6px">' + [0, 1, 2, 3, 4, 5].map((i) => "<option" + (+w[p.k] === i ? " selected" : "") + ">" + i + "</option>").join("") + '</select></td><td class="r">' + (p.v == null ? '<span class="muted">нет данных</span>' : p.v) + "</td></tr>").join("") + "</table>" +
          (ps.sw ? '<div class="formula" style="margin-top:8px">' + ps.parts.filter((p) => p.v != null && p.w > 0).map((p) => p.w + "×" + p.v).join(" + ") + " = " + fmtN(sum(ps.parts.filter((p) => p.v != null && p.w > 0).map((p) => p.w * p.v))) + "<br>÷ " + ps.sw + " = <b>" + ps.score + "</b></div>" : '<p class="small muted">За этот день нет данных в выбранных областях.</p>') + '<p class="small muted">Вес 0 — область не учитывается. Показатель опциональный, его можно скрыть с экрана «Сегодня».</p></div>';
        const days = A.lastDays(30, d);
        h += '<div class="card"><h3>30 дней</h3>' + A.charts.bars(days.map((x) => ({ x: fmtShort(x).slice(0, 2), v: A.primeScore(x).score })), { color: "#8E7CC3" }) + "</div>";
      }
      return h;
    },
    bind(el, r) {
      const tab = r.args[0] || "month", P = A.route().params;
      A.bind(el, {
        tab(b) { if (b.dataset.v === "day") return A.go("summary"); A.go("stats/" + b.dataset.v, true); },
        wnav(b) { A.go("stats/week?w=" + addDays(P.w || A.weekStart(today()), +b.dataset.v), true); },
        mnav(b) { let [y, m] = (P.m || today().slice(0, 7)).split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } A.go("stats/month?m=" + y + "-" + pad(m), true); },
        ynav(b) { A.go("stats/year?y=" + (+(P.y || today().slice(0, 4)) + +b.dataset.v), true); },
        n(b) { A.go("stats/corr?n=" + b.dataset.v, true); },
        print() { A.print("Месяц в одном экране"); },
        dnav(b) { const d = P.d || today(); if (b.dataset.v === "pick") return A.pickDate(d, (v) => A.go("stats/prime?d=" + v, true)); A.go("stats/prime?d=" + addDays(d, +b.dataset.v), true); },
        w(b) { A.db().profile.weights[b.dataset.k] = +b.value; A.save(); A.refresh(); },
        refl(b) { const R = (A.db().reviews["refl:" + b.dataset.key] = A.db().reviews["refl:" + b.dataset.key] || {}); R[b.dataset.i] = b.value; A.save(); }
      });
    }
  });

  // Ключевые достижения периода — формулируются из данных.
  A.periodHighlights = (s, from, to) => {
    const out = [];
    if (s.tasksDone) out.push("✅ Выполнено задач: " + s.tasksDone);
    if (s.habitsDone) out.push("🔥 Отметок «привычка выполнена»: " + s.habitsDone);
    if (s.workouts) out.push("🏋 Тренировок: " + s.workouts + " (" + fmtDur(s.workoutMin) + ")");
    if (s.stepsSum) out.push("👟 Пройдено шагов: " + fmtN(s.stepsSum));
    if (s.goalsDone) out.push("🎯 Достигнуто целей: " + s.goalsDone);
    if (s.books) out.push("📖 Прочитано книг: " + s.books + (s.pages ? ", страниц: " + fmtN(s.pages) : ""));
    else if (s.pages) out.push("📖 Прочитано страниц: " + fmtN(s.pages));
    if (s.piano) out.push("🎹 Практика пианино: " + fmtDur(s.piano));
    if (s.cooked) out.push("🍳 Приготовлено блюд: " + s.cooked);
    if (s.weightCh != null) out.push("⚖ Вес: " + (s.weightCh > 0 ? "+" : "") + s.weightCh.toFixed(1) + " кг");
    if (s.income - s.expense > 0) out.push("💰 Отложено / осталось: " + money(s.income - s.expense));
    const best = A.col("habits").map((h) => [h, A.habitStats(h, from, to)]).filter(([, x]) => x.pct != null && x.plan >= 3).sort((a, b) => b[1].pct - a[1].pct)[0];
    if (best) out.push("⭐ Самая устойчивая привычка: " + best[0].name + " — " + Math.round(best[1].pct * 100) + "%");
    return out;
  };
  const REFLECT = ["Что получилось лучше всего?", "Чем я горжусь?", "Что забирало энергию?", "Что я сделаю иначе?", "За что я благодарна?", "Мои действия были похожи на жизнь, которую я хочу?"];
  const highlightsCard = (s, from, to, title) => { const hl = A.periodHighlights(s, from, to); return '<div class="card tint"><h3>🏆 ' + title + "</h3>" + (hl.length ? hl.map((x) => '<p style="margin:5px 0">' + esc(x) + "</p>").join("") : '<p class="small muted">Данных пока мало — достижения появятся по мере записей.</p>') + "</div>"; };
  const reflectCard = (key) => { const R = (A.db().reviews["refl:" + key] = A.db().reviews["refl:" + key] || {}); return '<div class="card sage"><h3>🪞 Вопросы для рефлексии</h3><div class="form">' + REFLECT.map((q, i) => '<div class="fld full"><label>' + esc(q) + '</label><textarea rows="2" data-c="refl" data-key="' + key + '" data-i="' + i + '">' + esc(R[i] || "") + "</textarea></div>").join("") + "</div></div>"; };
  A._hl = { highlightsCard, reflectCard };

  /* ---------- дневная сводка ---------- */
  A.view("summary", {
    title: "Сводка дня", tab: "today",
    render(el, r) {
      const d = r.params.d || today(), day = A.dayGet(d) || {}, p = A.db().profile, m = A.macrosDay(d);
      let h = A.dateNav(d);
      const ps = A.primeScore(d);
      h += '<div class="card hero"><div class="date">' + esc(A.fmtDate(d, { dow: true })) + "</div>" + (ps.score != null ? '<div class="big" style="margin-top:6px">◈ ' + ps.score + '</div><div class="small muted">PRIME SCORE</div>' : "") + "</div>";
      const T = (ic, n, v, route) => '<div class="stat tap" data-a="go" data-v="' + route + '"><small>' + ic + " " + n + "</small><b>" + v + "</b></div>";
      h += '<div class="grid3">' + [
        T("👟", "Шаги", fmtN(day.steps), "health/steps"), T("🔥", "Потрачено", fmtN(A.burnDay(d)) + " ккал", "workouts"), T("🍎", "Съедено", fmtN(m.kcal) + " ккал", "food?d=" + d),
        T("🥗", "Б/Ж/У", Math.round(m.p) + "/" + Math.round(m.f) + "/" + Math.round(m.c), "food?d=" + d), T("💧", "Вода", fmtN(day.water) + " мл", "health/water"), T("⚖", "Вес", day.weight ? fmtN(day.weight, 1) : "—", "health/weight"),
        T("🌙", "Сон", A.sleepH(d) ? fmtDur(A.sleepH(d) * 60) : "—", "health/sleep"), T("💜", "Настроение", fmtN(day.mood, 1), "health/state"), T("⚡", "Энергия", fmtN(A.energyAvg(d), 1), "health/state"),
        T("🌀", "Стресс", fmtN(day.stress), "health/state"), T("🏋", "Тренировки", fmtDur(A.workoutMin(d) || 0), "workouts"), T("📖", "Чтение", fmtN(A.pagesDay(d) || 0) + " стр.", "culture/books"),
        T("🎓", "Обучение", fmtDur(A.learnMinDay(d) || 0), "learn"), T("⬇", "Расходы", money(A.spendDay(d) || 0), "money/ops"), T("🎯", "Главное", day.main ? (day.mainDone ? "✓" : "…") : "—", "today?d=" + d)
      ].join("") + "</div>";
      const tDone = A.col("tasks").filter((t) => t.done && (t.doneAt || "").slice(0, 10) === d);
      h += '<div class="card" style="margin-top:12px"><h3>✅ Выполненные задачи · ' + tDone.length + "</h3>" + (tDone.length ? tDone.map((t) => '<div class="small" style="padding:3px 0">✓ ' + esc(t.title) + "</div>").join("") : '<p class="small muted">Нет</p>') + "</div>";
      const hs = A.col("habits").filter((x) => A.habitPlanned(x, d));
      h += '<div class="card"><h3>🔥 Привычки · ' + hs.filter((x) => A.hs(x.id, d) === "done").length + " из " + hs.length + "</h3>" + hs.map((x) => { const st = A.hs(x.id, d), S = st && A.HSTATUS[st]; return '<div class="row small" style="padding:3px 0"><span style="width:22px;color:' + (S ? S[2] : "var(--ink2)") + '">' + (S ? S[1] : "○") + '</span><span class="grow">' + esc((x.icon || "") + " " + x.name) + "</span>" + (x.type === "num" ? "<b>" + fmtN(A.hv(x.id, d) || 0, 1) + "/" + (x.target || 1) + "</b>" : "") + "</div>"; }).join("") + "</div>";
      const gs = A.col("goals").filter((g) => g.status === "active");
      if (gs.length) h += '<div class="card"><h3>🎯 Прогресс по целям</h3>' + gs.map((g) => { const pr = A.goalProgress(g); return '<div style="margin:6px 0"><div class="row small"><span class="grow">' + esc(g.title) + "</span><b>" + Math.round(pr * 100) + "%</b></div>" + A.bar(pr) + "</div>"; }).join("") + "</div>";
      if (day.fact || day.obstacle || day.note) h += '<div class="card sage"><h3>🌿 Вечерняя сверка</h3>' + [["План", day.plan || day.main], ["Факт", day.fact], ["Препятствие", day.obstacle], ["Заметка", day.note]].filter((x) => x[1]).map((x) => "<p><b>" + x[0] + ":</b> " + esc(x[1]) + "</p>").join("") + "</div>";
      return h;
    },
    bind(el, r) {
      const d = () => A.route().params.d || today();
      A.bind(el, { go(b) { A.go(b.dataset.v); }, dnav(b) { if (b.dataset.v === "pick") return A.pickDate(d(), (v) => A.go("summary?d=" + v, true)); A.go("summary?d=" + addDays(d(), +b.dataset.v), true); } });
    }
  });
})();
