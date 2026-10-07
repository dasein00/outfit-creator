/* Культурный календарь: карточка «Этот день в культуре» на экране «Сегодня» и экран с выбором дня. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today, addDays, fmtDate } = A;

  const TYPE = { b: ["{{i:gift}}", "день рождения"], f: ["{{i:clapperboard}}", "премьера"], m: ["{{i:music}}", "музыка"], e: ["{{i:sparkles}}", "событие"] };
  const REG = { RU: "Россия", US: "США", WORLD: "Мир" };
  const key = (d) => d.slice(5);
  const yearsAgo = (y, d) => { if (!y) return ""; const n = +d.slice(0, 4) - y; return n > 0 ? n + " " + A.plural(n, "год", "года", "лет") + " назад" : ""; };
  const listFor = (d, reg) => ((window.CULTURE || {})[key(d)] || []).filter((x) => !reg || reg === "all" || x[1] === reg);
  const row = (x, d) => {
    const t = TYPE[x[0]] || TYPE.e, ago = yearsAgo(x[2], d);
    return '<div class="item"><div class="ic">' + t[0] + '</div><div class="tx"><b>' + esc(x[3]) + "</b><small>" + esc([x[2] ? (x[0] === "b" ? "родился(-ась) в " : "") + x[2] : "", ago, REG[x[1]] || ""].filter(Boolean).join(" · ")) + "</small></div></div>";
  };

  A.CARD_NAMES.culture = "Этот день в культуре";
  A.todayCards.culture = (d) => {
    const all = listFor(d);
    if (!all.length) return "";
    // на карточке — до четырёх записей, рождения и премьеры вперёд
    const order = { b: 0, f: 1, m: 2, e: 3 };
    const top = all.slice().sort((a, b) => order[a[0]] - order[b[0]]).slice(0, 4);
    return '<div class="card"><h3>{{i:clapperboard}} Этот день в культуре<span class="sp"></span><a class="link" href="#/culturecal?d=' + d + '">все ' + all.length + " →</a></h3>" + top.map((x) => row(x, d)).join("") + "</div>";
  };

  A.view("culturecal", {
    title: "Календарь культуры", tab: "more",
    render(el, r) {
      const d = r.params.d || today(), reg = r.params.r || "all";
      const list = listFor(d, reg);
      let h = A.dateNav(d, "cday") + A.seg([["all", "Все"], ["RU", "Россия"], ["US", "США"], ["WORLD", "Мир"]], reg, "creg");
      const groups = [["b", "Дни рождения"], ["f", "Премьеры"], ["m", "Музыка"], ["e", "События и праздники"]];
      groups.forEach(([t, n]) => { const g = list.filter((x) => x[0] === t); if (g.length) h += '<div class="card"><h3>' + TYPE[t][0] + " " + n + "</h3>" + g.map((x) => row(x, d)).join("") + "</div>"; });
      if (!list.length) h += A.empty("Для этого дня и региона записей нет");
      h += '<p class="small muted" style="padding:0 6px">Календарь работает без интернета. Даты премьер — по первому показу или выходу в США; даты рождения — по новому стилю.</p>';
      return h;
    },
    bind(el, r) {
      const d = r.params.d || today(), reg = r.params.r || "all";
      const go = (dd, rr) => A.go("culturecal?d=" + dd + "&r=" + rr, true);
      A.bind(el, {
        cday(b) { if (b.dataset.v === "pick") A.pickDate(d, (x) => go(x, reg)); else go(addDays(d, +b.dataset.v), reg); },
        creg(b) { go(d, b.dataset.v); }
      });
    }
  });
})();
