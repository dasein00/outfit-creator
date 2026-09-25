/* Путь жизни — ядро: утилиты, даты, хранилище, мост к Android, навигация, интерфейсные примитивы. */
(function () {
  "use strict";
  const App = (window.App = {});

  /* ---------- утилиты ---------- */
  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const num = (v, d = 0) => { const n = parseFloat(String(v).replace(",", ".")); return isFinite(n) ? n : d; };
  const round = (v, p = 0) => { const k = Math.pow(10, p); return Math.round(v * k) / k; };
  const sum = (a) => a.reduce((s, x) => s + (+x || 0), 0);
  const avg = (a) => { const b = a.filter((x) => x != null && isFinite(x)); return b.length ? sum(b) / b.length : null; };
  const fmtN = (v, p = 0) => v == null || !isFinite(v) ? "—" : round(v, p).toLocaleString("ru-RU");
  const money = (v) => v == null ? "—" : Math.round(v).toLocaleString("ru-RU") + " ₽";
  const plural = (n, a, b, c) => { n = Math.abs(n) % 100; const n1 = n % 10; if (n > 10 && n < 20) return c; if (n1 > 1 && n1 < 5) return b; if (n1 === 1) return a; return c; };
  Object.assign(App, { esc, uid, clamp, num, round, sum, avg, fmtN, money, plural });

  /* ---------- даты ---------- */
  const pad = (n) => String(n).padStart(2, "0");
  const iso = (d = new Date()) => d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  const parse = (s) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
  const addDays = (s, n) => { const d = parse(s); d.setDate(d.getDate() + n); return iso(d); };
  const diffDays = (a, b) => Math.round((parse(b) - parse(a)) / 864e5);
  const dow = (s) => (parse(s).getDay() + 6) % 7 + 1; // 1 = пн
  const weekStart = (s) => addDays(s, 1 - dow(s));
  const monthKey = (s) => s.slice(0, 7);
  const daysInMonth = (y, m) => new Date(y, m, 0).getDate(); // m: 1..12
  const range = (a, b) => { const r = []; for (let d = a; d <= b; d = addDays(d, 1)) r.push(d); return r; };
  const today = () => iso(new Date());
  const nowHM = () => { const d = new Date(); return pad(d.getHours()) + ":" + pad(d.getMinutes()); };
  const hm2min = (t) => { if (!t) return null; const [h, m] = t.split(":").map(Number); return h * 60 + (m || 0); };
  const min2hm = (m) => pad(Math.floor(((m % 1440) + 1440) % 1440 / 60)) + ":" + pad(Math.round(((m % 60) + 60) % 60));
  const MONTHS = ["январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"];
  const MONTHS_G = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"];
  const MON_SHORT = ["Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д"];
  const DOW = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];
  const DOW_FULL = ["понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"];
  const fmtDate = (s, opt = {}) => { const d = parse(s); return d.getDate() + " " + MONTHS_G[d.getMonth()] + (opt.year || d.getFullYear() !== new Date().getFullYear() ? " " + d.getFullYear() : "") + (opt.dow ? ", " + DOW_FULL[dow(s) - 1] : ""); };
  const fmtShort = (s) => { const d = parse(s); return pad(d.getDate()) + "." + pad(d.getMonth() + 1); };
  const monthTitle = (mk) => { const [y, m] = mk.split("-").map(Number); return MONTHS[m - 1][0].toUpperCase() + MONTHS[m - 1].slice(1) + " " + y; };
  const isoWeek = (s) => { const d = parse(s); d.setDate(d.getDate() + 4 - ((d.getDay() + 6) % 7 + 1)); const y = d.getFullYear(); const n = Math.ceil(((d - new Date(y, 0, 1)) / 864e5 + 1) / 7); return y + "-W" + pad(n); };
  const durH = (bed, wake) => { const a = hm2min(bed), b = hm2min(wake); if (a == null || b == null) return null; let d = b - a; if (d <= 0) d += 1440; return d / 60; };
  const fmtDur = (min) => { if (min == null) return "—"; min = Math.round(min); const h = Math.floor(min / 60), m = min % 60; return h ? h + " ч" + (m ? " " + m + " мин" : "") : m + " мин"; };
  Object.assign(App, { pad, iso, parse, addDays, diffDays, dow, weekStart, monthKey, daysInMonth, range, today, nowHM, hm2min, min2hm, MONTHS, MONTHS_G, MON_SHORT, DOW, DOW_FULL, fmtDate, fmtShort, monthTitle, isoWeek, durH, fmtDur });

  /* ---------- мост к Android ---------- */
  const N = window.Android && typeof window.Android.isAndroid === "function" ? window.Android : null;
  const waiters = {};
  window.__nativeCb = (type, value) => { const w = waiters[type]; if (w && w.length) w.shift()(value); else App.emit("native:" + type, value); };
  const nativeCall = (type, fn) => new Promise((res) => { (waiters[type] = waiters[type] || []).push(res); try { fn(); } catch (e) { res(""); } });
  App.native = N;
  App.nativeCall = nativeCall;

  /* ---------- события ---------- */
  const listeners = {};
  App.on = (ev, fn) => { (listeners[ev] = listeners[ev] || []).push(fn); };
  App.emit = (ev, data) => (listeners[ev] || []).forEach((f) => { try { f(data); } catch (e) { console.error(e); } });

  /* ---------- хранилище ---------- */
  const KEY = "pz.db.v1";
  let db = null, saveTimer = null;
  App.dbVersion = 1;

  function load() {
    let raw = null;
    try { raw = localStorage.getItem(KEY); } catch (e) {}
    if (!raw && N) { try { raw = N.readBackup(); } catch (e) {} }
    if (raw) { try { db = JSON.parse(raw); } catch (e) { db = null; } }
    if (!db || typeof db !== "object") db = App.seed();
    App.migrate(db);
    return db;
  }
  function saveNow() {
    clearTimeout(saveTimer); saveTimer = null;
    db.savedAt = new Date().toISOString();
    const s = JSON.stringify(db);
    try { localStorage.setItem(KEY, s); } catch (e) { App.toast("Не удалось сохранить в память браузера — используйте резервную копию"); }
    if (N) { try { N.backup(s); } catch (e) {} }
    App.emit("saved");
  }
  App.save = () => { clearTimeout(saveTimer); saveTimer = setTimeout(saveNow, 250); };
  App.saveNow = saveNow;
  App.db = () => db;
  App.load = load;
  App.replaceDb = (obj) => { db = obj; App.migrate(db); saveNow(); };
  window.addEventListener("pagehide", () => { if (saveTimer) saveNow(); });
  document.addEventListener("visibilitychange", () => { if (document.hidden && saveTimer) saveNow(); });

  // Коллекции — массивы объектов с id.
  App.col = (name) => (db[name] = db[name] || []);
  App.byId = (name, id) => App.col(name).find((x) => x.id === id);
  App.upsert = (name, obj) => { const c = App.col(name); if (!obj.id) obj.id = uid(); const i = c.findIndex((x) => x.id === obj.id); if (i >= 0) c[i] = obj; else c.push(obj); App.save(); return obj; };
  App.remove = (name, id) => { const c = App.col(name); const i = c.findIndex((x) => x.id === id); if (i >= 0) c.splice(i, 1); App.save(); };
  // Запись дня.
  App.day = (d, create = true) => { db.days = db.days || {}; if (!db.days[d] && create) db.days[d] = {}; return db.days[d] || {}; };
  App.dayGet = (d) => (db.days && db.days[d]) || null;

  /* ---------- навигация ---------- */
  const views = (App.views = {});
  App.view = (name, def) => { views[name] = def; };
  let current = null;
  App.go = (path, replace) => { const h = "#/" + path.replace(/^#?\/?/, ""); if (replace) location.replace(h); else location.hash = h; };
  App.route = () => {
    const h = location.hash.replace(/^#\/?/, "") || "today";
    const [path, qs] = h.split("?");
    const parts = path.split("/").filter(Boolean);
    const name = parts[0] || "today";
    const params = {}; (qs || "").split("&").forEach((p) => { if (!p) return; const [k, v] = p.split("="); params[decodeURIComponent(k)] = decodeURIComponent(v || ""); });
    return { name, args: parts.slice(1), params, path };
  };
  App.render = (keepScroll) => {
    const r = App.route();
    const v = views[r.name] || views.today;
    const main = document.getElementById("main");
    const sc = keepScroll ? main.scrollTop : 0;
    current = r;
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("on", t.dataset.tab === (v.tab || r.name)));
    document.getElementById("title").textContent = typeof v.title === "function" ? v.title(r) : v.title || "Путь жизни";
    const back = document.getElementById("back");
    back.hidden = !!v.root;
    const actions = document.getElementById("actions");
    actions.innerHTML = "";
    try {
      // Новый контейнер на каждую отрисовку — обработчики не накапливаются.
      main.innerHTML = "";
      const box = document.createElement("div");
      main.appendChild(box);
      const out = v.render(box, r);
      if (typeof out === "string") box.innerHTML = out;
      if (v.actions) v.actions(actions, r);
      if (v.bind) v.bind(box, r);
    } catch (e) {
      console.error(e);
      main.innerHTML = '<div class="card"><b>Ошибка экрана.</b><p class="muted">' + esc(e.message) + "</p></div>";
    }
    main.scrollTop = sc;
  };
  App.refresh = () => App.render(true);
  App.current = () => current;
  window.addEventListener("hashchange", () => { App.closeSheet(true); App.render(); });

  window.__onBack = () => {
    if (App.sheetOpen()) { App.closeSheet(); return true; }
    const r = App.route();
    if (r.name !== "today") { history.length > 1 ? history.back() : App.go("today", true); return true; }
    return false;
  };

  /* ---------- делегирование событий ---------- */
  // Элементы с data-a="имя" вызывают handlers[имя](элемент, событие) по клику,
  // data-c="имя" — по change/input.
  App.bind = (root, handlers) => {
    root.addEventListener("click", (e) => {
      const el = e.target.closest("[data-a]");
      if (!el || !root.contains(el)) return;
      const fn = handlers[el.dataset.a];
      if (fn) { e.preventDefault(); fn(el, e); }
    });
    const onCh = (e) => {
      const el = e.target.closest("[data-c]");
      if (!el || !root.contains(el)) return;
      const fn = handlers[el.dataset.c];
      if (fn && (e.type === "change" || el.dataset.live != null)) fn(el, e);
    };
    root.addEventListener("change", onCh);
    root.addEventListener("input", onCh);
  };

  /* ---------- тост, подтверждение ---------- */
  let toastT;
  App.toast = (msg) => {
    const t = document.getElementById("toast");
    t.textContent = msg; t.classList.add("on");
    clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove("on"), 2400);
  };
  App.vibe = (ms = 12) => { if (N) try { N.vibrate(ms); } catch (e) {} };

  /* ---------- нижний лист (модальное окно) ---------- */
  const stack = [];
  App.sheetOpen = () => stack.length > 0;
  App.sheet = (title, body, opts = {}) => {
    const wrap = document.createElement("div");
    wrap.className = "sheet-wrap";
    wrap.innerHTML = '<div class="sheet-bg"></div><div class="sheet" role="dialog" aria-label="' + esc(title) + '"><div class="sheet-h"><div class="grab"></div><h3>' + esc(title) + '</h3><button class="icon-btn" data-close aria-label="Закрыть">✕</button></div><div class="sheet-b"></div><div class="sheet-f"></div></div>';
    const b = wrap.querySelector(".sheet-b");
    if (typeof body === "string") b.innerHTML = body; else if (body) b.appendChild(body);
    const f = wrap.querySelector(".sheet-f");
    (opts.buttons || []).forEach((btn) => {
      const el = document.createElement("button");
      el.className = "btn " + (btn.cls || "");
      el.textContent = btn.label;
      el.onclick = () => { const r = btn.onClick ? btn.onClick(wrap) : true; if (r !== false) App.closeSheet(); };
      f.appendChild(el);
    });
    if (!f.children.length) f.remove();
    wrap.querySelector(".sheet-bg").onclick = () => App.closeSheet();
    wrap.querySelector("[data-close]").onclick = () => App.closeSheet();
    document.body.appendChild(wrap);
    stack.push({ wrap, onClose: opts.onClose });
    requestAnimationFrame(() => wrap.classList.add("on"));
    if (opts.bind) App.bind(b, opts.bind);
    return wrap;
  };
  App.closeSheet = (all) => {
    do {
      const s = stack.pop();
      if (!s) return;
      s.wrap.classList.remove("on");
      setTimeout(() => s.wrap.remove(), 220);
      if (s.onClose) s.onClose();
    } while (all && stack.length);
  };
  App.confirm = (text, onYes, yesLabel = "Да", danger = false) => {
    App.sheet("Подтверждение", '<p style="margin:4px 0 8px">' + esc(text) + "</p>", {
      buttons: [{ label: "Отмена", cls: "ghost" }, { label: yesLabel, cls: danger ? "danger" : "primary", onClick: () => { onYes(); } }]
    });
  };

  /* ---------- формы ---------- */
  // Поле: {k, label, type: text|number|date|time|textarea|select|chips|multi|range|check|color|stars, opts:[[v,label]], min, max, step, hint, ph, full}
  App.field = (f, val) => {
    const id = "f_" + f.k;
    const v = val == null ? (f.def != null ? f.def : "") : val;
    const lab = f.label ? '<label for="' + id + '">' + esc(f.label) + "</label>" : "";
    const hint = f.hint ? '<div class="hint">' + esc(f.hint) + "</div>" : "";
    let inp = "";
    const opts = typeof f.opts === "function" ? f.opts() : f.opts || [];
    switch (f.type) {
      case "textarea": inp = '<textarea id="' + id + '" name="' + f.k + '" rows="' + (f.rows || 3) + '" placeholder="' + esc(f.ph || "") + '">' + esc(v) + "</textarea>"; break;
      case "select": inp = '<select id="' + id + '" name="' + f.k + '">' + opts.map((o) => { const [ov, ol] = Array.isArray(o) ? o : [o, o]; return '<option value="' + esc(ov) + '"' + (String(ov) === String(v) ? " selected" : "") + ">" + esc(ol) + "</option>"; }).join("") + "</select>"; break;
      case "chips": case "multi": {
        const sel = f.type === "multi" ? (Array.isArray(v) ? v.map(String) : []) : [String(v)];
        inp = '<div class="chips" data-field="' + f.k + '" data-multi="' + (f.type === "multi" ? 1 : 0) + '">' + opts.map((o) => { const [ov, ol] = Array.isArray(o) ? o : [o, o]; return '<button type="button" class="chip' + (sel.includes(String(ov)) ? " on" : "") + '" data-v="' + esc(ov) + '">' + esc(ol) + "</button>"; }).join("") + "</div>"; break;
      }
      case "range": inp = '<div class="rangebox"><input type="range" id="' + id + '" name="' + f.k + '" min="' + (f.min ?? 0) + '" max="' + (f.max ?? 10) + '" step="' + (f.step ?? 1) + '" value="' + esc(v === "" ? (f.min ?? 0) : v) + '" oninput="this.nextElementSibling.textContent=this.value"><output>' + esc(v === "" ? (f.min ?? 0) : v) + "</output></div>"; break;
      case "check": inp = '<label class="switch"><input type="checkbox" id="' + id + '" name="' + f.k + '"' + (v ? " checked" : "") + '><span></span>' + esc(f.text || "") + "</label>"; break;
      case "stars": inp = '<div class="stars" data-field="' + f.k + '" data-v="' + esc(v || 0) + '">' + [1, 2, 3, 4, 5].map((i) => '<button type="button" data-s="' + i + '" class="' + (i <= (v || 0) ? "on" : "") + '" aria-label="' + i + '">★</button>').join("") + "</div>"; break;
      default: inp = '<input id="' + id + '" name="' + f.k + '" type="' + (f.type || "text") + '"' + (f.type === "number" ? ' inputmode="decimal" step="' + (f.step || "any") + '"' : "") + (f.min != null ? ' min="' + f.min + '"' : "") + (f.max != null ? ' max="' + f.max + '"' : "") + ' value="' + esc(v) + '" placeholder="' + esc(f.ph || "") + '">';
    }
    return '<div class="fld' + (f.full || ["textarea", "chips", "multi", "range", "stars"].includes(f.type) ? " full" : "") + '">' + lab + inp + hint + "</div>";
  };
  App.formHtml = (fields, obj = {}) => '<div class="form">' + fields.map((f) => f.section ? '<div class="fsec full">' + esc(f.section) + "</div>" : App.field(f, obj[f.k])).join("") + "</div>";
  App.wireForm = (root) => {
    root.querySelectorAll(".chips[data-field]").forEach((box) => box.addEventListener("click", (e) => {
      const c = e.target.closest(".chip"); if (!c) return;
      if (box.dataset.multi === "1") c.classList.toggle("on");
      else { box.querySelectorAll(".chip").forEach((x) => x.classList.remove("on")); c.classList.add("on"); }
    }));
    root.querySelectorAll(".stars[data-field]").forEach((box) => box.addEventListener("click", (e) => {
      const s = e.target.closest("[data-s]"); if (!s) return;
      const v = +s.dataset.s === +box.dataset.v ? 0 : +s.dataset.s;
      box.dataset.v = v;
      box.querySelectorAll("[data-s]").forEach((x) => x.classList.toggle("on", +x.dataset.s <= v));
    }));
  };
  App.readForm = (root, fields) => {
    const o = {};
    fields.forEach((f) => {
      if (f.section) return;
      if (f.type === "chips" || f.type === "multi") {
        const vals = [...root.querySelectorAll('.chips[data-field="' + f.k + '"] .chip.on')].map((c) => c.dataset.v);
        o[f.k] = f.type === "multi" ? (f.numeric ? vals.map(Number) : vals) : (vals[0] ?? "");
        if (f.numeric && f.type === "chips") o[f.k] = o[f.k] === "" ? null : +o[f.k];
        return;
      }
      if (f.type === "stars") { o[f.k] = +(root.querySelector('.stars[data-field="' + f.k + '"]').dataset.v || 0); return; }
      const el = root.querySelector('[name="' + f.k + '"]');
      if (!el) return;
      if (f.type === "check") o[f.k] = el.checked;
      else if (f.type === "number" || f.type === "range") o[f.k] = el.value === "" ? null : num(el.value);
      else o[f.k] = el.value.trim();
    });
    return o;
  };
  // Готовая форма в листе: onSave(obj) → false чтобы не закрывать.
  App.formSheet = (title, fields, obj, onSave, opts = {}) => {
    const buttons = [];
    if (opts.onDelete) buttons.push({ label: "Удалить", cls: "danger ghost", onClick: () => { App.confirm("Удалить запись?", () => { opts.onDelete(); App.closeSheet(); }, "Удалить", true); return false; } });
    buttons.push({ label: opts.saveLabel || "Сохранить", cls: "primary", onClick: (w) => {
      const o = App.readForm(w, fields);
      for (const f of fields) if (f.req && (o[f.k] == null || o[f.k] === "")) { App.toast("Заполните: " + f.label); return false; }
      return onSave(o, w);
    } });
    const w = App.sheet(title, (opts.pre || "") + App.formHtml(fields, obj || {}) + (opts.post || ""), { buttons, bind: opts.bind });
    App.wireForm(w);
    if (opts.after) opts.after(w);
    return w;
  };

  /* ---------- маленькие компоненты ---------- */
  App.ring = (pct, label, sub, color) => {
    const p = clamp(pct || 0, 0, 1), r = 30, c = 2 * Math.PI * r;
    return '<div class="ring"><svg viewBox="0 0 72 72" width="72" height="72" aria-hidden="true"><circle cx="36" cy="36" r="' + r + '" class="ring-bg"/><circle cx="36" cy="36" r="' + r + '" class="ring-fg" style="stroke:' + (color || "var(--accent)") + '" stroke-dasharray="' + (c * p).toFixed(1) + " " + c.toFixed(1) + '" transform="rotate(-90 36 36)"/></svg><div class="ring-t"><b>' + esc(label) + "</b>" + (sub ? "<small>" + esc(sub) + "</small>" : "") + "</div></div>";
  };
  App.bar = (pct, color) => '<div class="pbar"><i style="width:' + (clamp(pct || 0, 0, 1) * 100).toFixed(1) + "%;" + (color ? "background:" + color : "") + '"></i></div>';
  App.empty = (text, btn) => '<div class="empty"><p>' + esc(text) + "</p>" + (btn || "") + "</div>";
  App.seg = (items, cur, act) => '<div class="seg">' + items.map(([v, l]) => '<button data-a="' + act + '" data-v="' + esc(v) + '" class="' + (String(v) === String(cur) ? "on" : "") + '">' + esc(l) + "</button>").join("") + "</div>";
  App.scale = (k, val, max = 10, min = 0) => { let s = '<div class="scale" data-k="' + k + '">'; for (let i = min; i <= max; i++) s += '<button data-a="scale" data-k="' + k + '" data-v="' + i + '" class="' + (val === i ? "on" : "") + '" style="--c:' + App.scaleColor(i, max, k) + '">' + i + "</button>"; return s + "</div>"; };
  App.scaleColor = (v, max = 10, kind = "mood") => {
    const t = v / max;
    const good = ["#D2555E", "#E57F5B", "#F0A45B", "#F4C95D", "#C9D66B", "#8CC474", "#5DAE7B"];
    const idx = Math.round((kind === "stress" || kind === "anx" ? 1 - t : t) * (good.length - 1));
    return good[clamp(idx, 0, good.length - 1)];
  };
  App.dateNav = (d, act = "dnav") => '<div class="datenav"><button class="icon-btn" data-a="' + act + '" data-v="-1" aria-label="Назад">‹</button><button class="dn-t" data-a="' + act + '" data-v="pick">' + esc(d === today() ? "Сегодня, " + fmtDate(d) : fmtDate(d, { dow: true })) + '</button><button class="icon-btn" data-a="' + act + '" data-v="1" aria-label="Вперёд">›</button></div>';
  App.pickDate = (cur, cb) => {
    const w = App.sheet("Выбрать дату", '<div class="form"><div class="fld full"><input type="date" id="pd" value="' + cur + '"></div></div>', { buttons: [{ label: "Сегодня", cls: "ghost", onClick: () => cb(today()) }, { label: "Выбрать", cls: "primary", onClick: (w) => { const v = w.querySelector("#pd").value; if (v) cb(v); } }] });
    return w;
  };

  /* ---------- экспорт ---------- */
  App.download = (name, mime, content) => {
    if (N) { N.saveFile(name, mime, content); return nativeCall("save", () => {}).then((r) => { if (r === "ok") App.toast("Файл сохранён"); else if (r === "error") App.toast("Не удалось сохранить файл"); }); }
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([content], { type: mime }));
    a.download = name; document.body.appendChild(a); a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 800);
    return Promise.resolve();
  };
  App.pickFile = () => {
    if (N) return nativeCall("open", () => N.openFile());
    return new Promise((res) => {
      const i = document.createElement("input"); i.type = "file"; i.accept = ".json,application/json,text/*";
      i.onchange = () => { const f = i.files[0]; if (!f) return res(""); const r = new FileReader(); r.onload = () => res(String(r.result)); r.readAsText(f); };
      i.click();
    });
  };
  App.csv = (rows, cols) => {
    const q = (v) => { const s = v == null ? "" : Array.isArray(v) ? v.join("; ") : typeof v === "object" ? JSON.stringify(v) : String(v); return /[";\n,]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
    cols = cols || [...new Set(rows.flatMap((r) => Object.keys(r)))];
    return "﻿" + [cols.join(";")].concat(rows.map((r) => cols.map((c) => q(r[c])).join(";"))).join("\n");
  };
  App.print = (title) => { if (N) N.print(title || "Путь жизни"); else window.print(); };

  /* ---------- хеш PIN ---------- */
  App.sha256 = async (s) => {
    if (window.crypto && crypto.subtle) {
      const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
      return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
    }
    let h = 5381; for (let i = 0; i < s.length; i++) h = (h * 33) ^ s.charCodeAt(i); return "d" + (h >>> 0).toString(16);
  };
})();
