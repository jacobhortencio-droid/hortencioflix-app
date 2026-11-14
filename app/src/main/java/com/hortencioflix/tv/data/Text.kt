package com.hortencioflix.tv.data

import org.json.JSONObject

/** Texto seguro para a interface: sem «null», plurais a sério, e-mails mascarados, milhares separados. */
object Text {
    /** h•••••@gmail.com — para ecrãs principais e para o texto do Diagnóstico (que se copia e partilha). */
    fun maskEmail(e: String): String {
        val at = e.indexOf('@')
        if (at <= 0) return e
        return e.take(1) + "•••••" + e.substring(at)
    }

    /** Mascara qualquer e-mail dentro de um texto. */
    fun maskEmails(s: String): String = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").replace(s) { maskEmail(it.value) }

    /** plural(1, "perfil", "perfis") = «1 perfil»; plural(3, …) = «3 perfis». */
    fun plural(n: Int, one: String, many: String): String = "${num(n)} ${if (n == 1) one else many}"

    /** 20000 → «20 000» (espaço fino, como se escreve em português). */
    fun num(n: Int): String = String.format(java.util.Locale.US, "%,d", n).replace(',', ' ')
}

/**
 * Leitura segura de texto em JSON. O `optString` do Android devolve o texto «null» quando o valor vem
 * nulo (era isto que aparecia como título em «Continuar a ver»).
 */
fun JSONObject.str(key: String): String {
    if (isNull(key)) return ""
    val v = optString(key)
    return if (v == "null" || v == "undefined") "" else v
}
