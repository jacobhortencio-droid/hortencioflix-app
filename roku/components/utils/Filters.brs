' Filtros das fontes (iguais aos do Android).
' Grupos: q (4K/1440p/1080p/720p/SD), img (anyhdr/dv/hdr10p/hdr10/hlg/sdr), src (remux/bluray/web),
' nocam, lang (br/pt), aud (atmos/lossless/51), cached. Um valor por grupo; todos os grupos têm de bater.
' Usado pela lista de fontes (DetailsScreen) e pela ordenação das fontes (AddonTask).

function fltStr(v as dynamic) as string
    if v = invalid then return ""
    t = type(v)
    if t = "roString" or t = "String" then return v
    if t = "roInt" or t = "Integer" or t = "roFloat" or t = "Float" or t = "Double" or t = "roDouble" or t = "LongInteger" then return Int(v).ToStr()
    return ""
end function

function fltBool(v as dynamic) as boolean
    if v = invalid then return false
    t = type(v)
    if t = "roBoolean" or t = "Boolean" then return v
    return false
end function

function fltQLabel(q as dynamic) as string
    n = 0
    t = type(q)
    if t = "roInt" or t = "Integer" or t = "roFloat" or t = "Float" or t = "Double" or t = "roDouble" or t = "LongInteger" then n = Int(q)
    if n >= 2160 then return "4K"
    if n >= 1440 then return "1440p"
    if n >= 1080 then return "1080p"
    if n >= 720 then return "720p"
    if n > 0 then return "SD"
    return "?"
end function

function fltFacet(s as object, g as string, v as string, caps as dynamic) as boolean
    r = fltStr(s.range)
    if g = "q" then return fltQLabel(s.q) = v
    if g = "img"
        if v = "anyhdr"
            if r = "hdr10" or r = "hdr10p" or r = "hlg" or r = "dvhdr" then return true
            if r = "dv" and caps <> invalid
                if fltBool(caps.dv) then return true
            end if
            return false
        end if
        if v = "dv" then return r = "dv" or r = "dvhdr"
        return r = v
    end if
    if g = "src" then return fltStr(s.src) = v
    if g = "nocam" then return fltStr(s.src) <> "cam"
    if g = "lang"
        d = fltStr(s.dub)
        if d = "" then return false
        return d = v or d = "por"
    end if
    if g = "aud"
        if v = "atmos" then return fltBool(s.atmos)
        if v = "lossless" then return fltBool(s.lossless)
        if v = "51" then return fltBool(s.surround)
        return true
    end if
    if g = "cached" then return fltBool(s.cached)
    return true
end function

' A fonte cumpre todos os filtros (opcionalmente ignorando um grupo, para contar as opções desse grupo)
function fltPasses(s as object, f as dynamic, except as string, caps as dynamic) as boolean
    if f = invalid then return true
    for each g in f
        v = fltStr(f[g])
        if g <> except and v <> ""
            if not fltFacet(s, g, v, caps) then return false
        end if
    end for
    return true
end function

' Com filtros fixados, as fontes prontas que os cumprem vão para a frente (as outras ficam como reserva)
function fltPinnedFirst(list as object, f as dynamic, caps as dynamic) as object
    if f = invalid or list.Count() < 2 then return list
    if f.Count() = 0 then return list
    a = []
    b = []
    c = []
    for each x in list
        if not fltBool(x.cached)
            c.Push(x)
        else if fltPasses(x, f, "", caps)
            a.Push(x)
        else
            b.Push(x)
        end if
    end for
    a.Append(b)
    a.Append(c)
    return a
end function

function fltValueLabel(g as string, v as string) as string
    if g = "q"
        if v = "?" then return "Outras"
        return v
    end if
    if g = "img"
        if v = "anyhdr" then return "Qualquer HDR"
        if v = "dv" then return "Dolby Vision"
        if v = "hdr10p" then return "HDR10+"
        if v = "hdr10" then return "HDR10"
        if v = "hlg" then return "HLG"
        if v = "sdr" then return "SDR"
    end if
    if g = "src"
        if v = "remux" then return "Remux"
        if v = "bluray" then return "Blu-ray"
        if v = "web" then return "WEB"
    end if
    if g = "nocam" then return "Sem CAM/TS"
    if g = "lang"
        if v = "br" then return "Dublado PT-BR"
        if v = "pt" then return "Dobrado PT-PT"
    end if
    if g = "aud"
        if v = "atmos" then return "Atmos"
        if v = "lossless" then return "Som sem perdas"
        if v = "51" then return "Som 5.1+"
    end if
    if g = "cached" then return "Em cache"
    return v
end function

' «4K · Dolby Vision · PT-BR»
function fltDescribe(f as dynamic) as string
    if f = invalid then return ""
    out = ""
    for each g in ["q", "img", "src", "nocam", "lang", "aud", "cached"]
        v = fltStr(f[g])
        if v <> ""
            if out <> "" then out = out + " · "
            out = out + fltValueLabel(g, v)
        end if
    end for
    return out
end function

' Tamanho em GB a partir do texto da fonte (0 se não disser)
function fltSizeGb(t as string) as float
    rx = CreateObject("roRegex", "(\d+(?:[.,]\d+)?)\s?(GB|MB|GiB|MiB|TB)", "i")
    mm = rx.Match(t)
    if mm.Count() < 3 then return 0
    n = Val(mm[1].Replace(",", "."))
    u = UCase(mm[2])
    if u = "MB" or u = "MIB" then return n / 1024
    if u = "TB" then return n * 1024
    return n
end function
