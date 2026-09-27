# Tether 50D

Captura vinculada via USB para a **Canon EOS 50D** no Android.

Com a câmera ligada ao celular por um cabo USB OTG:

- **Você fotografa normalmente pela câmera** (botão, dials e menus continuam funcionando).
- Cada foto é gravada no cartão CF, que serve de backup, **e uma cópia chega sozinha no celular**.
- O app salva **RAW (CR2)** e/ou **JPG** em **pastas escolhidas por você**, que podem ser pastas diferentes para cada formato.
- A tela **Sessão** mostra a última foto, a fila de download, a exposição atual (Tv, Av, ISO, ±EV, WB) e um disparador remoto.
- No visualizador você tem zoom para conferir o foco, histograma, **Enviar** (WhatsApp, Drive…) e **Lightroom**. Para quadros com RAW + JPG, dá para escolher mandar só o JPG, só o RAW ou os dois.

O app não tem Wi-Fi, FTP nem Live View. Ele usa só a USB, só com a 50D.

---

## 1. Como usar

1. Na 50D, no menu de configuração (ícone de ferramenta amarela), deixe **Desligamento automático** em **Desativado** para sessões longas. O app também mantém a câmera acordada enquanto está conectado.
2. Ligue a câmera e conecte o cabo **USB OTG** no celular.
3. O Android pergunta se deve abrir o **Tether 50D**. Marque **"Usar por padrão"** para ele abrir e liberar a USB sozinho nas próximas vezes.
4. Na primeira vez, vá em **Ajustes** e toque em **Escolher pasta** para os JPG e para os RAW.
5. Fotografe. As fotos aparecem na aba **Sessão** e ficam na aba **Fotos**.

*Dica:* o disparador do app funciona como apertar o botão até o fim. Com AF ligado, a câmera foca antes. Se não achar foco, ela não dispara. Com a lente em MF ou usando AF no botão traseiro (AF-ON), o disparo é imediato.

### Onde as fotos ficam

| Ajuste | Resultado |
|---|---|
| Pasta escolhida | `…/sua pasta/` (ou `…/sua pasta/Nome da sessão/` com a subpasta ligada) |
| Sem pasta escolhida | `Android/data/com.jrtechofc.tether50d/files/Pictures/Canon50D/JPG` e `/RAW` (apagada se desinstalar o app) |

---

## 2. Como gerar o APK

Requisitos: **Android Studio** recente (2025 ou mais novo) **ou** JDK 17+ com o Android SDK instalado.

**Pelo Android Studio:** *File → Open* na pasta do projeto, espere o Gradle sincronizar e use *Build → Build APK(s)*.

**Pelo terminal:**

```bash
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Passe para o celular e instale (permita "instalar apps desconhecidos").

Testes automáticos do protocolo:

```bash
./gradlew :app:testDebugUnitTest
```

---

## 3. Estrutura do código

```
app/src/main/java/com/example/
├── TetherApp.kt                 cria as peças do app (câmera, banco, configurações)
├── MainActivity.kt              navegação entre Sessão / Fotos / Ajustes
├── core/
│   ├── ptp/                     protocolo PTP da Canon (pacotes, sessão USB, eventos, tabelas de valores)
│   ├── camera/CameraManager.kt  conecta na 50D, lê eventos, dispara, muda ISO/Av/Tv…
│   ├── tether/TetherController.kt  auto-save: baixa cada foto nova e grava na pasta certa
│   ├── storage/                 gravação nas pastas escolhidas + preview extraído do CR2
│   ├── data/                    banco de fotos (Room) e configurações (DataStore)
│   └── service/                 notificação que mantém a conexão com a tela apagada
└── ui/                          telas em Jetpack Compose
```

---

## 4. Como personalizar

| Quero mudar… | Onde |
|---|---|
| Cores (fundo, âmbar de destaque) | `ui/theme/Theme.kt` → objeto `Tone` |
| Nome do app | `app/src/main/res/values/strings.xml` |
| Textos das telas | arquivos em `ui/session`, `ui/photos`, `ui/settings` |
| Formato padrão (RAW+JPG) | `core/data/AppSettings.kt` → `Settings.downloadMode` |
| Padrão do nome renomeado (`Sessão_0001`) | `core/tether/TetherController.kt` → `savedBase` |
| Velocidade da leitura de eventos | `core/camera/CameraManager.kt` → `POLL_IDLE_MS` |

---

## 5. Se algo der errado

| Sintoma | O que fazer |
|---|---|
| "Câmera não conectada" | Confira se a câmera está ligada, se o cabo é OTG e se a chave da 50D não está em OFF. Toque em **Conectar**. |
| "Permissão USB negada" | Toque em **Conectar** e depois em **Permitir**. |
| "Outro app está usando a câmera" | Feche outros apps de câmera/USB e tente de novo. |
| Foto não chega | Veja se o formato em **Ajustes** inclui o tipo que a câmera grava (ex.: câmera em RAW e app em "Só JPG"). |
| Não dispara pelo app | AF sem foco. Use MF ou AF-ON, ou mire num ponto com contraste. |
| Qualquer outro erro | **Ajustes → Diagnóstico → Copiar log** e envie o log para análise. |

Ao tocar em **Encerrar conexão**, o app avisa a câmera, que sai do modo PC e volta ao normal.
