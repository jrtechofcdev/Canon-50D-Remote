# Melhorias de funcionamento — Canon-50D-Remote
### Comparação módulo a módulo com o "Camera Connect & Control" (referência que funciona na sua 50D)

> Data: 2026-11-20 · Repositório: `jrtechofcdev/Canon-50D-Remote` @ `c3bd775`
> Foco: **o que prejudica o funcionamento** de algum módulo. Não são ferramentas novas.
> Da referência foram observados apenas **comandos PTP, parâmetros, ordem e escolhas de comportamento**. Nenhum código foi copiado. Os trechos Kotlin são propostas novas para o seu projeto.
> Nenhum arquivo do repositório foi alterado. O detalhamento do disparo está em `CORRECAO_DISPARO_50D.md`.

---

## 0. Visão geral

| # | Módulo | Problema no seu app | O que a referência faz | Impacto | Prioridade |
|---|---|---|---|---|---|
| M1 | Disparo | Usa `0x910F` e repete o comando até a câmera aceitar | `0x9128/0x9129` em etapas | Fotos perdidas, "Focando… 10 s" | **Crítica** |
| M2 | Download | `GetObject` do arquivo inteiro segura a USB | `GetPartialObject` em blocos | Live View congela e disparo espera durante o download do RAW | **Alta** |
| M3 | Download | Falha no meio → recomeça do zero | Lê por posição (offset) | Retransmissões longas com cabo instável | Média |
| M4 | Sessão | Supõe que a câmera manda todos os valores sozinha | Pede valores com `0x9127 RequestDevicePropValue` | Campos vazios (ex.: Drive Mode) e controles que ficam desatualizados | Média |
| M5 | Live View / controles | Descobre o que não é suportado errando (ex.: DOF) | Checa a lista de operações antes de cada recurso | Erros e mensagens desnecessárias, ida e volta perdida na USB | Média |
| M6 | Sessão | `SetRemoteMode(1)` | `SetRemoteMode(5)` | Possível diferença de comportamento remoto | Teste |
| M7 | Sessão | Grava `CaptureDestination` ao conectar | Não altera; usa o padrão da câmera | Risco baixo, mas desnecessário | Baixa |
| M8 | Live View | Iniciar/parar o LV | Mesmo protocolo (`0xD1B0` = 2 / 0) | ✅ já igual | — |
| M9 | Transporte USB | Mutex, transactionId, reset | Mesmo princípio | ✅ já bom | — |

**Ordem recomendada:** M1 → M2 → M3 → M4 → M5 → testes M6/M7.

---

## M1 — Disparo (crítico)

Resumo do `CORRECAO_DISPARO_50D.md`:

- **Seu app:** `CameraManager.kt:459` escolhe `0x910F` sempre que a câmera o anuncia. O laço em `ShutterRelease.kt:70-110` repete o comando a cada 300 ms, e cada repetição refaz o AF.
- **Referência:** se a câmera anuncia `0x9128`, usa On(1,1) → On(2,1) → Off(2) → Off(1). O `0x910F` só entra se `0x9128` não existir. Ao segurar o botão, o Drive Mode (`0xD106`) decide se sai rajada.
- **Correção:** inverter a prioridade, soltar o botão sempre em `finally`, confirmar a foto pelo evento `ObjectAddedEx` e trocar o botão `clickable` por pressionar/segurar/soltar.

---

## M2 — Download em blocos (alta)

### Problema

`CameraManager.kt:390-393`:

```kotlin
suspend fun download(handle: Int, sink: PtpDataSink) {
    ptp.transact(PtpOp.GET_OBJECT, handle, dataIn = sink, timeoutMs = DOWNLOAD_TIMEOUT_MS)
}
```

O comentário diz: *"Runs inside the session lock, so polling pauses meanwhile."* O problema é que **tudo** pausa junto com o polling:

- Um CR2 da 50D tem cerca de 15–25 MB. Durante esse tempo, o `Mutex` do `PtpSession` fica preso numa única transação.
- O Live View (`runLiveView`, `CameraManager.kt:614-662`) não consegue pedir quadros: **a imagem congela depois de cada foto**.
- O disparo seguinte (`capture` → `shotLock` → `transact`) espera o download inteiro terminar: **o botão "não responde"** quando você faz fotos em sequência.
- O `TetherController` (`TetherController.kt:94-118` e `:222`) dispara o download assim que chega o `ObjectAddedEx`, ou seja, exatamente quando o fotógrafo quer voltar a ver o Live View.

### Na referência

O download usa **`0x101B GetPartialObject(handle, offset, tamanho)`** em blocos de **no mínimo 512 KB** (`0x80000`). O `0x1009 GetObject` só aparece como alternativa. Entre um bloco e outro, a USB fica livre para o Live View e para o disparo.

### Correção proposta

```kotlin
// PtpConstants.kt
const val GET_PARTIAL_OBJECT = 0x101B

// CameraManager.kt — substitui download()
suspend fun download(handle: Int, size: Long, sink: PtpDataSink, startAt: Long = 0) {
    val ptp = session ?: throw PtpException("Câmera desconectada")
    val info = deviceInfo ?: throw PtpException("Câmera desconectada")
    if (!info.supports(PtpOp.GET_PARTIAL_OBJECT) || size <= 0) {
        ptp.transact(PtpOp.GET_OBJECT, handle, dataIn = sink, timeoutMs = DOWNLOAD_TIMEOUT_MS)
        return
    }
    sink.begin(size)
    var offset = startAt
    while (offset < size) {
        val len = minOf(CHUNK, size - offset).toInt()
        // Cada bloco é uma transação: entre dois blocos o Mutex é liberado
        // e o Live View / disparo / GetEvent entram na fila.
        ptp.transact(PtpOp.GET_PARTIAL_OBJECT, handle, offset.toInt(), len,
                     dataIn = ChunkSink(sink), timeoutMs = 5_000)
        offset += len
    }
}
private const val CHUNK = 1L shl 20   // 1 MB (a referência usa no mínimo 512 KB)
```

- `ChunkSink` repassa o `write()` ao sink final **sem** chamar `begin()` de novo, para o progresso continuar somando.
- O tamanho vem de `EosEventRecord.ObjectAdded.size` (já existe em `EosEventParser.kt:94`).
- **Prioridade para o disparo:** o `Mutex` do Kotlin atende na ordem de chegada. Para o disparo não esperar nem um bloco, pause o download enquanto `liveHold` for `true` (cheque a flag antes de cada bloco).
- Tamanho de bloco: com 512 KB a 1 MB, um bloco leva perto de 50–150 ms no USB 2.0. O LV fica no máximo esse tempo sem quadro, em vez de vários segundos.

### Teste
1. Live View aberto, RAW+JPG, disparar 5 fotos seguidas: o LV não pode ficar parado mais que ~0,3 s.
2. Medir o tempo total de download de um CR2 antes e depois: a diferença deve ser pequena (< 15 %).

---

## M3 — Retomar download do ponto em que parou (média)

### Problema
Em `TetherController.kt:118`, a nova tentativa (`delay(1_000L * (job.attempt + 1))`) chama `download` de novo e **baixa o arquivo inteiro outra vez**. Com cabo ou OTG instável, um RAW pode ser retransmitido várias vezes.

### Correção
Com M2 pronto, guarde no job o número de bytes já gravados (o `StreamSink` já sabe: `received`). Na nova tentativa:
- abra o arquivo em modo *append*;
- chame `download(handle, size, sink, startAt = bytesGravados)`.

Se o arquivo temporário não existir ou tiver tamanho maior que o esperado, recomece do zero.

---

## M4 — Pedir os valores à câmera (`0x9127`) (média)

### Problema
O `CameraStatus` só se preenche com o que chega por `GetEvent` (`CameraManager.kt:316-320` e `:375-387`). Se a 50D não reportar alguma propriedade na carga inicial, o valor fica `null` até alguém girar o dial. Isso já afeta:
- **Drive Mode (`0xD106`)**, necessário para o "segurar = rajada" de M1;
- `0xD1B0` (saída do LV), que o `evfNote()` (`CameraManager.kt:475`) usa no log;
- qualquer controle da tela que dependa de `status.value(...)`.

### Na referência
A camada PTP tem uma chamada dedicada a **`0x9127 RequestDevicePropValue(prop)`**: a câmera responde mandando o valor atual daquela propriedade no próximo `GetEvent`. A referência a usa quando precisa de um valor específico.

### Correção
```kotlin
// PtpConstants.kt
const val EOS_REQUEST_DEVICE_PROP_VALUE = 0x9127

// runSession(), depois da carga inicial de eventos (CameraManager.kt:320)
if (info.supports(PtpOp.EOS_REQUEST_DEVICE_PROP_VALUE)) {
    for (prop in listOf(EosProp.DRIVE_MODE, EosProp.SHOOTING_MODE, EosProp.ISO,
                        EosProp.SHUTTER_SPEED, EosProp.APERTURE, EosProp.EXPOSURE_COMPENSATION,
                        EosProp.WHITE_BALANCE, EosProp.AVAILABLE_SHOTS)) {
        if (_status.value.value(prop) == null)
            runCatching { ptp.transact(PtpOp.EOS_REQUEST_DEVICE_PROP_VALUE, prop) }
    }
    // O eventLoop recebe os valores como PropValueChanged normalmente.
}
```

Use o mesmo pedido ao **abrir o Live View** e ao **voltar de uma reconexão**.

---

## M5 — Checar a capacidade antes de usar (média)

### Problema
Vários comandos de Live View são enviados sem consultar o `DeviceInfo`:
`setLiveZoom` (`:666`), `setLiveFocusPoint` (`:672`), `liveAutofocus`/`runAf` (`:478`), `driveLens` (`:691`), `cancelAf` (`:492`) e o próprio `GetViewFinderData` (`:623`).
A prévia de profundidade (`:700-721`) só descobre que não é suportada **depois** de errar (`OPERATION_NOT_SUPPORTED`).

O resultado são idas e voltas inúteis na USB (cada uma com o Mutex preso) e mensagens de erro na tela.

### Na referência
Antes de cada recurso, a referência pergunta "a câmera tem a operação X?". Antes de mostrar o Live View, ela confere `0x9153`, `0x9154`, `0x9155`, `0x9160`, `0x9110` e `0x9128`, entre outras. O que não existe nem aparece na interface.

### Correção
Monte um objeto de capacidades uma vez, em `runSession` (`CameraManager.kt:301-305`), e exponha como `StateFlow`:

```kotlin
data class LiveCapabilities(
    val liveView: Boolean,     // 0x9153
    val af: Boolean,           // 0x9154 + 0x9160
    val driveLens: Boolean,    // 0x9155
    val zoom: Boolean,         // 0x9158
    val zoomPosition: Boolean, // 0x9159
    val dof: Boolean,          // 0x9156
    val pressRelease: Boolean, // 0x9128 + 0x9129
    val requestProp: Boolean,  // 0x9127
)
```

- `liveCommand` retorna sem enviar nada se a capacidade for falsa.
- `LiveViewScreen` e `LiveOptionsSheet` escondem os botões sem suporte.
- O `_dofSupported` passa a vir daqui. Mantenha o fallback atual (desligar ao receber `0x2005`), porque algumas câmeras anunciam operações que recusam.
- Registre as capacidades no log ao conectar. Isso também resolve o teste T1 da correção do disparo.

---

## M6 — Valor do `SetRemoteMode` (teste)

- **Seu app** (`CameraManager.kt:309`): `SetRemoteMode(1)`.
- **Referência:** `SetRemoteMode(5)`, seguido de `SetEventMode(1)`. Nas câmeras que anunciam `0x913D`, ela também envia `SetRequestOLCInfoGroup(0x4E)`. A 50D provavelmente não anuncia `0x913D`; use só se o `DeviceInfo` listar.

O significado exato dos bits do modo remoto não é documentado publicamente. **Não troque às cegas:**
1. Deixe o valor configurável no Modo desenvolvedor (1 ou 5).
2. Com cada valor, rode o T2/T4 da correção do disparo e 10 minutos de Live View.
3. Fique com o valor que der menos "busy" (`0x2019`) no log.

---

## M7 — `CaptureDestination` (baixa)

- **Seu app** (`CameraManager.kt:311`): grava `0xD11C = 2` em toda conexão.
- **Referência:** não grava essa propriedade e deixa a câmera no padrão (cartão).

Gravar a propriedade não é necessariamente errado. Mas, se o valor 2 não significar "cartão" na 50D, as fotos podem ir para a RAM da câmera e travar o disparo quando a memória enche (sintoma: dispara 1–3 vezes e para). Verificação simples: depois de conectar, confira no log o `PropValueChanged` de `0xD11C` e se as fotos aparecem no cartão. Se tiver dúvida, faça como a referência e não grave nada.

---

## M8/M9 — O que já está igual ou melhor (manter)

| Ponto | Seu app | Referência |
|---|---|---|
| Ligar/desligar LV | `0xD1B0` = 2 / 0, com `EVF_CAMERA` opcional (`:601`) | Mesmo, no perfil EOS genérico (onde a 50D se encaixa) |
| `GetViewFinderData` | `0x9153(0x00100000)` e `0xA102` tratado como "ainda não" | Mesmo opcode |
| Serialização PTP | `Mutex` + transactionId + descarte de resposta antiga | Fila única |
| Reset da USB | `controlTransfer(0x21, 0x66)` + clear halt + drenagem | Usa `controlTransfer` |
| Pausa do LV no disparo | `liveHold` | Mesmo princípio |
| Decodificação do LV | Pool de 3 bitmaps com `inBitmap`, `conflate()` na tela | `ImageDecoder` mutável. Os dois são adequados |
| Reconexão | Backoff 1→30 s, 10 tentativas | — |

Não há motivo para trocar nenhum desses pontos.

---

## Plano de execução

| Etapa | Arquivos | Concluído quando |
|---|---|---|
| 1. Capacidades (M5) + log | `CameraManager.kt`, `PtpConstants.kt` | Log mostra a lista da 50D |
| 2. Disparo (M1) | `ShutterRelease.kt`, `CameraManager.kt`, `LiveViewScreen.kt` | T2–T10 do documento de disparo |
| 3. Download em blocos (M2) | `CameraManager.kt`, `PtpSession.kt` (sink), `TetherController.kt` | LV parado < 0,3 s durante o download |
| 4. Retomar download (M3) | `TetherController.kt` | Cabo puxado no meio → retoma do offset |
| 5. RequestDevicePropValue (M4) | `CameraManager.kt` | Drive Mode aparece logo após conectar |
| 6. Testes M6/M7 | Modo desenvolvedor | Valor escolhido registrado |

## Limitações

- Não houve teste na sua 50D. Os valores de M6 e M7 precisam de confirmação na câmera.
- O tamanho de bloco ideal (512 KB a 1 MB) deve ser medido no seu celular.
- O intervalo de polling de eventos e o keep-alive da referência não foram comparados em detalhe. Os seus (`POLL_IDLE_MS = 200`, `POLL_BUSY_MS = 40`, `KEEP_ALIVE_MS = 60 s`) são razoáveis.
