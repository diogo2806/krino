# KRINO

Sistema integrado de gestão educacional baseado nos requisitos do Processo Licitatório nº 010/2026 FME / Pregão Eletrônico nº 007/2026 FME da Secretaria Municipal de Educação de Itaíba/PE.

## Estrutura

```text
krino/
├── backend/
│   └── README.md
├── frontend/
│   └── README.md
└── docs/
    ├── 00-visao-geral.md
    ├── 01-requisitos-funcionais.md
    ├── 02-regras-de-negocio.md
    ├── 03-requisitos-nao-funcionais.md
    ├── 04-perfis-e-permissoes.md
    ├── 05-poc.md
    ├── 06-relatorios-e-indicadores.md
    ├── 07-wireframes-ascii.md
    ├── 08-rastreabilidade.md
    ├── 09-restricoes-licitacao-operacao.md
    └── 10-pendencias-e-conflitos-documentais.md
```

## Regra de prioridade

Para desenvolvimento, a prioridade máxima é a Prova de Conceito (POC). O Anexo Único do Termo de Referência contém 53 itens, sendo 44 essenciais e 9 complementares. Internamente, considerar todos os 44 essenciais como bloqueadores de entrega.

## Fontes

- Estudo Técnico Preliminar - ETP, Itaíba/PE, 20/08/2026.
- Termo de Referência - TR, Itaíba/PE, 20/08/2026.
- Edital do Processo Licitatório nº 010/2026 FME / Pregão Eletrônico nº 007/2026 FME.

Os documentos em `docs/` distinguem requisitos expressos das decisões de implementação. Quando as fontes divergem, a divergência é registrada em `docs/10-pendencias-e-conflitos-documentais.md`, sem ser silenciosamente resolvida.
