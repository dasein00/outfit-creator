/* Рецепты и меню: каталог, карточка рецепта, редактор, меню на неделю, шаблоны, повторяющиеся блюда,
   список покупок, история готовки. Связано с дневником питания. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtShort, fmtDate, sum, round } = A;
  A.edit = A.edit || {};

  const CATS = ["Завтраки", "Обеды", "Ужины", "Супы", "Салаты", "Перекусы", "Десерты"];
  const TAGS = ["быстрые", "лёгкие", "вегетарианские", "белковые", "без глютена"];
  const SHOP_CATS = ["Овощи", "Фрукты", "Мясо и рыба", "Молочное", "Крупы и бакалея", "Другое"];
  const SLOTS = A.MEALS; // завтрак, обед, ужин, перекус

  A.allRecipes = () => (window.RECIPES || []).concat(A.col("recipes"));
  A.recipe = (id) => A.allRecipes().find((r) => r.id === id) || null;
  // КБЖУ на одну порцию.
  A.recipeMacros = (r) => {
    const t = { kcal: 0, p: 0, f: 0, c: 0 };
    (r.ingr || []).forEach((i) => { ["kcal", "p", "f", "c"].forEach((k) => (t[k] += ((+i[k] || 0) * (+i.g || 0)) / 100)); });
    const n = +r.servings || 1;
    return { kcal: Math.round(t.kcal / n), p: round(t.p / n, 1), f: round(t.f / n, 1), c: round(t.c / n, 1), g: Math.round(sum((r.ingr || []).map((i) => +i.g || 0)) / n) };
  };
  const recThumb = (r, cls) => r.photo ? '<img class="' + (cls || "thumb") + '" data-photo="' + r.photo + '" alt="">' : '<div class="' + (cls || "thumb") + '" style="display:flex;align-items:center;justify-content:center;font-size:44px;background:linear-gradient(135deg,var(--peach2),var(--accent2))">' + (r.emoji || "🍽") + "</div>";

  /* ---------- дневник: добавить блюдо из рецепта ---------- */
  A.addRecipeToDiary = (r, d, type, servings) => {
    const m = A.recipeMacros(r), n = +servings || 1;
    const meal = A.upsert("meals", { date: d, type, name: r.name + (n !== 1 ? " × " + n : ""), recipeId: r.id, g: m.g * n, kcal: Math.round(m.kcal * n), p: round(m.p * n, 1), f: round(m.f * n, 1), c: round(m.c * n, 1), fib: 0 });
    return meal;
  };

  /* ---------- редактор рецепта ---------- */
  A.edit.recipe = (r0) => {
    const isNew = !r0 || r0.builtin;
    const r = r0 ? JSON.parse(JSON.stringify(r0)) : { name: "", cat: "Обеды", tags: [], prep: 10, cook: 20, diff: 1, servings: 2, ingr: [], steps: [], tips: "", subs: "", emoji: "🍽" };
    if (r0 && r0.builtin) { delete r.builtin; r.id = null; r.name = r.name + " (моя версия)"; }
    const fields = [
      { k: "name", label: "Название", req: true, full: true }, { k: "emoji", label: "Значок" }, { k: "cat", label: "Категория", type: "select", opts: CATS },
      { k: "tags", label: "Теги", type: "multi", opts: TAGS },
      { k: "prep", label: "Подготовка, мин", type: "number" }, { k: "cook", label: "Приготовление, мин", type: "number" },
      { k: "diff", label: "Сложность", type: "chips", numeric: true, opts: [[1, "1 — легко"], [2, "2 — средне"], [3, "3 — сложно"]] }, { k: "servings", label: "Порций", type: "number", min: 1 },
      { k: "stepsStr", label: "Шаги (каждый с новой строки)", type: "textarea", rows: 5 },
      { k: "tips", label: "Советы и трюки", type: "textarea", rows: 2 }, { k: "subs", label: "Возможные замены", type: "textarea", rows: 2 }
    ];
    const ingr = r.ingr.map((i) => Object.assign({}, i));
    const ingHtml = () => '<div class="fsec">Ингредиенты · КБЖУ на порцию: <b id="rmac"></b></div><div class="list">' + ingr.map((i, k) => '<div class="row" style="gap:6px;margin:3px 0"><div class="grow small"><b>' + esc(i.name) + '</b><br><span class="muted">' + i.kcal + " ккал/100 г</span></div><input type=\"number\" data-g=\"" + k + '" value="' + esc(i.g) + '" style="width:80px"> г<button type="button" class="icon-btn" data-del="' + k + '">✕</button></div>').join("") + '</div><div class="fld full" style="margin-top:6px"><input id="iq" placeholder="Добавить ингредиент: поиск в базе продуктов" autocomplete="off"><div id="il" class="list" style="max-height:180px;overflow:auto"></div></div>';
    const w = A.formSheet(isNew ? "Новый рецепт" : "Рецепт", fields, Object.assign({}, r, { stepsStr: (r.steps || []).join("\n") }), (o) => {
      o.steps = (o.stepsStr || "").split("\n").map((x) => x.trim()).filter(Boolean); delete o.stepsStr;
      Object.assign(r, o); r.ingr = ingr; r.servings = Math.max(1, +r.servings || 1);
      A.upsert("recipes", r); A.toast("Рецепт сохранён"); A.go("recipes/r/" + r.id);
    }, {
      pre: '<div id="rph" style="margin-bottom:10px"></div>',
      post: '<div id="ingBox"></div>',
      onDelete: !isNew && r0 && !r0.builtin ? () => { if (r.photo) A.photos.del(r.photo); A.remove("recipes", r.id); A.go("recipes", true); } : null,
      after(wr) {
        const ph = wr.querySelector("#rph");
        const drawPh = () => { ph.innerHTML = (r.photo ? '<img class="thumb" data-photo="' + r.photo + '" alt="">' : "") + '<button type="button" class="btn sm" id="phBtn" style="margin-top:6px">' + (r.photo ? "Заменить фото" : "📷 Добавить фото блюда") + "</button>"; A.hydratePhotos(ph); ph.querySelector("#phBtn").onclick = async () => { const url = await A.pickPhoto(4 / 3, 900); if (!url) return; const id = A.uid(); await A.photos.put(id, url); r.photo = id; drawPh(); }; };
        drawPh();
        const box = wr.querySelector("#ingBox");
        const mac = () => { const m = A.recipeMacros({ ingr, servings: +wr.querySelector('[name="servings"]').value || 1 }); const e = box.querySelector("#rmac"); if (e) e.textContent = m.kcal + " ккал · Б " + m.p + " · Ж " + m.f + " · У " + m.c; };
        const draw = () => { box.innerHTML = ingHtml(); mac(); wire(); };
        const wire = () => {
          const q = box.querySelector("#iq"), l = box.querySelector("#il");
          const src = () => A.col("foods").map((f) => ({ name: f.name, kcal: f.kcal, p: f.p, f: f.f, c: f.c, cat: "Другое" })).concat(Object.entries(window.INGREDIENTS || {}).map(([n, x]) => ({ name: n, kcal: x[0], p: x[1], f: x[2], c: x[3], cat: x[4] })));
          q.addEventListener("input", () => {
            const s = q.value.trim().toLowerCase(); if (!s) { l.innerHTML = ""; return; }
            const seen = {}; const res = src().filter((x) => x.name.toLowerCase().includes(s) && !seen[x.name] && (seen[x.name] = 1)).slice(0, 15);
            l.innerHTML = res.map((x, k) => '<div class="item" data-k="' + k + '" style="min-height:38px;padding:4px 2px"><div class="tx"><b>' + esc(x.name) + "</b><small>" + x.kcal + " ккал · Б " + x.p + " Ж " + x.f + " У " + x.c + "</small></div></div>").join("") + '<div class="item" data-new="1" style="min-height:38px"><div class="tx"><b>+ «' + esc(q.value.trim()) + '» вручную</b><small>КБЖУ можно задать потом в базе продуктов</small></div></div>';
            l._res = res;
          });
          l.addEventListener("click", (e) => { const it = e.target.closest("[data-k],[data-new]"); if (!it) return; const x = it.dataset.new ? { name: q.value.trim(), kcal: 0, p: 0, f: 0, c: 0, cat: "Другое" } : l._res[+it.dataset.k]; ingr.push(Object.assign({ g: 100 }, x)); draw(); });
          box.querySelectorAll("[data-g]").forEach((inp) => inp.addEventListener("input", () => { ingr[+inp.dataset.g].g = A.num(inp.value, 0); mac(); }));
          box.querySelectorAll("[data-del]").forEach((b) => (b.onclick = () => { ingr.splice(+b.dataset.del, 1); draw(); }));
        };
        draw();
        wr.querySelector('[name="servings"]').addEventListener("input", mac);
      }
    });
    return w;
  };

  /* ---------- меню ---------- */
  const menuDay = (d) => (A.db().menu[d] = A.db().menu[d] || []);
  // Повторяющиеся блюда появляются в меню автоматически.
  A.ensureRecurring = (dates) => {
    let ch = false;
    A.col("menuRecur").forEach((rr) => dates.forEach((d) => {
      if (!rr.active || d < rr.start || (rr.end && d > rr.end) || !(rr.days || []).includes(A.dow(d))) return;
      const m = menuDay(d);
      if (m.some((x) => x.recurId === rr.id) || (rr.skip || []).includes(d)) return;
      m.push({ id: A.uid(), slot: rr.slot, recipeId: rr.recipeId, servings: rr.servings || 1, status: "plan", recurId: rr.id });
      ch = true;
    }));
    if (ch) A.save();
  };
  const itemMacros = (x) => { const r = x.recipeId ? A.recipe(x.recipeId) : null; if (!r) return { kcal: +x.kcal || 0, p: +x.p || 0, f: +x.f || 0, c: +x.c || 0 }; const m = A.recipeMacros(r), n = +x.servings || 1; return { kcal: m.kcal * n, p: m.p * n, f: m.f * n, c: m.c * n }; };
  const setStatus = (d, x, st) => {
    x.status = st;
    if (st === "eaten" && !x.mealId) {
      const r = A.recipe(x.recipeId);
      const meal = r ? A.addRecipeToDiary(r, d, x.slot, x.servings) : A.upsert("meals", { date: d, type: x.slot, name: x.name, g: 0, kcal: +x.kcal || 0, p: 0, f: 0, c: 0, fib: 0 });
      x.mealId = meal.id;
    }
    if (st !== "eaten" && x.mealId) { A.remove("meals", x.mealId); x.mealId = null; }
    A.save();
  };
  const pickRecipe = (cb) => {
    const w = A.sheet("Выбрать блюдо", '<input id="rq" placeholder="Поиск по названию"><div id="rl" class="list" style="margin-top:6px"></div><div class="fld full" style="margin-top:10px"><label>Или просто название блюда</label><div class="inline-add"><input id="rn" placeholder="Например: гречка с котлетой"><button class="btn" id="rnb">+</button></div></div>');
    const draw = () => { const s = w.querySelector("#rq").value.trim().toLowerCase(); w.querySelector("#rl").innerHTML = A.allRecipes().filter((r) => !s || r.name.toLowerCase().includes(s)).sort((a, b) => (A.db().favs[b.id] ? 1 : 0) - (A.db().favs[a.id] ? 1 : 0)).map((r) => '<div class="item" data-id="' + r.id + '"><div class="ic">' + (r.emoji || "🍽") + '</div><div class="tx"><b>' + (A.db().favs[r.id] ? "★ " : "") + esc(r.name) + "</b><small>" + A.recipeMacros(r).kcal + " ккал/порция · " + esc(r.cat) + "</small></div></div>").join(""); };
    draw();
    w.querySelector("#rq").addEventListener("input", draw);
    w.querySelector("#rl").addEventListener("click", (e) => { const it = e.target.closest("[data-id]"); if (!it) return; A.closeSheet(); cb({ recipeId: it.dataset.id }); });
    w.querySelector("#rnb").onclick = () => { const v = w.querySelector("#rn").value.trim(); if (!v) return; A.closeSheet(); cb({ name: v }); };
  };
  A.addToMenu = (d, slot, recipeId) => {
    const go = (o) => { menuDay(d).push(Object.assign({ id: A.uid(), slot, servings: 1, status: "plan" }, o)); A.save(); A.toast("Добавлено в меню"); A.refresh(); };
    if (recipeId) go({ recipeId }); else pickRecipe(go);
  };

  /* ---------- список покупок ---------- */
  A.buildShopping = (from, to) => {
    const agg = {};
    A.range(from, to).forEach((d) => (A.db().menu[d] || []).forEach((x) => {
      if (x.status === "skip" || x.status === "eaten") return;
      const r = A.recipe(x.recipeId); if (!r) return;
      const k = (+x.servings || 1) / (+r.servings || 1);
      (r.ingr || []).forEach((i) => { const key = i.name.toLowerCase(); agg[key] = agg[key] || { name: i.name, g: 0, cat: i.cat || "Другое" }; agg[key].g += (+i.g || 0) * k; });
    }));
    const manual = A.col("shopping").filter((x) => x.manual);
    A.db().shopping = manual.concat(Object.values(agg).map((x) => ({ id: A.uid(), name: x.name, g: Math.round(x.g), cat: x.cat, bought: false })));
    A.save();
  };
  const dedupeShopping = () => {
    const map = {}, out = [];
    A.col("shopping").forEach((x) => { const k = x.name.trim().toLowerCase(); if (map[k]) { map[k].g = (+map[k].g || 0) + (+x.g || 0); map[k].bought = map[k].bought && x.bought; } else { map[k] = x; out.push(x); } });
    const n = A.col("shopping").length - out.length; A.db().shopping = out; A.save(); return n;
  };
  const shoppingText = () => SHOP_CATS.map((c) => { const it = A.col("shopping").filter((x) => (x.cat || "Другое") === c && !x.bought); return it.length ? c + ":\n" + it.map((x) => "☐ " + x.name + (x.g ? " — " + x.g + " г" : "") + (x.qty ? " — " + x.qty : "")).join("\n") : ""; }).filter(Boolean).join("\n\n");

  /* ---------- экраны ---------- */
  const TABS = [["catalog", "Рецепты"], ["menu", "Меню"], ["shop", "Покупки"], ["presets", "Шаблоны"], ["history", "Готовила"]];
  A.view("recipes", {
    title: (r) => (r.args[0] === "r" ? "Рецепт" : "Рецепты и меню"), tab: "more",
    actions(el, r) { if (!r.args[0] || r.args[0] === "catalog") el.innerHTML = '<button class="icon-btn" onclick="App.edit.recipe()" aria-label="Новый рецепт">＋</button>'; },
    render(el, r) {
      const sec = r.args[0] || "catalog", P = r.params;
      if (sec === "r") {
        const rc = A.recipe(r.args[1]); if (!rc) return A.empty("Рецепт не найден");
        const sv = +(P.s || rc.servings || 1), k = sv / (+rc.servings || 1), m = A.recipeMacros(rc);
        const fav = A.db().favs[rc.id];
        return recThumb(rc) + '<div class="card" style="margin-top:12px"><div class="row"><h2 class="grow" style="font-size:21px">' + esc(rc.name) + '</h2><button class="icon-btn" data-a="fav" style="color:' + (fav ? "var(--gold)" : "var(--ink2)") + '" aria-label="Избранное">' + (fav ? "★" : "☆") + "</button></div>" +
          '<div class="small muted">' + esc(rc.cat) + " · " + (rc.tags || []).map((t) => "#" + esc(t)).join(" ") + '</div><div class="grid3" style="margin-top:10px"><div class="stat"><small>Подготовка</small><b>' + (rc.prep || 0) + ' мин</b></div><div class="stat"><small>Готовка</small><b>' + (rc.cook || 0) + ' мин</b></div><div class="stat"><small>Сложность</small><b>' + "●".repeat(rc.diff || 1) + '<span style="opacity:.25">' + "●".repeat(3 - (rc.diff || 1)) + "</span></b></div></div></div>" +
          '<div class="card tint"><h3>КБЖУ на порцию</h3><div class="grid2"><div class="stat"><small>Калории</small><b>' + m.kcal + '</b></div><div class="stat"><small>Белки</small><b>' + m.p + ' г</b></div><div class="stat"><small>Жиры</small><b>' + m.f + ' г</b></div><div class="stat"><small>Углеводы</small><b>' + m.c + " г</b></div></div></div>" +
          '<div class="card"><h3>Ингредиенты<span class="sp"></span><button class="icon-btn" data-a="sv" data-v="-1" aria-label="Меньше порций">−</button><b>' + sv + " порц.</b><button class=\"icon-btn\" data-a=\"sv\" data-v=\"1\" aria-label=\"Больше порций\">+</button></h3>" + (rc.ingr || []).map((i) => '<div class="row" style="padding:5px 0;border-bottom:1px solid var(--line)"><span class="grow">' + esc(i.name) + '</span><b class="num">' + fmtN((+i.g || 0) * k) + " г</b></div>").join("") + "</div>" +
          '<div class="card"><h3>Приготовление</h3><ol>' + (rc.steps || []).map((s) => '<li style="margin:6px 0">' + esc(s) + "</li>").join("") + "</ol></div>" +
          (rc.tips ? '<div class="card sage"><h3>💡 Советы и трюки</h3><p>' + esc(rc.tips) + "</p></div>" : "") +
          (rc.subs ? '<div class="card peach"><h3>🔁 Замены</h3><p>' + esc(rc.subs) + "</p></div>" : "") +
          '<div class="btns"><button class="btn primary" data-a="cooked">🍳 Приготовила</button><button class="btn" data-a="toDiary">+ в дневник</button><button class="btn" data-a="toMenu">+ в меню</button><button class="btn" data-a="recur">↻ повторять</button><button class="btn ghost" data-a="editR">' + (rc.builtin ? "Сделать свою версию" : "Изменить") + "</button></div>";
      }
      let h = A.seg(TABS, sec, "sec");
      if (sec === "catalog") {
        const q = (P.q || "").toLowerCase(), cat = P.c || "", tag = P.t || "", sort = P.s || "name", fav = P.f === "1";
        let list = A.allRecipes().filter((x) => (!q || x.name.toLowerCase().includes(q)) && (!cat || x.cat === cat) && (!tag || (x.tags || []).includes(tag)) && (!fav || A.db().favs[x.id]));
        list.sort((a, b) => sort === "diff" ? (a.diff || 1) - (b.diff || 1) : sort === "time" ? ((a.prep || 0) + (a.cook || 0)) - ((b.prep || 0) + (b.cook || 0)) : sort === "kcal" ? A.recipeMacros(a).kcal - A.recipeMacros(b).kcal : a.name.localeCompare(b.name));
        h += '<input id="rq" placeholder="🔍 Поиск по названию" value="' + esc(P.q || "") + '" style="margin-bottom:8px">';
        h += '<div class="chips" style="margin-bottom:6px;flex-wrap:nowrap;overflow-x:auto"><button class="chip' + (!cat ? " on" : "") + '" data-a="cat" data-v="" style="flex:0 0 auto">Все</button><button class="chip' + (fav ? " on" : "") + '" data-a="favf" style="flex:0 0 auto">★ Избранное</button>' + CATS.map((c) => '<button class="chip' + (cat === c ? " on" : "") + '" data-a="cat" data-v="' + c + '" style="flex:0 0 auto">' + c + "</button>").join("") + "</div>";
        h += '<div class="chips" style="margin-bottom:6px;flex-wrap:nowrap;overflow-x:auto">' + TAGS.map((t) => '<button class="chip' + (tag === t ? " on" : "") + '" data-a="tag" data-v="' + t + '" style="flex:0 0 auto">#' + t + "</button>").join("") + "</div>";
        h += A.seg([["name", "А–Я"], ["time", "по времени"], ["diff", "по сложности"], ["kcal", "по калориям"]], sort, "sort");
        h += '<div class="rgrid">' + (list.length ? list.map((x) => { const m = A.recipeMacros(x); return '<a class="rcard" href="#/recipes/r/' + x.id + '">' + recThumb(x) + '<div class="rb"><b>' + (A.db().favs[x.id] ? "★ " : "") + esc(x.name) + '</b><span class="muted">' + ((x.prep || 0) + (x.cook || 0)) + " мин · " + m.kcal + " ккал · " + "●".repeat(x.diff || 1) + "</span></div></a>"; }).join("") : A.empty("Ничего не найдено")) + "</div>";
      } else if (sec === "menu") {
        const ws = P.w || A.weekStart(today()), days = A.range(ws, addDays(ws, 6));
        A.ensureRecurring(days);
        const goal = A.db().profile.kcal;
        h += '<div class="datenav"><button class="icon-btn" data-a="wnav" data-v="-7">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + fmtShort(ws) + " – " + fmtShort(addDays(ws, 6)) + '</div><button class="icon-btn" data-a="wnav" data-v="7">›</button></div>';
        h += '<div class="btns" style="margin:0 0 12px"><button class="btn sm" data-a="saveWeek">Сохранить неделю как шаблон</button><button class="btn sm" data-a="shopBuild">🛒 Список покупок</button></div>';
        days.forEach((d) => {
          const items = menuDay(d);
          const tot = items.filter((x) => x.status !== "skip").reduce((t, x) => { const m = itemMacros(x); ["kcal", "p", "f", "c"].forEach((k) => (t[k] += m[k])); return t; }, { kcal: 0, p: 0, f: 0, c: 0 });
          h += '<div class="card"><h3>' + esc(A.DOW_FULL[A.dow(d) - 1][0].toUpperCase() + A.DOW_FULL[A.dow(d) - 1].slice(1)) + " <small>" + fmtShort(d) + '</small><span class="sp"></span><small>' + Math.round(tot.kcal) + " / " + goal + ' ккал</small></h3><div class="small muted">Б ' + Math.round(tot.p) + " · Ж " + Math.round(tot.f) + " · У " + Math.round(tot.c) + "</div>" + A.bar(tot.kcal / goal, "var(--peach)");
          SLOTS.forEach(([sk, sn]) => {
            const its = items.filter((x) => x.slot === sk);
            h += '<div class="row" style="margin-top:8px"><b class="small grow">' + sn + '</b><button class="btn sm ghost" data-a="addM" data-d="' + d + '" data-s="' + sk + '">+</button></div>';
            its.forEach((x) => {
              const rc = A.recipe(x.recipeId), m = itemMacros(x), idx = items.indexOf(x);
              const stc = { plan: ["запланировано", "var(--ink2)"], eaten: ["съедено ✓", "var(--good)"], skip: ["пропущено", "var(--bad)"] }[x.status || "plan"];
              h += '<div class="item" style="padding:6px 0"><div class="ic">' + (rc ? rc.emoji || "🍽" : "🍽") + '</div><div class="tx tap" data-a="mItem" data-d="' + d + '" data-id="' + x.id + '"><b>' + esc(rc ? rc.name : x.name) + (x.servings && x.servings !== 1 ? " × " + x.servings : "") + (x.recurId ? " ↻" : "") + '</b><small style="color:' + stc[1] + '">' + stc[0] + " · " + Math.round(m.kcal) + ' ккал</small></div><button class="icon-btn" data-a="mUp" data-d="' + d + '" data-i="' + idx + '" aria-label="Выше">↑</button><button class="icon-btn" data-a="mEat" data-d="' + d + '" data-id="' + x.id + '" aria-label="Съедено" style="color:var(--good)">✓</button></div>';
            });
          });
          h += '<div class="btns"><button class="btn sm ghost" data-a="saveDay" data-d="' + d + '">В шаблон</button><button class="btn sm ghost" data-a="applyP" data-d="' + d + '">Применить шаблон</button></div></div>';
        });
        const rr = A.col("menuRecur");
        h += '<div class="card"><h3>↻ Повторяющиеся блюда</h3>' + (rr.length ? rr.map((x) => { const rc = A.recipe(x.recipeId); return '<div class="item" data-a="recEdit" data-id="' + x.id + '"><div class="tx"><b>' + esc(rc ? rc.name : "?") + "</b><small>" + (A.MEALS.find((m) => m[0] === x.slot) || [0, ""])[1] + " · " + (x.days || []).map((k) => A.DOW[k - 1]).join(", ") + " · с " + fmtShort(x.start) + (x.end ? " по " + fmtShort(x.end) : "") + (x.active ? "" : " · выключено") + "</small></div></div>"; }).join("") : '<p class="small muted">Откройте рецепт и нажмите «↻ повторять», чтобы блюдо автоматически появлялось в меню в выбранные дни.</p>') + "</div>";
      } else if (sec === "shop") {
        const list = A.col("shopping");
        h += '<div class="btns" style="margin:0 0 12px"><button class="btn sm primary" data-a="shopBuild">Собрать из меню недели</button><button class="btn sm" data-a="shopDedupe">Убрать дубли</button><button class="btn sm" data-a="shopExport">Экспорт в текст</button><button class="btn sm ghost" data-a="shopClear">Очистить купленное</button></div>';
        h += '<div class="card nocollapse"><div class="inline-add"><input id="sq" placeholder="Добавить вручную: хлеб, 2 шт."><select id="sc" style="width:130px">' + SHOP_CATS.map((c) => "<option>" + c + "</option>").join("") + '</select><button class="btn primary" data-a="shopAdd">+</button></div></div>';
        SHOP_CATS.forEach((c) => {
          const it = list.filter((x) => (x.cat || "Другое") === c); if (!it.length) return;
          h += '<div class="card"><h3>' + c + '<span class="sp"></span><small>' + it.filter((x) => x.bought).length + "/" + it.length + '</small></h3><div class="list">' + it.map((x) => '<div class="item' + (x.bought ? " done" : "") + '"><button class="check' + (x.bought ? " on" : "") + '" data-a="shopTog" data-id="' + x.id + '">' + (x.bought ? "✓" : "") + '</button><div class="tx"><b>' + esc(x.name) + "</b><small>" + (x.g ? x.g + " г" : "") + (x.qty ? esc(x.qty) : "") + (x.manual ? " · вручную" : "") + '</small></div><button class="icon-btn" data-a="shopDel" data-id="' + x.id + '" aria-label="Удалить">✕</button></div>').join("") + "</div></div>";
        });
        if (!list.length) h += A.empty("Список пуст. Соберите его из меню недели или добавьте вручную.");
      } else if (sec === "presets") {
        const ps = A.col("menuPresets");
        h += '<div class="card"><h3>Шаблоны меню</h3>' + (ps.length ? ps.map((p) => '<div class="item"><div class="tx"><b>' + esc(p.name) + "</b><small>" + (p.kind === "week" ? "неделя" : "день") + " · блюд: " + sum(p.days.map((d) => d.length)) + '</small></div><button class="btn sm" data-a="pApply" data-id="' + p.id + '">Применить</button><button class="icon-btn" data-a="pEdit" data-id="' + p.id + '" aria-label="Изменить">✎</button></div>').join("") : A.empty("Сохраните день или неделю меню как шаблон на вкладке «Меню».")) + "</div>";
      } else if (sec === "history") {
        const mk = P.m || today().slice(0, 7);
        const log = A.col("cookLog").filter((x) => x.date.slice(0, 7) === mk).sort((a, b) => (a.date < b.date ? 1 : -1));
        h += '<div class="datenav"><button class="icon-btn" data-a="hnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="hnav" data-v="1">›</button></div>';
        h += '<div class="grid3"><div class="stat"><small>Приготовлено</small><b>' + log.length + '</b></div><div class="stat"><small>Разных блюд</small><b>' + new Set(log.map((x) => x.name)).size + '</b></div><div class="stat"><small>Порций</small><b>' + sum(log.map((x) => +x.servings || 1)) + "</b></div></div>";
        h += '<div class="card" style="margin-top:12px"><div class="list">' + (log.length ? log.map((x) => '<div class="item" data-a="hItem" data-id="' + x.id + '"><div class="ic">🍳</div><div class="tx"><b>' + esc(x.name) + "</b><small>" + fmtDate(x.date) + " · " + x.servings + " порц. · на порцию " + x.snap.kcal + " ккал, Б " + x.snap.p + " Ж " + x.snap.f + " У " + x.snap.c + (x.mealId ? " · в дневнике" : "") + "</small></div></div>").join("") : A.empty("В этом месяце ещё ничего не готовили по рецептам")) + "</div></div>";
      }
      return h;
    },
    bind(el, r) {
      const P = A.route().params, sec = r.args[0] || "catalog";
      const q = (o) => { const x = Object.assign({}, P, o); A.go("recipes/" + sec + "?" + Object.keys(x).filter((k) => x[k] !== "" && x[k] != null).map((k) => k + "=" + encodeURIComponent(x[k])).join("&"), true); };
      const rq = el.querySelector("#rq"); if (rq) rq.addEventListener("change", () => q({ q: rq.value.trim() }));
      const rc = sec === "r" ? A.recipe(r.args[1]) : null;
      A.bind(el, {
        sec(b) { A.go("recipes/" + b.dataset.v, true); },
        cat(b) { q({ c: b.dataset.v }); }, tag(b) { q({ t: P.t === b.dataset.v ? "" : b.dataset.v }); }, sort(b) { q({ s: b.dataset.v }); }, favf() { q({ f: P.f === "1" ? "" : "1" }); },
        fav() { const f = A.db().favs; if (f[rc.id]) delete f[rc.id]; else f[rc.id] = 1; A.save(); A.refresh(); },
        sv(b) { const s = Math.max(1, +(P.s || rc.servings || 1) + +b.dataset.v); A.go("recipes/r/" + rc.id + "?s=" + s, true); },
        editR() { A.edit.recipe(rc); },
        cooked() {
          A.formSheet("Приготовила: " + rc.name, [{ k: "date", label: "Дата", type: "date" }, { k: "servings", label: "Порций", type: "number" }, { k: "diary", label: "Дневник", type: "check", text: "Добавить мою порцию в дневник питания" }, { k: "type", label: "Приём пищи", type: "chips", opts: A.MEALS }], { date: today(), servings: +(P.s || rc.servings), diary: true, type: new Date().getHours() < 11 ? "breakfast" : new Date().getHours() < 16 ? "lunch" : "dinner" }, (o) => {
            const snap = A.recipeMacros(rc);
            const e = { date: o.date, recipeId: rc.id, name: rc.name, servings: o.servings || 1, snap };
            if (o.diary) e.mealId = A.addRecipeToDiary(rc, o.date, o.type, 1).id;
            A.upsert("cookLog", e); A.toast("Записано в историю готовки"); A.refresh();
          });
        },
        toDiary() { A.formSheet("В дневник: " + rc.name, [{ k: "date", label: "Дата", type: "date" }, { k: "type", label: "Приём пищи", type: "chips", opts: A.MEALS }, { k: "servings", label: "Порций", type: "number", step: 0.5 }], { date: today(), type: "lunch", servings: 1 }, (o) => { A.addRecipeToDiary(rc, o.date, o.type, o.servings); A.toast("Добавлено в дневник"); }); },
        toMenu() { A.formSheet("В меню: " + rc.name, [{ k: "date", label: "Дата", type: "date" }, { k: "slot", label: "Приём пищи", type: "chips", opts: A.MEALS }, { k: "servings", label: "Порций", type: "number", step: 0.5 }], { date: today(), slot: "lunch", servings: 1 }, (o) => { menuDay(o.date).push({ id: A.uid(), slot: o.slot, recipeId: rc.id, servings: o.servings || 1, status: "plan" }); A.save(); A.toast("Добавлено в меню"); }); },
        recur() { A.edit.menuRecur({ recipeId: rc.id }); },
        wnav(b) { q({ w: addDays(P.w || A.weekStart(today()), +b.dataset.v) }); },
        addM(b) { A.addToMenu(b.dataset.d, b.dataset.s); },
        mUp(b) { const m = menuDay(b.dataset.d), i = +b.dataset.i; const slot = m[i].slot; let j = i - 1; while (j >= 0 && m[j].slot !== slot) j--; if (j >= 0) { [m[j], m[i]] = [m[i], m[j]]; A.save(); A.refresh(); } },
        mEat(b) { const x = menuDay(b.dataset.d).find((k) => k.id === b.dataset.id); setStatus(b.dataset.d, x, x.status === "eaten" ? "plan" : "eaten"); A.toast(x.status === "eaten" ? "Отмечено: съедено, добавлено в дневник" : "Снова запланировано"); A.refresh(); },
        mItem(b) {
          const d = b.dataset.d, m = menuDay(d), x = m.find((k) => k.id === b.dataset.id), rcp = A.recipe(x.recipeId);
          A.formSheet(rcp ? rcp.name : x.name, [{ k: "status", label: "Статус", type: "chips", opts: [["plan", "запланировано"], ["eaten", "съедено"], ["skip", "пропущено"]] }, { k: "servings", label: "Порций", type: "number", step: 0.5 }, { k: "slot", label: "Приём пищи", type: "chips", opts: A.MEALS }], x, (o) => {
            x.servings = o.servings || 1; x.slot = o.slot; setStatus(d, x, o.status); A.refresh();
          }, { onDelete: () => { if (x.mealId) A.remove("meals", x.mealId); if (x.recurId) { const rr = A.byId("menuRecur", x.recurId); if (rr) (rr.skip = rr.skip || []).push(d); } m.splice(m.indexOf(x), 1); A.save(); A.refresh(); }, post: rcp ? '<a class="btn sm" href="#/recipes/r/' + rcp.id + '">Открыть рецепт</a>' : "" });
        },
        saveDay(b) { A.formSheet("Шаблон дня", [{ k: "name", label: "Название", req: true, full: true }], { name: "Мой день " + fmtShort(b.dataset.d) }, (o) => { A.upsert("menuPresets", { name: o.name, kind: "day", days: [menuDay(b.dataset.d).map((x) => ({ slot: x.slot, recipeId: x.recipeId, name: x.name, servings: x.servings }))] }); A.toast("Шаблон сохранён"); }); },
        saveWeek() { const ws = P.w || A.weekStart(today()); A.formSheet("Шаблон недели", [{ k: "name", label: "Название", req: true, full: true }], { name: "Неделя " + fmtShort(ws) }, (o) => { A.upsert("menuPresets", { name: o.name, kind: "week", days: A.range(ws, addDays(ws, 6)).map((d) => menuDay(d).map((x) => ({ slot: x.slot, recipeId: x.recipeId, name: x.name, servings: x.servings }))) }); A.toast("Шаблон сохранён"); }); },
        applyP(b) { A.applyPreset(null, b.dataset.d); },
        pApply(b) { A.applyPreset(A.byId("menuPresets", b.dataset.id)); },
        pEdit(b) { const p = A.byId("menuPresets", b.dataset.id); A.formSheet("Шаблон", [{ k: "name", label: "Название", full: true }], p, (o) => { p.name = o.name; A.save(); A.refresh(); }, { onDelete: () => { A.remove("menuPresets", p.id); A.refresh(); }, pre: '<div class="small">' + p.days.map((d, i) => (p.kind === "week" ? A.DOW[i] + ": " : "") + d.map((x) => { const rr = A.recipe(x.recipeId); return rr ? rr.name : x.name; }).join(", ")).join("<br>") + "</div>" }); },
        recEdit(b) { A.edit.menuRecur(A.byId("menuRecur", b.dataset.id)); },
        shopBuild() { const ws = P.w || A.weekStart(today()); A.buildShopping(ws, addDays(ws, 6)); A.toast("Список покупок собран"); A.go("recipes/shop"); },
        shopDedupe() { A.toast("Объединено дублей: " + dedupeShopping()); A.refresh(); },
        shopExport() { const t = shoppingText() || "Список пуст"; if (A.native) A.native.shareText("Список покупок", t); else A.download("pokupki-" + today() + ".txt", "text/plain", t); },
        shopClear() { A.db().shopping = A.col("shopping").filter((x) => !x.bought); A.save(); A.refresh(); },
        shopAdd() { const v = el.querySelector("#sq").value.trim(); if (!v) return; const [name, qty] = v.split(",").map((x) => x.trim()); A.col("shopping").push({ id: A.uid(), name, qty: qty || "", cat: el.querySelector("#sc").value, bought: false, manual: true }); A.save(); A.refresh(); },
        shopTog(b) { const x = A.byId("shopping", b.dataset.id); x.bought = !x.bought; A.save(); A.refresh(); },
        shopDel(b) { A.remove("shopping", b.dataset.id); A.refresh(); },
        hnav(b) { let [y, m] = (P.m || today().slice(0, 7)).split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } q({ m: y + "-" + A.pad(m) }); },
        hItem(b) { const x = A.byId("cookLog", b.dataset.id); A.confirm("Удалить запись «" + x.name + "» из истории?" + (x.mealId ? " Запись в дневнике питания тоже будет удалена." : ""), () => { if (x.mealId) A.remove("meals", x.mealId); A.remove("cookLog", x.id); A.refresh(); }, "Удалить", true); }
      });
    }
  });

  A.applyPreset = (p, date) => {
    const run = (pp, d) => {
      const days = pp.kind === "week" ? A.range(A.weekStart(d), addDays(A.weekStart(d), 6)) : [d];
      days.forEach((dd, i) => (pp.days[pp.kind === "week" ? i : 0] || []).forEach((x) => menuDay(dd).push(Object.assign({ id: A.uid(), status: "plan" }, x))));
      A.save(); A.toast("Шаблон применён"); A.refresh();
    };
    if (p && date) return run(p, date);
    const ps = A.col("menuPresets"); if (!ps.length) return A.toast("Шаблонов пока нет");
    A.formSheet("Применить шаблон", [{ k: "pid", label: "Шаблон", type: "select", opts: ps.map((x) => [x.id, x.name + (x.kind === "week" ? " (неделя)" : " (день)")]), full: true }, { k: "date", label: "К дате (для недели — к неделе этой даты)", type: "date", full: true }], { pid: p ? p.id : ps[0].id, date: date || addDays(today(), 1) }, (o) => run(A.byId("menuPresets", o.pid), o.date));
  };
  A.edit.menuRecur = (rr) => {
    const isNew = !rr || !rr.id;
    rr = Object.assign({ slot: "breakfast", days: [1, 2, 3, 4, 5], start: today(), end: "", servings: 1, active: true }, rr || {});
    A.formSheet("Повторяющееся блюдо", [
      { k: "recipeId", label: "Рецепт", type: "select", opts: () => A.allRecipes().map((x) => [x.id, x.name]), full: true },
      { k: "slot", label: "Приём пищи", type: "chips", opts: A.MEALS }, { k: "days", label: "Дни недели", type: "multi", numeric: true, opts: [[1, "Пн"], [2, "Вт"], [3, "Ср"], [4, "Чт"], [5, "Пт"], [6, "Сб"], [7, "Вс"]] },
      { k: "start", label: "Дата начала", type: "date" }, { k: "end", label: "Дата окончания", type: "date" },
      { k: "servings", label: "Порций", type: "number", step: 0.5 }, { k: "active", label: "Статус", type: "check", text: "Активно" }
    ], rr, (o) => { Object.assign(rr, o); A.upsert("menuRecur", rr); A.toast("Блюдо будет добавляться в меню автоматически"); A.refresh(); }, isNew ? {} : { onDelete: () => { Object.values(A.db().menu).forEach((m) => { for (let i = m.length - 1; i >= 0; i--) if (m[i].recurId === rr.id && m[i].status === "plan") m.splice(i, 1); }); A.remove("menuRecur", rr.id); A.refresh(); } });
  };
})();
