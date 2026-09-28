# Novidades

Versões do **Jr_Link**. A mais recente fica no topo.

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
