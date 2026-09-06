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
| RF-034 Consolidação interna | `PedagogicalMetricProvider` define o contrato de fontes; `DiaryPedagogicalMetricProvider` consolida avaliações/notas do Diário por período |
| RF-035 Visão municipal | `PedagogicalMonitoringService#summary` com escopo `NETWORK`; exige permissão municipal `MONITORING_READ`/`MONITORING_MANAGE` |
| RF-036 Visão por escola | `summary` com `schoolId`, respeitando escopo autorizado da unidade |
| RF-037 Visão por turma | `summary` com `classId`, validando ano letivo e unidade da turma |
| Visão individual | `GET /api/pedagogical-monitoring/students` e `summary` com `studentId`; valida matrícula real na turma/ano antes de consolidar |
| RF-038 Simulação IDEB | `pedagogical_indicator_record` suporta `SIMULATION` em Rede, Escola, Turma e Estudante; classificação é obrigatoriamente `NON_OFFICIAL` e o KRINO não inventa fórmula oficial |
| RF-039 Resultado prévio/projeção | o mesmo registro suporta `OBSERVED_RESULT` documentado e `PROJECTION` não oficial para o ano informado, preservando origem e premissas |
| RF-040 Indicadores educacionais | cards e gráficos exibem cobertura, aproveitamento observado, volume de estudantes/resultados e evolução por período; IDEB/IDEPE documentados permanecem separados das métricas internas |
| RF-041 Integração Avaliação em Rede | novos resultados implementam `PedagogicalMetricProvider`; escolas, turmas e estudantes centrais são reutilizados e a fonte passa a aparecer no filtro do frontend |
| Filtro por período | períodos 1 a 4 aplicados à fonte interna sem remover estudantes da base de cobertura |
| Evolução temporal | `PedagogicalMonitoringService#trend` calcula os indicadores para os quatro períodos em Rede, Escola, Turma ou Estudante |
| Comparação hierárquica | `breakdown`: Rede compara escolas; escola compara turmas; turma compara estudantes |
| Cobertura | `(estudantes_com_resultado / estudantes_no_escopo) * 100`, arredondamento `HALF_UP` em 2 casas; sem base retorna percentual indisponível |
| Aproveitamento observado | `(soma_das_notas / soma_das_pontuacoes_maximas_correspondentes) * 100`, somente notas com pontuação máxima; `HALF_UP` em 2 casas; não representa IDEB/IDEPE |
| Habilidades/descritores | frontend apresenta estado informativo até existir fonte avaliativa integrada com esses dados; não simula dados inexistentes |
| Permissões | `MONITORING_READ` e `MONITORING_MANAGE` por Rede/unidade no backend e refletidas no frontend |
| Auditoria | criação de referências IDEB/IDEPE, simulações e projeções registra `PEDAGOGICAL_INDICATOR_RECORDED` |
| Frontend | `MonitoringPage`, `MetricCard`, `TrendLineChart`, `ProgressBarChart` e `IndicatorRecordDialog`; dashboard usa cards e gráficos, possui filtros até estudante/fonte, Manual da Tela no header e estilos em `frontend/src/shared/styles/monitoring.css` |

## Implementação de RF-042 a RF-050

| Requisito / grupo | Implementação |
|---|---|
| RF-042 Carteirinha | `student_access_credential`, `StudentAccessCredentialService#issueCard`, `PUT /api/access-control/students/{studentId}/card` e `StudentAccessCardDialog`; QR contém token opaco e não ID interno |
| RF-043 Identificação | `POST /api/access-control/identify`, `QrScanner` para QR Code e matrícula como código manual; falha de câmera/leitura orienta fallback manual |
| RF-044 Entrada | `POST /api/access-control/events` com `eventType=ENTRY`, validado no backend pelo escopo da unidade |
| RF-045 Saída | `POST /api/access-control/events` com `eventType=EXIT`, mesma persistência/idempotência da entrada |
| RF-046 Histórico | `GET /api/access-control/events` ordena por horário real de captura e respeita unidades autorizadas; frontend apresenta os últimos registros sincronizados |
| RF-047 Notificação | `student_access_notification` cria uma notificação interna única vinculada ao evento, com estudante, responsável informado e horário original; o Portal do Responsável é o consumidor do registro |
| RF-048 Dispositivos | `AccessControlPage` responsiva; câmera usa `getUserMedia`/`BarcodeDetector` quando suportados, com fallback manual em celular, tablet e computador |
| RF-049 Offline | `offlineQueue.ts` mantém fila/cache local por usuário autenticado, UUID do evento, contexto capturado e dispositivo; reconexão dispara `/api/access-control/sync` automaticamente e existe ação manual `Sincronizar agora` |
| RF-050 Adiar notificação | evento offline não cria notificação local; `StudentAccessNotificationService#issue` só executa depois que o backend recebe o evento durante sincronização |
| Idempotência | `student_access_event.client_event_id` é `uuid unique`; reenvio retorna evento existente sem nova persistência, auditoria ou notificação |
| Ordem/histórico | `captured_at` permanece o horário da captura; `received_at` registra o recebimento; fila local é ordenada por `capturedAt` |
| Transferência entre captura/sync | evento carrega IDs canônicos identificados na captura; `validateCapturedIdentity` valida matrícula e movimentos na data original, evitando deslocar o histórico para turma posterior |
| Sincronização parcial | `/sync` valida cada item individualmente; erro de um evento não impede eventos válidos do mesmo lote; falha de permissão continua bloqueando por segurança |
| Privacidade local | fila/cache são separados por `username`; limpeza fica desabilitada com eventos pendentes; UUID/dispositivo não são exibidos ao operador |
| Permissões | `ACCESS_CONTROL_READ`, `ACCESS_CONTROL_WRITE`, `ACCESS_CARD_MANAGE`; `AccessControlAccessService` valida Rede/unidade no backend; perfil-base `Operador de entrada e saída` possui leitura/registro |
| Auditoria | novas entradas/saídas geram `STUDENT_ACCESS_EVENT_RECORDED`; reenvios idempotentes não duplicam auditoria; emissão inicial de QR gera `STUDENT_ACCESS_CARD_ISSUED` |
| Frontend | `AccessControlPage`, `QrScanner`, `StudentAccessCardDialog`, componentes compartilhados e Manual da Tela no header; estilos em `frontend/src/shared/styles/access-control.css` via `index.css` |

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
