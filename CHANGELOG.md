# Novidades

Versões do **Jr_Link**. A mais recente fica no topo.

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
