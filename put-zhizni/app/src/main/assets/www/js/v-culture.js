/* Хобби, книги, фильмы и сериалы. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN, fmtDur, fmtDate, fmtShort, sum } = A;
  A.edit = A.edit || {};
  const PAL = ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6", "#B6A6D9", "#9CC5A1", "#F3C29F", "#D8C08A"];
  const stars = (n) => (n ? "★".repeat(n) + '<span style="opacity:.25">' + "★".repeat(5 - n) + "</span>" : "");
  const genreDonut = (items, key, center, sub) => {
    const cnt = {}; items.forEach((x) => String(x[key] || "").split(",").map((g) => g.trim()).filter(Boolean).forEach((g) => (cnt[g] = (cnt[g] || 0) + 1)));
    if (!Object.keys(cnt).length) return "";
    return A.charts.donut(Object.entries(cnt).sort((a, b) => b[1] - a[1]).slice(0, 8).map(([n, v], i) => ({ name: n, v, color: PAL[i % PAL.length] })), center, sub);
  };

  /* ---------- хобби ---------- */
  A.edit.hobby = (h) => A.formSheet(h ? "Хобби" : "Новое хобби", [
    { k: "icon", label: "Значок", ph: "🎨" }, { k: "name", label: "Название", req: true },
    { k: "cat", label: "Категория", type: "select", opts: ["Творчество", "Рукоделие", "Музыка", "Фотография", "Готовка", "Спорт", "Путешествия", "Игры", "Наука", "Другое"] },
    { k: "level", label: "Уровень", type: "select", opts: ["Новичок", "Любитель", "Уверенный", "Продвинутый"] },
    { k: "goal", label: "Цель", full: true }, { k: "freq", label: "Частота", ph: "2 раза в неделю" }, { k: "weekMin", label: "Цель минут в неделю", type: "number" },
    { k: "materials", label: "Материалы", type: "textarea" }, { k: "next", label: "Следующие действия", type: "textarea" }, { k: "notes", label: "Заметки", type: "textarea" }
  ], h || { icon: "🎨", level: "Новичок", cat: "Творчество" }, (o) => { A.upsert("hobbies", Object.assign(h || {}, o)); A.refresh(); }, h ? { onDelete: () => { A.remove("hobbies", h.id); A.refresh(); } } : {});
  A.edit.hobbySession = (s) => {
    const isNew = !s || !s.id;
    s = Object.assign({ date: today(), min: 30, hid: (A.col("hobbies")[0] || {}).id || "", note: "" }, s || {});
    if (!A.col("hobbies").length) { A.toast("Сначала добавьте хобби"); return A.go("hobbies"); }
    A.formSheet("Занятие хобби", [{ k: "hid", label: "Хобби", type: "select", opts: A.col("hobbies").map((h) => [h.id, (h.icon || "") + " " + h.name]), full: true }, { k: "date", label: "Дата", type: "date" }, { k: "min", label: "Минут", type: "number", req: true }, { k: "note", label: "Что сделала / прогресс", type: "textarea" }], s, (o) => { Object.assign(s, o); A.upsert("hsess", s); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("hsess", s.id); A.refresh(); } });
  };

  A.view("hobbies", {
    title: "Хобби", tab: "more",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.edit.hobby()" aria-label="Добавить">＋</button>'; },
    render(el, r) {
      const hs = A.col("hobbies"), ss = A.col("hsess");
      if (r.args[0]) {
        const h = A.byId("hobbies", r.args[0]); if (!h) return A.empty("Хобби не найдено");
        const my = ss.filter((s) => s.hid === h.id).sort((a, b) => (b.date > a.date ? 1 : -1));
        const wk = sum(my.filter((s) => s.date > A.addDays(today(), -7)).map((s) => s.min));
        return '<div class="card peach"><div class="row"><div style="font-size:36px">' + esc(h.icon || "🎨") + '</div><div class="grow"><h2 style="font-size:21px">' + esc(h.name) + '</h2><div class="small muted">' + esc([h.cat, h.level, h.freq].filter(Boolean).join(" · ")) + "</div></div></div></div>" +
          '<div class="grid3"><div class="stat"><small>За 7 дней</small><b>' + fmtDur(wk) + '</b></div><div class="stat"><small>Всего</small><b>' + fmtDur(sum(my.map((s) => s.min))) + '</b></div><div class="stat"><small>Занятий</small><b>' + my.length + "</b></div></div>" +
          (h.weekMin ? '<div class="card" style="margin-top:12px"><div class="small">Прогресс недели: ' + wk + " / " + h.weekMin + " мин</div>" + A.bar(wk / h.weekMin, "var(--peach)") + "</div>" : "") +
          '<div class="card" style="margin-top:12px"><div class="kv">' + [["Цель", h.goal], ["Материалы", h.materials], ["Следующие действия", h.next], ["Заметки", h.notes]].filter((x) => x[1]).map((x) => "<b>" + x[0] + "</b><span>" + esc(x[1]) + "</span>").join("") + "</div>" +
          '<div class="btns"><button class="btn primary" data-a="sess" data-id="' + h.id + '">+ занятие</button><button class="btn" data-a="timer" data-id="' + h.id + '">⏱</button><button class="btn" data-a="cal" data-id="' + h.id + '">В календарь</button><button class="btn ghost" data-a="edit" data-id="' + h.id + '">Изменить</button></div></div>' +
          '<div class="card"><h3>История</h3>' + (my.length ? my.map((s) => '<div class="item" data-a="s" data-id="' + s.id + '"><div class="tx"><b>' + fmtDate(s.date) + " · " + fmtDur(s.min) + "</b><small>" + esc(s.note || "") + "</small></div></div>").join("") : A.empty("Занятий пока нет")) + "</div>";
      }
      const mk = today().slice(0, 7), mm = ss.filter((s) => s.date.slice(0, 7) === mk);
      let h = "";
      if (mm.length) h += '<div class="card"><h3>Любимые хобби · этот месяц</h3>' + A.charts.donut(hs.map((x, i) => ({ name: (x.icon || "") + " " + x.name, v: sum(mm.filter((s) => s.hid === x.id).map((s) => s.min)), color: PAL[i % PAL.length] })), fmtDur(sum(mm.map((s) => s.min))), "за месяц") + "</div>";
      h += '<div class="card"><div class="list">' + (hs.length ? hs.map((x) => '<a class="item" style="text-decoration:none;color:inherit" href="#/hobbies/' + x.id + '"><div class="ic">' + esc(x.icon || "🎨") + '</div><div class="tx"><b>' + esc(x.name) + "</b><small>" + esc([x.level, x.goal].filter(Boolean).join(" · ")) + " · за месяц " + fmtDur(sum(mm.filter((s) => s.hid === x.id).map((s) => s.min))) + "</small></div></a>").join("") : A.empty("Добавьте хобби: рисование, готовка, фотография, музыка…", '<button class="btn primary" data-a="new">+ хобби</button>')) + "</div></div>";
      return h;
    },
    bind(el) {
      A.bind(el, {
        new() { A.edit.hobby(); }, edit(b) { A.edit.hobby(A.byId("hobbies", b.dataset.id)); },
        sess(b) { A.edit.hobbySession({ hid: b.dataset.id }); }, s(b) { A.edit.hobbySession(A.byId("hsess", b.dataset.id)); },
        timer(b) { const h = A.byId("hobbies", b.dataset.id); A.timer.start("hobby", h.id, h.name); A.go("today"); },
        cal(b) { const h = A.byId("hobbies", b.dataset.id); A.edit.event(null, today()); setTimeout(() => { const w = document.querySelector(".sheet-wrap:last-child"); if (!w) return; w.querySelector('[name="title"]').value = (h.icon || "") + " " + h.name; w.querySelector('[name="cat"]').value = "hobby"; }, 30); }
      });
    }
  });

  /* ---------- книги ---------- */
  const BSTAT = [["want", "Хочу прочитать"], ["reading", "Читаю"], ["done", "Прочитано"], ["drop", "Отложено"]];
  A.edit.book = (b) => A.formSheet(b ? "Книга" : "Новая книга", [
    { k: "title", label: "Название", req: true, full: true }, { k: "author", label: "Автор" }, { k: "genre", label: "Жанр", type: "select", opts: ["Психология", "Фантастика", "Художественная", "Наука", "Бизнес", "История", "Биография", "Эзотерика", "Другое"] },
    { k: "status", label: "Статус", type: "chips", opts: BSTAT }, { k: "pages", label: "Страниц всего", type: "number" }, { k: "read", label: "Прочитано страниц", type: "number" },
    { k: "start", label: "Начало", type: "date" }, { k: "end", label: "Окончание", type: "date" }, { k: "rating", label: "Рейтинг", type: "stars" },
    { k: "notes", label: "Заметки", type: "textarea" }, { k: "quotes", label: "Цитаты (каждая с новой строки)", type: "textarea", rows: 4 }
  ], b || { status: "want", genre: "Психология" }, (o) => { const x = Object.assign(b || {}, o); if (x.status === "reading" && !x.start) x.start = today(); if (x.status === "done" && !x.end) x.end = today(); A.upsert("books", x); A.refresh(); }, b ? { onDelete: () => { A.remove("books", b.id); A.refresh(); } } : {});
  A.edit.reading = (s) => {
    const isNew = !s || !s.id;
    s = Object.assign({ date: today(), pages: "", min: "", bookId: (A.col("books").find((x) => x.status === "reading") || {}).id || "" }, s || {});
    A.formSheet("Чтение", [{ k: "bookId", label: "Книга", type: "select", opts: [["", "—"]].concat(A.col("books").map((b) => [b.id, b.title])), full: true }, { k: "date", label: "Дата", type: "date" }, { k: "pages", label: "Страниц", type: "number" }, { k: "min", label: "Минут", type: "number" }], s, (o) => {
      const prev = isNew ? 0 : +s.pages || 0;
      Object.assign(s, o); A.upsert("rsess", s);
      const b = A.byId("books", s.bookId);
      if (b && s.pages) { b.read = Math.max(0, (+b.read || 0) + (+s.pages || 0) - prev); if (b.status === "want") { b.status = "reading"; b.start = b.start || today(); } if (b.pages && b.read >= b.pages && b.status !== "done") { b.status = "done"; b.end = today(); A.toast("Книга прочитана! 🎉"); } A.save(); }
      A.refresh();
    }, isNew ? {} : { onDelete: () => { A.remove("rsess", s.id); A.refresh(); } });
  };

  /* ---------- фильмы и сериалы ---------- */
  A.edit.movie = (m) => A.formSheet(m ? "Фильм" : "Новый фильм", [
    { k: "title", label: "Название", req: true, full: true }, { k: "year", label: "Год", type: "number" }, { k: "country", label: "Страна" },
    { k: "genres", label: "Жанры (через запятую)", full: true, ph: "Драма, Комедия" }, { k: "dur", label: "Длительность, мин", type: "number" },
    { k: "date", label: "Дата просмотра", type: "date" }, { k: "rating", label: "Мой рейтинг", type: "stars" }, { k: "notes", label: "Заметки", type: "textarea" }
  ], m || { date: today() }, (o) => { A.upsert("movies", Object.assign(m || {}, o)); A.refresh(); }, m ? { onDelete: () => { A.remove("movies", m.id); A.refresh(); } } : {});
  A.edit.series = (s) => A.formSheet(s ? "Сериал" : "Новый сериал", [
    { k: "title", label: "Название", req: true, full: true }, { k: "genre", label: "Жанр" },
    { k: "status", label: "Статус", type: "chips", opts: [["plan", "Хочу"], ["watch", "Смотрю"], ["done", "Завершён"], ["drop", "Брошен"]] },
    { k: "season", label: "Сезон", type: "number" }, { k: "ep", label: "Серия", type: "number" },
    { k: "watched", label: "Просмотрено серий", type: "number" }, { k: "total", label: "Всего серий", type: "number" }, { k: "epMin", label: "Минут в серии", type: "number" },
    { k: "rating", label: "Рейтинг", type: "stars" }, { k: "notes", label: "Заметки", type: "textarea" }
  ], s || { status: "watch", season: 1, ep: 1, watched: 0, epMin: 45 }, (o) => { A.upsert("series", Object.assign(s || {}, o)); A.refresh(); }, s ? { onDelete: () => { A.remove("series", s.id); A.refresh(); } } : {});

  A.view("culture", {
    title: "Книги, фильмы, сериалы", tab: "more",
    actions(el, r) { const t = r.args[0] || "books"; el.innerHTML = '<button class="icon-btn" onclick="App.edit.' + (t === "books" ? "book" : t === "movies" ? "movie" : "series") + '()" aria-label="Добавить">＋</button>'; },
    render(el, r) {
      const tab = r.args[0] || "books";
      let h = A.seg([["books", "Книги"], ["movies", "Фильмы"], ["series", "Сериалы"]], tab, "tab");
      const mk = today().slice(0, 7);
      if (tab === "books") {
        const bs = A.col("books"), rs = A.col("rsess"), mr = rs.filter((s) => s.date.slice(0, 7) === mk);
        h += '<div class="grid3"><div class="stat"><small>Книг в этом месяце</small><b>' + bs.filter((b) => b.end && b.end.slice(0, 7) === mk).length + '</b></div><div class="stat"><small>Страниц</small><b>' + fmtN(sum(mr.map((s) => +s.pages || 0))) + '</b></div><div class="stat"><small>Время</small><b>' + fmtDur(sum(mr.map((s) => +s.min || 0))) + "</b></div></div>";
        const g = genreDonut(bs.filter((b) => b.status === "done" || b.status === "reading"), "genre", bs.filter((b) => b.status === "done").length, "прочитано");
        if (g) h += '<div class="card" style="margin-top:12px"><h3>📖 Чтение книг</h3>' + g + "</div>";
        h += '<button class="btn primary block" data-a="rlog" style="margin:12px 0">+ записать чтение</button>';
        BSTAT.forEach(([k, n]) => {
          const list = bs.filter((b) => (b.status || "want") === k);
          if (!list.length) return;
          h += '<div class="sec-t">' + n + '</div><div class="card"><div class="list">' + list.map((b) => '<div class="item" data-a="book" data-id="' + b.id + '"><div class="ic">📖</div><div class="tx"><b>' + esc(b.title) + "</b><small>" + esc([b.author, b.genre].filter(Boolean).join(" · ")) + (b.pages ? " · " + (b.read || 0) + "/" + b.pages + " стр." : "") + " " + stars(b.rating) + "</small>" + (b.pages && k === "reading" ? A.bar((b.read || 0) / b.pages, "var(--peach)") : "") + "</div></div>").join("") + "</div></div>";
        });
        if (!bs.length) h += A.empty("Добавьте первую книгу");
      } else if (tab === "movies") {
        const ms = A.col("movies").slice().sort((a, b) => ((b.date || "") > (a.date || "") ? 1 : -1));
        const mm = ms.filter((m) => (m.date || "").slice(0, 7) === mk);
        h += '<div class="grid3"><div class="stat"><small>Фильмов в месяце</small><b>' + mm.length + '</b></div><div class="stat"><small>Время</small><b>' + fmtDur(sum(mm.map((m) => +m.dur || 0))) + '</b></div><div class="stat"><small>Всего</small><b>' + ms.length + "</b></div></div>";
        const g = genreDonut(ms, "genres", ms.length, "фильмов");
        if (g) h += '<div class="card" style="margin-top:12px"><h3>🎬 Жанры фильмов</h3>' + g + "</div>";
        h += '<div class="card" style="margin-top:12px"><div class="list">' + (ms.length ? ms.map((m) => '<div class="item" data-a="movie" data-id="' + m.id + '"><div class="ic">🎬</div><div class="tx"><b>' + esc(m.title) + (m.year ? " (" + m.year + ")" : "") + "</b><small>" + esc([m.genres, m.date && fmtShort(m.date)].filter(Boolean).join(" · ")) + " " + stars(m.rating) + "</small></div></div>").join("") : A.empty("Фильмов пока нет")) + "</div></div>";
      } else {
        const ss = A.col("series");
        const epw = sum(ss.map((s) => +s.watched || 0));
        h += '<div class="grid3"><div class="stat"><small>В процессе</small><b>' + ss.filter((s) => s.status === "watch").length + '</b></div><div class="stat"><small>Завершено</small><b>' + ss.filter((s) => s.status === "done").length + '</b></div><div class="stat"><small>Серий просмотрено</small><b>' + epw + "</b></div></div>";
        const tot = sum(ss.map((s) => +s.total || 0));
        if (tot) h += '<div class="card" style="margin-top:12px"><div class="small">Просмотрено ' + epw + " из " + tot + " серий · время ≈ " + fmtDur(sum(ss.map((s) => (+s.watched || 0) * (+s.epMin || 45)))) + "</div>" + A.bar(epw / tot, "var(--sage)") + "</div>";
        h += '<div class="card" style="margin-top:12px"><div class="list">' + (ss.length ? ss.map((s) => '<div class="item"><div class="ic">📺</div><div class="tx" data-a="ser" data-id="' + s.id + '"><b>' + esc(s.title) + "</b><small>сезон " + (s.season || 1) + ", серия " + (s.ep || 1) + " · просмотрено " + (s.watched || 0) + (s.total ? " · осталось " + Math.max(0, s.total - (s.watched || 0)) : "") + " " + stars(s.rating) + "</small></div>" + (s.status === "watch" ? '<button class="btn sm" data-a="ep" data-id="' + s.id + '">+1 серия</button>' : "") + "</div>").join("") : A.empty("Сериалов пока нет")) + "</div></div>";
      }
      return h;
    },
    bind(el) {
      A.bind(el, {
        tab(b) { A.go("culture/" + b.dataset.v, true); },
        book(b) { A.edit.book(A.byId("books", b.dataset.id)); }, rlog() { A.edit.reading(); },
        movie(b) { A.edit.movie(A.byId("movies", b.dataset.id)); },
        ser(b) { A.edit.series(A.byId("series", b.dataset.id)); },
        ep(b) { const s = A.byId("series", b.dataset.id); s.watched = (+s.watched || 0) + 1; s.ep = (+s.ep || 0) + 1; (s.log = s.log || []).push(today()); if (s.total && s.watched >= s.total) { s.status = "done"; A.toast("Сериал завершён!"); } A.save(); A.refresh(); }
      });
    }
  });
})();
