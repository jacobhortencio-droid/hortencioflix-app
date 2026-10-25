package com.hortencioflix.tv.ui

import android.app.Activity
import android.content.Intent
import com.hortencioflix.tv.data.Prefs

/** Perfis: cada um com o seu progresso, vistos, favoritos e pesquisas. O infantil só mostra animação/família. */
object Profiles {
    private fun icon(p: Prefs.Profile) = if (p.kids) "🧒" else "👤"

    fun pick(a: Activity, firstRun: Boolean = false) {
        val list = Prefs.profiles()
        val labels = list.map { (if (it.id == Prefs.profileId) "● " else "") + "${icon(it)}  ${it.name}" + (if (it.kids) "  (infantil)" else "") } + "⚙  Gerir perfis"
        a.dialog().setTitle(if (firstRun) "Quem está a ver?" else "Perfil")
            .setItems(labels.toTypedArray()) { _, w ->
                if (w == list.size) manage(a) else switchTo(a, list[w])
            }.show()
    }

    private fun switchTo(a: Activity, p: Prefs.Profile) {
        if (p.id == Prefs.profileId) return
        val go = {
            Prefs.switchProfile(p.id)
            a.toast("Perfil: ${p.name}")
            a.startActivity(Intent(a, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        // Sair de um perfil infantil exige o PIN (se existir).
        if (Prefs.isKids && Prefs.pin.isNotEmpty()) {
            a.inputDialog("PIN para sair do perfil infantil", "PIN", "", password = true) { if (it == Prefs.pin) go() else a.toast("PIN errado") }
        } else go()
    }

    fun manage(a: Activity) {
        val list = Prefs.profiles()
        val opts = arrayListOf("➕  Novo perfil", "🧒  Novo perfil infantil")
        val removable = list.filter { it.id != "main" }
        if (removable.isNotEmpty()) opts.add("🗑  Apagar um perfil")
        a.choiceDialog("Gerir perfis", opts) { w ->
            when (w) {
                0, 1 -> a.inputDialog(if (w == 1) "Nome do perfil infantil" else "Nome do perfil", "Nome", "") { n ->
                    if (n.isEmpty()) return@inputDialog
                    val p = Prefs.addProfile(n, w == 1)
                    a.toast("Perfil «${p.name}» criado" + if (w == 1 && Prefs.pin.isEmpty()) ". Dica: ativa um PIN nas Definições para proteger o perfil infantil." else "")
                    pick(a)
                }
                2 -> a.choiceDialog("Apagar perfil", removable.map { "${icon(it)}  ${it.name}" }) { i ->
                    a.dialog().setTitle("Apagar «${removable[i].name}»?").setMessage("O progresso, vistos e favoritos deste perfil são apagados.")
                        .setPositiveButton("Apagar") { _, _ -> Prefs.removeProfile(removable[i].id); a.toast("Perfil apagado") }
                        .setNegativeButton("Cancelar", null).show()
                }
            }
        }
    }
}
