# Backend KRINO

API em Java 21 + Spring Boot, executada isoladamente no EasyPanel e conectada a PostgreSQL externo por variáveis de ambiente.

A tecnologia específica não era imposta pelos documentos da licitação; a fundação adotada na issue #2 usa Java 21, Spring Boot, JDBC, Flyway e PostgreSQL.

## Variáveis de ambiente

| Variável | Obrigatória | Finalidade |
|---|---|---|
| `DB_URL` | sim | JDBC URL do PostgreSQL |
| `DB_USERNAME` | sim | usuário do banco |
| `DB_PASSWORD` | sim | senha do banco |
| `FRONTEND_ORIGIN` | não | origem autorizada no CORS; padrão local `http://localhost:5173` |
| `DB_POOL_MAX` | não | limite do pool; padrão `10` |
| `PORT` | não | porta HTTP; padrão `8080` |

A ausência das variáveis obrigatórias de banco impede a inicialização da aplicação, evitando execução com configuração incompleta.

## Execução local

```bash
DB_URL=jdbc:postgresql://localhost:5432/krino DB_USERNAME=krino DB_PASSWORD=krino mvn spring-boot:run
```

Saúde técnica: `GET /api/health`.

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
