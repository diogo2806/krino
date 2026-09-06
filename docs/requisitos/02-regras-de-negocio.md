# 02 - Regras de negócio

## RN-001 - Integração e fonte única de dados
Os módulos devem compartilhar informações pertinentes sem exigir duplicação desnecessária de cadastros. Estudante, unidade escolar, turma, professor, calendário e demais entidades centrais devem ter fonte única de verdade dentro da solução.

## RN-002 - Dias letivos no Diário
Aula e frequência só podem ser registradas em dias letivos definidos no calendário escolar.

## RN-003 - Componente curricular
Nos diários separados por componente curricular dos Anos Finais e EJA, aula e frequência só podem ser registradas nos dias em que o componente possuir aula conforme o horário semanal vigente da turma.

## RN-004 - Professor responsável pelo diário
Cada diário deve possuir um professor responsável autorizado a editá-lo. Perfis superiores podem possuir consulta, fiscalização ou administração conforme permissão, mas a regra de edição docente deve preservar a responsabilidade individual do diário.

## RN-005 - Entrada e saída offline
Quando não houver internet, o dispositivo deve conseguir registrar entrada/saída localmente. O registro deve ser sincronizado quando a conexão retornar. A notificação ao responsável só é enviada quando houver conexão.

## RN-006 - Responsável e estudante
Um responsável só pode acessar informações de estudante ao qual esteja vinculado no sistema.

## RN-007 - Transporte universitário
A solicitação deve seguir estados rastreáveis, no mínimo: `RASCUNHO -> ENVIADA -> EM_ANALISE -> AJUSTE_SOLICITADO | NEGADA | APROVADA`. Após ajuste, deve ser possível reenviar para nova análise.

## RN-008 - Motivo de ajuste/negação
Ao solicitar ajuste ou negar transporte, a Secretaria deve registrar motivo legível ao estudante.

## RN-009 - Carteirinha universitária
A carteirinha aprovada deve apresentar foto, identificação do estudante, curso, validade e dias da semana autorizados.

## RN-010 - Avaliação em rede
O sistema deve suportar três etapas avaliativas durante a vigência: Diagnóstica, Monitoramento e Final, para turmas do 1º ao 9º ano do Ensino Fundamental.

## RN-011 - Questões das avaliações
As questões são elaboradas e/ou disponibilizadas/validadas pela Secretaria. O KRINO deve permitir cadastramento/importação e organização dessas questões/instrumentos sem assumir autoria pedagógica não prevista.

## RN-012 - Segunda chamada
A avaliação online para retardatários/segunda chamada é disponibilizada quando solicitada pela Secretaria.

## RN-013 - Prazo de resultado
Após o recolhimento integral dos materiais necessários ao processamento, os resultados de cada etapa devem ser disponibilizados em até 7 dias úteis.

## RN-014 - Dados da Administração
Todos os dados gerados, armazenados ou tratados no contrato pertencem à Administração Municipal. Não pode existir retenção tecnológica que impeça exportação ou migração.

## RN-015 - Exportação e encerramento
Ao término ou rescisão, os dados devem ser disponibilizados sem custo adicional em formato aberto, estruturado, interoperável e legível, preservando integridade e completude.

## RN-016 - Perfis
Toda operação protegida deve validar o perfil/permissão do usuário no backend; ocultar botão no frontend não é controle de autorização suficiente.

## RN-017 - Auditoria
Operações administrativas, acadêmicas, pedagógicas e de segurança relevantes devem gerar registro de auditoria com usuário, data/hora, ação e referência do recurso afetado.

## RN-018 - Manutenção programada
Interrupções programadas devem ser previamente comunicadas à Contratante.

## RN-019 - POC operacional
Na POC, funcionalidades essenciais precisam funcionar de forma efetiva em ambiente operacional. Não é válido substituir operação por slide, imagem, vídeo gravado, protótipo estático ou promessa de desenvolvimento.
