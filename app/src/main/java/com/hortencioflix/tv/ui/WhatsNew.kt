package com.hortencioflix.tv.ui

import android.app.Activity
import com.hortencioflix.tv.BuildConfig
import com.hortencioflix.tv.data.Prefs

/** «O que há de novo» — aparece uma vez depois de cada atualização. */
object WhatsNew {
    private val NOTES = listOf(
        "⚡ Fontes curadas (opcional): com o teu servidor gratuito, um só pedido junta as fontes de todos os add-ons, sem repetidos, e confirma no TorBox o que está pronto — Definições › Fontes curadas",
        "🖼 Mini-leitor: ao ir para o ecrã inicial com um vídeo a tocar, ele continua numa janela pequena, com botões para pausar, recuar 10 s e avançar 30 s (canal seguinte na TV ao vivo)",
        "Sair e voltar já não recomeça: se saíres da app a meio de um vídeo, ao voltar estás no mesmo ponto (em pausa se estiveste fora mais de 3 min)",
        "Se o Android fechar a app em segundo plano, ao abrir de novo volta ao título que estavas a ver",
        "Retomar sem falhas: depois de uma pausa longa ou de uma queda da Internet, o vídeo volta a abrir sozinho no mesmo ponto",
        "Desenhos animados e anime: as fontes dobradas em português vêm primeiro (Definições › Animação: preferir dobrado)",
        "Rede lenta: a app pergunta antes de baixar a qualidade (Definições: perguntar / mudar sozinha / nunca)",
        "🎬 Trailer no fundo: ao abrir um filme ou série, o trailer começa sem som por trás da página; o botão «Trailer» abre-o em ecrã inteiro, com som, dentro da app",
        "«A seguir» no fim do filme: quando começam os créditos, o vídeo encolhe e aparecem filmes recomendados — o primeiro começa sozinho ao fim de 15 s (Voltar continua os créditos)",
        "📺 Ver na TV: no telemóvel, abre um filme ou episódio e toca em «Ver na TV» — a Roku abre a app e começa a reproduzir",
        "Escrever na TV: usa o teclado do telemóvel para escrever na Roku (Definições › Roku)",
        "Android TV / Google TV: o que estás a ver aparece em «Continuar a ver» no ecrã inicial da TV",
        "Organizar o Início: escolhe que filas aparecem e qual vem primeiro (Definições)",
        "TV ao vivo no Android TV: escreve o número do canal no comando",
        "A sincronização com a Roku passa também o idioma das legendas e as preferências de áudio dobrado e de imagem",
        "O leitor já não volta em silêncio para a página do filme: se a fonte acabar sem imagem tenta a seguinte, e se a ligação cair a meio continua noutra fonte no mesmo ponto",
        "Filtros das fontes mais inteligentes: Dolby Vision, HDR10+, HDR10, HLG, «Qualquer HDR», Remux/Blu-ray/WEB, sem CAM, Atmos e som sem perdas — podes escolher vários do mesmo grupo (ex.: Dolby Vision ou HDR10+)",
        "📌 Fixar filtros: ficam para todos os títulos e o «Ver» começa por uma fonte que os cumpre (se o título não tiver nenhuma, mostra todas)",
        "Aviso ⚠ nas fontes que o teu ecrã não mostra bem (ex.: Dolby Vision «só DV» num ecrã sem DV) e «HDRip» já não é confundido com HDR",
        "Filtros de fontes combináveis: qualidade + imagem (Dolby Vision, HDR10+, HDR, SDR) + áudio (PT-BR, PT-PT, Atmos, 5.1) — toca de novo num filtro para o tirar",
        "Definições › Preferir imagem: Automático escolhe o melhor que o teu ecrã mostra (Dolby Vision › HDR › SDR), sem trocar a qualidade",
        "Sincronização com a Roku inclui agora os add-ons que adicionas à mão",
        "Fontes por áudio: filtros «🇧🇷 Dublado PT-BR» e «🇵🇹 Dobrado PT-PT», e cada fonte mostra a língua do áudio",
        "Definições › Preferir áudio dobrado: as fontes dobradas na variante que escolheres passam à frente (também no «Ver»)",
        "Dois temas novos em Definições › Tema: Claro e Vidro (liquid glass), com fundo colorido, painéis translúcidos e janelas desfocadas",
        "Leitor com menus em vidro («liquid glass»): o vídeo fica desfocado por trás (Android 12 ou mais recente)",
        "Lista de canais nova: procura por nome, número ou grupo, logótipos, o que está a dar agora e filtro de favoritos (manter premido para marcar)",
        "Atualizar a app da Roku: agora fecha sozinha a Hortêncioflix na Roku antes de instalar e mostra o motivo exato se falhar",
        "Sincronização com a Roku: «Continuar a ver», vistos, listas IPTV e favoritos iguais nos dois — Definições › Atualizações › Roku (IP + código que a Roku mostra)",
        "Android TV: o ecrã ajusta-se à frequência do filme (24p sem solavancos) — desliga em Definições se não gostares",
        "Modo de vídeo avançado (tunneling) nas Definições, para 4K mais suave em algumas TVs",
        "Saltos mais rápidos: mantém ◀ ▶ premido e o salto acelera até 2 minutos",
        "Lista de fontes sem repetidos e com filtros «HDR / Dolby Vision» e «Compatíveis» com o teu aparelho",
        "Guia TV em grelha: canais × horas, como numa box de operador (TV ao vivo › Guia TV). OK num programa a dar agora abre o canal",
        "TorBox: a app vê o estado da tua conta (plano, validade, chave) e avisa se houver algum problema",
        "Fontes por descarregar: «Descarregar e avisar-me» — o TorBox descarrega e recebes uma notificação quando estiver pronto",
        "Cópia de segurança encriptada por defeito, ou sem chaves nem contas para poderes partilhar à vontade",
        "Ecrã inteiro a sério: o vídeo ocupa também a zona do entalhe da câmara",
        "HDR: indicação no leitor se o vídeo e o ecrã são HDR10, HLG ou Dolby Vision"
    )

    fun maybeShow(a: Activity) {
        val last = Prefs.str("seenVersion", "0").toIntOrNull() ?: 0
        val cur = BuildConfig.VERSION_CODE
        Prefs.put("seenVersion", cur.toString())
        if (last == 0 || last >= cur) return
        a.window.decorView.postDelayed({
            if (a.isFinishing) return@postDelayed
            a.dialog().setTitle("Novidades da versão ${BuildConfig.VERSION_NAME}")
                .setMessage(NOTES.joinToString("\n\n") { "•  $it" })
                .setPositiveButton("Fixe!", null)
                .show()
        }, 1600)
    }
}
