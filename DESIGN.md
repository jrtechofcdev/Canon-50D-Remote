# DESIGN.md: JR C50D Sync

App de captura vinculada da **Jr_Tech_OFC** ("Nossa Arte, Seu Sucesso").
Direção tirada do kit da marca enviado pelo José Renato (`branding/`).

## Nome
**JR C50D Sync** (até a 2.7, Jr_Link). JR é a marca, C50D a Canon 50D e Sync a captura vinculada.
Ícone: uma câmera no degradê menta → ciano, com o visor em bloco inclinado como os do símbolo e a lente formada por duas setas de sincronia (`branding/icon.py`).
Subtítulo: "Captura vinculada · Canon 50D".

## Origem
A entrega na hora vem da **Fotografia Express** (fotografar e entregar no evento, sem internet), mas o produto é o JR C50D Sync, para qualquer trabalho.
Tudo segue a cadeia **Capturar → Controlar → Melhorar → Entregar**, que também organiza Ajustes (Câmera, Captura e salvamento, Fotografia, Edição, Entrega, Sistema).

## Identidade
- Ferramenta de trabalho do fotógrafo: a foto é a protagonista, e a marca aparece com peso nos pontos de identidade (ícone, cabeçalho, disparador, marca d'água).
- O motivo visual é o **bloco inclinado** do símbolo, um paralelogramo a ~16° com cantos suaves. Ele aparece como indicador de estado, etiquetas de formato e ilustração de tela vazia. Nunca vira padrão de fundo repetido (a grade do kit é para peças impressas, não para uma tela de trabalho).

## Tema
- Escuro por decisão: igrejas e eventos com pouca luz, e cinza neutro para julgar cor e exposição. O grafite da marca (`#292929`) é neutro, então serve de base sem tingir a foto.

## Paleta
| Papel | Cor | Uso |
|---|---|---|
| Fundo | `#1F1F1F` | Telas |
| Superfície | `#292929` (grafite da marca) | Cartões, barras |
| Superfície alta | `#333333` | Chips, campos |
| Linha | `#3D3D3D` | Divisórias |
| Texto | `#F2F2F2` / `#A6A6A6` | Principal / secundário |
| **Menta** `#00FCA8` | acento principal | Ação principal da tela, estado "conectado", seleção |
| **Ciano** `#1CDAFF` | acento de informação | Progresso de download, histograma de luminância, links |
| Degradê menta → ciano | só identidade | Símbolo, disparador |
| Erro | `#FF6B6B` | Falhas reais |

Regra: no máximo **um** elemento menta por área da tela. O degradê só aparece no símbolo e no disparador.

## Tipografia
- **Exo 2**, a fonte do logo, em toda a interface: 600 em títulos, 500 em rótulos e botões, 400 no texto.
- Números de exposição com algarismos tabulares, como no LCD da câmera.
- Letras espaçadas só no nome "JR C50D Sync", do jeito do logo. Rótulos de seção ficam em caixa normal.

## Formas e movimento
- Raio 8dp em cartões e campos, 4dp nas miniaturas, círculo só no disparador.
- Sem sombras decorativas e sem brilho.

## Movimento
Cada animação responde a algo que aconteceu, e nada fica em loop. As curvas e durações ficam em `ui/components/Motion.kt` (160, 280 e 420 ms, curva "emphasized").

| Momento | Movimento |
|---|---|
| Abrir tela | Entra pelo lado. Abas trocam com fade, e a foto cresce a partir da grade |
| Tocar | Botões e blocos afundam um pouco e voltam com mola |
| Disparar | O disco fecha como a cortina e um anel se abre |
| Foto nova | Flash de obturador curto na última foto |
| Número mudou | O número rola para cima quando cresce e para baixo quando diminui |
| Escolher | A foto afunda na moldura, o coração pulsa e o ícone da aba pula |
| Girar a tela | Dial redondo: o celular desenhado gira com mola e encaixa a cada 90° |

A abertura (`ui/intro/BrandIntro.kt`) é a única peça longa:
- os blocos do símbolo entram pela própria inclinação;
- o quadro de foco trava em menta;
- o diafragma de 6 lâminas dispara;
- o nome entra com a leitura de exposição.

Ela dura menos de 3 s, pula com um toque e respeita a opção de animações do Android.

## Modo escuro total
- Fundo preto, menta escurecida `#00A870`, ciano escurecido `#1290AA`, texto `#B5B5B5`.

## Dials
- ENERGY 2 / RHYTHM 2 / MOTION 3

## Portal da entrega local
- É a página que o cliente abre no celular dele (`assets/portal`), com a mesma identidade: grafite `#1F1F1F`, menta `#00FCA8` só na ação principal (**Salvar foto**) e Exo 2 servida pelo próprio app.
- A foto vem primeiro: a mais recente em destaque e a grade das outras logo abaixo. Botões com 48 px de altura, sem bibliotecas externas e sem CDN.
- A placa impressa é clara (papel branco, pouca tinta), com a faixa e os números de passo em menta e os QR em preto sobre branco.
