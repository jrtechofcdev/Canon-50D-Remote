# Novidades

Versões do **JR C50D Sync** (até a 2.7, Jr_Link). A mais recente fica no topo.

## [3.5] · 2026-09-29

- **Rotação que funciona sempre:**
  - Nas posições fixas (em pé, deitado para cada lado, de cabeça para baixo) a tela não gira mais sozinha.
  - Ao abrir, o app já entra na posição escolhida. Antes começava em Automático até carregar os ajustes, e girava sozinho por um instante.
  - **Automático** agora segue o sensor do celular mesmo com a rotação do Android travada. Antes dependia dessa trava: às vezes girava, às vezes não.
  - A posição padrão passou a ser **Em pé**.
- **Girar com a foto** (Ajustes → Tela, ligado por padrão): no visualizador e no modo cliente, a tela se ajusta a cada foto.
  - Foto em pé → tela em pé, em tela cheia. Foto deitada → tela deitada, do lado que você escolheu.
  - Dá para fotografar em retrato e em paisagem na mesma sessão e mostrar cada uma inteira ao cliente.
- **Botão de girar no modo cliente**, além da Sessão, do visualizador e do live view.
- **Girar foto (90°)** no visualizador: para fotos que a câmera salvou deitadas. Funciona como um recorte e não altera o arquivo original.
- **Prévia do RAW na posição certa:** a prévia tirada de dentro do CR2 agora guarda a rotação da câmera. Antes, fotos verticais feitas só em RAW apareciam deitadas.

## [3.4] · 2026-09-29

- **Corrigido o fechamento com o visualizador aberto:** o log da 3.3 mostrou a causa.
  - Com uma foto aberta em tela cheia, a chegada de uma foto nova fazia o app procurar a foto 13 numa lista que ainda tinha 12, e ele fechava (`IndexOutOfBoundsException` no visualizador).
  - A mesma falha foi corrigida no modo cliente e no Culling.
  - Um teste automático agora simula fotos chegando com cada uma dessas telas abertas.

## [3.3] · 2026-09-28

- **Correções do fechamento depois de várias fotos:**
  - *Processamento pela CPU:* quando o processamento de preset pela GPU falha, o app passa para a CPU. Nesse caminho, uma foto de 15 MP em tamanho cheio ocupava mais de 300 MB, e a segunda foto esgotava a memória. Agora esse caminho trabalha com no máximo 2560 px. Pela GPU nada muda.
  - *Cópia editada:* o tamanho da cópia editada se ajusta à memória livre no momento. Com pouca memória, os previews guardados são liberados antes.
  - *Memória do app:* o app pede ao Android mais memória para imagens (`largeHeap`) e libera o cache quando o sistema pede.
  - *Notificação e barra de progresso:* antes eram atualizadas a cada pedaço baixado (centenas de vezes por segundo), o que podia travar a tela até o Android fechar o app. Agora mudam a cada 1 % na tela e a cada 20 % na notificação.
  - *Erros em segundo plano:* downloads, análise, câmera e avisos não derrubam mais o app. O erro vai para o log e o app continua. A fila de downloads também não para mais por falta de memória.
- **Log do app salvo no celular** (Ajustes → Diagnóstico):
  - Um arquivo por dia, guardado por 10 dias, que não se perde ao fechar o app nem ao reiniciar o celular.
  - Registra conexão USB, cada download com tempo e tamanho, a memória a cada foto, as telas abertas, os avisos, os disparos e os erros com o detalhe técnico.
  - Se o app fechar por erro, o erro é gravado antes de ele sair.
  - Na próxima abertura, o app lê do Android por que a execução anterior terminou (erro, erro nativo de gráficos, travamento, falta de memória, fechado pelo sistema) e avisa no topo do Diagnóstico.
  - **Liga/desliga:** desligado, grava só erros e fechamentos inesperados, para o máximo de desempenho.
  - **Pasta dos logs:** escolha onde salvar (Downloads, cartão SD, pendrive). O arquivo é copiado para lá a cada 15 s.
  - **Compartilhar:** junta os últimos 3 dias em um arquivo e abre o menu de envio (WhatsApp, Drive, e-mail). Também dá para copiar e apagar os logs, e ver os registros recentes com o filtro "Só erros".

## [3.2] · 2026-09-28

- **Girar a tela do app todo:** o botão de girar (Sessão, visualizador e live view) abre um menu em círculo com as posições em pé, deitado para a esquerda, deitado para a direita e de cabeça para baixo, e Auto no meio. A escolha vale para todas as telas e fica guardada.
  - Deitado, as abas passam para uma barra lateral e as telas se reorganizam para usar a altura toda.
  - Girar não reinicia mais o app nem o live view.
- **Live view mais leve e com mais imagem:**
  - Tela cheia, sem as barras do Android.
  - Em pé, os controles ficam embaixo da imagem. Deitado, ficam nas laterais: exposição à esquerda, zoom, disparador e foco à direita.
  - Menos travadas: a imagem não cria um bitmap novo a cada quadro, a zebra e o peaking são calculados a cada dois quadros e o histograma a cada quatro, e só a imagem é redesenhada a cada quadro.
  - Fluidez ajustável (10 a 30 quadros por segundo).
  - Foco manual com ícones: montanha (longe), setas e flor (perto), AF no meio e profundidade de campo segurando.
- **Menos barulho de espelho:**
  - O app pede à 50D o AF **Modo Live** (foco pelo sensor, sem descer o espelho).
  - O toque na imagem só escolhe o ponto de foco. O AF fica no botão, ou no toque se você ligar.
  - Nas opções do monitor aparece a dica: Modo AF Live e Disparo silencioso Modo 1.
- **Disparador que espera o foco:**
  - Quando a 50D responde "não consegui focar" ou "ocupada", o app pergunta de novo até disparar. Por padrão espera até 5 s; dá para escolher 3, 5, 8 ou 12 s.
  - Antes, essa resposta era tratada como disparo feito e a foto se perdia.
  - O botão mostra *Focando…*, *Disparou* ou *Não disparou*, com o motivo (sem foco, cartão cheio).
  - No live view, a imagem pausa durante o disparo.
- **Sessão organizada por ícones:** as ferramentas ficam em três grupos (Câmera, Sessão, Cliente e extras). Cada uma tem ícone, nome e, quando faz sentido, o valor atual (look, momento, perfil).
- **Visualizador:**
  - Um toque esconde tudo e deixa só a foto. Dois toques ampliam no ponto tocado.
  - Segurar mostra o **antes** (sem preset e sem recorte); soltar volta ao depois.
  - Ações em ícones: Lightroom, WhatsApp, Enviar, Recortar, Nota e Abrir com.
  - Presets e Calibrar alinhados na mesma faixa.
  - Deitado, o painel vai para a lateral.
- **Culling com arrastar de verdade:**
  - O cartão segue o dedo e inclina.
  - Para cima aparece o carimbo vermelho **REJEITAR**; para baixo, o verde **MARCAR**.
  - Quando o gesto passa do ponto, o carimbo trava com uma vibração. Ao soltar (ou dar um peteleco), o cartão sai da tela.
  - Corrigido: rejeitar pulava uma foto.
  - A última escolha tem **Desfazer**.

## [3.1] · 2026-09-28

- **WhatsApp que abre a conversa certa:** o envio agora é em dois toques.
  - **1 · Abrir conversa:** abre direto o chat do número digitado, sem passar pela tela "Enviar para", já com a mensagem automática escrita.
  - **2 · Enviar fotos:** de volta ao app, as fotos vão para essa mesma conversa.
  - O **nome do cliente** passa a ser obrigatório (dá para desligar) e entra no `{cliente}` da mensagem.
  - Novo modelo **Envio das fotos** em Ajustes → Mensagens, com liga/desliga da mensagem automática.
- **Modo cliente refeito:**
  - A foto tem a própria área, e as informações (nome, velocidade, abertura, ISO) ficam numa linha embaixo, sem cortar.
  - Botão **Sair** no canto de cima, com cadeado quando há PIN. Segurar o dedo 3 s continua funcionando, e dá para desligar.
  - Barra de ícones grandes com nome embaixo: Escolher, WhatsApp, Recortar e Calibrar.
  - Presets em miniaturas da própria foto, com os favoritos primeiro.
- **Presets por categoria e favoritos:**
  - Estrela em cada preset; favoritos e os usados por último aparecem primeiro em todas as listas.
  - Segurar a miniatura de um preset também favorita.
  - Categorias com filtro na aba Presets. Crie, renomeie e apague categorias (apagar não apaga presets) e use **Mover para categoria** no menu.
- **Perfis de ajustes:** sets prontos (Casamento / igreja, Ensaio externo, Evento / festa, Estúdio / produto) e os seus, aplicados em um toque pelo chip **Perfil** na Sessão.
- **Backup dos ajustes:** um arquivo .json com ajustes, perfis e presets (com categorias e favoritos) para guardar ou passar para outro celular.
- **Ícones em todos os ajustes** e nas opções do live view, para achar cada opção de olho.
- Ajustes novos:
  - botões Sair e Recortar do modo cliente;
  - sair segurando o dedo;
  - nome obrigatório, mensagem automática e o que vai por padrão no WhatsApp;
  - tamanho das miniaturas da galeria.
- Banco de dados v7 (favoritos dos presets). Fotos, ajustes e presets das versões anteriores continuam.

## [3.0] · 2026-09-28

Novo nome, **JR C50D Sync** (JR da marca, C50D da Canon 50D, Sync da captura vinculada), e ícone novo: uma câmera nas cores da marca com a lente formada por setas de sincronia.

- **Live view profissional:** a imagem da 50D no celular como monitor externo.
  - Preset em tempo real, como um perfil de cor.
  - Guias de enquadramento: terços, áurea, centro, 1:1, Instagram 4:5 e Stories 9:16.
  - Zebra de estouro, focus peaking e histograma RGB.
  - Toque para focar, zoom da câmera 5× e 10×, lupa com pinça, foco fino e prévia de profundidade de campo.
  - ISO, Tv, Av, ±EV e WB, e disparo.
  - Tela sempre ligada, paisagem e monitor limpo.
- **Recorte no estilo Lightroom:**
  - Proporções prontas para Instagram (1:1, 4:5, 1,91:1), Stories/Reels/Status 9:16, Facebook, foto de perfil, YouTube e impressão (10×15, 13×18, 15×21, 20×25, 20×30, A4, 3×4).
  - Recorte livre, com moldura de alças e arraste para enquadrar.
  - Endireitar pela régua, com **Auto** (detecta o horizonte) ou traçando o horizonte com o dedo.
  - Girar 90° e espelhar.
  - Vale para a tela, a cópia editada e o envio, no tamanho de cada rede. O original não muda.
  - Recorte rápido em lote na galeria.
- **WhatsApp direto para o cliente:**
  - Na galeria, no visualizador e no modo cliente, digite o número e a conversa abre com a foto pronta (preset, recorte e marca d'água) já anexada.
  - Os números usados ficam em Recentes.
- **Modo cliente com zoom:** pinça e duplo toque. Com zoom, a foto não troca sozinha, e segurar a tela para sair não dispara durante a pinça. O cliente também pode receber a foto no WhatsApp dele.
- Nas versões anteriores, as fotos, os ajustes e os presets continuam; o app instala por cima.

## [2.7] · 2026-09-28

- **Aba Culling** na barra de baixo, entre Fotos e Presets. A seleção com análise local (tremidas, olhos fechados, sugeridas) antes ficava escondida em um chip dentro de Fotos.
- **Botão "Iniciar captura"** flutuante em todas as abas: conecta a 50D e abre a Sessão. Enquanto conecta, mostra o andamento; depois de conectada, vira um botão de câmera que volta para a captura.

## [2.6] · 2026-09-28

Seleção com análise local, gratuita e sem internet. Nenhuma foto sai do celular.

- **Alerta de foto tremida:** cada foto recebida é analisada (nitidez no rosto, ou no ponto mais nítido). Se saiu tremida ou fora de foco, o celular dá dois toques curtos e a foto ganha o selo **Tremida**. Sensibilidade ajustável.
- **Olhos fechados:** detecção de rostos com o Google ML Kit embutido no app (funciona offline). Avisa quando alguém piscou: "olhos fechados (2 de 5)".
- **Fotos parecidas e sugestões:** fotos da mesma pose são agrupadas, e a melhor de cada grupo (nitidez, olhos abertos, exposição) ganha o selo **Sugerida**.
- **Seleção rápida:** arrastar para cima rejeita, para baixo marca, estrelas de 1 a 5, comparar duas fotos lado a lado com zoom junto, filtros (não avaliadas, sugeridas, tremidas, olhos fechados, rejeitadas) e **Aceitar sugestões** com desfazer.
- Rejeitar nunca apaga arquivo; a galeria pode esconder as rejeitadas.
- O APK passou de 20 para 32 MB por causa do modelo de rostos (só as versões para celular ARM entram no APK).

## [2.5] · 2026-09-28

Ferramentas do dia a dia. Tudo funciona sem internet, e cada função tem liga/desliga em Ajustes.

- **Seleção pelo cliente:** no modo cliente, o cliente toca no coração das fotos que quer. No resumo da sessão você copia a lista para o filtro do Lightroom, copia as escolhidas para a pasta **Escolhidas** ou manda a seleção no WhatsApp.
- **Galeria em lote:** toque longo seleciona várias fotos. Dá para aplicar preset, enviar (JPG leve, editadas, originais), mandar ao Lightroom, favoritar, definir momento e apagar tudo de uma vez.
- **Mensagens prontas:** prévia, entrega, lembrete de pagamento e seleção recebida, com nome, quantidade, data e lista preenchidos. Abre direto no WhatsApp, com ou sem as fotos. Textos editáveis em Ajustes → Mensagens.
- **Momentos do evento:** chip **Momento** na Sessão (Making of, Cerimônia, Família, Festa…). Cada foto nova recebe o momento, com subpasta opcional `02 Cerimônia` e filtro na galeria.
- **Notas por foto:** texto ou voz (segurar para gravar), no visualizador.
- **Flash e foco:** alcance e abertura do Yongnuo por potência e ISO, e profundidade de campo/hiperfocal da 50mm f/1.8 e da 18-55 no sensor da 50D.
- **GPS nas fotos:** a posição do celular vai para o EXIF das cópias editadas e dos JPG leves. Funciona pelo GPS, sem internet. As cópias também levam os dados da câmera (abertura, velocidade, ISO, data).
- **Backup da sessão:** copia RAW, JPG, editadas e notas para o cartão SD ou um pendrive OTG e confere cada arquivo.
- **Relógio da câmera:** opção para a 50D pegar a hora do celular ao conectar. Se a câmera não aceitar, a opção se desliga sozinha.

## [2.4] · 2026-09-28

Presets do Lightroom dentro do app.

- Nova aba **Presets**: importa presets em **DNG, XMP, LRTemplate** ou **ZIP**, cria do zero, duplica, apaga e exporta em XMP.
- **Editor** com os painéis do Lightroom: Luz, Cor (balanço de branco, mistura de 8 cores, P&B), Efeitos (textura, claridade, desembaçar, vinheta, granulação), Detalhe, Curva (paramétrica e por pontos RGB/R/G/B), Color grading (3 rodas + global) e Calibração. Desfazer/refazer, segurar para ver o antes e alerta de recorte.
- **Look da sessão:** o preset escolhido é aplicado em cada foto nova, como um perfil de cor. O JPG e o RAW da câmera não mudam.
- **Cópia editada:** um JPG com o preset é salvo na pasta das editadas (liga/desliga e qualidade em Ajustes). Pode ser enviado pelo botão Enviar.
- **Calibrar:** ajusta só a foto aberta, com **Aplicar às próximas fotos** e **Salvar como preset**.
- **Modo cliente:** a foto aparece com o preset e o cliente pode trocar. Calibrar pede o PIN, se houver.
- **Pack Externo** com 11 presets para começar (Outdoor, Golden Hour, Flash Event, Clean B&W e outros).
- **Pasta de presets:** cada preset vira um `.xmp` na pasta escolhida; arquivos colocados nela entram no app.
- Na Sessão, os "presets" da câmera passaram a se chamar **Exposições**, para não confundir.
- A prévia é uma aproximação do Lightroom; o XMP exportado dá o resultado exato lá.

## [2.3] · 2026-09-28

Correção da conexão.

- Corrigido "Sem resposta da câmera" ao conectar: o app pedia leituras USB de 256 KB, que muitos celulares recusam. Agora usa blocos de 16 KB, aceitos por qualquer aparelho.
- Antes de abrir a sessão, o app reseta a USB da câmera e descarta respostas presas de tentativas anteriores. Se a primeira tentativa falhar, tenta de novo sozinho.
- O log de Diagnóstico agora diz se a falha foi timeout da câmera ou recusa do USB do celular.

## [2.2] · 2026-09-27

Identidade Jr_Tech_OFC.

- Novo nome **Jr_Link** e ícone com o símbolo da marca, redesenhado em vetor a partir do kit.
- Cores da marca em todo o app: menta `#00FCA8`, ciano `#1CDAFF` e grafite `#292929`.
- Fonte **Exo 2**, a mesma do logo.
- Marca d'água já vem com os logos Jr_Tech_OFC: horizontal branco, horizontal nas cores, completo, só o símbolo ou o seu PNG.
- Tela de abertura, notificações e rodapé "Sobre" com a marca.
- Modo cliente com assinatura discreta da marca (pode ser desligada).
- Disparador redesenhado: botão de intervalo à esquerda e miniatura da última foto à direita.

## [2.1] · 2026-09-27

Funções novas, cada uma com liga/desliga em Ajustes.

- **Ajustes por categoria:** Salvamento, Revisão, Câmera, Envio e cliente, Alertas, Tela, Diagnóstico.
- **Revisão:** slide automático para a foto nova, estrelas de 1 a 5, histograma RGB e alerta de estouro piscando.
- **Câmera:** bateria da 50D, navegação no cartão, presets de exposição e bracketing (AEB) de 3, 5 ou 7 fotos.
- **Envio:** "JPG leve" para WhatsApp e marca d'água aplicada só na cópia enviada.
- **Cliente:** modo cliente em tela cheia com PIN e resumo ao fim da sessão.
- **Campo:** vibração a cada foto, alerta se o cabo soltar, aviso de cartão e memória cheios, reenvio automático e modo escuro total.
- As fotos já recebidas continuam no app depois da atualização.

## [2.0] · 2026-09-27

Reescrita da captura vinculada. Detalhes em [`docs/AUDITORIA.md`](docs/AUDITORIA.md).

- Corrigido o travamento da 50D no modo de transferência: toda comunicação com a câmera agora espera a resposta antes do próximo comando.
- A câmera continua disparando pelo próprio botão durante a conexão.
- Captura vinculada real: cada foto nova é detectada e baixada do cartão.
- Auto-save de RAW e JPG em pastas escolhidas, com preview do RAW pelo JPEG embutido no CR2.
- Visualizador com zoom, compartilhamento e envio para o Lightroom.
- Removidos simulador, Live View e bibliotecas sem uso.
- Corrigido o fechamento do app ao pedir permissão USB no Android 14 e o erro ao compartilhar.

[2.3]: https://github.com/jrtechofcdev/Canon-50D-Remote/releases/tag/v2.3
[2.2]: https://github.com/jrtechofcdev/Canon-50D-Remote/releases/tag/v2.2
[2.1]: https://github.com/jrtechofcdev/Canon-50D-Remote/compare/de5f5ce...95221c3
[2.0]: https://github.com/jrtechofcdev/Canon-50D-Remote/commit/de5f5ce
