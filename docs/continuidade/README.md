# Continuidade: estado do trabalho

Pasta com o que a sessão anterior produziu, para a próxima sessão continuar sem refazer a análise.
Branch: `feature/video-experimental`. A `main` não foi alterada.

## Arquivos desta pasta

| Arquivo | O que é | Status |
|---|---|---|
| `LIVE_VIEWER_REVERSE_ENGINEERING.md` | Protocolo PTP/EOS da 50D: Live View, disparo, eventos, arquitetura proposta e plano de testes | Referência |
| `CORRECAO_DISPARO_50D.md` | Por que o disparo falha e como corrigir (preferir 0x9128/0x9129 em etapas, soltar sempre em `finally`, confirmar pelo ObjectAddedEx, botão pressionar/segurar/soltar) | **Não implementado** |
| `MELHORIAS_CANON_50D_REMOTE.md` | Melhorias de funcionamento por módulo (M1 a M7): disparo, download em blocos, retomar download, 0x9127, capacidades, SetRemoteMode, CaptureDestination | **Não implementado** (só o download em blocos existe, e só para vídeos) |
| `PROMPT_PROXIMA_SESSAO.md` | O prompt para começar a próxima sessão | — |

Os números de linha citados nos dois documentos de correção são do commit `c3bd775` (main, versão 5.1). Confira as linhas no código atual antes de editar.

## Módulo de vídeo experimental (já no branch, nunca compilado)

Commit `55b5bb9`. Arquivos novos:

- `core/video/VideoLog.kt`, `LiveRecorder.kt`, `VideoController.kt`, `VideoLibrary.kt`
- `ui/video/VideoGallery.kt`, `ui/video/LiveRecordSheet.kt`, `ui/settings/VideoSettings.kt`
- Testes: `test/core/ptp/VideoFormatTest.kt` e um teste novo em `AppLogTest`

Arquivos alterados:
- `AppLog`, `CameraManager`, `PtpConstants`, `PtpDataReader`, `EosEventParser`
- `AppFolders`, `TetherApp`, `PhotosScreen`, `LiveViewScreen`
- `SettingsScreen`, `SettingsIndex`, `SettingsIndexTest`, `file_paths.xml`, `CHANGELOG.md`

Esta máquina não tinha Java nem Android SDK: o código nunca foi compilado nem testado.
