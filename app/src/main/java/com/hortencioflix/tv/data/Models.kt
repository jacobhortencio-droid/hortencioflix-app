package com.hortencioflix.tv.data

import java.io.Serializable

data class MetaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String,
    val background: String = "",
    val description: String = "",
    val info: String = "",
    val videoId: String = "",
    val progress: Float = 0f
) : Serializable

data class Episode(
    val id: String,
    val season: Int,
    val episode: Int,
    val name: String,
    val thumbnail: String = "",
    val overview: String = "",
    val released: String = ""
) : Serializable {
    val label: String get() = "T$season E$episode"
}

data class StreamItem(
    val addon: String,
    val name: String,
    val title: String,
    val url: String,
    val filename: String,
    val headers: Map<String, String>,
    val quality: Int,
    val cached: Boolean,
    val risky: Boolean
) : Serializable

data class SubtitleItem(val url: String, val lang: String, val label: String) : Serializable

data class Channel(val name: String, val logo: String, val url: String, val group: String, val tvgId: String = "") : Serializable

data class ChannelGroup(val title: String, val channels: List<Channel>)

/** Item de menu (atalhos no Início). */
data class ActionItem(val key: String, val title: String) : Serializable
