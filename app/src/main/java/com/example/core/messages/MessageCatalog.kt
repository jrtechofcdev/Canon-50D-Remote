package com.example.core.messages

/**
 * The 35 ready messages of "Jr_Tech_OFC Fotografia: Central de Atendimento", version 1.3, section 5
 * (JR_TECH_OFC_ATENDIMENTO_v1.3_1071_CENARIOS_EXTREMOS.md). Copied by a script, character for character:
 * do not edit a text here. The test MessageCatalogTest checks each one against the document.
 * The first 14 keep the ids and codes the app already had; the other 21 use their code as id.
 */
object MessageCatalog {
    /** The document these texts come from. */
    const val SOURCE = "Central de Atendimento v1.3"

    val ALL: List<MessageTemplate> = listOf(
        MessageTemplate(
            "fotos", "Envio das fotos", "fotos",
            """Olá, {cliente}! Tudo bem? 📸

Sua galeria está disponível!

Os registros do dia {data} já podem ser conferidos. Ao todo, são *{quantidade} fotos* para você visualizar e, conforme o combinado para o seu serviço, escolher suas favoritas.

Agradecemos pela confiança em nosso trabalho!

Caso tenha alguma dúvida ou precise de suporte, permanecemos à disposição.

Atenciosamente,
Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "previa", "Prévia pronta", "previa",
            """Olá, {cliente}! Tudo bem? ✨

Sua prévia fotográfica está disponível!

Liberamos uma prévia dos registros do nosso ensaio para que você possa conhecer o resultado do trabalho. 📸

A prévia é apenas uma amostra; assim que a galeria completa estiver disponível, enviaremos as orientações para a próxima etapa, conforme o serviço contratado.

Se precisar de ajuda durante o acesso, é só nos chamar.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "entrega", "Fotos entregues", "entrega",
            """Olá, {cliente}! Tudo bem? ✨

Suas *{quantidade} fotografias editadas* estão prontas e foram entregues!

Foi um prazer registrar esses momentos especiais. Agradecemos pela confiança e pela oportunidade de fazer parte dessas lembranças.

Se compartilhar alguma das fotografias nas redes sociais, ficaremos felizes em receber sua marcação: *@jr_tech_ofc* 📸

Será um prazer acompanhar sua publicação!

Atenciosamente,
Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "pag", "Lembrete de pagamento", "pag",
            """Olá, {cliente}! Tudo bem?

*Lembrete de pagamento* 📸

Conforme combinado, o pagamento restante referente ao ensaio realizado em {data} deverá ser efetuado no momento da entrega das fotografias.

Caso tenha alguma dúvida, estou à disposição para ajudar.

Agradeço pela confiança e preferência!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "sel", "Seleção recebida", "sel",
            """Olá, {cliente}! Tudo bem? 💚

Sua seleção foi recebida com sucesso! 📸

Confirmamos o recebimento de *{quantidade} fotografias* selecionadas:

{lista}

Agora iniciaremos o processo de edição e tratamento das imagens, sempre buscando entregar o melhor resultado em cada detalhe.

Assim que suas fotografias estiverem finalizadas, entraremos em contato para informar sobre a entrega.

Agradecemos pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "agenda", "Confirmação do ensaio", "agenda",
            """Olá, {cliente}! Tudo bem? 📸

Gostaríamos de confirmar nosso ensaio fotográfico agendado para o dia *{data}*.

Pedimos, por gentileza, que chegue com *10 minutos de antecedência*. Assim, poderemos organizar os últimos detalhes e aproveitar ao máximo as condições de iluminação para garantir excelentes registros.

Contamos com sua pontualidade e estamos ansiosos para registrar esse momento especial!

Atenciosamente,
Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "local", "Local e horário", "local",
            """Olá, {cliente}! Tudo bem? 📸

Seguem as informações do nosso ensaio fotográfico:

📍 *Local:* {local}
🕒 *Horário:* {horario}

Pedimos que fique atento(a) ao horário combinado. Caso ocorra algum imprevisto ou precise de orientação para chegar ao local, entre em contato por aqui.

Será um prazer registrar esse momento com você!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "sinal", "Reserva da data", "sinal",
            """Olá, {cliente}! Tudo bem? ✨

Para solicitar a reserva da data *{data}*, o sinal pode ser realizado via Pix, utilizando a chave abaixo:

💳 *Chave Pix:* {pix}

Após a identificação do pagamento, enviaremos a confirmação do agendamento.

A data será considerada reservada somente após a confirmação do sinal, conforme combinado.

Agradecemos pelo interesse e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "pix", "Dados do Pix", "pix",
            """Olá, {cliente}! Tudo bem?

Segue abaixo a chave Pix para realizar o pagamento:

💳 *Chave Pix:* {pix}

Após concluir a transferência, pedimos que envie o comprovante por aqui para facilitar a identificação e a confirmação do recebimento.

Agradecemos pela preferência e confiança em nosso trabalho!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "orcamento", "Orçamento", "orc",
            """Olá, {cliente}! Tudo bem? 📸

Agradecemos pelo seu interesse em nosso trabalho!

Conforme solicitado, segue a proposta para o serviço fotográfico:

📷 *Serviço:* {servico}
💰 *Investimento:* {valor}
📦 *Prazo de entrega:* {entrega}

Nosso compromisso é oferecer um trabalho de qualidade, com atenção aos detalhes e cuidado em cada registro.

Caso tenha alguma dúvida ou deseje mais informações sobre a proposta, estamos à disposição para ajudar.

Será um prazer fazer parte desse momento!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "atraso", "Edição em andamento", "edit",
            """Olá, {cliente}! Tudo bem? 🎨📸

Passando para informar que suas fotografias estão em processo de edição e tratamento.

Estamos cuidando de cada detalhe para garantir um resultado de qualidade e valorizar ainda mais os momentos registrados.

Assim que a edição for concluída, enviaremos as fotografias por este canal.

Agradecemos pela paciência e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "retirada", "Impressos prontos", "ret",
            """Olá, {cliente}! Tudo bem? 😊

Temos uma ótima notícia: seus materiais impressos já estão prontos e disponíveis para retirada! 🖨️✨

🕒 *Horário para retirada:* {horario}

Pedimos, por gentileza, que observe o horário informado para organizar a retirada.

Agradecemos pela preferência e pela confiança em nosso trabalho!

Jr_Tech_OFC""",
        ),
        MessageTemplate(
            "wifi", "Fotos no Wi-Fi (entrega local)", "wifi",
            """Olá, {cliente}! Tudo bem? 📸

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

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "obrigado", "Agradecimento", "obg",
            """Olá, {cliente}! Tudo bem? 💚

Agradecemos pela confiança e pela oportunidade de registrar momentos tão especiais. Foi um prazer fazer parte dessa experiência e eternizar essas lembranças através da fotografia.

Esperamos que tenha gostado do resultado! ✨

Se compartilhar suas fotografias nas redes sociais, ficaremos muito felizes se marcar nosso perfil *@jr_tech_ofc*. Sua marcação e seu apoio são muito importantes para o nosso trabalho.

Muito obrigado pela preferência!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "oi", "Boas-vindas", "oi",
            """Olá, {cliente}! Seja bem-vindo(a)! 😊

É um prazer receber seu contato e conhecer seu interesse em nosso trabalho.

Será um prazer ajudar você a encontrar a melhor opção para registrar esse momento.

Me conte: qual tipo de ensaio ou serviço fotográfico você está procurando?

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "duvida", "Entender o pedido", "duvida",
            """Claro, {cliente}! Será um prazer ajudar. 😊

Para entender melhor o que você procura e apresentar a opção mais adequada, poderia me contar um pouco mais sobre o tipo de fotografia ou serviço que deseja?

Assim consigo orientar você com mais precisão.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "disp", "Disponibilidade", "disp",
            """Olá, {cliente}! 😊

Sobre a data *{data}*, vou verificar a disponibilidade na agenda para confirmar se conseguimos realizar seu ensaio nesse dia.

Assim que tiver a confirmação, retorno com as informações para darmos continuidade ao agendamento.

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "disponivel", "Data disponível", "disponivel",
            """Olá, {cliente}! 😊

Boa notícia: a data *{data}* está disponível para o ensaio!

Se desejar seguir com a reserva, posso explicar as condições e orientar você sobre o próximo passo.

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "indisponivel", "Data indisponível", "indisponivel",
            """Olá, {cliente}! Tudo bem?

Verifiquei nossa agenda e, infelizmente, a data *{data}* não está disponível para o ensaio.

Se você tiver flexibilidade, podemos consultar outras datas e procurar uma opção que funcione melhor para você.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "pago", "Pagamento confirmado", "pago",
            """Olá, {cliente}! Tudo bem? 💚

Confirmamos o recebimento do pagamento referente ao serviço do dia *{data}*.

Agradecemos por realizar o pagamento e pela confiança em nosso trabalho! Daremos continuidade às próximas etapas conforme o combinado.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "preparo", "Preparação para o ensaio", "preparo",
            """Olá, {cliente}! Tudo bem? ✨

Está chegando o dia do nosso ensaio! Para que tudo aconteça da melhor forma, separe com antecedência as roupas e os itens que deseja utilizar.

Nossa dica é escolher peças em que você se sinta confortável e que combinem com o estilo das fotografias que deseja.

Se tiver alguma dúvida ou quiser conversar sobre os detalhes da preparação, estou à disposição.

Até breve! 📸

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "lembrete", "Lembrete do ensaio", "lembrete",
            """Olá, {cliente}! Tudo bem? 😊

Passando para lembrar que nosso ensaio fotográfico está marcado para *{data}*, às *{horario}*.

📍 *Local:* {local}

Se possível, organize-se para chegar com 10 minutos de antecedência. Assim teremos tempo para preparar tudo com tranquilidade.

Se surgir qualquer imprevisto, por favor, avise por aqui.

Estamos preparando tudo para receber você! 📸

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "reag", "Reagendamento", "reag",
            """Olá, {cliente}! Tudo bem?

Sem problemas, vamos verificar uma alternativa para o seu ensaio. 😊

Qual outra data seria melhor para você?

Vou consultar a disponibilidade da agenda e retorno para alinharmos a alteração.

Agradeço por nos avisar com antecedência!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "cancel", "Cancelamento", "cancel",
            """Olá, {cliente}! Entendo, e agradeço por nos avisar.

Vou verificar as condições do agendamento e os próximos passos para o cancelamento, considerando o que foi combinado no momento da reserva.

Se preferir, também podemos verificar a possibilidade de reagendamento.

Retorno com as informações para orientar você da melhor forma.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "pendente", "Seleção pendente", "pendente",
            """Olá, {cliente}! Tudo bem? 😊

Passando para saber se conseguiu visualizar sua galeria e escolher as fotografias favoritas.

Assim que recebermos sua seleção, poderemos dar continuidade à etapa de edição.

Se tiver qualquer dificuldade para acessar as imagens ou realizar a escolha, podemos ajudar!

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "prazo", "Prazo de entrega", "prazo",
            """Olá, {cliente}! Tudo bem?

Sobre suas fotografias, a previsão de entrega é *{prazo}*.

Estamos acompanhando cada etapa do processo para garantir o cuidado e a qualidade do resultado final.

Caso haja alguma atualização, manteremos você informado(a).

Agradecemos pela compreensão e confiança!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "suporte", "Suporte à galeria", "suporte",
            """Olá, {cliente}! Vamos ajudar você com isso. 😊

Para entender o que está acontecendo, me conte em qual etapa aparece a dificuldade:

- Ao abrir o link;
- Ao informar o código de acesso;
- Ao visualizar as fotografias;
- Ao baixar as imagens.

Se possível, envie também uma captura de tela da mensagem que aparece. Assim conseguiremos orientar você com mais precisão.

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "avaliacao", "Avaliação do atendimento", "avaliacao",
            """Olá, {cliente}! 💚

Esperamos que tenha gostado das suas fotografias e da experiência com nosso atendimento.

Sua opinião é muito importante para nós! Se puder, conte como foi sua experiência com a Jr_Tech_OFC Fotografia.

Seu retorno nos ajuda a aprimorar continuamente nosso trabalho e oferecer uma experiência cada vez melhor.

Muito obrigado pela confiança! 📸""",
        ),
        MessageTemplate(
            "autoriza", "Autorização para divulgação", "autoriza",
            """Olá, {cliente}! Tudo bem? 😊

Gostaríamos de saber se você autoriza a Jr_Tech_OFC Fotografia a compartilhar algumas imagens do seu ensaio em nosso portfólio e nas redes sociais, para apresentar nosso trabalho.

A publicação só será realizada com sua autorização. Antes de publicar, confirme e registre quais imagens foram autorizadas e em quais canais poderão ser utilizadas.

Fique à vontade para aceitar ou não. Sua decisão será respeitada!

Agradecemos pela confiança. 💚

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "retorno", "Retorno ao cliente", "retorno",
            """Olá, {cliente}! Tudo bem? 😊

Estou retomando nosso contato para saber se ainda deseja dar continuidade ao atendimento sobre o serviço fotográfico.

Se tiver alguma dúvida ou precisar de mais informações, estou à disposição para ajudar.

Será um prazer atender você!

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "indicacao", "Agradecimento pela indicação", "indicacao",
            """Olá, {cliente}! Que bom receber seu contato! 💚

Ficamos muito felizes em saber que nosso trabalho foi indicado a você.

A confiança de quem conhece nosso trabalho e nos recomenda é muito importante para nós.

Será um prazer conhecer o que você deseja registrar e ajudar a tornar esse momento especial!

Jr_Tech_OFC Fotografia 📸""",
        ),
        MessageTemplate(
            "imprevisto", "Imprevisto ou atraso", "imprevisto",
            """Olá, {cliente}! Obrigado(a) por nos avisar.

Entendemos que imprevistos acontecem. 😊

Me informe, por gentileza, quanto tempo estima que vai atrasar, para verificarmos a melhor forma de organizar o atendimento.

Vamos fazer o possível para orientar você da melhor maneira.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "insatisfacao", "Cliente insatisfeito", "insatisfacao",
            """Olá, {cliente}. Sinto muito por saber que algo não saiu como você esperava.

Agradeço por compartilhar sua percepção conosco. Queremos entender o que aconteceu e analisar a situação com a devida atenção.

Pode nos explicar, por gentileza, qual ponto não atendeu às suas expectativas? Se possível, envie também a fotografia ou o detalhe ao qual está se referindo.

Vamos avaliar com cuidado e retornar com uma orientação.

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "andamento", "Andamento do pedido", "andamento",
            """Olá, {cliente}! Tudo bem?

Entendo sua expectativa para receber as fotografias. 💚

Vou verificar o andamento do seu pedido e confirmar em que etapa ele está.

Assim que tiver uma atualização precisa, retorno para você.

Agradeço pela paciência e pela confiança em nosso trabalho!

Jr_Tech_OFC Fotografia""",
        ),
        MessageTemplate(
            "retirado", "Impressos retirados", "retirado",
            """Olá, {cliente}! 😊

Esperamos que seus impressos tenham ficado como você esperava!

Agradecemos pela preferência e pela confiança em nosso trabalho. Será sempre um prazer atender você novamente.

Jr_Tech_OFC""",
        ),
    )

    fun byId(id: String): MessageTemplate? = ALL.firstOrNull { it.id == id }
}
