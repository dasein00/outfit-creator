/* Экран «Сегодня» — быстрый ежедневный ввод. Карточки переставляются и отключаются в настройках. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtDate, fmtN, fmtDur, hm2min, nowHM } = A;

  A.CARD_NAMES = {
    main: "Главное дело дня", state: "Состояние", schedule: "Расписание", habits: "Привычки", tasks: "Задачи",
    water: "Вода", sleep: "Сон", steps: "Шаги", weather: "Погода", food: "Питание и КБЖУ", workout: "Тренировка",
    learn: "Чтение и пианино", tarot: "Карта дня", money: "Финансы", reminders: "Напоминания", evening: "Вечерняя сверка"
  };

  /* ---------- таймер занятий (чтение, пианино, хобби, учёба) ---------- */
  A.timer = {
    get: () => A.db().profile.timer || null,
    start(kind, ref, label) { A.db().profile.timer = { kind, ref: ref || "", label: label || "", t0: Date.now() }; A.save(); A.toast("Таймер запущен"); A.refresh(); },
    stop() {
      const t = A.timer.get(); if (!t) return;
      const min = Math.max(1, Math.round((Date.now() - t.t0) / 60000));
      A.db().profile.timer = null; A.save();
      A.logSession(t.kind, t.ref, min);
    }
  };
  A.logSession = (kind, ref, min) => {
    const d = today();
    if (kind === "piano") return A.edit.practice({ date: d, min });
    if (kind === "read") return A.edit.reading({ date: d, min, bookId: ref });
    if (kind === "hobby") return A.edit.hobbySession({ date: d, min, hid: ref });
    return A.edit.learnSession({ date: d, min, subject: ref || "" });
  };
  const timerBanner = () => {
    const t = A.timer.get(); if (!t) return "";
    const min = Math.round((Date.now() - t.t0) / 60000);
    const names = { piano: "Пианино", read: "Чтение", hobby: "Хобби", learn: "Обучение" };
    return '<div class="card tint row"><span style="font-size:22px">⏱</span><div class="grow"><b>' + esc(names[t.kind] || "Занятие") + (t.label ? " · " + esc(t.label) : "") + '</b><div class="small muted">идёт ' + fmtDur(min) + '</div></div><button class="btn primary sm" data-a="timerStop">Стоп и записать</button></div>';
  };

  /* ---------- карточки ---------- */
  const C = {};

  C.main = (d, day) => {
    const wk = A.db().weeks[A.isoWeek(d)] || {};
    return '<div class="card"><h3>🎯 Главное дело дня<span class="sp"></span>' + (day.main ? '<button class="check' + (day.mainDone ? " on" : "") + '" data-a="mainDone" aria-label="Выполнено">' + (day.mainDone ? "✓" : "") + "</button>" : "") + "</h3>" +
      '<input data-c="mainSet" placeholder="Глагол + объект + объём + срок" value="' + esc(day.main || "") + '">' +
      '<input data-c="mainMin" style="margin-top:6px" placeholder="Минимальная версия (если сил мало)" value="' + esc(day.mainMin || "") + '">' +
      (wk.main ? '<p class="small muted" style="margin-top:8px">Главное дело недели: <b>' + esc(wk.main) + "</b></p>" : '<p class="small muted" style="margin-top:8px"><a href="#/calendar/week">Выбрать главное дело недели →</a></p>') + "</div>";
  };

  C.state = (d, day) => {
    const h = new Date().getHours(), part = h < 12 ? "m" : h < 17 ? "d" : "e";
    const partName = { m: "утро", d: "день", e: "вечер" };
    const en = day.energy || {};
    const epart = A._epart || part;
    return '<div class="card"><h3>💜 Состояние<span class="sp"></span><button class="link" data-a="feelings">' + (day.feelings && day.feelings.length ? esc(day.feelings.slice(0, 2).join(", ")) + (day.feelings.length > 2 ? "…" : "") : "+ чувства") + "</button></h3>" +
      '<div class="small muted">Настроение ' + (day.mood != null ? "<b>" + day.mood + "</b>" : "") + "</div>" + A.scale("mood", day.mood) +
      '<div class="small muted" style="margin-top:8px">Энергия' + A.seg([["m", "утро " + (en.m ?? "")], ["d", "день " + (en.d ?? "")], ["e", "вечер " + (en.e ?? "")]], epart, "epart").replace('class="seg"', 'class="seg" style="margin:4px 0"') + "</div>" + A.scale("energy", en[epart]) +
      '<div class="small muted" style="margin-top:8px">Стресс ' + (day.stress != null ? "<b>" + day.stress + "</b>" : "") + (day.stressSrc && day.stressSrc.length ? " · " + esc(day.stressSrc.join(", ")) : "") + ' <button class="link" data-a="stressSrc">источник</button></div>' + A.scale("stress", day.stress) +
      '<div class="row" style="margin-top:10px"><div class="grow small muted">Тревожность: ' + (day.anx && day.anx.length ? day.anx.map((a) => "<b>" + a.v + "</b> " + esc(a.t || "")).join(", ") : "нет записей") + '</div><button class="btn sm" data-a="anx">+ запись</button></div>' +
      '<p class="small muted" style="margin-top:6px">Это дневник самонаблюдения, а не диагностика.</p></div>';
  };

  C.schedule = (d) => {
    const ev = A.eventsOn(d);
    const now = hm2min(nowHM()), isToday = d === today();
    const row = (e) => {
      const c = A.cat(e.cat), a = hm2min(e.start), b = hm2min(e.end);
      const on = isToday && a != null && b != null && (b > a ? now >= a && now < b : now >= a || now < b);
      return '<div class="ev' + (on ? " now" : "") + '" style="--c:' + c.color + ';margin-bottom:6px" data-a="event" data-id="' + e.id + '"><b>' + esc(c.icon + " " + e.title) + "</b><small>" + esc((e.start || "") + (e.end ? "–" + e.end : "")) + (e.must ? ' · <span class="tag must">обязательно</span>' : "") + "</small></div>";
    };
    const must = ev.filter((e) => e.must), want = ev.filter((e) => !e.must);
    return '<div class="card"><h3>🗓 Расписание<span class="sp"></span><button class="link" data-a="addEvent">+ событие</button></h3>' +
      (ev.length ? (must.length ? '<div class="small muted" style="margin:2px 0 6px">Обязательные</div>' + must.map(row).join("") : "") + (want.length ? '<div class="small muted" style="margin:8px 0 6px">Желательные</div>' + want.map(row).join("") : "") : A.empty("Событий нет")) +
      '<a class="link" href="#/calendar/day?d=' + d + '">Открыть день →</a></div>';
  };

  C.habits = (d) => {
    const hs = A.col("habits").filter((h) => A.habitPlanned(h, d));
    const done = hs.filter((h) => A.hs(h.id, d) === "done").length;
    return '<div class="card"><h3>✅ Привычки<span class="sp"></span><small>' + done + " из " + hs.length + '</small></h3><div class="list">' +
      (hs.length ? hs.map((h) => { const st = A.hs(h.id, d), S = st ? A.HSTATUS[st] : null; return '<div class="item"><div class="ic">' + esc(h.icon || "•") + '</div><div class="tx"><b>' + esc(h.name) + "</b><small>" + (h.min ? "мин.: " + esc(h.min) + " · " : "") + "серия " + A.habitStreak(h, d) + '</small></div><button class="hbtn" data-a="habit" data-id="' + h.id + '" style="' + (S ? "background:" + S[2] + ";border-color:" + S[2] + ";color:" + (st === "none" ? "var(--ink2)" : "#fff") : "") + '" aria-label="' + esc(S ? S[0] : "Отметить") + '">' + (S ? S[1] : "") + "</button></div>"; }).join("") : A.empty("На сегодня привычек нет")) +
      '</div><p class="small muted">Нажатие: ✓ выполнено → ½ частично → × пропуск → – не планировалось. Пропуск — не провал.</p><a class="link" href="#/habits">Все привычки →</a></div>';
  };

  C.tasks = (d) => {
    const t = A.col("tasks").filter((x) => (x.date === d) || (!x.done && x.date && x.date < d && d === today()));
    t.sort((a, b) => (a.done - b.done) || ((a.prio || 2) - (b.prio || 2)));
    return '<div class="card"><h3>📝 Задачи<span class="sp"></span><small>' + t.filter((x) => x.done).length + "/" + t.length + "</small></h3>" +
      '<div class="inline-add"><input id="qtask" placeholder="Новая задача на день"><button class="btn primary" data-a="qtask">+</button></div><div class="list">' +
      t.map((x) => A.taskRow(x, d)).join("") + '</div><a class="link" href="#/tasks">Все задачи →</a></div>';
  };

  C.water = (d, day) => {
    const g = A.db().profile.waterGoal || 2000, w = day.water || 0;
    const cups = Math.round(g / 250), full = Math.min(cups, Math.floor(w / 250));
    return '<div class="card"><h3>💧 Вода<span class="sp"></span><small>' + fmtN(w) + " / " + fmtN(g) + " мл</small></h3>" +
      '<div style="font-size:22px;letter-spacing:2px;margin:2px 0 6px" aria-hidden="true">' + "🥛".repeat(full) + '<span style="opacity:.25">' + "🥛".repeat(Math.max(0, cups - full)) + "</span></div>" + A.bar(w / g, "var(--sky)") +
      '<div class="qbtns" style="margin-top:10px">' + [100, 250, 330, 500].map((v) => '<button class="btn sm" data-a="water" data-v="' + v + '">+' + v + "</button>").join("") + '<button class="btn sm ghost" data-a="water" data-v="-250" aria-label="Убрать 250">−</button></div></div>';
  };

  C.sleep = (d, day) => {
    const s = day.sleep || {}, h = A.sleepH(d);
    return '<div class="card"><h3>🌙 Сон<span class="sp"></span><button class="link" data-a="sleep">' + (h ? "изменить" : "+ записать") + "</button></h3>" +
      (h ? '<div class="row"><div class="big">' + fmtDur(h * 60) + '</div><div class="grow small muted">' + esc((s.bed || "?") + " → " + (s.wake || "?")) + "<br>качество " + (s.q ?? "—") + "/10 · восстановление " + (s.rec ?? "—") + "/10" + (s.wakeups ? " · пробуждений " + s.wakeups : "") + "</div></div>" : '<p class="muted small">Во сколько легла и проснулась? Цель: ' + A.db().profile.sleepMin + "–" + A.db().profile.sleepMax + " ч.</p>") + "</div>";
  };

  C.steps = (d, day) => {
    const g = A.db().profile.stepsGoal || 8000, s = day.steps;
    return '<div class="card"><h3>👟 Шаги<span class="sp"></span>' + (A.native && A.db().profile.stepsSensor && d === today() ? '<button class="link" data-a="stepsSync">⟳ датчик</button>' : "") + '<button class="link" data-a="steps">ввести</button></h3><div class="row">' +
      A.ring(s ? s / g : 0, s != null ? fmtN(s) : "—", "из " + fmtN(g), "var(--sage)") + '<div class="grow small muted">' + (s != null ? Math.round((s / g) * 100) + "% цели" : "Нет данных") + "<br>серия ≥ цели: " + A.streakMetric((x) => ((A.dayGet(x) || {}).steps || 0) >= g) + " дн." + (day.stepsSrc === "sensor" ? "<br>из датчика телефона" : "") + "</div></div></div>";
  };

  C.weather = (d, day) => {
    const w = day.weather;
    const cond = w && A.weather(w.cond);
    return '<div class="card"><h3>🌤 Погода<span class="sp"></span>' + (d === today() ? '<button class="link" data-a="wRefresh">⟳</button>' : "") + '<button class="link" data-a="weather">ввести</button></h3>' +
      (w ? '<div class="row"><div style="font-size:40px">' + (cond ? cond.icon : "🌡") + '</div><div class="grow"><div class="big">' + (w.t != null ? fmtN(w.t) + "°" : "—") + '</div><div class="small muted">' + esc(cond ? cond.name : "") + (w.feel != null ? " · ощущается " + fmtN(w.feel) + "°" : "") + "</div></div></div>" +
        '<div class="small muted" style="margin-top:6px">' + [w.hum != null ? "влажность " + w.hum + "%" : "", w.press != null ? "давление " + w.press + " мм" : "", w.wind != null ? "ветер " + w.wind + " м/с" : "", w.cloud != null ? "облачность " + w.cloud + "%" : "", w.pp != null ? "осадки " + w.pp + "%" : "", w.rise ? "☀ " + w.rise + "–" + w.set : ""].filter(Boolean).join(" · ") + (w.src === "api" ? "<br>обновлено " + esc(w.at || "") : "") + "</div>"
        : '<p class="small muted">' + (A.db().profile.lat ? "Нажмите ⟳, чтобы получить погоду." : "Укажите город в настройках или введите вручную.") + "</p>") + "</div>";
  };

  C.food = (d) => {
    const p = A.db().profile, m = A.macrosDay(d);
    const row = (n, v, g, c) => '<div style="margin:5px 0"><div class="row small"><span class="grow">' + n + "</span><b>" + fmtN(v) + " / " + g + " г</b></div>" + A.bar(v / g, c) + "</div>";
    return '<div class="card"><h3>🍎 КБЖУ<span class="sp"></span><a class="link" href="#/food?d=' + d + '">+ приём пищи</a></h3><div class="row">' + A.ring(m.kcal / p.kcal, fmtN(m.kcal), "/" + p.kcal + " ккал", "var(--peach)") +
      '<div class="grow">' + row("Белки", m.p, p.prot, "var(--sage)") + row("Жиры", m.f, p.fat, "var(--peach)") + row("Углеводы", m.c, p.carb, "var(--gold)") + "</div></div></div>";
  };

  C.workout = (d) => {
    const w = A.col("workouts").filter((x) => x.date === d);
    const wk = A.col("workouts").filter((x) => x.date > addDays(d, -7) && x.date <= d).length;
    return '<div class="card"><h3>🏋 Тренировка<span class="sp"></span><small>' + wk + "/" + A.db().profile.workoutsWeek + ' за 7 дней</small></h3>' +
      (w.length ? w.map((x) => '<div class="item" data-a="workout" data-id="' + x.id + '"><div class="ic">🏋</div><div class="tx"><b>' + esc(A.wType(x.type)) + "</b><small>" + fmtDur(x.dur) + (x.sets && x.sets.length ? " · " + x.sets.length + " подходов" : "") + "</small></div></div>").join("") : '<p class="small muted">В дни низкой энергии подойдёт минимальная версия: прогулка вместо тренировки.</p>') +
      '<div class="btns"><button class="btn sm primary" data-a="addWorkout">+ тренировка</button><button class="btn sm" data-a="quickWalk">Прогулка 30 мин</button></div></div>';
  };

  C.learn = (d) => {
    const cur = window.LEARN.PIANO.find((l) => l.id === A.db().piano.cur) || window.LEARN.PIANO[0];
    const reading = A.col("books").find((b) => b.status === "reading");
    return '<div class="card"><h3>📚 Чтение и пианино</h3>' +
      '<div class="item"><div class="ic">📖</div><div class="tx"><b>' + esc(reading ? reading.title : "Книга не выбрана") + "</b><small>сегодня " + fmtN(A.pagesDay(d) || 0) + " стр. · " + fmtDur(A.readMinDay(d) || 0) + '</small></div><button class="btn sm" data-a="readLog">+</button><button class="btn sm" data-a="timer" data-k="read" data-ref="' + (reading ? reading.id : "") + '">⏱</button></div>' +
      '<div class="item"><div class="ic">🎹</div><div class="tx"><b>' + esc(cur.title) + "</b><small>сегодня " + fmtDur(A.pianoMinDay(d) || 0) + '</small></div><button class="btn sm" data-a="pianoLog">+</button><button class="btn sm" data-a="timer" data-k="piano">⏱</button></div>' +
      '<div class="btns"><a class="link" href="#/piano">Урок →</a>&nbsp;&nbsp;<a class="link" href="#/learn">Обучение →</a></div></div>';
  };

  C.tarot = (d) => {
    const id = A.db().tarot.daily[d];
    const c = id && window.LEARN.TAROT.find((x) => x.id === id);
    return '<div class="card"><h3>🃏 Карта дня<span class="sp"></span><small>для рефлексии</small></h3>' +
      (c ? '<div class="row" data-a="tarotCard" data-id="' + c.id + '"><div class="tcard" style="min-height:90px;width:74px;padding:8px"><div style="font-size:26px">' + (c.arcana === "Старший аркан" ? "✶" : "✦") + '</div></div><div class="grow"><b>' + esc(c.name) + '</b><div class="small muted">' + esc(c.up) + '</div><div class="small" style="margin-top:4px"><i>' + esc(c.q) + "</i></div></div></div>" : '<button class="btn block" data-a="tarotDraw">Вытянуть карту дня</button>') + "</div>";
  };

  C.money = (d) => {
    const out = A.col("tx").filter((t) => t.date === d && t.kind === "out");
    const due = A.col("recurring").filter((r) => r.active && r.next && r.next >= d && r.next <= addDays(d, 3));
    return '<div class="card"><h3>💰 Финансы<span class="sp"></span><small>расходы: ' + A.money(A.sum(out.map((t) => t.amt))) + "</small></h3>" +
      out.slice(-4).map((t) => '<div class="item" data-a="tx" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.cat) + "</b><small>" + esc(t.note || t.sub || "") + '</small></div><b class="num">−' + A.money(t.amt) + "</b></div>").join("") +
      (due.length ? '<div class="small muted" style="margin-top:6px">Скоро платежи: ' + due.map((r) => esc(r.name) + " " + A.fmtShort(r.next) + " (" + A.money(r.amt) + ")").join(", ") + "</div>" : "") +
      '<div class="btns"><button class="btn sm primary" data-a="addExp">− расход</button><button class="btn sm" data-a="addInc">+ доход</button><a class="btn sm ghost" href="#/money">Финансы</a></div></div>';
  };

  C.reminders = (d) => {
    const rs = A.col("reminders").filter((r) => r.on && A.reminderOn(r, d)).sort((a, b) => (a.time > b.time ? 1 : -1));
    return '<div class="card"><h3>🔔 Напоминания<span class="sp"></span><a class="link" href="#/reminders">все</a></h3>' + (rs.length ? rs.map((r) => '<div class="item"><div class="ic">🔔</div><div class="tx"><b>' + esc(r.title) + "</b><small>" + esc(r.time + (r.text ? " · " + r.text : "")) + "</small></div></div>").join("") : A.empty("На сегодня напоминаний нет")) + "</div>";
  };

  C.evening = (d, day) => '<div class="card sage"><h3>🌿 Вечерняя сверка</h3>' +
    '<div class="form"><div class="fld full"><label>План</label><input data-c="dayF" data-k="plan" value="' + esc(day.plan || day.main || "") + '"></div>' +
    '<div class="fld full"><label>Факт</label><input data-c="dayF" data-k="fact" value="' + esc(day.fact || "") + '"></div>' +
    '<div class="fld full"><label>Препятствие (усталость, скука, неопределённость, объём, страх ошибки…)</label><input data-c="dayF" data-k="obstacle" value="' + esc(day.obstacle || "") + '"></div>' +
    '<div class="fld full"><label>Заметка / благодарность</label><textarea data-c="dayF" data-k="note" rows="2">' + esc(day.note || "") + "</textarea></div></div>" +
    '<p class="small muted" style="margin-top:8px"><i>«Мои действия сегодня были похожи на жизнь, которую я хочу?»</i> Если сорвалась — ничего не компенсировать, возврат начинается со следующего доступного действия.</p></div>';

  A.todayCards = C;

  /* ---------- общие строки ---------- */
  A.taskRow = (x, d) => '<div class="item' + (x.done ? " done" : "") + '"><button class="check' + (x.done ? " on" : "") + '" data-a="taskDone" data-id="' + x.id + '" aria-label="Выполнено">' + (x.done ? "✓" : "") + '</button><div class="tx" data-a="task" data-id="' + x.id + '"><b>' + (x.prio === 1 ? "❗ " : "") + esc(x.title) + "</b><small>" + [x.date && x.date < (d || today()) && !x.done ? "⏰ просрочено " + A.fmtShort(x.date) : "", x.time || "", x.must ? "обязательно" : "", x.minVer ? "мин.: " + esc(x.minVer) : ""].filter(Boolean).join(" · ") + "</small></div></div>";
  A.reminderOn = (r, d) => {
    switch (r.type) {
      case "once": return r.date === d;
      case "daily": return true;
      case "weekdays": return (r.days || []).includes(A.dow(d));
      case "weekly": return (r.days || [1])[0] === A.dow(d);
      case "monthly": return A.parse(d).getDate() === +r.dom;
    }
    return false;
  };

  /* ---------- общие обработчики для карточек дня ---------- */
  A.dayHandlers = (getD) => ({
    scale(el) {
      const d = getD(), day = A.day(d), k = el.dataset.k, v = +el.dataset.v;
      if (k === "energy") { const part = A._epart || (new Date().getHours() < 12 ? "m" : new Date().getHours() < 17 ? "d" : "e"); day.energy = day.energy || {}; day.energy[part] = day.energy[part] === v ? null : v; }
      else day[k] = day[k] === v ? null : v;
      A.save(); A.vibe(); A.refresh();
    },
    epart(el) { A._epart = el.dataset.v; A.refresh(); },
    feelings() {
      const d = getD(), day = A.day(d);
      A.formSheet("Чувства", [{ k: "feelings", type: "multi", opts: A.FEELINGS }], { feelings: day.feelings || [] }, (o) => { day.feelings = o.feelings; A.save(); A.refresh(); });
    },
    stressSrc() {
      const d = getD(), day = A.day(d);
      A.formSheet("Источник стресса", [{ k: "stressSrc", type: "multi", opts: A.STRESS_SRC }, { k: "stressNote", label: "Что произошло", type: "textarea" }], day, (o) => { Object.assign(day, o); A.save(); A.refresh(); });
    },
    anx() { A.edit.anxiety(getD()); },
    mainSet(el) { const day = A.day(getD()); const had = !!day.main; day.main = el.value.trim(); A.save(); if (had !== !!day.main) A.refresh(); },
    mainMin(el) { const day = A.day(getD()); day.mainMin = el.value.trim(); A.save(); },
    mainDone() { const day = A.day(getD()); day.mainDone = !day.mainDone; A.save(); A.vibe(20); if (day.mainDone) A.toast("Главное сделано 💜"); A.refresh(); },
    habit(el) { const h = A.byId("habits", el.dataset.id); A.cycleHs(h, getD()); A.vibe(); A.refresh(); },
    qtask(el) { const i = document.getElementById("qtask"); const v = i.value.trim(); if (!v) return; A.upsert("tasks", { title: v, date: getD(), prio: 2, done: false, created: today() }); A.refresh(); },
    taskDone(el) { A.completeTask(A.byId("tasks", el.dataset.id)); A.vibe(); A.refresh(); },
    task(el) { A.edit.task(A.byId("tasks", el.dataset.id)); },
    water(el) { const day = A.day(getD()); day.water = Math.max(0, (day.water || 0) + +el.dataset.v); A.save(); A.vibe(); A.refresh(); },
    sleep() { A.edit.sleep(getD()); },
    steps() { A.edit.steps(getD()); },
    stepsSync() { A.syncSteps(true); },
    weather() { A.edit.weather(getD()); },
    wRefresh() { A.weatherRefresh(true); },
    event(el) { A.edit.event(A.byId("events", el.dataset.id), getD()); },
    addEvent() { A.edit.event(null, getD()); },
    addWorkout() { A.edit.workout({ date: getD() }); },
    workout(el) { A.edit.workout(A.byId("workouts", el.dataset.id)); },
    quickWalk() { A.upsert("workouts", { date: getD(), type: "walk", dur: 30, int: 3, sets: [], note: "" }); A.toast("Прогулка записана"); A.refresh(); },
    readLog() { const b = A.col("books").find((x) => x.status === "reading"); A.edit.reading({ date: getD(), bookId: b ? b.id : "" }); },
    pianoLog() { A.edit.practice({ date: getD() }); },
    timer(el) { A.timer.start(el.dataset.k, el.dataset.ref, el.dataset.k === "piano" ? "практика" : ""); },
    timerStop() { A.timer.stop(); },
    tarotDraw() { A.tarotDaily(getD()); A.refresh(); },
    tarotCard(el) { A.go("tarot/card/" + el.dataset.id); },
    addExp() { A.edit.tx({ kind: "out", date: getD() }); },
    addInc() { A.edit.tx({ kind: "in", date: getD() }); },
    tx(el) { A.edit.tx(A.byId("tx", el.dataset.id)); },
    dayF(el) { const day = A.day(getD()); day[el.dataset.k] = el.value.trim(); A.save(); }
  });

  /* ---------- экран ---------- */
  A.view("today", {
    root: true, title: "Сегодня",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.go(\'stats/prime\')" aria-label="PRIME SCORE" title="PRIME SCORE">◈</button><button class="icon-btn" onclick="App.go(\'settings/cards\')" aria-label="Настроить карточки">⚙</button>'; },
    render(el, r) {
      const d = r.params.d || today();
      const day = A.day(d, false) || {};
      const p = A.db().profile;
      const ps = A.primeScore(d);
      let html = A.dateNav(d) + timerBanner();
      html += '<div class="card hero"><div class="date">' + esc(A.DOW_FULL[A.dow(d) - 1][0].toUpperCase() + A.DOW_FULL[A.dow(d) - 1].slice(1)) + ", " + esc(fmtDate(d)) + '</div><div class="phrase">' + esc(p.phrase) + "</div>" +
        (ps.score != null ? '<div style="margin-top:10px"><a href="#/stats/prime" class="tag" style="font-size:13px;padding:4px 12px">◈ PRIME SCORE ' + ps.score + "</a></div>" : "") + "</div>";
      p.cards.filter((k) => !p.hidden.includes(k) && C[k]).forEach((k) => { html += C[k](d, day); });
      html += '<p class="small muted" style="text-align:center;margin:18px 0 6px">' + esc(p.principle) + "</p>";
      return html;
    },
    bind(el, r) {
      const getD = () => A.route().params.d || today();
      A.bind(el, Object.assign(A.dayHandlers(getD), {
        dnav(b) { const d = getD(); if (b.dataset.v === "pick") return A.pickDate(d, (v) => A.go("today?d=" + v)); const nd = addDays(d, +b.dataset.v); A.go(nd === today() ? "today" : "today?d=" + nd, true); }
      }));
      const q = el.querySelector("#qtask");
      if (q) q.addEventListener("keydown", (e) => { if (e.key === "Enter") el.querySelector('[data-a="qtask"]').click(); });
    }
  });
})();
