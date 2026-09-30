/* Финансы: счета, доходы и расходы, бюджеты, цели накоплений, регулярные платежи, финплан месяца. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN, fmtShort, fmtDate, money, sum, pad } = A;
  A.edit = A.edit || {};
  const PAL = ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6", "#B6A6D9", "#9CC5A1", "#F3C29F", "#D8C08A", "#EBB0BC", "#A9CBE5", "#C9C3D3"];

  A.edit.tx = (t) => {
    const isNew = !t || !t.id;
    t = Object.assign({ kind: "out", date: today(), amt: "", cat: "Еда", sub: "", acc: (A.col("accounts")[1] || A.col("accounts")[0] || {}).id || "", note: "", src: "" }, t || {});
    const fields = () => [
      { k: "kind", label: "Тип", type: "chips", opts: [["out", "Расход"], ["in", "Доход"], ["move", "Перевод"]] },
      { k: "amt", label: "Сумма, ₽", type: "number", req: true }, { k: "date", label: "Дата", type: "date" },
      { k: "cat", label: "Категория", type: "select", opts: t.kind === "in" ? A.incCats() : A.expCats() },
      { k: "sub", label: t.kind === "in" ? "Источник" : "Подкатегория" },
      { k: "acc", label: t.kind === "move" ? "Со счёта" : "Счёт", type: "select", opts: A.col("accounts").map((a) => [a.id, a.name]) },
      { k: "to", label: "На счёт (для перевода)", type: "select", opts: [["", "—"]].concat(A.col("accounts").map((a) => [a.id, a.name])) },
      { k: "note", label: "Комментарий", type: "textarea" }
    ];
    const open = () => {
      const w = A.formSheet(isNew ? (t.kind === "in" ? "Доход" : t.kind === "move" ? "Перевод" : "Расход") : "Операция", fields(), t, (o) => {
        Object.assign(t, o);
        if (t.kind === "move" && (!t.to || t.to === t.acc)) { A.toast("Выберите другой счёт"); return false; }
        A.upsert("tx", t); A.budgetWarn(t); A.refresh();
      }, isNew ? {} : { onDelete: () => { A.remove("tx", t.id); A.refresh(); } });
      // смена типа перестраивает список категорий
      w.querySelector('.chips[data-field="kind"]').addEventListener("click", (e) => {
        const c = e.target.closest(".chip"); if (!c || c.dataset.v === t.kind) return;
        Object.assign(t, A.readForm(w, fields())); t.kind = c.dataset.v; t.cat = t.kind === "in" ? A.incCats()[0] : A.expCats()[0];
        A.closeSheet(); setTimeout(open, 230);
      });
      const amt = w.querySelector('[name="amt"]'); if (amt && isNew) setTimeout(() => amt.focus(), 250);
    };
    open();
  };
  A.edit.account = (a) => A.formSheet(a ? "Счёт" : "Новый счёт", [
    { k: "emoji", label: "Иконка", type: "icon" }, { k: "name", label: "Название", req: true },
    { k: "type", label: "Тип", type: "select", opts: [["cash", "Кошелёк / наличные"], ["card", "Банковская карта"], ["savings", "Сбережения"], ["crypto", "Крипто"], ["other", "Другое"]] },
    { k: "start", label: "Начальный баланс, ₽", type: "number" },
    { k: "color", label: "Цвет", type: "chips", opts: A.PAL.map((c) => [c, "●"]) },
    { k: "card4", label: "Последние 4 цифры карты (для импорта SMS)", ph: "1234" },
    { k: "archived", label: "Архив", type: "check", text: "Счёт в архиве" }
  ], a || { type: "card", start: 0, emoji: "{{i:credit-card}}", color: A.PAL[A.col("accounts").length % A.PAL.length] }, (o) => { A.upsert("accounts", Object.assign(a || {}, o)); A.refresh(); },
    Object.assign({ after: (w) => w.querySelectorAll('.chips[data-field="color"] .chip').forEach((c) => (c.style.color = c.dataset.v)) }, a ? { onDelete: () => { if (A.col("tx").some((t) => t.acc === a.id)) { A.toast("По счёту есть операции — лучше отправьте его в архив"); return; } A.remove("accounts", a.id); A.refresh(); } } : {}));
  A.accIcon = (a) => a.emoji || ({ cash: "{{i:banknote}}", card: "{{i:credit-card}}", savings: "{{i:landmark}}", crypto: "{{i:coins}}" }[a.type] || "{{i:wallet}}");
  // Предупреждение при приближении к лимиту категории или общего бюджета.
  A.budgetWarn = (t) => {
    if (t.kind !== "out") return;
    const B = A.db().budgets, mk = t.date.slice(0, 7), out = A.monthTx(mk).filter((x) => x.kind === "out");
    const msgs = [];
    const chk = (name, spent, lim) => { if (!lim) return; const p = spent / lim; if (p >= 1) msgs.push("{{i:triangle-alert}} «" + name + "»: лимит превышен (" + Math.round(p * 100) + "%)"); else if (p >= 0.85) msgs.push("«" + name + "»: израсходовано " + Math.round(p * 100) + "% лимита"); };
    chk(t.cat, sum(out.filter((x) => x.cat === t.cat).map((x) => x.amt)), B[t.cat]);
    chk("Общий бюджет", sum(out.map((x) => x.amt)), B.__total);
    if (msgs.length) setTimeout(() => A.toast(msgs.join(" · ")), 300);
  };
  A.edit.saving = (g) => A.formSheet(g ? "Цель накоплений" : "Новая цель", [{ k: "name", label: "Название", req: true, full: true }, { k: "target", label: "Целевая сумма", type: "number", req: true }, { k: "cur", label: "Накоплено сейчас", type: "number" }, { k: "deadline", label: "Срок", type: "date" }, { k: "monthly", label: "Регулярный взнос в месяц", type: "number" }, { k: "why", label: "Какую ценность обслуживает", full: true }], g || {}, (o) => { A.upsert("savings", Object.assign(g || {}, o)); A.refresh(); }, g ? { onDelete: () => { A.remove("savings", g.id); A.refresh(); } } : {});
  A.edit.recurring = (r) => A.formSheet(r && r.id ? "Регулярная операция" : "Новая регулярная операция", [
    { k: "name", label: "Название", req: true, full: true, ph: "Подписка, аренда, связь…" }, { k: "amt", label: "Сумма", type: "number", req: true },
    { k: "kind", label: "Тип", type: "select", opts: [["out", "Расход"], ["in", "Доход"]] }, { k: "cat", label: "Категория", type: "select", opts: () => A.expCats().concat(A.incCats()) },
    { k: "acc", label: "Счёт", type: "select", opts: A.col("accounts").map((a) => [a.id, a.name]) },
    { k: "period", label: "Период", type: "select", opts: [["month", "Ежемесячно"], ["week", "Еженедельно"], ["year", "Ежегодно"]] },
    { k: "next", label: "Следующая дата", type: "date", req: true },
    { k: "auto", label: "Запись", type: "check", text: "Создавать операцию автоматически в срок" },
    { k: "notifyDays", label: "Уведомить заранее", type: "select", opts: [[0, "в день платежа"], [1, "за 1 день"], [2, "за 2 дня"], [3, "за 3 дня"], [7, "за неделю"]] },
    { k: "active", label: "Статус", type: "check", text: "Активен" }
  ], Object.assign({ kind: "out", cat: "Подписки", period: "month", next: today(), active: true, auto: false, notifyDays: 1 }, r || {}), (o) => { o.notifyDays = +o.notifyDays || 0; const x = Object.assign(r || {}, o); x.dom = A.parse(x.next).getDate(); A.upsert("recurring", x); A.processRecurring(); A.syncReminders(); A.refresh(); }, r && r.id ? { onDelete: () => { A.remove("recurring", r.id); A.syncReminders(); A.refresh(); } } : {});

  const monthNav = (mk) => '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';

  A.view("money", {
    title: "Финансы",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.tx({kind:\'out\'})" aria-label="Расход">−</button><button class="icon-btn" onclick="App.edit.tx({kind:\'in\'})" aria-label="Доход">{{i:plus}}</button>'; },
    render(el, r) {
      const tab = r.args[0] || "overview", mk = r.params.m || today().slice(0, 7);
      const tx = A.monthTx(mk), inc = sum(tx.filter((t) => t.kind === "in").map((t) => t.amt)), out = sum(tx.filter((t) => t.kind === "out").map((t) => t.amt));
      const B = A.db().budgets;
      let h = A.seg([["overview", "Обзор"], ["ops", "Операции"], ["budget", "Бюджет"], ["reports", "Отчёты"], ["save", "Накопления"], ["rec", "Регулярные"], ["cats", "Категории"], ["import", "Импорт"], ["plan", "План месяца"]], tab, "tab");
      if (!["save", "rec", "cats", "import", "reports"].includes(tab)) h += monthNav(mk);
      const byCat = {}; tx.filter((t) => t.kind === "out").forEach((t) => (byCat[t.cat] = (byCat[t.cat] || 0) + t.amt));
      if (tab === "overview") {
        h += '<div class="grid3"><div class="stat"><small>Доходы</small><b style="color:var(--good)">' + money(inc) + '</b></div><div class="stat"><small>Расходы</small><b style="color:var(--bad)">' + money(out) + '</b></div><div class="stat"><small>Разница</small><b>' + money(inc - out) + "</b></div></div>";
        h += '<div class="card" style="margin-top:12px"><h3>Счета<span class="sp"></span><button class="link" data-a="newAcc">+ счёт</button></h3>' + A.col("accounts").filter((a) => !a.archived).map((a) => '<div class="item" data-a="acc" data-id="' + a.id + '" style="border-left:4px solid ' + (a.color || "var(--line)") + ';padding-left:8px"><div class="ic">' + A.icon(A.accIcon(a)) + '</div><div class="tx"><b>' + esc(a.name) + "</b><small>" + ({ cash: "кошелёк", card: "карта" + (a.card4 ? " ··" + esc(a.card4) : ""), savings: "сбережения", crypto: "крипто", other: "" }[a.type] || "") + '</small></div><b class="num">' + money(A.accBalance(a)) + "</b></div>").join("") + '<div class="row between" style="margin-top:6px"><span class="muted">Итого</span><b>' + money(sum(A.col("accounts").filter((a) => !a.archived).map(A.accBalance))) + "</b></div>" + (A.col("accounts").some((a) => a.archived) ? '<details class="small" style="margin-top:6px"><summary class="muted">Архив счетов</summary>' + A.col("accounts").filter((a) => a.archived).map((a) => '<div class="item" data-a="acc" data-id="' + a.id + '"><div class="tx"><b>' + esc(a.name) + '</b></div><b class="num">' + money(A.accBalance(a)) + "</b></div>").join("") + "</details>" : "") + "</div>";
        if (Object.keys(byCat).length) h += '<div class="card"><h3>Расходы по категориям</h3>' + A.charts.donut(Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([n, v]) => ({ name: n, v, color: A.catColor(n) })), fmtN(out), "₽ за месяц") + "</div>";
        const budgets = Object.keys(B).filter((c) => B[c] > 0 && c !== "__total");
        if (B.__total) { const p2 = out / B.__total; h += '<div class="card"><h3>Общий бюджет</h3><div class="row small"><span class="grow">Потрачено</span><b>' + money(out) + " / " + money(B.__total) + "</b></div>" + A.bar(p2, p2 > 1 ? "var(--bad)" : p2 > 0.85 ? "var(--warn)" : "var(--sage)") + (p2 > 0.85 ? '<p class="small" style="color:var(--bad);margin-top:6px">' + (p2 > 1 ? "Лимит превышен" : "Близко к лимиту") + "</p>" : "") + "</div>"; }
        if (budgets.length) h += '<div class="card"><h3>Бюджеты</h3>' + budgets.map((c) => { const f = byCat[c] || 0, p = f / B[c]; return '<div style="margin:8px 0"><div class="row small"><span class="grow">' + esc(c) + "</span><b>" + money(f) + " / " + money(B[c]) + "</b></div>" + A.bar(p, p > 1 ? "var(--bad)" : p > 0.85 ? "var(--warn)" : "var(--sage)") + "</div>"; }).join("") + "</div>";
        const months = []; for (let i = 5; i >= 0; i--) { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - i); const k = d.getFullYear() + "-" + pad(d.getMonth() + 1); const t2 = A.monthTx(k); months.push({ x: A.MON_SHORT[d.getMonth()], i: sum(t2.filter((t) => t.kind === "in").map((t) => t.amt)), o: sum(t2.filter((t) => t.kind === "out").map((t) => t.amt)) }); }
        h += '<div class="card"><h3>Расходы по месяцам</h3>' + A.charts.bars(months.map((m) => ({ x: m.x, v: m.o })), { color: "#E3899A", fmt: money }) + "</div>";
        h += '<div class="card tint"><p class="small">Деньги — средство свободы. Раз в месяц фиксируй доход, обязательные расходы, накопления и свободные деньги. Резерв — это «фонд свободы».</p></div>';
      } else if (tab === "ops") {
        const qq = (r.params.q || "").toLowerCase();
        const src = qq ? A.col("tx").filter((t) => [t.cat, t.sub, t.note].join(" ").toLowerCase().includes(qq)) : tx;
        h += '<input id="txq" data-c="txq" placeholder="{{i:search}} Поиск по категории или заметке (по всей истории)" value="' + esc(r.params.q || "") + '" style="margin-bottom:10px">' + (qq ? '<div class="small muted" style="margin-bottom:8px">Найдено: ' + src.length + " · сумма расходов " + money(sum(src.filter((t) => t.kind === "out").map((t) => t.amt))) + "</div>" : "");
        const list = src.slice().sort((a, b) => (b.date > a.date ? 1 : b.date < a.date ? -1 : 0));
        let last = "";
        h += '<div class="card"><div class="list">' + (list.length ? list.map((t) => { let s = ""; if (t.date !== last) { last = t.date; s += '<div class="small muted" style="margin-top:8px">' + fmtDate(t.date, { dow: true }) + "</div>"; } const acc = A.byId("accounts", t.acc); return s + '<div class="item" data-a="tx" data-id="' + t.id + '"><span class="dot" style="background:' + A.catColor(t.cat) + '"></span><div class="tx"><b>' + esc(t.kind === "move" ? "Перевод" : t.cat) + (t.src === "sms" ? " {{i:mail}}" : t.src === "import" ? " {{i:download}}" : "") + "</b><small>" + esc([t.sub, t.note, acc && acc.name].filter(Boolean).join(" · ")) + '</small></div><b class="num" style="color:' + (t.kind === "in" ? "var(--good)" : t.kind === "move" ? "var(--ink2)" : "var(--ink)") + '">' + (t.kind === "in" ? "+" : t.kind === "out" ? "−" : "") + money(t.amt) + "</b></div>"; }).join("") : A.empty("Операций за месяц нет")) + "</div></div>";
        h += '<button class="fab" data-a="newTx" aria-label="Добавить операцию">+</button>';
      } else if (tab === "budget") {
        h += '<div class="card"><h3>Месячные лимиты</h3><p class="small muted">План / факт / остаток / процент использования.</p><table class="tbl"><tr><th>Категория</th><th class="r">Лимит</th><th class="r">Факт</th><th class="r">Остаток</th></tr>' +
          A.expCats().map((c) => { const f = byCat[c] || 0, l = B[c] || 0; return "<tr><td>" + esc(c) + '</td><td class="r"><input data-c="bud" data-k="' + esc(c) + '" type="number" inputmode="numeric" value="' + (l || "") + '" style="width:90px;min-height:34px;padding:4px 8px;text-align:right"></td><td class="r">' + fmtN(f) + '</td><td class="r" style="color:' + (l && f > l ? "var(--bad)" : "inherit") + '">' + (l ? fmtN(l - f) + "<br><small>" + Math.round((f / l) * 100) + "%</small>" : "—") + "</td></tr>"; }).join("") +
          '<tr><td><b>Общий бюджет</b><br><small class="muted">на все расходы</small></td><td class="r"><input data-c="bud" data-k="__total" type="number" inputmode="numeric" value="' + (B.__total || "") + '" style="width:90px;min-height:34px;padding:4px 8px;text-align:right"></td><td class="r">' + fmtN(out) + '</td><td class="r">' + (B.__total ? fmtN(B.__total - out) + "<br><small>" + Math.round((out / B.__total) * 100) + "%</small>" : "—") + "</td></tr>" + '<tr><td><b>Сумма лимитов</b></td><td class="r"><b>' + fmtN(sum(Object.entries(B).filter(([k]) => k !== "__total").map(([, v]) => v))) + '</b></td><td class="r"><b>' + fmtN(out) + '</b></td><td class="r"></td></tr></table><p class="small muted">При 85% лимита и при превышении появится предупреждение.</p></div>';
      } else if (tab === "save") {
        h += '<div class="card"><h3>Цели накоплений<span class="sp"></span><button class="btn sm primary" data-a="newSave">+</button></h3>' + A.col("savings").map((g) => { const p = g.target ? g.cur / g.target : 0; const left = Math.max(0, g.target - g.cur); const months = g.monthly ? Math.ceil(left / g.monthly) : null; return '<div style="padding:10px 0;border-bottom:1px solid var(--line)"><div class="row" data-a="save" data-id="' + g.id + '"><div class="grow"><b>' + esc(g.name) + '</b><div class="small muted">' + money(g.cur) + " из " + money(g.target) + (g.deadline ? " · срок " + fmtDate(g.deadline) : "") + (months ? " · ≈ " + months + " мес. при взносе " + money(g.monthly) : "") + "</div></div><b>" + Math.round(p * 100) + "%</b></div>" + A.bar(p, "var(--gold)") + '<div class="btns"><button class="btn sm" data-a="dep" data-id="' + g.id + '">+ пополнить</button></div></div>'; }).join("") + "</div>";
      } else if (tab === "rec") {
        const rs = A.col("recurring");
        h += '<div class="card"><h3>Подписки и регулярные платежи<span class="sp"></span><button class="btn sm primary" data-a="newRec">+</button></h3>' + (rs.length ? rs.map((r2) => '<div class="item" data-a="rec" data-id="' + r2.id + '"><div class="ic">↻</div><div class="tx"><b>' + esc(r2.name) + "</b><small>" + ({ month: "ежемесячно", week: "еженедельно", year: "ежегодно" }[r2.period] || "") + " · следующий " + (r2.next ? fmtDate(r2.next) : "—") + (r2.auto ? " · авто" : " · напоминание") + (r2.active ? "" : " · выключен") + '</small></div><b class="num">' + money(r2.amt) + "</b></div>").join("") : A.empty("Нет регулярных платежей")) +
          '<p class="small muted">Если включено «автоматически», в день платежа создаётся запись операции. Иначе приходит напоминание.</p></div>';
        const monthly = sum(rs.filter((x) => x.active && x.kind !== "in").map((x) => (x.period === "week" ? x.amt * 4.33 : x.period === "year" ? x.amt / 12 : x.amt)));
        h += '<div class="stat"><small>Регулярные расходы в месяц (оценка)</small><b>' + money(monthly) + "</b></div>";
      } else if (tab === "reports") {
        const months = []; for (let i = 11; i >= 0; i--) { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - i); const k = d.getFullYear() + "-" + pad(d.getMonth() + 1); const t2 = A.monthTx(k); const inc2 = sum(t2.filter((t) => t.kind === "in").map((t) => t.amt)), out2 = sum(t2.filter((t) => t.kind === "out").map((t) => t.amt)); months.push({ k, x: A.MON_SHORT[d.getMonth()], i: inc2, o: out2 }); }
        const per = r.params.per || "q", ms = per === "q" ? months.slice(-3) : months;
        h += A.seg([["q", "Квартал"], ["y", "Год"]], per, "rper");
        h += '<div class="card"><h3>Доходы и расходы</h3>' + A.charts.line([{ name: "Доходы", color: "#5DAE7B", data: ms.map((m) => ({ x: m.x, v: m.i })) }, { name: "Расходы", color: "#E3899A", data: ms.map((m) => ({ x: m.x, v: m.o })) }], { min: 0 }) + "</div>";
        h += '<div class="card"><h3>Сводка по месяцам</h3><table class="tbl"><tr><th>Месяц</th><th class="r">Доход</th><th class="r">Расход</th><th class="r">Остаток</th></tr>' + ms.slice().reverse().map((m) => "<tr><td>" + esc(A.monthTitle(m.k)) + '</td><td class="r">' + fmtN(m.i) + '</td><td class="r">' + fmtN(m.o) + '</td><td class="r" style="color:' + (m.i - m.o >= 0 ? "var(--good)" : "var(--bad)") + '">' + fmtN(m.i - m.o) + "</td></tr>").join("") + '<tr><td><b>Итого</b></td><td class="r"><b>' + fmtN(sum(ms.map((m) => m.i))) + '</b></td><td class="r"><b>' + fmtN(sum(ms.map((m) => m.o))) + '</b></td><td class="r"><b>' + fmtN(sum(ms.map((m) => m.i - m.o))) + "</b></td></tr></table></div>";
        const pc = {}; A.col("tx").filter((t) => t.kind === "out" && t.date.slice(0, 7) >= ms[0].k).forEach((t) => (pc[t.cat] = (pc[t.cat] || 0) + t.amt));
        if (Object.keys(pc).length) h += '<div class="card"><h3>Разбор по категориям за период</h3>' + A.charts.donut(Object.entries(pc).sort((a, b) => b[1] - a[1]).map(([n, v]) => ({ name: n, v, color: A.catColor(n) })), fmtN(sum(Object.values(pc))), "₽") + "</div>";
        const m1 = r.params.a || months[10].k, m2 = r.params.b || months[11].k;
        const catOf = (k) => { const o = {}; A.monthTx(k).filter((t) => t.kind === "out").forEach((t) => (o[t.cat] = (o[t.cat] || 0) + t.amt)); return o; };
        const c1 = catOf(m1), c2 = catOf(m2), cs = [...new Set(Object.keys(c1).concat(Object.keys(c2)))];
        h += '<div class="card"><h3>Сравнение месяцев</h3><div class="row"><select data-c="cmpA" class="grow">' + months.map((m) => '<option value="' + m.k + '"' + (m.k === m1 ? " selected" : "") + ">" + A.monthTitle(m.k) + "</option>").join("") + '</select><span>vs</span><select data-c="cmpB" class="grow">' + months.map((m) => '<option value="' + m.k + '"' + (m.k === m2 ? " selected" : "") + ">" + A.monthTitle(m.k) + "</option>").join("") + '</select></div><table class="tbl" style="margin-top:8px"><tr><th>Категория</th><th class="r">' + A.MON_SHORT[+m1.slice(5) - 1] + '</th><th class="r">' + A.MON_SHORT[+m2.slice(5) - 1] + '</th><th class="r">Δ</th></tr>' + cs.map((c) => { const a = c1[c] || 0, b2 = c2[c] || 0; return "<tr><td>" + esc(c) + '</td><td class="r">' + fmtN(a) + '</td><td class="r">' + fmtN(b2) + '</td><td class="r" style="color:' + (b2 > a ? "var(--bad)" : "var(--good)") + '">' + (b2 - a > 0 ? "+" : "") + fmtN(b2 - a) + "</td></tr>"; }).join("") + '<tr><td><b>Всего</b></td><td class="r"><b>' + fmtN(sum(Object.values(c1))) + '</b></td><td class="r"><b>' + fmtN(sum(Object.values(c2))) + '</b></td><td class="r"><b>' + fmtN(sum(Object.values(c2)) - sum(Object.values(c1))) + "</b></td></tr></table></div>";
      } else if (tab === "cats") {
        const C = A.db().cats;
        [["out", "Категории расходов"], ["in", "Категории доходов"]].forEach(([k, n]) => {
          h += '<div class="card"><h3>' + n + '<span class="sp"></span><button class="btn sm" data-a="catAdd" data-k="' + k + '">+</button></h3><div class="list">' + C[k].map((c, i) => '<div class="item"><span class="dot" style="background:' + c.color + ';width:16px;height:16px"></span><div class="tx tap" data-a="catEdit" data-k="' + k + '" data-i="' + i + '"><b>' + esc(c.name) + "</b><small>операций: " + A.col("tx").filter((t) => t.cat === c.name).length + '</small></div><button class="icon-btn" data-a="catUp" data-k="' + k + '" data-i="' + i + '" aria-label="Выше">↑</button></div>').join("") + "</div></div>";
        });
      } else if (tab === "import") {
        const L = A.col("importLog").slice().reverse();
        h += '<div class="card"><h3>{{i:notebook}} Импорт из тетради или файла</h3><p class="small">Вставьте строки из тетради или загрузите CSV/JSON. Формат строки свободный: сумма, дата и категория в любом порядке, например<br><code>12.09 350 еда Пятёрочка</code> · <code>+50000 зарплата 05.09</code> · <code>такси 420</code>.</p><textarea id="impT" rows="6" placeholder="Каждая операция — с новой строки"></textarea><div class="btns"><button class="btn primary" data-a="impParse">Разобрать</button><button class="btn" data-a="impFile">Загрузить CSV / JSON</button><button class="btn" data-a="impPhoto">{{i:camera}} Фото страницы</button></div><p class="small muted">Фото страницы сохраняется рядом с полем ввода, чтобы удобно переносить записи. Автоматическое распознавание рукописного текста без интернета в приложении недоступно.</p><div id="impPh"></div></div>';
        h += '<div class="card"><h3>{{i:mail}} Автоимпорт из SMS Сбербанка (900)</h3>' + (A.native ? '<label class="switch"><input type="checkbox" data-c="smsOn"' + (A.db().profile.smsImport ? " checked" : "") + '><span></span>Автоматически добавлять операции из новых SMS</label><button class="btn sm" data-a="smsScan">Проверить SMS за 30 дней</button><p class="small muted">Приложение читает только SMS от номера 900 на телефоне и ничего никуда не отправляет. Счёт определяется по последним 4 цифрам карты (укажите их в счёте).</p>' : '<p class="small muted">Доступно в Android-приложении.</p>') + "</div>";
        h += '<div class="card"><h3>История импорта</h3>' + (L.length ? L.map((x) => '<div class="item"><div class="tx"><b>' + esc(x.source) + " · " + x.count + " опер.</b><small>" + new Date(x.at).toLocaleString("ru-RU") + " · " + (x.undone ? "отменён" : "сумма " + money(x.total || 0)) + "</small></div>" + (x.undone ? "" : '<button class="btn sm ghost" data-a="impUndo" data-id="' + x.id + '">Отменить</button>') + "</div>").join("") : A.empty("Импорта ещё не было")) + "</div>";
      } else if (tab === "plan") {
        const P = (A.db().finplan[mk] = A.db().finplan[mk] || {});
        const rows = [["inc", "Доход", inc], ["must", "Обязательные расходы", null], ["save", "Накопления / резерв", null], ["free", "Свободные деньги", null], ["inv", "Инвестиции / крупные цели", null]];
        h += '<div class="card"><h3>Финансовый план на месяц</h3><table class="tbl"><tr><th></th><th class="r">План</th><th class="r">Факт</th></tr>' + rows.map(([k, n, fact]) => "<tr><td>" + n + '</td><td class="r"><input data-c="fp" data-k="' + k + '" type="number" inputmode="numeric" value="' + esc(P[k] ?? "") + '" style="width:100px;min-height:34px;padding:4px 8px;text-align:right"></td><td class="r">' + (fact != null ? fmtN(fact) : '<input data-c="fp" data-k="' + k + '_f" type="number" inputmode="numeric" value="' + esc(P[k + "_f"] ?? "") + '" style="width:100px;min-height:34px;padding:4px 8px;text-align:right">') + "</td></tr>").join("") +
          '<tr><td><b>Итого (доход − распределено)</b></td><td class="r"><b>' + fmtN((+P.inc || 0) - (+P.must || 0) - (+P.save || 0) - (+P.free || 0) - (+P.inv || 0)) + "</b></td><td></td></tr></table>" +
          '<div class="fld full" style="margin-top:10px"><label>Цель по накоплениям</label><input data-c="fp" data-k="goal" value="' + esc(P.goal || "") + '"></div></div>';
      }
      return h;
    },
    bind(el, r) {
      const tab = r.args[0] || "overview";
      const mk = () => A.route().params.m || today().slice(0, 7);
      A.bind(el, {
        tab(b) { A.go("money/" + b.dataset.v + (A.route().params.m ? "?m=" + A.route().params.m : ""), true); },
        mnav(b) { let [y, m] = mk().split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } A.go("money/" + tab + "?m=" + y + "-" + pad(m), true); },
        tx(b) { A.edit.tx(A.byId("tx", b.dataset.id)); }, newTx() { A.edit.tx({ kind: "out" }); },
        acc(b) { A.edit.account(A.byId("accounts", b.dataset.id)); }, newAcc() { A.edit.account(); },
        newSave() { A.edit.saving(); }, save(b) { A.edit.saving(A.byId("savings", b.dataset.id)); },
        dep(b) { const g = A.byId("savings", b.dataset.id); A.formSheet("Пополнить: " + g.name, [{ k: "v", label: "Сумма", type: "number", req: true }], {}, (o) => { g.cur = (+g.cur || 0) + o.v; A.save(); A.toast("Накоплено " + money(g.cur)); A.refresh(); }); },
        newRec() { A.edit.recurring(); }, rec(b) { A.edit.recurring(A.byId("recurring", b.dataset.id)); },
        txq(b) { A.go("money/ops?q=" + encodeURIComponent(b.value.trim()), true); },
        bud(b) { const v = A.num(b.value, 0); if (v > 0) A.db().budgets[b.dataset.k] = v; else delete A.db().budgets[b.dataset.k]; A.save(); A.refresh(); },
        rper(b) { A.go("money/reports?per=" + b.dataset.v, true); },
        cmpA(b) { A.go("money/reports?per=" + (A.route().params.per || "q") + "&a=" + b.value + "&b=" + (A.route().params.b || ""), true); },
        cmpB(b) { A.go("money/reports?per=" + (A.route().params.per || "q") + "&b=" + b.value + "&a=" + (A.route().params.a || ""), true); },
        catAdd(b) { A.editCat(b.dataset.k); }, catEdit(b) { A.editCat(b.dataset.k, +b.dataset.i); },
        catUp(b) { const L = A.db().cats[b.dataset.k], i = +b.dataset.i; if (i > 0) { [L[i - 1], L[i]] = [L[i], L[i - 1]]; A.save(); A.refresh(); } },
        impParse() { A.importPreview(A.parseTxText(el.querySelector("#impT").value), "Тетрадь (текст)"); },
        async impFile() { const t = await A.pickFile(); if (!t) return; let rows; try { const j = JSON.parse(t); rows = (Array.isArray(j) ? j : j.tx || []).map((x) => ({ date: x.date || today(), amt: Math.abs(+x.amt || +x.amount || +x.sum || 0), kind: x.kind || ((+x.amt || +x.amount || 0) < 0 ? "out" : x.type === "income" ? "in" : "out"), cat: x.cat || x.category || "Другое", note: x.note || x.comment || "" })).filter((x) => x.amt); } catch (e) { rows = A.parseTxText(t); } A.importPreview(rows, "Файл"); },
        async impPhoto() { const url = await A.pickPhoto(3 / 4, 1200); if (!url) return; el.querySelector("#impPh").innerHTML = '<img src="' + url + '" style="width:100%;border-radius:10px;margin-top:8px" alt="Фото страницы">'; },
        impUndo(b) { const x = A.byId("importLog", b.dataset.id); A.confirm("Удалить " + x.count + " импортированных операций?", () => { A.db().tx = A.col("tx").filter((t) => !(x.txIds || []).includes(t.id)); x.undone = true; A.save(); A.refresh(); }, "Отменить импорт", true); },
        smsOn(b) { A.db().profile.smsImport = b.checked; A.save(); if (b.checked) A.smsImport(false); },
        smsScan() { A.smsImport(true); },
        fp(b) { const P = A.db().finplan[mk()]; P[b.dataset.k] = b.type === "number" ? (b.value === "" ? null : A.num(b.value)) : b.value; A.save(); if (b.type === "number") A.refresh(); }
      });
    }
  });

  A.editCat = (k, i) => {
    const L = A.db().cats[k], c = i != null ? L[i] : { name: "", color: A.PAL[L.length % A.PAL.length] };
    A.formSheet(i != null ? "Категория" : "Новая категория", [{ k: "name", label: "Название", req: true, full: true }, { k: "color", label: "Цвет", type: "chips", opts: A.PAL.map((x) => [x, "●"]) }], c, (o) => {
      if (i != null && o.name !== c.name) A.col("tx").forEach((t) => { if (t.cat === c.name) t.cat = o.name; });
      if (i != null && o.name !== c.name && A.db().budgets[c.name]) { A.db().budgets[o.name] = A.db().budgets[c.name]; delete A.db().budgets[c.name]; }
      Object.assign(c, o); if (i == null) L.push(c); A.save(); A.refresh();
    }, Object.assign({ after: (w) => w.querySelectorAll('.chips[data-field="color"] .chip').forEach((x) => (x.style.color = x.dataset.v)) }, i != null ? { onDelete: () => { L.splice(i, 1); A.save(); A.refresh(); } } : {}));
  };

  /* ---------- разбор строк «сумма / дата / категория» ---------- */
  const guessCat = (text, kind) => {
    const t = text.toLowerCase();
    const rules = [["Еда", /пят[её]рочк|pyaterochk|магнит|magnit|перекр[её]ст|perekrest|вкусвилл|vkusvill|лента|lenta|ашан|auchan|дикси|dixy|продукт|еда|кафе|cafe|ресторан|restoran|кофе|coffee|самокат|samokat|яндекс.?лавк|lavka|food|burger|kfc|бургер|пицц|pizza|суши|sushi/], ["Транспорт", /такси|taxi|метро|metro|транспорт|бензин|азс|лукойл|lukoil|газпромн|gazprom|автобус|парковк|parking|мосгортранс|тройка/], ["Здоровье", /аптек|apteka|клиник|врач|анализ|стомат|zdrav|здоров/], ["Одежда", /одежд|zara|h&m|uniqlo|lamoda|wildberries|обувь|gloria/], ["Красота", /салон|маникюр|красот|косметик|летуаль|letu|zolotoe yabloko|золотое яблоко|парикмах/], ["Развлечения", /кино|cinema|театр|концерт|билет|игр/], ["Подписки", /подписк|netflix|кинопоиск|kinopoisk|spotify|yandex.?plus|плюс|ivi|okko|apple\.com|google/], ["Жильё", /жкх|квартплат|аренд|коммунал|электроэнерг|мосэнерго|свет|газ\b|вода\b/], ["Обучение", /курс|обучен|школ|книг|литрес|litres/], ["Дом", /леруа|leroy|икеа|ikea|hoff|хозтовар|дом\b/], ["Подарки", /подар|цвет/]];
    if (kind === "in") return /зарплат|аванс|оклад|salary/.test(t) ? "Зарплата" : /кешб|cashback|процент/.test(t) ? "Проценты / кешбэк" : "Другое";
    for (const [c, re] of rules) if (re.test(t) && A.expCats().includes(c)) return c;
    return A.expCats().includes("Другое") ? "Другое" : A.expCats()[0];
  };
  A.guessCat = guessCat;
  A.parseTxText = (text) => {
    const y = new Date().getFullYear(), out = [];
    text.split(/\n+/).map((l) => l.trim()).filter(Boolean).forEach((line) => {
      let date = today(), rest = line;
      const dm = /(\d{1,2})[./](\d{1,2})(?:[./](\d{2,4}))?/.exec(rest);
      if (dm) { const yy = dm[3] ? (dm[3].length === 2 ? 2000 + +dm[3] : +dm[3]) : y; date = yy + "-" + pad(+dm[2]) + "-" + pad(+dm[1]); rest = rest.replace(dm[0], " "); }
      const am = /([+-]?)\s*(\d[\d\s]*(?:[.,]\d{1,2})?)\s*(?:р|₽|руб)?/i.exec(rest);
      if (!am) return;
      const amt = A.num(am[2].replace(/\s/g, ""), 0); if (!amt) return;
      rest = rest.replace(am[0], " ").replace(/\s+/g, " ").trim();
      const kind = am[1] === "+" || /зарплат|доход|аванс|зачисл|подар(ок|или) мне/i.test(rest) ? "in" : "out";
      const words = rest.split(" ");
      const cats = kind === "in" ? A.incCats() : A.expCats();
      let cat = cats.find((c) => words.some((w) => w.toLowerCase() === c.toLowerCase() || c.toLowerCase().startsWith(w.toLowerCase()) && w.length > 2));
      if (!cat) cat = guessCat(rest, kind);
      out.push({ date, amt, kind, cat, note: rest });
    });
    return out;
  };
  // Предпросмотр: пользователь проверяет строки и подтверждает импорт.
  A.importPreview = (rows, source, opts = {}) => {
    if (!rows.length) { A.toast("Не нашла операций в тексте"); return; }
    const acc = (A.col("accounts").find((a) => a.type === "card") || A.col("accounts")[0] || {}).id || "";
    const body = '<p class="small muted">Проверьте строки: снимите галочку с лишних, поправьте категорию.</p><div class="list">' + rows.map((x, i) => '<div class="item" style="gap:6px"><input type="checkbox" data-i="' + i + '" checked style="width:22px;min-height:22px"><div class="tx"><b style="color:' + (x.kind === "in" ? "var(--good)" : "var(--ink)") + '">' + (x.kind === "in" ? "+" : "−") + money(x.amt) + " · " + fmtShort(x.date) + "</b><small>" + esc(x.note || "") + '</small></div><select data-ci="' + i + '" style="width:130px;min-height:34px;padding:2px 6px">' + (x.kind === "in" ? A.incCats() : A.expCats()).map((c) => "<option" + (c === x.cat ? " selected" : "") + ">" + esc(c) + "</option>").join("") + "</select></div>").join("") + "</div>";
    A.sheet("Импорт: " + rows.length + " операций", body, { buttons: [{ label: "Отмена", cls: "ghost" }, { label: "Импортировать", cls: "primary", onClick: (w) => {
      const ids = [];
      rows.forEach((x, i) => { if (!w.querySelector('[data-i="' + i + '"]').checked) return; x.cat = w.querySelector('[data-ci="' + i + '"]').value; const t = A.upsert("tx", { date: x.date, amt: x.amt, kind: x.kind, cat: x.cat, sub: x.sub || "", note: x.note || "", acc: x.acc || acc, src: opts.src || "import", smsId: x.smsId }); ids.push(t.id); });
      A.upsert("importLog", { at: new Date().toISOString(), source, count: ids.length, txIds: ids, total: sum(rows.filter((x, i) => w.querySelector('[data-i="' + i + '"]').checked && x.kind === "out").map((x) => x.amt)) });
      A.toast("Импортировано: " + ids.length); A.refresh();
    } }] });
  };

  /* ---------- SMS Сбербанка ---------- */
  A.parseSberSms = (body, ts) => {
    const b = body.replace(/ /g, " ");
    const am = /(\d[\d ]*(?:[.,]\d{1,2})?)\s?(?:р|₽|руб)/i.exec(b); if (!am) return null;
    const amt = A.num(am[1].replace(/ /g, ""), 0); if (!amt) return null;
    const low = b.toLowerCase();
    if (/баланс:?\s*$/.test(b.slice(0, am.index).toLowerCase())) return null;
    if (/код|пароль|никому не сообщайте|подтвержд/.test(low) && !/покупка|оплата|списан|зачислен/.test(low)) return null;
    const kind = /зачислен|поступлен|пополнен|возврат|перевод от|\+\s?\d/.test(low) ? "in" : /покупка|оплата|списан|выдача|перевод|платёж|платеж|оплатил/.test(low) ? "out" : null;
    if (!kind) return null;
    const card = /(?:\*|мир-|visa|ecmc|mir|счёт|счет|карта)\s?(\d{4})/i.exec(b);
    const dm = /(\d{2})\.(\d{2})(?:\.(\d{2,4}))?/.exec(b);
    const d = ts ? A.iso(new Date(+ts)) : dm ? new Date().getFullYear() + "-" + dm[2] + "-" + dm[1] : today();
    let merchant = b.slice(am.index + am[0].length).split(/баланс/i)[0].replace(/[.,]\s*$/, "").trim();
    if (!merchant) merchant = b.slice(0, am.index).replace(/^[^\s]+\s+\d{1,2}:\d{2}\s*/, "").trim();
    const acc = card ? A.col("accounts").find((a) => a.card4 === card[1]) : null;
    return { date: d, amt, kind, cat: guessCat(merchant + " " + b, kind), note: merchant.slice(0, 60), acc: acc ? acc.id : "", sub: "Сбербанк" };
  };
  A.smsImport = async (manual) => {
    if (!A.native || !A.native.smsList) return;
    const raw = await A.nativeCall("sms", () => A.native.smsList(manual ? 30 : 3));
    let list; try { list = JSON.parse(raw); } catch (e) { if (manual) A.toast("Нет доступа к SMS — разрешите чтение SMS"); return; }
    const known = new Set(A.col("tx").map((t) => t.smsId).filter(Boolean));
    const rows = list.filter((m) => !known.has(m.id)).map((m) => { const x = A.parseSberSms(m.body, m.date); if (x) x.smsId = m.id; return x; }).filter(Boolean);
    if (!rows.length) { if (manual) A.toast("Новых операций в SMS не найдено"); return; }
    if (manual) return A.importPreview(rows, "SMS 900", { src: "sms" });
    // Автоматический режим: добавляем сразу, запись в истории импорта позволяет отменить.
    const acc = (A.col("accounts").find((a) => a.type === "card") || {}).id || "";
    const ids = rows.map((x) => A.upsert("tx", Object.assign({ src: "sms" }, x, { acc: x.acc || acc })).id);
    A.upsert("importLog", { at: new Date().toISOString(), source: "SMS 900 (авто)", count: ids.length, txIds: ids, total: sum(rows.filter((x) => x.kind === "out").map((x) => x.amt)) });
    A.toast("Из SMS добавлено операций: " + ids.length);
  };
})();
