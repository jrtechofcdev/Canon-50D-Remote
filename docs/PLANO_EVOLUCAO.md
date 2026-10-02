# JR C50D Sync: plano de evolução (auditado e alinhado ao app)

## Contexto

O José Renato escreveu um plano de evolução e pediu que eu o auditasse contra o código real, alinhasse e recriasse para o app.

**Ideia central do plano dele:**
- O app deixa de ser só uma ferramenta de captura e vira o sistema pessoal do fotógrafo: **capturar → controlar → melhorar → entregar**.
- A peça nova é a **Entrega Local**: o celular do fotógrafo cria uma rede, serve as fotos e o cliente salva no próprio aparelho, sem internet e sem instalar nada.
- A **Fotografia Express** é a origem conceitual, mas não um limite do produto.

**O que este documento faz:**
1. mostra o que o app já tem em cada módulo;
2. corrige o que o Android não permite, com a alternativa real;
3. define a arquitetura do módulo `delivery`;
4. divide a entrega em fases, cada uma testável e desligável.

---

## 1. Auditoria: o que já existe (versão 3.8)

| Módulo do plano | Já existe no app | Onde |
|---|---|---|
| 📷 Captura | Sessão PTP USB, eventos EOS, disparo, live view, intervalômetro, bracketing, relógio | `core/ptp`, `core/camera` |
| 🔗 Sincronização | Download automático, fila com novas tentativas, reenvio do que ficou no cartão, 2º cartão, pastas do app | `core/tether`, `core/storage` (`PhotoStore`, `MirrorCopy`, `AppFolders`) |
| 🖼️ Fotografia | Galeria com filtros e lote, visualizador, culling com análise local (nitidez e olhos), momentos, notas | `ui/photos`, `ui/cull`, `core/analysis`, `core/notes` |
| 🎨 Edição | Presets (DNG/XMP), editor no estilo Lightroom, recorte, cópia editada, Loja de presets | `core/preset`, `core/render`, `core/shop`, `ui/preset`, `ui/crop` |
| 📤 Entrega | **Só por apps de terceiros:** WhatsApp (com atalhos de mensagem), compartilhar, Lightroom, JPG leve com marca d'água, modo cliente na tela do fotógrafo | `ui/PhotoActions.kt`, `core/export/ExportService.kt`, `ui/client` |
| ⚙️ Configurações | 11 seções, perfis, backup dos ajustes, modo desenvolvedor | `ui/settings`, `core/data` |

**Lacunas em relação ao plano:**
- **Rede local e servidor:** não existem.
- **Barramento de eventos interno:** não existe. Hoje só há `TetherController.saved` (`SharedFlow<Photo>`), que equivale a *PhotoCaptured*.
- **"Aprovada" e "editada":** existem como dados, mas não como eventos:
  - aprovada = `favorite`, `rating`, `clientPick`;
  - rejeitada = `rejected`;
  - editada = `presetId`, `adjust`, `editedUri`.
- **Banco:** Room na versão 7, com as tabelas `photos` e `presets`. A entrega precisa de tabelas próprias (migração 7→8).

**Peças que serão reaproveitadas, sem duplicar:**
- **`ExportService.exportLight`:** JPG redimensionado, com marca d'água e GPS. Gera as versões de entrega.
- **`Looks.exportFile` / `writeEditedCopy`:** a foto com o preset aplicado.
- **`CameraForegroundService`:** o modelo para o serviço que mantém a entrega viva com a tela apagada.
- **`AppFolders`:** ganha a pasta `07 Entregas` (histórico e cópias entregues).
- **`MessageLibrary`:** ganha a mensagem "Suas fotos estão no Wi-Fi" com `{codigo}`.
- **Seleção em lote da galeria:** ganha a ação **Entregar**.

---

## 2. Correções técnicas ao plano (o que o Android permite)

| No plano | Realidade | Como fica |
|---|---|---|
| **App cria o hotspot com nome e senha fixos** | O hotspot que um app comum pode abrir (`LocalOnlyHotspot`) tem nome e senha **sorteados pelo Android** a cada vez. Ligar o roteador normal do celular é proibido para apps. | **Wi-Fi Direct em modo grupo** (`WifiP2pManager.createGroup` com `WifiP2pConfig.Builder`, Android 10+). O app escolhe nome e senha fixos, liga e desliga sozinho, e iPhone e Android entram como numa rede comum. Exigência do Android: o nome começa com `DIRECT-` + 2 letras, ex.: **`DIRECT-JR-Fotos`**. O IP do celular é sempre `192.168.49.1`. **Alternativa** (se algum aparelho não suportar): o roteador Wi-Fi do próprio celular, ligado pelo fotógrafo (o app abre a tela certa), ou o Wi-Fi do local. |
| **Portal cativo abre sozinho** | Abrir sozinho exige controlar o DNS e a porta 80, o que só é possível com root. | **QR Code fixo do portal:** `http://192.168.49.1:8080`. Funciona sempre, porque o IP do Wi-Fi Direct é fixo. A placa física leva dois QR: **① entrar no Wi-Fi** e **② abrir as fotos**. Na tela do fotógrafo também aparece um QR por cliente, já com o acesso dele. |
| **"Clientes conectados"** | Com Wi-Fi Direct, o app vê a lista de aparelhos do grupo. No roteador do sistema, não vê. | Mostra os aparelhos do grupo (Wi-Fi Direct) e os **clientes com o portal aberto** nos últimos 2 min (vale para qualquer rede). |
| **NFC** | Android lê a etiqueta Wi-Fi (NDEF WSC) e conecta. **iPhone não aceita Wi-Fi por NFC** (só abre links). | NFC é opcional e só para Android. O QR continua sendo o caminho universal, como o próprio plano já diz. Gravar a etiqueta pelo app fica para a Fase 3. |
| **"Salvar foto"** | No Android e no computador, o botão baixa a foto. No iPhone, por `http://` local, o Safari não oferece o compartilhar direto para Fotos. | Botão **Salvar** (download) para todos. No iPhone, o portal mostra "Toque e segure a foto → Adicionar às Fotos". |
| **Rede sem internet** | Android e iPhone avisam "sem internet" e alguns trocam para os dados móveis. | A placa e o portal instruem: "**Manter conectado / Usar sem internet**". **Risco principal, validado na Fase 0** com o seu Samsung e com um iPhone. |
| **Continuar transferência interrompida** | Possível por HTTP. | O servidor responde `Range` (download retomável), e a fila nunca apaga o arquivo antes da confirmação. |

---

## 3. Arquitetura: módulo `core/delivery` (desacoplado, desligável)

```
core/delivery/
  DeliveryEvents.kt     eventos da foto (interface entre captura e entrega)
  DeliveryStore.kt      Room: sessões de cliente + itens da fila (migração 7→8)
  DeliveryQueue.kt      prepara cada foto (JPG de entrega) uma de cada vez, sem duplicar
  DeliveryRules.kt      modo automático / manual: que foto vai para qual sessão
  LocalServer.kt        servidor HTTP leve (NanoHTTPD), só ligado durante a entrega
  PortalAssets          assets/portal/: index.html, app.js, style.css (sem internet, sem CDN)
  network/
    DeliveryNetwork.kt  interface: start(nome, senha) / stop / estado / clientes
    WifiDirectNetwork.kt   (Android 10+) grupo com nome e senha fixos
    SystemHotspotNetwork.kt  orienta o fotógrafo a ligar o roteador e detecta o IP
    SameWifiNetwork.kt  Wi-Fi do local
  DeliveryService.kt    serviço em primeiro plano ("Entrega ativa · 2 clientes · 17 fotos")
ui/delivery/
  DeliveryScreen.kt     ENTREGA LOCAL: status, iniciar/parar, clientes, fila, QR
  ClientSessionSheet.kt novo cliente → código + QR na tela
  PlateExport.kt        placa para imprimir (PNG/PDF A6): QR Wi-Fi + QR portal + marca
```

- **Eventos** (`DeliveryEvents`, um `SharedFlow`): `PhotoCaptured`, `PhotoApproved`, `PhotoEdited`, `PhotoQueued`, `PhotoDelivered`.
  - Os emissores são o `TetherController.saved`, as ações de favoritar, estrelas e escolha do cliente, o `Looks.setLook` e a cópia editada.
  - A entrega só **ouve**: não conhece câmera, PTP nem USB.
- **Desligável:** Ajustes → Entrega → **Entrega local** (padrão desligado). Desligada:
  - nada sobe, nenhuma permissão nova é pedida;
  - captura, galeria, presets e WhatsApp seguem iguais.
- **Banco** (`delivery_sessions`, `delivery_items`):
  - **sessão:** cliente, código de 6 dígitos, token aleatório de 128 bits, modo, criada e encerrada em;
  - **item:** foto, sessão, estado (aguardando → preparando → disponível → baixada | erro), arquivo, tamanho, número de downloads.
- **Arquivos de entrega:** `cacheDir/delivery/<token>/`, gerados pelo `ExportService` com o tamanho escolhido. Tamanhos:
  - **leve** (2048 px, marca d'água opcional);
  - **editada inteira**;
  - **original JPG**.
  - Se a pasta `07 Entregas` estiver ligada, uma cópia vai para `07 Entregas/<cliente>/`.
- **Servidor** (porta 8080, só com a entrega ativa):
  - `/` mostra o portal (pede o código, ou já entra pelo QR do cliente);
  - `/api/s/<token>/fotos` lista as fotos da sessão;
  - `/f/<token>/<item>` entrega o arquivo, com Range.
  - **Sem listagem de pastas:** só arquivos ligados a um token válido.
  - **Proteção do código:** limite de tentativas de 5 por minuto por aparelho.
- **Portal:** uma página leve, responsiva, offline e na identidade do app (grafite, menta, Exo 2 embutida). Ela traz:
  - "Sua foto está pronta!", a foto grande, **Salvar foto**, a contagem, a grade das outras;
  - atualização a cada 3 s (no modo automático a foto nova aparece sozinha);
  - instruções de salvar por aparelho.

---

## 4. Fluxos

**Modo automático (eventos, Fotografia Express):**
- Foto capturada → `PhotoCaptured`.
- Se a entrega está ativa e o modo é automático, a foto entra na fila da **sessão ativa**, depois de aplicado o look da sessão (se houver).
- A sessão ativa é o cliente da vez. O fotógrafo troca de cliente com um toque e cria um novo em 2 toques.
- **Opção "Galeria do evento":** uma sessão aberta, para todos que têm o código do evento. Fica desligada por padrão, pela privacidade.

**Modo manual (ensaios):**
- Galeria → seleção → **Entregar** → escolhe o cliente (ou cria) → fila.
- Também do visualizador e do culling (marcadas como favoritas).

**Cliente:**
1. QR ① ou NFC para entrar no Wi-Fi;
2. QR ② ou QR da tela do fotógrafo para abrir o portal;
3. código, se não veio no QR;
4. ver e salvar.

**Encerrar entrega:**
- invalida tokens, para o servidor e desliga a rede;
- limpa `cacheDir/delivery` (opção "manter por 24 h" para reabrir);
- o histórico fica no banco.

---

## 5. Fases (cada uma é uma versão, desligável, com testes)

### Fase 0: prova técnica (3.9, dentro do modo desenvolvedor)

Antes de construir a interface, provar no aparelho real o que só ele responde.
- **Desenvolvedor → Teste de entrega local:**
  - cria o grupo `DIRECT-JR-Teste` com senha fixa;
  - sobe o servidor com uma página de teste e uma foto;
  - mostra o QR de Wi-Fi e o QR de URL;
  - registra cada conexão no log.
- **Checklist com o José:**
  - Samsung A16 (Android 16) cria o grupo com o cabo da 50D ligado;
  - um Android e um iPhone entram pelo QR;
  - a página abre em `192.168.49.1:8080` com o aviso "sem internet";
  - o download funciona nos dois;
  - bateria e temperatura em 30 min.
- **Decisão no fim da fase:** Wi-Fi Direct como rede principal, ou o roteador do sistema como principal.

### Fase 1: núcleo da entrega, modo manual (4.0)

- `DeliveryEvents`, `DeliveryStore` (migração 7→8 com teste), `DeliveryQueue`, `LocalServer`, portal.
- **ENTREGA LOCAL**, aberta pelo tile **Entrega** na Sessão (grupo Cliente e extras) e por Ajustes → Entrega. Mostra:
  - servidor ativo, rede, clientes, fotos disponíveis;
  - **Abrir portal** (testa no próprio celular);
  - **Parar entrega**.
- Clientes com código e QR na tela.
- Ação **Entregar** no lote da galeria e no visualizador.
- Fila visível:
  - ● enviada · ◐ preparando · ○ aguardando · ⚠ erro, com tentar de novo;
  - sem duplicar: a mesma foto na mesma sessão entra uma vez só.
- **Rede nesta fase:** a escolhida na Fase 0. A outra fica como alternativa em Ajustes.

### Fase 2: modo automático, rede completa e placa (4.1)

- **Modo automático** com sessão ativa e a opção Galeria do evento.
- **Ajustes → Entrega:**
  - nome e senha da rede (estáveis);
  - modo;
  - tamanho da foto;
  - marca d'água;
  - validade da sessão;
  - limpar ao encerrar.
- **Placa para imprimir:** PNG e PDF A6/A5 com QR Wi-Fi, QR do portal e a marca Jr_Tech_OFC, pronta para a gráfica.
- **Downloads retomáveis** (Range) e confirmação de download no item (`PhotoDelivered`).
- `DeliveryService` em primeiro plano. A notificação mostra clientes e fotos, com o botão Parar.

### Fase 3: evolução (4.2+, conforme o uso)

- No portal:
  - o cliente marca favoritas, que chegam como `clientPick` e aparecem no culling;
  - prévia leve e "baixar original";
  - marca d'água só na prévia;
  - personalização (nome do fotógrafo, cores).
- QR individual impresso por cliente; gravar etiqueta NFC Wi-Fi pelo app (Android).
- Histórico e estatísticas de entrega (quantas fotos baixadas por cliente).
- Vídeos (MOV da câmera) quando a captura de vídeo existir.

---

## 6. Alinhamento de produto (o que muda no app, além da entrega)

- **Origem documentada:** seção "Origem: Fotografia Express" no `DESIGN.md` e no README. O app segue com a identidade **JR C50D Sync**, sem virar app da Fotografia Express.
- **Cadeia única:** cada função nova precisa responder a **Capturar → Controlar → Melhorar → Entregar**.
- **Ajustes reagrupados** pelos módulos do plano, sem perder nada:
  - Câmera;
  - Captura e salvamento;
  - Fotografia (revisão, culling);
  - Edição (presets, Loja);
  - **Entrega** (WhatsApp, mensagens, modo cliente, entrega local);
  - Sistema (tela, alertas, perfis, diagnóstico, desenvolvedor).
- **Fluxo em poucos toques** na tela inicial: Capturar → Selecionar (culling) → Editar (look) → **Entregar**. O tile Entrega mostra o estado ("2 clientes · 17 fotos") quando ativo.

---

## 7. Privacidade e segurança

- **Acesso por sessão:**
  - uma sessão por cliente;
  - token aleatório (128 bits) no QR;
  - código de 6 dígitos com limite de tentativas;
  - nada é público sem sessão.
- **Servidor:** só liga com a entrega ativa e só na interface da rede de entrega. Nunca expõe as pastas do app.
- **Encerrar:** invalida tokens, para o servidor, desliga a rede e apaga os temporários (ou guarda por 24 h, se o fotógrafo pedir).
- **Permissões novas só quando a entrega é ligada:**
  - `NEARBY_WIFI_DEVICES` (Android 13+);
  - localização (Android 10–12, exigência do Wi-Fi Direct);
  - `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE`.

---

## 8. Arquivos principais (Fase 0–2)

- **Novos:**
  - `core/delivery/*`, `ui/delivery/*`
  - `app/src/main/assets/portal/*`
  - teste `DeliveryStoreMigrationTest`
- **Alterados:**
  - `core/data/PhotoDatabase.kt` (versão 8);
  - `core/data/AppSettings.kt` (seção Entrega);
  - `core/tether/TetherController.kt` (emite eventos);
  - `core/render/Looks.kt` (PhotoEdited);
  - `ui/photos/PhotosScreen.kt` e `ViewerScreen.kt` (Entregar);
  - `ui/session/SessionExtras.kt` (tile Entrega);
  - `ui/settings/SettingsScreen.kt`;
  - `core/storage/AppFolders.kt` (`07 Entregas`);
  - `AndroidManifest.xml` (serviço e permissões);
  - `TetherApp.kt`.
- **Dependências:**
  - `org.nanohttpd:nanohttpd` (servidor, ~100 KB);
  - `com.google.zxing:core` (QR, ~500 KB).
  - Ambas funcionam offline.

## 9. Verificação

- **Testes de unidade:**
  - migração 7→8 preserva fotos e presets;
  - fila sem duplicar e com retomada após erro;
  - regras automático/manual;
  - tokens: sessão A não vê foto da B, código errado é bloqueado após 5 tentativas;
  - servidor (NanoHTTPD em porta local no teste): lista, Range parcial e 404 sem token;
  - entrega desligada não sobe nada.
- **Portal:** captura em 375 px e 1280 px, testada pelo próprio servidor no teste.
- **No aparelho (José), por fase:**
  - **Fase 0:** checklist da prova técnica.
  - **Fase 1:** 2 clientes simultâneos, ensaio manual de ponta a ponta.
  - **Fase 2:** evento simulado com a 50D, 20 fotos em modo automático, 3 celulares (incluindo iPhone), placa impressa.
- Cada versão: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` limpo, CI verde, APK e CHANGELOG.
