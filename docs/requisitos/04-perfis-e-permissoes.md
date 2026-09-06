# 04 - Perfis e permissões

As fontes citam expressamente funções como direção escolar, secretaria escolar, técnicos da Secretaria Municipal, coordenação escolar, coordenação da Secretaria Municipal, administrador do sistema e professor. Também há usuários responsáveis legais e estudantes do transporte universitário. O modelo abaixo consolida essas necessidades sem restringir a Administração a nomes fixos: o sistema deve permitir perfis configuráveis.

| Perfil base | Escopo típico |
|---|---|
| Administrador do sistema | usuários, perfis, parâmetros globais, auditoria, configuração |
| SME / Técnico da Secretaria | visão municipal, cadastros e relatórios de toda a Rede |
| Coordenação da SME | monitoramento pedagógico municipal, indicadores e avaliações |
| Direção escolar | gestão da unidade escolar, turmas, consulta pedagógica e administrativa |
| Secretaria escolar | estudantes, matrículas, documentos, turmas, calendário e horários |
| Coordenação escolar | acompanhamento pedagógico da escola/turmas |
| Professor | diário(s) sob sua responsabilidade, frequência, conteúdo, notas e planejamento |
| Responsável legal | dados dos estudantes vinculados: boletim, frequência, mensagens/notificações |
| Estudante do transporte | solicitação, documentos, acompanhamento e carteirinha do transporte |
| Operador de avaliação | preparação/parametrização, processamento e resultados conforme permissão |
| Fiscal/Auditoria | consulta de logs, relatórios e evidências de execução conforme autorização |

## Regras

1. Permissões devem ser aplicadas no backend e refletidas no frontend.
2. Escopo escolar deve impedir acesso indevido a outra unidade quando o perfil não possuir abrangência municipal.
3. Responsável legal só acessa estudante vinculado.
4. Professor só edita diário sob sua responsabilidade.
5. Operações sensíveis devem ser auditadas.
6. Perfis e permissões devem ser configuráveis para acomodar variações definidas pela Secretaria.
