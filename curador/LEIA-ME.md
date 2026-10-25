# Hortêncioflix · Fontes curadas

Servidor pequeno (Cloudflare Workers, gratuito) que junta as fontes de todos os teus add-ons num só pedido:
pede a todos em paralelo, tira repetidos, confirma no TorBox o que já está pronto e torna jogáveis pelo
TorBox as fontes «só torrent». O resultado fica 10 min em cache.

Não guarda nada: a lista de add-ons e a chave TorBox vão no endereço de cada pedido, que só as tuas apps conhecem.

## Publicar (uma vez)
1. Cria uma conta gratuita em https://dash.cloudflare.com
2. *Workers & Pages* → copia o **Account ID** (coluna da direita).
3. *My Profile › API Tokens › Create Token* → modelo **Edit Cloudflare Workers** → *Create* → copia o token.
4. No GitHub (repositório da app): *Settings › Secrets and variables › Actions › New repository secret*:
   - `CLOUDFLARE_API_TOKEN` = o token
   - `CLOUDFLARE_ACCOUNT_ID` = o Account ID
5. *Actions › Curador de fontes › Run workflow*. O endereço fica no ramo `ci-curador`
   (algo como `https://hortencio-fontes.<o-teu-nome>.workers.dev`).

## Ligar nas apps
Android: *Definições › Fontes curadas* → cola o endereço → *Testar agora*. A Roku recebe-o na sincronização
(e pode pausá-lo nas Definições). Se o servidor falhar, as apps voltam a pedir a cada add-on sozinhas.

## Testes
`node curador/test.mjs` (sem rede).
