' Trakt: ligação por código (device flow), renovação do token, scrobble e recomendações

sub init_x()
    m.top.functionName = "run"
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("TraktTask.init", e)
    end try
end sub

function traktBase() as string
    return "https://api.trakt.tv"
end function

function authHeaders(req as object) as object
    return { "trakt-api-version": "2", "trakt-api-key": req.clientId, Authorization: "Bearer " + sOr(req.token) }
end function

sub run_x()
    req = m.top.request
    kind = req.kind
    res = { ok: false }
    if kind = "code"
        res = deviceCode(req.clientId)
    else if kind = "poll"
        res = pollToken(req)
    else if kind = "refresh"
        res = refreshToken(req)
    else if kind = "scrobble"
        res = scrobble(req)
    else if kind = "recs"
        res = recommendations(req)
    end if
    res.kind = kind
    m.top.result = res
end sub
sub run()
    try
        run_x()
    catch e
        reportCrash("TraktTask.run", e)
    end try
end sub

function deviceCode(clientId as string) as object
    body = "{" + jsonValue("client_id") + ":" + jsonValue(clientId) + "}"
    r = httpPostJsonH(traktBase() + "/oauth/device/code", body, {}, 20000)
    if r.code <> 200 or r.data = invalid then return { ok: false, error: "O Trakt recusou o Client ID (código " + r.code.ToStr() + ")." }
    d = r.data
    interval = 5
    if d.interval <> invalid then interval = d.interval
    return { ok: true, deviceCode: sOr(d.device_code), userCode: sOr(d.user_code), url: sOr(d.verification_url), interval: interval }
end function

function tokenResult(d as dynamic) as object
    expires = 0
    if d.created_at <> invalid and d.expires_in <> invalid then expires = d.created_at + d.expires_in
    return { ok: true, token: sOr(d.access_token), refresh: sOr(d.refresh_token), expires: expires }
end function

function pollToken(req as object) as object
    body = "{" + jsonValue("code") + ":" + jsonValue(req.deviceCode) + "," + jsonValue("client_id") + ":" + jsonValue(req.clientId) + "," + jsonValue("client_secret") + ":" + jsonValue(req.clientSecret) + "}"
    delay = req.interval
    if delay = invalid or delay < 1 then delay = 5
    for i = 1 to 120
        if m.top.cancel then return { ok: false, error: "cancelado" }
        r = httpPostJsonH(traktBase() + "/oauth/device/token", body, {}, 20000)
        if r.code = 200 and r.data <> invalid then return tokenResult(r.data)
        if r.code = 404 or r.code = 409 or r.code = 410 or r.code = 418
            return { ok: false, error: "O código expirou ou foi recusado. Tenta outra vez." }
        end if
        if r.code = 429 then delay = delay + 1
        sleep(delay * 1000)
    end for
    return { ok: false, error: "Tempo esgotado." }
end function

function refreshToken(req as object) as object
    body = "{" + jsonValue("refresh_token") + ":" + jsonValue(req.refresh) + "," + jsonValue("client_id") + ":" + jsonValue(req.clientId) + "," + jsonValue("client_secret") + ":" + jsonValue(req.clientSecret) + "," + jsonValue("redirect_uri") + ":" + jsonValue("urn:ietf:wg:oauth:2.0:oob") + "," + jsonValue("grant_type") + ":" + jsonValue("refresh_token") + "}"
    r = httpPostJsonH(traktBase() + "/oauth/token", body, {}, 20000)
    if r.code = 200 and r.data <> invalid then return tokenResult(r.data)
    return { ok: false, error: "Não foi possível renovar a sessão Trakt." }
end function

function scrobble(req as object) as object
    parts = sOr(req.videoId).Split(":")
    imdb = parts[0]
    if Left(imdb, 2) <> "tt" then return { ok: false }
    prog = jsonValue(req.progress)
    if req.type = "series" and parts.Count() >= 3
        body = "{" + jsonValue("show") + ":{" + jsonValue("ids") + ":{" + jsonValue("imdb") + ":" + jsonValue(imdb) + "}}," + jsonValue("episode") + ":{" + jsonValue("season") + ":" + parts[1] + "," + jsonValue("number") + ":" + parts[2] + "}," + jsonValue("progress") + ":" + prog + "}"
    else
        body = "{" + jsonValue("movie") + ":{" + jsonValue("ids") + ":{" + jsonValue("imdb") + ":" + jsonValue(imdb) + "}}," + jsonValue("progress") + ":" + prog + "}"
    end if
    action = "stop"
    if req.action <> invalid then action = req.action
    r = httpPostJsonH(traktBase() + "/scrobble/" + action, body, authHeaders(req), 20000)
    return { ok: r.code = 201 or r.code = 200 or r.code = 409, code: r.code }
end function

function recommendations(req as object) as object
    h = authHeaders(req)
    rows = []
    for each kind in [["movies", "movie", "Filmes recomendados (Trakt)"], ["shows", "show", "Séries recomendadas (Trakt)"]]
        d = httpJsonAuth(traktBase() + "/recommendations/" + kind[0] + "?limit=25&ignore_collected=true", h, 20000)
        items = []
        if d <> invalid and type(d) = "roArray"
            for each x in d
                ids = invalid
                if x.ids <> invalid then ids = x.ids
                if ids <> invalid and isStr(ids.imdb)
                    t = "movie"
                    if kind[1] = "show" then t = "series"
                    items.Push({ id: ids.imdb, type: t, name: sOr(x.title), poster: "https://images.metahub.space/poster/medium/" + ids.imdb + "/img", videoId: "", progress: 0.0 })
                end if
            end for
        end if
        if items.Count() > 0 then rows.Push({ title: kind[2], items: items })
    end for
    return { ok: true, rows: rows }
end function
