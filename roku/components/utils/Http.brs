' Rede (só para Tasks): pedidos em paralelo, POST JSON e descargas

function newXfer(url as string) as object
    x = CreateObject("roUrlTransfer")
    x.SetUrl(url)
    x.SetCertificatesFile("common:/certs/ca-bundle.crt")
    x.InitClientCertificates()
    x.EnableEncodings(true)
    x.RetainBodyOnError(true)
    x.AddHeader("User-Agent", "Hortencioflix/0.4 (Roku)")
    x.AddHeader("Accept", "*/*")
    return x
end function

' Faz vários GET em simultâneo; devolve [{ok, code, body}] pela mesma ordem
function httpGetMany(urls as object, timeoutMs as integer, maxParallel as integer) as object
    results = []
    for i = 0 to urls.Count() - 1
        results.Push({ ok: false, code: 0, body: "" })
    end for
    port = CreateObject("roMessagePort")
    active = {}
    nextIdx = 0
    pending = 0
    timer = CreateObject("roTimespan")
    while true
        while nextIdx < urls.Count() and pending < maxParallel
            x = newXfer(urls[nextIdx])
            x.SetMessagePort(port)
            if x.AsyncGetToString()
                active[x.GetIdentity().ToStr()] = { x: x, i: nextIdx }
                pending = pending + 1
            end if
            nextIdx = nextIdx + 1
        end while
        if pending = 0 then exit while
        remaining = timeoutMs - timer.TotalMilliseconds()
        if remaining <= 0
            for each k in active
                active[k].x.AsyncCancel()
            end for
            exit while
        end if
        msg = wait(remaining, port)
        if type(msg) = "roUrlEvent" and msg.GetInt() = 1
            k = msg.GetSourceIdentity().ToStr()
            if active.DoesExist(k)
                e = active[k]
                code = msg.GetResponseCode()
                results[e.i] = { ok: (code >= 200 and code < 300), code: code, body: msg.GetString() }
                active.Delete(k)
                pending = pending - 1
            end if
        end if
    end while
    return results
end function

function httpGet(url as string, timeoutMs = 30000 as integer) as object
    return httpGetMany([url], timeoutMs, 1)[0]
end function

function httpJson(url as string) as dynamic
    r = httpGet(url, 30000)
    if not r.ok then return invalid
    return ParseJson(r.body)
end function

function httpJsonMany(urls as object, timeoutMs as integer, maxParallel as integer) as object
    out = []
    for each r in httpGetMany(urls, timeoutMs, maxParallel)
        if r.ok
            out.Push(ParseJson(r.body))
        else
            out.Push(invalid)
        end if
    end for
    return out
end function

' POST com corpo JSON já serializado (para controlar a capitalização das chaves)
function httpPostJson(url as string, body as string, timeoutMs = 30000 as integer) as dynamic
    x = newXfer(url)
    port = CreateObject("roMessagePort")
    x.SetMessagePort(port)
    x.AddHeader("Content-Type", "application/json")
    if not x.AsyncPostFromString(body) then return invalid
    msg = wait(timeoutMs, port)
    if type(msg) = "roUrlEvent"
        return ParseJson(msg.GetString())
    end if
    x.AsyncCancel()
    return invalid
end function

' Descarrega vários ficheiros em simultâneo; devolve [true/false]
function httpDownloadMany(urls as object, paths as object, timeoutMs as integer, maxParallel as integer) as object
    results = []
    for i = 0 to urls.Count() - 1
        results.Push(false)
    end for
    port = CreateObject("roMessagePort")
    active = {}
    nextIdx = 0
    pending = 0
    timer = CreateObject("roTimespan")
    while true
        while nextIdx < urls.Count() and pending < maxParallel
            x = newXfer(urls[nextIdx])
            x.SetMessagePort(port)
            if x.AsyncGetToFile(paths[nextIdx])
                active[x.GetIdentity().ToStr()] = { x: x, i: nextIdx }
                pending = pending + 1
            end if
            nextIdx = nextIdx + 1
        end while
        if pending = 0 then exit while
        remaining = timeoutMs - timer.TotalMilliseconds()
        if remaining <= 0
            for each k in active
                active[k].x.AsyncCancel()
            end for
            exit while
        end if
        msg = wait(remaining, port)
        if type(msg) = "roUrlEvent" and msg.GetInt() = 1
            k = msg.GetSourceIdentity().ToStr()
            if active.DoesExist(k)
                e = active[k]
                code = msg.GetResponseCode()
                results[e.i] = (code >= 200 and code < 300)
                active.Delete(k)
                pending = pending - 1
            end if
        end if
    end while
    return results
end function

function urlEscape(s as string) as string
    return CreateObject("roUrlTransfer").Escape(s)
end function

' Serializa um valor isolado em JSON (string, número, booleano, null)
function jsonValue(v as dynamic) as string
    s = FormatJson([v])
    return Mid(s, 2, Len(s) - 2)
end function

function isStr(v as dynamic) as boolean
    return v <> invalid and GetInterface(v, "ifString") <> invalid
end function

function sOr(v as dynamic) as string
    if isStr(v) then return v
    return ""
end function

function baseFromUrl(u as string) as string
    s = u.Trim()
    if LCase(Left(s, 10)) = "stremio://" then s = "https://" + Mid(s, 11)
    if LCase(Right(s, 14)) = "/manifest.json" then s = Left(s, Len(s) - 14)
    while Len(s) > 0 and Right(s, 1) = "/"
        s = Left(s, Len(s) - 1)
    end while
    return s
end function

' HEAD (sem descarregar o corpo) para testar se um link responde; segue redireções
function httpHead(url as string, headers as dynamic, timeoutMs as integer) as object
    x = newXfer(url)
    port = CreateObject("roMessagePort")
    x.SetMessagePort(port)
    if headers <> invalid and type(headers) = "roArray"
        for each h in headers
            p = Instr(1, h, ":")
            if p > 1 then x.AddHeader(Left(h, p - 1), Mid(h, p + 1))
        end for
    end if
    if not x.AsyncHead() then return { code: -1, contentType: "" }
    msg = wait(timeoutMs, port)
    if type(msg) = "roUrlEvent"
        ct = ""
        hdrs = msg.GetResponseHeaders()
        if hdrs <> invalid
            for each k in hdrs
                if LCase(k) = "content-type" then ct = LCase(sOr(hdrs[k]))
            end for
        end if
        return { code: msg.GetResponseCode(), contentType: ct }
    end if
    x.AsyncCancel()
    return { code: -2, contentType: "" }
end function

' GET com cabeçalhos (ex.: Authorization) e JSON na resposta
function httpJsonAuth(url as string, headers as object, timeoutMs as integer) as dynamic
    x = newXfer(url)
    port = CreateObject("roMessagePort")
    x.SetMessagePort(port)
    for each k in headers
        x.AddHeader(k, headers[k])
    end for
    if not x.AsyncGetToString() then return invalid
    msg = wait(timeoutMs, port)
    if type(msg) = "roUrlEvent"
        return ParseJson(msg.GetString())
    end if
    x.AsyncCancel()
    return invalid
end function

' POST JSON com cabeçalhos extra (ex.: Trakt); devolve { code, data }
function httpPostJsonH(url as string, body as string, headers as object, timeoutMs as integer) as object
    x = newXfer(url)
    port = CreateObject("roMessagePort")
    x.SetMessagePort(port)
    x.AddHeader("Content-Type", "application/json")
    for each k in headers
        x.AddHeader(k, headers[k])
    end for
    if not x.AsyncPostFromString(body) then return { code: -1, data: invalid }
    msg = wait(timeoutMs, port)
    if type(msg) = "roUrlEvent"
        return { code: msg.GetResponseCode(), data: ParseJson(msg.GetString()) }
    end if
    x.AsyncCancel()
    return { code: -2, data: invalid }
end function

' Testa vários links em paralelo (HEAD); devolve true/false (online) pela mesma ordem
function httpAliveMany(urls as object, timeoutMs as integer, maxParallel as integer) as object
    results = []
    for i = 0 to urls.Count() - 1
        results.Push(false)
    end for
    port = CreateObject("roMessagePort")
    active = {}
    nextIdx = 0
    pending = 0
    timer = CreateObject("roTimespan")
    while true
        while nextIdx < urls.Count() and pending < maxParallel
            x = newXfer(urls[nextIdx])
            x.SetMessagePort(port)
            if x.AsyncHead()
                active[x.GetIdentity().ToStr()] = { x: x, i: nextIdx, t: timer.TotalMilliseconds() }
                pending = pending + 1
            end if
            nextIdx = nextIdx + 1
        end while
        if pending = 0 then exit while
        remaining = timeoutMs - timer.TotalMilliseconds()
        if remaining <= 0
            for each k in active
                active[k].x.AsyncCancel()
            end for
            exit while
        end if
        msg = wait(remaining, port)
        if type(msg) = "roUrlEvent" and msg.GetInt() = 1
            k = msg.GetSourceIdentity().ToStr()
            if active.DoesExist(k)
                e = active[k]
                code = msg.GetResponseCode()
                ' 405/403 em HEAD não quer dizer que o canal esteja em baixo
                results[e.i] = (code >= 200 and code < 400) or code = 405 or code = 403
                active.Delete(k)
                pending = pending - 1
            end if
        end if
    end while
    return results
end function
