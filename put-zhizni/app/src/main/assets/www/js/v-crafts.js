/* Рукоделие: вязание спицами и крючком (приёмы по шагам с картинками, узоры — вид полотна и схема)
   и украшения своими руками (инструменты, приёмы сборки, серьги и другие изделия по шагам).
   Картинки — векторные схемы, рисуются из описаний кадров в content-crafts.js. */
(function () {
  "use strict";
  const A = window.App;
  const { esc } = A;
  const K = () => window.CRAFTS;

  /* ---------- рисование кадров ---------- */
  // Кадр — список примитивов в системе координат 200×120.
  /* Реалистичная отрисовка: пряжа со скруткой (контур + основа + блики-пряди), деревянные спицы с заострёнными
     кончиками и бликом, металл с градиентом и бликом, глянцевые бусины, пальцы, плоскогубцы с ручками. */
  const n1 = (v) => Math.round(v * 10) / 10;
  // нить: тень, основа, светлые пряди «скрутки», тонкий блик
  const yarnPath = (d, col, w, cls) => {
    const c = col || "#C98BB0";
    return '<g class="cfy' + (cls ? " " + cls : "") + '"><path d="' + d + '" fill="none" stroke="rgba(40,20,40,.35)" stroke-width="' + (w + 2.2) + '" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path d="' + d + '" fill="none" stroke="' + c + '" stroke-width="' + w + '" stroke-linecap="round" stroke-linejoin="round"/>' +
      '<path d="' + d + '" fill="none" stroke="rgba(255,255,255,.38)" stroke-width="' + Math.max(1, w - 2) + '" stroke-dasharray="1.6 2.4" stroke-linecap="butt"/>' +
      '<path d="' + d + '" fill="none" stroke="rgba(255,255,255,.35)" stroke-width="0.8" transform="translate(-0.6 -0.8)"/></g>';
  };
  const YC = { base: "#C98BB0", on: "#9B7FD4", work: "#9B7FD4", old: "#C98BB0", faint: "#D9C7D3" };
  // спица: заострённый кончик во второй точке, блик вдоль, бусина-ограничитель в первой
  const needleSvg = (x1, y1, x2, y2) => {
    const dx = x2 - x1, dy = y2 - y1, L = Math.hypot(dx, dy) || 1, ux = dx / L, uy = dy / L, nx = -uy, ny = ux, r = 3.4, tip = 11;
    const bx = x2 - ux * tip, by = y2 - uy * tip;
    const poly = [x1 + nx * r, y1 + ny * r, bx + nx * r, by + ny * r, x2, y2, bx - nx * r, by - ny * r, x1 - nx * r, y1 - ny * r].map(n1).join(" ");
    return '<g class="cf-needle3"><polygon points="' + poly + '" fill="url(#cfWood)" stroke="#8C6A3A" stroke-width=".8" stroke-linejoin="round"/>' +
      '<line x1="' + n1(x1 + nx * 1.4) + '" y1="' + n1(y1 + ny * 1.4) + '" x2="' + n1(bx + nx * 1.4) + '" y2="' + n1(by + ny * 1.4) + '" stroke="rgba(255,255,255,.55)" stroke-width="1.1" stroke-linecap="round"/>' +
      '<circle cx="' + n1(x1) + '" cy="' + n1(y1) + '" r="5" fill="url(#cfWood)" stroke="#8C6A3A" stroke-width=".8"/></g>';
  };
  // металл: тёмный контур, золотая основа, светлый блик
  const metal = (d, w, extra) => '<g' + (extra || "") + '><path d="' + d + '" fill="none" stroke="#8A6A1E" stroke-width="' + (w + 1.4) + '" stroke-linecap="round" stroke-linejoin="round"/><path d="' + d + '" fill="none" stroke="#D9B44A" stroke-width="' + w + '" stroke-linecap="round" stroke-linejoin="round"/><path d="' + d + '" fill="none" stroke="#FFF2B8" stroke-width="' + Math.max(.6, w / 3) + '" stroke-linecap="round" stroke-linejoin="round" transform="translate(-.5 -.6)" opacity=".9"/></g>';
  const circ = (cx, cy, r) => "M" + n1(cx - r) + " " + n1(cy) + " a" + r + " " + r + " 0 1 0 " + 2 * r + " 0 a" + r + " " + r + " 0 1 0 " + -2 * r + " 0";
  const PR = {
    needle: (a) => needleSvg(a[1], a[2], a[3], a[4]),
    // петля на спице: дуга-«подкова» из пряжи, ножки уходят вниз в полотно
    loop: (a) => { const [cx, cy, rx, ry, cls] = [a[1], a[2], a[3] || 5, a[4] || 9, a[5] || ""]; const d = "M" + n1(cx - rx) + " " + n1(cy + ry) + " C" + n1(cx - rx * 1.15) + " " + n1(cy - ry * 0.4) + " " + n1(cx - rx * 0.9) + " " + n1(cy - ry * 1.05) + " " + cx + " " + n1(cy - ry * 1.05) + " C" + n1(cx + rx * 0.9) + " " + n1(cy - ry * 1.05) + " " + n1(cx + rx * 1.15) + " " + n1(cy - ry * 0.4) + " " + n1(cx + rx) + " " + n1(cy + ry); return yarnPath(d, YC[cls] || YC.base, 3.4, cls); },
    yarn: (a) => yarnPath(a[1], YC[a[2] || "base"] || YC.base, a[2] === "faint" ? 2.4 : 3.4, a[2]),
    arrow: (a) => '<path d="' + a[1] + '" class="cf-arrow" marker-end="url(#cfAr)"/>',
    hook: (a) => { const [x, y, ang] = [a[1], a[2], a[3] || 0]; return '<g transform="translate(' + x + " " + y + ") rotate(" + ang + ')"><rect x="-92" y="-3.2" width="86" height="6.4" rx="3.2" fill="url(#cfSteel)" stroke="#5E6B78" stroke-width=".8"/><rect x="-62" y="-4.6" width="16" height="9.2" rx="2" fill="#9B7FD4" stroke="#6B57A0" stroke-width=".8"/><path d="M-6 -3 L2 -2 Q6 -1 6 -5 Q6 -9 1 -8.5" fill="none" stroke="#5E6B78" stroke-width="3.6" stroke-linecap="round"/><path d="M-6 -3 L2 -2 Q6 -1 6 -5 Q6 -9 1 -8.5" fill="none" stroke="#C9D3DD" stroke-width="1.6" stroke-linecap="round"/><line x1="-88" y1="-1.5" x2="-8" y2="-1.5" stroke="rgba(255,255,255,.6)" stroke-width="1"/></g>'; },
    bead: (a) => { const [x, y, r, c] = [a[1], a[2], a[3] || 7, a[4] || "#B3A4E6"]; return '<g><circle cx="' + x + '" cy="' + (y + r * .25) + '" r="' + r + '" fill="rgba(0,0,0,.18)"/><circle cx="' + x + '" cy="' + y + '" r="' + r + '" fill="' + c + '"/><circle cx="' + x + '" cy="' + y + '" r="' + r + '" fill="url(#cfShade)"/><circle cx="' + x + '" cy="' + y + '" r="' + r + '" fill="url(#cfShine)"/></g>'; },
    drop: (a) => { const [x, y, r, c] = [a[1], a[2], a[3] || 8, a[4] || "#E7B7C8"]; const d = "M" + x + " " + n1(y - r * 1.6) + " C" + n1(x + r) + " " + n1(y - r * 0.4) + " " + n1(x + r) + " " + n1(y + r) + " " + x + " " + n1(y + r) + " C" + n1(x - r) + " " + n1(y + r) + " " + n1(x - r) + " " + n1(y - r * 0.4) + " " + x + " " + n1(y - r * 1.6) + "Z"; return '<g><path d="' + d + '" fill="' + c + '"/><path d="' + d + '" fill="url(#cfShade)"/><path d="' + d + '" fill="url(#cfShine)"/></g>'; },
    pin: (a) => metal("M" + a[1] + " " + a[2] + " L" + a[1] + " " + (a[2] + a[3]), 1.6) + '<ellipse cx="' + a[1] + '" cy="' + (a[2] + a[3]) + '" rx="3.6" ry="1.6" fill="#D9B44A" stroke="#8A6A1E" stroke-width=".7"/>',
    wire: (a) => a[2] === "cable" ? '<g><path d="' + a[1] + '" fill="none" stroke="#5E6B78" stroke-width="2.6" stroke-linecap="round"/><path d="' + a[1] + '" fill="none" stroke="#B9C3CC" stroke-width="1.4" stroke-linecap="round"/><path d="' + a[1] + '" fill="none" stroke="#fff" stroke-width=".5" stroke-dasharray="2 2" opacity=".7"/></g>' : metal(a[1], 1.8),
    ring: (a) => { const [cx, cy, r, gap] = [a[1], a[2], a[3] || 8, a[4] || 0]; if (!gap) return metal(circ(cx, cy, r), r > 20 ? 1.8 : 2.2); const g = gap * Math.PI / 180, x1 = cx + r * Math.sin(g / 2), y1 = cy - r * Math.cos(g / 2), x2 = cx - r * Math.sin(g / 2); return metal("M" + n1(x1) + " " + n1(y1) + " A" + r + " " + r + " 0 1 1 " + n1(x2) + " " + n1(y1), 2.2); },
    // цепочка: звенья чередуются «плашмя» и «ребром»
    chain: (a) => { const [x, y, n, dx, dy] = [a[1], a[2], a[3], a[4] || 0, a[5] || 7]; let h = ""; for (let i = 0; i < n; i++) { const X = x + dx * i, Y = y + dy * i; h += i % 2 ? metal("M" + X + " " + n1(Y - 4) + " L" + X + " " + n1(Y + 4), 1.6) : metal("M" + n1(X - 2.8) + " " + Y + " a2.8 4.2 0 1 0 5.6 0 a2.8 4.2 0 1 0 -5.6 0", 1.3); } return h; },
    // швенза-«рыболовный крючок»: дуга, бусинка, спиралька и петелька
    hookear: (a) => { const [x, y] = [a[1], a[2]]; return metal("M" + x + " " + (y + 6) + " C" + (x - 1) + " " + (y - 10) + " " + (x - 18) + " " + (y - 14) + " " + (x - 18) + " " + (y - 2) + " C" + (x - 18) + " " + (y + 6) + " " + (x - 13) + " " + (y + 12) + " " + (x - 12) + " " + (y + 20), 1.6) + metal("M" + (x - 3) + " " + (y + 3) + " l6 0 M" + (x - 3) + " " + (y + 5) + " l6 0", 1) + '<circle cx="' + x + '" cy="' + (y - 4) + '" r="2" fill="#D9B44A" stroke="#8A6A1E" stroke-width=".6"/>' + metal(circ(x, y + 9, 3), 1.5); },
    // плоскогубцы, круглогубцы, кусачки: стальные губки, шарнир, цветные ручки
    tool: (a) => { const [x, y, kind, ang] = [a[1], a[2], a[3], a[4] || 0];
      const jaw = kind === "round" ? '<path d="M0 -3.5 L20 -2 Q22 -1.6 22 -1 L22 0 L0 -0.5 Z M0 3.5 L20 2 Q22 1.6 22 1 L22 0 L0 0.5 Z" fill="url(#cfSteel)" stroke="#4E5964" stroke-width=".7"/>'
        : kind === "cut" ? '<path d="M0 -4 L14 -3 L19 0 L0 -0.4 Z M0 4 L14 3 L19 0 L0 0.4 Z" fill="url(#cfSteel)" stroke="#4E5964" stroke-width=".7"/>'
        : '<path d="M0 -4 L20 -3 L21 -0.4 L0 -0.4 Z M0 4 L20 3 L21 0.4 L0 0.4 Z" fill="url(#cfSteel)" stroke="#4E5964" stroke-width=".7"/>';
      return '<g transform="translate(' + x + " " + y + ") rotate(" + ang + ')"><path d="M0 -3 C-10 -4 -18 -8 -24 -11 M0 3 C-10 4 -18 8 -24 11" fill="none" stroke="#7B8794" stroke-width="3.4" stroke-linecap="round"/><path d="M-22 -10 C-32 -14 -42 -17 -52 -18 M-22 10 C-32 14 -42 17 -52 18" fill="none" stroke="#D27A8E" stroke-width="7" stroke-linecap="round"/><path d="M-24 -11.5 C-33 -15 -42 -17.5 -50 -19" fill="none" stroke="rgba(255,255,255,.4)" stroke-width="1.6" stroke-linecap="round"/>' + jaw + '<circle cx="0" cy="0" r="3.2" fill="url(#cfSteel)" stroke="#4E5964" stroke-width=".8"/></g>'; },
    tassel: (a) => { const [x, y, len, c] = [a[1], a[2], a[3] || 36, a[4] || "#D27A8E"]; let h = ""; for (let i = -7; i <= 7; i++) { const sh = i % 3 === 0 ? "rgba(255,255,255,.25)" : i % 2 ? "rgba(0,0,0,.12)" : "rgba(0,0,0,0)"; h += '<path d="M' + n1(x + i * 0.7) + " " + (y + 8) + " Q" + n1(x + i * 1.2) + " " + n1(y + 8 + len * .5) + " " + n1(x + i * 1.7) + " " + n1(y + 8 + len) + '" fill="none" stroke="' + c + '" stroke-width="1.6" stroke-linecap="round"/><path d="M' + n1(x + i * 0.7) + " " + (y + 8) + " Q" + n1(x + i * 1.2) + " " + n1(y + 8 + len * .5) + " " + n1(x + i * 1.7) + " " + n1(y + 8 + len) + '" fill="none" stroke="' + sh + '" stroke-width="1.6"/>'; } return h + '<rect x="' + (x - 7) + '" y="' + y + '" width="14" height="9" rx="2.5" fill="url(#cfGold)" stroke="#8A6A1E" stroke-width=".7"/><rect x="' + (x - 5) + '" y="' + (y + 1.5) + '" width="10" height="1.4" rx=".7" fill="#FFF2B8" opacity=".8"/>'; },
    hoop: (a) => metal(circ(a[1], a[2], a[3]), 1.8),
    stud: (a) => metal("M" + a[1] + " " + a[2] + " L" + (a[1] + 16) + " " + a[2], 1.4) + '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="' + ((a[3] || 6) + 1.5) + '" fill="url(#cfGold)" stroke="#8A6A1E" stroke-width=".7"/>' + PR.bead(["", a[1], a[2], a[3] || 6, a[4] || "#A4D3C5"]),
    crimp: (a) => '<rect x="' + (a[1] - 4.5) + '" y="' + (a[2] - 3.5) + '" width="9" height="7" rx="2" fill="url(#cfSteel)" stroke="#4E5964" stroke-width=".7"/><rect x="' + (a[1] - 3) + '" y="' + (a[2] - 2.6) + '" width="6" height="1.2" rx=".6" fill="#fff" opacity=".7"/>',
    // палец (большой / указательный) — для набора петель
    finger: (a) => { const [x, y, ang, len, lbl] = [a[1], a[2], a[3] || 0, a[4] || 34, a[5]]; return '<g transform="translate(' + x + " " + y + ") rotate(" + ang + ')"><path d="M-7 0 L-7 ' + -len + " Q-7 " + (-len - 9) + " 0 " + (-len - 9) + " Q7 " + (-len - 9) + " 7 " + -len + ' L7 0 Z" fill="#F2C9A8" stroke="#C99673" stroke-width="1"/><path d="M-3.6 ' + (-len - 4) + " Q0 " + (-len - 7.6) + " 3.6 " + (-len - 4) + ' Q0 ' + (-len - 1) + ' -3.6 ' + (-len - 4) + 'Z" fill="#F8E2D2" stroke="#D9AE8E" stroke-width=".6"/><path d="M-5 ' + (-len * .45) + " Q0 " + (-len * .45 - 2) + " 5 " + (-len * .45) + '" fill="none" stroke="#D9AE8E" stroke-width=".8"/></g>' + (lbl ? "" : ""); },
    hand: (a) => '<path d="M' + a[1] + " " + a[2] + ' c-10 -2 -20 4 -26 12 c-2 3 1 6 4 4 l10 -6 M' + a[1] + " " + a[2] + ' c-12 4 -18 12 -22 20" class="cf-hand"/>',
    text: (a) => { const w = String(a[3]).length * 4.7, x = a[4] ? a[1] : Math.max(2, Math.min(a[1], 198 - w)); return '<text x="' + x + '" y="' + a[2] + '" class="cf-txt"' + (a[4] ? ' text-anchor="' + a[4] + '"' : "") + ">" + esc(a[3]) + "</text>"; },
    num: (a) => '<circle cx="' + a[1] + '" cy="' + a[2] + '" r="7" class="cf-numc"/><text x="' + a[1] + '" y="' + (a[2] + 3.5) + '" class="cf-numt" text-anchor="middle">' + a[3] + "</text>"
  };
  const DEFS = '<defs><marker id="cfAr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10z" class="cf-arhead"/></marker>' +
    '<linearGradient id="cfWood" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F0D9A8"/><stop offset=".5" stop-color="#D3A866"/><stop offset="1" stop-color="#A97C3E"/></linearGradient>' +
    '<linearGradient id="cfSteel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#EEF2F5"/><stop offset=".5" stop-color="#AEB8C2"/><stop offset="1" stop-color="#6F7B87"/></linearGradient>' +
    '<linearGradient id="cfGold" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF0B0"/><stop offset=".5" stop-color="#D9B44A"/><stop offset="1" stop-color="#9C7A22"/></linearGradient>' +
    '<radialGradient id="cfShine" cx=".32" cy=".3" r=".45"><stop offset="0" stop-color="#fff" stop-opacity=".85"/><stop offset=".35" stop-color="#fff" stop-opacity=".25"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>' +
    '<radialGradient id="cfShade" cx=".45" cy=".4" r=".62"><stop offset=".6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".3"/></radialGradient>' +
    '<linearGradient id="cfBg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" class="cf-bg1"/><stop offset="1" class="cf-bg2"/></linearGradient></defs>';
  // кадр: фон и мягкая «столешница», затем примитивы
  const frame = (f, w) => '<svg viewBox="0 0 200 120" class="cf-svg" style="max-width:' + (w || 360) + 'px" role="img">' + DEFS + '<rect x="0" y="0" width="200" height="120" fill="url(#cfBg)"/>' + f.map((a) => (PR[a[0]] ? PR[a[0]](a) : "")).join("") + "</svg>";
  A.craftFrame = frame;

  /* ---------- узоры спицами: вид полотна и схема ---------- */
  // Символы ряда схемы: k лицевая, p изнаночная, o накид, / 2 вместе лицевой (наклон вправо), \ протяжка (наклон влево),
  // < и > — начало перекрёста 2/2 влево и вправо (занимает 4 клетки), . — не учитывается.
  const C = 13;
  // вид полотна: каждая лицевая — две «ножки» пряжи (V), изнаночная — поперечный бугорок; с объёмом и бликами
  const leg = (x1, y1, x2, y2, c) => '<path d="M' + n1(x1) + " " + n1(y1) + " Q" + n1((x1 + x2) / 2 + (x2 > x1 ? -1.5 : 1.5)) + " " + n1((y1 + y2) / 2) + " " + n1(x2) + " " + n1(y2) + '" stroke="' + c + '" stroke-width="5.2" stroke-linecap="round" fill="none"/><path d="M' + n1(x1) + " " + n1(y1) + " Q" + n1((x1 + x2) / 2 + (x2 > x1 ? -1.5 : 1.5)) + " " + n1((y1 + y2) / 2) + " " + n1(x2) + " " + n1(y2) + '" stroke="rgba(255,255,255,.35)" stroke-width="1.6" stroke-dasharray="1.4 1.8" fill="none"/>';
  const fabric = (rows, opt = {}) => {
    const W = rows[0].length, R = []; for (let r = 0; r < (opt.rep || 1); r++) rows.forEach((x) => R.push(x));
    const WR = opt.wrep || 1, base = "#C98BB0", dark = "#A86C93", deep = "#8A5579";
    let h = '<svg viewBox="0 0 ' + W * C * WR + " " + R.length * C + '" class="cf-fab" role="img"><rect width="100%" height="100%" fill="#7E4E6E"/>';
    R.slice().reverse().forEach((row, yi) => {
      const y = (R.length - 1 - yi) * C;
      for (let wr = 0; wr < WR; wr++) for (let x = 0; x < W; x++) {
        const ch = row[x], X = (wr * W + x) * C;
        if (ch === "k") h += leg(X + 2, y + 1, X + C / 2, y + C + 1, base) + leg(X + C - 2, y + 1, X + C / 2, y + C + 1, dark);
        else if (ch === "p") h += '<path d="M' + X + " " + (y + C * .62) + " Q" + (X + C / 2) + " " + (y + C * .05) + " " + (X + C) + " " + (y + C * .62) + '" stroke="' + dark + '" stroke-width="6" fill="none" stroke-linecap="round"/><path d="M' + (X + 2) + " " + (y + C * .5) + " Q" + (X + C / 2) + " " + (y + C * .12) + " " + (X + C - 2) + " " + (y + C * .5) + '" stroke="rgba(255,255,255,.3)" stroke-width="1.6" stroke-dasharray="1.4 1.8" fill="none"/>';
        else if (ch === "o") h += '<ellipse cx="' + (X + C / 2) + '" cy="' + (y + C / 2) + '" rx="4" ry="4.6" fill="#3E2436"/><path d="M' + (X + 2) + " " + (y + 3) + " Q" + (X + C / 2) + " " + (y - 1) + " " + (X + C - 2) + " " + (y + 3) + '" stroke="' + base + '" stroke-width="3" fill="none"/>';
        else if (ch === "/") h += leg(X + 1, y + 1, X + C - 2, y + C + 1, deep) + leg(X + C - 1, y + 2, X + C / 2 + 2, y + C, base);
        else if (ch === "\\") h += leg(X + C - 1, y + 1, X + 2, y + C + 1, deep) + leg(X + 1, y + 2, X + C / 2 - 2, y + C, base);
        else if (ch === "<" || ch === ">") {
          const x1 = X, x2 = X + 4 * C, front = ch === "<";
          const back = '<path d="M' + (front ? x2 - C : x1 + C) + " " + (y + C) + " C" + (front ? x2 - C : x1 + C) + " " + (y + C * .4) + " " + (front ? x1 + C : x2 - C) + " " + (y + C * .6) + " " + (front ? x1 + C : x2 - C) + " " + (y - C * 0.2) + '" stroke="' + deep + '" stroke-width="10" fill="none" stroke-linecap="round"/>';
          const fr = '<path d="M' + (front ? x1 + C : x2 - C) + " " + (y + C) + " C" + (front ? x1 + C : x2 - C) + " " + (y + C * .4) + " " + (front ? x2 - C : x1 + C) + " " + (y + C * .6) + " " + (front ? x2 - C : x1 + C) + " " + (y - C * 0.2) + '" stroke="' + base + '" stroke-width="11" fill="none" stroke-linecap="round"/>';
          h += back + fr;
        }
      }
    });
    // столбики кос, проходящие через ряды без перекрёста, рисуем поверх как лицевые
    return h + "</svg>";
  };
  const SYM = { k: "□", p: "—", o: "○", "/": "╱", "\\": "╲", "<": "⟨⟩", ">": "⟨⟩" };
  const chart = (rows) => {
    const W = rows[0].length;
    let h = '<svg viewBox="0 0 ' + (W * C + 22) + " " + (rows.length * C + 2) + '" class="cf-chart" role="img">';
    rows.slice().reverse().forEach((row, i) => {
      const y = i * C + 1, rn = rows.length - i;
      for (let x = 0; x < W; x++) {
        const ch = row[x], X = x * C;
        h += '<rect x="' + X + '" y="' + y + '" width="' + C + '" height="' + C + '" class="cf-cell"/>';
        if (ch === "p") h += '<line x1="' + (X + 3) + '" y1="' + (y + C / 2) + '" x2="' + (X + C - 3) + '" y2="' + (y + C / 2) + '" class="cf-sym"/>';
        else if (ch === "o") h += '<circle cx="' + (X + C / 2) + '" cy="' + (y + C / 2) + '" r="3.5" class="cf-sym"/>';
        else if (ch === "/") h += '<line x1="' + (X + 3) + '" y1="' + (y + C - 3) + '" x2="' + (X + C - 3) + '" y2="' + (y + 3) + '" class="cf-sym"/>';
        else if (ch === "\\") h += '<line x1="' + (X + 3) + '" y1="' + (y + 3) + '" x2="' + (X + C - 3) + '" y2="' + (y + C - 3) + '" class="cf-sym"/>';
        else if (ch === "<" || ch === ">") h += '<path d="M' + (X + 2) + " " + (y + C - 2) + " L" + (X + 4 * C - 2) + " " + (y + 2) + " M" + (X + 2) + " " + (y + 2) + " L" + (X + 4 * C - 2) + " " + (y + C - 2) + '" class="cf-sym' + (ch === "<" ? " l" : " r") + '"/>';
      }
      h += '<text x="' + (W * C + 4) + '" y="' + (y + C - 3) + '" class="cf-rn">' + rn + "</text>";
    });
    return h + "</svg>";
  };
  A.craftFabric = fabric;

  /* ---------- экраны ---------- */
  const TABS = [["knit", "Спицы"], ["crochet", "Крючок"], ["jewel", "Украшения"]];
  const diff = (n) => "●".repeat(n) + "○".repeat(3 - n);
  const stepsHtml = (steps) => steps.map((s, i) => '<div class="cf-step"><div class="cf-sn">' + (i + 1) + "</div><div>" + (s.f ? frame(s.f) : "") + "<p>" + esc(s.t) + "</p>" + (s.tip ? '<p class="small muted">' + esc(s.tip) + "</p>" : "") + "</div></div>").join("");
  const patternCard = (p) => '<div class="item" data-a="pat" data-id="' + p.id + '"><div class="ic" style="width:64px;height:auto;background:none">' + fabric(p.rows, { rep: 1, wrep: p.rows[0].length < 6 ? 2 : 1 }) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>';

  A.view("crafts", {
    title: (r) => (r.args[1] && window.CRAFTS ? ((K().items || {})[r.args[1]] || {}).t || "Рукоделие" : "Рукоделие"), tab: "more",
    render(el, r) {
      if (!window.CRAFTS) { A.need("js/content-crafts.js").then(() => A.refresh()); return '<div class="card"><p class="muted">Загружаю…</p></div>'; }
      const sec = r.args[0] || "knit", id = r.args[1], X = K();
      if (id && X.items[id]) return itemHtml(X.items[id]);
      let h = A.seg(TABS, sec, "cSec");
      if (sec === "knit") {
        h += '<div class="card tint"><p class="small">' + esc(X.knitIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Приёмы по шагам</h3><div class="list">' + X.knitTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:layout-grid}} Узоры: как выглядит и как вязать</h3><div class="list">' + X.knitPat.map(patternCard).join("") + "</div></div>";
        h += '<div class="card"><h3>Как читать схемы</h3><p class="small">' + esc(X.chartHow) + '</p><p class="small">□ лицевая · — изнаночная · ○ накид · ╱ две вместе лицевой (наклон вправо) · ╲ протяжка (наклон влево) · ✕ на 4 клетки — коса</p></div>';
        h += '<div class="card"><h3>Словарь вязальщицы</h3>' + X.glossary.map((g) => '<p class="small"><b>' + esc(g[0]) + "</b> — " + esc(g[1]) + "</p>").join("") + "</div>";
      } else if (sec === "crochet") {
        h += '<div class="card tint"><p class="small">' + esc(X.crochetIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Основные петли</h3><div class="list">' + X.crochetTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>Условные обозначения</h3>' + X.crochetSym.map((s) => '<p class="small"><b style="display:inline-block;min-width:26px;font-size:16px">' + esc(s[0]) + "</b> " + esc(s[1]) + "</p>").join("") + "</div>";
      } else {
        h += '<div class="card tint"><p class="small">' + esc(X.jewelIntro) + "</p></div>";
        h += '<div class="card"><h3>{{i:sparkles}} Серьги</h3><div class="list">' + X.jewelProj.filter((p) => p.kind === "серьги").map((p) => '<div class="item" data-a="it" data-id="' + p.id + '"><div class="ic" style="width:56px;height:auto;background:none">' + frame(p.pic, 56) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + p.time + " мин · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:gem}} Браслеты и колье</h3><div class="list">' + X.jewelProj.filter((p) => p.kind !== "серьги").map((p) => '<div class="item" data-a="it" data-id="' + p.id + '"><div class="ic" style="width:56px;height:auto;background:none">' + frame(p.pic, 56) + '</div><div class="tx"><b>' + esc(p.t) + "</b><small>" + esc(p.short) + " · " + p.time + " мин · " + diff(p.lvl) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>{{i:graduation-cap}} Приёмы сборки</h3><div class="list">' + X.jewelTech.map((t) => '<div class="item" data-a="it" data-id="' + t.id + '"><div class="tx"><b>' + esc(t.t) + "</b><small>" + esc(t.short) + '</small></div><span class="muted">›</span></div>').join("") + "</div></div>";
        h += '<div class="card"><h3>Инструменты</h3>' + X.tools.map((t) => '<div class="row" style="gap:10px;align-items:center;margin:6px 0">' + frame(t.f, 90) + '<p class="small grow" style="margin:0"><b>' + esc(t.t) + ".</b> " + esc(t.d) + "</p></div>").join("") + "</div>";
        h += '<div class="card"><h3>Фурнитура</h3>' + X.findings.map((g) => '<p class="small"><b>' + esc(g[0]) + "</b> — " + esc(g[1]) + "</p>").join("") + "</div>";
      }
      return h;
    },
    bind(el, r) {
      if (!window.CRAFTS) return;
      A.bind(el, {
        cSec(b) { A.go("crafts/" + b.dataset.v, true); },
        it(b) { A.go("crafts/" + (r.args[0] || "knit") + "/" + b.dataset.id); },
        pat(b) { A.go("crafts/knit/" + b.dataset.id); },
        async cAsk(b) {
          const it = K().items[r.args[1]], q = el.querySelector("#cQ").value.trim(), out = el.querySelector("#cOut");
          if (!q) return A.toast("Напишите вопрос");
          if (!A.ai.ready()) { out.innerHTML = A.ai.need(); return; }
          b.disabled = true; out.innerHTML = '<p class="muted">Думаю…</p>';
          const ctx = it.t + ". " + (it.short || "") + "\n" + (it.steps || []).map((s, i) => i + 1 + ". " + s.t).join("\n") + (it.rowsText ? "\nРяды: " + it.rowsText.join(" ") : "");
          try { out.innerHTML = A.ai.fmt(await A.ai.ask("Инструкция из приложения:\n" + ctx + "\n\nВопрос рукодельницы: " + q + "\nОтветь по-русски понятно и пошагово, как опытная мастерица; если вопрос про ошибку — как её найти и исправить.", { system: "Ты — опытная вязальщица и мастер украшений, объясняешь терпеливо и очень конкретно.", max: 1800 })); }
          catch (e) { out.innerHTML = A.ai.errHtml(e); }
          b.disabled = false;
        }
      });
    }
  });

  const itemHtml = (it) => {
    let h = "";
    if (it.rows) {
      h += '<div class="card"><h3>Как выглядит</h3><div class="cf-fabwrap">' + fabric(it.rows, { rep: it.rep || 2, wrep: it.wrep || 2 }) + '</div><p class="small muted">Раппорт — ' + it.rows[0].length + " " + A.plural(it.rows[0].length, "петля", "петли", "петель") + " × " + it.rows.length + " " + A.plural(it.rows.length, "ряд", "ряда", "рядов") + (it.plus ? " " + esc(it.plus) : "") + "</p></div>";
      h += '<div class="card"><h3>Схема</h3><div class="cf-fabwrap">' + chart(it.rows) + '</div><p class="small muted">Схема читается снизу вверх; нечётные (лицевые) ряды — справа налево, чётные (изнаночные) — слева направо. Номер ряда — справа.</p></div>';
    }
    h += '<div class="card"><h3>' + esc(it.t) + "</h3>" + (it.about ? '<p class="small">' + esc(it.about) + "</p>" : "") +
      (it.need ? '<p class="small"><b>Понадобится:</b> ' + it.need.map(esc).join(", ") + "</p>" : "") +
      (it.time ? '<p class="small muted">Время: ~' + it.time + " мин · сложность " + diff(it.lvl || 1) + "</p>" : "") + "</div>";
    if (it.rowsText) h += '<div class="card"><h3>Вязать так</h3><p class="small"><b>Набор:</b> ' + esc(it.cast) + "</p>" + it.rowsText.map((t) => '<p class="small">' + esc(t) + "</p>").join("") + "</div>";
    if (it.steps) h += '<div class="card"><h3>По шагам</h3>' + stepsHtml(it.steps) + "</div>";
    if (it.mistakes) h += '<div class="card"><h3>Частые ошибки</h3>' + it.mistakes.map((m) => '<p class="small"><b>' + esc(m[0]) + ".</b> " + esc(m[1]) + "</p>").join("") + "</div>";
    if (it.uses) h += '<div class="card tint"><p class="small"><b>Где использовать:</b> ' + esc(it.uses) + "</p></div>";
    h += '<div class="card"><h3>{{i:sparkles}} Спросить мастерицу (ИИ)</h3><textarea id="cQ" rows="2" placeholder="Например: у меня петли перекручиваются — что не так?"></textarea><button class="btn block" data-a="cAsk" style="margin-top:8px">Спросить</button><div id="cOut" class="ai-out" style="margin-top:8px"></div></div>';
    return h;
  };
})();
