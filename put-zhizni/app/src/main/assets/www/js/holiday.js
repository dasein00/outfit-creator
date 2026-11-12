/* Праздничные темы — Новый год (15.12–15.01) и Хэллоуин (15.10–02.11), по образцу DASEIN.
   Всё рисуется на canvas поверх или под интерфейсом (pointer-events: none — нажатия проходят сквозь).
   Один таймер requestAnimationFrame (~30 кадров/с); останавливается в фоне, в энергосбережении,
   в облегчённой графике и при «уменьшить движение» — тогда украшения остаются неподвижным рисунком.
   Слои:
     #holBack  — фон под карточками (бархат, кайма, искорки, снег; ночь, луна, кладбище, мыши, пауки, ведьма);
     #holMid   — над карточками, но под шапкой и нижней панелью (шапки карточек, сани / привидение);
     #holTop   — гирлянда вверху экрана (Новый год);
     #holFall  — снегопад / листопад при входе (создаётся на время показа). */
(function () {
  "use strict";
  const A = window.App, S = window.HolidaySeason;
  const PI = Math.PI, sin = Math.sin, cos = Math.cos, abs = Math.abs, min = Math.min, max = Math.max, floor = Math.floor;
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
  const smooth = (u) => u * u * (3 - 2 * u);

  /* ---------- настройки ---------- */
  const DEF = {
    ny: { auto: true, preview: false, snow: true, backdrop: true, border: true, caps: true, garland: true, lights: true, lightsMode: 0, santa: true, bg: 0, card: 0 },
    hw: { auto: true, preview: false, fog: true, bats: true, eyes: true, hand: true, crows: true, webs: true, caps: true, spiders: true, witch: true, ghost: true, leaves: true, bg: 0, card: 0 }
  };
  A.holCfg = () => {
    const p = A.db().profile;
    if (!p.holiday || typeof p.holiday !== "object") p.holiday = {};
    for (const k in DEF) {
      if (!p.holiday[k] || typeof p.holiday[k] !== "object") p.holiday[k] = {};
      for (const f in DEF[k]) if (p.holiday[k][f] === undefined) p.holiday[k][f] = DEF[k][f];
    }
    return p.holiday;
  };

  const NY_BG = [
    { n: "Зелёный бархат", dark: ["#235A41", "#15352A", "#0A1E16"], light: ["#EFF7F2", "#DCEDE3", "#C6E0D0"] },
    { n: "Красный бархат", dark: ["#8C1A2B", "#5E0F1C", "#330710"], light: ["#FBE9E9", "#F3D3D5", "#E6B9BD"] },
    { n: "Синяя ночь", dark: ["#27447A", "#162850", "#0A142B"], light: ["#EDF2FB", "#D9E3F4", "#C3D2EC"] },
    { n: "Шоколад", dark: ["#5E3D29", "#3B2519", "#1F130C"], light: ["#F8EFE6", "#EEDFCF", "#E2CDB7"] }
  ];
  const NY_CARD = [
    { n: "Матовое стекло", dark: "rgba(46,50,60,.55)", light: "rgba(255,255,255,.65)" },
    { n: "Бордовый", dark: "rgba(97,22,38,.65)", light: "rgba(247,221,224,.7)" },
    { n: "Хвойный", dark: "rgba(27,74,51,.65)", light: "rgba(221,239,228,.7)" },
    { n: "Тёмный", dark: "rgba(18,18,18,.7)", light: "rgba(255,255,255,.8)" },
    { n: "Сливочный", dark: "rgba(250,223,175,.25)", light: "rgba(255,246,230,.7)" }
  ];
  const HW_BG = [
    { n: "Лунная ночь", dark: ["#7A4FA8", "#3E2163", "#1A0F2C"], light: ["#F4EDFB", "#E4D6F3", "#CDB8E6"] },
    { n: "Болото", dark: ["#5E4A7E", "#2C2840", "#121A10"], light: ["#F1F4EA", "#E0E7D3", "#C9D5B8"] },
    { n: "Тыквенная", dark: ["#8A4A1E", "#4A2410", "#1F0E06"], light: ["#FDF1E6", "#F6DEC8", "#ECC6A3"] },
    { n: "Полночь", dark: ["#3A2A5A", "#1E1530", "#0A0710"], light: ["#EFEDF6", "#DCD8EA", "#C4BEDB"] }
  ];
  const HW_CARD = [
    { n: "Дымчатая слива", dark: "rgba(42,24,56,.65)", light: "rgba(243,236,250,.7)" },
    { n: "Стекло", dark: "rgba(46,38,56,.55)", light: "rgba(255,255,255,.65)" },
    { n: "Тыква", dark: "rgba(89,42,16,.6)", light: "rgba(251,231,211,.7)" },
    { n: "Болотные", dark: "rgba(28,42,22,.65)", light: "rgba(229,238,220,.7)" },
    { n: "Тёмные", dark: "rgba(16,10,22,.7)", light: "rgba(255,255,255,.8)" }
  ];
  A.HOL_STYLES = { ny: { bg: NY_BG, card: NY_CARD }, hw: { bg: HW_BG, card: HW_CARD } };
  const LIGHT_MODES = ["Переливы", "Бегущие огни", "Мерцание"];
  A.HOL_LIGHT_MODES = LIGHT_MODES;

  /* ---------- случайные числа с зерном (у каждой карточки свои шапки) ---------- */
  const rng = (seed) => {
    let a = (seed | 0) ^ 0x9E3779B9;
    const f = () => { a = (a + 0x6D2B79F5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
    return { f, i: (n) => floor(f() * n), b: () => f() < 0.5 };
  };
  const hash = (s) => { let h = 7; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return h; };

  /* ---------- цвета ---------- */
  const rgb = (hex) => { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
  const rgba = (c, a) => "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + a + ")";
  const mix = (c1, c2, k) => [c1[0] + (c2[0] - c1[0]) * k | 0, c1[1] + (c2[1] - c1[1]) * k | 0, c1[2] + (c2[2] - c1[2]) * k | 0];
  const BULBS = ["#E53935", "#FFC24A", "#43A047", "#42A5F5", "#FFE7B0"].map(rgb);
  const SOCKET = rgb("#3A342C"), GOLD = rgb("#F2C760"), LIME = rgb("#C9F05A"), LILAC = rgb("#F2EEF6"), EYE = rgb("#FF3B3B"), LAV = rgb("#D8C6F0"), GHOST_TRAIL = rgb("#CDB8F0");

  /* ---------- картинки ---------- */
  const NY_FILES = [].concat(
    Array.from({ length: 8 }, (_, i) => "snow_" + i), Array.from({ length: 12 }, (_, i) => "ice_" + i), Array.from({ length: 10 }, (_, i) => "pair_" + i),
    ["border_top", "border_bottom", "sleigh_reins", "sleigh_wave", "deer_a", "deer_b", "deer_c", "deer_a_red", "deer_b_red", "deer_c_red"]);
  const many = (p, n) => Array.from({ length: n }, (_, i) => p + "_" + i);
  const HW_FILES = [].concat(["border_far", "border_near", "border_top", "fog_1", "fog_2"],
    many("bat_hang", 4), many("bat_up", 4), many("bat_fly", 4), many("bat_land", 4), many("hand", 6), many("ghost", 6), many("ghostling", 4),
    many("fall_leaf", 6), many("fall_bat", 3), many("fall_star", 3), many("cap", 8), many("spider_side", 4), many("spider_top", 4), many("spider_peek", 3),
    many("crow_idle", 4), many("crow_caw", 4), many("crow_fly", 4), many("crow_up", 4), many("crow_land", 4), many("witch", 8),
    ...["walk", "peek", "snarl", "scared", "run", "climb", "dive"].map((k) => many("rat_" + k, 4)),
    ...["snarl", "scared", "run", "climb", "dive"].map((k) => many("spi_" + k, 4)));
  const IMG = { ny: {}, hw: {} }, ready = { ny: false, hw: false }, loading = {};
  const loadSet = (th) => {
    if (ready[th] || loading[th]) return loading[th] || Promise.resolve();
    const dir = th === "ny" ? "img/holiday/" : "img/halloween/", files = th === "ny" ? NY_FILES : HW_FILES;
    loading[th] = Promise.all(files.map((f) => new Promise((res) => { const im = new Image(); im.decoding = "async"; im.onload = () => { IMG[th][f] = im; res(); }; im.onerror = () => res(); im.src = dir + f + ".webp"; })))
      .then(() => { ready[th] = true; delete loading[th]; prepare(th); kick(); });
    return loading[th];
  };
  // Удобные списки кадров.
  const N = {}, H = {};
  const list = (o, p, n) => Array.from({ length: n }, (_, i) => o[p + "_" + i]).filter(Boolean);
  function prepare(th) {
    if (th === "ny") {
      const o = IMG.ny;
      N.snow = list(o, "snow", 8); N.ice = list(o, "ice", 12); N.pairs = list(o, "pair", 10);
      N.top = o.border_top; N.bottom = o.border_bottom;
      N.sleigh = [o.sleigh_reins, o.sleigh_wave].filter(Boolean);
      N.deer = [o.deer_a, o.deer_b, o.deer_c].filter(Boolean); N.deerRed = [o.deer_a_red, o.deer_b_red, o.deer_c_red].filter(Boolean);
      N.ok = N.snow.length && N.ice.length;
      N.team = N.sleigh.length === 2 && N.deer.length === 3 && N.deerRed.length === 3;
    } else {
      const o = IMG.hw;
      Object.assign(H, { far: o.border_far, near: o.border_near, top: o.border_top, fog1: o.fog_1, fog2: o.fog_2 });
      ["bat_hang", "bat_up", "bat_fly", "bat_land", "crow_idle", "crow_caw", "crow_fly", "crow_up", "crow_land", "spider_side", "spider_top"].forEach((k) => { H[k] = list(o, k, 4); });
      H.hand = list(o, "hand", 6); H.ghost = list(o, "ghost", 6); H.ghostling = list(o, "ghostling", 4);
      H.leaves = list(o, "fall_leaf", 6); H.fallBats = list(o, "fall_bat", 3); H.stars = list(o, "fall_star", 3);
      H.caps = list(o, "cap", 8); H.peek = list(o, "spider_peek", 3); H.witch = list(o, "witch", 8);
      H.rat = {}; ["walk", "peek", "snarl", "scared", "run", "climb", "dive"].forEach((k) => { const l = list(o, "rat_" + k, 4); if (l.length === 4) H.rat[k] = l; });
      H.spi = {}; ["snarl", "scared", "run", "climb", "dive"].forEach((k) => { const l = list(o, "spi_" + k, 4); if (l.length === 4) H.spi[k] = l; });
      H.ratsOn = Object.keys(H.rat).length === 7 && Object.keys(H.spi).length === 5;
      // Туман окрашивается (как ColorFilter.tint): готовим лиловую и лаймовую копии текстур один раз.
      H.fogTint = {};
      [["fog1", H.fog1], ["fog2", H.fog2]].forEach(([k, im]) => { if (!im) return; H.fogTint[k] = { lav: tint(im, "#D8C6F0"), lime: tint(im, "#C9F05A") }; });
      H.ok = !!(H.near && H.bat_hang.length === 4 && H.bat_fly.length === 4 && H.hand.length === 6 && H.ghost.length === 6 && H.spider_top.length === 4 && H.spider_side.length === 4 && H.peek.length === 3 && H.crow_fly.length === 4 && H.crow_idle.length === 4 && H.witch.length === 8);
    }
  }
  function tint(im, color) {
    const c = document.createElement("canvas"); c.width = im.naturalWidth; c.height = im.naturalHeight;
    const x = c.getContext("2d"); x.drawImage(im, 0, 0); x.globalCompositeOperation = "source-in"; x.fillStyle = color; x.fillRect(0, 0, c.width, c.height);
    return c;
  }
  const iw = (im) => im.naturalWidth || im.width, ih = (im) => im.naturalHeight || im.height;

  /* ---------- рисование: мелкие помощники ---------- */
  let X = null; // текущий контекст
  const img = (b, x, y, w, h, a) => { if (!b) return; if (a != null && a < 1) { X.globalAlpha = a; X.drawImage(b, x, y, max(1, w), max(1, h)); X.globalAlpha = 1; } else X.drawImage(b, x, y, max(1, w), max(1, h)); };
  const mirrorX = (ax, fn) => { X.save(); X.translate(ax, 0); X.scale(-1, 1); X.translate(-ax, 0); fn(); X.restore(); };
  const around = (x, y, sx, sy, rot, fn) => { X.save(); X.translate(x, y); if (rot) X.rotate(rot); if (sx !== 1 || sy !== 1) X.scale(sx, sy); X.translate(-x, -y); fn(); X.restore(); };
  const glow = (c, cx, cy, r, a) => { if (a <= 0.01 || r <= 0) return; const g = X.createRadialGradient(cx, cy, 0, cx, cy, r); g.addColorStop(0, rgba(c, a)); g.addColorStop(1, rgba(c, 0)); X.fillStyle = g; X.beginPath(); X.arc(cx, cy, r, 0, 2 * PI); X.fill(); };
  const dot = (c, a, cx, cy, r) => { X.fillStyle = rgba(c, a); X.beginPath(); X.arc(cx, cy, r, 0, 2 * PI); X.fill(); };
  const line = (color, x1, y1, x2, y2, w) => { X.strokeStyle = color; X.lineWidth = w; X.lineCap = "round"; X.beginPath(); X.moveTo(x1, y1); X.lineTo(x2, y2); X.stroke(); };

  /** Шестиконечная снежинка: три оси через центр, на каждом луче по паре веточек. */
  function snowflake(cx, cy, r, color, alpha, angleDeg) {
    X.save(); X.translate(cx, cy); X.rotate(angleDeg * PI / 180);
    X.strokeStyle = color; X.globalAlpha = alpha; X.lineWidth = max(1, r * 0.16); X.lineCap = "round";
    X.beginPath();
    for (let k = 0; k < 6; k++) {
      const a = k * PI / 3, tx = cos(a) * r, ty = sin(a) * r;
      X.moveTo(0, 0); X.lineTo(tx, ty);
      const bx = cos(a) * r * 0.58, by = sin(a) * r * 0.58;
      for (const s of [-1, 1]) { const ba = a + s * PI / 4; X.moveTo(bx, by); X.lineTo(bx + cos(ba) * r * 0.32, by + sin(ba) * r * 0.32); }
    }
    X.stroke(); X.restore();
  }
  /** Четырёхлучевая искорка с ореолом. */
  function sparkle(cx, cy, r, c, a) {
    glow(c, cx, cy, r * 2.2, 0.35 * a);
    X.fillStyle = rgba(c, a); X.beginPath();
    X.moveTo(cx, cy - r); X.quadraticCurveTo(cx, cy, cx + r, cy); X.quadraticCurveTo(cx, cy, cx, cy + r); X.quadraticCurveTo(cx, cy, cx - r, cy); X.quadraticCurveTo(cx, cy, cx, cy - r);
    X.fill();
  }

  /* ---------- состояние движка ---------- */
  let theme = null, prevTheme = null, dark = false;
  let clock = 0, raf = 0, lastTs = 0, animating = false, paused = false, powerSave = false, stoppedAt = 0;
  const reduced = window.matchMedia ? matchMedia("(prefers-reduced-motion: reduce)") : { matches: false };
  let back = null, mid = null, top = null, fallCv = null, insetTop = 0;
  let cards = [], cardsKey = 0;
  const mainEl = () => document.getElementById("main");

  const mkCanvas = (id, parent, cls) => { let c = document.getElementById(id); if (!c) { c = document.createElement("canvas"); c.id = id; c.className = "hol-cv " + (cls || ""); c.setAttribute("aria-hidden", "true"); parent.appendChild(c); } return c; };
  const dprOf = () => min(window.devicePixelRatio || 1, 2);
  /** Подогнать холст под экран (или заданный размер) и вернуть контекст в CSS-пикселях. */
  function fit(c, w, h) {
    const d = dprOf(), W = Math.round(w * d), Hh = Math.round(h * d);
    if (c.width !== W || c.height !== Hh) { c.width = W; c.height = Hh; c.style.width = w + "px"; c.style.height = h + "px"; }
    const x = c.getContext("2d"); x.setTransform(d, 0, 0, d, 0, 0); x.clearRect(0, 0, w, h); return x;
  }
  function measureInset() {
    const p = document.createElement("div"); p.style.cssText = "position:fixed;top:0;left:0;width:1px;height:env(safe-area-inset-top,0px);visibility:hidden;pointer-events:none";
    document.body.appendChild(p); insetTop = p.offsetHeight || 0; p.remove();
  }

  /* ---------- применение темы ---------- */
  A.holTheme = () => theme;
  A.holApply = (fromTheme) => {
    const cfg = A.holCfg();
    const next = S.activeTheme(cfg, A.today());
    dark = document.documentElement.dataset.theme === "dark";
    const root = document.documentElement;
    theme = next;
    root.classList.toggle("hol", !!theme);
    root.classList.toggle("hol-ny", theme === "ny");
    root.classList.toggle("hol-hw", theme === "hw");
    root.classList.toggle("hol-garland", theme === "ny" && cfg.ny.garland);
    if (!theme) {
      ["--card", "--bg", "--hol-top", "--hol-sheet", "--accent", "--accent2", "--accent-ink"].forEach((v) => root.style.removeProperty(v));
      teardown();
      if (prevTheme && !fromTheme) { prevTheme = null; A.applyTheme(); }
      prevTheme = null;
      return;
    }
    const st = A.HOL_STYLES[theme], c = cfg[theme];
    const bg = st.bg[c.bg] || st.bg[0], card = st.card[c.card] || st.card[0];
    const v = dark ? bg.dark : bg.light;
    root.style.setProperty("--card", dark ? card.dark : card.light);
    root.style.setProperty("--bg", v[1]);
    root.style.setProperty("--hol-top", rgba(rgb(v[1]), 0.55));
    root.style.setProperty("--hol-sheet", theme === "ny" ? (dark ? "#1F2B26" : "#FFFDF9") : (dark ? "#241832" : "#FCF9FF"));
    if (theme === "ny") {
      root.style.setProperty("--accent", dark ? "#F2C760" : "#B8322B"); root.style.setProperty("--accent2", dark ? "rgba(242,199,96,.18)" : "rgba(184,50,43,.12)"); root.style.setProperty("--accent-ink", dark ? "#2A1A08" : "#FFFFFF");
    } else {
      root.style.setProperty("--accent", dark ? "#F5A623" : "#B5520E"); root.style.setProperty("--accent2", dark ? "rgba(245,166,35,.18)" : "rgba(181,82,14,.12)"); root.style.setProperty("--accent-ink", dark ? "#1A0F2C" : "#FFFFFF");
    }
    const meta = document.querySelector('meta[name="theme-color"]'); if (meta) meta.content = v[1];
    const nav = theme === "ny" ? "#5E0F1C" : "#1A0F2C";
    if (A.native) try { if (A.native.setBars2) A.native.setBars2(v[1], !dark, nav, false); else A.native.setBars(v[1], !dark); } catch (e) {}
    const first = prevTheme !== theme;
    prevTheme = theme;
    setup();
    loadSet(theme);
    if (first) { velvetKey = ""; if (!entered) { entered = true; startFall(); } }
    kick();
  };

  let entered = false;
  function setup() {
    measureInset();
    back = mkCanvas("holBack", document.body, "hol-back");
    mid = mkCanvas("holMid", document.getElementById("app"), "hol-mid");
    if (theme === "ny" && A.holCfg().ny.garland) top = mkCanvas("holTop", document.body, "hol-top");
    else if (top) { top.remove(); top = null; }
    refreshCards();
  }
  function teardown() {
    stop();
    [back, mid, top, fallCv].forEach((c) => c && c.remove());
    back = mid = top = fallCv = null; fall = null;
  }

  /* ---------- таймер ---------- */
  const canAnimate = () => !!theme && !paused && !document.hidden && !powerSave && !reduced.matches && !A.lite;
  function frame(ts) {
    raf = requestAnimationFrame(frame);
    const dt = ts - lastTs;
    if (dt < 31) return; // ~30 кадров в секунду
    lastTs = ts;
    clock += min(dt, 100) / 1000;
    draw();
  }
  function start() { if (raf) return; lastTs = performance.now(); raf = requestAnimationFrame(frame); animating = true; }
  function stop() { if (raf) cancelAnimationFrame(raf); raf = 0; animating = false; }
  let pending = 0;
  /** Перерисовать: запустить таймер, если анимации разрешены, иначе один неподвижный кадр. */
  function kick() {
    if (!theme) { drawPreviewOnly(); return; }
    if (canAnimate()) start();
    else { stop(); if (!pending) pending = requestAnimationFrame(() => { pending = 0; if (theme) draw(); }); }
  }

  /* ---------- общий кадр ---------- */
  function draw() {
    if (!theme || !back) return;
    const w = window.innerWidth, h = window.innerHeight;
    const m = mainEl(); if (!m) return;
    const mr = m.getBoundingClientRect();
    try {
      X = fit(back, w, h);
      if (theme === "ny") nyBackdrop(w, mr.bottom); else if (ready.hw && H.ok) halloweenScene(clock, w, mr.bottom); else hwSky(w, mr.bottom);
      X = fit(mid, w, h);
      X.save(); X.beginPath(); X.rect(0, mr.top, w, mr.height); X.clip();
      drawCaps(mr);
      drawFlight(mr, w);
      X.restore();
      if (top) { X = fit(top, w, insetTop + 44); drawGarland(w); }
      if (fall) drawFall(w, h);
      drawPreview();
    } catch (e) { console.error(e); }
    X = null;
  }

  /* ================= НОВЫЙ ГОД ================= */
  let velvetCache = null, velvetKey = "";
  /** Бархат выбранного цвета: светлее сверху по центру, к краям глубже; хвойная кайма сверху и снизу. */
  function velvet(w, bottom) {
    const c = A.holCfg().ny, st = NY_BG[c.bg] || NY_BG[0];
    const key = [w, bottom | 0, c.bg, dark, c.border, ready.ny].join("|");
    if (velvetKey === key && velvetCache) return velvetCache;
    const cv = velvetCache || document.createElement("canvas"), d = dprOf();
    const hh = window.innerHeight;
    cv.width = Math.round(w * d); cv.height = Math.round(hh * d);
    const save = X; X = cv.getContext("2d"); X.setTransform(d, 0, 0, d, 0, 0);
    const v = dark ? st.dark : st.light;
    const g = X.createRadialGradient(w * 0.5, hh * 0.3, 0, w * 0.5, hh * 0.3, max(w, hh) * 0.9);
    g.addColorStop(0, v[0]); g.addColorStop(0.5, v[1]); g.addColorStop(1, v[2]);
    X.fillStyle = g; X.fillRect(0, 0, w, hh);
    const s = X.createLinearGradient(0, 0, 0, hh * 0.35); s.addColorStop(0, "rgba(255,255,255," + (dark ? 0.04 : 0.12) + ")"); s.addColorStop(1, "rgba(255,255,255,0)");
    X.fillStyle = s; X.fillRect(0, 0, w, hh * 0.35);
    if (c.border && ready.ny) {
      if (N.bottom) { const bh = w * ih(N.bottom) / iw(N.bottom); img(N.bottom, 0, bottom - bh, w, bh, 0.9); }
      if (N.top) { const th = w * ih(N.top) / iw(N.top); img(N.top, 0, 0, w, th, 0.75); }
    }
    X = save; velvetCache = cv; velvetKey = key;
    return cv;
  }
  const SPARKLES = (() => { const r = rng(1225); return Array.from({ length: 16 }, (_, i) => { const y = i % 3 === 0 ? 0.3 + r.f() * 0.4 : i % 2 === 0 ? r.f() * 0.22 : 0.78 + r.f() * 0.2; return { x: r.f(), y, r: 3 + r.f() * 4, sp: 0.8 + r.f() * 1.6, ph: r.f() * 6.28 }; }); })();
  const BACK_FLAKES = (() => { const r = rng(2026); return Array.from({ length: 26 }, () => ({ x: r.f(), y0: r.f(), sp: 0.03 + r.f() * 0.045, r: 3.5 + r.f() * 5, sway: 0.01 + r.f() * 0.02, hz: 0.08 + r.f() * 0.15, ph: r.f() * 6.28, a: 0.35 + r.f() * 0.65 })); })();
  const snowTint = () => (dark ? "#FFFFFF" : "#AFC4DA");

  function nyBackdrop(w, bottom) {
    const c = A.holCfg().ny, hh = window.innerHeight;
    const v = velvet(w, bottom);
    X.drawImage(v, 0, 0, w, hh);
    const t = clock;
    if (c.border) SPARKLES.forEach((p) => {
      let a = 0.5 + 0.5 * sin(t * p.sp + p.ph); a *= a;
      if (a > 0.05) sparkle(p.x * w, p.y * bottom, p.r, GOLD, a * (dark ? 0.85 : 0.7));
    });
    if (!c.backdrop) return;
    const tint = snowTint(), strength = dark ? 0.16 : 0.3;
    BACK_FLAKES.forEach((f) => {
      const p = (((f.y0 + f.sp * t) % 1.1) + 1.1) % 1.1 - 0.05;
      const x = (f.x + f.sway * sin(2 * PI * f.hz * t + f.ph)) * w;
      snowflake(x, p * hh, f.r, tint, strength * f.a, f.ph * 57 + t * 8);
    });
  }

  /* --- гирлянда --- */
  let bulbs = [];
  function garlandGeom(w) {
    const y0 = insetTop + 3, sag = 9, swags = 3, per = 8, pts = [];
    const segs = [];
    for (let s = 0; s < swags; s++) {
      const x0 = w * s / swags, x1 = w * (s + 1) / swags, cx = (x0 + x1) / 2, cy = y0 + sag * 2;
      segs.push([x0, cx, cy, x1]);
      for (let i = 0; i < per; i++) {
        const k = (i + 0.5) / per;
        pts.push({ x: (1 - k) * (1 - k) * x0 + 2 * (1 - k) * k * cx + k * k * x1, y: (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * cy + k * k * y0 });
      }
    }
    return { y0, segs, pts };
  }
  /** Цвет и яркость огонька i в момент t: переливы, бегущие огни, мерцание. */
  function bulb(i, mode, t) {
    if (mode === 0) {
      const f = t * 0.6 + i * 0.35, a = floor(f);
      const c = mix(BULBS[((a % 5) + 5) % 5], BULBS[(((a + 1) % 5) + 5) % 5], f - a);
      return [c, 0.2 + 0.8 * (0.5 + 0.5 * sin(t * 3.2 + i * 1.3))];
    }
    if (mode === 1) { const pos = (t * 7) % 24, d = (((i - pos) % 24) + 24) % 24; return [BULBS[i % 5], d < 4 ? 1 : 0.12]; }
    const v = sin(t * (1.3 + ((i * 37) % 11) / 6) + i * 1.7);
    return [BULBS[i % 5], v > 0.2 ? 1 : 0.1];
  }
  function drawGarland(w) {
    const c = A.holCfg().ny, g = garlandGeom(w), on = c.lights, mode = c.lightsMode | 0, t = clock;
    bulbs = g.pts;
    X.strokeStyle = "#2B2620"; X.lineWidth = 1.6; X.lineCap = "round"; X.beginPath(); X.moveTo(0, g.y0);
    g.segs.forEach(([, cx, cy, x1]) => X.quadraticCurveTo(cx, cy, x1, g.y0)); X.stroke();
    const r = 4.5;
    g.pts.forEach((p, i) => {
      const [col, gl] = on ? bulb(i, mode, t) : [rgb("#4A4238"), 0];
      const cx = p.x, cy = p.y + r * 1.6;
      if (gl > 0.05) {
        let gr = X.createRadialGradient(cx, cy, 0, cx, cy, r * 7);
        gr.addColorStop(0, rgba(col, 0.55 * gl)); gr.addColorStop(0.5, rgba(col, 0.18 * gl)); gr.addColorStop(1, rgba(col, 0));
        X.fillStyle = gr; X.beginPath(); X.arc(cx, cy, r * 7, 0, 2 * PI); X.fill();
        gr = X.createRadialGradient(cx, cy, 0, cx, cy, r * 2.8);
        gr.addColorStop(0, "rgba(255,255,255," + 0.5 * gl + ")"); gr.addColorStop(0.5, rgba(col, 0.85 * gl)); gr.addColorStop(1, rgba(col, 0));
        X.fillStyle = gr; X.beginPath(); X.arc(cx, cy, r * 2.8, 0, 2 * PI); X.fill();
      }
      X.fillStyle = rgba(SOCKET, 1); X.fillRect(p.x - r * 0.45, p.y, r * 0.9, r * 0.7);
      const body = on ? mix(col, SOCKET, (1 - gl) * 0.55) : col;
      X.fillStyle = rgba(body, 1); X.beginPath(); X.ellipse(cx, cy + r * 0.05, r * 0.8, r * 1.05, 0, 0, 2 * PI); X.fill();
      X.fillStyle = "rgba(255,255,255," + (on ? 0.4 * gl + 0.08 : 0.08) + ")"; X.beginPath(); X.ellipse(cx - r * 0.2, cy - r * 0.35, r * 0.25, r * 0.35, 0, 0, 2 * PI); X.fill();
    });
    around(0, insetTop, 0.8, 0.8, 0, () => pine(0, insetTop, false)); around(w, insetTop, 0.8, 0.8, 0, () => pine(w, insetTop, true));
  }
  /** Еловая ветка в углу с тремя ягодами. */
  function pine(x0, y0, mirror) {
    const dir = mirror ? -1 : 1;
    [[10, 62, 54], [30, 70, 46], [52, 58, 40], [-8, 50, 34]].forEach(([ang, len, needles], k) => {
      const a = ang * PI / 180, ex = x0 + dir * cos(a) * len, ey = y0 + sin(a) * len;
      line("#1C4A2C", x0, y0, ex, ey, 2.2);
      const n = floor(needles / 6);
      for (let j = 1; j <= n; j++) {
        const f = j / (n + 1), bx = x0 + (ex - x0) * f, by = y0 + (ey - y0) * f, nl = 9 - 4 * f;
        for (const s of [-1, 1]) { const na = a + s * 0.9; line((j + k) % 2 === 0 ? "#2E6B3F" : "#1C4A2C", bx, by, bx + dir * cos(na) * nl, by + sin(na) * nl, 1.4); }
      }
    });
    [[22, 22], [28, 28], [18, 30]].forEach(([ox, oy]) => { const cx = x0 + dir * ox, cy = y0 + oy; X.fillStyle = "#D32F2F"; X.beginPath(); X.arc(cx, cy, 3.2, 0, 2 * PI); X.fill(); dot([255, 255, 255], 0.45, cx - 1, cy - 1, 1); });
  }
  // Нажатие на лампочку — вкл/выкл, удержание — следующий режим. Сам холст нажатий не ловит: слушаем документ
  // и реагируем только рядом с лампочкой и не на кнопке — кнопки шапки нажимаются как обычно.
  let press = null;
  const nearBulb = (x, y) => theme === "ny" && top && bulbs.some((p) => abs(p.x - x) < 13 && abs(p.y + 7 - y) < 13);
  document.addEventListener("pointerdown", (e) => {
    if (!nearBulb(e.clientX, e.clientY) || (e.target.closest && e.target.closest("button,a,input,select,textarea,[data-a],.sheet-wrap,.lock"))) return;
    const p = { x: e.clientX, y: e.clientY, long: false };
    p.timer = setTimeout(() => {
      p.long = true; const c = A.holCfg().ny; c.lightsMode = ((c.lightsMode | 0) + 1) % LIGHT_MODES.length; c.lights = true; A.save();
      A.toast("Гирлянда: " + LIGHT_MODES[c.lightsMode].toLowerCase()); A.vibe(15); kick();
    }, 550);
    press = p;
  }, { passive: true });
  const endPress = (e, cancel) => {
    if (!press) return; clearTimeout(press.timer);
    if (!cancel && !press.long && abs(e.clientX - press.x) < 14 && abs(e.clientY - press.y) < 14) { const c = A.holCfg().ny; c.lights = !c.lights; A.save(); A.vibe(10); kick(); }
    press = null;
  };
  document.addEventListener("pointerup", (e) => endPress(e, false), { passive: true });
  document.addEventListener("pointercancel", (e) => endPress(e, true), { passive: true });

  /* --- шапки карточек --- */
  const MIN_W = 80, MIN_H = 36;
  function refreshCards() {
    const m = mainEl(); if (!m) { cards = []; return; }
    const path = (A.route ? A.route().path : "") || "";
    const h0 = hash(path);
    cards = Array.from(m.querySelectorAll(".card")).filter((c) => !c.parentElement.closest(".card") && !c.closest(".sheet-wrap")).map((el, i) => ({ el, seed: (h0 + i * 7919) | 0 }));
    cardsKey++;
  }
  function drawCaps(mr) {
    const cfg = A.holCfg()[theme];
    if (!cfg.caps) return;
    if (theme === "ny" && !(ready.ny && N.ok)) return;
    if (theme === "hw" && !(ready.hw && H.ok && H.caps.length === 8)) return;
    for (const c of cards) {
      if (!c.el.isConnected) continue;
      const r = c.el.getBoundingClientRect();
      if (r.width < MIN_W || r.height < MIN_H || r.bottom < mr.top - 30 || r.top > mr.bottom + 30) continue;
      if (theme === "ny") snowCap(r, c.seed); else hwCap(r, c.seed, cfg.spiders);
    }
  }
  /** Снежная шапка: случайная полоса снега и её кусок, под ней — сосульки, одиночные и сдвоенные, утоплены в снег. */
  function snowCap(r, seed) {
    const rnd = rng(seed), w = r.width, strip = N.snow[((seed % N.snow.length) + N.snow.length) % N.snow.length];
    const thick = 10, scale = thick / ih(strip);
    const srcW = clamp(floor(w / scale), 1, iw(strip));
    const srcX = iw(strip) > srcW ? rnd.i(iw(strip) - srcW + 1) : 0;
    X.save(); X.translate(r.left, r.top);
    // Плашка скруглена — шапку обрезаем по её верхним углам.
    const rad = 16;
    X.beginPath(); X.moveTo(0, rad); X.arcTo(0, 0, rad, 0, rad); X.lineTo(w - rad, 0); X.arcTo(w, 0, w, rad, rad); X.lineTo(w, 60); X.lineTo(0, 60); X.closePath(); X.clip();
    let count = clamp(floor(w / 64), 2, 7) + rnd.i(3) - 1;
    for (let k = 0; k < count; k++) {
      const pair = N.pairs.length && rnd.f() < 0.3;
      const im = pair ? N.pairs[rnd.i(N.pairs.length)] : N.ice[rnd.i(N.ice.length)];
      const hh = 9 + rnd.f() * 15 + thick * 0.6, ww = hh * iw(im) / ih(im);
      const x = w * (0.05 + rnd.f() * 0.9), y = thick * 0.05;
      const flip = rnd.b();
      if (flip) mirrorX(x, () => img(im, x - ww / 2, y, ww, hh, 0.88)); else img(im, x - ww / 2, y, ww, hh, 0.88);
    }
    X.drawImage(strip, srcX, 0, srcW, ih(strip), 0, 0, w, thick);
    X.restore();
  }

  /* --- сани Санты / привидение --- */
  const FLIGHT = 7.5, PAUSE = 35, FIRST = 3;
  const flight = { at: -1, toLeft: false, next: 0, laneSince: -1, force: false };
  A.holLaunch = () => { flight.force = true; flight.next = 0; if (A.route().name !== "today" || A.route().params.d) A.go("today"); kick(); };
  function laneRect(mr) {
    if (A.route().name !== "today") return null;
    const el = mainEl().querySelector(".datenav"); if (!el) return null;
    const r = el.getBoundingClientRect();
    if (r.height < 10 || r.bottom < mr.top || r.top > mr.bottom) return null;
    return r;
  }
  function drawFlight(mr, w) {
    const cfg = A.holCfg();
    const ghost = theme === "hw" && cfg.hw.ghost, santa = theme === "ny" && cfg.ny.santa;
    if (!ghost && !santa) return;
    if (ghost ? !(ready.hw && H.ok && H.ghostling.length === 4) : !(ready.ny && N.team)) return;
    const lane = laneRect(mr);
    const sheet = A.sheetOpen && A.sheetOpen();
    if (flight.at < 0) {
      if (!lane || sheet || !animating) { flight.laneSince = -1; return; }
      if (flight.laneSince < 0) flight.laneSince = clock;
      const wait = flight.force ? 0 : max(flight.next - clock, FIRST - (clock - flight.laneSince));
      if (wait > 0) return;
      flight.at = clock; flight.force = false;
    }
    const p = (clock - flight.at) / FLIGHT;
    if (p >= 1) { flight.at = -1; flight.toLeft = !flight.toLeft; flight.next = clock + PAUSE; return; }
    if (!lane) return; // ушли с главной посреди полёта — полёт идёт дальше невидимо
    const el = clock - flight.at, h = lane.height;
    X.save(); X.translate(0, lane.top);
    const go = () => (ghost ? ghostTeam(p, el, w, h) : team(p, el, w, h));
    if (flight.toLeft) mirrorX(w / 2, go); else go();
    X.restore();
  }
  /** Упряжка летит слева направо: p — доля пути (0 — за левым краем, 1 — за правым), t — секунды полёта. */
  function team(p, t, cw, ch) {
    const deerH = ch * 0.92, deerW = deerH * iw(N.deer[0]) / ih(N.deer[0]);
    const sleighH = ch * 1.12, sleighW = sleighH * iw(N.sleigh[0]) / ih(N.sleigh[0]);
    const step = deerW * 0.78, gap = deerW * 0.25, teamW = deerW + step * 2 + gap + sleighW;
    const front = -cw * 0.03 + p * (cw * 1.06 + teamW);
    const arc = -sin(p * PI) * ch * 0.12, wave = sin(p * PI * 3) * ch * 0.05;
    const baseY = (ch - deerH) / 2 + arc + wave;
    const cycle = [0, 2, 1, 2], frame = floor(t * 9);
    const deers = [0, 1, 2].map((i) => {
      const x = front - deerW - i * step, bob = sin(t * 2 * PI * 2.2 + i * 1.3) * ch * 0.04, f = cycle[(frame + i) % 4];
      return { x, y: baseY + bob, im: i === 0 ? N.deerRed[f] : N.deer[f] };
    });
    const sleighX = front - deerW - step * 2 - gap - sleighW;
    const sleighY = baseY + deerH - sleighH * 0.92 + sin(t * 2 * PI * 1.1) * ch * 0.03;
    const midp = (sleighX + sleighW / 2) / cw;
    const sImg = midp >= 0.25 && midp <= 0.75 ? N.sleigh[1] : N.sleigh[0];
    // Золотой шлейф искр за санями.
    for (let k = 1; k <= 22; k++) {
      const sx = sleighX + sleighW * 0.12 - k * cw * 0.022, sy = sleighY + sleighH * (0.82 + 0.08 * sin(k * 1.7 + t * 3));
      const s2 = sin(t * 9 + k * 2.1), al = (1 - k / 23) * (0.55 + 0.45 * s2 * s2);
      const r = (1.2 + (k % 3) * 0.9) * (1 - k / 30);
      glow(GOLD, sx, sy, r * 3, 0.45 * al); dot(GOLD, al, sx, sy, r);
    }
    // Вожжи с провисанием.
    X.strokeStyle = "#7A1A24"; X.lineWidth = 1.6; X.lineCap = "round"; X.beginPath();
    let px = sleighX + sleighW * 0.96, py = sleighY + sleighH * 0.6; X.moveTo(px, py);
    deers.slice().reverse().forEach((d) => { const cx = d.x + deerW * 0.67, cy = d.y + deerH * 0.56; X.quadraticCurveTo((px + cx) / 2, max(py, cy) + ch * 0.06, cx, cy); px = cx; py = cy; });
    X.stroke();
    around(sleighX + sleighW / 2, sleighY + sleighH * 0.8, 1, 1, sin(t * 2 * PI * 1.1) * 2.5 * PI / 180, () => img(sImg, sleighX, sleighY, sleighW, sleighH));
    deers.slice().reverse().forEach((d) => img(d.im, d.x, d.y, deerW, deerH));
  }
  /** Привидение с двумя маленькими спутниками летит по той же полосе. */
  function ghostTeam(p, t, cw, ch) {
    const gh = ch * 1.05, gw = gh * iw(H.ghost[0]) / ih(H.ghost[0]);
    const sh = ch * 0.6, sw = sh * iw(H.ghostling[0]) / ih(H.ghostling[0]);
    const teamW = gw + sw * 2.4, front = -cw * 0.03 + p * (cw * 1.06 + teamW);
    const arc = -sin(p * PI) * ch * 0.12, gx = front - gw, gy = (ch - gh) / 2 + arc + sin(t * 2.4) * ch * 0.07;
    for (let k = 1; k <= 16; k++) {
      const sx = gx - sw * 2.2 - k * cw * 0.02, sy = gy + gh * (0.55 + 0.1 * sin(k * 1.3 + t * 3));
      const s2 = sin(t * 8 + k * 2), al = (1 - k / 17) * (0.5 + 0.5 * s2 * s2), r = 1.4 + (k % 3) * 0.8;
      glow(GHOST_TRAIL, sx, sy, r * 3, 0.4 * al); dot(GHOST_TRAIL, al, sx, sy, r);
    }
    for (let k = 0; k < 2; k++) {
      const f = H.ghostling[(floor(t * 6) + k * 2) % 4];
      img(f, gx - sw * (1.05 + k * 1.15), gy + gh * (0.2 + k * 0.18) + sin(t * 3 + k * 1.7) * ch * 0.08, sw, sh);
    }
    const midp = (gx + gw / 2) / cw;
    const f = midp >= 0.3 && midp <= 0.7 ? H.ghost[4 + (floor(t * 3) % 2)] : H.ghost[floor(t * 6) % 4];
    img(f, gx, gy, gw, gh);
  }

  /* --- снегопад / листопад при входе --- */
  let fall = null;
  function startFall(force) {
    if (!theme) return;
    const c = A.holCfg()[theme];
    if (!force && (theme === "ny" ? !c.snow : !c.leaves)) return;
    if (!canAnimate()) return;
    const seed = (Date.now() / 1000) | 0, items = [];
    if (theme === "ny") {
      const r = rng(seed);
      for (let i = 0; i < 56; i++) {
        const layer = r.i(3), sp = 0.16 + 0.07 * layer + r.f() * 0.06, appear = r.f() * (S.FALL_MS / 1000);
        items.push({ x: r.f(), y0: -0.04 - sp * appear, sp, r: 3 + 2 * layer + r.f() * 1.5, sway: 0.008 + r.f() * 0.018, hz: 0.25 + r.f() * 0.35, ph: r.f() * 2 * PI, a: 0.55 + 0.15 * layer + r.f() * 0.15 });
      }
    } else {
      const r = rng(seed * 7 + 1);
      for (let i = 0; i < 34; i++) {
        const kind = i % 5 === 0 ? 1 : i % 7 === 0 ? 2 : 0, sp = 0.14 + r.f() * 0.08;
        items.push({ kind, im: r.i(6), x: r.f(), y0: -0.06 - sp * r.f() * 5, sp, size: kind === 2 ? 10 : kind === 1 ? 22 : 14 + r.f() * 8, sway: 0.01 + r.f() * 0.03, hz: 0.2 + r.f() * 0.3, ph: r.f() * 6.28, spin: (r.f() - 0.5) * 140 });
      }
    }
    const total = Math.max(...items.map((f) => (1.08 - f.y0) / f.sp));
    fall = { kind: theme, t0: clock, items, total };
    fallCv = mkCanvas("holFall", document.body, "hol-fall");
    if (theme === "hw") loadSet("hw");
    kick();
  }
  A.holFall = () => startFall(true);
  function drawFall(w, h) {
    const t = clock - fall.t0;
    if (t > fall.total || fall.kind !== theme) { fall = null; if (fallCv) { fallCv.remove(); fallCv = null; } return; }
    X = fit(fallCv, w, h);
    const fade = min(1, t / 0.4);
    if (fall.kind === "ny") {
      const tint = snowTint();
      fall.items.forEach((f) => {
        const y = (f.y0 + f.sp * t) * h; if (y < -20 || y > h + 30) return;
        const x = (f.x + f.sway * sin(2 * PI * f.hz * t + f.ph)) * w;
        snowflake(x, y, f.r, tint, f.a * fade, f.ph * 57 + t * 25 * (f.hz > 0.4 ? 1 : -1));
      });
      return;
    }
    if (!ready.hw) return;
    fall.items.forEach((f) => {
      const y = (f.y0 + f.sp * t) * h; if (y < -60 || y > h + 60) return;
      const x = (f.x + f.sway * sin(2 * PI * f.hz * t + f.ph) + (f.kind === 1 ? t * 0.03 : 0)) * w;
      const L = f.kind === 1 ? H.fallBats : f.kind === 2 ? H.stars : H.leaves; if (!L.length) return;
      const b = L[f.im % L.length], sw = f.size, sh = sw * ih(b) / iw(b);
      if (f.kind === 0) around(x, y, 1, 1, (f.ph * 57 + t * f.spin) * PI / 180, () => img(b, x - sw / 2, y - sh / 2, sw, sh, fade));
      else if (f.kind === 1) around(x, y, 1, 0.75 + 0.25 * sin(t * 18 + f.ph), 0, () => img(b, x - sw / 2, y - sh / 2, sw, sh, fade));
      else { const s2 = sin(t * 6 + f.ph); img(b, x - sw / 2, y - sh / 2, sw, sh, fade * (0.5 + 0.5 * s2 * s2)); }
    });
  }

  /* ================= ХЭЛЛОУИН ================= */
  const FIREFLIES = (() => { const r = rng(3110); return Array.from({ length: 14 }, (_, i) => ({ x: r.f(), y: 0.25 + r.f() * 0.65, sp: 0.3 + r.f() * 0.5, ph: r.f() * 6.28, gold: i % 4 === 0 })); })();
  // Где на нижней кайме глаза (дупла, куст), норы, ветки для ворон; на верхней — ветки для мышей (доли картинки).
  const TREE_EYES = [[0.0859, 0.2374, 0.7], [0.0635, 0.4306, 0.9], [0.9047, 0.2666, 0.7], [0.9366, 0.5006, 0.9]];
  const BURROWS = [[0.071, 0.905], [0.367, 0.912], [0.6907, 0.8995]];
  const CROW_PERCHES = [[0.18, 0.214], [0.24, 0.173], [0.27, 0.329], [0.77, 0.338], [0.83, 0.179], [0.86, 0.342]];
  const PERCHES = [[0.23, 0.14], [0.315, 0.135], [0.215, 0.34], [0.30, 0.32], [0.57, 0.215], [0.645, 0.225], [0.78, 0.315], [0.86, 0.317]];

  /** Стайка (мыши, вороны): сидят на ветках, иногда одна перелетает на свободную ветку. */
  const flock = (at, next, seed, dur, nPerch, rest, restRnd, lag) => ({
    DUR: dur, at, to: at.map(() => -1), start: at.map(() => 0), next, rnd: rng(seed),
    step(t) {
      for (let i = 0; i < this.at.length; i++) {
        if (this.to[i] >= 0 && (t < this.start[i] || t - this.start[i] >= this.DUR)) { this.at[i] = this.to[i]; this.to[i] = -1; this.next[i] = t + rest + this.rnd.f() * restRnd; }
        if (this.to[i] < 0 && t >= this.next[i]) {
          const busy = new Set(this.at.concat(this.to.filter((x) => x >= 0)));
          const free = []; for (let k = 0; k < nPerch; k++) if (!busy.has(k)) free.push(k);
          if (free.length) { this.to[i] = free[this.rnd.i(free.length)]; this.start[i] = t; }
        }
        if (t + lag < this.next[i]) this.next[i] = t + 5;
      }
    }
  });
  const Flock = flock([0, 4, 6], [5, 11, 17], 1031, 2.8, PERCHES.length, 7, 10, 60);
  const Crows = flock([0, 3, 5], [9, 19, 29], 777, 3.2, CROW_PERCHES.length, 9, 14, 80);

  /** Небо без картинок (пока грузятся). */
  function hwSky(w, hh) {
    const c = A.holCfg().hw, st = HW_BG[c.bg] || HW_BG[0], v = dark ? st.dark : st.light, full = window.innerHeight;
    const g = X.createRadialGradient(w * 0.5, hh * 0.3, 0, w * 0.5, hh * 0.3, max(w, full) * 0.95);
    g.addColorStop(0, v[0]); g.addColorStop(0.5, v[1]); g.addColorStop(1, v[2]); X.fillStyle = g; X.fillRect(0, 0, w, full);
    return { mx: w * 0.8, my: hh * 0.15, mr: w * 0.1 };
  }
  function fogBand(key, y, bandH, speed, t, color, alpha) {
    const tex = H.fogTint[key] && H.fogTint[key][color]; if (!tex) return;
    const w = X.canvas.width / dprOf(), tw = w * 1.3;
    let x = -((t * speed) % tw);
    X.globalAlpha = alpha;
    while (x < w) { X.drawImage(tex, x, y, tw, bandH); x += tw; }
    X.globalAlpha = 1;
  }
  /** Сцена: ночь, луна, светлячки, ведьма, туман между дальним и ближним планом кладбища, рука, глаза, вороны, мыши, пауки. */
  function halloweenScene(t, w, hh) {
    const c = A.holCfg().hw;
    const { mx, my, mr } = hwSky(w, hh);
    const pulse = 0.85 + 0.15 * sin(t * 0.8);
    glow(LIME, mx, my, mr * 3.2, (dark ? 0.26 : 0.18) * pulse);
    dot(LIME, dark ? 0.92 : 0.7, mx, my, mr);
    const cr = rgb("#A8D84A");
    dot(cr, 0.7, mx - mr * 0.32, my - mr * 0.18, mr * 0.22); dot(cr, 0.7, mx + mr * 0.3, my + mr * 0.25, mr * 0.14); dot(cr, 0.7, mx - mr * 0.05, my + mr * 0.45, mr * 0.1);
    FIREFLIES.forEach((f) => {
      let a = (sin(t * 1.6 * f.sp + f.ph) + 1) / 2; a *= a; if (a < 0.05) return;
      const px = (f.x + 0.03 * sin(t * f.sp + f.ph)) * w, py = (f.y + 0.02 * cos(t * f.sp * 0.8 + f.ph)) * hh;
      const col = f.gold ? rgb("#F7D66B") : LIME; glow(col, px, py, 9, 0.5 * a); dot(col, a, px, py, 1.8);
    });
    if (c.witch) witchFlight(t, w, mx, my, mr);
    const fogOn = c.fog;
    if (fogOn) fogBand("fog2", hh * 0.38, hh * 0.3, 5, t, "lav", dark ? 0.09 : 0.16);
    const bh = w / 3, by = hh - bh;
    img(H.far, 0, by, w, bh, 0.95);
    if (fogOn) { fogBand("fog1", by + bh * 0.4, bh * 0.45, 9, t, "lav", dark ? 0.5 : 0.55); fogBand("fog2", by + bh * 0.55, bh * 0.35, 16, t, "lime", 0.2); }
    img(H.near, 0, by, w, bh);
    if (c.hand) {
      const local = (t + 8) % 26;
      const f = local < 1 ? clamp(floor(local * 6), 0, 5) : local < 3.2 ? (floor((local - 1) / 0.35) % 2 === 0 ? 5 : 4) : local < 4.2 ? clamp(5 - floor((local - 3.2) * 6), 0, 5) : 0;
      const hb = H.hand[f], hhh = bh * 0.3, hw = hhh * iw(hb) / ih(hb);
      img(hb, w * 0.8 - hw / 2, by + bh * 0.99 - hhh, hw, hhh);
    }
    if (c.eyes) TREE_EYES.forEach(([fx, fy, r], i) => evilEyes(i, t, w * fx, by + bh * fy, r, true, 1));
    if (c.crows) crows(t, w, by, bh);
    if (fogOn) fogBand("fog1", by + bh * 0.8, bh * 0.3, 22, t, "lav", 0.2);
    const th = w / 3;
    img(H.top, 0, 0, w, th);
    if (c.bats) bats(t, w, th);
    if (c.webs) webSpider(t, w, th, by, bh);
  }
  /** Пара красных глаз-точек: моргают (иногда дважды), при roam то гаснут, то загораются. */
  function evilEyes(i, t, cx, cy, r, roam, alpha) {
    r = max(r, 0.8);
    const vis = alpha * (roam ? clamp((sin(t * 0.19 + i * 2.3) + 0.3) / 0.35, 0, 1) : 1);
    if (vis <= 0.01) return;
    const p = 2.6 + (i * 1.37) % 3.1, ph = (t + i * 1.7) % p, dbl = (floor((t + i * 1.7) / p) + i) % 3 === 0;
    const lid = (x) => (x < 0.25 ? abs(x - 0.125) / 0.125 : 1);
    const open = max(0.12, ph < 0.25 ? lid(ph) : dbl && ph >= 0.38 && ph <= 0.63 ? lid(ph - 0.38) : 1);
    const gap = r * 3.4;
    glow(EYE, cx, cy, r * 5, 0.28 * vis * open);
    for (const sx of [-1, 1]) {
      const ex = cx + sx * gap / 2;
      X.fillStyle = rgba(EYE, vis); X.beginPath(); X.ellipse(ex, cy, r, r * open, 0, 0, 2 * PI); X.fill();
      if (open > 0.6) dot([255, 255, 255], 0.5 * vis, ex - r * 0.3, cy - r * 0.3, r * 0.3);
    }
  }
  function crows(t, w, by, bh) {
    Crows.step(t);
    const cw = w * 0.065;
    const perch = (i) => ({ x: CROW_PERCHES[i][0] * w, y: by + CROW_PERCHES[i][1] * bh });
    const drawCrow = (f, fx, fy, left) => { const x = fx - cw * 0.473, y = fy - cw * 0.925; if (left) mirrorX(fx, () => img(f, x, y, cw, cw)); else img(f, x, y, cw, cw); };
    for (let i = 0; i < Crows.at.length; i++) {
      const from = perch(Crows.at[i]), dest = Crows.to[i];
      if (dest < 0) {
        const left = from.x > w / 2, local = (t + i * 4.3) % 11;
        const f = local < 1.4 ? H.crow_caw[[0, 1, 2, 1, 2, 0][clamp(floor(local / 0.24), 0, 5)]] : local >= 5 && local <= 6.2 ? H.crow_idle[2] : local >= 8 && local <= 8.8 ? H.crow_idle[1] : local >= 9.6 && local <= 10 ? H.crow_caw[3] : H.crow_idle[0];
        drawCrow(f, from.x, from.y, left); continue;
      }
      const to = perch(dest), u = clamp((t - Crows.start[i]) / Crows.DUR, 0, 1), left = to.x < from.x;
      if (u < 0.14) drawCrow(H.crow_up[clamp(floor(u / 0.14 * 4), 0, 3)], from.x, from.y, left);
      else if (u > 0.84) drawCrow(H.crow_land[clamp(floor((u - 0.84) / 0.16 * 4), 0, 3)], to.x, to.y, left);
      else {
        const s = smooth((u - 0.14) / 0.7), cx = (from.x + to.x) / 2, cy = min(from.y, to.y) - bh * 0.35;
        drawCrow(H.crow_fly[floor(t * 9) % 4], (1 - s) * (1 - s) * from.x + 2 * (1 - s) * s * cx + s * s * to.x, (1 - s) * (1 - s) * from.y + 2 * (1 - s) * s * cy + s * s * to.y, left);
      }
    }
  }
  function bats(t, w, th) {
    Flock.step(t);
    const bw = w * 0.1, bh = bw * ih(H.bat_hang[0]) / iw(H.bat_hang[0]);
    const perch = (i) => ({ x: PERCHES[i][0] * w, y: PERCHES[i][1] * th });
    for (let i = 0; i < Flock.at.length; i++) {
      const from = perch(Flock.at[i]), dest = Flock.to[i];
      if (dest < 0) { const k = floor((t * 1.1 + i * 3) % 7); img(H.bat_hang[k === 5 ? 1 : k === 6 ? 2 : 0], from.x - bw * 0.53, from.y, bw, bh); continue; }
      const to = perch(dest), u = clamp((t - Flock.start[i]) / Flock.DUR, 0, 1), left = to.x < from.x;
      const mir = (ax, fn) => (left ? mirrorX(ax, fn) : fn());
      if (u < 0.14) { const f = H.bat_up[clamp(floor(u / 0.14 * 4), 0, 3)]; mir(from.x, () => img(f, from.x - bw * 0.53, from.y, bw, bh)); }
      else if (u > 0.86) { const f = H.bat_land[clamp(floor((u - 0.86) / 0.14 * 4), 0, 3)]; mir(to.x, () => img(f, to.x - bw * 0.53, to.y, bw, bh)); }
      else {
        const s = smooth((u - 0.14) / 0.72), p0x = from.x, p0y = from.y + bh * 0.55, p2x = to.x, p2y = to.y + bh * 0.55;
        const cx = (p0x + p2x) / 2, cy = max(p0y, p2y) + w * 0.2;
        const px = (1 - s) * (1 - s) * p0x + 2 * (1 - s) * s * cx + s * s * p2x, py = (1 - s) * (1 - s) * p0y + 2 * (1 - s) * s * cy + s * s * p2y;
        const f = H.bat_fly[floor(t * 10) % 4]; mir(px, () => img(f, px - bw / 2, py - bh / 2, bw, bh));
      }
    }
  }
  /** Ведьма на метле силуэтом проходит мимо луны — раз в ~75 с, то вправо, то влево. */
  function witchFlight(t, w, mx, my, mr) {
    if (!H.witch || H.witch.length < 8) return;
    const period = 75, dur = 11, n = floor((t + 25) / period), local = (t + 25) % period;
    if (local > dur) return;
    const ww = w * 0.3, wh = ww * ih(H.witch[0]) / iw(H.witch[0]), u = local / dur, toRight = n % 2 === 0;
    const xc = toRight ? -ww + u * (w + 2 * ww) : w + ww - u * (w + 2 * ww);
    const yc = my + mr * 0.15 - sin(u * PI) * mr * 0.5 + sin(t * 2) * mr * 0.06;
    const nearMoon = abs(xc - mx) < mr * 1.6;
    const f = nearMoon ? H.witch[4 + (floor(t * 3) % 2)] : n % 3 === 1 ? H.witch[6 + (floor(t * 2) % 2)] : H.witch[floor(t * 8) % 4];
    const x = xc - ww / 2, y = yc - wh / 2;
    if (toRight) img(f, x, y, ww, wh); else mirrorX(xc, () => img(f, x, y, ww, wh));
  }

  /* --- зверьки: паук и крыса --- */
  const seq = (u) => clamp(floor(u * 4), 0, 3);
  const loop = (t, fps) => ((floor(t * (fps || 10)) % 4) + 4) % 4;
  const ratF = (a, i) => H.rat[a] && H.rat[a][clamp(i, 0, 3)];
  const spiF = (a, i) => H.spi[a] && H.spi[a][clamp(i, 0, 3)];
  const RAT_BASE = 0.959, SPI_BASE = 0.97, OLD_SIDE_BASE = 0.955;
  /** Кадр зверька: низ рисунка на линии base, по центру x; кадры смотрят вправо, faceLeft отражает. */
  function critter(b, x, base, fw, baseline, faceLeft) {
    if (!b) return;
    const fh = fw * ih(b) / iw(b), l = x - fw / 2, tp = base - fh * baseline;
    if (faceLeft) mirrorX(x, () => img(b, l, tp, fw, fh)); else img(b, l, tp, fw, fh);
  }
  /**
   * Нижняя кайма: пауки спускаются с веток на паутине по краю экрана, в норах живёт крыса. Цикл ~46 с:
   *  - паук идёт в нору с крысой → крыса скалится, паук пугается и бежит в ближайшую свободную нору;
   *  - паук сидит в норе (горят глаза), крыса идёт к нему → паук скалится, крыса бежит в ближайшую свободную;
   *  - просто паук: нора, глаза, уходит за край. Крыса иногда выглядывает из своей норы.
   */
  function webSpider(t, w, th, by, bh) {
    const cycle = 46, n = max(0, floor((t + 6) / cycle)), lt = (t + 6) % cycle;
    const pos = BURROWS.map(([x, y]) => ({ x: x * w, y: by + y * bh }));
    const rim = pos.map((p) => ({ x: p.x, y: p.y + bh * 0.03 }));
    const nearest = (fx, except) => { let best = -1, bd = 1e9; pos.forEach((p, i) => { if (except.has(i)) return; const d = abs(p.x - fx); if (d < bd) { bd = d; best = i; } }); return best; };
    const ratsOn = H.ratsOn, stopGap = 18;
    const fleeHome = (hm, tg) => nearest(pos[tg].x + (pos[hm].x < pos[tg].x ? -stopGap : stopGap), new Set([tg]));
    let home = 1, type = 0, target = 0;
    for (let c = 0; c <= n; c++) {
      const left = c % 2 === 0;
      type = ratsOn ? c % 3 : 2;
      const side = left ? [0, 1] : [2, 1];
      target = type === 0 ? home : side.find((it) => it !== home || !ratsOn);
      if (c < n && type === 1) home = fleeHome(home, target);
    }
    const sx = (n % 2 === 0 ? 0.06 : 0.95) * w, startY = th * 0.29, ground = rim[target].y, speed = w * 0.11;
    const tw = 18, tH = tw * ih(H.spider_top[0]) / iw(H.spider_top[0]);
    const sideW = 20, spiW = 25, ratW = 28, tAppear = 0.8, tDesc = 10;
    const spiderWalk = (x, toLeft, y) => critter(H.spider_side[loop(t, 9)], x, y == null ? ground : y, sideW, OLD_SIDE_BASE, toLeft);
    const ratIdle = (hm) => { const q = (t + hm * 3.7) % 11; if (q < 2.4) critter(ratF("peek", q < 1.2 ? seq(q / 1.2) : seq((2.4 - q) / 1.2)), pos[hm].x, rim[hm].y, ratW, RAT_BASE); };
    const eyesIn = (i, at, a) => evilEyes(i + 7, t, at.x, at.y, 0.9, false, a == null ? 1 : a);
    let k = lt;
    // 1. Спуск по паутине (голова вниз).
    if (k < tAppear + tDesc) {
      if (ratsOn) ratIdle(home);
      const sway = sin(t * 1.3) * 3;
      const y = k < tAppear ? startY : startY + (ground - startY - tH) * smooth((k - tAppear) / tDesc);
      const x = sx + sway * clamp((y - startY) / (ground - startY), 0, 1);
      line(rgba(LILAC, 0.55), sx, startY - 2, x, y, 1);
      const sc = k < tAppear ? k / tAppear : 1, f = H.spider_top[k < tAppear ? 0 : loop(t, 9)];
      around(x, y, sc, sc, 0, () => around(x, y + tH / 2, 1, 1, PI, () => img(f, x - tw / 2, y, tw, tH)));
      return;
    }
    k -= tAppear + tDesc;
    const tgt = pos[target], fromLeft = sx < tgt.x;
    if (type === 0) {
      // 2a. Паук идёт к норе, где сидит крыса.
      const stopX = tgt.x + (fromLeft ? -stopGap : stopGap), walk1 = max(0.4, abs(stopX - sx) / speed);
      if (k < walk1) { ratIdle(home); spiderWalk(sx + (stopX - sx) * (k / walk1), !fromLeft); return; }
      k -= walk1;
      if (k < 1.4) { const u = k / 1.4; critter(ratF("snarl", seq(u)), tgt.x, rim[target].y, ratW, RAT_BASE, fromLeft); critter(spiF("scared", seq(u)), stopX, ground, spiW, SPI_BASE, !fromLeft); return; }
      k -= 1.4;
      const free = nearest(stopX, new Set([target])), fx = pos[free].x, run = max(0.3, abs(fx - stopX) / (speed * 2.2));
      if (k < run) { critter(ratF("dive", seq(k / run)), tgt.x, rim[target].y, ratW, RAT_BASE); critter(spiF("run", loop(t, 14)), stopX + (fx - stopX) * (k / run), ground, spiW, SPI_BASE, fx < stopX); return; }
      k -= run;
      if (k < 0.6) { critter(spiF("dive", seq(k / 0.6)), fx, rim[free].y, spiW, SPI_BASE, fx < stopX); return; }
      k -= 0.6;
      if (k < 6) { eyesIn(free, pos[free], min(1, k / 0.5)); ratIdle(home); return; }
      k -= 6;
      const edgeX = fx < w / 2 ? -w * 0.06 : w * 1.06;
      if (k < 0.7) { critter(spiF("climb", seq(k / 0.7)), fx, rim[free].y, spiW, SPI_BASE, edgeX < fx); return; }
      k -= 0.7;
      const walk2 = abs(edgeX - fx) / speed;
      if (k < walk2) spiderWalk(fx + (edgeX - fx) * (k / walk2), edgeX < fx, rim[free].y);
      ratIdle(home);
      return;
    }
    // 2b. Паук идёт в свободную нору.
    const walk1 = max(0.6, abs(tgt.x - sx) / speed);
    if (k < walk1) { if (ratsOn) ratIdle(home); spiderWalk(sx + (tgt.x - sx) * (k / walk1), !fromLeft); return; }
    k -= walk1;
    if (k < 0.6) { if (ratsOn) ratIdle(home); critter(spiF("dive", seq(k / 0.6)) || H.peek[2], tgt.x, rim[target].y, spiW, SPI_BASE, !fromLeft); return; }
    k -= 0.6;
    const stay = type === 1 ? 9 : 7;
    if (k < stay) {
      if (type === 1) {
        // Крыса идёт к норе паука: паук скалится, крыса пугается и бежит в ближайшую свободную нору.
        const hp = pos[home], ratLeft = hp.x < tgt.x, stopX = tgt.x + (ratLeft ? -stopGap : stopGap), newHome = fleeHome(home, target);
        let q = k - 1;
        if (q < 0) { eyesIn(target, tgt, min(1, k / 0.5)); return; }
        if (q < 0.6) { critter(ratF("climb", seq(q / 0.6)), hp.x, rim[home].y, ratW, RAT_BASE, !ratLeft); eyesIn(target, tgt); return; }
        q -= 0.6;
        const walk = max(0.4, abs(stopX - hp.x) / speed);
        if (q < walk) { critter(ratF("walk", loop(t, 9)), hp.x + (stopX - hp.x) * (q / walk), rim[home].y, ratW, RAT_BASE, !ratLeft); eyesIn(target, tgt); return; }
        q -= walk;
        if (q < 1.4) { const u = q / 1.4; critter(spiF("snarl", seq(u)), tgt.x, rim[target].y, spiW, SPI_BASE, ratLeft); critter(ratF("scared", seq(u)), stopX, rim[target].y, ratW, RAT_BASE, !ratLeft); return; }
        q -= 1.4;
        const nx = pos[newHome].x, run = max(0.3, abs(nx - stopX) / (speed * 2.2));
        if (q < run) { critter(ratF("run", loop(t, 14)), stopX + (nx - stopX) * (q / run), rim[target].y, ratW, RAT_BASE, nx < stopX); eyesIn(target, tgt); return; }
        q -= run;
        if (q < 0.6) { critter(ratF("dive", seq(q / 0.6)), nx, rim[newHome].y, ratW, RAT_BASE, nx < stopX); eyesIn(target, tgt); return; }
        eyesIn(target, tgt);
        return;
      }
      if (ratsOn) ratIdle(home);
      eyesIn(target, tgt, min(1, k / 0.5));
      return;
    }
    k -= stay;
    const edgeX = tgt.x < w / 2 ? -w * 0.06 : w * 1.06, newHome = type === 1 ? fleeHome(home, target) : home;
    if (ratsOn) ratIdle(newHome);
    if (k < 0.7) { critter(spiF("climb", seq(k / 0.7)) || H.peek[2], tgt.x, rim[target].y, spiW, SPI_BASE, edgeX < tgt.x); return; }
    k -= 0.7;
    const walk2 = abs(edgeX - tgt.x) / speed;
    if (k < walk2) spiderWalk(tgt.x + (edgeX - tgt.x) * (k / walk2), edgeX < tgt.x, rim[target].y);
  }

  /* --- хэллоуинские шапки карточек --- */
  const CAP_HOLES = [
    [[0.257, 0.79]], [[0.749, 0.79]], [[0.287, 0.795]], [[0.725, 0.8]],
    [[0.231, 0.845], [0.798, 0.845]], [[0.203, 0.841], [0.824, 0.841]], [[0.194, 0.841], [0.804, 0.841]], [[0.172, 0.845], [0.784, 0.845]]
  ];
  /** Холмик со мхом и норами на верхнем краю плашки — целые полосы cap_*; из нор выползают паучки и крысы. */
  function hwCap(r, seed, life) {
    const w = r.width, capH = 22, natural = capH * iw(H.caps[0]) / ih(H.caps[0]), ov = 0.06;
    const count = max(1, Math.round(w / (natural * (1 - ov)))), pieceW = w / (count - (count - 1) * ov);
    const topY = -capH * 0.8;
    let k = ((seed % 8) + 8) % 8, x = 0;
    const holes = [];
    X.save(); X.translate(r.left, r.top);
    for (let i = 0; i < count; i++) {
      img(H.caps[k], x, topY, pieceW, capH);
      CAP_HOLES[k].forEach(([hx, hy]) => { const px = x + hx * pieceW; if (px > 10 && px < w - 10) holes.push({ x: px, y: topY + hy * capH }); });
      x += pieceW * (1 - ov); k = (k + 3) % 8;
    }
    if (life && holes.length && H.spider_side.length === 4) capLife(seed, holes, capH, clock);
    X.restore();
  }
  /**
   * Жизнь на шапке. Одна нора — паучок гуляет петлёй или крыса выбегает и возвращается;
   * две и больше — перебежка в соседнюю нору, а на части плашек встреча: гость пугается хозяина
   * (тот выглядывает и скалится) и убегает в ближайшую свободную нору.
   */
  function capLife(seed, holes, capH, t) {
    const sm = (a) => ((seed % a) + a) % a;
    const period = 16 + sm(5), n = floor((t + sm(97)) / period), local = (t + sm(97)) % period;
    const rimDy = capH * 0.08, feetDy = -capH * 0.06;
    const ratW = 26, spiW = 23, sideW = 18, peekW = 18, peekH = peekW * ih(H.peek[0]) / iw(H.peek[0]), speed = 34;
    const oldPeek = (at, u) => img(H.peek[clamp(floor(u * 3), 0, 2)], at.x - peekW / 2, at.y + rimDy - peekH, peekW, peekH);
    const spiderSide = (x, y, toLeft) => critter(H.spider_side[loop(t, 10)], x, y, sideW, OLD_SIDE_BASE, toLeft);
    let i0 = sm(holes.length); if (holes.length >= 2 && i0 === holes.length - 1) i0--;
    const first = holes[i0], kind = sm(3), ratsOn = H.ratsOn;
    if (holes.length === 1 || !ratsOn || kind !== 2) {
      const rat = ratsOn && kind === 1;
      if (local > 6) return;
      if (holes.length >= 2) {
        const b = holes[i0 + 1], left = b.x < first.x;
        if (local < 0.7) { if (rat) critter(ratF("peek", seq(local / 0.7)), first.x, first.y + rimDy, ratW, RAT_BASE, left); else oldPeek(first, local / 0.7); }
        else if (local > 5.3) { if (rat) critter(ratF("dive", seq((local - 5.3) / 0.7)), b.x, b.y + rimDy, ratW, RAT_BASE, left); else oldPeek(b, (6 - local) / 0.7); }
        else {
          const u = smooth((local - 0.7) / 4.6), x = first.x + (b.x - first.x) * u, y = first.y + feetDy + sin(u * PI * 5) * 0.6;
          if (rat) critter(ratF("walk", loop(t, 9)), x, y, ratW, RAT_BASE, left); else spiderSide(x, y, left);
        }
        return;
      }
      if (rat) {
        const out = 22 * ((seed & 2) === 0 ? 1 : -1), left = out < 0;
        if (local < 0.7) critter(ratF("peek", seq(local / 0.7)), first.x, first.y + rimDy, ratW, RAT_BASE, left);
        else if (local < 1.3) critter(ratF("climb", seq((local - 0.7) / 0.6)), first.x, first.y + rimDy, ratW, RAT_BASE, left);
        else if (local < 2.6) critter(ratF("walk", loop(t, 9)), first.x + out * ((local - 1.3) / 1.3), first.y + feetDy, ratW, RAT_BASE, left);
        else if (local < 3.6) critter(ratF("peek", 3), first.x + out, first.y + feetDy, ratW, RAT_BASE, left);
        else if (local < 4.9) critter(ratF("walk", loop(t, 9)), first.x + out * (1 - (local - 3.6) / 1.3), first.y + feetDy, ratW, RAT_BASE, !left);
        else critter(ratF("dive", seq((local - 4.9) / 1.1)), first.x, first.y + rimDy, ratW, RAT_BASE, !left);
        return;
      }
      // Одна нора, паучок: петля по плашке и обратно.
      if (local < 0.7) oldPeek(first, local / 0.7);
      else if (local > 5.3) oldPeek(first, (6 - local) / 0.7);
      else {
        const u = (local - 0.7) / 4.6, ang = u * 2 * PI, rr = 26;
        const px = first.x + rr * 1.5 * sin(ang), py = first.y + rr * (1 - cos(ang));
        const heading = Math.atan2(rr * sin(ang), rr * 1.5 * cos(ang)) + PI / 2;
        const f = H.spider_top[loop(t, 10)], tw = 16, thh = tw * ih(f) / iw(f);
        around(px, py, 1, 1, heading, () => img(f, px - tw / 2, py - thh / 2, tw, thh));
      }
      return;
    }
    // Встреча: хозяин сидит в норе A, гость выходит из норы B и идёт к A.
    const ratVisits = n % 2 === 0, home = holes[i0], from = holes[i0 + 1], gLeft = home.x < from.x;
    const stopX = home.x + (gLeft ? 16 : -16), walkT = clamp(abs(stopX - from.x) / speed, 0.6, 4);
    let k = local;
    if (k < 0.8) { if (ratVisits) critter(ratF("climb", seq(k / 0.8)), from.x, from.y + rimDy, ratW, RAT_BASE, gLeft); else critter(spiF("climb", seq(k / 0.8)), from.x, from.y + rimDy, spiW, SPI_BASE, gLeft); return; }
    k -= 0.8;
    if (k < walkT) { const x = from.x + (stopX - from.x) * (k / walkT); if (ratVisits) critter(ratF("walk", loop(t, 9)), x, from.y + feetDy, ratW, RAT_BASE, gLeft); else spiderSide(x, from.y + feetDy, gLeft); return; }
    k -= walkT;
    if (k < 1.4) {
      const u = k / 1.4;
      if (ratVisits) { critter(spiF("snarl", seq(u)), home.x, home.y + rimDy, spiW, SPI_BASE, !gLeft); critter(ratF("scared", seq(u)), stopX, from.y + feetDy, ratW, RAT_BASE, gLeft); }
      else { critter(ratF("snarl", seq(u)), home.x, home.y + rimDy, ratW, RAT_BASE, !gLeft); critter(spiF("scared", seq(u)), stopX, from.y + feetDy, spiW, SPI_BASE, gLeft); }
      return;
    }
    k -= 1.4;
    const free = holes.filter((hh) => hh !== home).reduce((a, b) => (abs(b.x - stopX) < abs(a.x - stopX) ? b : a));
    const runT = clamp(abs(free.x - stopX) / (speed * 2.4), 0.3, 2), runLeft = free.x < stopX;
    if (k < runT) {
      const x = stopX + (free.x - stopX) * (k / runT);
      if (ratVisits) { critter(spiF("dive", seq(k / runT)), home.x, home.y + rimDy, spiW, SPI_BASE, !gLeft); critter(ratF("run", loop(t, 14)), x, from.y + feetDy, ratW, RAT_BASE, runLeft); }
      else { critter(ratF("dive", seq(k / runT)), home.x, home.y + rimDy, ratW, RAT_BASE, !gLeft); critter(spiF("run", loop(t, 14)), x, from.y + feetDy, spiW, SPI_BASE, runLeft); }
      return;
    }
    k -= runT;
    if (k < 0.6) { if (ratVisits) critter(ratF("dive", seq(k / 0.6)), free.x, free.y + rimDy, ratW, RAT_BASE, runLeft); else critter(spiF("dive", seq(k / 0.6)), free.x, free.y + rimDy, spiW, SPI_BASE, runLeft); }
  }

  /* ---------- живое превью в настройках ---------- */
  function drawPreview() {
    const cv = document.getElementById("holPrev"); if (!cv || !cv.isConnected) return;
    const th = cv.dataset.th, cfg = A.holCfg()[th], st = A.HOL_STYLES[th];
    const w = cv.clientWidth || 300, h = cv.clientHeight || 160;
    const save = X; X = fit(cv, w, h);
    const isDark = document.documentElement.dataset.theme === "dark";
    const v = isDark ? (st.bg[cfg.bg] || st.bg[0]).dark : (st.bg[cfg.bg] || st.bg[0]).light;
    const g = X.createRadialGradient(w * 0.5, h * 0.3, 0, w * 0.5, h * 0.3, max(w, h) * 0.9);
    g.addColorStop(0, v[0]); g.addColorStop(0.5, v[1]); g.addColorStop(1, v[2]); X.fillStyle = g; X.fillRect(0, 0, w, h);
    const t = clock;
    if (th === "ny") {
      SPARKLES.slice(0, 8).forEach((p) => { let a = 0.5 + 0.5 * sin(t * p.sp + p.ph); a *= a; if (a > 0.05) sparkle(p.x * w, p.y * h, p.r * 0.8, GOLD, a * 0.8); });
      if (ready.ny && N.bottom) { const bh = w * ih(N.bottom) / iw(N.bottom); img(N.bottom, 0, h - bh, w, bh, 0.9); }
    } else {
      dot(LIME, isDark ? 0.92 : 0.7, w * 0.82, h * 0.22, h * 0.12); glow(LIME, w * 0.82, h * 0.22, h * 0.36, 0.2);
      if (ready.hw && H.near) { const bh = w / 3; img(H.far, 0, h - bh, w, bh, 0.95); img(H.near, 0, h - bh, w, bh); }
    }
    const card = (st.card[cfg.card] || st.card[0])[isDark ? "dark" : "light"];
    const r = { left: w * 0.1, top: h * 0.3, width: w * 0.8, height: h * 0.42 };
    X.fillStyle = card; X.beginPath(); X.roundRect ? X.roundRect(r.left, r.top, r.width, r.height, 14) : X.rect(r.left, r.top, r.width, r.height); X.fill();
    X.fillStyle = isDark ? "rgba(255,255,255,.85)" : "rgba(40,30,50,.85)"; X.font = "600 14px " + getComputedStyle(document.body).fontFamily;
    X.fillText(th === "ny" ? "С наступающим!" : "Жуткая ночь", r.left + 14, r.top + 30);
    X.fillStyle = isDark ? "rgba(255,255,255,.4)" : "rgba(40,30,50,.35)"; X.fillRect(r.left + 14, r.top + 42, r.width * 0.55, 6); X.fillRect(r.left + 14, r.top + 54, r.width * 0.4, 6);
    if (th === "ny" && ready.ny && N.ok) snowCap(r, 11);
    if (th === "hw" && ready.hw && H.ok && H.caps.length === 8) hwCap(r, 6, true);
    X = save;
  }
  let prevRaf = 0;
  function drawPreviewOnly() {
    // Тема не включена, но открыты её настройки — превью анимируется своим таймером, пока оно на экране.
    const cv = document.getElementById("holPrev");
    if (!cv) return;
    loadSet(cv.dataset.th);
    if (prevRaf) return;
    let last = performance.now();
    const step = (ts) => {
      if (theme || !document.getElementById("holPrev")) { prevRaf = 0; return; }
      prevRaf = requestAnimationFrame(step);
      if (ts - last < 31 && clock > 0) return;
      const ok = !document.hidden && !powerSave && !reduced.matches && !A.lite;
      if (ok) clock += min(ts - last, 100) / 1000;
      last = ts; drawPreview();
      if (!ok) { cancelAnimationFrame(prevRaf); prevRaf = 0; }
    };
    prevRaf = requestAnimationFrame(step);
  }
  A.holPreview = () => { if (theme) { const t = document.getElementById("holPrev"); if (t) loadSet(t.dataset.th); kick(); } else drawPreviewOnly(); };

  /* ---------- настройки: Оформление → Праздничные темы ---------- */
  const esc = (x) => A.esc(x);
  const fmt = (d) => A.fmtDate(d, { year: true });
  const NY_SW = [["snow", "Снегопад при входе (5 секунд)"], ["backdrop", "Полупрозрачный снег на фоне"], ["border", "Хвойная кайма и золотые искорки"], ["caps", "Снежные шапки с сосульками на карточках"], ["garland", "Гирлянда вверху экрана"], ["santa", "Сани Санты на главной (раз в ~35 с)"]];
  const HW_SW = [["leaves", "Листопад при входе"], ["fog", "Туман над кладбищем"], ["eyes", "Красные глаза в дуплах"], ["hand", "Рука из могилы"], ["crows", "Вороны на ветках"], ["bats", "Летучие мыши"], ["webs", "Пауки на паутине и крыса в норе"], ["caps", "Шапки-холмики на карточках"], ["spiders", "Паучки и крысы на шапках"], ["witch", "Ведьма на метле у луны"], ["ghost", "Привидение на главной (вместо саней)"]];
  A.HOL_SUB = { ny: "Новый год", hw: "Хэллоуин" };
  A.holidaySettingsHtml = (sub) => {
    const cfg = A.holCfg(), d = A.today(), cur = S.activeTheme(cfg, d);
    const state = cur ? "Сейчас включена: <b>" + (cur === "ny" ? "новогодняя" : "хэллоуинская") + "</b>" + (cfg[cur].preview ? " (тестовый режим)" : "") : "Сейчас праздничная тема не включена";
    if (!sub) {
      return '<div class="card"><p class="small">' + state + '.</p><p class="small muted">Украшения — только рисунок поверх и под интерфейсом: нажатия проходят сквозь. Анимации останавливаются в фоне, в энергосбережении, в облегчённой графике и при системной настройке «Уменьшить движение».</p></div>' +
        '<div class="card"><div class="list">' + [["ny", "Новый год", "15 декабря — 15 января", "snowflake"], ["hw", "Хэллоуин", "15 октября — 2 ноября", "ghost"]].map(([k, n, dt, ic]) =>
          '<a class="item" style="text-decoration:none;color:inherit" href="#/settings/holiday/' + k + '"><span class="ti" style="font-size:22px">{{i:' + ic + '}}</span><div class="tx"><b>' + n + "</b><small>" + dt + (cfg[k].preview ? " · тестовый режим" : cfg[k].auto ? " · автоматически" : " · выключено") + '</small></div><span class="muted">›</span></a>').join("") + "</div></div>";
    }
    const c = cfg[sub], st = A.HOL_STYLES[sub], ny = sub === "ny";
    const start = ny ? S.nyNextStart(d) : S.hwNextStart(d), end = ny ? S.nySeasonEnd(d) : S.hwSeasonEnd(d);
    const inSeason = ny ? S.nySeason(d) : S.hwSeason(d);
    const sw = (k, label, extra) => '<label class="switch"><input type="checkbox" data-c="holSw" data-k="' + k + '"' + (c[k] ? " checked" : "") + "><span></span>" + label + "</label>" + (extra || "");
    const isDark = document.documentElement.dataset.theme === "dark";
    let h = '<div class="card"><canvas id="holPrev" class="hol-prev" data-th="' + sub + '"></canvas>' +
      '<p class="small muted" style="margin-top:8px">' + (inSeason ? "Сезон идёт до " + fmt(end) : "Сезон: с " + fmt(start) + " по " + fmt(end)) + ". " + state + ".</p>" +
      sw("auto", "Автоматически в сезон (" + (ny ? "15.12 — 15.01" : "15.10 — 02.11") + ")") +
      sw("preview", "Тестовый режим — тема в любой день", '<p class="small muted" style="margin:-4px 0 4px 54px">Темы не смешиваются: тестовый режим ' + (ny ? "Хэллоуина" : "Нового года") + " при этом выключится.</p>") + "</div>";
    const swatch = (kind, i, css, name) => '<button class="hol-sw' + (c[kind] === i ? " on" : "") + '" data-a="holStyle" data-k="' + kind + '" data-v="' + i + '" title="' + esc(name) + '"><i style="background:' + css + '"></i><span>' + esc(name) + "</span></button>";
    h += '<div class="card"><h3>Фон</h3><div class="hol-sws">' + st.bg.map((b, i) => { const v = isDark ? b.dark : b.light; return swatch("bg", i, "radial-gradient(circle at 50% 30%," + v[0] + "," + v[1] + " 50%," + v[2] + ")", b.n); }).join("") + "</div>" +
      '<h3 style="margin-top:12px">Плашки</h3><div class="hol-sws">' + st.card.map((b, i) => { const v = isDark ? st.bg[c.bg].dark : st.bg[c.bg].light; return swatch("card", i, "linear-gradient(" + (isDark ? b.dark : b.light) + "," + (isDark ? b.dark : b.light) + "),radial-gradient(circle," + v[0] + "," + v[2] + ")", b.n); }).join("") + "</div></div>";
    h += '<div class="card"><h3>Украшения</h3>' + (ny ? NY_SW : HW_SW).map(([k, l]) => sw(k, l)).join("");
    if (ny) h += '<div class="small muted" style="margin:6px 0 4px">Гирлянда: нажатие на лампочку — вкл/выкл, удержание — следующий режим</div>' + A.seg(LIGHT_MODES.map((n, i) => [i, n]), c.lightsMode | 0, "holMode") + sw("lights", "Гирлянда горит");
    h += '</div><div class="card"><div class="btns" style="margin-top:0"><button class="btn primary" data-a="holLaunch">' + (ny ? "{{i:gift}} Запустить сани" : "{{i:ghost}} Запустить привидение") + '</button><button class="btn" data-a="holFallNow">' + (ny ? "{{i:snowflake}} Показать снегопад" : "{{i:leaf}} Показать листопад") + "</button></div>" +
      '<p class="small muted">Сани и привидение летают на экране «Сегодня» по полосе кнопок с датой. ' + (cur === sub ? "" : "Тема сейчас не включена — кнопки сработают в тестовом режиме.") + "</p></div>";
    return h;
  };
  A.holidaySettingsBind = (el, sub) => {
    const after = () => { A.save(); A.holApply(); A.holPreview(); };
    A.bind(el, {
      holSw(b) {
        const cfg = A.holCfg(), k = b.dataset.k;
        if (k === "preview") { S.setPreview(cfg, sub, b.checked); A.save(); A.holApply(); if (b.checked) A.holFall(); A.refresh(); return; }
        cfg[sub][k] = b.checked; after();
        if (k === "auto") A.refresh();
      },
      holStyle(b) { A.holCfg()[sub][b.dataset.k] = +b.dataset.v; after(); el.querySelectorAll('.hol-sw[data-k="' + b.dataset.k + '"]').forEach((x) => x.classList.toggle("on", x === b)); if (b.dataset.k === "bg") A.refresh(); },
      holMode(b) { const c = A.holCfg().ny; c.lightsMode = +b.dataset.v; c.lights = true; after(); A.refresh(); },
      holLaunch() { if (A.holTheme() !== sub) return A.toast("Включите тему или тестовый режим"); A.holLaunch(); },
      holFallNow() { if (A.holTheme() !== sub) return A.toast("Включите тему или тестовый режим"); if (A.lite || reduced.matches || powerSave) return A.toast("Анимации остановлены: облегчённая графика, энергосбережение или «уменьшить движение»"); A.holFall(); }
    });
    A.holPreview();
  };

  /* ---------- жизненный цикл ---------- */
  A.holPause = () => { paused = true; stoppedAt = Date.now(); stop(); };
  A.holResume = () => {
    paused = false;
    checkPower();
    if (theme && S.isNewEntry(stoppedAt, Date.now())) startFall();
    if (S.activeTheme(A.holCfg(), A.today()) !== theme) A.holApply(); else kick();
  };
  document.addEventListener("visibilitychange", () => { if (document.hidden) { if (!paused) { stoppedAt = Date.now(); stop(); } } else if (!paused) A.holResume(); });
  const checkPower = () => { try { const v = !!(A.native && A.native.powerSave && A.native.powerSave()); if (v !== powerSave) { powerSave = v; kick(); } } catch (e) {} };
  setInterval(() => { if (theme) checkPower(); }, 30000);
  if (reduced.addEventListener) reduced.addEventListener("change", () => kick());
  window.addEventListener("resize", () => { velvetKey = ""; measureInset(); kick(); });
  // Смена даты, пока приложение открыто (например, в полночь 15 декабря): тема включается и выключается сама.
  setInterval(() => { try { if (S.activeTheme(A.holCfg(), A.today()) !== theme) A.holApply(); } catch (e) {} }, 60000);
  // Экран перерисован — заново находим карточки; прокрутка — перерисовать шапки, если таймер стоит.
  const hookMain = () => {
    const m = mainEl(); if (!m) return;
    new MutationObserver(() => { refreshCards(); if (theme) kick(); else if (document.getElementById("holPrev")) A.holPreview(); }).observe(m, { childList: true });
    m.addEventListener("scroll", () => { if (theme && !animating) kick(); }, { passive: true });
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", hookMain); else hookMain();
})();
