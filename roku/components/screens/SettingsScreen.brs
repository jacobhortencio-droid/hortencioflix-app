' Definições: conta Stremio, TorBox, legendas, filtros de qualidade e add-ons

sub init_x()
    m.list = m.top.findNode("list")
    m.header = m.top.findNode("header")
    m.help = m.top.findNode("help")
    m.list.observeField("itemSelected", "onSelected")
    m.header.color = accentColor()
    m.menu = m.top.findNode("menu")
    m.menu.observeField("selected", "onMenuSelected")
    m.menu.observeField("cancelled", "closeMenu")
    m.top.observeField("focusedChild", "onFocusedChild")
    m.mode = "main"
    render()
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("SettingsScreen.init", e)
    end try
end sub

sub onFocusedChild_x()
    if m.top.hasFocus()
        if m.menu.visible
            m.menu.setFocus(true)
        else
            m.list.setFocus(true)
        end if
    end if
end sub
sub onFocusedChild()
    try
        onFocusedChild_x()
    catch e
        reportCrash("SettingsScreen.onFocusedChild", e)
    end try
end sub

function yesNo(v as string) as string
    if v = "1" then return "Sim"
    return "Não"
end function

function qualityLabel(v as string) as string
    if v = "2160" then return "4K"
    if v = "1080" then return "1080p"
    if v = "720" then return "720p"
    return "Todas"
end function

sub render_x()
    focus = m.list.itemFocused
    items = []
    m.actions = []
    if m.mode = "main"
        m.header.text = "Definições"
        items.Push("Perfil: " + strOr(currentProfile().n) + "  (mudar / criar)")
        m.actions.Push("profile")
        items.Push("Configurar pelo telemóvel (código QR)")
        m.actions.Push("phone")
        items.Push("Sincronizar com a app Android: código " + cfgRead("syncCode", "—"))
        m.actions.Push("syncInfo")
        if stremioLinked()
            email = cfgRead("stremioEmail", "")
            if email = "" then email = "ligada"
            items.Push("Conta Stremio: " + email)
            m.actions.Push("stremio")
        else
            items.Push("Ligar conta Stremio (código no telemóvel)")
            m.actions.Push("link")
            items.Push("Entrar no Stremio com e-mail e palavra-passe")
            m.actions.Push("login")
        end if
        items.Push("Enviar progresso para o Stremio: " + yesNo(cfgRead("syncProgress", "1")))
        m.actions.Push("syncProgress")
        items.Push("Chave TorBox: " + mask(cfgRead("torboxKey", "")))
        m.actions.Push("torbox")
        tr = "não ligado"
        if cfgRead("traktToken", "") <> "" then tr = "ligado"
        items.Push("Trakt: " + tr)
        m.actions.Push("trakt")
        pl = "precisa da chave TMDB ou da conta Trakt"
        if cfgRead("tmdbKey", "") <> "" or cfgRead("traktId", "") <> "" then pl = yesNo(cfgRead("ptDesc", "1"))
        items.Push("Sinopses em português: " + pl)
        m.actions.Push("ptDesc")
        items.Push("Chave TMDB (sinopses em português e filmografias): " + mask(cfgRead("tmdbKey", "")))
        m.actions.Push("tmdb")
        items.Push("Idioma das legendas: " + subLangLabel(subLangPref()))
        m.actions.Push("subLang")
        items.Push("Qualidade máxima das fontes: " + qualityLabel(cfgRead("maxQ", "0")))
        m.actions.Push("maxQ")
        items.Push("Só fontes já em cache no TorBox: " + yesNo(cfgRead("cachedOnly", "0")))
        m.actions.Push("cachedOnly")
        dp = cfgRead("debridPref", "auto")
        dpl = "automático (TorBox)"
        if dp = "1" then dpl = "sim"
        if dp = "0" then dpl = "não"
        items.Push("Tenho conta debrid (fontes debrid primeiro): " + dpl)
        m.actions.Push("debridPref")
        items.Push("Esconder fontes DV sem HDR / AV1 / 3D: " + yesNo(cfgRead("hideRisky", "0")))
        m.actions.Push("hideRisky")
        items.Push("Idioma preferido nas fontes: " + audioLangLabel(cfgRead("audioLang", "any")))
        m.actions.Push("audioLang")
        ad = cfgRead("animDub", "any")
        al = "Português (Portugal ou Brasil)"
        if ad = "pt" then al = "Português de Portugal"
        if ad = "br" then al = "Português do Brasil"
        if ad = "off" then al = "Não (como os outros filmes)"
        items.Push("Animação: preferir dobrado: " + al)
        m.actions.Push("animDub")
        dp2 = cfgRead("dubPref", "")
        dl = "Não"
        if dp2 = "br" then dl = "Português do Brasil"
        if dp2 = "pt" then dl = "Português de Portugal"
        items.Push("Preferir áudio dobrado: " + dl)
        m.actions.Push("dubPref")
        rp = cfgRead("rangePref", "auto")
        rl = "Automático (o melhor que esta TV mostra)"
        if rp = "dv" then rl = "Dolby Vision"
        if rp = "hdr" then rl = "HDR"
        if rp = "sdr" then rl = "SDR (sem HDR)"
        if rp = "off" then rl = "Indiferente"
        items.Push("Preferir imagem: " + rl)
        m.actions.Push("rangePref")
        nh = homeHidden().Count()
        hl = "escolher filas e ordem"
        if nh > 0 then hl = nh.ToStr() + " fila(s) escondida(s)"
        items.Push("Organizar o Início: " + hl)
        m.actions.Push("homeLayout")
        items.Push("Saltar introdução automaticamente: " + yesNo(cfgRead("autoSkip", "0")))
        m.actions.Push("autoSkip")
        qa = cfgRead("qualAuto", "ask")
        ql = "perguntar"
        if qa = "auto" then ql = "mudar sozinha"
        if qa = "off" then ql = "nunca"
        items.Push("Rede lenta, baixar a qualidade: " + ql)
        m.actions.Push("qualAuto")
        items.Push("«A seguir» no fim do filme (sugestões nos créditos): " + yesNo(cfgRead("postPlay", "1")))
        m.actions.Push("postPlay")
        am = cfgRead("ambientMin", "5")
        aml = "ao fim de " + am + " min sem mexer"
        if am = "0" then aml = "desligado"
        items.Push("Modo ambiente (imagens dos filmes; OK para ver): " + aml)
        m.actions.Push("ambientMin")
        items.Push("Fundo animado na página do título: " + yesNo(cfgRead("trailerAuto", "1")))
        m.actions.Push("trailerAuto")
        items.Push("Tema de cor: " + themeName())
        m.actions.Push("theme")
        st = "Escuro"
        if glassMode() then st = "Vidro (liquid glass)"
        items.Push("Aspeto: " + st)
        m.actions.Push("uiStyle")
        items.Push("Animação de abertura: " + yesNo(cfgRead("introAnim", "1")))
        m.actions.Push("introAnim")
        items.Push("Voltar onde estavas ao abrir: " + yesNo(cfgRead("restoreState", "1")))
        m.actions.Push("restoreState")
        items.Push("Adicionar add-on (URL)")
        m.actions.Push("addAddon")
        items.Push("Gerir add-ons locais (" + getUserAddons().Count().ToStr() + ")")
        m.actions.Push("listAddons")
        pinTxt = "não definido"
        if parentalPin() <> "" then pinTxt = "definido"
        items.Push("PIN parental: " + pinTxt)
        m.actions.Push("pin")
        items.Push("Proteger canais adultos com PIN: " + yesNo(cfgRead("adultLock", "0")))
        m.actions.Push("adultLock")
        items.Push("Limpar «Continuar a ver» local")
        m.actions.Push("clearProgress")
        items.Push("Diagnóstico")
        m.actions.Push("diag")
        items.Push("Procurar atualizações")
        m.actions.Push("update")
        items.Push("Repor predefinições")
        m.actions.Push("reset")

        help = "Hortêncioflix v" + appVersion() + Chr(10) + Chr(10)
        if stremioLinked()
            help = help + "Conta Stremio ligada: " + stremioAddons().Count().ToStr() + " add-ons e " + libAll().Count().ToStr() + " itens da biblioteca sincronizados. Os add-ons gerem-se na app/site do Stremio e passam para aqui ao abrir a app." + Chr(10) + Chr(10)
        else
            help = help + "Liga a tua conta Stremio para trazer automaticamente os teus add-ons, a biblioteca e o «Continuar a ver»." + Chr(10) + Chr(10)
        end if
        help = help + "Legendas: escolhidas automaticamente no idioma definido; muda durante o vídeo com a tecla *." + Chr(10) + Chr(10)
        help = help + "Dica: para escrever mais depressa usa o teclado da app Roku no telemóvel."
        m.help.text = help
    else if m.mode = "addons"
        m.header.text = "Add-ons locais — OK para remover"
        m.addonList = getUserAddons()
        for each a in m.addonList
            items.Push(a)
            m.actions.Push("removeAddon")
        end for
        if items.Count() = 0
            items.Push("(nenhum add-on local)")
            m.actions.Push("none")
        end if
        n = stremioAddons().Count()
        if n > 0
            items.Push("+ " + n.ToStr() + " add-ons da conta Stremio (gerir no Stremio)")
            m.actions.Push("none")
        end if
        if cfgRead("torboxKey", "") <> ""
            items.Push("+ Torrentio com TorBox (automático pela chave, se a tua conta não o tiver)")
            m.actions.Push("none")
        end if
        m.help.text = "Estes add-ons ficam só nesta Roku. Os da conta Stremio aparecem automaticamente."
    end if
    m.list.content = labelContent(items)
    if focus > 0 and focus < items.Count() then m.list.jumpToItem = focus
end sub
sub render()
    try
        render_x()
    catch e
        reportCrash("SettingsScreen.render", e)
    end try
end sub

sub cycle(key as string, values as object, def as string)
    cur = cfgRead(key, def)
    nxt = values[0]
    for i = 0 to values.Count() - 1
        if values[i] = cur and i + 1 < values.Count() then nxt = values[i + 1]
    end for
    cfgWrite(key, nxt)
end sub

sub onSelected_x()
    idx = m.list.itemSelected
    action = m.actions[idx]
    if action = "link"
        m.top.action = "link"
    else if action = "login"
        askText("Stremio — e-mail", "E-mail da tua conta Stremio", "", "email", false)
    else if action = "stremio"
        dlg = CreateObject("roSGNode", "StandardMessageDialog")
        dlg.title = "Conta Stremio"
        dlg.message = [cfgRead("stremioEmail", "")]
        dlg.buttons = ["Sincronizar agora", "Terminar sessão", "Cancelar"]
        dlg.observeFieldScoped("buttonSelected", "onAccountDialog")
        m.top.getScene().dialog = dlg
    else if action = "syncProgress"
        cycle("syncProgress", ["1", "0"], "1")
        render()
    else if action = "torbox"
        askText("Chave API do TorBox", "Encontras a chave em torbox.app → Settings → API Key. Deixa vazio para remover.", cfgRead("torboxKey", ""), "torbox", false)
    else if action = "subLang"
        cycle("subLang", ["por", "eng", "spa", "fre", "off"], "por")
        render()
    else if action = "maxQ"
        cycle("maxQ", ["0", "2160", "1080", "720"], "0")
        render()
    else if action = "cachedOnly"
        cycle("cachedOnly", ["0", "1"], "0")
        render()
    else if action = "debridPref"
        cycle("debridPref", ["auto", "1", "0"], "auto")
        render()
    else if action = "hideRisky"
        cycle("hideRisky", ["0", "1"], "0")
        render()
    else if action = "audioLang"
        cycle("audioLang", ["any", "pt", "en", "es", "fr", "it"], "any")
        render()
    else if action = "homeLayout"
        openHomeLayout(0)
    else if action = "ptDesc"
        if cfgRead("tmdbKey", "") = "" and cfgRead("traktId", "") = ""
            showMessage("Sinopses em português", "Os catálogos só trazem a sinopse em inglês. Para a ter em português, cria uma chave grátis em themoviedb.org (Definições › API) e cola-a em «Configurar pelo telemóvel» › Chave TMDB. Se tiveres o Trakt ligado, já funciona.")
        else
            cycle("ptDesc", ["1", "0"], "1")
            render()
        end if
    else if action = "qualAuto"
        cycle("qualAuto", ["ask", "auto", "off"], "ask")
        render()
    else if action = "ambientMin"
        cycle("ambientMin", ["5", "3", "10", "0"], "5")
        render()
    else if action = "postPlay" or action = "trailerAuto"
        cycle(action, ["1", "0"], "1")
        render()
    else if action = "autoSkip"
        cycle("autoSkip", ["0", "1"], "0")
        render()
    else if action = "introAnim"
        cycle("introAnim", ["1", "0"], "1")
    else if action = "restoreState"
        cycle("restoreState", ["1", "0"], "1")
        render()
    else if action = "rangePref"
        cycle("rangePref", ["auto", "dv", "hdr", "sdr", "off"], "auto")
        render()
    else if action = "animDub"
        cycle("animDub", ["any", "pt", "br", "off"], "any")
        render()
    else if action = "dubPref"
        cycle("dubPref", ["", "br", "pt"], "")
        render()
    else if action = "uiStyle"
        cycle("uiStyle", ["dark", "glass"], "dark")
        if glassMode()
            m.top.getScene().backgroundURI = "pkg:/images/glass_bg_fhd.jpg"
            glassify(m.top)
        end if
        showMessage("Aspeto", "Para aplicar em todos os ecrãs, fecha e volta a abrir a Hortêncioflix.")
        render()
    else if action = "theme"
        keys = []
        for each t in themeList()
            keys.Push(t.k)
        end for
        cycle("theme", keys, "red")
        m.global.accent = ""
        m.global.accent = accentColor()
        m.header.color = accentColor()
        m.top.changed = true
        render()
    else if action = "addAddon"
        askText("Adicionar add-on", "Cola o URL do manifest.json (ex.: https://…/manifest.json ou stremio://…)", "https://", "addon", false)
    else if action = "listAddons"
        m.mode = "addons"
        render()
    else if action = "removeAddon"
        m.addonList.Delete(idx)
        setUserAddons(m.addonList)
        m.top.changed = true
        render()
    else if action = "clearProgress"
        cfgDelete(pkey("progress"))
        m.top.changed = true
        showMessage("Continuar a ver", "O progresso local foi apagado. (O da conta Stremio mantém-se.)")
    else if action = "profile"
        openProfileMenu()
    else if action = "syncInfo"
        showMessage("Sincronizar com a app Android", "Na app Android: Definições › Atualizações › Roku. Indica o IP " + localIp() + " e o código " + cfgRead("syncCode", "") + "." + Chr(10) + Chr(10) + "O progresso («Continuar a ver»), os episódios vistos, as listas IPTV e os favoritos passam a ser iguais nos dois aparelhos sempre que abres a app Android ou acabas de ver algo. A Hortêncioflix tem de estar aberta na Roku.")
    else if action = "phone"
        m.top.action = "phone"
    else if action = "diag"
        m.top.action = "diag"
    else if action = "update"
        if cfgRead("updateUrl", "") = ""
            showMessage("Atualizações", "Esta app atualiza-se pela app Android: Definições › Atualizações › «Atualizar a app da Roku agora» (ou liga «Atualizar a Roku automaticamente»). Precisas do IP desta Roku (" + localIp() + ") e da palavra-passe do modo de programador.")
        else
            m.top.action = "checkUpdate"
        end if
    else if action = "trakt"
        if cfgRead("traktToken", "") <> ""
            cfgDelete("traktToken")
            cfgDelete("traktRefresh")
            cfgDelete("traktExpires")
            m.top.changed = true
            render()
            showMessage("Trakt", "Sessão Trakt terminada.")
        else
            m.top.action = "trakt"
        end if
    else if action = "tmdb"
        askText("Chave TMDB", "API Key (v3) de themoviedb.org — opcional, para filmografias de atores", cfgRead("tmdbKey", ""), "tmdb", false)
    else if action = "pin"
        if parentalPin() <> ""
            askPin("Introduz o PIN atual", "pinOld")
        else
            askPin("Escolhe um PIN de 4 dígitos", "pinNew")
        end if
    else if action = "adultLock"
        if parentalPin() = ""
            showMessage("Controlo parental", "Define primeiro o PIN parental.")
        else if cfgRead("adultLock", "0") = "1"
            ' desligar a proteção pede o PIN (senão qualquer pessoa a desligava)
            askPin("PIN parental para desligar a proteção", "pinAdultOff")
        else
            cycle("adultLock", ["0", "1"], "0")
            render()
        end if
    else if action = "reset"
        ' apagar tudo nunca acontece com um só clique: confirmação (e PIN, se houver)
        dlg = CreateObject("roSGNode", "StandardMessageDialog")
        dlg.title = "Repor predefinições?"
        dlg.message = ["Apaga TUDO nesta TV: chave TorBox, contas, add-ons, listas IPTV, perfis, progresso e vistos. Não dá para desfazer.", "Sugestão: faz antes uma cópia de segurança em «Configurar pelo telemóvel»."]
        dlg.buttons = ["Cancelar", "Apagar tudo"]
        dlg.observeFieldScoped("buttonSelected", "onResetConfirm")
        m.resetDlg = dlg
        m.top.getScene().dialog = dlg
    end if
end sub
sub onSelected()
    try
        onSelected_x()
    catch e
        reportCrash("SettingsScreen.onSelected", e)
    end try
end sub

sub onAccountDialog_x()
    dlg = m.top.getScene().dialog
    idx = dlg.buttonSelected
    dlg.close = true
    if idx = 0
        m.top.action = "sync"
        showMessage("Stremio", "A sincronizar add-ons e biblioteca…")
    else if idx = 1
        cfgDelete("stremioKey")
        cfgDelete("stremioEmail")
        m.top.action = "logout"
        render()
    end if
end sub
sub onAccountDialog()
    try
        onAccountDialog_x()
    catch e
        reportCrash("SettingsScreen.onAccountDialog", e)
    end try
end sub

' ---------- Teclado ----------

sub askText(title as string, message as string, text as string, target as string, secure as boolean)
    if secure
        dlg = CreateObject("roSGNode", "KeyboardDialog")
        dlg.title = title + " — " + message
        if dlg.keyboard <> invalid and dlg.keyboard.textEditBox <> invalid then dlg.keyboard.textEditBox.secureMode = true
    else
        dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
        dlg.title = title
        dlg.message = [message]
    end if
    dlg.text = text
    dlg.buttons = ["OK", "Cancelar"]
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
    if idx <> 0 then return
    t = m.askTarget
    if t = "newProfile" or t = "newKidProfile"
        if text = "" then return
        profs = getProfiles()
        pid = "p" + nowEpoch().ToStr()
        profs.Push({ id: pid, n: text, kid: (t = "newKidProfile") })
        cfgWrite("profiles", FormatJson(profs))
        switchProfile(pid)
        return
    else if t = "tmdb"
        cfgWrite("tmdbKey", text)
    else if t = "torbox"
        cfgWrite("torboxKey", text)
        m.top.changed = true
    else if t = "addon"
        url = normalizeAddonUrl(text)
        if Left(LCase(url), 4) <> "http" or Len(url) < 12
            showMessage("URL inválido", "O endereço do add-on deve começar por http(s)://")
            return
        end if
        list = getUserAddons()
        for each a in list
            if a = url
                showMessage("Add-ons", "Esse add-on já está adicionado.")
                return
            end if
        end for
        list.Push(url)
        setUserAddons(list)
        m.top.changed = true
    else if t = "email"
        if text = "" then return
        m.loginEmail = text
        askText("Stremio — palavra-passe", "Palavra-passe", "", "password", true)
        return
    else if t = "password"
        m.help.text = "A entrar no Stremio…"
        m.loginTask = CreateObject("roSGNode", "StremioTask")
        m.loginTask.request = { kind: "login", email: m.loginEmail, password: text }
        m.loginTask.observeField("result", "onLogin")
        m.loginTask.control = "run"
        return
    end if
    render()
end sub
sub onAskDone()
    try
        onAskDone_x()
    catch e
        reportCrash("SettingsScreen.onAskDone", e)
    end try
end sub

sub onLogin_x(event as object)
    res = event.getData()
    if res.ok
        cfgWrite("stremioKey", res.authKey)
        cfgWrite("stremioEmail", strOr(res.email))
        m.top.action = "sync"
        render()
        showMessage("Stremio", "Sessão iniciada. A sincronizar add-ons e biblioteca…")
    else
        render()
        showMessage("Stremio", "Não foi possível entrar: " + strOr(res.error))
    end if
end sub
sub onLogin(event as object)
    try
        onLogin_x(event)
    catch e
        reportCrash("SettingsScreen.onLogin", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if press and key = "back" and m.mode <> "main"
        m.mode = "main"
        render()
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("SettingsScreen.onKeyEvent", e)
    end try
    return handled
end function

' ---------- Perfis ----------

sub openProfileMenu()
    m.profs = getProfiles()
    m.menuActions = []
    items = []
    cur = currentProfileId()
    for each p in m.profs
        mark = "   "
        if strOr(p.id) = cur then mark = "> "
        extra = ""
        if boolOr(p.kid) then extra = "  (infantil)"
        items.Push(mark + strOr(p.n) + extra)
        m.menuActions.Push("switch:" + strOr(p.id))
    end for
    items.Push("+ Novo perfil")
    m.menuActions.Push("new")
    items.Push("+ Novo perfil infantil")
    m.menuActions.Push("newKid")
    if strOr(currentProfile().pin) <> ""
        items.Push("Tirar o PIN deste perfil")
    else
        items.Push("Proteger este perfil com PIN")
    end if
    m.menuActions.Push("ppin")
    if cur <> "main"
        items.Push("- Apagar o perfil atual")
        m.menuActions.Push("delete")
    end if
    openMenu(m.menu, "profiles", "Perfis", items, 0)
end sub

' Organizar o Início: cada fila mostra ✓ (visível) ou ✗ (escondida); escolher troca. No fim: mover para o topo / repor.
sub openHomeLayout(focus as integer)
    known = homeKnown()
    if known.Count() = 0
        showMessage("Organizar o Início", "Abre primeiro o Início para a app conhecer as filas.")
        return
    end if
    hidden = homeHidden()
    items = []
    m.menuActions = []
    for each t in known
        mark = "✓  "
        if hidden.DoesExist(strOr(t)) then mark = "✗  "
        items.Push(mark + strOr(t))
        m.menuActions.Push("hrow:" + strOr(t))
    end for
    items.Push("↑  Escolher a primeira fila…")
    m.menuActions.Push("htop")
    items.Push("Repor o Início")
    m.menuActions.Push("hreset")
    if focus >= items.Count() then focus = 0
    openMenu(m.menu, "homeLayout", "Filas do Início (escolher mostra/esconde)", items, focus)
end sub

sub openHomeTop()
    items = []
    m.menuActions = []
    for each t in homeKnown()
        items.Push(strOr(t))
        m.menuActions.Push("hmove:" + strOr(t))
    end for
    openMenu(m.menu, "homeTop", "Qual fila vem primeiro?", items, 0)
end sub

sub closeMenu_x()
    m.menu.visible = false
    m.list.setFocus(true)
end sub
sub closeMenu()
    try
        closeMenu_x()
    catch e
        reportCrash("SettingsScreen.closeMenu", e)
    end try
end sub

sub onMenuSelected_x()
    action = m.menuActions[m.menu.selected]
    sel = m.menu.selected
    closeMenu()
    if Left(action, 5) = "hrow:"
        toggleHomeRow(Mid(action, 6))
        m.top.changed = true
        openHomeLayout(sel)
        return
    else if action = "htop"
        openHomeTop()
        return
    else if Left(action, 6) = "hmove:"
        moveHomeRowTop(Mid(action, 7))
        m.top.changed = true
        showMessage("Organizar o Início", "«" + Mid(action, 7) + "» passa a ser a primeira fila.")
        render()
        return
    else if action = "hreset"
        resetHomeLayout()
        m.top.changed = true
        render()
        return
    end if
    if Left(action, 7) = "switch:"
        target = Mid(action, 8)
        if target = currentProfileId() then return
        tp = invalid
        for each p in getProfiles()
            if strOr(p.id) = target then tp = p
        end for
        if isKidProfile() and parentalPin() <> ""
            m.pendingProfile = target
            askPin("PIN para sair do perfil infantil", "pinSwitch")
        else if tp <> invalid and strOr(tp.pin) <> ""
            ' perfil protegido: pede o PIN desse perfil
            m.pendingProfile = target
            m.pendingPin = strOr(tp.pin)
            askPin("PIN do perfil «" + strOr(tp.n) + "»", "profPinEnter")
        else
            switchProfile(target)
        end if
    else if action = "ppin"
        if strOr(currentProfile().pin) <> ""
            setProfilePin("")
            showMessage("Perfis", "Este perfil já não pede PIN.")
        else
            askPin("Escolhe um PIN de 4 dígitos para este perfil", "profPinNew")
        end if
    else if action = "new"
        askText("Novo perfil", "Nome do perfil", "", "newProfile", false)
    else if action = "newKid"
        askText("Novo perfil infantil", "Nome do perfil (esconde canais adultos e a biblioteca da conta)", "", "newKidProfile", false)
    else if action = "delete"
        cur = currentProfileId()
        out = []
        for each p in getProfiles()
            if strOr(p.id) <> cur then out.Push(p)
        end for
        cfgWrite("profiles", FormatJson(out))
        for each k in ["progress", "iptvFav", "iptvRecent", "trackPrefs", "watched", "searchHist"]
            cfgDelete(k + "_" + cur)
        end for
        switchProfile("main")
    end if
end sub
sub onMenuSelected()
    try
        onMenuSelected_x()
    catch e
        reportCrash("SettingsScreen.onMenuSelected", e)
    end try
end sub

sub switchProfile(pid as string)
    cfgWrite("profile", pid)
    if m.global.hasField("pinOk") then m.global.pinOk = false
    m.top.action = "profile"
    m.top.changed = true
    render()
    showMessage("Perfil", "Agora estás no perfil «" + strOr(currentProfile().n) + "».")
end sub

' ---------- PIN ----------

sub askPin(message as string, target as string)
    dlg = CreateObject("roSGNode", "StandardPinPadDialog")
    dlg.title = "PIN parental"
    dlg.message = [message]
    dlg.buttons = ["OK", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onPinDone")
    m.pinTarget = target
    m.pinDlg = dlg
    m.top.getScene().dialog = dlg
end sub

sub onPinDone_x()
    dlg = m.pinDlg
    okBtn = dlg.buttonSelected = 0
    pin = strOr(dlg.pin)
    dlg.close = true
    if not okBtn then return
    t = m.pinTarget
    if t = "pinNew"
        if Len(pin) < 4
            showMessage("PIN parental", "O PIN deve ter 4 dígitos.")
            return
        end if
        cfgWrite("pin", pin)
        render()
        showMessage("PIN parental", "PIN definido. Ativa «Proteger canais adultos com PIN» para o usar na TV ao vivo.")
    else if t = "pinOld"
        if pin <> parentalPin()
            showMessage("PIN parental", "PIN errado.")
            return
        end if
        dlg2 = CreateObject("roSGNode", "StandardMessageDialog")
        dlg2.title = "PIN parental"
        dlg2.message = ["O que queres fazer?"]
        dlg2.buttons = ["Mudar o PIN", "Remover o PIN", "Cancelar"]
        dlg2.observeFieldScoped("buttonSelected", "onPinAction")
        m.top.getScene().dialog = dlg2
    else if t = "profPinNew"
        if Len(pin) < 4
            showMessage("Perfis", "O PIN deve ter 4 dígitos.")
            return
        end if
        setProfilePin(pin)
        showMessage("Perfis", "Feito: para entrar neste perfil a partir de outro, pede este PIN.")
    else if t = "profPinEnter"
        if pin = strOr(m.pendingPin)
            switchProfile(m.pendingProfile)
        else
            showMessage("Perfis", "PIN errado.")
        end if
    else if t = "pinAdultOff"
        if pin = parentalPin()
            cfgWrite("adultLock", "0")
            render()
        else
            showMessage("PIN parental", "PIN errado.")
        end if
    else if t = "pinReset"
        if pin = parentalPin()
            doReset()
        else
            showMessage("PIN parental", "PIN errado.")
        end if
    else if t = "pinSwitch"
        if pin = parentalPin()
            switchProfile(m.pendingProfile)
        else
            showMessage("PIN parental", "PIN errado.")
        end if
    end if
end sub
sub onPinDone()
    try
        onPinDone_x()
    catch e
        reportCrash("SettingsScreen.onPinDone", e)
    end try
end sub

sub onPinAction_x()
    dlg = m.top.getScene().dialog
    idx = dlg.buttonSelected
    dlg.close = true
    if idx = 0
        askPin("Escolhe o novo PIN de 4 dígitos", "pinNew")
    else if idx = 1
        cfgDelete("pin")
        cfgWrite("adultLock", "0")
        render()
    end if
end sub
sub onPinAction()
    try
        onPinAction_x()
    catch e
        reportCrash("SettingsScreen.onPinAction", e)
    end try
end sub

sub setProfilePin(pin as string)
    cur = currentProfileId()
    out = []
    for each p in getProfiles()
        if strOr(p.id) = cur then p.pin = pin
        out.Push(p)
    end for
    cfgWrite("profiles", FormatJson(out))
end sub

sub onResetConfirm()
    try
        dlg = m.resetDlg
        idx = dlg.buttonSelected
        dlg.close = true
        if idx <> 1 then return
        if parentalPin() <> ""
            askPin("PIN parental para apagar tudo", "pinReset")
        else
            doReset()
        end if
    catch e
        reportCrash("SettingsScreen.onResetConfirm", e)
    end try
end sub

sub doReset()
    cfgReset()
    m.top.action = "logout"
    m.top.changed = true
    render()
    showMessage("Definições", "As definições foram repostas.")
end sub
