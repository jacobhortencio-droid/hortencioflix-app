' TV ao vivo: listas M3U/Xtream, bases públicas e add-ons Stremio de TV
' (da conta Stremio ou locais), com favoritos, recentes e pesquisa.

sub init_x()
    m.gridOn = false
    hdr = m.top.findNode("header")
    if hdr <> invalid then hdr.color = accentColor()
    m.header = m.top.findNode("header")
    m.summary = m.top.findNode("summary")
    m.groupsList = m.top.findNode("groups")
    m.channelsList = m.top.findNode("channels")
    m.status = m.top.findNode("status")
    m.menu = m.top.findNode("menu")
    m.groupsList.observeField("itemFocused", "onGroupFocused")
    m.groupsList.observeField("itemSelected", "onGroupSelected")
    m.channelsList.observeField("itemSelected", "onChannelSelected")
    m.channelsList.observeField("itemFocused", "onChannelFocused")
    m.epgTimer = m.top.findNode("epgTimer")
    m.epgTimer.observeField("fire", "updateEpg")
    m.epgLabel = m.top.findNode("epg")
    m.menu.observeField("selected", "onMenuSelected")
    m.menu.observeField("cancelled", "closeMenu")
    m.top.observeField("focusedChild", "onFocusedChild")
    m.groups = []
    m.groupMap = []
    m.view = []
    m.sources = []
    m.tvSources = []
    m.cur = -1
    m.curKey = ""
    m.waitingTv = true
    m.pendingKey = ""
    m.loadingGroup = -1
    m.offline = {}
    m.epgCache = {}
    m.xmlGuide = {}
    m.xmlLoaded = {}
    m.epgUrl = ""
    m.focusTarget = m.groupsList
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("IptvScreen.init", e)
    end try
end sub

sub onFocusedChild_x()
    if m.top.hasFocus()
        if m.menu.visible
            m.menu.setFocus(true)
        else
            m.focusTarget.setFocus(true)
        end if
    end if
end sub
sub onFocusedChild()
    try
        onFocusedChild_x()
    catch e
        reportCrash("IptvScreen.onFocusedChild", e)
    end try
end sub

sub focusOn(node as object)
    m.focusTarget = node
    if not m.menu.visible and screenIsActive() then node.setFocus(true)
end sub

' ---------- Fontes (listas M3U + add-ons de TV) ----------

sub onStart_x()
    m.waitingTv = true
    rebuildSources()
    key = cfgRead("iptvCurKey", "")
    idx = sourceIndex(key)
    if idx >= 0
        selectSource(idx)
    else if Left(key, 6) = "addon:"
        ' a fonte guardada é um add-on: espera pela deteção
        renderGroups(0)
        m.status.text = "A procurar add-ons de TV…"
    else if m.sources.Count() > 0
        selectSource(0)
    else
        renderGroups(0)
        m.status.text = "A procurar add-ons de TV…"
    end if
    scanTvAddons()
end sub
sub onStart()
    try
        onStart_x()
    catch e
        reportCrash("IptvScreen.onStart", e)
    end try
end sub

sub scanTvAddons()
    m.tvTask = startAddonTask({ kind: "tvsources" }, "onTvSources")
end sub

sub onTvSources_x(event as object)
    if m.tvTask = invalid or not event.getRoSGNode().isSameNode(m.tvTask) then return
    res = event.getData()
    storeManifests(res)
    m.waitingTv = false
    m.tvSources = res.sources
    if m.tvSources = invalid then m.tvSources = []
    curKey = ""
    if m.cur >= 0 and m.cur < m.sources.Count() then curKey = m.sources[m.cur].key
    rebuildSources()
    want = cfgRead("iptvCurKey", "")
    if m.pendingKey <> ""
        want = m.pendingKey
        m.pendingKey = ""
    end if
    idx = sourceIndex(want)
    if curKey <> "" and want = curKey
        m.cur = sourceIndex(curKey)
        updateSummary()
        renderGroups(m.groupsList.itemFocused)
    else if idx >= 0
        selectSource(idx)
    else if m.cur < 0 and m.sources.Count() > 0
        selectSource(0)
    else if m.sources.Count() = 0
        showEmpty()
    end if
end sub
sub onTvSources(event as object)
    try
        onTvSources_x(event)
    catch e
        reportCrash("IptvScreen.onTvSources", e)
    end try
end sub

sub rebuildSources()
    m.sources = []
    for each l in getIptvLists()
        m.sources.Push({ kind: "m3u", n: strOr(l.n), u: strOr(l.u), e: strOr(l.e), key: strOr(l.u) })
    end for
    for each t in m.tvSources
        m.sources.Push({ kind: "addon", n: "[Add-on] " + strOr(t.n), base: t.base, groups: t.groups, key: "addon:" + t.base })
    end for
end sub

function sourceIndex(key as string) as integer
    if key = "" then return -1
    for i = 0 to m.sources.Count() - 1
        if m.sources[i].key = key then return i
    end for
    return -1
end function

sub showEmpty()
    m.header.text = "TV ao vivo"
    m.summary.text = ""
    m.groups = []
    renderGroups(0)
    m.channelsList.content = invalid
    m.status.text = "Ainda não tens listas nem add-ons de TV." + Chr(10) + Chr(10) + "Carrega OK em «Listas» para escolher uma base pública gratuita, um add-on de TV do Stremio, ou adicionar a tua lista M3U / conta Xtream."
    focusOn(m.groupsList)
end sub

sub selectSource(i as integer)
    if i < 0 or i >= m.sources.Count()
        showEmpty()
        return
    end if
    m.cur = i
    src = m.sources[i]
    cfgWrite("iptvCurKey", src.key)
    m.groups = []
    m.view = []
    m.curGroupKey = ""
    m.channelsList.content = invalid
    m.header.text = "TV ao vivo · " + src.n
    if src.kind = "addon"
        for each g in src.groups
            m.groups.Push({ title: g.title, url: g.url, channels: invalid })
        end for
        m.status.text = ""
        updateSummary()
        renderGroups(firstGroupIndex())
        showGroupAt(firstGroupIndex())
        focusOn(m.groupsList)
        return
    end if
    cache = invalid
    if m.global.hasField("iptvLast") then cache = m.global.iptvLast
    if cache <> invalid and strOr(cache.url) = src.u and cache.result <> invalid
        onParsed(cache.result)
        return
    end if
    m.summary.text = ""
    renderGroups(0)
    m.status.text = "A carregar «" + src.n + "»…" + Chr(10) + "Listas grandes podem demorar alguns segundos."
    m.loadingUrl = src.u
    m.task = startAddonTask({ kind: "m3u", url: m.loadingUrl }, "onLoaded")
end sub

sub onLoaded_x(event as object)
    if m.task = invalid or not event.getRoSGNode().isSameNode(m.task) then return
    res = event.getData()
    if res.ok
        if not m.global.hasField("iptvLast") then m.global.addField("iptvLast", "assocarray", false)
        m.global.iptvLast = { url: m.loadingUrl, result: res }
    end if
    onParsed(res)
end sub
sub onLoaded(event as object)
    try
        onLoaded_x(event)
    catch e
        reportCrash("IptvScreen.onLoaded", e)
    end try
end sub

sub onParsed(res as object)
    if not res.ok
        m.groups = []
        renderGroups(0)
        m.status.text = strOr(res.error)
        focusOn(m.groupsList)
        return
    end if
    m.status.text = ""
    m.groups = res.groups
    m.epgUrl = ""
    if m.cur >= 0 and m.cur < m.sources.Count() then m.epgUrl = strOr(m.sources[m.cur].e)
    if m.epgUrl = "" then m.epgUrl = strOr(res.epgUrl)
    if LCase(Right(m.epgUrl, 3)) = ".gz" then m.epgUrl = ""
    m.xmlGuide = {}
    m.xmlLoaded = {}
    updateSummary()
    m.curGroupKey = ""
    renderGroups(firstGroupIndex())
    showGroupAt(firstGroupIndex())
    focusOn(m.groupsList)
end sub

sub updateSummary()
    if m.cur < 0 then return
    src = m.sources[m.cur]
    total = 0
    for each g in m.groups
        if g.channels <> invalid then total = total + g.channels.Count()
    end for
    txt = ""
    if src.kind = "addon"
        txt = "Add-on Stremio de TV · " + m.groups.Count().ToStr() + " categorias"
    else
        txt = total.ToStr() + " canais em " + m.groups.Count().ToStr() + " categorias"
    end if
    m.summary.text = txt + "   ·   fonte " + (m.cur + 1).ToStr() + " de " + m.sources.Count().ToStr()
end sub

' ---------- Coluna de categorias ----------

sub renderGroups(focusIdx as integer)
    labels = ["» Listas e add-ons de TV"]
    m.groupMap = [{ k: "lists" }]
    labels.Push("Pesquisar canal…")
    m.groupMap.Push({ k: "search" })
    favs = getFavs()
    if favs.Count() > 0
        labels.Push("Favoritos (" + favs.Count().ToStr() + ")")
        m.groupMap.Push({ k: "fav" })
    end if
    rec = getRecents()
    if rec.Count() > 0
        labels.Push("Vistos recentemente")
        m.groupMap.Push({ k: "recent" })
    end if
    locked = adultLocked()
    hidden = 0
    for i = 0 to m.groups.Count() - 1
        g = m.groups[i]
        if locked and isAdultText(strOr(g.title))
            hidden = hidden + 1
        else
            t = cleanText(g.title)
            if g.channels <> invalid then t = t + "  (" + g.channels.Count().ToStr() + ")"
            labels.Push(t)
            m.groupMap.Push({ k: "g", i: i })
        end if
    end for
    if hidden > 0 and not isKidProfile()
        labels.Push("Conteúdo protegido (" + hidden.ToStr() + " categorias) — PIN")
        m.groupMap.Push({ k: "locked" })
    end if
    m.groupsList.content = labelContent(labels)
    if focusIdx > 0 and focusIdx < labels.Count() then m.groupsList.jumpToItem = focusIdx
end sub

' Primeiro item útil: favoritos, recentes ou a primeira categoria
function firstGroupIndex() as integer
    idx = 2
    if getFavs().Count() = 0 and getRecents().Count() = 0 and m.groups.Count() = 0 then idx = 0
    return idx
end function

function savedChannels(arr as object) as object
    out = []
    for each f in arr
        out.Push({ name: strOr(f.n), url: strOr(f.u), logo: strOr(f.l) })
    end for
    return out
end function

sub showGroupAt(idx as integer)
    if idx < 0 or idx >= m.groupMap.Count() then return
    e = m.groupMap[idx]
    if e.k = "lists" or e.k = "search" or e.k = "locked" then return
    key = e.k
    if e.k = "g" then key = "g" + e.i.ToStr()
    if key = m.curGroupKey then return
    m.curGroupKey = key
    m.status.text = ""
    if e.k = "fav"
        showChannels(savedChannels(getFavs()))
    else if e.k = "recent"
        showChannels(savedChannels(getRecents()))
    else
        g = m.groups[e.i]
        if g.channels <> invalid
            showChannels(g.channels)
        else
            ' categoria de add-on: carrega só quando é escolhida
            m.channelsList.content = invalid
            m.view = []
            m.status.text = "A carregar canais de «" + cleanText(g.title) + "»…"
            m.loadingGroup = e.i
            m.groupTask = startAddonTask({ kind: "tvcatalog", url: g.url }, "onGroupLoaded")
        end if
    end if
end sub

sub onGroupLoaded_x(event as object)
    if m.groupTask = invalid or not event.getRoSGNode().isSameNode(m.groupTask) then return
    res = event.getData()
    i = m.loadingGroup
    if i < 0 or i >= m.groups.Count() then return
    g = m.groups[i]
    g.channels = res.channels
    if g.channels = invalid then g.channels = []
    m.groups[i] = g
    if m.curGroupKey = "g" + i.ToStr()
        if g.channels.Count() = 0
            m.status.text = "Esta categoria não tem canais (ou o add-on não respondeu)."
        else
            m.status.text = ""
        end if
        showChannels(g.channels)
    end if
end sub
sub onGroupLoaded(event as object)
    try
        onGroupLoaded_x(event)
    catch e
        reportCrash("IptvScreen.onGroupLoaded", e)
    end try
end sub

sub showChannels(list as object)
    m.rawView = list
    locked = adultLocked()
    hideOff = cfgRead("hideOffline", "0") = "1"
    view = []
    total = 0
    for each ch in list
        ok = true
        if locked and isAdultText(strOr(ch.name)) then ok = false
        if hideOff and m.offline.DoesExist(strOr(ch.url)) then ok = false
        if ok
            total = total + 1
            ' listas enormes: desenhar milhares de linhas bloqueia a TV — mostra as primeiras 1500
            if view.Count() < 1500 then view.Push(ch)
        end if
    end for
    m.view = view
    if total > view.Count() then m.status.text = "A mostrar " + view.Count().ToStr() + " de " + total.ToStr() + " canais — usa «Pesquisar canal…» para os outros."
    favSet = {}
    for each f in getFavs()
        favSet[strOr(f.u)] = true
    end for
    root = CreateObject("roSGNode", "ContentNode")
    for each ch in view
        c = root.createChild("ContentNode")
        t = cleanText(ch.name)
        if ch.num <> invalid and ch.num > 0 then t = ch.num.ToStr() + "   " + t
        c.title = t
        c.HDPosterUrl = strOr(ch.logo)
        c.description = nowText(ch)
        if favSet.DoesExist(strOr(ch.url)) then c.shortDescriptionLine2 = "fav"
        if m.offline.DoesExist(strOr(ch.url)) then c.shortDescriptionLine1 = "off"
    end for
    m.channelsList.content = root
    m.epgLabel.text = ""
    loadXmlGuideForView()
end sub

' ---------- Guia de programação (EPG) ----------

function hhmm(secs as dynamic) as string
    d = CreateObject("roDateTime")
    d.FromSeconds(Int(numOr(secs, 0)))
    d.ToLocalTime()
    h = d.GetHours()
    mi = d.GetMinutes()
    hs = h.ToStr()
    if h < 10 then hs = "0" + hs
    ms = mi.ToStr()
    if mi < 10 then ms = "0" + ms
    return hs + ":" + ms
end function

' Dados Xtream a partir do URL do canal (/live/utilizador/senha/ID.m3u8)
function xtreamInfo(url as string) as dynamic
    p = Instr(1, url, "/live/")
    if p = 0 then return invalid
    parts = Mid(url, p + 6).Split("/")
    if parts.Count() < 3 then return invalid
    idf = parts[2]
    d = Instr(1, idf, ".")
    if d > 0 then idf = Left(idf, d - 1)
    return { server: Left(url, p - 1), user: parts[0], pass: parts[1], id: idf }
end function

function programmesFor(ch as object) as dynamic
    url = strOr(ch.url)
    if m.epgCache.DoesExist(url) then return m.epgCache[url]
    tid = strOr(ch.tid)
    if tid <> "" and m.xmlGuide.DoesExist(tid) then return m.xmlGuide[tid]
    return invalid
end function

function nowText(ch as object) as string
    progs = programmesFor(ch)
    if progs = invalid then return ""
    now = nowEpoch()
    for i = 0 to progs.Count() - 1
        pr = progs[i]
        if pr.s <= now and pr.e > now
            t = "Agora: " + cleanText(pr.t)
            if i + 1 < progs.Count() then t = t + "   ·   " + hhmm(progs[i + 1].s) + " " + cleanText(progs[i + 1].t)
            return t
        end if
    end for
    return ""
end function

sub loadXmlGuideForView()
    if m.epgUrl = "" or m.curGroupKey = "" or m.xmlLoaded.DoesExist(m.curGroupKey) then return
    ids = []
    for each ch in m.view
        tid = strOr(ch.tid)
        if tid <> "" and ids.Count() < 300 then ids.Push(tid)
    end for
    if ids.Count() = 0 then return
    m.xmlLoaded[m.curGroupKey] = true
    m.xmlTask = startAddonTask({ kind: "epgxml", url: m.epgUrl, ids: ids }, "onXmlGuide")
end sub

sub onXmlGuide_x(event as object)
    res = event.getData()
    if not res.ok
        logEvent("EPG: " + strOr(res.error))
        return
    end if
    for each k in res.guide
        m.xmlGuide[k] = res.guide[k]
    end for
    annotateView()
end sub
sub onXmlGuide(event as object)
    try
        onXmlGuide_x(event)
    catch e
        reportCrash("IptvScreen.onXmlGuide", e)
    end try
end sub

' Atualiza a linha "Agora:" dos canais visíveis sem refazer a lista
sub annotateView()
    root = m.channelsList.content
    if root = invalid then return
    for i = 0 to m.view.Count() - 1
        node = root.getChild(i)
        if node <> invalid then node.description = nowText(m.view[i])
    end for
end sub

sub onChannelFocused_x()
    m.epgTimer.control = "stop"
    m.epgTimer.control = "start"
end sub
sub onChannelFocused()
    try
        onChannelFocused_x()
    catch e
        reportCrash("IptvScreen.onChannelFocused", e)
    end try
end sub

sub updateEpg_x()
    idx = m.channelsList.itemFocused
    if idx < 0 or idx >= m.view.Count() then return
    ch = m.view[idx]
    url = strOr(ch.url)
    if not m.epgCache.DoesExist(url)
        xi = xtreamInfo(url)
        if xi <> invalid
            m.epgFor = url
                        api = xi.server + "/player_api.php?username=" + urlEnc(xi.user) + "&password=" + urlEnc(xi.pass) + "&action=get_short_epg&stream_id=" + xi.id + "&limit=8"
            m.epgTask = startAddonTask({ kind: "epgxtream", url: api }, "onXtreamEpg")
            return
        end if
    end if
    showEpgPanel(ch)
end sub
sub updateEpg()
    try
        updateEpg_x()
    catch e
        reportCrash("IptvScreen.updateEpg", e)
    end try
end sub

sub onXtreamEpg_x(event as object)
    if m.epgTask = invalid or not event.getRoSGNode().isSameNode(m.epgTask) then return
    res = event.getData()
    m.epgCache[m.epgFor] = res.items
    annotateView()
    idx = m.channelsList.itemFocused
    if idx >= 0 and idx < m.view.Count() then showEpgPanel(m.view[idx])
end sub
sub onXtreamEpg(event as object)
    try
        onXtreamEpg_x(event)
    catch e
        reportCrash("IptvScreen.onXtreamEpg", e)
    end try
end sub

sub showEpgPanel(ch as object)
    t = nowText(ch)
    if t = ""
        m.epgLabel.text = ""
    else
        m.epgLabel.text = cleanText(ch.name) + "   —   " + t
    end if
end sub

sub openGuideMenu()
    idx = m.channelsList.itemFocused
    if idx < 0 or idx >= m.view.Count() then return
    ch = m.view[idx]
    progs = programmesFor(ch)
    if progs = invalid or progs.Count() = 0
        showMessage(cleanText(ch.name), "Sem guia de programação para este canal. Funciona com contas Xtream e com listas que tenham guia EPG (XMLTV sem .gz) — podes indicar o URL do guia ao adicionar a lista pelo telemóvel.")
        return
    end if
    items = []
    now = nowEpoch()
    for each pr in progs
        if pr.e > now
            mark = ""
            if pr.s <= now then mark = "AGORA  "
            items.Push(mark + hhmm(pr.s) + "–" + hhmm(pr.e) + "   " + cleanText(pr.t))
        end if
    end for
    m.menuActions = []
    for each x in items
        m.menuActions.Push("none")
    end for
    openMenu(m.menu, "guide", "Programação · " + cleanText(ch.name), items, 0)
end sub

sub onGroupFocused_x()
    showGroupAt(m.groupsList.itemFocused)
end sub
sub onGroupFocused()
    try
        onGroupFocused_x()
    catch e
        reportCrash("IptvScreen.onGroupFocused", e)
    end try
end sub

sub onGroupSelected_x()
    idx = m.groupsList.itemSelected
    e = m.groupMap[idx]
    if e.k = "lists"
        openListsMenu()
    else if e.k = "locked"
        askPin()
    else if e.k = "search"
        askText("Pesquisar canal", "Nome (ou parte do nome) do canal", "", "search")
    else
        showGroupAt(idx)
        if m.view.Count() > 0 then focusOn(m.channelsList)
    end if
end sub
sub onGroupSelected()
    try
        onGroupSelected_x()
    catch e
        reportCrash("IptvScreen.onGroupSelected", e)
    end try
end sub

sub runSearch(q as string)
    ql = LCase(q)
    found = []
    for each g in m.groups
        if g.channels <> invalid
            for each ch in g.channels
                if found.Count() < 300 and Instr(1, LCase(strOr(ch.name)), ql) > 0 then found.Push(ch)
            end for
        end if
    end for
    m.curGroupKey = "search"
    showChannels(found)
    if found.Count() = 0
        m.status.text = "Nenhum canal com «" + q + "» nesta fonte."
        if m.cur >= 0 and m.sources[m.cur].kind = "addon" then m.status.text = m.status.text + Chr(10) + "(Nos add-ons, só procura nas categorias já abertas.)"
    else
        m.status.text = ""
        m.summary.text = found.Count().ToStr() + " canais encontrados para «" + q + "»"
        focusOn(m.channelsList)
    end if
end sub

sub onChannelSelected_x()
    playChannelAt(m.channelsList.itemSelected)
end sub

sub playChannelAt(idx as integer)
    if idx < 0 or idx >= m.view.Count() then return
    ch = m.view[idx]
    addRecent(ch)
    if isVodUrl(strOr(ch.url))
        m.top.playRequest = { url: ch.url, title: ch.name, format: guessStreamFormat(ch.url, ""), headers: [], kind: "vod", subtitles: [], autoSubFind: true, subQuery: ch.name, metaId: "", videoId: "" }
        return
    end if
    chans = []
    for each x in m.view
        ' «agora / a seguir» de cada canal, para o mini-guia do leitor
        e = ""
        if chans.Count() < 400 then e = nowText(x)
        chans.Push({ name: x.name, url: x.url, e: e })
    end for
    m.top.playRequest = {
        url: ch.url,
        title: ch.name,
        format: guessStreamFormat(ch.url, ""),
        headers: [],
        kind: "live",
        channels: chans,
        chIndex: idx,
        epgNow: nowText(ch)
    }
end sub
sub onChannelSelected()
    try
        onChannelSelected_x()
    catch e
        reportCrash("IptvScreen.onChannelSelected", e)
    end try
end sub

sub toggleFavorite()
    idx = m.channelsList.itemFocused
    if idx < 0 or idx >= m.view.Count() then return
    ch = m.view[idx]
    hadFavs = getFavs().Count() > 0
    isFav = toggleFav(ch)
    hasFavs = getFavs().Count() > 0
    node = m.channelsList.content.getChild(idx)
    if node <> invalid
        if isFav
            node.shortDescriptionLine2 = "fav"
        else
            node.shortDescriptionLine2 = ""
        end if
    end if
    g = m.groupsList.itemFocused
    if hasFavs and not hadFavs and g >= 2 then g = g + 1
    if hadFavs and not hasFavs and g > 2 then g = g - 1
    renderGroups(g)
    if m.curGroupKey = "fav"
        showChannels(savedChannels(getFavs()))
        if m.view.Count() = 0
            m.curGroupKey = ""
            focusOn(m.groupsList)
        end if
    end if
end sub

' ---------- Menus ----------

sub openListsMenu()
    m.menuActions = []
    items = []
    for i = 0 to m.sources.Count() - 1
        mark = "   "
        if i = m.cur then mark = "> "
        items.Push(mark + m.sources[i].n)
        m.menuActions.Push("src" + i.ToStr())
    end for
    if m.waitingTv
        items.Push("   (a procurar add-ons de TV…)")
        m.menuActions.Push("none")
    end if
    items.Push("+ Bases públicas gratuitas (iptv-org, Free-TV)")
    m.menuActions.Push("public")
    items.Push("+ Add-ons de TV do Stremio (diretório)")
    m.menuActions.Push("tvdir")
    items.Push("+ Adicionar lista M3U (URL)")
    m.menuActions.Push("m3u")
    items.Push("+ Adicionar conta Xtream Codes")
    m.menuActions.Push("xtream")
    if m.cur >= 0 and m.cur < m.sources.Count()
        items.Push("- Remover a fonte atual")
        m.menuActions.Push("remove")
        items.Push("Recarregar a fonte atual")
        m.menuActions.Push("reload")
    end if
    start = m.cur
    if start < 0 then start = 0
    openMenu(m.menu, "lists", "Listas e add-ons de TV", items, start)
end sub

sub openPublicMenu()
    m.publicSources = publicIptvSources()
    items = []
    for each s in m.publicSources
        items.Push(s.n)
    end for
    openMenu(m.menu, "public", "Bases públicas (canais gratuitos)", items, 0)
end sub

sub openTvDirMenu()
    m.tvDir = tvAddonDirectory()
    items = []
    for each s in m.tvDir
        items.Push(s.n)
    end for
    items.Push("Outro add-on: colar o URL do manifest.json")
    openMenu(m.menu, "tvdir", "Add-ons de TV do Stremio", items, 0)
end sub

sub closeMenu_x()
    m.menu.visible = false
    m.focusTarget.setFocus(true)
end sub
sub closeMenu()
    try
        closeMenu_x()
    catch e
        reportCrash("IptvScreen.closeMenu", e)
    end try
end sub

sub addM3uList(name as string, url as string)
    lists = getIptvLists()
    found = false
    for each l in lists
        if strOr(l.u) = url then found = true
    end for
    if not found
        lists.Push({ n: name, u: url })
        setIptvLists(lists)
    end if
    rebuildSources()
    selectSource(sourceIndex(url))
end sub

sub addTvAddon(url as string)
    base = normalizeAddonUrl(url)
    list = getUserAddons()
    found = false
    for each a in list
        if a = base then found = true
    end for
    if not found
        list.Push(base)
        setUserAddons(list)
    end if
    m.pendingKey = "addon:" + base
    m.waitingTv = true
    m.status.text = "A ler o add-on…"
    scanTvAddons()
end sub

sub removeCurrent()
    src = m.sources[m.cur]
    if src.kind = "m3u"
        out = []
        for each l in getIptvLists()
            if strOr(l.u) <> src.u then out.Push(l)
        end for
        setIptvLists(out)
    else
        mine = getUserAddons()
        out = []
        wasLocal = false
        for each a in mine
            if a = src.base
                wasLocal = true
            else
                out.Push(a)
            end if
        end for
        if not wasLocal
            showMessage("Add-on da conta Stremio", "Este add-on vem da tua conta Stremio. Remove-o na app ou no site do Stremio e ele desaparece daqui na próxima sincronização.")
            return
        end if
        setUserAddons(out)
        tv = []
        for each t in m.tvSources
            if t.base <> src.base then tv.Push(t)
        end for
        m.tvSources = tv
    end if
    rebuildSources()
    m.cur = -1
    if m.sources.Count() > 0
        selectSource(0)
    else
        showEmpty()
    end if
end sub

sub openChannelMenu()
    idx = m.channelsList.itemFocused
    if idx < 0 or idx >= m.view.Count() then return
    ch = m.view[idx]
    m.menuActions = []
    items = []
    isFav = false
    for each f in getFavs()
        if strOr(f.u) = strOr(ch.url) then isFav = true
    end for
    if m.curGroupKey = "fav"
        items.Push("Mover para cima")
        m.menuActions.Push("favUp")
        items.Push("Mover para baixo")
        m.menuActions.Push("favDown")
        items.Push("Mover para o topo")
        m.menuActions.Push("favTop")
    end if
    if isFav
        items.Push("Remover dos favoritos")
    else
        items.Push("Adicionar aos favoritos")
    end if
    m.menuActions.Push("fav")
    items.Push("Guia TV em grelha (todos os canais)")
    m.menuActions.Push("grid")
    items.Push("Ver programação (guia)")
    m.menuActions.Push("guide")
    items.Push("Ir para o canal n.º…")
    m.menuActions.Push("goto")
    items.Push("Verificar canais desta categoria (online/offline)")
    m.menuActions.Push("check")
    hideOff = "Não"
    if cfgRead("hideOffline", "0") = "1" then hideOff = "Sim"
    items.Push("Esconder canais offline: " + hideOff)
    m.menuActions.Push("hideOff")
    items.Push("Fechar")
    m.menuActions.Push("none")
    openMenu(m.menu, "channel", cleanText(ch.name), items, 0)
end sub

sub checkChannels()
    list = m.rawView
    if list = invalid or list.Count() = 0 then return
    urls = []
    m.checkUrls = []
    for each ch in list
        u = strOr(ch.url)
        if LCase(Left(u, 4)) = "http" and urls.Count() < 400
            urls.Push(u)
            m.checkUrls.Push(u)
        end if
    end for
    if urls.Count() = 0
        showMessage("Verificar canais", "Os canais desta fonte vêm de um add-on e não podem ser verificados antecipadamente.")
        return
    end if
    m.status.text = "A verificar " + urls.Count().ToStr() + " canais… (até 25 s)"
    m.checkTask = startAddonTask({ kind: "alive", urls: urls }, "onChecked")
end sub

sub onChecked_x(event as object)
    res = event.getData()
    dead = 0
    for i = 0 to m.checkUrls.Count() - 1
        if res.alive[i] = true
            if m.offline.DoesExist(m.checkUrls[i]) then m.offline.Delete(m.checkUrls[i])
        else
            m.offline[m.checkUrls[i]] = true
            dead = dead + 1
        end if
    end for
    m.status.text = ""
    showChannels(m.rawView)
    showMessage("Verificar canais", dead.ToStr() + " de " + m.checkUrls.Count().ToStr() + " canais não responderam e foram marcados OFF. Podes escondê-los com «Esconder canais offline» no menu *.")
end sub
sub onChecked(event as object)
    try
        onChecked_x(event)
    catch e
        reportCrash("IptvScreen.onChecked", e)
    end try
end sub

sub gotoNumber(n as integer)
    for each g in m.groups
        if g.channels <> invalid
            for i = 0 to g.channels.Count() - 1
                ch = g.channels[i]
                if ch.num <> invalid and ch.num = n
                    ' o número não pode contornar o PIN dos canais adultos
                    if adultLocked() and (isAdultText(strOr(ch.name)) or isAdultText(strOr(g.title)))
                        showMessage("Canal n.º " + n.ToStr(), "Este canal está protegido pelo PIN parental.")
                        return
                    end if
                    chans = []
                    for each x in g.channels
                        e = ""
                        if chans.Count() < 400 then e = nowText(x)
                        chans.Push({ name: x.name, url: x.url, e: e })
                    end for
                    addRecent(ch)
                    m.top.playRequest = { url: ch.url, title: ch.name, format: guessStreamFormat(ch.url, ""), headers: [], kind: "live", channels: chans, chIndex: i, epgNow: nowText(ch) }
                    return
                end if
            end for
        end if
    end for
    showMessage("Canal n.º " + n.ToStr(), "Não há nenhum canal com esse número nesta lista.")
end sub

sub askPin()
    pin = parentalPin()
    if pin = ""
        showMessage("Controlo parental", "Define primeiro um PIN em Definições → Controlo parental.")
        return
    end if
    dlg = CreateObject("roSGNode", "StandardPinPadDialog")
    dlg.title = "Conteúdo protegido"
    dlg.message = ["Introduz o PIN parental"]
    dlg.buttons = ["OK", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onPinDone")
    m.pinDlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onPinDone_x()
    dlg = m.pinDlg
    ok = dlg.buttonSelected = 0 and dlg.pin = parentalPin()
    wrong = dlg.buttonSelected = 0 and not ok
    dlg.close = true
    if ok
        if not m.global.hasField("pinOk") then m.global.addField("pinOk", "boolean", false)
        m.global.pinOk = true
        renderGroups(m.groupsList.itemFocused)
    else if wrong
        showMessage("Controlo parental", "PIN errado.")
    end if
end sub
sub onPinDone()
    try
        onPinDone_x()
    catch e
        reportCrash("IptvScreen.onPinDone", e)
    end try
end sub

sub onMenuSelected_x()
    id = m.menu.menuId
    idx = m.menu.selected
    if id = "guide"
        closeMenu()
        return
    else if id = "channel"
        action = m.menuActions[idx]
        closeMenu()
        ci = m.channelsList.itemFocused
        if action = "fav"
            toggleFavorite()
        else if action = "favUp" or action = "favDown" or action = "favTop"
            if ci >= 0 and ci < m.view.Count()
                moveFav(strOr(m.view[ci].url), action)
                showChannels(savedChannels(getFavs()))
                nf = ci
                if action = "favUp" and ci > 0 then nf = ci - 1
                if action = "favDown" and ci + 1 < m.view.Count() then nf = ci + 1
                if action = "favTop" then nf = 0
                m.channelsList.jumpToItem = nf
            end if
        else if action = "grid"
            openGrid()
        else if action = "guide"
            openGuideMenu()
        else if action = "goto"
            askText("Ir para o canal", "Número do canal", "", "goto")
        else if action = "check"
            checkChannels()
        else if action = "hideOff"
            if cfgRead("hideOffline", "0") = "1"
                cfgWrite("hideOffline", "0")
            else
                cfgWrite("hideOffline", "1")
            end if
            if m.rawView <> invalid then showChannels(m.rawView)
        end if
        return
    end if
    if id = "lists"
        action = m.menuActions[idx]
        if Left(action, 3) = "src"
            closeMenu()
            selectSource(Int(Val(Mid(action, 4))))
        else if action = "public"
            openPublicMenu()
        else if action = "tvdir"
            openTvDirMenu()
        else if action = "m3u"
            closeMenu()
            askText("Adicionar lista M3U", "Cola o URL da lista (http://… ou https://…)", "http://", "m3u")
        else if action = "xtream"
            closeMenu()
            askText("Xtream Codes — servidor", "Endereço do servidor, ex.: http://exemplo.com:8080", "http://", "xserver")
        else if action = "remove"
            closeMenu()
            removeCurrent()
        else if action = "reload"
            closeMenu()
            if m.global.hasField("iptvLast") then m.global.iptvLast = {}
            selectSource(m.cur)
        else
            closeMenu()
        end if
    else if id = "public"
        closeMenu()
        s = m.publicSources[idx]
        addM3uList(s.n, s.u)
    else if id = "tvdir"
        closeMenu()
        if idx < m.tvDir.Count()
            addTvAddon(m.tvDir[idx].u)
        else
            askText("Add-on de TV", "Cola o URL do manifest.json do add-on (https://… ou stremio://…)", "https://", "tvaddon")
        end if
    end if
end sub
sub onMenuSelected()
    try
        onMenuSelected_x()
    catch e
        reportCrash("IptvScreen.onMenuSelected", e)
    end try
end sub

' ---------- Teclado ----------

sub askText(title as string, message as string, text as string, target as string)
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = title
    dlg.message = [message]
    dlg.text = text
    dlg.buttons = ["Continuar", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onAskDone")
    m.askTarget = target
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onAskDone_x()
    dlg = m.dialog
    idx = dlg.buttonSelected
    text = dlg.text.Trim()
    dlg.close = true
    if idx <> 0 or text = "" then return
    t = m.askTarget
    if t = "goto"
        gotoNumber(Int(Val(text)))
    else if t = "search"
        runSearch(text)
    else if t = "m3u"
        if LCase(Left(text, 4)) <> "http"
            showMessage("URL inválido", "O endereço deve começar por http:// ou https://")
            return
        end if
        addM3uList("Lista " + (getIptvLists().Count() + 1).ToStr(), text)
    else if t = "tvaddon"
        if LCase(Left(text, 4)) <> "http" and LCase(Left(text, 10)) <> "stremio://"
            showMessage("URL inválido", "O endereço do add-on deve começar por https:// ou stremio://")
            return
        end if
        addTvAddon(text)
    else if t = "xserver"
        if LCase(Left(text, 4)) <> "http" then text = "http://" + text
        while Right(text, 1) = "/"
            text = Left(text, Len(text) - 1)
        end while
        m.xServer = text
        askText("Xtream Codes — utilizador", "Nome de utilizador da tua conta", "", "xuser")
    else if t = "xuser"
        m.xUser = text
        askText("Xtream Codes — palavra-passe", "Palavra-passe da tua conta", "", "xpass")
    else if t = "xpass"
                url = m.xServer + "/get.php?username=" + urlEnc(m.xUser) + "&password=" + urlEnc(text) + "&type=m3u_plus&output=m3u8"
        addM3uList("Xtream · " + m.xUser, url)
    end if
end sub
sub onAskDone()
    try
        onAskDone_x()
    catch e
        reportCrash("IptvScreen.onAskDone", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    if m.gridOn = true then return gridKey(key)
    if key = "options"
        if m.channelsList.hasFocus()
            openChannelMenu()
        else
            openListsMenu()
        end if
        return true
    end if
    if key = "right" and m.groupsList.hasFocus() and m.view.Count() > 0
        focusOn(m.channelsList)
        return true
    else if key = "left" and m.channelsList.hasFocus()
        focusOn(m.groupsList)
        return true
    else if key = "back" and m.channelsList.hasFocus()
        ' Voltar na lista de canais vai primeiro às categorias (não sai logo da TV ao vivo)
        focusOn(m.groupsList)
        return true
    else if key = "play" and m.channelsList.hasFocus()
        playChannelAt(m.channelsList.itemFocused)
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("IptvScreen.onKeyEvent", e)
    end try
    return handled
end function

' ---------- Guia TV em grelha (canais × horas) ----------

sub openGrid()
    if m.view = invalid or m.view.Count() = 0 then return
    loadXmlGuideForView()
    m.gridOn = true
    m.gridRow = m.channelsList.itemFocused
    if m.gridRow < 0 then m.gridRow = 0
    m.gridTop = m.gridRow - 3
    if m.gridTop < 0 then m.gridTop = 0
    now = nowEpoch()
    m.gridStart = now - (now mod 1800)
    m.gridCol = -1
    m.gridAtTime = now
    if m.grid <> invalid then m.top.removeChild(m.grid)
    m.grid = CreateObject("roSGNode", "Group")
    m.top.appendChild(m.grid)
    renderGrid()
    m.top.setFocus(true)
end sub

sub closeGrid()
    m.gridOn = false
    if m.grid <> invalid then m.top.removeChild(m.grid)
    m.grid = invalid
    focusOn(m.channelsList)
end sub

function gridProgs(ch as object) as object
    out = []
    progs = programmesFor(ch)
    if progs = invalid then return out
    wEnd = m.gridStart + 3 * 3600
    for each pr in progs
        if pr.e > m.gridStart and pr.s < wEnd then out.Push(pr)
    end for
    return out
end function

function gridRect(parent as object, x as integer, y as integer, w as integer, h as integer, color as string) as object
    r = parent.createChild("Rectangle")
    r.translation = [x, y]
    r.width = w
    r.height = h
    r.color = color
    return r
end function

function gridLabel(parent as object, x as integer, y as integer, w as integer, h as integer, text as string, color as string, font as string) as object
    l = parent.createChild("Label")
    l.translation = [x, y]
    l.width = w
    l.height = h
    l.text = text
    l.color = color
    l.font = font
    l.wrap = true
    l.maxLines = 2
    l.vertAlign = "center"
    return l
end function

sub renderGrid()
    g = m.grid
    if g = invalid then return
    while g.getChildCount() > 0
        g.removeChildIndex(0)
    end while
    accent = accentColor()
    gridRect(g, 0, 0, 1920, 1080, "0x0B0B0FF5")
    gridLabel(g, 60, 40, 600, 60, "Guia TV", accent, "font:LargeBoldSystemFont")
    gridLabel(g, 380, 40, 1200, 60, hhmm(m.gridStart) + " – " + hhmm(m.gridStart + 3 * 3600) + "      ◀ ▶ mudar de programa / hora   ·   OK ver   ·   ↩ voltar", "0x9C9CABFF", "font:SmallSystemFont")
    x0 = 380
    tw = 1480
    for i = 0 to 5
        gridLabel(g, x0 + i * Int(tw / 6), 120, 200, 40, hhmm(m.gridStart + i * 1800), "0x9C9CABFF", "font:SmallestSystemFont")
    end for
    rows = 8
    rh = 100
    y0 = 170
    if m.gridRow < m.gridTop then m.gridTop = m.gridRow
    if m.gridRow >= m.gridTop + rows then m.gridTop = m.gridRow - rows + 1
    now = nowEpoch()
    wEnd = m.gridStart + 3 * 3600
    for r = 0 to rows - 1
        idx = m.gridTop + r
        if idx >= m.view.Count() then exit for
        ch = m.view[idx]
        y = y0 + r * rh
        sel = (idx = m.gridRow)
        nameColor = "0xF2F2F5FF"
        if sel then nameColor = accent
        gridLabel(g, 60, y, 300, rh - 8, cleanText(ch.name), nameColor, "font:SmallSystemFont")
        progs = gridProgs(ch)
        if sel
            ' coluna escolhida = programa que passa na hora em foco
            if m.gridCol < 0 or m.gridCol >= progs.Count()
                m.gridCol = 0
                for j = 0 to progs.Count() - 1
                    if progs[j].s <= m.gridAtTime and progs[j].e > m.gridAtTime then m.gridCol = j
                end for
            end if
        end if
        if progs.Count() = 0
            bg = "0x16161DFF"
            if sel then gridRect(g, x0 - 3, y - 3, tw + 6, rh - 2, "0xFFFFFFFF")
            gridRect(g, x0, y, tw, rh - 8, bg)
            gridLabel(g, x0 + 16, y, tw - 32, rh - 8, "Sem guia — OK para ver o canal", "0x9C9CABFF", "font:SmallSystemFont")
        else
            for j = 0 to progs.Count() - 1
                pr = progs[j]
                s = pr.s
                if s < m.gridStart then s = m.gridStart
                e = pr.e
                if e > wEnd then e = wEnd
                bx = x0 + Int((s - m.gridStart) * tw / (3 * 3600))
                bw = Int((e - s) * tw / (3 * 3600)) - 6
                if bw < 8 then bw = 8
                live = pr.s <= now and pr.e > now
                color = "0x16161DFF"
                if live then color = "0x5A1A24FF"
                if sel and j = m.gridCol then gridRect(g, bx - 3, y - 3, bw + 6, rh - 2, "0xFFFFFFFF")
                gridRect(g, bx, y, bw, rh - 8, color)
                if bw > 40 then gridLabel(g, bx + 12, y, bw - 20, rh - 8, cleanText(pr.t) + Chr(10) + hhmm(pr.s), "0xF2F2F5FF", "font:SmallestSystemFont")
            end for
        end if
    end for
    if now >= m.gridStart and now < wEnd
        nx = x0 + Int((now - m.gridStart) * tw / (3 * 3600))
        gridRect(g, nx, y0 - 10, 3, rows * rh, accent)
    end if
end sub

function gridKey(key as string) as boolean
    if key = "back" or key = "options"
        closeGrid()
        return true
    end if
    ch = m.view[m.gridRow]
    progs = gridProgs(ch)
    if key = "up" or key = "down"
        if key = "up" and m.gridRow > 0 then m.gridRow = m.gridRow - 1
        if key = "down" and m.gridRow + 1 < m.view.Count() then m.gridRow = m.gridRow + 1
        m.gridCol = -1
        renderGrid()
        return true
    end if
    if key = "right"
        if m.gridCol + 1 < progs.Count()
            m.gridCol = m.gridCol + 1
            m.gridAtTime = progs[m.gridCol].s + 60
        else if m.gridStart < nowEpoch() + 4 * 3600
            m.gridStart = m.gridStart + 3600
            m.gridAtTime = m.gridStart + 60
            m.gridCol = -1
        end if
        renderGrid()
        return true
    end if
    if key = "left"
        if m.gridCol > 0
            m.gridCol = m.gridCol - 1
            m.gridAtTime = progs[m.gridCol].s + 60
        else if m.gridStart > nowEpoch() - 2 * 3600
            m.gridStart = m.gridStart - 3600
            m.gridAtTime = m.gridStart + 3 * 3600 - 60
            m.gridCol = -1
        end if
        renderGrid()
        return true
    end if
    if key = "OK"
        now = nowEpoch()
        pr = invalid
        if m.gridCol >= 0 and m.gridCol < progs.Count() then pr = progs[m.gridCol]
        if pr = invalid or (pr.s <= now and pr.e > now)
            row = m.gridRow
            closeGrid()
            playChannelAt(row)
        else
            dlg = CreateObject("roSGNode", "StandardMessageDialog")
            dlg.title = cleanText(pr.t)
            info = cleanText(ch.name) + "   ·   " + hhmm(pr.s) + " – " + hhmm(pr.e)
            if pr.e <= now then info = info + Chr(10) + "Este programa já terminou."
            if strOr(pr.d) <> "" then info = info + Chr(10) + Chr(10) + cleanText(pr.d)
            dlg.message = [info]
            if pr.s > now
                dlg.buttons = ["Lembrar-me quando começar", "Fechar"]
                m.gridDlgPr = { u: strOr(ch.url), c: cleanText(ch.name), t: cleanText(pr.t), s: pr.s }
            else
                dlg.buttons = ["OK"]
                m.gridDlgPr = invalid
            end if
            dlg.observeFieldScoped("buttonSelected", "onGridDialogClose")
            m.top.getScene().dialog = dlg
        end if
        return true
    end if
    return true
end function

sub onGridDialogClose()
    try
        dlg = m.top.getScene().dialog
        idx = -1
        if dlg <> invalid
            idx = dlg.buttonSelected
            dlg.close = true
        end if
        if idx = 0 and m.gridDlgPr <> invalid
            addReminder(m.gridDlgPr)
            m.gridDlgPr = invalid
            showMessage("Lembrete", "Avisamos-te 1 minuto antes, com a Hortêncioflix aberta.")
        end if
        m.top.setFocus(true)
    catch e
        reportCrash("IptvScreen.onGridDialogClose", e)
    end try
end sub
