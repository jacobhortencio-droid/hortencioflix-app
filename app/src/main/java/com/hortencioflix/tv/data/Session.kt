package com.hortencioflix.tv.data

/** Dados partilhados entre ecrãs (evita passar listas grandes por Intent). */
object Session {
    var meta: MetaItem? = null
    var videoId: String = ""
    var streams: List<StreamItem> = emptyList()
    var streamIndex: Int = 0
    var subtitles: List<SubtitleItem> = emptyList()
    var episodes: List<Episode> = emptyList()
    var channels: List<Channel> = emptyList()
    var channelIndex: Int = 0
    var live: Boolean = false
    var startAt: Long = 0
    /** Para fontes diretas (Xtream, TorBox): obter a fonte de um episódio sem add-ons. */
    var directStream: ((String) -> StreamItem?)? = null

    /** «Ver» carregado antes de as fontes estarem prontas: o leitor abre já e procura-as ele mesmo. */
    var pending: Boolean = false

    fun prepare(meta: MetaItem, videoId: String, subs: List<SubtitleItem>, episodes: List<Episode>, startAt: Long) {
        vod(meta, videoId, emptyList(), 0, subs, episodes, startAt)
        pending = true
    }

    fun vod(meta: MetaItem, videoId: String, streams: List<StreamItem>, index: Int, subs: List<SubtitleItem>, episodes: List<Episode>, startAt: Long) {
        this.meta = meta; this.videoId = videoId; this.streams = streams; this.streamIndex = index
        this.subtitles = subs; this.episodes = episodes; this.live = false; this.startAt = startAt
        this.channels = emptyList()
        this.directStream = null
        this.pending = false
    }

    fun live(channels: List<Channel>, index: Int) {
        this.channels = channels; this.channelIndex = index; this.live = true; this.pending = false
        this.meta = null; this.streams = emptyList(); this.subtitles = emptyList(); this.episodes = emptyList(); this.startAt = 0
    }
}
