/* Читалка: EPUB, FB2 (и .fb2.zip), MOBI/AZW3, PDF, DjVu, TXT, DOCX, RTF, HTML, CBZ.
   Файлы книг хранятся в IndexedDB «pz-books», сведения о книгах и прогресс — в коллекции rbooks.
   Библиотеки (lib/): pdf.js — Apache-2.0, DjVu.js — The Unlicense, lingo-reader mobi-parser — MIT, JSZip — MIT. */
(function () {
  "use strict";
  const A = window.App;
  const { esc, today } = A;

  /* ---------- хранилище файлов ---------- */
  const idb = () => new Promise((res, rej) => { const r = indexedDB.open("pz-books", 1); r.onupgradeneeded = () => r.result.createObjectStore("f"); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });
  const fput = async (id, buf) => { const d = await idb(); await new Promise((res, rej) => { const tx = d.transaction("f", "readwrite"); tx.objectStore("f").put(buf, id); tx.oncomplete = res; tx.onerror = () => rej(tx.error); }); };
  const fget = async (id) => { const d = await idb(); return new Promise((res) => { const r = d.transaction("f").objectStore("f").get(id); r.onsuccess = () => res(r.result || null); r.onerror = () => res(null); }); };
  const fdel = async (id) => { try { const d = await idb(); d.transaction("f", "readwrite").objectStore("f").delete(id); } catch (e) {} };

  /* ---------- загрузка библиотек по требованию ---------- */
  const loaded = {};
  const script = (src) => loaded[src] || (loaded[src] = new Promise((res, rej) => { const s = document.createElement("script"); s.src = src; s.onload = res; s.onerror = () => rej(new Error("Не загрузилась библиотека " + src)); document.head.appendChild(s); }));
  const libs = () => script("lib/readerlibs.js").then(() => window.ReaderLibs);
  let pdfjs = null;
  const getPdf = async () => { if (!pdfjs) { pdfjs = await import(new URL("lib/pdf.min.mjs", location.href).href); pdfjs.GlobalWorkerOptions.workerSrc = new URL("lib/pdf.worker.min.mjs", location.href).href; } return pdfjs; };

  /* ---------- форматы ---------- */
  const FORMATS = { epub: "EPUB", fb2: "FB2", fbz: "FB2", mobi: "MOBI", azw3: "AZW3", azw: "AZW", prc: "MOBI", kf8: "AZW3", pdf: "PDF", djvu: "DjVu", djv: "DjVu", txt: "TXT", docx: "DOCX", rtf: "RTF", html: "HTML", htm: "HTML", xhtml: "HTML", cbz: "CBZ" };
  const extOf = (name) => { const n = name.toLowerCase(); if (n.endsWith(".fb2.zip")) return "fbz"; const m = /\.([a-z0-9]+)$/.exec(n); return m ? m[1] : ""; };
  const ACCEPT = ".epub,.fb2,.zip,.mobi,.azw3,.azw,.prc,.pdf,.djvu,.djv,.txt,.docx,.rtf,.html,.htm,.cbz";

  const decodeText = (u8) => {
    const head = new TextDecoder("latin1").decode(u8.slice(0, 200));
    const m = /encoding=["']([\w-]+)["']/i.exec(head);
    if (m) { try { return new TextDecoder(m[1]).decode(u8); } catch (e) {} }
    if (u8[0] === 0xff && u8[1] === 0xfe) return new TextDecoder("utf-16le").decode(u8);
    try { return new TextDecoder("utf-8", { fatal: true }).decode(u8); } catch (e) { return new TextDecoder("windows-1251").decode(u8); }
  };
  const sanitize = (html) => html.replace(/<script[\s\S]*?<\/script>/gi, "").replace(/<(iframe|object|embed|form|link|meta|style)[^>]*>([\s\S]*?<\/\1>)?/gi, "").replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, "").replace(/javascript:/gi, "");
  const paras = (t) => t.split(/\r?\n\s*\r?\n|\r?\n(?=\s{2,}|\t)/).map((p) => p.trim()).filter(Boolean).map((p) => "<p>" + esc(p.replace(/\s*\r?\n\s*/g, " ")) + "</p>").join("");
  const blobUrl = (bytes, mime) => URL.createObjectURL(new Blob([bytes], { type: mime || "application/octet-stream" }));
  const mimeByExt = (p) => ({ jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif", svg: "image/svg+xml", webp: "image/webp" }[(p.split(".").pop() || "").toLowerCase()] || "application/octet-stream");

  // Каждый парсер возвращает {title, author, cover?, chapters:[{title, html}], toc:[{t, i}]} или {fixed: "pdf"|"djvu", ...}
  const P = {};
  P.txt = async (u8) => { const t = decodeText(u8); const parts = []; const size = 60000; for (let i = 0; i < t.length; i += size) parts.push(t.slice(i, i + size)); return { chapters: parts.map((p, i) => ({ title: "Часть " + (i + 1), html: paras(p) })) }; };
  P.html = async (u8) => { const doc = new DOMParser().parseFromString(decodeText(u8), "text/html"); return { title: (doc.title || "").trim(), chapters: [{ title: doc.title || "Текст", html: sanitize(doc.body ? doc.body.innerHTML : "") }] }; };
  P.htm = P.html; P.xhtml = P.html;
  P.rtf = async (u8) => {
    const raw = new TextDecoder("latin1").decode(u8);
    const cp = (/\\ansicpg(\d+)/.exec(raw) || [])[1] || "1251";
    const dec = new TextDecoder("windows-" + cp);
    let out = "", i = 0, depth = 0, skip = [];
    const skipDest = /^(fonttbl|colortbl|stylesheet|info|pict|object|header|footer|\*)/;
    while (i < raw.length) {
      const c = raw[i];
      if (c === "{") { depth++; skip.push(skip[skip.length - 1] || false); i++; continue; }
      if (c === "}") { depth--; skip.pop(); i++; continue; }
      if (c === "\\") {
        const m = /^\\([a-z*]+)(-?\d+)? ?|^\\'([0-9a-f]{2})|^\\(.)/i.exec(raw.slice(i, i + 40));
        if (!m) { i++; continue; }
        i += m[0].length;
        if (skip[skip.length - 1]) continue;
        if (m[3]) { out += dec.decode(new Uint8Array([parseInt(m[3], 16)])); continue; }
        if (m[4]) { if (m[4] === "*") skip[skip.length - 1] = true; else if ("\\{}".includes(m[4])) out += m[4]; continue; }
        const w = m[1];
        if (skipDest.test(w) && raw[i - m[0].length - 1] === "{") { skip[skip.length - 1] = true; continue; }
        if (w === "par" || w === "line") out += "\n\n"; else if (w === "tab") out += "\t"; else if (w === "u" && m[2]) { let n = +m[2]; if (n < 0) n += 65536; out += String.fromCharCode(n); if (raw[i] === "?") i++; else if (raw[i] === "\\" && raw[i + 1] === "'") i += 4; }
        continue;
      }
      if (!skip[skip.length - 1] && c !== "\r" && c !== "\n") out += c;
      i++;
    }
    return P.txt(new TextEncoder().encode(out));
  };
  P.docx = async (u8) => {
    const { JSZip } = await libs(); const z = await JSZip.loadAsync(u8);
    const xml = await z.file("word/document.xml").async("string");
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    const W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    let html = "";
    Array.from(doc.getElementsByTagNameNS(W, "p")).forEach((p) => {
      const st = p.getElementsByTagNameNS(W, "pStyle")[0], sv = st ? st.getAttributeNS(W, "val") || st.getAttribute("w:val") || "" : "";
      const runs = Array.from(p.getElementsByTagNameNS(W, "r")).map((r) => { const t = Array.from(r.getElementsByTagNameNS(W, "t")).map((x) => x.textContent).join(""); if (!t) return ""; const b = r.getElementsByTagNameNS(W, "b").length, it = r.getElementsByTagNameNS(W, "i").length; let s = esc(t); if (b) s = "<b>" + s + "</b>"; if (it) s = "<i>" + s + "</i>"; return s; }).join("");
      if (!runs.trim()) return;
      html += /heading|заголовок|title/i.test(sv) ? "<h2>" + runs + "</h2>" : "<p>" + runs + "</p>";
    });
    return { chapters: [{ title: "Документ", html }] };
  };
  const fb2FromXml = (xml) => {
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    const q = (sel, root) => (root || doc).getElementsByTagName(sel);
    const bins = {}; Array.from(q("binary")).forEach((b) => { const id = b.getAttribute("id"); try { const bytes = Uint8Array.from(atob(b.textContent.replace(/\s+/g, "")), (c) => c.charCodeAt(0)); bins[id] = blobUrl(bytes, b.getAttribute("content-type") || "image/jpeg"); } catch (e) {} });
    const href = (el) => (el.getAttribute("l:href") || el.getAttribute("xlink:href") || el.getAttribute("href") || Array.from(el.attributes).map((a) => (/href$/.test(a.name) ? a.value : "")).join("")).replace(/^#/, "");
    const conv = (node) => Array.from(node.childNodes).map((n) => {
      if (n.nodeType === 3) return esc(n.textContent);
      if (n.nodeType !== 1) return "";
      const t = n.localName;
      const inner = () => conv(n);
      switch (t) {
        case "p": return "<p>" + inner() + "</p>";
        case "emphasis": return "<i>" + inner() + "</i>";
        case "strong": return "<b>" + inner() + "</b>";
        case "strikethrough": return "<s>" + inner() + "</s>";
        case "sup": case "sub": return "<" + t + ">" + inner() + "</" + t + ">";
        case "title": return "<h2>" + inner() + "</h2>";
        case "subtitle": return "<h3>" + inner() + "</h3>";
        case "epigraph": return '<blockquote class="epi">' + inner() + "</blockquote>";
        case "cite": return "<blockquote>" + inner() + "</blockquote>";
        case "poem": case "stanza": return '<div class="poem">' + inner() + "</div>";
        case "v": return "<div>" + inner() + "</div>";
        case "text-author": return '<p class="ta">' + inner() + "</p>";
        case "empty-line": return "<br>";
        case "image": { const u = bins[href(n)]; return u ? '<img src="' + u + '" alt="">' : ""; }
        case "a": return '<span class="note">' + inner() + "</span>";
        case "section": return inner();
        default: return inner();
      }
    }).join("");
    const ti = q("title-info")[0];
    const title = ti && q("book-title", ti)[0] ? q("book-title", ti)[0].textContent.trim() : "";
    const au = ti && q("author", ti)[0] ? ["first-name", "last-name"].map((k) => (q(k, q("author", ti)[0])[0] || {}).textContent || "").join(" ").trim() : "";
    const cp = ti && q("coverpage", ti)[0] ? q("image", q("coverpage", ti)[0])[0] : null;
    const bodies = Array.from(q("body")).filter((b) => b.getAttribute("name") !== "notes");
    const chapters = [];
    bodies.forEach((body) => {
      const secs = Array.from(body.children).filter((c) => c.localName === "section");
      const lead = Array.from(body.children).filter((c) => c.localName !== "section");
      if (lead.length) chapters.push({ title: title || "Начало", html: lead.map((x) => conv({ childNodes: [x] })).join("") });
      secs.forEach((s) => { const t = Array.from(s.children).find((c) => c.localName === "title"); chapters.push({ title: t ? t.textContent.trim().replace(/\s+/g, " ") : "Глава " + (chapters.length + 1), html: conv(s) }); });
    });
    const notes = Array.from(q("body")).find((b) => b.getAttribute("name") === "notes");
    if (notes) chapters.push({ title: "Примечания", html: conv(notes) });
    return { title, author: au, cover: cp ? bins[href(cp)] : "", chapters };
  };
  P.fb2 = async (u8) => fb2FromXml(decodeText(u8));
  P.fbz = async (u8) => { const { JSZip } = await libs(); const z = await JSZip.loadAsync(u8); const f = Object.values(z.files).find((x) => /\.fb2$/i.test(x.name)); if (!f) throw new Error("В архиве нет файла .fb2"); return fb2FromXml(decodeText(await f.async("uint8array"))); };
  P.zip = async (u8) => { const { JSZip } = await libs(); const z = await JSZip.loadAsync(u8); if (Object.values(z.files).some((x) => /\.fb2$/i.test(x.name))) return P.fbz(u8); if (z.file("META-INF/container.xml")) return P.epub(u8); return P.cbz(u8); };
  P.epub = async (u8) => {
    const { JSZip } = await libs(); const z = await JSZip.loadAsync(u8);
    const rd = (p) => { const f = z.file(decodeURIComponent(p)) || z.file(p); return f ? f.async("string") : Promise.resolve(""); };
    const cont = new DOMParser().parseFromString(await rd("META-INF/container.xml"), "application/xml");
    const opfPath = cont.getElementsByTagName("rootfile")[0].getAttribute("full-path");
    const base = opfPath.includes("/") ? opfPath.slice(0, opfPath.lastIndexOf("/") + 1) : "";
    const opf = new DOMParser().parseFromString(await rd(opfPath), "application/xml");
    const manifest = {}; Array.from(opf.getElementsByTagName("item")).forEach((it) => (manifest[it.getAttribute("id")] = { href: it.getAttribute("href"), type: it.getAttribute("media-type"), props: it.getAttribute("properties") || "" }));
    const resolve = (from, rel) => { if (/^[a-z]+:/i.test(rel)) return rel; const parts = (from.slice(0, from.lastIndexOf("/") + 1) + rel).split("/"); const out = []; parts.forEach((p) => { if (p === "..") out.pop(); else if (p && p !== ".") out.push(p); }); return out.join("/"); };
    const md = (tag) => { const el = opf.getElementsByTagNameNS("*", tag)[0]; return el ? el.textContent.trim() : ""; };
    const imgCache = {};
    const img = async (path) => { if (imgCache[path]) return imgCache[path]; const f = z.file(decodeURIComponent(path)) || z.file(path); if (!f) return ""; return (imgCache[path] = blobUrl(await f.async("uint8array"), mimeByExt(path))); };
    const spine = Array.from(opf.getElementsByTagName("itemref")).map((r) => manifest[r.getAttribute("idref")]).filter(Boolean);
    const chapters = [], hrefIdx = {};
    for (const it of spine) {
      const path = base + it.href;
      const doc = new DOMParser().parseFromString(await rd(path), "application/xhtml+xml");
      const body = doc.body || doc.getElementsByTagName("body")[0];
      if (!body) continue;
      for (const im of Array.from(body.querySelectorAll("img, image"))) { const a = im.getAttribute("src") || im.getAttribute("xlink:href") || im.getAttribute("href"); if (a) { const u = await img(resolve(path, a)); if (im.localName === "image") { const e = document.createElement("img"); e.src = u; im.replaceWith(e); } else im.setAttribute("src", u); } }
      body.querySelectorAll("a[href]").forEach((a) => { const h = a.getAttribute("href"); if (!/^https?:/i.test(h)) { a.setAttribute("data-href", resolve(path, h.split("#")[0])); a.removeAttribute("href"); } });
      const h = body.querySelector("h1,h2,h3");
      hrefIdx[path] = chapters.length;
      chapters.push({ title: h ? h.textContent.trim().slice(0, 80) : "Глава " + (chapters.length + 1), html: sanitize(new XMLSerializer().serializeToString(body).replace(/^<body[^>]*>|<\/body>$/g, "")) });
    }
    // оглавление: nav (EPUB 3) или ncx (EPUB 2)
    const toc = [];
    const navItem = Object.values(manifest).find((m) => m.props.includes("nav"));
    if (navItem) { const p = base + navItem.href, nd = new DOMParser().parseFromString(await rd(p), "application/xhtml+xml"); Array.from(nd.querySelectorAll("nav a")).forEach((a) => { const t = resolve(p, (a.getAttribute("href") || "").split("#")[0]); if (hrefIdx[t] != null) toc.push({ t: a.textContent.trim(), i: hrefIdx[t] }); }); }
    else { const ncx = Object.values(manifest).find((m) => /ncx/.test(m.type || "")); if (ncx) { const p = base + ncx.href, nd = new DOMParser().parseFromString(await rd(p), "application/xml"); Array.from(nd.getElementsByTagName("navPoint")).forEach((np) => { const lab = np.getElementsByTagName("text")[0], c = np.getElementsByTagName("content")[0]; const t = resolve(p, (c ? c.getAttribute("src") : "").split("#")[0]); if (lab && hrefIdx[t] != null) toc.push({ t: lab.textContent.trim(), i: hrefIdx[t] }); }); } }
    const coverItem = Object.values(manifest).find((m) => m.props.includes("cover-image")) || manifest[(Array.from(opf.getElementsByTagName("meta")).find((m) => m.getAttribute("name") === "cover") || { getAttribute: () => "" }).getAttribute("content")];
    return { title: md("title"), author: md("creator"), cover: coverItem ? await img(base + coverItem.href) : "", chapters, toc };
  };
  // Запасной разбор старых MOBI/PRC (PalmDOC без сжатия или со сжатием LZ77), когда в файле нет блока EXTH.
  const palmDoc = (u8) => {
    const dv = new DataView(u8.buffer, u8.byteOffset, u8.byteLength), n = dv.getUint16(76);
    const off = (i) => dv.getUint32(78 + i * 8), rec = (i) => u8.subarray(off(i), i + 1 < n ? off(i + 1) : u8.length);
    const r0 = rec(0), d0 = new DataView(r0.buffer, r0.byteOffset, r0.byteLength);
    const comp = d0.getUint16(0), count = d0.getUint16(8);
    const isMobi = r0.length > 20 && String.fromCharCode(r0[16], r0[17], r0[18], r0[19]) === "MOBI";
    const enc = isMobi && d0.getUint32(28) === 65001 ? "utf-8" : "windows-1252";
    const hl = isMobi ? d0.getUint32(20) : 0, extra = isMobi && hl >= 0xe4 ? d0.getUint16(16 + 0xe2) : 0;
    const title = isMobi ? new TextDecoder(enc).decode(r0.subarray(d0.getUint32(84), d0.getUint32(84) + d0.getUint32(88))) : "";
    const trail = (b) => { let size = b.length; for (let bit = 15; bit > 0; bit--) if (extra & (1 << bit)) { let v = 0; for (let k = Math.max(0, size - 4); k < size; k++) v = (b[k] & 0x80) ? 0 : (v << 7) | (b[k] & 0x7f); let s2 = 0, sh = 0; for (let k = size - 1; k >= 0 && k >= size - 4; k--) { s2 |= (b[k] & 0x7f) << sh; sh += 7; if (b[k] & 0x80) break; } size -= s2 || 0; } if (extra & 1) size -= (b[size - 1] & 3) + 1; return b.subarray(0, Math.max(0, size)); };
    const lz = (b) => { const out = []; let i = 0; while (i < b.length) { const c = b[i++]; if (c >= 1 && c <= 8) { for (let k = 0; k < c; k++) out.push(b[i++]); } else if (c < 0x80) out.push(c); else if (c >= 0xc0) { out.push(32, c ^ 0x80); } else { const x = (c << 8) | b[i++], dist = (x >> 3) & 0x7ff, len = (x & 7) + 3; for (let k = 0; k < len; k++) out.push(out[out.length - dist]); } } return new Uint8Array(out); };
    if (comp !== 1 && comp !== 2) throw new Error("Сжатие HUFF/CDIC не поддерживается — сконвертируйте книгу в EPUB");
    const parts = []; for (let i = 1; i <= count && i < n; i++) { let b = trail(rec(i)); parts.push(comp === 2 ? lz(b) : b); }
    const all = new Uint8Array(parts.reduce((a2, p) => a2 + p.length, 0)); let o = 0; parts.forEach((p) => { all.set(p, o); o += p.length; });
    const text = new TextDecoder(enc).decode(all);
    const chunks = text.split(/<mbp:pagebreak\s*\/?>/i).map((h) => sanitize(h.replace(/<\/?(html|head|body)[^>]*>/gi, "")));
    return { title, chapters: chunks.filter((c) => c.trim()).map((h, i) => ({ title: "Часть " + (i + 1), html: h })) };
  };
  const mobiLike = async (u8, kf8) => {
    const L = await libs();
    let book;
    try { book = kf8 ? await L.initKf8File(u8) : await L.initMobiFile(u8); }
    catch (e) { try { book = kf8 ? await L.initMobiFile(u8) : await L.initKf8File(u8); } catch (e2) { return palmDoc(u8); } }
    const meta = book.getMetadata ? book.getMetadata() : {};
    const chapters = book.getSpine().map((s, i) => { const c = book.loadChapter(s.id) || { html: "" }; return { title: "Глава " + (i + 1), html: sanitize(c.html || "") }; });
    const toc = []; try { const walk = (a) => a.forEach((t) => { const r = book.resolveHref ? book.resolveHref(t.href) : null; const idx = r ? book.getSpine().findIndex((s) => s.id === r.id) : -1; if (idx >= 0) toc.push({ t: t.label, i: idx }); if (t.children) walk(t.children); }); walk(book.getToc() || []); } catch (e) {}
    return { title: meta.title || "", author: Array.isArray(meta.author) ? meta.author.join(", ") : meta.author || "", cover: book.getCoverImage ? book.getCoverImage() || "" : "", chapters, toc };
  };
  P.mobi = (u8) => mobiLike(u8, false); P.azw = P.mobi; P.prc = P.mobi; P.azw3 = (u8) => mobiLike(u8, true); P.kf8 = P.azw3;
  P.cbz = async (u8) => { const { JSZip } = await libs(); const z = await JSZip.loadAsync(u8); const files = Object.values(z.files).filter((f) => /\.(jpe?g|png|gif|webp)$/i.test(f.name)).sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true })); const html = (await Promise.all(files.map(async (f) => '<img src="' + blobUrl(await f.async("uint8array"), mimeByExt(f.name)) + '" alt="">'))).join(""); return { chapters: [{ title: "Страницы", html }] }; };
  P.pdf = async (u8) => { const lib = await getPdf(); const doc = await lib.getDocument({ data: u8 }).promise; const meta = await doc.getMetadata().catch(() => ({})); return { fixed: "pdf", doc, pages: doc.numPages, title: (meta.info && meta.info.Title) || "", author: (meta.info && meta.info.Author) || "" }; };
  P.djvu = async (u8) => { await script("lib/djvu.js"); const doc = new window.DjVu.Document(u8.buffer.slice(u8.byteOffset, u8.byteOffset + u8.byteLength)); return { fixed: "djvu", doc, pages: doc.getPagesQuantity() }; };
  P.djv = P.djvu;

  /* ---------- библиотека ---------- */
  const books = () => A.col("rbooks").slice().sort((a, b) => ((b.opened || b.added) > (a.opened || a.added) ? 1 : -1));
  const addFiles = async (files) => {
    for (const file of files) {
      const ext = extOf(file.name);
      if (!P[ext]) { A.toast("Формат не поддерживается: " + file.name); continue; }
      A.toast("Открываю «" + file.name + "»…");
      const u8 = new Uint8Array(await file.arrayBuffer());
      const id = A.uid();
      let meta = {};
      try { const b = await P[ext](u8.slice()); meta = { title: b.title, author: b.author, pages: b.pages || (b.chapters ? b.chapters.length : 0) }; if (b.cover) { try { meta.cover = await thumb(b.cover); } catch (e) {} } } catch (e) { A.toast("Не удалось прочитать файл: " + e.message); continue; }
      await fput(id, u8);
      A.upsert("rbooks", { id, name: file.name, ext, fmt: FORMATS[ext] || ext.toUpperCase(), size: file.size, title: meta.title || file.name.replace(/\.[^.]+(\.zip)?$/, ""), author: meta.author || "", cover: meta.cover || "", added: new Date().toISOString(), pos: 0, pct: 0 });
    }
    A.refresh();
  };
  const thumb = (url) => new Promise((res, rej) => { const im = new Image(); im.onload = () => { const c = document.createElement("canvas"), w = 120, h = Math.round(w * im.height / im.width); c.width = w; c.height = h; c.getContext("2d").drawImage(im, 0, 0, w, h); res(c.toDataURL("image/jpeg", 0.7)); }; im.onerror = rej; im.src = url; });
  const pickBooks = () => { const i = document.createElement("input"); i.type = "file"; i.multiple = true; i.accept = ACCEPT; i.onchange = () => addFiles(Array.from(i.files || [])); i.click(); };

  /* ---------- режим чтения (полноэкранный слой) ---------- */
  const THEMES = { light: ["#FFFFFF", "#1E1C22"], sepia: ["#F4ECD8", "#4A3B28"], dark: ["#16151A", "#D8D3DE"] };
  const rs = () => (A.db().profile.reader = A.db().profile.reader || { fs: 19, theme: "sepia", font: "serif", lh: 1.6 });
  let cur = null;
  const close = () => { if (!cur) return; savePos(); cur.wrap.remove(); if (cur.cleanup) cur.cleanup(); cur = null; A.refresh(); };
  const savePos = () => {
    if (!cur) return;
    const b = A.byId("rbooks", cur.id); if (!b) return;
    const sc = cur.scroller;
    if (cur.fixed) { b.pos = cur.page || 1; b.pct = Math.round((b.pos / cur.pages) * 100); }
    else { const max = sc.scrollHeight - sc.clientHeight; b.pos = sc.scrollTop / (max || 1); b.pct = Math.round(b.pos * 100); }
    b.opened = new Date().toISOString(); A.save();
  };
  const applyStyle = () => { if (!cur) return; const s = rs(), t = THEMES[s.theme]; cur.wrap.style.setProperty("--rb", t[0]); cur.wrap.style.setProperty("--rf", t[1]); cur.wrap.style.setProperty("--rs", s.fs + "px"); cur.wrap.style.setProperty("--rl", s.lh); cur.wrap.style.setProperty("--rff", s.font === "serif" ? 'Georgia,"Noto Serif",serif' : "system-ui,Roboto,sans-serif"); };

  const open = async (id) => {
    const b = A.byId("rbooks", id); if (!b) return;
    const u8 = await fget(id); if (!u8) { A.toast("Файл книги не найден — добавьте её заново"); return; }
    const wrap = document.createElement("div"); wrap.className = "rdr";
    wrap.innerHTML = '<div class="rdr-top"><button class="icon-btn" data-r="close" aria-label="Закрыть">‹</button><div class="rdr-t">' + esc(b.title) + '</div><button class="icon-btn" data-r="toc" aria-label="Оглавление">☰</button><button class="icon-btn" data-r="set" aria-label="Настройки">Aa</button></div><div class="rdr-sc"><div class="rdr-body"><p class="rdr-load">Открываю книгу…</p></div></div><div class="rdr-bot"><span class="rdr-pct"></span></div><div class="rdr-panel" hidden></div>';
    document.body.appendChild(wrap);
    cur = { id, wrap, scroller: wrap.querySelector(".rdr-sc"), body: wrap.querySelector(".rdr-body") };
    applyStyle();
    let book;
    try { book = await P[b.ext](u8); } catch (e) { cur.body.innerHTML = '<p class="rdr-load">Не удалось открыть: ' + esc(e.message) + "</p>"; return; }
    if (!cur || cur.id !== id) return;
    cur.book = book;
    const pct = wrap.querySelector(".rdr-pct");
    if (book.fixed) await renderFixed(book, b, pct);
    else {
      cur.body.innerHTML = book.chapters.map((c, i) => '<section class="rdr-ch" data-i="' + i + '">' + c.html + "</section>").join("");
      cur.body.querySelectorAll("[data-href]").forEach((a) => a.addEventListener("click", () => { const i = (book.toc || []).find((t) => t.t === a.textContent.trim()); if (i) goChapter(i.i); }));
      requestAnimationFrame(() => { const sc = cur.scroller; sc.scrollTop = (b.pos || 0) * (sc.scrollHeight - sc.clientHeight); });
      let t; cur.scroller.addEventListener("scroll", () => { const sc = cur.scroller, p = sc.scrollTop / ((sc.scrollHeight - sc.clientHeight) || 1); pct.textContent = Math.round(p * 100) + "%"; clearTimeout(t); t = setTimeout(savePos, 800); }, { passive: true });
    }
    wrap.addEventListener("click", (e) => {
      const r = e.target.closest("[data-r]");
      if (r) { const k = r.dataset.r; if (k === "close") close(); else if (k === "toc") showToc(); else if (k === "set") showSettings(); else if (k.startsWith("set:")) setOpt(k.slice(4), r.dataset.v); else if (k === "goch") { goChapter(+r.dataset.v); hidePanel(); } else if (k === "gopg") goPage(+r.dataset.v); return; }
      if (e.target.closest(".rdr-panel")) return;
      if (!e.target.closest(".rdr-top")) { wrap.classList.toggle("ui"); hidePanel(); }
    });
    wrap.classList.add("ui");
  };
  const hidePanel = () => { if (cur) cur.wrap.querySelector(".rdr-panel").hidden = true; };
  const panel = (html) => { const p = cur.wrap.querySelector(".rdr-panel"); p.innerHTML = html; p.hidden = false; };
  const goChapter = (i) => { const s = cur.body.querySelector('.rdr-ch[data-i="' + i + '"]'); if (s) cur.scroller.scrollTop = s.offsetTop; };
  const showToc = () => {
    const bk = cur.book;
    if (bk.fixed) { panel('<h3>Перейти к странице</h3><div class="row"><input type="number" id="rpg" min="1" max="' + bk.pages + '" value="' + (cur.page || 1) + '" style="width:110px"><button class="btn sm primary" data-r="gopg" data-v="0" id="rpgo">Перейти</button><span class="muted small">из ' + bk.pages + "</span></div>"); const go = cur.wrap.querySelector("#rpgo"); go.onclick = (e) => { e.stopPropagation(); goPage(+cur.wrap.querySelector("#rpg").value); hidePanel(); }; return; }
    const items = (bk.toc && bk.toc.length ? bk.toc : bk.chapters.map((c, i) => ({ t: c.title, i })));
    panel("<h3>Оглавление</h3>" + items.map((t) => '<div class="item" data-r="goch" data-v="' + t.i + '"><div class="tx"><b style="font-weight:500">' + esc(t.t || "Глава " + (t.i + 1)) + "</b></div></div>").join(""));
  };
  const showSettings = () => {
    const s = rs();
    const btn = (k, v, l, on) => '<button class="chip' + (on ? " on" : "") + '" data-r="set:' + k + '" data-v="' + v + '">' + l + "</button>";
    panel("<h3>Оформление</h3>" + (cur.fixed ? '<div class="chips">' + btn("zoom", -1, "− масштаб") + btn("zoom", 1, "+ масштаб") + btn("zoom", 0, "по ширине") + "</div>" :
      '<div class="small muted">Размер шрифта: ' + s.fs + '</div><div class="chips">' + btn("fs", -1, "A−") + btn("fs", 1, "A+") + "</div>" +
      '<div class="small muted" style="margin-top:8px">Шрифт</div><div class="chips">' + btn("font", "serif", "С засечками", s.font === "serif") + btn("font", "sans", "Без засечек", s.font === "sans") + "</div>" +
      '<div class="small muted" style="margin-top:8px">Интервал</div><div class="chips">' + [1.35, 1.6, 1.9].map((v) => btn("lh", v, v === 1.35 ? "плотный" : v === 1.6 ? "обычный" : "просторный", s.lh === v)).join("") + "</div>") +
      '<div class="small muted" style="margin-top:8px">Тема</div><div class="chips">' + btn("theme", "light", "Светлая", s.theme === "light") + btn("theme", "sepia", "Сепия", s.theme === "sepia") + btn("theme", "dark", "Тёмная", s.theme === "dark") + "</div>");
  };
  const setOpt = (k, v) => {
    const s = rs();
    if (k === "fs") s.fs = A.clamp(s.fs + +v, 12, 34);
    else if (k === "lh") s.lh = +v; else if (k === "font" || k === "theme") s[k] = v;
    else if (k === "zoom") { cur.zoom = +v === 0 ? 1 : A.clamp((cur.zoom || 1) * (+v > 0 ? 1.25 : 0.8), 0.5, 4); rerenderFixed(); }
    A.save(); applyStyle(); showSettings();
  };

  /* PDF и DjVu: страницы-холсты, отрисовка при приближении к экрану */
  const renderFixed = async (bk, b, pct) => {
    cur.fixed = true; cur.pages = bk.pages; cur.zoom = 1; cur.page = b.pos || 1;
    const sizes = [];
    if (bk.fixed === "djvu") { try { bk.doc.getPagesSizes().forEach((s) => sizes.push([s.width, s.height])); } catch (e) {} }
    else { const p1 = await bk.doc.getPage(1); const v = p1.getViewport({ scale: 1 }); for (let i = 0; i < bk.pages; i++) sizes.push([v.width, v.height]); }
    cur.sizes = sizes;
    cur.body.classList.add("fixed");
    cur.body.innerHTML = Array.from({ length: bk.pages }, (_, i) => '<div class="rdr-pg" data-p="' + (i + 1) + '"><span>' + (i + 1) + "</span></div>").join("");
    const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) drawPage(+e.target.dataset.p); }), { root: cur.scroller, rootMargin: "800px 0px" });
    cur.body.querySelectorAll(".rdr-pg").forEach((el) => io.observe(el));
    cur.cleanup = () => { io.disconnect(); try { if (bk.doc.destroy) bk.doc.destroy(); } catch (e) {} };
    layoutPages();
    let t; cur.scroller.addEventListener("scroll", () => { const mid = cur.scroller.scrollTop + cur.scroller.clientHeight / 2; let p = 1; cur.body.querySelectorAll(".rdr-pg").forEach((el) => { if (el.offsetTop <= mid) p = +el.dataset.p; }); cur.page = p; pct.textContent = p + " / " + bk.pages; clearTimeout(t); t = setTimeout(savePos, 800); }, { passive: true });
    requestAnimationFrame(() => goPage(cur.page));
  };
  const layoutPages = () => { const W = cur.scroller.clientWidth * (cur.zoom || 1); cur.body.querySelectorAll(".rdr-pg").forEach((el) => { const s = cur.sizes[+el.dataset.p - 1] || cur.sizes[0] || [600, 800]; el.style.width = W + "px"; el.style.height = Math.round(W * s[1] / s[0]) + "px"; el.dataset.done = ""; const c = el.querySelector("canvas"); if (c) c.remove(); }); };
  const rerenderFixed = () => { const p = cur.page; layoutPages(); goPage(p); };
  const goPage = (p) => { const el = cur.body.querySelector('.rdr-pg[data-p="' + A.clamp(p || 1, 1, cur.pages) + '"]'); if (el) cur.scroller.scrollTop = el.offsetTop; };
  const drawPage = async (n) => {
    const el = cur && cur.body.querySelector('.rdr-pg[data-p="' + n + '"]'); if (!el || el.dataset.done) return; el.dataset.done = "1";
    const bk = cur.book, W = parseFloat(el.style.width), dpr = Math.min(window.devicePixelRatio || 1, 2);
    const c = document.createElement("canvas");
    try {
      if (bk.fixed === "pdf") {
        const pg = await bk.doc.getPage(n), v1 = pg.getViewport({ scale: 1 }), v = pg.getViewport({ scale: (W / v1.width) * dpr });
        c.width = v.width; c.height = v.height; await pg.render({ canvasContext: c.getContext("2d"), viewport: v }).promise;
      } else {
        const pg = await bk.doc.getPage(n), img = pg.getImageData();
        const t = document.createElement("canvas"); t.width = img.width; t.height = img.height; t.getContext("2d").putImageData(img, 0, 0);
        c.width = Math.round(W * dpr); c.height = Math.round(c.width * img.height / img.width); c.getContext("2d").drawImage(t, 0, 0, c.width, c.height);
      }
      c.style.width = "100%"; c.style.height = "100%"; el.appendChild(c);
    } catch (e) { el.dataset.done = ""; }
  };

  /* ---------- экран библиотеки ---------- */
  A.view("reader", {
    title: "Читалка", tab: "more",
    render() {
      const L = books();
      let h = '<div class="btns" style="margin-bottom:12px"><button class="btn primary" data-a="add">+ Добавить книгу</button></div>';
      if (!L.length) return h + '<div class="card tint"><h3>{{i:book-open}} Ваша библиотека</h3><p>Добавьте книгу с телефона: EPUB, FB2, MOBI, AZW3, PDF, DjVu, TXT, DOCX, RTF, HTML или комиксы CBZ. Книги хранятся в памяти телефона и открываются без интернета, место чтения запоминается.</p></div>';
      const last = L.find((b) => b.opened);
      if (last) h += '<div class="card"><h3>{{i:book-open}} Продолжить чтение</h3><div class="rbook" data-a="open" data-id="' + last.id + '">' + coverHtml(last) + '<div class="tx"><b>' + esc(last.title) + "</b><small>" + esc(last.author || "") + "</small>" + A.bar((last.pct || 0) / 100) + '<small class="muted">' + (last.pct || 0) + "% · " + esc(last.fmt) + "</small></div></div></div>";
      h += '<div class="card"><h3>{{i:library}} Библиотека · ' + L.length + "</h3>" + L.map((b) => '<div class="rbook" data-a="open" data-id="' + b.id + '">' + coverHtml(b) + '<div class="tx"><b>' + esc(b.title) + "</b><small>" + esc([b.author, b.fmt, Math.round(b.size / 1024) + " КБ"].filter(Boolean).join(" · ")) + "</small>" + A.bar((b.pct || 0) / 100) + '</div><button class="icon-btn" data-a="del" data-id="' + b.id + '" aria-label="Удалить">✕</button></div>').join("") + "</div>";
      return h;
    },
    actions(box) { box.innerHTML = '<button class="icon-btn" data-add aria-label="Добавить книгу">＋</button>'; box.querySelector("[data-add]").onclick = pickBooks; },
    bind(el) {
      A.bind(el, {
        add: pickBooks,
        open(b) { open(b.dataset.id); },
        del(b, e) { e.stopPropagation(); const bk = A.byId("rbooks", b.dataset.id); A.confirm("Удалить «" + (bk ? bk.title : "") + "» из библиотеки?", () => { fdel(b.dataset.id); A.remove("rbooks", b.dataset.id); A.refresh(); }, "Удалить", true); }
      });
    }
  });
  const coverHtml = (b) => b.cover ? '<img class="rcov" src="' + b.cover + '" alt="">' : '<div class="rcov ph">' + esc(b.fmt) + "</div>";
  window.addEventListener("pagehide", savePos);
  const prevBack = window.__onBack;
  window.__onBack = () => { if (cur) { const p = cur.wrap.querySelector(".rdr-panel"); if (!p.hidden) hidePanel(); else close(); return true; } return prevBack(); };
})();
