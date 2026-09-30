/* Здоровье и активность: сон, шаги, вода, состояние, температура тела, погода. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtDur, fmtShort, fmtDate } = A;
  A.edit = A.edit || {};

  /* ---------- редакторы ---------- */
  A.SLEEP_TAGS = ["храп", "бессонница", "яркие сновидения", "кошмары", "частые пробуждения", "поздний ужин", "кофе вечером", "экран перед сном", "спала отлично"];
  A.edit.sleep = (d) => {
    const day = A.day(d), s = day.sleep || {};
    const fields = [
      { k: "bed", label: "Час сна (легла)", type: "time" }, { k: "asleep", label: "Засыпание", type: "time" },
      { k: "wake", label: "Час пробуждения", type: "time" }, { k: "wakeups", label: "Пробуждений ночью", type: "number", min: 0 },
      { k: "q", label: "Качество сна", type: "stars" },
      { k: "tags", label: "Заметки о сне", type: "multi", opts: A.SLEEP_TAGS },
      { k: "rec", label: "Ощущение восстановления 1–10", type: "range", min: 1, max: 10, def: 7 },
      { k: "hours", label: "Или продолжительность вручную, ч", type: "number", step: 0.25 },
      { k: "note", label: "Заметка", type: "textarea", rows: 2 }
    ];
    const q = s.q > 5 ? Math.round(s.q / 2) : s.q;
    A.formSheet("Сон · ночь на " + fmtShort(d), fields, Object.assign({ bed: A.db().profile.bed, wake: A.db().profile.wake }, s, { q }), (o) => {
      if (!o.hours) delete o.hours;
      day.sleep = o; A.save(); A.refresh();
    }, {
      pre: '<p class="small muted">Сон записывается на день пробуждения.</p>' + (A.native ? '<button class="btn sm" id="sleepAuto" style="margin-bottom:8px">Определить по использованию телефона</button>' : ""),
      onDelete: day.sleep ? () => { delete day.sleep; A.save(); A.refresh(); } : null,
      after(w) { const b = w.querySelector("#sleepAuto"); if (b) b.onclick = async () => { const r = await A.detectSleep(d); if (r) { w.querySelector('[name="bed"]').value = r.bed; w.querySelector('[name="wake"]').value = r.wake; A.toast("Найдено: " + r.bed + " → " + r.wake); } }; }
    });
  };
  // Автоопределение сна по самому длинному периоду, когда экран был выключен (Android UsageStats).
  A.detectSleep = async (d) => {
    if (!A.native) return null;
    if (!A.native.usageAllowed()) { A.toast("Разрешите доступ к истории использования для «Путь жизни»"); A.native.openUsageSettings(); return null; }
    const r = A.native.detectSleep(d);
    if (!r) { A.toast("Не удалось определить сон за эту ночь"); return null; }
    try { return JSON.parse(r); } catch (e) { return null; }
  };
  A.edit.weight = (d) => {
    const day = A.day(d);
    A.formSheet("Вес · " + fmtShort(d), [{ k: "weight", label: "Вес, кг", type: "number", step: 0.1, req: true, full: true }, { k: "weightNote", label: "Заметка", full: true }], { weight: day.weight ?? A.lastWeight(d), weightNote: day.weightNote }, (o) => {
      day.weight = o.weight; day.weightNote = o.weightNote; day.weightSrc = "manual";
      if (!A.db().profile.body.startWeight) A.db().profile.body.startWeight = o.weight;
      A.save(); A.refresh();
    }, day.weight != null ? { onDelete: () => { delete day.weight; A.save(); A.refresh(); } } : {});
  };
  A.edit.mood = (d, i) => {
    const day = A.day(d), m = i != null ? day.moods[i] : { v: 7, tags: [], note: "" };
    A.formSheet(i != null ? "Запись настроения" : "Новая запись настроения", [
      { k: "v", label: "Оценка 1–10", type: "range", min: 1, max: 10 },
      { k: "tags", label: "Эмоции", type: "multi", opts: A.FEELINGS.concat(["стресс", "благодарность", "любовь", "скука"]) },
      { k: "note", label: "Заметка: что происходит?", type: "textarea" }
    ], m, (o) => {
      if (i != null) Object.assign(day.moods[i], o); else { day.moods = day.moods || []; day.moods.push(Object.assign({ t: A.nowHM() }, o)); }
      day.mood = Math.round(A.avg(day.moods.map((x) => x.v)) * 10) / 10;
      day.feelings = [...new Set(day.moods.flatMap((x) => x.tags || []))];
      A.save(); A.refresh();
    }, i != null ? { onDelete: () => { day.moods.splice(i, 1); day.mood = day.moods.length ? Math.round(A.avg(day.moods.map((x) => x.v)) * 10) / 10 : null; A.save(); A.refresh(); } } : {});
  };
  A.edit.habitValue = (h, d) => {
    A.formSheet((h.icon || "") + " " + h.name + " · " + fmtShort(d), [{ k: "v", label: "Значение (" + (h.unit || "раз") + "), цель " + (h.target || 1), type: "number", step: "any", full: true }], { v: A.hv(h.id, d) }, (o) => { A.setHv(h, d, o.v); A.save(); A.refresh(); },
      { post: '<div class="btns">' + [1, 0.5, 0.25].map((k) => '<button type="button" class="btn sm" data-q="' + Math.round((h.target || 1) * k * 100) / 100 + '">' + (k === 1 ? "вся цель" : k === 0.5 ? "половина" : "четверть") + "</button>").join("") + "</div>", after: (w) => w.querySelectorAll("[data-q]").forEach((b) => (b.onclick = () => { w.querySelector('[name="v"]').value = b.dataset.q; })) });
  };
  A.edit.body = () => {
    const b = A.db().profile.body;
    A.formSheet("Профиль тела", [
      { k: "sex", label: "Пол", type: "chips", opts: [["f", "женский"], ["m", "мужской"]] },
      { k: "age", label: "Возраст", type: "number" }, { k: "height", label: "Рост, см", type: "number" },
      { k: "startWeight", label: "Стартовый вес, кг", type: "number", step: 0.1 }, { k: "targetWeight", label: "Целевой вес, кг", type: "number", step: 0.1 },
      { k: "goal", label: "Цель", type: "chips", opts: [["lose", "дефицит (снижение)"], ["keep", "поддержание"], ["gain", "профицит (набор)"]] },
      { k: "activity", label: "Уровень активности", type: "select", opts: [[1.375, "1.375 — лёгкая (1–3 тренировки)"], [1.465, "1.465 — умеренная (3–4)"], [1.55, "1.55 — средняя (4–5)"], [1.64, "1.64 — высокая (5–6)"], [1.725, "1.725 — очень высокая (6–7)"]] },
      { k: "protKg", label: "Белки, г на кг", type: "number", step: 0.1 }, { k: "fatKg", label: "Жиры, г на кг", type: "number", step: 0.1 },
      { k: "monthChange", label: "Целевое изменение в месяц, кг", type: "number", step: 0.1 }
    ], b, (o) => { o.activity = +o.activity; Object.assign(b, o); A.save(); A.refresh(); });
  };
  A.edit.measure = (m) => {
    const isNew = !m;
    m = m || { date: today() };
    A.formSheet("Замеры, см", [{ k: "date", label: "Дата", type: "date", full: true }, { k: "chest", label: "Грудь", type: "number", step: 0.1 }, { k: "waist", label: "Талия", type: "number", step: 0.1 }, { k: "belly", label: "Живот", type: "number", step: 0.1 }, { k: "hips", label: "Бёдра", type: "number", step: 0.1 }, { k: "arm", label: "Рука", type: "number", step: 0.1 }], m, (o) => { A.upsert("measures", Object.assign(m, o)); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("measures", m.id); A.refresh(); } });
  };
  A.addPhoto = async (view) => {
    const url = await A.pickPhoto(3 / 4, 720); if (!url) return;
    const id = A.uid(); await A.photos.put(id, url);
    A.upsert("photos", { id, date: today(), view }); A.toast("Фото сохранено"); A.refresh();
  };
  A.WHEEL = ["Здоровье", "Отношения", "Работа", "Финансы", "Рост", "Развлечения", "Семья", "Духовность"];
  A.edit.wheel = (w) => {
    const isNew = !w; w = w || { date: today(), vals: {} };
    const f = [{ k: "date", label: "Дата", type: "date", full: true }].concat(A.WHEEL.map((k) => ({ k, label: k, type: "range", min: 1, max: 10, def: w.vals[k] || 5 })));
    A.formSheet("Колесо баланса", f, Object.assign({ date: w.date }, w.vals), (o) => { w.date = o.date; w.vals = {}; A.WHEEL.forEach((k) => (w.vals[k] = o[k])); A.upsert("wheel", w); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("wheel", w.id); A.refresh(); } });
  };
  A.syncHealth = async (manual) => {
    if (!A.native || !A.native.hcAvailable || !A.native.hcAvailable()) { if (manual) A.toast("Health Connect недоступен на этом телефоне"); return; }
    if (!A.db().profile.hc && !manual) return;
    const r = await A.nativeCall("hc", () => A.native.hcSync(14));
    let o; try { o = JSON.parse(r); } catch (e) { if (manual) A.toast("Health Connect: нет доступа. Выдайте разрешения."); return; }
    if (o.error) { if (manual) A.toast("Health Connect: " + o.error); return; }
    let n = 0;
    Object.entries(o.steps || {}).forEach(([d, v]) => { const day = A.day(d); if (day.stepsSrc !== "manual" || v > (day.steps || 0)) { day.steps = v; day.stepsSrc = "hc"; n++; } });
    Object.entries(o.weight || {}).forEach(([d, v]) => { const day = A.day(d); if (day.weightSrc !== "manual") { day.weight = Math.round(v * 10) / 10; day.weightSrc = "hc"; n++; } });
    Object.entries(o.sleep || {}).forEach(([d, v]) => { const day = A.day(d); if (!day.sleep || day.sleep.src === "hc") { day.sleep = Object.assign({ q: null }, day.sleep, { bed: v.bed, wake: v.wake, src: "hc" }); n++; } });
    (o.workouts || []).forEach((w) => { if (!A.col("workouts").some((x) => x.hcId === w.id)) { A.col("workouts").push({ id: A.uid(), hcId: w.id, date: w.date, type: w.type || "other", dur: w.dur, kcal: w.kcal || null, int: 5, sets: [], note: "Health Connect" + (w.title ? ": " + w.title : "") }); n++; } });
    A.db().profile.hcLast = new Date().toISOString();
    A.save(); if (manual) A.toast("Синхронизировано записей: " + n); A.refresh();
  };
  A.edit.steps = (d) => {
    const day = A.day(d);
    A.formSheet("Шаги · " + fmtShort(d), [{ k: "steps", label: "Шагов за день", type: "number", min: 0, full: true }], { steps: day.steps }, (o) => { day.steps = o.steps == null ? null : Math.round(o.steps); day.stepsSrc = "manual"; A.save(); A.refresh(); });
  };
  A.edit.anxiety = (d) => {
    const day = A.day(d);
    const h = new Date().getHours();
    A.formSheet("Тревожность", [
      { k: "v", label: "Уровень 0–10", type: "range", min: 0, max: 10, def: 3 },
      { k: "part", label: "Время суток", type: "chips", opts: ["утро", "день", "вечер", "ночь"], def: h < 12 ? "утро" : h < 17 ? "день" : h < 23 ? "вечер" : "ночь" },
      { k: "dur", label: "Длительность", type: "select", opts: ["несколько минут", "до часа", "несколько часов", "весь день"] },
      { k: "ctx", label: "Контекст: что происходило", type: "textarea" }
    ], {}, (o) => { day.anx = (day.anx || []).concat({ v: o.v, t: o.part, dur: o.dur, ctx: o.ctx, at: A.nowHM() }); A.save(); A.refresh(); },
    { pre: '<p class="small muted">Дневник самонаблюдения, без диагностики. Если тревога мешает жить, стоит обсудить это со специалистом.</p>' });
  };
  A.edit.bodyTemp = (d) => {
    const day = A.day(d);
    A.formSheet("Температура тела", [{ k: "v", label: "Температура, °C", type: "number", step: 0.1, req: true }, { k: "t", label: "Время", type: "time", def: A.nowHM() }, { k: "note", label: "Заметка", type: "textarea" }], {}, (o) => { day.bodyT = (day.bodyT || []).concat(o); A.save(); A.refresh(); },
      { pre: '<p class="small muted">Только запись значений. Приложение не ставит диагнозов.</p>' });
  };
  A.edit.weather = (d) => {
    const day = A.day(d), w = day.weather || {};
    A.formSheet("Погода · " + fmtShort(d), [
      { k: "cond", label: "Условия", type: "chips", opts: A.WEATHER.map((x) => [x[0], x[2] + " " + x[1]]) },
      { k: "t", label: "Температура, °C", type: "number", step: 0.1 }, { k: "feel", label: "Ощущается как, °C", type: "number", step: 0.1 },
      { k: "hum", label: "Влажность, %", type: "number" }, { k: "press", label: "Давление, мм рт. ст.", type: "number" },
      { k: "precip", label: "Осадки, мм", type: "number", step: 0.1 }, { k: "pp", label: "Вероятность осадков, %", type: "number" },
      { k: "wind", label: "Ветер, м/с", type: "number", step: 0.1 }, { k: "cloud", label: "Облачность, %", type: "number" },
      { k: "rise", label: "Восход", type: "time" }, { k: "set", label: "Закат", type: "time" }
    ], w, (o) => { day.weather = Object.assign(o, { src: "manual" }); A.save(); A.refresh(); });
  };

  /* ---------- погода: сменный поставщик ---------- */
  // Любой поставщик реализует fetch(lat, lon) → Promise<запись погоды> и geocode(name) → Promise<[{name, lat, lon}]>.
  const codeToCond = (c, wind) => {
    if (wind >= 12 && c <= 3) return "wind";
    if (c === 0 || c === 1) return "sun"; if (c === 2) return "part"; if (c === 3) return "cloud";
    if (c === 45 || c === 48) return "fog"; if (c >= 95) return "storm";
    if (c === 66 || c === 67 || c === 56 || c === 57) return "sleet";
    if ((c >= 71 && c <= 77) || c === 85 || c === 86) return "snow";
    if ((c >= 51 && c <= 65) || (c >= 80 && c <= 82)) return "rain";
    return "cloud";
  };
  A.WeatherProviders = {
    openmeteo: {
      name: "Open-Meteo",
      async fetch(lat, lon) {
        const u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,precipitation,weather_code,cloud_cover,wind_speed_10m&daily=sunrise,sunset,precipitation_probability_max,temperature_2m_max,temperature_2m_min&wind_speed_unit=ms&timezone=auto&forecast_days=1";
        const r = await fetch(u); if (!r.ok) throw new Error("HTTP " + r.status);
        const j = await r.json(); const c = j.current, dd = j.daily;
        return {
          t: Math.round(c.temperature_2m * 10) / 10, feel: Math.round(c.apparent_temperature * 10) / 10, hum: c.relative_humidity_2m,
          press: Math.round(c.surface_pressure * 0.750062), precip: c.precipitation, cloud: c.cloud_cover, wind: Math.round(c.wind_speed_10m * 10) / 10,
          cond: codeToCond(c.weather_code, c.wind_speed_10m), pp: dd.precipitation_probability_max ? dd.precipitation_probability_max[0] : null,
          tmax: dd.temperature_2m_max[0], tmin: dd.temperature_2m_min[0], rise: (dd.sunrise[0] || "").slice(11, 16), set: (dd.sunset[0] || "").slice(11, 16)
        };
      },
      async geocode(name) {
        const r = await fetch("https://geocoding-api.open-meteo.com/v1/search?count=6&language=ru&name=" + encodeURIComponent(name));
        const j = await r.json();
        return (j.results || []).map((x) => ({ name: x.name + (x.admin1 ? ", " + x.admin1 : "") + (x.country ? ", " + x.country : ""), lat: x.latitude, lon: x.longitude }));
      }
    }
  };
  A.weatherProvider = () => A.WeatherProviders[A.db().profile.weatherProvider || "openmeteo"] || A.WeatherProviders.openmeteo;
  A.weatherRefresh = async (manual) => {
    const p = A.db().profile;
    if (p.lat == null) { if (manual) { A.toast("Укажите город в настройках"); A.go("settings/profile"); } return; }
    const day = A.day(today());
    if (!manual && day.weather && day.weather.src === "api" && day.weather.ts && Date.now() - day.weather.ts < 3 * 3600e3) return;
    if (day.weather && day.weather.src === "manual" && !manual) return;
    try {
      const w = await A.weatherProvider().fetch(p.lat, p.lon);
      day.weather = Object.assign(w, { src: "api", at: A.nowHM(), ts: Date.now() });
      p.lastWeather = day.weather;
      A.save(); if (manual) A.toast("Погода обновлена"); A.refresh();
    } catch (e) {
      if (manual) A.toast("Нет связи. Показано последнее значение — можно ввести вручную.");
      if (!day.weather && p.lastWeather) { day.weather = Object.assign({}, p.lastWeather, { src: "cache" }); A.save(); A.refresh(); }
    }
  };

  /* ---------- шаги из датчика ---------- */
  A.syncSteps = async (manual) => {
    if (!A.native || !A.db().profile.stepsSensor) return;
    if (!A.native.stepsAvailable()) { if (manual) A.toast("В телефоне нет датчика шагов"); return; }
    const v = await A.nativeCall("steps", () => A.native.requestSteps());
    const n = parseInt(v, 10);
    if (isFinite(n) && n >= 0) {
      const day = A.day(today());
      // Не затираем ручной ввод меньшим значением.
      if (day.stepsSrc !== "manual" || n > (day.steps || 0)) { day.steps = n; day.stepsSrc = "sensor"; A.save(); A.refresh(); }
      if (manual) A.toast("Шаги: " + n);
    } else if (manual) A.toast("Нет данных от датчика (нужно разрешение «Физическая активность»)");
  };

  /* ---------- экран ---------- */
  const TABS = [["weight", "Вес"], ["body", "Тело"], ["sleep", "Сон"], ["steps", "Шаги"], ["water", "Вода"], ["state", "Настроение"], ["balance", "Баланс"], ["temp", "Температура"], ["weather", "Погода"]];
  const periodSeg = (per) => A.seg([["7", "7 дн"], ["30", "30 дн"], ["90", "90 дн"], ["365", "Год"]], per, "per");
  const labelFor = (d, n) => n > 60 ? fmtShort(d).slice(3) : fmtShort(d).slice(0, 2);

  function series(key, days, get) { return days.map((d) => ({ x: labelFor(d, days.length), v: get(d) })); }
  const statsRow = (s, fmt = (v) => fmtN(v, 1)) => '<div class="grid3" style="margin-top:10px"><div class="stat"><small>Среднее</small><b>' + fmt(s.avg) + '</b></div><div class="stat"><small>Минимум</small><b>' + fmt(s.min) + '</b></div><div class="stat"><small>Максимум</small><b>' + fmt(s.max) + "</b></div></div>";
  // Для длинных периодов усредняем по неделям, чтобы график оставался читаемым.
  function compress(data, days) {
    if (days.length <= 90) return data;
    const out = []; for (let i = 0; i < data.length; i += 7) { const ch = data.slice(i, i + 7); out.push({ x: ch[0].x, v: A.avg(ch.map((c) => c.v)) }); } return out;
  }

  A.view("health", {
    title: "Здоровье и активность",
    render(el, r) {
      const tab = r.args[0] || "weight", per = r.params.per || "30";
      const days = A.lastDays(+per);
      const p = A.db().profile, t = today(), day = A.day(t, false) || {};
      let h = A.seg(TABS, tab, "tab") + periodSeg(per);
      if (tab === "weight") {
        const cur = A.lastWeight(), b = p.body, tr = A.weightTrend(14), fc = A.weightForecast();
        const cls = tr.arrow === "↑" ? "up" : tr.arrow === "↓" ? "down" : "flat";
        const wk = A.lastDays(7);
        h = A.seg(TABS, tab, "tab");
        h += '<div class="card"><h3>{{i:scale}} Вес<span class="sp"></span><button class="btn sm primary" data-a="w">+ взвешивание</button></h3><div class="row"><div class="big">' + (cur ? fmtN(cur, 1) + " кг" : "—") + '</div><div class="trend ' + cls + '">' + tr.arrow + '</div><div class="grow small muted">' + esc(tr.word) + (tr.wk != null ? "<br>" + (tr.wk > 0 ? "+" : "") + tr.wk.toFixed(2) + " кг в неделю" : "") + "</div></div>" +
          '<div class="small muted" style="margin-top:8px">Неделя</div>' + A.charts.line([{ name: "Вес", color: "#6C9FD3", data: wk.map((d) => ({ x: A.DOW[A.dow(d) - 1], v: (A.dayGet(d) || {}).weight ?? null })) }], { h: 120 }) + "</div>";
        h += periodSeg(r.params.per || "90");
        const per = +(r.params.per || 90), pd = A.lastDays(per), pts = A.weightPoints(pd[0], today());
        const st = A.statsOf(pts.map((x) => x.v));
        h += '<div class="card"><h3>Тренд за ' + (per === 30 ? "месяц" : per === 90 ? "квартал" : per === 365 ? "год" : per + " дн.") + "</h3>" + A.charts.line([{ name: "Вес", color: "#6C9FD3", data: compress(pd.map((d) => ({ x: labelFor(d, pd.length), v: (A.dayGet(d) || {}).weight ?? null })), pd) }]) +
          '<div class="grid2" style="margin-top:10px"><div class="stat"><small>Минимум</small><b>' + fmtN(st.min, 1) + '</b></div><div class="stat"><small>Максимум</small><b>' + fmtN(st.max, 1) + '</b></div><div class="stat"><small>Среднее</small><b>' + fmtN(st.avg, 1) + '</b></div><div class="stat"><small>Изменение</small><b>' + (pts.length > 1 ? ((pts[pts.length - 1].v - pts[0].v > 0 ? "+" : "") + (pts[pts.length - 1].v - pts[0].v).toFixed(1)) : "—") + " кг</b></div></div></div>";
        h += '<div class="card tint"><h3>{{i:target}} Прогноз</h3>' + (b.targetWeight ? "<p>Цель: <b>" + fmtN(b.targetWeight, 1) + " кг</b>" + (cur ? " · осталось " + fmtN(Math.abs(cur - b.targetWeight), 1) + " кг" : "") + "</p>" + (fc ? "<p>При текущем темпе цель будет достигнута примерно <b>" + fmtDate(fc, { year: true }) + "</b>.</p>" : '<p class="small muted">Для прогноза нужно несколько взвешиваний за 4 недели с движением в сторону цели.</p>') : '<p class="small muted">Укажите целевой вес во вкладке «Тело».</p>') + '<p class="small muted">Прогноз — простая линейная оценка, а не обещание. Вес колеблется день ото дня, смотрите на тренд.</p></div>';
        if (A.native) h += '<div class="card"><h3>Health Connect</h3><p class="small">Автоматически забирать вес из умных весов, шаги, сон и тренировки с часов через Health Connect.</p><label class="switch"><input type="checkbox" data-c="hcOn"' + (p.hc ? " checked" : "") + '><span></span>Синхронизация при запуске и в фоне</label><button class="btn sm" data-a="hcSync">Синхронизировать сейчас</button>' + (p.hcLast ? '<p class="small muted">Последняя синхронизация: ' + new Date(p.hcLast).toLocaleString("ru-RU") + "</p>" : "") + "</div>";
        const all = Object.entries(A.db().days).filter(([, x]) => x.weight != null).sort((a, b2) => (a[0] < b2[0] ? 1 : -1));
        h += '<div class="card"><h3>История взвешиваний</h3>' + (all.length ? all.slice(0, 60).map(([d, x], i) => { const prev = all[i + 1]; const dd = prev ? x.weight - prev[1].weight : 0; return '<div class="item" data-a="wD" data-d="' + d + '"><div class="tx"><b>' + fmtDate(d) + "</b><small>" + (x.weightSrc === "hc" ? "Health Connect" : "вручную") + (x.weightNote ? " · " + esc(x.weightNote) : "") + '</small></div><b class="num">' + fmtN(x.weight, 1) + ' кг</b><small class="num" style="min-width:44px;text-align:right;color:' + (dd > 0 ? "var(--warn)" : dd < 0 ? "var(--good)" : "var(--ink2)") + '">' + (prev ? (dd > 0 ? "+" : "") + dd.toFixed(1) : "") + "</small></div>"; }).join("") : A.empty("Взвешиваний пока нет")) + "</div>";
        return h;
      }
      if (tab === "body") {
        const b = p.body, T = A.bodyTargets();
        h = A.seg(TABS, tab, "tab");
        h += '<div class="card"><h3>{{i:person-standing}} Профиль тела<span class="sp"></span><button class="btn sm" data-a="bodyEdit">изменить</button></h3><div class="kv"><b>Пол</b><span>' + (b.sex === "m" ? "мужской" : "женский") + "</span><b>Возраст</b><span>" + (b.age || "—") + "</span><b>Рост</b><span>" + (b.height || "—") + " см</span><b>Стартовый вес</b><span>" + fmtN(b.startWeight, 1) + " кг</span><b>Целевой вес</b><span>" + fmtN(b.targetWeight, 1) + " кг</span><b>Цель</b><span>" + { lose: "дефицит калорий", keep: "поддержание", gain: "профицит калорий" }[b.goal] + "</span><b>Активность</b><span>" + b.activity + "</span><b>Белки / жиры</b><span>" + b.protKg + " / " + b.fatKg + " г на кг</span><b>Изменение в месяц</b><span>" + fmtN(b.monthChange, 1) + " кг</span></div></div>";
        if (T) h += '<div class="card tint"><h3>Расчёт (формула Миффлина — Сан-Жеора)</h3><div class="grid3"><div class="stat"><small>Базовый обмен</small><b>' + T.bmr + '</b></div><div class="stat"><small>Расход с активностью</small><b>' + T.tdee + '</b></div><div class="stat"><small>Цель, ккал</small><b>' + T.kcal + '</b></div><div class="stat"><small>Белки</small><b>' + T.prot + ' г</b></div><div class="stat"><small>Жиры</small><b>' + T.fat + ' г</b></div><div class="stat"><small>Углеводы</small><b>' + T.carb + ' г</b></div></div><button class="btn primary block" data-a="applyT" style="margin-top:10px">Сделать целями питания</button><p class="small muted">Дефицит ограничен 25% от расхода, профицит — 500 ккал. Это ориентир, а не медицинская рекомендация.</p></div>';
        const ms = A.col("measures").slice().sort((a, b2) => (a.date < b2.date ? 1 : -1));
        const MK = [["chest", "Грудь"], ["waist", "Талия"], ["belly", "Живот"], ["hips", "Бёдра"], ["arm", "Рука"]];
        h += '<div class="card"><h3>{{i:ruler}} Замеры<span class="sp"></span><button class="btn sm primary" data-a="mAdd">+ замеры</button></h3>' + (ms.length ? '<div style="overflow-x:auto"><table class="tbl"><tr><th>Дата</th>' + MK.map((k) => '<th class="r">' + k[1] + "</th>").join("") + "</tr>" + ms.map((m) => '<tr data-a="mEdit" data-id="' + m.id + '"><td>' + fmtShort(m.date) + "</td>" + MK.map((k) => '<td class="r">' + (m[k[0]] ?? "·") + "</td>").join("") + "</tr>").join("") + "</table></div>" + (ms.length > 1 ? '<p class="small">Изменение с ' + fmtShort(ms[ms.length - 1].date) + ": " + MK.map((k) => (ms[0][k[0]] != null && ms[ms.length - 1][k[0]] != null ? k[1].toLowerCase() + " " + ((ms[0][k[0]] - ms[ms.length - 1][k[0]]) > 0 ? "+" : "") + (ms[0][k[0]] - ms[ms.length - 1][k[0]]).toFixed(1) : "")).filter(Boolean).join(", ") + "</p>" : "") : A.empty("Замеров пока нет")) + "</div>";
        const ph = A.col("photos").slice().sort((a, b2) => (a.date < b2.date ? 1 : -1));
        const V = [["front", "Анфас"], ["side", "Профиль"], ["back", "Спина"]];
        const view = r.params.v || "front";
        const pv = ph.filter((x) => x.view === view);
        h += '<div class="card"><h3>{{i:camera}} Фото прогресса</h3>' + A.seg(V, view, "pview") + '<div class="btns" style="margin:0 0 10px">' + V.map(([k, n]) => '<button class="btn sm" data-a="phAdd" data-v="' + k + '">+ ' + n + "</button>").join("") + "</div>";
        if (pv.length >= 2) { const a = r.params.a || pv[pv.length - 1].id, c = r.params.b || pv[0].id; const pa = pv.find((x) => x.id === a) || pv[pv.length - 1], pc = pv.find((x) => x.id === c) || pv[0]; h += '<div class="small muted">Сравнение: выберите два фото ниже (первое — «было»)</div><div class="photo-cmp" style="margin:8px 0"><div><img data-photo="' + pa.id + '" alt="Было"><div class="small" style="text-align:center">' + fmtDate(pa.date) + '</div></div><div><img data-photo="' + pc.id + '" alt="Стало"><div class="small" style="text-align:center">' + fmtDate(pc.date) + "</div></div></div>"; }
        h += '<div class="rgrid" style="grid-template-columns:repeat(3,1fr)">' + (pv.length ? pv.map((x) => '<div class="tap" data-a="phPick" data-id="' + x.id + '"><img data-photo="' + x.id + '" style="width:100%;aspect-ratio:3/4;object-fit:cover;border-radius:10px" alt=""><div class="small" style="text-align:center">' + fmtShort(x.date) + "</div></div>").join("") : '<p class="small muted">Фото пока нет</p>') + '</div><p class="small muted">Нажмите на фото, чтобы выбрать его для сравнения; долгое удаление — через кнопку ✕ в просмотре.</p></div>';
        return h;
      }
      if (tab === "balance") {
        const W = A.col("wheel").slice().sort((a, b2) => (a.date < b2.date ? 1 : -1));
        const last = W[0], SPH = A.WHEEL;
        h = A.seg(TABS, tab, "tab");
        h += '<div class="card"><h3>{{i:radar}} Колесо баланса<span class="sp"></span><button class="btn sm primary" data-a="wheelNew">+ оценка</button></h3>' + (last ? '<div class="small muted">' + fmtDate(last.date) + "</div>" + A.charts.radar(SPH.map((k) => ({ name: k, v: last.vals[k] || 0 }))) + '<div class="grid2">' + SPH.map((k) => '<div class="stat"><small>' + k + "</small><b>" + (last.vals[k] || 0) + "/10</b>" + (W[1] ? '<small>' + ((last.vals[k] || 0) - (W[1].vals[k] || 0) >= 0 ? "+" : "") + ((last.vals[k] || 0) - (W[1].vals[k] || 0)) + " к прошлой</small>" : "") + "</div>").join("") + "</div>" : A.empty("Оцените 8 сфер жизни от 1 до 10")) + "</div>";
        if (W.length > 1) h += '<div class="card"><h3>История оценок</h3>' + A.charts.line(SPH.slice(0, 8).map((k, i) => ({ name: k, color: A.PAL[i], data: W.slice().reverse().map((w) => ({ x: fmtShort(w.date), v: w.vals[k] || 0 })) })), { min: 0, max: 10 }) + W.map((w) => '<div class="item" data-a="wheelEdit" data-id="' + w.id + '"><div class="tx"><b>' + fmtDate(w.date) + "</b><small>среднее " + fmtN(A.avg(SPH.map((k) => w.vals[k] || 0)), 1) + "</small></div></div>").join("") + "</div>";
        return h;
      }
      if (tab === "sleep") {
        const data = series("sleep", days, A.sleepH);
        const s = A.statsOf(data.map((x) => x.v));
        const goal = p.sleepGoal || 8, wk = A.lastDays(7);
        h += '<div class="card"><h3>{{i:moon}} Сон за неделю<span class="sp"></span><button class="btn sm primary" data-a="sleep">+ сегодня</button></h3>' + A.charts.bars(wk.map((d) => ({ x: A.DOW[A.dow(d) - 1], v: A.sleepH(d), c: A.sleepH(d) != null && A.sleepH(d) < goal - 1 ? "#E8A0A8" : "#9C8FD8" })), { goal, color: "#9C8FD8", fmt: (v) => fmtDur(v * 60) }) +
          '<div class="row" style="margin-top:8px"><span class="small muted grow">Целевое время сна</span><select data-c="sleepGoal" style="width:110px">' + [6, 6.5, 7, 7.5, 8, 8.5, 9].map((x) => "<option" + (x === goal ? " selected" : "") + ' value="' + x + '">' + x + " ч</option>").join("") + "</select></div>" +
          (A.native ? '<div class="btns"><button class="btn sm" data-a="sleepAutoToday">Определить сон по телефону</button><label class="switch small"><input type="checkbox" data-c="sleepAutoOn"' + (p.sleepAuto ? " checked" : "") + "><span></span>каждое утро автоматически</label></div>" : "") + "</div>";
        h += '<div class="card"><h3>Продолжительность за период</h3>' + A.charts.bars(compress(data, days), { goal, color: "#9C8FD8", fmt: (v) => fmtDur(v * 60) }) + statsRow(s, (v) => v == null ? "—" : fmtDur(v * 60)) +
          '<div class="grid2" style="margin-top:10px"><div class="stat"><small>Качество (сред.)</small><b>' + fmtN(A.avg(days.map((d) => { const q = ((A.dayGet(d) || {}).sleep || {}).q; return q > 5 ? q / 2 : q; })), 1) + ' ★</b></div><div class="stat"><small>Дней ≥ ' + goal + ' ч</small><b>' + data.filter((x) => x.v != null && x.v >= goal).length + '</b></div><div class="stat"><small>Серия ≥ ' + goal + ' ч</small><b>' + A.streakMetric((d) => (A.sleepH(d) || 0) >= goal) + ' дн.</b></div><div class="stat"><small>Частые заметки</small><b style="font-size:13px">' + esc(Object.entries(days.flatMap((d) => (((A.dayGet(d) || {}).sleep || {}).tags) || []).reduce((m, t2) => ((m[t2] = (m[t2] || 0) + 1), m), {})).sort((a, b2) => b2[1] - a[1]).slice(0, 2).map((x) => x[0]).join(", ") || "—") + "</b></div></div></div>";
        const month = A.lastDays(31).reverse().filter((d) => (A.dayGet(d) || {}).sleep);
        h += '<div class="card"><h3>История сна за месяц</h3>' + (month.length ? month.map((d) => { const s2 = A.dayGet(d).sleep; const q = s2.q > 5 ? Math.round(s2.q / 2) : s2.q; return '<div class="item" data-a="sleepD" data-d="' + d + '"><div class="tx"><b>' + fmtDate(d) + "</b><small>" + esc((s2.bed || "?") + " → " + (s2.wake || "?")) + " · " + (q ? "★".repeat(q) : "без оценки") + (s2.tags && s2.tags.length ? " · " + esc(s2.tags.join(", ")) : "") + (s2.src === "hc" ? " · Health Connect" : s2.auto ? " · авто" : "") + '</small></div><b class="num">' + fmtDur((A.sleepH(d) || 0) * 60) + "</b></div>"; }).join("") : A.empty("Записей нет")) + "</div>";
      } else if (tab === "steps") {
        const data = series("steps", days, (d) => (A.dayGet(d) || {}).steps ?? null);
        const s = A.statsOf(data.map((x) => x.v));
        const hit = data.filter((x) => x.v != null && x.v >= p.stepsGoal).length;
        h += '<div class="card"><h3>{{i:footprints}} Шаги<span class="sp"></span><button class="btn sm primary" data-a="steps">ввести</button></h3>' + A.charts.bars(compress(data, days), { goal: p.stepsGoal, color: "#5FA774" }) + statsRow(s, (v) => fmtN(v)) +
          '<div class="grid3" style="margin-top:8px"><div class="stat"><small>Всего</small><b>' + fmtN(s.sum) + '</b></div><div class="stat"><small>Дней в цели</small><b>' + hit + "/" + s.n + '</b></div><div class="stat"><small>Серия</small><b>' + A.streakMetric((d) => ((A.dayGet(d) || {}).steps || 0) >= p.stepsGoal) + "</b></div></div></div>";
        h += '<div class="card"><h3>Автоматический импорт</h3>' + (A.native ? '<label class="switch"><input type="checkbox" data-c="sensor"' + (p.stepsSensor ? " checked" : "") + "><span></span>Брать шаги из датчика телефона</label><p class=\"small muted\">Счёт идёт с полуночи или с первого открытия приложения за день. Ручной ввод всегда доступен.</p>" + (p.stepsSensor ? '<button class="btn sm" data-a="sync">Обновить сейчас</button>' : "") : '<p class="small muted">Импорт шагов из датчика доступен в Android-приложении.</p>') + "</div>";
      } else if (tab === "water") {
        const data = series("water", days, (d) => (A.dayGet(d) || {}).water ?? null);
        h += A.todayCards.water(t, day);
        const log = (day.waterLog || []).slice().reverse();
        h += '<div class="card"><h3>Сегодня по времени</h3>' + (log.length ? log.map((x) => '<div class="row"><span class="grow small">' + esc(x.t) + '</span><b class="num">' + (x.ml > 0 ? "+" : "") + x.ml + " мл</b></div>").join("") : '<p class="small muted">Записей нет — нажимайте кнопки +100 / +250 / +330 / +500.</p>') + "</div>";
        h += '<div class="card"><h3>Неделя</h3>' + A.charts.bars(A.lastDays(7).map((d) => ({ x: A.DOW[A.dow(d) - 1], v: (A.dayGet(d) || {}).water || null })), { goal: p.waterGoal, color: "#7DB0D6" }) + "</div>";
        h += '<div class="card"><h3>История</h3>' + A.charts.bars(compress(data, days), { goal: p.waterGoal, color: "#7DB0D6" }) + statsRow(A.statsOf(data.map((x) => x.v)), (v) => fmtN(v) + " мл") + "</div>";
      } else if (tab === "state") {
        h += A.todayCards.state(t, day);
        const me = day.moods || [];
        h += '<div class="card"><h3>Записи настроения сегодня<span class="sp"></span><button class="btn sm primary" data-a="moodEntry">+ запись</button></h3>' + (me.length ? me.map((m, i) => '<div class="item" data-a="moodEdit" data-i="' + i + '"><div class="ic" style="background:' + A.scaleColor(m.v) + ';color:#fff">' + m.v + '</div><div class="tx"><b>' + esc(m.t) + (m.tags && m.tags.length ? " · " + esc(m.tags.join(", ")) : "") + "</b><small>" + esc(m.note || "") + "</small></div></div>").join("") : A.empty("Можно записывать настроение несколько раз в день")) + "</div>";
        const mk = (get) => compress(series("", days, get), days);
        h += '<div class="card"><h3>Динамика</h3>' + A.charts.line([
          { name: "Настроение", color: "#8CC474", data: mk((d) => (A.dayGet(d) || {}).mood ?? null) },
          { name: "Энергия", color: "#F0A45B", data: mk(A.energyAvg) },
          { name: "Стресс", color: "#D2555E", data: mk((d) => (A.dayGet(d) || {}).stress ?? null) },
          { name: "Тревожность", color: "#8E7CC3", data: mk(A.anxVal) }
        ], { min: 0, max: 10 }) + "</div>";
        const en = { m: [], d: [], e: [] }; days.forEach((d) => { const e = (A.dayGet(d) || {}).energy; if (e) ["m", "d", "e"].forEach((k) => e[k] != null && en[k].push(e[k])); });
        h += '<div class="grid3"><div class="stat"><small>Энергия утром</small><b>' + fmtN(A.avg(en.m), 1) + '</b></div><div class="stat"><small>Днём</small><b>' + fmtN(A.avg(en.d), 1) + '</b></div><div class="stat"><small>Вечером</small><b>' + fmtN(A.avg(en.e), 1) + "</b></div></div>";
        const fc = {}; days.forEach((d) => ((A.dayGet(d) || {}).feelings || []).forEach((f) => (fc[f] = (fc[f] || 0) + 1)));
        const sc = {}; days.forEach((d) => ((A.dayGet(d) || {}).stressSrc || []).forEach((f) => (sc[f] = (sc[f] || 0) + 1)));
        const pal = ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6", "#B6A6D9", "#9CC5A1", "#F3C29F", "#D8C08A", "#EBB0BC", "#A9CBE5"];
        if (Object.keys(fc).length) h += '<div class="card" style="margin-top:12px"><h3>Чувства за период</h3>' + A.charts.donut(Object.entries(fc).map(([n, v], i) => ({ name: n, v, color: pal[i % pal.length] })), A.sum(Object.values(fc)), "отметок") + "</div>";
        if (Object.keys(sc).length) h += '<div class="card"><h3>Источники стресса</h3>' + A.charts.donut(Object.entries(sc).map(([n, v], i) => ({ name: n, v, color: pal[i % pal.length] })), A.sum(Object.values(sc)), "отметок") + "</div>";
        const anx = days.flatMap((d) => ((A.dayGet(d) || {}).anx || []).map((a) => Object.assign({ d }, a))).reverse().slice(0, 12);
        h += '<div class="card"><h3>Дневник тревожности</h3>' + (anx.length ? anx.map((a) => '<div class="item"><div class="ic" style="background:' + A.scaleColor(a.v, 10, "anx") + ';color:#fff">' + a.v + '</div><div class="tx"><b>' + fmtShort(a.d) + " · " + esc(a.t || "") + " · " + esc(a.dur || "") + "</b><small>" + esc(a.ctx || "") + "</small></div></div>").join("") : A.empty("Записей нет")) + "</div>";
      } else if (tab === "temp") {
        const recs = days.flatMap((d) => ((A.dayGet(d) || {}).bodyT || []).map((x, i) => Object.assign({ d, i }, x)));
        h += '<div class="card"><h3>{{i:thermometer}} Температура тела<span class="sp"></span><button class="btn sm primary" data-a="bodyT">+ запись</button></h3>' +
          A.charts.line([{ name: "°C", color: "#E3899A", data: recs.map((x) => ({ x: fmtShort(x.d), v: x.v })) }], { title: "Температура тела" }) +
          '<div class="list">' + recs.slice().reverse().map((x) => '<div class="item"><div class="tx"><b>' + fmtN(x.v, 1) + " °C</b><small>" + fmtShort(x.d) + " " + esc(x.t || "") + (x.note ? " · " + esc(x.note) : "") + '</small></div><button class="icon-btn" data-a="delT" data-d="' + x.d + '" data-i="' + x.i + '" aria-label="Удалить">✕</button></div>').join("") + '</div><p class="small muted">Температура окружающей среды хранится отдельно — во вкладке «Погода».</p></div>';
      } else if (tab === "weather") {
        h += A.todayCards.weather(t, day);
        const data = series("temp", days, (d) => { const w = (A.dayGet(d) || {}).weather; return w && w.t != null ? w.t : null; });
        h += '<div class="card"><h3>График температуры воздуха</h3>' + A.charts.line([{ name: "°C", color: "#F08A3C", data: compress(data, days) }]) + statsRow(A.statsOf(data.map((x) => x.v)), (v) => fmtN(v, 1) + "°") + "</div>";
        const cnt = {}; days.forEach((d) => { const w = (A.dayGet(d) || {}).weather; if (w && w.cond) cnt[w.cond] = (cnt[w.cond] || 0) + 1; });
        if (Object.keys(cnt).length) h += '<div class="card"><h3>Погода за период</h3>' + A.charts.donut(Object.entries(cnt).map(([k, v]) => { const w = A.weather(k); return { name: w.icon + " " + w.name, v, color: w.color }; }), A.sum(Object.values(cnt)), "дней") + "</div>";
        h += '<div class="card"><h3>История</h3>' + days.slice().reverse().filter((d) => (A.dayGet(d) || {}).weather).slice(0, 20).map((d) => { const w = A.dayGet(d).weather, c = A.weather(w.cond); return '<div class="item" data-a="weatherD" data-d="' + d + '"><div class="ic">' + (c ? c.icon : "{{i:thermometer}}") + '</div><div class="tx"><b>' + fmtDate(d) + "</b><small>" + esc(c ? c.name : "") + (w.hum != null ? " · " + w.hum + "%" : "") + (w.press != null ? " · " + w.press + " мм" : "") + (w.src === "manual" ? " · вручную" : "") + '</small></div><b class="num">' + (w.t != null ? fmtN(w.t) + "°" : "") + "</b></div>"; }).join("") + '<p class="small muted">Источник: ' + esc(A.weatherProvider().name) + ". Без интернета показывается последнее полученное значение.</p></div>";
      }
      return h;
    },
    bind(el, r) {
      const tab = r.args[0] || "weight";
      A.bind(el, Object.assign(A.dayHandlers(() => today()), {
        tab(b) { A.go("health/" + b.dataset.v + (A.route().params.per ? "?per=" + A.route().params.per : ""), true); },
        per(b) { A.go("health/" + tab + "?per=" + b.dataset.v, true); },
        sleepD(b) { A.edit.sleep(b.dataset.d); }, weatherD(b) { A.edit.weather(b.dataset.d); },
        w() { A.edit.weight(today()); }, wD(b) { A.edit.weight(b.dataset.d); },
        hcOn(b) { A.db().profile.hc = b.checked; A.save(); if (b.checked) A.syncHealth(true); },
        hcSync() { A.syncHealth(true); },
        bodyEdit() { A.edit.body(); },
        applyT() { const T = A.bodyTargets(), p = A.db().profile; Object.assign(p, { kcal: T.kcal, prot: T.prot, fat: T.fat, carb: T.carb }); A.save(); A.toast("Цели питания обновлены"); },
        mAdd() { A.edit.measure(); }, mEdit(b) { A.edit.measure(A.byId("measures", b.dataset.id)); },
        pview(b) { A.go("health/body?v=" + b.dataset.v, true); },
        phAdd(b) { A.addPhoto(b.dataset.v); },
        phPick(b) {
          const P = A.route().params, id = b.dataset.id, ph = A.byId("photos", id);
          A.sheet("Фото · " + fmtDate(ph.date), '<img data-photo="' + id + '" style="width:100%;border-radius:12px" alt="">', { buttons: [
            { label: "Удалить", cls: "danger ghost", onClick: () => { A.confirm("Удалить фото?", () => { A.photos.del(id); A.remove("photos", id); A.refresh(); }, "Удалить", true); return false; } },
            { label: "Как «было»", cls: "", onClick: () => A.go("health/body?v=" + (P.v || ph.view) + "&a=" + id + (P.b ? "&b=" + P.b : ""), true) },
            { label: "Как «стало»", cls: "primary", onClick: () => A.go("health/body?v=" + (P.v || ph.view) + "&b=" + id + (P.a ? "&a=" + P.a : ""), true) }] });
          A.hydratePhotos(document.querySelector(".sheet-wrap:last-child"));
        },
        wheelNew() { A.edit.wheel(); }, wheelEdit(b) { A.edit.wheel(A.byId("wheel", b.dataset.id)); },
        moodEdit(b) { A.edit.mood(today(), +b.dataset.i); },
        sleepGoal(b) { A.db().profile.sleepGoal = +b.value; A.save(); A.refresh(); },
        sleepAutoOn(b) { A.db().profile.sleepAuto = b.checked; A.save(); if (b.checked) A.detectSleep(today()); },
        async sleepAutoToday() { const r = await A.detectSleep(today()); if (r) { const day = A.day(today()); day.sleep = Object.assign({}, day.sleep, { bed: r.bed, wake: r.wake, auto: true }); A.save(); A.toast("Сон: " + r.bed + " → " + r.wake); A.refresh(); } },
        bodyT() { A.edit.bodyTemp(today()); },
        delT(b) { const day = A.day(b.dataset.d); day.bodyT.splice(+b.dataset.i, 1); A.save(); A.refresh(); },
        sensor(b) { A.db().profile.stepsSensor = b.checked; A.save(); if (b.checked) A.syncSteps(true); A.refresh(); },
        sync() { A.syncSteps(true); }
      }));
    }
  });
})();
