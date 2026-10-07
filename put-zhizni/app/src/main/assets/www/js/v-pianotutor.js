/* Репетитор фортепиано — по образцу языкового: база знаний (клавиши, ноты в двух ключах, аккорды,
   интервалы на слух, гаммы, термины, ритм), порция нового в день, интервальные повторения (SM-2),
   тренировки, серия дней и опыт, карточка «Аккорд дня» на экране «Сегодня».
   Звук — синтезатор телефона (A.playNote из v-learn.js). */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays } = A;

  const NM = ["до", "до♯", "ре", "ре♯", "ми", "фа", "фа♯", "соль", "соль♯", "ля", "ля♯", "си"];
  const NMF = ["до", "ре♭", "ре", "ми♭", "ми", "фа", "соль♭", "соль", "ля♭", "ля", "си♭", "си"];
  const LT = ["C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"];
  const WHITE = [0, 2, 4, 5, 7, 9, 11];
  const DIA = ["до", "ре", "ми", "фа", "соль", "ля", "си"];
  const TOPICS = { keys: ["Клавиатура", "piano"], treble: ["Ноты: скрипичный ключ", "music"], bass: ["Ноты: басовый ключ", "music"], chords: ["Аккорды", "layers"], ear: ["Интервалы на слух", "headphones"], scales: ["Гаммы и тональности", "repeat"], terms: ["Термины", "book-a"], rhythm: ["Ритм и длительности", "timer"] };
  const LEVELS = [[1, "Начинающий"], [2, "Базовый"], [3, "Средний"], [4, "Продвинутый"]];

  /* ---------- база знаний ---------- */
  const CH = { "": ["мажор", [0, 4, 7], "Мажорное трезвучие: основной тон, большая терция (+4 полутона), чистая квинта (+7). Звучит светло и устойчиво."],
    m: ["минор", [0, 3, 7], "Минорное трезвучие: основной тон, малая терция (+3), чистая квинта (+7). От мажора отличается одной нотой — средней, на полутон ниже."],
    "7": ["доминантсептаккорд", [0, 4, 7, 10], "Мажорное трезвучие плюс малая септима (+10). Звучит напряжённо и «просится» разрешиться в аккорд на кварту выше."],
    maj7: ["большой мажорный септаккорд", [0, 4, 7, 11], "Мажор плюс большая септима (+11): мягкий, «джазовый» цвет."],
    m7: ["малый минорный септаккорд", [0, 3, 7, 10], "Минор плюс малая септима (+10): спокойный, частый в соуле и поп-балладах."],
    sus2: ["sus2", [0, 2, 7], "Терция заменена секундой (+2): ни мажор, ни минор — открытое, «воздушное» звучание."],
    sus4: ["sus4", [0, 5, 7], "Терция заменена квартой (+5): подвешенное звучание, которое обычно разрешается в мажор или минор."],
    dim: ["уменьшённое трезвучие", [0, 3, 6], "Две малые терции (+3, +6): тревожный, неустойчивый аккорд-связка."] };
  const IV = [[1, "малая секунда", 3], [2, "большая секунда", 2], [3, "малая терция", 1], [4, "большая терция", 1], [5, "чистая кварта", 2], [6, "тритон", 3], [7, "чистая квинта", 1], [8, "малая секста", 3], [9, "большая секста", 2], [10, "малая септима", 3], [11, "большая септима", 4], [12, "октава", 1]];
  const IVHINT = { 1: "самый тесный, «скрежещущий» шаг — начало темы из «Челюстей»", 2: "шаг гаммы — «до-ре»", 3: "грустный шаг — начало «Колыбельной» Брамса вниз, «Greensleeves» вверх", 4: "светлый шаг — начало «Oh, When the Saints»", 5: "призыв — «Вставай, проклятьем заклеймённый», «Here Comes the Bride»", 6: "неустойчивое «дьявольское» звучание — начало «Мария» из «Вестсайдской истории»", 7: "пустое, мощное звучание — начало темы «Звёздных войн»", 8: "напряжённо-нежный шаг — «The Entertainer» (мелодия с затактом)", 9: "широкий тёплый шаг — «My Bonnie Lies over the Ocean»", 10: "требовательный шаг — «Somewhere» из «Вестсайдской истории»", 11: "почти октава, режет слух — «Take On Me» (припев)", 12: "та же нота выше — «Somewhere over the Rainbow»" };
  const TERMS = [
    ["Allegro", "быстро, оживлённо", 1], ["Andante", "спокойно, шагом", 1], ["Adagio", "медленно", 1], ["Moderato", "умеренно", 2], ["Presto", "очень быстро", 2], ["Largo", "очень медленно, широко", 3],
    ["piano (p)", "тихо", 1], ["forte (f)", "громко", 1], ["mezzo piano (mp)", "умеренно тихо", 2], ["mezzo forte (mf)", "умеренно громко", 2], ["pianissimo (pp)", "очень тихо", 2], ["fortissimo (ff)", "очень громко", 2],
    ["crescendo (<)", "постепенно громче", 1], ["diminuendo (>)", "постепенно тише", 1], ["accelerando", "постепенно ускоряя", 3], ["ritardando (rit.)", "постепенно замедляя", 2], ["a tempo", "вернуться к прежнему темпу", 2],
    ["legato", "связно, без разрывов между нотами", 1], ["staccato", "отрывисто, коротко", 1], ["акцент (>)", "выделить ноту громкостью", 2], ["фермата", "задержать ноту дольше, чем написано", 2], ["тенуто", "выдержать ноту на всю длительность", 3],
    ["диез (♯)", "повышает ноту на полутон", 1], ["бемоль (♭)", "понижает ноту на полутон", 1], ["бекар (♮)", "отменяет диез или бемоль", 1], ["знаки при ключе", "диезы или бемоли, действующие на всё произведение", 2], ["затакт", "неполный такт перед первой сильной долей", 3],
    ["реприза", "знак повтора фрагмента", 2], ["вольта", "разные окончания при повторе", 3], ["D.C. al Fine", "повторить с начала до слова Fine", 3], ["кода", "заключительный раздел пьесы", 3],
    ["аппликатура", "цифры над нотами: каким пальцем играть", 1], ["педаль (Ped.)", "правая педаль: звуки продолжают звучать после отпускания клавиш", 2], ["арпеджио", "звуки аккорда по очереди, а не вместе", 2], ["октава (8va)", "играть на октаву выше написанного", 3],
    ["тоника", "главная, устойчивая ступень тональности", 2], ["доминанта", "пятая ступень, создаёт напряжение и тянет к тонике", 3], ["субдоминанта", "четвёртая ступень, мягкое отдаление от тоники", 3], ["каденция", "оборот, завершающий фразу", 4], ["модуляция", "переход в другую тональность", 4],
    ["полутон", "соседние клавиши, включая чёрные", 1], ["тон", "два полутона — через одну клавишу", 1], ["трезвучие", "аккорд из трёх звуков по терциям", 1], ["обращение аккорда", "те же ноты аккорда, но внизу не основной тон", 3], ["синкопа", "акцент на слабую долю", 3]];
  const RHY = [["целая нота", "4 доли в размере 4/4", 1], ["половинная нота", "2 доли", 1], ["четвертная нота", "1 доля", 1], ["восьмая нота", "половина доли, две на счёт «раз-и»", 1], ["шестнадцатая нота", "четверть доли, четыре на одну долю", 2],
    ["половинная с точкой", "3 доли — точка добавляет половину длительности", 2], ["четвертная с точкой", "1,5 доли", 2], ["триоль", "три равные ноты вместо двух", 3], ["пауза", "длительность тишины того же размера, что и нота", 1],
    ["размер 4/4", "четыре четвертные доли в такте, сильная — первая", 1], ["размер 3/4", "три доли — вальс", 1], ["размер 2/4", "две доли — марш", 2], ["размер 6/8", "шесть восьмых, ощущается как два крупных шага", 3], ["лига", "соединяет две одинаковые ноты в одну длительность", 2]];
  const MAJ = [0, 2, 4, 5, 7, 9, 11], MIN = [0, 2, 3, 5, 7, 8, 10];
  const SIG = { 0: "без знаков", 7: "1 диез (фа♯)", 2: "2 диеза (фа♯, до♯)", 9: "3 диеза", 4: "4 диеза", 11: "5 диезов", 6: "6 диезов (фа♯ мажор) или 6 бемолей (соль♭ мажор)", 5: "1 бемоль (си♭)", 10: "2 бемоля (си♭, ми♭)", 3: "3 бемоля", 8: "4 бемоля", 1: "5 бемолей" };
  // правописание по ступеням: третья ступень — всегда «через букву» (до–ми, а не до–ре♯), септима до7 — си♭
  const LPC = [0, 2, 4, 5, 7, 9, 11], RU = ["до", "ре", "ми", "фа", "соль", "ля", "си"], EN = ["C", "D", "E", "F", "G", "A", "B"];
  const DEG = { 0: 0, 1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 4, 7: 4, 8: 5, 9: 5, 10: 6, 11: 6 };
  const ROOT = { 0: [0, 0], 1: [1, -1], 2: [1, 0], 3: [2, -1], 4: [2, 0], 5: [3, 0], 6: [3, 1], 7: [4, 0], 8: [5, -1], 9: [5, 0], 10: [6, -1], 11: [6, 0] };
  const ROOT_MIN = Object.assign({}, ROOT, { 1: [0, 1], 6: [3, 1], 8: [4, 1] }); // до♯, фа♯, соль♯ минор
  const ACC = { "-2": "𝄫", "-1": "♭", 0: "", 1: "♯", 2: "𝄪" };
  const spell = (pc, minor, semis, steps) => {
    const [li] = (minor ? ROOT_MIN : ROOT)[pc], l2 = (li + (steps == null ? DEG[semis] : steps)) % 7;
    const diff = ((((pc + semis) % 12) - LPC[l2] + 18) % 12) - 6;
    return { ru: RU[l2] + ACC[diff], en: EN[l2] + ACC[diff] };
  };

  const ITEMS = (() => {
    const out = [];
    // клавиатура
    for (let pc = 0; pc < 12; pc++) out.push({ id: "k" + pc, t: "keys", lv: WHITE.includes(pc) ? 1 : 2, title: NM[pc] + (WHITE.includes(pc) ? "" : " / " + NMF[pc]), ans: "латинское обозначение: " + (WHITE.includes(pc) ? EN[LPC.indexOf(pc)] : EN[LPC.indexOf(pc - 1)] + "♯ / " + EN[LPC.indexOf(pc + 1)] + "♭"), midi: [60 + pc], pc,
      ex: WHITE.includes(pc) ? (pc === 0 ? "Белая клавиша слева от группы из двух чёрных." : pc === 5 ? "Белая клавиша слева от группы из трёх чёрных." : "Белая клавиша: считайте от «до» — до, ре, ми, фа, соль, ля, си.") : "Чёрная клавиша: " + NM[pc - 1] + " с диезом = " + NMF[pc + 1 > 11 ? 0 : pc + 1] + " с бемолем." });
    // ноты на стане
    for (let i = 0; i <= 12; i++) out.push({ id: "t" + i, t: "treble", lv: i >= 2 && i <= 10 ? 1 : 2, title: DIA[i % 7] + " " + (i < 7 ? "первой" : "второй") + " октавы", ans: DIA[i % 7], staff: ["treble", i], midi: [60 + Math.floor(i / 7) * 12 + MAJ[i % 7]], ex: i === 0 ? "На первой добавочной линейке снизу." : "Скрипичный ключ: нижняя линейка — ми первой октавы, ключ обвивает линейку соль." });
    for (let i = 2; i <= 14; i++) out.push({ id: "b" + i, t: "bass", lv: i >= 4 && i <= 12 ? 2 : 3, title: DIA[i % 7] + " " + (i < 7 ? "большой" : i < 14 ? "малой" : "первой") + " октавы", ans: DIA[i % 7], staff: ["bass", i], midi: [36 + Math.floor(i / 7) * 12 + MAJ[i % 7]], ex: "Басовый ключ: нижняя линейка — соль большой октавы, точки ключа окружают линейку фа." });
    // аккорды
    for (let pc = 0; pc < 12; pc++) Object.keys(CH).forEach((q) => {
      const [qn, iv, ex] = CH[q], mn = q === "m" || q === "m7" || q === "dim", r0 = spell(pc, mn, 0), name = r0.en + q;
      const easy = ["C", "F", "G", "Am", "Dm", "Em"].includes(name), basic = WHITE.includes(pc) && (q === "" || q === "m");
      out.push({ id: "c" + pc + q, t: "chords", lv: easy ? 1 : basic ? 2 : q === "" || q === "m" || q === "7" || q.startsWith("sus") ? 3 : 4, title: name + " — " + r0.ru + " " + qn, ans: iv.map((x) => spell(pc, mn, x).ru).join(" – "), midi: iv.map((x) => 60 + pc + x), ex });
    });
    // интервалы
    IV.forEach(([n, name, lv]) => out.push({ id: "i" + n, t: "ear", lv, title: name, ans: name, midi: [60, 60 + n], seq: true, ex: n + " " + A.plural(n, "полутон", "полутона", "полутонов") + ". Запомнить на слух: " + IVHINT[n] + "." }));
    // гаммы
    for (let pc = 0; pc < 12; pc++) {
      const r0 = spell(pc, false, 0);
      out.push({ id: "sM" + pc, t: "scales", lv: [0, 7, 5].includes(pc) ? 2 : WHITE.includes(pc) ? 3 : 4, title: r0.ru + " мажор (" + r0.en + ")", ans: MAJ.map((x, i) => spell(pc, false, x, i).ru).join(" "), midi: MAJ.concat([12]).map((x) => 60 + pc + x), seq: true, ex: "Формула мажора: тон-тон-полутон-тон-тон-тон-полутон. Ключевые знаки: " + SIG[pc] + "." });
      const rel = (pc + 3) % 12, m0 = spell(pc, true, 0), relN = spell(pc, true, 3, 2);
      out.push({ id: "sm" + pc, t: "scales", lv: [9, 4, 2].includes(pc) ? 3 : 4, title: m0.ru + " минор (" + m0.en + "m)", ans: MIN.map((x, i) => spell(pc, true, x, i).ru).join(" "), midi: MIN.concat([12]).map((x) => 60 + pc + x), seq: true, ex: "Натуральный минор: тон-полутон-тон-тон-полутон-тон-тон. Параллельный мажор — " + relN.ru + " мажор, знаки те же: " + SIG[rel] + "." });
    }
    TERMS.forEach(([w, a, lv], i) => out.push({ id: "w" + i, t: "terms", lv, title: w, ans: a, ex: "" }));
    RHY.forEach(([w, a, lv], i) => out.push({ id: "r" + i, t: "rhythm", lv, title: w, ans: a, ex: "" }));
    return out;
  })();
  const BY = {}; ITEMS.forEach((x) => (BY[x.id] = x));

  /* ---------- состояние ---------- */
  const st = () => {
    const db = A.db();
    const t = (db.ptutor = db.ptutor || {});
    t.srs = t.srs || {}; t.known = t.known || {}; t.xp = t.xp || {};
    t.cfg = Object.assign({ level: 1, topic: "", n: 5 }, t.cfg || {});
    return t;
  };
  const addXp = (n) => { const t = st(); t.xp[today()] = (t.xp[today()] || 0) + n; };
  const streak = () => { const x = st().xp; let n = 0, d = today(); if (!x[d]) d = addDays(d, -1); while (x[d]) { n++; d = addDays(d, -1); } return n; };
  const grade = (id, q) => {
    const S = st().srs, c = S[id] || { e: 2.5, i: 0, n: 0 };
    if (q < 3) { c.n = 0; c.i = 1; c.d = today(); }
    else { c.n++; c.i = c.n === 1 ? 1 : c.n === 2 ? 3 : Math.round(c.i * c.e * (q === 5 ? 1.3 : 1)); c.e = Math.max(1.3, c.e + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))); c.d = addDays(today(), c.i); }
    S[id] = c; addXp(q >= 3 ? 2 : 1); A.save();
  };
  const startItem = (id) => { const S = st().srs; if (!S[id]) { S[id] = { e: 2.5, i: 0, n: 0, d: today() }; addXp(5); A.save(); } };
  const due = () => Object.keys(st().srs).filter((id) => BY[id] && st().srs[id].d <= today());
  const pool = () => { const t = st(), c = t.cfg; return ITEMS.filter((x) => x.lv <= c.level && !t.srs[x.id] && !t.known[x.id] && (!c.topic || x.t === c.topic)).sort((a, b) => a.lv - b.lv); };
  const daily = () => {
    const t = st(), c = t.cfg, sig = c.level + c.topic + c.n;
    if (!t.daily || t.daily.date !== today() || t.daily.sig !== sig) {
      const keep = t.daily && t.daily.date === today() ? t.daily.done : [];
      // по одному элементу из разных тем по кругу — так день не превращается в одну тему
      const p = pool(), groups = {}; p.forEach((x) => (groups[x.t] = groups[x.t] || []).push(x));
      const ids = []; let k = 0; const keys = Object.keys(groups);
      while (ids.length < c.n - keep.length && keys.some((g) => groups[g].length)) { const g = keys[k++ % keys.length]; if (groups[g].length) ids.push(groups[g].shift().id); }
      t.daily = { date: today(), sig, ids: keep.concat(ids), done: keep.slice() }; A.save();
    }
    return t.daily;
  };

  /* ---------- графика ---------- */
  // маленькая клавиатура (2 октавы от до первой), подсвечивает ноты
  const miniKb = (lit, from = 60) => {
    const w = 14, h = 56, L = new Set((lit || []).map((m) => m)); let s = '<svg viewBox="0 0 ' + (w * 15) + " " + h + '" class="pt-kb" role="img" aria-label="Клавиатура">'; let x = 0; const blacks = [];
    for (let m = from; m < from + 25; m++) { const pc = m % 12; if (WHITE.includes(pc)) { s += '<rect x="' + x + '" y="0" width="' + w + '" height="' + h + '" rx="2" class="' + (L.has(m) ? "on" : "w") + '"/>'; x += w; } else blacks.push([x - w * 0.32, m]); }
    blacks.forEach(([bx, m]) => { s += '<rect x="' + bx + '" y="0" width="' + w * 0.64 + '" height="' + h * 0.6 + '" rx="1.5" class="' + (L.has(m) ? "onb" : "b") + '"/>'; });
    return s + "</svg>";
  };
  const play = (x) => { if (!A.playNote || !x.midi) return; if (x.seq) x.midi.forEach((m, i) => setTimeout(() => A.playNote(m, 0.7), i * (x.t === "ear" ? 650 : 330))); else x.midi.forEach((m) => A.playNote(m, 1.4)); };
  const visual = (x) => (x.staff && A.staffSvg ? A.staffSvg(x.staff[0], x.staff[1]) : "") + (x.midi && x.t !== "ear" && x.t !== "treble" && x.t !== "bass" ? miniKb(x.midi, x.midi[0] < 60 ? 48 : 60) : "");

  /* ---------- «Аккорд дня» на главной ---------- */
  A.CARD_NAMES.pianotutor = "Пианино: аккорд дня";
  const chordOfDay = (shift) => { const list = ITEMS.filter((x) => x.t === "chords" && x.lv <= Math.max(2, st().cfg.level)); return list[((A.diffDays("2026-01-01", today()) * 7 + (shift || 0)) % list.length + list.length) % list.length]; };
  const termOfDay = () => { const list = ITEMS.filter((x) => x.t === "terms"); return list[(A.diffDays("2026-01-01", today()) * 11) % list.length]; };
  const codHtml = (shift) => {
    const x = chordOfDay(shift), w = termOfDay(), dl = daily(), left = dl.ids.filter((i) => !dl.done.includes(i)).length, dn = due().length;
    return '<div class="pt-cod"><div class="tw-head"><div class="tw-w">' + esc(x.title.split(" — ")[0]) + '</div><button class="icon-btn tw-say" data-a="ptPlay" data-id="' + x.id + '" aria-label="Сыграть">{{i:play}}</button></div>' +
      '<div class="tw-ipa">' + esc(x.title.split(" — ")[1] || "") + " · " + esc(x.ans) + "</div>" + miniKb(x.midi) +
      '<div class="tw-ex"><span><b>' + esc(w.title) + "</b> — " + esc(w.ans) + "</span></div>" +
      '<div class="btns"><button class="btn sm" data-a="ptNext" data-s="' + ((shift || 0) + 1) + '">{{i:shuffle}} Другой</button><a class="btn sm' + (left || dn ? " primary" : "") + '" href="#/pianotutor">' + (left ? "Новое: " + left : dn ? "Повторить: " + dn : "Репетитор") + "</a></div></div>";
  };
  A.todayCards.pianotutor = () => '<div class="card"><h3>{{i:piano}} Пианино: аккорд дня<span class="sp"></span><small>{{i:flame}} ' + streak() + "</small></h3><div id=\"ptBox\">" + codHtml(0) + "</div></div>";
  const baseDH = A.dayHandlers;
  A.dayHandlers = (getD) => Object.assign(baseDH(getD), {
    ptPlay(b) { const x = BY[b.dataset.id]; if (x) play(x); },
    ptNext(b) { const box = document.getElementById("ptBox"); if (box) box.innerHTML = codHtml(+b.dataset.s); }
  });

  /* ---------- экраны ---------- */
  const SUB = { "": "Репетитор фортепиано", learn: "Новое на сегодня", quiz: "Тренировка", set: "Настройки", base: "База знаний" };
  let S = null;
  const card = (x, hide) => '<div class="tw-card"><div class="tw-head"><div class="tw-w">' + esc(x.title) + "</div>" + (x.midi ? '<button class="icon-btn tw-say" data-a="pPlay" data-id="' + x.id + '">{{i:play}}</button>' : "") + "</div>" +
    '<div class="pt-vis">' + visual(x) + "</div>" + (hide ? '<button class="btn ghost tw-reveal" data-a="pReveal">{{i:eye}} Показать ответ</button><div class="tw-hidden" hidden>' : "<div>") +
    '<div class="tw-ru">' + esc(x.ans) + "</div>" + (x.ex ? '<p class="small muted" style="margin-top:6px">' + esc(x.ex) + "</p>" : "") + '</div><div class="tw-tags"><span class="tag">' + esc(TOPICS[x.t][0]) + '</span><span class="tag">' + LEVELS[x.lv - 1][1] + "</span></div></div>";

  A.view("pianotutor", {
    title: (r) => SUB[r.args[0] || ""] || "Репетитор фортепиано",
    tab: "more",
    render(el, r) {
      const sub = r.args[0] || "", t = st();
      if (sub === "learn") {
        const dl = daily(), left = dl.ids.filter((i) => !dl.done.includes(i));
        if (!dl.ids.length) return A.empty("На этом уровне новое закончилось — повысьте уровень в настройках.", '<button class="btn primary" data-a="pGo" data-v="set">Настройки</button>');
        if (!left.length) return '<div class="card tw-done"><div class="tw-big">{{i:party-popper}}</div><h3>Новое на сегодня пройдено</h3><p class="muted">Закрепите тренировкой, а потом — 10 минут за инструментом: сыграйте аккорд дня и гамму.</p><div class="btns" style="justify-content:center"><button class="btn primary" data-a="pGo" data-v="quiz?m=review">Закрепить</button><a class="btn" href="#/piano">Курс и песни</a></div></div>';
        const x = BY[left[0]];
        return '<div class="tw-prog">' + A.bar(dl.done.length / dl.ids.length) + "<small>" + (dl.done.length + 1) + " / " + dl.ids.length + "</small></div>" + card(x) +
          '<div class="btns tw-actions"><button class="btn" data-a="lKnow" data-id="' + x.id + '">{{i:check}} Уже знаю</button><button class="btn primary" data-a="lLearn" data-id="' + x.id + '">{{i:list-plus}} Учить</button></div>';
      }
      if (sub === "quiz") return '<div id="pq"></div>';
      if (sub === "set") {
        const c = t.cfg;
        return '<div class="card"><h3>{{i:graduation-cap}} Уровень</h3>' + A.seg(LEVELS, c.level, "sLv") + '<p class="small muted">Начинающий — белые клавиши, ноты в скрипичном ключе, простые аккорды и термины; дальше добавляются басовый ключ, все тональности, септаккорды и слух.</p></div>' +
          '<div class="card"><h3>{{i:target}} Тема</h3><div class="chips"><button class="chip' + (!c.topic ? " on" : "") + '" data-a="sTp" data-v="">Все темы вперемешку</button>' + Object.keys(TOPICS).map((k) => '<button class="chip' + (c.topic === k ? " on" : "") + '" data-a="sTp" data-v="' + k + '">{{i:' + TOPICS[k][1] + "}} " + TOPICS[k][0] + "</button>").join("") + "</div></div>" +
          '<div class="card"><h3>{{i:list-todo}} Нового в день</h3>' + A.seg([[3, "3"], [5, "5"], [8, "8"], [12, "12"]], c.n, "sN") + "</div>";
      }
      if (sub === "base") {
        const tp = r.params.t || "chords";
        return '<div class="chips" style="margin-bottom:10px">' + Object.keys(TOPICS).map((k) => '<button class="chip' + (tp === k ? " on" : "") + '" data-a="bT" data-v="' + k + '">' + TOPICS[k][0] + "</button>").join("") + "</div>" +
          '<div class="card"><div class="list">' + ITEMS.filter((x) => x.t === tp).map((x) => { const s = t.srs[x.id]; return '<div class="item"><div class="tx tap" data-a="bOpen" data-id="' + x.id + '"><b>' + esc(x.title) + "</b><small>" + esc(x.ans) + "</small></div>" + (x.midi ? '<button class="icon-btn" data-a="pPlay" data-id="' + x.id + '">{{i:play}}</button>' : "") + (s || t.known[x.id] ? '<span class="tag">' + (t.known[x.id] ? "знаю" : s.i >= 21 ? "выучено" : "учу") + "</span>" : '<button class="icon-btn" data-a="bAdd" data-id="' + x.id + '">{{i:list-plus}}</button>') + "</div>"; }).join("") + "</div></div>";
      }
      const dl = daily(), left = dl.ids.filter((i) => !dl.done.includes(i)).length, dn = due().length, all = Object.keys(t.srs).length, got = Object.values(t.srs).filter((c) => c.i >= 21).length;
      const tile = (v, ic, a, b, hot) => '<button class="tw-tile' + (hot ? " hot" : "") + '" data-a="pGo" data-v="' + v + '"><span class="tw-ti">{{i:' + ic + "}}</span><b>" + a + "</b><small>" + b + "</small></button>";
      let h = '<div class="card"><div class="grid3"><div class="stat"><small>{{i:flame}} Серия</small><b>' + streak() + "</b><small>" + A.plural(streak(), "день", "дня", "дней") + '</small></div><div class="stat"><small>{{i:star}} Опыт сегодня</small><b>' + (t.xp[today()] || 0) + '</b><small>XP</small></div><div class="stat"><small>{{i:award}} Освоено</small><b>' + got + "</b><small>из " + all + " в работе</small></div></div>" +
        '<p class="small muted" style="margin-top:8px">Уровень: <b>' + LEVELS[t.cfg.level - 1][1] + "</b> · " + (t.cfg.topic ? esc(TOPICS[t.cfg.topic][0]) : "все темы") + " · " + t.cfg.n + ' нового в день · <button class="link" data-a="pGo" data-v="set">изменить</button></p></div>';
      h += '<div class="tw-tiles">' + tile("learn", "book-open-check", "Новое на сегодня", left ? "осталось " + left + " из " + dl.ids.length : "пройдено", left) + tile("quiz?m=review", "repeat", "Повторение", dn ? dn + " ждут" : "на сегодня всё", dn) +
        tile("quiz?m=treble", "music", "Чтение нот", "скрипичный и басовый ключ") + tile("quiz?m=keys", "piano", "Найди клавишу", "на клавиатуре") + tile("quiz?m=chords", "layers", "Аккорды", "из каких нот состоят") + tile("quiz?m=ear", "headphones", "Слух", "интервалы и аккорды") +
        tile("quiz?m=terms", "book-a", "Термины и ритм", "что значит знак") + tile("base", "library", "База знаний", ITEMS.length + " элементов") + "</div>";
      h += '<div class="card"><h3>{{i:target}} Темы</h3><div class="tw-topics">' + Object.keys(TOPICS).map((k) => { const tot = ITEMS.filter((x) => x.t === k).length, g = ITEMS.filter((x) => x.t === k && (t.srs[x.id] || t.known[x.id])).length; return '<button class="tw-topic' + (t.cfg.topic === k ? " on" : "") + '" data-a="tTopic" data-v="' + k + '"><span>{{i:' + TOPICS[k][1] + "}}</span><b>" + TOPICS[k][0] + "</b><small>" + g + "/" + tot + "</small>" + A.bar(g / tot) + "</button>"; }).join("") + "</div></div>";
      h += '<div class="card sage"><h3>{{i:lightbulb}} План на 15 минут</h3><ol class="small"><li>2 мин — гамма дня и разминка пальцев.</li><li>3 мин — новое на сегодня.</li><li>3 мин — повторение.</li><li>7 мин — урок курса или песня: <a class="link" href="#/piano">Курс и песни</a>.</li></ol></div>';
      return h;
    },
    bind(el, r) {
      const sub = r.args[0] || "", t = st();
      A.bind(el, {
        pGo(b) { A.go("pianotutor/" + b.dataset.v); },
        pPlay(b) { const x = BY[b.dataset.id]; if (x) play(x); },
        pReveal(b) { const h = b.parentNode.querySelector(".tw-hidden"); if (h) h.hidden = false; b.remove(); },
        tTopic(b) { t.cfg.topic = b.dataset.v; A.save(); A.go("pianotutor/learn"); },
        sLv(b) { t.cfg.level = +b.dataset.v; A.save(); A.refresh(); },
        sTp(b) { t.cfg.topic = b.dataset.v; A.save(); A.refresh(); },
        sN(b) { t.cfg.n = +b.dataset.v; A.save(); A.refresh(); },
        lKnow(b) { t.known[b.dataset.id] = today(); done(b.dataset.id); },
        lLearn(b) { startItem(b.dataset.id); done(b.dataset.id); },
        bT(b) { A.go("pianotutor/base?t=" + b.dataset.v, true); },
        bOpen(b) { const x = BY[b.dataset.id]; A.sheet(x.title, card(x), { buttons: [{ label: t.srs[x.id] ? "Уже в повторениях" : "Учить", cls: "primary", onClick: () => { startItem(x.id); A.toast("Добавлено в повторения"); } }] }); setTimeout(() => document.querySelectorAll('.sheet [data-a="pPlay"]').forEach((p) => p.addEventListener("click", () => play(x))), 50); },
        bAdd(b) { startItem(b.dataset.id); b.outerHTML = '<span class="tag">учу</span>'; },
        qPick(b) { pick(el, b); }, qNext() { S.i++; drawQ(el.querySelector("#pq")); }, qAgain() { startQuiz(el, S.m); }, qPlay() { play(S.cur); },
        qGrade(b) { const q = +b.dataset.q; if (q >= 3) S.ok++; else S.bad.push(S.cur); grade(S.cur.id, q); S.i++; drawQ(el.querySelector("#pq")); }
      });
      if (sub === "quiz") startQuiz(el, r.params.m || "review");
      const done = (id) => { const dl = daily(); if (!dl.done.includes(id)) dl.done.push(id); A.save(); A.refresh(); };
    }
  });

  /* ---------- тренировки ---------- */
  const shuffle = (a) => { a = a.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const QN = { review: "Повторение", treble: "Чтение нот", keys: "Найди клавишу", chords: "Аккорды", ear: "Слух", terms: "Термины и ритм" };
  const startQuiz = (el, m) => {
    const box = el.querySelector("#pq"), t = st(), lv = t.cfg.level;
    let items;
    if (m === "review") items = due().map((id) => BY[id]);
    else {
      const tps = { treble: ["treble", "bass"], keys: ["keys"], chords: ["chords"], ear: ["ear", "earch"], terms: ["terms", "rhythm"] }[m] || [m];
      items = ITEMS.filter((x) => tps.includes(x.t) && (x.lv <= Math.max(lv, 1) || t.srs[x.id]) && (x.t !== "bass" || lv >= 2));
      if (m === "ear") items = items.concat(ITEMS.filter((x) => x.t === "chords" && (x.id.endsWith("m") || /^c\d+$/.test(x.id)) && x.lv <= 2).map((x) => Object.assign({}, x, { earch: true })));
    }
    items = shuffle(items).slice(0, m === "review" ? 25 : 12);
    if (!items.length) { box.innerHTML = A.empty(m === "review" ? "Повторений на сегодня нет — возьмите новое." : "Для этого уровня заданий пока нет — повысьте уровень.", '<button class="btn primary" data-a="pGo" data-v="learn">Новое на сегодня</button>'); return; }
    S = { m, items, i: 0, ok: 0, bad: [] }; drawQ(box);
  };
  const optsFor = (x) => {
    if (x.earch) return shuffle(["мажорный аккорд", "минорный аккорд"]);
    const same = ITEMS.filter((y) => y.t === x.t && y.ans !== x.ans);
    if (x.t === "treble" || x.t === "bass") return shuffle(DIA.slice());
    return shuffle([x.ans].concat(shuffle([...new Set(same.map((y) => y.ans))]).slice(0, 3)));
  };
  const ansOf = (x) => (x.earch ? (x.id.endsWith("m") ? "минорный аккорд" : "мажорный аккорд") : x.ans);
  const drawQ = (box) => {
    if (!S) return;
    if (S.i >= S.items.length) {
      box.innerHTML = '<div class="card tw-done"><div class="tw-big">{{i:' + (S.ok / S.items.length >= 0.8 ? "trophy" : "sprout") + '}}</div><h3>' + QN[S.m] + ": " + S.ok + " из " + S.items.length + '</h3><p class="muted">Сегодня ' + (st().xp[today()] || 0) + " XP, серия " + streak() + " " + A.plural(streak(), "день", "дня", "дней") + ".</p>" +
        (S.bad.length ? '<div class="list" style="text-align:left">' + S.bad.map((x) => '<div class="item"><div class="tx"><b>' + esc(x.title) + "</b><small>" + esc(ansOf(x)) + "</small></div></div>").join("") + "</div>" : "") +
        '<div class="btns" style="justify-content:center"><button class="btn primary" data-a="pGo" data-v="">Готово</button><button class="btn" data-a="qAgain">Ещё раз</button></div></div>';
      return;
    }
    const x = S.items[S.i]; S.cur = x; S.answered = false;
    let h = '<div class="tw-prog">' + A.bar(S.i / S.items.length) + "<small>" + (S.i + 1) + " / " + S.items.length + "</small></div>";
    if (x.t === "keys") {
      h += '<div class="tw-card tw-q"><div class="tw-w">' + esc(x.title) + '</div><small class="muted">Нажмите эту клавишу</small></div>' + A.keyboard(60, 1);
    } else if (x.t === "ear" || x.earch) {
      h += '<div class="tw-card tw-q"><button class="tw-play" data-a="qPlay">{{i:headphones}}</button><small class="muted">' + (x.earch ? "Мажор или минор?" : "Какой интервал прозвучал?") + "</small></div>";
    } else if (x.t === "treble" || x.t === "bass") {
      h += '<div class="tw-card tw-q pt-vis">' + A.staffSvg(x.staff[0], x.staff[1]) + "</div>";
    } else if (x.t === "chords") {
      h += '<div class="tw-card tw-q"><div class="tw-w">' + esc(x.title.split(" — ")[0]) + '</div><small class="muted">' + esc(x.title.split(" — ")[1]) + " — из каких нот состоит?</small></div>";
    } else if (x.t === "scales") {
      h += '<div class="tw-card tw-q"><div class="tw-w">' + esc(x.title) + '</div><small class="muted">Какие ноты в гамме?</small></div>';
    } else h += '<div class="tw-card tw-q"><div class="tw-w">' + esc(x.title) + '</div><small class="muted">Что это значит?</small></div>';
    if (x.t !== "keys") h += '<div class="tw-opts' + (x.t === "treble" || x.t === "bass" ? " pt-7" : "") + '">' + optsFor(x).map((o) => '<button class="btn tw-opt" data-a="qPick" data-v="' + esc(o) + '">' + esc(o) + "</button>").join("") + "</div>";
    h += '<div id="qFb"></div>';
    box.innerHTML = h;
    if (x.t === "ear" || x.earch) setTimeout(() => play(x.earch ? Object.assign({}, x, { seq: false }) : x), 250);
    if (x.t === "keys") {
      const p = box.querySelector("#piano");
      p.addEventListener("pointerdown", (e) => {
        const k = e.target.closest(".pk"); if (!k || S.answered) return;
        e.preventDefault(); const m = +k.dataset.m; A.playNote(m);
        const ok = m % 12 === x.pc; k.classList.add(ok ? "lit" : "hit");
        if (!ok) { const r = p.querySelector('.pk[data-m="' + (60 + x.pc) + '"]'); if (r) r.classList.add("lit"); }
        fb(box, ok, x);
      });
    }
  };
  const pick = (el, b) => {
    if (!S || S.answered) return;
    const x = S.cur, ok = b.dataset.v === ansOf(x);
    el.querySelectorAll(".tw-opt").forEach((o) => { o.disabled = true; if (o.dataset.v === ansOf(x)) o.classList.add("ok"); else if (o === b) o.classList.add("bad"); });
    fb(el.querySelector("#pq"), ok, x);
  };
  const fb = (box, ok, x) => {
    S.answered = true; if (ok) S.ok++; else S.bad.push(x);
    if (st().srs[x.id] || S.m === "review") grade(x.id, ok ? 4 : 1); else addXp(ok ? 1 : 0), A.save();
    if (x.t !== "ear" && !x.earch && x.midi) play(x);
    box.querySelector("#qFb").innerHTML = '<div class="tw-fb ' + (ok ? "ok" : "bad") + '"><b>' + (ok ? "{{i:check}} Верно" : "{{i:x}} Правильно: " + esc(ansOf(x))) + "</b><small>" + esc(x.title) + (x.ex ? " · " + esc(x.ex) : "") + '</small><button class="btn primary" data-a="qNext">Дальше</button></div>';
    const n = box.querySelector('[data-a="qNext"]'); if (n) n.scrollIntoView({ block: "nearest" });
  };
})();
