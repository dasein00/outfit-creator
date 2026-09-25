/* Здоровье и активность: сон, шаги, вода, состояние, температура тела, погода. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtDur, fmtShort, fmtDate } = A;
  A.edit = A.edit || {};

  /* ---------- редакторы ---------- */
  A.edit.sleep = (d) => {
    const day = A.day(d), s = day.sleep || {};
    const fields = [
      { k: "bed", label: "Отход ко сну", type: "time" }, { k: "asleep", label: "Засыпание", type: "time" },
      { k: "wake", label: "Пробуждение", type: "time" }, { k: "wakeups", label: "Пробуждений ночью", type: "number", min: 0 },
      { k: "q", label: "Качество сна 1–10", type: "range", min: 1, max: 10, def: 7 },
      { k: "rec", label: "Ощущение восстановления 1–10", type: "range", min: 1, max: 10, def: 7 },
      { k: "hours", label: "Или продолжительность вручную, ч", type: "number", step: 0.25 },
      { k: "note", label: "Заметка", type: "textarea" }
    ];
    A.formSheet("Сон · ночь на " + fmtShort(d), fields, Object.assign({ bed: A.db().profile.bed, wake: A.db().profile.wake }, s), (o) => {
      if (!o.hours) delete o.hours;
      day.sleep = o; A.save(); A.refresh();
    }, { pre: '<p class="small muted">Сон записывается на день пробуждения.</p>', onDelete: day.sleep ? () => { delete day.sleep; A.save(); A.refresh(); } : null });
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
  const TABS = [["sleep", "Сон"], ["steps", "Шаги"], ["water", "Вода"], ["state", "Состояние"], ["temp", "Температура"], ["weather", "Погода"]];
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
      const tab = r.args[0] || "sleep", per = r.params.per || "30";
      const days = A.lastDays(+per);
      const p = A.db().profile, t = today(), day = A.day(t, false) || {};
      let h = A.seg(TABS, tab, "tab") + periodSeg(per);
      if (tab === "sleep") {
        const data = series("sleep", days, A.sleepH);
        const s = A.statsOf(data.map((x) => x.v));
        h += '<div class="card"><h3>🌙 Продолжительность сна<span class="sp"></span><button class="btn sm primary" data-a="sleep">+ сегодня</button></h3>' + A.charts.bars(compress(data, days), { goal: p.sleepMin, color: "#9C8FD8", fmt: (v) => fmtDur(v * 60) }) + statsRow(s, (v) => v == null ? "—" : fmtDur(v * 60)) +
          '<div class="grid2" style="margin-top:10px"><div class="stat"><small>Качество (сред.)</small><b>' + fmtN(A.avg(days.map((d) => ((A.dayGet(d) || {}).sleep || {}).q)), 1) + '</b></div><div class="stat"><small>Восстановление (сред.)</small><b>' + fmtN(A.avg(days.map((d) => ((A.dayGet(d) || {}).sleep || {}).rec)), 1) + '</b></div><div class="stat"><small>Дней в цели ' + p.sleepMin + "–" + p.sleepMax + ' ч</small><b>' + data.filter((x) => x.v != null && x.v >= p.sleepMin && x.v <= p.sleepMax + 0.5).length + '</b></div><div class="stat"><small>Серия ≥ ' + p.sleepMin + ' ч</small><b>' + A.streakMetric((d) => (A.sleepH(d) || 0) >= p.sleepMin) + " дн.</b></div></div></div>";
        h += '<div class="card"><h3>Журнал</h3>' + days.slice().reverse().filter((d) => (A.dayGet(d) || {}).sleep).slice(0, 14).map((d) => { const s2 = A.dayGet(d).sleep; return '<div class="item" data-a="sleepD" data-d="' + d + '"><div class="tx"><b>' + fmtDate(d) + "</b><small>" + esc((s2.bed || "?") + " → " + (s2.wake || "?")) + " · кач. " + (s2.q ?? "—") + " · восст. " + (s2.rec ?? "—") + '</small></div><b class="num">' + fmtDur(A.sleepH(d) * 60) + "</b></div>"; }).join("") + "</div>";
      } else if (tab === "steps") {
        const data = series("steps", days, (d) => (A.dayGet(d) || {}).steps ?? null);
        const s = A.statsOf(data.map((x) => x.v));
        const hit = data.filter((x) => x.v != null && x.v >= p.stepsGoal).length;
        h += '<div class="card"><h3>👟 Шаги<span class="sp"></span><button class="btn sm primary" data-a="steps">ввести</button></h3>' + A.charts.bars(compress(data, days), { goal: p.stepsGoal, color: "#5FA774" }) + statsRow(s, (v) => fmtN(v)) +
          '<div class="grid3" style="margin-top:8px"><div class="stat"><small>Всего</small><b>' + fmtN(s.sum) + '</b></div><div class="stat"><small>Дней в цели</small><b>' + hit + "/" + s.n + '</b></div><div class="stat"><small>Серия</small><b>' + A.streakMetric((d) => ((A.dayGet(d) || {}).steps || 0) >= p.stepsGoal) + "</b></div></div></div>";
        h += '<div class="card"><h3>Автоматический импорт</h3>' + (A.native ? '<label class="switch"><input type="checkbox" data-c="sensor"' + (p.stepsSensor ? " checked" : "") + "><span></span>Брать шаги из датчика телефона</label><p class=\"small muted\">Счёт идёт с полуночи или с первого открытия приложения за день. Ручной ввод всегда доступен.</p>" + (p.stepsSensor ? '<button class="btn sm" data-a="sync">Обновить сейчас</button>' : "") : '<p class="small muted">Импорт шагов из датчика доступен в Android-приложении.</p>') + "</div>";
      } else if (tab === "water") {
        const data = series("water", days, (d) => (A.dayGet(d) || {}).water ?? null);
        h += A.todayCards.water(t, day);
        h += '<div class="card"><h3>История</h3>' + A.charts.bars(compress(data, days), { goal: p.waterGoal, color: "#7DB0D6" }) + statsRow(A.statsOf(data.map((x) => x.v)), (v) => fmtN(v) + " мл") + "</div>";
      } else if (tab === "state") {
        h += A.todayCards.state(t, day);
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
        h += '<div class="card"><h3>🌡 Температура тела<span class="sp"></span><button class="btn sm primary" data-a="bodyT">+ запись</button></h3>' +
          A.charts.line([{ name: "°C", color: "#E3899A", data: recs.map((x) => ({ x: fmtShort(x.d), v: x.v })) }], { title: "Температура тела" }) +
          '<div class="list">' + recs.slice().reverse().map((x) => '<div class="item"><div class="tx"><b>' + fmtN(x.v, 1) + " °C</b><small>" + fmtShort(x.d) + " " + esc(x.t || "") + (x.note ? " · " + esc(x.note) : "") + '</small></div><button class="icon-btn" data-a="delT" data-d="' + x.d + '" data-i="' + x.i + '" aria-label="Удалить">✕</button></div>').join("") + '</div><p class="small muted">Температура окружающей среды хранится отдельно — во вкладке «Погода».</p></div>';
      } else if (tab === "weather") {
        h += A.todayCards.weather(t, day);
        const data = series("temp", days, (d) => { const w = (A.dayGet(d) || {}).weather; return w && w.t != null ? w.t : null; });
        h += '<div class="card"><h3>График температуры воздуха</h3>' + A.charts.line([{ name: "°C", color: "#F08A3C", data: compress(data, days) }]) + statsRow(A.statsOf(data.map((x) => x.v)), (v) => fmtN(v, 1) + "°") + "</div>";
        const cnt = {}; days.forEach((d) => { const w = (A.dayGet(d) || {}).weather; if (w && w.cond) cnt[w.cond] = (cnt[w.cond] || 0) + 1; });
        if (Object.keys(cnt).length) h += '<div class="card"><h3>Погода за период</h3>' + A.charts.donut(Object.entries(cnt).map(([k, v]) => { const w = A.weather(k); return { name: w.icon + " " + w.name, v, color: w.color }; }), A.sum(Object.values(cnt)), "дней") + "</div>";
        h += '<div class="card"><h3>История</h3>' + days.slice().reverse().filter((d) => (A.dayGet(d) || {}).weather).slice(0, 20).map((d) => { const w = A.dayGet(d).weather, c = A.weather(w.cond); return '<div class="item" data-a="weatherD" data-d="' + d + '"><div class="ic">' + (c ? c.icon : "🌡") + '</div><div class="tx"><b>' + fmtDate(d) + "</b><small>" + esc(c ? c.name : "") + (w.hum != null ? " · " + w.hum + "%" : "") + (w.press != null ? " · " + w.press + " мм" : "") + (w.src === "manual" ? " · вручную" : "") + '</small></div><b class="num">' + (w.t != null ? fmtN(w.t) + "°" : "") + "</b></div>"; }).join("") + '<p class="small muted">Источник: ' + esc(A.weatherProvider().name) + ". Без интернета показывается последнее полученное значение.</p></div>";
      }
      return h;
    },
    bind(el, r) {
      const tab = r.args[0] || "sleep";
      A.bind(el, Object.assign(A.dayHandlers(() => today()), {
        tab(b) { A.go("health/" + b.dataset.v + (A.route().params.per ? "?per=" + A.route().params.per : ""), true); },
        per(b) { A.go("health/" + tab + "?per=" + b.dataset.v, true); },
        sleepD(b) { A.edit.sleep(b.dataset.d); }, weatherD(b) { A.edit.weather(b.dataset.d); },
        bodyT() { A.edit.bodyTemp(today()); },
        delT(b) { const day = A.day(b.dataset.d); day.bodyT.splice(+b.dataset.i, 1); A.save(); A.refresh(); },
        sensor(b) { A.db().profile.stepsSensor = b.checked; A.save(); if (b.checked) A.syncSteps(true); A.refresh(); },
        sync() { A.syncSteps(true); }
      }));
    }
  });
})();
