# 05 - Prova de Conceito (POC)

## Regra interna de entrega

O Anexo Único do TR possui 53 itens: **44 Essenciais (E)** e **9 Complementares (C)**. Para o KRINO, tratar os 44 essenciais como bloqueadores absolutos da versão de POC.

O Anexo determina que requisito essencial só é aprovado com conceito `ATENDE`; `ATENDE PARCIALMENTE` é insuficiente. Complementares podem não reprovar isoladamente a POC, mas continuam obrigatórios na implantação quando previstos no TR.

## Roteiro completo

| # | Requisito da POC | Classe |
|---:|---|:---:|
| 1 | Cadastro de estudantes, professores, turmas e unidades escolares | E |
| 2 | Controle de matrículas, transferências e movimentações escolares | E |
| 3 | Emissão de documentos escolares e administrativos | E |
| 4 | Gestão de calendário escolar e horários de aulas | E |
| 5 | Funcionamento do Diário de Classe Eletrônico | E |
| 6 | Registro de frequência dos estudantes | E |
| 7 | Registro de conteúdos ministrados | E |
| 8 | Controle de usuários e perfis de acesso | E |
| 9 | Funcionamento do Módulo de Monitoramento Pedagógico | E |
| 10 | Visualização de indicadores educacionais | E |
| 11 | Simulação e acompanhamento de resultados educacionais | C |
| 12 | Funcionamento do Módulo de Controle de Entrada e Saída | E |
| 13 | Leitura de QR Code ou código de barras | E |
| 14 | Registro de entrada e saída | E |
| 15 | Envio de notificações aos responsáveis | C |
| 16 | Funcionamento do Portal dos Pais e Responsáveis | E |
| 17 | Consulta de notas e boletins pelos responsáveis | E |
| 18 | Consulta de frequência pelos responsáveis | E |
| 19 | Comunicação entre escola e responsáveis | C |
| 20 | Funcionamento do Módulo de Transporte Universitário | E |
| 21 | Cadastro de estudantes usuários do transporte | E |
| 22 | Solicitação eletrônica de transporte universitário | E |
| 23 | Emissão de carteirinhas universitárias | E |
| 24 | Funcionamento do Módulo de Avaliação em Rede | E |
| 25 | Cadastro e parametrização das avaliações | E |
| 26 | Organização/identificação por escola, turma e estudante | E |
| 27 | Listas de presença nominais | C |
| 28 | Etiquetas/instrumentos de identificação de pacotes | C |
| 29 | Manual do Aplicador e Ata de Ocorrências | C |
| 30 | Avaliação online para retardatários/segunda chamada | C |
| 31 | Inserção/importação/leitura/processamento de dados dos gabaritos | E |
| 32 | Processamento e consolidação de resultados | E |
| 33 | Resultados consolidados da Rede Municipal | E |
| 34 | Resultados por unidade escolar | E |
| 35 | Resultados por turma | E |
| 36 | Resultados por estudante | E |
| 37 | Relatórios pedagógicos das avaliações | E |
| 38 | Gráficos, dashboards e indicadores pedagógicos | E |
| 39 | Análise por habilidades e/ou descritores | E |
| 40 | Integração com Monitoramento Pedagógico | E |
| 41 | Relatórios gerenciais da solução | E |
| 42 | Gráficos e dashboards gerenciais | E |
| 43 | Relatórios por escola, turma e estudante | E |
| 44 | Solução em ambiente web | E |
| 45 | Compatibilidade com dispositivos móveis nos módulos exigidos | E |
| 46 | Navegadores modernos e atualizados | E |
| 47 | Segurança e autenticação | E |
| 48 | Logs e rastreabilidade | E |
| 49 | Backup e recuperação | E |
| 50 | Exportação em formato aberto | E |
| 51 | Canais/mecanismos de suporte | C |
| 52 | Atualização e manutenção da plataforma | C |
| 53 | Desempenho, estabilidade e usabilidade na demonstração | E |

## Cenário de demonstração recomendado

Usar exclusivamente dados fictícios e preparar um roteiro executável que demonstre:

1. criar escola, turma, professor, estudante e responsável;
2. matricular estudante e vincular professor/horário;
3. lançar diário, frequência e conteúdo em dia permitido;
4. emitir documento escolar;
5. registrar entrada por QR/código de barras;
6. consultar boletim/frequência como responsável;
7. solicitar, analisar e aprovar transporte; emitir carteirinha;
8. cadastrar avaliação, vincular estudantes, importar/processar gabarito e consolidar resultados;
9. visualizar resultados nos quatro níveis exigidos;
10. demonstrar relatório, dashboard, logs, backup/recuperação e exportação aberta.

## Restrição da POC

Não depender de slides, capturas, vídeos ou protótipos estáticos para item essencial. A Comissão pode solicitar criação/alteração de dados, consultas, geração de documentos e relatórios em tempo real.
