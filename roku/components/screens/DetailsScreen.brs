' Detalhes: destaque com logótipo, botões (Reproduzir/Continuar, Episódios, Fontes,
' Biblioteca), "Mais como este", temporadas/episódios e lista de fontes.

sub init_x()
    m.prefetching = false
    m.preId = ""
    m.preAt = 0
    m.backdrop = m.top.findNode("backdrop")
    m.logoImg = m.top.findNode("logoImg")
    m.title = m.top.findNode("title")
    m.info = m.top.findNode("info")
    m.desc = m.top.findNode("desc")
    m.people = m.top.findNode("people")
    m.buttons = m.top.findNode("buttons")
    m.more = m.top.findNode("more")
    m.section = m.top.findNode("section")
    m.seasonsList = m.top.findNode("seasons")
    m.epList = m.top.findNode("episodes")
    m.streamList = m.top.findNode("streams")
    m.spinner = m.top.findNode("spinner")
    m.status = m.top.findNode("status")
    m.hint = m.top.findNode("hint")
    m.menu = m.top.findNode("menu")
    m.bgFade = m.top.findNode("bgFade")
    m.bgDrift = m.top.findNode("bgDrift")
    m.backdrop.scaleRotateCenter = [960, 540]

    m.section.color = accentColor()

    m.backdrop.observeField("loadStatus", "onBgLoaded")
    m.logoImg.observeField("loadStatus", "onLogoLoaded")
    m.buttons.observeField("buttonSelected", "onButton")
    m.more.observeField("rowItemSelected", "onMoreSelected")
    m.seasonsList.observeField("itemFocused", "onSeasonFocused")
    m.seasonsList.observeField("itemSelected", "onSeasonSelected")
    m.epList.observeField("itemFocused", "onEpisodeFocused")
    m.epList.observeField("itemSelected", "onEpisodeSelected")
    m.streamList.observeField("itemSelected", "onStreamSelected")
    m.menu.observeField("selected", "onMenuSelected")
    m.menu.observeField("cancelled", "closeMenu")
    m.top.observeField("focusedChild", "onFocusedChild")

    m.mode = "overview"
    m.seasons = []
    m.flatEps = []
    m.curSeason = -1
    m.startSeason = 0
    m.epFocus = 0
    m.streams = []
    m.subs = []
    m.subsDone = false
    m.streamsDone = false
    m.quick = false
    m.currentEp = invalid
    m.targetEp = invalid
    m.fromEpisodes = false
    m.posterUrl = ""
    m.metaName = ""
    m.focusTarget = m.buttons
    setStatus("")
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("DetailsScreen.init", e)
    end try
end sub

' ---------- Foco ----------

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
        reportCrash("DetailsScreen.onFocusedChild", e)
    end try
end sub

sub focusOn(node as object)
    m.focusTarget = node
    if not m.menu.visible and screenIsActive() then node.setFocus(true)
end sub

sub setStatus(text as string)
    m.status.text = text
    m.status.visible = (text <> "")
end sub

sub showSpinner(spinOn as boolean, text as string)
    m.spinner.visible = spinOn
    if spinOn
        m.spinner.control = "start"
        m.status.translation = [190, 535]
    else
        m.spinner.control = "stop"
        m.status.translation = [100, 520]
    end if
    setStatus(text)
end sub

' Mostra só os elementos do modo atual
sub setMode(mode as string)
    m.mode = mode
    overview = (mode = "overview")
    m.desc.visible = overview
    m.people.visible = overview
    m.buttons.visible = overview
    m.more.visible = overview and m.more.content <> invalid
    m.section.visible = not overview
    m.seasonsList.visible = (mode = "episodes")
    m.epList.visible = (mode = "episodes")
    m.streamList.visible = (mode = "streams")
    if mode <> "loading" then showSpinner(false, "")
    if overview
        m.hint.text = "OK escolher   ·   Baixo: Mais como este   ·   * opções   ·   Voltar"
    else if mode = "episodes"
        m.hint.text = "OK: escolher fonte   ·   Play: reproduzir já (melhor fonte)   ·   Esquerda: temporadas   ·   Voltar"
    else
        m.hint.text = "OK reproduzir   ·   * recarregar fontes   ·   Voltar"
    end if
end sub

' ---------- Dados ----------

sub onItem_x()
    it = m.top.item
    m.metaName = strOr(it.name)
    m.title.text = m.metaName
    m.posterUrl = strOr(it.poster)
    bg = strOr(it.bg)
    if bg = "" then bg = metahubBg(strOr(it.id))
    if bg = "" then bg = m.posterUrl
    m.backdrop.uri = bg
    logo = strOr(it.logo)
    if logo = "" then logo = metahubLogo(strOr(it.id))
    m.logoImg.uri = logo
    setMode("overview")
    m.buttons.buttons = ["A carregar…"]
    focusOn(m.buttons)
    m.metaTask = startAddonTask({ kind: "meta", type: it.type, id: it.id }, "onMeta")
end sub
sub onItem()
    try
        onItem_x()
    catch e
        reportCrash("DetailsScreen.onItem", e)
    end try
end sub

sub onBgLoaded_x()
    if m.backdrop.loadStatus = "ready"
        m.bgFade.control = "start"
        if cfgRead("trailerAuto", "1") = "1" then m.bgDrift.control = "start"
    end if
end sub
sub onBgLoaded()
    try
        onBgLoaded_x()
    catch e
        reportCrash("DetailsScreen.onBgLoaded", e)
    end try
end sub

sub onLogoLoaded_x()
    if m.logoImg.loadStatus = "ready"
        bw = m.logoImg.bitmapWidth
        bh = m.logoImg.bitmapHeight
        if bw > 0 and bh > 0
            sc = 700 / bw
            if 190 / bh < sc then sc = 190 / bh
            m.logoImg.width = Int(bw * sc)
            m.logoImg.height = Int(bh * sc)
            m.logoImg.translation = [100, 90 + Int((190 - bh * sc) / 2)]
        end if
        m.logoImg.visible = true
        m.title.visible = false
    else if m.logoImg.loadStatus = "failed"
        m.logoImg.visible = false
        m.title.visible = true
    end if
end sub
sub onLogoLoaded()
    try
        onLogoLoaded_x()
    catch e
        reportCrash("DetailsScreen.onLogoLoaded", e)
    end try
end sub

function joinNames(arr as dynamic, maxN as integer) as string
    out = ""
    n = 0
    if arr = invalid or type(arr) <> "roArray" then return ""
    for each x in arr
        s = ""
        if type(x) = "roAssociativeArray"
            s = strOr(x.name)
        else
            s = strOr(x)
        end if
        if s <> "" and n < maxN
            if out <> "" then out = out + ", "
            out = out + s
            n = n + 1
        end if
    end for
    return out
end function

sub onMeta_x(event as object)
    res = event.getData()
    storeManifests(res)
    it = m.top.item
    m.meta = invalid
    if res.ok
        meta = res.meta
        m.meta = meta
        if strOr(meta.name) <> ""
            m.metaName = meta.name
            m.title.text = meta.name
        end if
        if strOr(meta.poster) <> "" then m.posterUrl = meta.poster
        if strOr(meta.background) <> "" and strOr(it.bg) = "" then m.backdrop.uri = meta.background
        if strOr(meta.logo) <> "" and strOr(it.logo) = "" then m.logoImg.uri = meta.logo
        parts = []
        if strOr(meta.releaseInfo) <> "" then parts.Push(strOr(meta.releaseInfo))
        if strOr(meta.runtime) <> "" then parts.Push(strOr(meta.runtime))
        if strOr(meta.imdbRating) <> "" then parts.Push("IMDb " + strOr(meta.imdbRating))
        g = joinNames(meta.genres, 4)
        if g <> "" then parts.Push(g)
        if libInLibrary(strOr(it.id)) then parts.Push("Na biblioteca")
        ' filmes: a que horas acaba se começar agora (contando com o que já foi visto)
        if strOr(it.type) = "movie"
            done = 0.0
            for each pr in getProgress()
                if strOr(pr.m) = strOr(it.id) and numOr(pr.d, 0) > 0 then done = numOr(pr.t, 0) / numOr(pr.d, 1)
            end for
            ea = endsAtText(runtimeMinutes(strOr(meta.runtime)), done)
            if ea <> "" then parts.Push(ea)
        end if
        m.infoParts = parts
        m.origName = strOr(meta.name)
        renderInfo(invalid)
        m.desc.text = cleanText(meta.description)
        ' sinopse e título em português (TMDB / Trakt) e classificação etária, guardados para a próxima vez
        if ptTranslationsOn() and Left(strOr(it.id), 2) = "tt"
            pt = ptCacheGet(strOr(it.id))
            if pt <> invalid
                if strOr(pt.desc) <> "" then m.desc.text = cleanText(pt.desc)
                renderInfo(pt)
            else
                m.ptTask = ptTranslateTask(strOr(it.id), strOr(it.type), "onDetailsPt")
            end if
        end if
        ppl = ""
        cast = joinNames(meta.cast, 5)
        dir = joinNames(meta.director, 2)
        if cast <> "" then ppl = "Elenco: " + cast
        if dir <> ""
            if ppl <> "" then ppl = ppl + Chr(10)
            ppl = ppl + "Realização: " + dir
        end if
        m.people.text = ppl
        m.castNames = []
        for each lst in [meta.cast, meta.director, meta.writer]
            if lst <> invalid and type(lst) = "roArray"
                for each x in lst
                    nm = ""
                    if type(x) = "roAssociativeArray"
                        nm = strOr(x.name)
                    else
                        nm = strOr(x)
                    end if
                    if nm <> "" and m.castNames.Count() < 15 then m.castNames.Push(nm)
                end for
            end if
        end for
        loadMoreLikeThis(meta)
        ' coleção / saga (precisa da chave TMDB)
        m.colRow = invalid
        if strOr(it.type) = "movie" and cfgRead("tmdbKey", "") <> ""
            m.colTask = startAddonTask({ kind: "collection", id: strOr(it.id), tmdbKey: cfgRead("tmdbKey", "") }, "onCollection")
        end if
    end if
    if it.type = "series" and res.ok and res.meta.videos <> invalid and type(res.meta.videos) = "roArray" and res.meta.videos.Count() > 0
        buildSeasons(res.meta.videos)
        m.targetEp = findTargetEpisode()
    end if
    renderButtons()
    ' procura já as fontes do que «Ver» vai abrir, para arrancar sem espera
    if it.type = "movie" or m.targetEp <> invalid then prefetch(m.targetEp)
end sub

function nowSecs() as integer
    return CreateObject("roDateTime").AsSeconds()
end function

sub prefetch(ep as dynamic)
    it = m.top.item
    id = it.id
    if ep <> invalid then id = ep.id
    if m.preId = id and nowSecs() - m.preAt < 600 then return
    m.preId = id
    m.preAt = nowSecs()
    m.prefetching = true
    m.quick = false
    fetchStreams(ep)
end sub
sub onMeta(event as object)
    try
        onMeta_x(event)
    catch e
        reportCrash("DetailsScreen.onMeta", e)
    end try
end sub

sub loadMoreLikeThis(meta as object)
    it = m.top.item
    t = strOr(it.type)
    if t <> "movie" and t <> "series" then return
    if meta.genres = invalid or type(meta.genres) <> "roArray" or meta.genres.Count() = 0 then return
    g = strOr(meta.genres[0])
    if g = "" then return
    url = cinemetaUrl() + "/catalog/" + t + "/top/genre=" + urlEnc(g) + ".json"
    m.moreTask = startAddonTask({ kind: "catalogurl", url: url, exclude: strOr(it.id) }, "onMore")
end sub

sub onMore_x(event as object)
    res = event.getData()
    if res.items = invalid or res.items.Count() = 0 then return
    m.moreRow = { title: "Mais como este", items: res.items }
    renderMoreRows()
end sub

' Filas por baixo dos botões: a coleção (se houver) e «Mais como este»
sub renderMoreRows()
    rows = []
    if m.colRow <> invalid then rows.Push(m.colRow)
    if m.moreRow <> invalid then rows.Push(m.moreRow)
    if rows.Count() = 0 then return
    m.more.content = buildRowContent(rows)
    if m.mode = "overview" then m.more.visible = true
end sub

sub onCollection(event as object)
    try
        res = event.getData()
        if res = invalid or res.ok <> true then return
        m.colRow = { title: "Coleção: " + strOr(res.title), items: res.items }
        renderMoreRows()
    catch e
        reportCrash("DetailsScreen.onCollection", e)
    end try
end sub
sub onMore(event as object)
    try
        onMore_x(event)
    catch e
        reportCrash("DetailsScreen.onMore", e)
    end try
end sub

sub onMoreSelected_x()
    item = itemFromRowList(m.more)
    if item <> invalid then m.top.itemSelected = item
end sub
sub onMoreSelected()
    try
        onMoreSelected_x()
    catch e
        reportCrash("DetailsScreen.onMoreSelected", e)
    end try
end sub

' Episódio a reproduzir em "Continuar"/"Reproduzir" numa série
function findTargetEpisode() as dynamic
    it = m.top.item
    vid = strOr(it.videoId)
    if vid = ""
        for each x in getProgress()
            if strOr(x.m) = strOr(it.id) then vid = strOr(x.v)
        end for
    end if
    afterFinished = false
    if vid = ""
        lib = libFind(strOr(it.id))
        if lib <> invalid and lib.state <> invalid
            vid = strOr(lib.state.video_id)
            if numOr(lib.state.timeOffset, 0) = 0 then afterFinished = true
        end if
    end if
    if vid <> ""
        for i = 0 to m.flatEps.Count() - 1
            if m.flatEps[i].id = vid
                if afterFinished and i + 1 < m.flatEps.Count() then return m.flatEps[i + 1]
                return m.flatEps[i]
            end if
        end for
    end if
    ' sem progresso nem conta: continua a seguir ao último episódio marcado como visto neste aparelho
    seen = watchedFor(strOr(it.id))
    last = -1
    for i = 0 to m.flatEps.Count() - 1
        ep = m.flatEps[i]
        if seen.DoesExist(ep.s.ToStr() + ":" + ep.e.ToStr()) then last = i
    end for
    if last >= 0 and last + 1 < m.flatEps.Count() then return m.flatEps[last + 1]
    if m.flatEps.Count() > 0 then return m.flatEps[0]
    return invalid
end function

sub renderButtons()
    it = m.top.item
    labels = []
    m.btnActions = []
    resumeId = strOr(it.id)
    if m.targetEp <> invalid then resumeId = m.targetEp.id
    resume = findResume(resumeId, strOr(it.id))
    if it.type = "series" and m.targetEp <> invalid
        if resume > 30 or m.targetEp.id <> m.flatEps[0].id
            labels.Push("Continuar " + m.targetEp.label)
        else
            labels.Push("Reproduzir " + m.targetEp.label)
        end if
        m.btnActions.Push("play")
        labels.Push("Episódios")
        m.btnActions.Push("episodes")
        labels.Push("Escolher fonte")
        m.btnActions.Push("sources")
    else
        if resume > 30
            labels.Push("Continuar (" + formatTime(resume) + ")")
        else
            labels.Push("Reproduzir")
        end if
        m.btnActions.Push("play")
        labels.Push("Escolher fonte")
        m.btnActions.Push("sources")
    end if
    if m.people.text <> ""
        labels.Push("Elenco")
        m.btnActions.Push("cast")
    end if
    if stremioLinked()
        if libInLibrary(strOr(it.id))
            labels.Push("Remover da biblioteca")
            m.btnActions.Push("libRemove")
        else
            labels.Push("+ Biblioteca")
            m.btnActions.Push("libAdd")
        end if
    end if
    m.buttons.buttons = labels
    if m.mode = "overview" then focusOn(m.buttons)
    maybeAutoPlay()
end sub

' «Enviar para a TV» (a partir do telemóvel): começa logo a reproduzir, com o episódio pedido
sub maybeAutoPlay()
    it = m.top.item
    if it = invalid or boolOr(m.autoPlayDone) then return
    if not boolOr(it.autoPlay) then return
    m.autoPlayDone = true
    vid = strOr(it.autoVideo)
    ep = m.targetEp
    if vid <> "" and m.seasons <> invalid
        for each sn in m.seasons
            for each e in sn.eps
                if e.id = vid then ep = e
            end for
        end for
    end if
    startQuick(ep)
end sub

sub onRefresh_x()
    if m.meta <> invalid then renderButtons()
end sub
sub onRefresh()
    try
        onRefresh_x()
    catch e
        reportCrash("DetailsScreen.onRefresh", e)
    end try
end sub

sub onButton_x()
    if m.btnActions = invalid then return
    action = m.btnActions[m.buttons.buttonSelected]
    it = m.top.item
    if action = "play"
        startQuick(m.targetEp)
    else if action = "episodes"
        showEpisodes()
    else if action = "sources"
        m.fromEpisodes = false
        loadStreams(m.targetEp)
    else if action = "cast"
        openCastMenu()
    else if action = "libAdd" or action = "libRemove"
        change = "add"
        if action = "libRemove" then change = "remove"
        json = libraryItemJson(it.id, it.type, m.metaName, m.posterUrl, change, "", 0, 0)
        m.top.pushJson = json
        libApplyLocal(ParseJson(json))
        renderButtons()
    end if
end sub
sub onButton()
    try
        onButton_x()
    catch e
        reportCrash("DetailsScreen.onButton", e)
    end try
end sub

' ---------- Temporadas e episódios ----------

sub buildSeasons(videos as object)
    bySeason = {}
    order = []
    for each v in videos
        if strOr(v.id) <> ""
            s = Int(numOr(v.season, 0))
            e = Int(numOr(v.episode, numOr(v.number, 0)))
            name = strOr(v.name)
            if name = "" then name = strOr(v.title)
            key = s.ToStr()
            if not bySeason.DoesExist(key)
                bySeason[key] = []
                order.Push({ s: s, sk: s })
            end if
            bySeason[key].Push({ id: v.id, s: s, e: e, sk: e, label: "T" + s.ToStr() + " E" + e.ToStr(), name: cleanText(name) })
        end if
    end for
    for each o in order
        if o.s = 0 then o.sk = 99999
    end for
    order.SortBy("sk")
    m.seasons = []
    m.flatEps = []
    for each o in order
        eps = bySeason[o.s.ToStr()]
        eps.SortBy("sk")
        title = "Temporada " + o.s.ToStr()
        if o.s = 0 then title = "Especiais"
        m.seasons.Push({ s: o.s, title: title, eps: eps })
        if o.s > 0 then m.flatEps.Append(eps)
    end for
    labels = []
    for each s in m.seasons
        labels.Push(s.title)
    end for
    m.seasonsList.content = labelContent(labels)
end sub

sub showEpisodes()
    if m.seasons.Count() = 0 then return
    setMode("episodes")
    m.section.text = "Episódios"
    if m.curSeason < 0
        ' começa na temporada/episódio do "Continuar"
        si = 0
        ei = 0
        if m.targetEp <> invalid
            for a = 0 to m.seasons.Count() - 1
                for b = 0 to m.seasons[a].eps.Count() - 1
                    if m.seasons[a].eps[b].id = m.targetEp.id
                        si = a
                        ei = b
                    end if
                end for
            end for
        end if
        fillSeason(si)
        m.seasonsList.jumpToItem = si
        m.epFocus = ei
        if ei > 0 then m.epList.jumpToItem = ei
    end if
    focusOn(m.epList)
end sub

sub fillSeason(idx as integer)
    if idx < 0 or idx >= m.seasons.Count() or idx = m.curSeason then return
    m.curSeason = idx
    labels = []
    seen = watchedSet()
    requestSeasonPt(idx)
    ptNames = invalid
    if m.epPt <> invalid and m.seasons[idx].eps.Count() > 0 then ptNames = m.epPt[m.seasons[idx].eps[0].s.ToStr()]
    for each ep in m.seasons[idx].eps
        t = "E" + ep.e.ToStr()
        nm = ep.name
        if ptNames <> invalid
            if ptNames.DoesExist(ep.e.ToStr()) then nm = ptNames[ep.e.ToStr()]
        end if
        if nm <> "" then t = t + "   " + nm
        if seen.DoesExist(ep.s.ToStr() + ":" + ep.e.ToStr()) then t = t + "      · visto"
        labels.Push(t)
    end for
    m.epList.content = labelContent(labels)
end sub

' Episódios vistos: marcados localmente + anteriores ao último visto na conta Stremio
function watchedSet() as object
    it = m.top.item
    set = watchedFor(strOr(it.id))
    lib = libFind(strOr(it.id))
    if lib <> invalid and lib.state <> invalid
        vid = strOr(lib.state.video_id)
        finished = numOr(lib.state.timeOffset, 0) = 0
        for i = 0 to m.flatEps.Count() - 1
            ep = m.flatEps[i]
            if ep.id = vid
                if finished then set[ep.s.ToStr() + ":" + ep.e.ToStr()] = true
                exit for
            end if
            set[ep.s.ToStr() + ":" + ep.e.ToStr()] = true
        end for
    end if
    return set
end function

sub refreshEpisodes()
    if m.curSeason < 0 then return
    keep = m.curSeason
    focus = m.epList.itemFocused
    m.curSeason = -1
    fillSeason(keep)
    m.epList.jumpToItem = focus
end sub

sub openCastMenu()
    if m.castNames = invalid or m.castNames.Count() = 0 then return
    m.menuActions = []
    for each n in m.castNames
        m.menuActions.Push("person")
    end for
    openMenu(m.menu, "cast", "Elenco e equipa — escolhe para ver a filmografia", m.castNames, 0)
end sub

sub onSeasonFocused_x()
    if m.mode = "episodes"
        fillSeason(m.seasonsList.itemFocused)
    end if
end sub
sub onSeasonFocused()
    try
        onSeasonFocused_x()
    catch e
        reportCrash("DetailsScreen.onSeasonFocused", e)
    end try
end sub

sub onSeasonSelected_x()
    fillSeason(m.seasonsList.itemSelected)
    focusOn(m.epList)
end sub
sub onSeasonSelected()
    try
        onSeasonSelected_x()
    catch e
        reportCrash("DetailsScreen.onSeasonSelected", e)
    end try
end sub

sub onEpisodeFocused_x()
    m.epFocus = m.epList.itemFocused
end sub
sub onEpisodeFocused()
    try
        onEpisodeFocused_x()
    catch e
        reportCrash("DetailsScreen.onEpisodeFocused", e)
    end try
end sub

function focusedEpisode() as dynamic
    if m.curSeason < 0 then return invalid
    eps = m.seasons[m.curSeason].eps
    i = m.epList.itemFocused
    if i < 0 or i >= eps.Count() then return invalid
    return eps[i]
end function

sub onEpisodeSelected_x()
    ep = focusedEpisode()
    if ep = invalid then return
    m.fromEpisodes = true
    loadStreams(ep)
end sub
sub onEpisodeSelected()
    try
        onEpisodeSelected_x()
    catch e
        reportCrash("DetailsScreen.onEpisodeSelected", e)
    end try
end sub

' ---------- Fontes ----------

sub fetchStreams(ep as dynamic)
    it = m.top.item
    m.currentEp = ep
    id = it.id
    if ep <> invalid then id = ep.id
    m.streams = []
    m.subs = []
    m.subsDone = false
    m.streamsDone = false
    m.streamList.content = invalid
    m.streamTask = startAddonTask({ kind: "streams", type: it.type, id: id, animated: isAnimatedMeta(m.meta) }, "onStreams")
    m.subTask = startAddonTask({ kind: "subtitles", type: it.type, id: id }, "onSubs")
end sub

function epLabel(ep as dynamic) as string
    if ep = invalid then return ""
    t = " — " + ep.label
    if ep.name <> "" then t = t + " · " + ep.name
    return t
end function

sub loadStreams(ep as dynamic)
    m.quick = false
    m.prefetching = false
    setMode("loading")
    m.sectionBase = "Fontes" + epLabel(ep)
    m.section.text = m.sectionBase
    m.section.visible = true
    showSpinner(true, "A procurar fontes nos add-ons…")
    fetchStreams(ep)
end sub

' Reprodução rápida: escolhe sozinho a melhor fonte (em cache, compatível, idioma preferido)
sub startQuick(ep as dynamic)
    it = m.top.item
    id = it.id
    if ep <> invalid then id = ep.id
    reuse = m.prefetching and m.preId = id and nowSecs() - m.preAt < 600
    m.prefetching = false
    m.quick = true
    ' as legendas não podem atrasar o «Ver» mais de 5 s (o leitor procura-as depois, se faltarem)
    if m.subWait = invalid
        m.subWait = CreateObject("roSGNode", "Timer")
        m.subWait.duration = 5
        m.subWait.repeat = false
        m.subWait.observeField("fire", "onQuickSubWait")
        m.top.appendChild(m.subWait)
    end if
    m.subWait.duration = 5
    m.subWait.control = "stop"
    m.subWait.control = "start"
    setMode("loading")
    m.section.text = "Reproduzir" + epLabel(ep)
    m.section.visible = true
    showSpinner(true, "A preparar o vídeo…")
    if reuse
        ' as fontes já foram (ou estão a ser) procuradas ao abrir o título
        m.currentEp = ep
        if m.streamsDone then quickFromResult(m.lastRes)
        return
    end if
    fetchStreams(ep)
end sub

sub onSubs_x(event as object)
    if m.subTask = invalid or not event.getRoSGNode().isSameNode(m.subTask) then return
    res = event.getData()
    if res.subtitles <> invalid then m.subs = res.subtitles
    m.subsDone = true
    if m.quick and m.streamsDone then quickLaunch()
end sub
sub onSubs(event as object)
    try
        onSubs_x(event)
    catch e
        reportCrash("DetailsScreen.onSubs", e)
    end try
end sub

sub onStreams_x(event as object)
    if m.streamTask = invalid or not event.getRoSGNode().isSameNode(m.streamTask) then return
    res = event.getData()
    storeManifests(res)
    m.streams = res.streams
    if m.streams = invalid then m.streams = []
    m.allStreams = m.streams
    m.sortMode = ""
    m.fltNotice = ""
    applyPinnedFilters()
    if m.streams = invalid then m.streams = []
    m.streamsDone = true
    m.lastRes = res
    if m.prefetching and not m.quick then return
    if m.quick
        quickFromResult(res)
        return
    end if
    showStreamList(res)
end sub

sub quickFromResult(res as object)
    if m.quick
        if m.streams.Count() = 0
            m.quick = false
            showStreamList(res)
        else if m.streams[0].cached = false
            ' nenhuma fonte pronta no debrid: não tocar o vídeo «downloading to debrid»
            m.quick = false
            showStreamList(res)
            setStatus("Ainda nenhuma fonte está pronta no teu serviço debrid. Escolhe uma (download) para começar a descarregar e volta daqui a alguns minutos.")
        else if m.subsDone
            quickLaunch()
        else
            ' fontes prontas, legendas ainda não: espera no máximo mais 2,5 s
            m.subWait.duration = 2.5
            m.subWait.control = "stop"
            m.subWait.control = "start"
        end if
    end if
end sub
sub onStreams(event as object)
    try
        onStreams_x(event)
    catch e
        reportCrash("DetailsScreen.onStreams", e)
    end try
end sub

sub quickLaunch()
    m.quick = false
    setMode("overview")
    focusOn(m.buttons)
    playStream(0)
end sub

sub showStreamList(res as object)
    setMode("streams")
    if m.streams.Count() = 0
        msg = "Nenhuma fonte reproduzível encontrada."
        if numOr(res.filtered, 0) > 0 then msg = msg + Chr(10) + Int(res.filtered).ToStr() + " fontes escondidas pelos filtros (qualidade, cache, DV/AV1) — muda em Definições."
        if numOr(res.skipped, 0) > 0 then msg = msg + Chr(10) + Int(res.skipped).ToStr() + " fontes ignoradas (torrent direto ou links externos)."
        if cfgRead("torboxKey", "") = "" then msg = msg + Chr(10) + "Dica: define a chave TorBox em Definições."
        setStatus(msg)
        return
    end if
    setStatus("")
    if strOr(m.fltNotice) <> "" then setStatus(m.fltNotice)
    extra = ""
    if numOr(res.filtered, 0) > 0 then extra = "   ·   " + Int(res.filtered).ToStr() + " escondidas pelos filtros"
    cnt = m.streams.Count().ToStr()
    if m.allStreams <> invalid
        if m.allStreams.Count() <> m.streams.Count() then cnt = cnt + " de " + m.allStreams.Count().ToStr()
    end if
    fd = ""
    if m.flt <> invalid then fd = fltDescribe(m.flt)
    if fd <> "" then extra = "   ·   " + fd + extra
    m.section.text = m.sectionBase + "  (" + cnt + ")" + extra
    root = CreateObject("roSGNode", "ContentNode")
    for each s in m.streams
        c = root.createChild("ContentNode")
        head = cleanText(s.name)
        if head = "" then head = cleanText(s.addon)
        if s.dub = "br" then head = "[Dublado PT-BR]  " + head
        if s.dub = "pt" then head = "[Dobrado PT-PT]  " + head
        if s.dub = "por" then head = "[Áudio PT]  " + head
        r = strOr(s.range)
        if r = "dvhdr" then head = "[DV+HDR10]  " + head
        if r = "dv"
            ' Dolby Vision sem HDR10 por baixo: numa TV sem DV fica com cores erradas
            if m.devCaps = invalid then m.devCaps = deviceCaps()
            if fltBool(m.devCaps.dv) then head = "[Dolby Vision]  " + head else head = "[Dolby Vision ⚠ cores erradas nesta TV]  " + head
        end if
        if r = "hdr10p" then head = "[HDR10+]  " + head
        if r = "hdr10" or r = "hdr" then head = "[HDR10]  " + head
        if r = "hlg" then head = "[HLG]  " + head
        if strOr(s.src) = "remux" then head = "[Remux]  " + head
        if strOr(s.src) = "cam" then head = "[CAM/TS]  " + head
        file = ""
        rest = ""
        lines = strOr(s.title).Split(Chr(10))
        if lines.Count() > 0 then file = cleanText(lines[0])
        for i = 1 to lines.Count() - 1
            t = cleanText(lines[i])
            if t <> ""
                if rest <> "" then rest = rest + "   "
                rest = rest + t
            end if
        end for
        if rest <> "" then head = head + "     " + rest
        if file = "" then file = cleanText(s.filename)
        c.title = head
        c.description = file
        q = ""
        if s.q >= 2160
            q = "4K"
        else if s.q > 0
            q = s.q.ToStr() + "p"
        end if
        c.shortDescriptionLine1 = q
        if s.risky = true
            c.shortDescriptionLine2 = "risky"
        else if s.cached
            c.shortDescriptionLine2 = "cached"
        else
            c.shortDescriptionLine2 = ""
        end if
    end for
    m.streamList.content = root
    focusOn(m.streamList)
end sub

sub onStreamSelected_x()
    sel = m.streamList.itemSelected
    if sel >= 0 and sel < m.streams.Count()
        s = m.streams[sel]
        if s.cached = false and cfgRead("torboxKey", "") <> "" and torrentHash(strOr(s.url)) <> ""
            ' fonte ainda por descarregar: perguntar antes de tocar o vídeo de espera
            m.pendingSel = sel
            dlg = CreateObject("roSGNode", "StandardMessageDialog")
            dlg.title = "Esta fonte ainda não está no TorBox"
            dlg.message = ["Se a abrires agora aparece só um vídeo de espera. Queres que o TorBox a descarregue? Avisamos-te aqui na TV quando estiver pronta (com a app aberta)."]
            dlg.buttons = ["Descarregar e avisar-me", "Abrir mesmo assim", "Cancelar"]
            dlg.observeFieldScoped("buttonSelected", "onQueueAsk")
            m.top.getScene().dialog = dlg
            return
        end if
    end if
    playStream(sel)
end sub

sub onQueueAsk()
    try
        dlg = m.top.getScene().dialog
        idx = dlg.buttonSelected
        dlg.close = true
        sel = m.pendingSel
        if idx = 1 then playStream(sel)
        if idx = 0 and sel >= 0 and sel < m.streams.Count()
            s = m.streams[sel]
            m.queueHash = torrentHash(strOr(s.url))
            m.queueTitle = strOr(m.metaName)
            setStatus("A enviar para o TorBox…")
            m.queueTask = startAddonTask({ kind: "tbqueue", key: cfgRead("torboxKey", ""), hash: m.queueHash }, "onQueued")
        end if
    catch e
        reportCrash("DetailsScreen.onQueueAsk", e)
    end try
end sub

sub onQueued(event as object)
    try
        if m.queueTask = invalid or not event.getRoSGNode().isSameNode(m.queueTask) then return
        res = event.getData()
        if res.ok
            tbWatch(m.queueHash, m.queueTitle)
            setStatus(strOr(res.msg) + " Avisamos-te quando «" + m.queueTitle + "» estiver pronto.")
        else
            setStatus(strOr(res.msg))
        end if
    catch e
        reportCrash("DetailsScreen.onQueued", e)
    end try
end sub
sub onStreamSelected()
    try
        onStreamSelected_x()
    catch e
        reportCrash("DetailsScreen.onStreamSelected", e)
    end try
end sub

sub playStream(sel as integer)
    if sel < 0 or sel >= m.streams.Count() then return
    s = m.streams[sel]
    it = m.top.item
    title = m.metaName
    videoId = it.id
    epIndex = -1
    if m.currentEp <> invalid
        title = title + " — " + m.currentEp.label
        videoId = m.currentEp.id
        for i = 0 to m.flatEps.Count() - 1
            if m.flatEps[i].id = videoId then epIndex = i
        end for
    end if
    eps = []
    for each ep in m.flatEps
        eps.Push({ id: ep.id, label: ep.label })
    end for
    ' alternativas se a escolhida falhar: primeiro as da lista (filtrada) a seguir, depois as de cima,
    ' e por fim as que os filtros esconderam — nunca fica sem reserva por ter escolhido a última
    alts = [altOf(s)]
    used = {}
    used[strOr(s.url)] = true
    order = []
    for i = sel + 1 to m.streams.Count() - 1
        order.Push(m.streams[i])
    end for
    for i = 0 to sel - 1
        order.Push(m.streams[i])
    end for
    if m.allStreams <> invalid then order.Append(m.allStreams)
    for each x in order
        u = strOr(x.url)
        if alts.Count() < 30 and not used.DoesExist(u)
            used[u] = true
            alts.Push(altOf(x))
        end if
    end for
    m.top.playRequest = {
        url: s.url,
        title: title,
        format: guessStreamFormat(s.url, s.filename + " " + s.title),
        headers: s.headers,
        kind: "vod",
        subtitles: m.subs,
        metaId: it.id,
        metaType: it.type,
        metaName: m.metaName,
        poster: m.posterUrl,
        videoId: videoId,
        streamName: s.name,
        episodes: eps,
        epIndex: epIndex,
        alts: alts,
        altIndex: 0,
        animated: isAnimatedMeta(m.meta),
        subsId: subsIdIfMissing(videoId)
    }
end sub

' ---------- Menu * ----------

sub openOptions(focus = 0 as integer)
    m.menuActions = []
    items = []
    if m.flt = invalid then m.flt = {}
    if m.mode = "streams"
        items.Push("Recarregar fontes")
        m.menuActions.Push("reload")
        ' Filtros: cada linha percorre só os valores que existem neste título (nunca deixa a lista vazia)
        for each g in fltGroups()
            opts = fltOptions(g)
            cur = fltStr(m.flt[g])
            if opts.Count() > 0 or cur <> ""
                items.Push(fltGroupName(g) + ": " + fltCurrentLabel(g))
                m.menuActions.Push("flt:" + g)
            end if
        end for
        if m.allStreams <> invalid and m.allStreams.Count() > 1
            sl = "a melhor primeiro"
            if m.sortMode = "big" then sl = "maior ficheiro primeiro"
            if m.sortMode = "small" then sl = "mais leve primeiro"
            items.Push("Ordenar: " + sl)
            m.menuActions.Push("sort")
        end if
        if m.flt <> invalid and m.flt.Count() > 0
            items.Push("Limpar filtros (ver todas)")
            m.menuActions.Push("fltClear")
            if FormatJson(m.flt) = cfgRead("pinFilters", "")
                items.Push("Filtros fixados para todos os títulos ✓ (escolher para soltar)")
            else
                items.Push("Fixar estes filtros para todos os títulos")
            end if
            m.menuActions.Push("fltPin")
        else if cfgRead("pinFilters", "") <> ""
            items.Push("Soltar os filtros fixos (" + fltDescribe(pinnedFilters()) + ")")
            m.menuActions.Push("fltPin")
        end if
    end if
    ' listas pessoais (por perfil)
    it0 = m.top.item
    if it0 <> invalid and strOr(it0.id) <> ""
        items.Push("Adicionar a uma lista…")
        m.menuActions.Push("listAdd")
        for each ln in listsContaining(strOr(it0.id))
            items.Push("Tirar da lista «" + ln + "»")
            m.menuActions.Push("listDel:" + ln)
        end for
    end if
    if m.seasons.Count() > 0 and m.mode <> "episodes"
        items.Push("Ver episódios")
        m.menuActions.Push("episodes")
    end if
    if m.mode = "episodes"
        items.Push("Reproduzir este episódio (melhor fonte)")
        m.menuActions.Push("quickEp")
        ep = focusedEpisode()
        if ep <> invalid
            if watchedSet().DoesExist(ep.s.ToStr() + ":" + ep.e.ToStr())
                items.Push("Marcar como não visto")
                m.menuActions.Push("unwatched")
            else
                items.Push("Marcar como visto")
                m.menuActions.Push("watched")
            end if
            items.Push("Marcar a temporada toda como vista")
            m.menuActions.Push("seasonWatched")
        end if
    end if
    if not stremioLinked()
        items.Push("Liga a conta Stremio em Definições para usar a biblioteca")
        m.menuActions.Push("none")
    end if
    items.Push("Fechar")
    m.menuActions.Push("none")
    if focus >= items.Count() then focus = 0
    openMenu(m.menu, "options", m.metaName, items, focus)
end sub

sub closeMenu_x()
    m.menu.visible = false
    m.focusTarget.setFocus(true)
end sub
sub closeMenu()
    try
        closeMenu_x()
    catch e
        reportCrash("DetailsScreen.closeMenu", e)
    end try
end sub

sub onMenuSelected_x()
    if m.menu.menuId = "cast"
        name = m.castNames[m.menu.selected]
        closeMenu()
        m.top.personSelected = name
        return
    end if
    action = m.menuActions[m.menu.selected]
    closeMenu()
    it = m.top.item
    if action = "watched" or action = "unwatched"
        ep = focusedEpisode()
        if ep <> invalid then setWatched(strOr(it.id), ep.s, ep.e, action = "watched")
        refreshEpisodes()
        return
    else if action = "seasonWatched" and m.curSeason >= 0
        for each ep in m.seasons[m.curSeason].eps
            setWatched(strOr(it.id), ep.s, ep.e, true)
        end for
        refreshEpisodes()
        return
    end if
    if Left(action, 4) = "flt:" or action = "sort" or action = "fltClear" or action = "fltPin"
        if m.allStreams = invalid then m.allStreams = m.streams
        if m.flt = invalid then m.flt = {}
        sel = m.menu.selected
        m.fltNotice = ""
        if Left(action, 4) = "flt:"
            g = Mid(action, 5)
            nv = nextOf(fltStr(m.flt[g]), fltCycle(g))
            if nv = "" then m.flt.Delete(g) else m.flt[g] = nv
        else if action = "sort"
            m.sortMode = nextOf(m.sortMode, ["", "big", "small"])
        else if action = "fltClear"
            m.flt = {}
            m.sortMode = ""
        else if action = "fltPin"
            cur = FormatJson(m.flt)
            if m.flt.Count() = 0 or cur = cfgRead("pinFilters", "")
                cfgWrite("pinFilters", "")
                m.fltNotice = "Filtros soltos: cada título volta a mostrar todas as fontes."
            else
                cfgWrite("pinFilters", cur)
                m.fltNotice = "Filtros fixados para todos os títulos: " + fltDescribe(m.flt) + ". O «Ver» também os segue."
            end if
        end if
        applyStreamFilters()
        if Left(action, 4) = "flt:" or action = "sort" then openOptions(sel)
        return
    end if
    if action = "listAdd"
        openListPicker()
        return
    end if
    if Left(action, 8) = "listDel:"
        listRemove(Mid(action, 9), strOr(it.id))
        setStatus("Tirado da lista «" + Mid(action, 9) + "».")
        return
    end if
    if Left(action, 8) = "listPut:"
        listAdd(Mid(action, 9), { id: strOr(it.id), type: strOr(it.type), name: m.metaName })
        setStatus("Adicionado à lista «" + Mid(action, 9) + "».")
        return
    end if
    if action = "listNew"
        askNewList()
        return
    end if
    if action = "reload"
        loadStreams(m.currentEp)
    else if action = "episodes"
        showEpisodes()
    else if action = "quickEp"
        ep = focusedEpisode()
        if ep <> invalid then startQuick(ep)
    end if
end sub
sub onMenuSelected()
    try
        onMenuSelected_x()
    catch e
        reportCrash("DetailsScreen.onMenuSelected", e)
    end try
end sub

sub cancelLoading()
    if m.streamTask <> invalid then m.streamTask.unobserveField("result")
    if m.subTask <> invalid then m.subTask.unobserveField("result")
    m.streamTask = invalid
    m.subTask = invalid
    m.quick = false
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    if key = "options"
        openOptions()
        return true
    end if
    if m.mode = "overview"
        if key = "down" and m.buttons.isInFocusChain() and m.more.visible
            focusOn(m.more)
            return true
        else if key = "up" and m.more.isInFocusChain()
            focusOn(m.buttons)
            return true
        else if key = "back" and m.more.isInFocusChain()
            ' Voltar nas sugestões leva primeiro aos botões (não sai logo da página)
            focusOn(m.buttons)
            return true
        else if key = "play" and m.buttons.isInFocusChain()
            ' tecla ▶ do comando: começa já (a melhor fonte)
            startQuick(m.targetEp)
            return true
        end if
        return false
    end if
    if m.mode = "episodes"
        if key = "left" and m.epList.hasFocus()
            focusOn(m.seasonsList)
            return true
        else if key = "right" and m.seasonsList.hasFocus()
            focusOn(m.epList)
            return true
        else if key = "play"
            ep = focusedEpisode()
            if ep <> invalid then startQuick(ep)
            return true
        else if key = "back"
            setMode("overview")
            focusOn(m.buttons)
            return true
        end if
    end if
    if key = "back" and (m.mode = "streams" or m.mode = "loading")
        cancelLoading()
        if m.fromEpisodes and m.seasons.Count() > 0
            m.fromEpisodes = false
            setMode("episodes")
            m.section.text = "Episódios"
            focusOn(m.epList)
        else
            setMode("overview")
            focusOn(m.buttons)
        end if
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("DetailsScreen.onKeyEvent", e)
    end try
    return handled
end function

function dubMatchesUi(dub as string, pref as string) as boolean
    if dub = "" then return false
    return dub = pref or dub = "por"
end function

function nextOf(cur as string, values as object) as string
    for k = 0 to values.Count() - 1
        if values[k] = cur
            if k + 1 < values.Count() then return values[k + 1]
            return values[0]
        end if
    end for
    return values[0]
end function

' ---------- Filtros das fontes (lógica em utils/Filters.brs, igual ao Android) ----------

function fltGroups() as object
    return ["q", "img", "src", "nocam", "lang", "aud", "cached"]
end function

function fltValues(g as string) as object
    if g = "q" then return ["4K", "1440p", "1080p", "720p", "SD"]
    if g = "img" then return ["anyhdr", "dv", "hdr10p", "hdr10", "hlg", "sdr"]
    if g = "src" then return ["remux", "bluray", "web"]
    if g = "lang" then return ["br", "pt"]
    if g = "aud" then return ["atmos", "lossless", "51"]
    return ["1"]
end function

function fltGroupName(g as string) as string
    if g = "q" then return "Qualidade"
    if g = "img" then return "Imagem"
    if g = "src" then return "Origem"
    if g = "nocam" then return "Esconder CAM/TS"
    if g = "lang" then return "Idioma do áudio"
    if g = "aud" then return "Som"
    if g = "cached" then return "Só prontas (em cache)"
    return g
end function

function fltCount(g as string, v as string) as integer
    if m.devCaps = invalid then m.devCaps = deviceCaps()
    n = 0
    for each s in m.allStreams
        if fltPasses(s, m.flt, g, m.devCaps)
            if v = "" or fltFacet(s, g, v, m.devCaps) then n = n + 1
        end if
    end for
    return n
end function

' Valores deste grupo que mudam alguma coisa: existem (contando com os outros filtros) e não são todas
function fltOptions(g as string) as object
    out = []
    if m.allStreams = invalid then return out
    base = fltCount(g, "")
    cur = ""
    if m.flt <> invalid then cur = fltStr(m.flt[g])
    for each v in fltValues(g)
        n = fltCount(g, v)
        if n > 0 and (n < base or v = cur) then out.Push(v)
    end for
    return out
end function

function fltCycle(g as string) as object
    c = [""]
    c.Append(fltOptions(g))
    return c
end function

function fltCurrentLabel(g as string) as string
    cur = ""
    if m.flt <> invalid then cur = fltStr(m.flt[g])
    if cur = ""
        if g = "nocam" or g = "cached" then return "não"
        return "todas (" + fltCount(g, "").ToStr() + ")"
    end if
    if g = "nocam" or g = "cached" then return "sim (" + fltCount(g, cur).ToStr() + ")"
    lb = fltValueLabel(g, cur)
    if g = "img"
        if m.devCaps = invalid then m.devCaps = deviceCaps()
        if cur = "dv" and not fltBool(m.devCaps.dv) then lb = lb + " ⚠ esta TV não tem DV"
        if cur <> "sdr" and not fltBool(m.devCaps.hdr) and not fltBool(m.devCaps.dv) then lb = lb + " ⚠ TV sem HDR"
    end if
    return "só " + lb + " (" + fltCount(g, cur).ToStr() + ")"
end function

' Ao abrir um título: aplica os filtros fixados; se não houver nenhuma fonte pronta assim, mostra todas e avisa
sub applyPinnedFilters()
    m.flt = {}
    pin = pinnedFilters()
    if pin.Count() = 0 then return
    if m.devCaps = invalid then m.devCaps = deviceCaps()
    anyReady = false
    readyOk = false
    out = []
    for each s in m.allStreams
        if fltBool(s.cached) then anyReady = true
        if fltPasses(s, pin, "", m.devCaps)
            out.Push(s)
            if fltBool(s.cached) then readyOk = true
        end if
    end for
    if out.Count() = 0 or (anyReady and not readyOk)
        m.fltNotice = "Sem fontes «" + fltDescribe(pin) + "» prontas neste título — a mostrar todas."
        return
    end if
    m.flt = pin
    m.streams = out
end sub

' Filtros combinados (todos os grupos têm de bater) + ordenação
sub applyStreamFilters()
    if m.devCaps = invalid then m.devCaps = deviceCaps()
    out = []
    for each s in m.allStreams
        if fltPasses(s, m.flt, "", m.devCaps) then out.Push(s)
    end for
    if out.Count() = 0
        ' (não devia acontecer: os menus só oferecem combinações que existem)
        m.flt = {}
        out = m.allStreams
        m.fltNotice = "Nenhuma fonte com esses filtros — a mostrar todas."
    end if
    if m.sortMode = "big" or m.sortMode = "small"
        ' dentro de cada qualidade, por tamanho do ficheiro
        sorted = []
        for each s in out
            sorted.Push({ s: s, k: fltSortKey(s) })
        end for
        if m.sortMode = "big" then sorted.SortBy("k", "r") else sorted.SortBy("k")
        out = []
        for each x in sorted
            out.Push(x.s)
        end for
    end if
    m.streams = out
    showStreamList(m.lastRes)
    if m.fltNotice <> "" then setStatus(m.fltNotice)
end sub

' Chave de ordenação: qualidade primeiro (sempre da maior para a menor), depois o tamanho
function fltSortKey(s as object) as float
    q = Int(numOr(s.q, 0))
    gb = numOr(s.gb, 0)
    if gb > 999 then gb = 999
    if m.sortMode = "small"
        ' crescente: qualidade mais alta tem de vir primeiro → inverte a qualidade
        if gb <= 0 then gb = 999
        return (10000 - q) * 1000 + gb
    end if
    return q * 1000 + gb
end function

sub onDetailsPt(event as object)
    try
        res = event.getData()
        it = m.top.item
        if it = invalid then return
        if res <> invalid and res.ok = true
            pt = { name: strOr(res.name), desc: strOr(res.desc), cert: strOr(res.cert) }
            ptCacheSet(strOr(it.id), pt)
            if pt.desc <> "" then m.desc.text = cleanText(pt.desc)
            renderInfo(pt)
        else
            ptCacheSet(strOr(it.id), { name: "", desc: "" })
        end if
    catch e
        reportCrash("DetailsScreen.onDetailsPt", e)
    end try
end sub

' Linha de informação: título em português (se for diferente do original), classificação etária e o resto
sub renderInfo(pt as dynamic)
    parts = []
    if pt <> invalid
        nm = strOr(pt.name)
        if nm <> "" and LCase(nm) <> LCase(strOr(m.origName)) then parts.Push(nm)
        if strOr(pt.cert) <> "" then parts.Push(strOr(pt.cert))
    end if
    if m.infoParts <> invalid then parts.Append(m.infoParts)
    info = ""
    for each p in parts
        if info <> "" then info = info + "   ·   "
        info = info + p
    end for
    m.info.text = info
end sub

' Nomes dos episódios em português (por temporada, guardados)
sub requestSeasonPt(idx as integer)
    if not ptTranslationsOn() then return
    it = m.top.item
    id = strOr(it.id)
    if Left(id, 2) <> "tt" or idx < 0 or idx >= m.seasons.Count() then return
    if m.seasons[idx].eps.Count() = 0 then return
    sn = m.seasons[idx].eps[0].s
    key = id + "#s" + sn.ToStr()
    if m.epPt = invalid then m.epPt = {}
    if m.epPt.DoesExist(sn.ToStr()) then return
    c = ptCacheGet(key)
    if c <> invalid
        m.epPt[sn.ToStr()] = c
        return
    end if
    m.epPtFor = { key: key, s: sn.ToStr(), idx: idx }
    m.epPtTask = ptSeasonTask(id, sn, "onSeasonPt")
end sub

sub onSeasonPt(event as object)
    try
        res = event.getData()
        f = m.epPtFor
        if f = invalid then return
        names = {}
        if res <> invalid and res.ok = true and res.names <> invalid then names = res.names
        ptCacheSet(f.key, names)
        if m.epPt = invalid then m.epPt = {}
        m.epPt[f.s] = names
        if m.curSeason = f.idx and names.Count() > 0 then refreshEpisodes()
    catch e
        reportCrash("DetailsScreen.onSeasonPt", e)
    end try
end sub

sub openListPicker()
    m.menuActions = []
    items = []
    for each n in listNames()
        items.Push(n)
        m.menuActions.Push("listPut:" + n)
    end for
    items.Push("+ Nova lista…")
    m.menuActions.Push("listNew")
    openMenu(m.menu, "options", "Adicionar a uma lista", items, 0)
end sub

sub askNewList()
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = "Nova lista"
    dlg.message = ["Nome da lista (ex.: Fim de semana, Ver com a família)"]
    dlg.buttons = ["Criar e adicionar", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onNewList")
    m.listDlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onNewList()
    try
        dlg = m.listDlg
        idx = dlg.buttonSelected
        name = dlg.text.Trim()
        dlg.close = true
        if idx <> 0 or name = "" then return
        if Len(name) > 30 then name = Left(name, 30)
        it = m.top.item
        listAdd(name, { id: strOr(it.id), type: strOr(it.type), name: m.metaName })
        setStatus("Lista «" + name + "» criada com este título.")
    catch e
        reportCrash("DetailsScreen.onNewList", e)
    end try
end sub

sub onQuickSubWait()
    try
        if m.quick and m.streamsDone and not m.subsDone
            m.subsDone = true
            m.subsLate = true
            quickLaunch()
        end if
    catch e
        reportCrash("DetailsScreen.onQuickSubWait", e)
    end try
end sub

' Sem legendas ainda (demoraram): o leitor procura-as sozinho e junta-as quando chegarem
function subsIdIfMissing(videoId as string) as string
    if m.subs <> invalid and type(m.subs) = "roArray" and m.subs.Count() > 0 then return ""
    if Left(videoId, 2) <> "tt" then return ""
    return videoId
end function
