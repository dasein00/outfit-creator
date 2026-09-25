/* Графики на SVG: столбцы, линии, кольцевая диаграмма, календарные матрицы. */
(function () {
  "use strict";
  const A = window.App;
  const esc = A.esc;
  const C = (A.charts = {});

  const nice = (max) => { if (max <= 0) return 1; const p = Math.pow(10, Math.floor(Math.log10(max))); const n = max / p; return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p; };

  // data: [{x: подпись, v: число|null}], opt: {h, goal, color, fmt, labelsEvery}
  C.bars = (data, opt = {}) => {
    const W = 320, H = opt.h || 140, pl = 30, pb = 18, pt = 8, pr = 4;
    const vals = data.map((d) => d.v || 0);
    const max = nice(Math.max(opt.goal || 0, ...vals, 1));
    const bw = (W - pl - pr) / Math.max(data.length, 1);
    const y = (v) => pt + (H - pt - pb) * (1 - v / max);
    const every = opt.labelsEvery || Math.ceil(data.length / 8);
    let s = '<svg class="chart" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' + esc(opt.title || "Столбчатая диаграмма") + '">';
    [0, 0.5, 1].forEach((k) => { const yy = y(max * k); s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + yy + '" y2="' + yy + '" class="grid"/><text x="' + (pl - 4) + '" y="' + (yy + 3) + '" class="ax" text-anchor="end">' + esc(A.fmtN(max * k, max < 10 ? 1 : 0)) + "</text>"; });
    if (opt.goal) s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + y(opt.goal) + '" y2="' + y(opt.goal) + '" class="goal"/>';
    data.forEach((d, i) => {
      const x = pl + i * bw;
      if (d.v != null && d.v > 0) {
        const col = d.c || opt.color || "var(--accent)";
        s += '<rect x="' + (x + bw * 0.15).toFixed(1) + '" y="' + y(d.v).toFixed(1) + '" width="' + (bw * 0.7).toFixed(1) + '" height="' + (H - pb - y(d.v)).toFixed(1) + '" rx="2" fill="' + col + '"><title>' + esc(d.x + ": " + (opt.fmt ? opt.fmt(d.v) : A.fmtN(d.v, 1))) + "</title></rect>";
      }
      if (i % every === 0) s += '<text x="' + (x + bw / 2) + '" y="' + (H - 5) + '" class="ax" text-anchor="middle">' + esc(d.x) + "</text>";
    });
    return s + "</svg>";
  };

  // series: [{name, color, data:[{x, v}]}]
  C.line = (series, opt = {}) => {
    const W = 320, H = opt.h || 140, pl = 30, pb = 18, pt = 8, pr = 6;
    const all = series.flatMap((s) => s.data.map((d) => d.v)).filter((v) => v != null);
    if (!all.length) return '<div class="muted small">Нет данных для графика</div>';
    let mn = opt.min != null ? opt.min : Math.min(...all), mx = opt.max != null ? opt.max : Math.max(...all);
    if (mn === mx) { mn -= 1; mx += 1; }
    const n = Math.max(...series.map((s) => s.data.length));
    const x = (i) => pl + (W - pl - pr) * (n <= 1 ? 0.5 : i / (n - 1));
    const y = (v) => pt + (H - pt - pb) * (1 - (v - mn) / (mx - mn));
    let s = '<svg class="chart" viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' + esc(opt.title || "Линейный график") + '">';
    [0, 0.5, 1].forEach((k) => { const v = mn + (mx - mn) * k; s += '<line x1="' + pl + '" x2="' + (W - pr) + '" y1="' + y(v) + '" y2="' + y(v) + '" class="grid"/><text x="' + (pl - 4) + '" y="' + (y(v) + 3) + '" class="ax" text-anchor="end">' + esc(A.fmtN(v, Math.abs(mx - mn) < 10 ? 1 : 0)) + "</text>"; });
    const every = Math.ceil(n / 7);
    (series[0].data || []).forEach((d, i) => { if (i % every === 0) s += '<text x="' + x(i) + '" y="' + (H - 5) + '" class="ax" text-anchor="middle">' + esc(d.x) + "</text>"; });
    series.forEach((se) => {
      let path = "", pen = false;
      se.data.forEach((d, i) => { if (d.v == null) { pen = false; return; } path += (pen ? "L" : "M") + x(i).toFixed(1) + " " + y(d.v).toFixed(1); pen = true; });
      s += '<path d="' + path + '" fill="none" stroke="' + se.color + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>';
      se.data.forEach((d, i) => { if (d.v != null) s += '<circle cx="' + x(i).toFixed(1) + '" cy="' + y(d.v).toFixed(1) + '" r="2.4" fill="' + se.color + '"><title>' + esc(d.x + " · " + se.name + ": " + A.fmtN(d.v, 1)) + "</title></circle>"; });
    });
    s += "</svg>";
    if (series.length > 1) s += '<div class="legend">' + series.map((se) => '<span><i style="background:' + se.color + '"></i>' + esc(se.name) + "</span>").join("") + "</div>";
    return s;
  };

  // parts: [{name, v, color}]
  C.donut = (parts, center, sub) => {
    const tot = A.sum(parts.map((p) => p.v));
    const r = 42, c = 2 * Math.PI * r;
    let off = 0, s = '<div class="donut"><svg viewBox="0 0 110 110" width="130" height="130" role="img" aria-label="Кольцевая диаграмма"><circle cx="55" cy="55" r="' + r + '" fill="none" stroke="var(--line)" stroke-width="16"/>';
    if (tot > 0) parts.forEach((p) => {
      const L = (p.v / tot) * c;
      s += '<circle cx="55" cy="55" r="' + r + '" fill="none" stroke="' + p.color + '" stroke-width="16" stroke-dasharray="' + L.toFixed(2) + " " + (c - L).toFixed(2) + '" stroke-dashoffset="' + (-off).toFixed(2) + '" transform="rotate(-90 55 55)"><title>' + esc(p.name + ": " + Math.round((p.v / tot) * 100) + "%") + "</title></circle>";
      off += L;
    });
    s += '<text x="55" y="55" text-anchor="middle" class="dn-c">' + esc(center == null ? A.fmtN(tot) : center) + '</text><text x="55" y="70" text-anchor="middle" class="dn-s">' + esc(sub || "") + "</text></svg>";
    s += '<div class="dn-leg">' + parts.filter((p) => p.v > 0).map((p) => '<div><i style="background:' + p.color + '"></i><span>' + esc(p.name) + "</span><b>" + (tot ? Math.round((p.v / tot) * 100) : 0) + "%</b></div>").join("") + "</div></div>";
    return s;
  };

  // Годовая матрица: строки — числа 1–31, столбцы — месяцы. cell(dateIso) → {c: цвет, t: подсказка, s: символ}
  C.yearMatrix = (year, cell, opt = {}) => {
    let s = '<div class="matrix-wrap"><table class="matrix"><thead><tr><th></th>' + A.MON_SHORT.map((m, i) => "<th>" + m + "</th>").join("") + "</tr></thead><tbody>";
    const t = A.today();
    for (let d = 1; d <= 31; d++) {
      s += "<tr><th>" + d + "</th>";
      for (let m = 1; m <= 12; m++) {
        if (d > A.daysInMonth(year, m)) { s += '<td class="na"></td>'; continue; }
        const di = year + "-" + A.pad(m) + "-" + A.pad(d);
        const r = di > t ? null : cell(di);
        s += '<td data-a="mcell" data-d="' + di + '"' + (r && r.c ? ' style="background:' + r.c + '"' : "") + (di === t ? ' class="now"' : "") + ' title="' + esc(A.fmtShort(di) + (r && r.t ? ": " + r.t : "")) + '">' + (r && r.s && opt.symbols ? esc(r.s) : "") + "</td>";
      }
      s += "</tr>";
    }
    return s + "</tbody></table></div>";
  };

  // Месячная сетка привычек: строки — привычки, столбцы — дни.
  C.monthGrid = (mk, rows, cell) => {
    const [y, m] = mk.split("-").map(Number);
    const n = A.daysInMonth(y, m);
    let s = '<div class="matrix-wrap"><table class="matrix wide"><thead><tr><th></th>';
    for (let d = 1; d <= n; d++) s += "<th>" + d + "</th>";
    s += "</tr></thead><tbody>";
    rows.forEach((r) => {
      s += '<tr><th class="rowh"><span style="display:inline-block;max-width:118px;overflow:hidden;text-overflow:ellipsis;vertical-align:middle">' + esc(r.name) + "</span></th>";
      for (let d = 1; d <= n; d++) {
        const di = mk + "-" + A.pad(d);
        const c = cell(r, di) || {};
        s += '<td data-a="gcell" data-id="' + esc(r.id) + '" data-d="' + di + '"' + (c.c ? ' style="background:' + c.c + '"' : "") + ' title="' + esc(c.t || "") + '">' + esc(c.s || "") + "</td>";
      }
      s += "</tr>";
    });
    return s + "</tbody></table></div>";
  };

  // Радар для колеса жизни: axes [{name, v (0..10)}]
  C.radar = (axes) => {
    const n = axes.length, R = 80, cx = 110, cy = 100;
    const pt = (i, v) => { const a = -Math.PI / 2 + (i * 2 * Math.PI) / n; return [cx + Math.cos(a) * R * v / 10, cy + Math.sin(a) * R * v / 10]; };
    let s = '<svg class="chart" viewBox="0 0 220 200" role="img" aria-label="Колесо жизни">';
    [2, 4, 6, 8, 10].forEach((k) => { s += '<polygon class="grid" fill="none" points="' + axes.map((_, i) => pt(i, k).join(",")).join(" ") + '"/>'; });
    axes.forEach((a, i) => { const [x, y] = pt(i, 11.6); s += '<line class="grid" x1="' + cx + '" y1="' + cy + '" x2="' + pt(i, 10)[0] + '" y2="' + pt(i, 10)[1] + '"/><text class="ax" x="' + x + '" y="' + (y + 3) + '" text-anchor="middle">' + esc(a.name) + "</text>"; });
    s += '<polygon fill="var(--accent)" fill-opacity=".25" stroke="var(--accent)" stroke-width="2" points="' + axes.map((a, i) => pt(i, a.v || 0).join(",")).join(" ") + '"/>';
    return s + "</svg>";
  };
})();
