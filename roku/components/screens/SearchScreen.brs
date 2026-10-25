' Pesquisa: histórico, teclado (com voz) e resultados dos add-ons.
' Também serve para a "filmografia" de um ator (campo query + TMDB opcional).

sub init_x()
    m.top.findNode("header").color = accentColor()
    m.rows = m.top.findNode("rows")
    m.history = m.top.findNode("history")
    m.status = m.top.findNode("status")
    m.header = m.top.findNode("header")
    m.skeleton = m.top.findNode("skeleton")
    m.focusTitle = m.top.findNode("focusTitle")
    m.rows.observeField("rowItemSelected", "onRowSelected")
    m.rows.observeField("rowItemFocused", "onRowFocused")
    m.history.observeField("itemSelected", "onHistorySelected")
    m.top.observeField("focusedChild", "onFocusedChild")
    m.lastQuery = ""
    m.kbd = m.top.findNode("kbd")
    m.histTitle = m.top.findNode("histTitle")
    m.typeTimer = m.top.findNode("typeTimer")
    m.kbd.observeField("text", "onTyping")
    m.typeTimer.observeField("fire", "onTypePause")
    m.focusTarget = m.kbd
end sub

' Pesquisa enquanto escreves: espera 0,7 s sem teclas e procura (2 letras ou mais)
sub onTyping()
    m.typeTimer.control = "stop"
    m.typeTimer.control = "start"
end sub

sub onTypePause()
    try
        t = m.kbd.text.Trim()
        if Len(t) < 2 or t = m.lastQuery then return
        runSearch(t, false)
    catch e
        reportCrash("SearchScreen.onTypePause", e)
    end try
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("SearchScreen.init", e)
    end try
end sub

sub onFocusedChild_x()
    if m.top.hasFocus() then m.focusTarget.setFocus(true)
end sub
sub onFocusedChild()
    try
        onFocusedChild_x()
    catch e
        reportCrash("SearchScreen.onFocusedChild", e)
    end try
end sub

sub onStart_x()
    q = m.top.query
    if q <> ""
        ' página de ator/realizador
        m.header.text = q
        m.top.findNode("hint").text = "Filmografia (com chave TMDB) ou resultados de pesquisa pelo nome"
        m.kbd.visible = false
        m.rows.translation = [80, 210]
        m.skeleton.translation = [80, 210]
        m.focusTitle.translation = [80, 150]
        m.skeleton.visible = true
        m.lastQuery = q
        m.task = startAddonTask({ kind: "person", name: q, tmdbKey: cfgRead("tmdbKey", ""), traktId: cfgRead("traktId", "") }, "onResults")
        return
    end if
    showHistory()
end sub
sub onStart()
    try
        onStart_x()
    catch e
        reportCrash("SearchScreen.onStart", e)
    end try
end sub

sub showHistory()
    hist = getSearchHistory()
    m.focusTarget = m.kbd
    m.kbd.setFocus(true)
    if hist.Count() = 0 then return
    labels = []
    for each h in hist
        if labels.Count() < 6 then labels.Push(strOr(h))
    end for
    m.history.content = labelContent(labels)
    m.history.visible = true
    m.histTitle.visible = true
end sub

sub onHistorySelected_x()
    idx = m.history.itemSelected
    hist = getSearchHistory()
    if idx < hist.Count()
        m.kbd.text = strOr(hist[idx])
        runSearch(strOr(hist[idx]), true)
    end if
end sub
sub onHistorySelected()
    try
        onHistorySelected_x()
    catch e
        reportCrash("SearchScreen.onHistorySelected", e)
    end try
end sub

sub openKeyboard()
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = "Pesquisar filmes e séries"
    dlg.text = m.lastQuery
    dlg.buttons = ["Pesquisar", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onKeyboardButton")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onKeyboardButton_x()
    dlg = m.dialog
    idx = dlg.buttonSelected
    text = dlg.text.Trim()
    dlg.close = true
    if idx = 0 and text <> ""
        m.kbd.text = text
        runSearch(text, true)
    end if
end sub
sub onKeyboardButton()
    try
        onKeyboardButton_x()
    catch e
        reportCrash("SearchScreen.onKeyboardButton", e)
    end try
end sub

sub runSearch(text as string, save as boolean)
    m.lastQuery = text
    if save then addSearchHistory(text)
    m.header.text = "Pesquisar: " + text
    m.rows.visible = true
    m.status.text = ""
    m.focusTitle.text = ""
    m.skeleton.visible = true
    m.rows.content = invalid
    m.task = startAddonTask({ kind: "search", query: text }, "onResults")
end sub

sub onResults_x(event as object)
    res = event.getData()
    storeManifests(res)
    m.skeleton.visible = false
    if res.rows = invalid or res.rows.Count() = 0
        m.status.text = "Sem resultados para " + Chr(34) + m.lastQuery + Chr(34) + "."
        m.status.visible = true
        return
    end if
    m.status.visible = false
    m.rows.visible = true
    m.rows.content = buildRowContent(res.rows)
    ' enquanto se escreve, o foco fica no teclado; só vai para os resultados quando se pede
    if not m.kbd.visible or not m.kbd.isInFocusChain()
        m.focusTarget = m.rows
        m.rows.setFocus(true)
    end if
end sub
sub onResults(event as object)
    try
        onResults_x(event)
    catch e
        reportCrash("SearchScreen.onResults", e)
    end try
end sub

sub onRowSelected_x()
    item = itemFromRowList(m.rows)
    ' a pesquisa só entra nas recentes quando escolhes um resultado
    if item <> invalid and m.lastQuery <> "" and m.kbd.visible then addSearchHistory(m.lastQuery)
    if item <> invalid then m.top.itemSelected = item
end sub
sub onRowSelected()
    try
        onRowSelected_x()
    catch e
        reportCrash("SearchScreen.onRowSelected", e)
    end try
end sub

sub onRowFocused_x()
    m.focusTitle.text = focusedTitle(m.rows)
end sub
sub onRowFocused()
    try
        onRowFocused_x()
    catch e
        reportCrash("SearchScreen.onRowFocused", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    if key = "options"
        openKeyboard()
        return true
    end if
    if not m.kbd.visible then return false
    if key = "right" and m.kbd.isInFocusChain() and m.rows.content <> invalid
        m.focusTarget = m.rows
        m.rows.setFocus(true)
        return true
    else if key = "left" and m.rows.isInFocusChain()
        m.focusTarget = m.kbd
        m.kbd.setFocus(true)
        return true
    else if key = "down" and m.kbd.isInFocusChain() and m.history.visible
        m.focusTarget = m.history
        m.history.setFocus(true)
        return true
    else if key = "up" and m.history.isInFocusChain()
        m.focusTarget = m.kbd
        m.kbd.setFocus(true)
        return true
    else if key = "back" and m.rows.isInFocusChain()
        m.focusTarget = m.kbd
        m.kbd.setFocus(true)
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("SearchScreen.onKeyEvent", e)
    end try
    return handled
end function
