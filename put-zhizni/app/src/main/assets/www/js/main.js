/* Запуск: загрузка данных, тема, блокировка PIN, фоновые задачи. */
(function () {
  "use strict";
  const A = window.App;

  A.applyTheme = () => {
    const p = A.db().profile;
    const dark = p.theme === "dark" || (p.theme === "system" && window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    document.documentElement.dataset.accent = p.accent || "lavender";
    const bg = dark ? "#1C1A22" : "#FBF7F2";
    document.querySelector('meta[name="theme-color"]').content = bg;
    if (A.native) try { A.native.setBars(bg, !dark); } catch (e) {}
  };
  if (window.matchMedia) matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => A.applyTheme());

  /* ---------- блокировка ---------- */
  let entered = "", hiddenAt = 0;
  const lock = document.getElementById("lock");
  const dots = () => { document.getElementById("pinDots").innerHTML = Array.from({ length: Math.max(4, entered.length) }, (_, i) => '<i class="' + (i < entered.length ? "on" : "") + '"></i>').join(""); };
  const unlock = () => { lock.hidden = true; entered = ""; };
  A.lock = () => {
    const p = A.db().profile;
    if (!p.pin) return;
    lock.hidden = false; entered = ""; dots();
    document.getElementById("lockMsg").textContent = "Введите PIN-код";
    if (p.bio && A.native && A.native.biometricAvailable()) A.nativeCall("auth", () => A.native.authenticate()).then((ok) => { if (ok === "true") unlock(); });
  };
  const pad = document.getElementById("pinpad");
  pad.innerHTML = [1, 2, 3, 4, 5, 6, 7, 8, 9, "bio", 0, "del"].map((k) => '<button data-k="' + k + '" aria-label="' + (k === "del" ? "Стереть" : k === "bio" ? "Биометрия" : k) + '">' + (k === "del" ? "⌫" : k === "bio" ? "{{i:fingerprint}}" : k) + "</button>").join("");
  pad.addEventListener("click", async (e) => {
    const b = e.target.closest("button"); if (!b) return;
    const k = b.dataset.k, p = A.db().profile;
    if (k === "del") entered = entered.slice(0, -1);
    else if (k === "bio") { if (p.bio && A.native) { const ok = await A.nativeCall("auth", () => A.native.authenticate()); if (ok === "true") return unlock(); } return; }
    else if (entered.length < 8) entered += k;
    dots();
    if (entered.length >= 4) {
      const h = await A.sha256("pz:" + entered);
      if (h === p.pin) unlock();
      else if (entered.length === 8) { entered = ""; dots(); document.getElementById("lockMsg").textContent = "Неверный PIN"; A.vibe(60); }
    }
  });

  /* ---------- жизненный цикл ---------- */
  const daily = () => {
    A.processRecurring();
    A.syncReminders();
    A.weatherRefresh(false);
    A.syncSteps(false);
    if (A.db().profile.hc) A.syncHealth(false);
    if (A.native && A.db().profile.smsImport) A.smsImport(false);
    if (A.native && A.db().profile.sleepAuto) {
      const day = A.day(A.today());
      if (!day.sleep && new Date().getHours() >= 5 && A.native.usageAllowed()) { const r = A.native.detectSleep(A.today()); if (r) { try { const o = JSON.parse(r); day.sleep = { bed: o.bed, wake: o.wake, auto: true }; A.save(); } catch (e) {} } }
    }
    A.updateWidget();
  };
  // Данные для виджета на главном экране.
  A.updateWidget = () => {
    if (!A.native || !A.native.updateWidget) return;
    const d = A.today(), p = A.db().profile, day = A.dayGet(d) || {}, tr = A.weightTrend(14);
    const tasks = A.col("tasks").filter((t) => !t.done && !t.archived && t.date && t.date <= d).sort((a, b) => (a.prio || 2) - (b.prio || 2) || ((a.time || "99") > (b.time || "99") ? 1 : -1)).slice(0, 2).map((t) => (t.time ? t.time + " " : "") + t.title);
    const spark = A.lastDays(7).map((x) => (A.dayGet(x) || {}).weight ?? null);
    try { A.native.updateWidget(JSON.stringify({ date: A.DOW_FULL[A.dow(d) - 1] + ", " + A.fmtDate(d), steps: day.steps || 0, goal: p.stepsGoal || 8000, burn: A.burnDay(d), weight: A.lastWeight(d), arrow: tr.arrow, spark, tasks })); } catch (e) {}
  };
  A.on("saved", () => {
    clearTimeout(A._wT); A._wT = setTimeout(A.updateWidget, 1500);
    const p = A.db().profile;
    if (A.native && A.native.setFlags && (A._fl !== "" + p.hc + p.smsImport)) { A._fl = "" + p.hc + p.smsImport; try { A.native.setFlags(!!p.hc, !!p.smsImport); } catch (e) {} }
  });
  window.__onPause = () => { hiddenAt = Date.now(); A.saveNow(); };
  window.__onResume = () => {
    if (hiddenAt && Date.now() - hiddenAt > 60000) A.lock();
    hiddenAt = 0;
    daily();
    A.refresh();
  };
  document.addEventListener("visibilitychange", () => { if (!A.native) { if (document.hidden) hiddenAt = Date.now(); else window.__onResume(); } });

  // Смена дня, пока приложение открыто.
  let lastDay = A.today();
  setInterval(() => { if (A.today() !== lastDay) { lastDay = A.today(); daily(); A.refresh(); } }, 60000);

  /* ---------- старт ---------- */
  A.load();
  A.applyTheme();
  A.lock();
  if (!location.hash) location.replace("#/today");
  A.render();
  A.saveNow();
  setTimeout(daily, 300);
})();
