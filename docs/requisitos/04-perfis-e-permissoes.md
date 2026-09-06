# 04 - Perfis e permissões

As fontes citam expressamente funções como direção escolar, secretaria escolar, técnicos da Secretaria Municipal, coordenação escolar, coordenação da Secretaria Municipal, administrador do sistema e professor. Também há usuários responsáveis legais e estudantes do transporte universitário. O modelo abaixo consolida essas necessidades sem restringir a Administração a nomes fixos: o sistema deve permitir perfis configuráveis.

| Perfil base | Escopo típico |
|---|---|
| Administrador do sistema | usuários, perfis, parâmetros globais, auditoria, configuração |
| SME / Técnico da Secretaria | visão municipal, cadastros e relatórios de toda a Rede |
| Coordenação da SME | monitoramento pedagógico municipal, indicadores e avaliações |
| Direção escolar | gestão da unidade escolar, turmas, consulta pedagógica e administrativa |
| Secretaria escolar | estudantes, matrículas, documentos, turmas, calendário e horários |
| Coordenação escolar | acompanhamento pedagógico da escola/turmas |
| Professor | diário(s) sob sua responsabilidade, frequência, conteúdo, notas e planejamento |
| Responsável legal | dados dos estudantes vinculados: boletim, frequência, mensagens/notificações |
| Estudante do transporte | solicitação, documentos, acompanhamento e carteirinha do transporte |
| Operador de avaliação | preparação/parametrização, processamento e resultados conforme permissão |
| Fiscal/Auditoria | consulta de logs, relatórios e evidências de execução conforme autorização |

## Regras

1. Permissões devem ser aplicadas no backend e refletidas no frontend.
2. Escopo escolar deve impedir acesso indevido a outra unidade quando o perfil não possuir abrangência municipal.
3. Responsável legal só acessa estudante vinculado.
4. Professor só edita diário sob sua responsabilidade.
5. Operações sensíveis devem ser auditadas.
6. Perfis e permissões devem ser configuráveis para acomodar variações definidas pela Secretaria.

## Implementação no KRINO

A identidade e o acesso utilizam quatro conceitos separados:

1. **Usuário**: conta autenticável, com nome, identificador de login, senha protegida por BCrypt e estado ativo/inativo.
2. **Perfil**: agrupador configurável de permissões. Os perfis-base são criados pelo banco, mas suas permissões podem evoluir sem limitar a Administração a nomes fixos.
3. **Permissão**: capacidade funcional identificada por código estável.
4. **Atribuição de perfil**: vínculo entre usuário, perfil e escopo `NETWORK`, `SCHOOL` ou `USER`.

A autenticação usa JWT assinado por segredo externo. O token não congela as permissões: o backend recarrega as permissões atuais do usuário em cada requisição. Isso garante que remoção ou concessão de acesso produza efeito imediato.

### Escopos

- `NETWORK`: acesso municipal, quando a permissão do perfil autorizar a operação.
- `SCHOOL`: acesso limitado à referência da unidade escolar atribuída.
- `USER`: acesso restrito ao próprio usuário, usado quando o fluxo exigir isolamento individual.

O backend expõe verificadores específicos de escopo e não considera a ocultação de botões no frontend como mecanismo de segurança.

### Vínculos especiais

A tabela `linked_resource_access` guarda vínculos condicionais usados pelos domínios funcionais:

- `STUDENT` + `READ`: permite ao responsável consultar somente estudante legalmente vinculado, em conjunto com a permissão `STUDENT_LINKED_READ`;
- `DIARY` + `EDIT`: permite ao professor editar somente diário sob sua responsabilidade, em conjunto com `DIARY_ASSIGNED_EDIT`.

O vínculo responsável-estudante é implementado na Administração em **Usuários e acessos > Vincular estudantes**. A operação exige `SCOPE_ASSIGN`, conta ativa e um perfil atualmente atribuído que contenha `STUDENT_LINKED_READ`. Perfil e vínculo individual são independentes: possuir o perfil sem vínculo não concede acesso a estudante; manter um vínculo depois de remover o perfil também não concede acesso.

O backend revalida `STUDENT_LINKED_READ` e `linked_resource_access` em todas as consultas, notificações e mensagens do Portal do Responsável. Remover o vínculo ou a permissão impede novas consultas sem apagar histórico já persistido.

### Comunicação com famílias

A escola usa permissões separadas do acesso do responsável:

- `FAMILY_COMMUNICATION_READ`: consulta conversas e comunicados da unidade autorizada;
- `FAMILY_COMMUNICATION_WRITE`: inicia/responde conversas, publica e desativa comunicados no escopo autorizado.

Uma conversa só pode ser iniciada com conta ativa que mantenha vínculo `STUDENT` e perfil atual com `STUDENT_LINKED_READ`. A tela **Comunicação com Famílias** não cria ou altera vínculo legal; essa responsabilidade permanece na Administração.

### Transporte universitário

O módulo utiliza permissões separadas entre o estudante e a equipe da SEDUC:

- `TRANSPORT_REQUEST_READ`: consulta somente as próprias solicitações, histórico, documentos protegidos e carteirinha;
- `TRANSPORT_REQUEST_WRITE`: cria, corrige, envia documentos e submete somente a própria solicitação;
- `TRANSPORT_REVIEW_READ`: consulta a fila de solicitações e documentos para análise;
- `TRANSPORT_REVIEW_WRITE`: inicia a análise e registra aprovação, solicitação de ajuste ou negativa;
- `TRANSPORT_CARD_ART_WRITE`: parametriza e aprova a arte ativa da carteirinha.

O perfil-base **Estudante do transporte** recebe `TRANSPORT_REQUEST_READ` e `TRANSPORT_REQUEST_WRITE`. A propriedade da solicitação é validada pelo `applicant_user_id` no backend em todas as operações do estudante; conhecer um identificador de outra solicitação não concede acesso.

O perfil-base **SME / Técnico da Secretaria** recebe as permissões de análise e configuração da arte. **Coordenação da SME** recebe consulta da fila. O **Administrador do sistema** recebe todas as permissões do domínio. Como os perfis continuam configuráveis, a Administração pode ajustar os vínculos pelas funcionalidades de perfis sem alterar a regra de autorização do backend.

Foto e comprovante de matrícula permanecem em endpoint autenticado. O frontend carrega esses arquivos com o token da sessão; não existe URL pública de documento. Negativa e solicitação de ajuste exigem motivo, e a emissão da carteirinha depende de solicitação aprovada, validade vigente e arte aprovada pela SEDUC.

### Operações administrativas

São auditadas na tabela `security_audit_event`, no mínimo: criação/alteração/desativação de usuário, redefinição de senha por administrador, atribuição/remoção de perfil, criação/alteração/exclusão de perfil, alteração das permissões de um perfil e vínculo/desvínculo de responsável com estudante. Senhas e tokens não são registrados no evento.

No Transporte Universitário também são auditadas criação e alteração da solicitação, atualização de documentos, submissão, mudanças de estado e atualização/aprovação da arte da carteirinha. O conteúdo binário dos documentos não é copiado para a trilha de auditoria.

O primeiro administrador pode ser criado somente quando a base não possui usuários, por `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD`. Não existe credencial administrativa padrão versionada.

### Interface

A tela **Usuários e acessos** consulta o contexto efetivo de permissões municipais antes de apresentar seções e ações. O botão **Vincular estudantes** aparece somente quando a conta selecionada possui perfil com `STUDENT_LINKED_READ` e o operador possui `SCOPE_ASSIGN`.

O **Portal do Responsável** aparece para contas com `STUDENT_LINKED_READ`; o conteúdo de cada estudante continua condicionado ao vínculo individual validado no backend. A tela **Comunicação com Famílias** aparece conforme `FAMILY_COMMUNICATION_READ`/`FAMILY_COMMUNICATION_WRITE` no escopo autorizado.

O **Transporte Universitário** aparece quando a conta possui alguma permissão `TRANSPORT_*`. Para estudantes, a tela apresenta somente o fluxo próprio. Para a SEDUC, apresenta a fila, decisões e, quando autorizado, a configuração da arte. O Manual da Tela permanece no header por meio do componente compartilhado `PageHeader`/`ScreenManual`.

Usuários sem autorização recebem estado explícito “Acesso não permitido”. O frontend reduz ações disponíveis, mas o backend continua autorizando cada endpoint protegido.
