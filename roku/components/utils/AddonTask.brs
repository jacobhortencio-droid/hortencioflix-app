' Cliente do protocolo de add-ons do Stremio + legendas + listas M3U
' Corre numa thread separada (Task) para não bloquear a interface.

sub init_x()
    m.top.functionName = "run"
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("AddonTask.init", e)
    end try
end sub

sub run_x()
    req = m.top.request
    m.manifests = {}
    if req.manifests <> invalid then m.manifests = req.manifests
    kind = req.kind
    res = { ok: false }
    if kind = "home"
        res = loadHome(req.addons)
    else if kind = "meta"
        res = loadMeta(req.addons, req.type, req.id)
    else if kind = "recommend"
        res = loadRecommend(req)
    else if kind = "translate"
        res = loadTranslation(req)
    else if kind = "collection"
        res = loadCollection(req)
    else if kind = "translateSeason"
        res = loadSeasonTranslation(req)
    else if kind = "streams"
        res = loadStreams(req.addons, req.type, req.id, req)
    else if kind = "search"
        res = loadSearch(req.addons, req.query)
    else if kind = "subtitles"
        res = loadSubtitles(req.addons, req.type, req.id, req.langs)
    else if kind = "dlsubs"
        res = downloadSubtitles(req.items)
    else if kind = "m3u"
        res = loadM3u(req.url)
    else if kind = "segments"
        res = loadSegments(req.id, req.type)
    else if kind = "probe"
        res = probeUrl(req.url, req.headers)
    else if kind = "shiftsub"
        res = shiftSubtitle(req.src, req.ms)
    else if kind = "torbox"
        res = loadTorbox(req.key)
    else if kind = "catalogurl"
        res = loadCatalogUrl(req.url, req.exclude)
    else if kind = "json"
        d = httpJson(req.url)
        res = { ok: d <> invalid, data: d }
    else if kind = "calendar"
        res = loadCalendar(req.items)
    else if kind = "person"
        res = loadPerson(req.name, req.tmdbKey, req.addons)
        if res.source = "search" and sOr(req.traktId) <> ""
            tr = loadPersonTrakt(req.name, sOr(req.traktId))
            if tr.ok then res = tr
        end if
    else if kind = "epgxtream"
        res = loadXtreamEpg(req.url)
    else if kind = "epgxml"
        res = loadXmlEpg(req.url, req.ids)
    else if kind = "alive"
        res = { ok: true, alive: httpAliveMany(req.urls, 25000, 12) }
    else if kind = "tvsources"
        res = loadTvSources(req.addons)
    else if kind = "tvcatalog"
        res = loadTvCatalog(req.url)
    else if kind = "tbqueue"
        res = tbQueue(sOr(req.key), sOr(req.hash))
    else if kind = "tbcheck"
        res = tbCheck(sOr(req.key), req.hashes)
    else if kind = "tbme"
        d = httpJsonAuth("https://api.torbox.app/v1/api/user/me?settings=false", { "Authorization": "Bearer " + sOr(req.key) }, 15000)
        res = { ok: d <> invalid, data: d }
    else if kind = "subfind"
        res = findSubsByTitle(req.addons, req.title, req.langs)
    end if
    res.kind = kind
    res.manifests = m.manifests
    m.top.result = res
end sub
sub run()
    try
        run_x()
    catch e
        reportCrash("AddonTask.run", e)
        ' devolve sempre uma resposta, para quem espera (ex.: o leitor) não ficar preso
        kind = ""
        if m.top.request <> invalid and m.top.request.kind <> invalid then kind = m.top.request.kind
        m.top.result = { ok: false, kind: kind, error: "erro interno", streams: [], subtitles: [], tracks: [] }
    end try
end sub

' Só codifica ids com caracteres problemáticos (mantém "tt123:1:2" como está)
function pathId(id as string) as string
    for each c in [" ", "/", "?", "#", "%", "&"]
        if Instr(1, id, c) > 0 then return urlEscape(id)
    end for
    return id
end function

' ---------- Manifestos ----------

sub ensureManifests(addons as object)
    missing = []
    urls = []
    for each base in addons
        if not m.manifests.DoesExist(base)
            missing.Push(base)
            urls.Push(base + "/manifest.json")
        end if
    end for
    if urls.Count() = 0 then return
    data = httpJsonMany(urls, 15000, 8)
    for i = 0 to missing.Count() - 1
        if data[i] <> invalid then m.manifests[missing[i]] = data[i]
    end for
end sub

function getManifest(base as string) as dynamic
    if m.manifests.DoesExist(base) then return m.manifests[base]
    return invalid
end function

function listMatch(list as dynamic, value as string, isPrefix as boolean) as boolean
    if list = invalid or type(list) <> "roArray" or list.Count() = 0 then return true
    for each p in list
        if isStr(p)
            if isPrefix
                if Left(value, Len(p)) = p then return true
            else if p = value
                return true
            end if
        end if
    end for
    return false
end function

function addonSupports(man as dynamic, resource as string, mtype as string, id as string) as boolean
    if man = invalid or man.resources = invalid then return false
    for each r in man.resources
        name = invalid
        types = man.types
        prefixes = man.idPrefixes
        if isStr(r)
            name = r
        else if type(r) = "roAssociativeArray"
            name = r.name
            if r.types <> invalid then types = r.types
            if r.idPrefixes <> invalid then prefixes = r.idPrefixes
        end if
        if isStr(name) and name = resource
            return listMatch(types, mtype, false) and listMatch(prefixes, id, true)
        end if
    end for
    return false
end function

function supportingAddons(addons as object, resource as string, mtype as string, id as string) as object
    ensureManifests(addons)
    out = []
    for each base in addons
        if addonSupports(getManifest(base), resource, mtype, id) then out.Push(base)
    end for
    return out
end function

function catalogNeedsExtra(cat as object) as boolean
    if cat.extraRequired <> invalid and type(cat.extraRequired) = "roArray" and cat.extraRequired.Count() > 0 then return true
    if cat.extra <> invalid and type(cat.extra) = "roArray"
        for each e in cat.extra
            if type(e) = "roAssociativeArray" and e.isRequired <> invalid
                if type(e.isRequired) = "roBoolean" or type(e.isRequired) = "Boolean"
                    if e.isRequired then return true
                end if
            end if
        end for
    end if
    return false
end function

function catalogSupportsSearch(cat as object) as boolean
    if cat.extra <> invalid and type(cat.extra) = "roArray"
        for each e in cat.extra
            if isStr(e.name) and e.name = "search" then return true
        end for
    end if
    if cat.extraSupported <> invalid and type(cat.extraSupported) = "roArray"
        for each e in cat.extraSupported
            if isStr(e) and e = "search" then return true
        end for
    end if
    return false
end function

function typeLabel(t as string) as string
    if t = "movie" then return "Filmes"
    if t = "series" then return "Séries"
    if t = "tv" then return "TV"
    if t = "channel" then return "Canais"
    if t = "other" then return "Outros"
    return t
end function

function metasToItems(data as dynamic, limit as integer) as object
    items = []
    if data = invalid or data.metas = invalid or type(data.metas) <> "roArray" then return items
    for each mt in data.metas
        if items.Count() >= limit then exit for
        if isStr(mt.id) and isStr(mt.type)
            info = ""
            if mt.releaseInfo <> invalid then info = mt.releaseInfo.ToStr()
            if mt.imdbRating <> invalid
                if info <> "" then info = info + "   ·   "
                info = info + "IMDb " + mt.imdbRating.ToStr()
            end if
            if mt.genres <> invalid and type(mt.genres) = "roArray"
                gs = ""
                n = 0
                for each g in mt.genres
                    if isStr(g) and n < 3
                        if gs <> "" then gs = gs + ", "
                        gs = gs + g
                        n = n + 1
                    end if
                end for
                if gs <> ""
                    if info <> "" then info = info + "   ·   "
                    info = info + gs
                end if
            end if
            rating = 0.0
            if mt.imdbRating <> invalid then rating = Val(mt.imdbRating.ToStr())
            items.Push({ id: mt.id, type: mt.type, name: sOr(mt.name), poster: sOr(mt.poster), videoId: "", progress: 0.0, bg: sOr(mt.background), logo: sOr(mt.logo), desc: sOr(mt.description), info: info, rating: rating })
        end if
    end for
    return items
end function

' ---------- Catálogos / metadados / pesquisa ----------

function loadHome(addons as object) as object
    ensureManifests(addons)
    jobs = []
    first = true
    for each base in addons
        man = getManifest(base)
        if man <> invalid and man.catalogs <> invalid and type(man.catalogs) = "roArray"
            for each cat in man.catalogs
                if jobs.Count() >= 30 then exit for
                if isStr(cat.type) and isStr(cat.id) and not catalogNeedsExtra(cat)
                    catName = cat.id
                    if isStr(cat.name) then catName = cat.name
                    title = catName + " · " + typeLabel(cat.type)
                    if not first and isStr(man.name) then title = man.name + " — " + title
                    jobs.Push({ title: title, url: base + "/catalog/" + cat.type + "/" + pathId(cat.id) + ".json" })
                end if
            end for
            first = false
        end if
    end for
    urls = []
    for each j in jobs
        urls.Push(j.url)
    end for
    data = httpJsonMany(urls, 25000, 6)
    rows = []
    for i = 0 to jobs.Count() - 1
        items = metasToItems(data[i], 40)
        if items.Count() > 0 then rows.Push({ title: jobs[i].title, items: items })
    end for
    return { ok: rows.Count() > 0, rows: rows }
end function

function loadMeta(addons as object, mtype as string, id as string) as object
    bases = supportingAddons(addons, "meta", mtype, id)
    urls = []
    for each b in bases
        urls.Push(b + "/meta/" + mtype + "/" + pathId(id) + ".json")
    end for
    data = httpJsonMany(urls, 20000, 4)
    for each d in data
        if d <> invalid and d.meta <> invalid then return { ok: true, meta: d.meta }
    end for
    return { ok: false }
end function

function loadSearch(addons as object, query as string) as object
    ensureManifests(addons)
    jobs = []
    q = urlEscape(query)
    for each base in addons
        man = getManifest(base)
        if man <> invalid and man.catalogs <> invalid and type(man.catalogs) = "roArray"
            for each cat in man.catalogs
                if isStr(cat.type) and isStr(cat.id) and catalogSupportsSearch(cat)
                    title = typeLabel(cat.type)
                    if isStr(man.name) then title = title + " · " + man.name
                    jobs.Push({ title: title, url: base + "/catalog/" + cat.type + "/" + pathId(cat.id) + "/search=" + q + ".json" })
                end if
            end for
        end if
    end for
    urls = []
    for each j in jobs
        urls.Push(j.url)
    end for
    data = httpJsonMany(urls, 25000, 6)
    rows = []
    for i = 0 to jobs.Count() - 1
        items = metasToItems(data[i], 40)
        if items.Count() > 0 then rows.Push({ title: jobs[i].title, items: items })
    end for
    return { ok: true, rows: rows }
end function

' ---------- Fontes (streams) ----------

function streamQuality(s as string) as integer
    t = LCase(s)
    if Instr(1, t, "2160p") > 0 or Instr(1, t, "4k") > 0 or Instr(1, t, "uhd") > 0 then return 2160
    if Instr(1, t, "1440p") > 0 then return 1440
    if Instr(1, t, "1080p") > 0 then return 1080
    if Instr(1, t, "720p") > 0 then return 720
    if Instr(1, t, "576p") > 0 or Instr(1, t, "480p") > 0 then return 480
    return 0
end function

' A fonte cabe nas capacidades desta Roku? (4K, HEVC, Dolby Vision, AV1, DTS, TrueHD)
function compatible(s as string, q as integer, caps as dynamic, rxs as object) as boolean
    if caps = invalid or caps.known <> true then return true
    if q >= 2160 and caps.uhd <> true then return false
    if rxs.hevc.IsMatch(s)
        if caps.hevc <> true then return false
        if rxs.tenbit.IsMatch(s) and caps.hevc10 <> true then return false
    end if
    if rxs.av1.IsMatch(s) and caps.av1 <> true then return false
    if rxs.dv.IsMatch(s) and not rxs.hdr.IsMatch(s) and caps.dv <> true then return false
    if rxs.dts.IsMatch(s) and caps.dts <> true then return false
    if rxs.truehd.IsMatch(s) and caps.truehd <> true then return false
    return true
end function

' Fontes que a Roku muitas vezes não lê: Dolby Vision sem HDR (perfil 5), AV1, 3D
function streamRisky(s as string) as boolean
    t = LCase(s)
    if Instr(1, t, "av1") > 0 or Instr(1, t, " 3d") > 0 or Instr(1, t, ".3d.") > 0 then return true
    dv = Instr(1, t, " dv") > 0 or Instr(1, t, ".dv.") > 0 or Instr(1, t, "dovi") > 0 or Instr(1, t, "dolby vision") > 0 or Instr(1, t, "dolbyvision") > 0
    hdr = Instr(1, t, "hdr") > 0
    return dv and not hdr
end function

function langMatch(s as string, tokens as object) as boolean
    if tokens.Count() = 0 then return false
    t = LCase(s)
    for each tk in tokens
        if Instr(1, t, tk) > 0 then return true
    end for
    return false
end function

function langFirst(list as object) as object
    a = []
    b = []
    for each x in list
        if x.lang
            a.Push(x)
        else
            b.Push(x)
        end if
    end for
    a.Append(b)
    return a
end function

function loadStreams(addons as object, mtype as string, id as string, prefs as object) as object
    bases = supportingAddons(addons, "stream", mtype, id)
    urls = []
    for each b in bases
        urls.Push(b + "/stream/" + mtype + "/" + pathId(id) + ".json")
    end for
    data = httpJsonMany(urls, 30000, 8)
    maxQ = 0
    if prefs.maxQ <> invalid then maxQ = prefs.maxQ
    cachedOnly = (prefs.cachedOnly = true)
    hideRisky = (prefs.hideRisky = true)
    hasDebrid = (prefs.hasDebrid = true)
    if prefs.ignoreMax = true then maxQ = 0
    rxAtmos = CreateObject("roRegex", "atmos", "i")
    rxLossless = CreateObject("roRegex", "truehd|dts[ .-]?hd|dts[ .-]?x\b|dts[ .-]?ma\b|\bflac\b|\blpcm\b|\bpcm\b", "i")
    rxSurround = CreateObject("roRegex", "[57]\.1|\b6ch\b|\b8ch\b|ddp?[ .]?[57]|dd\+[ .]?[57]|e-?ac-?3|atmos|truehd|dts", "i")
    rxDeb = CreateObject("roRegex", "\[(TB|RD|AD|PM|DL|OC|ED|PP)\s*[+\-]?", "")
    caps = prefs.caps
    rxs = {
        hevc: CreateObject("roRegex", "(hevc|x265|h\.?265)", "i"),
        dv: CreateObject("roRegex", "(\bdv\b|dovi|dolby ?vision)", "i"),
        hdr: CreateObject("roRegex", "\bhdr", "i"),
        av1: CreateObject("roRegex", "\bav1\b", "i"),
        dts: CreateObject("roRegex", "\bdts", "i"),
        truehd: CreateObject("roRegex", "truehd", "i"),
        tenbit: CreateObject("roRegex", "(10.?bit|hdr|\bdv\b|dovi)", "i")
    }
    stats = invalid
    if prefs.srcStats <> invalid then stats = ParseJson(prefs.srcStats)
    if stats <> invalid then stats.SetModeCaseSensitive()
    dubPref = ""
    if prefs.dubPref <> invalid then dubPref = prefs.dubPref
    badSet = {}
    if prefs.badSrc <> invalid and type(prefs.badSrc) = "roArray"
        for each bk in prefs.badSrc
            if isStr(bk) then badSet[bk] = true
        end for
    end if
    langTokens = []
    if prefs.langTokens <> invalid then langTokens = prefs.langTokens
    good = []
    riskyList = []
    later = []
    skipped = 0
    filtered = 0
    total = 0
    for i = 0 to bases.Count() - 1
        d = data[i]
        man = getManifest(bases[i])
        addonName = bases[i]
        if man <> invalid and isStr(man.name) then addonName = man.name
        if d <> invalid and d.streams <> invalid and type(d.streams) = "roArray"
            for each s in d.streams
                total = total + 1
                if isStr(s.url) and LCase(Left(s.url, 4)) = "http"
                    filename = ""
                    headers = []
                    bh = s.behaviorHints
                    if bh <> invalid and type(bh) = "roAssociativeArray"
                        filename = sOr(bh.filename)
                        if bh.proxyHeaders <> invalid and type(bh.proxyHeaders) = "roAssociativeArray" and bh.proxyHeaders.request <> invalid
                            for each hk in bh.proxyHeaders.request
                                v = bh.proxyHeaders.request[hk]
                                if v <> invalid then headers.Push(hk + ":" + v.ToStr())
                            end for
                        end if
                    end if
                    label = sOr(s.name)
                    detail = sOr(s.title)
                    if detail = "" then detail = sOr(s.description)
                    q = streamQuality(label + " " + detail + " " + filename)
                    uncached = Instr(1, LCase(label), "download]") > 0
                    risky = streamRisky(label + " " + detail + " " + filename) or not compatible(label + " " + detail + " " + filename, q, caps, rxs)
                    dub = audioDub(label + " " + detail + " " + filename)
                    full = label + " " + detail + " " + filename
                    rng = videoRange(full)
                    item = { addon: addonName, name: label, title: detail, url: s.url, filename: filename, headers: headers, q: q, cached: not uncached, risky: risky, debrid: rxDeb.IsMatch(label), dub: dub, range: rng, src: srcKind(full), atmos: rxAtmos.IsMatch(full), lossless: rxLossless.IsMatch(full), surround: rxSurround.IsMatch(full), gb: fltSizeGb(full), lang: langMatch(full, langTokens) or dubMatches(dub, dubPref) }
                    if maxQ > 0 and q > maxQ
                        filtered = filtered + 1
                    else if cachedOnly and uncached
                        filtered = filtered + 1
                    else if hideRisky and risky
                        filtered = filtered + 1
                    else if uncached or badSet.DoesExist(urlKey(s.url)) or statBad(stats, label, q, filename)
                        ' por descarregar, ou fez a Roku fechar a app numa vez anterior: fica para o fim
                        later.Push(item)
                    else if risky
                        riskyList.Push(item)
                    else
                        good.Push(item)
                    end if
                else
                    skipped = skipped + 1
                end if
            end for
        end if
    end for
    ' dentro de cada grupo, as fontes no idioma preferido vêm primeiro
    good = preferRange(rankDebrid(good, hasDebrid), wantedRange(prefs))
    ' filtros fixados pelo utilizador (ex.: «só Dolby Vision»): essas fontes vão para a frente
    good = fltPinnedFirst(good, prefs.pinFilters, prefs.caps)
    good.Append(rankDebrid(riskyList, hasDebrid))
    good.Append(rankDebrid(later, hasDebrid))
    return { ok: true, streams: good, skipped: skipped, filtered: filtered, total: total }
end function

function urlKey(url as string) as string
    u = LCase(url)
    p = Instr(1, u, "?")
    if p > 0 then u = Left(u, p - 1)
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(u)
    d = CreateObject("roEVPDigest")
    d.Setup("md5")
    return Left(d.Process(ba), 12)
end function

' Gama de imagem: "dvhdr" (DV com HDR10 por baixo), "dv" (só DV, perfil 5), "hdr10p", "hdr10", "hlg" ou "sdr".
' «HDRip» é um formato de cópia, não HDR — por isso não basta procurar «hdr».
function videoRange(s as string) as string
    rxDv = CreateObject("roRegex", "\bdv\b|dovi|dolby[ .-]?vision", "i")
    rxProf = CreateObject("roRegex", "(?:\bdv|dovi|dolby[ .-]?vision)[ .-]?(?:profile[ .-]?|p)([4578])(?:\.\d)?\b", "i")
    rxP = CreateObject("roRegex", "hdr10\+|hdr10plus|hdr10p\b|hdr\+", "i")
    rxH = CreateObject("roRegex", "\bhdr(?:10)?\b|\bhdr[ .-]|\buhd[ .-]?blu-?ray\b|\bpq\b|bt\.?2020", "i")
    rxL = CreateObject("roRegex", "\bhlg\b", "i")
    if rxDv.IsMatch(s)
        mm = rxProf.Match(s)
        if mm.Count() > 1
            if mm[1] = "5" then return "dv"
            if mm[1] = "7" or mm[1] = "8" then return "dvhdr"
        end if
        if rxP.IsMatch(s) or rxH.IsMatch(s) then return "dvhdr"
        return "dv"
    end if
    if rxP.IsMatch(s) then return "hdr10p"
    if rxH.IsMatch(s) then return "hdr10"
    if rxL.IsMatch(s) then return "hlg"
    return "sdr"
end function

' Origem do ficheiro: "remux", "bluray", "web", "hdtv", "cam" ou ""
function srcKind(s as string) as string
    if CreateObject("roRegex", "\bcam(?:-?rip)?\b|\bhd-?cam\b|\bhd-?ts\b|telesync|\btelecine\b|\bhd-?tc\b|\bscreener\b|\bdvd-?scr\b|[ \[(]ts[ \])]|[ \[(]tc[ \])]", "i").IsMatch(s) then return "cam"
    if CreateObject("roRegex", "\bremux\b|\bbdremux\b", "i").IsMatch(s) then return "remux"
    if CreateObject("roRegex", "blu-?ray|\bbd-?rip\b|\bbr-?rip\b|\bbdmux\b|\buhd[ .-]?bd\b", "i").IsMatch(s) then return "bluray"
    if CreateObject("roRegex", "web[ .-]?dl|web-?rip|\bweb\b|\bamzn\b|\bnf\b|\bdsnp\b|\batvp\b|\bhmax\b|\bpcok\b|\bhulu\b", "i").IsMatch(s) then return "web"
    if CreateObject("roRegex", "\bhdtv\b|\bpdtv\b|\btv-?rip\b", "i").IsMatch(s) then return "hdtv"
    return ""
end function

' A imagem que a preferência pede: "auto" = o melhor que esta TV mostra
function wantedRange(prefs as object) as string
    pref = "auto"
    if prefs.rangePref <> invalid then pref = prefs.rangePref
    if pref = "dv" or pref = "hdr" or pref = "sdr" then return pref
    if pref = "off" then return ""
    caps = prefs.caps
    if caps = invalid or caps.known <> true then return ""
    if caps.dv = true then return "dv"
    if caps.hdr = true then return "hdr"
    return "sdr"
end function

function rangeMatches(r as string, want as string, caps as dynamic) as boolean
    if want = "dv" then return r = "dv" or r = "dvhdr"
    if want = "hdr" then return r = "hdr10" or r = "hdr10p" or r = "hlg" or r = "dvhdr"
    if want = "sdr" then return r = "sdr"
    return false
end function

' Dentro de cada qualidade, as fontes com a imagem desejada passam à frente (não troca 4K por 1080p)
function preferRange(list as object, want as string) as object
    if want = "" or list.Count() < 2 then return list
    byQ = {}
    order = []
    for each x in list
        k = Int(numOr0(x.q)).ToStr()
        if not byQ.DoesExist(k)
            byQ[k] = { a: [], b: [] }
        end if
        if rangeMatches(sOr(x.range), want, invalid)
            byQ[k].a.Push(x)
        else
            byQ[k].b.Push(x)
        end if
        order.Push(k)
    end for
    for each k in byQ
        byQ[k].a.Append(byQ[k].b)
        byQ[k].i = 0
    end for
    out = []
    for each k in order
        g = byQ[k]
        out.Push(g.a[g.i])
        g.i = g.i + 1
    end for
    return out
end function

function numOr0(v as dynamic) as float
    if v = invalid then return 0
    return v
end function

' Língua do áudio: "br" (dublado PT-BR), "pt" (dobrado PT-PT), "por" (português sem variante) ou ""
function audioDub(s as string) as string
    rxBr = CreateObject("roRegex", "dublad|pt-?br|\bbr[ .-]?dub|\bnacional\b|brazil|brasil|\bpob\b|🇧🇷", "i")
    if rxBr.IsMatch(s) then return "br"
    rxPt = CreateObject("roRegex", "pt-?pt\b|\bportugal\b|dobrad|europeu|🇵🇹", "i")
    if rxPt.IsMatch(s) then return "pt"
    rxSubs = CreateObject("roRegex", "(legendad\w*|\bleg\b|subtitles?|\bsubs?\b)[ .:_-]*(pt(-?br|-?pt)?|por\w*|portugu\w*)?", "i")
    rxPor = CreateObject("roRegex", "portugu|\bpor\b|\bpt\b", "i")
    if rxPor.IsMatch(rxSubs.ReplaceAll(s, " ")) then return "por"
    return ""
end function

function dubMatches(dub as string, pref as string) as boolean
    if pref = "" or pref = "off" or dub = "" then return false
    ' «any»: qualquer áudio em português (Portugal ou Brasil)
    if pref = "any" then return true
    return dub = pref or dub = "por"
end function

' Com conta debrid: fontes debrid primeiro; sem conta: ligações diretas primeiro
function rankDebrid(list as object, hasDebrid as boolean) as object
    a = []
    b = []
    for each x in list
        if x.debrid = hasDebrid
            a.Push(x)
        else
            b.Push(x)
        end if
    end for
    out = langFirst(a)
    out.Append(langFirst(b))
    return out
end function

' ---------- Legendas ----------

function langName(code as string) as string
    c = LCase(code)
    names = { por: "Português", pob: "Português (BR)", "pt-br": "Português (BR)", pt: "Português", eng: "Inglês", en: "Inglês", spa: "Espanhol", es: "Espanhol", fre: "Francês", fra: "Francês", fr: "Francês", ita: "Italiano", ger: "Alemão", deu: "Alemão", rus: "Russo", jpn: "Japonês", ara: "Árabe", chi: "Chinês", zho: "Chinês" }
    if names.DoesExist(c) then return names[c]
    return code
end function

function rokuLang(code as string) as string
    c = LCase(code)
    if c = "pob" or c = "pt" or c = "pt-br" or c = "pt-pt" then return "por"
    if c = "en" then return "eng"
    if c = "es" then return "spa"
    if c = "fr" or c = "fra" then return "fre"
    return c
end function

function loadSubtitles(addons as object, mtype as string, id as string, langs as dynamic) as object
    bases = supportingAddons(addons, "subtitles", mtype, id)
    if bases.Count() = 0 then bases = ["https://opensubtitles-v3.strem.io"]
    urls = []
    for each b in bases
        urls.Push(b + "/subtitles/" + mtype + "/" + pathId(id) + ".json")
    end for
    data = httpJsonMany(urls, 15000, 4)
    all = []
    for each d in data
        if d <> invalid and d.subtitles <> invalid and type(d.subtitles) = "roArray"
            for each s in d.subtitles
                if isStr(s.url) and isStr(s.lang) then all.Push({ url: s.url, lang: s.lang })
            end for
        end if
    end for
    out = []
    if langs = invalid or type(langs) <> "roArray" or langs.Count() = 0 then langs = ["por", "pob", "eng"]
    for each l in langs
        n = 0
        for each s in all
            if out.Count() < 8 and n < 3 and LCase(s.lang) = LCase(l)
                n = n + 1
                out.Push({ url: s.url, lang: s.lang, label: langName(s.lang) + " " + n.ToStr() })
            end if
        end for
    end for
    return { ok: true, subtitles: out }
end function

function downloadSubtitles(items as dynamic) as object
    tracks = []
    if items = invalid or items.Count() = 0 then return { ok: true, tracks: tracks }
    fs = CreateObject("roFileSystem")
    stamp = CreateObject("roDateTime").AsSeconds().ToStr()
    urls = []
    paths = []
    for i = 0 to items.Count() - 1
        urls.Push(items[i].url)
        paths.Push("tmp:/sub_" + stamp + "_" + i.ToStr() + ".srt")
    end for
    ok = httpDownloadMany(urls, paths, 12000, 6)
    for i = 0 to items.Count() - 1
        if ok[i]
            path = paths[i]
            head = Left(ReadAsciiFile(path), 20)
            if Instr(1, head, "WEBVTT") > 0
                vtt = "tmp:/sub_" + stamp + "_" + i.ToStr() + ".vtt"
                if fs.Rename(path, vtt) then path = vtt
            end if
            tracks.Push({ Language: rokuLang(items[i].lang), TrackName: path, Description: items[i].label + " (OpenSubtitles)" })
        end if
    end for
    return { ok: true, tracks: tracks }
end function

' ---------- IPTV (M3U) ----------

function m3uAttr(line as string, key as string) as string
    tag = key + "=" + Chr(34)
    p = Instr(1, line, tag)
    if p = 0 then return ""
    startPos = p + Len(tag)
    q = Instr(startPos, line, Chr(34))
    if q = 0 then return ""
    return Mid(line, startPos, q - startPos)
end function

function m3uName(line as string) as string
    lastQuote = 0
    p = Instr(1, line, Chr(34))
    while p > 0
        lastQuote = p
        p = Instr(p + 1, line, Chr(34))
    end while
    c = Instr(lastQuote + 1, line, ",")
    if c = 0 then return ""
    return Mid(line, c + 1).Trim()
end function

function fixIptvUrl(url as string) as string
    ' Servidores Xtream: /live/user/pass/123.ts -> .m3u8 (a Roku prefere HLS)
    if Instr(1, url, "/live/") > 0 and LCase(Right(url, 3)) = ".ts"
        return Left(url, Len(url) - 3) + ".m3u8"
    end if
    return url
end function

function loadM3u(url as string) as object
    r = httpGet(url, 60000)
    if not r.ok then return { ok: false, url: url, error: "Não foi possível descarregar a lista (código " + r.code.ToStr() + ")." }
    body = r.body
    if Instr(1, body, "#EXTINF") = 0
        return { ok: false, url: url, error: "O ficheiro não parece ser uma lista M3U (ou está vazio)." }
    end if
    epgUrl = ""
    hdrEnd = Instr(1, body, Chr(10))
    if hdrEnd > 0
        hdr = Left(body, hdrEnd)
        epgUrl = m3uAttr(hdr, "url-tvg")
        if epgUrl = "" then epgUrl = m3uAttr(hdr, "x-tvg-url")
        c = Instr(1, epgUrl, ",")
        if c > 0 then epgUrl = Left(epgUrl, c - 1)
    end if
    lines = body.Split(Chr(10))
    body = ""
    groups = {}
    order = []
    total = 0
    pending = invalid
    for each raw in lines
        line = raw.Trim()
        if Left(line, 7) = "#EXTINF"
            pending = { name: m3uName(line), logo: m3uAttr(line, "tvg-logo"), group: m3uAttr(line, "group-title"), tid: m3uAttr(line, "tvg-id"), chno: m3uAttr(line, "tvg-chno") }
        else if Left(line, 7) = "#EXTGRP" and pending <> invalid
            pending.group = Mid(line, 9).Trim()
        else if line <> "" and Left(line, 1) <> "#" and pending <> invalid
            g = pending.group
            if g = "" then g = "Sem categoria"
            ' iptv-org usa "Categoria;Outra" — fica a primeira
            sc = Instr(1, g, ";")
            if sc > 1 then g = Left(g, sc - 1)
            if not groups.DoesExist(g)
                groups[g] = []
                order.Push(g)
            end if
            name = pending.name
            if name = "" then name = "Canal " + (total + 1).ToStr()
            total = total + 1
            num = total
            if pending.chno <> "" then num = Int(Val(pending.chno))
            groups[g].Push({ name: name, logo: pending.logo, url: fixIptvUrl(line), tid: pending.tid, num: num })
            pending = invalid
            if total >= 15000 then exit for
        end if
    end for
    out = []
    for each g in order
        out.Push({ title: g, channels: groups[g] })
    end for
    return { ok: total > 0, url: url, groups: out, total: total, epgUrl: epgUrl, error: "A lista não tem canais." }
end function

' ---------- TV ao vivo via add-ons Stremio ----------

function isTvType(t as dynamic) as boolean
    return isStr(t) and (t = "tv" or t = "channel")
end function

' Devolve os add-ons que têm catálogos de TV, com os grupos (catálogo / género)
function loadTvSources(addons as object) as object
    ensureManifests(addons)
    out = []
    for each base in addons
        man = getManifest(base)
        if man <> invalid and man.catalogs <> invalid and type(man.catalogs) = "roArray"
            groups = []
            for each cat in man.catalogs
                if isTvType(cat.type) and isStr(cat.id)
                    catName = cat.id
                    if isStr(cat.name) then catName = cat.name
                    baseUrl = base + "/catalog/" + cat.type + "/" + pathId(cat.id)
                    genres = []
                    genreRequired = false
                    otherRequired = false
                    if cat.extra <> invalid and type(cat.extra) = "roArray"
                        for each e in cat.extra
                            if type(e) = "roAssociativeArray" and isStr(e.name)
                                req = false
                                if e.isRequired <> invalid and (type(e.isRequired) = "roBoolean" or type(e.isRequired) = "Boolean") then req = e.isRequired
                                if e.name = "genre"
                                    genreRequired = req
                                    if e.options <> invalid and type(e.options) = "roArray" then genres = e.options
                                else if req
                                    otherRequired = true
                                end if
                            end if
                        end for
                    end if
                    if not otherRequired
                        if not genreRequired
                            groups.Push({ title: catName, url: baseUrl + ".json" })
                        end if
                        n = 0
                        for each g in genres
                            if isStr(g) and n < 80
                                n = n + 1
                                groups.Push({ title: catName + " · " + g, url: baseUrl + "/genre=" + urlEscape(g) + ".json" })
                            end if
                        end for
                    end if
                end if
            end for
            if groups.Count() > 0
                name = base
                if isStr(man.name) then name = man.name
                out.Push({ n: name, base: base, groups: groups })
            end if
        end if
    end for
    return { ok: true, sources: out }
end function

' Canais de um catálogo de TV; o URL "stremio:tipo:id" é resolvido no leitor
function loadTvCatalog(url as string) as object
    d = httpJson(url)
    channels = []
    if d <> invalid and d.metas <> invalid and type(d.metas) = "roArray"
        for each mt in d.metas
            if isStr(mt.id) and channels.Count() < 3000
                t = "tv"
                if isStr(mt.type) then t = mt.type
                logo = sOr(mt.logo)
                if logo = "" then logo = sOr(mt.poster)
                channels.Push({ name: sOr(mt.name), logo: logo, url: "stremio:" + t + ":" + mt.id })
            end if
        end for
    end if
    return { ok: d <> invalid, url: url, channels: channels }
end function

' ---------- Introdução / genérico (IntroDB) ----------

function segOf(v as dynamic) as dynamic
    if v = invalid or type(v) <> "roAssociativeArray" then return invalid
    if v.start_sec = invalid or v.end_sec = invalid then return invalid
    return { s: v.start_sec, e: v.end_sec }
end function

function loadSegments(id as string, mtype as string) as object
    parts = id.Split(":")
    if parts.Count() = 0 or Left(parts[0], 2) <> "tt" then return { ok: false }
    url = "https://api.introdb.app/segments?imdb_id=" + parts[0]
    if mtype = "series" and parts.Count() >= 3
        url = url + "&season=" + parts[1] + "&episode=" + parts[2]
    else
        url = url + "&is_movie=true"
    end if
    d = httpJson(url)
    if d = invalid then return { ok: false }
    return { ok: true, intro: segOf(d.intro), recap: segOf(d.recap), outro: segOf(d.outro) }
end function

' ---------- Teste rápido de um link antes de reproduzir ----------

function probeUrl(url as string, headers as dynamic) as object
    r = httpHead(url, headers, 12000)
    code = r.code
    ct = r.contentType
    bad = false
    reason = ""
    ' 405/501: o servidor só não aceita pedidos HEAD — o vídeo pode estar bom
    if code = 404 or code = 410 or (code >= 500 and code < 600 and code <> 501)
        bad = true
        reason = "o servidor respondeu " + code.ToStr()
    else if (code = 200 or code = 206) and (Left(ct, 5) = "text/" or Instr(1, ct, "json") > 0)
        bad = true
        reason = "o link devolveu uma página em vez de vídeo"
    end if
    return { ok: not bad, code: code, reason: reason }
end function

' ---------- Sincronizar legendas (desloca os tempos de um SRT/VTT) ----------

function fmtSubTime(ms as integer, sep as string) as string
    if ms < 0 then ms = 0
    h = Int(ms / 3600000)
    mi = Int((ms - h * 3600000) / 60000)
    sc = Int((ms - h * 3600000 - mi * 60000) / 1000)
    rest = ms - h * 3600000 - mi * 60000 - sc * 1000
    return two(h) + ":" + two(mi) + ":" + two(sc) + sep + three(rest)
end function

function two(n as integer) as string
    if n < 10 then return "0" + n.ToStr()
    return n.ToStr()
end function

function three(n as integer) as string
    if n < 10 then return "00" + n.ToStr()
    if n < 100 then return "0" + n.ToStr()
    return n.ToStr()
end function

function shiftTimes(part as string, re as object, ms as integer) as string
    mt = re.Match(part)
    if mt.Count() < 5 then return part
    h = 0
    if mt[1] <> "" then h = Val(mt[1])
    total = Int(((h * 60 + Val(mt[2])) * 60 + Val(mt[3])) * 1000 + Val(mt[4])) + ms
    sep = ","
    if Instr(1, mt[0], ".") > 0 then sep = "."
    return re.Replace(part, fmtSubTime(total, sep))
end function

function shiftSubtitle(src as string, ms as integer) as object
    body = ReadAsciiFile(src)
    if body = "" then return { ok: false }
    re = CreateObject("roRegex", "(?:(\d+):)?(\d\d):(\d\d)[,.](\d\d\d)", "")
    lines = body.Split(Chr(10))
    out = []
    for each ln in lines
        p = Instr(1, ln, "-->")
        if p > 0
            a = Left(ln, p - 1)
            b = Mid(ln, p + 3)
            out.Push(shiftTimes(a, re, ms) + "-->" + shiftTimes(b, re, ms))
        else
            out.Push(ln)
        end if
    end for
    ext = ".srt"
    if LCase(Right(src, 4)) = ".vtt" then ext = ".vtt"
    dst = "tmp:/subshift_" + CreateObject("roDateTime").AsSeconds().ToStr() + "_" + Rnd(99999).ToStr() + ext
    sep = Chr(10)
    joined = ""
    ' junta em blocos para não ficar lento em ficheiros grandes
    chunk = []
    for each ln in out
        chunk.Push(ln)
        if chunk.Count() >= 200
            joined = joined + chunk.Join(sep) + sep
            chunk = []
        end if
    end for
    if chunk.Count() > 0 then joined = joined + chunk.Join(sep)
    ok = WriteAsciiFile(dst, joined)
    return { ok: ok, path: dst }
end function

' ---------- TorBox ----------

function loadTorbox(key as string) as object
    d = httpJsonAuth("https://api.torbox.app/v1/api/torrents/mylist?bypass_cache=true", { Authorization: "Bearer " + key }, 20000)
    if d = invalid then return { ok: false, error: "Sem resposta do TorBox." }
    if type(d.success) = "roBoolean" and not d.success then return { ok: false, error: sOr(d.detail) }
    items = []
    if d.data <> invalid and type(d.data) = "roArray"
        for each t in d.data
            files = []
            if t.files <> invalid and type(t.files) = "roArray"
                for each f in t.files
                    nm = sOr(f.short_name)
                    if nm = "" then nm = sOr(f.name)
                    ln = LCase(nm)
                    isVideo = Right(ln, 4) = ".mkv" or Right(ln, 4) = ".mp4" or Right(ln, 4) = ".m4v" or Right(ln, 4) = ".avi" or Right(ln, 3) = ".ts" or Right(ln, 5) = ".webm"
                    if isVideo and f.id <> invalid
                        sz = 0.0
                        if f.size <> invalid then sz = f.size
                        files.Push({ id: f.id.ToStr(), name: nm, size: sz })
                    end if
                end for
            end if
            prog = 0.0
            if t.progress <> invalid then prog = t.progress
            sz = 0.0
            if t.size <> invalid then sz = t.size
            done = false
            if t.download_finished <> invalid and type(t.download_finished) = "roBoolean" then done = t.download_finished
            tid = ""
            if t.id <> invalid then tid = t.id.ToStr()
            items.Push({ id: tid, name: sOr(t.name), state: sOr(t.download_state), progress: prog, size: sz, finished: done, files: files })
        end for
    end if
    return { ok: true, items: items }
end function

' ---------- Catálogo por URL (ex.: "Mais como este") ----------

function loadCatalogUrl(url as string, exclude as dynamic) as object
    items = metasToItems(httpJson(url), 30)
    out = []
    for each it in items
        if exclude = invalid or it.id <> exclude then out.Push(it)
    end for
    return { ok: true, items: out }
end function

' ---------- Novos episódios das séries da biblioteca ----------

function isoDate(s as string) as integer
    if s = "" then return 0
    d = CreateObject("roDateTime")
    d.FromISO8601String(s)
    return d.AsSeconds()
end function

function loadCalendar(items as dynamic) as object
    if items = invalid or items.Count() = 0 then return { ok: true, recent: [], upcoming: [] }
    urls = []
    for each it in items
        urls.Push("https://v3-cinemeta.strem.io/meta/series/" + it.id + ".json")
    end for
    data = httpJsonMany(urls, 25000, 8)
    now = CreateObject("roDateTime").AsSeconds()
    recent = []
    upcoming = []
    months = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]
    for i = 0 to items.Count() - 1
        d = data[i]
        if d <> invalid and d.meta <> invalid and d.meta.videos <> invalid and type(d.meta.videos) = "roArray"
            best = invalid
            nextUp = invalid
            for each v in d.meta.videos
                t = isoDate(sOr(v.released))
                s = 0
                if v.season <> invalid then s = v.season
                if t > 0 and s > 0
                    if t <= now and now - t <= 14 * 86400
                        if best = invalid or t > best.t then best = { v: v, t: t }
                    else if t > now and t - now <= 21 * 86400
                        if nextUp = invalid or t < nextUp.t then nextUp = { v: v, t: t }
                    end if
                end if
            end for
            for each pick in [best, nextUp]
                if pick <> invalid
                    v = pick.v
                    dt = CreateObject("roDateTime")
                    dt.FromSeconds(pick.t)
                    ep = 0
                    if v.episode <> invalid then ep = v.episode
                    if v.number <> invalid and v.episode = invalid then ep = v.number
                    label = "T" + v.season.ToStr() + " E" + ep.ToStr() + " · " + dt.GetDayOfMonth().ToStr() + " " + months[dt.GetMonth() - 1]
                    entry = { id: items[i].id, type: "series", name: sOr(d.meta.name) + " — " + label, poster: sOr(d.meta.poster), videoId: sOr(v.id), progress: 0.0, bg: sOr(d.meta.background), logo: sOr(d.meta.logo), desc: sOr(v.name), info: label, sk: -pick.t }
                    if pick.t <= now
                        recent.Push(entry)
                    else
                        entry.sk = pick.t
                        upcoming.Push(entry)
                    end if
                end if
            end for
        end if
    end for
    recent.SortBy("sk")
    upcoming.SortBy("sk")
    return { ok: true, recent: recent, upcoming: upcoming }
end function

' ---------- Filmografia de um ator/realizador ----------

function loadPerson(name as string, tmdbKey as dynamic, addons as object) as object
    key = sOr(tmdbKey)
    if key <> ""
        s = httpJson("https://api.themoviedb.org/3/search/person?api_key=" + key + "&language=pt-PT&query=" + urlEscape(name))
        if s <> invalid and s.results <> invalid and s.results.Count() > 0
            pid = s.results[0].id.ToStr()
            cred = httpJson("https://api.themoviedb.org/3/person/" + pid + "/combined_credits?api_key=" + key + "&language=pt-PT")
            if cred <> invalid
                list = []
                for each grp in [cred.cast, cred.crew]
                    if grp <> invalid and type(grp) = "roArray"
                        for each c in grp
                            mt = sOr(c.media_type)
                            if (mt = "movie" or mt = "tv") and c.id <> invalid
                                pop = 0.0
                                if c.popularity <> invalid then pop = c.popularity
                                list.Push({ tid: c.id.ToStr(), mt: mt, pop: -pop })
                            end if
                        end for
                    end if
                end for
                list.SortBy("pop")
                seen = {}
                pick = []
                for each c in list
                    k = c.mt + c.tid
                    if not seen.DoesExist(k) and pick.Count() < 30
                        seen[k] = true
                        pick.Push(c)
                    end if
                end for
                urls = []
                for each c in pick
                    urls.Push("https://api.themoviedb.org/3/" + c.mt + "/" + c.tid + "/external_ids?api_key=" + key)
                end for
                ext = httpJsonMany(urls, 20000, 8)
                items = []
                for i = 0 to pick.Count() - 1
                    e = ext[i]
                    if e <> invalid and isStr(e.imdb_id) and e.imdb_id <> ""
                        t = "movie"
                        if pick[i].mt = "tv" then t = "series"
                        items.Push({ id: e.imdb_id, type: t, name: "", poster: "https://images.metahub.space/poster/medium/" + e.imdb_id + "/img", videoId: "", progress: 0.0 })
                    end if
                end for
                if items.Count() > 0 then return { ok: true, rows: [{ title: "Filmografia de " + name, items: items }], source: "tmdb" }
            end if
        end if
    end if
    ' sem chave TMDB: pesquisa pelo nome nos add-ons (como faz o Stremio)
    res = loadSearch(addons, name)
    res.source = "search"
    return res
end function

' ---------- Guia de programação (EPG) ----------

function b64(s as dynamic) as string
    if not isStr(s) or s = "" then return ""
    ba = CreateObject("roByteArray")
    ba.FromBase64String(s)
    return ba.ToAsciiString()
end function

' Xtream: get_short_epg (títulos em base64)
function loadXtreamEpg(url as string) as object
    d = httpJson(url)
    out = []
    if d <> invalid and d.epg_listings <> invalid and type(d.epg_listings) = "roArray"
        for each e in d.epg_listings
            st = 0
            en = 0
            if e.start_timestamp <> invalid then st = Val(e.start_timestamp.ToStr())
            if e.stop_timestamp <> invalid then en = Val(e.stop_timestamp.ToStr())
            out.Push({ t: b64(e.title), s: st, e: en, d: b64(e.description) })
        end for
    end if
    return { ok: true, items: out }
end function

function xmltvTime(s as string) as integer
    ' 20261004183000 +0100
    if Len(s) < 14 then return 0
    iso = Mid(s, 1, 4) + "-" + Mid(s, 5, 2) + "-" + Mid(s, 7, 2) + "T" + Mid(s, 9, 2) + ":" + Mid(s, 11, 2) + ":" + Mid(s, 13, 2) + "Z"
    d = CreateObject("roDateTime")
    d.FromISO8601String(iso)
    secs = d.AsSeconds()
    sp = Instr(15, s, " ")
    if sp > 0
        tz = Mid(s, sp + 1, 5)
        if Len(tz) = 5
            off = (Val(Mid(tz, 2, 2)) * 60 + Val(Mid(tz, 4, 2))) * 60
            if Left(tz, 1) = "+" then secs = secs - off else secs = secs + off
        end if
    end if
    return secs
end function

function xmlAttr(tag as string, name as string) as string
    k = name + "=" + Chr(34)
    p = Instr(1, tag, k)
    if p = 0 then return ""
    q = Instr(p + Len(k), tag, Chr(34))
    if q = 0 then return ""
    return Mid(tag, p + Len(k), q - p - Len(k))
end function

function xmlUnescape(s as string) as string
    r = s.Replace("&amp;", "&").Replace("&lt;", "<").Replace("&gt;", ">").Replace("&quot;", Chr(34)).Replace("&apos;", "'").Replace("&#39;", "'")
    return r
end function

' XMLTV simples (só ficheiros pequenos, sem .gz): programas de agora até +6 h para os canais pedidos
function loadXmlEpg(url as string, ids as object) as object
    lu = LCase(url)
    if Right(lu, 3) = ".gz" then return { ok: false, error: "O guia está comprimido (.gz) — a Roku não o consegue abrir." }
    want = {}
    for each i in ids
        want[i] = true
    end for
    r = httpGet(url, 45000)
    if not r.ok then return { ok: false, error: "Não foi possível descarregar o guia." }
    body = r.body
    if Len(body) > 12000000 then return { ok: false, error: "O guia é demasiado grande para a Roku." }
    now = CreateObject("roDateTime").AsSeconds()
    guide = {}
    p = Instr(1, body, "<programme ")
    count = 0
    scanned = 0
    ' só contam os programas dos canais pedidos (num guia grande, os nossos podem estar no fim do ficheiro)
    while p > 0 and count < 20000 and scanned < 400000
        q = Instr(p, body, "</programme>")
        if q = 0 then exit while
        scanned = scanned + 1
        tagEnd = Instr(p, body, ">")
        tag = Mid(body, p, tagEnd - p + 1)
        ch = xmlAttr(tag, "channel")
        block = ""
        if want.DoesExist(ch) then block = Mid(body, p, q - p)
        if block <> ""
            st = xmltvTime(xmlAttr(tag, "start"))
            en = xmltvTime(xmlAttr(tag, "stop"))
            if en > now and st < now + 6 * 3600
                t1 = Instr(1, block, "<title")
                title = ""
                if t1 > 0
                    t2 = Instr(t1, block, ">")
                    t3 = Instr(t2, block, "</title>")
                    if t2 > 0 and t3 > t2 then title = xmlUnescape(Mid(block, t2 + 1, t3 - t2 - 1))
                end if
                desc = ""
                d1 = Instr(1, block, "<desc")
                if d1 > 0
                    d2 = Instr(d1, block, ">")
                    d3 = Instr(d2, block, "</desc>")
                    if d2 > 0 and d3 > d2 then desc = Left(xmlUnescape(Mid(block, d2 + 1, d3 - d2 - 1)), 300)
                end if
                count = count + 1
                if not guide.DoesExist(ch) then guide[ch] = []
                guide[ch].Push({ t: title, s: st, e: en, d: desc })
            end if
        end if
        p = Instr(q, body, "<programme ")
    end while
    return { ok: true, guide: guide }
end function

' ---------- Legendas automáticas por título (IPTV / TorBox) ----------

function isStopWord(w as string) as boolean
    rx = CreateObject("roRegex", "^((19|20)\d\d|s\d{1,2}e\d{1,2}|\d{3,4}p|4k|uhd|bluray|bdrip|brrip|webrip|web-?dl|web|hdtv|hdrip|dvdrip|x26[45]|h\.?26[45]|hevc|dual|multi|remux)$", "i")
    return rx.IsMatch(w)
end function

function parseMediaTitle(raw as string) as object
    s = raw
    rxExt = CreateObject("roRegex", "\.(mkv|mp4|avi|ts|m4v|mov|wmv|webm)$", "i")
    s = rxExt.ReplaceAll(s, "")
    rxSep = CreateObject("roRegex", "[\._\[\]\(\)\|]", "")
    s = rxSep.ReplaceAll(s, " ")
    rxPre = CreateObject("roRegex", "^\s*[A-Za-z]{2,3}\s*[-:]\s+", "")
    s = rxPre.ReplaceAll(s, "")
    words = s.Split(" ")
    name = ""
    year = 0
    season = 0
    episode = 0
    rxYear = CreateObject("roRegex", "^(19|20)\d\d$", "")
    rxSE = CreateObject("roRegex", "^[sS](\d{1,2})[eE](\d{1,2})$", "")
    stopped = false
    n = 0
    for each w in words
        if w <> ""
            if rxYear.IsMatch(w) and year = 0 and n > 0 then year = Val(w)
            mm = rxSE.Match(w)
            if mm.Count() = 3 and season = 0
                season = Val(mm[1])
                episode = Val(mm[2])
            end if
            if not stopped
                if n > 0 and isStopWord(w)
                    stopped = true
                else
                    if name <> "" then name = name + " "
                    name = name + w
                    n = n + 1
                end if
            end if
        end if
    end for
    return { name: name, year: year, season: season, episode: episode }
end function

function findSubsByTitle(addons as object, title as string, langs as dynamic) as object
    t = parseMediaTitle(title)
    if Len(t.name) < 2 then return { ok: false, subtitles: [] }
    mtype = "movie"
    if t.season > 0 then mtype = "series"
    url = "https://v3-cinemeta.strem.io/catalog/" + mtype + "/top/search=" + CreateObject("roUrlTransfer").Escape(t.name) + ".json"
    data = httpJsonMany([url], 8000, 1)
    d = data[0]
    if d = invalid or d.metas = invalid or type(d.metas) <> "roArray" or d.metas.Count() = 0 then return { ok: false, subtitles: [] }
    pick = d.metas[0]
    if t.year > 0
        for each x in d.metas
            ri = LCase(sOr(x.releaseInfo))
            if Left(ri, 4) = Str(t.year).Trim() then
                pick = x
                exit for
            end if
        end for
    end if
    id = sOr(pick.id)
    if Left(id, 2) <> "tt" then return { ok: false, subtitles: [] }
    if mtype = "series" then id = id + ":" + Str(t.season).Trim() + ":" + Str(t.episode).Trim()
    res = loadSubtitles(addons, mtype, id, langs)
    res.imdb = id
    return res
end function

' ---------- Aprendizagem de fontes (igual a srcSig em Config.brs) ----------

function statBad(stats as dynamic, name as string, q as integer, filename as string) as boolean
    if stats = invalid or type(stats) <> "roAssociativeArray" then return false
    first = name
    p = Instr(1, first, Chr(10))
    if p > 0 then first = Left(first, p - 1)
    grp = ""
    rx = CreateObject("roRegex", "-([A-Za-z0-9]+)(\.[a-z0-9]{2,4})?\s*$", "")
    mm = rx.Match(filename)
    if mm.Count() > 1 then grp = mm[1]
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(LCase(first + "|" + q.ToStr() + "|" + grp))
    d = CreateObject("roEVPDigest")
    d.Setup("md5")
    k = Left(d.Process(ba), 10)
    if not stats.DoesExist(k) then return false
    return stats[k] <= -3
end function

' ---------- TorBox ----------

function tbQueue(key as string, hash as string) as object
    if key = "" or hash = "" then return { ok: false, msg: "Sem chave TorBox." }
    x = newXfer("https://api.torbox.app/v1/api/torrents/createtorrent")
    x.AddHeader("Authorization", "Bearer " + key)
    x.AddHeader("Content-Type", "application/x-www-form-urlencoded")
    port = CreateObject("roMessagePort")
    x.SetMessagePort(port)
    body = "magnet=" + x.Escape("magnet:?xt=urn:btih:" + hash)
    if not x.AsyncPostFromString(body) then return { ok: false, msg: "Não foi possível contactar o TorBox." }
    msg = wait(20000, port)
    if type(msg) <> "roUrlEvent" then return { ok: false, msg: "O TorBox não respondeu." }
    d = ParseJson(msg.GetString())
    if d = invalid then return { ok: false, msg: "Resposta inválida do TorBox." }
    if d.success = true then return { ok: true, msg: "Adicionado ao TorBox." }
    detail = LCase(sOr(d.detail) + " " + sOr(d.error))
    if Instr(1, detail, "already") > 0 or Instr(1, detail, "exist") > 0 then return { ok: true, msg: "Já estava no TorBox." }
    if Instr(1, detail, "limit") > 0 then return { ok: false, msg: "Atingiste o limite de downloads do teu plano TorBox." }
    return { ok: false, msg: "O TorBox recusou: " + sOr(d.detail) }
end function

function tbCheck(key as string, hashes as dynamic) as object
    ready = []
    if key = "" or hashes = invalid then return { ok: false, ready: ready }
    d = httpJsonAuth("https://api.torbox.app/v1/api/torrents/mylist?bypass_cache=true", { "Authorization": "Bearer " + key }, 20000)
    if d = invalid or d.data = invalid or type(d.data) <> "roArray" then return { ok: false, ready: ready }
    want = {}
    for each h in hashes
        want[LCase(sOr(h))] = true
    end for
    for each t in d.data
        h = LCase(sOr(t.hash))
        if want.DoesExist(h)
            st = LCase(sOr(t.download_state))
            done = (t.download_finished = true) or (t.download_present = true) or Instr(1, st, "cached") > 0 or Instr(1, st, "completed") > 0 or Instr(1, st, "uploading") > 0 or Instr(1, st, "seeding") > 0
            if done then ready.Push(h)
        end if
    end for
    return { ok: true, ready: ready }
end function

' «A seguir» no fim de um filme: títulos dos mesmos géneros (os que partilham os dois primeiros géneros
' vêm primeiro), sem o próprio nem os já vistos (req.exclude)
function loadRecommend(req as object) as object
    t = "movie"
    if req.type = "series" then t = "series"
    base = "https://v3-cinemeta.strem.io"
    meta = httpJson(base + "/meta/" + t + "/" + req.id + ".json")
    if meta = invalid or meta.meta = invalid then return { ok: false, items: [] }
    g = meta.meta.genres
    if g = invalid or type(g) <> "roArray" or g.Count() = 0 then return { ok: false, items: [] }
    skip = {}
    skip[req.id] = true
    if req.exclude <> invalid
        for each x in req.exclude
            skip[sOr(x)] = true
        end for
    end if
    lists = []
    for i = 0 to 1
        if i < g.Count()
            d = httpJson(base + "/catalog/" + t + "/top/genre=" + urlEscape(sOr(g[i])) + ".json")
            arr = []
            if d <> invalid and d.metas <> invalid then arr = d.metas
            lists.Push(arr)
        end if
    end for
    second = {}
    if lists.Count() > 1
        for each x in lists[1]
            if x.id <> invalid then second[sOr(x.id)] = true
        end for
    end if
    both = []
    rest = []
    for each x in lists[0]
        id = sOr(x.id)
        if id <> "" and not skip.DoesExist(id)
            inf = sOr(x.releaseInfo)
            if sOr(x.imdbRating) <> ""
                if inf <> "" then inf = inf + "   ·   "
                inf = inf + "IMDb " + sOr(x.imdbRating)
            end if
            it = { id: id, type: t, name: sOr(x.name), poster: sOr(x.poster), bg: "https://images.metahub.space/background/medium/" + id + "/img", desc: sOr(x.description), info: inf }
            if second.DoesExist(id) then both.Push(it) else rest.Push(it)
        end if
    end for
    ' baralha um pouco, para não sugerir sempre os mesmos
    out = []
    for each lst in [both, rest]
        n = lst.Count()
        for i = n - 1 to 1 step -1
            j = Rnd(i + 1) - 1
            tmp = lst[i]
            lst[i] = lst[j]
            lst[j] = tmp
        end for
        for each x in lst
            if out.Count() < 8 then out.Push(x)
        end for
    end for
    return { ok: out.Count() > 0, items: out }
end function

' Sinopse (e título) em português: TMDB (com chave) e/ou Trakt (com o Client ID da conta ligada).
' Prefere português de Portugal; se não houver, do Brasil. Com Trakt junta a classificação etária.
' Devolve { ok, name, desc, lang, cert }.
function loadTranslation(req as object) as object
    id = sOr(req.id).Split(":")[0]
    if Left(id, 2) <> "tt" then return { ok: false }
    isSeries = sOr(req.type) = "series"
    out = { ok: false, name: "", desc: "", lang: "", cert: "" }
    key = sOr(req.tmdbKey)
    if key <> ""
        for each lang in ["pt-PT", "pt-BR"]
            if not out.ok
                d = httpJson("https://api.themoviedb.org/3/find/" + id + "?api_key=" + key + "&external_source=imdb_id&language=" + lang)
                if d <> invalid
                    r = invalid
                    if isSeries and d.tv_results <> invalid and d.tv_results.Count() > 0 then r = d.tv_results[0]
                    if r = invalid and d.movie_results <> invalid and d.movie_results.Count() > 0 then r = d.movie_results[0]
                    if r = invalid and d.tv_results <> invalid and d.tv_results.Count() > 0 then r = d.tv_results[0]
                    if r <> invalid and sOr(r.overview) <> ""
                        nm = sOr(r.title)
                        if nm = "" then nm = sOr(r.name)
                        out = { ok: true, name: nm, desc: sOr(r.overview), lang: lang, cert: "" }
                    end if
                end if
            end if
        end for
    end if
    cid = sOr(req.traktId)
    if cid <> ""
        kindPath = "movies"
        if isSeries then kindPath = "shows"
        h = { "trakt-api-version": "2", "trakt-api-key": cid, "Content-Type": "application/json" }
        if not out.ok
            arr = httpJsonAuth("https://api.trakt.tv/" + kindPath + "/" + id + "/translations/pt", h, 8000)
            if arr <> invalid and type(arr) = "roArray"
                best = invalid
                for each t in arr
                    if sOr(t.overview) <> ""
                        if LCase(sOr(t.country)) = "pt" then best = t
                        if best = invalid then best = t
                    end if
                end for
                if best <> invalid then out = { ok: true, name: sOr(best.title), desc: sOr(best.overview), lang: "pt-" + UCase(sOr(best.country)), cert: "" }
            end if
        end if
        ' classificação etária (dos EUA, convertida para o estilo português)
        sm = httpJsonAuth("https://api.trakt.tv/" + kindPath + "/" + id + "?extended=full", h, 8000)
        if sm <> invalid and type(sm) = "roAssociativeArray"
            out.cert = ptCert(sOr(sm.certification))
            if out.cert <> "" then out.ok = true
        end if
    end if
    return out
end function

function ptCert(c as string) as string
    u = UCase(c)
    if u = "G" or u = "TV-Y" or u = "TV-G" then return "Todos"
    if u = "TV-Y7" then return "M/6"
    if u = "PG" or u = "TV-PG" then return "M/6"
    if u = "PG-13" then return "M/12"
    if u = "TV-14" then return "M/14"
    if u = "R" then return "M/16"
    if u = "NC-17" or u = "TV-MA" then return "M/18"
    return ""
end function

' Nomes dos episódios de uma temporada em português: { "1": "Nome", "2": … }
function loadSeasonTranslation(req as object) as object
    id = sOr(req.id).Split(":")[0]
    sn = sOr(req.season)
    names = {}
    key = sOr(req.tmdbKey)
    if key <> ""
        f = httpJson("https://api.themoviedb.org/3/find/" + id + "?api_key=" + key + "&external_source=imdb_id")
        if f <> invalid and f.tv_results <> invalid and f.tv_results.Count() > 0
            tid = Int(f.tv_results[0].id).ToStr()
            for each lang in ["pt-PT", "pt-BR"]
                if names.Count() = 0
                    d = httpJson("https://api.themoviedb.org/3/tv/" + tid + "/season/" + sn + "?api_key=" + key + "&language=" + lang)
                    if d <> invalid and d.episodes <> invalid
                        for each e in d.episodes
                            nm = sOr(e.name)
                            ' o TMDB devolve «Episódio 3» quando não há tradução: não serve
                            if nm <> "" and Left(LCase(nm), 8) <> "episódio" and Left(LCase(nm), 8) <> "episodio" then names[Int(e.episode_number).ToStr()] = nm
                        end for
                    end if
                end if
            end for
        end if
    end if
    cid = sOr(req.traktId)
    if names.Count() = 0 and cid <> ""
        h = { "trakt-api-version": "2", "trakt-api-key": cid, "Content-Type": "application/json" }
        arr = httpJsonAuth("https://api.trakt.tv/shows/" + id + "/seasons/" + sn + "?translations=pt", h, 10000)
        if arr <> invalid and type(arr) = "roArray"
            for each e in arr
                best = ""
                if e.translations <> invalid and type(e.translations) = "roArray"
                    for each t in e.translations
                        if sOr(t.title) <> ""
                            if LCase(sOr(t.country)) = "pt" or best = "" then best = sOr(t.title)
                        end if
                    end for
                end if
                if best <> "" and e.number <> invalid then names[Int(e.number).ToStr()] = best
            end for
        end if
    end if
    return { ok: names.Count() > 0, names: names }
end function

' Filmografia pelo Trakt (sem chave TMDB): filmes e séries em que a pessoa entrou, os mais recentes primeiro
function loadPersonTrakt(name as string, cid as string) as object
    h = { "trakt-api-version": "2", "trakt-api-key": cid, "Content-Type": "application/json" }
    s = httpJsonAuth("https://api.trakt.tv/search/person?query=" + urlEscape(name) + "&limit=1", h, 8000)
    if s = invalid or type(s) <> "roArray" or s.Count() = 0 or s[0].person = invalid then return { ok: false }
    p = s[0].person
    slug = ""
    if p.ids <> invalid then slug = sOr(p.ids.slug)
    if slug = "" then return { ok: false }
    items = []
    seen = {}
    for each kindPath in ["movies", "shows"]
        d = httpJsonAuth("https://api.trakt.tv/people/" + slug + "/" + kindPath, h, 10000)
        if d <> invalid and type(d) = "roAssociativeArray"
            list = []
            for each grp in [d.cast, d.crew]
                if grp <> invalid
                    if type(grp) = "roArray"
                        list.Append(grp)
                    else if type(grp) = "roAssociativeArray"
                        for each k in grp
                            if type(grp[k]) = "roArray" then list.Append(grp[k])
                        end for
                    end if
                end if
            end for
            for each c in list
                o = c.movie
                t = "movie"
                if o = invalid
                    o = c.show
                    t = "series"
                end if
                if o <> invalid and o.ids <> invalid
                    imdb = sOr(o.ids.imdb)
                    if imdb <> "" and not seen.DoesExist(imdb)
                        seen[imdb] = true
                        yr = 0
                        if o.year <> invalid then yr = Int(o.year)
                        items.Push({ id: imdb, type: t, name: sOr(o.title), poster: "https://images.metahub.space/poster/medium/" + imdb + "/img", videoId: "", progress: 0.0, sk: -yr })
                    end if
                end if
            end for
        end if
    end for
    if items.Count() = 0 then return { ok: false }
    items.SortBy("sk")
    out = []
    for each it in items
        if out.Count() < 60 then out.Push(it)
    end for
    return { ok: true, rows: [{ title: "Filmografia de " + sOr(p.name), items: out }], source: "trakt" }
end function

' Coleção / saga de um filme (TMDB): as partes por ordem de estreia
function loadCollection(req as object) as object
    key = sOr(req.tmdbKey)
    id = sOr(req.id)
    if key = "" or Left(id, 2) <> "tt" then return { ok: false }
    f = httpJson("https://api.themoviedb.org/3/find/" + id + "?api_key=" + key + "&external_source=imdb_id")
    if f = invalid or f.movie_results = invalid or f.movie_results.Count() = 0 then return { ok: false }
    mv = httpJson("https://api.themoviedb.org/3/movie/" + Int(f.movie_results[0].id).ToStr() + "?api_key=" + key + "&language=pt-PT")
    if mv = invalid or mv.belongs_to_collection = invalid then return { ok: false }
    col = httpJson("https://api.themoviedb.org/3/collection/" + Int(mv.belongs_to_collection.id).ToStr() + "?api_key=" + key + "&language=pt-PT")
    if col = invalid or col.parts = invalid or col.parts.Count() < 2 then return { ok: false }
    parts = []
    for each p in col.parts
        parts.Push({ tid: Int(p.id).ToStr(), d: sOr(p.release_date), name: sOr(p.title) })
    end for
    parts.SortBy("d")
    urls = []
    for each p in parts
        urls.Push("https://api.themoviedb.org/3/movie/" + p.tid + "/external_ids?api_key=" + key)
    end for
    ext = httpJsonMany(urls, 15000, 6)
    items = []
    for i = 0 to parts.Count() - 1
        e = ext[i]
        if e <> invalid and isStr(e.imdb_id) and e.imdb_id <> "" and parts[i].d <> ""
            items.Push({ id: e.imdb_id, type: "movie", name: parts[i].name, poster: "https://images.metahub.space/poster/medium/" + e.imdb_id + "/img", videoId: "", progress: 0.0 })
        end if
    end for
    if items.Count() < 2 then return { ok: false }
    return { ok: true, title: sOr(col.name), items: items }
end function
