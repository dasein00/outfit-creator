/* Цели по горизонтам, программа 90 дней PRIME ERA, ревизии месяца и года, колесо жизни. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtDate, fmtShort, diffDays, sum } = A;
  A.edit = A.edit || {};

  A.goalProgress = (g) => {
    if (g.stages && g.stages.length) return g.stages.filter((s) => s.done).length / g.stages.length;
    const ts = A.col("tasks").filter((t) => t.goalId === g.id);
    if (ts.length && g.progress == null) return ts.filter((t) => t.done).length / ts.length;
    return (+g.progress || 0) / 100;
  };

  A.edit.goal = (g, level) => {
    const isNew = !g;
    g = g || { level: level || "m", title: "", why: "", value: "", start: today(), deadline: "", measure: "", stages: [], habits: [], progress: null, result: "", note: "", status: "active" };
    const stages = (g.stages || []).map((s) => Object.assign({}, s));
    const fields = [
      { k: "level", label: "Горизонт", type: "chips", opts: A.GOAL_LEVELS },
      { k: "title", label: "Название", req: true, full: true },
      { k: "why", label: "Почему важно", type: "textarea", rows: 2 },
      { k: "value", label: "Какую ценность обслуживает", type: "select", opts: [["", "—"]].concat(A.db().profile.values.map((v) => [v, v])), full: true },
      { k: "start", label: "Начало", type: "date" }, { k: "deadline", label: "Дедлайн", type: "date" },
      { k: "measure", label: "Измеримый результат", full: true },
      { k: "habits", label: "Связанные привычки", type: "multi", opts: () => A.col("habits").filter((h) => !h.archived).map((h) => [h.id, (h.icon || "") + " " + h.name]) },
      { k: "progress", label: "Прогресс вручную, % (если нет этапов)", type: "number", min: 0, max: 100 },
      { k: "status", label: "Статус", type: "chips", opts: [["active", "В работе"], ["done", "Достигнута"], ["paused", "Пауза"], ["closed", "Закрыта"]] },
      { k: "result", label: "Фактический результат", type: "textarea", rows: 2 },
      { k: "note", label: "Комментарий", type: "textarea", rows: 2 }
    ];
    const stHtml = () => '<div class="fsec">Этапы</div>' + stages.map((s, i) => '<div class="row" style="margin:4px 0"><button type="button" class="check' + (s.done ? " on" : "") + '" data-st="' + i + '">' + (s.done ? "✓" : "") + '</button><input data-sti="' + i + '" value="' + esc(s.t) + '"><button type="button" class="icon-btn" data-sd="' + i + '">✕</button></div>').join("") + '<div class="inline-add" style="margin-top:6px"><input id="newSt" placeholder="Новый этап"><button type="button" class="btn" id="addSt">+</button></div>';
    A.formSheet(isNew ? "Новая цель" : "Цель", fields, g, (o) => {
      Object.assign(g, o); g.stages = stages.filter((s) => s.t);
      if (g.status === "done" && !g.doneAt) g.doneAt = today();
      A.upsert("goals", g); A.refresh();
    }, {
      pre: isNew ? '<p class="small muted">Перед новой целью: я сама этого хочу? Это соответствует ценностям? Увеличивает свободу? Что я готова убрать?</p>' : "",
      post: '<div id="stBox"></div>' + (isNew ? "" : '<div class="btns"><button type="button" class="btn sm" id="gTask">+ связанная задача</button></div>'),
      onDelete: isNew ? null : () => { A.remove("goals", g.id); A.refresh(); },
      after(w) {
        const box = w.querySelector("#stBox"); const draw = () => (box.innerHTML = stHtml()); draw();
        box.addEventListener("click", (e) => {
          if (e.target.id === "addSt") { const v = box.querySelector("#newSt").value.trim(); if (v) { stages.push({ t: v, done: false }); draw(); } }
          if (e.target.dataset.st != null) { const s = stages[+e.target.dataset.st]; s.done = !s.done; draw(); }
          if (e.target.dataset.sd != null) { stages.splice(+e.target.dataset.sd, 1); draw(); }
        });
        box.addEventListener("input", (e) => { if (e.target.dataset.sti != null) stages[+e.target.dataset.sti].t = e.target.value; });
        const gt = w.querySelector("#gTask"); if (gt) gt.onclick = () => { A.closeSheet(); A.edit.task(null, { goalId: g.id }); };
      }
    });
  };

  const goalCard = (g) => {
    const p = A.goalProgress(g);
    const left = g.deadline ? diffDays(today(), g.deadline) : null;
    const ts = A.col("tasks").filter((t) => t.goalId === g.id);
    return '<div class="card" data-a="goal" data-id="' + g.id + '"><div class="row"><div class="grow"><b>' + esc(g.title) + "</b>" + (g.status !== "active" ? ' <span class="tag">' + { done: "достигнута", paused: "пауза", closed: "закрыта" }[g.status] + "</span>" : "") + '<div class="small muted">' + esc([g.value && "♡ " + g.value, g.measure, g.deadline && (left >= 0 ? "осталось " + left + " дн." : "срок прошёл")].filter(Boolean).join(" · ")) + "</div></div><b>" + Math.round(p * 100) + "%</b></div>" + A.bar(p, g.status === "done" ? "var(--good)" : "var(--accent)") +
      ((g.stages || []).length || ts.length || (g.habits || []).length ? '<div class="small muted" style="margin-top:6px">' + [g.stages.length ? "этапы " + g.stages.filter((s) => s.done).length + "/" + g.stages.length : "", ts.length ? "задачи " + ts.filter((t) => t.done).length + "/" + ts.length : "", (g.habits || []).length ? "привычки: " + g.habits.map((id) => (A.byId("habits", id) || {}).name).filter(Boolean).join(", ") : ""].filter(Boolean).join(" · ") + "</div>" : "") + "</div>";
  };

  A.view("goals", {
    root: true, title: "Цели",
    actions(el, r) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.goal(null, \'' + (r.params.l || "m") + '\')" aria-label="Добавить">＋</button>'; },
    render(el, r) {
      const sec = r.args[0] || "list";
      let h = A.seg([["list", "Цели"], ["era", "90 дней"], ["review", "Ревизия"], ["values", "Ценности"]], sec, "sec");
      if (sec === "list") {
        const l = r.params.l || "all";
        h += '<div class="chips" style="margin-bottom:12px"><button class="chip' + (l === "all" ? " on" : "") + '" data-a="lvl" data-v="all">Все</button>' + A.GOAL_LEVELS.map(([k, n]) => '<button class="chip' + (l === k ? " on" : "") + '" data-a="lvl" data-v="' + k + '">' + n + "</button>").join("") + "</div>";
        const gs = A.col("goals").filter((g) => l === "all" || g.level === l);
        if (!gs.length) h += A.empty("Целей на этом горизонте пока нет. Один год — фундамент, три года — система, десять лет — жизнь по выбору.", '<button class="btn primary" data-a="new">+ цель</button>');
        A.GOAL_LEVELS.forEach(([k, n]) => { const list = gs.filter((g) => g.level === k); if (list.length) h += '<div class="sec-t">' + n + "</div>" + list.sort((a, b) => (a.status === "active" ? -1 : 1)).map(goalCard).join(""); });
      } else if (sec === "era") {
        const era = A.db().profile.era || (A.db().profile.era = { start: today() });
        const day = diffDays(era.start, today()) + 1;
        const phase = day <= 30 ? 1 : day <= 60 ? 2 : day <= 90 ? 3 : 4;
        const ph = [null, ["Наблюдение", "Ежедневно записывать «план → факт → препятствие». Понять причины сбоев."], ["Настройка", "Оставить только устойчивые практики: движение, личный проект, финансовый учёт, восстановление."], ["Закрепление", "Определить, какие действия стали естественными. Довести до конца материальный результат."], ["Завершено", "90 дней пройдены. Что дальше: продолжить, завершить или закрыть главный проект?"]][phase];
        const days = A.range(era.start, today() < addDays(era.start, 89) ? today() : addDays(era.start, 89));
        const filled = days.filter((d) => { const x = A.dayGet(d); return x && (x.fact || x.plan); }).length;
        const mains = days.filter((d) => (A.dayGet(d) || {}).mainDone).length;
        h += '<div class="card hero"><div class="small muted">PRIME ERA · 90 дней</div><div class="big" style="margin:6px 0">' + (phase < 4 ? "День " + day : "✓") + '</div><div class="date" style="font-size:18px">Месяц ' + Math.min(phase, 3) + ": " + ph[0] + '</div><p class="small">' + ph[1] + "</p>" + A.bar(Math.min(1, day / 90)) + "</div>";
        h += '<div class="grid3"><div class="stat"><small>Дней со сверкой</small><b>' + filled + "/" + days.length + '</b></div><div class="stat"><small>Главное выполнено</small><b>' + mains + '</b></div><div class="stat"><small>Возвращений</small><b>' + days.filter((d, i) => i > 0 && !(A.dayGet(days[i - 1]) || {}).mainDone && (A.dayGet(d) || {}).mainDone).length + "</b></div></div>";
        h += '<div class="card" style="margin-top:12px"><h3>Главный проект периода</h3><input data-c="era" data-k="project" value="' + esc(era.project || "") + '" placeholder="Один главный проект на 90 дней"><div class="form" style="margin-top:8px"><div class="fld full"><label>Материальный результат к концу</label><input data-c="era" data-k="result" value="' + esc(era.result || "") + '"></div><div class="fld"><label>Дата старта</label><input type="date" data-c="era" data-k="start" value="' + esc(era.start) + '"></div></div><p class="small muted">Ежедневно — одно главное действие. Еженедельно — один завершённый микро-результат. Ежемесячно — одна большая ревизия.</p></div>';
        h += '<div class="card"><h3>Контрольные точки</h3><table class="tbl"><tr><th>Период</th><th>Фокус</th><th>Результат</th></tr><tr' + (phase === 1 ? ' style="background:var(--accent2)"' : "") + "><td>1-й месяц</td><td>Наблюдение</td><td>Понять причины сбоев</td></tr><tr" + (phase === 2 ? ' style="background:var(--accent2)"' : "") + "><td>2-й месяц</td><td>Настройка</td><td>Стабильные минимальные действия</td></tr><tr" + (phase === 3 ? ' style="background:var(--accent2)"' : "") + "><td>3-й месяц</td><td>Закрепление</td><td>Материальный завершённый результат</td></tr></table></div>";
        const obst = {}; days.forEach((d) => { const o = ((A.dayGet(d) || {}).obstacle || "").trim().toLowerCase(); if (o) obst[o] = (obst[o] || 0) + 1; });
        const top = Object.entries(obst).sort((a, b) => b[1] - a[1]).slice(0, 6);
        h += '<div class="card"><h3>Частые препятствия</h3>' + (top.length ? top.map(([o, n]) => '<div class="row"><span class="grow">' + esc(o) + "</span><b>" + n + "</b></div>").join("") : '<p class="small muted">Заполняй вечернюю сверку на экране «Сегодня» — здесь появятся закономерности.</p>') + "</div>";
        h += '<div class="card tint"><p class="book"><i>«Тебе важно получить новый опыт: я могу вернуться после сбоя. Это надёжнее, чем пытаться никогда не ошибаться.»</i></p></div>';
      } else if (sec === "review") {
        const mk = r.params.m || today().slice(0, 7);
        const R = (A.db().reviews[mk] = A.db().reviews[mk] || { ratings: {}, wheel: {} });
        const items = [["total", "Как оцениваю месяц в целом?"], ["health", "Здоровье и энергия"], ["work", "Работа и доход"], ["rel", "Отношения"], ["growth", "Личное развитие"], ["money", "Финансы"], ["joy", "Удовольствие от жизни"]];
        h += '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">Рефлексия · ' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
        h += '<div class="card"><h3>Рефлексия в конце месяца</h3>' + items.map(([k, n]) => '<div class="row between" style="margin:4px 0"><span class="small">' + n + '</span><div class="stars" data-rk="' + k + '">' + [1, 2, 3, 4, 5].map((i) => '<button data-a="rate" data-k="' + k + '" data-v="' + i + '" class="' + ((R.ratings[k] || 0) >= i ? "on" : "") + '">★</button>').join("") + "</div></div>").join("") +
          '<div class="form" style="margin-top:10px">' + [["best", "Что получилось лучше всего?"], ["improve", "Что нужно улучшить?"], ["change", "Что я хочу изменить в следующем месяце?"], ["values5", "Пять вещей, которые сейчас действительно важны"], ["stopdoing", "Что больше не хочется делать"]].map(([k, l]) => '<div class="fld full"><label>' + l + '</label><textarea rows="2" data-c="rev" data-k="' + k + '">' + esc(R[k] || "") + "</textarea></div>").join("") + "</div></div>";
        const wheel = ["Здоровье", "Работа", "Деньги", "Отношения", "Семья", "Развитие", "Отдых", "Дом", "Свобода", "Интересы"];
        h += '<div class="card"><h3>Колесо жизни</h3>' + A.charts.radar(wheel.map((w) => ({ name: w, v: R.wheel[w] || 0 }))) + '<div class="form" style="margin-top:8px">' + wheel.map((w) => '<div class="fld"><label>' + w + ' <b id="wv_' + w + '">' + (R.wheel[w] || 0) + '</b></label><input type="range" min="0" max="10" value="' + (R.wheel[w] || 0) + '" data-c="wheel" data-k="' + w + '"></div>').join("") + "</div></div>";
        const y = mk.slice(0, 4), Y = (A.db().reviews[y] = A.db().reviews[y] || {});
        h += '<div class="card sage"><h3>Раз в год · ' + y + '</h3><div class="form">' + [["y1", "Стало ли больше свободы?"], ["y2", "Стало ли больше устойчивости?"], ["y3", "Стало ли больше близости?"], ["y4", "Стало ли больше возможностей выбирать?"]].map(([k, l]) => '<div class="fld full"><label>' + l + '</label><div class="chips">' + ["да", "так же", "нет"].map((v) => '<button class="chip' + (Y[k] === v ? " on" : "") + '" data-a="yq" data-k="' + k + '" data-y="' + y + '" data-v="' + v + '">' + v + "</button>").join("") + "</div></div>").join("") + '</div><p class="small muted">Если два года подряд показатель ухудшается — менять не цель, а стратегию.</p></div>';
        h += '<div class="card"><h3>Раз в квартал — оценка работы</h3><p class="small muted">Шкала 1–10. Выбери один показатель с наибольшим потенциалом изменения.</p>' + ["Интерес", "Доход", "Команда", "Стабильность", "Развитие", "Свобода", "Соответствие принципам"].map((w) => { const q = y + "-Q" + (Math.floor((+mk.slice(5) - 1) / 3) + 1); const Q = (A.db().reviews[q] = A.db().reviews[q] || {}); return '<div class="fld"><label>' + w + " · " + q + ' <b id="qv_' + w + '">' + (Q[w] || 0) + '</b></label><input type="range" min="0" max="10" value="' + (Q[w] || 0) + '" data-c="qwork" data-q="' + q + '" data-k="' + w + '"></div>'; }).join("") + "</div>";
      } else if (sec === "values") {
        const p = A.db().profile;
        h += '<div class="card hero"><div class="date">Мои ценности</div><div class="phrase">' + esc(p.phrase) + "</div></div>";
        h += '<div class="card"><div class="list">' + p.values.map((v) => { const gs = A.col("goals").filter((g) => g.value === v); return '<div class="item"><div class="ic">♡</div><div class="tx"><b>' + esc(v) + "</b><small>" + (gs.length ? "целей: " + gs.length + " · " + gs.map((g) => g.title).slice(0, 2).map(esc).join(", ") : "нет связанных целей") + "</small></div></div>"; }).join("") + '</div><a class="link" href="#/settings/profile">Изменить ценности →</a></div>';
        const orphan = A.col("goals").filter((g) => !g.value && g.status === "active");
        if (orphan.length) h += '<div class="warn">Целей без связанной ценности: ' + orphan.length + ". Если цель не связана ни с одной ценностью, проверь, не появилась ли она из чужих ожиданий.</div>";
        h += '<div class="card peach" style="margin-top:12px"><h3>Напоминание себе</h3><p>Я имею право: ' + p.rights.map(esc).join(", ") + '.</p><p class="book"><i>Я выбираю: себя, свои цели, свою жизнь ♡</i></p></div>';
        h += '<div class="card tint"><p class="book"><i>Главная система: ценности → выбор → действие → результат → анализ → коррекция → новый выбор.</i></p><a class="link" href="#/book">Книга PRIME ERA →</a></div>';
      }
      return h;
    },
    bind(el, r) {
      const mk = () => A.route().params.m || today().slice(0, 7);
      A.bind(el, {
        sec(b) { A.go("goals/" + b.dataset.v, true); },
        lvl(b) { A.go("goals/list?l=" + b.dataset.v, true); },
        new() { A.edit.goal(null, A.route().params.l && A.route().params.l !== "all" ? A.route().params.l : "m"); },
        goal(b) { A.edit.goal(A.byId("goals", b.dataset.id)); },
        era(b) { const e = A.db().profile.era; e[b.dataset.k] = b.value; A.save(); if (b.dataset.k === "start") A.refresh(); },
        mnav(b) { let [y, m] = mk().split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } A.go("goals/review?m=" + y + "-" + A.pad(m), true); },
        rate(b) { const R = A.db().reviews[mk()]; R.ratings[b.dataset.k] = R.ratings[b.dataset.k] === +b.dataset.v ? 0 : +b.dataset.v; A.save(); A.refresh(); },
        rev(b) { A.db().reviews[mk()][b.dataset.k] = b.value; A.save(); },
        wheel(b) { A.db().reviews[mk()].wheel[b.dataset.k] = +b.value; A.save(); A.refresh(); },
        qwork(b) { A.db().reviews[b.dataset.q][b.dataset.k] = +b.value; A.save(); const l = document.getElementById("qv_" + b.dataset.k); if (l) l.textContent = b.value; },
        yq(b) { A.db().reviews[b.dataset.y][b.dataset.k] = b.dataset.v; A.save(); A.refresh(); }
      });
    }
  });
})();
