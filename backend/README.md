# Backend KRINO

API em Java 21 + Spring Boot, executada isoladamente no EasyPanel e conectada a PostgreSQL externo por variáveis de ambiente.

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

## Regras arquiteturais

- Nenhuma regra de autorização pode existir somente no frontend.
- Operações offline de entrada/saída devem possuir identificador idempotente.
- Entidades centrais devem evitar duplicidade entre módulos.
- Processamento de avaliação deve preservar dados de origem e resultados calculados.
- Toda alteração de resultado consolidado deve ser auditável.
- Exportação deve ser possível em formato aberto.
- Dados pessoais e instrumentos avaliativos devem ser protegidos conforme LGPD e sigilo exigido.
