sub init_x()
    m.top.findNode("header").color = accentColor()
    m.cats = m.top.findNode("cats")
    m.grid = m.top.findNode("grid")
    m.status = m.top.findNode("status")
    m.skeleton = m.top.findNode("skeleton")
    m.focusTitle = m.top.findNode("focusTitle")
    m.cats.observeField("itemFocused", "onCatFocused")
    m.cats.observeField("itemSelected", "onCatSelected")
    m.grid.observeField("itemFocused", "onGridFocused")
    m.grid.observeField("itemSelected", "onGridSelected")
    m.top.observeField("focusedChild", "onFocusedChild")
    m.focusTarget = m.cats
    buildCategories()
    m.curCat = -1
    m.items = []
    m.loading = false
    m.hasMore = true
end sub
sub init()
    try
        init_x()
    catch e
        reportCrash("ExploreScreen.init", e)
    end try
end sub

sub onFocusedChild_x()
    if m.top.hasFocus() then m.focusTarget.setFocus(true)
end sub
sub onFocusedChild()
    try
        onFocusedChild_x()
    catch e
        reportCrash("ExploreScreen.onFocusedChild", e)
    end try
end sub

sub buildCategories()
    genres = [["Action", "Ação"], ["Adventure", "Aventura"], ["Animation", "Animação"], ["Comedy", "Comédia"], ["Crime", "Crime"], ["Documentary", "Documentário"], ["Drama", "Drama"], ["Family", "Família"], ["Fantasy", "Fantasia"], ["History", "História"], ["Horror", "Terror"], ["Mystery", "Mistério"], ["Romance", "Romance"], ["Sci-Fi", "Ficção científica"], ["Sport", "Desporto"], ["Thriller", "Suspense"], ["War", "Guerra"], ["Western", "Faroeste"]]
    m.catList = []
    m.catList.Push({ label: "Filmes · Populares", type: "movie", cat: "top", genre: "" })
    m.catList.Push({ label: "Filmes · Em destaque", type: "movie", cat: "imdbRating", genre: "" })
    m.catList.Push({ label: "Séries · Populares", type: "series", cat: "top", genre: "" })
    m.catList.Push({ label: "Séries · Em destaque", type: "series", cat: "imdbRating", genre: "" })
    for each g in genres
        m.catList.Push({ label: "Filmes · " + g[1], type: "movie", cat: "top", genre: g[0] })
    end for
    for each g in genres
        if g[0] <> "Sport" and g[0] <> "War" and g[0] <> "Western"
            m.catList.Push({ label: "Séries · " + g[1], type: "series", cat: "top", genre: g[0] })
        end if
    end for
    ' por ano (os últimos 8)
    yr = CreateObject("roDateTime").GetYear()
    for y = yr to yr - 7 step -1
        m.catList.Push({ label: "Filmes · " + y.ToStr(), type: "movie", cat: "year", genre: y.ToStr() })
    end for
    for y = yr to yr - 4 step -1
        m.catList.Push({ label: "Séries · " + y.ToStr(), type: "series", cat: "year", genre: y.ToStr() })
    end for
    labels = []
    for each c in m.catList
        labels.Push(c.label)
    end for
    m.cats.content = labelContent(labels)
end sub

sub onCatFocused_x()
    loadCategory(m.cats.itemFocused)
end sub
sub onCatFocused()
    try
        onCatFocused_x()
    catch e
        reportCrash("ExploreScreen.onCatFocused", e)
    end try
end sub

sub onCatSelected_x()
    loadCategory(m.cats.itemSelected)
    if m.items.Count() > 0
        m.focusTarget = m.grid
        m.grid.setFocus(true)
    end if
end sub
sub onCatSelected()
    try
        onCatSelected_x()
    catch e
        reportCrash("ExploreScreen.onCatSelected", e)
    end try
end sub

function catUrl(c as object, skip as integer) as string
    extras = []
    if c.genre <> "" then extras.Push("genre=" + urlEnc(c.genre))
    if skip > 0 then extras.Push("skip=" + skip.ToStr())
    u = cinemetaUrl() + "/catalog/" + c.type + "/" + c.cat
    if extras.Count() > 0 then u = u + "/" + extras.Join("&")
    return u + ".json"
end function

sub loadCategory(idx as integer)
    if idx = m.curCat or idx < 0 or idx >= m.catList.Count() then return
    m.curCat = idx
    m.items = []
    m.hasMore = true
    m.grid.content = invalid
    m.skeleton.visible = true
    m.status.text = ""
    m.skipped = 0
    m.focusTitle.text = m.catList[idx].label
    fetch(0)
end sub

sub fetch(skip as integer)
    m.loading = true
    m.task = startAddonTask({ kind: "catalogurl", url: catUrl(m.catList[m.curCat], skip), exclude: "" }, "onLoaded")
end sub

sub onLoaded_x(event as object)
    if m.task = invalid or not event.getRoSGNode().isSameNode(m.task) then return
    res = event.getData()
    m.loading = false
    m.skeleton.visible = false
    newItems = res.items
    if newItems = invalid then newItems = []
    if newItems.Count() = 0
        m.hasMore = false
        if m.items.Count() = 0 then m.status.text = "Sem títulos nesta categoria."
        return
    end if
    seen = {}
    for each it in m.items
        seen[it.id] = true
    end for
    root = m.grid.content
    if root = invalid then root = CreateObject("roSGNode", "ContentNode")
    added = 0
    for each it in newItems
        ' filtro de nota mínima (tecla ✱)
        okRating = m.minRating = invalid or m.minRating <= 0 or numOr(it.rating, 0) >= m.minRating
        if not seen.DoesExist(it.id) and okRating
            m.items.Push(it)
            c = root.createChild("ContentNode")
            c.title = it.name
            c.HDPosterUrl = it.poster
            c.addFields({ metaId: it.id, metaType: it.type, videoId: "", progress: 0.0, bg: strOr(it.bg), logo: strOr(it.logo) })
            added = added + 1
        end if
    end for
    if added = 0 and (m.minRating = invalid or m.minRating <= 0) then m.hasMore = false
    ' com o filtro de nota, uma página pode não trazer nada: pede a seguinte
    if added = 0 and m.hasMore and m.items.Count() < 12 then
        m.skipped = numOr(m.skipped, 0) + newItems.Count()
        if m.skipped < 400 then fetch(m.items.Count() + m.skipped)
    end if
    if m.grid.content = invalid then m.grid.content = root
end sub
sub onLoaded(event as object)
    try
        onLoaded_x(event)
    catch e
        reportCrash("ExploreScreen.onLoaded", e)
    end try
end sub

sub onGridFocused_x()
    i = m.grid.itemFocused
    if i >= 0 and i < m.items.Count() then m.focusTitle.text = m.items[i].name
    if not m.loading and m.hasMore and i >= m.items.Count() - 12 then fetch(m.items.Count())
end sub
sub onGridFocused()
    try
        onGridFocused_x()
    catch e
        reportCrash("ExploreScreen.onGridFocused", e)
    end try
end sub

sub onGridSelected_x()
    i = m.grid.itemSelected
    if i < 0 or i >= m.items.Count() then return
    it = m.items[i]
    m.top.itemSelected = { id: it.id, type: it.type, name: it.name, poster: it.poster, videoId: "", bg: strOr(it.bg), logo: strOr(it.logo) }
end sub
sub onGridSelected()
    try
        onGridSelected_x()
    catch e
        reportCrash("ExploreScreen.onGridSelected", e)
    end try
end sub

function onKeyEvent_x(key as string, press as boolean) as boolean
    if not press then return false
    if key = "options"
        ' nota mínima IMDb: todas → 6+ → 7+ → 8+
        r = numOr(m.minRating, 0)
        if r = 0
            r = 6
        else if r = 6
            r = 7
        else if r = 7
            r = 8
        else
            r = 0
        end if
        m.minRating = r
        h = "Explorar"
        if r > 0 then h = "Explorar  ·  IMDb " + Int(r).ToStr() + "+"
        m.top.findNode("header").text = h
        idx = m.curCat
        m.curCat = -1
        loadCategory(idx)
        return true
    end if
    if key = "right" and m.cats.hasFocus() and m.items.Count() > 0
        m.focusTarget = m.grid
        m.grid.setFocus(true)
        return true
    else if key = "left" and m.grid.hasFocus()
        m.focusTarget = m.cats
        m.cats.setFocus(true)
        m.focusTitle.text = m.catList[m.curCat].label
        return true
    end if
    return false
end function
function onKeyEvent(key as string, press as boolean) as boolean
    handled = false
    try
        handled = onKeyEvent_x(key, press)
    catch e
        reportCrash("ExploreScreen.onKeyEvent", e)
    end try
    return handled
end function
