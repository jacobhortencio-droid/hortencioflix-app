' Utilitários partilhados (thread da interface): definições, add-ons, progresso,
' biblioteca Stremio e listas IPTV.

function appVersion() as string
    ' versão real do pacote instalado (manifest), em vez de um número fixo
    v = CreateObject("roAppInfo").GetVersion()
    if v = invalid or v = "" then return "?"
    p = v.Split(".")
    if p.Count() >= 3 and p[2] = "0" then return p[0] + "." + p[1]
    return v
end function

function cinemetaUrl() as string
    return "https://v3-cinemeta.strem.io"
end function

' ---------- Tipos ----------

function strOr(v as dynamic) as string
    if v = invalid then return ""
    if GetInterface(v, "ifString") <> invalid then return v
    if GetInterface(v, "ifToStr") <> invalid then return v.ToStr()
    return ""
end function

function isNum(v as dynamic) as boolean
    if v = invalid then return false
    t = type(v)
    return t = "roInt" or t = "roInteger" or t = "Integer" or t = "roFloat" or t = "Float" or t = "roDouble" or t = "Double" or t = "LongInteger" or t = "roLongInteger"
end function

function numOr(v as dynamic, def as float) as float
    if isNum(v) then return v
    if v <> invalid and GetInterface(v, "ifString") <> invalid then return Val(v)
    return def
end function

function boolOr(v as dynamic) as boolean
    if v = invalid then return false
    t = type(v)
    if t = "roBoolean" or t = "Boolean" then return v
    return false
end function

' ---------- Registo (definições persistentes) ----------

function cfgRead(key as string, def as string) as string
    sec = CreateObject("roRegistrySection", "streambox")
    if sec.Exists(key) then return sec.Read(key)
    return def
end function

sub cfgWrite(key as string, value as string)
    sec = CreateObject("roRegistrySection", "streambox")
    sec.Write(key, value)
    sec.Flush()
    ' a Roku só dá ~16 KB de registo por app: perto do limite, encurta os históricos
    if CreateObject("roRegistry").GetSpaceAvailable() < 2000 then trimRegistry()
end sub

' Corta para metade as listas menos importantes até haver folga (nunca toca em chaves, contas ou definições)
sub trimRegistry()
    sec = CreateObject("roRegistrySection", "streambox")
    reg = CreateObject("roRegistry")
    ' do menos importante para o mais importante (as listas pessoais e os lembretes são os últimos)
    order = ["searchHist", "badSrc", "homeKnown", "subDelays", "doneRecent", "iptvRecent", "trackPrefs", "watched", "progress", "iptvFav", "reminders", "lists"]
    for pass = 1 to 3
        for each base in order
            if reg.GetSpaceAvailable() >= 4000 then return
            for each k in sec.GetKeyList()
                if k = base or Left(k, Len(base) + 1) = base + "_"
                    arr = ParseJson(sec.Read(k))
                    if arr <> invalid and type(arr) = "roArray" and arr.Count() > 1
                        keep = []
                        for i = 0 to Int(arr.Count() / 2) - 1
                            keep.Push(arr[i])
                        end for
                        sec.Write(k, FormatJson(keep))
                        sec.Flush()
                    end if
                end if
            end for
        end for
    end for
end sub

sub cfgDelete(key as string)
    sec = CreateObject("roRegistrySection", "streambox")
    sec.Delete(key)
    sec.Flush()
end sub

sub cfgReset()
    sec = CreateObject("roRegistrySection", "streambox")
    for each k in sec.GetKeyList()
        sec.Delete(k)
    end for
    sec.Flush()
end sub

function cfgJsonArray(key as string) as object
    raw = cfgRead(key, "")
    if raw = "" then return []
    v = ParseJson(raw)
    if v = invalid or type(v) <> "roArray" then return []
    return v
end function

' ---------- Add-ons ----------

function getUserAddons() as object
    raw = cfgRead("addons", "")
    if raw = "" then return [cinemetaUrl()]
    arr = ParseJson(raw)
    if arr = invalid or type(arr) <> "roArray" then return [cinemetaUrl()]
    return arr
end function

sub setUserAddons(arr as object)
    cfgWrite("addons", FormatJson(arr))
end sub

function stremioAddons() as object
    if m.global.hasField("stremioAddons") and m.global.stremioAddons <> invalid then return m.global.stremioAddons
    return []
end function

function torrentioTorboxUrl(key as string) as string
    return "https://torrentio.strem.fun/torbox=" + key
end function

' Lista usada pela app: add-ons da conta Stremio + locais + Torrentio/TorBox (se preciso)
function getAddons() as object
    list = []
    seen = {}
    sources = []
    sources.Append(stremioAddons())
    sources.Append(getUserAddons())
    for each a in sources
        if not seen.DoesExist(a)
            seen[a] = true
            list.Push(a)
        end if
    end for
    key = cfgRead("torboxKey", "")
    if key <> ""
        hasTorbox = false
        for each a in list
            la = LCase(a)
            if Instr(1, la, "torrentio") > 0 and Instr(1, la, "torbox") > 0 then hasTorbox = true
        end for
        if not hasTorbox then list.Push(torrentioTorboxUrl(key))
    end if
    return list
end function

function normalizeAddonUrl(u as string) as string
    s = u.Trim()
    if LCase(Left(s, 10)) = "stremio://" then s = "https://" + Mid(s, 11)
    if LCase(Right(s, 14)) = "/manifest.json" then s = Left(s, Len(s) - 14)
    while Len(s) > 0 and Right(s, 1) = "/"
        s = Left(s, Len(s) - 1)
    end while
    return s
end function

' ---------- Preferências ----------

function subLangPref() as string
    return cfgRead("subLang", "por")
end function

function subLangCodes(pref as string) as object
    if pref = "por" then return ["por", "pob", "pt", "pt-br", "pt-pt"]
    if pref = "eng" then return ["eng", "en"]
    if pref = "spa" then return ["spa", "es", "spn"]
    if pref = "fre" then return ["fre", "fra", "fr"]
    return []
end function

' Idiomas a pedir aos add-ons de legendas (preferido + inglês)
function subLangFetchList() as object
    pref = subLangPref()
    if pref = "off" then return ["por", "pob", "eng"]
    list = subLangCodes(pref)
    if pref <> "eng"
        list.Push("eng")
    end if
    return list
end function

function subLangLabel(pref as string) as string
    if pref = "por" then return "Português"
    if pref = "eng" then return "Inglês"
    if pref = "spa" then return "Espanhol"
    if pref = "fre" then return "Francês"
    return "Desligadas"
end function

function langName(code as string) as string
    c = LCase(code)
    names = { por: "Português", pob: "Português (BR)", "pt-br": "Português (BR)", pt: "Português", eng: "Inglês", en: "Inglês", spa: "Espanhol", es: "Espanhol", fre: "Francês", fra: "Francês", fr: "Francês", ita: "Italiano", ger: "Alemão", deu: "Alemão", rus: "Russo", jpn: "Japonês", ara: "Árabe", chi: "Chinês", zho: "Chinês", und: "Desconhecido" }
    if c = "" then return "Faixa"
    if names.DoesExist(c) then return names[c]
    return code
end function

' ---------- Tarefas ----------

' Chave estável de uma fonte (link sem parâmetros), para lembrar as que fizeram a Roku fechar a app
function srcKey(url as string) as string
    u = LCase(url)
    p = Instr(1, u, "?")
    if p > 0 then u = Left(u, p - 1)
    ' guarda só um resumo curto (12 caracteres) para poupar o registo
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(u)
    d = CreateObject("roEVPDigest")
    d.Setup("md5")
    return Left(d.Process(ba), 12)
end function

' Chamado no arranque: a app fechou a meio de uma fonte da última vez?
function checkPlayGuard() as boolean
    k = cfgRead("playGuard", "")
    if k = "" then return false
    cfgDelete("playGuard")
    bad = [k]
    for each x in cfgJsonArray("badSrc")
        if bad.Count() < 25 and x <> k and Len(x) <= 12 then bad.Push(x)
    end for
    cfgWrite("badSrc", FormatJson(bad))
    return true
end function

' O que esta Roku (e a TV/barra de som ligada) consegue mesmo tocar
function deviceCaps() as object
    caps = { uhd: true, hevc: true, hevc10: true, dv: false, hdr: false, av1: false, dts: false, truehd: false, known: false }
    try
        di = CreateObject("roDeviceInfo")
        vm = LCase(di.GetVideoMode())
        caps.uhd = Instr(1, vm, "2160") > 0
        caps.hevc = di.CanDecodeVideo({ Codec: "hevc" }).result = true
        caps.hevc10 = di.CanDecodeVideo({ Codec: "hevc", Profile: "main10" }).result = true
        caps.dv = di.CanDecodeVideo({ Codec: "dvhe" }).result = true
        caps.av1 = di.CanDecodeVideo({ Codec: "av1" }).result = true
        caps.dts = di.CanDecodeAudio({ Codec: "dts" }).result = true
        caps.truehd = di.CanDecodeAudio({ Codec: "truehd" }).result = true
        dp = di.GetDisplayProperties()
        if dp <> invalid
            if dp.Hdr10 = true or dp.Hdr10Plus = true or dp.DolbyVision = true then caps.hdr = true
            if dp.DolbyVision <> true then caps.dv = false
        end if
        caps.known = true
    catch e
        reportCrash("deviceCaps", e)
    end try
    return caps
end function

' Tem conta debrid? (automático: chave TorBox definida)
function hasDebridAccount() as boolean
    pref = cfgRead("debridPref", "auto")
    if pref = "1" then return true
    if pref = "0" then return false
    return cfgRead("torboxKey", "") <> ""
end function

' Fonte alternativa com os campos usados na troca de qualidade e no failover
function altOf(x as object) as object
    cached = true
    if x.cached <> invalid then cached = x.cached
    q = 0
    if x.q <> invalid then q = Int(numOr(x.q, 0))
    rng = ""
    if x.range <> invalid then rng = x.range
    return { url: x.url, name: x.name, title: x.title, filename: x.filename, headers: x.headers, q: q, cached: cached, range: rng }
end function

' Índice da melhor fonte para uma resolução: exata → a mais próxima abaixo → a mais próxima acima
function bestIndexForQuality(list as object, target as integer, skipUrl as string) as integer
    best = -1
    bestScore = 99999999.0
    for i = 0 to list.Count() - 1
        x = list[i]
        if strOr(x.url) <> skipUrl
            q = 0
            if x.q <> invalid then q = Int(numOr(x.q, 0))
            score = 0.0
            if q = 0
                score = 3000000.0
            else if q = target
                score = 0.0
            else if q < target
                score = 1000000.0 + (target - q)
            else
                score = 2000000.0 + (q - target)
            end if
            score = score + i / 1000.0
            if score < bestScore
                bestScore = score
                best = i
            end if
        end if
    end for
    return best
end function

' Ligações de filmes/séries de listas IPTV/Xtream (e não canais)
function isVodUrl(url as string) as boolean
    u = LCase(url)
    p = Instr(1, u, "?")
    if p > 0 then u = Left(u, p - 1)
    if Instr(1, u, "/movie/") > 0 or Instr(1, u, "/series/") > 0 then return true
    rx = CreateObject("roRegex", "\.(mkv|mp4|avi|m4v|mov|wmv)$", "")
    return rx.IsMatch(u)
end function

function startAddonTask(req as object, callback as string) as object
    req.addons = getAddons()
    if m.global.hasField("manifests") then req.manifests = m.global.manifests
    req.maxQ = Int(Val(cfgRead("maxQ", "0")))
    req.cachedOnly = (cfgRead("cachedOnly", "0") = "1")
    req.langs = subLangFetchList()
    req.hideRisky = (cfgRead("hideRisky", "0") = "1")
    req.langTokens = audioLangTokens(cfgRead("audioLang", "any"))
    req.hasDebrid = hasDebridAccount()
    req.badSrc = cfgJsonArray("badSrc")
    req.dubPref = cfgRead("dubPref", "")
    ' animação: preferência própria de áudio dobrado (ex.: desenhos animados sempre dobrados)
    if boolOr(req.animated) and cfgRead("animDub", "any") <> "off" then req.dubPref = cfgRead("animDub", "any")
    req.rangePref = cfgRead("rangePref", "auto")
    req.pinFilters = pinnedFilters()
    req.srcStats = cfgRead("srcStats", "{}")
    if m.devCaps = invalid then m.devCaps = deviceCaps()
    req.caps = m.devCaps
    t = CreateObject("roSGNode", "AddonTask")
    t.request = req
    t.observeField("result", callback)
    t.control = "run"
    return t
end function

sub storeManifests(res as object)
    if res <> invalid and res.manifests <> invalid and m.global.hasField("manifests")
        m.global.manifests = res.manifests
    end if
end sub

' ---------- Texto ----------

function guessStreamFormat(url as string, hint as string) as string
    s = LCase(url + " " + hint)
    if Instr(1, s, ".m3u8") > 0 then return "hls"
    if Instr(1, s, ".mpd") > 0 then return "dash"
    if Instr(1, s, ".mp4") > 0 or Instr(1, s, ".m4v") > 0 then return "mp4"
    if Instr(1, s, ".mkv") > 0 then return "mkv"
    if Instr(1, s, ".mp3") > 0 then return "mp3"
    return ""
end function

' Remove emojis e símbolos que as fontes da Roku não mostram
function cleanText(s as dynamic) as string
    src = strOr(s)
    out = ""
    lastSpace = false
    for i = 1 to Len(src)
        c = Mid(src, i, 1)
        code = Asc(c)
        if code = 10 or code = 13
            out = out + " · "
            lastSpace = true
        else if code = 32 or (code > 8191)
            if not lastSpace then out = out + " "
            lastSpace = true
        else if code > 31
            out = out + c
            lastSpace = false
        end if
    end for
    return out.Trim()
end function

function mask(s as string) as string
    if s = "" then return "(não definida)"
    if Len(s) <= 4 then return "****"
    return "****" + Right(s, 4)
end function

function formatTime(sec as integer) as string
    h = Int(sec / 3600)
    mi = Int((sec - h * 3600) / 60)
    s = sec - h * 3600 - mi * 60
    ms = mi.ToStr()
    if mi < 10 and h > 0 then ms = "0" + ms
    ss = s.ToStr()
    if s < 10 then ss = "0" + ss
    if h > 0 then return h.ToStr() + ":" + ms + ":" + ss
    return ms + ":" + ss
end function

' ---------- Tempo ----------

function nowEpoch() as integer
    return CreateObject("roDateTime").AsSeconds()
end function

function nowIso() as string
    return CreateObject("roDateTime").ToISOString()
end function

function isoToEpoch(s as string) as integer
    if s = "" then return 0
    d = CreateObject("roDateTime")
    d.FromISO8601String(s)
    return d.AsSeconds()
end function

' ---------- Progresso local ("Continuar a ver") ----------

function getProgress() as object
    return cfgJsonArray(pkey("progress"))
end function

sub saveProgress(e as object)
    out = [e]
    for each x in getProgress()
        if strOr(x.m) <> e.m and out.Count() < 12 then out.Push(x)
    end for
    cfgWrite(pkey("progress"), FormatJson(out))
end sub

sub removeProgress(metaId as string)
    out = []
    for each x in getProgress()
        if strOr(x.m) <> metaId then out.Push(x)
    end for
    cfgWrite(pkey("progress"), FormatJson(out))
end sub

' Posição (segundos) onde retomar um vídeo, local ou da conta Stremio
function findResume(videoId as string, metaId as string) as integer
    for each x in getProgress()
        if strOr(x.v) = videoId then return Int(numOr(x.t, 0))
    end for
    lib = libFind(metaId)
    if lib <> invalid and lib.state <> invalid
        vid = strOr(lib.state.video_id)
        off = numOr(lib.state.timeOffset, 0) / 1000
        if off > 0 and (vid = videoId or vid = "" or vid = metaId) then return Int(off)
    end if
    return 0
end function

' ---------- Biblioteca Stremio ----------

function stremioLinked() as boolean
    return cfgRead("stremioKey", "") <> ""
end function

function libAll() as object
    if m.global.hasField("library") and m.global.library <> invalid then return m.global.library
    return []
end function

function libFind(id as string) as dynamic
    if id = "" then return invalid
    for each x in libAll()
        if strOr(x["_id"]) = id then return x
    end for
    return invalid
end function

function libInLibrary(id as string) as boolean
    x = libFind(id)
    if x = invalid then return false
    return not boolOr(x.removed) and not boolOr(x.temp)
end function

sub libApplyLocal(item as dynamic)
    if item = invalid then return
    arr = libAll()
    id = strOr(item["_id"])
    found = false
    for i = 0 to arr.Count() - 1
        if strOr(arr[i]["_id"]) = id
            arr[i] = item
            found = true
            exit for
        end if
    end for
    if not found then arr.Push(item)
    m.global.library = arr
end sub

function libraryRowItems() as object
    tmp = []
    for each x in libAll()
        t = strOr(x.type)
        if t <> "other" and t <> "" and not boolOr(x.removed) and not boolOr(x.temp)
            tmp.Push({ id: strOr(x["_id"]), type: t, name: strOr(x.name), poster: strOr(x.poster), videoId: "", progress: 0.0, sk: -isoToEpoch(strOr(x["_mtime"])) })
        end if
    end for
    tmp.SortBy("sk")
    out = []
    for each x in tmp
        if out.Count() < 40 then out.Push(x)
    end for
    return out
end function

function continueRowItems() as object
    tmp = []
    for each x in getProgress()
        d = numOr(x.d, 0)
        p = 0.0
        if d > 0 then p = numOr(x.t, 0) / d
        tmp.Push({ id: strOr(x.m), type: strOr(x.y), name: strOr(x.n), poster: strOr(x.p), videoId: strOr(x.v), progress: p, sk: -numOr(x.a, 0) })
    end for
    ' o progresso da conta Stremio só aparece no perfil principal
    lib = []
    if currentProfileId() = "main" then lib = libAll()
    for each x in lib
        t = strOr(x.type)
        st = x.state
        if t <> "other" and t <> "" and st <> invalid and (not boolOr(x.removed) or boolOr(x.temp))
            off = numOr(st.timeOffset, 0)
            if off > 0
                dur = numOr(st.duration, 0)
                p = 0.0
                if dur > 0 then p = off / dur
                at = isoToEpoch(strOr(st.lastWatched))
                if at = 0 then at = isoToEpoch(strOr(x["_mtime"]))
                tmp.Push({ id: strOr(x["_id"]), type: t, name: strOr(x.name), poster: strOr(x.poster), videoId: strOr(st.video_id), progress: p, sk: -at })
            end if
        end if
    end for
    tmp.SortBy("sk")
    out = []
    seen = {}
    for each x in tmp
        if x.id <> "" and not seen.DoesExist(x.id) and out.Count() < 20
            seen[x.id] = true
            out.Push(x)
        end if
    end for
    return out
end function

' Valor numérico em JSON sem perder inteiros grandes
function jsonNum(v as dynamic) as string
    if v <> invalid
        t = type(v)
        if t = "roInt" or t = "roInteger" or t = "Integer" or t = "LongInteger" or t = "roLongInteger"
            s = FormatJson([v])
            return Mid(s, 2, Len(s) - 2)
        end if
    end if
    return Int(numOr(v, 0)).ToStr()
end function

function jsonStr(v as dynamic) as string
    if v = invalid then return "null"
    s = FormatJson([strOr(v)])
    return Mid(s, 2, Len(s) - 2)
end function

function jsonStrOrNull(v as dynamic) as string
    if strOr(v) = "" then return "null"
    return jsonStr(v)
end function

function jsonBool(b as boolean) as string
    if b then return "true"
    return "false"
end function

' Constrói o JSON de um item da biblioteca (chaves com a capitalização exata do Stremio)
' change: "progress" | "finished" | "add" | "remove"
function libraryItemJson(id as string, mtype as string, name as string, poster as string, change as string, videoId as string, posSec as integer, durSec as integer) as string
    ex = libFind(id)
    now = nowIso()
    st = {}
    hints = {}
    removed = true
    temp = true
    ctime = now
    shape = "poster"
    if ex <> invalid
        if ex.state <> invalid then st = ex.state
        if ex.behaviorHints <> invalid and type(ex.behaviorHints) = "roAssociativeArray" then hints = ex.behaviorHints
        removed = boolOr(ex.removed)
        temp = boolOr(ex.temp)
        if strOr(ex["_ctime"]) <> "" then ctime = strOr(ex["_ctime"])
        if strOr(ex.posterShape) <> "" then shape = strOr(ex.posterShape)
        if strOr(ex.name) <> "" then name = strOr(ex.name)
        if strOr(ex.poster) <> "" then poster = strOr(ex.poster)
    end if

    lastWatched = st.lastWatched
    timeWatched = jsonNum(st.timeWatched)
    timeOffset = jsonNum(st.timeOffset)
    overall = jsonNum(st.overallTimeWatched)
    timesWatched = jsonNum(st.timesWatched)
    flagged = jsonNum(st.flaggedWatched)
    duration = jsonNum(st.duration)
    vid = st.video_id

    if change = "progress" or change = "finished"
        lastWatched = now
        vid = videoId
        duration = (durSec * 1000).ToStr()
        timeOffset = (posSec * 1000).ToStr()
        if change = "finished"
            timeOffset = "0"
            flagged = "1"
            timesWatched = (Int(numOr(st.timesWatched, 0)) + 1).ToStr()
        end if
    else if change = "add"
        removed = false
        temp = false
    else if change = "remove"
        removed = true
        temp = false
    end if

    q = Chr(34)
    s = "{"
    s = s + q + "_id" + q + ":" + jsonStr(id) + ","
    s = s + q + "name" + q + ":" + jsonStr(name) + ","
    s = s + q + "type" + q + ":" + jsonStr(mtype) + ","
    s = s + q + "poster" + q + ":" + jsonStrOrNull(poster) + ","
    s = s + q + "posterShape" + q + ":" + jsonStr(shape) + ","
    s = s + q + "removed" + q + ":" + jsonBool(removed) + ","
    s = s + q + "temp" + q + ":" + jsonBool(temp) + ","
    s = s + q + "_ctime" + q + ":" + jsonStr(ctime) + ","
    s = s + q + "_mtime" + q + ":" + jsonStr(now) + ","
    s = s + q + "state" + q + ":{"
    s = s + q + "lastWatched" + q + ":" + jsonStrOrNull(lastWatched) + ","
    s = s + q + "timeWatched" + q + ":" + timeWatched + ","
    s = s + q + "timeOffset" + q + ":" + timeOffset + ","
    s = s + q + "overallTimeWatched" + q + ":" + overall + ","
    s = s + q + "timesWatched" + q + ":" + timesWatched + ","
    s = s + q + "flaggedWatched" + q + ":" + flagged + ","
    s = s + q + "duration" + q + ":" + duration + ","
    s = s + q + "video_id" + q + ":" + jsonStrOrNull(vid) + ","
    s = s + q + "watched" + q + ":" + jsonStrOrNull(st.watched) + ","
    s = s + q + "noNotif" + q + ":" + jsonBool(boolOr(st.noNotif))
    s = s + "},"
    s = s + q + "behaviorHints" + q + ":{"
    s = s + q + "defaultVideoId" + q + ":" + jsonStrOrNull(hints.defaultVideoId) + ","
    s = s + q + "featuredVideoId" + q + ":" + jsonStrOrNull(hints.featuredVideoId) + ","
    s = s + q + "hasScheduledVideos" + q + ":" + jsonBool(boolOr(hints.hasScheduledVideos))
    s = s + "}}"
    return s
end function

' ---------- IPTV ----------

function publicIptvSources() as object
    base = "https://iptv-org.github.io/iptv/"
    return [
        { n: "iptv-org · Angola", u: base + "countries/ao.m3u" },
        { n: "iptv-org · Portugal", u: base + "countries/pt.m3u" },
        { n: "iptv-org · Brasil", u: base + "countries/br.m3u" },
        { n: "iptv-org · Moçambique", u: base + "countries/mz.m3u" },
        { n: "iptv-org · Cabo Verde", u: base + "countries/cv.m3u" },
        { n: "iptv-org · Canais em português", u: base + "languages/por.m3u" },
        { n: "iptv-org · Notícias (mundo)", u: base + "categories/news.m3u" },
        { n: "iptv-org · Desporto (mundo)", u: base + "categories/sports.m3u" },
        { n: "iptv-org · Infantil", u: base + "categories/kids.m3u" },
        { n: "iptv-org · Animação", u: base + "categories/animation.m3u" },
        { n: "iptv-org · Filmes", u: base + "categories/movies.m3u" },
        { n: "iptv-org · Música", u: base + "categories/music.m3u" },
        { n: "iptv-org · Documentários", u: base + "categories/documentary.m3u" },
        { n: "iptv-org · Mundo inteiro (lista grande)", u: base + "index.category.m3u" },
        { n: "Free-TV · Canais gratuitos em HD", u: "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8" }
    ]
end function

function getIptvLists() as object
    lists = cfgJsonArray("iptvLists")
    ' migração da v0.1 (uma só lista)
    old = cfgRead("m3uUrl", "")
    if old <> ""
        lists.Unshift({ n: "A minha lista", u: old })
        cfgWrite("iptvLists", FormatJson(lists))
        cfgDelete("m3uUrl")
    end if
    return lists
end function

sub setIptvLists(lists as object)
    cfgWrite("iptvLists", FormatJson(lists))
end sub

function getFavs() as object
    return cfgJsonArray(pkey("iptvFav"))
end function

' Devolve true se ficou favorito
function toggleFav(ch as object) as boolean
    favs = getFavs()
    out = []
    removed = false
    for each f in favs
        if strOr(f.u) = ch.url
            removed = true
        else
            out.Push(f)
        end if
    end for
    if not removed
        logo = strOr(ch.logo)
        if Len(logo) > 110 then logo = ""
        out.Unshift({ n: ch.name, u: ch.url, l: logo })
        while out.Count() > 20
            out.Pop()
        end while
    end if
    cfgWrite(pkey("iptvFav"), FormatJson(out))
    return not removed
end function

' Add-ons Stremio de TV verificados (canais gratuitos). Qualquer outro add-on de TV
' que esteja na conta Stremio ou nos add-ons locais aparece automaticamente.
function tvAddonDirectory() as object
    return [
        { n: "Watchio.live TV — canais gratuitos de todo o mundo", u: "https://watchio-addon.pages.dev" },
        { n: "Stremio IPTV (dexter21767) — canais por país (base iptv-org)", u: "https://ryann-devv.github.io/Stremio-IPTV" }
    ]
end function

function getRecents() as object
    return cfgJsonArray(pkey("iptvRecent"))
end function

sub addRecent(ch as object)
    out = []
    logo = strOr(ch.logo)
    if Len(logo) > 110 then logo = ""
    out.Push({ n: strOr(ch.name), u: strOr(ch.url), l: logo })
    for each r in getRecents()
        if strOr(r.u) <> strOr(ch.url) and out.Count() < 8 then out.Push(r)
    end for
    cfgWrite(pkey("iptvRecent"), FormatJson(out))
end sub

' "stremio:tipo:id" -> { type, id } (canal de um add-on, resolvido no leitor)
function parseStremioUrl(u as string) as dynamic
    if LCase(Left(u, 8)) <> "stremio:" then return invalid
    rest = Mid(u, 9)
    p = Instr(1, rest, ":")
    if p < 2 then return invalid
    return { type: Left(rest, p - 1), id: Mid(rest, p + 1) }
end function


' ---------- Tema de cor ----------

function themeList() as object
    return [
        { k: "red", n: "Vermelho", c: "0xE50914FF" },
        { k: "blue", n: "Azul", c: "0x1E88E5FF" },
        { k: "gold", n: "Dourado", c: "0xE0A526FF" },
        { k: "green", n: "Verde", c: "0x22A06BFF" },
        { k: "purple", n: "Roxo", c: "0x9B59B6FF" }
    ]
end function

function accentColor() as string
    if m.global.hasField("accent") and strOr(m.global.accent) <> "" then return m.global.accent
    k = cfgRead("theme", "red")
    for each t in themeList()
        if t.k = k then return t.c
    end for
    return "0xE50914FF"
end function

function themeName() as string
    k = cfgRead("theme", "red")
    for each t in themeList()
        if t.k = k then return t.n
    end for
    return "Vermelho"
end function

' ---------- Idioma preferido nas fontes ----------

function audioLangTokens(pref as string) as object
    if pref = "pt" then return ["🇵🇹", "🇧🇷", "pt-br", "ptbr", "pt-pt", "dublado", "dual audio", "dual-audio", "portugu", ".por.", " por ", "[por]"]
    if pref = "es" then return ["🇪🇸", "🇲🇽", "latino", "castellano", "spanish", "español", ".spa.", " spa "]
    if pref = "fr" then return ["🇫🇷", "french", "truefrench", " vff", ".vff.", "français", ".fre."]
    if pref = "it" then return ["🇮🇹", "italian", ".ita.", " ita "]
    if pref = "en" then return ["🇬🇧", "🇺🇸", "english", ".eng."]
    return []
end function

function audioLangLabel(pref as string) as string
    if pref = "pt" then return "Português (dobrado/dual)"
    if pref = "es" then return "Espanhol"
    if pref = "fr" then return "Francês"
    if pref = "it" then return "Italiano"
    if pref = "en" then return "Inglês"
    return "Qualquer"
end function

' ---------- Faixas preferidas por série (áudio/legendas) ----------

function getTrackPref(metaId as string) as dynamic
    for each x in cfgJsonArray(pkey("trackPrefs"))
        if strOr(x.m) = metaId then return x
    end for
    return invalid
end function

sub setTrackPref(metaId as string, field as string, value as string)
    if metaId = "" then return
    cur = getTrackPref(metaId)
    e = { m: metaId, a: "", s: "" }
    if cur <> invalid
        e.a = strOr(cur.a)
        e.s = strOr(cur.s)
    end if
    e[field] = value
    out = [e]
    for each x in cfgJsonArray(pkey("trackPrefs"))
        if strOr(x.m) <> metaId and out.Count() < 30 then out.Push(x)
    end for
    cfgWrite(pkey("trackPrefs"), FormatJson(out))
end sub

' ---------- Imagens (serviço de imagens do Stremio) ----------

function metahubBg(id as string) as string
    if Left(id, 2) <> "tt" then return ""
    p = Instr(1, id, ":")
    if p > 0 then id = Left(id, p - 1)
    return "https://images.metahub.space/background/medium/" + id + "/img"
end function

function metahubLogo(id as string) as string
    if Left(id, 2) <> "tt" then return ""
    p = Instr(1, id, ":")
    if p > 0 then id = Left(id, p - 1)
    return "https://images.metahub.space/logo/medium/" + id + "/img"
end function

function torboxFileUrl(key as string, torrentId as string, fileId as string) as string
    return "https://api.torbox.app/v1/api/torrents/requestdl?token=" + key + "&torrent_id=" + torrentId + "&file_id=" + fileId + "&redirect=true"
end function

' ---------- Animações simples (fade/deslizar) ----------

' Cria uma animação de opacidade (e opcionalmente translação) para um nó com id
function makeFade(parent as object, target as object, fromOp as float, toOp as float, secs as float, slideFrom as dynamic) as object
    if target.id = "" then target.id = "anim" + Rnd(999999).ToStr()
    anim = parent.createChild("Animation")
    anim.duration = secs
    anim.easeFunction = "outCubic"
    fi = anim.createChild("FloatFieldInterpolator")
    fi.key = [0.0, 1.0]
    fi.keyValue = [fromOp, toOp]
    fi.fieldToInterp = target.id + ".opacity"
    if slideFrom <> invalid
        vi = anim.createChild("Vector2DFieldInterpolator")
        vi.key = [0.0, 1.0]
        vi.keyValue = [slideFrom, target.translation]
        vi.fieldToInterp = target.id + ".translation"
    end if
    target.opacity = fromOp
    anim.control = "start"
    return anim
end function


' ---------- Perfis ----------

function getProfiles() as object
    arr = cfgJsonArray("profiles")
    if arr.Count() = 0 then arr = [{ id: "main", n: "Principal", kid: false }]
    return arr
end function

function currentProfileId() as string
    return cfgRead("profile", "main")
end function

function currentProfile() as object
    pid = currentProfileId()
    for each p in getProfiles()
        if strOr(p.id) = pid then return p
    end for
    return { id: "main", n: "Principal", kid: false }
end function

function isKidProfile() as boolean
    return boolOr(currentProfile().kid)
end function

' Chave do registo própria do perfil atual (o perfil principal usa as chaves antigas)
function pkey(k as string) as string
    pid = currentProfileId()
    if pid = "main" then return k
    return k + "_" + pid
end function

' ---------- Controlo parental ----------

function parentalPin() as string
    return cfgRead("pin", "")
end function

function isAdultText(s as string) as boolean
    t = " " + LCase(s) + " "
    for each w in ["xxx", "adult", "adulto", "18+", "+18", "porn", "erotic", "erótic", "playboy", "hustler", "brazzers", "for adults", "xxl hot", " hot ", "sexy", "privat"]
        if Instr(1, t, w) > 0 then return true
    end for
    return false
end function

function adultLocked() as boolean
    if isKidProfile() then return true
    if parentalPin() = "" or cfgRead("adultLock", "0") <> "1" then return false
    if m.global.hasField("pinOk") and m.global.pinOk = true then return false
    return true
end function

' ---------- Episódios vistos (por perfil, formato compacto) ----------
' guardado como [{m: "tt123", e: "1:1-8,2:1-3"}]

function watchedFor(metaId as string) as object
    out = {}
    for each x in cfgJsonArray(pkey("watched"))
        if strOr(x.m) = metaId
            for each part in strOr(x.e).Split(",")
                c = Instr(1, part, ":")
                if c > 1
                    sn = Left(part, c - 1)
                    rng = Mid(part, c + 1)
                    d = Instr(1, rng, "-")
                    a = 0
                    b = 0
                    if d > 0
                        a = Int(Val(Left(rng, d - 1)))
                        b = Int(Val(Mid(rng, d + 1)))
                    else
                        a = Int(Val(rng))
                        b = a
                    end if
                    for n = a to b
                        out[sn + ":" + n.ToStr()] = true
                    end for
                end if
            end for
        end if
    end for
    return out
end function

function encodeWatched(set as object) as string
    bySeason = {}
    for each k in set
        c = Instr(1, k, ":")
        sn = Left(k, c - 1)
        if not bySeason.DoesExist(sn) then bySeason[sn] = []
        bySeason[sn].Push({ n: Int(Val(Mid(k, c + 1))) })
    end for
    parts = []
    for each sn in bySeason
        arr = bySeason[sn]
        arr.SortBy("n")
        i = 0
        while i < arr.Count()
            a = arr[i].n
            b = a
            while i + 1 < arr.Count() and arr[i + 1].n = b + 1
                i = i + 1
                b = arr[i].n
            end while
            if a = b
                parts.Push(sn + ":" + a.ToStr())
            else
                parts.Push(sn + ":" + a.ToStr() + "-" + b.ToStr())
            end if
            i = i + 1
        end while
    end for
    return parts.Join(",")
end function

sub setWatched(metaId as string, season as integer, episode as integer, value as boolean)
    if metaId = "" then return
    set = watchedFor(metaId)
    key = season.ToStr() + ":" + episode.ToStr()
    if value
        set[key] = true
    else if set.DoesExist(key)
        set.Delete(key)
    end if
    out = []
    enc = encodeWatched(set)
    if enc <> "" then out.Push({ m: metaId, e: enc })
    for each x in cfgJsonArray(pkey("watched"))
        if strOr(x.m) <> metaId and out.Count() < 25 then out.Push(x)
    end for
    cfgWrite(pkey("watched"), FormatJson(out))
end sub

' Marca como visto a partir de um id de episódio "tt123:2:5"
sub markEpisodeWatched(videoId as string)
    parts = videoId.Split(":")
    if parts.Count() >= 3 then setWatched(parts[0], Int(Val(parts[1])), Int(Val(parts[2])), true)
end sub

' ---------- Histórico de pesquisa ----------

function getSearchHistory() as object
    return cfgJsonArray(pkey("searchHist"))
end function

sub addSearchHistory(q as string)
    out = [q]
    for each x in getSearchHistory()
        if LCase(strOr(x)) <> LCase(q) and out.Count() < 10 then out.Push(x)
    end for
    cfgWrite(pkey("searchHist"), FormatJson(out))
end sub

' ---------- Diagnóstico ----------

sub logEvent(msg as string)
    if not m.global.hasField("logs") then m.global.addField("logs", "array", false)
    arr = m.global.logs
    if arr = invalid then arr = []
    d = CreateObject("roDateTime")
    d.ToLocalTime()
    t = two2(d.GetHours()) + ":" + two2(d.GetMinutes()) + ":" + two2(d.GetSeconds())
    arr.Unshift(t + "  " + msg)
    while arr.Count() > 40
        arr.Pop()
    end while
    m.global.logs = arr
end sub

function two2(n as integer) as string
    if n < 10 then return "0" + n.ToStr()
    return n.ToStr()
end function

function registryUsage() as integer
    sec = CreateObject("roRegistrySection", "streambox")
    total = 0
    for each k in sec.GetKeyList()
        total = total + Len(k) + Len(sec.Read(k))
    end for
    return total
end function

' ---------- Cópia de segurança ----------

function exportSettings() as string
    sec = CreateObject("roRegistrySection", "streambox")
    pairs = []
    for each k in sec.GetKeyList()
        pairs.Push({ k: k, v: sec.Read(k) })
    end for
    return FormatJson({ app: "hortencioflix", version: appVersion(), pairs: pairs })
end function

function importSettings(json as string) as boolean
    d = ParseJson(json)
    if d = invalid or d.pairs = invalid or type(d.pairs) <> "roArray" then return false
    sec = CreateObject("roRegistrySection", "streambox")
    for each p in d.pairs
        if isStrVal(p.k) and p.v <> invalid then sec.Write(p.k, strOr(p.v))
    end for
    sec.Flush()
    return true
end function

function isStrVal(v as dynamic) as boolean
    return v <> invalid and GetInterface(v, "ifString") <> invalid and v <> ""
end function

' Reordena um favorito: "favUp" | "favDown" | "favTop"
sub moveFav(url as string, how as string)
    favs = getFavs()
    idx = -1
    for i = 0 to favs.Count() - 1
        if strOr(favs[i].u) = url then idx = i
    end for
    if idx < 0 then return
    item = favs[idx]
    favs.Delete(idx)
    if how = "favTop"
        favs.Unshift(item)
    else
        ni = idx
        if how = "favUp" and idx > 0 then ni = idx - 1
        if how = "favDown" and idx < favs.Count() then ni = idx + 1
        out = []
        for i = 0 to favs.Count()
            if i = ni then out.Push(item)
            if i < favs.Count() then out.Push(favs[i])
        end for
        favs = out
    end if
    cfgWrite(pkey("iptvFav"), FormatJson(favs))
end sub


' Codifica texto para URL sem roUrlTransfer (que não pode ser usado no ecrã/render thread)
function urlEnc(s as string) as string
    ba = CreateObject("roByteArray")
    ba.FromAsciiString(s)
    hex = "0123456789ABCDEF"
    out = ""
    for i = 0 to ba.Count() - 1
        b = ba[i]
        if (b >= 48 and b <= 57) or (b >= 65 and b <= 90) or (b >= 97 and b <= 122) or b = 45 or b = 46 or b = 95 or b = 126
            out = out + Chr(b)
        else
            out = out + "%" + Mid(hex, Int(b / 16) + 1, 1) + Mid(hex, (b mod 16) + 1, 1)
        end if
    end for
    return out
end function

' Títulos acabados de ver (para a sincronização não os voltar a pôr em «Continuar a ver»)
sub addDone(metaId as string)
    if metaId = "" then return
    out = [metaId]
    for each x in cfgJsonArray(pkey("doneRecent"))
        if x <> metaId and out.Count() < 15 then out.Push(x)
    end for
    cfgWrite(pkey("doneRecent"), FormatJson(out))
end sub

' Endereço IP da Roku na rede de casa
function localIp() as string
    addrs = CreateObject("roDeviceInfo").GetIPAddrs()
    for each k in addrs
        return addrs[k]
    end for
    return ""
end function

' ---------- Aspeto «Vidro» (liquid glass) ----------

function glassMode() as boolean
    return cfgRead("uiStyle", "dark") = "glass"
end function

' Torna translúcidos os painéis escuros de um ecrã (o fundo colorido da cena aparece por trás)
sub glassify(node as object)
    if node = invalid then return
    if node.subtype() = "Rectangle"
        c = LCase(strOr(node.color))
        if Len(c) = 10 and Left(c, 2) = "0x"
            r = Val(Mid(c, 3, 2), 16)
            g = Val(Mid(c, 5, 2), 16)
            b = Val(Mid(c, 7, 2), 16)
            a = Val(Mid(c, 9, 2), 16)
            if r < 64 and g < 64 and b < 64 and a >= 128
                if node.width >= 1800 and node.height >= 1000
                    node.color = "0x00000000"
                else
                    node.color = "0xFFFFFF1F"
                end if
            end if
        end if
    end if
    for i = 0 to node.getChildCount() - 1
        glassify(node.getChild(i))
    end for
end sub

' Cor de destaque escolhida em todos os elementos que ainda tinham o vermelho fixo
sub applyAccent(node as object, accent as string)
    if node = invalid or accent = "" or LCase(accent) = "0xe50914ff" then return
    st = node.subtype()
    if st = "Rectangle" or st = "Label" or st = "SimpleLabel"
        if LCase(strOr(node.color)) = "0xe50914ff" then node.color = accent
    end if
    for i = 0 to node.getChildCount() - 1
        applyAccent(node.getChild(i), accent)
    end for
end sub

' ---------- Aprender que fontes funcionam nesta TV ----------

' Assinatura curta de um tipo de fonte (nome do add-on + qualidade + grupo de lançamento)
function srcSig(name as string, q as integer, filename as string) as string
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
    return Left(d.Process(ba), 10)
end function

sub srcScore(name as string, q as integer, filename as string, delta as integer)
    st = ParseJson(cfgRead("srcStats", "{}"))
    if st = invalid or type(st) <> "roAssociativeArray" then st = {}
    st.SetModeCaseSensitive()
    k = srcSig(name, q, filename)
    v = 0
    if st.DoesExist(k) then v = Int(numOr(st[k], 0))
    v = v + delta
    if v > 10 then v = 10
    if v < -10 then v = -10
    st[k] = v
    if st.Count() > 40
        ' descarta as neutras para não crescer
        drop = []
        for each kk in st
            if Abs(Int(numOr(st[kk], 0))) <= 2 then drop.Push(kk)
        end for
        for each kk in drop
            if st.Count() > 35 then st.Delete(kk)
        end for
        ' nunca mais de 60 entradas (o registo da Roku é pequeno): tira as mais fracas
        while st.Count() > 60
            weakest = ""
            wv = 99
            for each kk in st
                av = Abs(Int(numOr(st[kk], 0)))
                if av < wv
                    wv = av
                    weakest = kk
                end if
            end for
            if weakest = "" then exit while
            st.Delete(weakest)
        end while
    end if
    cfgWrite("srcStats", FormatJson(st))
end sub

' ---------- TorBox: descarregar e avisar ----------

function torrentHash(url as string) as string
    rx = CreateObject("roRegex", "(^|[^0-9a-fA-F])([0-9a-fA-F]{40})([^0-9a-fA-F]|$)", "")
    mm = rx.Match(url)
    if mm.Count() > 2 then return LCase(mm[2])
    return ""
end function

sub tbWatch(hash as string, title as string)
    out = [{ h: hash, t: title, a: CreateObject("roDateTime").AsSeconds() }]
    for each x in cfgJsonArray("tbWatch")
        if strOr(x.h) <> hash and out.Count() < 8 then out.Push(x)
    end for
    cfgWrite("tbWatch", FormatJson(out))
end sub

' Filtros das fontes fixados para todos os títulos (ex.: {img: "dv", q: "4K"})
function pinnedFilters() as object
    raw = cfgRead("pinFilters", "")
    if raw = "" then return {}
    j = ParseJson(raw)
    if j = invalid or type(j) <> "roAssociativeArray" then return {}
    return j
end function

' ---------- Início personalizável (por perfil): filas escondidas e ordem ----------

function homeHidden() as object
    out = {}
    for each t in cfgJsonArray(pkey("homeHidden"))
        out[strOr(t)] = true
    end for
    return out
end function

function homeKnown() as object
    return cfgJsonArray(pkey("homeKnown"))
end function

' Guarda os títulos das filas (para as poder escolher nas Definições), esconde e ordena
function applyHomeLayout(rows as object) as object
    known = homeKnown()
    seen = {}
    for each t in known
        seen[strOr(t)] = true
    end for
    changed = false
    for each r in rows
        t = strOr(r.title)
        if t <> "" and not seen.DoesExist(t) and known.Count() < 40
            known.Push(t)
            seen[t] = true
            changed = true
        end if
    end for
    if changed then cfgWrite(pkey("homeKnown"), FormatJson(known))
    hidden = homeHidden()
    order = cfgJsonArray(pkey("homeOrder"))
    first = []
    rest = []
    for each o in order
        for each r in rows
            if strOr(r.title) = strOr(o) and not hidden.DoesExist(strOr(r.title)) then first.Push(r)
        end for
    end for
    inOrder = {}
    for each o in order
        inOrder[strOr(o)] = true
    end for
    for each r in rows
        t = strOr(r.title)
        if not hidden.DoesExist(t) and not inOrder.DoesExist(t) then rest.Push(r)
    end for
    first.Append(rest)
    return first
end function

sub toggleHomeRow(title as string)
    out = []
    found = false
    for each t in cfgJsonArray(pkey("homeHidden"))
        if strOr(t) = title then found = true else out.Push(strOr(t))
    end for
    if not found then out.Push(title)
    cfgWrite(pkey("homeHidden"), FormatJson(out))
end sub

sub moveHomeRowTop(title as string)
    out = [title]
    for each t in cfgJsonArray(pkey("homeOrder"))
        if strOr(t) <> title and out.Count() < 40 then out.Push(strOr(t))
    end for
    cfgWrite(pkey("homeOrder"), FormatJson(out))
end sub

sub resetHomeLayout()
    cfgDelete(pkey("homeHidden"))
    cfgDelete(pkey("homeOrder"))
end sub

' Último título acabado de ver (para «Porque viste…»)
sub setLastSeen(id as string, t as string, name as string)
    if id = "" or name = "" then return
    cfgWrite(pkey("lastSeen"), FormatJson({ id: id, type: t, name: name }))
end sub

' ---------- Sinopses em português (cache no disco temporário da app) ----------

function ptTranslationsOn() as boolean
    if cfgRead("ptDesc", "1") <> "1" then return false
    return cfgRead("tmdbKey", "") <> "" or cfgRead("traktId", "") <> ""
end function

function ptCache() as object
    if m.ptCache = invalid
        m.ptCache = {}
        raw = ReadAsciiFile("cachefs:/pt_desc.json")
        if raw <> ""
            j = ParseJson(raw)
            if j <> invalid and type(j) = "roAssociativeArray" then m.ptCache = j
        end if
    end if
    return m.ptCache
end function

' { name, desc } já traduzido ou invalid. Um registo vazio quer dizer «não há tradução» (não volta a pedir).
function ptCacheGet(id as string) as dynamic
    return ptCache()[id.Split(":")[0]]
end function

sub ptCacheSet(id as string, v as object)
    c = ptCache()
    if c.Count() > 400 then c.Clear()
    c[id.Split(":")[0]] = v
    WriteAsciiFile("cachefs:/pt_desc.json", FormatJson(c))
end sub

' «Termina às 23:40»: a hora a que acaba, contando com o que falta ver
function endsAtText(runtimeMin as integer, doneFrac as float) as string
    if runtimeMin <= 0 then return ""
    left = runtimeMin * (1 - doneFrac)
    dt = CreateObject("roDateTime")
    dt.ToLocalTime()
    e = CreateObject("roDateTime")
    e.FromSeconds(dt.AsSeconds() + Int(left * 60))
    mi = e.GetMinutes()
    ms = mi.ToStr()
    if mi < 10 then ms = "0" + ms
    return "Termina às " + e.GetHours().ToStr() + ":" + ms
end function

' «152 min», «2h 32min», «2 h» → minutos
function runtimeMinutes(s as string) as integer
    rxH = CreateObject("roRegex", "(\d+)\s*h(?:\s*(\d+))?", "i")
    mh = rxH.Match(s)
    if mh.Count() > 1
        total = Int(Val(mh[1])) * 60
        if mh.Count() > 2 and mh[2] <> "" then total = total + Int(Val(mh[2]))
        return total
    end if
    rxM = CreateObject("roRegex", "(\d+)\s*min", "i")
    mm = rxM.Match(s)
    if mm.Count() > 1 then return Int(Val(mm[1]))
    return 0
end function

function ptSeasonTask(id as string, season as integer, callback as string) as object
    return startAddonTask({ kind: "translateSeason", id: id, season: season.ToStr(), tmdbKey: cfgRead("tmdbKey", ""), traktId: cfgRead("traktId", "") }, callback)
end function

function ptTranslateTask(id as string, t as string, callback as string) as object
    return startAddonTask({ kind: "translate", id: id, type: t, tmdbKey: cfgRead("tmdbKey", ""), traktId: cfgRead("traktId", "") }, callback)
end function

' Nomes das filas dos add-ons em português (só as palavras mais comuns; o resto fica como está)
function ptRowTitle(t as string) as string
    if t = "" then return t
    pairs = [["Top Rated", "Mais bem avaliados"], ["Now Playing", "Nos cinemas"], ["Coming Soon", "Brevemente"], ["Upcoming", "Brevemente"], ["Trending", "Em alta"], ["Popular", "Populares"], ["Featured", "Em destaque"], ["Latest", "Novidades"], ["New Releases", "Estreias"], ["Recently Added", "Adicionados recentemente"], ["TV Shows", "Séries"], ["Shows", "Séries"], ["Series", "Séries"], ["Movies", "Filmes"], ["Movie", "Filme"], ["Anime", "Anime"], ["Documentaries", "Documentários"], ["Kids", "Infantil"], ["Watchlist", "Para ver"], ["Recommended", "Recomendados"], ["Top", "Top"]]
    out = t
    for each p in pairs
        rx = CreateObject("roRegex", "\b" + p[0] + "\b", "i")
        out = rx.ReplaceAll(out, p[1])
    end for
    return out
end function

' ---------- Listas pessoais (por perfil) ----------
' [{ n: "Fim de semana", i: [{ id, t, n }] }]  — compacto: a Roku tem pouco espaço de registo

function listsGet() as object
    return cfgJsonArray(pkey("lists"))
end function

function listNames() as object
    out = []
    for each l in listsGet()
        out.Push(strOr(l.n))
    end for
    return out
end function

function listsContaining(id as string) as object
    out = []
    for each l in listsGet()
        if l.i <> invalid
            for each x in l.i
                if strOr(x.id) = id then out.Push(strOr(l.n))
            end for
        end if
    end for
    return out
end function

sub listAdd(name as string, it as object)
    ls = listsGet()
    total = 0
    for each l in ls
        if l.i <> invalid then total = total + l.i.Count()
    end for
    found = false
    for each l in ls
        if strOr(l.n) = name
            found = true
            exists = false
            for each x in l.i
                if strOr(x.id) = it.id then exists = true
            end for
            if not exists and total < 80 then l.i.Unshift({ id: it.id, t: it.type, n: Left(strOr(it.name), 40) })
        end if
    end for
    if not found and ls.Count() < 8 then ls.Push({ n: name, i: [{ id: it.id, t: it.type, n: Left(strOr(it.name), 40) }] })
    cfgWrite(pkey("lists"), FormatJson(ls))
end sub

sub listRemove(name as string, id as string)
    ls = listsGet()
    out = []
    for each l in ls
        if strOr(l.n) = name
            keep = []
            for each x in l.i
                if strOr(x.id) <> id then keep.Push(x)
            end for
            l.i = keep
        end if
        ' listas vazias desaparecem
        if l.i <> invalid and l.i.Count() > 0 then out.Push(l)
    end for
    cfgWrite(pkey("lists"), FormatJson(out))
end sub

' Filas do Início: uma por lista
function listRows() as object
    rows = []
    for each l in listsGet()
        items = []
        for each x in l.i
            id = strOr(x.id)
            p = ""
            if Left(id, 2) = "tt" then p = "https://images.metahub.space/poster/medium/" + id + "/img"
            items.Push({ id: id, type: strOr(x.t), name: strOr(x.n), poster: p, videoId: "", progress: 0.0 })
        end for
        if items.Count() > 0 then rows.Push({ title: "Lista: " + strOr(l.n), items: items })
    end for
    return rows
end function

' ---------- Lembretes de programas (TV ao vivo) ----------

function getReminders() as object
    return cfgJsonArray("reminders")
end function

sub addReminder(r as object)
    out = []
    now = nowEpoch()
    for each x in getReminders()
        if numOr(x.s, 0) > now - 600 and not (strOr(x.u) = r.u and numOr(x.s, 0) = r.s) and out.Count() < 15 then out.Push(x)
    end for
    out.Push(r)
    cfgWrite("reminders", FormatJson(out))
end sub

' Lembretes que começam no próximo minuto (e saem da lista)
function dueReminders() as object
    now = nowEpoch()
    due = []
    keep = []
    for each x in getReminders()
        s = numOr(x.s, 0)
        if s - now <= 60 and s > now - 600
            due.Push(x)
        else if s > now
            keep.Push(x)
        end if
    end for
    if due.Count() > 0 or keep.Count() <> getReminders().Count() then cfgWrite("reminders", FormatJson(keep))
    return due
end function

' O título é animação (desenhos animados / anime)?
function isAnimatedMeta(meta as dynamic) as boolean
    if meta = invalid or meta.genres = invalid or type(meta.genres) <> "roArray" then return false
    for each g in meta.genres
        lg = LCase(strOr(g))
        if lg = "animation" or lg = "anime" or lg = "animação" or lg = "animacao" then return true
    end for
    return false
end function
