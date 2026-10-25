' Conta Stremio: ligação por código, login, sincronização de add-ons/biblioteca e progresso

sub init_x()
    m.top.functionName = "run"
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("StremioTask.init", e)
    end try
end sub

function apiUrl(method as string) as string
    return "https://api.strem.io/api/" + method
end function

sub run_x()
    req = m.top.request
    kind = req.kind
    res = { ok: false }
    if kind = "linkCreate"
        res = linkCreate()
    else if kind = "linkPoll"
        res = linkPoll(req.code)
    else if kind = "login"
        res = login(req.email, req.password)
    else if kind = "sync"
        res = sync(req.authKey)
    else if kind = "push"
        res = push(req.authKey, req.itemJson)
    end if
    res.kind = kind
    m.top.result = res
end sub
sub run()
    try
        run_x()
    catch e
        reportCrash("StremioTask.run", e)
    end try
end sub

function apiError(d as dynamic) as string
    if d = invalid then return "Sem resposta do servidor Stremio."
    if d.error <> invalid
        if type(d.error) = "roAssociativeArray" and isStr(d.error.message) then return d.error.message
        return "Erro do servidor Stremio."
    end if
    return ""
end function

function linkCreate() as object
    d = httpJson("https://link.stremio.com/api/v2/create?type=Create")
    if d = invalid or d.result = invalid then return { ok: false, error: "Não foi possível criar o código de ligação." }
    r = d.result
    qr = ""
    q = sOr(r.qrcode)
    if LCase(Left(q, 4)) = "http"
        qr = q
    else if q <> ""
        p = Instr(1, q, ",")
        if Left(q, 5) = "data:" and p > 0 then q = Mid(q, p + 1)
        ba = CreateObject("roByteArray")
        ba.FromBase64String(q)
        path = "tmp:/stremio_qr_" + CreateObject("roDateTime").AsSeconds().ToStr() + ".png"
        if ba.Count() > 0 and ba.WriteFile(path) then qr = path
    end if
    return { ok: true, code: sOr(r.code), link: sOr(r.link), qr: qr }
end function

function linkPoll(code as string) as object
    url = "https://link.stremio.com/api/v2/read?type=Read&code=" + urlEscape(code)
    for i = 1 to 120
        if m.top.cancel then return { ok: false, error: "cancelado" }
        d = httpJson(url)
        if d <> invalid and d.result <> invalid and isStr(d.result.authKey)
            key = d.result.authKey
            return { ok: true, authKey: key, email: userEmail(key) }
        end if
        sleep(3000)
    end for
    return { ok: false, error: "O código expirou. Tenta outra vez." }
end function

function userEmail(key as string) as string
    body = "{" + jsonValue("type") + ":" + jsonValue("GetUser") + "," + jsonValue("authKey") + ":" + jsonValue(key) + "}"
    d = httpPostJson(apiUrl("getUser"), body)
    if d <> invalid and d.result <> invalid then return sOr(d.result.email)
    return ""
end function

function login(email as string, password as string) as object
    body = "{" + jsonValue("type") + ":" + jsonValue("Login") + "," + jsonValue("email") + ":" + jsonValue(email) + "," + jsonValue("password") + ":" + jsonValue(password) + "," + jsonValue("facebook") + ":false}"
    d = httpPostJson(apiUrl("login"), body)
    err = apiError(d)
    if err <> "" then return { ok: false, error: err }
    if d.result = invalid or not isStr(d.result.authKey) then return { ok: false, error: "Resposta inesperada do Stremio." }
    em = email
    if d.result.user <> invalid and isStr(d.result.user.email) then em = d.result.user.email
    return { ok: true, authKey: d.result.authKey, email: em }
end function

function sync(key as string) as object
    body = "{" + jsonValue("type") + ":" + jsonValue("AddonCollectionGet") + "," + jsonValue("authKey") + ":" + jsonValue(key) + "," + jsonValue("update") + ":true}"
    d = httpPostJson(apiUrl("addonCollectionGet"), body)
    err = apiError(d)
    if err <> "" then return { ok: false, error: err }
    addons = []
    if d.result <> invalid and d.result.addons <> invalid and type(d.result.addons) = "roArray"
        for each a in d.result.addons
            u = sOr(a.transportUrl)
            lu = LCase(u)
            if Left(lu, 4) = "http" and Instr(1, lu, "127.0.0.1") = 0 and Instr(1, lu, "localhost") = 0
                addons.Push({ base: baseFromUrl(u), manifest: a.manifest })
            end if
        end for
    end if

    body = "{" + jsonValue("authKey") + ":" + jsonValue(key) + "," + jsonValue("collection") + ":" + jsonValue("libraryItem") + "," + jsonValue("ids") + ":[]," + jsonValue("all") + ":true}"
    lib = httpPostJson(apiUrl("datastoreGet"), body, 45000)
    library = []
    if lib <> invalid and lib.result <> invalid and type(lib.result) = "roArray" then library = lib.result
    return { ok: true, addons: addons, library: library }
end function

function push(key as string, itemJson as string) as object
    body = "{" + jsonValue("authKey") + ":" + jsonValue(key) + "," + jsonValue("collection") + ":" + jsonValue("libraryItem") + "," + jsonValue("changes") + ":[" + itemJson + "]}"
    d = httpPostJson(apiUrl("datastorePut"), body)
    err = apiError(d)
    return { ok: err = "", error: err }
end function
