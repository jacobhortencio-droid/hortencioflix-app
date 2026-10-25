// Testes do curador (node curador/test.mjs) — sem rede: o fetch é simulado.
import assert from "node:assert/strict";
import worker, { encodeCfg, decodeCfg, curate, normalize, pickFile, quality, sizeGb } from "./worker.js";

const H1 = "a".repeat(40), H2 = "b".repeat(40), H3 = "c".repeat(40);
const calls = [];
globalThis.fetch = async (input, init = {}) => {
  const url = typeof input === "string" ? input : input.url;
  calls.push({ url, init });
  const j = (o, s = 200) => new Response(JSON.stringify(o), { status: s, headers: { "Content-Type": "application/json" } });
  if (url.endsWith("/manifest.json")) return j({ name: url.includes("one") ? "Um" : "Dois" });
  if (url.startsWith("https://one.test/stream/movie/tt1.json")) return j({ streams: [
    { name: "[TB+] Um\n4k", title: "Filme.2160p.DV.mkv 12 GB", url: `https://one.test/resolve/torbox/KEY/${H1}/0`, behaviorHints: { filename: "Filme.2160p.DV.mkv" } },
    { name: "[TB download] Um\n1080p", title: "Filme.1080p.mkv 4 GB", url: `https://one.test/resolve/torbox/KEY/${H2}/0`, behaviorHints: { filename: "Filme.1080p.mkv" } },
  ] });
  if (url.startsWith("https://two.test/stream/movie/tt1.json")) return j({ streams: [
    { name: "Dois", title: "Filme.2160p.DV.mkv 12 GB", infoHash: H1, fileIdx: 0, behaviorHints: { filename: "Filme.2160p.DV.mkv" } },
    { name: "Dois", title: "Filme.720p.mkv", infoHash: H3, fileIdx: 1 },
    { name: "Dois", title: "sem nada" },
  ] });
  if (url.startsWith("https://slow.test/")) return new Promise((_, rej) => init.signal?.addEventListener("abort", () => rej(new Error("timeout"))));
  if (url.includes("/torrents/checkcached")) {
    assert.equal(init.headers.Authorization, "Bearer SEGREDO");
    return j({ success: true, data: { [H1]: { name: "x" }, [H2]: { name: "y" } } });
  }
  if (url.includes("/torrents/mylist")) return j({ success: true, data: [{ id: 7, hash: H1, download_finished: true, files: [{ id: 0, name: "Filme/Filme.2160p.DV.mkv", short_name: "Filme.2160p.DV.mkv", size: 12 }, { id: 1, name: "Filme/sample.mkv", size: 1 }] }] });
  if (url.includes("/torrents/requestdl")) return j({ success: true, data: "https://cdn.torbox.test/file.mkv" });
  return j({}, 404);
};

// básicos
assert.equal(quality("x 2160p"), 2160);
assert.equal(quality("x 4K HDR"), 2160);
assert.equal(sizeGb("12,5 GB"), 12.5);
assert.equal(sizeGb("700 MB").toFixed(3), (700 / 1024).toFixed(3));
const raw = encodeCfg({ a: ["https://one.test/manifest.json", "https://two.test/", "ftp://x", "https://one.test"], k: "SEGREDO" });
const cfg = decodeCfg(raw);
assert.deepEqual(cfg.a, ["https://one.test", "https://two.test"]);
assert.equal(cfg.k, "SEGREDO");
assert.equal(decodeCfg("!!!"), null);
assert.equal(normalize({ url: `https://x/${H1}/1` }, "a", 0).hash, H1);
assert.equal(pickFile([{ id: 0, name: "a.txt", size: 9 }, { id: 1, name: "b.mkv", size: 2 }], -1, "").id, 1);

// fontes: junta, tira repetidos, marca prontas, torna jogáveis as «só torrent»
const ctx = { waitUntil() {} };
const keep = setInterval(() => {}, 1000);  // o AbortSignal.timeout do node não segura o processo
let r = await worker.fetch(new Request(`https://cur.test/${raw}/stream/movie/tt1.json`), {}, ctx);
let d = await r.json();
assert.equal(r.status, 200);
assert.equal(r.headers.get("Access-Control-Allow-Origin"), "*");
const names = d.streams.map((s) => s.name);
assert.equal(d.streams.length, 3, JSON.stringify(names));            // H1 repetido entre add-ons → um só; «sem nada» fora
assert.ok(d.streams[0].hf.cached && d.streams[0].hf.q === 2160);    // pronto e 4K primeiro
const h2 = d.streams.find((s) => s.hf.hash === H2);
assert.ok(h2.hf.cached && h2.name.startsWith("[TB+]"));             // «download» mas já no TorBox → pronto
const h3 = d.streams.find((s) => s.hf.hash === H3);
assert.ok(!h3.hf.cached && h3.name.startsWith("[TB download]"));
assert.ok(h3.url.startsWith(`https://cur.test/${raw}/play/${H3}/1/`));
assert.ok(!("infoHash" in h3));
assert.equal(d.streams[0].addon, "Um");
assert.equal(d.hf.checked, 3);
assert.ok(!JSON.stringify(d.hf).includes("SEGREDO"));

// add-on lento não bloqueia (limite de tempo) — teste rápido com limite simulado
const raw2 = encodeCfg({ a: ["https://one.test", "https://slow.test"], k: "" });
const t0 = Date.now();
r = await worker.fetch(new Request(`https://cur.test/${raw2}/stream/movie/tt1.json`), {}, ctx);
d = await r.json();
assert.ok(Date.now() - t0 < 12000);
assert.equal(d.hf.addons[1].ok, false);
assert.equal(d.streams.length, 2);   // sem chave: só as que já têm link

// reproduzir pelo TorBox
r = await worker.fetch(new Request(`https://cur.test/${raw}/play/${H1}/0/Filme.2160p.DV.mkv`), {}, ctx);
assert.equal(r.status, 302);
assert.equal(r.headers.get("Location"), "https://cdn.torbox.test/file.mkv");
const dl = calls.find((c) => c.url.includes("requestdl"));
assert.ok(dl.url.includes("file_id=0") && dl.url.includes("torrent_id=7"));

// manifesto e configuração inválida
r = await worker.fetch(new Request(`https://cur.test/${raw}/manifest.json`), {}, ctx);
assert.equal((await r.json()).resources[0], "stream");
r = await worker.fetch(new Request(`https://cur.test/xyz/stream/movie/tt1.json`), {}, ctx);
assert.equal(r.status, 400);

clearInterval(keep);
console.log("curador: todos os testes passaram");
