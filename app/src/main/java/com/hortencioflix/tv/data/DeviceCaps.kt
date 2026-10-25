package com.hortencioflix.tv.data

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * O que este aparelho consegue mesmo tocar (vídeo e áudio). Usado para pôr no fim da lista as fontes
 * que iam falhar ou tocar sem som — em vez de as descobrir à força durante a reprodução.
 */
object DeviceCaps {
    var known = false; private set
    var hevc = true; private set
    var hevc10 = true; private set
    var uhd = true; private set
    var av1 = false; private set
    var dv = false; private set
    var hdr = false; private set
    /** Tipos de HDR que o ecrã mostra (ex.: «HDR10», «HLG», «Dolby Vision»). */
    var hdrTypes: List<String> = emptyList(); private set
    var eac3 = true; private set
    var ac3 = true; private set
    var dts = false; private set
    var truehd = false; private set

    fun init(ctx: Context) {
        try {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
            fun has(mime: String) = infos.any { i -> i.supportedTypes.any { it.equals(mime, true) } }
            fun caps(mime: String) = infos.mapNotNull { i -> i.supportedTypes.firstOrNull { it.equals(mime, true) }?.let { t -> try { i.getCapabilitiesForType(t) } catch (e: Exception) { null } } }
            hevc = has("video/hevc")
            hevc10 = caps("video/hevc").any { c -> c.profileLevels.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 || it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 } }
            uhd = caps("video/hevc").any { c -> try { c.videoCapabilities?.isSizeSupported(3840, 2160) == true } catch (e: Exception) { false } } ||
                caps("video/avc").any { c -> try { c.videoCapabilities?.isSizeSupported(3840, 2160) == true } catch (e: Exception) { false } }
            av1 = has("video/av01")
            dv = has("video/dolby-vision")
            if (Build.VERSION.SDK_INT >= 24) {
                @Suppress("DEPRECATION")
                val d = (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                val types = d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
                hdrTypes = types.map {
                    when (it) {
                        android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                        android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                        android.view.Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                        4 -> "HDR10+"
                        else -> "HDR"
                    }
                }.distinct()
                hdr = hdrTypes.isNotEmpty()
            }
            eac3 = has("audio/eac3") || has("audio/eac3-joc")
            ac3 = has("audio/ac3") || eac3
            dts = has("audio/vnd.dts") || has("audio/vnd.dts.hd")
            truehd = has("audio/true-hd")
            // Android TV ligada a uma barra de som/recetor: o áudio pode passar direto (passthrough).
            try {
                val a = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(ctx)
                if (a.supportsEncoding(androidx.media3.common.C.ENCODING_E_AC3)) eac3 = true
                if (a.supportsEncoding(androidx.media3.common.C.ENCODING_AC3)) ac3 = true
                if (a.supportsEncoding(androidx.media3.common.C.ENCODING_DTS)) dts = true
                if (a.supportsEncoding(androidx.media3.common.C.ENCODING_DOLBY_TRUEHD)) truehd = true
            } catch (e: Throwable) {
            }
            known = true
        } catch (e: Throwable) {
            known = false
        }
    }

    private val rxHevc = Regex("hevc|x265|h\\.?265", RegexOption.IGNORE_CASE)
    private val rx10 = Regex("10.?bit|hdr|\\bdv\\b|dovi", RegexOption.IGNORE_CASE)
    private val rxAv1 = Regex("\\bav1\\b", RegexOption.IGNORE_CASE)
    private val rxDv = Regex("\\bdv\\b|dovi|dolby.?vision", RegexOption.IGNORE_CASE)
    private val rxHdr = Regex("\\bhdr", RegexOption.IGNORE_CASE)
    private val rxEac3 = Regex("ddp|dd\\+|e-?ac-?3|atmos", RegexOption.IGNORE_CASE)
    private val rxAc3 = Regex("\\bdd\\s?[257]\\.?[01]|\\bac-?3\\b", RegexOption.IGNORE_CASE)
    private val rxDts = Regex("\\bdts", RegexOption.IGNORE_CASE)
    private val rxTrueHd = Regex("truehd", RegexOption.IGNORE_CASE)

    /** Motivo pelo qual a fonte não deve tocar bem aqui (null = compatível). */
    fun problem(text: String, quality: Int): String? {
        if (!known) return null
        if (quality >= 2160 && !uhd) return "4K"
        if (rxHevc.containsMatchIn(text) && (!hevc || (rx10.containsMatchIn(text) && !hevc10))) return "HEVC"
        if (rxAv1.containsMatchIn(text) && !av1) return "AV1"
        if (rxDv.containsMatchIn(text) && !rxHdr.containsMatchIn(text) && !dv) return "Dolby Vision"
        if (rxTrueHd.containsMatchIn(text) && !truehd) return "TrueHD"
        if (rxDts.containsMatchIn(text) && !dts) return "DTS"
        if (rxEac3.containsMatchIn(text) && !eac3) return "E-AC3"
        if (rxAc3.containsMatchIn(text) && !ac3) return "AC3"
        return null
    }

    fun summary() = if (!known) "desconhecido" else listOf(
        "4K" to uhd, "HEVC 10-bit" to hevc10, "HDR" to hdr, "Dolby Vision" to dv, "AV1" to av1,
        "E-AC3" to eac3, "DTS" to dts, "TrueHD" to truehd
    ).joinToString("  ·  ") { (n, ok) -> (if (ok) "✅ " else "❌ ") + n }
}
