' Cena principal: navegação, sincronização com o Stremio e envio de progresso

sub init_x()
    m.top.backgroundColor = "0x0F1014FF"
    m.top.backgroundURI = ""
    if glassMode() then m.top.backgroundURI = "pkg:/images/glass_bg_fhd.jpg"
    g = m.global
    if not g.hasField("manifests") then g.addField("manifests", "assocarray", false)
    if not g.hasField("stremioAddons") then g.addField("stremioAddons", "array", false)
    if not g.hasField("library") then g.addField("library", "array", false)
    if not g.hasField("syncState") then g.addField("syncState", "string", false)
    if not g.hasField("iptvLast") then g.addField("iptvLast", "assocarray", false)
    if not g.hasField("accent") then g.addField("accent", "string", false)
    if not g.hasField("crash") then g.addField("crash", "string", true)
    g.observeField("crash", "onCrash")
    if checkPlayGuard()
        logEvent("A app fechou a meio de uma fonte; essa fonte passa a ficar no fim da lista")
        m.guardTimer = CreateObject("roSGNode", "Timer")
        m.guardTimer.duration = 4
        m.guardTimer.observeField("fire", "onGuardNotice")
        m.top.appendChild(m.guardTimer)
        m.guardTimer.control = "start"
    end if
    g.accent = ""
    g.accent = accentColor()
    g.manifests = {}
    g.stremioAddons = []
    g.library = []
    m.screens = m.top.findNode("screens")
    m.overlay = m.top.findNode("overlay")
    m.player = invalid
    m.stack = []
    m.pushTasks = []
    m.settingsChanged = false
    m.focusGuard = CreateObject("roSGNode", "Timer")
    m.focusGuard.duration = 1
    m.focusGuard.repeat = true
    m.focusGuard.observeField("fire", "onFocusGuard")
    m.top.appendChild(m.focusGuard)
    m.focusGuard.control = "start"

    startSync()

    m.home = CreateObject("roSGNode", "HomeScreen")
    m.home.observeField("itemSelected", "onItemSelected")
    m.home.observeField("menuSelected", "onMenuSelected")
    pushScreen(m.home)
    ' primeira utilização: assistente de 3 passos enquanto a app não estiver configurada
    if cfgRead("onboarded", "") <> "1" and cfgRead("torboxKey", "") = "" and getIptvLists().Count() = 0 and not stremioLinked()
        w = CreateObject("roSGNode", "WelcomeScreen")
        w.observeField("action", "onSettingsAction")
        w.observeField("done", "onWelcomeDone")
        pushScreen(w)
    else
        ' voltar exatamente onde estavas (ecrãs abertos e o vídeo no mesmo ponto)
        m.restored = restoreNav()
    end if
    m.navReady = true
    if boolOr(m.restored) then saveNav()
    refreshTraktToken()
    if cfgRead("updateUrl", "") <> "" then checkUpdate(false)
    startSyncServer()
    ' de 10 em 10 minutos: os downloads pedidos ao TorBox já estão prontos?
    m.tbTimer = CreateObject("roSGNode", "Timer")
    m.tbTimer.duration = 600
    m.tbTimer.repeat = true
    m.tbTimer.observeField("fire", "onTbTimer")
    m.top.appendChild(m.tbTimer)
    m.tbTimer.control = "start"
    ' modo ambiente: com a app parada, as imagens dos teus títulos; OK começa a ver o que está a passar
    m.ambTimer = CreateObject("roSGNode", "Timer")
    m.ambTimer.duration = 20
    m.ambTimer.repeat = true
    m.ambTimer.observeField("fire", "onAmbTimer")
    m.top.appendChild(m.ambTimer)
    m.ambTimer.control = "start"
    ' lembretes de programas (TV ao vivo): verifica a cada 30 s
    m.remTimer = CreateObject("roSGNode", "Timer")
    m.remTimer.duration = 30
    m.remTimer.repeat = true
    m.remTimer.observeField("fire", "onRemTimer")
    m.top.appendChild(m.remTimer)
    m.remTimer.control = "start"
    if cfgRead("torboxKey", "") <> "" then m.tbTask = startAddonTask({ kind: "tbme", key: cfgRead("torboxKey", "") }, "onTbMe")

    ' abertura animada (pode ser desligada nas Definições)
    if cfgRead("introAnim", "1") = "1" and not boolOr(m.restored)
        m.intro = CreateObject("roSGNode", "IntroScreen")
        m.intro.observeField("done", "onIntroDone")
        m.top.appendChild(m.intro)
    end if
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("MainScene.init", e)
    end try
end sub

sub onIntroDone_x()
    if m.intro <> invalid
        m.top.removeChild(m.intro)
        m.intro = invalid
    end if
end sub
sub onIntroDone()
    try
        onIntroDone_x()
    catch e
        reportCrash("MainScene.onIntroDone", e)
    end try
end sub

' ---------- Transições ----------

sub animateIn(node as object, slide as boolean)
    if slide
        anim = makeFade(m.screens, node, 0.0, 1.0, 0.28, [70, 0])
    else
        anim = makeFade(m.screens, node, 0.0, 1.0, 0.22, invalid)
    end if
    anim.observeField("state", "onAnimState")
end sub

sub onAnimState_x(event as object)
    anim = event.getRoSGNode()
    if anim.state = "stopped"
        p = anim.getParent()
        if p <> invalid then p.removeChild(anim)
    end if
end sub
sub onAnimState(event as object)
    try
        onAnimState_x(event)
    catch e
        reportCrash("MainScene.onAnimState", e)
    end try
end sub

' ---------- Sincronização Stremio ----------

sub startSync()
    key = cfgRead("stremioKey", "")
    if key = ""
        m.global.stremioAddons = []
        m.global.library = []
        m.global.syncState = "none"
        return
    end if
    m.global.syncState = "running"
    m.syncTask = CreateObject("roSGNode", "StremioTask")
    m.syncTask.request = { kind: "sync", authKey: key }
    m.syncTask.observeField("result", "onSync")
    m.syncTask.control = "run"
end sub

sub onSync_x(event as object)
    res = event.getData()
    if res.ok
        bases = []
        man = m.global.manifests
        for each a in res.addons
            bases.Push(a.base)
            if a.manifest <> invalid and type(a.manifest) = "roAssociativeArray" then man[a.base] = a.manifest
        end for
        m.global.manifests = man
        m.global.stremioAddons = bases
        m.global.library = res.library
    end if
    m.global.syncState = "done"
    m.home.reloadRequest = true
    if not res.ok
        logEvent("Stremio: " + strOr(res.error))
        showSyncError(strOr(res.error))
    end if
end sub
sub onSync(event as object)
    try
        onSync_x(event)
    catch e
        reportCrash("MainScene.onSync", e)
    end try
end sub

sub showSyncError(msg as string)
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Stremio"
    dlg.message = ["Não foi possível sincronizar a conta: " + msg, "Podes voltar a ligar a conta nas Definições."]
    dlg.buttons = ["OK"]
    dlg.observeFieldScoped("buttonSelected", "onDialogClose")
    m.top.dialog = dlg
end sub

sub onDialogClose_x()
    if m.top.dialog <> invalid then m.top.dialog.close = true
end sub
sub onDialogClose()
    try
        onDialogClose_x()
    catch e
        reportCrash("MainScene.onDialogClose", e)
    end try
end sub

sub onPushJson_x(event as object)
    json = event.getData()
    if json = invalid or json = "" then return
    key = cfgRead("stremioKey", "")
    if key = "" then return
    libApplyLocal(ParseJson(json))
    t = CreateObject("roSGNode", "StremioTask")
    t.request = { kind: "push", authKey: key, itemJson: json }
    t.observeField("result", "onPushDone")
    m.pushTasks.Push(t)
    t.control = "run"
end sub
sub onPushJson(event as object)
    try
        onPushJson_x(event)
    catch e
        reportCrash("MainScene.onPushJson", e)
    end try
end sub

sub onPushDone_x(event as object)
    node = event.getRoSGNode()
    out = []
    for each t in m.pushTasks
        if not t.isSameNode(node) then out.Push(t)
    end for
    m.pushTasks = out
end sub
sub onPushDone(event as object)
    try
        onPushDone_x(event)
    catch e
        reportCrash("MainScene.onPushDone", e)
    end try
end sub

' ---------- Pilha de ecrãs ----------

sub pushScreen(node as object)
    if m.stack.Count() > 0
        m.stack.Peek().visible = false
    end if
    if glassMode() then glassify(node)
    applyAccent(node, accentColor())
    m.screens.appendChild(node)
    m.stack.Push(node)
    node.visible = true
    m.screens.visible = true
    if m.player <> invalid and m.player.mode = "full" then m.player.mode = "mini"
    node.setFocus(true)
    if m.stack.Count() > 1 then animateIn(node, true)
    saveNav()
end sub

sub popScreen()
    if m.stack.Count() <= 1 then return
    top = m.stack.Pop()
    if top.hasField("closing") then top.closing = true
    m.screens.removeChild(top)
    prev = m.stack.Peek()
    prev.visible = true
    animateIn(prev, false)
    if m.player = invalid or m.player.mode = "mini" then prev.setFocus(true)
    if prev.isSameNode(m.home)
        if m.settingsChanged = true
            m.settingsChanged = false
            m.home.reloadRequest = true
        else
            m.home.refreshLocal = true
        end if
    end if
    if prev.hasField("refresh") then prev.refresh = true
    saveNav()
end sub

sub onItemSelected_x(event as object)
    item = event.getData()
    if item = invalid or item.id = invalid then return
    details = CreateObject("roSGNode", "DetailsScreen")
    details.observeField("playRequest", "onPlayRequest")
    details.observeField("pushJson", "onPushJson")
    details.observeField("itemSelected", "onItemSelected")
    details.observeField("personSelected", "onPersonSelected")
    tagNav(details, { k: "details", item: navItem(item) })
    pushScreen(details)
    details.item = item
end sub
sub onItemSelected(event as object)
    try
        onItemSelected_x(event)
    catch e
        reportCrash("MainScene.onItemSelected", e)
    end try
end sub

sub onMenuSelected_x(event as object)
    openMenuScreen(event.getData())
end sub

sub openMenuScreen(name as dynamic)
    if name = invalid then return
    if name = "explore"
        s = CreateObject("roSGNode", "ExploreScreen")
        s.observeField("itemSelected", "onItemSelected")
        tagNav(s, { k: "menu", n: "explore" })
        pushScreen(s)
    else if name = "search"
        s = CreateObject("roSGNode", "SearchScreen")
        s.observeField("itemSelected", "onItemSelected")
        s.observeField("closeRequest", "onCloseRequest")
        tagNav(s, { k: "menu", n: "search" })
        pushScreen(s)
        s.start = true
    else if name = "iptv"
        s = CreateObject("roSGNode", "IptvScreen")
        s.observeField("playRequest", "onPlayRequest")
        tagNav(s, { k: "menu", n: "iptv" })
        pushScreen(s)
        s.start = true
    else if name = "torbox"
        s = CreateObject("roSGNode", "TorboxScreen")
        s.observeField("playRequest", "onPlayRequest")
        tagNav(s, { k: "menu", n: "torbox" })
        pushScreen(s)
        s.start = true
    else if name = "settings"
        ' no perfil infantil, as Definições pedem o PIN parental (se existir)
        if isKidProfile() and parentalPin() <> ""
            dlg = CreateObject("roSGNode", "StandardPinPadDialog")
            dlg.title = "Definições"
            dlg.message = ["PIN parental"]
            dlg.buttons = ["OK", "Cancelar"]
            dlg.observeFieldScoped("buttonSelected", "onSettingsPin")
            m.setPinDlg = dlg
            m.top.dialog = dlg
            return
        end if
        openSettings()
    end if
end sub

sub openSettings()
    s = CreateObject("roSGNode", "SettingsScreen")
    s.observeField("changed", "onSettingsChanged")
    s.observeField("action", "onSettingsAction")
    pushScreen(s)
end sub

sub onSettingsPin()
    try
        dlg = m.setPinDlg
        ok = dlg.buttonSelected = 0 and strOr(dlg.pin) = parentalPin()
        bad = dlg.buttonSelected = 0 and not ok
        dlg.close = true
        if ok then openSettings()
        if bad then showInfo("Definições", "PIN errado.")
    catch e
        reportCrash("MainScene.onSettingsPin", e)
    end try
end sub
sub onMenuSelected(event as object)
    try
        onMenuSelected_x(event)
    catch e
        reportCrash("MainScene.onMenuSelected", e)
    end try
end sub

sub onSettingsChanged_x()
    m.settingsChanged = true
    m.global.manifests = {}
end sub
sub onSettingsChanged()
    try
        onSettingsChanged_x()
    catch e
        reportCrash("MainScene.onSettingsChanged", e)
    end try
end sub

sub onSettingsAction_x(event as object)
    action = event.getData()
    if action = "link"
        s = CreateObject("roSGNode", "StremioLinkScreen")
        s.observeField("linked", "onLinked")
        pushScreen(s)
    else if action = "phone"
        s = CreateObject("roSGNode", "PhoneConfigScreen")
        s.observeField("changed", "onSettingsChanged")
        pushScreen(s)
    else if action = "diag"
        pushScreen(CreateObject("roSGNode", "DiagnosticsScreen"))
    else if action = "trakt"
        s = CreateObject("roSGNode", "TraktLinkScreen")
        s.observeField("linked", "onTraktLinked")
        pushScreen(s)
    else if action = "checkUpdate"
        checkUpdate(true)
    else if action = "profile"
        m.settingsChanged = true
    else if action = "sync" or action = "logout"
        m.global.manifests = {}
        m.settingsChanged = false
        startSync()
        m.home.reloadRequest = true
    end if
end sub
sub onSettingsAction(event as object)
    try
        onSettingsAction_x(event)
    catch e
        reportCrash("MainScene.onSettingsAction", e)
    end try
end sub

sub onLinked_x(event as object)
    node = event.getRoSGNode()
    if m.stack.Count() > 1 and node.isSameNode(m.stack.Peek()) then popScreen()
    m.global.manifests = {}
    startSync()
    m.home.reloadRequest = true
end sub
sub onLinked(event as object)
    try
        onLinked_x(event)
    catch e
        reportCrash("MainScene.onLinked", e)
    end try
end sub

' ---------- Leitor (ecrã inteiro / mini) ----------

sub onPlayRequest_x(event as object)
    startMedia(event.getData())
end sub

sub startMedia(req as dynamic)
    if req = invalid or req.url = invalid then return
    if m.player = invalid
        m.player = CreateObject("roSGNode", "PlayerScreen")
        m.player.observeField("finished", "onPlayerFinished")
        m.player.observeField("pushJson", "onPushJson")
        m.player.observeField("scrobble", "onScrobble")
        m.player.observeField("openTitle", "onPlayerOpenTitle")
        m.player.observeField("mode", "onPlayerMode")
        m.overlay.appendChild(m.player)
    end if
    m.player.mode = "full"
    m.player.media = req
    showPlayerFull()
    saveNav()
end sub
sub onPlayRequest(event as object)
    try
        onPlayRequest_x(event)
    catch e
        reportCrash("MainScene.onPlayRequest", e)
    end try
end sub

sub showPlayerFull()
    m.screens.visible = false
    m.player.setFocus(true)
end sub

sub onPlayerMode_x()
    if m.player = invalid then return
    if m.player.mode = "full"
        showPlayerFull()
    else
        m.screens.visible = true
        focusTopScreen()
    end if
end sub
sub onPlayerMode()
    try
        onPlayerMode_x()
    catch e
        reportCrash("MainScene.onPlayerMode", e)
    end try
end sub

sub focusTopScreen()
    top = m.stack.Peek()
    top.setFocus(true)
    if top.isSameNode(m.home) then m.home.refreshLocal = true
    if top.hasField("refresh") then top.refresh = true
end sub

sub onPlayerFinished_x()
    closePlayer()
    ' «A seguir»: abre o título escolhido e começa a reproduzir
    if m.pendingOpen <> invalid
        a = m.pendingOpen
        m.pendingOpen = invalid
        m.top.launchArgs = a
    end if
end sub

sub onPlayerOpenTitle(event as object)
    m.pendingOpen = event.getData()
end sub
sub onPlayerFinished()
    try
        onPlayerFinished_x()
    catch e
        reportCrash("MainScene.onPlayerFinished", e)
    end try
end sub

sub closePlayer()
    if m.player = invalid then return
    p = m.player
    m.player = invalid
    p.closing = true
    m.overlay.removeChild(p)
    m.screens.visible = true
    focusTopScreen()
    saveNav()
end sub

function miniActive() as boolean
    return m.player <> invalid and m.player.mode = "mini"
end function

sub onCloseRequest_x(event as object)
    node = event.getRoSGNode()
    if m.stack.Count() > 1 and node.isSameNode(m.stack.Peek()) then popScreen()
end sub
sub onCloseRequest(event as object)
    try
        onCloseRequest_x(event)
    catch e
        reportCrash("MainScene.onCloseRequest", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    ' teclas do mini-leitor (quando o ecrã atual não as usa)
    if miniActive()
        if key = "replay"
            m.player.mode = "full"
            return true
        else if key = "play"
            m.player.command = "toggle"
            return true
        end if
    end if
    if key = "back"
        if m.stack.Count() > 1
            popScreen()
        else if miniActive()
            closePlayer()
        else
            confirmExit()
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
        reportCrash("MainScene.onKeyEvent", e)
    end try
    return handled
end function

sub confirmExit()
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Sair da Hortêncioflix?"
    dlg.buttons = ["Sair", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onExitDialog")
    m.top.dialog = dlg
end sub

sub onExitDialog_x()
    dlg = m.top.dialog
    idx = dlg.buttonSelected
    dlg.close = true
    if idx = 0
        ' saída pedida: da próxima vez começa no Início
        clearNav()
        m.top.exitApp = true
    end if
end sub
sub onExitDialog()
    try
        onExitDialog_x()
    catch e
        reportCrash("MainScene.onExitDialog", e)
    end try
end sub

' ---------- Ator / realizador ----------

sub onPersonSelected_x(event as object)
    name = event.getData()
    if name = invalid or name = "" then return
    s = CreateObject("roSGNode", "SearchScreen")
    s.observeField("itemSelected", "onItemSelected")
    s.observeField("closeRequest", "onCloseRequest")
    tagNav(s, { k: "person", n: name })
    pushScreen(s)
    s.query = name
    s.start = true
end sub
sub onPersonSelected(event as object)
    try
        onPersonSelected_x(event)
    catch e
        reportCrash("MainScene.onPersonSelected", e)
    end try
end sub

' ---------- Trakt ----------

sub onTraktLinked_x(event as object)
    node = event.getRoSGNode()
    if m.stack.Count() > 1 and node.isSameNode(m.stack.Peek()) then popScreen()
    m.settingsChanged = true
end sub
sub onTraktLinked(event as object)
    try
        onTraktLinked_x(event)
    catch e
        reportCrash("MainScene.onTraktLinked", e)
    end try
end sub

sub refreshTraktToken()
    if cfgRead("traktRefresh", "") = "" then return
    expires = Int(Val(cfgRead("traktExpires", "0")))
    if expires > 0 and expires - nowEpoch() > 2 * 86400 then return
    t = CreateObject("roSGNode", "TraktTask")
    t.request = { kind: "refresh", clientId: cfgRead("traktId", ""), clientSecret: cfgRead("traktSecret", ""), refresh: cfgRead("traktRefresh", "") }
    t.observeField("result", "onTraktRefreshed")
    t.control = "run"
    m.traktRefreshTask = t
end sub

sub onTraktRefreshed_x(event as object)
    res = event.getData()
    if res.ok
        cfgWrite("traktToken", res.token)
        cfgWrite("traktRefresh", res.refresh)
        cfgWrite("traktExpires", strOr(res.expires))
    else
        logEvent("Trakt: " + strOr(res.error))
    end if
end sub
sub onTraktRefreshed(event as object)
    try
        onTraktRefreshed_x(event)
    catch e
        reportCrash("MainScene.onTraktRefreshed", e)
    end try
end sub

sub onScrobble_x(event as object)
    d = event.getData()
    token = cfgRead("traktToken", "")
    if d = invalid or token = "" then return
    t = CreateObject("roSGNode", "TraktTask")
    t.request = { kind: "scrobble", clientId: cfgRead("traktId", ""), token: token, videoId: d.videoId, type: d.type, progress: d.progress, action: "stop" }
    t.observeField("result", "onPushDone")
    m.pushTasks.Push(t)
    t.control = "run"
end sub
sub onScrobble(event as object)
    try
        onScrobble_x(event)
    catch e
        reportCrash("MainScene.onScrobble", e)
    end try
end sub

' ---------- Atualizações ----------

function versionNewer(remote as string, mine as string) as boolean
    a = remote.Split(".")
    b = mine.Split(".")
    n = a.Count()
    if b.Count() > n then n = b.Count()
    for i = 0 to n - 1
        x = 0
        y = 0
        if i < a.Count() then x = Int(Val(a[i]))
        if i < b.Count() then y = Int(Val(b[i]))
        if x > y then return true
        if x < y then return false
    end for
    return false
end function

sub checkUpdate(manual as boolean)
    m.updateManual = manual
    m.updateTask = startAddonTask({ kind: "json", url: cfgRead("updateUrl", "") }, "onUpdateInfo")
end sub

sub onUpdateInfo_x(event as object)
    res = event.getData()
    d = res.data
    if d = invalid or strOr(d.version) = ""
        if m.updateManual then showInfo("Atualizações", "Não foi possível ler o endereço de atualizações.")
        return
    end if
    if versionNewer(strOr(d.version), appVersion())
        showInfo("Nova versão disponível: " + strOr(d.version), strOr(d.notes) + Chr(10) + Chr(10) + "Instala-a pelo navegador, no endereço da Roku (modo de programador), com «Replace».")
    else if m.updateManual
        showInfo("Atualizações", "Já tens a versão mais recente (" + appVersion() + ").")
    end if
end sub
sub onUpdateInfo(event as object)
    try
        onUpdateInfo_x(event)
    catch e
        reportCrash("MainScene.onUpdateInfo", e)
    end try
end sub

sub showInfo(title as string, text as string)
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = title
    dlg.message = [text]
    dlg.buttons = ["OK"]
    dlg.observeFieldScoped("buttonSelected", "onDialogClose")
    m.top.dialog = dlg
end sub

' Mostra o erro apanhado (para me enviares uma foto)
sub onCrash()
    msg = m.global.crash
    if msg = invalid or msg = "" then return
    if m.intro <> invalid
        m.top.removeChild(m.intro)
        m.intro = invalid
    end if
    if m.crashShown = true then return
    m.crashShown = true
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = "Ocorreu um erro"
    dlg.message = [msg, "A app continua a funcionar. Tira uma foto deste aviso e envia-ma para eu corrigir."]
    dlg.buttons = ["OK"]
    dlg.observeFieldScoped("buttonSelected", "onDialogClose")
    m.top.dialog = dlg
end sub

' Se o foco se perder (nenhum elemento focado), as teclas deixam de funcionar: recupera-o
sub onFocusGuard()
    try
        lost = not m.top.isInFocusChain() and m.top.dialog = invalid
        if lost
            if m.player <> invalid and m.player.mode = "full"
                m.player.setFocus(true)
            else if m.stack.Count() > 0
                m.stack.Peek().setFocus(true)
            end if
        end if
    catch e
        reportCrash("MainScene.onFocusGuard", e)
    end try
end sub

sub onGuardNotice()
    try
        if m.top.dialog <> invalid then return
        dlg = CreateObject("roSGNode", "StandardMessageDialog")
        dlg.title = "A app fechou da última vez"
        dlg.message = ["A Roku não conseguiu tocar a última fonte e fechou a app (acontece com alguns ficheiros MKV, áudio DTS ou HEVC especial).", "Essa fonte passa a ficar no fim da lista e não volta a ser escolhida automaticamente."]
        dlg.buttons = ["OK"]
        dlg.observeFieldScoped("buttonSelected", "onDialogClose")
        m.top.dialog = dlg
    catch e
        reportCrash("MainScene.onGuardNotice", e)
    end try
end sub

' Estado da conta TorBox: avisa (no máximo uma vez por dia) se a chave falhar ou o plano não servir
sub onTbMe(event as object)
    try
        res = event.getData()
        d = res.data
        if d = invalid then return
        msg = ""
        data = d.data
        if d.success = false or data = invalid
            detail = LCase(strOr(d.detail) + " " + strOr(d.error))
            if Instr(1, detail, "auth") > 0 or Instr(1, detail, "token") > 0 then msg = "A chave do TorBox foi recusada. Volta a introduzi-la em Definições."
        else
            plan = Int(numOr(data.plan, -1))
            expires = 0
            exp = strOr(data.premium_expires_at)
            if exp <> ""
                dt = CreateObject("roDateTime")
                dt.FromISO8601String(Left(exp, 19))
                expires = dt.AsSeconds()
            end if
            now = CreateObject("roDateTime").AsSeconds()
            if plan = 0
                msg = "A tua conta TorBox está no plano gratuito: muitas fontes não tocam ou ficam por descarregar."
            else if expires > 0 and expires < now
                msg = "A tua subscrição TorBox terminou. Renova em torbox.app para as fontes voltarem a funcionar."
            else if expires > 0 and expires - now < 5 * 86400
                msg = "A tua subscrição TorBox termina em " + Int((expires - now) / 86400).ToStr() + " dia(s)."
            end if
        end if
        if msg = "" then return
        day = Int(CreateObject("roDateTime").AsSeconds() / 86400).ToStr()
        if cfgRead("tbWarnDay", "") = day then return
        cfgWrite("tbWarnDay", day)
        showInfo("Conta TorBox", msg)
    catch e
        reportCrash("MainScene.onTbMe", e)
    end try
end sub

' Servidor de sincronização com a app Android (porta 8889, só com o código de 4 dígitos)
sub startSyncServer()
    try
        if cfgRead("syncCode", "") = "" then cfgWrite("syncCode", (1000 + Rnd(8999)).ToStr())
        m.syncServer = CreateObject("roSGNode", "ServerTask")
        m.syncServer.port = 8889
        m.syncServer.syncOnly = true
        m.syncServer.observeField("changed", "onSyncChanged")
        m.syncServer.control = "run"
    catch e
        reportCrash("MainScene.startSyncServer", e)
    end try
end sub

sub onSyncChanged()
    try
        if m.home <> invalid then m.home.refreshLocal = true
        logEvent("Sincronizado com a app Android")
    catch e
        reportCrash("MainScene.onSyncChanged", e)
    end try
end sub

sub onTbTimer()
    try
        w = cfgJsonArray("tbWatch")
        if w.Count() = 0 or cfgRead("torboxKey", "") = "" then return
        hashes = []
        for each x in w
            hashes.Push(strOr(x.h))
        end for
        m.tbCheckTask = startAddonTask({ kind: "tbcheck", key: cfgRead("torboxKey", ""), hashes: hashes }, "onTbChecked")
    catch e
        reportCrash("MainScene.onTbTimer", e)
    end try
end sub

sub onTbChecked(event as object)
    try
        res = event.getData()
        if not res.ok then return
        ready = {}
        for each h in res.ready
            ready[h] = true
        end for
        keep = []
        names = []
        now = CreateObject("roDateTime").AsSeconds()
        for each x in cfgJsonArray("tbWatch")
            if ready.DoesExist(strOr(x.h))
                names.Push(strOr(x.t))
            else if now - Int(numOr(x.a, now)) < 2 * 86400
                keep.Push(x)
            end if
        end for
        cfgWrite("tbWatch", FormatJson(keep))
        if names.Count() > 0
            logEvent("TorBox pronto: " + names.Join(", "))
            if m.player = invalid then showInfo("Pronto para ver", names.Join(", ") + Chr(10) + Chr(10) + "O TorBox já descarregou. Abre o título e carrega em «Ver».")
        end if
    catch e
        reportCrash("MainScene.onTbChecked", e)
    end try
end sub

' ---------- «Enviar para a TV»: abrir um título vindo do telemóvel (ECP launch/input) ----------
' contentId = id do título (ex.: tt1234567) ou do episódio (tt1234567:1:2); mediaType = movie | series | episode
sub onLaunchArgs_x()
    a = m.top.launchArgs
    if a = invalid then return
    cid = strOr(a.contentId)
    if cid = "" then return
    mt = LCase(strOr(a.mediaType))
    parts = cid.Split(":")
    metaId = parts[0]
    t = "movie"
    if mt = "series" or mt = "episode" or mt = "season" or parts.Count() >= 3 then t = "series"
    item = { id: metaId, type: t, name: strOr(a.title), poster: strOr(a.poster), autoPlay: true }
    if parts.Count() >= 3 then item.autoVideo = cid
    ' se já estiver a tocar alguma coisa, fecha primeiro
    if m.player <> invalid and m.player.visible then m.player.command = "close"
    details = CreateObject("roSGNode", "DetailsScreen")
    details.observeField("playRequest", "onPlayRequest")
    details.observeField("pushJson", "onPushJson")
    details.observeField("itemSelected", "onItemSelected")
    details.observeField("personSelected", "onPersonSelected")
    tagNav(details, { k: "details", item: navItem(item) })
    pushScreen(details)
    details.item = item
end sub
sub onLaunchArgs()
    try
        onLaunchArgs_x()
    catch e
        reportCrash("MainScene.onLaunchArgs", e)
    end try
end sub

sub onWelcomeDone()
    try
        popScreen()
        if m.home <> invalid then m.home.refreshLocal = true
    catch e
        reportCrash("MainScene.onWelcomeDone", e)
    end try
end sub

sub onRemTimer()
    try
        ' de 30 em 30 s, com um vídeo aberto: guarda o ponto (se a TV desligar, a app volta aqui)
        if m.player <> invalid
            m.player.command = "save"
            md = m.player.media
            if md <> invalid and strOr(md.videoId) + strOr(md.url) <> strOr(m.navSig) then saveNav()
        end if
        due = dueReminders()
        if due.Count() = 0 or m.top.dialog <> invalid then return
        r = due[0]
        m.remDue = r
        dlg = CreateObject("roSGNode", "StandardMessageDialog")
        dlg.title = "A começar: " + strOr(r.t)
        dlg.message = ["Em " + strOr(r.c) + "."]
        dlg.buttons = ["Ver agora", "Ignorar"]
        dlg.observeFieldScoped("buttonSelected", "onRemDialog")
        m.top.dialog = dlg
    catch e
        reportCrash("MainScene.onRemTimer", e)
    end try
end sub

sub onRemDialog()
    try
        dlg = m.top.dialog
        idx = -1
        if dlg <> invalid
            idx = dlg.buttonSelected
            dlg.close = true
        end if
        r = m.remDue
        if idx = 0 and r <> invalid
            startMedia({ url: strOr(r.u), title: strOr(r.c), format: guessStreamFormat(strOr(r.u), ""), headers: [], kind: "live", channels: [{ name: strOr(r.c), url: strOr(r.u), e: "Agora: " + strOr(r.t) }], chIndex: 0, epgNow: "Agora: " + strOr(r.t) })
        end if
    catch e
        reportCrash("MainScene.onRemDialog", e)
    end try
end sub

' ---------- Modo ambiente (dentro da app, aceita o comando) ----------

sub onAmbTimer()
    try
        mins = Int(Val(cfgRead("ambientMin", "5")))
        if mins <= 0 or m.ambient <> invalid then return
        if m.player <> invalid then return
        if m.top.dialog <> invalid then return
        idle = CreateObject("roDeviceInfo").TimeSinceLastKeypress()
        if idle < mins * 60 then return
        a = CreateObject("roSGNode", "AmbientView")
        a.interactive = true
        a.observeField("chosen", "onAmbChosen")
        a.observeField("dismiss", "onAmbDismiss")
        m.ambient = a
        m.top.appendChild(a)
        a.setFocus(true)
    catch e
        reportCrash("MainScene.onAmbTimer", e)
    end try
end sub

sub closeAmbient()
    if m.ambient = invalid then return
    m.top.removeChild(m.ambient)
    m.ambient = invalid
    focusTopScreen()
end sub

sub onAmbDismiss()
    try
        closeAmbient()
    catch e
        reportCrash("MainScene.onAmbDismiss", e)
    end try
end sub

sub onAmbChosen(event as object)
    try
        it = event.getData()
        closeAmbient()
        if it = invalid or strOr(it.id) = "" then return
        mt = strOr(it.type)
        if mt = "" then mt = "movie"
        m.top.launchArgs = { contentId: strOr(it.id), mediaType: mt, title: strOr(it.name) }
    catch e
        reportCrash("MainScene.onAmbChosen", e)
    end try
end sub

' ---------- Voltar onde estavas ----------
' Retoma Instantânea (Roku OS mantém a app em memória): customSuspend/customResume.
' Se a Roku fechar a app (pouca memória, TV desligada), o estado guardado em cachefs reabre os mesmos
' ecrãs e o vídeo no mesmo ponto (até 6 h depois).

function navPath() as string
    return "cachefs:/nav_state.json"
end function

sub tagNav(node as object, desc as object)
    if not node.hasField("navState") then node.addField("navState", "assocarray", false)
    node.navState = desc
end sub

function navItem(item as dynamic) as object
    if item = invalid then return {}
    return { id: strOr(item.id), type: strOr(item.type), name: strOr(item.name), poster: strOr(item.poster) }
end function

sub saveNav()
    if boolOr(m.restoring) or not boolOr(m.navReady) then return
    if cfgRead("restoreState", "1") <> "1" then return
    st = { t: nowEpoch(), stack: [] }
    for each n in m.stack
        if n.hasField("navState")
            d = n.navState
            if d <> invalid and d.k <> invalid then st.stack.Push(d)
        end if
    end for
    if m.player <> invalid
        md = m.player.media
        if md <> invalid and strOr(md.url) <> "" then st.player = md
    end if
    m.navSig = ""
    if st.player <> invalid then m.navSig = strOr(st.player.videoId) + strOr(st.player.url)
    if st.stack.Count() = 0 and st.player = invalid
        clearNav()
        return
    end if
    js = FormatJson(st)
    if js = "" and st.player <> invalid
        ' algum campo não se deixa guardar: fica o essencial do vídeo
        md = st.player
        st.player = { url: md.url, title: md.title, format: md.format, kind: md.kind, metaId: md.metaId, metaType: md.metaType, metaName: md.metaName, poster: md.poster, videoId: md.videoId, streamName: md.streamName, subsId: md.videoId }
        js = FormatJson(st)
    end if
    if js <> "" then WriteAsciiFile(navPath(), js)
end sub

sub clearNav()
    fs = CreateObject("roFileSystem")
    if fs.Exists(navPath()) then fs.Delete(navPath())
end sub

function restoreNav() as boolean
    if cfgRead("restoreState", "1") <> "1" then return false
    raw = ReadAsciiFile(navPath())
    if raw = "" then return false
    clearNav()
    st = ParseJson(raw)
    if st = invalid or type(st) <> "roAssociativeArray" then return false
    age = nowEpoch() - Int(numOr(st.t, 0))
    if age < 0 or age > 6 * 3600 then return false
    m.restoring = true
    got = false
    if st.stack <> invalid
        for each d in st.stack
            k = strOr(d.k)
            if k = "details" and d.item <> invalid and strOr(d.item.id) <> ""
                it = d.item
                if strOr(it.type) = "" then it.type = "movie"
                details = CreateObject("roSGNode", "DetailsScreen")
                details.observeField("playRequest", "onPlayRequest")
                details.observeField("pushJson", "onPushJson")
                details.observeField("itemSelected", "onItemSelected")
                details.observeField("personSelected", "onPersonSelected")
                tagNav(details, { k: "details", item: it })
                pushScreen(details)
                details.item = it
                got = true
            else if k = "menu" and strOr(d.n) <> "" and strOr(d.n) <> "settings"
                openMenuScreen(strOr(d.n))
                got = true
            else if k = "person" and strOr(d.n) <> ""
                s = CreateObject("roSGNode", "SearchScreen")
                s.observeField("itemSelected", "onItemSelected")
                s.observeField("closeRequest", "onCloseRequest")
                tagNav(s, { k: "person", n: strOr(d.n) })
                pushScreen(s)
                s.query = strOr(d.n)
                s.start = true
                got = true
            end if
        end for
    end if
    ' o vídeo só volta se a app não tiver fechado por causa dele (ver checkPlayGuard)
    req = st.player
    if req <> invalid and type(req) = "roAssociativeArray" and adultLocked() and isAdultText(strOr(req.title)) then req = invalid
    if req <> invalid and type(req) = "roAssociativeArray" and strOr(req.url) <> "" and m.guardTimer = invalid
        if strOr(req.kind) <> "live" then req.autoResume = true
        logEvent("A voltar ao que estavas a ver: " + strOr(req.title))
        startMedia(req)
        got = true
    end if
    m.restoring = false
    return got
end function

function customSuspend(arg as dynamic)
    try
        if m.player <> invalid then m.player.command = "suspend"
        saveNav()
    catch e
        reportCrash("MainScene.customSuspend", e)
    end try
end function

function customResume(arg as dynamic)
    try
        if m.player <> invalid then m.player.command = "resume"
        if arg <> invalid and arg.launchParams <> invalid
            lp = arg.launchParams
            if lp.contentId <> invalid then m.top.launchArgs = { contentId: lp.contentId, mediaType: lp.mediaType }
        end if
        m.top.signalBeacon("AppResumeComplete")
    catch e
        reportCrash("MainScene.customResume", e)
    end try
end function
