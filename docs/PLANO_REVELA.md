# Plano: Revela

**Revela** é um app irmão do JR C50D Sync, para **outros fotógrafos**. As ferramentas são as mesmas: captura vinculada, auto-save, live view, culling, presets, entrega local, Fotografia Express e Pix. O que muda:

- nome, logo, paleta, tipografia e posição de cada tela;
- funciona com **qualquer câmera PTP**, não só a Canon 50D;
- cada fotógrafo coloca a **marca dele** (nome, logo e cor), e é essa marca que o cliente vê;
- português e inglês.

O JR C50D Sync continua igual: mesmo nome, mesma chave e mesmo ID de instalação. Os dois podem ficar instalados no mesmo celular.

## Decisões do José
| Tema | Escolha |
|---|---|
| Nome | **Revela** ("revelar" a foto: captura e entrega) |
| Visual | **Câmara escura**: só escuro, preto quente como um laboratório de revelação |
| Onde | Mesmo repositório, como segundo app (variante `revela` do Gradle) |
| Idiomas | Português e inglês |

## Arquitetura
- **Duas variantes no Gradle** (`productFlavors`):
  - `jr`: `com.jrtechofc.tether50d`, igual a hoje;
  - `revela`: `com.jrtechofc.revela`, versão própria, começando em 1.0.
- **`src/main`:** tudo o que é compartilhado (`core/`: câmera, PTP, entrega, presets, render, banco) e as telas que o Revela ainda usa enquanto não ganham a versão própria.
- **`src/jr` e `src/revela`:** cada um com a sua `MainActivity`, o seu `AppBrand` (nome, pastas, textos da marca) e os seus recursos (ícone, nome, abertura).
- **Tema por marca:** o `Tone` (paleta), a tipografia e as formas vêm do app que está rodando. Uma tela compartilhada aparece com a cara do Revela no Revela e com a cara do JR no JR. Assim o Revela funciona completo desde a primeira versão, e cada tela é redesenhada no seu tempo.

## Identidade do Revela ("Câmara escura")
- **Conceito:** o laboratório de revelação. Preto quente, papel fotográfico e a luz de segurança.
- **Paleta:**

  | Papel | Cor |
  |---|---|
  | Fundo | `#110F0D` |
  | Superfície | `#1B1815` |
  | Superfície alta | `#26221E` |
  | Linha | `#36302A` |
  | Texto (papel) | `#F2EBE0` |
  | Texto secundário | `#A89E91` |
  | Destaque padrão (luz de segurança) | `#FF7A3D` |
  | Informação (fixador) | `#8FB7C9` |
  | Atenção | `#E8C26A` |
  | Erro | `#FF4D5E` |

  - O destaque é a **cor da marca do fotógrafo**: ele escolhe, e o texto em cima dela fica preto ou branco conforme o contraste.
- **Modo luz vermelha:** no lugar do "modo escuro total", a tela inteira fica vermelha e escura, como a luz da câmara escura. Serve para igreja e evento à noite, sem ofuscar os olhos.
- **Tipografia:**
  - Instrument Serif nos títulos grandes e nas saudações (o tom editorial de uma cópia em papel);
  - Instrument Sans em toda a interface, com algarismos tabulares nas leituras de exposição.
  - As duas são de licença livre (OFL) e vão dentro do app.
- **Logo:** uma moldura de cópia fotográfica com um semicírculo de luz nascendo da borda de baixo, a imagem "surgindo" no papel. Em papel e luz de segurança sobre preto quente.
- **Forma:** cantos de 14 dp nos cartões e cantos retos nas fotos (como uma cópia). Divisórias finas em vez de caixas.

## Fases

### Fase R1: fundação (esta etapa)
1. Variantes `jr` e `revela` no Gradle; o CI e o workflow de release passam a gerar os dois APKs.
2. `AppBrand` por variante, tirando "Jr_Tech_OFC" e "JR C50D Sync" de dentro do `core/`.
3. Tema por marca: paleta "Câmara escura", modo luz vermelha, Instrument Serif e Sans, formas.
4. Logo, ícone do app e abertura do Revela.
5. **Navegação nova do Revela**, reposicionada:
   - a **Captura** é a tela inicial, com a última foto ocupando a tela e as leituras da câmera no topo, como num visor;
   - uma faixa de filme embaixo com as últimas fotos;
   - o disparador fica no polegar;
   - o resto (Galeria, Entrega, Ajustes) fica numa barra lateral de "gavetas", não em abas embaixo.
6. **"Sua marca":** primeira abertura com nome do estúdio, logo (da galeria), cor da marca e idioma.
7. Textos do Revela em `strings.xml` (pt-BR) e `values-en` (inglês).

### Fase R2: telas redesenhadas
Galeria, visualizador, live view, culling, editor e presets, Entrega e Express, Ajustes e modo cliente. Cada uma ganha a versão própria do Revela, sai do compartilhado, ganha captura de tela em pé e deitado e texto em pt/en.

### Fase R3: qualquer câmera PTP
- `CameraDriver`, com três implementações:
  - **Canon EOS:** o código atual;
  - **PTP genérico:** disparo `InitiateCapture`, fotos novas pelo evento `ObjectAdded` ou pela lista do cartão, e exposição pelas propriedades padrão (`ExposureTime`, `FNumber`, `ExposureIndex`);
  - **Nikon:** disparo e eventos próprios, live view nas câmeras que suportam.
- O filtro USB passa a aceitar qualquer câmera de imagem (classe 6), não só Canon.
- **Limite honesto:** sem as câmeras na mão, Nikon, Sony e outras precisam de teste por fotógrafos de verdade. O log do app já está pronto para isso.

### Fase R4: marca do fotógrafo em tudo
- Páginas do cliente (Entrega e Express) com logo, nome e cor do fotógrafo.
- Marca d'água, placa impressa, mensagens de WhatsApp e assinatura com a marca dele.
- Nenhum "Jr_Tech_OFC" aparece para o cliente do Revela.

### Fase R5: publicação
- APK release com R8 e chave própria do Revela;
- README e página do Revela;
- textos de loja em pt e en.

## Verificação em cada fase
- Testes das duas variantes e lint sem erros.
- Capturas das telas do Revela em pé e deitado, com texto grande e em inglês.
- O APK do JR continua instalando por cima da versão anterior.
