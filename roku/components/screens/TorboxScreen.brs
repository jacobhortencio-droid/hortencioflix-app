sub init_x()
    m.list = m.top.findNode("list")
    m.summary = m.top.findNode("summary")
    m.status = m.top.findNode("status")
    m.spinner = m.top.findNode("spinner")
    m.menu = m.top.findNode("menu")
    m.timer = m.top.findNode("refreshTimer")
    m.top.findNode("header").color = accentColor()
    m.list.observeField("itemSelected", "onSelected")
    m.menu.observeField("selected", "onFileSelected")
    m.menu.observeField("cancelled", "closeMenu")
    m.timer.observeField("fire", "refresh")
    m.top.observeField("focusedChild", "onFocusedChild")
    m.items = []
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("TorboxScreen.init", e)
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
        reportCrash("TorboxScreen.onFocusedChild", e)
    end try
end sub

sub load_x()
    m.key = cfgRead("torboxKey", "")
    if m.key = ""
        m.status.text = "Ainda não definiste a chave TorBox." + Chr(10) + "Vai a Definições → Chave TorBox (encontras a chave em torbox.app → Settings → API Key)."
        return
    end if
    m.spinner.visible = true
    m.spinner.control = "start"
    m.status.text = "A carregar os teus downloads…"
    refresh()
    m.timer.control = "start"
end sub
sub load()
    try
        load_x()
    catch e
        reportCrash("TorboxScreen.load", e)
    end try
end sub

sub refresh_x()
    if m.key = "" then return
    m.task = startAddonTask({ kind: "torbox", key: m.key }, "onLoaded")
end sub
sub refresh()
    try
        refresh_x()
    catch e
        reportCrash("TorboxScreen.refresh", e)
    end try
end sub

function fmtSize(b as float) as string
    if b >= 1073741824
        g = Int(b / 107374182.4) / 10
        return g.ToStr() + " GB"
    end if
    if b >= 1048576 then return Int(b / 1048576).ToStr() + " MB"
    return Int(b / 1024).ToStr() + " KB"
end function

function stateLabel(s as string) as string
    st = LCase(s)
    if st = "downloading" then return "a descarregar"
    if st = "uploading" or st = "seeding" or st = "completed" or st = "cached" then return "pronto"
    if st = "stalled (no seeds)" or Instr(1, st, "stalled") > 0 then return "parado (sem seeds)"
    if st = "paused" then return "em pausa"
    if Instr(1, st, "metadl") > 0 or Instr(1, st, "metadata") > 0 then return "a obter informação"
    return s
end function

sub onLoaded_x(event as object)
    res = event.getData()
    m.spinner.visible = false
    m.spinner.control = "stop"
    if not res.ok
        m.status.text = "Não foi possível ler a TorBox: " + strOr(res.error)
        return
    end if
    m.items = res.items
    focus = m.list.itemFocused
    root = CreateObject("roSGNode", "ContentNode")
    ready = 0
    for each t in m.items
        c = root.createChild("ContentNode")
        c.title = cleanText(t.name)
        pct = Int(numOr(t.progress, 0) * 100)
        line = stateLabel(t.state) + "   ·   " + fmtSize(numOr(t.size, 0))
        if t.finished
            ready = ready + 1
            line = line + "   ·   " + t.files.Count().ToStr() + " ficheiro(s) de vídeo"
            c.shortDescriptionLine1 = "OK"
            c.shortDescriptionLine2 = "cached"
        else
            line = line + "   ·   " + pct.ToStr() + "% concluído"
            c.shortDescriptionLine1 = pct.ToStr() + "%"
            c.shortDescriptionLine2 = ""
        end if
        c.description = line
    end for
    m.list.content = root
    if focus > 0 and focus < m.items.Count() then m.list.jumpToItem = focus
    m.summary.text = m.items.Count().ToStr() + " downloads   ·   " + ready.ToStr() + " prontos a ver"
    if m.items.Count() = 0
        m.status.text = "A tua TorBox está vazia. Quando escolheres uma fonte «[TB download]», o torrent aparece aqui com o progresso."
    else
        m.status.text = ""
    end if
    if not m.menu.visible and m.top.isInFocusChain() then m.list.setFocus(true)
end sub
sub onLoaded(event as object)
    try
        onLoaded_x(event)
    catch e
        reportCrash("TorboxScreen.onLoaded", e)
    end try
end sub

sub onSelected_x()
    idx = m.list.itemSelected
    if idx < 0 or idx >= m.items.Count() then return
    t = m.items[idx]
    if not t.finished
        showMessage(cleanText(t.name), "Ainda está a descarregar (" + Int(numOr(t.progress, 0) * 100).ToStr() + "%). Fica disponível aqui quando terminar.")
        return
    end if
    if t.files.Count() = 0
        showMessage(cleanText(t.name), "Este download não tem ficheiros de vídeo.")
        return
    end if
    m.curTorrent = t
    if t.files.Count() = 1
        playFile(0)
        return
    end if
    labels = []
    for each f in t.files
        labels.Push(cleanText(f.name) + "   (" + fmtSize(numOr(f.size, 0)) + ")")
    end for
    openMenu(m.menu, "files", cleanText(t.name), labels, 0)
end sub
sub onSelected()
    try
        onSelected_x()
    catch e
        reportCrash("TorboxScreen.onSelected", e)
    end try
end sub

sub onFileSelected_x()
    closeMenu()
    playFile(m.menu.selected)
end sub
sub onFileSelected()
    try
        onFileSelected_x()
    catch e
        reportCrash("TorboxScreen.onFileSelected", e)
    end try
end sub

sub playFile(i as integer)
    t = m.curTorrent
    f = t.files[i]
    url = torboxFileUrl(m.key, t.id, f.id)
    m.top.playRequest = {
        url: url,
        title: cleanText(f.name),
        format: guessStreamFormat(f.name, ""),
        headers: [],
        kind: "vod",
        subtitles: [],
        autoSubFind: true,
        subQuery: f.name,
        metaId: "",
        videoId: "torbox:" + t.id + ":" + f.id
    }
end sub

sub closeMenu_x()
    m.menu.visible = false
    m.list.setFocus(true)
end sub
sub closeMenu()
    try
        closeMenu_x()
    catch e
        reportCrash("TorboxScreen.closeMenu", e)
    end try
end sub

sub onClosing_x()
    m.timer.control = "stop"
end sub
sub onClosing()
    try
        onClosing_x()
    catch e
        reportCrash("TorboxScreen.onClosing", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if press and key = "options"
        refresh()
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("TorboxScreen.onKeyEvent", e)
    end try
    return handled
end function
