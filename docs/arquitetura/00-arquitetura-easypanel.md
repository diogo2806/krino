# Arquitetura de execução no EasyPanel

## Topologia

```text
Internet
   |
   v
+-------------------+        HTTPS/API        +-------------------+
| krino-frontend    | ----------------------> | krino-backend     |
| Nginx + React     |                         | Spring Boot       |
+-------------------+                         +---------+---------+
                                                        |
                                                        | JDBC
                                                        v
                                              +-------------------+
                                              | PostgreSQL        |
                                              | EasyPanel         |
                                              +-------------------+
```

Não existe `docker-compose.yml`. Cada aplicação possui seu próprio `Dockerfile` e o PostgreSQL é provisionado como serviço separado no EasyPanel.

## Backend

Build: `backend/Dockerfile`.

Configuração externa: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FRONTEND_ORIGIN`, `PORT` e, opcionalmente, `DB_POOL_MAX`. Segredos não são versionados. A aplicação utiliza Flyway e falha na inicialização quando a configuração obrigatória do banco não está disponível.

## Frontend

Build: `frontend/Dockerfile`.

`API_URL` define a base pública da API e é materializada em `/config.js` durante a inicialização do container. O frontend nunca recebe credenciais do banco.

## Comunicação e persistência

O navegador acessa o frontend e chama somente a API HTTP do backend. O backend é o único serviço de aplicação autorizado a acessar o PostgreSQL. Em produção, `FRONTEND_ORIGIN` deve corresponder à origem pública do frontend.

A base PostgreSQL é persistente e externa ao container do backend. Regras detalhadas de backup, recuperação, auditoria e portabilidade pertencem à funcionalidade específica de segurança.
