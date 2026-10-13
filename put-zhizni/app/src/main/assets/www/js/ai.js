/* ИИ-помощник через OpenRouter (бесплатные модели).
   Ключ (sk-or-…) хранится только на телефоне, отдельно от базы — в резервные копии и экспорт он не попадает.
   Список бесплатных моделей берётся с openrouter.ai/api/v1/models; если модель недоступна или перегружена,
   запрос автоматически повторяется на следующей бесплатной модели. */
(function () {
  "use strict";
  const A = window.App;
  const { esc } = A;
  const API = "https://openrouter.ai/api/v1";
  const KEY = "pz-ai-key", CFG = "pz-ai-cfg", MODELS = "pz-ai-models";
  const ls = { get: (k) => { try { return localStorage.getItem(k); } catch (e) { return null; } }, set: (k, v) => { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch (e) {} } };
  // запасные варианты на случай, если список моделей не загрузился
  const FALLBACK_TEXT = ["deepseek/deepseek-chat-v3-0324:free", "meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free", "mistralai/mistral-small-3.2-24b-instruct:free"];
  const FALLBACK_VISION = ["google/gemma-3-27b-it:free", "qwen/qwen2.5-vl-72b-instruct:free", "meta-llama/llama-4-maverick:free", "mistralai/mistral-small-3.2-24b-instruct:free"];

  const ai = (A.ai = {});
  ai.key = () => ls.get(KEY) || "";
  ai.ready = () => /^sk-or-/.test(ai.key());
  ai.cfg = () => { try { return Object.assign({ model: "", vmodel: "" }, JSON.parse(ls.get(CFG) || "{}")); } catch (e) { return { model: "", vmodel: "" }; } };
  const saveCfg = (c) => ls.set(CFG, JSON.stringify(c));

  /* ---------- список бесплатных моделей ---------- */
  ai.models = async (force) => {
    try {
      const c = JSON.parse(ls.get(MODELS) || "null");
      if (c && !force && Date.now() - c.t < 3 * 864e5) return c.list;
    } catch (e) {}
    const r = await fetch(API + "/models");
    if (!r.ok) throw new Error("HTTP " + r.status);
    const j = await r.json();
    const list = (j.data || []).filter((m) => /:free$/.test(m.id) || (m.pricing && +m.pricing.prompt === 0 && +m.pricing.completion === 0 && !/^openrouter\//.test(m.id)))
      .map((m) => ({ id: m.id, name: m.name || m.id, ctx: m.context_length || 0, vision: ((m.architecture && (m.architecture.input_modalities || [])) || []).includes("image") || /image/.test((m.architecture && m.architecture.modality) || "") }))
      .sort((a, b) => b.ctx - a.ctx);
    ls.set(MODELS, JSON.stringify({ t: Date.now(), list }));
    return list;
  };
  const candidates = async (vision) => {
    const c = ai.cfg(); let list = [];
    try { list = (await ai.models()).filter((m) => !vision || m.vision).map((m) => m.id); } catch (e) {}
    const pref = vision ? c.vmodel : c.model;
    const out = [pref].concat(list.slice(0, 6), vision ? FALLBACK_VISION : FALLBACK_TEXT).filter(Boolean);
    return [...new Set(out)];
  };

  /* ---------- запрос ---------- */
  // messages — в формате OpenAI; opts: {vision, system, temperature, max}
  ai.chat = async (messages, opts = {}) => {
    if (!ai.ready()) throw new Error("Нет ключа OpenRouter — добавьте его в Настройки → ИИ-помощник");
    const msgs = (opts.system ? [{ role: "system", content: opts.system }] : []).concat(messages);
    const models = await candidates(!!opts.vision);
    let last = "";
    for (const model of models.slice(0, 5)) {
      try {
        const r = await fetch(API + "/chat/completions", {
          method: "POST",
          headers: { Authorization: "Bearer " + ai.key(), "Content-Type": "application/json", "HTTP-Referer": "https://putzhizni.app", "X-Title": "Put zhizni" },
          body: JSON.stringify({ model, messages: msgs, temperature: opts.temperature ?? 0.7, max_tokens: opts.max || 1800 })
        });
        const j = await r.json().catch(() => ({}));
        if (r.status === 401) throw Object.assign(new Error("Ключ не принят OpenRouter — проверьте, что он скопирован полностью"), { fatal: true });
        if (!r.ok || j.error) { last = (j.error && j.error.message) || "HTTP " + r.status; continue; }
        const text = j.choices && j.choices[0] && j.choices[0].message && j.choices[0].message.content;
        if (!text) { last = "пустой ответ"; continue; }
        ai.lastModel = model;
        return String(text).trim();
      } catch (e) { if (e.fatal) throw e; last = e.message; }
    }
    throw new Error("Бесплатные модели сейчас не ответили (" + last + "). Попробуйте через минуту — у бесплатного тарифа есть лимит запросов в минуту и в день.");
  };
  ai.ask = (prompt, opts = {}) => ai.chat([{ role: "user", content: prompt }], opts);
  // фото → data URL до 1280 px по длинной стороне (меньше трафика и лимитов)
  ai.shrink = (file, max = 1280) => new Promise((res, rej) => {
    const img = new Image(), url = URL.createObjectURL(file);
    img.onload = () => { const k = Math.min(1, max / Math.max(img.width, img.height)), c = document.createElement("canvas"); c.width = Math.round(img.width * k); c.height = Math.round(img.height * k); c.getContext("2d").drawImage(img, 0, 0, c.width, c.height); URL.revokeObjectURL(url); res(c.toDataURL("image/jpeg", 0.85)); };
    img.onerror = () => rej(new Error("Не удалось прочитать фото"));
    img.src = url;
  });
  ai.askImage = (prompt, dataUrl, opts = {}) => ai.chat([{ role: "user", content: [{ type: "text", text: prompt }, { type: "image_url", image_url: { url: dataUrl } }] }], Object.assign({}, opts, { vision: true }));
  // простое форматирование ответа: абзацы, **жирный**, списки
  ai.fmt = (t) => esc(String(t || "")).replace(/^#{1,4}\s*(.+)$/gm, "<b>$1</b>").replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/^\s*[-*•]\s+/gm, "• ").split(/\n{2,}/).map((p) => "<p>" + p.replace(/\n/g, "<br>") + "</p>").join("");
  ai.need = () => '<div class="card peach"><h3>{{i:sparkles}} Нужен бесплатный ключ ИИ</h3><p class="small">Один раз: Настройки → «ИИ-помощник (бесплатно)». Это займёт пару минут, карта не нужна.</p><a class="btn sm primary" href="#/settings/ai">Подключить</a></div>';

  /* ---------- настройки ---------- */
  A.aiSettingsHtml = () => {
    const k = ai.key(), c = ai.cfg();
    return '<div class="card"><h3>{{i:sparkles}} ИИ-помощник (бесплатно)</h3><p class="small">Помощник работает через сервис OpenRouter на бесплатных моделях. Нужен личный ключ — он бесплатный, банковская карта не требуется.</p>' +
      '<ol class="small"><li>Нажмите кнопку ниже — откроется сайт openrouter.ai.</li><li>Зарегистрируйтесь (можно через Google).</li><li>Откройте <b>Keys → Create key</b> и скопируйте ключ — он начинается с <code>sk-or-</code>.</li><li>Вставьте ключ в поле ниже и нажмите «Сохранить».</li></ol>' +
      '<a class="btn primary block" href="https://openrouter.ai/keys">{{i:sparkles}} Открыть openrouter.ai — получить ключ</a></div>' +
      '<div class="card"><div class="form"><div class="fld full"><label>Ключ OpenRouter</label><input id="aiKey" type="password" autocomplete="off" placeholder="sk-or-v1-…" value="' + esc(k) + '"></div></div>' +
      '<div class="btns"><button class="btn primary" data-a="aiSave">Сохранить</button><button class="btn" data-a="aiTest">Проверить</button>' + (k ? '<button class="btn ghost" data-a="aiDel">Удалить ключ</button>' : "") + '</div><p class="small muted" id="aiState" style="margin-top:8px">' + (ai.ready() ? "Ключ сохранён на этом телефоне." : "Ключ не добавлен.") + "</p></div>" +
      '<div class="card"><h3>Модели</h3><p class="small muted">По умолчанию приложение само выбирает доступную бесплатную модель и переключается на другую, если первая занята.</p><div class="form">' +
      '<div class="fld full"><label>Для текста</label><select id="aiM"><option value="">Автовыбор</option></select></div>' +
      '<div class="fld full"><label>Для фото (распознавание раскладов)</label><select id="aiV"><option value="">Автовыбор</option></select></div></div>' +
      '<button class="btn sm ghost" data-a="aiReload" style="margin-top:8px">Обновить список моделей</button></div>' +
      '<p class="small muted" style="padding:0 6px">Запросы уходят напрямую на openrouter.ai. Бесплатные модели имеют лимит запросов в минуту и в день; ответы ИИ могут быть неточными. Не отправляйте пароли и данные карт.</p>';
  };
  A.aiSettingsBind = (el) => {
    const st = el.querySelector("#aiState");
    const fill = async (force) => {
      try {
        const list = await ai.models(force), c = ai.cfg();
        const opt = (m, sel) => '<option value="' + esc(m.id) + '"' + (m.id === sel ? " selected" : "") + ">" + esc(m.name.replace(/\s*\(free\)/i, "")) + "</option>";
        el.querySelector("#aiM").innerHTML = '<option value="">Автовыбор (' + list.length + " бесплатных)</option>" + list.map((m) => opt(m, c.model)).join("");
        el.querySelector("#aiV").innerHTML = '<option value="">Автовыбор (' + list.filter((m) => m.vision).length + " понимают фото)</option>" + list.filter((m) => m.vision).map((m) => opt(m, c.vmodel)).join("");
      } catch (e) { st.textContent = "Не удалось загрузить список моделей: " + e.message; }
    };
    fill(false);
    el.querySelector("#aiM").addEventListener("change", (e) => { const c = ai.cfg(); c.model = e.target.value; saveCfg(c); });
    el.querySelector("#aiV").addEventListener("change", (e) => { const c = ai.cfg(); c.vmodel = e.target.value; saveCfg(c); });
    A.bind(el, {
      aiSave() { const v = el.querySelector("#aiKey").value.trim(); if (!/^sk-or-/.test(v)) { A.toast("Ключ должен начинаться с sk-or-"); return; } ls.set(KEY, v); st.textContent = "Ключ сохранён на этом телефоне."; A.toast("Ключ сохранён"); },
      async aiTest() { st.textContent = "Проверяю…"; try { const t = await ai.ask("Ответь одним словом по-русски: работает?", { max: 20, temperature: 0 }); st.textContent = "Работает ✓ Ответ модели (" + ai.lastModel + "): " + t; } catch (e) { st.textContent = e.message; } },
      aiDel() { A.confirm("Удалить ключ с телефона?", () => { ls.set(KEY, null); A.refresh(); }); },
      aiReload() { fill(true); }
    });
  };

  /* ---------- экран чата ---------- */
  const HKEY = "pz-ai-chat";
  const hist = () => { try { return JSON.parse(ls.get(HKEY) || "[]"); } catch (e) { return []; } };
  const SYS = "Ты — дружелюбный помощник в личном приложении «Путь жизни» (здоровье, привычки, финансы, обучение, нумерология, таро). Отвечай по-русски, по делу, без воды. Медицинские и финансовые вопросы — с оговоркой, что это не замена специалисту.";
  A.view("ai", {
    title: "ИИ-помощник", tab: "more",
    render() {
      if (!ai.ready()) return ai.need();
      const H = hist();
      return '<div id="aiLog">' + (H.length ? H.map((m) => '<div class="ai-msg ' + m.role + '">' + (m.role === "user" ? esc(m.content) : ai.fmt(m.content)) + "</div>").join("") : '<div class="card tint"><p class="small">Спросите что угодно: разобрать траты, составить план тренировок, объяснить грамматику, истолковать карту дня.</p></div>') + "</div>" +
        '<div class="ai-bar"><textarea id="aiIn" rows="2" placeholder="Ваш вопрос…"></textarea><button class="btn primary" data-a="aiSend">{{i:message-circle-question}}</button></div>' +
        (H.length ? '<button class="link small" data-a="aiClear">Очистить переписку</button>' : "");
    },
    bind(el) {
      if (!ai.ready()) return;
      const log = el.querySelector("#aiLog"); log.lastElementChild && log.lastElementChild.scrollIntoView({ block: "end" });
      A.bind(el, {
        async aiSend() {
          const i = el.querySelector("#aiIn"), q = i.value.trim(); if (!q) return;
          const H = hist(); H.push({ role: "user", content: q }); ls.set(HKEY, JSON.stringify(H.slice(-30)));
          i.value = ""; const tip = log.querySelector(".card.tint"); if (tip) tip.remove(); log.insertAdjacentHTML("beforeend", '<div class="ai-msg user">' + esc(q) + '</div><div class="ai-msg assistant" id="aiWait"><span class="muted">Думаю…</span></div>');
          log.lastElementChild.scrollIntoView({ block: "end" });
          try { const a = await ai.chat(H.slice(-12), { system: SYS }); H.push({ role: "assistant", content: a }); ls.set(HKEY, JSON.stringify(H.slice(-30))); el.querySelector("#aiWait").outerHTML = '<div class="ai-msg assistant">' + ai.fmt(a) + "</div>"; }
          catch (e) { el.querySelector("#aiWait").outerHTML = '<div class="ai-msg assistant warn">' + esc(e.message) + "</div>"; }
        },
        aiClear() { ls.set(HKEY, null); A.refresh(); }
      });
    }
  });
})();
