' Hortêncioflix - ponto de entrada
sub Main(args as dynamic)
    applySeed()
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.show()
    scene.observeField("exitApp", port)
    ' «Enviar para a TV»: o telemóvel abre a app com um título (deep link) ou envia-o com a app já aberta
    inp = CreateObject("roInput")
    inp.setMessagePort(port)
    if args <> invalid and args.contentId <> invalid then scene.launchArgs = { contentId: args.contentId, mediaType: args.mediaType }
    while true
        msg = wait(0, port)
        msgType = type(msg)
        if msgType = "roSGScreenEvent"
            if msg.isScreenClosed() then return
        else if msgType = "roSGNodeEvent"
            if msg.getField() = "exitApp" then return
        else if msgType = "roInputEvent"
            if msg.isInput()
                info = msg.getInfo()
                if info <> invalid and info.contentId <> invalid then scene.launchArgs = { contentId: info.contentId, mediaType: info.mediaType, title: info.title }
            end if
        end if
    end while
end sub

' Definições trazidas da app Android (aplicadas uma só vez; nunca substituem o que já mudaste no Roku)
sub applySeed()
    raw = ReadAsciiFile("pkg:/source/seed.json")
    if raw = "" then return
    d = ParseJson(raw)
    if d = invalid or d.pairs = invalid or d.id = invalid then return
    sec = CreateObject("roRegistrySection", "streambox")
    if sec.Exists("seedApplied") and sec.Read("seedApplied") = d.id then return
    ' lista de {k, v}: os nomes das chaves do registo distinguem maiúsculas
    for each p in d.pairs
        if p.k <> invalid and p.v <> invalid and not sec.Exists(p.k) then sec.Write(p.k, p.v)
    end for
    sec.Write("seedApplied", d.id)
    sec.Flush()
end sub
