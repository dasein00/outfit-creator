/* Рецепты и меню по образцу приложения DASEIN: 186 блюд с фото, калькулятор граммовки, замены ингредиентов,
   КБЖУ на порцию / 100 г / весь рецепт, меню на день, неделю и месяц, автосоставление меню, шаблоны,
   повторяющиеся блюда, список покупок с единицами и история готовки. Связано с дневником питания. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtShort, fmtDate, sum, round } = A;
  A.edit = A.edit || {};

  /* ---------- справочники (перенос logic/Cooking.kt) ---------- */
  const CATS = ["Завтраки", "Обеды", "Ужины", "Перекусы", "Кухни мира", "Супы", "Салаты", "Гарниры", "Десерты", "Напитки", "Выпечка", "Заготовки"];
  const WORLD = "Кухни мира";
  const SHOP_CATS = ["Овощи", "Фрукты", "Мясо", "Рыба", "Молочные продукты", "Крупы", "Бакалея", "Заморозка", "Другое"];
  const UNITS = ["г", "кг", "мл", "л", "шт", "ст.л.", "ч.л.", "стакан", "щепотка", "по вкусу"];
  const DIFF = ["Легко", "Средне", "Сложно"];
  const SLOTS = A.MEALS;
  const SHARE = { breakfast: 0.25, brunch: 0.1, lunch: 0.35, afternoon: 0.1, dinner: 0.25, snack: 0.1 };
  const SWAPS = {
    "Куриное филе": ["Филе индейки", "Треска", "Тофу"], "Филе индейки": ["Куриное филе", "Треска", "Тофу"], "Говядина постная": ["Филе индейки", "Куриное филе", "Чечевица красная"],
    "Рис бурый": ["Гречка", "Булгур", "Картофель"], "Рис белый": ["Рис бурый", "Гречка", "Булгур"], "Гречка": ["Булгур", "Киноа", "Рис бурый"], "Булгур": ["Гречка", "Киноа", "Рис бурый"],
    "Паста цельнозерновая": ["Гречневая лапша", "Булгур", "Кабачок"], "Сметана 10%": ["Греческий йогурт", "Кефир 1%"], "Сливки 10%": ["Молоко 1,5%", "Греческий йогурт"],
    "Творог 5%": ["Творог 2%", "Греческий йогурт", "Тофу"], "Сахар": ["Мёд", "Эритрит", "Банан"], "Мука пшеничная": ["Мука цельнозерновая", "Овсяная мука", "Мука рисовая"],
    "Картофель": ["Батат", "Цветная капуста", "Гречка"], "Лосось": ["Форель", "Скумбрия", "Треска"], "Треска": ["Минтай", "Хек", "Куриное филе"],
    "Майонез": ["Греческий йогурт", "Сметана 10%"], "Хлеб цельнозерновой": ["Хлебцы цельнозерновые", "Лаваш тонкий"], "Молоко 1,5%": ["Миндальное молоко", "Кефир 1%"], "Яйцо куриное": ["Яичный белок", "Тофу"]
  };
  const cuisineOf = (r) => { const t = (r.tags || []).find((x) => / кухня$/.test(x)); return t ? t.replace(/ кухня$/, "").replace(/^./, (c) => c.toUpperCase()) : null; };
  const shopCat = (c) => ({ "Мясо и рыба": "Мясо", "Молочное": "Молочные продукты", "Крупы и бакалея": "Бакалея" }[c] || c || "Другое");

  // Продукт из базы DASEIN или из своей базы продуктов.
  A.product = (name) => {
    const n = (name || "").toLowerCase();
    const d = (window.DASEIN_PRODUCTS || []).find((p) => p.name.toLowerCase() === n);
    if (d) return d;
    const f = A.col("foods").find((p) => p.name.toLowerCase() === n);
    return f ? { name: f.name, cat: "Другое", kcal: f.kcal, p: f.p, f: f.f, c: f.c, fib: f.fib || 0, gpu: 0 } : null;
  };
  const gramsOf = (amount, unit, gpu) => ({ "г": amount, "мл": amount, "кг": amount * 1000, "л": amount * 1000, "шт": amount * (gpu > 0 ? gpu : 100), "ст.л.": amount * 15, "ч.л.": amount * 5, "стакан": amount * 200, "щепотка": amount }[unit] ?? 0);
  // Приводит ингредиент к полному виду (у старых рецептов есть только граммы).
  const norm = (i) => Object.assign({ amount: i.amount != null ? i.amount : i.g, unit: i.unit || "г", g: i.g != null ? i.g : i.amount, fib: 0, cat: "Другое" }, i, { cat: shopCat(i.cat) });
  const ingrOf = (r) => (r.ingr || []).map(norm);
  const macrosOf = (list) => list.reduce((t, i) => { const k = (+i.g || 0) / 100; t.kcal += (+i.kcal || 0) * k; t.p += (+i.p || 0) * k; t.f += (+i.f || 0) * k; t.c += (+i.c || 0) * k; t.fib += (+i.fib || 0) * k; t.g += +i.g || 0; return t; }, { kcal: 0, p: 0, f: 0, c: 0, fib: 0, g: 0 });
  // КБЖУ на одну порцию.
  A.recipeMacros = (r) => { const t = macrosOf(ingrOf(r)), n = +r.servings || 1; return { kcal: Math.round(t.kcal / n), p: round(t.p / n, 1), f: round(t.f / n, 1), c: round(t.c / n, 1), fib: round(t.fib / n, 1), g: Math.round(t.g / n) }; };
  const amountFmt = (v) => { if (!v) return "0"; const r = v >= 10 ? Math.round(v) : Math.round(v * 100) / 100; return String(r).replace(".", ","); };
  const gFmt = (v) => (v < 10 ? amountFmt(Math.round(v * 10) / 10) : String(Math.round(v)));
  // Сколько положить — удобно для кухонных весов.
  const weighLabel = (i) => {
    if (i.unit === "по вкусу") return "по вкусу";
    const g = +i.g || 0;
    switch (i.unit) {
      case "г": return gFmt(i.amount) + " г";
      case "мл": return gFmt(i.amount) + " мл";
      case "кг": return i.amount < 1 ? gFmt(i.amount * 1000) + " г" : amountFmt(i.amount) + " кг";
      case "л": return i.amount < 1 ? gFmt(i.amount * 1000) + " мл" : amountFmt(i.amount) + " л";
      case "шт": return amountFmt(i.amount) + " шт" + (g > 0 ? " (≈ " + gFmt(g) + " г)" : "");
      default: return g > 0 && g < 5 ? gFmt(g) + " г" : amountFmt(i.amount) + " " + i.unit + (g > 0 ? " (" + gFmt(g) + " г)" : "");
    }
  };
  const scale = (list, k) => list.map((i) => Object.assign({}, i, { amount: i.unit === "по вкусу" ? i.amount : i.amount * k, g: (+i.g || 0) * k }));
  // Основной продукт — то, что взвешивают первым: мясо или рыба, затем заморозка, затем молочное, иначе самый тяжёлый.
  const mainIndex = (list) => {
    const wd = list.map((x, i) => [x, i]).filter(([x]) => x.unit !== "по вкусу" && x.g > 0);
    if (!wd.length) return -1;
    for (const cs of [["Мясо", "Рыба"], ["Заморозка"], ["Молочные продукты"]]) { const c = wd.filter(([x]) => cs.includes(x.cat)).sort((a, b) => b[0].g - a[0].g)[0]; if (c) return c[1]; }
    return wd.sort((a, b) => b[0].g - a[0].g)[0][1];
  };
  const factorFor = (base, have) => (have <= 0 ? null : base.unit === "шт" && base.amount > 0 ? have / base.amount : base.g > 0 ? have / base.g : base.amount > 0 ? have / base.amount : null);
  // Покупки: одинаковые продукты объединяются, количество округляется до того, что можно купить.
  const baseUnit = (u) => (u === "кг" ? ["г", 1000] : u === "л" ? ["мл", 1000] : [u, 1]);
  const buyable = (a, u) => (u === "кг" || u === "л" ? Math.ceil(a * 10 - 1e-9) / 10 : ["г", "мл", "шт"].includes(u) ? Math.ceil(a - 1e-9) : Math.round(a * 100) / 100);
  const mergeShopping = (items) => {
    const g = {};
    items.filter((i) => i.unit !== "по вкусу" && i.amount > 0).forEach((i) => { const [u, k] = baseUnit(i.unit); const key = i.name.trim().toLowerCase().replace(/ё/g, "е") + "|" + u; g[key] = g[key] || { name: i.name.trim(), sum: 0, u, cat: i.cat }; g[key].sum += i.amount * k; });
    return Object.values(g).map((x) => { let unit = x.u, amount = x.sum; if (unit === "г" && amount >= 1000) { unit = "кг"; amount /= 1000; } else if (unit === "мл" && amount >= 1000) { unit = "л"; amount /= 1000; } return { id: A.uid(), name: x.name, amount: buyable(amount, unit), unit, cat: x.cat || "Другое", bought: false }; })
      .sort((a, b) => (SHOP_CATS.indexOf(a.cat) + 1 || 99) - (SHOP_CATS.indexOf(b.cat) + 1 || 99) || a.name.localeCompare(b.name));
  };
  // Подбор меню: по рецепту на приём пищи, калории близко к цели, белка не меньше минимума.
  const autofill = (cands, p, seed, avoid) => {
    const words = (s) => s.split(",").map((x) => x.trim().toLowerCase()).filter(Boolean);
    const ex = words(p.exclude || ""), pref = words(p.prefer || "");
    const has = (c, ws) => ws.some((w) => c.ingr.some((n) => n.includes(w)));
    const usable = cands.filter((c) => (!p.maxMin || c.min <= p.maxMin) && !has(c, ex) && (!p.cats.length || p.cats.includes(c.cat)) && c.m.kcal > 0);
    const meals = SLOTS.map((m) => m[0]).filter((m) => p.meals.includes(m));
    const shareSum = sum(meals.map((m) => SHARE[m])) || 1, ppk = p.kcal ? p.minProt / p.kcal : 0;
    const used = new Set(), out = [];
    meals.forEach((meal, idx) => {
      const tgt = (p.kcal * SHARE[meal]) / shareSum;
      let pool = usable.filter((c) => c.meals.includes(meal)); if (!pool.length) pool = usable; if (!pool.length) return;
      const best = pool.map((c) => {
        const sv = Math.min(4, Math.max(1, Math.round((tgt / c.m.kcal) * 2))) / 2;
        let sc = Math.abs(c.m.kcal * sv - tgt) / Math.max(tgt, 1);
        const need = tgt * ppk; if (need > 0) sc += (Math.max(0, need - c.m.p * sv) / need) * 0.8;
        if (has(c, pref)) sc -= 0.15; if (used.has(c.id)) sc += 0.6; if (avoid.has(c.id)) sc += 0.25;
        sc += ((((c.h * 31 + seed * 17 + idx * 7) % 13) + 13) % 13 / 13) * 0.12;
        return [c, sv, sc];
      }).sort((a, b) => a[2] - b[2])[0];
      used.add(best[0].id); out.push([meal, best[0].id, best[1]]);
    });
    return out;
  };

  /* ---------- рецепты ---------- */
  const archived = () => (A.db().recArchive = A.db().recArchive || {});
  A.allRecipes = (withArchive) => (window.DASEIN_RECIPES || []).concat(A.col("recipes")).filter((r) => withArchive || !archived()[r.id]);
  A.recipe = (id) => (window.DASEIN_RECIPES || []).find((r) => r.id === id) || A.col("recipes").find((r) => r.id === id) || (window.RECIPES || []).find((r) => r.id === id) || null;
  const recImg = (r, cls, style) => r.photo ? '<img class="' + (cls || "thumb") + '" data-photo="' + r.photo + '" alt="" style="' + (style || "") + '">' : r.img ? '<div class="' + (cls || "thumb") + '" style="display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,var(--peach2),var(--accent2));' + (style || "") + '"><img src="' + r.img + '" alt="" loading="lazy" style="height:82%;max-width:82%;object-fit:contain;image-rendering:auto"></div>' : '<div class="' + (cls || "thumb") + '" style="display:flex;align-items:center;justify-content:center;font-size:40px;color:var(--ink2);background:linear-gradient(135deg,var(--peach2),var(--accent2));' + (style || "") + '">{{i:cooking-pot}}</div>';
  A.recipeThumb = recImg;
  const hash = (s) => { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h); };

  A.addRecipeToDiary = (r, d, type, servings, factor) => {
    const m = A.recipeMacros(r), n = +servings || 1;
    return A.upsert("meals", { date: d, type, name: r.name + (n !== 1 ? " × " + amountFmt(n) : ""), recipeId: r.id, servings: n, g: m.g * n, kcal: Math.round(m.kcal * n), p: round(m.p * n, 1), f: round(m.f * n, 1), c: round(m.c * n, 1), fib: round(m.fib * n, 1) });
  };
  A.addRecipeToShopping = (r, k) => {
    const merged = mergeShopping(A.col("shopping").filter((x) => !x.bought && x.unit).map((x) => ({ name: x.name, amount: x.amount, unit: x.unit, cat: x.cat })).concat(scale(ingrOf(r), k)));
    A.db().shopping = A.col("shopping").filter((x) => x.bought || !x.unit).concat(merged); A.save();
  };

  /* ---------- редактор рецепта ---------- */
  A.edit.recipe = (r0, opts = {}) => {
    const isNew = !r0 || r0.builtin || opts.copy;
    const r = r0 ? JSON.parse(JSON.stringify(r0)) : { name: "", cat: "Обеды", tags: [], meals: ["lunch"], prep: 10, cook: 20, diff: 1, servings: 2, ingr: [], steps: [], tips: "", notes: "" };
    if (isNew && r0) { delete r.builtin; r.id = null; r.name = r.name + (opts.suffix || (r0.builtin ? " (моя версия)" : " (копия)")); if (r0.img && !r.photo) r.img = r0.img; }
    const fields = [
      { k: "name", label: "Название", req: true, full: true }, { k: "cat", label: "Категория", type: "select", opts: CATS },
      { k: "diff", label: "Сложность", type: "chips", numeric: true, opts: [[1, DIFF[0]], [2, DIFF[1]], [3, DIFF[2]]] },
      { k: "meals", label: "Подходит для", type: "multi", opts: SLOTS },
      { k: "prep", label: "Подготовка, мин", type: "number" }, { k: "cook", label: "Готовка, мин", type: "number" }, { k: "servings", label: "Порций", type: "number", min: 1 },
      { k: "tagsStr", label: "Теги (через запятую)", full: true, ph: "быстро, высокобелковые, грузинская кухня" },
      { k: "stepsStr", label: "Шаги (каждый с новой строки)", type: "textarea", rows: 6 },
      { k: "tips", label: "Советы и трюки", type: "textarea", rows: 2 }, { k: "notes", label: "Заметки", type: "textarea", rows: 2 }
    ];
    const ingr = ingrOf(r);
    const ingHtml = () => '<div class="fsec">Ингредиенты · на порцию: <b id="rmac"></b></div><div class="list">' + ingr.map((i, k) => '<div class="row" style="gap:6px;margin:4px 0;flex-wrap:wrap"><div class="grow small" style="min-width:120px"><b>' + esc(i.name) + '</b><br><span class="muted">' + Math.round(i.kcal) + " ккал/100 г · " + gFmt(i.g) + ' г</span></div><input type="number" inputmode="decimal" data-am="' + k + '" value="' + esc(i.amount) + '" style="width:74px"><select data-un="' + k + '" style="width:92px">' + UNITS.map((u) => "<option" + (u === i.unit ? " selected" : "") + ">" + u + "</option>").join("") + '</select><button type="button" class="icon-btn" data-del="' + k + '" aria-label="Убрать">✕</button></div>').join("") + '</div><div class="fld full" style="margin-top:6px"><input id="iq" placeholder="Добавить ингредиент: поиск в базе продуктов" autocomplete="off"><div id="il" class="list" style="max-height:200px;overflow:auto"></div></div>';
    A.formSheet(isNew ? "Новый рецепт" : "Рецепт", fields, Object.assign({}, r, { tagsStr: (r.tags || []).join(", "), stepsStr: (r.steps || []).join("\n") }), (o) => {
      o.steps = (o.stepsStr || "").split("\n").map((x) => x.trim()).filter(Boolean); delete o.stepsStr;
      o.tags = (o.tagsStr || "").split(",").map((x) => x.trim()).filter(Boolean); delete o.tagsStr;
      Object.assign(r, o); r.ingr = ingr; r.servings = Math.max(1, +r.servings || 1); r.diff = +r.diff || 1;
      A.upsert("recipes", r); A.toast("Рецепт сохранён"); A.go("recipes/r/" + r.id);
    }, {
      pre: '<div id="rph" style="margin-bottom:10px"></div>', post: '<div id="ingBox"></div>',
      onDelete: !isNew ? () => { A.confirm("Удалить рецепт?", () => { if (r.photo) A.photos.del(r.photo); A.remove("recipes", r.id); A.go("recipes", true); }, "Удалить", true); } : null,
      after(wr) {
        const ph = wr.querySelector("#rph");
        const drawPh = () => { ph.innerHTML = (r.photo || r.img ? recImg(r) : "") + '<button type="button" class="btn sm" id="phBtn" style="margin-top:6px">{{i:camera}} ' + (r.photo ? "Заменить фото" : "Добавить своё фото") + "</button>"; A.hydratePhotos(ph); ph.querySelector("#phBtn").onclick = async () => { const url = await A.pickPhoto(4 / 3, 900); if (!url) return; const id = A.uid(); await A.photos.put(id, url); r.photo = id; drawPh(); }; };
        drawPh();
        const box = wr.querySelector("#ingBox");
        const mac = () => { const m = macrosOf(ingr), n = +wr.querySelector('[name="servings"]').value || 1; const e = box.querySelector("#rmac"); if (e) e.textContent = Math.round(m.kcal / n) + " ккал · Б " + round(m.p / n, 1) + " · Ж " + round(m.f / n, 1) + " · У " + round(m.c / n, 1); };
        const regram = (i) => { const pr = A.product(i.name); i.g = gramsOf(+i.amount || 0, i.unit, pr ? pr.gpu : 0); };
        const draw = () => { box.innerHTML = ingHtml(); mac(); wire(); };
        const wire = () => {
          const q = box.querySelector("#iq"), l = box.querySelector("#il");
          const src = () => (window.DASEIN_PRODUCTS || []).concat(A.col("foods").map((f) => ({ name: f.name, cat: "Другое", kcal: f.kcal, p: f.p, f: f.f, c: f.c, fib: f.fib || 0, gpu: 0 })));
          q.addEventListener("input", () => {
            const s = q.value.trim().toLowerCase(); if (!s) { l.innerHTML = ""; return; }
            const seen = {}; const res = src().filter((x) => x.name.toLowerCase().includes(s) && !seen[x.name] && (seen[x.name] = 1)).slice(0, 15);
            l.innerHTML = res.map((x, k) => '<div class="item" data-k="' + k + '" style="min-height:38px;padding:4px 2px"><div class="tx"><b>' + esc(x.name) + "</b><small>" + x.kcal + " ккал · Б " + x.p + " Ж " + x.f + " У " + x.c + " · " + esc(x.cat) + "</small></div></div>").join("") + '<div class="item" data-new="1" style="min-height:38px"><div class="tx"><b>+ «' + esc(q.value.trim()) + '» вручную</b><small>без КБЖУ</small></div></div>';
            l._res = res;
          });
          l.addEventListener("click", (e) => { const it = e.target.closest("[data-k],[data-new]"); if (!it) return; const x = it.dataset.new ? { name: q.value.trim(), kcal: 0, p: 0, f: 0, c: 0, fib: 0, cat: "Другое" } : l._res[+it.dataset.k]; const unit = x.gpu > 0 ? "шт" : "г"; const ni = { name: x.name, amount: unit === "шт" ? 1 : 100, unit, g: 0, kcal: x.kcal, p: x.p, f: x.f, c: x.c, fib: x.fib || 0, cat: shopCat(x.cat) }; regram(ni); ingr.push(ni); draw(); });
          box.querySelectorAll("[data-am]").forEach((inp) => inp.addEventListener("input", () => { const i = ingr[+inp.dataset.am]; i.amount = A.num(inp.value, 0); regram(i); mac(); }));
          box.querySelectorAll("[data-un]").forEach((sel) => sel.addEventListener("change", () => { const i = ingr[+sel.dataset.un]; i.unit = sel.value; regram(i); draw(); }));
          box.querySelectorAll("[data-del]").forEach((b) => (b.onclick = () => { ingr.splice(+b.dataset.del, 1); draw(); }));
        };
        draw();
        wr.querySelector('[name="servings"]').addEventListener("input", mac);
      }
    });
  };

  /* ---------- меню ---------- */
  const menuDay = (d) => (A.db().menu[d] = A.db().menu[d] || []);
  A.menuDay = menuDay;
  A.ensureRecurring = (dates) => {
    let ch = false;
    A.col("menuRecur").forEach((rr) => dates.forEach((d) => {
      if (!rr.active || d < rr.start || (rr.end && d > rr.end) || !(rr.days || []).includes(A.dow(d)) || (rr.skip || []).includes(d)) return;
      const m = menuDay(d); if (m.some((x) => x.recurId === rr.id)) return;
      m.push({ id: A.uid(), slot: rr.slot, recipeId: rr.recipeId, servings: rr.servings || 1, status: "plan", recurId: rr.id }); ch = true;
    }));
    if (ch) A.save();
  };
  const itemMacros = (x) => { const r = x.recipeId ? A.recipe(x.recipeId) : null; if (!r) return { kcal: +x.kcal || 0, p: +x.p || 0, f: +x.f || 0, c: +x.c || 0 }; const m = A.recipeMacros(r), n = +x.servings || 1; return { kcal: m.kcal * n, p: m.p * n, f: m.f * n, c: m.c * n }; };
  const dayPlan = (d) => menuDay(d).filter((x) => x.status !== "skip").reduce((t, x) => { const m = itemMacros(x); t.kcal += m.kcal; t.p += m.p; return t; }, { kcal: 0, p: 0 });
  const setStatus = (d, x, st) => {
    x.status = st;
    if (st === "eaten" && !x.mealId) { const r = A.recipe(x.recipeId); const meal = r ? A.addRecipeToDiary(r, d, x.slot, x.servings) : A.upsert("meals", { date: d, type: x.slot, name: x.name, g: 0, kcal: +x.kcal || 0, p: 0, f: 0, c: 0, fib: 0 }); x.mealId = meal.id; }
    if (st !== "eaten" && x.mealId) { A.remove("meals", x.mealId); x.mealId = null; }
    A.save();
  };
  const pickRecipe = (cb, mealHint) => {
    const w = A.sheet("Выберите блюдо", '<input id="rq" placeholder="Поиск: название, продукт, тег"><div class="small muted" style="margin:6px 0">' + (mealHint ? "Для: " + esc((SLOTS.find((m) => m[0] === mealHint) || [0, ""])[1].toLowerCase()) : "") + '</div><div id="rl" class="list"></div><div class="fld full" style="margin-top:10px"><label>Или просто название блюда</label><div class="inline-add"><input id="rn" placeholder="Например: гречка с котлетой"><button class="btn" id="rnb">+</button></div></div>');
    const draw = () => {
      const s = w.querySelector("#rq").value.trim().toLowerCase();
      w.querySelector("#rl").innerHTML = A.allRecipes().filter((r) => (!s ? !mealHint || (r.meals || []).includes(mealHint) : (r.name + " " + (r.tags || []).join(" ") + " " + ingrOf(r).map((i) => i.name).join(" ")).toLowerCase().includes(s))).sort((a, b) => (A.db().favs[b.id] ? 1 : 0) - (A.db().favs[a.id] ? 1 : 0)).slice(0, 60)
        .map((r) => { const m = A.recipeMacros(r); return '<div class="item" data-id="' + r.id + '">' + recImg(r, "", "width:48px;height:48px;border-radius:10px;object-fit:cover;flex:0 0 auto") + '<div class="tx"><b>' + (A.db().favs[r.id] ? "★ " : "") + esc(r.name) + "</b><small>" + m.kcal + " ккал · Б " + Math.round(m.p) + " · " + esc(r.cat) + "</small></div></div>"; }).join("") || A.empty("Ничего не нашлось");
      A.hydratePhotos(w);
    };
    draw();
    w.querySelector("#rq").addEventListener("input", draw);
    w.querySelector("#rl").addEventListener("click", (e) => { const it = e.target.closest("[data-id]"); if (!it) return; A.closeSheet(); cb({ recipeId: it.dataset.id }); });
    w.querySelector("#rnb").onclick = () => { const v = w.querySelector("#rn").value.trim(); if (!v) return; A.closeSheet(); cb({ name: v }); };
  };
  A.addToMenu = (d, slot, recipeId) => { const go = (o) => { menuDay(d).push(Object.assign({ id: A.uid(), slot, servings: 1, status: "plan" }, o)); A.save(); A.toast("Добавлено в меню"); A.refresh(); }; if (recipeId) go({ recipeId }); else pickRecipe(go, slot); };

  // Составить меню автоматически.
  A.autoMenu = (from) => {
    const p = A.db().profile, T = A.bodyTargets ? A.bodyTargets() : null;
    A.formSheet("Составить меню", [
      { k: "period", label: "Период", type: "chips", opts: [["day", "День"], ["week", "Неделя"], ["range", "Несколько дней"]] },
      { k: "from", label: "С", type: "date" }, { k: "to", label: "По (для «несколько дней»)", type: "date" },
      { k: "meals", label: "Приёмы пищи", type: "multi", opts: SLOTS },
      { k: "kcal", label: "Калорий в день", type: "number", hint: "По умолчанию — цель из профиля" + (T ? " (расчёт по телу: " + T.kcal + ")" : "") },
      { k: "minProt", label: "Белка не меньше, г в день", type: "number" }, { k: "maxMin", label: "Время готовки до, мин (0 — любое)", type: "number" },
      { k: "cats", label: "Категории (пусто — все)", type: "multi", opts: CATS },
      { k: "exclude", label: "Исключить продукты (через запятую)", full: true, ph: "грибы, рыба" }, { k: "prefer", label: "Предпочитаемые продукты (через запятую)", full: true, ph: "курица, творог" },
      { k: "replace", label: "Уже запланированное", type: "check", text: "Заменить уже запланированное" }
    ], { period: "week", from: from || today(), to: addDays(from || today(), 2), meals: ["breakfast", "lunch", "dinner", "snack"], kcal: p.kcal, minProt: p.prot, maxMin: 0, cats: [], replace: false }, (o) => {
      const start = o.period === "week" ? A.weekStart(o.from) : o.from;
      const end = o.period === "day" ? o.from : o.period === "week" ? addDays(start, 6) : o.to < o.from ? o.from : o.to;
      const cands = A.allRecipes().map((r) => ({ id: r.id, h: hash(r.id), meals: r.meals && r.meals.length ? r.meals : ["lunch", "dinner"], m: A.recipeMacros(r), min: (+r.prep || 0) + (+r.cook || 0), cat: r.cat, ingr: ingrOf(r).map((i) => i.name.toLowerCase()) }));
      let n = 0, prev = new Set();
      A.range(start, end).forEach((d, i) => {
        const m = menuDay(d);
        if (o.replace) for (let k = m.length - 1; k >= 0; k--) if (m[k].status === "plan" && !m[k].recurId) m.splice(k, 1);
        const res = autofill(cands, { kcal: o.kcal || p.kcal, meals: o.meals.length ? o.meals : ["breakfast", "lunch", "dinner"], minProt: o.minProt || 0, maxMin: o.maxMin || 0, cats: o.cats || [], exclude: o.exclude || "", prefer: o.prefer || "" }, i + hash(d) % 97, prev);
        prev = new Set(res.map((x) => x[1]));
        res.forEach(([slot, rid, sv]) => { if (!o.replace && m.some((x) => x.slot === slot)) return; m.push({ id: A.uid(), slot, recipeId: rid, servings: sv, status: "plan" }); n++; });
      });
      A.save(); A.toast(n ? "Добавлено блюд: " + n : "Не нашлось подходящих блюд — ослабьте фильтры"); A.go("recipes/menu?w=" + A.weekStart(start), true);
    });
  };

  /* ---------- список покупок ---------- */
  A.buildShopping = (from, to) => {
    const items = [];
    A.range(from, to).forEach((d) => (A.db().menu[d] || []).forEach((x) => { if (x.status !== "plan") return; const r = A.recipe(x.recipeId); if (!r) return; items.push(...scale(ingrOf(r), (+x.servings || 1) / (+r.servings || 1))); }));
    A.db().shopping = A.col("shopping").filter((x) => x.manual).concat(mergeShopping(items)); A.save();
    return A.col("shopping").length;
  };
  const shopQty = (x) => (x.unit ? amountFmt(x.amount) + " " + x.unit : x.g ? x.g + " г" : "") + (x.qty ? " " + x.qty : "");
  const shoppingText = () => SHOP_CATS.map((c) => { const it = A.col("shopping").filter((x) => shopCat(x.cat) === c && !x.bought); return it.length ? c + ":\n" + it.map((x) => "☐ " + x.name + (shopQty(x) ? " — " + shopQty(x) : "")).join("\n") : ""; }).filter(Boolean).join("\n\n");
  const dedupeShopping = () => {
    const before = A.col("shopping").length;
    const withUnit = A.col("shopping").filter((x) => x.unit && !x.bought), rest = A.col("shopping").filter((x) => !x.unit || x.bought);
    const seen = {}, rest2 = rest.filter((x) => { const k = x.name.toLowerCase() + x.bought; if (seen[k]) return false; seen[k] = 1; return true; });
    A.db().shopping = rest2.concat(mergeShopping(withUnit.map((x) => ({ name: x.name, amount: x.amount, unit: x.unit, cat: x.cat }))));
    A.save(); return before - A.col("shopping").length;
  };

  /* ---------- экраны ---------- */
  const TABS = [["catalog", "Рецепты"], ["menu", "Меню"], ["shop", "Покупки"], ["presets", "Шаблоны и повторы"], ["history", "История"]];
  const QUICK = [["fast", "Быстрые", (r) => (+r.prep || 0) + (+r.cook || 0) <= 20], ["prot", "Высокобелковые", (r) => A.recipeMacros(r).p >= 30], ["low", "Низкокалорийные", (r) => A.recipeMacros(r).kcal <= 500]];

  function detail(rc, P) {
    const baseList = ingrOf(rc);
    // Замены ингредиентов на время просмотра.
    const subs = A._subs && A._subs.id === rc.id ? A._subs.map : {};
    const list = baseList.map((i, k) => { const to = subs[k]; if (!to) return i; const p = A.product(to); return Object.assign({}, i, { name: to, kcal: p ? p.kcal : i.kcal, p: p ? p.p : i.p, f: p ? p.f : i.f, c: p ? p.c : i.c, fib: p ? p.fib : i.fib, cat: p ? shopCat(p.cat) : i.cat, swapped: true }); });
    const serv = +(P.s || rc.servings || 1), base = +rc.servings || 1;
    const mi = P.main != null ? +P.main : mainIndex(list);
    const have = +(P.have || 0);
    const factor = have > 0 && mi >= 0 ? factorFor(list[mi], have) : null;
    const k = factor != null ? factor : serv / base;
    const scaled = scale(list, k), servD = factor != null ? base * factor : serv;
    const tot = macrosOf(scaled), mode = P.n || "portion";
    const shown = mode === "100" ? (tot.g ? { kcal: (tot.kcal * 100) / tot.g, p: (tot.p * 100) / tot.g, f: (tot.f * 100) / tot.g, c: (tot.c * 100) / tot.g, fib: (tot.fib * 100) / tot.g } : tot) : mode === "all" ? tot : { kcal: tot.kcal / servD, p: tot.p / servD, f: tot.f / servD, c: tot.c / servD, fib: tot.fib / servD };
    const fav = A.db().favs[rc.id], arch = archived()[rc.id], cz = cuisineOf(rc);
    let h = recImg(rc, "thumb", "border-radius:16px") + '<div class="card" style="margin-top:12px"><div class="row"><h2 class="grow" style="font-size:21px">' + esc(rc.name) + '</h2><button class="icon-btn" data-a="fav" style="color:' + (fav ? "var(--gold)" : "var(--ink2)") + '" aria-label="Избранное">{{i:' + (fav ? "star" : "star-off") + "}}</button></div>" +
      '<div class="small muted">' + esc(rc.cat + (cz ? " · " + cz + " кухня" : "")) + " · " + (rc.tags || []).filter((t) => !/ кухня$/.test(t)).map((t) => "#" + esc(t)).join(" ") + "</div>" +
      '<div class="grid3" style="margin-top:10px"><div class="stat"><small>Подготовка</small><b>' + (rc.prep || 0) + ' мин</b></div><div class="stat"><small>Готовка</small><b>' + (rc.cook ? rc.cook + " мин" : "без готовки") + '</b></div><div class="stat"><small>Сложность</small><b style="font-size:14px">' + DIFF[(rc.diff || 1) - 1] + "</b></div></div>" + (arch ? '<div class="warn" style="margin-top:8px">Рецепт в архиве — он не показывается в каталоге и подборе меню.</div>' : "") + "</div>";
    h += '<div class="card tint nocollapse"><h3>Пищевая ценность</h3>' + A.seg([["portion", "На 1 порцию"], ["100", "На 100 г"], ["all", "На весь рецепт"]], mode, "nmode") + '<div class="grid3"><div class="stat"><small>Ккал</small><b>' + Math.round(shown.kcal) + '</b></div><div class="stat"><small>Белки</small><b>' + round(shown.p, 1) + ' г</b></div><div class="stat"><small>Жиры</small><b>' + round(shown.f, 1) + ' г</b></div><div class="stat"><small>Углеводы</small><b>' + round(shown.c, 1) + ' г</b></div><div class="stat"><small>Клетчатка</small><b>' + round(shown.fib, 1) + ' г</b></div><div class="stat"><small>Порция</small><b>' + Math.round(tot.g / servD) + " г</b></div></div>" + (Object.keys(subs).length ? '<p class="small muted">С учётом замен ингредиентов.</p>' : "") + '<p class="small muted">Расчёт по базе продуктов, значения приблизительные.</p></div>';
    h += '<div class="card nocollapse"><h3>' + (factor != null ? "Ингредиенты на ≈ " + amountFmt(servD) + " порц." : "Ингредиенты на " + serv + " порц.") + '<span class="sp"></span>' + (factor == null ? '<button class="icon-btn" data-a="sv" data-v="-1" aria-label="Меньше">−</button><button class="icon-btn" data-a="sv" data-v="1" aria-label="Больше">+</button>' : "") + "</h3>" +
      scaled.map((i, n) => '<div class="row" style="padding:6px 0;border-bottom:1px solid var(--line)"><span class="grow">' + (n === mi ? "<b>" : "") + esc(i.name) + (n === mi ? "</b>" : "") + (i.swapped ? ' <span class="tag">замена</span>' : "") + (SWAPS[baseList[n].name] ? ' <button class="link" data-a="swap" data-i="' + n + '" style="padding:0 4px">{{i:repeat}}</button>' : "") + '</span><b class="num">' + weighLabel(i) + "</b></div>").join("") +
      (Object.keys(subs).length ? '<div class="btns"><button class="btn sm" data-a="subsReset">Вернуть исходный</button><button class="btn sm primary" data-a="subsSave">Сохранить с заменами</button></div>' : "") + "</div>";
    if (mi >= 0) h += '<div class="card peach"><h3>{{i:scale}} Калькулятор граммовки</h3><p class="small">Взвесьте основной продукт — остальные ингредиенты пересчитаются в тех же пропорциях.</p><div class="form"><div class="fld full"><label>Основной продукт</label><select data-c="calcMain">' + list.map((i, n) => (i.unit === "по вкусу" || !i.g ? "" : '<option value="' + n + '"' + (n === mi ? " selected" : "") + ">" + esc(i.name) + " (по рецепту: " + weighLabel(i) + ")</option>")).join("") + '</select></div><div class="fld"><label>У меня есть, ' + (list[mi].unit === "шт" ? "шт" : "г") + '</label><input type="number" inputmode="decimal" data-c="calcHave" value="' + (have || "") + '"></div><div class="fld" style="justify-content:flex-end">' + (factor != null ? '<button class="btn" data-a="calcReset">Сброс</button>' : "") + "</div></div>" + (factor != null ? '<p><b>Получилось порций: ≈ ' + amountFmt(servD) + "</b> · множитель ×" + amountFmt(factor) + "</p>" : "") + "</div>";
    h += '<div class="card"><h3>Приготовление</h3>' + ((rc.steps || []).length ? rc.steps.map((s, i) => '<div style="margin:8px 0"><div class="small muted">Шаг ' + (i + 1) + "</div>" + esc(s) + "</div>").join("") : '<p class="small muted">Шаги не добавлены</p>') + "</div>";
    if (rc.tips) h += '<div class="card sage"><h3>{{i:lightbulb}} Советы</h3><p>' + esc(rc.tips) + "</p></div>";
    if (rc.subs) h += '<div class="card peach"><h3>{{i:repeat}} Замены</h3><p>' + esc(rc.subs) + "</p></div>";
    if (rc.notes) h += '<div class="card"><h3>Заметки</h3><p>' + esc(rc.notes) + "</p></div>";
    h += '<div class="btns"><button class="btn primary" data-a="cooked">{{i:chef-hat}} Приготовить</button><button class="btn" data-a="toMenu">{{i:calendar-days}} В меню</button><button class="btn" data-a="toDiary">{{i:utensils}} В питание</button><button class="btn" data-a="toShop">{{i:shopping-cart}} В покупки</button><button class="btn" data-a="copy">{{i:copy}} Копия</button><button class="btn ghost" data-a="editR">{{i:pencil}} ' + (rc.builtin ? "Своя версия" : "Изменить") + '</button><button class="btn ghost" data-a="arch">{{i:archive}} ' + (arch ? "Вернуть из архива" : "В архив") + "</button></div>";
    A._detail = { rc, scaled, servD, factor, list };
    return h;
  }

  A.view("recipes", {
    title: (r) => (r.args[0] === "r" ? "Рецепт" : "Рецепты и меню"), tab: "more",
    actions(el, r) { if (!r.args[0] || r.args[0] === "catalog") el.innerHTML = '<button class="icon-btn" onclick="App.edit.recipe()" aria-label="Новый рецепт">{{i:plus}}</button>'; },
    render(el, r) {
      const sec = r.args[0] || "catalog", P = r.params;
      if (sec === "r") { const rc = A.recipe(r.args[1]); return rc ? detail(rc, P) : A.empty("Рецепт не найден или удалён"); }
      let h = A.seg(TABS, sec, "sec");
      if (sec === "catalog") {
        const q = (P.q || "").toLowerCase(), cat = P.c || "", cz = P.z || "", quick = P.k || "", sort = P.s || "name";
        let list = cat === "Архив" ? A.allRecipes(true).filter((x) => archived()[x.id]) : A.allRecipes();
        if (cat === "Избранное") list = list.filter((x) => A.db().favs[x.id]);
        else if (cat === "Мои") list = list.filter((x) => !x.builtin);
        else if (cat && cat !== "Архив") list = list.filter((x) => x.cat === cat);
        if (cz) list = list.filter((x) => cuisineOf(x) === cz);
        if (quick) list = list.filter(QUICK.find((x) => x[0] === quick)[2]);
        if (q) list = list.filter((x) => (x.name + " " + (x.tags || []).join(" ") + " " + ingrOf(x).map((i) => i.name).join(" ") + " " + (x.meals || []).map((m) => (SLOTS.find((s) => s[0] === m) || [0, ""])[1]).join(" ")).toLowerCase().includes(q));
        list.sort((a, b) => sort === "diff" ? (a.diff || 1) - (b.diff || 1) : sort === "time" ? ((+a.prep || 0) + (+a.cook || 0)) - ((+b.prep || 0) + (+b.cook || 0)) : sort === "kcal" ? A.recipeMacros(a).kcal - A.recipeMacros(b).kcal : sort === "prot" ? A.recipeMacros(b).p - A.recipeMacros(a).p : a.name.localeCompare(b.name));
        h += '<input id="rq" placeholder="Поиск: название, ингредиент, тег, приём пищи" value="' + esc(P.q || "") + '" style="margin-bottom:8px">';
        const chip = (a, v, label, on) => '<button class="chip' + (on ? " on" : "") + '" data-a="' + a + '" data-v="' + esc(v) + '" style="flex:0 0 auto">' + label + "</button>";
        h += '<div class="chips" style="margin-bottom:6px;flex-wrap:nowrap;overflow-x:auto">' + chip("cat", "", "Все", !cat) + chip("cat", "Избранное", "{{i:star}} Избранное", cat === "Избранное") + chip("cat", "Мои", "Мои", cat === "Мои") + CATS.map((c) => chip("cat", c, c, cat === c)).join("") + chip("cat", "Архив", "{{i:archive}} Архив", cat === "Архив") + "</div>";
        if (cat === WORLD) { const zs = [...new Set(A.allRecipes().map(cuisineOf).filter(Boolean))].sort(); h += '<div class="chips" style="margin-bottom:6px;flex-wrap:nowrap;overflow-x:auto">' + chip("cz", "", "Все кухни", !cz) + zs.map((z) => chip("cz", z, z, cz === z)).join("") + "</div>"; }
        h += '<div class="chips" style="margin-bottom:6px">' + QUICK.map(([k2, n]) => chip("quick", k2, n, quick === k2)).join("") + "</div>";
        h += A.seg([["name", "А–Я"], ["time", "время"], ["diff", "сложность"], ["kcal", "ккал"], ["prot", "белок"]], sort, "sort");
        h += '<div class="small muted" style="margin:-4px 0 8px">Найдено: ' + list.length + "</div>";
        h += '<div class="rgrid">' + (list.length ? list.map((x) => { const m = A.recipeMacros(x); return '<a class="rcard" href="#/recipes/r/' + x.id + '">' + recImg(x) + '<div class="rb"><b>' + (A.db().favs[x.id] ? "★ " : "") + esc(x.name) + '</b><span class="muted">' + ((+x.prep || 0) + (+x.cook || 0)) + " мин · " + m.kcal + " ккал · Б " + Math.round(m.p) + "</span></div></a>"; }).join("") : A.empty(cat === "Избранное" ? "Избранного пока нет" : cat === "Мои" ? "Своих рецептов пока нет" : "Ничего не нашлось. Измените запрос или фильтры — или добавьте свой рецепт.")) + "</div>";
      } else if (sec === "menu") {
        const view = P.v || "week";
        h += A.seg([["day", "День"], ["week", "Неделя"], ["month", "Месяц"], ["stats", "За 30 дней"]], view, "mview");
        h += '<div class="btns" style="margin:0 0 12px"><button class="btn sm primary" data-a="auto">{{i:sparkles}} Составить меню</button><button class="btn sm" data-a="shopBuild">{{i:shopping-cart}} Покупки на неделю</button><button class="btn sm" data-a="saveWeek">Неделю как шаблон</button></div>';
        const goal = A.db().profile.kcal;
        const dayCard = (d) => {
          const items = menuDay(d), pl = dayPlan(d), fact = A.kcalDay(d) || 0;
          let c = '<div class="card nocollapse"><h3>' + esc(A.DOW_FULL[A.dow(d) - 1][0].toUpperCase() + A.DOW_FULL[A.dow(d) - 1].slice(1)) + " <small>" + fmtShort(d) + '</small><span class="sp"></span><small>план ' + Math.round(pl.kcal) + " · факт " + Math.round(fact) + " / " + goal + "</small></h3>" + A.bar(pl.kcal / goal, "var(--peach)") + '<div class="small muted" style="margin-top:4px">белок ' + Math.round(pl.p) + " г</div>";
          SLOTS.forEach(([sk, sn]) => {
            const its = items.filter((x) => x.slot === sk);
            if (!its.length && !["breakfast", "lunch", "dinner"].includes(sk)) { c += '<button class="link small" data-a="addM" data-d="' + d + '" data-s="' + sk + '">+ ' + sn.toLowerCase() + "</button> "; return; }
            c += '<div class="row" style="margin-top:8px"><b class="small grow">' + sn + '</b><button class="btn sm ghost" data-a="addM" data-d="' + d + '" data-s="' + sk + '">+</button></div>';
            its.forEach((x) => {
              const rcp = A.recipe(x.recipeId), m = itemMacros(x), idx = items.indexOf(x);
              const stc = { plan: ["запланировано", "var(--ink2)"], eaten: ["съедено ✓", "var(--good)"], skip: ["пропущено", "var(--bad)"] }[x.status || "plan"];
              c += '<div class="item" style="padding:6px 0">' + (rcp ? recImg(rcp, "", "width:44px;height:44px;border-radius:10px;object-fit:cover;flex:0 0 auto") : '<div class="ic">{{i:utensils}}</div>') + '<div class="tx tap" data-a="mItem" data-d="' + d + '" data-id="' + x.id + '"><b>' + esc(rcp ? rcp.name : x.name) + (x.servings && x.servings !== 1 ? " × " + amountFmt(x.servings) : "") + (x.recurId ? " ↻" : "") + '</b><small style="color:' + stc[1] + '">' + stc[0] + " · " + Math.round(m.kcal) + ' ккал</small></div><button class="icon-btn" data-a="mUp" data-d="' + d + '" data-i="' + idx + '" aria-label="Выше">↑</button><button class="icon-btn" data-a="mEat" data-d="' + d + '" data-id="' + x.id + '" aria-label="Съедено" style="color:var(--good)">✓</button></div>';
            });
          });
          return c + '<div class="btns"><button class="btn sm ghost" data-a="saveDay" data-d="' + d + '">Сохранить день как шаблон</button><button class="btn sm ghost" data-a="applyP" data-d="' + d + '">Из шаблона</button></div></div>';
        };
        if (view === "day") {
          const d = P.d || today(); A.ensureRecurring([d]);
          h += '<div class="datenav"><button class="icon-btn" data-a="dnav2" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + fmtDate(d, { dow: true }) + '</div><button class="icon-btn" data-a="dnav2" data-v="1">›</button></div>' + dayCard(d);
        } else if (view === "week") {
          const ws = P.w || A.weekStart(today()), days = A.range(ws, addDays(ws, 6)); A.ensureRecurring(days);
          h += '<div class="datenav"><button class="icon-btn" data-a="wnav" data-v="-7">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + fmtShort(ws) + " – " + fmtShort(addDays(ws, 6)) + '</div><button class="icon-btn" data-a="wnav" data-v="7">›</button></div>';
          const wk = days.map(dayPlan); h += '<div class="small muted" style="margin-bottom:8px">В среднем ' + Math.round(A.avg(wk.map((x) => x.kcal)) || 0) + " ккал/день в плане · " + Math.round(A.avg(wk.map((x) => x.p)) || 0) + " г белка/день</div>";
          if (!days.some((d) => menuDay(d).length)) h += A.empty("На неделе нет блюд из рецептов. Нажмите «Составить меню» — приложение подберёт блюда под вашу цель по калориям и белку.");
          h += days.map(dayCard).join("");
        } else if (view === "month") {
          const mk = P.m || today().slice(0, 7), [y, mo] = mk.split("-").map(Number), n = A.daysInMonth(y, mo);
          h += '<div class="datenav"><button class="icon-btn" data-a="mnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="mnav" data-v="1">›</button></div>';
          const start = A.weekStart(mk + "-01"); let planned = 0;
          h += '<div class="card"><div class="month">' + A.DOW.map((x) => '<div class="h">' + x + "</div>").join("");
          for (let i = 0; i < 42; i++) { const d = addDays(start, i); if (i >= 35 && d.slice(0, 7) !== mk) break; const m = A.db().menu[d] || []; const pl = dayPlan(d); if (d.slice(0, 7) === mk && m.length) planned++; const all = m.length && m.every((x) => x.status !== "plan"); h += '<button class="mday' + (d.slice(0, 7) !== mk ? " out" : "") + (d === today() ? " now" : "") + '" data-a="openDay" data-d="' + d + '" style="' + (m.length ? "background:var(--peach2)" : "") + '">' + A.parse(d).getDate() + (m.length ? '<small style="font-size:9.5px">' + Math.round(pl.kcal) + "</small>" + (all ? '<small style="font-size:9px;color:var(--good)">✓✓</small>' : "") : "") + "</button>"; }
          h += '</div><p class="small muted" style="margin-top:8px">Дней с меню: ' + planned + " из " + n + ". «✓✓» — всё из меню съедено.</p></div>";
        } else {
          const days = A.lastDays(30), withM = days.filter((d) => (A.db().menu[d] || []).length);
          const its = withM.flatMap((d) => A.db().menu[d]);
          const eaten = its.filter((x) => x.status === "eaten").length;
          h += '<div class="grid3"><div class="stat"><small>Дней с меню</small><b>' + withM.length + '</b></div><div class="stat"><small>Плана съедено</small><b>' + (its.length ? Math.round((eaten / its.length) * 100) : 0) + '%</b></div><div class="stat"><small>Блюд</small><b>' + its.length + "</b></div></div>";
          h += '<div class="card" style="margin-top:12px"><h3>Калории: план и факт</h3>' + A.charts.line([{ name: "План", color: "#EFA984", data: days.map((d) => ({ x: fmtShort(d).slice(0, 2), v: (A.db().menu[d] || []).length ? Math.round(dayPlan(d).kcal) : null })) }, { name: "Факт", color: "#8E7CC3", data: days.map((d) => ({ x: fmtShort(d).slice(0, 2), v: A.kcalDay(d) })) }]) + "</div>";
          const cnt = {}; its.forEach((x) => { const rc = A.recipe(x.recipeId); if (rc) cnt[rc.name] = (cnt[rc.name] || 0) + 1; });
          const top = Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 8);
          h += '<div class="card"><h3>Чаще всего в меню</h3>' + (top.length ? top.map(([n2, c]) => '<div class="row small" style="padding:3px 0"><span class="grow">' + esc(n2) + "</span><b>" + c + "</b></div>").join("") : '<p class="small muted">В меню за период нет блюд из рецептов</p>') + "</div>";
        }
      } else if (sec === "shop") {
        const list = A.col("shopping");
        h += '<div class="small muted" style="margin-bottom:8px">Куплено ' + list.filter((x) => x.bought).length + " из " + list.length + "</div>";
        h += '<div class="btns" style="margin:0 0 12px"><button class="btn sm primary" data-a="shopBuild">Собрать из меню недели</button><button class="btn sm" data-a="shopDedupe">Объединить дубли</button><button class="btn sm" data-a="shopExport">Экспорт в текст</button><button class="btn sm ghost" data-a="shopClear">Убрать купленное</button></div>';
        h += '<div class="card nocollapse"><div class="inline-add"><input id="sq" placeholder="Новая позиция: хлеб, 2 шт"><select id="sc" style="width:130px">' + SHOP_CATS.map((c) => "<option>" + c + "</option>").join("") + '</select><button class="btn primary" data-a="shopAdd">+</button></div></div>';
        SHOP_CATS.forEach((c) => {
          const it = list.filter((x) => shopCat(x.cat) === c); if (!it.length) return;
          h += '<div class="card"><h3>' + c + '<span class="sp"></span><small>' + it.filter((x) => x.bought).length + "/" + it.length + '</small></h3><div class="list">' + it.map((x) => '<div class="item' + (x.bought ? " done" : "") + '"><button class="check' + (x.bought ? " on" : "") + '" data-a="shopTog" data-id="' + x.id + '">' + (x.bought ? "✓" : "") + '</button><div class="tx"><b>' + esc(x.name) + "</b><small>" + esc(shopQty(x)) + (x.manual ? " · вручную" : "") + '</small></div><button class="icon-btn" data-a="shopDel" data-id="' + x.id + '" aria-label="Удалить">✕</button></div>').join("") + "</div></div>";
        });
        if (!list.length) h += A.empty("Список пуст. Соберите его из меню недели, из рецепта или добавьте вручную.");
        h += '<p class="small muted">Как собирается список: ингредиенты запланированных блюд складываются (200 г + 0,3 кг = 500 г) и округляются до того, что можно купить.</p>';
      } else if (sec === "presets") {
        const ps = A.col("menuPresets");
        h += '<div class="card"><h3>Шаблоны меню</h3><p class="small muted">Шаблон дня — на каждый день периода, шаблон недели — блюда по дням недели. Уже составленное меню не изменится.</p>' + (ps.length ? ps.map((p) => '<div class="item"><div class="tx"><b>' + esc(p.name) + "</b><small>" + (p.kind === "week" ? "неделя" : "день") + " · блюд: " + sum(p.days.map((d) => d.length)) + '</small></div><button class="btn sm" data-a="pApply" data-id="' + p.id + '">Применить</button><button class="icon-btn" data-a="pEdit" data-id="' + p.id + '" aria-label="Изменить">{{i:pencil}}</button></div>').join("") : A.empty("Шаблонов нет")) + "</div>";
        const rr = A.col("menuRecur");
        h += '<div class="card"><h3>Повторяющиеся блюда</h3>' + (rr.length ? rr.map((x) => { const rc = A.recipe(x.recipeId); return '<div class="item tap" data-a="recEdit" data-id="' + x.id + '"><div class="tx"><b>' + esc(rc ? rc.name : "Рецепт удалён") + "</b><small>" + (SLOTS.find((m) => m[0] === x.slot) || [0, ""])[1] + " · " + (x.days || []).map((k2) => A.DOW[k2 - 1]).join(", ") + " · с " + fmtShort(x.start) + (x.end ? " по " + fmtShort(x.end) : " · без конца") + (x.active ? "" : " · выключено") + "</small></div></div>"; }).join("") : '<p class="small muted">Откройте рецепт → «В меню» → «Повторять по дням недели».</p>') + "</div>";
      } else if (sec === "history") {
        const mk = P.m || today().slice(0, 7);
        const log = A.col("cookLog").filter((x) => x.date.slice(0, 7) === mk).sort((a, b) => (a.date < b.date ? 1 : -1));
        h += '<div class="datenav"><button class="icon-btn" data-a="hnav" data-v="-1">‹</button><div class="dn-t" style="display:flex;align-items:center;justify-content:center">' + esc(A.monthTitle(mk)) + '</div><button class="icon-btn" data-a="hnav" data-v="1">›</button></div>';
        h += '<div class="grid3"><div class="stat"><small>Приготовлено</small><b>' + log.length + '</b></div><div class="stat"><small>Разных блюд</small><b>' + new Set(log.map((x) => x.name)).size + '</b></div><div class="stat"><small>Порций</small><b>' + amountFmt(sum(log.map((x) => +x.servings || 1))) + "</b></div></div>";
        h += '<div class="card" style="margin-top:12px"><h3>Что готовили</h3><div class="list">' + (log.length ? log.map((x) => { const rc = A.recipe(x.recipeId); return '<div class="item tap" data-a="hItem" data-id="' + x.id + '">' + (rc ? recImg(rc, "", "width:44px;height:44px;border-radius:10px;object-fit:cover;flex:0 0 auto") : '<div class="ic">{{i:chef-hat}}</div>') + '<div class="tx"><b>' + esc(x.name) + "</b><small>" + fmtDate(x.date) + " · " + amountFmt(x.servings) + " порц. · на порцию " + x.snap.kcal + " ккал, Б " + x.snap.p + " Ж " + x.snap.f + " У " + x.snap.c + (x.mealId ? " · в питании ✓" : "") + "</small></div></div>"; }).join("") : A.empty("История пуста")) + "</div></div>";
      }
      return h;
    },
    bind(el, r) {
      const P = A.route().params, sec = r.args[0] || "catalog";
      const q = (o) => { const x = Object.assign({}, P, o); A.go("recipes/" + (sec === "r" ? "r/" + r.args[1] : sec) + "?" + Object.keys(x).filter((k) => x[k] !== "" && x[k] != null).map((k) => k + "=" + encodeURIComponent(x[k])).join("&"), true); };
      const rq = el.querySelector("#rq"); if (rq) rq.addEventListener("change", () => q({ q: rq.value.trim() }));
      const rc = sec === "r" ? A.recipe(r.args[1]) : null;
      const D = () => A._detail;
      A.bind(el, {
        sec(b) { A.go("recipes/" + b.dataset.v, true); },
        cat(b) { q({ c: b.dataset.v, z: "" }); }, cz(b) { q({ z: b.dataset.v }); }, quick(b) { q({ k: P.k === b.dataset.v ? "" : b.dataset.v }); }, sort(b) { q({ s: b.dataset.v }); },
        fav() { const f = A.db().favs; if (f[rc.id]) delete f[rc.id]; else f[rc.id] = 1; A.save(); A.toast(f[rc.id] ? "В избранном" : "Убрано из избранного"); A.refresh(); },
        arch() { const a = archived(); if (a[rc.id]) { delete a[rc.id]; A.toast("Рецепт возвращён в каталог"); } else { a[rc.id] = 1; A.toast("Рецепт в архиве — он в категории «Архив»"); } A.save(); A.refresh(); },
        nmode(b) { q({ n: b.dataset.v }); },
        sv(b) { q({ s: Math.max(1, +(P.s || rc.servings || 1) + +b.dataset.v) }); },
        calcMain(b) { q({ main: b.value, have: "" }); }, calcHave(b) { q({ have: b.value }); }, calcReset() { q({ have: "", main: "" }); },
        swap(b) {
          const i = +b.dataset.i, from = ingrOf(rc)[i];
          A.sheet("Заменить: " + from.name, '<div class="list">' + SWAPS[from.name].map((t) => { const p = A.product(t); return '<div class="item tap" data-t="' + esc(t) + '"><div class="tx"><b>' + esc(t) + "</b><small>" + (p ? p.kcal + " ккал · Б " + p.p + " Ж " + p.f + " У " + p.c + " на 100 г" : "нет в базе продуктов") + "</small></div></div>"; }).join("") + "</div>");
          document.querySelector(".sheet-wrap:last-child .list").addEventListener("click", (e) => { const it = e.target.closest("[data-t]"); if (!it) return; if (!A._subs || A._subs.id !== rc.id) A._subs = { id: rc.id, map: {} }; A._subs.map[i] = it.dataset.t; A.closeSheet(); A.refresh(); });
        },
        subsReset() { A._subs = null; A.refresh(); },
        subsSave() { const copy = Object.assign({}, rc, { ingr: D().list.map((i) => { const o = Object.assign({}, i); delete o.swapped; return o; }) }); A._subs = null; A.edit.recipe(copy, { copy: true, suffix: " (с заменами)" }); },
        editR() { A.edit.recipe(rc); }, copy() { A.edit.recipe(rc, { copy: true }); },
        cooked() {
          const d = D();
          A.formSheet("Приготовить: " + rc.name, [{ k: "servings", label: "Получилось порций", type: "number", step: 0.5 }, { k: "eaten", label: "Съедено порций", type: "number", step: 0.5 }, { k: "date", label: "Дата", type: "date" }, { k: "diary", label: "Питание", type: "check", text: "Добавить съеденное в питание" }, { k: "type", label: "Приём пищи", type: "chips", opts: SLOTS }],
            { servings: round(d.servD, 1), eaten: 1, date: today(), diary: true, type: new Date().getHours() < 11 ? "breakfast" : new Date().getHours() < 16 ? "lunch" : "dinner" }, (o) => {
              const e = { date: o.date, recipeId: rc.id, name: rc.name, servings: o.servings || 1, snap: A.recipeMacros(rc), factor: d.factor };
              if (o.diary && o.eaten > 0) e.mealId = A.addRecipeToDiary(rc, o.date, o.type, o.eaten).id;
              A.upsert("cookLog", e); A.toast(e.mealId ? "Записано в историю и в питание" : "Записано в историю готовки"); A.refresh();
            });
        },
        toDiary() { A.formSheet("Добавить в питание", [{ k: "date", label: "День", type: "date" }, { k: "type", label: "Приём пищи", type: "chips", opts: SLOTS }, { k: "servings", label: "Порций", type: "number", step: 0.5 }], { date: today(), type: "lunch", servings: 1 }, (o) => { A.addRecipeToDiary(rc, o.date, o.type, o.servings); A.toast("Добавлено: " + (SLOTS.find((m) => m[0] === o.type) || [0, ""])[1].toLowerCase() + ", " + fmtShort(o.date)); }); },
        toShop() { A.addRecipeToShopping(rc, D().factor != null ? D().factor : (+(P.s || rc.servings)) / (+rc.servings || 1)); A.toast("Ингредиенты добавлены в список покупок"); },
        toMenu() {
          A.formSheet("Добавить в меню", [{ k: "date", label: "Начиная с", type: "date" }, { k: "slot", label: "Приём пищи", type: "chips", opts: SLOTS }, { k: "servings", label: "Порций", type: "number", step: 0.5 }, { k: "rep", label: "Повторять по дням недели", type: "chips", opts: [["no", "нет"], ["all", "каждый день"], ["wk", "по будням"], ["we", "по выходным"]] }, { k: "end", label: "До (необязательно)", type: "date" }],
            { date: today(), slot: (rc.meals || ["lunch"])[0], servings: 1, rep: "no" }, (o) => {
              if (o.rep !== "no") { A.upsert("menuRecur", { recipeId: rc.id, slot: o.slot, days: o.rep === "all" ? [1, 2, 3, 4, 5, 6, 7] : o.rep === "wk" ? [1, 2, 3, 4, 5] : [6, 7], start: o.date, end: o.end || "", servings: o.servings || 1, active: true }); A.ensureRecurring(A.range(o.date, addDays(o.date, 34))); A.toast("Блюдо будет в меню по выбранным дням"); }
              else { menuDay(o.date).push({ id: A.uid(), slot: o.slot, recipeId: rc.id, servings: o.servings || 1, status: "plan" }); A.save(); A.toast("Добавлено в меню"); }
            });
        },
        mview(b) { A.go("recipes/menu?v=" + b.dataset.v, true); },
        auto() { A.autoMenu(P.w || (P.d || today())); },
        wnav(b) { q({ w: addDays(P.w || A.weekStart(today()), +b.dataset.v) }); },
        dnav2(b) { q({ d: addDays(P.d || today(), +b.dataset.v) }); },
        mnav(b) { let [y, m] = (P.m || today().slice(0, 7)).split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } q({ m: y + "-" + A.pad(m) }); },
        openDay(b) { A.go("recipes/menu?v=day&d=" + b.dataset.d); },
        addM(b) { A.addToMenu(b.dataset.d, b.dataset.s); },
        mUp(b) { const m = menuDay(b.dataset.d), i = +b.dataset.i, slot = m[i].slot; let j = i - 1; while (j >= 0 && m[j].slot !== slot) j--; if (j >= 0) { [m[j], m[i]] = [m[i], m[j]]; A.save(); A.refresh(); } },
        mEat(b) { const x = menuDay(b.dataset.d).find((k) => k.id === b.dataset.id); setStatus(b.dataset.d, x, x.status === "eaten" ? "plan" : "eaten"); A.toast(x.status === "eaten" ? "Съедено — добавлено в питание" : "Снова запланировано"); A.refresh(); },
        mItem(b) {
          const d = b.dataset.d, m = menuDay(d), x = m.find((k) => k.id === b.dataset.id), rcp = A.recipe(x.recipeId);
          A.formSheet(rcp ? rcp.name : x.name, [{ k: "status", label: "Статус", type: "chips", opts: [["plan", "запланировано"], ["eaten", "съедено"], ["skip", "пропущено"]] }, { k: "servings", label: "Порций", type: "number", step: 0.5 }, { k: "slot", label: "Перенести в приём пищи", type: "chips", opts: SLOTS }, { k: "date", label: "Перенести на день", type: "date" }], Object.assign({ date: d }, x), (o) => {
            x.servings = o.servings || 1; x.slot = o.slot; setStatus(d, x, o.status);
            if (o.date && o.date !== d) { m.splice(m.indexOf(x), 1); if (x.recurId) { const rr = A.byId("menuRecur", x.recurId); if (rr) (rr.skip = rr.skip || []).push(d); delete x.recurId; } menuDay(o.date).push(x); A.save(); }
            A.refresh();
          }, { onDelete: () => { if (x.mealId) A.remove("meals", x.mealId); if (x.recurId) { const rr = A.byId("menuRecur", x.recurId); if (rr) (rr.skip = rr.skip || []).push(d); } m.splice(m.indexOf(x), 1); A.save(); A.refresh(); }, post: rcp ? '<a class="btn sm" href="#/recipes/r/' + rcp.id + '">Открыть рецепт</a>' : "" });
        },
        saveDay(b) { A.formSheet("Сохранить день как шаблон", [{ k: "name", label: "Название шаблона", req: true, full: true, ph: "Мой обычный день" }], {}, (o) => { A.upsert("menuPresets", { name: o.name, kind: "day", days: [menuDay(b.dataset.d).map((x) => ({ slot: x.slot, recipeId: x.recipeId, name: x.name, servings: x.servings }))] }); A.toast("Шаблон «" + o.name + "» сохранён"); }); },
        saveWeek() { const ws = P.w || A.weekStart(today()); A.formSheet("Шаблон недели", [{ k: "name", label: "Название шаблона", req: true, full: true, ph: "Рабочая неделя" }], {}, (o) => { A.upsert("menuPresets", { name: o.name, kind: "week", days: A.range(ws, addDays(ws, 6)).map((d) => menuDay(d).map((x) => ({ slot: x.slot, recipeId: x.recipeId, name: x.name, servings: x.servings }))) }); A.toast("Шаблон недели «" + o.name + "» сохранён"); }); },
        applyP(b) { A.applyPreset(null, b.dataset.d); }, pApply(b) { A.applyPreset(A.byId("menuPresets", b.dataset.id)); },
        pEdit(b) { const p = A.byId("menuPresets", b.dataset.id); A.formSheet("Шаблон", [{ k: "name", label: "Название", full: true }], p, (o) => { p.name = o.name; A.save(); A.refresh(); }, { onDelete: () => { A.remove("menuPresets", p.id); A.refresh(); }, pre: '<div class="small">' + p.days.map((d, i) => (p.kind === "week" ? A.DOW[i] + ": " : "") + d.map((x) => { const rr = A.recipe(x.recipeId); return rr ? rr.name : x.name; }).join(", ")).join("<br>") + "</div>" }); },
        recEdit(b) { A.edit.menuRecur(A.byId("menuRecur", b.dataset.id)); },
        shopBuild() { const ws = P.w || A.weekStart(today()); const n = A.buildShopping(ws, addDays(ws, 6)); A.toast("Список покупок собран: " + n + " позиций"); A.go("recipes/shop"); },
        shopDedupe() { A.toast("Объединено: " + dedupeShopping()); A.refresh(); },
        shopExport() { const t = shoppingText() || "Список пуст"; if (A.native) A.native.shareText("Список покупок", t); else A.download("pokupki-" + today() + ".txt", "text/plain", t); },
        shopClear() { A.db().shopping = A.col("shopping").filter((x) => !x.bought); A.save(); A.refresh(); },
        shopAdd() { const v = el.querySelector("#sq").value.trim(); if (!v) return; const [name, qty] = v.split(",").map((x) => x.trim()); A.col("shopping").push({ id: A.uid(), name, qty: qty || "", cat: el.querySelector("#sc").value, bought: false, manual: true }); A.save(); A.refresh(); },
        shopTog(b) { const x = A.byId("shopping", b.dataset.id); x.bought = !x.bought; A.save(); A.refresh(); },
        shopDel(b) { A.remove("shopping", b.dataset.id); A.refresh(); },
        hnav(b) { let [y, m] = (P.m || today().slice(0, 7)).split("-").map(Number); m += +b.dataset.v; if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; } q({ m: y + "-" + A.pad(m) }); },
        hItem(b) {
          const x = A.byId("cookLog", b.dataset.id), rcp = A.recipe(x.recipeId);
          A.sheet(x.name, '<p class="small muted">' + fmtDate(x.date) + " · " + amountFmt(x.servings) + " порц.</p><p>Снимок КБЖУ на порцию на момент готовки: " + x.snap.kcal + " ккал, Б " + x.snap.p + " · Ж " + x.snap.f + " · У " + x.snap.c + "</p>", { buttons: [
            { label: "Удалить", cls: "danger ghost", onClick: () => { A.confirm("Удалить запись? Запись в дневнике питания, если была, останется.", () => { A.remove("cookLog", x.id); A.refresh(); }, "Удалить", true); return false; } },
            { label: x.mealId ? "В питании ✓" : "В питание", cls: "primary", onClick: () => { if (x.mealId) return; x.mealId = A.upsert("meals", { date: x.date, type: "lunch", name: x.name, recipeId: x.recipeId, g: 0, kcal: x.snap.kcal, p: x.snap.p, f: x.snap.f, c: x.snap.c, fib: 0 }).id; A.save(); A.toast("Добавлено в питание"); A.refresh(); } }
          ].concat(rcp ? [{ label: "Рецепт", cls: "", onClick: () => A.go("recipes/r/" + rcp.id) }] : []) });
        }
      });
    }
  });

  A.applyPreset = (p, date) => {
    const run = (pp, from, to) => {
      A.range(from, to).forEach((dd) => (pp.days[pp.kind === "week" ? A.dow(dd) - 1 : 0] || []).forEach((x) => menuDay(dd).push(Object.assign({ id: A.uid(), status: "plan" }, x))));
      A.save(); A.toast("Шаблон «" + pp.name + "» применён"); A.refresh();
    };
    const ps = A.col("menuPresets"); if (!ps.length) return A.toast("Шаблонов пока нет");
    A.formSheet("Применить шаблон", [{ k: "pid", label: "Шаблон", type: "select", opts: ps.map((x) => [x.id, x.name + (x.kind === "week" ? " (неделя)" : " (день)")]), full: true }, { k: "from", label: "С", type: "date" }, { k: "to", label: "По", type: "date" }], { pid: p ? p.id : ps[0].id, from: date || addDays(today(), 1), to: date || addDays(today(), 1) }, (o) => { const pp = A.byId("menuPresets", o.pid); const to = pp.kind === "week" && o.to === o.from ? addDays(o.from, 6) : o.to < o.from ? o.from : o.to; run(pp, o.from, to); });
  };
  A.edit.menuRecur = (rr) => {
    const isNew = !rr || !rr.id;
    rr = Object.assign({ slot: "breakfast", days: [1, 2, 3, 4, 5], start: today(), end: "", servings: 1, active: true }, rr || {});
    A.formSheet("Повторяющееся блюдо", [
      { k: "recipeId", label: "Блюдо", type: "select", opts: () => A.allRecipes().map((x) => [x.id, x.name]), full: true },
      { k: "slot", label: "Приём пищи", type: "chips", opts: SLOTS }, { k: "days", label: "Дни недели", type: "multi", numeric: true, opts: [[1, "Пн"], [2, "Вт"], [3, "Ср"], [4, "Чт"], [5, "Пт"], [6, "Сб"], [7, "Вс"]] },
      { k: "start", label: "С", type: "date" }, { k: "end", label: "По (пусто — без конца)", type: "date" },
      { k: "servings", label: "Порций", type: "number", step: 0.5 }, { k: "active", label: "Статус", type: "check", text: "Активно" }
    ], rr, (o) => { Object.assign(rr, o); A.upsert("menuRecur", rr); A.ensureRecurring(A.range(today(), addDays(today(), 34))); A.toast("Блюдо будет в меню по выбранным дням"); A.refresh(); },
      isNew ? {} : { onDelete: () => { A.confirm("Удалить повтор? Будущие блюда этого повтора уберутся из меню, прошлые останутся.", () => { const t = today(); Object.entries(A.db().menu).forEach(([d, m]) => { if (d < t) return; for (let i = m.length - 1; i >= 0; i--) if (m[i].recurId === rr.id && m[i].status === "plan") m.splice(i, 1); }); A.remove("menuRecur", rr.id); A.refresh(); }, "Удалить", true); } });
  };
})();
