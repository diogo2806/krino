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
