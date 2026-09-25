/* Меню «Ещё», настройки, экспорт и резервное копирование, приватность. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtDate } = A;

  A.view("more", {
    root: true, title: "Все разделы",
    render() {
      const items = [
        ["tasks", "📝", "Задачи"], ["habits", "✅", "Привычки"], ["reminders", "🔔", "Напоминания"],
        ["health", "❤", "Здоровье и активность"], ["food", "🍎", "Питание"], ["workouts", "🏋", "Тренировки"],
        ["money", "💰", "Финансы"], ["learn", "🎓", "Обучение"], ["hobbies", "🎨", "Хобби"],
        ["culture", "📚", "Книги / фильмы / сериалы"], ["piano", "🎹", "Пианино"], ["tarot", "🃏", "Таро"],
        ["ketu", "🌿", "Кету"], ["bazi", "☯", "Бацзы"], ["stats", "📊", "Статистика"],
        ["book", "📘", "Книга PRIME ERA"], ["ideas", "💡", "Идеи"], ["settings", "⚙", "Настройки"]
      ];
      return '<div class="card hero" style="padding:14px"><div class="row" style="justify-content:center;gap:12px"><img src="img/logo-192.png" width="54" height="54" alt="" style="border-radius:50%"><div style="text-align:left"><div class="date" style="font-size:20px">Путь жизни</div><div class="small muted">' + esc(A.db().profile.name ? "Привет, " + A.db().profile.name + " 💜" : "Живу и не жалею") + "</div></div></div></div>" +
        '<div class="menu-grid">' + items.map((i) => '<a href="#/' + i[0] + '"><span>' + i[1] + "</span>" + esc(i[2]) + "</a>").join("") + "</div>" +
        '<a class="card row" href="numerology.html" style="text-decoration:none;color:inherit;margin-top:12px"><span style="font-size:28px">命</span><div class="grow"><b>Нумерология и Бацзы</b><div class="small muted">Личный цифровой код, матрица, карта четырёх столпов, такты удачи, совместимость</div></div><span class="muted">›</span></a>';
    }
  });

  /* ---------- экспорт ---------- */
  const EXPORTS = {
    all: ["Полная резервная копия (JSON)", () => ["put-zhizni-backup-" + today() + ".json", "application/json", JSON.stringify(A.db(), null, 1)]],
    days: ["Дневник дней (CSV)", () => {
      const rows = Object.entries(A.db().days).sort().map(([d, x]) => ({ date: d, mood: x.mood, energy_m: (x.energy || {}).m, energy_d: (x.energy || {}).d, energy_e: (x.energy || {}).e, stress: x.stress, stress_src: x.stressSrc, anxiety_max: A.anxVal(d), feelings: x.feelings, water_ml: x.water, steps: x.steps, sleep_h: A.sleepH(d), sleep_q: (x.sleep || {}).q, sleep_rec: (x.sleep || {}).rec, bed: (x.sleep || {}).bed, wake: (x.sleep || {}).wake, weather: (x.weather || {}).cond, air_t: (x.weather || {}).t, body_t: (x.bodyT || []).map((b) => b.v), main: x.main, main_done: x.mainDone, plan: x.plan, fact: x.fact, obstacle: x.obstacle, note: x.note }));
      return ["days-" + today() + ".csv", "text/csv", A.csv(rows)];
    }],
    habits: ["Привычки и отметки (CSV)", () => { const rows = Object.entries(A.db().hlog).map(([k, v]) => { const [id, d] = k.split("|"); return { date: d, habit: (A.byId("habits", id) || {}).name, status: A.HSTATUS[v] ? A.HSTATUS[v][0] : v }; }).sort((a, b) => (a.date > b.date ? 1 : -1)); return ["habits-" + today() + ".csv", "text/csv", A.csv(rows)]; }],
    tasks: ["Задачи (CSV)", () => ["tasks-" + today() + ".csv", "text/csv", A.csv(A.col("tasks"), ["date", "time", "title", "prio", "must", "done", "doneAt", "minVer", "note"])]],
    events: ["Календарь (CSV)", () => ["events-" + today() + ".csv", "text/csv", A.csv(A.col("events").map((e) => Object.assign({}, e, { cat: A.cat(e.cat).name })), ["title", "cat", "date", "start", "end", "repeat", "days", "must", "note"])]],
    tx: ["Финансы: операции (CSV)", () => ["finance-" + today() + ".csv", "text/csv", A.csv(A.col("tx").map((t) => Object.assign({}, t, { account: (A.byId("accounts", t.acc) || {}).name })), ["date", "kind", "amt", "cat", "sub", "account", "note"])]],
    meals: ["Питание (CSV)", () => ["meals-" + today() + ".csv", "text/csv", A.csv(A.col("meals"), ["date", "type", "name", "g", "kcal", "p", "f", "c", "fib"])]],
    workouts: ["Тренировки (CSV)", () => ["workouts-" + today() + ".csv", "text/csv", A.csv(A.col("workouts").map((w) => Object.assign({}, w, { type: A.wType(w.type), sets: (w.sets || []).map((s) => (A.byId("exercises", s.ex) || {}).name + " " + (s.reps || "") + "×" + (s.w || "")) })), ["date", "type", "dur", "int", "dist", "pace", "sets", "note"])]],
    culture: ["Книги, фильмы, сериалы (CSV)", () => ["culture-" + today() + ".csv", "text/csv", A.csv([].concat(A.col("books").map((b) => ({ kind: "книга", title: b.title, author: b.author, genre: b.genre, status: b.status, rating: b.rating, start: b.start, end: b.end, pages: b.pages })), A.col("movies").map((m) => ({ kind: "фильм", title: m.title, genre: m.genres, rating: m.rating, end: m.date })), A.col("series").map((s) => ({ kind: "сериал", title: s.title, genre: s.genre, status: s.status, rating: s.rating, pages: s.watched }))))]],
    learn: ["Обучение и практика (CSV)", () => ["learning-" + today() + ".csv", "text/csv", A.csv([].concat(A.col("learn").map((x) => ({ date: x.date, subject: x.subject, min: x.min, note: x.note })), A.col("psess").map((x) => ({ date: x.date, subject: "Пианино", min: x.min, note: x.item + " " + (x.note || "") })), A.col("hsess").map((x) => ({ date: x.date, subject: "Хобби: " + ((A.byId("hobbies", x.hid) || {}).name || ""), min: x.min, note: x.note }))))]],
    goals: ["Цели (CSV)", () => ["goals-" + today() + ".csv", "text/csv", A.csv(A.col("goals").map((g) => Object.assign({}, g, { progress: Math.round(A.goalProgress(g) * 100), stages: (g.stages || []).map((s) => (s.done ? "✓ " : "") + s.t) })), ["level", "title", "why", "value", "start", "deadline", "measure", "progress", "status", "stages", "result", "note"])]]
  };

  const validBackup = (o) => o && typeof o === "object" && o.profile && o.days && Array.isArray(o.habits);

  const SECTIONS = [["profile", "Профиль и режим"], ["targets", "Цели по здоровью"], ["look", "Оформление"], ["cards", "Экран «Сегодня»"], ["notif", "Уведомления"], ["security", "Защита"], ["data", "Данные и экспорт"], ["about", "О приложении"]];

  A.view("settings", {
    title: (r) => (r.args[0] ? (SECTIONS.find((s) => s[0] === r.args[0]) || [0, "Настройки"])[1] : "Настройки"), tab: "more",
    render(el, r) {
      const p = A.db().profile, sec = r.args[0];
      if (!sec) return '<div class="card"><div class="list">' + SECTIONS.map((s) => '<a class="item" style="text-decoration:none;color:inherit" href="#/settings/' + s[0] + '"><div class="tx"><b>' + s[1] + '</b></div><span class="muted">›</span></a>').join("") + "</div></div>";
      if (sec === "profile") {
        return '<div class="card"><div class="form">' +
          '<div class="fld full"><label>Имя</label><input data-c="p" data-k="name" value="' + esc(p.name) + '"></div>' +
          '<div class="fld"><label>Подъём</label><input type="time" data-c="p" data-k="wake" value="' + esc(p.wake) + '"></div><div class="fld"><label>Сон</label><input type="time" data-c="p" data-k="bed" value="' + esc(p.bed) + '"></div>' +
          '<div class="fld"><label>Работа с</label><input type="time" data-c="p" data-k="workStart" value="' + esc(p.workStart) + '"></div><div class="fld"><label>Работа до</label><input type="time" data-c="p" data-k="workEnd" value="' + esc(p.workEnd) + '"></div>' +
          '<div class="fld full"><label>Мои ценности (каждая с новой строки)</label><textarea rows="6" data-c="plist" data-k="values">' + esc(p.values.join("\n")) + "</textarea></div>" +
          '<div class="fld full"><label>Моя фраза-якорь</label><textarea rows="2" data-c="p" data-k="phrase">' + esc(p.phrase) + "</textarea></div>" +
          '<div class="fld full"><label>Мой принцип</label><input data-c="p" data-k="principle" value="' + esc(p.principle) + '"></div>' +
          '<div class="fld full"><label>Мои правила недели (каждое с новой строки)</label><textarea rows="5" data-c="plist" data-k="rules">' + esc(p.rules.join("\n")) + "</textarea></div>" +
          '<div class="fld full"><label>Я имею право (каждое с новой строки)</label><textarea rows="5" data-c="plist" data-k="rights">' + esc(p.rights.join("\n")) + "</textarea></div>" +
          '<div class="fsec">Погода</div><div class="fld full"><label>Город' + (p.city ? ": <b>" + esc(p.city) + "</b>" : "") + '</label><div class="inline-add"><input id="city" placeholder="Москва" value=""><button class="btn" data-a="geo">Найти</button></div><div id="geoRes" class="list"></div></div>' +
          "</div></div>";
      }
      if (sec === "targets") {
        const f = [["waterGoal", "Вода в день, мл"], ["stepsGoal", "Шаги в день"], ["sleepMin", "Сон от, ч"], ["sleepMax", "Сон до, ч"], ["kcal", "Калории в день"], ["prot", "Белки, г"], ["fat", "Жиры, г"], ["carb", "Углеводы, г"], ["fiber", "Клетчатка, г"], ["workoutsWeek", "Тренировок в неделю"]];
        return '<div class="card"><div class="form">' + f.map(([k, l]) => '<div class="fld"><label>' + l + '</label><input type="number" inputmode="decimal" data-c="pn" data-k="' + k + '" value="' + esc(p[k]) + '"></div>').join("") + '</div><p class="small muted" style="margin-top:8px">Цели задаёшь ты сама. Приложение не назначает медицинских диет и не ставит диагнозов.</p></div>';
      }
      if (sec === "look") {
        const acc = [["lavender", "#8E7CC3", "Лаванда"], ["sage", "#6F9A76", "Шалфей"], ["peach", "#E08F68", "Персик"], ["rose", "#D27A8E", "Роза"], ["sky", "#5E97C4", "Небо"], ["gold", "#B8913F", "Золото"]];
        return '<div class="card"><h3>Тема</h3>' + A.seg([["system", "Как в системе"], ["light", "Светлая"], ["dark", "Тёмная"]], p.theme, "theme") + '<h3>Акцентный цвет</h3><div class="chips">' + acc.map(([k, c, n]) => '<button class="chip' + (p.accent === k ? " on" : "") + '" data-a="accent" data-v="' + k + '"><span class="dot" style="background:' + c + ';margin-right:6px"></span>' + n + "</button>").join("") + "</div></div>" +
          '<div class="card"><label class="switch"><input type="checkbox" data-c="pb" data-k="points"' + (p.points ? " checked" : "") + '><span></span>Баллы и награды за привычки</label><label class="switch"><input type="checkbox" data-c="pb" data-k="tarotRev"' + (p.tarotRev ? " checked" : "") + "><span></span>Перевёрнутые карты в раскладах Таро</label></div>";
      }
      if (sec === "cards") {
        return '<div class="card"><p class="small muted">Порядок и видимость карточек на экране «Сегодня».</p><div class="list">' + p.cards.map((k, i) => '<div class="item"><label class="switch grow"><input type="checkbox" data-c="cardOn" data-k="' + k + '"' + (p.hidden.includes(k) ? "" : " checked") + "><span></span>" + esc(A.CARD_NAMES[k] || k) + '</label><button class="icon-btn" data-a="up" data-i="' + i + '" aria-label="Выше">↑</button><button class="icon-btn" data-a="down" data-i="' + i + '" aria-label="Ниже">↓</button></div>').join("") + "</div></div>";
      }
      if (sec === "notif") {
        const n = p.notif;
        const kinds = [["general", "Все напоминания (главный выключатель)"], ["habits", "Привычки"], ["events", "События календаря"], ["payments", "Платежи"], ["workouts", "Тренировки"], ["reading", "Чтение"], ["piano", "Пианино"], ["study", "Учебные занятия"]];
        let h = "";
        if (A.native) h += '<div class="card row"><div class="grow small">Системные уведомления: <b>' + (A.native.notificationsAllowed() ? "разрешены" : "выключены") + '</b></div><button class="btn sm" data-a="perm">Разрешить</button><button class="btn sm" data-a="test">Тест</button></div>';
        else h += '<div class="warn" style="margin-bottom:12px">Системные уведомления доступны в Android-приложении.</div>';
        h += '<div class="card">' + kinds.map(([k, l]) => '<label class="switch"><input type="checkbox" data-c="notif" data-k="' + k + '"' + (n[k] !== false ? " checked" : "") + "><span></span>" + l + "</label>").join("") + '<p class="small muted">Без спама: уведомления приходят только для того, что ты сама настроила. Время — локальное время телефона.</p><a class="link" href="#/reminders">Мои напоминания →</a></div>';
        return h;
      }
      if (sec === "security") {
        const bio = A.native && A.native.biometricAvailable();
        return '<div class="card"><h3>PIN-код</h3><p class="small muted">' + (p.pin ? "PIN установлен. Приложение блокируется при запуске и после 1 минуты в фоне." : "PIN не установлен.") + '</p><div class="btns"><button class="btn primary" data-a="setPin">' + (p.pin ? "Сменить PIN" : "Установить PIN") + "</button>" + (p.pin ? '<button class="btn danger ghost" data-a="delPin">Убрать PIN</button>' : "") + "</div>" +
          (p.pin && bio ? '<label class="switch" style="margin-top:10px"><input type="checkbox" data-c="pb" data-k="bio"' + (p.bio ? " checked" : "") + "><span></span>Входить по отпечатку / лицу</label>" : "") + "</div>" +
          '<div class="card"><h3>Приватность</h3><p class="small">Все данные хранятся только на этом телефоне. В интернет уходит только запрос погоды (координаты города) к Open-Meteo, если указан город. Никаких аккаунтов, рекламы и аналитики.</p></div>';
      }
      if (sec === "data") {
        const saved = A.db().savedAt ? new Date(A.db().savedAt).toLocaleString("ru-RU") : "—";
        let backups = "";
        if (A.native) { const list = (A.native.listBackups() || "").split(",").filter(Boolean).reverse(); backups = '<div class="card"><h3>Автоматические копии</h3><p class="small muted">Ежедневно сохраняются во внутренней памяти приложения (последние 14 дней).</p>' + (list.length ? list.map((d) => '<div class="item"><div class="tx"><b>' + fmtDate(d) + '</b></div><button class="btn sm" data-a="restoreDay" data-d="' + d + '">Восстановить</button></div>').join("") : A.empty("Пока нет")) + "</div>"; }
        return '<div class="card"><h3>Экспорт</h3><p class="small muted">Последнее сохранение: ' + esc(saved) + '</p><div class="list">' + Object.entries(EXPORTS).map(([k, [n]]) => '<div class="item"><div class="tx"><b>' + esc(n) + '</b></div><button class="btn sm" data-a="exp" data-k="' + k + '">Сохранить</button></div>').join("") + '</div><div class="btns"><a class="btn" href="#/stats/month">PDF: месячный отчёт</a><a class="btn" href="#/stats/year">PDF: год</a></div><p class="small muted">PDF создаётся через печать: кнопка ⎙ → «Сохранить как PDF».</p></div>' +
          '<div class="card"><h3>Восстановление</h3><p class="small">Загрузить полную резервную копию из файла (JSON). Текущие данные будут заменены.</p><button class="btn" data-a="import">Выбрать файл</button></div>' + backups +
          '<div class="card" style="border:1px solid var(--bad)"><h3 style="color:var(--bad)">Полное удаление данных</h3><p class="small">Удаляет все записи, настройки, автоматические копии и напоминания без возможности восстановления. Сначала сохраните резервную копию.</p><button class="btn danger" data-a="wipe">Удалить все данные</button></div>';
      }
      if (sec === "about") {
        return '<div class="card hero"><img src="img/logo-192.png" width="96" height="96" alt="" style="border-radius:50%"><div class="date">Путь жизни</div><div class="small muted">версия ' + esc(A.native ? A.native.version() : "веб") + '</div><div class="phrase">Персональная система, где день, состояние, активность, питание, тренировки, деньги, привычки, обучение, культура, хобби и долгосрочные цели — связанные части одной жизни.</div></div>' +
          '<div class="card"><p class="small">Приложение не требует идеальности. Пропущенный день не является провалом личности. Оно помогает видеть реальную жизнь, замечать закономерности, планировать важное и постепенно повышать управляемость жизни.</p><p class="small muted">Не является медицинским или психологическим инструментом. Разделы Таро, Кету и Бацзы — образовательные и рефлексивные; традиционные толкования не являются научными фактами. Погода: Open-Meteo.com (CC BY 4.0).</p></div>';
      }
      return "";
    },
    bind(el, r) {
      const p = A.db().profile;
      A.bind(el, {
        p(b) { p[b.dataset.k] = b.value.trim(); A.save(); },
        plist(b) { p[b.dataset.k] = b.value.split("\n").map((x) => x.trim()).filter(Boolean); A.save(); },
        pn(b) { p[b.dataset.k] = A.num(b.value, p[b.dataset.k]); A.save(); },
        pb(b) { p[b.dataset.k] = b.checked; A.save(); },
        async geo() {
          const q = el.querySelector("#city").value.trim(); if (!q) return;
          const box = el.querySelector("#geoRes"); box.innerHTML = '<div class="small muted">Поиск…</div>';
          try {
            const res = await A.weatherProvider().geocode(q);
            box.innerHTML = res.length ? res.map((x, i) => '<div class="item" data-a="pickCity" data-i="' + i + '"><div class="tx"><b>' + esc(x.name) + "</b><small>" + x.lat.toFixed(2) + ", " + x.lon.toFixed(2) + "</small></div></div>").join("") : '<div class="small muted">Не найдено</div>';
            box._res = res;
          } catch (e) { box.innerHTML = '<div class="small muted">Нет связи с интернетом. Погоду можно вводить вручную.</div>'; }
        },
        pickCity(b) { const x = el.querySelector("#geoRes")._res[+b.dataset.i]; p.city = x.name; p.lat = x.lat; p.lon = x.lon; A.save(); A.toast("Город: " + x.name); A.weatherRefresh(true); A.refresh(); },
        theme(b) { p.theme = b.dataset.v; A.save(); A.applyTheme(); A.refresh(); },
        accent(b) { p.accent = b.dataset.v; A.save(); A.applyTheme(); A.refresh(); },
        cardOn(b) { const k = b.dataset.k; p.hidden = p.hidden.filter((x) => x !== k); if (!b.checked) p.hidden.push(k); A.save(); },
        up(b) { const i = +b.dataset.i; if (i > 0) { [p.cards[i - 1], p.cards[i]] = [p.cards[i], p.cards[i - 1]]; A.save(); A.refresh(); } },
        down(b) { const i = +b.dataset.i; if (i < p.cards.length - 1) { [p.cards[i + 1], p.cards[i]] = [p.cards[i], p.cards[i + 1]]; A.save(); A.refresh(); } },
        notif(b) { p.notif[b.dataset.k] = b.checked; A.save(); A.syncReminders(); },
        perm() { A.nativeCall("notif", () => A.native.requestNotifications()).then(() => A.refresh()); },
        test() { A.native.testNotification("Путь жизни", "Уведомления работают 💜"); },
        setPin() {
          A.formSheet("PIN-код", [{ k: "a", label: "Новый PIN (4–8 цифр)", type: "password" }, { k: "b", label: "Повторите PIN", type: "password" }], {}, (o) => {
            if (!/^\d{4,8}$/.test(o.a)) { A.toast("PIN — от 4 до 8 цифр"); return false; }
            if (o.a !== o.b) { A.toast("PIN не совпадает"); return false; }
            A.sha256("pz:" + o.a).then((h) => { p.pin = h; A.saveNow(); A.toast("PIN установлен"); A.refresh(); });
          }, { after: (w) => w.querySelectorAll("input").forEach((i) => i.setAttribute("inputmode", "numeric")) });
        },
        delPin() { A.confirm("Убрать PIN-код?", () => { p.pin = ""; p.bio = false; A.save(); A.refresh(); }); },
        exp(b) { const [name, mime, content] = EXPORTS[b.dataset.k][1](); A.download(name, mime, content); },
        async import() {
          const text = await A.pickFile(); if (!text) return;
          let o; try { o = JSON.parse(text); } catch (e) { return A.toast("Это не файл резервной копии"); }
          if (!validBackup(o)) return A.toast("Файл не похож на резервную копию «Путь жизни»");
          A.confirm("Заменить текущие данные данными из файла" + (o.savedAt ? " от " + new Date(o.savedAt).toLocaleString("ru-RU") : "") + "?", () => { A.replaceDb(o); A.applyTheme(); A.syncReminders(); A.toast("Данные восстановлены"); A.go("today"); }, "Восстановить");
        },
        restoreDay(b) {
          const text = A.native.readBackupDay(b.dataset.d); let o; try { o = JSON.parse(text); } catch (e) { return A.toast("Копия повреждена"); }
          if (!validBackup(o)) return A.toast("Копия повреждена");
          A.confirm("Восстановить копию за " + fmtDate(b.dataset.d) + "? Текущие данные будут заменены.", () => { A.replaceDb(o); A.applyTheme(); A.syncReminders(); A.toast("Восстановлено"); A.go("today"); }, "Восстановить");
        },
        wipe() {
          A.confirm("Удалить ВСЕ данные? Это действие нельзя отменить.", () => {
            setTimeout(() => A.formSheet("Последнее подтверждение", [{ k: "w", label: "Напишите УДАЛИТЬ", full: true }], {}, (o) => {
              if (o.w.trim().toUpperCase() !== "УДАЛИТЬ") { A.toast("Не совпадает — данные не удалены"); return false; }
              try { localStorage.clear(); } catch (e) {}
              if (A.native) A.native.wipe();
              A.replaceDb(A.seed()); A.applyTheme(); A.syncReminders();
              A.toast("Все данные удалены"); A.go("today");
            }, { saveLabel: "Удалить навсегда" }), 250);
          }, "Продолжить", true);
        }
      });
    }
  });
})();
