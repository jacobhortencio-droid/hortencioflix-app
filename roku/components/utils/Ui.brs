' Ajudas de interface partilhadas

function buildRowContent(rows as object) as object
    root = CreateObject("roSGNode", "ContentNode")
    for each r in rows
        row = root.createChild("ContentNode")
        row.title = r.title
        for each it in r.items
            c = row.createChild("ContentNode")
            c.title = strOr(it.name)
            c.HDPosterUrl = strOr(it.poster)
            c.FHDPosterUrl = strOr(it.poster)
            prog = 0.0
            if it.progress <> invalid then prog = it.progress
            c.addFields({ metaId: strOr(it.id), metaType: strOr(it.type), videoId: strOr(it.videoId), progress: prog, bg: strOr(it.bg), logo: strOr(it.logo), desc: strOr(it.desc), info: strOr(it.info) })
        end for
    end for
    return root
end function

function itemFromRowList(rowList as object) as object
    c = rowCell(rowList, rowList.rowItemSelected)
    if c = invalid then return invalid
    return { id: c.metaId, type: c.metaType, name: c.title, poster: c.HDPosterUrl, videoId: c.videoId, bg: c.bg, logo: c.logo }
end function

function focusedTitle(rowList as object) as string
    c = rowCell(rowList, rowList.rowItemFocused)
    if c = invalid then return ""
    return c.title
end function

function labelContent(labels as object) as object
    root = CreateObject("roSGNode", "ContentNode")
    for each l in labels
        c = root.createChild("ContentNode")
        c.title = l
    end for
    return root
end function

sub showMessage(title as string, text as string)
    dlg = CreateObject("roSGNode", "StandardMessageDialog")
    dlg.title = title
    dlg.message = [text]
    dlg.buttons = ["OK"]
    dlg.observeFieldScoped("buttonSelected", "onMessageDialogClose")
    m.top.getScene().dialog = dlg
end sub

sub onMessageDialogClose_x()
    dlg = m.top.getScene().dialog
    if dlg <> invalid then dlg.close = true
end sub
sub onMessageDialogClose()
    try
        onMessageDialogClose_x()
    catch e
        reportCrash("Ui.onMessageDialogClose", e)
    end try
end sub

' Abre um MenuPanel (componente) com uma lista de opções
sub openMenu(panel as object, menuId as string, title as string, items as object, startIndex as integer)
    panel.menuId = menuId
    panel.title = title
    panel.startIndex = startIndex
    panel.items = items
    panel.visible = true
    panel.setFocus(true)
end sub

' Conteúdo do item focado numa RowList (para o destaque do Início)
' Célula [linha, coluna] de uma RowList, sem falhar se o índice não for válido (lista vazia ou acabada de mudar)
function rowCell(rowList as object, f as dynamic) as dynamic
    if rowList = invalid or rowList.content = invalid then return invalid
    if f = invalid or type(f) <> "roArray" or f.Count() < 2 then return invalid
    if f[0] = invalid or f[1] = invalid then return invalid
    r = Int(f[0])
    c = Int(f[1])
    if r < 0 or c < 0 or r >= rowList.content.getChildCount() then return invalid
    row = rowList.content.getChild(r)
    if row = invalid or c >= row.getChildCount() then return invalid
    return row.getChild(c)
end function

function focusedContent(rowList as object) as dynamic
    ' o foco pode ainda não existir (lista vazia ou acabada de ser substituída): nunca falhar
    if rowList = invalid or rowList.content = invalid then return invalid
    f = rowList.rowItemFocused
    if f = invalid or type(f) <> "roArray" or f.Count() < 2 then return invalid
    if f[0] = invalid or f[1] = invalid then return invalid
    r = Int(f[0])
    c = Int(f[1])
    if r < 0 or c < 0 or r >= rowList.content.getChildCount() then return invalid
    row = rowList.content.getChild(r)
    if row = invalid or c >= row.getChildCount() then return invalid
    return row.getChild(c)
end function

' O ecrã está à frente (visível e o leitor não está em ecrã inteiro)?
function screenIsActive() as boolean
    if not m.top.visible then return false
    p = m.top.getParent()
    if p <> invalid and not p.visible then return false
    return true
end function

' Título em foco numa RowList como item (para a tecla ▶)
function itemFromRowFocus(rowList as object) as dynamic
    c = rowCell(rowList, rowList.rowItemFocused)
    if c = invalid then return invalid
    return { id: c.metaId, type: c.metaType, name: c.title, poster: c.HDPosterUrl, videoId: c.videoId, bg: c.bg, logo: c.logo }
end function
