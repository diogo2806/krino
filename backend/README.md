# Backend KRINO

API em Java 21 + Spring Boot, executada isoladamente no EasyPanel e conectada a PostgreSQL externo por variáveis de ambiente.

A tecnologia específica não era imposta pelos documentos da licitação; a fundação adotada na issue #2 usa Java 21, Spring Boot, JDBC, Flyway e PostgreSQL.

## Variáveis de ambiente

| Variável | Obrigatória | Finalidade |
|---|---|---|
| `DB_URL` | sim | JDBC URL do PostgreSQL |
| `DB_USERNAME` | sim | usuário do banco |
| `DB_PASSWORD` | sim | senha do banco |
| `JWT_SECRET` | sim | segredo HMAC de no mínimo 32 bytes para assinatura dos tokens |
| `FRONTEND_ORIGIN` | não | origem autorizada no CORS; padrão local `http://localhost:5173` |
| `JWT_EXPIRATION_MINUTES` | não | validade do token; padrão `60` minutos |
| `BOOTSTRAP_ADMIN_USERNAME` | não | usuário usado somente para criar o primeiro administrador quando a base está vazia |
| `BOOTSTRAP_ADMIN_PASSWORD` | não | senha inicial do primeiro administrador; mínimo de 12 caracteres |
| `DB_POOL_MAX` | não | limite do pool; padrão `10` |
| `PORT` | não | porta HTTP; padrão `8080` |

A ausência das variáveis obrigatórias impede a inicialização da aplicação. O bootstrap administrativo só é executado quando não há nenhum usuário; depois disso, as variáveis de bootstrap não criam ou sobrescrevem contas.

## Execução local

```bash
DB_URL=jdbc:postgresql://localhost:5432/krino \
DB_USERNAME=krino \
DB_PASSWORD=krino \
JWT_SECRET=troque-por-um-segredo-local-com-32-bytes-ou-mais \
BOOTSTRAP_ADMIN_USERNAME=admin \
BOOTSTRAP_ADMIN_PASSWORD=senha-local-com-12-ou-mais \
mvn spring-boot:run
```

Saúde técnica: `GET /api/health`.

## Identidade e acesso

- `POST /api/auth/login`: autenticação com mensagem genérica para credenciais inválidas;
- `GET /api/auth/me`: dados do usuário autenticado;
- `GET /api/auth/access-context`: permissões efetivas no escopo municipal usadas para refletir ações no frontend;
- `/api/admin/users`: gestão de usuários, senha e vínculos de perfil/escopo;
- `/api/admin/roles` e `/api/admin/permissions`: gestão configurável de perfis e permissões.

O token contém somente identidade básica. As permissões são recarregadas do banco a cada requisição autenticada, de modo que alterações administrativas passam a valer sem emitir novo token. Autorizações de Rede, unidade escolar, estudante vinculado e diário atribuído permanecem validadas no backend.

## Diário de Classe

Endpoints principais:

- `GET/POST /api/diaries`: consulta e criação de diários por turma;
- `GET /api/diaries/{id}/roster`: estudantes com matrícula ativa na turma;
- `GET/PUT /api/diaries/{id}/lessons`: consulta e gravação de conteúdo/frequência por data e aula;
- `/api/diaries/{id}/assessments`: avaliações e notas;
- `/api/diaries/{id}/planning`: planejamento pedagógico por período;
- `/api/diaries/{id}/curriculum`: referências curriculares aplicáveis;
- `/api/secretaria/professionals/{professionalId}/user-link`: vínculo entre cadastro profissional e conta utilizada no login.

Regras aplicadas no backend:

- `DIARY_READ`, `DIARY_EDIT`, `DIARY_ADMIN` e `CURRICULUM_MANAGE` respeitam escopo de Rede/unidade;
- professor com `DIARY_EDIT` só edita diário em que é o responsável e sua conta está vinculada ao cadastro profissional;
- aula/frequência exigem dia letivo em `school_calendar_day`;
- o professor responsável deve possuir `teacher_assignment` vigente;
- Anos Finais e EJA exigem componente curricular e horário vigente em `class_schedule` no dia da semana do lançamento;
- conteúdo curricular não é inventado nem pré-carregado: `curriculum_item` recebe somente referências validadas pela Administração;
- operações relevantes geram auditoria sem armazenar senha/token.

## Monitoramento Pedagógico

Endpoints principais:

- `GET /api/pedagogical-monitoring/context`: unidades autorizadas para o ano selecionado;
- `GET /api/pedagogical-monitoring/classes`: turmas da unidade/ano;
- `GET /api/pedagogical-monitoring/students`: estudantes matriculados na turma/ano;
- `GET /api/pedagogical-monitoring/summary`: consolidação por Rede, Escola, Turma ou Estudante;
- `GET /api/pedagogical-monitoring/trend`: evolução nos períodos 1 a 4;
- `GET /api/pedagogical-monitoring/breakdown`: comparação Rede→Escolas, Escola→Turmas ou Turma→Estudantes;
- `GET/POST /api/pedagogical-monitoring/indicator-records`: resultados documentados, simulações e projeções IDEB/IDEPE no escopo selecionado.

O contrato `PedagogicalMetricProvider` desacopla as fontes de resultado. `DiaryPedagogicalMetricProvider` implementa a fonte interna do Diário de Classe. A Avaliação em Rede deve fornecer outro provedor e reutilizar as entidades centrais, sem duplicar escola, turma ou estudante.

Fórmulas internas documentadas em `docs/requisitos/06-relatorios-e-indicadores.md`:

- cobertura: `(estudantes_com_resultado / estudantes_no_escopo) * 100`;
- aproveitamento observado: `(soma_das_notas / soma_das_pontuacoes_maximas_correspondentes) * 100`;
- ambos usam duas casas decimais e arredondamento `HALF_UP`;
- ausência de denominador válido retorna percentual indisponível, não zero artificial.

O KRINO não calcula IDEB/IDEPE oficial nesta implementação porque a fórmula necessária não está confirmada nos documentos-fonte. `SIMULATION` e `PROJECTION` são obrigatoriamente `NON_OFFICIAL`; `OBSERVED_RESULT` exige origem documentada. Os registros podem ser vinculados a Rede, Escola, Turma ou Estudante e são auditados.

## Controle de Entrada e Saída

Endpoints principais:

- `POST /api/access-control/identify`: identifica estudante por QR Code ou matrícula manual;
- `PUT /api/access-control/students/{studentId}/card`: emite/disponibiliza carteirinha com QR Code opaco;
- `POST /api/access-control/events`: registra uma entrada ou saída;
- `POST /api/access-control/sync`: sincroniza lote de eventos capturados offline, com validação independente por item e limite de 5.000 eventos por lote;
- `GET /api/access-control/events`: consulta histórico por unidades autorizadas, com filtros opcionais de unidade e estudante.

Persistência e regras:

- `student_access_credential` contém token QR aleatório, sem expor ID interno no código visual;
- `student_access_event.client_event_id` é UUID único gerado no dispositivo e constitui a chave idempotente do evento;
- reenvio do mesmo UUID retorna o evento existente e não cria nova entrada/saída, nova auditoria ou nova notificação;
- `captured_at` registra o horário original do dispositivo e `received_at` registra quando o servidor recebeu o evento;
- eventos offline preservam `student_id`, `school_id` e `class_id` identificados no momento da captura;
- ao sincronizar, o backend valida que o estudante possuía matrícula na turma/unidade naquela data e considera movimentações com data efetiva, evitando reatribuição retroativa após transferência;
- `source_type` diferencia leitura `QR` e identificação `MANUAL`;
- `captured_offline` diferencia captura offline de captura diretamente online;
- `device_id` e `operator_username` preservam rastreabilidade sem serem exibidos como identificadores técnicos ao operador;
- `ACCESS_CONTROL_READ`, `ACCESS_CONTROL_WRITE` e `ACCESS_CARD_MANAGE` são validadas por Rede/unidade no backend;
- o perfil-base `Operador de entrada e saída` recebe leitura e registro, mas não gestão de carteirinha.

Notificação:

- `student_access_notification.event_id` é único, garantindo uma notificação interna por evento;
- evento registrado online cria a notificação interna imediatamente após persistência;
- evento capturado offline só cria a notificação quando for recebido pelo backend durante sincronização;
- a notificação preserva o horário original da entrada/saída em sua mensagem;
- o registro fica disponível para a caixa interna do responsável; o Portal do Responsável é responsável por exibi-lo somente ao usuário legalmente vinculado ao estudante;
- o módulo não afirma envio por SMS/e-mail sem existir integração de canal externo.

## Portal do Responsável e Comunicação com Famílias

O domínio reutiliza os cadastros centrais e não duplica estudante, matrícula, notas, frequência ou eventos de acesso.

Endpoints do responsável:

- `GET /api/family-portal/students`: lista somente estudantes ligados à conta por `linked_resource_access`;
- `GET /api/family-portal/students/{studentId}/report-card?year=&period=`: boletim, avaliações e frequência consolidada;
- `GET /api/family-portal/students/{studentId}/notifications`: notificações internas da Entrada e Saída;
- `GET /api/family-portal/students/{studentId}/announcements`: comunicados aplicáveis à Escola/Turma/Estudante;
- `GET /api/family-portal/students/{studentId}/conversations`: conversas do responsável para o estudante;
- `GET/POST /api/family-portal/conversations/...`: histórico e resposta;
- `POST /api/family-portal/conversations`: inicia conversa com a escola da matrícula ativa.

A autorização exige simultaneamente `STUDENT_LINKED_READ` e vínculo `STUDENT` + `READ/EDIT`. A verificação ocorre em toda leitura ou escrita. Remover perfil ou vínculo impede novas consultas sem apagar histórico.

Administração do vínculo:

- `/api/admin/users/{userId}/linked-students`: consulta, catálogo, vínculo e desvínculo;
- exige `SCOPE_ASSIGN` municipal;
- a conta alvo precisa estar ativa e possuir perfil atualmente atribuído com `STUDENT_LINKED_READ`;
- vínculo e desvínculo geram auditoria.

Comunicação da escola:

- `/api/family-communication/schools`, `/classes`, `/students` e `/students/{studentId}/guardians`: catálogo do escopo autorizado;
- `/api/family-communication/conversations`: lista e inicia conversas;
- `/api/family-communication/conversations/{id}/messages`: consulta e resposta;
- `/api/family-communication/announcements`: consulta e publicação;
- `DELETE /api/family-communication/announcements/{id}`: desativa comunicado preservando histórico.

`FAMILY_COMMUNICATION_READ` permite consulta; `FAMILY_COMMUNICATION_WRITE` permite iniciar/responder conversas e publicar/desativar comunicados na Rede/unidade autorizada. Uma nova conversa só aceita responsável ativo, ainda vinculado e com perfil atual contendo `STUDENT_LINKED_READ`.

Boletim e frequência:

- notas consolidadas vêm de `student_term_result`;
- avaliações detalhadas vêm de `diary_assessment` e `diary_assessment_grade`;
- frequência por período usa `classes_count` e `absences` consolidados, sem atribuir bimestre artificial às aulas do Diário que não possuem período explícito;
- fórmula: `((aulas - faltas) / aulas) * 100`;
- exemplo: 50 aulas, 2 faltas → 48 aulas frequentadas → `(48 / 50) * 100 = 96,00%`;
- arredondamento `HALF_UP`, 2 casas decimais;
- sem aulas, o percentual é indisponível (`Sem base`), não zero artificial;
- `FamilyAttendanceCalculatorTest` cobre cálculo, arredondamento, ausência de base e limite inferior de 0%.

Conversas, mensagens, publicação/desativação de comunicados e vínculos administrativos são auditados em `security_audit_event`.

## Docker / EasyPanel

O serviço `krino-backend` deve usar `backend/Dockerfile`. Não existe dependência de `docker-compose.yml`.

## Domínios mínimos

```text
identity-access
school-network
students-guardians
academic-enrollment
classes-schedules-calendar
class-diary
pedagogical-monitoring
student-access-control
parent-portal
university-transport
network-assessment
reports-indicators
audit
support
exports-backup
```

## APIs/capacidades obrigatórias

1. Autenticação e autorização por perfil/permissão.
2. Gestão de usuários, escolas, turmas, estudantes, professores e responsáveis.
3. Matrículas e movimentações escolares.
4. Calendário e horários.
5. Diário, frequência, conteúdo, notas e planejamento com validações de dia letivo/horário.
6. Entrada/saída com endpoint idempotente para sincronização offline.
7. Vínculo responsável-estudante.
8. Solicitação e workflow do transporte universitário.
9. Avaliações, instrumentos, gabaritos, processamento, consolidação e resultados.
10. Relatórios e exportações abertas.
11. Logs de auditoria.
12. Gestão/integração de suporte conforme canal adotado.
13. Mecanismos de backup/restore e portabilidade.

## Regras arquiteturais

- Nenhuma regra de autorização pode existir somente no frontend.
- Operações offline de entrada/saída devem possuir identificador idempotente para evitar duplicidade após sincronização.
- Entidades centrais devem evitar duplicidade de cadastro entre módulos.
- Processamento de avaliação deve preservar os dados de origem e resultados calculados para rastreabilidade.
- Toda alteração de resultado consolidado deve ser auditável.
- Exportação final deve ser possível sem dependência de formato proprietário.
- Dados pessoais e instrumentos avaliativos devem ter proteção compatível com LGPD e sigilo exigido.

## Testes mínimos por domínio

- fluxo principal;
- validação e cenário de erro;
- autorização por perfil e escopo;
- persistência;
- concorrência/idempotência quando aplicável;
- regressão de integração entre módulos;
- auditoria;
- exportação/portabilidade para dados críticos.
