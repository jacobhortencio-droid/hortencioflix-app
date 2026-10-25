sub init_x()
    hdr = m.top.findNode("header")
    if hdr <> invalid then hdr.color = accentColor()
    m.steps = m.top.findNode("steps")
    m.code = m.top.findNode("code")
    m.status = m.top.findNode("status")
    m.qr = m.top.findNode("qr")
    m.createTask = CreateObject("roSGNode", "StremioTask")
    m.createTask.request = { kind: "linkCreate" }
    m.createTask.observeField("result", "onCreated")
    m.createTask.control = "run"
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("StremioLinkScreen.init", e)
    end try
end sub

sub onCreated_x(event as object)
    res = event.getData()
    if not res.ok
        m.status.text = strOr(res.error) + Chr(10) + "Prime Voltar e tenta de novo, ou entra com e-mail nas Definições."
        return
    end if
    link = strOr(res.link)
    if link = "" then link = "https://www.stremio.com/link"
    m.steps.text = "1. No telemóvel ou computador, abre:" + Chr(10) + link + Chr(10) + Chr(10) + "2. Inicia sessão na tua conta Stremio e confirma este código:"
    m.code.text = res.code
    if strOr(res.qr) <> ""
        m.qr.uri = res.qr
    else
        m.top.findNode("qrHint").text = "(código QR indisponível — usa o link)"
    end if
    m.status.text = "A aguardar confirmação… (o código expira em poucos minutos)"
    m.pollTask = CreateObject("roSGNode", "StremioTask")
    m.pollTask.request = { kind: "linkPoll", code: res.code }
    m.pollTask.observeField("result", "onPolled")
    m.pollTask.control = "run"
end sub
sub onCreated(event as object)
    try
        onCreated_x(event)
    catch e
        reportCrash("StremioLinkScreen.onCreated", e)
    end try
end sub

sub onPolled_x(event as object)
    res = event.getData()
    if res.ok
        cfgWrite("stremioKey", res.authKey)
        cfgWrite("stremioEmail", strOr(res.email))
        m.status.text = "Conta ligada! A sincronizar…"
        m.top.linked = true
    else if strOr(res.error) <> "cancelado"
        m.status.text = strOr(res.error)
    end if
end sub
sub onPolled(event as object)
    try
        onPolled_x(event)
    catch e
        reportCrash("StremioLinkScreen.onPolled", e)
    end try
end sub

sub onClosing_x()
    if m.pollTask <> invalid then m.pollTask.cancel = true
end sub
sub onClosing()
    try
        onClosing_x()
    catch e
        reportCrash("StremioLinkScreen.onClosing", e)
    end try
end sub
