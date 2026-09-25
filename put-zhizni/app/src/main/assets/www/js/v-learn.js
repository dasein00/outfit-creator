/* Обучение: пианино, Таро, Кету, Бацзы, учебные занятия, книга PRIME ERA. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtN, fmtDur, fmtShort, fmtDate, sum } = A;
  const L = () => window.LEARN;
  A.edit = A.edit || {};

  /* ---------- журналы занятий ---------- */
  A.edit.practice = (s) => {
    const isNew = !s || !s.id;
    s = Object.assign({ date: today(), min: 15, item: "", diff: 5, note: "", lesson: A.db().piano.cur }, s || {});
    A.formSheet("Практика пианино", [
      { k: "date", label: "Дата", type: "date" }, { k: "min", label: "Минут", type: "number", req: true },
      { k: "lesson", label: "Урок", type: "select", opts: () => [["", "—"]].concat(L().PIANO.map((l) => [l.id, l.title])), full: true },
      { k: "item", label: "Упражнение / произведение", full: true }, { k: "diff", label: "Сложность 1–10", type: "range", min: 1, max: 10 },
      { k: "note", label: "Заметки", type: "textarea" }
    ], s, (o) => { Object.assign(s, o); A.upsert("psess", s); A.refresh(); }, isNew ? {} : { onDelete: () => { A.remove("psess", s.id); A.refresh(); } });
  };
  A.edit.learnSession = (s) => {
    const isNew = !s || !s.id;
    s = Object.assign({ date: today(), min: 30, subject: "", note: "" }, s || {});
    const subs = [...new Set(["Таро", "Кету", "Бацзы", "Язык", "Курс", "Работа / профессия"].concat(A.col("learn").map((x) => x.subject)))];
    A.formSheet("Учебное занятие", [{ k: "subject", label: "Предмет", type: "chips", opts: subs }, { k: "subject2", label: "Или новый предмет" }, { k: "date", label: "Дата", type: "date" }, { k: "min", label: "Минут", type: "number", req: true }, { k: "note", label: "Что изучила", type: "textarea" }], s, (o) => {
      o.subject = o.subject2 || o.subject || "Другое"; delete o.subject2;
      Object.assign(s, o); A.upsert("learn", s); A.refresh();
    }, isNew ? {} : { onDelete: () => { A.remove("learn", s.id); A.refresh(); } });
  };

  /* ---------- звук ---------- */
  let ac = null;
  const audio = () => { if (!ac) { const C = window.AudioContext || window.webkitAudioContext; if (C) ac = new C(); } if (ac && ac.state === "suspended") ac.resume(); return ac; };
  A.playNote = (midi, dur = 0.9) => {
    const c = audio(); if (!c) return;
    const f = 440 * Math.pow(2, (midi - 69) / 12), t = c.currentTime;
    const o = c.createOscillator(), o2 = c.createOscillator(), g = c.createGain();
    o.type = "triangle"; o.frequency.value = f; o2.type = "sine"; o2.frequency.value = f * 2;
    const g2 = c.createGain(); g2.gain.value = 0.15;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.35, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); o2.connect(g2); g2.connect(g); g.connect(c.destination);
    o.start(t); o2.start(t); o.stop(t + dur + 0.05); o2.stop(t + dur + 0.05);
  };
  const click = (accent) => { const c = audio(); if (!c) return; const t = c.currentTime, o = c.createOscillator(), g = c.createGain(); o.frequency.value = accent ? 1600 : 1000; g.gain.setValueAtTime(0.4, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.06); o.connect(g); g.connect(c.destination); o.start(t); o.stop(t + 0.07); };

  const NAMES = ["до", "до♯", "ре", "ре♯", "ми", "фа", "фа♯", "соль", "соль♯", "ля", "ля♯", "си"];
  const LET = ["C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"];
  A.keyboard = (from = 60, octaves = 2) => {
    const whites = [0, 2, 4, 5, 7, 9, 11];
    let h = '<div class="piano" id="piano">', wi = 0;
    for (let m = from; m < from + octaves * 12 + 1; m++) {
      const pc = m % 12;
      if (whites.includes(pc)) { h += '<div class="pk" data-m="' + m + '">' + (pc === 0 ? LET[pc] + (Math.floor(m / 12) - 1) : LET[pc]) + "</div>"; wi++; }
      else h += '<div class="pk b" data-m="' + m + '" style="left:' + (wi * 34 - 11) + 'px"></div>';
    }
    return h + '</div><div class="small muted" id="pkName" style="text-align:center;min-height:20px;margin-top:4px">Нажимайте клавиши</div>';
  };
  const wireKeyboard = (root) => {
    const p = root.querySelector("#piano"); if (!p) return;
    const hit = (e) => {
      const k = e.target.closest(".pk"); if (!k) return;
      e.preventDefault();
      const m = +k.dataset.m; A.playNote(m);
      k.classList.add("hit"); setTimeout(() => k.classList.remove("hit"), 180);
      const n = root.querySelector("#pkName"); if (n) n.textContent = NAMES[m % 12] + " · " + LET[m % 12] + (Math.floor(m / 12) - 1);
      root.dispatchEvent(new CustomEvent("pianokey", { detail: m }));
    };
    p.addEventListener("pointerdown", hit);
  };

  /* ---------- нотный тренажёр ---------- */
  const DIA = ["до", "ре", "ми", "фа", "соль", "ля", "си"];
  const staffSvg = (clef, idx) => {
    // idx — диатоническая ступень от «до» (скрипичный: 0 = C4, басовый: 0 = C2)
    const W = 220, H = 150, gap = 12, bottom = 100;
    const lineBase = clef === "treble" ? 2 : 4; // E4 или G2 — нижняя линейка
    const y = bottom - (idx - lineBase) * (gap / 2);
    let s = '<svg viewBox="0 0 ' + W + " " + H + '" class="staff" width="100%" role="img" aria-label="Нота на нотном стане">';
    for (let i = 0; i < 5; i++) s += '<line x1="10" x2="' + (W - 10) + '" y1="' + (bottom - i * gap) + '" y2="' + (bottom - i * gap) + '" stroke="currentColor" stroke-width="1.2"/>';
    s += '<text x="14" y="' + (clef === "treble" ? bottom + 6 : bottom - 22) + '" font-size="' + (clef === "treble" ? 62 : 38) + '" fill="currentColor">' + (clef === "treble" ? "𝄞" : "𝄢") + "</text>";
    // добавочные линейки
    for (let k = lineBase - 2; k >= idx; k -= 2) s += '<line x1="120" x2="160" y1="' + (bottom - (k - lineBase) * (gap / 2)) + '" y2="' + (bottom - (k - lineBase) * (gap / 2)) + '" stroke="currentColor" stroke-width="1.2"/>';
    for (let k = lineBase + 10; k <= idx; k += 2) s += '<line x1="120" x2="160" y1="' + (bottom - (k - lineBase) * (gap / 2)) + '" y2="' + (bottom - (k - lineBase) * (gap / 2)) + '" stroke="currentColor" stroke-width="1.2"/>';
    s += '<ellipse cx="140" cy="' + y + '" rx="8" ry="6" fill="currentColor" transform="rotate(-18 140 ' + y + ')"/>';
    s += '<line x1="' + (idx < lineBase + 4 ? 147.5 : 132.5) + '" x2="' + (idx < lineBase + 4 ? 147.5 : 132.5) + '" y1="' + y + '" y2="' + (idx < lineBase + 4 ? y - 36 : y + 36) + '" stroke="currentColor" stroke-width="1.5"/>';
    return s + "</svg>";
  };
  const noteMidi = (clef, idx) => { const base = clef === "treble" ? 60 : 36; const oct = Math.floor(idx / 7), st = [0, 2, 4, 5, 7, 9, 11][idx % 7]; return base + oct * 12 + st; };

  /* ---------- Таро: карта дня ---------- */
  A.tarotDaily = (d) => {
    const T = A.db().tarot;
    if (!T.daily[d]) { const cards = L().TAROT; T.daily[d] = cards[Math.floor(Math.random() * cards.length)].id; A.save(); }
    return T.daily[d];
  };

  /* ---------- карточки повторения (система Лейтнера) ---------- */
  const INTERVALS = [0, 1, 2, 4, 8, 16];
  const dueCards = (store, deck) => deck.filter((c) => { const b = store.box[c.id]; return !b || b.next <= today(); });
  const grade = (store, id, ok) => { const b = store.box[id] || { b: 0 }; b.b = ok ? Math.min(5, (b.b || 0) + 1) : 1; b.next = addDays(today(), ok ? INTERVALS[b.b] : 0); b.seen = (b.seen || 0) + 1; b.ok = (b.ok || 0) + (ok ? 1 : 0); store.box[id] = b; A.save(); };
  const flashSession = (title, store, deck) => {
    let queue = dueCards(store, deck).sort(() => Math.random() - 0.5).slice(0, 20);
    if (!queue.length) { A.toast("На сегодня повторений нет — все карточки выучены вовремя"); return; }
    let i = 0, shown = false, okN = 0;
    const w = A.sheet(title, '<div id="fc"></div>');
    const draw = () => {
      const box = w.querySelector("#fc");
      if (i >= queue.length) { box.innerHTML = '<div class="empty"><p class="big">' + okN + " / " + queue.length + '</p><p>Сессия завершена. Отлично!</p></div>'; return; }
      const c = queue[i];
      box.innerHTML = '<div class="small muted">' + (i + 1) + " из " + queue.length + " · коробка " + ((store.box[c.id] || {}).b || 0) + '</div><div class="tcard" style="margin:10px 0"><div class="tn">' + esc(c.front) + "</div>" + (shown ? '<div style="margin-top:8px">' + esc(c.back) + "</div>" : "") + "</div>" +
        (shown ? '<div class="btns"><button class="btn" data-g="0" style="flex:1">Не помню</button><button class="btn primary" data-g="1" style="flex:1">Знаю</button></div>' : '<button class="btn primary block" data-show>Показать ответ</button>');
    };
    w.querySelector("#fc").addEventListener("click", (e) => {
      if (e.target.closest("[data-show]")) { shown = true; draw(); }
      const g = e.target.closest("[data-g]");
      if (g) { const ok = g.dataset.g === "1"; if (ok) okN++; grade(store, queue[i].id, ok); i++; shown = false; draw(); }
    });
    draw();
  };
  const quizSession = (title, store, deck, n = 10) => {
    const qs = deck.slice().sort(() => Math.random() - 0.5).slice(0, Math.min(n, deck.length));
    let i = 0, score = 0;
    const w = A.sheet(title, '<div id="qz"></div>');
    const draw = () => {
      const box = w.querySelector("#qz");
      if (i >= qs.length) {
        store.quiz = (store.quiz || []).concat({ date: today(), score, total: qs.length });
        A.save();
        box.innerHTML = '<div class="empty"><p class="big">' + score + " / " + qs.length + "</p><p>" + (score / qs.length >= 0.8 ? "Прекрасный результат!" : "Повторение — мать учения. Попробуй ещё раз позже.") + "</p></div>";
        return;
      }
      const q = qs[i];
      const wrong = deck.filter((c) => c.back !== q.back).sort(() => Math.random() - 0.5).slice(0, 3).map((c) => c.back);
      const opts = wrong.concat(q.back).sort(() => Math.random() - 0.5);
      box.innerHTML = '<div class="small muted">Вопрос ' + (i + 1) + " из " + qs.length + '</div><div class="tcard" style="margin:10px 0;min-height:90px"><div class="tn">' + esc(q.front) + '</div></div><div class="list">' + opts.map((o) => '<button class="btn block" style="margin:4px 0;justify-content:flex-start;text-align:left;min-height:48px;height:auto;padding:8px 12px" data-o="' + esc(o) + '">' + esc(o) + "</button>").join("") + "</div>";
    };
    w.querySelector("#qz").addEventListener("click", (e) => {
      const b = e.target.closest("[data-o]"); if (!b || b.disabled) return;
      const ok = b.dataset.o === qs[i].back;
      if (ok) score++;
      w.querySelectorAll("[data-o]").forEach((x) => { x.disabled = true; if (x.dataset.o === qs[i].back) x.style.background = "var(--sage2)"; });
      if (!ok) b.style.background = "var(--peach2)";
      grade(store, qs[i].id, ok);
      setTimeout(() => { i++; draw(); }, ok ? 500 : 1300);
    });
    draw();
  };
  const memStats = (store, deck) => {
    const learned = deck.filter((c) => (store.box[c.id] || {}).b >= 3).length;
    const seen = deck.filter((c) => store.box[c.id]).length;
    const due = dueCards(store, deck).length;
    const q = (store.quiz || []).slice(-10);
    return '<div class="grid3"><div class="stat"><small>Выучено (коробка 3+)</small><b>' + learned + "/" + deck.length + '</b></div><div class="stat"><small>Изучалось</small><b>' + seen + '</b></div><div class="stat"><small>К повторению</small><b>' + due + "</b></div></div>" +
      (q.length ? '<div class="card" style="margin-top:12px"><h3>Результаты тестов</h3>' + A.charts.bars(q.map((x) => ({ x: fmtShort(x.date), v: Math.round((x.score / x.total) * 100) })), { color: "#8E7CC3", goal: 80, fmt: (v) => v + "%" }) + "</div>" : "");
  };

  /* ---------- колоды ---------- */
  const tarotDeck = () => L().TAROT.map((c) => ({ id: c.id, front: c.name, back: c.up }));
  const ketuDeck = () => {
    const K = L().KETU;
    return K.terms.map((t, i) => ({ id: "kt" + i, front: t[0], back: t[1] }))
      .concat(K.nakshatras.map((n, i) => ({ id: "kn" + i, front: "Накшатра " + n[0], back: "управитель " + n[1] + "; божество — " + n[2] + "; символ — " + n[3] })))
      .concat(K.dasha.order.map((d, i) => ({ id: "kd" + i, front: "Период " + d[0] + " в Вимшоттари", back: d[1] + " " + A.plural(d[1], "год", "года", "лет") })));
  };
  const baziDeck = () => {
    const B = L().BAZI;
    return B.stems.map((s, i) => ({ id: "bs" + i, front: s[0] + " (" + s[1] + ")", back: s[2] + " — " + s[3] }))
      .concat(B.branches.map((b, i) => ({ id: "bb" + i, front: b[0] + " (" + b[1] + ")", back: b[2] + ", " + b[3] + ", " + b[4] })))
      .concat(B.gods.map((g, i) => ({ id: "bg" + i, front: g[0], back: g[1] })))
      .concat(B.terms.map((t, i) => ({ id: "bt" + i, front: t[0], back: t[1] })));
  };

  /* ---------- Кету: средний лунный узел ---------- */
  // Средний восходящий узел Луны по Меeусу (гл. 47), аянамша Лахири (линейное приближение, точность ~0,01°).
  A.ketuCalc = (dateStr, timeStr, tz) => {
    const [y, m, d] = dateStr.split("-").map(Number);
    const [hh, mm] = (timeStr || "12:00").split(":").map(Number);
    const ut = hh + mm / 60 - (+tz || 0);
    let Y = y, M = m; if (M <= 2) { Y--; M += 12; }
    const Aa = Math.floor(Y / 100), Bb = 2 - Aa + Math.floor(Aa / 4);
    const JD = Math.floor(365.25 * (Y + 4716)) + Math.floor(30.6001 * (M + 1)) + d + ut / 24 + Bb - 1524.5;
    const T = (JD - 2451545.0) / 36525;
    let om = 125.0445479 - 1934.1362891 * T + 0.0020754 * T * T + (T * T * T) / 467441 - (T * T * T * T) / 60616000;
    const norm = (x) => ((x % 360) + 360) % 360;
    om = norm(om);
    const ayan = 23.85306 + 1.39722 * T + 0.00018 * T * T;
    const rahu = norm(om - ayan), ketu = norm(rahu + 180);
    const pos = (lon) => { const si = Math.floor(lon / 30), deg = lon - si * 30, nk = Math.floor(lon / (40 / 3)), pada = Math.floor((lon % (40 / 3)) / (10 / 3)) + 1; return { lon, sign: L().SIGNS[si], si, deg, nk, pada, edge: Math.min(deg, 30 - deg) < 0.5 || Math.min(lon % (40 / 3), 40 / 3 - (lon % (40 / 3))) < 0.2 }; };
    return { JD, ayan, tropRahu: om, rahu: pos(rahu), ketu: pos(ketu) };
  };
  const dms = (x) => { const d = Math.floor(x), m = Math.floor((x - d) * 60); return d + "°" + String(m).padStart(2, "0") + "′"; };

  /* ---------- общий учебный модуль (Кету, Бацзы) ---------- */
  function studyModule(key, title, content, deck, sections, extra) {
    return {
      title, tab: "more",
      render(el, r) {
        const st = A.db()[key];
        const sec = r.args[0] || "home";
        let h = A.seg([["home", "Уроки"], ["ref", "Справочник"], ["cards", "Карточки"], ["notes", "Конспект"]].concat(extra ? [["calc", extra.tab]] : []), sec, "sec");
        if (sec === "home") {
          h += '<div class="card tint"><p>' + esc(content.intro) + "</p></div>";
          h += '<div class="card"><h3>Уроки</h3><div class="list">' + content.lessons.map((l, i) => '<div class="item" data-a="lesson" data-id="' + l.id + '"><div class="ic">' + (st.lessons[l.id] ? "✓" : i + 1) + '</div><div class="tx"><b>' + esc(l.title) + "</b>" + (st.lessons[l.id] ? "<small>пройден " + fmtShort(st.lessons[l.id]) + "</small>" : "") + "</div></div>").join("") + "</div>" +
            A.bar(Object.keys(st.lessons).length / content.lessons.length, "var(--sage)") + "</div>";
          const mins = sum(A.col("learn").filter((x) => x.subject === title.split(" ")[0]).map((x) => x.min));
          h += '<div class="card"><div class="row"><div class="grow"><b>Время изучения</b><div class="small muted">' + fmtDur(mins) + ' всего</div></div><button class="btn sm" data-a="log">+ занятие</button><button class="btn sm" data-a="timer">⏱</button></div></div>';
        } else if (sec === "ref") {
          h += sections();
        } else if (sec === "cards") {
          h += memStats(st, deck());
          h += '<div class="btns" style="margin-top:12px"><button class="btn primary" data-a="flash" style="flex:1">Карточки повторения</button><button class="btn" data-a="quiz" style="flex:1">Тест</button></div>';
        } else if (sec === "notes") {
          h += '<div class="card"><h3>Мой конспект</h3><textarea rows="14" data-c="notes">' + esc(st.notes || "") + '</textarea><p class="small muted">Сохраняется автоматически.</p></div>';
        } else if (sec === "calc" && extra) h += extra.render();
        return h;
      },
      bind(el, r) {
        const st = A.db()[key];
        A.bind(el, Object.assign({
          sec(b) { A.go(r.name + "/" + b.dataset.v, true); },
          lesson(b) {
            const l = content.lessons.find((x) => x.id === b.dataset.id);
            A.sheet(l.title, '<div class="book"><p>' + esc(l.text) + "</p></div>", { buttons: [{ label: st.lessons[l.id] ? "Отметить непройденным" : "Урок пройден", cls: "primary", onClick: () => { if (st.lessons[l.id]) delete st.lessons[l.id]; else st.lessons[l.id] = today(); A.save(); A.refresh(); } }] });
          },
          flash() { flashSession(title + ": карточки", st, deck()); },
          quiz() { quizSession(title + ": тест", st, deck()); },
          notes(b) { st.notes = b.value; A.save(); },
          log() { A.edit.learnSession({ subject: title.split(" ")[0] }); },
          timer() { A.timer.start("learn", title.split(" ")[0], title.split(" ")[0]); A.go("today"); }
        }, extra && extra.handlers ? extra.handlers : {}));
        const ta = el.querySelector('[data-c="notes"]'); if (ta) ta.dataset.live = "";
      }
    };
  }

  A.view("ketu", studyModule("ketu", "Кету", L().KETU, ketuDeck, () => {
    const K = L().KETU;
    return '<div class="card"><h3>Термины и словарь</h3><div class="kv">' + K.terms.map((t) => "<b>" + esc(t[0]) + "</b><span>" + esc(t[1]) + "</span>").join("") + "</div></div>" +
      '<div class="card"><h3>Мифология и символика</h3>' + K.myth.map((p) => "<p>" + esc(p) + "</p>").join("") + "</div>" +
      '<div class="card"><h3>Кету в знаках</h3><p class="small muted">Традиционные образы, а не утверждения о личности.</p>' + K.signs.map((p) => '<p class="small">' + esc(p) + "</p>").join("") + "</div>" +
      '<div class="card"><h3>Кету в домах</h3>' + K.houses.map((p) => '<p class="small">' + esc(p) + "</p>").join("") + "</div>" +
      '<div class="card"><h3>27 накшатр</h3><table class="tbl"><tr><th>#</th><th>Накшатра</th><th>Управитель</th><th>Символ</th></tr>' + K.nakshatras.map((n, i) => "<tr" + (n[1] === "Кету" ? ' style="background:var(--accent2)"' : "") + "><td>" + (i + 1) + "</td><td><b>" + esc(n[0]) + "</b><br><small class=\"muted\">" + esc(n[2] + " · " + n[4]) + "</small></td><td>" + esc(n[1]) + "</td><td>" + esc(n[3]) + "</td></tr>").join("") + "</table></div>" +
      '<div class="card"><h3>Даши (традиционный материал)</h3><p>' + esc(K.dasha.text) + '</p><table class="tbl">' + K.dasha.order.map((d) => "<tr><td>" + esc(d[0]) + '</td><td class="r">' + d[1] + " лет</td></tr>").join("") + '<tr><td><b>Итого</b></td><td class="r"><b>120 лет</b></td></tr></table></div>';
  }, {
    tab: "Расчёт",
    render() {
      const c = A.db().ketu.calc || {};
      let h = '<div class="card"><h3>Положение Кету при рождении</h3><p class="small muted">Метод: <b>средний</b> лунный узел (формула Меeуса, «Астрономические алгоритмы», гл. 47), сидерический зодиак с аянамшей Лахири (приближение, точность ≈ 0,01°). Истинный узел может отличаться до ~1,5°. Это астрономический расчёт точки; толкования — традиционный материал.</p>' +
        '<div class="form"><div class="fld"><label>Дата рождения</label><input type="date" id="kd" value="' + esc(c.d || "") + '"></div><div class="fld"><label>Время</label><input type="time" id="kt" value="' + esc(c.t || "12:00") + '"></div><div class="fld"><label>Часовой пояс (UTC+)</label><input type="number" id="kz" step="0.5" value="' + esc(c.z ?? 3) + '"></div><div class="fld" style="justify-content:flex-end"><button class="btn primary" data-a="kcalc">Рассчитать</button></div></div></div>';
      if (c.d) {
        const r = A.ketuCalc(c.d, c.t, c.z), K = L().KETU;
        const row = (n, p) => "<tr><td><b>" + n + "</b></td><td>" + esc(p.sign) + " " + dms(p.deg) + "</td><td>" + esc(K.nakshatras[p.nk][0]) + ", пада " + p.pada + "</td></tr>";
        h += '<div class="card"><h3>Результат</h3><table class="tbl"><tr><th></th><th>Знак (сидерич.)</th><th>Накшатра</th></tr>' + row("Кету ☋", r.ketu) + row("Раху ☊", r.rahu) + "</table>" +
          (r.ketu.edge ? '<div class="warn" style="margin-top:8px">Кету близко к границе знака или накшатры — из-за разницы среднего и истинного узла результат может отличаться.</div>' : "") +
          '<p class="small muted" style="margin-top:8px">Аянамша: ' + dms(r.ayan) + " · тропический Раху: " + dms(r.tropRahu) + " · JD " + r.JD.toFixed(4) + "</p>" +
          '<div class="quote"><b>Кету в знаке ' + esc(r.ketu.sign) + ":</b> " + esc(K.signs[r.ketu.si].split(": ").slice(1).join(": ")) + "</div>" +
          '<p class="small"><b>Накшатра ' + esc(K.nakshatras[r.ketu.nk][0]) + "</b>: управитель " + esc(K.nakshatras[r.ketu.nk][1]) + ", символ — " + esc(K.nakshatras[r.ketu.nk][3]) + ", темы — " + esc(K.nakshatras[r.ketu.nk][4]) + '.</p><p class="small muted">Дом Кету зависит от асцендента, для которого нужен полный расчёт карты; он здесь не вычисляется.</p></div>';
      }
      return h;
    },
    handlers: {
      kcalc() {
        const d = document.getElementById("kd").value; if (!d) return A.toast("Укажите дату");
        A.db().ketu.calc = { d, t: document.getElementById("kt").value || "12:00", z: A.num(document.getElementById("kz").value, 3) };
        A.save(); A.refresh();
      }
    }
  }));

  A.view("bazi", studyModule("bazi", "Бацзы", L().BAZI, baziDeck, () => {
    const B = L().BAZI;
    return '<div class="card"><h3>Десять небесных стволов</h3><table class="tbl">' + B.stems.map((s) => '<tr><td style="font-size:22px">' + s[0] + "</td><td><b>" + esc(s[1]) + "</b> · " + esc(s[2]) + '<br><small class="muted">' + esc(s[3]) + "</small></td></tr>").join("") + "</table></div>" +
      '<div class="card"><h3>Двенадцать земных ветвей</h3><table class="tbl"><tr><th></th><th>Ветвь</th><th>Скрытые стволы</th></tr>' + B.branches.map((b) => '<tr><td style="font-size:22px">' + b[0] + "</td><td><b>" + esc(b[1] + " · " + b[2]) + '</b><br><small class="muted">' + esc(b[3] + " · " + b[4]) + '</small></td><td style="font-size:17px">' + esc(b[5]) + "</td></tr>").join("") + "</table></div>" +
      '<div class="card"><h3>Пять элементов и Инь/Ян</h3>' + B.elements.map((e) => "<p><b>" + esc(e[0]) + "</b> — " + esc(e[1]) + ' <small class="muted">(' + esc(e[2]) + ")</small></p>").join("") + B.cycles.map((c) => '<p class="small">' + esc(c) + "</p>").join("") + "</div>" +
      '<div class="card"><h3>Десять Богов</h3>' + B.gods.map((g) => '<p class="small"><b>' + esc(g[0]) + "</b>: " + esc(g[1]) + '. <span class="muted">' + esc(g[2]) + "</span></p>").join("") + "</div>" +
      '<div class="card"><h3>Словарь</h3><div class="kv">' + B.terms.map((t) => "<b>" + esc(t[0]) + "</b><span>" + esc(t[1]) + "</span>").join("") + "</div></div>";
  }, {
    tab: "Карта",
    render() {
      return '<div class="card"><h3>Расчёт карты Бацзы</h3><p>Карта четырёх столпов, такты удачи, сила элементов и Десять Богов рассчитываются во встроенном модуле «Нумерология и Бацзы». Там используется солнечный календарь сезонов, истинное солнечное время и историческое время СССР и России.</p><a class="btn primary block" href="numerology.html">Открыть модуль расчёта</a><p class="small muted" style="margin-top:8px">Традиционные толкования — культурный материал, а не научный факт.</p></div>';
    }
  }));

  /* ---------- Таро ---------- */
  A.view("tarot", {
    title: (r) => (r.args[0] === "card" ? "Карта Таро" : "Таро"), tab: "more",
    render(el, r) {
      const T = A.db().tarot, cards = L().TAROT;
      const sec = r.args[0] || "home";
      if (sec === "card") {
        const c = cards.find((x) => x.id === r.args[1]) || cards[0];
        const i = cards.indexOf(c);
        return '<div class="tcard"><div class="small muted">' + esc(c.arcana + (c.suit ? " · " + c.suit + " · " + c.el : " · " + c.num)) + '</div><div class="tn">' + esc(c.name) + "</div></div>" +
          '<div class="card" style="margin-top:12px"><h3>Традиционное значение</h3><p>' + esc(c.up) + '</p><h3 style="margin-top:10px">Перевёрнутое (опционально)</h3><p class="muted">' + esc(c.rev) + '</p><h3 style="margin-top:10px">Символика</h3><p>' + esc(c.sym) + "</p></div>" +
          '<div class="card tint"><h3>Вопрос для рефлексии</h3><p><i>' + esc(c.q) + "</i></p></div>" +
          '<div class="card"><h3>Мои заметки</h3><textarea rows="5" data-c="cnote" data-live data-id="' + c.id + '">' + esc(T.notes[c.id] || "") + "</textarea></div>" +
          '<div class="row between"><button class="btn" data-a="nav" data-v="' + cards[(i + 77) % 78].id + '">‹ ' + esc(cards[(i + 77) % 78].name) + '</button><button class="btn" data-a="nav" data-v="' + cards[(i + 1) % 78].id + '">' + esc(cards[(i + 1) % 78].name) + " ›</button></div>";
      }
      let h = A.seg([["home", "Главная"], ["lessons", "Уроки"], ["cards", "78 карт"], ["study", "Повторение"], ["journal", "Журнал"]], sec, "sec");
      if (sec === "home") {
        const id = T.daily[today()], c = id && cards.find((x) => x.id === id);
        h += '<div class="warn" style="margin-bottom:12px">Образовательный модуль. Карты — инструмент для размышлений, а не достоверное предсказание будущего.</div>';
        h += '<div class="card"><h3>Карта дня</h3>' + (c ? '<div class="tcard" data-a="open" data-id="' + c.id + '"><div class="tn">' + esc(c.name) + "</div><div>" + esc(c.up) + '</div><div class="small"><i>' + esc(c.q) + "</i></div></div>" + '<textarea rows="3" style="margin-top:10px" placeholder="Как тема проявилась сегодня?" data-c="dnote" data-live>' + esc(T.notes["day:" + today()] || "") + "</textarea>" : '<button class="btn primary block" data-a="draw">Вытянуть карту дня</button>') + "</div>";
        h += memStats(T, tarotDeck());
        h += '<div class="btns" style="margin-top:12px"><button class="btn primary" style="flex:1" data-a="flash">Карточки</button><button class="btn" style="flex:1" data-a="quiz">Тест</button></div>';
        const hist = Object.entries(T.daily).sort((a, b) => (b[0] > a[0] ? 1 : -1)).slice(0, 10);
        if (hist.length) h += '<div class="card" style="margin-top:12px"><h3>История изучения</h3>' + hist.map(([d, id]) => { const cc = cards.find((x) => x.id === id); return '<div class="item" data-a="open" data-id="' + id + '"><div class="tx"><b>' + esc(cc ? cc.name : id) + "</b><small>" + fmtDate(d) + (T.notes["day:" + d] ? " · " + esc(T.notes["day:" + d].slice(0, 60)) : "") + "</small></div></div>"; }).join("") + "</div>";
      } else if (sec === "lessons") {
        h += '<div class="card"><div class="list">' + L().TAROT_LESSONS.map((l, i) => '<div class="item" data-a="lesson" data-id="' + l.id + '"><div class="ic">' + (T.lessons[l.id] ? "✓" : i + 1) + '</div><div class="tx"><b>' + esc(l.title) + "</b></div></div>").join("") + "</div></div>";
      } else if (sec === "cards") {
        const f = r.params.f || "all";
        h += '<div class="chips" style="margin-bottom:10px">' + [["all", "Все"], ["major", "Старшие"]].concat(L().SUITS.map((s) => [s.name, s.name])).map(([k, n]) => '<button class="chip' + (f === k ? " on" : "") + '" data-a="filter" data-v="' + k + '">' + n + "</button>").join("") + "</div>";
        const list = cards.filter((c) => f === "all" || (f === "major" ? c.arcana === "Старший аркан" : c.suit === f));
        h += '<div class="card"><div class="list">' + list.map((c) => { const b = (T.box[c.id] || {}).b || 0; return '<div class="item" data-a="open" data-id="' + c.id + '"><div class="ic">' + (c.arcana === "Старший аркан" ? c.num : "✦") + '</div><div class="tx"><b>' + esc(c.name) + "</b><small>" + esc(c.up) + "</small></div><small>" + "●".repeat(b) + '<span style="opacity:.2">' + "●".repeat(5 - b) + "</span></small></div>"; }).join("") + "</div></div>";
      } else if (sec === "study") {
        h += memStats(T, tarotDeck());
        h += '<div class="btns" style="margin-top:12px"><button class="btn primary" style="flex:1" data-a="flash">Карточки повторения</button><button class="btn" style="flex:1" data-a="quiz">Тест 10 вопросов</button></div><p class="small muted">Карточки работают по системе Лейтнера: чем лучше знаешь карту, тем реже она повторяется.</p>';
      } else if (sec === "journal") {
        h += '<button class="btn primary block" data-a="spread" style="margin-bottom:12px">+ Новый расклад</button>';
        h += '<div class="card"><div class="list">' + (T.journal.length ? T.journal.slice().reverse().map((j) => '<div class="item" data-a="jopen" data-id="' + j.id + '"><div class="tx"><b>' + esc(j.question || "Без вопроса") + "</b><small>" + fmtDate(j.date) + " · " + j.cards.map((x) => (cards.find((c) => c.id === x.id) || {}).name).join(", ") + "</small></div></div>").join("") : A.empty("Раскладов пока нет")) + "</div></div>";
      }
      return h;
    },
    bind(el, r) {
      const T = A.db().tarot, cards = L().TAROT;
      A.bind(el, {
        sec(b) { A.go("tarot/" + b.dataset.v, true); },
        filter(b) { A.go("tarot/cards?f=" + b.dataset.v, true); },
        open(b) { A.go("tarot/card/" + b.dataset.id); },
        nav(b) { A.go("tarot/card/" + b.dataset.v, true); },
        draw() { A.tarotDaily(today()); A.refresh(); },
        dnote(b) { T.notes["day:" + today()] = b.value; A.save(); },
        cnote(b) { T.notes[b.dataset.id] = b.value; A.save(); },
        flash() { flashSession("Таро: карточки", T, tarotDeck()); },
        quiz() { quizSession("Таро: какое значение у карты?", T, tarotDeck()); },
        lesson(b) { const l = L().TAROT_LESSONS.find((x) => x.id === b.dataset.id); A.sheet(l.title, '<div class="book"><p>' + esc(l.text) + "</p></div>", { buttons: [{ label: T.lessons[l.id] ? "Отметить непройденным" : "Урок пройден", cls: "primary", onClick: () => { if (T.lessons[l.id]) delete T.lessons[l.id]; else T.lessons[l.id] = today(); A.save(); A.refresh(); } }] }); },
        spread() {
          A.formSheet("Новый расклад", [{ k: "type", label: "Расклад", type: "chips", opts: [["1", "Одна карта"], ["3", "Ситуация / что мешает / следующий шаг"]], def: "3" }, { k: "question", label: "Вопрос для размышления", full: true }], {}, (o) => {
            const n = +o.type || 1, pool = cards.slice().sort(() => Math.random() - 0.5);
            const pos = n === 3 ? ["Ситуация", "Что мешает", "Следующий шаг"] : ["Карта"];
            const j = { id: A.uid(), date: today(), question: o.question, cards: pos.map((p, i) => ({ id: pool[i].id, pos: p, rev: A.db().profile.tarotRev ? Math.random() < 0.3 : false })), notes: "" };
            T.journal.push(j); A.save(); A.refresh(); setTimeout(() => el.querySelector('[data-a="jopen"][data-id="' + j.id + '"]')?.click(), 50);
          });
        },
        jopen(b) {
          const j = T.journal.find((x) => x.id === b.dataset.id);
          const body = '<p class="muted">' + esc(j.question || "") + "</p>" + j.cards.map((x) => { const c = cards.find((k) => k.id === x.id); return '<div class="card tint"><div class="small muted">' + esc(x.pos) + (x.rev ? " · перевёрнута" : "") + "</div><b>" + esc(c.name) + '</b><div class="small">' + esc(x.rev ? c.rev : c.up) + '</div><div class="small"><i>' + esc(c.q) + "</i></div></div>"; }).join("") + '<label class="small muted">Мои мысли</label><textarea id="jn" rows="5">' + esc(j.notes || "") + "</textarea>";
          A.sheet("Расклад · " + fmtShort(j.date), body, { buttons: [{ label: "Удалить", cls: "danger ghost", onClick: () => { T.journal.splice(T.journal.indexOf(j), 1); A.save(); A.refresh(); } }, { label: "Сохранить", cls: "primary", onClick: (w) => { j.notes = w.querySelector("#jn").value; A.save(); } }] });
        }
      });
    }
  });

  /* ---------- Пианино ---------- */
  let metroT = null;
  A.view("piano", {
    title: "Самоучитель пианино", tab: "more",
    render(el, r) {
      const P = A.db().piano, lessons = L().PIANO;
      const sec = r.args[0] || "course";
      let h = A.seg([["course", "Курс"], ["keys", "Клавиатура"], ["read", "Ноты"], ["metro", "Метроном"], ["log", "Практика"]], sec === "lesson" ? "course" : sec, "sec");
      if (sec === "course") {
        const done = Object.keys(P.done).length;
        const wk = sum(A.col("psess").filter((s) => s.date > addDays(today(), -7)).map((s) => s.min)), mo = sum(A.col("psess").filter((s) => s.date.slice(0, 7) === today().slice(0, 7)).map((s) => s.min));
        h += '<div class="grid3"><div class="stat"><small>Уроков пройдено</small><b>' + done + "/" + lessons.length + '</b></div><div class="stat"><small>За 7 дней</small><b>' + fmtDur(wk) + '</b></div><div class="stat"><small>За месяц</small><b>' + fmtDur(mo) + "</b></div></div>";
        const mods = [...new Set(lessons.map((l) => l.mod))];
        mods.forEach((m) => {
          h += '<div class="sec-t">' + esc(m) + '</div><div class="card"><div class="list">' + lessons.filter((l) => l.mod === m).map((l) => { const i = lessons.indexOf(l); return '<div class="item" data-a="lesson" data-id="' + l.id + '"><div class="ic" style="' + (P.done[l.id] ? "background:var(--sage2)" : P.cur === l.id ? "background:var(--accent2)" : "") + '">' + (P.done[l.id] ? "✓" : i + 1) + '</div><div class="tx"><b>' + esc(l.title) + "</b><small>" + (P.cur === l.id ? "текущий урок · " : "") + esc(l.goal) + "</small></div></div>"; }).join("") + "</div></div>";
        });
      } else if (sec === "lesson") {
        const l = lessons.find((x) => x.id === r.args[1]) || lessons[0];
        const i = lessons.indexOf(l);
        h += '<div class="card peach"><div class="small muted">Урок ' + (i + 1) + " · " + esc(l.mod) + '</div><h2 style="font-size:22px;margin:4px 0">' + esc(l.title) + '</h2><p><b>Цель:</b> ' + esc(l.goal) + "</p></div>" +
          '<div class="card"><h3>Теория</h3><p>' + esc(l.theory) + '</p><h3 style="margin-top:12px">Пошагово</h3><ol>' + l.steps.map((s) => "<li>" + esc(s) + "</li>").join("") + "</ol></div>" +
          '<div class="card"><h3>Упражнение</h3><p>' + esc(l.exercise) + "</p>" + (["p02", "p03", "p04", "p05", "p10", "p13", "p14"].includes(l.id) ? A.keyboard(l.id === "p04" ? 48 : 60, 2) : "") + (["p08", "p09"].includes(l.id) ? '<a class="btn" href="#/piano/read' + (l.id === "p09" ? "?clef=bass" : "") + '">Открыть тренажёр нот</a>' : "") + (["p06", "p07"].includes(l.id) ? '<a class="btn" href="#/piano/metro">Открыть метроном</a>' : "") + "</div>" +
          '<div class="card"><h3>Практическое задание</h3><p>' + esc(l.task) + '</p><h3 style="margin-top:12px">Критерий завершения</h3><p>' + esc(l.done) + "</p></div>" +
          '<div class="card"><h3>Заметки</h3><textarea rows="4" data-c="lnote" data-live data-id="' + l.id + '">' + esc(P.notes[l.id] || "") + "</textarea></div>" +
          '<div class="btns"><button class="btn" data-a="setCur" data-id="' + l.id + '">Сделать текущим</button><button class="btn" data-a="practice" data-id="' + l.id + '">+ практика</button><button class="btn primary" data-a="lessonDone" data-id="' + l.id + '">' + (P.done[l.id] ? "Отменить завершение" : "Урок завершён ✓") + "</button></div>" +
          '<div class="row between" style="margin-top:12px">' + (i > 0 ? '<a class="link" href="#/piano/lesson/' + lessons[i - 1].id + '">‹ назад</a>' : "<span></span>") + (i < lessons.length - 1 ? '<a class="link" href="#/piano/lesson/' + lessons[i + 1].id + '">следующий ›</a>' : "") + "</div>";
      } else if (sec === "keys") {
        h += '<div class="card"><h3>Интерактивная клавиатура</h3>' + A.keyboard(48, 3) + '<p class="small muted">Звук синтезируется телефоном. Прокручивайте клавиатуру вбок.</p></div>' +
          '<div class="card"><h3>Найди ноту</h3><p id="findQ" class="big" style="text-align:center">—</p><div class="btns"><button class="btn primary block" data-a="findNew">Новая нота</button></div><p id="findR" class="small muted" style="text-align:center"></p></div>';
      } else if (sec === "read") {
        const clef = r.params.clef || "treble";
        h += A.seg([["treble", "Скрипичный ключ"], ["bass", "Басовый ключ"]], clef, "clef");
        const s = A.db().piano.reading || {};
        h += '<div class="card"><div id="staff"></div><div class="grid3" style="grid-template-columns:repeat(7,1fr);gap:4px;margin-top:10px">' + DIA.map((n, i) => '<button class="btn sm" data-a="ans" data-v="' + i + '">' + n + "</button>").join("") + '</div><p id="rres" class="small" style="text-align:center;min-height:20px;margin-top:8px"></p><p class="small muted" style="text-align:center">Точность за всё время: ' + (s.n ? Math.round((s.ok / s.n) * 100) + "% из " + s.n : "—") + "</p></div>";
      } else if (sec === "metro") {
        const m = A.db().piano.metro || { bpm: 60, beats: 4 };
        h += '<div class="card" style="text-align:center"><div class="big" id="bpm">' + m.bpm + '</div><div class="muted">ударов в минуту</div><input type="range" min="30" max="200" value="' + m.bpm + '" data-c="bpm" data-live style="margin:12px 0"><div class="seg">' + [2, 3, 4, 6].map((b) => '<button data-a="beats" data-v="' + b + '" class="' + (m.beats === b ? "on" : "") + '">' + b + "/" + (b === 6 ? 8 : 4) + "</button>").join("") + '</div><div id="beatDots" style="font-size:28px;letter-spacing:8px;min-height:40px">' + "○".repeat(m.beats) + '</div><button class="btn primary block" data-a="metro">' + (metroT ? "Стоп" : "Старт") + "</button></div>";
      } else if (sec === "log") {
        const ss = A.col("psess").slice().sort((a, b) => (b.date > a.date ? 1 : -1));
        const weeks = []; for (let i = 7; i >= 0; i--) { const a = addDays(A.weekStart(today()), -7 * i); weeks.push({ x: fmtShort(a), v: sum(ss.filter((s) => s.date >= a && s.date <= addDays(a, 6)).map((s) => s.min)) }); }
        h += '<div class="card"><h3>Минуты практики по неделям</h3>' + A.charts.bars(weeks, { color: "#8E7CC3", labelsEvery: 2 }) + "</div>";
        h += '<div class="btns" style="margin-bottom:12px"><button class="btn primary" data-a="practice" style="flex:1">+ запись</button><button class="btn" data-a="timerP" style="flex:1">⏱ Таймер</button></div>';
        h += '<div class="card"><div class="list">' + (ss.length ? ss.slice(0, 50).map((s) => { const l = lessons.find((x) => x.id === s.lesson); return '<div class="item" data-a="ps" data-id="' + s.id + '"><div class="tx"><b>' + fmtDate(s.date) + " · " + fmtDur(s.min) + "</b><small>" + esc([s.item, l && l.title, "сложность " + (s.diff || "—")].filter(Boolean).join(" · ")) + (s.note ? "<br>" + esc(s.note) : "") + "</small></div></div>"; }).join("") : A.empty("Практик пока нет")) + "</div></div>";
      }
      return h;
    },
    bind(el, r) {
      const P = A.db().piano, sec = r.args[0] || "course";
      wireKeyboard(el);
      let target = null, cur = null;
      const clef = r.params.clef || "treble";
      const newRead = () => {
        const rng = clef === "treble" ? [0, 12] : [2, 14];
        let n; do { n = rng[0] + Math.floor(Math.random() * (rng[1] - rng[0] + 1)); } while (n === cur);
        cur = n; el.querySelector("#staff").innerHTML = staffSvg(clef, n);
      };
      if (sec === "read") newRead();
      el.addEventListener("pianokey", (e) => {
        if (target == null) return;
        const ok = e.detail % 12 === target;
        el.querySelector("#findR").textContent = ok ? "Верно! 🎉" : "Это " + NAMES[e.detail % 12] + ", попробуй ещё";
        if (ok) target = null;
      });
      A.bind(el, {
        sec(b) { if (metroT) { clearInterval(metroT); metroT = null; } A.go("piano/" + b.dataset.v, true); },
        lesson(b) { A.go("piano/lesson/" + b.dataset.id); },
        setCur(b) { P.cur = b.dataset.id; A.save(); A.toast("Текущий урок выбран"); A.refresh(); },
        lessonDone(b) { const id = b.dataset.id; if (P.done[id]) delete P.done[id]; else { P.done[id] = today(); const i = L().PIANO.findIndex((x) => x.id === id); if (L().PIANO[i + 1] && P.cur === id) P.cur = L().PIANO[i + 1].id; A.toast("Урок завершён 🎹"); } A.save(); A.refresh(); },
        lnote(b) { P.notes[b.dataset.id] = b.value; A.save(); },
        practice(b) { A.edit.practice({ lesson: b.dataset.id || P.cur }); },
        timerP() { A.timer.start("piano", "", "практика"); A.go("today"); },
        ps(b) { A.edit.practice(A.byId("psess", b.dataset.id)); },
        findNew() { target = [0, 2, 4, 5, 7, 9, 11][Math.floor(Math.random() * 7)]; el.querySelector("#findQ").textContent = NAMES[target] + " (" + LET[target] + ")"; el.querySelector("#findR").textContent = "Нажми эту ноту на клавиатуре"; },
        clef(b) { A.go("piano/read?clef=" + b.dataset.v, true); },
        ans(b) {
          const ok = +b.dataset.v === cur % 7;
          const s = (P.reading = P.reading || { n: 0, ok: 0 }); s.n++; if (ok) s.ok++; A.save();
          const res = el.querySelector("#rres");
          res.textContent = ok ? "Верно: " + DIA[cur % 7] : "Это " + DIA[cur % 7];
          res.style.color = ok ? "var(--good)" : "var(--bad)";
          A.playNote(noteMidi(clef, cur), 0.6);
          setTimeout(newRead, ok ? 500 : 1200);
        },
        bpm(b) { const m = (P.metro = P.metro || { bpm: 60, beats: 4 }); m.bpm = +b.value; el.querySelector("#bpm").textContent = m.bpm; A.save(); if (metroT) { clearInterval(metroT); metroT = null; el.querySelector('[data-a="metro"]').click(); } },
        beats(b) { const m = (P.metro = P.metro || { bpm: 60, beats: 4 }); m.beats = +b.dataset.v; A.save(); if (metroT) { clearInterval(metroT); metroT = null; } A.refresh(); },
        metro(b) {
          if (metroT) { clearInterval(metroT); metroT = null; b.textContent = "Старт"; return; }
          const m = P.metro || { bpm: 60, beats: 4 }; let k = 0;
          const tick = () => { const dots = el.querySelector("#beatDots"); if (!dots) { clearInterval(metroT); metroT = null; return; } click(k % m.beats === 0); dots.textContent = Array.from({ length: m.beats }, (_, i) => (i === k % m.beats ? "●" : "○")).join(""); k++; };
          tick(); metroT = setInterval(tick, 60000 / m.bpm); b.textContent = "Стоп";
        }
      });
    }
  });
  window.addEventListener("hashchange", () => { if (metroT && !location.hash.includes("piano/metro")) { clearInterval(metroT); metroT = null; } });

  /* ---------- центр обучения ---------- */
  A.view("learn", {
    title: "Обучение", tab: "more",
    render() {
      const P = A.db().piano, T = A.db().tarot;
      const tot = (subj) => sum(A.col("learn").filter((x) => x.subject === subj).map((x) => x.min));
      const pm = sum(A.col("psess").map((s) => s.min));
      const mods = [
        ["piano", "🎹", "Самоучитель пианино", Object.keys(P.done).length + "/" + L().PIANO.length + " уроков · " + fmtDur(pm)],
        ["tarot", "🃏", "Обучатель по Таро", L().TAROT.filter((c) => (T.box[c.id] || {}).b >= 3).length + "/78 карт выучено"],
        ["ketu", "🌿", "Модуль Кету", Object.keys(A.db().ketu.lessons).length + "/" + L().KETU.lessons.length + " уроков"],
        ["bazi", "☯", "Обучатель по Бацзы", Object.keys(A.db().bazi.lessons).length + "/" + L().BAZI.lessons.length + " уроков"],
        ["book", "📘", "Книга PRIME ERA", Object.keys(A.db().bookDone).length + "/13 блоков"]
      ];
      let h = '<div class="card"><div class="list">' + mods.map((m) => '<a class="item" style="text-decoration:none;color:inherit" href="#/' + m[0] + '"><div class="ic">' + m[1] + '</div><div class="tx"><b>' + m[2] + "</b><small>" + esc(m[3]) + "</small></div><span class=\"muted\">›</span></a>").join("") + "</div></div>";
      const subs = {}; A.col("learn").forEach((x) => (subs[x.subject] = (subs[x.subject] || 0) + x.min));
      if (pm) subs["Пианино"] = pm;
      h += '<div class="card"><h3>Учебные занятия<span class="sp"></span><button class="btn sm primary" data-a="log">+ занятие</button></h3>' + (Object.keys(subs).length ? A.charts.donut(Object.entries(subs).map(([n, v], i) => ({ name: n, v, color: ["#8E7CC3", "#7F9C7A", "#EFA984", "#C9A45C", "#E3899A", "#7DB0D6"][i % 6] })), fmtDur(sum(Object.values(subs))), "всего") : "") +
        '<div class="list">' + A.col("learn").slice().reverse().slice(0, 15).map((s) => '<div class="item" data-a="ls" data-id="' + s.id + '"><div class="tx"><b>' + esc(s.subject) + " · " + fmtDur(s.min) + "</b><small>" + fmtDate(s.date) + (s.note ? " · " + esc(s.note) : "") + "</small></div></div>").join("") + "</div></div>";
      return h;
    },
    bind(el) { A.bind(el, { log() { A.edit.learnSession(); }, ls(b) { A.edit.learnSession(A.byId("learn", b.dataset.id)); } }); }
  });

  /* ---------- книга PRIME ERA ---------- */
  A.view("book", {
    title: "PRIME ERA", tab: "more",
    render(el, r) {
      const B = window.BOOK, done = A.db().bookDone;
      const n = r.args[0];
      if (n) {
        const b = B.blocks.find((x) => String(x.n) === n) || B.blocks[0];
        const todo = b.todo;
        return '<div class="card peach"><div class="small muted">Блок ' + b.n + '</div><h2 style="font-size:21px">' + esc(b.title[0] + b.title.slice(1).toLowerCase()) + "</h2></div>" +
          '<div class="card book">' + b.text.map((p) => "<p>" + esc(p) + "</p>").join("") + "</div>" +
          '<div class="card tint"><h3>Психологический вывод</h3>' + b.insight.map((p) => '<p class="book">' + esc(p) + "</p>").join("") + "</div>" +
          '<div class="card sage"><h3>Что делать</h3><p class="small muted">Выбери 1–3 действия и превращай их в задачу или привычку.</p><div class="list">' + todo.map((t, i) => '<div class="item"><div class="tx"><span>' + esc(t) + '</span></div>' + (t.length > 12 && !/:$/.test(t) ? '<button class="btn sm" data-a="toTask" data-i="' + i + '">задача</button><button class="btn sm" data-a="toHabit" data-i="' + i + '">привычка</button>' : "") + "</div>").join("") + "</div></div>" +
          '<div class="btns"><button class="btn primary block" data-a="done" data-n="' + b.n + '">' + (done[b.n] ? "Прочитано ✓ (" + fmtShort(done[b.n]) + ")" : "Отметить прочитанным") + "</button></div>" +
          '<div class="row between" style="margin-top:10px">' + (b.n > 1 ? '<a class="link" href="#/book/' + (b.n - 1) + '">‹ блок ' + (b.n - 1) + "</a>" : "<span></span>") + (b.n < 13 ? '<a class="link" href="#/book/' + (b.n + 1) + '">блок ' + (b.n + 1) + " ›</a>" : "") + "</div>";
      }
      const ch = A.db().checklist;
      return '<div class="card hero"><img src="img/logo-192.png" width="72" height="72" alt="" style="border-radius:50%"><div class="date" style="margin-top:6px">PRIME ERA</div><div class="phrase">Психологические выводы + конкретные действия + система на 90 дней и горизонты 1 / 3 / 10 лет</div></div>' +
        '<div class="card"><p class="small">' + esc(B.intro) + "</p></div>" +
        '<div class="card"><div class="list">' + B.blocks.map((b) => '<a class="item" style="text-decoration:none;color:inherit" href="#/book/' + b.n + '"><div class="ic" style="' + (done[b.n] ? "background:var(--sage2)" : "") + '">' + (done[b.n] ? "✓" : b.n) + '</div><div class="tx"><b>' + esc(b.title[0] + b.title.slice(1).toLowerCase()) + "</b></div></a>").join("") + "</div></div>" +
        '<div class="card peach"><h3>Финальный практический чек-лист</h3>' + B.checklist.map((c, i) => '<label class="switch"><input type="checkbox" data-c="ch" data-i="' + i + '"' + (ch[i] ? " checked" : "") + "><span></span>" + esc(c) + "</label>").join("") + '<p class="book" style="text-align:center;margin-top:10px"><i>«Я не обязана знать весь маршрут. Я обязана понимать направление и делать следующий шаг.»</i></p></div>' +
        '<div class="card sage"><h3>Опоры из книги</h3><div class="list">' +
        '<div class="item" data-a="tool" data-t="stop"><div class="ic">✋</div><div class="tx"><b>Протокол «СТОП»</b><small>вместо «я ленивая» — точная причина и минимальный шаг</small></div></div>' +
        '<div class="item" data-a="tool" data-t="four"><div class="ic">❓</div><div class="tx"><b>Четыре вопроса перед обязательством</b><small>я сама хочу? ценности? свобода? что уберу?</small></div></div>' +
        '<div class="item" data-a="tool" data-t="crisis"><div class="ic">🛟</div><div class="tx"><b>В кризисе</b><small>сон → еда → движение → обязательства → близкие → одна задача</small></div></div>' +
        '<div class="item" data-a="tool" data-t="conflict"><div class="ic">🤝</div><div class="tx"><b>Порядок в конфликте</b><small>выслушать → повторить → согласие → позиция → решение</small></div></div>' +
        '<a class="item" style="text-decoration:none;color:inherit" href="#/goals/era"><div class="ic">90</div><div class="tx"><b>90 дней PRIME ERA</b><small>наблюдение → настройка → закрепление</small></div></a></div></div>';
    },
    bind(el, r) {
      const B = window.BOOK;
      const blk = () => B.blocks.find((x) => String(x.n) === r.args[0]);
      A.bind(el, {
        done(b) { const n = b.dataset.n, d = A.db().bookDone; if (d[n]) delete d[n]; else d[n] = today(); A.save(); A.refresh(); },
        toTask(b) { A.edit.task(null, { title: blk().todo[+b.dataset.i].replace(/^[•\d.\s]+/, "").replace(/;$/, "") }); },
        toHabit(b) { A.edit.habit(); setTimeout(() => { const i = document.querySelector('.sheet-wrap:last-child [name="name"]'); if (i) i.value = blk().todo[+b.dataset.i].replace(/^[•\d.\s]+/, "").replace(/;$/, "").slice(0, 60); }, 30); },
        ch(b) { A.db().checklist[b.dataset.i] = b.checked; A.save(); },
        tool(b) { A.tools[b.dataset.t](); }
      });
    }
  });

  /* ---------- инструменты-опоры ---------- */
  A.tools = {
    stop() {
      A.formSheet("Протокол «СТОП»", [
        { k: "s", label: "С — стоп на автоматической самооценке. Что я про себя подумала?", type: "textarea", rows: 2 },
        { k: "t", label: "Т — точно назвать, что мешает", type: "chips", opts: ["усталость", "скука", "неопределённость", "слишком большой объём", "нет смысла", "страх ошибки", "реальное нежелание"] },
        { k: "o", label: "О — один возможный ответ", full: true },
        { k: "p", label: "П — минимальный шаг (станет задачей на сегодня)", full: true }
      ], {}, (o) => { if (o.p) A.upsert("tasks", { title: o.p, date: today(), prio: 1, done: false, note: "СТОП: " + [o.t, o.o].filter(Boolean).join(" → "), created: today() }); A.toast("Минимальный шаг добавлен в задачи"); A.refresh(); }, { saveLabel: "Сделать шаг" });
    },
    four() {
      A.formSheet("Перед новым обязательством", [
        { k: "what", label: "Что за обязательство", full: true },
        { k: "q1", label: "1. Я сама этого хочу?", type: "chips", opts: ["да", "не уверена", "нет"] },
        { k: "q2", label: "2. Это соответствует моим ценностям? Каким?", type: "multi", opts: A.db().profile.values },
        { k: "q3", label: "3. Это увеличивает или уменьшает мою свободу?", type: "chips", opts: ["увеличивает", "не влияет", "уменьшает"] },
        { k: "q4", label: "4. Что я готова ради этого убрать?", full: true }
      ], {}, (o) => {
        const warn = !o.q4 ? "Нет ответа на четвёртый вопрос — цель может быть перегруженной." : o.q1 === "нет" ? "Похоже, это не твоё желание. Проверь, не чужое ли это ожидание." : !o.q2.length ? "Цель не связана ни с одной ценностью — возможно, она из чужих ожиданий." : "Проверка пройдена. Можно брать — вместе с тем, что уберёшь: «" + o.q4 + "».";
        setTimeout(() => A.sheet("Итог", '<p class="book">' + esc(warn) + "</p>", { buttons: [{ label: "Понятно", cls: "primary" }] }), 250);
      }, { saveLabel: "Проверить" });
    },
    crisis() { A.sheet("В кризисе", '<div class="book"><p>Порядок восстановления:</p><ol><li>Сон</li><li>Еда</li><li>Движение</li><li>Обязательства</li><li>Близкие</li><li>Одна главная задача</li><li>Остальное — позже</li></ol><p><i>Устойчивость — это скорость возвращения к системе. Ничего не компенсировать: возврат начинается со следующего доступного действия.</i></p><p class="small muted">Если тяжело долго или появились мысли навредить себе — обратись к специалисту или на линию помощи.</p></div>', { buttons: [{ label: "Спасибо", cls: "primary" }] }); },
    conflict() { A.sheet("Порядок в конфликте", '<div class="book"><ol><li>Выслушать.</li><li>Своими словами повторить смысл позиции другого.</li><li>Сказать, с чем согласна.</li><li>Обозначить собственную позицию.</li><li>Сформулировать решение.</li></ol><p><i>Иногда другому человеку сначала необходимо почувствовать, что его поняли, и только потом обсуждать решение.</i></p><p>После конфликта записывай не идеальный ответ, который пришёл поздно, а один принцип на следующий раз.</p></div>', { buttons: [{ label: "Хорошо", cls: "primary" }] }); }
  };
})();
