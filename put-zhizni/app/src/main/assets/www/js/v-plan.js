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
      { k: "location", label: "Место", full: true, ph: "Адрес, кабинет, ссылка" },
      { k: "cat", label: "Категория", type: "select", opts: A.catOpts() },
      { k: "color", label: "Цвет (если нужен свой)", type: "chips", opts: [["", "как у категории"]].concat(A.PAL.map((c) => [c, "●"])) },
      { k: "prio", label: "Приоритет", type: "select", opts: [[1, "Высокий"], [2, "Обычный"], [3, "Низкий"]] },
      { k: "date", label: e.repeat && e.repeat !== "none" ? "Начало повторов" : "Дата", type: "date" },
      { k: "must", label: "Тип", type: "check", text: "Обязательное дело" },
      { k: "start", label: "Начало", type: "time" }, { k: "end", label: "Конец", type: "time" },
      { k: "repeat", label: "Повторение", type: "select", opts: REPEAT },
      { k: "until", label: "Повторять до", type: "date" },
      { k: "days", label: "Дни недели (для «По дням недели»)", type: "multi", opts: DAYS_OPTS, numeric: true },
      { k: "remind", label: "Напоминание", type: "select", opts: [["", "Без напоминания"], [0, "В момент начала"], [5, "За 5 минут"], [15, "За 15 минут"], [30, "За 30 минут"], [60, "За 1 час"], [120, "За 2 часа"], [1440, "За день"]] },
      { k: "note", label: "Описание", type: "textarea" }
    ];
    const opts = { after: (w) => w.querySelectorAll('.chips[data-field="color"] .chip').forEach((c) => { if (c.dataset.v) c.style.color = c.dataset.v; }) };
    if (!isNew) {
      opts.onDelete = () => { A.remove("events", e.id); A.syncReminders(); A.refresh(); };
      if (e.repeat !== "none" && d) opts.post = '<div class="btns"><button class="btn sm ghost" id="skipOne">Пропустить только ' + fmtShort(d) + "</button></div>";
      const a0 = opts.after; opts.after = (w) => { a0(w); const b = w.querySelector("#skipOne"); if (b) b.onclick = () => { e.skip = (e.skip || []).concat(d); A.save(); A.closeSheet(); A.refresh(); }; };
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
    t = t || Object.assign({ title: "", desc: "", date: today(), time: "", prio: 2, projectId: "", tags: [], subtasks: [], must: false, minVer: "", goalId: "", note: "", done: false, repeat: "none", remind: false, archived: false }, preset);
    const subs = (t.subtasks || []).map((x) => Object.assign({}, x));
    const fields = [
      { k: "title", label: "Заголовок", req: true, full: true, ph: "Глагол + объект + объём + срок" },
      { k: "desc", label: "Подробное описание", type: "textarea", rows: 3 },
      { k: "projectId", label: "Проект", type: "select", opts: () => [["", "— без проекта —"]].concat(A.col("projects").filter((p) => !p.archived || p.id === t.projectId).map((p) => [p.id, p.name])) },
      { k: "prio", label: "Приоритет", type: "chips", numeric: true, opts: [[1, "{{i:dot-high}} Высокий"], [2, "{{i:dot-mid}} Средний"], [3, "{{i:dot-low}} Низкий"]] },
      { k: "date", label: "Срок: дата", type: "date" }, { k: "time", label: "Срок: время", type: "time" },
      { k: "repeat", label: "Повторение", type: "select", opts: [["none", "Один раз"], ["daily", "Ежедневно"], ["weekly", "Еженедельно"], ["monthly", "Ежемесячно"]] },
      { k: "remind", label: "Напоминание", type: "check", text: "Уведомить в срок" },
      { k: "tagsStr", label: "Теги (через запятую)", full: true, ph: "звонки, дом, срочно" },
      { k: "goalId", label: "Связанная цель", type: "select", opts: () => [["", "—"]].concat(A.col("goals").map((g) => [g.id, g.title])), full: true },
      { k: "minVer", label: "Минимальная версия", full: true, hint: "Если сопротивление высокое — уменьшить объём в два раза, а не отказаться" },
      { k: "must", label: "Тип", type: "check", text: "Обязательно" },
      { k: "archived", label: "Архив", type: "check", text: "В архиве" }
    ];
    const subHtml = () => '<div class="fsec">Подзадачи</div>' + subs.map((x, i) => '<div class="row" style="margin:4px 0"><button type="button" class="check' + (x.done ? " on" : "") + '" data-st="' + i + '">' + (x.done ? "✓" : "") + '</button><input data-sti="' + i + '" value="' + esc(x.t) + '"><button type="button" class="icon-btn" data-sd="' + i + '" aria-label="Удалить">✕</button></div>').join("") + '<div class="inline-add" style="margin-top:6px"><input id="newSub" placeholder="Новая подзадача"><button type="button" class="btn" id="addSub">+</button></div>';
    A.formSheet(isNew ? "Новая задача" : "Задача", fields, Object.assign({}, t, { tagsStr: (t.tags || []).join(", ") }), (o) => {
      o.tags = (o.tagsStr || "").split(",").map((x) => x.trim()).filter(Boolean); delete o.tagsStr;
      Object.assign(t, o); t.prio = +o.prio || 2; t.subtasks = subs.filter((x) => x.t);
      if (isNew) t.created = new Date().toISOString();
      A.upsert("tasks", t); A.syncReminders(); A.refresh();
    }, {
      post: '<div id="subBox"></div>' + (!isNew && t.doneAt ? '<p class="small muted">Выполнена: ' + new Date(t.doneAt.length > 10 ? t.doneAt : t.doneAt + "T12:00").toLocaleString("ru-RU") + "</p>" : ""),
      onDelete: isNew ? null : () => { A.remove("tasks", t.id); A.syncReminders(); A.refresh(); },
      after(w) {
        const box = w.querySelector("#subBox"), draw = () => (box.innerHTML = subHtml()); draw();
        const add = () => { const v = box.querySelector("#newSub").value.trim(); if (v) { subs.push({ t: v, done: false }); draw(); box.querySelector("#newSub").focus(); } };
        box.addEventListener("click", (e) => {
          if (e.target.id === "addSub") add();
          if (e.target.dataset.st != null) { subs[+e.target.dataset.st].done = !subs[+e.target.dataset.st].done; draw(); }
          if (e.target.dataset.sd != null) { subs.splice(+e.target.dataset.sd, 1); draw(); }
        });
        box.addEventListener("keydown", (e) => { if (e.target.id === "newSub" && e.key === "Enter") { e.preventDefault(); add(); } });
        box.addEventListener("input", (e) => { if (e.target.dataset.sti != null) subs[+e.target.dataset.sti].t = e.target.value; });
      }
    });
  };
  A.edit.project = (p) => {
    const isNew = !p;
    p = p || { name: "", color: A.PAL[A.col("projects").length % A.PAL.length], order: A.col("projects").length, archived: false };
    A.formSheet(isNew ? "Новый проект" : "Проект", [{ k: "name", label: "Название", req: true, full: true }, { k: "color", label: "Цвет", type: "chips", opts: A.PAL.map((c) => [c, "●"]) }, { k: "desc", label: "Описание", type: "textarea", rows: 2 }, { k: "archived", label: "Архив", type: "check", text: "Проект в архиве" }], p, (o) => { Object.assign(p, o); A.upsert("projects", p); A.refresh(); },
      Object.assign({ after: (w) => w.querySelectorAll('.chips[data-field="color"] .chip').forEach((c) => (c.style.color = c.dataset.v)) }, isNew ? {} : { onDelete: () => { A.confirm("Удалить проект? Задачи останутся без проекта.", () => { A.col("tasks").forEach((t) => { if (t.projectId === p.id) t.projectId = ""; }); A.remove("projects", p.id); A.refresh(); }, "Удалить", true); } }));
  };
  // Выполнение повторяющейся задачи создаёт следующую.
  A.completeTask = (t) => {
    t.done = !t.done; t.doneAt = t.done ? new Date().toISOString() : "";
    if (t.done && t.repeat && t.repeat !== "none" && t.date && !t.spawned) {
      const nd = t.repeat === "daily" ? addDays(t.date, 1) : t.repeat === "weekly" ? addDays(t.date, 7) : A.nextMonthly(t.date, parse(t.date).getDate());
      A.col("tasks").push(Object.assign({}, t, { id: A.uid(), date: nd, done: false, doneAt: "", spawned: false }));
      t.spawned = true;
    }
    A.save();
  };

  A.edit.habit = (h) => {
    const isNew = !h;
    h = h || { name: "", icon: "{{i:sparkles}}", cat: "other", freq: "daily", days: [1, 2, 3, 4, 5, 6, 7], perWeek: 3, min: "", time: "", remind: false, start: today(), end: "", note: "", archived: false };
    const fields = [
      { k: "icon", label: "Иконка", type: "icon" }, { k: "name", label: "Название", req: true },
      { k: "cat", label: "Категория", type: "select", opts: [["health", "Здоровье"], ["sport", "Спорт"], ["learn", "Обучение"], ["home", "Дом"], ["money", "Финансы"], ["family", "Отношения"], ["rest", "Восстановление"], ["other", "Другое"]] },
      { k: "freq", label: "Периодичность", type: "select", opts: [["daily", "Ежедневно"], ["weekdays", "По дням недели"], ["xweek", "Еженедельно: N раз в неделю"]] },
      { k: "type", label: "Отслеживание", type: "chips", opts: [["check", "✓ галочка"], ["num", "{{i:hash}} число"]] },
      { k: "target", label: "Цель в день (число)", type: "number", step: "any", ph: "1, 10, 2" },
      { k: "unit", label: "Единица", ph: "раз, км, л, мин, стр." },
      { k: "days", label: "Дни недели", type: "multi", opts: DAYS_OPTS, numeric: true },
      { k: "perWeek", label: "Раз в неделю (для N раз)", type: "number", min: 1, max: 7 },
      { k: "min", label: "Минимальная цель", ph: "например, 10 минут" },
      { k: "time", label: "Время", type: "time" }, { k: "remind", label: "Напоминание", type: "check", text: "Напоминать" },
      { k: "start", label: "Дата начала", type: "date" }, { k: "end", label: "Дата окончания", type: "date" },
      { k: "note", label: "Комментарий", type: "textarea" },
      { k: "archived", label: "Архив", type: "check", text: "В архиве (не показывать)" }
    ];
    if (!h.type) h.type = "check";
    A.formSheet(isNew ? "Новая привычка" : "Привычка", fields, h, (o) => { Object.assign(h, o); if (h.type === "num" && !h.target) h.target = 1; h.mask = A.daysMask(h.freq === "daily" ? [1, 2, 3, 4, 5, 6, 7] : h.days); A.upsert("habits", h); A.syncReminders(); A.refresh(); },
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
  const evCard = (e, d) => { const c = A.cat(e.cat); return '<div class="ev" style="--c:' + (e.color || c.color) + '" data-a="event" data-id="' + e.id + '" data-d="' + d + '"><b>' + A.icon(c.icon) + " " + esc(e.title) + "</b><small>" + esc((e.start || "") + (e.end ? "–" + e.end : "") + (e.location ? " · {{i:map-pin}} " + e.location : "")) + (e.must ? " · обязательно" : " · желательно") + (e.repeat !== "none" ? " · ↻" : "") + "</small></div>"; };

  function dayView(d) {
    const ev = A.eventsOn(d);
    const tasks = A.col("tasks").filter((t) => t.date === d);
    let h = A.dateNav(d);
    h += '<div class="card"><h3>Задачи со сроком на этот день<span class="sp"></span><button class="link" data-a="addTask">+ задача</button></h3><div class="list">' + (tasks.length ? tasks.map((t) => A.taskRow(t, d)).join("") : A.empty("Задач нет")) + '</div><a class="link" href="#/tasks?f=all">Перейти в задачи →</a></div>';
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

    h += '<div class="card peach"><h3>{{i:star}} Главное дело недели</h3><input data-c="wk" data-k="main" value="' + esc(W.main) + '" placeholder="Одно главное дело">' +
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
        h += "<td>" + es.map((e) => { const c = A.cat(e.cat); return '<div class="wc" style="--c:' + c.color + '" data-a="event" data-id="' + e.id + '" data-d="' + x + '">' + A.icon(c.icon) + " " + esc(e.title) + "</div>"; }).join("") + "</td>";
      });
      h += "</tr>";
    });
    h += '</tbody></table></div><div class="legend" style="margin-top:8px">' + A.CATS.map((c) => '<span><i style="background:' + c[3] + '"></i>' + esc(c[1]) + "</span>").join("") + "</div></div>";

    // задачи недели
    h += '<div class="card"><h3>Задачи недели</h3>' + days.map((x, i) => { const ts = A.col("tasks").filter((k) => k.date === x); return ts.length ? '<div class="small muted" style="margin-top:6px">' + DOW[i] + " " + fmtShort(x) + '</div><div class="list">' + ts.map((k) => A.taskRow(k, x)).join("") + "</div>" : ""; }).join("") + '<button class="btn sm" data-a="addTask">+ задача</button></div>';

    // привычки недели
    const hs = A.col("habits").filter((hb) => !hb.archived);
    h += '<div class="card"><h3>Привычки недели</h3><div class="matrix-wrap"><table class="matrix wide"><thead><tr><th></th>' + days.map((x, i) => "<th>" + DOW[i] + "</th>").join("") + "</tr></thead><tbody>" +
      hs.map((hb) => '<tr><th class="rowh">' + A.icon(hb.icon) + " " + esc(hb.name) + "</th>" + days.map((x) => { const st = A.hs(hb.id, x), S = st && A.HSTATUS[st]; const pl = A.habitPlanned(hb, x); return '<td data-a="whabit" data-id="' + hb.id + '" data-d="' + x + '" style="' + (S ? "background:" + S[2] + ";color:#fff" : pl ? "" : "opacity:.35") + '">' + (S ? S[1] : "") + "</td>"; }).join("") + "</tr>").join("") + "</tbody></table></div></div>";

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
        const cats = [...new Set(ev.map((e) => e.color || A.cat(e.cat).color))].slice(0, 4);
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
  const sortTasks = (list, by) => list.sort((a, b) => {
    if (by === "prio") return (a.prio || 2) - (b.prio || 2) || ((a.date || "9") > (b.date || "9") ? 1 : -1);
    if (by === "project") { const pa = A.project(a.projectId), pb = A.project(b.projectId); return (pa ? pa.order : 999) - (pb ? pb.order : 999) || ((a.date || "9") > (b.date || "9") ? 1 : -1); }
    return ((a.date || "9") + (a.time || "99")) > ((b.date || "9") + (b.time || "99")) ? 1 : ((a.date || "9") === (b.date || "9") ? (a.prio || 2) - (b.prio || 2) : -1);
  });
  A.view("tasks", {
    title: "Задачи", tab: "calendar",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.go(\'projects\')" title="Проекты" aria-label="Проекты">{{i:folder}}</button><button class="icon-btn" onclick="App.go(\'ideas\')" title="Контейнер идей" aria-label="Идеи">{{i:lightbulb}}</button>'; },
    render(el, r) {
      const f = r.params.f || "today", t = today(), by = r.params.s || A.db().profile.taskSort || "date", pid = r.params.p || "";
      let list = A.col("tasks").filter((x) => f === "archive" ? x.archived : !x.archived);
      if (pid) list = list.filter((x) => x.projectId === pid);
      if (f === "today") list = list.filter((x) => !x.done && x.date && x.date <= t);
      else if (f === "next") list = list.filter((x) => !x.done && x.date > t);
      else if (f === "inbox") list = list.filter((x) => !x.done && !x.date);
      else if (f === "all") list = list.filter((x) => !x.done);
      else if (f === "done") list = list.filter((x) => x.done).sort((a, b) => ((b.doneAt || "") > (a.doneAt || "") ? 1 : -1)).slice(0, 150);
      if (f !== "done") sortTasks(list, by);
      let h = A.seg([["today", "Сегодня"], ["next", "Скоро"], ["inbox", "Без даты"], ["all", "Все"], ["done", "Готово"], ["archive", "Архив"]], f, "f");
      const projs = A.col("projects").filter((p) => !p.archived).sort((a, b) => a.order - b.order);
      h += '<div class="chips" style="margin-bottom:10px;flex-wrap:nowrap;overflow-x:auto"><button class="chip' + (!pid ? " on" : "") + '" data-a="proj" data-v="" style="flex:0 0 auto">Все проекты</button>' + projs.map((p) => '<button class="chip' + (pid === p.id ? " on" : "") + '" data-a="proj" data-v="' + p.id + '" style="flex:0 0 auto"><span class="dot" style="background:' + p.color + ';margin-right:6px"></span>' + esc(p.name) + "</button>").join("") + "</div>";
      h += '<div class="card nocollapse"><div class="inline-add"><input id="qt" placeholder="' + (f === "inbox" ? "Задача без даты" : "Новая задача") + '"><button class="btn primary" data-a="add">+</button></div><div class="row" style="margin-top:8px"><span class="small muted grow">Сортировка</span>' + A.seg([["date", "дата"], ["prio", "приоритет"], ["project", "проект"]], by, "sort").replace('class="seg"', 'class="seg" style="margin:0;flex:0 0 auto"') + "</div></div>";
      if (f === "next" || (f === "all" && by === "date")) {
        let last = "@";
        h += '<div class="card"><div class="list">';
        list.forEach((x) => { if ((x.date || "") !== last) { last = x.date || ""; h += '<div class="small muted" style="margin-top:8px">' + (x.date ? fmtDate(x.date, { dow: true }) : "Без даты") + "</div>"; } h += A.taskRow(x); });
        h += (list.length ? "" : A.empty("Нет задач")) + "</div></div>";
      } else h += '<div class="card"><div class="list">' + (list.length ? list.map((x) => A.taskRow(x)).join("") : A.empty(f === "today" ? "На сегодня всё. Можно отдыхать {{i:heart}}" : "Пусто")) + "</div></div>";
      if (f === "done") { const old = A.col("tasks").filter((x) => x.done && !x.archived && (x.doneAt || "").slice(0, 10) < addDays(t, -7)).length; if (old) h += '<button class="btn block" data-a="archOld">Архивировать выполненные старше 7 дней (' + old + ")</button>"; }
      h += '<button class="fab" data-a="full" aria-label="Подробная задача">+</button>';
      return h;
    },
    bind(el, r) {
      const P = A.route().params, f = P.f || "today";
      const q = (o) => { const x = Object.assign({ f: P.f || "", p: P.p || "", s: P.s || "" }, o); A.go("tasks?" + Object.keys(x).filter((k) => x[k]).map((k) => k + "=" + x[k]).join("&"), true); };
      const add = () => { const i = el.querySelector("#qt"); const v = i.value.trim(); if (!v) return; A.upsert("tasks", { title: v, date: f === "inbox" ? "" : f === "next" ? addDays(today(), 1) : today(), prio: 2, projectId: P.p || "", tags: [], subtasks: [], done: false, created: new Date().toISOString() }); A.refresh(); };
      el.querySelector("#qt").addEventListener("keydown", (e) => { if (e.key === "Enter") add(); });
      A.bind(el, {
        f(b) { q({ f: b.dataset.v }); }, proj(b) { q({ p: b.dataset.v }); }, sort(b) { A.db().profile.taskSort = b.dataset.v; A.save(); q({ s: b.dataset.v }); }, add,
        full() { A.edit.task(null, { date: f === "inbox" ? "" : today(), projectId: P.p || "" }); },
        taskDone(b) { A.completeTask(A.byId("tasks", b.dataset.id)); A.vibe(); A.refresh(); },
        task(b) { A.edit.task(A.byId("tasks", b.dataset.id)); },
        archOld() { const t = today(); let n = 0; A.col("tasks").forEach((x) => { if (x.done && !x.archived && (x.doneAt || "").slice(0, 10) < addDays(t, -7)) { x.archived = true; n++; } }); A.save(); A.toast("В архив: " + n); A.refresh(); }
      });
    }
  });

  /* ---------- проекты ---------- */
  A.view("projects", {
    title: "Проекты", tab: "calendar",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.project()" aria-label="Новый проект">{{i:plus}}</button>'; },
    render(el, r) {
      const showArch = r.params.a === "1";
      const ps = A.col("projects").filter((p) => !!p.archived === showArch).sort((a, b) => a.order - b.order);
      let h = A.seg([["0", "Активные"], ["1", "Архив"]], showArch ? "1" : "0", "arch");
      h += '<div class="card"><div class="list">' + (ps.length ? ps.map((p, i) => { const ts = A.col("tasks").filter((t) => t.projectId === p.id && !t.archived); const d = ts.filter((t) => t.done).length; return '<div class="item"><span class="dot" style="background:' + p.color + ';width:14px;height:14px"></span><div class="tx tap" data-a="open" data-id="' + p.id + '"><b>' + esc(p.name) + "</b><small>задач: " + (ts.length - d) + " открыто · " + d + " готово</small>" + A.bar(ts.length ? d / ts.length : 0, p.color) + '</div><button class="icon-btn" data-a="up" data-i="' + i + '" aria-label="Выше">↑</button><button class="icon-btn" data-a="edit" data-id="' + p.id + '" aria-label="Изменить">{{i:pencil}}</button></div>'; }).join("") : A.empty(showArch ? "Архив пуст" : "Проектов нет")) + "</div></div>";
      h += '<p class="small muted">Проекты — это папки для задач. Правило одного проекта: один главный проект на 30–90 дней, новые идеи — в список.</p>';
      return h;
    },
    bind(el, r) {
      A.bind(el, {
        arch(b) { A.go("projects" + (b.dataset.v === "1" ? "?a=1" : ""), true); },
        open(b) { A.go("tasks?f=all&p=" + b.dataset.id); },
        edit(b) { A.edit.project(A.byId("projects", b.dataset.id)); },
        up(b) { const ps = A.col("projects").filter((p) => !p.archived).sort((a, c) => a.order - c.order); const i = +b.dataset.i; if (i > 0) { const o = ps[i - 1].order; ps[i - 1].order = ps[i].order; ps[i].order = o; if (ps[i - 1].order === ps[i].order) ps.forEach((p, k) => (p.order = k)); A.save(); A.refresh(); } }
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
        '<div class="card"><div class="list">' + (ideas.length ? ideas.map((i) => '<div class="item" data-a="edit" data-id="' + i.id + '"><div class="ic">{{i:lightbulb}}</div><div class="tx"><b>' + esc(i.text) + "</b><small>" + esc(st[i.status || "new"]) + (i.exp ? " · эксперимент: " + esc(i.exp) : "") + "</small></div></div>").join("") : A.empty("Идей пока нет")) + "</div></div>";
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
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.habit()" aria-label="Добавить">{{i:plus}}</button>'; },
    render(el, r) {
      const t = today(), mk = r.params.m || t.slice(0, 7);
      const [y, m] = mk.split("-").map(Number);
      const from = mk + "-01", to = mk + "-" + pad(A.daysInMonth(y, m));
      const hs = A.col("habits").filter((h) => !h.archived);
      const p = A.db().profile;
      let h = '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
      h += '<div class="card"><h3>Трекер привычек</h3>' + A.charts.monthGrid(mk, hs.map((x) => ({ id: x.id, name: (x.icon || "") + " " + x.name })), (row, d) => { const st = A.hs(row.id, d); if (st) { const S = A.HSTATUS[st]; return { c: S[2], s: st === "none" ? "" : S[1], t: S[0] }; } const hb = A.byId("habits", row.id); return !A.habitPlanned(hb, d) ? { c: "transparent" } : null; }) +
        '<div class="mlegend">' + Object.values(A.HSTATUS).map((S) => '<div><i style="background:' + S[2] + '"></i>' + S[1] + " " + S[0] + "</div>").join("") + "</div></div>";
      const ws = A.weekStart(t), wd = A.range(ws, addDays(ws, 6));
      h += '<div class="card"><h3>Неделя: привычка × день</h3><div class="matrix-wrap"><table class="matrix wide"><thead><tr><th></th>' + wd.map((x, i) => '<th class="' + (x === t ? "today" : "") + '">' + DOW[i] + "</th>").join("") + '<th>%</th></tr></thead><tbody>' +
        hs.map((x) => { const st = A.habitStats(x, ws, addDays(ws, 6) > t ? t : addDays(ws, 6)); return '<tr><th class="rowh"><span style="display:inline-block;max-width:118px;overflow:hidden;text-overflow:ellipsis;vertical-align:middle">' + A.icon(x.icon) + " " + esc(x.name) + "</span></th>" + wd.map((d) => { const s2 = A.hs(x.id, d), S = s2 && A.HSTATUS[s2], pl = A.habitPlanned(x, d), nv = x.type === "num" ? A.hv(x.id, d) : null; return '<td data-a="gcell" data-id="' + x.id + '" data-d="' + d + '" style="' + (S ? "background:" + S[2] + ";color:#fff;" : pl ? "" : "opacity:.35;") + '">' + (nv != null ? A.fmtN(nv, 1) : S ? S[1] : "") + "</td>"; }).join("") + '<td style="background:transparent;font-weight:700">' + (st.pct == null ? "" : Math.round(st.pct * 100)) + "</td></tr>"; }).join("") + "</tbody></table></div></div>";
      h += '<div class="card"><h3>План / факт за месяц</h3><div class="list">' + hs.map((x) => { const s = A.habitStats(x, from, to > t ? t : to); return '<div class="item" data-a="detail" data-id="' + x.id + '"><div class="ic">' + A.icon(x.icon || "•") + '</div><div class="tx"><b>' + esc(x.name) + "</b><small>" + (x.type === "num" ? "цель " + (x.target || 1) + " " + esc(x.unit || "") + " · " : "") + (x.freq === "daily" ? "ежедневно" : x.freq === "weekdays" ? (x.days || []).map((k) => DOW[k - 1]).join(", ") : (x.perWeek || 3) + " раз в неделю") + " · серия " + A.habitStreak(x) + " · факт " + A.fmtN(s.done, 1) + "/" + s.plan + "</small>" + A.bar(s.pct || 0, "var(--sage)") + '</div><b class="num">' + (s.pct == null ? "—" : Math.round(s.pct * 100) + "%") + "</b></div>"; }).join("") + "</div></div>";
      if (p.points) {
        const pts = Object.values(A.db().hlog).reduce((s, v) => s + (v === "done" ? 2 : v === "part" ? 1 : 0), 0) - A.sum(A.col("rewards").filter((x) => x.claimed).map((x) => +x.cost || 0));
        h += '<div class="card peach"><h3>{{i:gift}} Баллы и награды<span class="sp"></span><b>' + pts + ' б.</b></h3><p class="small muted">Выполнено = 2 балла, частично = 1. Пропуски баллы не отнимают.</p><div class="list">' + A.col("rewards").map((rw) => '<div class="item"><div class="tx"><b>' + esc(rw.name) + "</b><small>" + rw.cost + " б." + (rw.claimed ? " · получена " + fmtShort(rw.claimed) : "") + "</small></div>" + (rw.claimed ? "" : '<button class="btn sm" data-a="claim" data-id="' + rw.id + '"' + (pts < rw.cost ? " disabled" : "") + ">Получить</button>") + "</div>").join("") + '</div><button class="btn sm" data-a="addReward">+ награда</button></div>';
      }
      const arch = A.col("habits").filter((x) => x.archived);
      if (arch.length) h += '<div class="card"><h3>Архив</h3>' + arch.map((x) => '<div class="item" data-a="edit" data-id="' + x.id + '"><div class="tx"><b>' + esc(x.name) + "</b></div></div>").join("") + "</div>";
      return h;
    },
    bind(el, r) {
      A.bind(el, {
        mnav(b) { const mk = A.route().params.m || today().slice(0, 7); let [y, m] = mk.split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } A.go("habits?m=" + y + "-" + pad(m), true); },
        gcell(b) { if (b.dataset.d > today()) return; const hb = A.byId("habits", b.dataset.id); if (hb.type === "num") return A.edit.habitValue(hb, b.dataset.d); A.cycleHs(hb, b.dataset.d); A.refresh(); },
        detail(b) {
          const hb = A.byId("habits", b.dataset.id), t = today();
          const weeks = []; for (let i = 11; i >= 0; i--) { const a = addDays(A.weekStart(t), -7 * i), e = addDays(a, 6); const st = A.habitStats(hb, a, e > t ? t : e); weeks.push({ x: fmtShort(a), v: st.pct == null ? null : Math.round(st.pct * 100) }); }
          const days30 = A.lastDays(30);
          const body = '<div class="grid3"><div class="stat"><small>Серия</small><b>' + A.habitStreak(hb) + '</b></div><div class="stat"><small>Лучшая серия</small><b>' + A.bestStreak(hb) + '</b></div><div class="stat"><small>30 дней</small><b>' + (A.habitStats(hb, days30[0], t).pct == null ? "—" : Math.round(A.habitStats(hb, days30[0], t).pct * 100) + "%") + "</b></div></div>" +
            '<h3 style="margin-top:12px">Выполнение по неделям, %</h3>' + A.charts.bars(weeks, { color: "#7F9C7A", goal: 100, labelsEvery: 3, fmt: (v) => v + "%" }) +
            (hb.type === "num" ? '<h3 style="margin-top:12px">Значения за 30 дней, ' + esc(hb.unit || "") + "</h3>" + A.charts.bars(days30.map((d) => ({ x: fmtShort(d).slice(0, 2), v: A.hv(hb.id, d) })), { color: "#8E7CC3", goal: hb.target || 1 }) : "") +
            (hb.note ? '<p class="small muted">' + esc(hb.note) + "</p>" : "");
          A.sheet((hb.icon || "") + " " + hb.name, body, { buttons: [{ label: hb.archived ? "Из архива" : "В архив", cls: "ghost", onClick: () => { hb.archived = !hb.archived; A.save(); A.refresh(); } }, { label: "Изменить", cls: "primary", onClick: () => setTimeout(() => A.edit.habit(hb), 250) }] });
        },
        edit(b) { A.edit.habit(A.byId("habits", b.dataset.id)); },
        addReward() { A.formSheet("Награда", [{ k: "name", label: "Награда", req: true, full: true }, { k: "cost", label: "Стоимость в баллах", type: "number" }], {}, (o) => { A.upsert("rewards", o); A.refresh(); }); },
        claim(b) { const rw = A.byId("rewards", b.dataset.id); rw.claimed = today(); A.save(); A.toast("Ты заслужила {{i:heart}}"); A.refresh(); }
      });
    }
  });

  /* ---------- напоминания ---------- */
  A.view("reminders", {
    title: "Напоминания",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.reminder()" aria-label="Добавить">{{i:plus}}</button>'; },
    render() {
      const types = { once: "разово", daily: "ежедневно", weekdays: "по дням", weekly: "еженедельно", monthly: "ежемесячно" };
      const rs = A.col("reminders");
      let h = "";
      if (A.native && !A.native.notificationsAllowed()) h += '<div class="card peach row"><div class="grow small">Уведомления выключены в системе.</div><button class="btn sm primary" data-a="perm">Разрешить</button></div>';
      if (!A.native) h += '<div class="warn" style="margin-bottom:12px">Системные уведомления работают в Android-приложении. Здесь напоминания видны на экране «Сегодня».</div>';
      h += '<div class="card"><div class="list">' + (rs.length ? rs.map((r) => '<div class="item"><div class="ic">{{i:bell}}</div><div class="tx" data-a="edit" data-id="' + r.id + '"><b>' + esc(r.title) + "</b><small>" + esc(r.time + " · " + types[r.type] + (r.type === "once" ? " " + fmtShort(r.date || today()) : "") + (r.type === "weekdays" || r.type === "weekly" ? " " + (r.days || []).map((k) => DOW[k - 1]).join(",") : "") + (r.type === "monthly" ? " " + r.dom + " числа" : "")) + '</small></div><label class="switch"><input type="checkbox" data-c="toggle" data-id="' + r.id + '"' + (r.on ? " checked" : "") + "><span></span></label></div>").join("") : A.empty("Напоминаний нет")) + "</div></div>";
      const t0 = today(), dueToday = rs.filter((r) => r.on && A.reminderOn(r, t0));
      if (dueToday.length) h += '<div class="card"><h3>Сегодня</h3>' + dueToday.map((r) => { const done = A.col("remLog").some((x) => x.rid === r.id && x.date === t0); return '<div class="item' + (done ? " done" : "") + '"><button class="check' + (done ? " on" : "") + '" data-a="rdone" data-id="' + r.id + '">' + (done ? "✓" : "") + '</button><div class="tx"><b>' + esc(r.title) + "</b><small>" + esc(r.time) + "</small></div></div>"; }).join("") + "</div>";
      const hist = A.col("remLog").slice().reverse().slice(0, 30);
      if (hist.length) h += '<div class="card"><h3>История</h3>' + hist.map((x) => { const r = A.byId("reminders", x.rid); return '<div class="row small" style="padding:4px 0"><span class="grow">✓ ' + esc(r ? r.title : "удалено") + '</span><span class="muted">' + fmtShort(x.date) + " " + esc(x.at || "") + "</span></div>"; }).join("") + "</div>";
      h += '<p class="small muted">Также уведомления приходят для привычек со временем, событий с напоминанием, задач с отметкой «напомнить» и регулярных платежей. Все типы отключаются в Настройках → Уведомления.</p>';
      if (A.native) h += '<button class="btn block" data-a="test">Проверить уведомление</button>';
      return h;
    },
    bind(el) {
      A.bind(el, {
        edit(b) { A.edit.reminder(A.byId("reminders", b.dataset.id)); },
        toggle(b) { A.byId("reminders", b.dataset.id).on = b.checked; A.save(); A.syncReminders(); },
        rdone(b) { const t0 = today(), L = A.col("remLog"), i = L.findIndex((x) => x.rid === b.dataset.id && x.date === t0); if (i >= 0) L.splice(i, 1); else L.push({ id: A.uid(), rid: b.dataset.id, date: t0, at: A.nowHM() }); A.save(); A.refresh(); },
        perm() { A.nativeCall("notif", () => A.native.requestNotifications()).then(() => A.refresh()); },
        test() { A.native.testNotification("Путь жизни", "Уведомления работают {{i:heart}}"); }
      });
    }
  });
})();
