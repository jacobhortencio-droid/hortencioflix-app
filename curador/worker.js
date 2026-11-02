// Hortêncioflix · Fontes curadas
// Add-on Stremio (Cloudflare Worker) que junta as fontes de todos os teus add-ons num só pedido:
//  - pede a todos em paralelo (cada um com limite de tempo, um lento não atrasa os outros)
//  - remove repetidos (o mesmo ficheiro vindo de vários add-ons)
//  - confirma de uma vez no TorBox o que já está pronto a ver
//  - torna jogáveis pelo TorBox as fontes «só torrent» (sem link), através de /play
//  - guarda o resultado 10 min (abrir o mesmo título no Android e na Roku é instantâneo)
// A configuração vai no próprio endereço (base64url de {"a":[add-ons],"k":"chave TorBox"}),
// por isso o servidor não guarda nada teu. Nunca escreve a chave em registos.

const VERSION = "1.5.0";
const TB = "https://api.torbox.app/v1/api";
const UA = "Hortencioflix-Curador/" + VERSION;
const ADDON_TIMEOUT_MS = 8000;
const MAX_ADDONS = 12;
const MAX_CACHED = 300;
const MAX_OTHER = 60;
// áudio dobrado em português: nunca fica de fora pelo limite
const PT_RX = /dublad|dobrad|\bdual\b|pt-?br|\bptbr\b|portugu|\bpor\b|🇧🇷|🇵🇹|nacional/i;
const CACHE_SECS = 600;
const VIDEO_RX = /\.(mkv|mp4|avi|m4v|mov|ts|m2ts|webm|wmv)$/i;

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    try {
      return cors(await route(request, ctx, env));
    } catch (e) {
      // nunca devolver erro ao leitor: lista vazia (a app volta a pedir diretamente aos add-ons)
      return cors(json({ streams: [], error: String((e && e.message) || e) }));
    }
  },
};

async function route(request, ctx, env) {
  const url = new URL(request.url);
  const parts = url.pathname.split("/").filter(Boolean);
  const origin = url.origin;
  if (parts.length === 0) return landing();
  if (parts[0] === "health") return json({ ok: true, name: "Hortêncioflix · Fontes curadas", version: VERSION, sync: !!(env && env.SYNC) });
  if (parts[0] === "sync" && parts[1]) return cloudSync(request, parts[1], env);
  if (parts[0] === "manifest.json") return json(manifest(false));
  const cfgRaw = parts[0];
  const cfg = decodeCfg(cfgRaw);
  if (!cfg) return json({ streams: [], error: "configuração inválida" }, 400);
  if (parts[1] === "manifest.json") return json(manifest(true));
  if (parts[1] === "stream" && parts.length >= 4) {
    const type = decodeURIComponent(parts[2]);
    const id = decodeURIComponent(parts.slice(3).join("/")).replace(/\.json$/, "");
    return streams(cfg, cfgRaw, type, id, origin, ctx);
  }
  if (parts[1] === "play" && parts.length >= 4) {
    const hash = parts[2].toLowerCase();
    const idx = parseInt(parts[3], 10);
    const name = parts[4] ? decodeURIComponent(parts[4]) : "";
    return play(cfg, hash, isNaN(idx) ? -1 : idx, name, request, ctx);
  }
  return json({ error: "não encontrado" }, 404);
}

// ---------- Configuração ----------

export function decodeCfg(raw) {
  try {
    let b = raw.replace(/-/g, "+").replace(/_/g, "/");
    while (b.length % 4) b += "=";
    const txt = new TextDecoder().decode(Uint8Array.from(atob(b), (c) => c.charCodeAt(0)));
    const o = JSON.parse(txt);
    if (!o || !Array.isArray(o.a)) return null;
    const addons = [];
    for (const x of o.a) {
      if (typeof x !== "string") continue;
      let u = x.trim().replace(/\/manifest\.json$/i, "").replace(/\/+$/, "");
      if (u.startsWith("stremio://")) u = "https://" + u.slice(10);
      if (!/^https?:\/\//i.test(u)) continue;
      // endereços da rede de casa: inalcançáveis daqui (e não devem ser pedidos por um servidor público)
      if (/^https?:\/\/(localhost|127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|[^/]*\.local(?:[:/]|$))/i.test(u)) continue;
      if (!addons.includes(u)) addons.push(u);
      if (addons.length >= MAX_ADDONS) break;
    }
    return { a: addons, k: typeof o.k === "string" ? o.k.trim() : "" };
  } catch (e) {
    return null;
  }
}

export function encodeCfg(o) {
  const bytes = new TextEncoder().encode(JSON.stringify(o));
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function manifest(configured) {
  return {
    id: "app.hortencioflix.curador",
    version: VERSION,
    name: "Hortêncio · Fontes curadas",
    description: "Junta as fontes de todos os teus add-ons, sem repetidos, e confirma no TorBox o que já está pronto.",
    resources: ["stream"],
    types: ["movie", "series", "anime", "other"],
    idPrefixes: ["tt", "kitsu", "mal", "anilist", "tmdb"],
    catalogs: [],
    behaviorHints: { configurable: false, configurationRequired: !configured },
  };
}

// ---------- Fontes ----------

export function quality(t) {
  const s = t.toLowerCase();
  if (/2160p|\b4k\b|\buhd\b/.test(s)) return 2160;
  if (s.includes("1440p")) return 1440;
  if (s.includes("1080p")) return 1080;
  if (s.includes("720p")) return 720;
  if (/480p|576p/.test(s)) return 480;
  return 0;
}

export function sizeGb(t) {
  const m = /(\d+(?:[.,]\d+)?)\s?(GB|MB|GiB|MiB|TB)\b/i.exec(t);
  if (!m) return 0;
  const v = parseFloat(m[1].replace(",", "."));
  const u = m[2].toUpperCase();
  if (u === "MB" || u === "MIB") return v / 1024;
  if (u === "TB") return v * 1024;
  return v;
}

export function normalize(s, addon, order) {
  const bh = (s && typeof s.behaviorHints === "object" && s.behaviorHints) || {};
  const label = String(s.name || "");
  const detail = String(s.title || s.description || "");
  const filename = String(bh.filename || "");
  const url = typeof s.url === "string" && /^https?:\/\//i.test(s.url) ? s.url : "";
  let hash = typeof s.infoHash === "string" ? s.infoHash.toLowerCase() : "";
  if (!hash && url) {
    const m = /(?:^|[^a-f0-9])([a-f0-9]{40})(?:[^a-f0-9]|$)/i.exec(url);
    if (m) hash = m[1].toLowerCase();
  }
  const text = `${label} ${detail} ${filename}`;
  return {
    s, addon, label, detail, filename, url, hash, order,
    idx: Number.isInteger(s.fileIdx) ? s.fileIdx : -1,
    q: quality(text),
    gb: sizeGb(text),
    uncached: /download\]/i.test(label),
  };
}

function dedupeKey(it) {
  const f = it.filename.toLowerCase().trim();
  if (it.hash) return it.hash + "|" + (f || String(it.idx));
  if (f.length > 8) return "f|" + f;
  return "u|" + it.url;
}

async function addonName(base) {
  try {
    const r = await fetch(base + "/manifest.json", {
      headers: { "User-Agent": UA },
      signal: AbortSignal.timeout(4000),
      cf: { cacheTtl: 86400, cacheEverything: true },
    });
    if (r.ok) {
      const m = await r.json();
      if (m && typeof m.name === "string" && m.name) return m.name;
    }
  } catch (e) {}
  try { return new URL(base).hostname; } catch (e) { return "Add-on"; }
}

async function fetchAddon(base, type, id) {
  const path = `${base}/stream/${encodeURIComponent(type)}/${encodeURIComponent(id).replace(/%3A/gi, ":")}.json`;
  const [name, list] = await Promise.all([
    addonName(base),
    (async () => {
      try {
        const t0 = Date.now();
        const r = await fetch(path, { headers: { "User-Agent": UA, Accept: "application/json" }, signal: AbortSignal.timeout(ADDON_TIMEOUT_MS) });
        const txt = await r.text();
        const ms = Date.now() - t0;
        // 404 = este add-on não tem nada para este título (não é falha)
        if (r.status === 404) return { ok: true, streams: [], status: 404, ms };
        if (!r.ok) return { ok: false, streams: [], status: r.status, ms, err: (r.status === 403 || r.status === 429 || r.status === 503) && /<html|<!doctype/i.test(txt) ? "bloqueia servidores (HTTP " + r.status + ")" : "HTTP " + r.status };
        let d = null;
        try { d = JSON.parse(txt); } catch (e) {
          return { ok: false, streams: [], status: r.status, ms, err: /<html|<!doctype/i.test(txt) ? "bloqueado (página HTML)" : "resposta inválida" };
        }
        return { ok: true, streams: Array.isArray(d && d.streams) ? d.streams : [], status: r.status, ms };
      } catch (e) {
        const n = (e && e.name) || "";
        return { ok: false, streams: [], status: 0, err: n === "TimeoutError" || n === "AbortError" ? "sem resposta em 8 s" : "erro de rede" };
      }
    })(),
  ]);
  return { base, name, ...list };
}

// Quais destes hashes já estão prontos no TorBox (pedidos de 100 em 100)
export async function torboxCached(key, hashes) {
  const ready = new Set();
  ready.checked = new Set();   // hashes que o TorBox confirmou (com ou sem cache)
  if (!key || hashes.length === 0) return ready;
  const chunks = [];
  for (let i = 0; i < hashes.length; i += 100) chunks.push(hashes.slice(i, i + 100));
  await Promise.all(chunks.map(async (c) => {
    try {
      const r = await fetch(`${TB}/torrents/checkcached?hash=${c.join(",")}&format=object&list_files=false`, {
        headers: { Authorization: "Bearer " + key, "User-Agent": UA },
        signal: AbortSignal.timeout(6000),
      });
      if (!r.ok) return;
      const d = await r.json();
      if (d && d.success === true) for (const h of c) ready.checked.add(h);
      const data = d && d.data;
      if (Array.isArray(data)) {
        for (const x of data) if (x && x.hash) ready.add(String(x.hash).toLowerCase());
      } else if (data && typeof data === "object") {
        for (const k of Object.keys(data)) if (data[k]) ready.add(k.toLowerCase());
      }
    } catch (e) {}
  }));
  return ready;
}

async function streams(cfg, cfgRaw, type, id, origin, ctx) {
  const cache = typeof caches !== "undefined" ? caches.default : null;
  const cacheReq = new Request(`${origin}/${cfgRaw}/stream/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`);
  if (cache) {
    const hit = await cache.match(cacheReq);
    if (hit) return hit;
  }
  const t0 = Date.now();
  const results = await Promise.all(cfg.a.map((b) => fetchAddon(b, type, id)));
  const items = [];
  let order = 0;
  for (const r of results) for (const s of r.streams) if (s && typeof s === "object") items.push(normalize(s, r.name, order++));

  const hashes = [...new Set(items.filter((x) => x.hash).map((x) => x.hash))].slice(0, 1500);
  const ready = await torboxCached(cfg.k, hashes);
  const out = curate(items, ready, cfg, cfgRaw, origin);

  const body = {
    streams: out,
    cacheMaxAge: CACHE_SECS,
    hf: {
      version: VERSION,
      ms: Date.now() - t0,
      addons: results.map((r) => ({ name: r.name, ok: r.ok, n: r.streams.length, status: r.status || 0, ms: r.ms || 0, err: r.err || "" })),
      checked: hashes.length,
      ready: ready.size,
    },
  };
  const resp = json(body, 200, out.length > 0 ? CACHE_SECS : 0);
  if (cache && out.length > 0 && ctx && ctx.waitUntil) ctx.waitUntil(cache.put(cacheReq, resp.clone()));
  return resp;
}

// Junta, tira repetidos, marca o que está pronto e ordena (pronto > qualidade > ordem dos add-ons)
export function curate(items, ready, cfg, cfgRaw, origin) {
  const best = new Map();
  for (const it of items) {
    if (it.url) {
      it.cached = !it.uncached;
      // o TorBox já tem este ficheiro: um link TorBox marcado «download» passa a pronto
      if (it.uncached && it.hash && ready.has(it.hash) && /\[TB/i.test(it.label)) {
        it.cached = true;
        it.label = it.label.replace(/\[TB[^\]]*download\]/i, "[TB+]");
      }
      // o add-on diz «pronto» mas o TorBox não o confirma: continua pronta (a confirmação do TorBox nem sempre
      // é completa) mas fica atrás das confirmadas; a app testa-a antes de a tocar
      if (it.cached && it.hash && ready.checked && ready.checked.has(it.hash) && !ready.has(it.hash)) it.unconfirmed = true;
    } else if (it.hash && cfg.k) {
      // fonte «só torrent»: passa a jogável pelo TorBox
      it.cached = ready.has(it.hash);
      const name = encodeURIComponent((it.filename || "video").slice(0, 120));
      it.url = `${origin}/${cfgRaw}/play/${it.hash}/${it.idx}/${name}`;
      it.label = (it.cached ? "[TB+] " : "[TB download] ") + it.label;
    } else {
      continue;
    }
    const k = dedupeKey(it);
    const prev = best.get(k);
    if (!prev || (it.cached && !prev.cached)) best.set(k, it);
  }
  const list = [...best.values()];
  list.sort((a, b) => (b.cached - a.cached) || ((a.unconfirmed ? 1 : 0) - (b.unconfirmed ? 1 : 0)) || (b.q - a.q) || (a.order - b.order));
  // limite: até 300 prontas e 60 por descarregar, mas as dobradas em português ficam sempre
  let nc = 0, no = 0;
  const kept = list.filter((it) => {
    const pt = PT_RX.test(`${it.label} ${it.detail} ${it.filename}`);
    if (it.cached) { nc++; return nc <= MAX_CACHED || pt; }
    no++; return no <= MAX_OTHER || pt;
  });
  return kept.map((it) => {
    const o = { ...it.s };
    delete o.infoHash;
    delete o.fileIdx;
    delete o.sources;
    o.name = it.label;
    o.url = it.url;
    o.addon = it.addon;
    o.behaviorHints = { ...(it.s.behaviorHints || {}) };
    if (it.filename) o.behaviorHints.filename = it.filename;
    o.hf = { cached: !!it.cached, confirmed: !it.unconfirmed, hash: it.hash || "", q: it.q, gb: Math.round(it.gb * 100) / 100 };
    return o;
  });
}

// ---------- Reproduzir uma fonte «só torrent» pelo TorBox ----------

async function tbJson(path, key, init = {}) {
  const r = await fetch(TB + path, {
    ...init,
    headers: { Authorization: "Bearer " + key, "User-Agent": UA, ...(init.headers || {}) },
    signal: AbortSignal.timeout(12000),
  });
  try { return await r.json(); } catch (e) { return null; }
}

export function pickFile(files, idx, name) {
  if (!Array.isArray(files) || files.length === 0) return null;
  const vids = files.filter((f) => VIDEO_RX.test(String(f.name || f.short_name || "")));
  const pool = vids.length ? vids : files;
  if (name && name !== "video") {
    const n = name.toLowerCase();
    const f = pool.find((x) => String(x.short_name || x.name || "").toLowerCase().endsWith(n) || String(x.name || "").toLowerCase().endsWith("/" + n));
    if (f) return f;
  }
  if (idx >= 0) {
    const f = files.find((x) => x.id === idx);
    if (f && VIDEO_RX.test(String(f.name || f.short_name || ""))) return f;
  }
  return pool.reduce((a, b) => ((b.size || 0) > (a.size || 0) ? b : a));
}

function isReady(t) {
  if (!t) return false;
  const st = String(t.download_state || "").toLowerCase();
  return t.download_finished === true || t.download_present === true || /cached|completed|uploading|seeding/.test(st);
}

async function play(cfg, hash, idx, name, request, ctx) {
  if (!cfg.k) return new Response("Falta a chave TorBox.", { status: 403 });
  if (!/^[a-f0-9]{40}$/.test(hash)) return new Response("Hash inválido.", { status: 400 });
  const cache = typeof caches !== "undefined" ? caches.default : null;
  const ck = new Request(new URL(request.url).origin + "/link/" + hash + "/" + idx + "/" + encodeURIComponent(name) + "/" + (await shortHash(cfg.k)));
  if (cache) {
    const hit = await cache.match(ck);
    if (hit) {
      const u = await hit.text();
      if (u) return Response.redirect(u, 302);
    }
  }
  const key = cfg.k;
  const list = await tbJson("/torrents/mylist?bypass_cache=true", key);
  let t = Array.isArray(list && list.data) ? list.data.find((x) => String(x.hash || "").toLowerCase() === hash) : null;
  if (!t) {
    const fd = new FormData();
    fd.append("magnet", "magnet:?xt=urn:btih:" + hash);
    const c = await tbJson("/torrents/createtorrent", key, { method: "POST", body: fd });
    const tid = c && c.data && (c.data.torrent_id || c.data.id);
    if (!tid) {
      const msg = (c && (c.detail || c.error)) || "o TorBox recusou";
      return new Response("TorBox: " + msg, { status: 502 });
    }
    const one = await tbJson(`/torrents/mylist?bypass_cache=true&id=${tid}`, key);
    t = one && one.data && !Array.isArray(one.data) ? one.data : null;
    if (!t) return new Response("A preparar no TorBox…", { status: 503, headers: { "Retry-After": "30" } });
  }
  if (!isReady(t)) return new Response("A descarregar no TorBox…", { status: 503, headers: { "Retry-After": "60" } });
  const f = pickFile(t.files, idx, name);
  if (!f) return new Response("Sem ficheiro de vídeo.", { status: 404 });
  const dl = await tbJson(`/torrents/requestdl?token=${encodeURIComponent(key)}&torrent_id=${t.id}&file_id=${f.id}`, key);
  const link = dl && typeof dl.data === "string" ? dl.data : "";
  if (!/^https?:\/\//.test(link)) return new Response("O TorBox não deu o link.", { status: 502 });
  if (cache && ctx && ctx.waitUntil) {
    ctx.waitUntil(cache.put(ck, new Response(link, { headers: { "Cache-Control": "max-age=1800" } })));
  }
  return Response.redirect(link, 302);
}

async function shortHash(s) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
  return [...new Uint8Array(d)].slice(0, 6).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------- Utilitários ----------

function json(o, status = 200, maxAge = 0) {
  return new Response(JSON.stringify(o), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store",
    },
  });
}

function cors(r) {
  const h = new Headers(r.headers);
  h.set("Access-Control-Allow-Origin", "*");
  h.set("Access-Control-Allow-Headers", "*");
  return new Response(r.body, { status: r.status, statusText: r.statusText, headers: h });
}

function landing() {
  const html = `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Hortêncioflix · Fontes curadas</title>
<style>body{font-family:system-ui,sans-serif;max-width:640px;margin:40px auto;padding:0 16px;line-height:1.5;background:#0f1014;color:#eee}code{background:#222;padding:2px 6px;border-radius:4px}</style>
<h1>Hortêncioflix · Fontes curadas</h1>
<p>O servidor está a funcionar (versão ${VERSION}).</p>
<p>Na app Android: <b>Definições › Fontes curadas</b> e cola este endereço: <code id="u"></code></p>
<p>A Roku recebe-o pela sincronização.</p>
<script>document.getElementById("u").textContent=location.origin</script>`;
  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
}

// ---------- Sincronização na nuvem (Android ⇄ Roku, em qualquer rede, sem a Roku ter de estar aberta) ----------
// Cada casa tem um código secreto (≥ 20 letras) criado pela app Android. Guarda só o essencial:
// «Continuar a ver», episódios vistos, títulos acabados e os que tiraste da lista. Nada de chaves nem contas.

const SYNC_ID = /^[A-Za-z0-9_-]{20,64}$/;

export function syncMerge(state, inc, now) {
  const st = {
    progress: Array.isArray(state.progress) ? state.progress : [],
    watched: Array.isArray(state.watched) ? state.watched : [],
    done: state.done && typeof state.done === "object" ? state.done : {},
    hidden: state.hidden && typeof state.hidden === "object" ? state.hidden : {},
  };
  // acabados / retirados (com a hora a que isso aconteceu)
  for (const m of (Array.isArray(inc.done) ? inc.done : [])) if (typeof m === "string" && m && !st.done[m]) st.done[m] = now;
  for (const m of (Array.isArray(inc.hidden) ? inc.hidden : [])) if (typeof m === "string" && m) st.hidden[m] = now;
  // «Continuar a ver»: por título, fica o mais recente
  const best = new Map();
  for (const o of [...st.progress, ...(Array.isArray(inc.progress) ? inc.progress : [])]) {
    if (!o || typeof o.m !== "string" || !o.m) continue;
    const cur = best.get(o.m);
    if (!cur || (Number(o.a) || 0) > (Number(cur.a) || 0)) best.set(o.m, {
      m: o.m, v: String(o.v || ""), t: Number(o.t) || 0, d: Number(o.d) || 0, n: String(o.n || "").slice(0, 120),
      p: String(o.p || "").slice(0, 300), y: String(o.y || ""), a: Number(o.a) || 0,
    });
  }
  // um título acabado/retirado só volta se for visto de novo depois disso
  st.progress = [...best.values()]
    .filter((o) => !(st.done[o.m] && st.done[o.m] >= o.a) && !(st.hidden[o.m] && st.hidden[o.m] >= o.a))
    .sort((a, b) => b.a - a.a).slice(0, 30);
  // vistos: junta (formato do Android "tt1:1:2" e o compacto da Roku {m, e:"1:1-8,2:3"})
  const seen = new Set(st.watched);
  const add = (id) => { if (typeof id === "string" && id && !seen.has(id)) { seen.add(id); st.watched.push(id); } };
  for (const id of (Array.isArray(inc.watched) ? inc.watched : [])) add(id);
  for (const id of rokuToIds(inc.watchedRoku)) add(id);
  if (st.watched.length > 6000) st.watched = st.watched.slice(-6000);
  // limites
  const trim = (o, n) => Object.fromEntries(Object.entries(o).sort((a, b) => b[1] - a[1]).slice(0, n));
  st.done = trim(st.done, 400);
  st.hidden = trim(st.hidden, 200);
  st.updated = now;
  return st;
}

export function rokuToIds(arr) {
  const out = [];
  if (!Array.isArray(arr)) return out;
  for (const o of arr) {
    if (!o || typeof o.m !== "string") continue;
    for (const part of String(o.e || "").split(",")) {
      const c = part.indexOf(":");
      if (c <= 0) continue;
      const season = parseInt(part.slice(0, c), 10);
      const ab = part.slice(c + 1).split("-").map((x) => parseInt(x, 10)).filter((x) => !isNaN(x));
      if (isNaN(season) || !ab.length) continue;
      const from = ab[0], to = ab.length > 1 ? ab[1] : ab[0];
      if (to - from > 500) continue;
      for (let e = from; e <= to; e++) out.push(`${o.m}:${season}:${e}`);
    }
  }
  return out;
}

/** Vistos em formato compacto para a Roku (as séries vistas mais recentemente primeiro). */
export function idsToRoku(ids, limit) {
  const by = new Map();
  for (let i = ids.length - 1; i >= 0; i--) {
    const p = ids[i].split(":");
    if (p.length !== 3) continue;
    if (!by.has(p[0])) { if (by.size >= limit) continue; by.set(p[0], new Map()); }
    const s = by.get(p[0]);
    const sn = parseInt(p[1], 10), ep = parseInt(p[2], 10);
    if (isNaN(sn) || isNaN(ep)) continue;
    if (!s.has(sn)) s.set(sn, new Set());
    s.get(sn).add(ep);
  }
  const out = [];
  for (const [m, seasons] of by) {
    const parts = [];
    for (const [sn, eps] of [...seasons].sort((a, b) => a[0] - b[0])) {
      const list = [...eps].sort((a, b) => a - b);
      let a = list[0], prev = list[0];
      for (let i = 1; i <= list.length; i++) {
        if (i < list.length && list[i] === prev + 1) { prev = list[i]; continue; }
        parts.push(`${sn}:${a === prev ? a : a + "-" + prev}`);
        if (i < list.length) { a = list[i]; prev = list[i]; }
      }
    }
    out.push({ m, e: parts.join(",") });
  }
  return out;
}

function syncView(st, device) {
  const v = {
    ok: true,
    updated: st.updated || 0,
    progress: (st.progress || []).slice(0, device === "roku" ? 12 : 30),
    done: Object.keys(st.done || {}),
    hidden: Object.keys(st.hidden || {}),
  };
  if (device === "roku") v.watchedRoku = idsToRoku(st.watched || [], 25);
  else v.watched = st.watched || [];
  return v;
}

async function cloudSync(request, id, env) {
  if (!env || !env.SYNC) return json({ ok: false, error: "sincronização na nuvem ainda não ativada no servidor" }, 503);
  if (!SYNC_ID.test(id)) return json({ ok: false, error: "código inválido" }, 400);
  const key = "s:" + id;
  const url = new URL(request.url);
  const device = url.searchParams.get("d") === "roku" ? "roku" : "android";
  let st = {};
  try { st = JSON.parse((await env.SYNC.get(key)) || "{}"); } catch (e) { st = {}; }
  if (request.method === "POST") {
    let inc = {};
    try { inc = await request.json(); } catch (e) { return json({ ok: false, error: "dados inválidos" }, 400); }
    st = syncMerge(st, inc || {}, Math.floor(Date.now() / 1000));
    await env.SYNC.put(key, JSON.stringify(st));
  }
  return json(syncView(st, device));
}
