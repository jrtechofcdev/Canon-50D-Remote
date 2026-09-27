# Auditoria da versão 1.0 (gerada no AI Studio)

Sintoma relatado: ao conectar, o celular não dispara a câmera e a 50D fica presa na tela de
comunicação, sem conseguir fotografar.

## Causas encontradas

| # | Problema | Efeito |
|---|---|---|
| 1 | Disparo usava `RemoteReleaseOn/Off` (0x9128/0x9129), que a 50D (2008) não tem. O comando dela é `RemoteRelease` (0x910F). | Nada disparava. |
| 2 | As respostas PTP do disparo nunca eram lidas, e vários comandos rodavam em paralelo sem trava. | A transação ficava pendente e a câmera travava em "comunicando". |
| 3 | O loop de eventos estava vazio: nunca chamava `GetEvent`, então nunca sabia de foto nova. | Não existia captura vinculada de verdade. |
| 4 | O "download" gravava bytes zerados como `.CR2` ou desenhava uma imagem falsa. | Arquivos que não abriam. |
| 5 | Códigos de propriedade errados (WB, qualidade), e o valor ia no lugar errado do pacote (sem fase de dados). | Ajustes de ISO, WB etc. não chegavam na câmera. |
| 6 | Leitura USB não separava fase de dados e resposta nem tratava pacote de tamanho zero. | Sessão dessincronizada em arquivos grandes. |
| 7 | `PendingIntent` mutável com intent implícito. | Crash ao pedir permissão USB no Android 14+. |
| 8 | Compartilhar usava `FileProvider` sem declarar no manifesto. | "Erro ao compartilhar" sempre. |
| 9 | Fotos em pasta privada do app. | Invisíveis para galeria e Lightroom, apagadas ao desinstalar. |
| 10 | Simulador ligado por padrão. | Parecia conectado sem câmera nenhuma. |
| 11 | Build dependia de `debug.keystore` inexistente, e trazia Firebase, Retrofit e OkHttp sem uso. | APK não compilava e ficava pesado. |

## O que a versão 2.0 faz

- A sessão PTP roda em série: cada comando lê sua resposta antes do próximo. Ao sair, o app avisa a câmera (`SetRemoteMode 0` + `CloseSession`).
- Modo remoto + modo de eventos, sem travar a interface da câmera: o botão da 50D continua funcionando.
- Destino de captura: cartão. Cada foto nova chega por `ObjectAddedEx` e é baixada com `GetObject`.
- Auto-save por formato (JPG, RAW ou ambos), com pasta própria para cada um, subpasta por sessão e renomeação opcional.
- Preview de RAW pelo JPEG embutido no CR2, EXIF real, histograma, zoom, envio e Lightroom.
- Sem simulador, Live View, rede, Firebase nem Retrofit.
- 16 testes automáticos cobrindo pacotes PTP, leitura em várias partes, pacote zero, resposta ocupada/atrasada, eventos EOS e extração do preview do CR2.
