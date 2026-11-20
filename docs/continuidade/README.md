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

## Módulo de vídeo experimental

Commit `55b5bb9`. Arquivos novos:

- `core/video/VideoLog.kt`, `LiveRecorder.kt`, `VideoController.kt`, `VideoLibrary.kt`
- `ui/video/VideoGallery.kt`, `ui/video/LiveRecordSheet.kt`, `ui/settings/VideoSettings.kt`
- Testes: `test/core/ptp/VideoFormatTest.kt` e um teste novo em `AppLogTest`

Arquivos alterados:
- `AppLog`, `CameraManager`, `PtpConstants`, `PtpDataReader`, `EosEventParser`
- `AppFolders`, `TetherApp`, `PhotosScreen`, `LiveViewScreen`
- `SettingsScreen`, `SettingsIndex`, `SettingsIndexTest`, `file_paths.xml`, `CHANGELOG.md`

Compilado e com os testes passando (commit `a0ff9e9`). Ainda não testado na câmera.

## Depois do live view (versões 5.3.1 a 5.3.4, testadas na 50D)

- O corpo da 50D ficava em BUSY depois do live view do app. A correção segue o libgphoto2: modo remoto 0 e de novo 1, na mesma sessão (`CameraManager.resetRemoteMode`). O mesmo reinício roda ao passar o live view para a câmera no modo SET.
- A 50D não aceita uma sessão nova sem tirar o cabo depois de fechar a sessão: não usar reconexão automática.
- `0xD1B3` é a nitidez do live view, não o modo: o app não grava essa propriedade.
