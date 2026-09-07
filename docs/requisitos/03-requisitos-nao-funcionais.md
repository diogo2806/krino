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

### Implementação do canal rastreável

O KRINO implementa o suporte interno por `support_ticket` e `support_ticket_event`. A data/hora de abertura é criada pelo banco e não é alterada pelos fluxos de atualização. Cada interação, mudança de status, mudança de criticidade e registro de solução permanece no histórico cronológico do chamado.

Permissões:

- `SUPPORT_TICKET_CREATE`: abre chamados e consulta/interage somente nos próprios chamados;
- `SUPPORT_TICKET_MANAGE`: permissão municipal para consultar toda a fila, responder, alterar criticidade/status, registrar solução e visualizar o resumo administrativo.

O backend expõe:

- `GET/POST /api/support/tickets` para a área do solicitante;
- `GET /api/support/tickets/{id}` e `POST /api/support/tickets/{id}/messages` com validação de propriedade ou gestão municipal;
- `GET /api/support/admin/tickets`, `PUT /api/support/admin/tickets/{id}` e `GET /api/support/admin/summary` para a equipe autorizada.

Estados implementados: `OPEN` (Aberto), `IN_PROGRESS` (Em atendimento), `WAITING_REQUESTER` (Aguardando solicitante), `RESOLVED` (Resolvido) e `CLOSED` (Encerrado). Marcar como Resolvido ou Encerrado exige solução. Depois de Encerrado o chamado é somente leitura, preservando histórico e solução.

A criticidade determina somente os alvos documentais, sem inventar calendário de contagem:

| Criticidade | Entrada persistida | Alvo de resposta | Alvo de solução |
|---|---|---:|---:|
| Crítico | `CRITICAL` | 1h | 4h |
| Médio | `MEDIUM` | 4h | 24h |
| Baixo | `LOW` | 24h | 72h |

O sistema registra `opened_at`, `first_support_response_at`, `resolved_at` e `closed_at`, portanto possui os fatos necessários para cálculo posterior. Entretanto, como as fontes não definem se a contagem contratual usa horas úteis ou corridas, `contractualCountingRuleDefined` permanece falso e a interface não apresenta “em risco” ou “vencido” como afirmação contratual. Essa classificação só deve ser implementada quando a regra documental for confirmada.

A interface **Suporte e Chamados** usa componentes de `frontend/src/components/support`, Manual da Tela via `PageHeader` e estilos exclusivamente em `frontend/src/shared/styles/support.css`, carregados pelo ponto único `index.css`. Para usuários comuns, a lista mostra somente os próprios chamados; para `SUPPORT_TICKET_MANAGE`, a mesma área apresenta fila municipal e cards de resumo sem duplicar o histórico operacional.

As operações de abertura, interação e gestão também produzem eventos na trilha de auditoria geral `security_audit_event`, sem copiar o conteúdo integral das mensagens para o log de segurança.
