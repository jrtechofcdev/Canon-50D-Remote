# Continuidade: estado do trabalho

Pasta com o que a sessão anterior produziu, para a próxima sessão continuar sem refazer a análise.
Branch: `feature/video-experimental`. A `main` não foi alterada.

## Arquivos desta pasta

| Arquivo | O que é | Status |
|---|---|---|
| `LIVE_VIEWER_REVERSE_ENGINEERING.md` | Protocolo PTP/EOS da 50D: Live View, disparo, eventos, arquitetura proposta e plano de testes | Referência |
| `CORRECAO_DISPARO_50D.md` | Por que o disparo falha e como corrigir (preferir 0x9128/0x9129 em etapas, soltar sempre em `finally`, confirmar pelo ObjectAddedEx, botão pressionar/segurar/soltar) | **Implementado** nas versões 5.2 a 5.3.4 (`ShutterPress.kt`), trazidas para este branch por merge |
| `MELHORIAS_CANON_50D_REMOTE.md` | Melhorias de funcionamento por módulo (M1 a M7): disparo, download em blocos, retomar download, 0x9127, capacidades, SetRemoteMode, CaptureDestination | **Implementado** (5.3): `PartialDownload.kt` para fotos e vídeos, retomada no `TetherController`, `CameraCapabilities.kt`; M6/M7 só no modo desenvolvedor |
| `PROMPT_PROXIMA_SESSAO.md` | O prompt para começar a próxima sessão | — |

Os números de linha citados nos dois documentos de correção são do commit `c3bd775` (main, versão 5.1). Confira as linhas no código atual antes de editar.

## Vídeo: só leitura dos vídeos da câmera

O José pediu para retirar os módulos de filmar (método A pelo SET, método B gravando o live view, laboratório de comandos, monitor de eventos, log separado do vídeo e o módulo Magic Lantern). Ficou só:
- `core/video/VideoController.kt`: procurar vídeos no cartão, baixar (`CameraManager.downloadInChunks`, que usa o `PartialDownload`) e baixar sozinho no ObjectAddedEx de vídeo;
- `core/video/VideoLibrary.kt` + `ui/video/VideoGallery.kt`: pasta "09 Videos", galeria e player;
- `ui/settings/VideoSettings.kt`: Ajustes → Vídeos da câmera.

O código retirado está no histórico do branch (commits 55b5bb9 a b121e9d), se um dia voltar.

## Depois do live view (versões 5.3.1 a 5.3.4, testadas na 50D)

- O corpo da 50D ficava em BUSY depois do live view do app. A correção segue o libgphoto2: modo remoto 0 e de novo 1, na mesma sessão (`CameraManager.resetRemoteMode`). O mesmo reinício roda ao passar o live view para a câmera no modo SET.
- A 50D não aceita uma sessão nova sem tirar o cabo depois de fechar a sessão: não usar reconexão automática.
- `0xD1B3` é a nitidez do live view, não o modo: o app não grava essa propriedade.

## Magic Lantern (pesquisa no código do ML, espelho reticulatedpines/magiclantern_simplified)

- O ML registra a operação USB `0xA1E8`, com o subcomando no primeiro parâmetro: `0` versão do protocolo (resposta com 2 parâmetros, 0.1), `1` textos do build (segundo parâmetro de 0 a 3: versão, id, data, usuário; os dados vêm em ASCII), `4` nomes dos menus raiz.
- `GetMenuStruct(s)` e `SetMenu` usam um ponteiro não inicializado no código atual (a busca do menu está comentada). Não enviar: pode mexer em memória aleatória da câmera.
- O protocolo CHDK (`0x9999`) também existe no ML, mas `ExecuteScript` não está implementado. Não há comando USB para começar a filmar.
- No ML da 50D: `FEATURE_MOVIE_RECORDING_50D`, sem áudio. O `movie.start()` do Lua exige o live view ligado na câmera.
- Teste em aberto: `0xD1B8 = 4` (estado de gravação do EVF, comando da Canon) com o live view da câmera ligado.
