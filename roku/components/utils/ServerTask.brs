' Servidor HTTP mínimo (só rede local) com uma página para colar chaves, add-ons,
' listas IPTV, Trakt/TMDB e para fazer cópia de segurança / restauro das definições.

sub init_x()
    m.top.functionName = "run"
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("ServerTask.init", e)
    end try
end sub

sub run_x()
    port = CreateObject("roMessagePort")
    addr = CreateObject("roSocketAddress")
    addr.setPort(m.top.port)
    srv = CreateObject("roStreamSocket")
    srv.setMessagePort(port)
    srv.setAddress(addr)
    srv.notifyReadable(true)
    srv.listen(4)
    if not srv.eOK()
        m.top.status = "error"
        return
    end if
    m.top.status = "running"
    conns = {}
    while not m.top.cancel
        msg = wait(1000, port)
        if type(msg) = "roSocketEvent"
            sid = msg.getSocketID()
            if sid = srv.getID()
                if srv.isReadable()
                    c = srv.accept()
                    if c <> invalid
                        c.setMessagePort(port)
                        c.notifyReadable(true)
                        conns[c.getID().ToStr()] = { s: c, buf: "" }
                    end if
                end if
            else
                k = sid.ToStr()
                if conns.DoesExist(k)
                    e = conns[k]
                    c = e.s
                    if c.isReadable()
                        got = c.receiveStr(65536)
                        if got = "" or not c.eOK()
                            c.close()
                            conns.Delete(k)
                        else
                            e.buf = e.buf + got
                            ' pedidos enormes (ou maliciosos) não podem esgotar a memória da Roku
                            if Len(e.buf) > 2000000
                                c.close()
                                conns.Delete(k)
                            else if requestComplete(e.buf)
                                resp = handleRequest(e.buf)
                                sendAll(c, resp)
                                c.close()
                                conns.Delete(k)
                            end if
                        end if
                    end if
                end if
            end if
        end if
    end while
    for each k in conns
        conns[k].s.close()
    end for
    srv.close()
end sub
sub run()
    try
        run_x()
    catch e
        reportCrash("ServerTask.run", e)
    end try
end sub

sub sendAll(c as object, data as string)
    ' envia em blocos (a página tem alguns KB)
    total = Len(data)
    i = 1
    while i <= total
        part = Mid(data, i, 4096)
        c.sendStr(part)
        i = i + 4096
    end while
end sub

function requestComplete(buf as string) as boolean
    he = Instr(1, buf, Chr(13) + Chr(10) + Chr(13) + Chr(10))
    if he = 0 then return false
    headers = LCase(Left(buf, he))
    p = Instr(1, headers, "content-length:")
    if p = 0 then return true
    q = Instr(p, headers, Chr(13))
    n = Int(Val(Mid(headers, p + 15, q - p - 15)))
    return Len(buf) - (he + 3) >= n
end function

function urlDecode(s as string) as string
    t = s.Replace("+", " ")
    return CreateObject("roUrlTransfer").Unescape(t)
end function

function parseQuery(q as string) as object
    out = {}
    for each pair in q.Split("&")
        p = Instr(1, pair, "=")
        if p > 1
            out[urlDecode(Left(pair, p - 1))] = urlDecode(Mid(pair, p + 1))
        end if
    end for
    return out
end function

function htmlEsc(s as string) as string
    return s.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;").Replace(Chr(34), "&quot;")
end function

function httpResponse(code as string, ctype as string, body as string, extra as string) as string
    crlf = Chr(13) + Chr(10)
    ' Len conta caracteres; o corpo pode ter acentos (UTF-8), por isso não enviamos Content-Length
    return "HTTP/1.1 " + code + crlf + "Content-Type: " + ctype + crlf + extra + "Connection: close" + crlf + crlf + body
end function

function handleRequest(buf as string) as string
    lineEnd = Instr(1, buf, Chr(13))
    first = Left(buf, lineEnd - 1)
    parts = first.Split(" ")
    if parts.Count() < 2 then return httpResponse("400 Bad Request", "text/plain", "pedido inválido", "")
    method = parts[0]
    target = parts[1]
    path = target
    query = ""
    qp = Instr(1, target, "?")
    if qp > 0
        path = Left(target, qp - 1)
        query = Mid(target, qp + 1)
    end if
    if path = "/sync.json" or path = "/sync" then return handleSync(method, path, query, buf)
    if m.top.syncOnly then return httpResponse("404 Not Found", "text/plain", "", "")
    if path = "/backup.json"
        return httpResponse("200 OK", "application/json; charset=utf-8", exportSettings(), "Content-Disposition: attachment; filename=" + Chr(34) + "hortencioflix-backup.json" + Chr(34) + Chr(13) + Chr(10))
    end if
    msg = ""
    if path = "/save"
        ' formulários enviados por POST: as chaves não ficam no histórico do navegador
        form = parseQuery(query)
        if method = "POST"
            he = Instr(1, buf, Chr(13) + Chr(10) + Chr(13) + Chr(10))
            form = parseQuery(Mid(buf, he + 4))
        end if
        msg = applyForm(form)
    else if path = "/restore" and method = "POST"
        he = Instr(1, buf, Chr(13) + Chr(10) + Chr(13) + Chr(10))
        body = Mid(buf, he + 4)
        form = parseQuery(body)
        if form.data <> invalid and importSettings(form.data)
            msg = "Definições restauradas. Reinicia a app na TV para aplicar tudo."
            m.top.changed = "restore"
        else
            msg = "O ficheiro de cópia de segurança não é válido."
        end if
    end if
    return httpResponse("200 OK", "text/html; charset=utf-8", page(msg), "")
end function

function applyForm(f as object) as string
    field = sv(f.f)
    if field = "torbox"
        cfgWrite("torboxKey", sv(f.v))
        m.top.changed = "torbox"
        return "Chave TorBox guardada."
    else if field = "addon"
        u = normalizeAddonUrl(sv(f.v))
        if LCase(Left(u, 4)) <> "http" then return "O endereço do add-on deve começar por https:// ou stremio://"
        list = getUserAddons()
        found = false
        for each a in list
            if a = u then found = true
        end for
        if not found
            list.Push(u)
            setUserAddons(list)
        end if
        m.top.changed = "addon"
        return "Add-on adicionado: " + u
    else if field = "m3u"
        u = sv(f.v)
        if LCase(Left(u, 4)) <> "http" then return "O URL da lista deve começar por http:// ou https://"
        name = sv(f.n)
        if name = "" then name = "Lista do telemóvel"
        lists = cfgJsonArray("iptvLists")
        lists.Push({ n: name, u: u, e: sv(f.epg) })
        cfgWrite("iptvLists", FormatJson(lists))
        m.top.changed = "iptv"
        return "Lista IPTV adicionada: " + name
    else if field = "xtream"
        srvUrl = sv(f.s)
        if srvUrl = "" or sv(f.u) = "" then return "Preenche o servidor, utilizador e palavra-passe."
        if LCase(Left(srvUrl, 4)) <> "http" then srvUrl = "http://" + srvUrl
        while Right(srvUrl, 1) = "/"
            srvUrl = Left(srvUrl, Len(srvUrl) - 1)
        end while
        esc = CreateObject("roUrlTransfer")
        u = srvUrl + "/get.php?username=" + esc.Escape(sv(f.u)) + "&password=" + esc.Escape(sv(f.p)) + "&type=m3u_plus&output=m3u8"
        lists = cfgJsonArray("iptvLists")
        lists.Push({ n: "Xtream · " + sv(f.u), u: u, e: "" })
        cfgWrite("iptvLists", FormatJson(lists))
        m.top.changed = "iptv"
        return "Conta Xtream adicionada."
    else if field = "tmdb"
        cfgWrite("tmdbKey", sv(f.v))
        m.top.changed = "tmdb"
        return "Chave TMDB guardada."
    else if field = "trakt"
        cfgWrite("traktId", sv(f.id))
        cfgWrite("traktSecret", sv(f.secret))
        m.top.changed = "trakt"
        return "Dados da app Trakt guardados. Agora, na TV, vai a Definições → Ligar Trakt."
    else if field = "update"
        cfgWrite("updateUrl", sv(f.v))
        return "Endereço de atualizações guardado."
    end if
    return ""
end function

function sv(v as dynamic) as string
    if v = invalid then return ""
    return strOr(v).Trim()
end function

function page(msg as string) as string
    css = "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0f1014;color:#e6e7ea;margin:0;padding:16px;max-width:640px;margin:auto}h1{color:#e50914;font-size:26px;margin:8px 0 4px}h2{font-size:17px;margin:24px 0 8px;color:#fff}p{color:#9a9da6;font-size:14px}form{background:#181a20;border-radius:12px;padding:14px;margin-bottom:12px}input,textarea{width:100%;box-sizing:border-box;padding:12px;margin:6px 0;border-radius:8px;border:1px solid #333;background:#0f1014;color:#fff;font-size:16px}button{background:#e50914;color:#fff;border:0;border-radius:8px;padding:12px 18px;font-size:16px;margin-top:6px;width:100%}.msg{background:#1f9d55;color:#fff;padding:12px;border-radius:8px}a{color:#e0a526}"
    h = "<!doctype html><html lang=" + Chr(34) + "pt" + Chr(34) + "><head><meta charset=" + Chr(34) + "utf-8" + Chr(34) + "><meta name=" + Chr(34) + "viewport" + Chr(34) + " content=" + Chr(34) + "width=device-width,initial-scale=1" + Chr(34) + "><title>Hortêncioflix</title><style>" + css + "</style></head><body>"
    h = h + "<h1>Hortêncioflix</h1><p>Configura a app da TV a partir do telemóvel. As alterações aplicam-se logo.</p>"
    if msg <> "" then h = h + "<div class=" + Chr(34) + "msg" + Chr(34) + ">" + htmlEsc(msg) + "</div>"
    h = h + formBlock("Chave TorBox", "torbox", "<input name=v placeholder='Chave API (torbox.app → Settings)'>")
    h = h + formBlock("Adicionar add-on Stremio", "addon", "<input name=v placeholder='https://…/manifest.json ou stremio://…'>")
    h = h + formBlock("Adicionar lista IPTV (M3U)", "m3u", "<input name=n placeholder='Nome da lista'><input name=v placeholder='URL da lista M3U'><input name=epg placeholder='URL do guia EPG (opcional, XMLTV sem .gz)'>")
    h = h + formBlock("Adicionar conta Xtream Codes", "xtream", "<input name=s placeholder='Servidor (http://exemplo.com:8080)'><input name=u placeholder='Utilizador'><input name=p placeholder='Palavra-passe' type=password>")
    h = h + formBlock("Chave TMDB (sinopses em português e filmografias de atores)", "tmdb", "<input name=v placeholder='API Key v3 de themoviedb.org'>")
    h = h + formBlock("App Trakt (Client ID e Secret)", "trakt", "<input name=id placeholder='Client ID'><input name=secret placeholder='Client Secret'><p>Cria em trakt.tv/oauth/applications (Redirect URI: urn:ietf:wg:oauth:2.0:oob).</p>")
    h = h + formBlock("Endereço de atualizações (opcional)", "update", "<input name=v placeholder='URL de um JSON {version, notes}'>")
    h = h + "<h2>Cópia de segurança</h2><form><p><a href=/backup.json>Descarregar cópia de segurança (JSON)</a></p></form>"
    h = h + "<form method=post action=/restore><p>Restaurar: cola aqui o conteúdo do ficheiro de cópia de segurança.</p><textarea name=data rows=5></textarea><button>Restaurar</button></form>"
    h = h + "</body></html>"
    return h
end function

function formBlock(title as string, field as string, inputs as string) as string
    return "<h2>" + title + "</h2><form method=post action=/save><input type=hidden name=f value=" + field + ">" + inputs + "<button>Guardar</button></form>"
end function

' ---------- Sincronização com a app Android (progresso, vistos, listas e favoritos IPTV) ----------

function handleSync(method as string, path as string, query as string, buf as string) as string
    q = parseQuery(query)
    code = cfgRead("syncCode", "")
    ' proteção contra adivinhar o código na rede: 5 tentativas erradas bloqueiam 10 minutos
    now = CreateObject("roDateTime").AsSeconds()
    if m.syncLockUntil <> invalid and now < m.syncLockUntil then return httpResponse("429 Too Many Requests", "application/json", "{" + Chr(34) + "ok" + Chr(34) + ":false}", "")
    if code = "" or q.k = invalid or q.k <> code
        if m.syncBad = invalid then m.syncBad = 0
        m.syncBad = m.syncBad + 1
        if m.syncBad >= 5
            m.syncBad = 0
            m.syncLockUntil = now + 600
        end if
        return httpResponse("403 Forbidden", "application/json", "{" + Chr(34) + "ok" + Chr(34) + ":false}", "")
    end if
    m.syncBad = 0
    if path = "/sync.json"
        d = { ok: true, v: 1, app: "hortencioflix-roku", version: appVersion(), progress: getProgress(), watched: cfgJsonArray(pkey("watched")), done: cfgJsonArray(pkey("doneRecent")), iptvLists: getIptvLists(), iptvFav: getFavs(), addons: getUserAddons() }
        return httpResponse("200 OK", "application/json; charset=utf-8", FormatJson(d), "")
    end if
    if method <> "POST" then return httpResponse("405 Method Not Allowed", "text/plain", "", "")
    he = Instr(1, buf, Chr(13) + Chr(10) + Chr(13) + Chr(10))
    d = ParseJson(Mid(buf, he + 4))
    if d = invalid then return httpResponse("400 Bad Request", "application/json", "{" + Chr(34) + "ok" + Chr(34) + ":false}", "")
    if d.progress <> invalid and type(d.progress) = "roArray" then cfgWrite(pkey("progress"), FormatJson(firstN(d.progress, 12)))
    if d.watched <> invalid and type(d.watched) = "roArray" then cfgWrite(pkey("watched"), FormatJson(firstN(d.watched, 25)))
    if d.iptvLists <> invalid and type(d.iptvLists) = "roArray" then cfgWrite("iptvLists", FormatJson(firstN(d.iptvLists, 15)))
    if d.iptvFav <> invalid and type(d.iptvFav) = "roArray" then cfgWrite(pkey("iptvFav"), FormatJson(firstN(d.iptvFav, 20)))
    ' preferências de reprodução vindas do telemóvel (só valores curtos e conhecidos)
    if d.prefs <> invalid and type(d.prefs) = "roAssociativeArray"
        for each k in ["subLang", "dubPref", "rangePref"]
            v = d.prefs[k]
            if v <> invalid and (type(v) = "roString" or type(v) = "String") and Len(v) <= 12 then cfgWrite(k, v)
        end for
        cu = d.prefs.curatorUrl
        if cu <> invalid and (type(cu) = "roString" or type(cu) = "String")
            if cu = ""
                cfgDelete("curatorUrl")
            else if Left(LCase(cu), 8) = "https://" and Len(cu) <= 150
                cfgWrite("curatorUrl", cu)
            end if
        end if
        co = d.prefs.curatorOn
        if co <> invalid and (co = "0" or co = "1") then cfgWrite("curatorOn", co)
    end if
    if d.addons <> invalid and type(d.addons) = "roArray"
        ' a Roku só tem ~16 KB de registo: links de add-on muito compridos ficam de fora
        keep = []
        total = 0
        for each a in d.addons
            if isStrVal(a) and Left(LCase(a), 4) = "http" and Len(a) <= 700 and total + Len(a) <= 5000
                keep.Push(normalizeAddonUrl(a))
                total = total + Len(a)
            end if
        end for
        if keep.Count() > 0 then setUserAddons(keep)
    end if
    m.top.changed = "sync"
    return httpResponse("200 OK", "application/json", "{" + Chr(34) + "ok" + Chr(34) + ":true}", "")
end function

function firstN(arr as object, n as integer) as object
    out = []
    for each x in arr
        if out.Count() < n then out.Push(x)
    end for
    return out
end function
