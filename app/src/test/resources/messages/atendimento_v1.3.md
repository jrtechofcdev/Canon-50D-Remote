## 5. Biblioteca de mensagens rápidas

Cada item deve ser cadastrado com **nome**, **atalho** e **texto integral**. Preservar acentos, pontuação, emojis, quebras de linha e asteriscos usados na formatação do WhatsApp.

### 5.1. Mensagens existentes na interface — manter nome e atalho

As 14 entradas abaixo já aparecem na interface mostrada pelo usuário. Seus nomes e atalhos são identificadores existentes e não devem ser renomeados.

#### 1. Envio das fotos
**Atalho:** `fotos`  
**Etapa:** informar que a galeria com as fotografias previstas para seleção/conferência está disponível. Não usar como confirmação de entrega final editada.

```text
Olá, {cliente}! Tudo bem? 📸

Sua galeria está disponível!

Os registros do dia {data} já podem ser conferidos. Ao todo, são *{quantidade} fotos* para você visualizar e, conforme o combinado para o seu serviço, escolher suas favoritas.

Agradecemos pela confiança em nosso trabalho!

Caso tenha alguma dúvida ou precise de suporte, permanecemos à disposição.

Atenciosamente,
Jr_Tech_OFC Fotografia
```

#### 2. Prévia pronta
**Atalho:** `previa`  
**Etapa:** avisar que uma pequena amostra/prévia está disponível antes da liberação da galeria completa ou da entrega final.  
**Nota de origem:** a captura de tela mostrou o nome e o atalho, mas não exibiu o texto salvo. O texto abaixo é uma proposta de conteúdo para essa entrada, e não uma transcrição do conteúdo anterior.

```text
Olá, {cliente}! Tudo bem? ✨

Sua prévia fotográfica está disponível!

Liberamos uma prévia dos registros do nosso ensaio para que você possa conhecer o resultado do trabalho. 📸

A prévia é apenas uma amostra; assim que a galeria completa estiver disponível, enviaremos as orientações para a próxima etapa, conforme o serviço contratado.

Se precisar de ajuda durante o acesso, é só nos chamar.

Jr_Tech_OFC Fotografia
```

#### 3. Fotos entregues
**Atalho:** `entrega`  
**Etapa:** comunicar a entrega final das fotografias editadas.

```text
Olá, {cliente}! Tudo bem? ✨

Suas *{quantidade} fotografias editadas* estão prontas e foram entregues!

Foi um prazer registrar esses momentos especiais. Agradecemos pela confiança e pela oportunidade de fazer parte dessas lembranças.

Se compartilhar alguma das fotografias nas redes sociais, ficaremos felizes em receber sua marcação: *@jr_tech_ofc* 📸

Será um prazer acompanhar sua publicação!

Atenciosamente,
Jr_Tech_OFC Fotografia
```

#### 4. Lembrete de pagamento
**Atalho:** `pag`  
**Etapa:** lembrar o saldo restante, quando essa condição tiver sido combinada para a entrega.

```text
Olá, {cliente}! Tudo bem?

*Lembrete de pagamento* 📸

Conforme combinado, o pagamento restante referente ao ensaio realizado em {data} deverá ser efetuado no momento da entrega das fotografias.

Caso tenha alguma dúvida, estou à disposição para ajudar.

Agradeço pela confiança e preferência!

Jr_Tech_OFC Fotografia
```

#### 5. Seleção recebida
**Atalho:** `sel`  
**Etapa:** confirmar o recebimento da lista de fotografias escolhidas.

```text
Olá, {cliente}! Tudo bem? 💚

Sua seleção foi recebida com sucesso! 📸

Confirmamos o recebimento de *{quantidade} fotografias* selecionadas:

{lista}

Agora iniciaremos o processo de edição e tratamento das imagens, sempre buscando entregar o melhor resultado em cada detalhe.

Assim que suas fotografias estiverem finalizadas, entraremos em contato para informar sobre a entrega.

Agradecemos pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia
```

#### 6. Confirmação do ensaio
**Atalho:** `agenda`  
**Etapa:** confirmar um agendamento já validado.

```text
Olá, {cliente}! Tudo bem? 📸

Gostaríamos de confirmar nosso ensaio fotográfico agendado para o dia *{data}*.

Pedimos, por gentileza, que chegue com *10 minutos de antecedência*. Assim, poderemos organizar os últimos detalhes e aproveitar ao máximo as condições de iluminação para garantir excelentes registros.

Contamos com sua pontualidade e estamos ansiosos para registrar esse momento especial!

Atenciosamente,
Jr_Tech_OFC Fotografia
```

**Controle de uso:** a orientação de chegada com 10 minutos de antecedência aparece nesta mensagem e também no lembrete. Confirmar que esse é realmente o procedimento adotado antes de manter o texto em produção.

#### 7. Local e horário
**Atalho:** `local`  
**Etapa:** enviar as informações logísticas do ensaio.

```text
Olá, {cliente}! Tudo bem? 📸

Seguem as informações do nosso ensaio fotográfico:

📍 *Local:* {local}
🕒 *Horário:* {horario}

Pedimos que fique atento(a) ao horário combinado. Caso ocorra algum imprevisto ou precise de orientação para chegar ao local, entre em contato por aqui.

Será um prazer registrar esse momento com você!

Jr_Tech_OFC Fotografia
```

#### 8. Reserva da data
**Atalho:** `sinal`  
**Etapa:** informar como solicitar a reserva mediante sinal, conforme as condições comerciais acordadas.

```text
Olá, {cliente}! Tudo bem? ✨

Para solicitar a reserva da data *{data}*, o sinal pode ser realizado via Pix, utilizando a chave abaixo:

💳 *Chave Pix:* {pix}

Após a identificação do pagamento, enviaremos a confirmação do agendamento.

A data será considerada reservada somente após a confirmação do sinal, conforme combinado.

Agradecemos pelo interesse e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia
```

#### 9. Dados do Pix
**Atalho:** `pix`  
**Etapa:** fornecer a chave Pix para um pagamento previamente combinado.

```text
Olá, {cliente}! Tudo bem?

Segue abaixo a chave Pix para realizar o pagamento:

💳 *Chave Pix:* {pix}

Após concluir a transferência, pedimos que envie o comprovante por aqui para facilitar a identificação e a confirmação do recebimento.

Agradecemos pela preferência e confiança em nosso trabalho!

Jr_Tech_OFC Fotografia
```

#### 10. Orçamento
**Atalho:** `orc`  
**Etapa:** apresentar uma proposta comercial.

```text
Olá, {cliente}! Tudo bem? 📸

Agradecemos pelo seu interesse em nosso trabalho!

Conforme solicitado, segue a proposta para o serviço fotográfico:

📷 *Serviço:* {servico}
💰 *Investimento:* {valor}
📦 *Prazo de entrega:* {entrega}

Nosso compromisso é oferecer um trabalho de qualidade, com atenção aos detalhes e cuidado em cada registro.

Caso tenha alguma dúvida ou deseje mais informações sobre a proposta, estamos à disposição para ajudar.

Será um prazer fazer parte desse momento!

Jr_Tech_OFC Fotografia
```

#### 11. Edição em andamento
**Atalho:** `edit`  
**Etapa:** informar que as fotografias estão sendo editadas/tratadas.

```text
Olá, {cliente}! Tudo bem? 🎨📸

Passando para informar que suas fotografias estão em processo de edição e tratamento.

Estamos cuidando de cada detalhe para garantir um resultado de qualidade e valorizar ainda mais os momentos registrados.

Assim que a edição for concluída, enviaremos as fotografias por este canal.

Agradecemos pela paciência e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia
```

#### 12. Impressos prontos
**Atalho:** `ret`  
**Etapa:** avisar que os materiais físicos estão disponíveis para retirada.

```text
Olá, {cliente}! Tudo bem? 😊

Temos uma ótima notícia: seus materiais impressos já estão prontos e disponíveis para retirada! 🖨️✨

🕒 *Horário para retirada:* {horario}

Pedimos, por gentileza, que observe o horário informado para organizar a retirada.

Agradecemos pela preferência e pela confiança em nosso trabalho!

Jr_Tech_OFC
```

**Controle de uso:** o texto não informa endereço de retirada. Se o cliente ainda não souber onde retirar, acrescentar o local confirmado manualmente antes do envio.

#### 13. Fotos no Wi-Fi (entrega local)
**Atalho:** `wifi`  
**Etapa:** orientar o acesso à galeria por meio da rede local.

```text
Olá, {cliente}! Tudo bem? 📸

Suas fotografias já estão disponíveis para visualização e seleção através do nosso sistema de entrega via Wi-Fi. ✨

Para acessar sua galeria, siga as instruções abaixo:

📶 *1. Conecte-se à rede Wi-Fi:*
Rede: *{wifi}*
Senha: *{senha}*

🌐 *2. Acesse o endereço:*
{link}

🔑 *3. Informe seu código de acesso:*
*{codigo}*

⚠️ *Importante:* Caso seu celular informe que a rede Wi-Fi está sem acesso à internet, selecione a opção *"Manter conectado"*. Isso é necessário para continuar o acesso à galeria.

Em caso de dúvidas ou dificuldades durante o acesso, entre em contato. Será um prazer ajudar!

Jr_Tech_OFC Fotografia
```

#### 14. Agradecimento
**Atalho:** `obg`  
**Etapa:** pós-entrega ou encerramento cordial do atendimento.

```text
Olá, {cliente}! Tudo bem? 💚

Agradecemos pela confiança e pela oportunidade de registrar momentos tão especiais. Foi um prazer fazer parte dessa experiência e eternizar essas lembranças através da fotografia.

Esperamos que tenha gostado do resultado! ✨

Se compartilhar suas fotografias nas redes sociais, ficaremos muito felizes se marcar nosso perfil *@jr_tech_ofc*. Sua marcação e seu apoio são muito importantes para o nosso trabalho.

Muito obrigado pela preferência!

Jr_Tech_OFC Fotografia
```

### 5.2. Mensagens novas — complementos do atendimento

As mensagens a seguir são novas sugestões para cobrir situações que não estavam claramente representadas na lista existente. Os nomes e atalhos devem ser adicionados sem remover ou sobrescrever os atuais.

#### 15. Boas-vindas
**Atalho:** `oi`  
**Etapa:** primeiro contato de um novo cliente.

```text
Olá, {cliente}! Seja bem-vindo(a)! 😊

É um prazer receber seu contato e conhecer seu interesse em nosso trabalho.

Será um prazer ajudar você a encontrar a melhor opção para registrar esse momento.

Me conte: qual tipo de ensaio ou serviço fotográfico você está procurando?

Jr_Tech_OFC Fotografia 📸
```

#### 16. Entender o pedido
**Atalho:** `duvida`  
**Etapa:** entender melhor a necessidade antes de oferecer uma solução.

```text
Claro, {cliente}! Será um prazer ajudar. 😊

Para entender melhor o que você procura e apresentar a opção mais adequada, poderia me contar um pouco mais sobre o tipo de fotografia ou serviço que deseja?

Assim consigo orientar você com mais precisão.

Jr_Tech_OFC Fotografia
```

#### 17. Disponibilidade
**Atalho:** `disp`  
**Etapa:** informar que a agenda será consultada. Usar enquanto a disponibilidade ainda estiver sendo verificada.

```text
Olá, {cliente}! 😊

Sobre a data *{data}*, vou verificar a disponibilidade na agenda para confirmar se conseguimos realizar seu ensaio nesse dia.

Assim que tiver a confirmação, retorno com as informações para darmos continuidade ao agendamento.

Jr_Tech_OFC Fotografia 📸
```

#### 17A. Data disponível
**Atalho:** `disponivel`  
**Etapa:** usar somente depois de consultar a agenda e confirmar que a data está livre.

```text
Olá, {cliente}! 😊

Boa notícia: a data *{data}* está disponível para o ensaio!

Se desejar seguir com a reserva, posso explicar as condições e orientar você sobre o próximo passo.

Jr_Tech_OFC Fotografia 📸
```

#### 17B. Data indisponível
**Atalho:** `indisponivel`  
**Etapa:** usar depois de confirmar que a data solicitada não está livre. Se possível, oferecer alternativas reais.

```text
Olá, {cliente}! Tudo bem?

Verifiquei nossa agenda e, infelizmente, a data *{data}* não está disponível para o ensaio.

Se você tiver flexibilidade, podemos consultar outras datas e procurar uma opção que funcione melhor para você.

Jr_Tech_OFC Fotografia
```

#### 18. Pagamento confirmado
**Atalho:** `pago`  
**Etapa:** confirmar o recebimento após conferência efetiva. A mensagem não deve presumir que todo pagamento é um sinal de reserva.

```text
Olá, {cliente}! Tudo bem? 💚

Confirmamos o recebimento do pagamento referente ao serviço do dia *{data}*.

Agradecemos por realizar o pagamento e pela confiança em nosso trabalho! Daremos continuidade às próximas etapas conforme o combinado.

Jr_Tech_OFC Fotografia
```

#### 19. Preparação para o ensaio
**Atalho:** `preparo`  
**Etapa:** orientar o cliente antes do ensaio. Personalizar as recomendações conforme o tipo de sessão.

```text
Olá, {cliente}! Tudo bem? ✨

Está chegando o dia do nosso ensaio! Para que tudo aconteça da melhor forma, separe com antecedência as roupas e os itens que deseja utilizar.

Nossa dica é escolher peças em que você se sinta confortável e que combinem com o estilo das fotografias que deseja.

Se tiver alguma dúvida ou quiser conversar sobre os detalhes da preparação, estou à disposição.

Até breve! 📸

Jr_Tech_OFC Fotografia
```

#### 20. Lembrete do ensaio
**Atalho:** `lembrete`  
**Etapa:** lembrar o cliente de um compromisso próximo.

```text
Olá, {cliente}! Tudo bem? 😊

Passando para lembrar que nosso ensaio fotográfico está marcado para *{data}*, às *{horario}*.

📍 *Local:* {local}

Se possível, organize-se para chegar com 10 minutos de antecedência. Assim teremos tempo para preparar tudo com tranquilidade.

Se surgir qualquer imprevisto, por favor, avise por aqui.

Estamos preparando tudo para receber você! 📸

Jr_Tech_OFC Fotografia
```

#### 21. Reagendamento
**Atalho:** `reag`  
**Etapa:** iniciar a conversa quando o cliente precisa mudar a data. A nova data depende de consulta à agenda.

```text
Olá, {cliente}! Tudo bem?

Sem problemas, vamos verificar uma alternativa para o seu ensaio. 😊

Qual outra data seria melhor para você?

Vou consultar a disponibilidade da agenda e retorno para alinharmos a alteração.

Agradeço por nos avisar com antecedência!

Jr_Tech_OFC Fotografia
```

#### 21A. Cancelamento
**Atalho:** `cancel`  
**Etapa:** acolher um pedido de cancelamento e explicar que as condições serão verificadas antes de confirmar qualquer consequência financeira.

```text
Olá, {cliente}! Entendo, e agradeço por nos avisar.

Vou verificar as condições do agendamento e os próximos passos para o cancelamento, considerando o que foi combinado no momento da reserva.

Se preferir, também podemos verificar a possibilidade de reagendamento.

Retorno com as informações para orientar você da melhor forma.

Jr_Tech_OFC Fotografia
```

#### 22. Seleção pendente
**Atalho:** `pendente`  
**Etapa:** lembrar com delicadeza que o cliente ainda precisa escolher as fotografias.

```text
Olá, {cliente}! Tudo bem? 😊

Passando para saber se conseguiu visualizar sua galeria e escolher as fotografias favoritas.

Assim que recebermos sua seleção, poderemos dar continuidade à etapa de edição.

Se tiver qualquer dificuldade para acessar as imagens ou realizar a escolha, podemos ajudar!

Jr_Tech_OFC Fotografia 📸
```

#### 23. Prazo de entrega
**Atalho:** `prazo`  
**Etapa:** informar uma previsão de entrega que já tenha sido definida.

```text
Olá, {cliente}! Tudo bem?

Sobre suas fotografias, a previsão de entrega é *{prazo}*.

Estamos acompanhando cada etapa do processo para garantir o cuidado e a qualidade do resultado final.

Caso haja alguma atualização, manteremos você informado(a).

Agradecemos pela compreensão e confiança!

Jr_Tech_OFC Fotografia
```

#### 24. Suporte à galeria
**Atalho:** `suporte`  
**Etapa:** ajudar quando o cliente tem dificuldade para abrir ou baixar as fotografias.

```text
Olá, {cliente}! Vamos ajudar você com isso. 😊

Para entender o que está acontecendo, me conte em qual etapa aparece a dificuldade:

- Ao abrir o link;
- Ao informar o código de acesso;
- Ao visualizar as fotografias;
- Ao baixar as imagens.

Se possível, envie também uma captura de tela da mensagem que aparece. Assim conseguiremos orientar você com mais precisão.

Jr_Tech_OFC Fotografia 📸
```

#### 25. Avaliação do atendimento
**Atalho:** `avaliacao`  
**Etapa:** pedir uma opinião depois que o cliente teve oportunidade de conferir o material e o atendimento.

```text
Olá, {cliente}! 💚

Esperamos que tenha gostado das suas fotografias e da experiência com nosso atendimento.

Sua opinião é muito importante para nós! Se puder, conte como foi sua experiência com a Jr_Tech_OFC Fotografia.

Seu retorno nos ajuda a aprimorar continuamente nosso trabalho e oferecer uma experiência cada vez melhor.

Muito obrigado pela confiança! 📸
```

#### 26. Autorização para divulgação
**Atalho:** `autoriza`  
**Etapa:** solicitar consentimento antes de publicar fotografias do cliente.

```text
Olá, {cliente}! Tudo bem? 😊

Gostaríamos de saber se você autoriza a Jr_Tech_OFC Fotografia a compartilhar algumas imagens do seu ensaio em nosso portfólio e nas redes sociais, para apresentar nosso trabalho.

A publicação só será realizada com sua autorização. Antes de publicar, confirme e registre quais imagens foram autorizadas e em quais canais poderão ser utilizadas.

Fique à vontade para aceitar ou não. Sua decisão será respeitada!

Agradecemos pela confiança. 💚

Jr_Tech_OFC Fotografia
```

#### 27. Retorno ao cliente
**Atalho:** `retorno`  
**Etapa:** retomar uma conversa sem resposta, sem pressionar.

```text
Olá, {cliente}! Tudo bem? 😊

Estou retomando nosso contato para saber se ainda deseja dar continuidade ao atendimento sobre o serviço fotográfico.

Se tiver alguma dúvida ou precisar de mais informações, estou à disposição para ajudar.

Será um prazer atender você!

Jr_Tech_OFC Fotografia 📸
```

#### 28. Agradecimento pela indicação
**Atalho:** `indicacao`  
**Etapa:** agradecer quando o cliente informa que chegou por indicação.

```text
Olá, {cliente}! Que bom receber seu contato! 💚

Ficamos muito felizes em saber que nosso trabalho foi indicado a você.

A confiança de quem conhece nosso trabalho e nos recomenda é muito importante para nós.

Será um prazer conhecer o que você deseja registrar e ajudar a tornar esse momento especial!

Jr_Tech_OFC Fotografia 📸
```

#### 29. Imprevisto ou atraso
**Atalho:** `imprevisto`  
**Etapa:** quando o cliente avisa que está atrasado ou teve um contratempo.

```text
Olá, {cliente}! Obrigado(a) por nos avisar.

Entendemos que imprevistos acontecem. 😊

Me informe, por gentileza, quanto tempo estima que vai atrasar, para verificarmos a melhor forma de organizar o atendimento.

Vamos fazer o possível para orientar você da melhor maneira.

Jr_Tech_OFC Fotografia
```

#### 30. Cliente insatisfeito
**Atalho:** `insatisfacao`  
**Etapa:** primeira resposta a uma reclamação. Não discutir nem antecipar uma solução antes de analisar o caso.

```text
Olá, {cliente}. Sinto muito por saber que algo não saiu como você esperava.

Agradeço por compartilhar sua percepção conosco. Queremos entender o que aconteceu e analisar a situação com a devida atenção.

Pode nos explicar, por gentileza, qual ponto não atendeu às suas expectativas? Se possível, envie também a fotografia ou o detalhe ao qual está se referindo.

Vamos avaliar com cuidado e retornar com uma orientação.

Jr_Tech_OFC Fotografia
```

#### 31. Andamento do pedido
**Atalho:** `andamento`  
**Etapa:** responder quando o cliente pergunta se as fotografias estão prontas, mas a conclusão ainda não foi confirmada.

```text
Olá, {cliente}! Tudo bem?

Entendo sua expectativa para receber as fotografias. 💚

Vou verificar o andamento do seu pedido e confirmar em que etapa ele está.

Assim que tiver uma atualização precisa, retorno para você.

Agradeço pela paciência e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia
```

#### 32. Impressos retirados
**Atalho:** `retirado`  
**Etapa:** agradecer e encerrar o atendimento após a retirada dos materiais impressos.

```text
Olá, {cliente}! 😊

Esperamos que seus impressos tenham ficado como você esperava!

Agradecemos pela preferência e pela confiança em nosso trabalho. Será sempre um prazer atender você novamente.

Jr_Tech_OFC
```

---

