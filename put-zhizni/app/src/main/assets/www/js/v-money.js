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
      { k: "cat", label: "Категория", type: "select", opts: t.kind === "in" ? A.INC_CATS : A.EXP_CATS },
      { k: "sub", label: t.kind === "in" ? "Источник" : "Подкатегория" },
      { k: "acc", label: t.kind === "move" ? "Со счёта" : "Счёт", type: "select", opts: A.col("accounts").map((a) => [a.id, a.name]) },
      { k: "to", label: "На счёт (для перевода)", type: "select", opts: [["", "—"]].concat(A.col("accounts").map((a) => [a.id, a.name])) },
      { k: "note", label: "Комментарий", type: "textarea" }
    ];
    const open = () => {
      const w = A.formSheet(isNew ? (t.kind === "in" ? "Доход" : t.kind === "move" ? "Перевод" : "Расход") : "Операция", fields(), t, (o) => {
        Object.assign(t, o);
        if (t.kind === "move" && (!t.to || t.to === t.acc)) { A.toast("Выберите другой счёт"); return false; }
        A.upsert("tx", t); A.refresh();
      }, isNew ? {} : { onDelete: () => { A.remove("tx", t.id); A.refresh(); } });
      // смена типа перестраивает список категорий
      w.querySelector('.chips[data-field="kind"]').addEventListener("click", (e) => {
        const c = e.target.closest(".chip"); if (!c || c.dataset.v === t.kind) return;
        Object.assign(t, A.readForm(w, fields())); t.kind = c.dataset.v; t.cat = t.kind === "in" ? A.INC_CATS[0] : "Еда";
        A.closeSheet(); setTimeout(open, 230);
      });
      const amt = w.querySelector('[name="amt"]'); if (amt && isNew) setTimeout(() => amt.focus(), 250);
    };
    open();
  };
  A.edit.account = (a) => A.formSheet(a ? "Счёт" : "Новый счёт", [{ k: "name", label: "Название", req: true, full: true }, { k: "type", label: "Тип", type: "select", opts: [["cash", "Наличные"], ["card", "Банковская карта"], ["savings", "Накопления"], ["other", "Другое"]] }, { k: "start", label: "Начальный остаток, ₽", type: "number" }], a || { type: "card", start: 0 }, (o) => { A.upsert("accounts", Object.assign(a || {}, o)); A.refresh(); }, a ? { onDelete: () => { A.remove("accounts", a.id); A.refresh(); } } : {});
  A.edit.saving = (g) => A.formSheet(g ? "Цель накоплений" : "Новая цель", [{ k: "name", label: "Название", req: true, full: true }, { k: "target", label: "Целевая сумма", type: "number", req: true }, { k: "cur", label: "Накоплено сейчас", type: "number" }, { k: "deadline", label: "Срок", type: "date" }, { k: "monthly", label: "Регулярный взнос в месяц", type: "number" }, { k: "why", label: "Какую ценность обслуживает", full: true }], g || {}, (o) => { A.upsert("savings", Object.assign(g || {}, o)); A.refresh(); }, g ? { onDelete: () => { A.remove("savings", g.id); A.refresh(); } } : {});
  A.edit.recurring = (r) => A.formSheet(r ? "Регулярный платёж" : "Новый регулярный платёж", [
    { k: "name", label: "Название", req: true, full: true, ph: "Подписка, аренда, связь…" }, { k: "amt", label: "Сумма", type: "number", req: true },
    { k: "kind", label: "Тип", type: "select", opts: [["out", "Расход"], ["in", "Доход"]] }, { k: "cat", label: "Категория", type: "select", opts: A.EXP_CATS.concat(A.INC_CATS) },
    { k: "acc", label: "Счёт", type: "select", opts: A.col("accounts").map((a) => [a.id, a.name]) },
    { k: "period", label: "Период", type: "select", opts: [["month", "Ежемесячно"], ["week", "Еженедельно"], ["year", "Ежегодно"]] },
    { k: "next", label: "Следующая дата", type: "date", req: true },
    { k: "auto", label: "Запись", type: "check", text: "Создавать операцию автоматически в срок" },
    { k: "active", label: "Статус", type: "check", text: "Активен" }
  ], r || { kind: "out", cat: "Подписки", period: "month", next: today(), active: true, auto: false }, (o) => { const x = Object.assign(r || {}, o); x.dom = A.parse(x.next).getDate(); A.upsert("recurring", x); A.processRecurring(); A.syncReminders(); A.refresh(); }, r ? { onDelete: () => { A.remove("recurring", r.id); A.syncReminders(); A.refresh(); } } : {});

  const monthNav = (mk) => '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';

  A.view("money", {
    title: "Финансы",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.tx({kind:\'out\'})" aria-label="Расход">−</button><button class="icon-btn" onclick="App.edit.tx({kind:\'in\'})" aria-label="Доход">＋</button>'; },
    render(el, r) {
      const tab = r.args[0] || "overview", mk = r.params.m || today().slice(0, 7);
      const tx = A.monthTx(mk), inc = sum(tx.filter((t) => t.kind === "in").map((t) => t.amt)), out = sum(tx.filter((t) => t.kind === "out").map((t) => t.amt));
      const B = A.db().budgets;
      let h = A.seg([["overview", "Обзор"], ["ops", "Операции"], ["budget", "Бюджет"], ["save", "Накопления"], ["rec", "Регулярные"], ["plan", "План месяца"]], tab, "tab");
      if (tab !== "save" && tab !== "rec") h += monthNav(mk);
      const byCat = {}; tx.filter((t) => t.kind === "out").forEach((t) => (byCat[t.cat] = (byCat[t.cat] || 0) + t.amt));
      if (tab === "overview") {
        h += '<div class="grid3"><div class="stat"><small>Доходы</small><b style="color:var(--good)">' + money(inc) + '</b></div><div class="stat"><small>Расходы</small><b style="color:var(--bad)">' + money(out) + '</b></div><div class="stat"><small>Разница</small><b>' + money(inc - out) + "</b></div></div>";
        h += '<div class="card" style="margin-top:12px"><h3>Счета<span class="sp"></span><button class="link" data-a="newAcc">+ счёт</button></h3>' + A.col("accounts").map((a) => '<div class="item" data-a="acc" data-id="' + a.id + '"><div class="ic">' + ({ cash: "💵", card: "💳", savings: "🏦" }[a.type] || "•") + '</div><div class="tx"><b>' + esc(a.name) + '</b></div><b class="num">' + money(A.accBalance(a)) + "</b></div>").join("") + '<div class="row between" style="margin-top:6px"><span class="muted">Итого</span><b>' + money(sum(A.col("accounts").map(A.accBalance))) + "</b></div></div>";
        if (Object.keys(byCat).length) h += '<div class="card"><h3>Расходы по категориям</h3>' + A.charts.donut(Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([n, v], i) => ({ name: n, v, color: PAL[i % PAL.length] })), fmtN(out), "₽ за месяц") + "</div>";
        const budgets = Object.keys(B).filter((c) => B[c] > 0);
        if (budgets.length) h += '<div class="card"><h3>Бюджеты</h3>' + budgets.map((c) => { const f = byCat[c] || 0, p = f / B[c]; return '<div style="margin:8px 0"><div class="row small"><span class="grow">' + esc(c) + "</span><b>" + money(f) + " / " + money(B[c]) + "</b></div>" + A.bar(p, p > 1 ? "var(--bad)" : p > 0.85 ? "var(--warn)" : "var(--sage)") + "</div>"; }).join("") + "</div>";
        const months = []; for (let i = 5; i >= 0; i--) { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - i); const k = d.getFullYear() + "-" + pad(d.getMonth() + 1); const t2 = A.monthTx(k); months.push({ x: A.MON_SHORT[d.getMonth()], i: sum(t2.filter((t) => t.kind === "in").map((t) => t.amt)), o: sum(t2.filter((t) => t.kind === "out").map((t) => t.amt)) }); }
        h += '<div class="card"><h3>Расходы по месяцам</h3>' + A.charts.bars(months.map((m) => ({ x: m.x, v: m.o })), { color: "#E3899A", fmt: money }) + "</div>";
        h += '<div class="card tint"><p class="small">Деньги — средство свободы. Раз в месяц фиксируй доход, обязательные расходы, накопления и свободные деньги. Резерв — это «фонд свободы».</p></div>';
      } else if (tab === "ops") {
        const list = tx.slice().sort((a, b) => (b.date > a.date ? 1 : b.date < a.date ? -1 : 0));
        let last = "";
        h += '<div class="card"><div class="list">' + (list.length ? list.map((t) => { let s = ""; if (t.date !== last) { last = t.date; s += '<div class="small muted" style="margin-top:8px">' + fmtDate(t.date, { dow: true }) + "</div>"; } const acc = A.byId("accounts", t.acc); return s + '<div class="item" data-a="tx" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.kind === "move" ? "Перевод" : t.cat) + "</b><small>" + esc([t.sub, t.note, acc && acc.name].filter(Boolean).join(" · ")) + '</small></div><b class="num" style="color:' + (t.kind === "in" ? "var(--good)" : t.kind === "move" ? "var(--ink2)" : "var(--ink)") + '">' + (t.kind === "in" ? "+" : t.kind === "out" ? "−" : "") + money(t.amt) + "</b></div>"; }).join("") : A.empty("Операций за месяц нет")) + "</div></div>";
        h += '<button class="fab" data-a="newTx" aria-label="Добавить операцию">+</button>';
      } else if (tab === "budget") {
        h += '<div class="card"><h3>Месячные лимиты</h3><p class="small muted">План / факт / остаток / процент использования.</p><table class="tbl"><tr><th>Категория</th><th class="r">Лимит</th><th class="r">Факт</th><th class="r">Остаток</th></tr>' +
          A.EXP_CATS.map((c) => { const f = byCat[c] || 0, l = B[c] || 0; return "<tr><td>" + esc(c) + '</td><td class="r"><input data-c="bud" data-k="' + esc(c) + '" type="number" inputmode="numeric" value="' + (l || "") + '" style="width:90px;min-height:34px;padding:4px 8px;text-align:right"></td><td class="r">' + fmtN(f) + '</td><td class="r" style="color:' + (l && f > l ? "var(--bad)" : "inherit") + '">' + (l ? fmtN(l - f) + "<br><small>" + Math.round((f / l) * 100) + "%</small>" : "—") + "</td></tr>"; }).join("") +
          '<tr><td><b>Итого</b></td><td class="r"><b>' + fmtN(sum(Object.values(B))) + '</b></td><td class="r"><b>' + fmtN(out) + '</b></td><td class="r"><b>' + fmtN(sum(Object.values(B)) - out) + "</b></td></tr></table></div>";
      } else if (tab === "save") {
        h += '<div class="card"><h3>Цели накоплений<span class="sp"></span><button class="btn sm primary" data-a="newSave">+</button></h3>' + A.col("savings").map((g) => { const p = g.target ? g.cur / g.target : 0; const left = Math.max(0, g.target - g.cur); const months = g.monthly ? Math.ceil(left / g.monthly) : null; return '<div style="padding:10px 0;border-bottom:1px solid var(--line)"><div class="row" data-a="save" data-id="' + g.id + '"><div class="grow"><b>' + esc(g.name) + '</b><div class="small muted">' + money(g.cur) + " из " + money(g.target) + (g.deadline ? " · срок " + fmtDate(g.deadline) : "") + (months ? " · ≈ " + months + " мес. при взносе " + money(g.monthly) : "") + "</div></div><b>" + Math.round(p * 100) + "%</b></div>" + A.bar(p, "var(--gold)") + '<div class="btns"><button class="btn sm" data-a="dep" data-id="' + g.id + '">+ пополнить</button></div></div>'; }).join("") + "</div>";
      } else if (tab === "rec") {
        const rs = A.col("recurring");
        h += '<div class="card"><h3>Подписки и регулярные платежи<span class="sp"></span><button class="btn sm primary" data-a="newRec">+</button></h3>' + (rs.length ? rs.map((r2) => '<div class="item" data-a="rec" data-id="' + r2.id + '"><div class="ic">↻</div><div class="tx"><b>' + esc(r2.name) + "</b><small>" + ({ month: "ежемесячно", week: "еженедельно", year: "ежегодно" }[r2.period] || "") + " · следующий " + (r2.next ? fmtDate(r2.next) : "—") + (r2.auto ? " · авто" : " · напоминание") + (r2.active ? "" : " · выключен") + '</small></div><b class="num">' + money(r2.amt) + "</b></div>").join("") : A.empty("Нет регулярных платежей")) +
          '<p class="small muted">Если включено «автоматически», в день платежа создаётся запись операции. Иначе приходит напоминание.</p></div>';
        const monthly = sum(rs.filter((x) => x.active && x.kind !== "in").map((x) => (x.period === "week" ? x.amt * 4.33 : x.period === "year" ? x.amt / 12 : x.amt)));
        h += '<div class="stat"><small>Регулярные расходы в месяц (оценка)</small><b>' + money(monthly) + "</b></div>";
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
        bud(b) { const v = A.num(b.value, 0); if (v > 0) A.db().budgets[b.dataset.k] = v; else delete A.db().budgets[b.dataset.k]; A.save(); A.refresh(); },
        fp(b) { const P = A.db().finplan[mk()]; P[b.dataset.k] = b.type === "number" ? (b.value === "" ? null : A.num(b.value)) : b.value; A.save(); if (b.type === "number") A.refresh(); }
      });
    }
  });
})();
