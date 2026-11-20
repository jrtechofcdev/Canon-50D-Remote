# Prompt para a próxima sessão

```
Estou continuando o trabalho no meu app JR C50D Sync (Kotlin + Jetpack Compose, captura
vinculada USB para a Canon EOS 50D). Mude para o branch feature/video-experimental.
Leia nesta ordem, antes de qualquer coisa:
1. CLAUDE.md
2. docs/continuidade/README.md (estado do trabalho e lista dos arquivos)
3. docs/continuidade/CORRECAO_DISPARO_50D.md
4. docs/continuidade/MELHORIAS_CANON_50D_REMOTE.md
5. docs/continuidade/LIVE_VIEWER_REVERSE_ENGINEERING.md (referência do protocolo)

Sobre a câmera: a 50D de fábrica não grava vídeo. A minha filma ao apertar SET e acende
o LED vermelho (comportamento do Magic Lantern). Não tem microfone: vídeo sem áudio.
Com o live view do app aberto, o SET não respondia. Por isso o módulo tem dois métodos:
A) gravar pela câmera (SET), com o app saindo do caminho;
B) o celular grava a imagem do live view que a câmera manda.

O módulo de vídeo experimental já está escrito no branch (commit 55b5bb9), mas NUNCA
foi compilado nem testado.

Tarefas, nesta ordem:
1. Compilar e testar: gradle :app:testDebugUnitTest e gradle :app:assembleDebug.
   Sem SDK local, abra um PR do branch para a main e use o build do GitHub Actions.
   Corrija todos os erros.
2. Revisar os pontos de risco do vídeo: early return em composables (PhotosScreen,
   RecordingChip), escrita YUV do LiveRecorder (rowStride/pixelStride), e
   handLiveViewToCamera junto com o LaunchedEffect do LiveViewScreen.
3. Implementar a correção do disparo de CORRECAO_DISPARO_50D.md (seções 3.1 a 3.4).
   Mantenha o 0x910F como alternativa e registre no log qual método a câmera usou.
4. Implementar as melhorias M2 a M5 de MELHORIAS_CANON_50D_REMOTE.md. O download em
   blocos já existe para vídeos (CameraManager.downloadInChunks): reaproveite nas fotos.
   M6 e M7 só como opção de teste no modo desenvolvedor.
5. Gerar o APK e me passar um roteiro curto de testes na 50D: disparo (T1 a T10),
   vídeo método A nas duas variantes, método B com 1 minuto, "Reconectar e procurar",
   comandos de teste e monitor de eventos.
6. Quando eu mandar o log, analisar as linhas VIDEO-… e DISPARO e dizer o que funcionou.

Regras:
- Os números de linha dos documentos são da versão 5.1 (commit c3bd775): confira antes de editar.
- Não quebrar o fluxo das fotos (TetherController, banco de fotos, culling, entrega).
- Não fazer merge na main nem mudar o versionName sem eu pedir.
- Comentários de código curtos, conforme .claude/skills/antislop-code/SKILL.md.
- Commits pequenos, um por tarefa.
- Responder em português.
```
