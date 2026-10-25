' Erros apanhados: em vez de a app congelar (modo de programador), regista e avisa

sub reportCrash(where as string, e as object)
    msg = where + ": "
    if e <> invalid and e.message <> invalid then msg = msg + e.message.ToStr()
    if e <> invalid and e.backtrace <> invalid and type(e.backtrace) = "roArray" and e.backtrace.Count() > 0
        b = e.backtrace[e.backtrace.Count() - 1]
        fn = ""
        ln = ""
        if b.function <> invalid then fn = b.function.ToStr()
        if b.line_number <> invalid then ln = b.line_number.ToStr()
        msg = msg + "  [" + fn + ", linha " + ln + "]"
    end if
    print "HORTENCIOFLIX ERRO "; msg
    g = m.global
    if g = invalid then return
    if not g.hasField("logs") then g.addField("logs", "array", false)
    arr = g.logs
    if arr = invalid then arr = []
    arr.Unshift("ERRO " + msg)
    while arr.Count() > 40
        arr.Pop()
    end while
    g.logs = arr
    if g.hasField("crash") then g.crash = msg
end sub
