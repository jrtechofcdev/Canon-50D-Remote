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
- O motivo visual é o **bloco inclinado** do símbolo, um paralelogramo a ~16° com cantos suaves. É a assinatura do app:
  - **escolhido:** o único jeito de marcar seleção (chips, presets, opções, aba aberta) é o fundo um tom acima com o bloco menta à esquerda (ou sobre o ícone, nas abas);
  - **estado:** a câmera, a Entrega e os avisos marcam o estado com o bloco na cor do estado (`StatusMark`);
  - **etiquetas** de formato (RAW, JPG) e a **arte das telas vazias** (o símbolo em linhas);
  - **carregador:** o símbolo em linhas com um bloco aceso girando no sentido horário (`BrandLoader`, ideia do José).
- Nunca vira padrão de fundo repetido (a grade do kit é para peças impressas, não para uma tela de trabalho).

## Tema
- Escuro por decisão: igrejas e eventos com pouca luz, e cinza neutro para julgar cor e exposição. O grafite da marca (`#292929`) é neutro, então serve de base sem tingir a foto.

## Paleta
| Papel | Cor | Uso |
|---|---|---|
| Fundo | `#1F1F1F` | Telas |
| Superfície | `#292929` (grafite da marca) | Cartões, barras |
| Superfície alta | `#333333` | Controles dentro do cartão, item escolhido |
| Linha | `#3D3D3D` | Divisórias, contorno de chip |
| Texto | `#F2F2F2` / `#A6A6A6` | Principal / secundário |
| **Menta** `#00FCA8` | acento principal | Ação principal da tela, estado "conectado", seleção |
| **Ciano** `#1CDAFF` | acento de informação | Progresso de download, histograma de luminância, links |
| Degradê menta → ciano | só identidade | Símbolo, disparador |
| Âmbar | `#FFB547` | Algo errado que ainda funciona: reconectando, pouco espaço |
| Erro | `#FF6B6B` | Falhas reais, apagar, parar |
| Véu | preto 55% | Legendas e botões redondos sobre a foto |
| Sobre a foto | `#FFFFFF` | Ícones e texto em cima da foto (`Tone.OnPhoto`) |

Regras:
- No máximo **um** elemento menta por área: a ação a fazer ali, o estado "conectado" ou o que está escolhido. Ícones de grupo, títulos e valores ficam neutros.
- O degradê aparece em dois lugares: o disparador e a linha fina embaixo de "Conectada" na Sessão (além do símbolo).
- Três níveis de superfície: fundo é a tela, superfície é o cartão ou a folha, superfície alta é o controle dentro dele.
- Nenhuma cor escrita à mão nas telas: tudo sai de `Tone` (`ui/theme/Theme.kt`). O `DesignTokensTest` cobra isso.

## Tipografia
- **Exo 2**, a fonte do logo, em toda a interface: 600 em títulos, 500 em rótulos e botões, 400 no texto.
- Números de exposição com algarismos tabulares, como no LCD da câmera.
- Letras espaçadas só no nome "JR C50D Sync", do jeito do logo. Rótulos de seção ficam em caixa normal.

## Formas e espaço
- Raio 8 dp em cartões, campos, chips e botões; 4 dp em miniaturas e etiquetas; 16 dp no topo das folhas.
- Círculo só no disparador e nos botões redondos sobre a foto (um círculo some menos na imagem que um quadrado).
- Espaços só da escala `Space`: 4, 8, 12, 16, 24 e 32 dp.
- Sem sombras decorativas e sem brilho.

## Componentes (`ui/components/Kit.kt`)
| Componente | Para quê |
|---|---|
| `JrButton` | Primário (menta, um por área), Secundário (tonal), Fantasma (só texto), Perigo (texto vermelho). 48 dp |
| `JrChip` / `ChipRow` | Filtro ou opção com a seleção única; a fileira rola com a borda esmaecida, sem cortar palavra |
| `PhotoChip` | O mesmo chip sobre a foto ou o live view, no véu |
| `JrCard` | Um cartão: superfície, 8 dp, sem borda e sem sombra |
| `JrBanner` | Aviso que fica na tela: informação (ciano), atenção (âmbar) ou erro (vermelho), com ações |
| `ScreenHeader` / `SheetHeader` | Título da tela (nunca cortado pelos botões, no máximo 2 ações) e da folha |
| `JrTile` | Ferramenta: ícone de 24 dp num quadrado de 52 dp, nome embaixo; menta só enquanto ligada |
| `PhotoButton` | Botão redondo sobre a foto, véu e ícone branco |
| `EmptyState` | Tela vazia: o símbolo em linhas, o que falta, por quê e o que fazer |
| `StatusMark` | O bloco inclinado na cor do estado |
| `BrandLoader` | Carregador da marca, com versão de progresso (cada quarto concluído fica aceso) |

## Movimento
Cada animação responde a algo que aconteceu. As curvas e durações ficam em `ui/components/Motion.kt` (160, 280 e 420 ms, curva "emphasized").

Só duas coisas repetem, e só enquanto o motivo existe: o carregador da marca (uma volta a cada 0,6 s enquanto a espera dura) e o pisca do estouro no visualizador (só com o alerta ligado e áreas estouradas). Com as animações do Android desligadas, o carregador fica parado com um bloco aceso.

| Momento | Movimento |
|---|---|
| Abrir tela | Entra pelo lado. Abas trocam com fade, e a foto cresce a partir da grade |
| Tocar | Botões e blocos afundam um pouco e voltam com mola |
| Disparar | O disco fecha como a cortina e um anel se abre |
| Foto nova | Flash de obturador curto na última foto |
| Número mudou | O número rola para cima quando cresce e para baixo quando diminui |
| Escolher | A foto afunda na moldura, o coração pulsa e o ícone da aba pula |
| Girar a tela | Dial redondo: o celular desenhado gira com mola e encaixa a cada 90° |
| Esperar | O bloco aceso gira no símbolo; com progresso, os blocos acendem e ficam |
| Abrir o app (Android 12+) | Os blocos do símbolo acendem um a um, no sentido horário, até o logo completo |

A abertura (`ui/intro/BrandIntro.kt`) é a única peça longa:
- os blocos do símbolo entram pela própria inclinação;
- o quadro de foco trava em menta;
- o diafragma de 6 lâminas dispara;
- o nome entra com a leitura de exposição.

Ela dura menos de 3 s, pula com um toque e respeita a opção de animações do Android.

## Modo escuro total
- Fundo preto, menta escurecida `#00A870`, ciano escurecido `#1290AA`, âmbar `#B98235`, texto `#B5B5B5`, branco sobre a foto `#B5B5B5`.
- É o único lugar com preto puro, de propósito: igreja e evento no escuro, onde qualquer cinza aceso incomoda.

## Dials
- ENERGY 2 / RHYTHM 2 / MOTION 3

## Páginas do cliente (Entrega local e Fotografia Express)
São o primeiro contato do cliente com a marca, no celular dele. Ficam em `assets/portal` e `assets/express`, com a base comum em `assets/shared` (`brand.css` e `brand.js`).

- **Leitura:** página de fotos para o cliente do evento, na identidade Jr_Tech_OFC (grafite, menta, bloco inclinado), dial ENERGY 3 na abertura e 2 no resto / RHYTHM 2 / MOTION 3.
- **Mesma identidade do app:**
  - grafite `#1F1F1F`;
  - menta `#00FCA8` só na ação da área (**Ver minhas fotos**, **Salvar foto**, **Pagar**);
  - Exo 2 em 400, 500 e 600, servida pelo próprio app;
  - sem bibliotecas externas e sem CDN.
- **Abertura** (uma vez por visita, menos de 2,5 s, pula com um toque, some com "reduzir movimento"):
  - os 4 blocos entram pela própria inclinação;
  - acendem no sentido horário;
  - o nome Jr_Tech_OFC fecha o espaçamento das letras, com o lema embaixo.
- **Código:** 4 números, 4 blocos. Cada número digitado acende um bloco do símbolo, e as casas do código têm a mesma inclinação de 16°. Durante a conferência, o símbolo vira o carregador. Com código errado, as casas tremem em vermelho.
- **A foto vem primeiro:**
  - a mais recente ocupa a largura do celular;
  - a grade guarda o formato de cada foto, em pé ou deitada, em linhas que fecham a largura;
  - no visualizador, o fundo é preto, dá para arrastar com o dedo e o botão Voltar do celular fecha.
- **Estados com o bloco:**
  - "Ao vivo" (menta), "Reconectando" (âmbar) e "Encerrado" (cinza) no Express;
  - "Prévia" (cinza) e "Liberada" (menta) em cada foto.
- **Movimento:**
  - foto nova chega com o flash de obturador;
  - foto liberada acende a etiqueta;
  - o coração pulsa ao escolher.
- **Placa impressa:** é clara (papel branco, pouca tinta), com a faixa e os números de passo em menta e os QR em preto sobre branco.
