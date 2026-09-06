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
| Documentos escolares | `SchoolDocumentService`, `/api/secretaria/documents/{type}`, `DocumentsPanel`; cobre os 13 tipos de emissão previstos em RF-009 a RF-021 |
| Resultados acadêmicos persistidos | `student_term_result`, fonte reutilizável pelo Diário de Classe para notas, faltas e aulas por período |
| Escopo e permissões | `SCHOOL_READ`, `SCHOOL_WRITE`, `SCHOOL_DOCUMENT_READ`; `SchoolAccessService` valida Rede/unidade no backend |
| Auditoria | alterações de cadastro, matrícula, movimentação, atribuição, calendário e horários registram eventos em `security_audit_event` |
| Frontend | `SecretariaEscolarPage` e componentes reutilizáveis em `frontend/src/components/secretaria`; Manual da Tela no header e estilos em `frontend/src/shared/styles` |

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
