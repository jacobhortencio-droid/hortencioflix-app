package com.hortencioflix.tv.ui

import android.app.Activity
import android.content.Intent
import java.io.File

/** Se a app fechou com um erro, oferece enviar o relatório na abertura seguinte. */
object CrashReport {
    fun maybeShow(a: Activity) {
        val f = File(a.filesDir, "last_crash.txt")
        if (!f.exists()) return
        val text = try { f.readText() } catch (e: Exception) { "" }
        f.delete()
        if (text.isEmpty()) return
        // fica uma cópia para o Diagnóstico (para a podermos ver mesmo que o relatório não seja enviado)
        try { File(a.filesDir, "prev_crash.txt").writeText(text.take(4000)) } catch (e: Exception) {}
        a.window.decorView.postDelayed({
            if (a.isFinishing) return@postDelayed
            a.dialog().setTitle("A app fechou inesperadamente")
                .setMessage("Da última vez a Hortêncioflix fechou por causa de um erro. Envia o relatório para ser corrigido.")
                .setPositiveButton("Enviar relatório") { _, _ ->
                    try {
                        a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Enviar relatório"))
                    } catch (e: Exception) {
                    }
                }
                .setNegativeButton("Ignorar", null).show()
        }, 2000)
    }
}
