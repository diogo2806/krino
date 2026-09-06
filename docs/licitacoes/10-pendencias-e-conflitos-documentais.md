# 10 - Pendências e conflitos documentais

Este documento não altera as fontes. Ele registra divergências encontradas entre ETP, TR e Edital que precisam ser tratadas com a regra mais segura no desenvolvimento e, se necessário, por pedido formal de esclarecimento no certame.

## CONFLITO-001 - Critério de aprovação da POC

**Como está:**
- ETP 3.15.8.2 e TR 9.3 mencionam aprovação com pelo menos 80% da pontuação total e atendimento integral dos requisitos obrigatórios.
- O Anexo Único do TR estabelece que 100% dos requisitos Essenciais (E) devem receber `ATENDE`; um único essencial não atendido reprova a solução.
- O Edital remete expressamente ao Item 9 e ao Anexo Único do TR.

**Tratamento no KRINO:** desenvolver para **100% dos 44 itens essenciais**. Não usar a regra de 80% como margem técnica.

**Ação externa recomendada:** caso a participação seja efetivada, confirmar formalmente qual regra prevalece para julgamento da POC.

## CONFLITO-002 - Prazo de implantação

**Como está:**
- ETP 5.2 prevê implantação completa em até 30 dias corridos após aprovação do plano de trabalho.
- TR 6.3 prevê implantação integral em até 15 dias corridos contados da Ordem de Serviço.

**Tratamento no KRINO:** planejar implantação para o prazo mais restritivo, **15 dias corridos**.

## CONFLITO-003 - Subcontratação/terceiros

**Como está:**
- ETP 3.13 afirma que não será admitida subcontratação total ou parcial.
- TR 15 admite terceiros para atividades acessórias/instrumentais, citando expressamente impressão, transporte/logística e infraestrutura em nuvem/data center, mantendo responsabilidade integral da contratada.
- A minuta contratual veda subcontratação completa ou da parcela principal.

**Tratamento no KRINO:** o núcleo do software, gestão e responsabilidade ficam com a contratada. Serviços acessórios só devem ser terceirizados se a interpretação final do Edital/TR permitir e com contratos que imponham sigilo, segurança e LGPD.

**Ação externa recomendada:** solicitar esclarecimento formal antes de depender de terceirização de impressão/logística/cloud para habilitação/execução.

## PENDÊNCIA-004 - Fórmula oficial de simulação IDEB/IDEPE

As fontes exigem simulação/acompanhamento, mas não fornecem fórmula, origem de todos os parâmetros, regra de arredondamento nem memória de cálculo. Não tratar uma fórmula criada pelo time como oficial sem validação pedagógica/documental.

## PENDÊNCIA-005 - Uptime, RPO e RTO

As fontes exigem disponibilidade, backup e recuperação, mas não fixam percentual de uptime, periodicidade exata de backup, RPO ou RTO. Definir valores técnicos internos e documentar, sem apresentá-los como exigência do edital até validação.
