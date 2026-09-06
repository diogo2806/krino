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

A tabela `linked_resource_access` guarda vínculos condicionais que serão utilizados pelos domínios funcionais:

- `STUDENT` + `READ`: permite ao responsável consultar apenas estudante legalmente vinculado, em conjunto com `STUDENT_LINKED_READ`.
- `DIARY` + `EDIT`: permite ao professor editar apenas diário sob sua responsabilidade, em conjunto com `DIARY_ASSIGNED_EDIT`.

Os módulos de Secretaria Escolar e Diário de Classe devem criar/remover esses vínculos conforme seus próprios fluxos, sem duplicar a regra de autorização.

### Operações administrativas

São auditadas na tabela `security_audit_event`, no mínimo: criação/alteração/desativação de usuário, redefinição de senha por administrador, atribuição/remoção de perfil, criação/alteração/exclusão de perfil e alteração das permissões de um perfil. Senhas e tokens não são registrados no evento.

O primeiro administrador pode ser criado somente quando a base não possui usuários, por `BOOTSTRAP_ADMIN_USERNAME` e `BOOTSTRAP_ADMIN_PASSWORD`. Não existe credencial administrativa padrão versionada.

### Interface

A tela **Usuários e acessos** consulta o contexto efetivo de permissões municipais antes de apresentar seções e ações. Usuários sem autorização administrativa recebem o estado “Acesso não permitido”. O frontend reduz ações disponíveis, mas o backend continua autorizando cada endpoint protegido.
