/* Праздничные сезоны оформления — чистые функции без DOM (проверяются тестами: put-zhizni/tests/holiday-season.test.js).
   Новый год: 15 декабря — 15 января включительно. Хэллоуин: 15 октября — 2 ноября включительно. По дате телефона. */
(function (root) {
  "use strict";
  // Дата — объект Date или строка "ГГГГ-ММ-ДД"; берём месяц и день по местному времени.
  const md = (d) => {
    if (typeof d === "string") { const p = d.split("-"); return { y: +p[0], m: +p[1], d: +p[2] }; }
    return { y: d.getFullYear(), m: d.getMonth() + 1, d: d.getDate() };
  };
  const iso = (y, m, d) => y + "-" + String(m).padStart(2, "0") + "-" + String(d).padStart(2, "0");

  const S = {
    /** Через сколько после ухода из приложения возвращение считается новым входом (снова снег / листопад). */
    NEW_ENTRY_AFTER_MS: 5 * 60 * 1000,
    /** Сколько идёт снегопад (листопад) при входе. */
    FALL_MS: 5000,

    nySeason(d) { const x = md(d); return (x.m === 12 && x.d >= 15) || (x.m === 1 && x.d <= 15); },
    hwSeason(d) { const x = md(d); return (x.m === 10 && x.d >= 15) || (x.m === 11 && x.d <= 2); },

    /** Тема действует: автоматический режим и сезон — или тестовый режим в любой день. */
    themeActive(auto, inSeason, preview) { return !!preview || (!!auto && !!inSeason); },

    /**
     * Какая тема сейчас включена: "ny", "hw" или null. Темы не смешиваются: тестовый режим одной темы
     * выключает другую; в сезон без тестового режима — та, чей сезон (сезоны не пересекаются).
     */
    activeTheme(cfg, d) {
      const ny = (cfg && cfg.ny) || {}, hw = (cfg && cfg.hw) || {};
      if (hw.preview) return "hw";
      if (ny.preview) return "ny";
      if (S.themeActive(ny.auto !== false, S.nySeason(d), false)) return "ny";
      if (S.themeActive(hw.auto !== false, S.hwSeason(d), false)) return "hw";
      return null;
    },

    /** Включить тестовый режим одной темы: тестовый режим другой при этом выключается. Возвращает cfg. */
    setPreview(cfg, theme, on) {
      cfg[theme].preview = !!on;
      if (on) cfg[theme === "ny" ? "hw" : "ny"].preview = false;
      return cfg;
    },

    /** Приложение открыли заново: первый показ после запуска (stoppedAt = 0) или возврат после 5 минут в фоне. */
    isNewEntry(stoppedAt, now) { return !stoppedAt || now - stoppedAt >= S.NEW_ENTRY_AFTER_MS; },

    /** Ближайший день начала сезона (сегодня, если сезон уже идёт) — "ГГГГ-ММ-ДД". */
    nyNextStart(d) {
      const x = md(d);
      if (x.m === 1 && x.d <= 15) return iso(x.y - 1, 12, 15);
      return iso(x.y, 12, 15);
    },
    /** Последний день идущего или ближайшего новогоднего сезона. */
    nySeasonEnd(d) { const x = md(d); return x.m === 1 && x.d <= 15 ? iso(x.y, 1, 15) : iso(x.y + 1, 1, 15); },
    hwNextStart(d) { const x = md(d); return x.m > 11 || (x.m === 11 && x.d > 2) ? iso(x.y + 1, 10, 15) : iso(x.y, 10, 15); },
    hwSeasonEnd(d) { const x = md(d); return x.m > 11 || (x.m === 11 && x.d > 2) ? iso(x.y + 1, 11, 2) : iso(x.y, 11, 2); }
  };

  if (typeof module !== "undefined" && module.exports) module.exports = S;
  else root.HolidaySeason = S;
})(typeof window !== "undefined" ? window : this);
