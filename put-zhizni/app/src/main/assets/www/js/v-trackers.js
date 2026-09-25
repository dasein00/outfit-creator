/* Календарные матрицы в стиле бумажного планера: строки 1–31, столбцы — месяцы года. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, fmtN } = A;

  const step = (bounds, colors, labels) => ({
    color: (v) => { for (let i = 0; i < bounds.length; i++) if (v < bounds[i]) return colors[i]; return colors[colors.length - 1]; },
    legend: colors.map((c, i) => [c, labels[i]])
  });
  const MOOD_C = ["#D2555E", "#DE6F6A", "#E88A5E", "#F0A45B", "#F4C95D", "#E6D35E", "#C9D66B", "#A8CC6E", "#8CC474", "#6DB577", "#4FA36F"];

  // Шкалы для каждого показателя: цвет, легенда, короткая подпись.
  A.metricScale = (k) => {
    const p = A.db().profile;
    switch (k) {
      case "mood": case "energy": case "sleepQ":
        return { color: (v) => MOOD_C[Math.round(A.clamp(v, 0, 10))], legend: [[MOOD_C[10], "10 — восторг"], [MOOD_C[8], "8 — хорошее"], [MOOD_C[6], "6 — нормальное"], [MOOD_C[5], "5 — спокойно"], [MOOD_C[3], "3 — раздражение"], [MOOD_C[1], "1 — очень плохо"]], short: (v) => fmtN(v) };
      case "stress": case "anx":
        return { color: (v) => MOOD_C[10 - Math.round(A.clamp(v, 0, 10))], legend: [[MOOD_C[10], "0 — нет"], [MOOD_C[7], "3 — слабый"], [MOOD_C[5], "5 — средний"], [MOOD_C[3], "7 — сильный"], [MOOD_C[0], "10 — очень сильный"]], short: (v) => fmtN(v) };
      case "sleep":
        return Object.assign(step([4, 5, 6, 7, 8, 9], ["#D2555E", "#E8A0A8", "#EDE7F3", "#C8BCEB", "#9C8FD8", "#6F60C2", "#4B3C9E"], ["< 4 ч", "4–5 ч", "5–6 ч", "6–7 ч", "7–8 ч", "8–9 ч", "> 9 ч"]), { short: (v) => fmtN(v, 1) });
      case "steps":
        return Object.assign(step([1000, 3000, 6000, 10000, 15000, 20000], ["#E6F2E8", "#BFDFC6", "#8EC59C", "#5FA774", "#3C8A57", "#23703F", "#0F4F29"], ["< 1 000", "1 000–3 000", "3 001–6 000", "6 001–10 000", "10 001–15 000", "15 001–20 000", "> 20 000"]), { short: (v) => (v >= 1000 ? Math.round(v / 1000) + "к" : v) });
      case "temp":
        return Object.assign(step([-20, -10, 1, 11, 21, 31, 41], ["#3E5FA8", "#5B86D6", "#8DB9E8", "#C4D96E", "#F4CD52", "#F08A3C", "#D2433A", "#6E3B35"], ["< −20 °C", "−20…−10 °C", "−10…0 °C", "1–10 °C", "11–20 °C", "21–30 °C", "31–40 °C", "> 40 °C"]), { short: (v) => fmtN(v) + "°" });
      case "water": { const g = p.waterGoal || 2000; return Object.assign(step([g * 0.25, g * 0.5, g * 0.75, g], ["#EAF3FA", "#BCD9EE", "#8DBDE0", "#5E9FD0", "#2F7DBE"], ["< 25%", "25–50%", "50–75%", "75–100%", "цель выполнена"]), { short: (v) => (v / 1000).toFixed(1) }); }
      case "workout":
        return Object.assign(step([1, 20, 40, 60], ["#EDEDED", "#CFE4CF", "#95C895", "#5FA75F", "#2F7F3F"], ["нет", "< 20 мин", "20–40 мин", "40–60 мин", "> 60 мин"]), { short: (v) => v });
      case "kcal": { const g = p.kcal || 1800; return { color: (v) => { const r = v / g; return r < 0.7 ? "#F6E7B5" : r < 0.9 ? "#F2D27A" : r <= 1.1 ? "#8CC474" : r <= 1.3 ? "#F0A45B" : "#D2555E"; }, legend: [["#F6E7B5", "< 70% цели"], ["#F2D27A", "70–90%"], ["#8CC474", "±10% цели"], ["#F0A45B", "110–130%"], ["#D2555E", "> 130%"]], short: (v) => Math.round(v / 100) / 10 + "к" }; }
      case "pages": case "read":
        return Object.assign(step([1, 10, 25, 50], ["#EDEDED", "#F6D9C5", "#EFB28C", "#E08A5A", "#B85F32"], ["нет", k === "pages" ? "1–9 стр." : "1–9 мин", k === "pages" ? "10–24" : "10–24 мин", k === "pages" ? "25–49" : "25–49 мин", k === "pages" ? "50+" : "50+ мин"]), { short: (v) => v });
      case "piano": case "learn": case "hobby": case "free":
        return Object.assign(step([1, 15, 30, 60], ["#EDEDED", "#D9D2F0", "#B3A6E3", "#8E7CC3", "#5E4C9C"], ["нет", "< 15 мин", "15–30 мин", "30–60 мин", "> 60 мин"]), { short: (v) => v });
      case "spend":
        return Object.assign(step([1, 500, 1500, 3000, 7000], ["#EDEDED", "#E5F0DD", "#F6E7B5", "#F2C27A", "#EB8F5E", "#D2555E"], ["0", "< 500", "500–1 500", "1 500–3 000", "3 000–7 000", "> 7 000"]), { short: (v) => (v >= 1000 ? Math.round(v / 1000) + "к" : Math.round(v)) });
      case "habits":
        return Object.assign(step([1, 34, 67, 100], ["#E07B6A", "#F0A45B", "#F2CF5B", "#A8CC6E", "#6BAF6B"], ["0%", "1–33%", "34–66%", "67–99%", "100%"]), { short: (v) => v + "%" });
    }
    return { color: () => "var(--accent)", legend: [] };
  };

  const TRACKERS = [
    ["mood", "Настроение"], ["weather", "Погода"], ["temp", "Температура"], ["steps", "Шаги"], ["sleep", "Сон"], ["stress", "Стресс"], ["anx", "Тревожность"],
    ["energy", "Энергия"], ["workout", "Тренировки"], ["water", "Вода"], ["habits", "Привычки"], ["pages", "Чтение"], ["kcal", "Питание"]
  ];

  const cellFor = (k, hid) => {
    if (k === "weather") return (d) => { const w = (A.dayGet(d) || {}).weather; const c = w && A.weather(w.cond); return c ? { c: c.color, t: c.name + (w.t != null ? ", " + w.t + "°" : ""), s: c.icon } : null; };
    if (k === "habit") return (d) => { const st = A.hs(hid, d); if (!st) return null; const S = A.HSTATUS[st]; return { c: S[2], t: S[0], s: S[1] }; };
    const sc = A.metricScale(k), get = A.METRICS[k].get;
    return (d) => { const v = get(d); if (v == null) return null; return { c: sc.color(v), t: fmtN(v, 1), s: sc.short ? sc.short(v) : fmtN(v) }; };
  };

  A.view("trackers", {
    root: true, title: "Трекеры",
    actions(el) { el.innerHTML = '<button class="icon-btn" onclick="App.print(\'Трекер\')" aria-label="Печать">⎙</button>'; },
    render(el, r) {
      const k = r.params.k || "mood", y = +(r.params.y || new Date().getFullYear());
      const hid = r.params.h || "";
      const sym = r.params.s === "1";
      let h = '<div class="chips noprint" style="margin-bottom:10px">' + TRACKERS.map(([kk, n]) => '<button class="chip' + (kk === k ? " on" : "") + '" data-a="k" data-v="' + kk + '">' + n + "</button>").join("") + "</div>";
      let title = (TRACKERS.find((x) => x[0] === k) || [0, ""])[1], cell, legend;
      if (k === "habits") {
        const hs = A.col("habits").filter((x) => !x.archived);
        h += '<div class="chips noprint" style="margin-bottom:10px"><button class="chip' + (!hid ? " on" : "") + '" data-a="h" data-v="">Все, % дня</button>' + hs.map((x) => '<button class="chip' + (x.id === hid ? " on" : "") + '" data-a="h" data-v="' + x.id + '">' + esc((x.icon || "") + " " + x.name) + "</button>").join("") + "</div>";
        if (hid) { const hb = A.byId("habits", hid); title = "Привычка: " + (hb ? hb.name : ""); cell = cellFor("habit", hid); legend = Object.values(A.HSTATUS).map((S) => [S[2], S[1] + " " + S[0]]); }
        else { cell = cellFor("habits"); legend = A.metricScale("habits").legend; }
      } else if (k === "weather") { cell = cellFor("weather"); legend = A.WEATHER.map((w) => [w[3], w[2] + " " + w[1]]); }
      else { cell = cellFor(k); legend = A.metricScale(k).legend; }
      h += '<div class="card"><div class="row between"><button class="icon-btn noprint" data-a="y" data-v="-1">‹</button><h2 style="font-size:18px;font-style:italic;text-align:center">Трекер: ' + esc(title.toLowerCase()) + " · " + y + '</h2><button class="icon-btn noprint" data-a="y" data-v="1">›</button></div>' +
        A.charts.yearMatrix(y, cell, { symbols: sym }) +
        '<div class="mlegend">' + legend.map(([c, l]) => '<div><i style="background:' + c + '"></i>' + esc(l) + "</div>").join("") + "</div>" +
        '<label class="switch noprint" style="margin-top:8px"><input type="checkbox" data-c="sym"' + (sym ? " checked" : "") + '><span></span>Показывать значения и символы в клетках</label></div>';
      // сводка за год
      if (A.METRICS[k]) {
        const vals = A.range(y + "-01-01", y + "-12-31" > today() ? today() : y + "-12-31").map(A.METRICS[k].get);
        const s = A.statsOf(vals);
        h += '<div class="grid3"><div class="stat"><small>Дней с данными</small><b>' + s.n + '</b></div><div class="stat"><small>Среднее</small><b>' + fmtN(s.avg, 1) + '</b></div><div class="stat"><small>Мин / макс</small><b>' + fmtN(s.min) + " / " + fmtN(s.max) + "</b></div></div>";
      }
      h += '<p class="small muted" style="margin-top:12px">Нажмите на клетку, чтобы открыть день и внести данные. В матрицах используются и цвета, и символы — включите «Показывать значения».</p>';
      return h;
    },
    bind(el, r) {
      const P = A.route().params;
      const q = (o) => { const x = Object.assign({ k: P.k || "mood", y: P.y || new Date().getFullYear(), h: P.h || "", s: P.s || "" }, o); A.go("trackers?" + Object.keys(x).filter((k) => x[k] !== "").map((k) => k + "=" + x[k]).join("&"), true); };
      A.bind(el, {
        k(b) { q({ k: b.dataset.v, h: "" }); }, h(b) { q({ h: b.dataset.v }); },
        y(b) { q({ y: +(P.y || new Date().getFullYear()) + +b.dataset.v }); },
        sym(b) { q({ s: b.checked ? "1" : "" }); },
        mcell(b) {
          const d = b.dataset.d;
          if (P.k === "habits" && P.h) { A.cycleHs(A.byId("habits", P.h), d); A.refresh(); return; }
          A.go("today?d=" + d);
        }
      });
    }
  });
})();
