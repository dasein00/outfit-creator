// Тесты праздничных сезонов: node --test put-zhizni/tests/*.test.js
const test = require("node:test");
const assert = require("node:assert/strict");
const S = require("../app/src/main/assets/www/js/holiday-season.js");

test("новогодний сезон: 15 декабря — 15 января включительно", () => {
  assert.equal(S.nySeason("2026-12-14"), false);
  assert.equal(S.nySeason("2026-12-15"), true);
  assert.equal(S.nySeason("2026-12-31"), true);
  assert.equal(S.nySeason("2027-01-01"), true);
  assert.equal(S.nySeason("2027-01-15"), true);
  assert.equal(S.nySeason("2027-01-16"), false);
  assert.equal(S.nySeason("2026-07-01"), false);
  assert.equal(S.nySeason(new Date(2026, 11, 20)), true);
});

test("хэллоуинский сезон: 15 октября — 2 ноября включительно", () => {
  assert.equal(S.hwSeason("2026-10-14"), false);
  assert.equal(S.hwSeason("2026-10-15"), true);
  assert.equal(S.hwSeason("2026-10-31"), true);
  assert.equal(S.hwSeason("2026-11-02"), true);
  assert.equal(S.hwSeason("2026-11-03"), false);
  assert.equal(S.hwSeason(new Date(2026, 9, 31)), true);
});

test("тема: авто в сезон, тестовый режим в любой день, выключенный авто", () => {
  const def = { ny: { auto: true }, hw: { auto: true } };
  assert.equal(S.activeTheme(def, "2026-12-20"), "ny");
  assert.equal(S.activeTheme(def, "2026-10-31"), "hw");
  assert.equal(S.activeTheme(def, "2026-06-01"), null);
  assert.equal(S.activeTheme({ ny: { auto: false }, hw: {} }, "2026-12-20"), null);
  assert.equal(S.activeTheme({ ny: { auto: false, preview: true }, hw: {} }, "2026-06-01"), "ny");
  assert.equal(S.activeTheme({ ny: {}, hw: { preview: true } }, "2026-12-20"), "hw");
  assert.equal(S.activeTheme({}, "2027-01-10"), "ny");
});

test("тестовые режимы не смешиваются", () => {
  const c = { ny: { preview: true }, hw: { preview: false } };
  S.setPreview(c, "hw", true);
  assert.deepEqual([c.ny.preview, c.hw.preview], [false, true]);
  S.setPreview(c, "ny", true);
  assert.deepEqual([c.ny.preview, c.hw.preview], [true, false]);
  S.setPreview(c, "ny", false);
  assert.deepEqual([c.ny.preview, c.hw.preview], [false, false]);
});

test("новый вход: первый запуск или 5 минут в фоне", () => {
  assert.equal(S.isNewEntry(0, 1000), true);
  assert.equal(S.isNewEntry(1000, 1000 + 4 * 60 * 1000), false);
  assert.equal(S.isNewEntry(1000, 1000 + 5 * 60 * 1000), true);
});

test("ближайшее начало и конец сезона", () => {
  assert.equal(S.nyNextStart("2026-11-12"), "2026-12-15");
  assert.equal(S.nyNextStart("2026-12-20"), "2026-12-15");
  assert.equal(S.nyNextStart("2027-01-10"), "2026-12-15");
  assert.equal(S.nyNextStart("2027-01-20"), "2027-12-15");
  assert.equal(S.nySeasonEnd("2026-12-20"), "2027-01-15");
  assert.equal(S.nySeasonEnd("2027-01-03"), "2027-01-15");
  assert.equal(S.hwNextStart("2026-11-12"), "2027-10-15");
  assert.equal(S.hwNextStart("2026-10-20"), "2026-10-15");
  assert.equal(S.hwNextStart("2026-03-01"), "2026-10-15");
  assert.equal(S.hwSeasonEnd("2026-10-20"), "2026-11-02");
  assert.equal(S.hwSeasonEnd("2026-12-01"), "2027-11-02");
});
