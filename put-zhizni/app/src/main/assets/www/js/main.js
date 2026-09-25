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
  pad.innerHTML = [1, 2, 3, 4, 5, 6, 7, 8, 9, "bio", 0, "del"].map((k) => '<button data-k="' + k + '" aria-label="' + (k === "del" ? "Стереть" : k === "bio" ? "Биометрия" : k) + '">' + (k === "del" ? "⌫" : k === "bio" ? "☝" : k) + "</button>").join("");
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
  };
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
