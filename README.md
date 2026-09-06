# KRINO

Sistema integrado de gestão educacional baseado nos requisitos do Processo Licitatório nº 010/2026 FME / Pregão Eletrônico nº 007/2026 FME da Secretaria Municipal de Educação de Itaíba/PE.

## Estrutura

```text
krino/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   ├── src/
│   └── README.md
├── frontend/
│   ├── Dockerfile
│   ├── src/
│   │   ├── components/
│   │   └── shared/styles/
│   └── README.md
├── docs/
│   ├── requisitos/
│   ├── poc/
│   ├── arquitetura/
│   └── licitacoes/
├── README.md
└── .gitignore
```

## Execução no EasyPanel

A produção é composta por serviços independentes, sem `docker-compose.yml`:

- `krino-backend`: build por `backend/Dockerfile`;
- `krino-frontend`: build por `frontend/Dockerfile`;
- PostgreSQL: serviço separado do EasyPanel;
- backend: recebe conexão do banco e origem do frontend por variáveis de ambiente;
- frontend: recebe `API_URL` em runtime e acessa somente a API do backend.

Detalhes: [`docs/arquitetura/00-arquitetura-easypanel.md`](docs/arquitetura/00-arquitetura-easypanel.md).

## Documentação

### Requisitos
- [`00-visao-geral.md`](docs/requisitos/00-visao-geral.md)
- [`01-requisitos-funcionais.md`](docs/requisitos/01-requisitos-funcionais.md)
- [`02-regras-de-negocio.md`](docs/requisitos/02-regras-de-negocio.md)
- [`03-requisitos-nao-funcionais.md`](docs/requisitos/03-requisitos-nao-funcionais.md)
- [`04-perfis-e-permissoes.md`](docs/requisitos/04-perfis-e-permissoes.md)
- [`06-relatorios-e-indicadores.md`](docs/requisitos/06-relatorios-e-indicadores.md)
- [`08-rastreabilidade.md`](docs/requisitos/08-rastreabilidade.md)

### POC
- [`05-poc.md`](docs/poc/05-poc.md)
- [`07-wireframes-ascii.md`](docs/poc/07-wireframes-ascii.md)

### Licitação
- [`09-restricoes-licitacao-operacao.md`](docs/licitacoes/09-restricoes-licitacao-operacao.md)
- [`10-pendencias-e-conflitos-documentais.md`](docs/licitacoes/10-pendencias-e-conflitos-documentais.md)

## Regra de prioridade

Para desenvolvimento, a prioridade máxima é a Prova de Conceito (POC). O Anexo Único do Termo de Referência contém 53 itens, sendo 44 essenciais e 9 complementares. Internamente, considerar todos os 44 essenciais como bloqueadores de entrega.

## Fontes

- Estudo Técnico Preliminar - ETP, Itaíba/PE, 20/08/2026.
- Termo de Referência - TR, Itaíba/PE, 20/08/2026.
- Edital do Processo Licitatório nº 010/2026 FME / Pregão Eletrônico nº 007/2026 FME.

Os documentos distinguem requisitos expressos das decisões de implementação. Quando as fontes divergem, a divergência permanece registrada em `docs/licitacoes/10-pendencias-e-conflitos-documentais.md`, sem ser silenciosamente resolvida.
