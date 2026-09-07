# 03 - Requisitos não funcionais

## Plataforma e compatibilidade

- **RNF-001 [POC-E]** Aplicação em ambiente web.
- **RNF-002 [POC-E]** Compatibilidade com navegadores modernos e atualizados.
- **RNF-003 [POC-E]** Compatibilidade com computadores, tablets e dispositivos móveis conforme o módulo.
- **RNF-004 [POC-E]** Interface responsiva nos fluxos destinados a dispositivos móveis.
- **RNF-005 [POC-E]** Desempenho, estabilidade e usabilidade suficientes durante a demonstração e operação.
- **RNF-006** Capacidade operacional compatível com a escala de referência, sem limitação contratual artificial de usuários.

## Disponibilidade e continuidade

- **RNF-007** Manter a solução disponível durante a vigência, ressalvadas manutenções programadas previamente comunicadas.
- **RNF-008** Não foi fixado percentual numérico de uptime nas fontes. Não inventar SLA de disponibilidade sem definição contratual posterior.
- **RNF-009** Disponibilizar recuperação de dados em caso de falha, perda, corrupção ou incidente.

## Segurança

- **RNF-010 [POC-E]** Autenticação de usuários.
- **RNF-011 [POC-E]** Autorização por perfil/permissão.
- **RNF-012 [POC-E]** Confidencialidade, integridade, disponibilidade e rastreabilidade das informações.
- **RNF-013 [POC-E]** Logs/auditoria de operações.
- **RNF-014** Criptografia e medidas técnicas compatíveis com boas práticas de segurança.
- **RNF-015** Comunicação de incidente de segurança, perda, vazamento ou acesso indevido à Contratante.
- **RNF-016** Proteção do sigilo das avaliações e instrumentos antes da aplicação.

## LGPD e privacidade

- **RNF-017** Observar integralmente a Lei nº 13.709/2018 (LGPD).
- **RNF-018** Usar dados fictícios no ambiente de POC sempre que possível; a POC não exige dados pessoais reais.
- **RNF-019** Aplicar minimização de acesso conforme perfil e finalidade.

## Backup e recuperação

- **RNF-020 [POC-E]** Backups automáticos e periódicos.
- **RNF-021 [POC-E]** Manter cópia de segurança em ambiente distinto da base principal.
- **RNF-022 [POC-E]** Demonstrar mecanismo de recuperação de informações na POC.
- **RNF-023** As fontes não definem RPO, RTO nem periodicidade exata de backup; não inventar valores sem decisão de projeto/contrato.

## Portabilidade e interoperabilidade

- **RNF-024 [POC-E]** Exportar dados em formato aberto.
- **RNF-025** Exportação final deve ser estruturada, interoperável e legível.
- **RNF-026** Evitar lock-in tecnológico que impeça migração.

## Suporte e manutenção

- **RNF-027 [POC-C]** Canais oficiais de suporte devem permitir registro/acompanhamento das ocorrências.
- **RNF-028** Suporte remoto contínuo e presencial quando necessário.
- **RNF-029 [POC-C]** Manutenção corretiva, preventiva e evolutiva.
- **RNF-030 [POC-C]** Atualizações tecnológicas, funcionais, legais e de segurança sem custo adicional durante a vigência.

## SLA de atendimento

| Criticidade | Definição | Resposta | Solução máxima |
|---|---|---:|---:|
| Crítico | Indisponibilidade total ou funcionalidade essencial sem contorno | até 1h | até 4h |
| Médio | Falha parcial com possibilidade de operação alternativa | até 4h | até 24h |
| Baixo | Dúvida, suporte ou ajuste sem comprometer continuidade | até 24h | até 72h |

Durante períodos de avaliação em rede, o suporte deve ser compatível com a criticidade da atividade para não comprometer o cronograma.

### Regra implementada de contagem e rastreabilidade

Os tempos contratuais da tabela acima são fixos no KRINO e não são parâmetros editáveis. A documentação contratual disponível não confirma se a contagem deve usar horas corridas ou horas úteis. Por isso, a política de cada criticidade nasce com `counting_rule = UNDEFINED`.

A Administração pode configurar apenas a forma de contagem:

- `UNDEFINED`: mantém e exibe os tempos contratuais, mas não calcula data/hora de vencimento;
- `ELAPSED`: contabiliza minutos corridos desde a abertura;
- `BUSINESS`: contabiliza apenas os dias, jornada e fuso horário explicitamente configurados.

Não existe pausa automática do prazo em `Aguardando solicitante`, porque esse comportamento não está definido nas fontes contratuais. Caso essa regra seja formalmente confirmada, ela deverá ser implementada e documentada de forma explícita.

Cada chamado guarda uma cópia da política de SLA vigente no momento da abertura. Mudanças posteriores da política global não reescrevem os prazos históricos. Quando a criticidade de um chamado em atendimento é alterada, os prazos ainda não cumpridos são recalculados desde a data/hora original de abertura usando a política vigente da nova criticidade, e a mudança fica registrada no histórico.

Estados de acompanhamento dos prazos:

- `NOT_CONFIGURED`: regra de contagem ainda não definida;
- `ON_TIME`: prazo calculado e ainda dentro da janela;
- `AT_RISK`: dentro da antecedência de risco configurada;
- `BREACHED`: prazo calculado ultrapassado;
- `MET`: etapa concluída dentro do prazo.

O módulo diferencia suporte e orientação, manutenção corretiva, manutenção preventiva e evolução da plataforma. A abertura, interações, alterações de criticidade/status e solução permanecem persistidas e auditáveis.
