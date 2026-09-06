# 08 - Matriz de rastreabilidade

A matriz abaixo relaciona os grupos de requisitos do KRINO às fontes anexadas e à POC.

| Domínio | Requisitos KRINO | Fonte principal | POC |
|---|---|---|---|
| Secretaria Escolar | RF-001 a RF-022 | ETP 4.2; TR 4/5 | itens 1-4 |
| Diário de Classe | RF-023 a RF-033 | ETP 4.3 | itens 5-8 |
| Monitoramento Pedagógico | RF-034 a RF-041 | ETP 4.4; TR 4.9 | itens 9-11 e 40 |
| Entrada e Saída | RF-042 a RF-050 | ETP 4.5 | itens 12-15 |
| Portal dos Pais | RF-051 a RF-055 | ETP 4.6 | itens 16-19 |
| Transporte Universitário | RF-056 a RF-066 | ETP 4.7 | itens 20-23 |
| Avaliação em Rede | RF-067 a RF-086 | ETP 4.8/4.9; TR 5.4/6.7/10.3.9 | itens 24-40 |
| Relatórios e dashboards | RF-091 a RF-094; REL-* | ETP 4.9/4.10; TR | itens 37-43 e 50 |
| Segurança e administração | RF-087 a RF-090; RNF-* | ETP 3.8/7; TR 8 | itens 44-53 |

## Implementação de RF-001 a RF-022

| Requisito / grupo | Implementação |
|---|---|
| Unidades escolares | `school_unit`, `SecretariaRegistryService`, `/api/secretaria/schools` |
| Estudantes e responsáveis | `student`, `/api/secretaria/students`, tela Secretaria Escolar / Estudantes |
| Profissionais da educação | `education_professional`, `/api/secretaria/professionals`, tela Secretaria Escolar / Profissionais da educação |
| Turmas | `school_class`, `/api/secretaria/classes`, tela Secretaria Escolar / Turmas |
| Matrícula e rematrícula | `student_enrollment`, `/api/secretaria/enrollments`; rematrícula mantém referência à matrícula anterior quando disponível |
| Transferência, troca de turma e falecimento | `student_movement`, `EnrollmentService#move`; troca de turma preserva a matrícula anterior e cria a nova matrícula vinculada |
| Histórico de movimentações | `EnrollmentService#movements` e `MovementHistoryDialog` |
| Professor por turma/componente/vigência | `teacher_assignment`, `AcademicStructureService`, `ClassAssignmentsDialog` |
| Calendário escolar | `school_calendar_day`, `/api/secretaria/calendar`, tela Calendário escolar |
| Horários de aula com vigência | `class_schedule`, `/api/secretaria/schedules`; valida conflitos da turma e do professor |
| Documentos escolares | `SchoolDocumentService`, `/api/secretaria/documents/{type}`, `DocumentsPanel`; cobre os 13 tipos de emissão previstos em RF-010 a RF-022 |
| Resultados acadêmicos persistidos | `student_term_result`, fonte reutilizável pelo Diário de Classe para notas, faltas e aulas por período |
| Escopo e permissões | `SCHOOL_READ`, `SCHOOL_WRITE`, `SCHOOL_DOCUMENT_READ`; `SchoolAccessService` valida Rede/unidade no backend |
| Auditoria | alterações de cadastro, matrícula, movimentação, atribuição, calendário e horários registram eventos em `security_audit_event` |
| Frontend | `SecretariaEscolarPage` e componentes reutilizáveis em `frontend/src/components/secretaria`; Manual da Tela no header e estilos em `frontend/src/shared/styles` |

## Implementação de RF-023 a RF-033

| Requisito / grupo | Implementação |
|---|---|
| Modalidades do Diário de Classe | `class_diary.mode` suporta Educação Infantil, Criança Alfabetizada, Anos Iniciais, Anos Finais e EJA |
| Diário por turma/componente | `class_diary`, `DiaryService` e `/api/diaries`; Anos Finais e EJA exigem componente curricular |
| Professor responsável | `class_diary.responsible_professional_id`; `professional_user_account` vincula a conta de login ao profissional da educação |
| Autorização docente | `DiaryAccessService#requireEdit` permite edição ao professor responsável com `DIARY_EDIT`; `DIARY_ADMIN` permite administração autorizada |
| Frequência e conteúdo | `diary_lesson`, `diary_attendance`, `/api/diaries/{id}/lessons`; `LessonEditor` apresenta frequência, conteúdo e observações pedagógicas |
| Dia letivo | `DiaryService#validateTeachingDate` consulta `school_calendar_day` e bloqueia lançamento fora de dia letivo no backend |
| Horário de Anos Finais/EJA | `DiaryService#validateTeachingDate` consulta `class_schedule` por turma, componente, dia da semana e vigência antes do lançamento |
| Atribuição vigente | o lançamento também exige `teacher_assignment` vigente para professor, turma e componente aplicável |
| Avaliações e notas | `diary_assessment`, `diary_assessment_grade`, `DiaryEvaluationService` e `AssessmentPanel` |
| Planejamento pedagógico | `diary_planning`, `diary_planning_curriculum`, `PlanningPanel` |
| Currículo aplicável | `curriculum_item` e `CurriculumPanel`; nenhum conteúdo BNCC/Currículo de Pernambuco é inventado ou pré-carregado, sendo aceitas referências cadastradas a partir de conteúdo validado pela Administração |
| Permissões | `DIARY_READ`, `DIARY_EDIT`, `DIARY_ADMIN` e `CURRICULUM_MANAGE`, aplicadas por Rede/unidade no backend e refletidas no frontend |
| Auditoria | criação de diário, aulas, avaliações/notas, planejamento, currículo e vínculo conta-profissional geram eventos em `security_audit_event` |
| Frontend | `DiaryPage` e componentes de `frontend/src/components/diario`; Manual da Tela no header e estilos em `frontend/src/shared/styles/diary.css` com entrada única por `index.css` |

## Implementação de RF-034 a RF-041

| Requisito / grupo | Implementação |
|---|---|
| Consolidação de resultados internos | `PedagogicalMetricProvider` define o contrato de fontes; `DiaryPedagogicalMetricProvider` consolida avaliações/notas do Diário de Classe |
| Visão municipal | `PedagogicalMonitoringService#summary` com escopo `NETWORK`; disponível apenas com permissão municipal `MONITORING_READ`/`MONITORING_MANAGE` |
| Visão por unidade escolar | `PedagogicalMonitoringService#summary` com `schoolId`, respeitando o escopo de unidade |
| Visão por turma | `PedagogicalMonitoringService#summary` com `classId`, validando ano letivo e escola da turma |
| Filtro por período | períodos 1 a 4 aplicados à fonte interna sem remover estudantes da base de cobertura |
| Evolução temporal | `PedagogicalMonitoringService#trend` calcula os mesmos indicadores para os quatro períodos comparáveis |
| Comparação entre níveis | `breakdown`: Rede compara unidades; unidade escolar compara turmas |
| Cobertura | `(estudantes_com_resultado / estudantes_no_escopo) * 100`, arredondamento `HALF_UP` em 2 casas; sem base retorna percentual indisponível |
| Aproveitamento observado | `(soma_das_notas / soma_das_pontuacoes_maximas_correspondentes) * 100`, somente notas com pontuação máxima; `HALF_UP` em 2 casas; não representa IDEB/IDEPE |
| IDEB/IDEPE | `pedagogical_indicator_record` diferencia resultado observado documentado, simulação não oficial e projeção não oficial; nenhuma fórmula oficial é inventada |
| Integração futura da Avaliação em Rede | novos resultados devem implementar `PedagogicalMetricProvider`, preservando escolas, turmas e estudantes centrais e aparecendo no filtro Fonte de resultados |
| Habilidades/descritores | frontend apresenta estado informativo até existir fonte avaliativa integrada com esses dados; não simula dados inexistentes |
| Permissões | `MONITORING_READ` e `MONITORING_MANAGE` por Rede/unidade no backend e refletidas no frontend |
| Auditoria | criação de referências IDEB/IDEPE, simulações e projeções registra `PEDAGOGICAL_INDICATOR_RECORDED` |
| Frontend | `MonitoringPage`, `MetricCard`, `TrendLineChart`, `ProgressBarChart` e `IndicatorRecordDialog`; dashboard usa cards e gráficos, Manual da Tela no header e estilos em `frontend/src/shared/styles/monitoring.css` |

## Implementação de RF-087 a RF-089

| Requisito | Implementação |
|---|---|
| Autenticação | `POST /api/auth/login`, JWT assinado externamente, BCrypt e conta ativa |
| Usuários | `/api/admin/users`, incluindo criação, alteração, desativação, senha e atribuições |
| Perfis e permissões | `/api/admin/roles`, `/api/admin/permissions` e atribuições com escopos de Rede/unidade/usuário |
| Escopo | `AuthorizationService` valida Rede, unidade, estudante vinculado e diário atribuído no backend |
| Auditoria de identidade | `security_audit_event` registra mudanças sensíveis sem senha/token |
| Reflexo no frontend | `GET /api/auth/access-context` informa permissões municipais e por unidade para apresentação das ações autorizadas |

## Critérios de aceite transversais

1. Fluxo demonstrável com dados fictícios.
2. Permissão validada no backend.
3. Operações relevantes auditadas.
4. Erros apresentados em linguagem clara e acionável.
5. Estados vazios, carregamento, erro e sucesso previstos no frontend.
6. Ações destrutivas/irreversíveis com confirmação quando aplicável.
7. Documentação da funcionalidade atualizada junto da alteração.
8. Toda nova página frontend deve possuir Manual da Tela no header.
