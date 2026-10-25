# Hortêncioflix para Roku — v0.18

## Novidades da v0.18

- 0.18.3 — **retomar sem falhas**: depois de uma pausa longa (5 min ou mais), a app volta a abrir a mesma fonte no mesmo ponto (a ligação e o link do debrid já tinham expirado); se a Internet cair, o vídeo pausa com «Sem ligação — continuo sozinho quando voltar» e retoma sozinho quando a rede volta; se ficar mais de 30 s a carregar a meio, reabre a mesma fonte e, se voltar a parar, passa a outra; erros a meio tentam primeiro a mesma fonte (link novo) antes de mudar.

- 0.18.2 (auditoria, 5.ª parte — definições e segurança): «Repor predefinições» passa a pedir confirmação (e o PIN, se houver) — antes apagava tudo com um clique; desligar a proteção dos canais adultos pede o PIN; no perfil infantil, as Definições pedem o PIN; a página «Configurar pelo telemóvel» envia as chaves por POST (não ficam no histórico do navegador); o servidor da rede local recusa pedidos enormes.

- 0.18.1 (auditoria, 4.ª parte — TV ao vivo): escrever o número de um canal já não contorna o PIN dos canais adultos; categorias enormes mostram as primeiras 1500 linhas (antes podiam bloquear a TV) com aviso para usar a pesquisa; Voltar na lista de canais vai primeiro às categorias; ▶ toca o canal em foco; o guia XMLTV lê o ficheiro todo (os teus canais podiam ficar sem guia num ficheiro grande) e passa a mostrar a descrição dos programas; o mini-guia também funciona ao entrar por número.

- **Modo ambiente com OK para ver**: com a app parada (5 min por defeito), passam as imagens dos teus filmes e séries; **OK** abre o que está a passar e começa a ver, **▶** passa ao seguinte, Voltar sai. Definições › «Modo ambiente»: 3, 5 ou 10 min, ou desligado. (A proteção de ecrã da própria Roku continua a não aceitar teclas — por isso o modo ambiente aparece antes dela.)

**Auditoria, 3.ª parte — página do título**
- **Continuar uma série** sem conta Stremio: segue a partir do último episódio marcado como visto neste aparelho (antes voltava ao 1.º episódio).
- **«Ver» mais rápido**: as legendas já não seguram o arranque mais de 5 s; se chegarem tarde, o leitor procura-as e junta-as sozinho.
- **Escolher uma fonte**: se falhar, há sempre alternativas (as seguintes, as anteriores e as escondidas pelos filtros), mesmo escolhendo a última da lista.
- Temporizadores da app ligados à cena (garante que os lembretes e o TorBox são verificados).

# v0.17

## Novidades da v0.17

- 0.17.4 (auditoria, 2.ª parte — leitor): uma fonte que fica a carregar mais de 45 s passa sozinha à seguinte (canais: aviso ao fim de 25 s); se a ligação cair a meio do filme, continua noutra fonte no mesmo ponto em vez de mostrar erro; o teste rápido do link já não descarta fontes boas de servidores que não aceitam esse teste; as sugestões «A seguir» já não repetem as do filme anterior; os temporizadores do leitor param ao fechar.

- 0.17.3 (auditoria, 1.ª parte): tecla ▶ do comando num cartaz do Início abre e começa logo (no «Continuar a ver» continua o episódio certo); ▶ na página do título começa já; Voltar nas sugestões vai primeiro aos botões; o registo da Roku (pouco espaço) já não cresce sem limite (estatísticas das fontes e listas novas entram na limpeza automática); a sincronização bloqueia 10 minutos depois de 5 códigos errados.

- 0.17.2: **animação dobrada** — nos desenhos animados e anime, as fontes dobradas em português vêm primeiro (também no «Ver» e no próximo episódio). Definições › «Animação: preferir dobrado»: Português (Portugal ou Brasil), só de Portugal, só do Brasil, ou não.

- 0.17.1: corrige o erro «HomeScreen.updateHero: Type Mismatch» que aparecia ao abrir o Explorar (o destaque do Início tentava atualizar-se com a lista a mudar).

**Descobrir**
- **Filmografia pelo Trakt**: a página de um ator/realizador mostra os filmes e séries em que entrou (mais recentes primeiro), mesmo sem chave TMDB.
- **Coleções / sagas** (com chave TMDB): na página de um filme aparece «Coleção: …» com todas as partes por ordem de estreia.
- **Explorar por ano** (últimos 8 anos de filmes, 5 de séries) e **nota mínima IMDb** com a tecla ✱ (6+, 7+, 8+).
- **Listas tuas**: na página do título, ✱ › «Adicionar a uma lista…» (ex.: «Fim de semana», «Ver com a família»). Cada lista aparece como fila no Início. Por perfil.

**TV ao vivo**
- **Mini-guia** por cima do canal: canal anterior, atual (com «agora / a seguir») e seguinte. Aparece ao mudar de canal e com OK.
- **Lembretes**: no Guia TV, escolhe um programa futuro › «Lembrar-me quando começar». Um minuto antes, a app pergunta se queres ver (com a app aberta).

**Perfis**
- **Avatar** com a inicial e a cor do perfil no Início.
- **PIN por perfil**: Definições › Perfil › «Proteger este perfil com PIN». Para entrar nele a partir de outro perfil, pede o PIN.

# v0.16

## Novidades da v0.16

- 0.16.1: quando a rede está lenta, a app **pergunta** se quer baixar a qualidade (o vídeo continua a tocar); «Manter» não volta a perguntar nesse vídeo. Em Definições: perguntar / mudar sozinha / nunca.

- **Título em português** na página do filme/série (ex.: «O Cavaleiro das Trevas»), ao lado do original, com a **classificação etária** (M/12, M/16…).
- **Nomes dos episódios em português**, por temporada (Trakt ou TMDB), guardados para a próxima vez.
- **«Termina às 23:40»** nos filmes: a hora a que acaba se começares agora (conta com o que já viste).
- **Filas dos add-ons em português**: «Trending» → «Em alta», «Top Rated» → «Mais bem avaliados», etc.
- **Novidades do ano**: fila nova no Início com os filmes mais vistos deste ano.
- **Pesquisa nova**: teclado no ecrã e resultados enquanto escreves (sem carregar em «Pesquisar»); as pesquisas recentes ficam por baixo do teclado. A tecla ✱ abre o teclado com voz.

# v0.15

## Novidades da v0.15

- 0.15.2: sinopses em português (de Portugal; se não houver, do Brasil) no destaque do Início e na página do título, através da conta Trakt ligada ou de uma chave TMDB. Ficam guardadas para abrir logo da próxima vez. Pode desligar-se em Definições.

- 0.15.1: o destaque do Início mostra sempre a sinopse, o ano, a duração, a nota IMDb e os géneros (também em «Continuar a ver», na biblioteca e nas recomendações); o número da versão no Início passa a ser o verdadeiro.

- **Organizar o Início** (Definições): escolhe que filas aparecem (✓/✗) e qual vem primeiro; «Repor o Início» volta ao normal. Por perfil.
- **«Porque viste…»**: fila nova no Início com recomendações a partir do último filme ou série que acabaste (ou do que estás a ver).
- **Proteção de ecrã própria**: com a app aberta e a TV parada, passam as imagens dos teus filmes e séries (Continuar a ver, biblioteca e catálogos), com movimento lento, o título e as horas.
- **Primeira utilização guiada**: numa Roku ainda por configurar, um assistente de 3 passos mostra o que falta (TorBox, Stremio/add-ons, TV ao vivo), abre a configuração pelo telemóvel e verifica a conta TorBox.

# v0.14

## Novidades da v0.14

- **«A seguir» no fim do filme**: nos créditos, o vídeo encolhe para a esquerda e aparecem recomendações do mesmo género; a primeira começa sozinha ao fim de 15 s. «Ver os créditos» ou Voltar devolvem o vídeo ao ecrã inteiro. Também no último episódio de uma série.
- **Fundo animado** na página do título: a imagem ganha um movimento lento de câmara (a Roku não consegue tocar trailers do YouTube).
- Opções novas em Definições para ligar/desligar as duas coisas.

# v0.13

## Novidades da v0.13

- **Ver na TV**: no telemóvel, abre um título e toca em «📺 Ver na TV». A Roku abre a Hortêncioflix (mesmo que esteja fechada) e começa a reproduzir o filme ou o episódio escolhido.
- **Escrever na TV**: o teclado do telemóvel escreve na Roku (Definições › Roku na app Android).
- A sincronização traz do telemóvel o idioma das legendas e as preferências de áudio dobrado e de imagem.

# v0.12

## Novidades da v0.12

- 0.12.1: quando uma fonte «termina» sem mostrar imagem (link vazio, expirado ou recusado pelo debrid), a app tenta a seguinte em vez de voltar em silêncio para a página do filme; se a ligação cair a meio, continua noutra fonte no mesmo ponto. Sem mais fontes, explica o que aconteceu.

- **Filtros das fontes mais completos** (tecla ✱ na lista de fontes): Qualidade, Imagem (Qualquer HDR, Dolby Vision, HDR10+, HDR10, HLG, SDR), Origem (Remux, Blu-ray, WEB), Esconder CAM/TS, Idioma do áudio, Som (Atmos, sem perdas, 5.1+) e Só prontas. Todos combinam entre si.
- Cada linha do menu mostra quantas fontes ficam e só percorre os valores que existem nesse título, por isso a lista nunca fica vazia. O menu fica aberto enquanto mudas os filtros.
- **Fixar filtros para todos os títulos**: ficam aplicados em cada filme/série e o «Ver» começa por uma fonte que os cumpre. Se um título não tiver nenhuma assim, mostra todas e avisa.
- **Ordenar** por tamanho do ficheiro (maior ou mais leve primeiro), dentro de cada qualidade.
- Deteção de imagem melhorada: «HDRip» já não conta como HDR; o perfil Dolby Vision (P5/P7/P8) é lido; as fontes «só DV» são assinaladas com ⚠ numa TV sem Dolby Vision.

# v0.11

## Novidades da v0.11

- **Nova identidade**: ícone, ecrã de arranque, animação de entrada e logótipo com a marca nova (o «H» com play, vermelho → laranja), iguais ao Android. O carregamento usa as três barras animadas da marca.
- **Cor de destaque em todo o lado**: os elementos que ainda tinham o vermelho fixo seguem o «Tema de cor».
- **Filtros de imagem e qualidade** na lista de fontes (tecla ✱): só Dolby Vision, só HDR, só SDR; só 4K / 1080p / 720p / SD. Combinam com o filtro de áudio. Cada fonte mostra [Dolby Vision], [DV+HDR], [HDR10+] ou [HDR].
- **Preferir imagem** (Definições): Automático escolhe o melhor que a TV mostra — Dolby Vision, senão HDR, senão SDR (numa TV sem HDR, as cópias SDR vêm primeiro para não ficarem desbotadas). Só reordena dentro da mesma qualidade.
- **Descarregar e avisar-me**: ao escolher uma fonte que ainda não está no TorBox, a app oferece-se para a descarregar e avisa quando estiver pronta (com a app aberta).
- **Leitor**: lista de canais e «Procurar canal…» no menu (TV ao vivo); a qualidade baixa sozinha depois de 3 pausas para carregar; indica se o vídeo é HDR/Dolby Vision e se a TV o mostra; o atraso das legendas fica guardado por título.
- **A app aprende**: fontes que falham nesta TV passam para o fim da lista; as reproduções ficam registadas no Diagnóstico.

# v0.10

## Novidades da v0.10

- 0.10.1: a sincronização com a app Android passa a incluir os add-ons adicionados à mão (os links muito compridos ficam só no Android, por falta de espaço na Roku).

- **Aspeto «Vidro» (liquid glass)**: Definições › Aspeto. Fundo colorido suave e painéis translúcidos (a Roku não desfoca, por isso o vidro é simulado com transparência). Fecha e abre a app para aplicar em todos os ecrãs. O leitor continua escuro.
- **Áudio dobrado**: cada fonte mostra [Dublado PT-BR], [Dobrado PT-PT] ou [Áudio PT]. Na lista de fontes, tecla ✱ → «Áudio das fontes» filtra só as dobradas. Em Definições › «Preferir áudio dobrado» as fontes dobradas na variante escolhida passam à frente (também no «Ver»).

# v0.9

## Novidades da v0.9

- **Guia TV em grelha**: em TV ao vivo, no menu de um canal (tecla ✱) → «Guia TV em grelha». Canais × horas numa janela de 3 h; ▲▼ muda de canal, ◀▶ de programa (nas pontas avança/recua uma hora), OK abre o canal (se o programa estiver a dar) ou mostra a descrição.
- **Sincronização com a app Android**: «Continuar a ver», episódios vistos, listas IPTV e favoritos ficam iguais nos dois aparelhos. Em Definições aparece o código de 4 dígitos; na app Android: Definições › Atualizações › Roku (IP + código). A sincronização é feita pela app Android sempre que a abres ou acabas de ver algo — a Hortêncioflix tem de estar aberta na Roku. O servidor de sincronização (porta 8889) só troca estes dados e nunca expõe chaves.

# v0.8

## Novidades da v0.8

- 0.8.2: as fontes são procuradas logo ao abrir o filme/série (o «Ver» arranca sem espera) e a app avisa se a conta TorBox tiver algum problema (chave recusada, plano gratuito, subscrição a terminar).

- 0.8.1: o leitor deixa de mostrar nomes e números de fontes — troca em silêncio para a melhor fonte (o pormenor fica no Diagnóstico).

- **Arranque mais rápido**: o vídeo começa logo; o teste do link corre em paralelo (se o link estiver morto, passa sozinho para outra fonte) e as legendas esperam no máximo 3,5 s — se chegarem depois, entram no mesmo ponto.
- **Fontes à medida da tua Roku**: a app pergunta à Roku o que ela (e a TV/barra de som) consegue tocar — 4K, HEVC 10-bit, Dolby Vision, AV1, DTS, TrueHD — e passa as fontes incompatíveis para o fim da lista (ou esconde-as, com "Esconder fontes DV/AV1/3D").
- **Memória das definições protegida**: a Roku só dá ~16 KB por app; quando está quase cheia, a app encurta os históricos (pesquisas, recentes, vistos antigos) em vez de deixar falhar as gravações.
- **Atualização automática pela app Android**: em Definições › Atualizações da app Android indica o IP da Roku e a palavra-passe de programador; a app Android instala as versões novas na Roku (a pedido ou uma vez por dia, só quando a Hortêncioflix não está aberta na Roku).

# v0.7

## Novidades da v0.7

- **Fontes debrid primeiro**: com chave TorBox (ou "Tenho conta debrid: sim" nas Definições), as fontes marcadas [TB+], [RD+], [AD+]… passam à frente das ligações diretas; sem conta debrid, as ligações diretas vêm primeiro.
- **Qualidade no leitor**: menu do leitor → "Qualidade". Escolhe 4K/1080p/720p/480p ou "Melhor disponível" e a app procura a melhor fonte dessa resolução (ignora o limite de qualidade das Definições) e continua do mesmo ponto.
- **Falha de fonte inteligente**: quando uma fonte falha, tenta primeiro outra da mesma resolução.
- **Legendas automáticas para IPTV/TorBox**: filmes e séries de listas IPTV/Xtream (ligações /movie/, /series/ ou ficheiros de vídeo) e ficheiros do TorBox procuram legendas pelo título (Cinemeta → IMDb → add-ons de legendas), sem depender das legendas da lista.
- **Nunca tocar o vídeo «TORRENT DOWNLOADING TO DEBRID»** (0.7.1): a app só arranca sozinha com fontes já prontas no debrid; se uma fonte devolver esse vídeo, passa para outra pronta ou avisa — e não marca o episódio como visto nem salta para o seguinte.
- **A app já não fecha sozinha quando uma fonte falha** (0.7.3): todas as rotinas do leitor e dos ecrãs passam a apanhar erros (ficam no Diagnóstico); se uma tarefa de fundo falhar, o leitor recebe resposta e não fica preso.
- **Fontes que fazem a Roku fechar a app** (alguns MKV, áudio DTS, HEVC especial): a app lembra-se da fonte que estava a tocar, avisa no arranque seguinte e passa a pô-la no fim da lista.
- Definição nova: "Tenho conta debrid" (automático / sim / não).
- Nota: versão não testada num Roku real — instalar em modo programador e reportar erros no ecrã de diagnóstico.

# v0.6

## Novidades da v0.6

### Descobrir
- **Explorar** (novo botão no Início): Filmes e Séries populares e em destaque, e mais de 30 géneros, numa grelha que vai carregando à medida que desces.
- **Elenco** nos detalhes: escolhe um ator ou realizador para ver a filmografia. Com uma chave TMDB gratuita (definida pelo telemóvel) mostra a filmografia completa; sem ela, mostra a pesquisa pelo nome.
- **Pesquisa** com as últimas 10 pesquisas. O teclado da Roku aceita voz.
- **Novos episódios das tuas séries** e **Em breve** no Início, a partir das séries da biblioteca Stremio.

### Conta e perfis
- **Episódios vistos**: marcados automaticamente quando terminas um episódio, e assumidos a partir do último visto na conta Stremio. No menu \* dos episódios podes marcar um episódio ou a temporada inteira.
- **Perfis**, em Definições → Perfil: cada perfil tem o seu «Continuar a ver», favoritos, canais recentes, vistos e pesquisas.
  - Perfil infantil: esconde a biblioteca da conta e os canais adultos.
  - Sair do perfil infantil pede o PIN.
- **Trakt**: ligação por código, envio do que vês (scrobble) e filas «Filmes/Séries recomendados (Trakt)» no Início.
  - Precisas de criar uma app gratuita em trakt.tv/oauth/applications e colar o Client ID e o Secret pelo telemóvel.

### TV ao vivo
- **Guia de programação (EPG)**:
  - «Agora / A seguir» em cada canal e no leitor;
  - «Ver programação» no menu \* mostra as próximas horas.
  - Funciona com contas Xtream (automático) e com listas M3U que tenham guia XMLTV sem .gz (indicado na lista ou pelo telemóvel).
- **Números de canal** e **«Ir para o canal n.º…»** no menu \*.
- **Ordenar favoritos**: mover para cima, para baixo ou para o topo.
- **Verificar canais offline** de uma categoria e **esconder canais offline**.
- **Controlo parental**: PIN de 4 dígitos que protege as categorias e os canais adultos.

### Configuração e estabilidade
- **Configurar pelo telemóvel**, em Definições: lê o código QR e cola, numa página no telemóvel, a chave TorBox, add-ons, listas M3U com EPG, contas Xtream, chaves TMDB e Trakt.
- **Cópia de segurança e restauro** de todas as definições, na mesma página do telemóvel.
- **Arranque mais rápido**: o Início mostra logo os catálogos da última vez enquanto atualiza.
- **Diagnóstico**, em Definições: aparelho, Roku OS, rede, suporte de 4K/Dolby Vision/AV1, espaço das definições e últimos erros.
- **Aviso de nova versão**: se indicares um endereço de atualizações (um JSON com `version` e `notes`), a app avisa quando houver versão nova.

---

# v0.5

## Novidades da v0.5

### Aspeto
- **Abertura animada**: o logótipo cresce com brilho, o nome aparece por baixo e toca um som original. Desliga-se em Definições.
- **Início com destaque**: imagem de fundo, logótipo, ano, nota IMDb e sinopse do título selecionado, a mudar com fade.
- **Cartazes**: fazem zoom e ganham moldura colorida no foco, e as imagens aparecem com fade.
- **Carregamento**: cartazes "fantasma" a pulsar no Início e animação a rodar nas fontes, em vez de texto.
- **Transições** a deslizar entre ecrãs.
- **Detalhes renovados**: botões grandes (Reproduzir/Continuar, Episódios, Escolher fonte, Biblioteca), elenco, realização e **«Mais como este»**.
- **Temas de cor**: vermelho, azul, dourado, verde e roxo.

### Leitor
- **Erros explicados por tipo** (rede, ficheiro incompatível, DRM), com «Tentar de novo» quando é falha de rede.
- **Teste do link antes de tocar**: links mortos são saltados para a fonte seguinte, sem esperar pelo erro.
- **Saltar introdução e resumo**: aparece um botão quando os tempos existem (base IntroDB). Também pode ser automático.
- **Próximo episódio**: cartão com contagem de 10 s no genérico final, com «Ver agora» e «Cancelar».
- **Sincronizar legendas** do OpenSubtitles: adiantar ou atrasar 0,5/1/2 s, no menu ✱.
- **Lembra o idioma do áudio e das legendas** que escolhes em cada série.
- **Informação técnica**: fonte, servidor, formato, áudio e débito, no menu ✱.
- **Temporizador para desligar**: 15 a 120 min, com a pergunta «Ainda estás a ver?».

### Fontes e TorBox
- **Reprodução rápida**: «Reproduzir» ou «Continuar» escolhe sozinho a melhor fonte (em cache, compatível e no teu idioma).
- **«A minha TorBox»**, no menu do Início: os teus downloads com o progresso em %, e reprodução dos ficheiros prontos.
- **Idioma preferido nas fontes**: põe primeiro as dobradas ou dual-áudio em português, ou noutra língua à escolha.
- **Esconder fontes DV sem HDR, AV1 e 3D**, em Definições.

---

# v0.4

## Novidades da v0.4
- **Novo nome e ícone: Hortêncioflix.** As definições, a chave TorBox, a conta Stremio e as listas mantêm-se.
- **TV ao vivo com add-ons do Stremio**:
  - Qualquer add-on de TV que tenhas na conta Stremio, ou nos add-ons locais, aparece em «Listas e add-ons de TV» como uma fonte, com as suas categorias.
  - Os canais carregam por categoria; ao escolher um, a app pede a emissão ao add-on.
  - Funcionam também o mini-leitor, os favoritos, a troca de canal (Cima/Baixo) e o «Tentar a próxima fonte».
- **Diretório de add-ons de TV** (Listas → «+ Add-ons de TV do Stremio»), com add-ons de canais gratuitos verificados:
  - Watchio.live TV;
  - Stremio IPTV (dexter21767), com canais por país da base iptv-org;
  - «Outro add-on» para colares o URL de qualquer outro.
- **Pesquisar canal** pelo nome, dentro da fonte atual.
- **Vistos recentemente**: os últimos 10 canais que abriste.

### Add-ons de TV que se configuram no telemóvel
Alguns add-ons precisam de ser configurados numa página web primeiro:
1. Abre a página de configuração do add-on no telemóvel.
2. Configura e copia o link de instalação.
3. Instala-o na tua conta Stremio, e ele passa sozinho para a Hortêncioflix. Em alternativa, cola o link em «Outro add-on».

Exemplos:
- **FastChannels**: canais FAST gratuitos e legais (Pluto TV, Samsung TV Plus, Plex, The Roku Channel, Tubi…).
- **IPTV by Savi**: iptv-org ou a tua conta Xtream.

---

# v0.3

## Novidades da v0.3
- **Mini-leitor**: durante um vídeo ou canal, **Voltar** encolhe-o para o canto inferior direito e continua a tocar enquanto navegas.
  - **Repetir (↺)**: volta a ecrã inteiro.
  - **Play/Pausa**: pausa ou retoma.
  - **Voltar no ecrã Início**: fecha o mini-leitor.
  - Escolher outro filme ou canal substitui o que está a tocar, guardando primeiro o progresso.
- **Fonte alternativa**: se uma fonte falhar, o aviso de erro oferece **«Tentar a próxima fonte»**. Também podes trocar de fonte em qualquer momento no menu \* do leitor.
- O menu \* do leitor tem também **Minimizar** e **Parar e fechar o leitor**.
- **Confirmação ao sair**: Voltar no Início pergunta se queres mesmo sair, em vez de fechar logo a app.

---

# v0.2

App ao estilo Stremio para Roku: conta Stremio sincronizada, fontes via TorBox, legendas, faixas de áudio e TV ao vivo com várias listas.

## Atualizar a partir da v0.1
Abre o IP da Roku no navegador → **Upload** → escolhe `StreamBox-roku-v0.2.zip` → **Replace**.
As definições (chave TorBox, add-ons, lista IPTV) mantêm-se.

## Novidades da v0.2

### Leitor
- Tecla **\*** (ou **Baixo**) durante o vídeo abre o painel de opções:
  - **Legendas**: as embutidas no ficheiro + as do OpenSubtitles (português e inglês). Ligam-se sozinhas no idioma definido.
  - **Áudio**: troca a faixa de áudio (idioma ou formato).
  - **Recomeçar do início** e **Próximo episódio**.
- **Retomar**: ao reabrir um filme ou episódio, a app pergunta se queres continuar de onde paraste.
- **Séries**: no fim do episódio passa automaticamente ao seguinte, com a mesma fonte e qualidade.
- **Cima** mostra o título.

### Conta Stremio
- Definições → **Ligar conta Stremio**: aparece um código e um QR. Abre o link no telemóvel, inicia sessão e confirma.
- Alternativa: **Entrar com e-mail e palavra-passe**.
- Passa automaticamente para a Roku: **todos os teus add-ons**, a **biblioteca** e o **Continuar a ver**. Cada vez que abres a app, volta a sincronizar.
- O progresso do que vês na Roku é enviado para a tua conta, por isso continuas no telemóvel ou no PC. Podes desligar isto nas Definições.
- No ecrã de detalhes, a tecla **\*** adiciona ou remove o título da biblioteca Stremio.

### Fontes
- Lista mais legível: etiqueta de qualidade (4K/1080p), verde quando já está em cache no TorBox, nome do ficheiro, tamanho e seeds.
- As fontes em cache aparecem primeiro.
- Filtros nas Definições: **qualidade máxima** (4K, 1080p, 720p) e **só fontes em cache**.
- Pedidos aos add-ons feitos em paralelo, por isso carrega mais depressa.
- Séries com **seletor de temporada**.

### TV ao vivo
- **Várias listas**: em «Listas», adicionas e alternas entre elas.
- **Bases públicas gratuitas** já incluídas:
  - iptv-org: Angola, Portugal, Brasil, Moçambique, Cabo Verde, canais em português, notícias, desporto, infantil, filmes, música, documentários, mundo inteiro;
  - Free-TV.
- **Conta Xtream Codes**: servidor, utilizador e palavra-passe.
- **Favoritos**: tecla **\*** num canal.
- Logótipos dos canais e **Cima/Baixo** para mudar de canal durante a emissão.

## Limitações conhecidas
- Áudio **DTS/TrueHD** pode não tocar sem soundbar ou receiver. Nesse caso, escolhe outra fonte ou outra faixa de áudio.
- As legendas embutidas só aparecem se forem de texto (SRT). As de imagem (PGS/VobSub) não são suportadas pela Roku.
- Canais das bases públicas podem estar offline ou bloqueados por região, porque são mantidos pela comunidade.
- Ainda sem guia de programação (EPG).
- O tamanho e o estilo das legendas definem-se na Roku: Definições → Acessibilidade → Legendas.
