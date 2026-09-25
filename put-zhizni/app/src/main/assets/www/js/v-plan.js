/* Планирование: календарь (день / неделя / месяц), задачи, идеи, привычки, напоминания. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtDate, fmtShort, hm2min, DOW, dow, weekStart, iso, parse, pad } = A;
  A.edit = A.edit || {};

  const DAYS_OPTS = [[1, "Пн"], [2, "Вт"], [3, "Ср"], [4, "Чт"], [5, "Пт"], [6, "Сб"], [7, "Вс"]];
  const REPEAT = [["none", "Не повторять"], ["daily", "Каждый день"], ["weekdays", "По дням недели"], ["weekly", "Каждую неделю"], ["monthly", "Каждый месяц"]];

  /* ---------- редакторы ---------- */
  A.edit.event = (e, d) => {
    const isNew = !e;
    // Правка одного повтора: разрешаем изменить серию или только этот день.
    e = e || { title: "", cat: "other", date: d || today(), start: "09:00", end: "10:00", repeat: "none", days: [], must: false, remind: "", note: "" };
    const fields = [
      { k: "title", label: "Название", req: true, full: true },
      { k: "cat", label: "Категория", type: "select", opts: A.catOpts() },
      { k: "prio", label: "Приоритет", type: "select", opts: [[1, "Высокий"], [2, "Обычный"], [3, "Низкий"]] },
      { k: "date", label: e.repeat && e.repeat !== "none" ? "Начало повторов" : "Дата", type: "date" },
      { k: "must", label: "Тип", type: "check", text: "Обязательное дело" },
      { k: "start", label: "Начало", type: "time" }, { k: "end", label: "Конец", type: "time" },
      { k: "repeat", label: "Повторение", type: "select", opts: REPEAT },
      { k: "until", label: "Повторять до", type: "date" },
      { k: "days", label: "Дни недели (для «По дням недели»)", type: "multi", opts: DAYS_OPTS, numeric: true },
      { k: "remind", label: "Напоминание", type: "select", opts: [["", "Без напоминания"], [0, "В момент начала"], [5, "За 5 минут"], [15, "За 15 минут"], [30, "За 30 минут"], [60, "За 1 час"], [1440, "За день"]] },
      { k: "note", label: "Заметка", type: "textarea" }
    ];
    const opts = {};
    if (!isNew) {
      opts.onDelete = () => { A.remove("events", e.id); A.syncReminders(); A.refresh(); };
      if (e.repeat !== "none" && d) opts.post = '<div class="btns"><button class="btn sm ghost" id="skipOne">Пропустить только ' + fmtShort(d) + "</button></div>";
      opts.after = (w) => { const b = w.querySelector("#skipOne"); if (b) b.onclick = () => { e.skip = (e.skip || []).concat(d); A.save(); A.closeSheet(); A.refresh(); }; };
    }
    A.formSheet(isNew ? "Новое событие" : "Событие", fields, e, (o) => {
      Object.assign(e, o);
      e.remind = o.remind === "" ? null : +o.remind;
      e.prio = +o.prio || 2;
      if (e.repeat === "weekdays" && !e.days.length) e.days = [dow(e.date)];
      A.upsert("events", e); A.syncReminders(); A.refresh();
    }, opts);
  };

  A.edit.task = (t, preset = {}) => {
    const isNew = !t;
    t = t || Object.assign({ title: "", date: today(), time: "", prio: 2, must: false, minVer: "", goalId: "", note: "", done: false, repeat: "none", remind: false }, preset);
    const fields = [
      { k: "title", label: "Задача", req: true, full: true, ph: "Глагол + объект + объём + срок", hint: "Не «работать над проектом», а «сегодня до 19:00 написать структуру из пяти пунктов»" },
      { k: "date", label: "Дата", type: "date" }, { k: "time", label: "Время", type: "time" },
      { k: "prio", label: "Приоритет", type: "select", opts: [[1, "Высокий"], [2, "Обычный"], [3, "Низкий"]] },
      { k: "must", label: "Тип", type: "check", text: "Обязательно" },
      { k: "minVer", label: "Минимальная версия", full: true, hint: "Если сопротивление высокое — уменьшить объём в два раза, а не отказаться" },
      { k: "goalId", label: "Цель", type: "select", opts: () => [["", "—"]].concat(A.col("goals").map((g) => [g.id, g.title])), full: true },
      { k: "repeat", label: "Повтор", type: "select", opts: [["none", "Нет"], ["daily", "Каждый день"], ["weekly", "Каждую неделю"], ["monthly", "Каждый месяц"]] },
      { k: "remind", label: "Уведомление", type: "check", text: "Напомнить во время" },
      { k: "note", label: "Заметка", type: "textarea" }
    ];
    A.formSheet(isNew ? "Новая задача" : "Задача", fields, t, (o) => {
      Object.assign(t, o); t.prio = +o.prio || 2;
      if (isNew) t.created = today();
      A.upsert("tasks", t); A.syncReminders(); A.refresh();
    }, isNew ? {} : { onDelete: () => { A.remove("tasks", t.id); A.refresh(); } });
  };
  // Выполнение повторяющейся задачи создаёт следующую.
  A.completeTask = (t) => {
    t.done = !t.done; t.doneAt = t.done ? today() : "";
    if (t.done && t.repeat && t.repeat !== "none" && t.date && !t.spawned) {
      const nd = t.repeat === "daily" ? addDays(t.date, 1) : t.repeat === "weekly" ? addDays(t.date, 7) : A.nextMonthly(t.date, parse(t.date).getDate());
      A.col("tasks").push(Object.assign({}, t, { id: A.uid(), date: nd, done: false, doneAt: "", spawned: false }));
      t.spawned = true;
    }
    A.save();
  };

  A.edit.habit = (h) => {
    const isNew = !h;
    h = h || { name: "", icon: "✨", cat: "other", freq: "daily", days: [1, 2, 3, 4, 5, 6, 7], perWeek: 3, min: "", time: "", remind: false, start: today(), end: "", note: "", archived: false };
    const fields = [
      { k: "icon", label: "Значок", ph: "✨" }, { k: "name", label: "Название", req: true },
      { k: "cat", label: "Категория", type: "select", opts: [["health", "Здоровье"], ["sport", "Спорт"], ["learn", "Обучение"], ["home", "Дом"], ["money", "Финансы"], ["family", "Отношения"], ["rest", "Восстановление"], ["other", "Другое"]] },
      { k: "freq", label: "Периодичность", type: "select", opts: [["daily", "Каждый день"], ["weekdays", "По дням недели"], ["xweek", "N раз в неделю"]] },
      { k: "days", label: "Дни недели", type: "multi", opts: DAYS_OPTS, numeric: true },
      { k: "perWeek", label: "Раз в неделю (для N раз)", type: "number", min: 1, max: 7 },
      { k: "min", label: "Минимальная цель", ph: "например, 10 минут" },
      { k: "time", label: "Время", type: "time" }, { k: "remind", label: "Напоминание", type: "check", text: "Напоминать" },
      { k: "start", label: "Дата начала", type: "date" }, { k: "end", label: "Дата окончания", type: "date" },
      { k: "note", label: "Комментарий", type: "textarea" },
      { k: "archived", label: "Архив", type: "check", text: "В архиве (не показывать)" }
    ];
    A.formSheet(isNew ? "Новая привычка" : "Привычка", fields, h, (o) => { Object.assign(h, o); A.upsert("habits", h); A.syncReminders(); A.refresh(); },
      isNew ? {} : { onDelete: () => { A.remove("habits", h.id); Object.keys(A.db().hlog).forEach((k) => { if (k.startsWith(h.id + "|")) delete A.db().hlog[k]; }); A.save(); A.refresh(); } });
  };

  A.edit.reminder = (r) => {
    const isNew = !r;
    r = r || { title: "", text: "", type: "daily", time: "09:00", date: today(), days: [], dom: 1, kind: "general", on: true };
    const fields = [
      { k: "title", label: "Заголовок", req: true, full: true }, { k: "text", label: "Текст", full: true },
      { k: "type", label: "Повтор", type: "select", opts: [["once", "Один раз / по дате"], ["daily", "Ежедневно"], ["weekdays", "По дням недели"], ["weekly", "Еженедельно"], ["monthly", "Ежемесячно"]] },
      { k: "time", label: "Время", type: "time" },
      { k: "date", label: "Дата (для разового)", type: "date" }, { k: "dom", label: "Число месяца", type: "number", min: 1, max: 31 },
      { k: "days", label: "Дни недели", type: "multi", opts: DAYS_OPTS, numeric: true },
      { k: "kind", label: "Тип", type: "select", opts: [["general", "Общее"], ["habits", "Привычка"], ["payments", "Платёж"], ["workouts", "Тренировка"], ["reading", "Чтение"], ["piano", "Пианино"], ["study", "Учебное занятие"]] },
      { k: "on", label: "Включено", type: "check", text: "Активно" }
    ];
    A.formSheet(isNew ? "Новое напоминание" : "Напоминание", fields, r, (o) => {
      Object.assign(r, o);
      if ((r.type === "weekdays" || r.type === "weekly") && !r.days.length) { A.toast("Выберите день недели"); return false; }
      A.upsert("reminders", r); A.syncReminders(); A.refresh();
    }, isNew ? {} : { onDelete: () => { A.remove("reminders", r.id); A.syncReminders(); A.refresh(); } });
  };

  /* ---------- календарь ---------- */
  const evCard = (e, d) => { const c = A.cat(e.cat); return '<div class="ev" style="--c:' + c.color + '" data-a="event" data-id="' + e.id + '" data-d="' + d + '"><b>' + esc(c.icon + " " + e.title) + "</b><small>" + esc((e.start || "") + (e.end ? "–" + e.end : "")) + (e.must ? " · обязательно" : " · желательно") + (e.repeat !== "none" ? " · ↻" : "") + "</small></div>"; };

  function dayView(d) {
    const ev = A.eventsOn(d);
    const tasks = A.col("tasks").filter((t) => t.date === d);
    let h = A.dateNav(d);
    h += '<div class="card"><h3>Задачи<span class="sp"></span><button class="link" data-a="addTask">+ задача</button></h3><div class="list">' + (tasks.length ? tasks.map((t) => A.taskRow(t, d)).join("") : A.empty("Задач нет")) + "</div></div>";
    h += '<div class="card"><h3>Шкала дня<span class="sp"></span><button class="link" data-a="addEvent">+ событие</button></h3><div class="timeline">';
    const hours = {}; ev.forEach((e) => { const k = Math.floor((hm2min(e.start) ?? 0) / 60); (hours[k] = hours[k] || []).push(e); });
    for (let hh = 5; hh <= 23; hh++) h += '<div class="tl-row"><div class="tl-h">' + pad(hh) + ':00</div><div class="tl-c">' + (hours[hh] || []).map((e) => evCard(e, d)).join("") + "</div></div>";
    [0, 1, 2, 3, 4].forEach((hh) => { if (hours[hh]) h += '<div class="tl-row"><div class="tl-h">' + pad(hh) + ':00</div><div class="tl-c">' + hours[hh].map((e) => evCard(e, d)).join("") + "</div></div>"; });
    h += "</div></div>";
    const must = ev.filter((e) => e.must).length;
    h += '<p class="small muted">Обязательных: ' + must + " · желательных: " + (ev.length - must) + ". Долгое нажатие не нужно — просто нажмите на событие, чтобы изменить.</p>";
    return h;
  }

  function weekView(d) {
    const ws = weekStart(d), days = A.range(ws, addDays(ws, 6));
    const wk = isoKey(ws);
    const W = (A.db().weeks[wk] = A.db().weeks[wk] || { prio: ["", "", ""], main: "", rules: {}, q1: "", q2: "", q3: "", minVer: "", remove: "", criterion: "", times: "" });
    const t = today();
    // процент выполнения: задачи + привычки
    let plan = 0, done = 0;
    days.forEach((x) => {
      A.col("tasks").filter((k) => k.date === x).forEach((k) => { plan++; if (k.done) done++; });
      A.col("habits").forEach((hb) => { if (!A.habitPlanned(hb, x) || x > t) return; const st = A.hs(hb.id, x); if (st === "none") return; plan++; if (st === "done") done++; else if (st === "part") done += 0.5; });
    });
    const unfinished = A.col("tasks").filter((k) => !k.done && k.date && k.date >= ws && k.date < t);
    let h = '<div class="datenav"><button class="icon-btn" data-a="wnav" data-v="-7">‹</button><button class="dn-t" data-a="wnav" data-v="0">' + fmtShort(ws) + " – " + fmtShort(addDays(ws, 6)) + '</button><button class="icon-btn" data-a="wnav" data-v="7">›</button></div>';
    h += '<div class="card"><div class="row">' + A.ring(plan ? done / plan : 0, plan ? Math.round((done / plan) * 100) + "%" : "—", "выполнено") + '<div class="grow"><b>Неделя ' + esc(wk.split("-W")[1]) + '</b><div class="small muted">задачи и привычки: ' + A.fmtN(done, 1) + " из " + plan + "</div>" +
      (unfinished.length ? '<button class="btn sm" style="margin-top:6px" data-a="moveUndone">Перенести незавершённое на сегодня (' + unfinished.length + ")</button>" : "") + "</div></div></div>";

    h += '<div class="card peach"><h3>⭐ Главное дело недели</h3><input data-c="wk" data-k="main" value="' + esc(W.main) + '" placeholder="Одно главное дело">' +
      '<div class="form" style="margin-top:8px"><div class="fld full"><label>2–3 конкретных времени для него</label><input data-c="wk" data-k="times" value="' + esc(W.times || "") + '" placeholder="Вт 17:00, Чт 17:00, Сб 17:00"></div>' +
      '<div class="fld full"><label>Минимальная версия</label><input data-c="wk" data-k="minVer" value="' + esc(W.minVer || "") + '"></div>' +
      '<div class="fld full"><label>Что убираю заранее (одно необязательное дело)</label><input data-c="wk" data-k="remove" value="' + esc(W.remove || "") + '"></div>' +
      '<div class="fld full"><label>Критерий завершения недели</label><input data-c="wk" data-k="criterion" value="' + esc(W.criterion || "") + '"></div></div>' +
      '<p class="small muted">Воскресный протокол на 15 минут — из блока «Система действий».</p></div>';

    h += '<div class="card"><h3>Приоритеты на неделю</h3>' + [0, 1, 2].map((i) => '<div class="row" style="margin-bottom:6px"><b>' + (i + 1) + '.</b><input data-c="wkp" data-i="' + i + '" value="' + esc((W.prio || [])[i] || "") + '"></div>').join("") + "</div>";
    const rules = A.db().profile.rules || [];
    h += '<div class="card sage"><h3>Мои правила недели</h3>' + rules.map((r, i) => '<label class="switch"><input type="checkbox" data-c="wkr" data-i="' + i + '"' + (W.rules && W.rules[i] ? " checked" : "") + "><span></span>" + esc(r) + "</label>").join("") + "</div>";

    // сетка как в бумажном планере
    const slots = [];
    days.forEach((x) => A.eventsOn(x).forEach((e) => { if (e.start && !slots.includes(e.start)) slots.push(e.start); }));
    slots.sort((a, b) => hm2min(a) - hm2min(b));
    h += '<div class="card"><h3>Мой план недели<span class="sp"></span><button class="link" data-a="addEvent">+ событие</button></h3><div class="week-grid"><table><thead><tr><th></th>' + days.map((x, i) => '<th class="' + (x === t ? "today" : "") + '" data-a="openDay" data-d="' + x + '">' + DOW[i] + "<br>" + fmtShort(x) + "</th>").join("") + "</tr></thead><tbody>";
    slots.forEach((s) => {
      h += '<tr><td class="t">' + s + "</td>";
      days.forEach((x) => {
        const es = A.eventsOn(x).filter((e) => e.start === s);
        h += "<td>" + es.map((e) => { const c = A.cat(e.cat); return '<div class="wc" style="--c:' + c.color + '" data-a="event" data-id="' + e.id + '" data-d="' + x + '">' + esc(c.icon + " " + e.title) + "</div>"; }).join("") + "</td>";
      });
      h += "</tr>";
    });
    h += '</tbody></table></div><div class="legend" style="margin-top:8px">' + A.CATS.map((c) => '<span><i style="background:' + c[3] + '"></i>' + esc(c[1]) + "</span>").join("") + "</div></div>";

    // задачи недели
    h += '<div class="card"><h3>Задачи недели</h3>' + days.map((x, i) => { const ts = A.col("tasks").filter((k) => k.date === x); return ts.length ? '<div class="small muted" style="margin-top:6px">' + DOW[i] + " " + fmtShort(x) + '</div><div class="list">' + ts.map((k) => A.taskRow(k, x)).join("") + "</div>" : ""; }).join("") + '<button class="btn sm" data-a="addTask">+ задача</button></div>';

    // привычки недели
    const hs = A.col("habits").filter((hb) => !hb.archived);
    h += '<div class="card"><h3>Привычки недели</h3><div class="matrix-wrap"><table class="matrix wide"><thead><tr><th></th>' + days.map((x, i) => "<th>" + DOW[i] + "</th>").join("") + "</tr></thead><tbody>" +
      hs.map((hb) => '<tr><th class="rowh">' + esc((hb.icon || "") + " " + hb.name) + "</th>" + days.map((x) => { const st = A.hs(hb.id, x), S = st && A.HSTATUS[st]; const pl = A.habitPlanned(hb, x); return '<td data-a="whabit" data-id="' + hb.id + '" data-d="' + x + '" style="' + (S ? "background:" + S[2] + ";color:#fff" : pl ? "" : "opacity:.35") + '">' + (S ? S[1] : "") + "</td>"; }).join("") + "</tr>").join("") + "</tbody></table></div></div>";

    h += '<div class="card tint"><h3>Недельная сверка</h3><div class="form">' +
      [["q1", "Что я выбрала?"], ["q2", "Что я сделала?"], ["q3", "Что изменю?"], ["q4", "Мои действия на этой неделе были похожи на жизнь, которую я хочу?"]].map(([k, l]) => '<div class="fld full"><label>' + l + '</label><textarea rows="2" data-c="wk" data-k="' + k + '">' + esc(W[k] || "") + "</textarea></div>").join("") + "</div></div>";
    return h;
  }
  const isoKey = (ws) => A.isoWeek(ws);

  function monthView(mk, metric) {
    const [y, m] = mk.split("-").map(Number);
    const first = mk + "-01", start = weekStart(first);
    const t = today();
    let h = '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><button class="dn-t" data-a="mnav" data-v="0">' + esc(A.monthTitle(mk)) + '</button><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
    const metrics = [["", "События"], ["mood", "Настроение"], ["sleep", "Сон"], ["steps", "Шаги"], ["energy", "Энергия"], ["stress", "Стресс"], ["habits", "Привычки"], ["water", "Вода"], ["workout", "Тренировки"], ["kcal", "Калории"], ["pages", "Чтение"], ["spend", "Расходы"]];
    h += '<div class="chips" style="margin-bottom:10px;flex-wrap:nowrap;overflow-x:auto">' + metrics.map(([k, n]) => '<button class="chip' + (k === metric ? " on" : "") + '" data-a="mmetric" data-v="' + k + '" style="flex:0 0 auto">' + n + "</button>").join("") + "</div>";
    const scale = metric ? A.metricScale(metric) : null;
    h += '<div class="card"><div class="month">' + DOW.map((x, i) => '<div class="h' + (i >= 5 ? " we" : "") + '">' + x + "</div>").join("");
    for (let i = 0; i < 42; i++) {
      const d = addDays(start, i);
      if (i >= 35 && d.slice(0, 7) !== mk) break;
      const out = d.slice(0, 7) !== mk;
      let style = "", inner = "";
      if (metric) {
        const v = A.METRICS[metric].get(d);
        const c = v != null && d <= t ? scale.color(v) : null;
        if (c) style = "background:" + c + ";color:#222";
        inner = v != null ? '<small style="font-size:10px">' + esc(scale.short ? scale.short(v) : A.fmtN(v)) + "</small>" : "";
      } else {
        const ev = A.eventsOn(d).filter((e) => e.repeat === "none");
        const ts = A.col("tasks").filter((k) => k.date === d);
        const cats = [...new Set(ev.map((e) => A.cat(e.cat).color))].slice(0, 4);
        inner = '<div class="dots">' + cats.map((c) => '<i style="background:' + c + '"></i>').join("") + (ts.length ? '<i style="background:var(--ink2)"></i>' : "") + "</div>";
      }
      h += '<button class="mday' + (out ? " out" : "") + (d === t ? " now" : "") + '" data-a="openDay" data-d="' + d + '" style="' + style + '">' + parse(d).getDate() + inner + "</button>";
    }
    h += "</div>";
    if (scale && scale.legend) h += '<div class="mlegend">' + scale.legend.map(([c, l]) => '<div><i style="background:' + c + '"></i>' + esc(l) + "</div>").join("") + "</div>";
    h += "</div>";
    if (!metric) {
      const once = A.col("events").filter((e) => e.repeat === "none" && e.date.slice(0, 7) === mk).sort((a, b) => a.date > b.date ? 1 : -1);
      h += '<div class="card"><h3>События месяца<span class="sp"></span><button class="link" data-a="addEvent">+</button></h3>' + (once.length ? once.map((e) => '<div class="item" data-a="event" data-id="' + e.id + '" data-d="' + e.date + '"><span class="dot" style="background:' + A.cat(e.cat).color + '"></span><div class="tx"><b>' + esc(e.title) + "</b><small>" + fmtDate(e.date) + " " + esc(e.start || "") + "</small></div></div>").join("") : A.empty("Разовых событий нет")) + "</div>";
    }
    return h;
  }

  A.view("calendar", {
    root: true, tab: "calendar", title: "План",
    render(el, r) {
      const mode = r.args[0] || "week";
      const d = r.params.d || today();
      let h = A.seg([["day", "День"], ["week", "Неделя"], ["month", "Месяц"], ["tasks", "Задачи"]], mode, "mode");
      if (mode === "day") h += dayView(d);
      else if (mode === "month") h += monthView(r.params.m || d.slice(0, 7), r.params.metric || "");
      else h += weekView(d);
      return h;
    },
    bind(el, r) {
      const mode = r.args[0] || "week";
      const d = () => A.route().params.d || today();
      const P = A.route().params;
      A.bind(el, {
        mode(b) { if (b.dataset.v === "tasks") return A.go("tasks"); A.go("calendar/" + b.dataset.v + (P.d ? "?d=" + P.d : ""), true); },
        dnav(b) { if (b.dataset.v === "pick") return A.pickDate(d(), (v) => A.go("calendar/day?d=" + v, true)); A.go("calendar/day?d=" + addDays(d(), +b.dataset.v), true); },
        wnav(b) { if (b.dataset.v === "0") return A.go("calendar/week", true); A.go("calendar/week?d=" + addDays(d(), +b.dataset.v), true); },
        mnav(b) {
          const mk = P.m || d().slice(0, 7); let [y, m] = mk.split("-").map(Number);
          if (b.dataset.v === "0") { y = new Date().getFullYear(); m = new Date().getMonth() + 1; } else { m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } }
          A.go("calendar/month?m=" + y + "-" + pad(m) + (P.metric ? "&metric=" + P.metric : ""), true);
        },
        mmetric(b) { A.go("calendar/month?m=" + (P.m || d().slice(0, 7)) + (b.dataset.v ? "&metric=" + b.dataset.v : ""), true); },
        openDay(b) { A.go("calendar/day?d=" + b.dataset.d); },
        event(b) { A.edit.event(A.byId("events", b.dataset.id), b.dataset.d); },
        addEvent() { A.edit.event(null, mode === "day" ? d() : today()); },
        addTask() { A.edit.task(null, { date: mode === "day" ? d() : today() }); },
        taskDone(b) { A.completeTask(A.byId("tasks", b.dataset.id)); A.vibe(); A.refresh(); },
        task(b) { A.edit.task(A.byId("tasks", b.dataset.id)); },
        whabit(b) { A.cycleHs(A.byId("habits", b.dataset.id), b.dataset.d); A.refresh(); },
        wk(b) { const W = A.db().weeks[isoKey(weekStart(d()))]; W[b.dataset.k] = b.value.trim(); A.save(); },
        wkp(b) { const W = A.db().weeks[isoKey(weekStart(d()))]; W.prio = W.prio || []; W.prio[+b.dataset.i] = b.value.trim(); A.save(); },
        wkr(b) { const W = A.db().weeks[isoKey(weekStart(d()))]; W.rules = W.rules || {}; W.rules[b.dataset.i] = b.checked; A.save(); },
        moveUndone() {
          const ws = weekStart(d()), t = today();
          const list = A.col("tasks").filter((k) => !k.done && k.date && k.date >= ws && k.date < t);
          list.forEach((k) => { k.moved = (k.moved || 0) + 1; k.date = t; });
          A.save(); A.toast("Перенесено: " + list.length); A.refresh();
        }
      });
    }
  });

  /* ---------- задачи ---------- */
  A.view("tasks", {
    title: "Задачи", tab: "calendar",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.go(\'ideas\')" title="Контейнер идей" aria-label="Идеи">💡</button>'; },
    render(el, r) {
      const f = r.params.f || "today", t = today();
      let list = A.col("tasks");
      if (f === "today") list = list.filter((x) => !x.done && x.date && x.date <= t);
      else if (f === "next") list = list.filter((x) => !x.done && x.date > t);
      else if (f === "inbox") list = list.filter((x) => !x.done && !x.date);
      else list = list.filter((x) => x.done).sort((a, b) => (b.doneAt || "") > (a.doneAt || "") ? 1 : -1).slice(0, 100);
      if (f !== "done") list.sort((a, b) => (a.date || "9") > (b.date || "9") ? 1 : a.date === b.date ? (a.prio || 2) - (b.prio || 2) : -1);
      let h = A.seg([["today", "Сегодня"], ["next", "Предстоящие"], ["inbox", "Без даты"], ["done", "Готово"]], f, "f");
      h += '<div class="card"><div class="inline-add"><input id="qt" placeholder="' + (f === "inbox" ? "Задача без даты" : "Новая задача") + '"><button class="btn primary" data-a="add">+</button></div><p class="small muted" style="margin-top:6px">Формула задачи: глагол + объект + объём + срок.</p></div>';
      if (f === "next") {
        let last = "";
        h += '<div class="card"><div class="list">';
        list.forEach((x) => { if (x.date !== last) { last = x.date; h += '<div class="small muted" style="margin-top:8px">' + fmtDate(x.date, { dow: true }) + "</div>"; } h += A.taskRow(x); });
        h += (list.length ? "" : A.empty("Нет предстоящих задач")) + "</div></div>";
      } else h += '<div class="card"><div class="list">' + (list.length ? list.map((x) => A.taskRow(x)).join("") : A.empty(f === "today" ? "На сегодня всё. Можно отдыхать 💜" : "Пусто")) + "</div></div>";
      h += '<button class="fab" data-a="full" aria-label="Подробная задача">+</button>';
      return h;
    },
    bind(el, r) {
      const f = r.params.f || "today";
      const add = () => { const i = el.querySelector("#qt"); const v = i.value.trim(); if (!v) return; A.upsert("tasks", { title: v, date: f === "inbox" ? "" : f === "next" ? addDays(today(), 1) : today(), prio: 2, done: false, created: today() }); A.refresh(); };
      el.querySelector("#qt").addEventListener("keydown", (e) => { if (e.key === "Enter") add(); });
      A.bind(el, {
        f(b) { A.go("tasks?f=" + b.dataset.v, true); }, add,
        full() { A.edit.task(null, { date: f === "inbox" ? "" : today() }); },
        taskDone(b) { A.completeTask(A.byId("tasks", b.dataset.id)); A.vibe(); A.refresh(); },
        task(b) { A.edit.task(A.byId("tasks", b.dataset.id)); }
      });
    }
  });

  /* ---------- контейнер идей ---------- */
  A.view("ideas", {
    title: "Контейнер идей", tab: "calendar",
    render(el) {
      const st = { new: "Новая", exp: "Эксперимент", grow: "Развивать", arch: "Архив", closed: "Закрыта" };
      const ideas = A.col("ideas").slice().reverse();
      return '<div class="card tint"><p class="small">Новые идеи — только в список. Для каждой: записать → что именно интересно → минимальный эксперимент на 1–3 дня → результат → развивать, архивировать или закрыть.</p><div class="inline-add"><input id="qi" placeholder="Идея"><button class="btn primary" data-a="add">+</button></div></div>' +
        '<div class="card"><div class="list">' + (ideas.length ? ideas.map((i) => '<div class="item" data-a="edit" data-id="' + i.id + '"><div class="ic">💡</div><div class="tx"><b>' + esc(i.text) + "</b><small>" + esc(st[i.status || "new"]) + (i.exp ? " · эксперимент: " + esc(i.exp) : "") + "</small></div></div>").join("") : A.empty("Идей пока нет")) + "</div></div>";
    },
    bind(el) {
      A.bind(el, {
        add() { const v = el.querySelector("#qi").value.trim(); if (!v) return; A.upsert("ideas", { text: v, date: today(), status: "new" }); A.refresh(); },
        edit(b) {
          const i = A.byId("ideas", b.dataset.id);
          A.formSheet("Идея", [{ k: "text", label: "Идея", full: true }, { k: "why", label: "Что именно интересно", type: "textarea" }, { k: "exp", label: "Минимальный эксперимент на 1–3 дня", full: true }, { k: "result", label: "Результат", type: "textarea" }, { k: "status", label: "Решение", type: "chips", opts: [["new", "Новая"], ["exp", "Эксперимент"], ["grow", "Развивать"], ["arch", "Архив"], ["closed", "Закрыть"]] }], i,
            (o) => { Object.assign(i, o); A.save(); A.refresh(); }, { onDelete: () => { A.remove("ideas", i.id); A.refresh(); }, post: '<div class="btns"><button class="btn sm" id="toTask">Сделать задачей</button></div>', after: (w) => { w.querySelector("#toTask").onclick = () => { A.closeSheet(); A.edit.task(null, { title: i.exp || i.text }); }; } });
        }
      });
    }
  });

  /* ---------- привычки ---------- */
  A.view("habits", {
    title: "Привычки",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.habit()" aria-label="Добавить">＋</button>'; },
    render(el, r) {
      const t = today(), mk = r.params.m || t.slice(0, 7);
      const [y, m] = mk.split("-").map(Number);
      const from = mk + "-01", to = mk + "-" + pad(A.daysInMonth(y, m));
      const hs = A.col("habits").filter((h) => !h.archived);
      const p = A.db().profile;
      let h = '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
      h += '<div class="card"><h3>Трекер привычек</h3>' + A.charts.monthGrid(mk, hs.map((x) => ({ id: x.id, name: (x.icon || "") + " " + x.name })), (row, d) => { const st = A.hs(row.id, d); if (st) { const S = A.HSTATUS[st]; return { c: S[2], s: st === "none" ? "" : S[1], t: S[0] }; } const hb = A.byId("habits", row.id); return !A.habitPlanned(hb, d) ? { c: "transparent" } : null; }) +
        '<div class="mlegend">' + Object.values(A.HSTATUS).map((S) => '<div><i style="background:' + S[2] + '"></i>' + S[1] + " " + S[0] + "</div>").join("") + "</div></div>";
      h += '<div class="card"><h3>План / факт за месяц</h3><div class="list">' + hs.map((x) => { const s = A.habitStats(x, from, to > t ? t : to); return '<div class="item" data-a="edit" data-id="' + x.id + '"><div class="ic">' + esc(x.icon || "•") + '</div><div class="tx"><b>' + esc(x.name) + "</b><small>" + (x.freq === "daily" ? "ежедневно" : x.freq === "weekdays" ? (x.days || []).map((k) => DOW[k - 1]).join(", ") : (x.perWeek || 3) + " раз в неделю") + " · серия " + A.habitStreak(x) + " · факт " + A.fmtN(s.done, 1) + "/" + s.plan + "</small>" + A.bar(s.pct || 0, "var(--sage)") + '</div><b class="num">' + (s.pct == null ? "—" : Math.round(s.pct * 100) + "%") + "</b></div>"; }).join("") + "</div></div>";
      if (p.points) {
        const pts = Object.values(A.db().hlog).reduce((s, v) => s + (v === "done" ? 2 : v === "part" ? 1 : 0), 0) - A.sum(A.col("rewards").filter((x) => x.claimed).map((x) => +x.cost || 0));
        h += '<div class="card peach"><h3>🎁 Баллы и награды<span class="sp"></span><b>' + pts + ' б.</b></h3><p class="small muted">Выполнено = 2 балла, частично = 1. Пропуски баллы не отнимают.</p><div class="list">' + A.col("rewards").map((rw) => '<div class="item"><div class="tx"><b>' + esc(rw.name) + "</b><small>" + rw.cost + " б." + (rw.claimed ? " · получена " + fmtShort(rw.claimed) : "") + "</small></div>" + (rw.claimed ? "" : '<button class="btn sm" data-a="claim" data-id="' + rw.id + '"' + (pts < rw.cost ? " disabled" : "") + ">Получить</button>") + "</div>").join("") + '</div><button class="btn sm" data-a="addReward">+ награда</button></div>';
      }
      const arch = A.col("habits").filter((x) => x.archived);
      if (arch.length) h += '<div class="card"><h3>Архив</h3>' + arch.map((x) => '<div class="item" data-a="edit" data-id="' + x.id + '"><div class="tx"><b>' + esc(x.name) + "</b></div></div>").join("") + "</div>";
      return h;
    },
    bind(el, r) {
      A.bind(el, {
        mnav(b) { const mk = A.route().params.m || today().slice(0, 7); let [y, m] = mk.split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } A.go("habits?m=" + y + "-" + pad(m), true); },
        gcell(b) { if (b.dataset.d > today()) return; A.cycleHs(A.byId("habits", b.dataset.id), b.dataset.d); A.refresh(); },
        edit(b) { A.edit.habit(A.byId("habits", b.dataset.id)); },
        addReward() { A.formSheet("Награда", [{ k: "name", label: "Награда", req: true, full: true }, { k: "cost", label: "Стоимость в баллах", type: "number" }], {}, (o) => { A.upsert("rewards", o); A.refresh(); }); },
        claim(b) { const rw = A.byId("rewards", b.dataset.id); rw.claimed = today(); A.save(); A.toast("Ты заслужила 💜"); A.refresh(); }
      });
    }
  });

  /* ---------- напоминания ---------- */
  A.view("reminders", {
    title: "Напоминания",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.reminder()" aria-label="Добавить">＋</button>'; },
    render() {
      const types = { once: "разово", daily: "ежедневно", weekdays: "по дням", weekly: "еженедельно", monthly: "ежемесячно" };
      const rs = A.col("reminders");
      let h = "";
      if (A.native && !A.native.notificationsAllowed()) h += '<div class="card peach row"><div class="grow small">Уведомления выключены в системе.</div><button class="btn sm primary" data-a="perm">Разрешить</button></div>';
      if (!A.native) h += '<div class="warn" style="margin-bottom:12px">Системные уведомления работают в Android-приложении. Здесь напоминания видны на экране «Сегодня».</div>';
      h += '<div class="card"><div class="list">' + (rs.length ? rs.map((r) => '<div class="item"><div class="ic">🔔</div><div class="tx" data-a="edit" data-id="' + r.id + '"><b>' + esc(r.title) + "</b><small>" + esc(r.time + " · " + types[r.type] + (r.type === "once" ? " " + fmtShort(r.date || today()) : "") + (r.type === "weekdays" || r.type === "weekly" ? " " + (r.days || []).map((k) => DOW[k - 1]).join(",") : "") + (r.type === "monthly" ? " " + r.dom + " числа" : "")) + '</small></div><label class="switch"><input type="checkbox" data-c="toggle" data-id="' + r.id + '"' + (r.on ? " checked" : "") + "><span></span></label></div>").join("") : A.empty("Напоминаний нет")) + "</div></div>";
      h += '<p class="small muted">Также уведомления приходят для привычек со временем, событий с напоминанием, задач с отметкой «напомнить» и регулярных платежей. Все типы отключаются в Настройках → Уведомления.</p>';
      if (A.native) h += '<button class="btn block" data-a="test">Проверить уведомление</button>';
      return h;
    },
    bind(el) {
      A.bind(el, {
        edit(b) { A.edit.reminder(A.byId("reminders", b.dataset.id)); },
        toggle(b) { A.byId("reminders", b.dataset.id).on = b.checked; A.save(); A.syncReminders(); },
        perm() { A.nativeCall("notif", () => A.native.requestNotifications()).then(() => A.refresh()); },
        test() { A.native.testNotification("Путь жизни", "Уведомления работают 💜"); }
      });
    }
  });
})();
