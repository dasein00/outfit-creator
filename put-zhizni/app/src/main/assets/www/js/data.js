/* Модель данных: начальные данные, миграции и общие вычисления по модулям. */
(function () {
  "use strict";
  const A = window.App;
  const { uid, iso, today, addDays, dow, parse, hm2min, durH, avg, sum, pad } = A;

  /* ---------- справочники ---------- */
  A.CATS = [
    ["work", "Работа", "{{i:briefcase}}", "#9C8FD0"],
    ["sport", "Спорт", "{{i:dumbbell}}", "#7FAE8A"],
    ["learn", "Развитие / обучение", "{{i:book-open}}", "#6FA4C9"],
    ["hobby", "Хобби / творчество", "{{i:palette}}", "#E0A15E"],
    ["family", "Семья / отношения", "{{i:users}}", "#E58C9C"],
    ["home", "Быт", "{{i:house}}", "#C4A77D"],
    ["rest", "Восстановление", "{{i:leaf}}", "#8DBF9E"],
    ["plan", "Планирование", "{{i:target}}", "#8E7CC3"],
    ["sleep", "Сон", "{{i:moon}}", "#7C83C9"],
    ["food", "Еда", "{{i:utensils}}", "#E6B87A"],
    ["other", "Другое", "•", "#A9A3B5"]
  ];
  A.cat = (k) => { const c = A.CATS.find((x) => x[0] === k) || A.CATS[A.CATS.length - 1]; return { k: c[0], name: c[1], icon: c[2], color: c[3] }; };
  A.catOpts = () => A.CATS.map((c) => [c[0], c[2] + " " + c[1]]);

  A.PAL = ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6", "#B6A6D9", "#9CC5A1", "#F3C29F", "#D8C08A", "#EBB0BC", "#A9CBE5", "#C9C3D3"];
  A.EXP_CATS = ["Жильё", "Еда", "Транспорт", "Здоровье", "Одежда", "Красота", "Развлечения", "Хобби", "Обучение", "Подписки", "Подарки", "Дом", "Другое"];
  A.INC_CATS = ["Зарплата", "Подработка", "Подарок", "Проценты / кешбэк", "Продажа", "Другое"];
  A.FEELINGS = ["радость", "спокойствие", "энергичность", "вдохновение", "уверенность", "усталость", "грусть", "раздражение", "злость", "страх", "тревога", "апатия"];
  A.STRESS_SRC = ["работа", "дом", "отношения", "деньги", "здоровье", "учёба", "люди", "другое"];
  A.WORKOUT_TYPES = [["strength", "Силовая"], ["gym", "Тренажёры"], ["cardio", "Кардио"], ["walk", "Ходьба"], ["stretch", "Растяжка"], ["yoga", "Йога"], ["home", "Домашняя"], ["dance", "Танцы"], ["run", "Бег"], ["other", "Другое"]];
  A.MEALS = [["breakfast", "Завтрак"], ["brunch", "Второй завтрак"], ["lunch", "Обед"], ["afternoon", "Полдник"], ["dinner", "Ужин"], ["snack", "Перекус"]];
  A.GOAL_LEVELS = [["10y", "10 лет"], ["3y", "3 года"], ["1y", "Год"], ["q", "Квартал"], ["m", "Месяц"], ["w", "Неделя"], ["d", "День"]];
  A.WEATHER = [
    ["sun", "Солнечно", "{{i:sun}}", "#F4CD52"], ["part", "Переменная облачность", "{{i:cloud-sun}}", "#F29A4A"], ["cloud", "Облачно", "{{i:cloud}}", "#9A9A9A"],
    ["rain", "Дождь", "{{i:cloud-rain}}", "#3F6FB5"], ["storm", "Гроза", "{{i:cloud-lightning}}", "#8750B0"], ["snow", "Снег", "{{i:snowflake}}", "#8FD3E8"],
    ["sleet", "Мокрый снег", "{{i:cloud-snow}}", "#3E9FC9"], ["fog", "Туман", "{{i:cloud-fog}}", "#58B8E0"], ["wind", "Ветер", "{{i:wind}}", "#3FB5A0"]
  ];
  A.weather = (k) => { const w = A.WEATHER.find((x) => x[0] === k); return w ? { k: w[0], name: w[1], icon: w[2], color: w[3] } : null; };
  A.HSTATUS = { done: ["Выполнено", "✓", "#6BAF6B"], part: ["Частично", "½", "#F2CF5B"], skip: ["Пропуск", "×", "#E07B6A"], none: ["Не планировалось", "–", "#E4E0EA"] };

  /* ---------- начальные данные ---------- */
  A.seed = () => {
    const t = today();
    const H = (name, icon, cat, extra = {}) => Object.assign({ id: uid(), name, icon, cat, freq: "daily", days: [1, 2, 3, 4, 5, 6, 7], min: "", time: "", remind: false, start: t, end: "", note: "", archived: false }, extra);
    const E = (title, cat, start, end, days, extra = {}) => Object.assign({ id: uid(), title, cat, date: t, start, end, repeat: days.length === 7 ? "daily" : "weekdays", days, must: false, remind: null, note: "" }, extra);
    const WK = [1, 2, 3, 4, 5], ALL = [1, 2, 3, 4, 5, 6, 7];
    return {
      v: 1,
      createdAt: new Date().toISOString(),
      profile: {
        name: "", wake: "05:00", bed: "21:00", workStart: "08:00", workEnd: "16:48",
        values: ["Свобода", "Любовь и близость", "Честность", "Развитие и знания", "Финансовая стабильность", "Оставаться хорошим человеком"],
        phrase: "Живу и не жалею. Я выбираю жизнь, в которой есть свобода, любовь, развитие и смысл.",
        principle: "Движение важнее, чем идеальность. Маленькие шаги к большой жизни.",
        rules: ["Одновременно только 1 главный проект", "Минимум 2 тренировки", "Каждый день — 1 маленький шаг к цели", "Сон не менее 7 часов", "Телефон и соцсети — по времени"],
        rights: ["отдыхать", "просить о помощи", "менять планы", "сказать нет", "ошибаться"],
        waterGoal: 2000, stepsGoal: 8000, sleepMin: 7, sleepMax: 9, kcal: 1800, prot: 110, fat: 65, carb: 190, fiber: 30, workoutsWeek: 3,
        city: "", lat: null, lon: null,
        theme: "system", accent: "lavender",
        cards: ["dash", "weight", "tasks5", "culture", "word", "pianotutor", "facts", "main", "state", "habits", "schedule", "water", "sleep", "steps", "weather", "food", "workout", "learn", "tarot", "money", "reminders", "esoteric", "evening"],
        hidden: [],
        notif: { general: true, habits: true, payments: true, workouts: true, reading: true, piano: true, study: true, events: true },
        stepsSensor: false, pin: "", bio: false, points: false,
        weights: { sleep: 3, move: 2, food: 2, workout: 2, habits: 3, learn: 2, money: 1, culture: 1, rest: 2, goals: 3 },
        era: { start: t },
        collapsed: {}, autoBackup: true, hc: false, smsImport: false, sleepAuto: false, sleepGoal: 8, factsOn: true,
        body: { height: 165, sex: "f", age: 30, startWeight: null, targetWeight: null, goal: "keep", activity: 1.375, protKg: 1.6, fatKg: 0.9, monthChange: 2 }
      },
      days: {},
      habits: [
        H("Тренировка", "{{i:dumbbell}}", "sport", { freq: "weekdays", days: [1, 3, 6], min: "10 минут зарядки" }),
        H("2 л воды", "{{i:droplet}}", "health"), H("Питание (КБЖУ)", "{{i:apple}}", "health"), H("Чтение", "{{i:book-open}}", "learn", { min: "5 страниц" }),
        H("Пианино", "{{i:piano}}", "learn", { min: "10 минут" }), H("Таро", "{{i:wand-sparkles}}", "learn", { freq: "weekdays", days: [2, 4, 6] }),
        H("Бацзы", "{{i:orbit}}", "learn", { freq: "weekdays", days: [1, 5] }), H("Кету", "{{i:leaf}}", "learn", { freq: "weekdays", days: [3, 7] }),
        H("Уборка", "{{i:house}}", "home", { min: "15 минут" }), H("Забота о себе", "{{i:heart}}", "rest"), H("Общение", "{{i:users}}", "family"),
        H("Финансы", "{{i:wallet}}", "money", { min: "записать расходы" }), H("Сон 7+ часов", "{{i:moon}}", "health"), H("Утренний ритуал", "{{i:sun}}", "rest")
      ],
      hlog: {},
      tasks: [], ideas: [],
      events: [
        E("Подъём, вода, медитация / мысли, план на день", "rest", "05:00", "06:00", ALL),
        E("Завтрак, сборы, дорога, подготовка", "food", "06:00", "07:00", WK),
        E("Завтрак, план дня, домашние дела", "home", "06:00", "07:00", [6]),
        E("Спокойное утро, завтрак", "rest", "06:00", "07:00", [7]),
        E("Дорога, настрой на день", "work", "07:00", "08:00", WK),
        E("Прогулка / спорт (по желанию)", "sport", "07:00", "08:00", [6]),
        E("Прогулка, время для себя", "rest", "07:00", "08:00", [7]),
        E("Работа", "work", "08:00", "12:00", WK, { must: true }),
        E("Обед, небольшая прогулка", "food", "12:00", "13:00", WK),
        E("Бытовые дела, дом, порядок", "home", "12:00", "13:00", [6]),
        E("Семья / близкие", "family", "12:00", "13:00", [7]),
        E("Работа", "work", "13:00", "16:48", WK, { must: true }),
        E("Спорт", "sport", "17:00", "18:00", [1, 3]),
        E("Личный проект / развитие", "learn", "17:00", "18:00", [2, 4, 6]),
        E("Свободное время, восстановление", "rest", "17:00", "18:00", [5]),
        E("Планирование недели, цели, разбор мыслей", "plan", "17:00", "18:00", [7]),
        E("Ужин, отдых", "food", "18:00", "19:00", WK),
        E("Ужин с семьёй / близкими", "family", "18:00", "19:00", [6, 7]),
        E("Время для себя: чтение, сериалы, хобби", "rest", "19:00", "20:00", [1, 7]),
        E("Обучение, чтение", "learn", "19:00", "20:00", [2, 4]),
        E("Хобби / творчество", "hobby", "19:00", "20:00", [3]),
        E("Встреча с близкими / общение", "family", "19:00", "20:00", [5]),
        E("Фильмы, отдых", "hobby", "19:00", "20:00", [6]),
        E("Подготовка ко сну", "rest", "20:00", "21:00", [1, 2, 3, 4, 5, 7]),
        E("Вечер без экранов, расслабление", "rest", "20:00", "21:00", [6]),
        E("Сон", "sleep", "21:00", "05:00", ALL)
      ],
      foods: A.FOOD_BASE.map((f) => ({ id: uid(), name: f[0], kcal: f[1], p: f[2], f: f[3], c: f[4], fib: f[5] || 0 })),
      meals: [],
      exercises: ["Приседания", "Выпады", "Ягодичный мост", "Отжимания", "Планка", "Тяга гантели в наклоне", "Жим гантелей", "Скручивания", "Становая тяга с гантелями", "Бёрпи", "Прыжки на скакалке", "Солнечное приветствие"].map((n) => ({ id: uid(), name: n, muscle: "" })),
      workouts: [],
      accounts: [{ id: uid(), name: "Наличные", type: "cash", start: 0 }, { id: uid(), name: "Банковская карта", type: "card", start: 0 }, { id: uid(), name: "Накопления", type: "savings", start: 0 }],
      tx: [], budgets: {}, savings: [{ id: uid(), name: "Фонд свободы", target: 100000, cur: 0, deadline: "", monthly: 0 }], recurring: [], finplan: {},
      reminders: [
        { id: uid(), title: "Утро — назвать главное", text: "Одно главное дело на сегодня?", type: "daily", time: "05:40", days: [], date: "", dom: 1, kind: "general", on: true },
        { id: uid(), title: "Вечер — проверить факт", text: "План → факт → препятствие", type: "daily", time: "20:15", days: [], date: "", dom: 1, kind: "general", on: true },
        { id: uid(), title: "Воскресный протокол", text: "15 минут: главное дело недели, 2–3 времени, минимальная версия", type: "weekdays", time: "17:00", days: [7], date: "", dom: 1, kind: "general", on: true }
      ],
      hobbies: [], hsess: [],
      books: [], rsess: [], movies: [], series: [],
      piano: { done: {}, cur: "p01", notes: {} }, psess: [],
      tarot: { box: {}, journal: [], quiz: [], notes: {}, daily: {}, lessons: {} },
      ketu: { box: {}, quiz: [], notes: "", lessons: {} },
      bazi: { box: {}, quiz: [], notes: "", lessons: {} },
      learn: [],
      goals: [], weeks: {}, reviews: {}, rewards: [], bookDone: {}, checklist: {},
      projects: [{ id: uid(), name: "Личное", color: "#8E7CC3", order: 0, archived: false }, { id: uid(), name: "Работа", color: "#6FA4C9", order: 1, archived: false }, { id: uid(), name: "Дом", color: "#C4A77D", order: 2, archived: false }],
      hval: {}, measures: [], photos: [], wheel: [], remLog: [],
      recipes: [], favs: {}, menu: {}, menuPresets: [], menuRecur: [], shopping: [], cookLog: [],
      cats: { out: A.EXP_CATS.map((n, i) => ({ name: n, color: A.PAL[i % A.PAL.length] })), in: A.INC_CATS.map((n, i) => ({ name: n, color: A.PAL[(i + 5) % A.PAL.length] })) },
      importLog: [], english: { box: {}, quiz: [], notes: "", lessons: {} }, latin: { box: {}, quiz: [], notes: "", lessons: {} }, spanish: { box: {}, quiz: [], notes: "", lessons: {} }, french: { box: {}, quiz: [], notes: "", lessons: {} }
    };
  };

  A.migrate = (db) => {
    const def = A.seed();
    for (const k of Object.keys(def)) if (db[k] == null) db[k] = def[k];
    for (const k of Object.keys(def.profile)) if (db.profile[k] == null) db.profile[k] = def.profile[k];
    for (const k of Object.keys(def.profile.notif)) if (db.profile.notif[k] == null) db.profile.notif[k] = true;
    for (const k of Object.keys(def.profile.weights)) if (db.profile.weights[k] == null) db.profile.weights[k] = def.profile.weights[k];
    for (const k of Object.keys(def.profile.body)) if (db.profile.body[k] === undefined) db.profile.body[k] = def.profile.body[k];
    if (!db.__icons) {
      const M = { "🏋": "dumbbell", "💧": "droplet", "🍎": "apple", "📖": "book-open", "🎹": "piano", "🃏": "wand-sparkles", "☯": "orbit", "🌿": "leaf", "🏠": "house", "❤": "heart", "👥": "users", "💰": "wallet", "🌙": "moon", "☀": "sun", "✨": "sparkles", "🎨": "palette", "💳": "credit-card", "💵": "banknote", "🏦": "landmark", "🪙": "coins" };
      const fix = (v) => { const k = String(v || "").replace(/\ufe0f/g, ""); return M[k] ? "{{i:" + M[k] + "}}" : v; };
      (db.habits || []).forEach((h) => (h.icon = fix(h.icon)));
      (db.hobbies || []).forEach((h) => (h.icon = fix(h.icon)));
      (db.accounts || []).forEach((a) => { if (a.emoji) a.emoji = fix(a.emoji); });
      db.__icons = 1;
    }
    if (!db.profile.cards.includes("dash")) db.profile.cards.unshift("dash");
    if (db.profile.cards.includes("tasks") && !db.profile.hidden.includes("tasks") && !db.profile.__t5) { db.profile.hidden.push("tasks"); db.profile.__t5 = 1; }
    ["weight", "tasks5", "facts", "esoteric"].forEach((c) => { if (!db.profile.cards.includes(c)) db.profile.cards.splice(2, 0, c); });
    if (!db.profile.cards.includes("culture")) { const i = db.profile.cards.indexOf("tasks5"); db.profile.cards.splice(i >= 0 ? i + 1 : 2, 0, "culture"); }
    if (!db.profile.cards.includes("pianotutor")) { const i = db.profile.cards.indexOf("word"); db.profile.cards.splice(i >= 0 ? i + 1 : 2, 0, "pianotutor"); }
    if (!db.profile.cards.includes("word")) { const i = db.profile.cards.indexOf("culture"); db.profile.cards.splice(i >= 0 ? i + 1 : 2, 0, "word"); }
    def.profile.cards.forEach((c) => { if (!db.profile.cards.includes(c)) db.profile.cards.push(c); });
    db.v = 1;
    return db;
  };

  /* База продуктов на 100 г: название, ккал, белки, жиры, углеводы, клетчатка */
  A.FOOD_BASE = [
    ["Овсяные хлопья (сухие)", 352, 12.3, 6.1, 59.5, 8], ["Гречка (сухая)", 313, 12.6, 3.3, 62.1, 10], ["Рис белый (сухой)", 344, 6.7, 0.7, 78.9, 0.4],
    ["Гречка варёная", 110, 4.2, 1.1, 21.3, 2.7], ["Рис варёный", 116, 2.2, 0.5, 24.9, 0.4], ["Макароны варёные", 112, 3.5, 0.4, 23.2, 1.8],
    ["Хлеб цельнозерновой", 247, 13, 3.4, 41, 7], ["Хлеб белый", 265, 8.1, 3.2, 49, 2.7], ["Картофель варёный", 82, 2, 0.4, 16.7, 1.4],
    ["Куриная грудка (варёная)", 137, 29.8, 1.8, 0.5, 0], ["Индейка (филе)", 114, 23.6, 1.5, 0, 0], ["Говядина (варёная)", 254, 25.8, 16.8, 0, 0],
    ["Лосось", 208, 20.4, 13.4, 0, 0], ["Треска", 78, 17.7, 0.7, 0, 0], ["Яйцо куриное", 157, 12.7, 11.5, 0.7, 0],
    ["Творог 5%", 121, 17.2, 5, 1.8, 0], ["Йогурт греческий 2%", 73, 9.9, 2, 3.9, 0], ["Кефир 2,5%", 53, 2.9, 2.5, 4, 0],
    ["Молоко 2,5%", 52, 2.8, 2.5, 4.7, 0], ["Сыр твёрдый", 350, 25, 27, 0, 0], ["Тофу", 76, 8, 4.8, 1.9, 0.3],
    ["Чечевица варёная", 116, 9, 0.4, 20, 7.9], ["Нут варёный", 164, 8.9, 2.6, 27.4, 7.6], ["Фасоль варёная", 127, 8.7, 0.5, 22.8, 6.4],
    ["Огурец", 15, 0.8, 0.1, 2.8, 0.7], ["Помидор", 20, 1.1, 0.2, 3.7, 1.2], ["Брокколи", 34, 2.8, 0.4, 6.6, 2.6], ["Морковь", 35, 1.3, 0.1, 6.9, 2.4],
    ["Салат листовой", 15, 1.4, 0.2, 2.9, 1.3], ["Яблоко", 47, 0.4, 0.4, 9.8, 1.8], ["Банан", 96, 1.5, 0.2, 21.8, 2.6], ["Апельсин", 43, 0.9, 0.2, 8.1, 2.2],
    ["Ягоды (черника)", 57, 0.7, 0.3, 14.5, 2.4], ["Авокадо", 160, 2, 14.7, 1.8, 6.7], ["Грецкий орех", 654, 15.2, 65.2, 7, 6.7], ["Миндаль", 609, 18.6, 53.7, 13, 12.5],
    ["Оливковое масло", 898, 0, 99.8, 0, 0], ["Сливочное масло", 748, 0.5, 82.5, 0.8, 0], ["Мёд", 329, 0.8, 0, 80.3, 0], ["Шоколад тёмный 70%", 598, 7.8, 42.6, 45.9, 10.9],
    ["Кофе с молоком (капучино)", 40, 2.2, 2, 3.2, 0], ["Сок апельсиновый", 45, 0.7, 0.2, 10.4, 0.2]
  ];

  /* ---------- повторяющиеся события ---------- */
  A.eventOn = (e, d) => {
    if (e.date && d < e.date && e.repeat !== "none") return false;
    if (e.until && d > e.until) return false;
    if ((e.skip || []).includes(d)) return false;
    switch (e.repeat) {
      case "daily": return true;
      case "weekdays": return (e.days || []).includes(dow(d));
      case "weekly": return dow(d) === dow(e.date);
      case "monthly": return parse(d).getDate() === parse(e.date).getDate();
      default: return e.date === d;
    }
  };
  A.eventsOn = (d) => A.col("events").filter((e) => A.eventOn(e, d)).sort((a, b) => (hm2min(a.start) ?? 0) - (hm2min(b.start) ?? 0));
  A.eventMin = (e) => { const a = hm2min(e.start), b = hm2min(e.end); if (a == null || b == null) return 0; let d = b - a; if (d < 0) d += 1440; return d; };

  /* ---------- привычки ---------- */
  A.habitPlanned = (h, d) => {
    if (h.archived) return false;
    if (h.start && d < h.start) return false;
    if (h.end && d > h.end) return false;
    if (h.freq === "daily") return true;
    if (h.freq === "weekdays") return (h.days || []).includes(dow(d));
    return true; // "Nраз в неделю" — планируется ежедневно по желанию
  };
  A.hs = (hid, d) => A.db().hlog[hid + "|" + d] || null;
  A.setHs = (hid, d, st) => { const k = hid + "|" + d; if (!st) delete A.db().hlog[k]; else A.db().hlog[k] = st; A.save(); };
  A.cycleHs = (h, d) => {
    const order = [null, "done", "part", "skip", "none"];
    const cur = A.hs(h.id, d);
    const nx = order[(order.indexOf(cur) + 1) % order.length];
    A.setHs(h.id, d, nx);
    return nx;
  };
  A.habitStreak = (h, upto = today()) => {
    let n = 0, d = upto;
    if (A.hs(h.id, d) !== "done" && A.hs(h.id, d) !== "part") d = addDays(d, -1);
    for (let i = 0; i < 1000; i++) {
      const st = A.hs(h.id, d);
      if (st === "done" || st === "part") n++;
      else if (st === "none" || !A.habitPlanned(h, d)) { /* не ломает серию */ }
      else break;
      d = addDays(d, -1);
      if (h.start && d < h.start) break;
    }
    return n;
  };
  A.bestStreak = (h) => {
    let best = 0, cur = 0;
    const keys = Object.keys(A.db().hlog).filter((k) => k.startsWith(h.id + "|")).map((k) => k.split("|")[1]).sort();
    if (!keys.length) return 0;
    for (const d of A.range(keys[0], today())) {
      const st = A.hs(h.id, d);
      if (st === "done" || st === "part") { cur++; best = Math.max(best, cur); } else if (st === "none" || !A.habitPlanned(h, d)) { /* пропуск по плану */ } else cur = 0;
    }
    return best;
  };
  A.habitStats = (h, from, to) => {
    let plan = 0, done = 0;
    for (const d of A.range(from, to)) {
      if (!A.habitPlanned(h, d)) continue;
      const st = A.hs(h.id, d);
      if (st === "none") continue;
      plan++;
      if (st === "done") done += 1; else if (st === "part") done += 0.5;
    }
    if (h.freq === "xweek") { const days = A.range(from, to).length; plan = Math.max(1, Math.round((h.perWeek || 3) * days / 7)); done = Math.min(done, plan); }
    return { plan, done, pct: plan ? done / plan : null };
  };
  A.habitsDayPct = (d) => {
    let plan = 0, done = 0, any = false;
    A.col("habits").forEach((h) => {
      if (!A.habitPlanned(h, d)) return;
      const st = A.hs(h.id, d);
      if (st) any = true;
      if (st === "none") return;
      plan++;
      if (st === "done") done++; else if (st === "part") done += 0.5;
    });
    return any && plan ? done / plan : null;
  };

  /* ---------- показатели дня ---------- */
  A.sleepH = (d) => { const s = (A.dayGet(d) || {}).sleep; if (!s) return null; if (s.hours) return s.hours; return durH(s.asleep || s.bed, s.wake); };
  A.energyAvg = (d) => { const e = (A.dayGet(d) || {}).energy; if (!e) return null; return avg([e.m, e.d, e.e].filter((x) => x != null)); };
  A.anxVal = (d) => { const a = (A.dayGet(d) || {}).anx; if (!a || !a.length) return null; return Math.max(...a.map((x) => x.v)); };
  A.kcalDay = (d) => { const m = A.col("meals").filter((x) => x.date === d); return m.length ? sum(m.map((x) => x.kcal)) : null; };
  A.macrosDay = (d) => { const m = A.col("meals").filter((x) => x.date === d); return { kcal: sum(m.map((x) => x.kcal)), p: sum(m.map((x) => x.p)), f: sum(m.map((x) => x.f)), c: sum(m.map((x) => x.c)), fib: sum(m.map((x) => x.fib)), n: m.length }; };
  A.workoutMin = (d) => { const w = A.col("workouts").filter((x) => x.date === d); return w.length ? sum(w.map((x) => x.dur || 0)) : null; };
  A.pagesDay = (d) => { const r = A.col("rsess").filter((x) => x.date === d); return r.length ? sum(r.map((x) => x.pages || 0)) : null; };
  A.readMinDay = (d) => { const r = A.col("rsess").filter((x) => x.date === d); return r.length ? sum(r.map((x) => x.min || 0)) : null; };
  A.pianoMinDay = (d) => { const r = A.col("psess").filter((x) => x.date === d); return r.length ? sum(r.map((x) => x.min || 0)) : null; };
  A.learnMinDay = (d) => { const r = A.col("learn").filter((x) => x.date === d); const p = A.pianoMinDay(d) || 0; const v = sum(r.map((x) => x.min || 0)) + p; return r.length || p ? v : null; };
  A.spendDay = (d) => { const r = A.col("tx").filter((x) => x.date === d && x.kind === "out"); return r.length ? sum(r.map((x) => x.amt)) : null; };
  A.freeMinDay = (d) => { const ev = A.eventsOn(d).filter((e) => ["rest", "hobby", "family"].includes(e.cat)); return sum(ev.map(A.eventMin)); };
  A.hobbyMinDay = (d) => { const r = A.col("hsess").filter((x) => x.date === d); return r.length ? sum(r.map((x) => x.min || 0)) : null; };

  // Все показатели для аналитики и матриц.
  A.METRICS = {
    sleep: { name: "Сон, ч", get: A.sleepH },
    sleepQ: { name: "Качество сна (1–5)", get: (d) => ((A.dayGet(d) || {}).sleep || {}).q ?? null },
    mood: { name: "Настроение", get: (d) => (A.dayGet(d) || {}).mood ?? null },
    weight: { name: "Вес, кг", get: (d) => (A.dayGet(d) || {}).weight ?? null },
    burn: { name: "Потрачено ккал", get: (d) => A.burnDay(d) || null },
    energy: { name: "Энергия", get: A.energyAvg },
    stress: { name: "Стресс", get: (d) => (A.dayGet(d) || {}).stress ?? null },
    anx: { name: "Тревожность", get: A.anxVal },
    steps: { name: "Шаги", get: (d) => (A.dayGet(d) || {}).steps ?? null },
    water: { name: "Вода, мл", get: (d) => (A.dayGet(d) || {}).water ?? null },
    workout: { name: "Тренировки, мин", get: A.workoutMin },
    kcal: { name: "Калории", get: A.kcalDay },
    pages: { name: "Страницы", get: A.pagesDay },
    read: { name: "Чтение, мин", get: A.readMinDay },
    piano: { name: "Пианино, мин", get: A.pianoMinDay },
    learn: { name: "Обучение, мин", get: A.learnMinDay },
    spend: { name: "Расходы", get: A.spendDay },
    temp: { name: "Температура воздуха", get: (d) => { const w = (A.dayGet(d) || {}).weather; return w && w.t != null ? w.t : null; } },
    habits: { name: "Привычки, %", get: (d) => { const p = A.habitsDayPct(d); return p == null ? null : Math.round(p * 100); } },
    free: { name: "Свободное время, мин", get: (d) => A.freeMinDay(d) || null },
    hobby: { name: "Хобби, мин", get: A.hobbyMinDay }
  };

  /* ---------- финансы ---------- */
  A.expCats = () => (A.db().cats && A.db().cats.out.length ? A.db().cats.out.map((c) => c.name) : A.EXP_CATS);
  A.incCats = () => (A.db().cats && A.db().cats.in.length ? A.db().cats.in.map((c) => c.name) : A.INC_CATS);
  A.catColor = (name) => { const c = A.db().cats; const x = c && (c.out.concat(c.in)).find((k) => k.name === name); return x ? x.color : "#A9A3B5"; };
  A.accBalance = (acc) => { let b = +acc.start || 0; A.col("tx").forEach((t) => { if (t.acc === acc.id) b += t.kind === "in" ? t.amt : -t.amt; if (t.kind === "move" && t.to === acc.id) b += t.amt; }); return b; };
  A.monthTx = (mk) => A.col("tx").filter((t) => t.date.slice(0, 7) === mk);
  // Регулярные платежи: создаём будущие записи, когда наступил срок (если включено авто), иначе напоминаем.
  A.nextMonthly = (d, dom) => { const x = parse(d); x.setDate(1); x.setMonth(x.getMonth() + 1); const n = A.daysInMonth(x.getFullYear(), x.getMonth() + 1); x.setDate(Math.min(dom, n)); return iso(x); };
  A.processRecurring = () => {
    const t = today(); let made = 0;
    A.col("recurring").forEach((r) => {
      if (!r.active || !r.next) return;
      let guard = 0;
      while (r.auto && r.next <= t && guard++ < 24) {
        A.col("tx").push({ id: uid(), date: r.next, amt: +r.amt || 0, kind: r.kind || "out", cat: r.cat || "Подписки", sub: r.name, acc: r.acc || "", note: "Регулярный платёж", recurId: r.id });
        r.next = r.period === "year" ? addDays(r.next, 365) : r.period === "week" ? addDays(r.next, 7) : A.nextMonthly(r.next, r.dom || parse(r.next).getDate());
        made++;
      }
    });
    if (made) A.save();
    return made;
  };

  /* ---------- напоминания → Android ---------- */
  A.syncReminders = () => {
    if (!A.native) return;
    const p = A.db().profile, n = p.notif || {};
    const out = [];
    const push = (r) => { if (out.length < 400) out.push(r); };
    if (n.general !== false) A.col("reminders").forEach((r) => {
      if (!r.on) return;
      if (r.kind && n[r.kind] === false) return;
      push({ id: "r:" + r.id, route: "reminders", title: r.title, text: r.text || "", type: r.type === "weekly" ? "weekdays" : r.type, time: r.time || "09:00", date: r.date || "", days: r.type === "weekly" ? [r.days && r.days[0] || 1] : r.days || [], dom: r.dom || 1 });
    });
    if (n.habits !== false) A.col("habits").forEach((h) => {
      if (!h.remind || !h.time || h.archived) return;
      push({ id: "h:" + h.id, route: "habits", title: (h.icon || "") + " " + h.name, text: h.min ? "Минимальная версия: " + h.min : "Время для привычки", type: h.freq === "weekdays" ? "weekdays" : "daily", time: h.time, days: h.days || [] });
    });
    if (n.events !== false) {
      const t = today();
      for (let i = 0; i < 21; i++) {
        const d = addDays(t, i);
        A.eventsOn(d).forEach((e) => {
          if (e.remind == null || e.remind === "" || !e.start) return;
          const kindOff = (e.cat === "sport" && n.workouts === false) || (e.cat === "learn" && n.study === false);
          if (kindOff) return;
          const m = hm2min(e.start) - (+e.remind || 0);
          let dd = d, mm = m; if (mm < 0) { dd = addDays(d, -1); mm += 1440; }
          push({ id: "e:" + e.id + ":" + d, route: "calendar/day?d=" + d, title: A.cat(e.cat).icon + " " + e.title, text: (+e.remind ? "Через " + A.fmtDur(+e.remind) : "Сейчас") + " · " + e.start + (e.end ? "–" + e.end : ""), type: "once", date: dd, time: A.min2hm(mm) });
        });
      }
    }
    if (n.payments !== false) A.col("recurring").forEach((r) => {
      if (!r.active || !r.next) return;
      const nd = +r.notifyDays || 0, when = addDays(r.next, -nd);
      push({ id: "p:" + r.id + ":" + r.next, route: "money/rec", title: (r.kind === "in" ? "{{i:wallet}} Поступление: " : "{{i:credit-card}} Платёж: ") + r.name, text: A.money(r.amt) + (nd ? " — через " + nd + " " + A.plural(nd, "день", "дня", "дней") + (r.auto ? " (запишется автоматически)" : "") : " — срок сегодня"), type: "once", date: when, time: "10:00" });
    });
    A.col("tasks").forEach((t) => {
      if (t.done || !t.date || !t.time || !t.remind) return;
      push({ id: "t:" + t.id, route: "tasks", title: "✓ " + t.title, text: t.minVer ? "Минимальная версия: " + t.minVer : "Задача на " + t.time, type: "once", date: t.date, time: t.time });
    });
    out.forEach((r) => { r.title = A.stripIcons(r.title); r.text = A.stripIcons(r.text); });
    try { A.native.scheduleReminders(JSON.stringify(out)); } catch (e) { console.error(e); }
  };

  /* ---------- PRIME SCORE ---------- */
  A.PS_AREAS = [
    ["sleep", "Сон", "часы сна в пределах цели"], ["move", "Движение", "шаги ÷ цель"], ["food", "Питание", "калории близки к цели (±10% = 100)"],
    ["workout", "Тренировки", "тренировок за 7 дней ÷ цель в неделю"], ["habits", "Привычки", "выполнено ÷ запланировано (частично = ½)"],
    ["learn", "Обучение", "минуты обучения и пианино ÷ 30"], ["money", "Финансы", "расходы записаны и бюджеты не превышены"],
    ["culture", "Культура", "чтение/кино/сериалы: 20 мин или 10 страниц = 100"], ["rest", "Отдых", "ощущение восстановления после сна ÷ 10"],
    ["goals", "Цели", "главное дело дня выполнено"]
  ];
  A.psArea = (k, d) => {
    const p = A.db().profile, day = A.dayGet(d) || {};
    switch (k) {
      case "sleep": { const h = A.sleepH(d); if (h == null) return null; if (h >= p.sleepMin && h <= p.sleepMax + 0.5) return 100; return Math.round(100 * Math.max(0, 1 - Math.abs(h < p.sleepMin ? p.sleepMin - h : h - p.sleepMax) / 3)); }
      case "move": return day.steps == null ? null : Math.round(100 * Math.min(1, day.steps / (p.stepsGoal || 8000)));
      case "food": { const k2 = A.kcalDay(d); if (k2 == null) return null; const dev = Math.abs(k2 - p.kcal) / p.kcal; return Math.round(100 * (dev <= 0.1 ? 1 : Math.max(0, 1 - (dev - 0.1) * 2.5))); }
      case "workout": { if (!A.col("workouts").some((w) => w.date <= d)) return null; const n = A.col("workouts").filter((w) => w.date <= d && w.date > addDays(d, -7)).length; return Math.round(100 * Math.min(1, n / (p.workoutsWeek || 3))); }
      case "habits": { const v = A.habitsDayPct(d); return v == null ? null : Math.round(v * 100); }
      case "learn": { const m = A.learnMinDay(d); return m == null ? null : Math.round(100 * Math.min(1, m / 30)); }
      case "money": {
        const txs = A.col("tx").filter((t) => t.date === d);
        const b = A.db().budgets, mk = d.slice(0, 7), keys = Object.keys(b).filter((c) => b[c] > 0);
        if (!txs.length && !keys.length) return null;
        let s = txs.length ? 60 : 30;
        if (keys.length) { const over = keys.filter((c) => sum(A.monthTx(mk).filter((t) => t.kind === "out" && t.cat === c && t.date <= d).map((t) => t.amt)) > b[c]).length; s += Math.round(40 * (1 - over / keys.length)); } else s += 40;
        return s;
      }
      case "culture": { const r = A.readMinDay(d) || 0, pg = A.pagesDay(d) || 0; const mv = A.col("movies").filter((m) => m.date === d).length; if (!r && !pg && !mv) return null; return Math.round(100 * Math.min(1, Math.max(r / 20, pg / 10, mv ? 1 : 0))); }
      case "rest": { const s = day.sleep; return s && s.rec != null ? s.rec * 10 : null; }
      case "goals": return day.main ? (day.mainDone ? 100 : 0) : null;
    }
    return null;
  };
  A.primeScore = (d) => {
    const w = A.db().profile.weights || {};
    let sw = 0, s = 0; const parts = [];
    A.PS_AREAS.forEach(([k, name]) => {
      const wt = +w[k] || 0; const v = A.psArea(k, d);
      parts.push({ k, name, w: wt, v });
      if (wt > 0 && v != null) { sw += wt; s += wt * v; }
    });
    return { score: sw ? Math.round(s / sw) : null, parts, sw };
  };

  /* ---------- корреляция ---------- */
  A.pearson = (xs, ys) => {
    const pairs = xs.map((x, i) => [x, ys[i]]).filter(([x, y]) => x != null && y != null && isFinite(x) && isFinite(y));
    const n = pairs.length;
    if (n < 3) return { r: null, n };
    const mx = avg(pairs.map((p) => p[0])), my = avg(pairs.map((p) => p[1]));
    let a = 0, b = 0, c = 0;
    pairs.forEach(([x, y]) => { a += (x - mx) * (y - my); b += (x - mx) ** 2; c += (y - my) ** 2; });
    if (!b || !c) return { r: null, n };
    return { r: a / Math.sqrt(b * c), n };
  };
  A.corrWords = (r) => {
    if (r == null) return "недостаточно данных";
    const a = Math.abs(r);
    const s = a < 0.1 ? "связь практически не наблюдается" : a < 0.3 ? "наблюдается слабая" : a < 0.5 ? "наблюдается умеренная" : "наблюдается заметная";
    return a < 0.1 ? s : s + (r > 0 ? " прямая связь" : " обратная связь");
  };

  /* ---------- серии дней для показателя ---------- */
  A.streakMetric = (fn) => { let n = 0, d = today(); if (!fn(d)) d = addDays(d, -1); while (fn(d) && n < 3650) { n++; d = addDays(d, -1); } return n; };
  A.statsOf = (vals) => { const v = vals.filter((x) => x != null); return v.length ? { avg: avg(v), min: Math.min(...v), max: Math.max(...v), n: v.length, sum: sum(v) } : { avg: null, min: null, max: null, n: 0, sum: 0 }; };

  /* ---------- тело, вес, энергозатраты ---------- */
  A.burnDay = (d) => {
    const w = A.col("workouts").filter((x) => x.date === d);
    const wk = A.sum(w.map((x) => +x.kcal || Math.round((+x.dur || 0) * (3 + (+x.int || 5) * 0.6))));
    const steps = (A.dayGet(d) || {}).steps || 0;
    const kg = A.lastWeight(d) || 65;
    return Math.round(wk + steps * 0.0005 * kg);
  };
  A.lastWeight = (d = today()) => { for (let i = 0; i < 400; i++) { const x = A.dayGet(addDays(d, -i)); if (x && x.weight) return x.weight; } const b = A.db().profile.body; return b.startWeight || null; };
  A.bmr = () => { const b = A.db().profile.body, w = A.lastWeight() || b.startWeight; if (!w || !b.height || !b.age) return null; return Math.round(10 * w + 6.25 * b.height - 5 * b.age + (b.sex === "m" ? 5 : -161)); };
  A.bodyTargets = () => {
    const b = A.db().profile.body, w = A.lastWeight() || b.startWeight, bmr = A.bmr();
    if (!bmr) return null;
    const tdee = Math.round(bmr * (+b.activity || 1.375));
    const delta = Math.round(((+b.monthChange || 0) * 7700) / 30); // 1 кг ≈ 7700 ккал
    const kcal = b.goal === "lose" ? tdee - Math.min(delta, Math.round(tdee * 0.25)) : b.goal === "gain" ? tdee + Math.min(delta, 500) : tdee;
    const prot = Math.round(w * (+b.protKg || 1.6)), fat = Math.round(w * (+b.fatKg || 0.9));
    const carb = Math.max(0, Math.round((kcal - prot * 4 - fat * 9) / 4));
    return { bmr, tdee, kcal, prot, fat, carb };
  };
  A.weightPoints = (from, to) => A.range(from, to).map((d) => ({ d, v: (A.dayGet(d) || {}).weight ?? null })).filter((x) => x.v != null);
  // Наклон линейной регрессии, кг в день.
  A.slope = (pts) => {
    if (pts.length < 2) return null;
    const xs = pts.map((p) => A.diffDays(pts[0].d, p.d)), ys = pts.map((p) => p.v);
    const mx = avg(xs), my = avg(ys); let a = 0, b = 0;
    xs.forEach((x, i) => { a += (x - mx) * (ys[i] - my); b += (x - mx) ** 2; });
    return b ? a / b : null;
  };
  A.weightTrend = (days = 14, end = today()) => {
    const pts = A.weightPoints(addDays(end, -(days - 1)), end);
    const k = A.slope(pts);
    if (k == null) return { arrow: "→", word: "мало данных", k: null, pts };
    const wk = k * 7;
    return { arrow: wk > 0.1 ? "↑" : wk < -0.1 ? "↓" : "→", word: wk > 0.1 ? "вес растёт" : wk < -0.1 ? "вес падает" : "вес стабилен", k, wk, pts };
  };
  A.weightForecast = () => {
    const tgt = A.db().profile.body.targetWeight, cur = A.lastWeight();
    const t = A.weightTrend(28);
    if (!tgt || !cur || t.k == null || Math.abs(t.k) < 0.002) return null;
    const days = (tgt - cur) / t.k;
    if (days <= 0 || days > 3650) return null;
    return addDays(today(), Math.round(days));
  };

  /* ---------- числовые привычки ---------- */
  A.hv = (hid, d) => A.db().hval[hid + "|" + d] ?? null;
  A.setHv = (h, d, v) => {
    const k = h.id + "|" + d;
    if (v == null || v === "" || isNaN(v)) { delete A.db().hval[k]; A.setHs(h.id, d, null); return; }
    A.db().hval[k] = +v;
    A.setHs(h.id, d, +v >= (+h.target || 1) ? "done" : +v > 0 ? "part" : "skip");
  };
  A.daysMask = (days) => (days || []).reduce((m, d) => m | (1 << (d - 1)), 0);

  /* ---------- настроение: несколько записей в день ---------- */
  A.addMood = (d, v, tags, note) => {
    const day = A.day(d);
    day.moods = day.moods || [];
    const now = A.nowHM(), last = day.moods[day.moods.length - 1];
    if (last && d === today() && Math.abs(hm2min(now) - hm2min(last.t)) < 30 && !tags && !note) last.v = v;
    else day.moods.push({ t: now, v, tags: tags || [], note: note || "" });
    day.mood = Math.round(avg(day.moods.map((m) => m.v)) * 10) / 10;
    A.save();
  };

  /* ---------- проекты и задачи ---------- */
  A.project = (id) => A.col("projects").find((p) => p.id === id) || null;
  A.PRIO = { 1: ["Высокий", "#D2555E"], 2: ["Средний", "#F0A45B"], 3: ["Низкий", "#7F9C7A"] };

  /* ---------- факты о достижениях ---------- */
  A.achievements = () => {
    const t = today(), w7 = A.lastDays(7), out = [];
    const water = A.sum(w7.map((d) => (A.dayGet(d) || {}).water || 0));
    if (water) out.push("{{i:droplet}} За 7 дней ты выпила " + (water / 1000).toFixed(1) + " л воды");
    const steps = A.sum(w7.map((d) => (A.dayGet(d) || {}).steps || 0));
    if (steps) out.push("{{i:footprints}} За неделю — " + A.fmtN(steps) + " шагов, это примерно " + A.fmtN(steps * 0.0007, 1) + " км");
    const done = A.col("tasks").filter((x) => x.done && (x.doneAt || "").slice(0, 10) >= w7[0]).length;
    if (done) out.push("{{i:circle-check}} Выполнено задач за неделю: " + done);
    A.col("habits").filter((h) => !h.archived).forEach((h) => { const s = A.habitStreak(h); if (s >= 3) out.push("{{i:flame}} Серия «" + h.name + "» — " + s + " " + A.plural(s, "день", "дня", "дней") + " подряд"); });
    const pages = A.sum(A.col("rsess").map((x) => +x.pages || 0));
    if (pages) out.push("{{i:book-open}} Всего прочитано " + A.fmtN(pages) + " страниц");
    const wk = A.col("workouts").length;
    if (wk) out.push("{{i:dumbbell}} Тренировок в журнале: " + wk);
    const pm = A.sum(A.col("psess").map((x) => x.min || 0));
    if (pm) out.push("{{i:piano}} На пианино уже " + A.fmtDur(pm) + " практики");
    const lw = A.weightTrend(30);
    if (lw.k != null && lw.pts.length > 2) { const ch = lw.pts[lw.pts.length - 1].v - lw.pts[0].v; out.push("{{i:scale}} За месяц вес изменился на " + (ch > 0 ? "+" : "") + ch.toFixed(1) + " кг"); }
    const mains = A.lastDays(30).filter((d) => (A.dayGet(d) || {}).mainDone).length;
    if (mains) out.push("{{i:target}} Главное дело дня выполнено " + mains + " раз за 30 дней");
    const cooked = A.col("cookLog").length;
    if (cooked) out.push("{{i:chef-hat}} Приготовлено блюд по рецептам: " + cooked);
    const tarot = Object.keys(A.db().tarot.daily).length;
    if (tarot) out.push("{{i:wand-sparkles}} Карт дня вытянуто: " + tarot);
    if (!out.length) out.push("{{i:sprout}} Каждая отметка здесь — маленький шаг. Первые записи появятся уже сегодня.", "{{i:heart}} Движение важнее, чем идеальность.");
    return out;
  };

  A.lastDays = (n, end = today()) => A.range(addDays(end, -(n - 1)), end);
  A.pad = pad;
})();
