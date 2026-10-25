' Leitor: legendas (OpenSubtitles + embutidas, sincronização), áudio, faixas por série,
' retomar, teste do link antes de tocar, fontes alternativas, saltar introdução (IntroDB),
' contagem para o próximo episódio, informação técnica, temporizador, mini-leitor e IPTV.

sub init_x()
    m.rebuf = []
    m.lastJumpAt = 0
    m.prevPos = 0
    m.scored = false
    m.delayApplied = ""
    m.guardOn = false
    m.qualResume = 0
    m.video = m.top.findNode("video")
    m.status = m.top.findNode("status")
    m.osd = m.top.findNode("osd")
    m.osdTitle = m.top.findNode("osdTitle")
    m.osdHint = m.top.findNode("osdHint")
    m.osdTimer = m.top.findNode("osdTimer")
    m.menu = m.top.findNode("menu")
    m.bg = m.top.findNode("bg")
    m.miniFrame = m.top.findNode("miniFrame")
    m.miniLabel = m.top.findNode("miniLabel")
    m.skipGroup = m.top.findNode("skipGroup")
    m.skipBtn = m.top.findNode("skipBtn")
    m.nextCard = m.top.findNode("nextCard")
    m.nextLabel = m.top.findNode("nextLabel")
    m.nextCount = m.top.findNode("nextCount")
    m.nextBtns = m.top.findNode("nextBtns")
    m.postPlay = m.top.findNode("postPlay")
    m.postHero = m.top.findNode("postHero")
    m.postTitle = m.top.findNode("postTitle")
    m.postBtns = m.top.findNode("postBtns")
    m.postGrid = m.top.findNode("postGrid")
    m.chGuide = m.top.findNode("chGuide")
    m.cgTimer = m.top.findNode("cgTimer")
    m.cgTimer.observeField("fire", "hideChGuide")
    m.postTimer = m.top.findNode("postTimer")
    m.postTimer.observeField("fire", "onPostTick")
    m.postBtns.observeField("buttonSelected", "onPostButton")
    m.postGrid.observeField("itemSelected", "onPostGrid")
    m.postGrid.observeField("itemFocused", "onPostGridFocus")
    m.top.findNode("postHead").color = accentColor()
    m.recs = invalid
    m.recsFor = ""
    m.postDone = false
    m.countTimer = m.top.findNode("countTimer")
    m.sleepTimer = m.top.findNode("sleepTimer")
    m.graceTimer = m.top.findNode("graceTimer")
    m.subWaitTimer = m.top.findNode("subWaitTimer")
    m.startDog = m.top.findNode("startDog")
    m.startDog.observeField("fire", "onStartDog")
    m.stallDog = m.top.findNode("stallDog")
    m.stallDog.observeField("fire", "onStallDog")
    m.netTimer = m.top.findNode("netTimer")
    m.netTimer.observeField("fire", "onNetTimer")
    m.subWaitTimer.observeField("fire", "onSubWait")

    accent = accentColor()
    m.top.findNode("miniBorder").color = accent
    m.top.findNode("nextBar").color = accent
    m.nextBtns.buttons = ["Ver agora", "Cancelar"]

    m.video.observeField("state", "onState")
    m.video.observeField("position", "onPosition")
    m.video.observeField("availableSubtitleTracks", "onSubtitleTracks")
    m.video.observeField("availableAudioTracks", "onAudioTracks")
    m.osdTimer.observeField("fire", "hideOsd")
    m.countTimer.observeField("fire", "onCountdown")
    m.sleepTimer.observeField("fire", "onSleep")
    m.graceTimer.observeField("fire", "onGrace")
    m.skipBtn.observeField("buttonSelected", "onSkipPressed")
    m.nextBtns.observeField("buttonSelected", "onNextButton")
    m.menu.observeField("selected", "onMenuSelected")
    m.menu.observeField("cancelled", "onMenuCancelled")
    m.top.observeField("focusedChild", "onFocusedChild")

    m.md = {}
    m.attempting = false
    m.autoTries = 0
    m.fromRetry = false
    m.subCacheFor = ""
    m.subCache = []
    m.curTracks = []
    m.started = false
    m.lastSave = 0
    m.lastPos = 0
    m.pushed = false
    m.sleepMins = 0
    resetEpisodeState()
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("PlayerScreen.init", e)
    end try
end sub

sub resetEpisodeState()
    m.segments = invalid
    m.skipKind = ""
    m.skipTarget = 0
    m.skipDismissed = {}
    m.nextShown = false
    m.nextDismissed = false
    m.noAutoNext = false
    m.subOffset = 0
    m.subBasePath = ""
    m.subBaseIdx = -1
    m.forceTrack = ""
    m.autoSubDone = false
    m.autoAudioDone = false
end sub

' ---------- Utilitários ----------

sub onFocusedChild_x()
    if m.top.hasFocus() and not isMini()
        if m.menu.visible
            m.menu.setFocus(true)
        else if m.nextCard.visible
            m.nextBtns.setFocus(true)
        else if m.skipGroup.visible
            m.skipBtn.setFocus(true)
        else
            m.video.setFocus(true)
        end if
    end if
end sub
sub onFocusedChild()
    try
        onFocusedChild_x()
    catch e
        reportCrash("PlayerScreen.onFocusedChild", e)
    end try
end sub

sub focusVideo()
    if not isMini() then m.video.setFocus(true)
end sub

function isMini() as boolean
    return m.top.mode = "mini"
end function

function isLive() as boolean
    return strOr(m.md.kind) = "live"
end function

function hasNextEpisode() as boolean
    eps = m.md.episodes
    if eps = invalid or type(eps) <> "roArray" then return false
    i = Int(numOr(m.md.epIndex, -1))
    return i >= 0 and i + 1 < eps.Count()
end function

function nextEpisodeLabel() as string
    if not hasNextEpisode() then return ""
    return m.md.episodes[Int(m.md.epIndex) + 1].label
end function

sub showOsd(title as string)
    updateMiniLabel()
    if isMini() then return
    m.osdTitle.text = title
    if isLive()
        m.osdHint.text = "Cima/Baixo: mudar de canal   ·   * opções (áudio, legendas, temporizador)   ·   Voltar: minimizar"
    else
        m.osdHint.text = "* ou Baixo: opções (legendas, sincronizar, áudio, outra fonte, info)   ·   Voltar: minimizar"
    end if
    m.osd.visible = true
    m.osdTimer.control = "stop"
    m.osdTimer.control = "start"
end sub

sub hideOsd_x()
    m.osd.visible = false
end sub
sub hideOsd()
    try
        hideOsd_x()
    catch e
        reportCrash("PlayerScreen.hideOsd", e)
    end try
end sub

sub setStatus(text as string)
    m.status.text = text
    m.status.visible = (text <> "")
end sub

' ---------- Arranque ----------

sub onMedia_x()
    ' guarda o progresso do que estava a tocar antes de mudar
    if m.started and not isLive()
        saveLocal(false)
        pushStremio(false)
    end if
    m.md = m.top.media
    m.attempting = false
    if not m.fromRetry
        m.autoTries = 0
        m.addonFails = {}
        m.qdropDeclined = false
        resetEpisodeState()
        ' «A seguir»: cada título novo volta a poder mostrar sugestões (e nunca as do filme anterior)
        m.postDone = false
        if m.recsFor <> strOr(m.md.metaId) then m.recs = invalid
        if m.postPlay.visible then hidePostPlay()
        m.chGuide.visible = false
    end if
    m.fromRetry = false
    m.started = false
    m.autoSubDone = false
    m.autoAudioDone = false
    m.forceTrack = ""
    m.subOffset = 0
    m.subBaseIdx = -1
    m.subBasePath = ""
    m.lastSave = 0
    m.lastPos = 0
    m.pushed = false
    hideSkip()
    hideNextCard()
    m.video.control = "stop"
    m.video.content = invalid
    ttl = strOr(m.md.title)
    if strOr(m.md.epgNow) <> "" then ttl = ttl + "   ·   " + strOr(m.md.epgNow)
    showOsd(ttl)
    ' canal de um add-on Stremio: obter primeiro o link da emissão
    sref = parseStremioUrl(strOr(m.md.url))
    if sref <> invalid
        setStatus("A obter a emissão…")
        m.resolveTask = startAddonTask({ kind: "streams", type: sref.type, id: sref.id }, "onResolved")
        return
    end if
    if isLive()
        startPlayback(0, [])
        showChGuide()
        return
    end if
    loadSegments()
    qr = Int(numOr(m.qualResume, 0))
    if qr > 0
        m.qualResume = 0
        prepare(qr)
        return
    end if
    resume = findResume(strOr(m.md.videoId), strOr(m.md.metaId))
    if resume > 30 and boolOr(m.md.autoResume)
        ' a app voltou ao que estavas a ver: continua no mesmo ponto sem perguntar
        m.md.autoResume = false
        prepare(resume)
    else if resume > 30 and isMini()
        prepare(resume)
    else if resume > 30
        m.pendingResume = resume
        setStatus("")
        openMenu(m.menu, "resume", strOr(m.md.title), ["Retomar a partir de " + formatTime(resume), "Começar do início"], 0)
    else
        prepare(0)
    end if
end sub
sub onMedia()
    try
        onMedia_x()
    catch e
        reportCrash("PlayerScreen.onMedia", e)
    end try
end sub

sub loadSegments()
    vid = strOr(m.md.videoId)
    if m.segments <> invalid or Left(vid, 2) <> "tt" then return
    m.segTask = startAddonTask({ kind: "segments", id: vid, type: strOr(m.md.metaType) }, "onSegments")
end sub

sub onSegments_x(event as object)
    if m.segTask = invalid or not event.getRoSGNode().isSameNode(m.segTask) then return
    res = event.getData()
    if res.ok then m.segments = { intro: res.intro, recap: res.recap, outro: res.outro }
end sub
sub onSegments(event as object)
    try
        onSegments_x(event)
    catch e
        reportCrash("PlayerScreen.onSegments", e)
    end try
end sub

' Prepara legendas e testa o link em paralelo; arranca quando ambos terminam
' O vídeo arranca logo: o teste do link corre em paralelo e as legendas têm no máximo 3,5 s
sub prepare(startPos as integer)
    m.startPos = startPos
    m.prepTracks = []
    m.waitSubs = false
    m.waitProbe = false
    m.prepStarted = false
    m.lateSubs = false
    m.subWaitTimer.control = "stop"
    subs = m.md.subtitles
    vid = strOr(m.md.videoId)
    if vid <> "" and vid = m.subCacheFor
        m.prepTracks = m.subCache
    else if subs <> invalid and type(subs) = "roArray" and subs.Count() > 0
        m.waitSubs = true
        m.subTask = startAddonTask({ kind: "dlsubs", items: subs }, "onSubsReady")
    else if strOr(m.md.subsId) <> ""
        ' as legendas não chegaram a tempo na página do título: procura-as aqui
        m.waitSubs = true
        m.subTask = startAddonTask({ kind: "subtitles", type: strOr(m.md.metaType), id: strOr(m.md.subsId) }, "onSubFound")
    else if boolOr(m.md.autoSubFind)
        q = strOr(m.md.subQuery)
        if q = "" then q = strOr(m.md.title)
        m.waitSubs = true
        m.subTask = startAddonTask({ kind: "subfind", title: q }, "onSubFound")
    end if
    url = strOr(m.md.url)
    if LCase(Left(url, 4)) = "http"
        m.probeTask = startAddonTask({ kind: "probe", url: url, headers: m.md.headers }, "onProbe")
    end if
    if m.waitSubs
        setStatus("A preparar o vídeo…")
        m.subWaitTimer.control = "start"
    else
        maybeStart()
    end if
end sub

' As legendas estão a demorar: começa já e junta-as quando chegarem
sub onSubWait()
    try
        if m.waitSubs and not m.prepStarted
            m.waitSubs = false
            m.lateSubs = true
            maybeStart()
        end if
    catch e
        reportCrash("PlayerScreen.onSubWait", e)
    end try
end sub

' Legendas procuradas pelo título (IPTV/TorBox sem ID IMDb)
sub onSubFound_x(event as object)
    if m.subTask = invalid or not event.getRoSGNode().isSameNode(m.subTask) then return
    res = event.getData()
    subs = res.subtitles
    if subs <> invalid and type(subs) = "roArray" and subs.Count() > 0
        m.subTask = startAddonTask({ kind: "dlsubs", items: subs }, "onSubsReady")
    else
        m.subCache = []
        m.prepTracks = []
        m.waitSubs = false
        maybeStart()
    end if
end sub
sub onSubFound(event as object)
    try
        onSubFound_x(event)
    catch e
        reportCrash("PlayerScreen.onSubFound", e)
    end try
end sub

sub onSubsReady_x(event as object)
    if m.subTask = invalid or not event.getRoSGNode().isSameNode(m.subTask) then return
    res = event.getData()
    tracks = res.tracks
    if tracks = invalid then tracks = []
    m.subCache = tracks
    m.subCacheFor = strOr(m.md.videoId)
    m.prepTracks = tracks
    m.waitSubs = false
    m.subWaitTimer.control = "stop"
    if m.prepStarted
        ' chegaram depois de o vídeo começar: recarrega no mesmo ponto já com as legendas
        if m.lateSubs and tracks.Count() > 0
            m.lateSubs = false
            curPos = m.startPos
            if m.started then curPos = Int(m.video.position)
            m.fromRetry = true
            startPlayback(curPos, tracks)
            showOsd("Legendas prontas")
        end if
        return
    end if
    maybeStart()
end sub
sub onSubsReady(event as object)
    try
        onSubsReady_x(event)
    catch e
        reportCrash("PlayerScreen.onSubsReady", e)
    end try
end sub

sub onProbe_x(event as object)
    if m.probeTask = invalid or not event.getRoSGNode().isSameNode(m.probeTask) then return
    res = event.getData()
    m.waitProbe = false
    if m.started then return
    if not res.ok and hasAltSource() and m.autoTries < 8
        m.autoTries = m.autoTries + 1
        setStatus("A procurar a melhor fonte…")
        logEvent("Fonte saltada: " + cleanText(strOr(m.md.streamName)) + " — " + strOr(res.reason))
        scoreCurrent(-3)
        if m.subTask <> invalid then m.subTask.unobserveField("result")
        m.waitSubs = false
        tryNextSource()
        return
    end if
end sub
sub onProbe(event as object)
    try
        onProbe_x(event)
    catch e
        reportCrash("PlayerScreen.onProbe", e)
    end try
end sub

sub maybeStart()
    if m.waitSubs or m.prepStarted then return
    m.prepStarted = true
    startPlayback(m.startPos, m.prepTracks)
end sub

sub startPlayback(curPos as integer, tracks as object)
    md = m.md
    c = CreateObject("roSGNode", "ContentNode")
    c.url = md.url
    c.title = strOr(md.title)
    fmt = strOr(md.format)
    if fmt = ""
        if isLive()
            fmt = "hls"
        else
            fmt = "mkv"
        end if
    end if
    c.streamFormat = fmt
    if isLive() then c.live = true
    if curPos > 0 then c.PlayStart = curPos
    if tracks.Count() > 0 then c.SubtitleTracks = tracks
    m.curTracks = tracks
    if md.headers <> invalid and type(md.headers) = "roArray" and md.headers.Count() > 0 then c.HttpHeaders = md.headers
    setStatus("A carregar…")
    m.attempting = true
    m.autoSubDone = false
    m.video.content = c
    ' se a Roku fechar a app ao tocar esta fonte, no próximo arranque ela fica marcada para evitar
    if not isLive()
        cfgWrite("playGuard", srcKey(strOr(md.url)))
        m.guardOn = true
    end if
    m.video.control = "play"
    if not m.menu.visible and not isMini() then m.video.setFocus(true)
    ' vigia: se a fonte não começar a tempo, não ficar a carregar para sempre
    m.startDog.duration = 45
    if isLive() then m.startDog.duration = 25
    m.startDog.control = "stop"
    m.startDog.control = "start"
end sub

sub onStartDog()
    try
        if m.started or not m.attempting then return
        if isLive()
            m.attempting = false
            m.video.control = "stop"
            setStatus("O canal não respondeu. Experimenta outro (▲ ▼) ou volta mais tarde.")
            logEvent("Canal sem resposta: " + cleanText(strOr(m.md.title)))
            return
        end if
        sourceFailed("não respondeu em " + Int(m.startDog.duration).ToStr() + " s")
    catch e
        reportCrash("PlayerScreen.onStartDog", e)
    end try
end sub

' ---------- Estado do vídeo ----------

sub onState_x()
    st = m.video.state
    ' app suspensa (botão Início): o sistema tira o descodificador — não é um erro da fonte
    if boolOr(m.suspended) then return
    updateMiniLabel()
    if st = "playing" and not m.started and isPlaceholder()
        sourceNotReady()
        return
    end if
    ' retomar depois de uma pausa longa: a ligação ao servidor (e o link do debrid) já expirou —
    ' em vez de ficar parado a carregar, volta a abrir a mesma fonte no mesmo ponto
    nowS = CreateObject("roDateTime").AsSeconds()
    if st = "paused"
        m.pausedAt = nowS
        m.stallDog.control = "stop"
    else if (st = "buffering" or st = "playing") and numOr(m.pausedAt, 0) > 0
        pausedFor = nowS - m.pausedAt
        m.pausedAt = 0
        if pausedFor >= 300 and m.started and not isLive() and st = "buffering"
            reloadSame("pausa de " + Int(pausedFor / 60).ToStr() + " min")
            return
        end if
    end if
    if st = "buffering" and m.started
        ' carregar a meio por demasiado tempo = ligação parada: vigia 30 s
        m.stallDog.control = "stop"
        m.stallDog.control = "start"
    else if st = "playing"
        m.stallDog.control = "stop"
    end if
    if st = "playing"
        m.startDog.control = "stop"
        m.status.visible = false
        if not m.started and not isLive()
            logEvent("A tocar: " + cleanText(strOr(m.md.streamName)) + "  " + qualityText(currentQuality()))
            announceRange()
            m.scored = false
        end if
        m.started = true
        m.autoTries = 0
        applySavedDelay()
        if boolOr(m.pauseAfterResume)
            ' estava em pausa quando saíste da app: volta em pausa, no mesmo ponto
            m.pauseAfterResume = false
            m.video.control = "pause"
        end if
    else if st = "buffering"
        setStatus("A carregar…")
        if m.started and not isLive() then noteRebuffer()
    else if st = "error"
        ' ignora erros de um vídeo que já foi substituído/parado
        if not m.attempting then return
        m.attempting = false
        clearGuard()
        kind = errorKind()
        if not m.started and hasAltSource() and m.autoTries < 4
            m.autoTries = m.autoTries + 1
            setStatus("A procurar a melhor fonte…")
            logEvent("Fonte saltada (" + kind + "): " + cleanText(strOr(m.md.streamName)))
            scoreCurrent(-3)
            tryNextSource()
            return
        end if
        ' já estava a tocar e a ligação caiu: continua noutra fonte no mesmo ponto, sem diálogo
        if m.started and kind <> "drm"
            if not netUp()
                waitForNet()
                return
            end if
            ' primeiro a mesma fonte (o link pode só ter expirado); só depois outra
            if nowS - numOr(m.lastReloadAt, 0) > 120
                reloadSame("erro a meio: " + kind)
                return
            end if
            if not isLive() and hasAltSource() and m.autoTries < 8
                m.attempting = true
                sourceFailed("erro a meio: " + kind)
                return
            end if
        end if
        setStatus("Erro de reprodução: " + strOr(m.video.errorMsg) + " (" + strOr(m.video.errorCode) + ")")
        showErrorDialog(kind)
    else if st = "finished"
        onFinished()
    end if
end sub
sub onState()
    try
        onState_x()
    catch e
        reportCrash("PlayerScreen.onState", e)
    end try
end sub

function errorKind() as string
    code = 0
    if m.video.errorCode <> invalid then code = m.video.errorCode
    msg = LCase(strOr(m.video.errorMsg))
    if m.video.hasField("errorStr") then msg = msg + " " + LCase(strOr(m.video.errorStr))
    if code = -6 or Instr(1, msg, "drm") > 0 then return "drm"
    if code = -1 or code = -2 or Instr(1, msg, "network") > 0 or Instr(1, msg, "http") > 0 or Instr(1, msg, "connect") > 0 or Instr(1, msg, "timeout") > 0 or Instr(1, msg, "server") > 0 then return "network"
    if code = -5 or Instr(1, msg, "malformed") > 0 or Instr(1, msg, "unsupported") > 0 or Instr(1, msg, "decod") > 0 or Instr(1, msg, "codec") > 0 then return "media"
    return "other"
end function

sub onSubtitleTracks_x()
    if m.autoSubDone then return
    tracks = m.video.availableSubtitleTracks
    if tracks = invalid or tracks.Count() = 0 then return
    m.autoSubDone = true
    ' 1) faixa pedida depois de sincronizar
    if m.forceTrack <> ""
        m.video.subtitleTrack = m.forceTrack
        m.video.globalCaptionMode = "On"
        return
    end if
    ' 2) preferência guardada para esta série
    pref = ""
    tp = getTrackPref(strOr(m.md.metaId))
    if tp <> invalid then pref = strOr(tp.s)
    if pref = "off"
        m.video.globalCaptionMode = "Off"
        return
    end if
    codes = []
    if pref <> ""
        codes = [LCase(pref)]
    else
        ' 3) idioma geral das Definições
        g = subLangPref()
        if g = "off"
            m.video.globalCaptionMode = "Off"
            return
        end if
        codes = subLangCodes(g)
    end if
    for each t in tracks
        lang = LCase(strOr(t.Language))
        for each c in codes
            if lang = c
                m.video.subtitleTrack = t.TrackName
                m.video.globalCaptionMode = "On"
                return
            end if
        end for
    end for
end sub
sub onSubtitleTracks()
    try
        onSubtitleTracks_x()
    catch e
        reportCrash("PlayerScreen.onSubtitleTracks", e)
    end try
end sub

sub onAudioTracks_x()
    if m.autoAudioDone then return
    tracks = m.video.availableAudioTracks
    if tracks = invalid or tracks.Count() < 2 then return
    m.autoAudioDone = true
    tp = getTrackPref(strOr(m.md.metaId))
    if tp = invalid or strOr(tp.a) = "" then return
    want = LCase(strOr(tp.a))
    for each t in tracks
        if LCase(strOr(t.Language)) = want
            if strOr(t.Track) <> strOr(m.video.currentAudioTrack) then m.video.audioTrack = t.Track
            return
        end if
    end for
end sub
sub onAudioTracks()
    try
        onAudioTracks_x()
    catch e
        reportCrash("PlayerScreen.onAudioTracks", e)
    end try
end sub

sub onPosition_x()
    if isLive() or not m.started then return
    curPos = m.video.position
    m.lastPos = curPos
    if m.guardOn = true and curPos > 20 then clearGuard()
    if Abs(curPos - m.prevPos) > 8 then m.lastJumpAt = CreateObject("roDateTime").AsSeconds()
    m.prevPos = curPos
    if not m.scored and curPos > 120
        m.scored = true
        scoreCurrent(2)
    end if
    if curPos - m.lastSave >= 30 or curPos < m.lastSave
        m.lastSave = curPos
        saveLocal(false)
    end if
    checkSegments(curPos)
    checkNextCard(curPos)
    checkPostPlay(curPos)
end sub

' ---------- «A seguir» no fim do filme ----------

sub checkPostPlay(curPos as float)
    if m.postDone or isLive() or isMini() then return
    if cfgRead("postPlay", "1") <> "1" then return
    t = strOr(m.md.metaType)
    if t <> "movie" and not (t = "series" and not hasNextEpisode()) then return
    dur = m.video.duration
    if dur < 1200 then return
    mid = strOr(m.md.metaId)
    if m.recs = invalid and m.recsFor <> mid and curPos > dur / 2
        m.recsFor = mid
        ex = []
        for each k in watchedIdsForExclude()
            ex.Push(k)
        end for
        m.recTask = startAddonTask({ kind: "recommend", type: t, id: mid, exclude: ex }, "onRecs")
    end if
    trig = dur * 0.045
    if trig < 60 then trig = 60
    if trig > 300 then trig = 300
    if m.segments <> invalid and m.segments.outro <> invalid
        if m.segments.outro.s > 0 and m.segments.outro.s < dur then trig = dur - m.segments.outro.s
    end if
    left = dur - curPos
    if left > trig or left < 5 then return
    if m.recs = invalid then return
    m.postDone = true
    if m.recs.Count() = 0 then return
    showPostPlay()
end sub

function watchedIdsForExclude() as object
    out = []
    for each x in cfgJsonArray(pkey("doneRecent"))
        if out.Count() < 60 then out.Push(strOr(x))
    end for
    return out
end function

sub onRecs_x(event as object)
    if m.recTask = invalid or not event.getRoSGNode().isSameNode(m.recTask) then return
    res = event.getData()
    m.recs = []
    if res <> invalid and res.items <> invalid then m.recs = res.items
end sub
sub onRecs(event as object)
    try
        onRecs_x(event)
    catch e
        reportCrash("PlayerScreen.onRecs", e)
    end try
end sub

sub showPostPlay()
    hideNextCard()
    hideSkip()
    m.osd.visible = false
    top = m.recs[0]
    m.postHero.uri = strOr(top.bg)
    m.postTitle.text = strOr(top.name)
    root = CreateObject("roSGNode", "ContentNode")
    for i = 1 to m.recs.Count() - 1
        if i <= 6
            c = root.createChild("ContentNode")
            c.title = strOr(m.recs[i].name)
            c.HDPosterUrl = strOr(m.recs[i].poster)
        end if
    end for
    m.postGrid.content = root
    m.postGrid.visible = root.getChildCount() > 0
    ' o vídeo continua (créditos), pequeno à esquerda
    m.video.enableUI = false
    m.video.translation = [80, 220]
    m.video.width = 700
    m.video.height = 394
    m.postCount = 15
    m.postBtns.buttons = ["Ver agora  ·  15 s", "Ver os créditos"]
    m.postPlay.visible = true
    m.postBtns.setFocus(true)
    m.postTimer.control = "start"
end sub

sub hidePostPlay()
    m.postTimer.control = "stop"
    m.postPlay.visible = false
    m.video.translation = [0, 0]
    m.video.width = 1920
    m.video.height = 1080
    m.video.enableUI = true
    focusVideo()
end sub

sub onPostTick_x()
    if m.postCount < 0 then return
    m.postCount = m.postCount - 1
    if m.postCount <= 0
        m.postTimer.control = "stop"
        goRecommendation(m.recs[0])
        return
    end if
    m.postBtns.buttons = ["Ver agora  ·  " + m.postCount.ToStr() + " s", "Ver os créditos"]
end sub
sub onPostTick()
    try
        onPostTick_x()
    catch e
        reportCrash("PlayerScreen.onPostTick", e)
    end try
end sub

sub onPostButton_x()
    if not m.postPlay.visible then return
    if m.postBtns.buttonSelected = 0
        goRecommendation(m.recs[0])
    else
        hidePostPlay()
    end if
end sub
sub onPostButton()
    try
        onPostButton_x()
    catch e
        reportCrash("PlayerScreen.onPostButton", e)
    end try
end sub

sub onPostGrid_x()
    i = m.postGrid.itemSelected + 1
    if i >= 1 and i < m.recs.Count() then goRecommendation(m.recs[i])
end sub
sub onPostGrid()
    try
        onPostGrid_x()
    catch e
        reportCrash("PlayerScreen.onPostGrid", e)
    end try
end sub

' ao escolher outra sugestão, a contagem pára
sub onPostGridFocus()
    if m.postPlay.visible and m.postGrid.hasFocus() and m.postCount > 0
        m.postCount = -1
        m.postTimer.control = "stop"
        m.postBtns.buttons = ["Ver agora", "Ver os créditos"]
    end if
end sub

sub goRecommendation(it as object)
    m.postTimer.control = "stop"
    m.postCount = -1
    md = m.md
    removeProgress(strOr(md.metaId))
    addDone(strOr(md.metaId))
    setLastSeen(strOr(md.metaId), strOr(md.metaType), strOr(md.metaName))
    m.top.openTitle = { contentId: strOr(it.id), mediaType: strOr(it.type), title: strOr(it.name), poster: strOr(it.poster) }
    m.video.control = "stop"
    m.top.finished = true
end sub
sub onPosition()
    try
        onPosition_x()
    catch e
        reportCrash("PlayerScreen.onPosition", e)
    end try
end sub

' ---------- Saltar introdução / resumo ----------

sub checkSegments(curPos as float)
    if m.segments = invalid then return
    for each kind in ["recap", "intro"]
        seg = m.segments[kind]
        if seg <> invalid
            inside = curPos >= seg.s and curPos < seg.e - 1
            if inside and not m.skipDismissed.DoesExist(kind)
                if kind = "intro" and cfgRead("autoSkip", "0") = "1"
                    m.skipDismissed[kind] = true
                    m.video.seek = seg.e
                    showOsd("Introdução saltada")
                else if m.skipKind <> kind
                    showSkip(kind, seg.e)
                end if
                return
            else if m.skipKind = kind and not inside
                hideSkip()
            end if
        end if
    end for
end sub

sub showSkip(kind as string, target as float)
    m.skipKind = kind
    m.skipTarget = target
    if kind = "intro"
        m.skipBtn.buttons = ["Saltar introdução"]
    else
        m.skipBtn.buttons = ["Saltar resumo"]
    end if
    if isMini() or m.menu.visible or m.nextCard.visible then return
    m.skipGroup.visible = true
    m.skipBtn.setFocus(true)
end sub

sub hideSkip()
    hadFocus = m.skipGroup.visible and m.skipBtn.isInFocusChain()
    m.skipGroup.visible = false
    m.skipKind = ""
    if hadFocus then focusVideo()
end sub

sub onSkipPressed_x()
    if m.skipKind <> ""
        m.skipDismissed[m.skipKind] = true
        m.video.seek = m.skipTarget
    end if
    hideSkip()
end sub
sub onSkipPressed()
    try
        onSkipPressed_x()
    catch e
        reportCrash("PlayerScreen.onSkipPressed", e)
    end try
end sub

' ---------- Próximo episódio (contagem) ----------

sub checkNextCard(curPos as float)
    if m.nextShown or m.nextDismissed or not hasNextEpisode() then return
    dur = m.video.duration
    trigger = false
    if m.segments <> invalid and m.segments.outro <> invalid
        if curPos >= m.segments.outro.s then trigger = true
    end if
    if dur > 300 and dur - curPos <= 25 then trigger = true
    if trigger then showNextCard()
end sub

sub showNextCard()
    m.nextShown = true
    m.countdown = 10
    m.nextLabel.text = nextEpisodeLabel()
    m.nextCount.text = "Começa em " + m.countdown.ToStr() + " s"
    m.countTimer.control = "start"
    if isMini() then return
    hideSkip()
    m.nextCard.visible = true
    m.nextBtns.setFocus(true)
end sub

sub hideNextCard()
    hadFocus = m.nextCard.visible and m.nextBtns.isInFocusChain()
    m.nextCard.visible = false
    m.countTimer.control = "stop"
    if hadFocus then focusVideo()
end sub

sub onCountdown_x()
    m.countdown = m.countdown - 1
    m.nextCount.text = "Começa em " + m.countdown.ToStr() + " s"
    if m.countdown <= 0
        hideNextCard()
        playNext()
    end if
end sub
sub onCountdown()
    try
        onCountdown_x()
    catch e
        reportCrash("PlayerScreen.onCountdown", e)
    end try
end sub

sub onNextButton_x()
    if m.nextBtns.buttonSelected = 0
        hideNextCard()
        playNext()
    else
        cancelNextCard()
    end if
end sub
sub onNextButton()
    try
        onNextButton_x()
    catch e
        reportCrash("PlayerScreen.onNextButton", e)
    end try
end sub

sub cancelNextCard()
    m.nextDismissed = true
    m.noAutoNext = true
    hideNextCard()
end sub

' ---------- Progresso ----------

sub saveLocal(done as boolean)
    md = m.md
    if strOr(md.metaId) = "" then return
    dur = m.video.duration
    curPos = m.video.position
    if dur <= 0 then return
    if done or curPos / dur > 0.92
        removeProgress(md.metaId)
        setLastSeen(strOr(md.metaId), strOr(md.metaType), strOr(md.metaName))
        if strOr(md.metaType) = "series" then markEpisodeWatched(strOr(md.videoId))
        addDone(strOr(md.metaId))
        scrobble(100.0)
    else if curPos > 20
        if m.lastScrobble <> invalid and m.lastScrobble = strOr(md.videoId) then m.lastScrobble = invalid
        saveProgress({ m: md.metaId, v: strOr(md.videoId), t: Int(curPos), d: Int(dur), n: strOr(md.metaName), p: strOr(md.poster), y: strOr(md.metaType), a: nowEpoch() })
    end if
end sub

' Envia o progresso para a conta Stremio (através da cena principal)
sub pushStremio(done as boolean)
    md = m.md
    if m.pushed or isLive() or not stremioLinked() or cfgRead("syncProgress", "1") = "0" or currentProfileId() <> "main" then return
    id = strOr(md.metaId)
    t = strOr(md.metaType)
    if id = "" or t = "other" or (t <> "movie" and t <> "series") then return
    dur = Int(m.video.duration)
    curPos = Int(m.video.position)
    if dur <= 0 or curPos < 20 then return
    change = "progress"
    if done or curPos / dur > 0.92 then change = "finished"
    json = libraryItemJson(id, t, strOr(md.metaName), strOr(md.poster), change, strOr(md.videoId), curPos, dur)
    m.pushed = true
    m.top.pushJson = json
end sub

sub onFinished()
    if isPlaceholder()
        sourceNotReady()
        return
    end if
    ' «Terminou» sem nunca ter tocado (link vazio, expirado ou recusado pelo debrid): é uma falha da fonte,
    ' não o fim do filme. Antes voltava em silêncio para a página do título.
    if not isLive() and (not m.started or (m.video.position < 5 and m.video.duration <= 0))
        sourceEmpty()
        return
    end if
    ' acabou a meio (mais de 5 min antes do fim de um título longo): a ligação caiu — continua noutra fonte
    if not isLive() and m.video.duration > 600 and m.video.position < m.video.duration - 300
        sourceEmpty()
        return
    end if
    saveLocal(true)
    pushStremio(true)
    hideNextCard()
    if hasNextEpisode() and not m.noAutoNext
        playNext()
    else
        m.top.finished = true
    end if
end sub

sub clearGuard()
    m.guardOn = false
    cfgDelete("playGuard")
end sub

sub onClosing_x()
    if m.top.closing
        clearGuard()
        if m.started and not isLive()
            saveLocal(false)
            pushStremio(false)
        end if
        m.countTimer.control = "stop"
        m.sleepTimer.control = "stop"
        ' nenhum temporizador do leitor pode disparar depois de o fechar
        m.startDog.control = "stop"
        m.stallDog.control = "stop"
        m.netTimer.control = "stop"
        m.postTimer.control = "stop"
        m.cgTimer.control = "stop"
        m.attempting = false
        m.graceTimer.control = "stop"
        m.video.control = "stop"
        m.video.content = invalid
    end if
end sub
sub onClosing()
    try
        onClosing_x()
    catch e
        reportCrash("PlayerScreen.onClosing", e)
    end try
end sub

' ---------- Erros ----------

sub showErrorDialog(kind as string)
    logEvent("Leitor (" + kind + "): " + strOr(m.video.errorMsg) + " · " + cleanText(strOr(m.md.streamName)))
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Não foi possível reproduzir"
    info = ""
    if isLive()
        info = "O canal pode estar offline, bloqueado na tua região ou num formato que a Roku não suporta."
    else if kind = "network"
        info = "Não foi possível ligar ao servidor desta fonte: o link pode ter expirado, o servidor estar lento ou sem resposta, ou a tua ligação à internet falhou."
    else if kind = "media"
        info = "A Roku não consegue ler este ficheiro: formato não suportado (ex.: Dolby Vision sem HDR, AV1) ou torrent ainda não descarregado no TorBox ([TB download])."
    else if kind = "drm"
        info = "Este conteúdo está protegido (DRM) e não pode ser reproduzido aqui."
    else
        info = "Ocorreu um erro inesperado ao reproduzir esta fonte."
    end if
    if m.autoTries > 0 then info = info + " Já tentei mais " + m.autoTries.ToStr() + " fonte(s) automaticamente."
    dlg.message = [strOr(m.video.errorMsg), info]
    m.errActions = []
    btns = []
    if m.started and kind = "network"
        btns.Push("Tentar de novo")
        m.errActions.Push("retry")
    end if
    if hasAltSource()
        btns.Push("Tentar a próxima fonte")
        m.errActions.Push("next")
    end if
    btns.Push("Voltar")
    m.errActions.Push("back")
    dlg.buttons = btns
    dlg.observeFieldScoped("buttonSelected", "onErrorClose")
    m.top.getScene().dialog = dlg
end sub

sub onErrorClose_x()
    dlg = m.top.getScene().dialog
    action = "back"
    if dlg <> invalid
        idx = dlg.buttonSelected
        if m.errActions <> invalid and idx >= 0 and idx < m.errActions.Count() then action = m.errActions[idx]
        dlg.close = true
    end if
    if action = "retry"
        m.fromRetry = true
        startPlayback(Int(m.lastPos), m.curTracks)
    else if action = "next"
        tryNextSource()
    else
        m.top.finished = true
    end if
end sub
sub onErrorClose()
    try
        onErrorClose_x()
    catch e
        reportCrash("PlayerScreen.onErrorClose", e)
    end try
end sub

' ---------- Fontes alternativas ----------

sub onResolved_x(event as object)
    if m.resolveTask = invalid or not event.getRoSGNode().isSameNode(m.resolveTask) then return
    res = event.getData()
    streams = res.streams
    if streams = invalid or streams.Count() = 0
        setStatus("Este canal não tem emissão disponível agora.")
        m.errActions = ["back"]
        dlg = CreateObject("roSGNode", "StandardMessageDialog")
        dlg.title = strOr(m.md.title)
        dlg.message = ["O add-on não devolveu nenhuma emissão reproduzível para este canal."]
        dlg.buttons = ["Voltar"]
        dlg.observeFieldScoped("buttonSelected", "onErrorClose")
        m.top.getScene().dialog = dlg
        return
    end if
    alts = []
    for each x in streams
        if alts.Count() < 20 then alts.Push(altOf(x))
    end for
    s = streams[0]
    md = m.md
    md.url = s.url
    md.format = guessStreamFormat(s.url, s.filename + " " + s.title)
    md.headers = s.headers
    md.streamName = s.name
    md.alts = alts
    md.altIndex = 0
    m.md = md
    if isLive()
        startPlayback(0, [])
    else
        prepare(0)
    end if
end sub
sub onResolved(event as object)
    try
        onResolved_x(event)
    catch e
        reportCrash("PlayerScreen.onResolved", e)
    end try
end sub

function hasAltSource() as boolean
    alts = m.md.alts
    if alts = invalid or type(alts) <> "roArray" then return false
    return nextAltIndex() < alts.Count()
end function

' O Torrentio devolve um vídeo curto («TORRENT DOWNLOADING TO DEBRID») quando o torrent ainda não está no debrid
function isPlaceholder() as boolean
    if isLive() then return false
    t = strOr(m.md.metaType)
    if t <> "movie" and t <> "series" then return false
    d = m.video.duration
    if d <= 0 or d > 120 then return false
    rx = CreateObject("roRegex", "\[(TB|RD|AD|PM|DL|OC|ED|PP)", "i")
    return rx.IsMatch(strOr(m.md.streamName))
end function

sub sourceEmpty()
    sourceFailed("terminou sem tocar")
end sub

' Uma fonte falhou (acabou sem imagem, não respondeu ou caiu a meio): tenta a seguinte no mesmo ponto
sub sourceFailed(why as string)
    if m.started then saveLocal(false)
    ' caiu a meio: a próxima fonte continua no mesmo ponto, sem perguntar
    if m.started and m.video.position > 30 then m.qualResume = Int(m.video.position)
    clearGuard()
    scoreCurrent(-3)
    logEvent("Fonte falhou (" + why + "): " + cleanText(strOr(m.md.streamName)))
    m.startDog.control = "stop"
    m.video.control = "stop"
    m.attempting = false
    m.started = false
    if hasAltSource() and m.autoTries < 8
        m.autoTries = m.autoTries + 1
        setStatus("A procurar a melhor fonte…")
        tryNextSource()
        return
    end if
    setStatus("")
    m.errActions = ["back"]
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Não foi possível reproduzir"
    dlg.message = ["As fontes tentadas terminaram sem mostrar imagem. Costuma ser o serviço debrid (TorBox) a recusar o link: confirma em Definições se a conta está ativa, ou escolhe outra fonte.", "Detalhes em Definições › Diagnóstico."]
    dlg.buttons = ["Voltar"]
    dlg.observeFieldScoped("buttonSelected", "onErrorClose")
    m.top.getScene().dialog = dlg
end sub

sub sourceNotReady()
    clearGuard()
    scoreCurrent(-3)
    m.video.control = "stop"
    m.attempting = false
    m.started = false
    if hasAltSource()
        setStatus("A procurar a melhor fonte…")
        tryNextSource()
        return
    end if
    setStatus("")
    m.errActions = ["back"]
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Ainda a descarregar"
    dlg.message = ["Nenhuma fonte deste título está pronta no teu serviço debrid. O download já começou — tenta de novo daqui a alguns minutos ou escolhe outra fonte."]
    dlg.buttons = ["Voltar"]
    dlg.observeFieldScoped("buttonSelected", "onErrorClose")
    m.top.getScene().dialog = dlg
end sub

' Próxima fonte: prefere a mesma resolução da que falhou
' Próxima fonte pronta (nunca uma «download»), de preferência com a mesma resolução
function nextAltIndex() as integer
    cur = Int(numOr(m.md.altIndex, 0))
    nxt = cur + 1
    alts = m.md.alts
    if alts = invalid or type(alts) <> "roArray" or nxt >= alts.Count() then return nxt
    cq = 0
    if alts[cur] <> invalid and alts[cur].q <> invalid then cq = Int(numOr(alts[cur].q, 0))
    ' um add-on que já falhou 2 vezes neste vídeo fica para o fim (não se percorrem as fontes dele uma a uma)
    for rnd = 0 to 1
        if cq > 0
            for j = nxt to alts.Count() - 1
                if altReady(alts[j]) and altOk(alts[j], rnd) and alts[j].q <> invalid and Int(numOr(alts[j].q, 0)) = cq then return j
            end for
        end if
        for j = nxt to alts.Count() - 1
            if altReady(alts[j]) and altOk(alts[j], rnd) then return j
        end for
    end for
    return alts.Count()
end function

function altOk(a as object, rnd as integer) as boolean
    if rnd > 0 or m.addonFails = invalid then return true
    k = strOr(a.addon)
    if k = "" then return true
    return Int(numOr(m.addonFails[k], 0)) < 2
end function

sub noteAddonFail()
    alts = m.md.alts
    i = Int(numOr(m.md.altIndex, 0))
    if alts = invalid or type(alts) <> "roArray" or i >= alts.Count() or alts[i] = invalid then return
    k = strOr(alts[i].addon)
    if k = "" then return
    if m.addonFails = invalid then m.addonFails = {}
    m.addonFails[k] = Int(numOr(m.addonFails[k], 0)) + 1
end sub

function altReady(a as object) as boolean
    if a = invalid then return false
    if a.cached = invalid then return true
    return a.cached
end function

sub tryNextSource()
    if not hasAltSource() then return
    if m.started then saveLocal(false)
    noteAddonFail()
    i = nextAltIndex()
    s = m.md.alts[i]
    md = m.md
    md.url = s.url
    md.format = guessStreamFormat(s.url, strOr(s.filename) + " " + strOr(s.title))
    md.headers = s.headers
    md.streamName = s.name
    md.altIndex = i
    m.started = false
    m.fromRetry = true
    m.top.media = md
    showOsd(strOr(md.title))
end sub

' ---------- Troca de qualidade ----------

function canSwitchQuality() as boolean
    if isLive() then return false
    t = strOr(m.md.metaType)
    return strOr(m.md.metaId) <> "" and (t = "movie" or t = "series")
end function

function currentQuality() as integer
    alts = m.md.alts
    i = Int(numOr(m.md.altIndex, 0))
    if alts <> invalid and type(alts) = "roArray" and i < alts.Count() and alts[i].q <> invalid then return Int(numOr(alts[i].q, 0))
    return 0
end function

function qualityText(q as integer) as string
    if q >= 2160 then return "4K"
    if q > 0 then return q.ToStr() + "p"
    return "Auto"
end function

function currentQualityLabel() as string
    q = currentQuality()
    if q = 0 then return "Automática"
    return qualityText(q)
end function

sub openQualityMenu()
    m.qualOpts = [{ q: 99999, label: "Melhor disponível" }, { q: 2160, label: "4K" }, { q: 1080, label: "1080p" }, { q: 720, label: "720p" }, { q: 480, label: "480p" }]
    items = []
    cur = currentQuality()
    for each o in m.qualOpts
        lbl = o.label
        if o.q = cur then lbl = lbl + "  (atual)"
        items.Push(lbl)
    end for
    openMenu(m.menu, "quality", "Qualidade (procura a melhor fonte)", items, 0)
end sub

sub startQualitySwitch(target as integer)
    vid = strOr(m.md.videoId)
    if vid = "" then vid = strOr(m.md.metaId)
    mt = strOr(m.md.metaType)
    if mt = "" then mt = "movie"
    m.qualTarget = target
    setStatus("A procurar a melhor fonte…")
    m.qualTask = startAddonTask({ kind: "streams", type: mt, id: vid, ignoreMax: true, animated: boolOr(m.md.animated) }, "onQualityStreams")
end sub

sub onQualityStreams_x(event as object)
    if m.qualTask = invalid or not event.getRoSGNode().isSameNode(m.qualTask) then return
    setStatus("")
    res = event.getData()
    streams = res.streams
    if streams = invalid or streams.Count() = 0
        showOsd("Sem outras fontes para esta qualidade")
        return
    end if
    idx = bestIndexForQuality(streams, m.qualTarget, strOr(m.md.url))
    if idx < 0
        showOsd("Esta já é a única fonte disponível")
        return
    end if
    alts = [altOf(streams[idx])]
    for i = 0 to streams.Count() - 1
        if i <> idx and alts.Count() < 30 then alts.Push(altOf(streams[i]))
    end for
    s = alts[0]
    if m.started
        saveLocal(false)
        m.qualResume = Int(m.video.position)
    end if
    md = m.md
    md.url = s.url
    md.format = guessStreamFormat(s.url, strOr(s.filename) + " " + strOr(s.title))
    md.headers = s.headers
    md.streamName = s.name
    md.alts = alts
    md.altIndex = 0
    m.started = false
    m.fromRetry = true
    m.top.media = md
    showOsd(strOr(md.title) + "   ·   " + qualityText(Int(numOr(s.q, 0))))
end sub
sub onQualityStreams(event as object)
    try
        onQualityStreams_x(event)
    catch e
        reportCrash("PlayerScreen.onQualityStreams", e)
    end try
end sub

' ---------- Mini-leitor ----------

sub onMode_x()
    if isMini()
        m.bg.visible = false
        m.osd.visible = false
        m.menu.visible = false
        m.skipGroup.visible = false
        m.nextCard.visible = false
        m.miniFrame.visible = true
        m.video.enableUI = false
        m.video.translation = [1380, 750]
        m.video.width = 480
        m.video.height = 270
        m.status.translation = [1380, 860]
        m.status.width = 480
        updateMiniLabel()
    else
        m.bg.visible = true
        m.miniFrame.visible = false
        m.video.translation = [0, 0]
        m.video.width = 1920
        m.video.height = 1080
        m.video.enableUI = true
        m.status.translation = [0, 980]
        m.status.width = 1920
        if m.top.isInFocusChain() or m.top.hasFocus() then m.video.setFocus(true)
        showOsd(strOr(m.md.title))
    end if
end sub
sub onMode()
    try
        onMode_x()
    catch e
        reportCrash("PlayerScreen.onMode", e)
    end try
end sub

sub updateMiniLabel()
    st = m.video.state
    prefix = ""
    if st = "paused" then prefix = "[pausa]  "
    if st = "buffering" then prefix = "[a carregar]  "
    m.miniLabel.text = prefix + "Repetir: ecrã inteiro · Play: pausa · " + strOr(m.md.title)
end sub

sub onCommand_x()
    cmd = m.top.command
    if cmd = "toggle"
        if m.video.state = "playing"
            m.video.control = "pause"
        else if m.video.state = "paused"
            m.video.control = "resume"
        end if
    else if cmd = "close"
        m.top.finished = true
    else if cmd = "save"
        if m.started and not isLive() then saveLocal(false)
    else if cmd = "suspend"
        ' a app vai para segundo plano (Retoma Instantânea): guardar o ponto exato
        m.suspendPos = Int(m.video.position)
        if m.suspendPos < 1 then m.suspendPos = Int(numOr(m.lastPos, 0))
        m.suspendWasPaused = m.video.state = "paused"
        if m.started and not isLive() then saveLocal(false)
        m.suspended = true
        m.stallDog.control = "stop"
        m.video.control = "stop"
    else if cmd = "resume"
        ' de volta: abre a mesma fonte no mesmo ponto (o link novo é pedido se o antigo expirou)
        if not boolOr(m.suspended) then return
        m.suspended = false
        if not isLive() and Int(numOr(m.suspendPos, 0)) > 5
            m.lastPos = m.suspendPos
            m.started = true
        end if
        m.pauseAfterResume = boolOr(m.suspendWasPaused)
        reloadSame("app retomada")
    end if
end sub
sub onCommand()
    try
        onCommand_x()
    catch e
        reportCrash("PlayerScreen.onCommand", e)
    end try
end sub

' ---------- Próximo episódio ----------

sub playNext()
    if not hasNextEpisode()
        m.top.finished = true
        return
    end if
    hideNextCard()
    if m.started and not m.pushed
        saveLocal(true)
        pushStremio(true)
    end if
    i = Int(numOr(m.md.epIndex, -1)) + 1
    m.nextIdx = i
    m.nextEp = m.md.episodes[i]
    m.video.control = "stop"
    m.started = false
    setStatus("A procurar fontes para " + m.nextEp.label + "…")
    showOsd(strOr(m.md.metaName) + " — " + m.nextEp.label)
    m.nextTask = startAddonTask({ kind: "streams", type: m.md.metaType, id: m.nextEp.id, animated: boolOr(m.md.animated) }, "onNextStreams")
end sub

sub onNextStreams_x(event as object)
    if m.nextTask = invalid or not event.getRoSGNode().isSameNode(m.nextTask) then return
    res = event.getData()
    streams = res.streams
    if streams = invalid or streams.Count() = 0
        setStatus("Não há fontes para " + m.nextEp.label + ".")
        showMessage("Próximo episódio", "Não encontrei fontes reproduzíveis para " + m.nextEp.label + ".")
        return
    end if
    ' a mesma fonte/qualidade do episódio anterior, senão a primeira compatível em cache
    prev = strOr(m.md.streamName)
    pick = invalid
    for each s in streams
        if pick = invalid and s.name = prev and s.risky <> true then pick = s
    end for
    for each s in streams
        if pick = invalid and s.cached and s.risky <> true then pick = s
    end for
    if pick = invalid then pick = streams[0]
    m.nextStream = pick
    m.nextPickIdx = 0
    for i = 0 to streams.Count() - 1
        if streams[i].url = pick.url then m.nextPickIdx = i
    end for
    alts = []
    for each x in streams
        if alts.Count() < 30 then alts.Push(altOf(x))
    end for
    m.nextStreams = alts
    if m.nextPickIdx >= alts.Count() then m.nextPickIdx = 0
    setStatus("A preparar legendas…")
    m.nextSubTask = startAddonTask({ kind: "subtitles", type: m.md.metaType, id: m.nextEp.id }, "onNextSubs")
end sub
sub onNextStreams(event as object)
    try
        onNextStreams_x(event)
    catch e
        reportCrash("PlayerScreen.onNextStreams", e)
    end try
end sub

sub onNextSubs_x(event as object)
    if m.nextSubTask = invalid or not event.getRoSGNode().isSameNode(m.nextSubTask) then return
    res = event.getData()
    md = m.md
    s = m.nextStream
    md.url = s.url
    md.title = strOr(md.metaName) + " — " + m.nextEp.label
    md.format = guessStreamFormat(s.url, s.filename + " " + s.title)
    md.headers = s.headers
    md.videoId = m.nextEp.id
    md.epIndex = m.nextIdx
    md.streamName = s.name
    md.alts = m.nextStreams
    md.altIndex = m.nextPickIdx
    md.subtitles = res.subtitles
    if md.subtitles = invalid then md.subtitles = []
    m.top.media = md
end sub
sub onNextSubs(event as object)
    try
        onNextSubs_x(event)
    catch e
        reportCrash("PlayerScreen.onNextSubs", e)
    end try
end sub

' ---------- Canais (IPTV) ----------

sub zap(delta as integer)
    chs = m.md.channels
    if chs = invalid or type(chs) <> "roArray" or chs.Count() < 2 then return
    i = Int(numOr(m.md.chIndex, 0)) + delta
    if i < 0 then i = chs.Count() - 1
    if i >= chs.Count() then i = 0
    ch = chs[i]
    md = m.md
    md.url = ch.url
    md.title = ch.name
    md.format = guessStreamFormat(ch.url, "")
    md.chIndex = i
    md.alts = []
    md.altIndex = 0
    md.headers = []
    md.epgNow = strOr(ch.e)
    m.top.media = md
    showChGuide()
end sub

' Mini-guia: canal atual em grande, com o anterior e o seguinte (▲ ▼ para mudar, OK para mostrar)
sub showChGuide()
    chs = m.md.channels
    if chs = invalid or type(chs) <> "roArray" or chs.Count() = 0 then return
    n = chs.Count()
    i = Int(numOr(m.md.chIndex, 0))
    if i < 0 or i >= n then return
    p = (i - 1 + n) mod n
    q = (i + 1) mod n
    m.top.findNode("cgPrev").text = "▲  " + (p + 1).ToStr() + ".  " + cleanText(strOr(chs[p].name)) + epgShort(chs[p])
    m.top.findNode("cgName").text = (i + 1).ToStr() + ".  " + cleanText(strOr(chs[i].name))
    nowT = strOr(chs[i].e)
    if nowT = "" then nowT = "Sem guia de programação para este canal"
    m.top.findNode("cgNow").text = nowT
    m.top.findNode("cgNext").text = "▼  " + (q + 1).ToStr() + ".  " + cleanText(strOr(chs[q].name)) + epgShort(chs[q])
    m.chGuide.visible = true
    m.cgTimer.control = "stop"
    m.cgTimer.control = "start"
end sub

function epgShort(ch as object) as string
    e = strOr(ch.e)
    if e = "" then return ""
    p = Instr(1, e, "   ·   ")
    if p > 0 then e = Left(e, p - 1)
    return "   —   " + e
end function

sub hideChGuide()
    m.chGuide.visible = false
end sub

' ---------- Sincronizar legendas ----------

function currentExternalTrackIdx() as integer
    if m.video.globalCaptionMode <> "On" then return -1
    cur = strOr(m.video.currentSubtitleTrack)
    for i = 0 to m.curTracks.Count() - 1
        if strOr(m.curTracks[i].TrackName) = cur then return i
    end for
    return -1
end function

function fmtOffset(ms as integer) as string
    sign = "+"
    if ms < 0 then sign = "-"
    a = Abs(ms)
    return sign + Int(a / 1000).ToStr() + "," + Int((a mod 1000) / 100).ToStr() + " s"
end function

sub openSyncMenu()
    m.syncDeltas = [-2000, -1000, -500, 500, 1000, 2000, 0]
    items = ["Adiantar 2,0 s", "Adiantar 1,0 s", "Adiantar 0,5 s", "Atrasar 0,5 s", "Atrasar 1,0 s", "Atrasar 2,0 s", "Repor (0 s)"]
    openMenu(m.menu, "sync", "Sincronizar legendas (atual: " + fmtOffset(m.subOffset) + ")", items, 3)
end sub

sub applySync(idx as integer)
    ti = currentExternalTrackIdx()
    if ti < 0 and m.subBaseIdx < 0
        showOsd("Escolhe primeiro uma legenda do OpenSubtitles")
        return
    end if
    if m.subBaseIdx < 0 or (ti >= 0 and ti <> m.subBaseIdx)
        m.subBaseIdx = ti
        m.subBasePath = strOr(m.curTracks[ti].TrackName)
        m.subOffset = 0
    end if
    d = m.syncDeltas[idx]
    if d = 0
        m.subOffset = 0
    else
        m.subOffset = m.subOffset + d
    end if
    saveDelay(m.subOffset)
    if m.subOffset = 0
        applySubPath(m.subBasePath)
    else
        setStatus("A ajustar legendas…")
        m.shiftTask = startAddonTask({ kind: "shiftsub", src: m.subBasePath, ms: m.subOffset }, "onShifted")
    end if
end sub

sub onShifted_x(event as object)
    if m.shiftTask = invalid or not event.getRoSGNode().isSameNode(m.shiftTask) then return
    res = event.getData()
    if res.ok
        applySubPath(res.path)
    else
        setStatus("")
        showOsd("Não foi possível ajustar estas legendas")
    end if
end sub
sub onShifted(event as object)
    try
        onShifted_x(event)
    catch e
        reportCrash("PlayerScreen.onShifted", e)
    end try
end sub

sub applySubPath(path as string)
    tracks = []
    for i = 0 to m.curTracks.Count() - 1
        t = m.curTracks[i]
        if i = m.subBaseIdx
            tracks.Push({ Language: t.Language, TrackName: path, Description: t.Description })
        else
            tracks.Push(t)
        end if
    end for
    m.forceTrack = path
    m.fromRetry = true
    startPlayback(Int(m.video.position), tracks)
    showOsd("Legendas: " + fmtOffset(m.subOffset))
end sub

' ---------- Informação técnica e temporizador ----------

function mbps(v as dynamic) as string
    b = numOr(v, 0)
    if b <= 0 then return "—"
    return (Int(b / 100000) / 10).ToStr() + " Mbps"
end function

sub showTechInfo()
    lines = []
    lines.Push("Fonte: " + cleanText(strOr(m.md.streamName)))
    url = strOr(m.md.url)
    host = url
    p = Instr(9, url, "/")
    if p > 0 then host = Left(url, p - 1)
    lines.Push("Servidor: " + host)
    if m.video.content <> invalid then lines.Push("Formato: " + strOr(m.video.content.streamFormat))
    if m.video.hasField("videoFormat") and strOr(m.video.videoFormat) <> "" then lines.Push("Vídeo: " + strOr(m.video.videoFormat))
    lines.Push("Áudio: " + currentAudioLabel())
    lines.Push("Legendas: " + currentSubtitleLabel())
    si = m.video.streamInfo
    if si <> invalid
        lines.Push("Débito do vídeo: " + mbps(si.streamBitrate) + "   ·   Ligação medida: " + mbps(si.measuredBitrate))
    end if
    if m.video.duration > 0 then lines.Push("Posição: " + formatTime(Int(m.video.position)) + " de " + formatTime(Int(m.video.duration)))
    if hasAltSource() or (m.md.alts <> invalid and m.md.alts.Count() > 0) then lines.Push("Fonte " + (Int(numOr(m.md.altIndex, 0)) + 1).ToStr() + " de " + m.md.alts.Count().ToStr())
    showMessage("Informação técnica", lines.Join(Chr(10)))
end sub

sub setSleep(mins as integer)
    m.sleepMins = mins
    m.sleepTimer.control = "stop"
    if mins > 0
        m.sleepTimer.duration = mins * 60
        m.sleepTimer.control = "start"
        showOsd("Temporizador: desliga dentro de " + mins.ToStr() + " min")
    else
        showOsd("Temporizador desligado")
    end if
end sub

sub onSleep_x()
    m.video.control = "pause"
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Ainda estás a ver?"
    dlg.message = ["O temporizador terminou. O vídeo fecha dentro de 1 minuto se não responderes."]
    dlg.buttons = ["Continuar a ver", "Parar"]
    dlg.observeFieldScoped("buttonSelected", "onSleepDialog")
    m.top.getScene().dialog = dlg
    m.graceTimer.control = "start"
end sub
sub onSleep()
    try
        onSleep_x()
    catch e
        reportCrash("PlayerScreen.onSleep", e)
    end try
end sub

sub onSleepDialog_x()
    dlg = m.top.getScene().dialog
    idx = 1
    if dlg <> invalid
        idx = dlg.buttonSelected
        dlg.close = true
    end if
    m.graceTimer.control = "stop"
    m.sleepMins = 0
    if idx = 0
        m.video.control = "resume"
    else
        m.top.finished = true
    end if
end sub
sub onSleepDialog()
    try
        onSleepDialog_x()
    catch e
        reportCrash("PlayerScreen.onSleepDialog", e)
    end try
end sub

sub onGrace_x()
    dlg = m.top.getScene().dialog
    if dlg <> invalid then dlg.close = true
    m.sleepMins = 0
    m.top.finished = true
end sub
sub onGrace()
    try
        onGrace_x()
    catch e
        reportCrash("PlayerScreen.onGrace", e)
    end try
end sub

' ---------- Menu de opções ----------

function subtitleLabel(t as object) as string
    d = strOr(t.Description)
    if d = "" then d = langName(strOr(t.Language))
    if Left(strOr(t.TrackName), 4) <> "tmp:" and Instr(1, d, "(") = 0 then d = d + " (no ficheiro)"
    return d
end function

function currentSubtitleLabel() as string
    if m.video.globalCaptionMode <> "On" then return "Desligadas"
    cur = strOr(m.video.currentSubtitleTrack)
    tracks = m.video.availableSubtitleTracks
    if tracks <> invalid
        for each t in tracks
            if strOr(t.TrackName) = cur then return subtitleLabel(t)
        end for
    end if
    return "Ligadas"
end function

function audioLabel(t as object) as string
    name = strOr(t.Name)
    lang = langName(strOr(t.Language))
    fmt = strOr(t.Format)
    out = lang
    if name <> "" and LCase(name) <> LCase(strOr(t.Language)) then out = out + " · " + name
    if fmt <> "" then out = out + " (" + fmt + ")"
    return out
end function

function currentAudioLabel() as string
    cur = strOr(m.video.currentAudioTrack)
    tracks = m.video.availableAudioTracks
    if tracks <> invalid
        for each t in tracks
            if strOr(t.Track) = cur then return audioLabel(t)
        end for
        if tracks.Count() = 1 then return audioLabel(tracks[0])
    end if
    return "Predefinido"
end function

sub openMainMenu()
    m.menuActions = []
    items = []
    items.Push("Legendas: " + currentSubtitleLabel())
    m.menuActions.Push("subs")
    if not isLive() and (currentExternalTrackIdx() >= 0 or m.subOffset <> 0)
        items.Push("Sincronizar legendas (" + fmtOffset(m.subOffset) + ")")
        m.menuActions.Push("sync")
    end if
    items.Push("Áudio: " + currentAudioLabel())
    m.menuActions.Push("audio")
    if not isLive()
        if m.segments <> invalid and m.segments.intro <> invalid and m.video.position < m.segments.intro.e
            items.Push("Saltar introdução")
            m.menuActions.Push("skipIntro")
        end if
        items.Push("Recomeçar do início")
        m.menuActions.Push("restart")
        if hasNextEpisode()
            items.Push("Próximo episódio: " + nextEpisodeLabel())
            m.menuActions.Push("next")
        end if
        if hasAltSource()
            items.Push("Tentar outra fonte (" + (nextAltIndex() + 1).ToStr() + " de " + m.md.alts.Count().ToStr() + ")")
            m.menuActions.Push("alt")
        end if
        if canSwitchQuality()
            items.Push("Qualidade: " + currentQualityLabel())
            m.menuActions.Push("quality")
        end if
    else if m.md.channels <> invalid and m.md.channels.Count() > 1
        items.Push("Lista de canais")
        m.menuActions.Push("chList")
        items.Push("Procurar canal…")
        m.menuActions.Push("chSearch")
        items.Push("Canal seguinte")
        m.menuActions.Push("chNext")
        items.Push("Canal anterior")
        m.menuActions.Push("chPrev")
    end if
    items.Push("Informação técnica")
    m.menuActions.Push("info")
    sl = "Desligado"
    if m.sleepMins > 0 then sl = m.sleepMins.ToStr() + " min"
    items.Push("Temporizador para desligar: " + sl)
    m.menuActions.Push("sleep")
    items.Push("Minimizar (continuar a navegar)")
    m.menuActions.Push("minimize")
    items.Push("Parar e fechar o leitor")
    m.menuActions.Push("stop")
    items.Push("Fechar menu")
    m.menuActions.Push("close")
    hideSkip()
    openMenu(m.menu, "main", strOr(m.md.title), items, 0)
end sub

sub openSubsMenu()
    m.subTracks = m.video.availableSubtitleTracks
    if m.subTracks = invalid then m.subTracks = []
    items = ["Desligadas"]
    start = 0
    cur = strOr(m.video.currentSubtitleTrack)
    for i = 0 to m.subTracks.Count() - 1
        t = m.subTracks[i]
        items.Push(subtitleLabel(t))
        if m.video.globalCaptionMode = "On" and strOr(t.TrackName) = cur then start = i + 1
    end for
    title = "Legendas"
    if m.subTracks.Count() = 0 then title = "Legendas — nenhuma disponível para este vídeo"
    openMenu(m.menu, "subs", title, items, start)
end sub

sub openAudioMenu()
    m.audioTracks = m.video.availableAudioTracks
    if m.audioTracks = invalid then m.audioTracks = []
    items = []
    start = 0
    cur = strOr(m.video.currentAudioTrack)
    for i = 0 to m.audioTracks.Count() - 1
        items.Push(audioLabel(m.audioTracks[i]))
        if strOr(m.audioTracks[i].Track) = cur then start = i
    end for
    if items.Count() = 0
        items.Push("Faixa única / predefinida")
    end if
    openMenu(m.menu, "audio", "Faixa de áudio", items, start)
end sub

sub openSleepMenu()
    m.sleepOptions = [0, 15, 30, 60, 90, 120]
    openMenu(m.menu, "sleep", "Temporizador para desligar", ["Desligado", "15 min", "30 min", "60 min", "90 min", "120 min"], 0)
end sub

sub closeMenu_x()
    m.menu.visible = false
    focusVideo()
end sub
sub closeMenu()
    try
        closeMenu_x()
    catch e
        reportCrash("PlayerScreen.closeMenu", e)
    end try
end sub

sub onMenuSelected_x()
    id = m.menu.menuId
    idx = m.menu.selected
    if id = "resume"
        closeMenu()
        if idx = 0
            prepare(m.pendingResume)
        else
            prepare(0)
        end if
    else if id = "main"
        action = m.menuActions[idx]
        if action = "subs"
            openSubsMenu()
        else if action = "sync"
            openSyncMenu()
        else if action = "audio"
            openAudioMenu()
        else if action = "sleep"
            openSleepMenu()
        else if action = "quality"
            openQualityMenu()
        else
            closeMenu()
            if action = "restart"
                m.video.seek = 0
            else if action = "skipIntro"
                m.skipDismissed["intro"] = true
                m.video.seek = m.segments.intro.e
            else if action = "next"
                playNext()
            else if action = "alt"
                tryNextSource()
            else if action = "info"
                showTechInfo()
            else if action = "minimize"
                m.top.mode = "mini"
            else if action = "stop"
                m.top.finished = true
            else if action = "chList"
                openChannelList("")
            else if action = "chSearch"
                askChannel()
            else if action = "chNext"
                zap(1)
            else if action = "chPrev"
                zap(-1)
            end if
        end if
    else if id = "chlist"
        closeMenu()
        if idx >= 0 and idx < m.chMap.Count() then zap(m.chMap[idx] - Int(numOr(m.md.chIndex, 0)))
    else if id = "qdrop"
        closeMenu()
        if idx = 0
            startQualitySwitch(Int(numOr(m.qdropTarget, 0)))
        else
            ' não volta a perguntar até ao próximo vídeo
            m.qdropDeclined = true
        end if
    else if id = "quality"
        closeMenu()
        startQualitySwitch(Int(m.qualOpts[idx].q))
    else if id = "subs"
        closeMenu()
        isSeries = strOr(m.md.metaType) = "series"
        if idx = 0
            m.video.globalCaptionMode = "Off"
            if isSeries then setTrackPref(strOr(m.md.metaId), "s", "off")
        else
            t = m.subTracks[idx - 1]
            m.video.subtitleTrack = t.TrackName
            m.video.globalCaptionMode = "On"
            if isSeries then setTrackPref(strOr(m.md.metaId), "s", LCase(strOr(t.Language)))
        end if
    else if id = "audio"
        closeMenu()
        if idx < m.audioTracks.Count()
            t = m.audioTracks[idx]
            m.video.audioTrack = t.Track
            if strOr(m.md.metaType) = "series" then setTrackPref(strOr(m.md.metaId), "a", LCase(strOr(t.Language)))
        end if
    else if id = "sync"
        closeMenu()
        applySync(idx)
    else if id = "sleep"
        closeMenu()
        setSleep(m.sleepOptions[idx])
    end if
end sub
sub onMenuSelected()
    try
        onMenuSelected_x()
    catch e
        reportCrash("PlayerScreen.onMenuSelected", e)
    end try
end sub

sub onMenuCancelled_x()
    if m.menu.menuId = "resume"
        ' "Voltar" na pergunta de retomar: sai do leitor
        m.menu.visible = false
        m.top.finished = true
    else
        closeMenu()
    end if
end sub
sub onMenuCancelled()
    try
        onMenuCancelled_x()
    catch e
        reportCrash("PlayerScreen.onMenuCancelled", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    if isMini() then return false
    if key = "OK" and isLive() and not m.menu.visible and not m.chGuide.visible
        showChGuide()
        return true
    end if
    if key = "back" and m.chGuide.visible
        hideChGuide()
        return true
    end if
    if key = "back" and m.postPlay.visible
        hidePostPlay()
        return true
    end if
    if m.postPlay.visible
        if key = "down" and m.postBtns.isInFocusChain() and m.postGrid.visible
            m.postGrid.setFocus(true)
            return true
        else if key = "up" and m.postGrid.isInFocusChain()
            m.postBtns.setFocus(true)
            return true
        end if
        return false
    end if
    if key = "back"
        if m.nextCard.visible
            cancelNextCard()
            return true
        end if
        if m.skipGroup.visible
            if m.skipKind <> "" then m.skipDismissed[m.skipKind] = true
            hideSkip()
            return true
        end if
        ' Voltar encolhe o vídeo para o canto; se ainda não começou, fecha
        if m.started or m.video.state = "paused"
            m.top.mode = "mini"
        else
            m.top.finished = true
        end if
        return true
    end if
    if key = "options"
        openMainMenu()
        return true
    end if
    if isLive()
        if key = "up"
            zap(-1)
            return true
        else if key = "down"
            zap(1)
            return true
        end if
    else if key = "down" and m.started and not m.skipGroup.visible and not m.nextCard.visible
        openMainMenu()
        return true
    end if
    if key = "info" or key = "up"
        showOsd(strOr(m.md.title))
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("PlayerScreen.onKeyEvent", e)
    end try
    return handled
end function

' Envia para o Trakt (através da cena principal) quando um vídeo termina
sub scrobble(progress as float)
    md = m.md
    vid = strOr(md.videoId)
    if Left(vid, 2) <> "tt" or cfgRead("traktToken", "") = "" or currentProfileId() <> "main" then return
    if m.lastScrobble <> invalid and m.lastScrobble = vid then return
    m.lastScrobble = vid
    m.top.scrobble = { videoId: vid, type: strOr(md.metaType), progress: progress }
end sub

' ---------- Canais: lista e pesquisa ----------

sub openChannelList(q as string)
    chs = m.md.channels
    if chs = invalid or chs.Count() = 0 then return
    cur = Int(numOr(m.md.chIndex, 0))
    items = []
    m.chMap = []
    start = 0
    lq = LCase(q)
    for i = 0 to chs.Count() - 1
        name = strOr(chs[i].name)
        if lq = "" or Instr(1, LCase(name), lq) > 0 or (i + 1).ToStr() = q
            if i = cur then start = items.Count()
            prefix = ""
            if i = cur then prefix = "▶ "
            items.Push(prefix + (i + 1).ToStr() + ".  " + cleanText(name))
            m.chMap.Push(i)
        end if
    end for
    if items.Count() = 0
        showOsd("Nenhum canal com «" + q + "»")
        return
    end if
    title = "Canais (" + chs.Count().ToStr() + ")"
    if q <> "" then title = "Canais com «" + q + "» (" + items.Count().ToStr() + ")"
    openMenu(m.menu, "chlist", title, items, start)
end sub

sub askChannel()
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = "Procurar canal"
    dlg.message = ["Nome ou número do canal"]
    dlg.buttons = ["Procurar", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onAskChannel")
    m.chDialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onAskChannel()
    try
        dlg = m.chDialog
        idx = dlg.buttonSelected
        text = dlg.text.Trim()
        dlg.close = true
        if idx = 0 and text <> "" then openChannelList(text)
    catch e
        reportCrash("PlayerScreen.onAskChannel", e)
    end try
end sub

' ---------- Aprender, qualidade automática, HDR e atraso das legendas ----------

sub scoreCurrent(delta as integer)
    if isLive() then return
    alts = m.md.alts
    i = Int(numOr(m.md.altIndex, 0))
    q = 0
    fn = ""
    if alts <> invalid and type(alts) = "roArray" and i < alts.Count()
        q = Int(numOr(alts[i].q, 0))
        fn = strOr(alts[i].filename)
    end if
    srcScore(strOr(m.md.streamName), q, fn, delta)
end sub

' 3 pausas para carregar em 3 minutos: baixa um nível de qualidade (saltos na barra não contam)
sub noteRebuffer()
    now = CreateObject("roDateTime").AsSeconds()
    if now - m.lastJumpAt < 6 then return
    keep = []
    for each t in m.rebuf
        if now - t < 180 then keep.Push(t)
    end for
    keep.Push(now)
    m.rebuf = keep
    if keep.Count() < 3 or not canSwitchQuality() then return
    cur = currentQuality()
    lower = 0
    if cur >= 2160 then lower = 1080
    if cur >= 1080 and cur < 2160 then lower = 720
    if cur >= 720 and cur < 1080 then lower = 480
    if lower = 0 then return
    m.rebuf = []
    mode = cfgRead("qualAuto", "ask")
    if mode = "off" or m.qdropDeclined = true then return
    if mode = "auto"
        logEvent("Muitas pausas a carregar — a baixar para " + qualityText(lower))
        showOsd("A rede está lenta — a mudar para " + qualityText(lower))
        startQualitySwitch(lower)
        return
    end if
    ' por defeito pergunta: o vídeo continua a tocar enquanto decides
    if m.menu.visible then return
    m.qdropTarget = lower
    logEvent("Muitas pausas a carregar — a perguntar se baixa para " + qualityText(lower))
    openMenu(m.menu, "qdrop", "A rede está lenta (" + qualityText(cur) + ")", ["Mudar para " + qualityText(lower) + " — menos pausas", "Manter " + qualityText(cur) + " neste vídeo"], 0)
end sub

' Indica se o vídeo é HDR / Dolby Vision e se esta TV o mostra
sub announceRange()
    alts = m.md.alts
    i = Int(numOr(m.md.altIndex, 0))
    if alts = invalid or type(alts) <> "roArray" or i >= alts.Count() then return
    r = strOr(alts[i].range)
    if r = "" or r = "sdr" then return
    if m.devCaps = invalid then m.devCaps = deviceCaps()
    caps = m.devCaps
    label = "HDR"
    if r = "dv" or r = "dvhdr" then label = "Dolby Vision"
    if r = "hdr10p" then label = "HDR10+"
    if r = "hdr10" then label = "HDR10"
    if r = "hlg" then label = "HLG"
    tvOk = caps.hdr = true
    if label = "Dolby Vision" then tvOk = caps.dv = true or (r = "dvhdr" and caps.hdr = true)
    if tvOk
        showOsd(label + " — a tua TV mostra este vídeo em HDR")
    else
        showOsd(label + " — a TV não mostra este formato: cores convertidas")
    end if
end sub

sub saveDelay(ms as integer)
    vid = strOr(m.md.videoId)
    if vid = "" then return
    out = [{ v: vid, ms: ms }]
    for each x in cfgJsonArray("subDelays")
        if strOr(x.v) <> vid and out.Count() < 8 then out.Push(x)
    end for
    cfgWrite("subDelays", FormatJson(out))
end sub

' Ao começar a tocar: aplica o atraso de legendas guardado para este título
sub applySavedDelay()
    vid = strOr(m.md.videoId)
    if vid = "" or m.delayApplied = vid or m.subOffset <> 0 then return
    ms = 0
    for each x in cfgJsonArray("subDelays")
        if strOr(x.v) = vid then ms = Int(numOr(x.ms, 0))
    end for
    if ms = 0 then
        m.delayApplied = vid
        return
    end if
    ti = currentExternalTrackIdx()
    if ti < 0 then return
    m.delayApplied = vid
    m.subBaseIdx = ti
    m.subBasePath = strOr(m.curTracks[ti].TrackName)
    m.subOffset = ms
    m.shiftTask = startAddonTask({ kind: "shiftsub", src: m.subBasePath, ms: ms }, "onShifted")
end sub

' ---------- Recuperar de pausas longas, quedas de rede e ligações paradas ----------

function netUp() as boolean
    di = CreateObject("roDeviceInfo")
    if not di.GetLinkStatus() then return false
    if FindMemberFunction(di, "GetInternetStatus") <> invalid then return di.GetInternetStatus()
    return true
end function

' Volta a abrir a MESMA fonte no ponto onde estava (link novo do debrid, ligação nova)
sub reloadSame(why as string)
    resumeAt = Int(m.video.position)
    if resumeAt < 1 then resumeAt = Int(numOr(m.lastPos, 0))
    logEvent("A retomar a mesma fonte (" + why + ") em " + formatTime(resumeAt))
    if m.started and not isLive() then saveLocal(false)
    m.lastReloadAt = CreateObject("roDateTime").AsSeconds()
    m.stallDog.control = "stop"
    if not isLive() and resumeAt > 5 then m.qualResume = resumeAt
    m.video.control = "stop"
    m.attempting = false
    m.started = false
    m.fromRetry = true
    setStatus("A retomar…")
    md = m.md
    md.reloadAt = m.lastReloadAt
    m.top.media = md
end sub

sub onStallDog()
    try
        if not m.started or m.video.state <> "buffering" then return
        if not netUp()
            waitForNet()
            return
        end if
        now = CreateObject("roDateTime").AsSeconds()
        if now - numOr(m.lastReloadAt, 0) > 120
            reloadSame("parou a carregar")
        else if not isLive() and hasAltSource()
            sourceFailed("parou a carregar outra vez")
        else
            setStatus("A ligação está muito lenta…")
        end if
    catch e
        reportCrash("PlayerScreen.onStallDog", e)
    end try
end sub

' Sem Internet: espera que volte e continua sozinho no mesmo ponto
sub waitForNet()
    if m.netWaitPos = invalid or m.netWaitPos < 1
        m.netWaitPos = Int(m.video.position)
        if m.netWaitPos < 1 then m.netWaitPos = Int(numOr(m.lastPos, 0))
    end if
    if m.started and not isLive() then saveLocal(false)
    m.video.control = "pause"
    m.attempting = false
    setStatus("Sem ligação à Internet — continuo sozinho quando voltar.")
    logEvent("Sem Internet a meio do vídeo — à espera")
    m.netTimer.control = "start"
end sub

sub onNetTimer()
    try
        if not netUp() then return
        m.netTimer.control = "stop"
        resumeAt = numOr(m.netWaitPos, 0)
        m.netWaitPos = 0
        if not isLive() and resumeAt > 5
            m.lastPos = resumeAt
            m.started = true
        end if
        reloadSame("a Internet voltou")
    catch e
        reportCrash("PlayerScreen.onNetTimer", e)
    end try
end sub
