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

## Implementação do módulo de Avaliação em Rede

Os itens 24 a 40 passaram a utilizar o fluxo web integrado do KRINO. A implementação mantém a parametrização das etapas Diagnóstica, Monitoramento e Final, organização por escola/turma/estudante, presença, identificação de pacotes, Manual do Aplicador, Ata de Ocorrências, segunda chamada, gabaritos, processamento e resultados.

Fluxo operacional:

```text
+----------------------+     +-----------------------+     +----------------------+
| Avaliação            | --> | Organização          | --> | Aplicação / presença |
| etapa, ano, série    |     | escola/turma/aluno   |     | materiais e ata      |
+----------------------+     +-----------------------+     +----------+-----------+
                                                                     |
                                                                     v
+----------------------+     +-----------------------+     +----------------------+
| Resultados           | <-- | Processamento        | <-- | Gabaritos            |
| Rede/Escola/Turma    |     | execução auditável   |     | manual/import/online |
| Aluno/Habilidade     |     | e reprocessamento    |     | validação prévia     |
+----------+-----------+     +-----------------------+     +----------------------+
           |
           v
+----------------------+
| Monitoramento        |
| fonte integrada      |
+----------------------+
```

Regras implementadas para a demonstração:

- os dados brutos de cada gabarito são preservados com hash SHA-256 e origem `MANUAL`, `IMPORT` ou `ONLINE`;
- uma nova submissão para o mesmo estudante desativa apenas a versão anterior, sem apagar o histórico;
- falhas de associação com estudante/turma são preservadas separadamente como rejeições de importação;
- questões e organização não podem ser alteradas depois do recebimento de gabaritos;
- cada processamento gera uma nova execução numerada e os reprocessamentos não sobrescrevem resultados anteriores;
- resultados são consolidados pela última execução concluída nos níveis Rede, escola, turma e estudante;
- habilidade e descritor mantêm acertos, total de questões e percentual de acerto;
- percentuais usam `acertos / total * 100`, arredondados em duas casas decimais, com `HALF_UP`; base zero não gera divisão por zero;
- `NetworkAssessmentMetricProvider` disponibiliza a fonte `NETWORK_ASSESSMENT` ao Monitoramento Pedagógico sem duplicar escolas, turmas ou estudantes;
- operações de criação, organização, artefatos, presença, importação, processamento e reprocessamento são registradas na trilha de auditoria existente;
- permissões `ASSESSMENT_READ`, `ASSESSMENT_WRITE`, `ASSESSMENT_PROCESS` e `ASSESSMENT_RESULT_READ` respeitam escopo municipal/escolar conforme a operação;
- a tela **Avaliações em Rede** usa o `PageHeader` reutilizável e, por consequência, mantém o botão **Manual da Tela** com `BookOpen`, `aria-label`, `title` e modal acessível.

Memória de cálculo usada pelo cenário POC automatizado:

```text
Gabaritos válidos = 10
Questões por gabarito = 1
Respostas válidas da Q1 = 10
Alternativa correta da Q1 = A
Respostas corretas = 7
Respostas incorretas = 3

Percentual de acerto = (7 / 10) * 100
Percentual de acerto = 70,00%

Arredondamento = 2 casas decimais, HALF_UP
```

Os relatórios pedagógicos, gráficos e dashboards especializados dos itens 37 e 38 usam estes dados como fonte.

## Implementação dos itens 51 e 52 - Suporte e manutenção

O KRINO possui fluxo interno rastreável para demonstrar os itens complementares de suporte e manutenção sem depender de ferramenta externa durante a POC.

```text
+-------------------+     +--------------------+     +-----------------------+
| Novo chamado      | --> | Atendimento        | --> | Solução / encerramento|
| assunto/descrição |     | mensagens/status   |     | histórico preservado |
| tipo/criticidade  |     | criticidade        |     | indicadores          |
+-------------------+     +---------+----------+     +-----------------------+
                                    |
                                    v
                          +--------------------+
                          | Prazos contratuais |
                          | regra de contagem  |
                          | configurável       |
                          +--------------------+
```

Durante a demonstração:

1. abrir um chamado e confirmar protocolo, data/hora e solicitante;
2. demonstrar as criticidades Crítico, Médio e Baixo e seus tempos contratuais fixos;
3. mostrar que, enquanto a regra de contagem estiver `UNDEFINED`, não é inventada uma data de vencimento;
4. quando houver regra confirmada para a POC, configurar `ELAPSED` ou `BUSINESS` e demonstrar o vencimento calculado;
5. registrar mensagem de atendimento e confirmar a primeira resposta;
6. alterar status/criticidade e conferir o histórico rastreável;
7. registrar solução, resolver e encerrar;
8. consultar indicadores administrativos e atrasos no escopo autorizado;
9. demonstrar os tipos Suporte e orientação, Manutenção corretiva, Manutenção preventiva e Evolução da plataforma;
10. durante uma Avaliação em Rede, vincular opcionalmente o chamado à avaliação e à unidade escolar correspondente.

Não pausar automaticamente a contagem no estado `Aguardando solicitante`: essa regra não foi confirmada no material contratual. Alterações futuras dessa regra exigem atualização do código e da documentação.

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
10. demonstrar relatório, dashboard, logs, backup/recuperação e exportação aberta;
11. abrir e acompanhar um chamado de suporte, registrar atendimento, solução e consultar os indicadores correspondentes.

## Cenário integrado automatizado

O cenário reproduzível da issue #15 está em `backend/src/test/java/br/com/krino/poc/PocEndToEndTest.java`. Ele não injeta seed na aplicação e não cria endpoint administrativo exclusivo para teste. Toda a massa funcional é criada durante o teste pelas APIs reais do KRINO, com autenticação JWT, autorização e validações habilitadas.

A execução usa PostgreSQL 16 real em container descartável com Flyway, iniciado por Testcontainers. O banco existe somente durante o teste e não utiliza `DB_URL`, credenciais, usuários ou dados da VPS/EasyPanel.

Pré-requisitos locais:

- JDK 21;
- Maven;
- Docker Engine disponível para o Testcontainers.

Execução a partir de `backend/`:

```bash
mvn -Dtest=PocEndToEndTest test
```

A massa criada é exclusivamente fictícia e possui nomes/códigos estáveis para facilitar diagnóstico:

- escolas `POC-EM-01` e `POC-EM-02`;
- turmas fictícias `7º A` e `7º B` no ano letivo de 2026;
- professor `PROF-POC-001` com conta de perfil escolar;
- 10 estudantes `ALUNO-POC-001` a `ALUNO-POC-010` na turma principal e um estudante adicional na segunda escola;
- responsável `poc.responsavel` vinculado somente ao primeiro estudante;
- estudante do transporte `poc.transporte`;
- avaliação `Avaliação Diagnóstica POC 2026` com uma questão de alternativa correta `A` e 10 gabaritos conhecidos;
- evento offline com `clientEventId` fixo para provar idempotência e não duplicidade.

O fluxo automatizado valida:

1. bloqueio de API protegida sem JWT;
2. criação de duas escolas e duas turmas;
3. cadastro de professor, 11 estudantes e matrículas;
4. atribuição docente, calendário e horário;
5. usuário professor com escopo apenas da escola A e bloqueio ao tentar consultar a escola B;
6. Diário de Classe, conteúdo e frequência em data letiva/horário válidos;
7. avaliação do Diário e nota visível ao responsável;
8. emissão de declaração de matrícula;
9. emissão de QR, identificação do estudante e sincronização offline repetida sem duplicar evento;
10. notificação interna e frequência consultadas pelo responsável vinculado;
11. solicitação de transporte, anexos fictícios, submissão, análise, aprovação, arte e carteirinha;
12. criação, organização, importação, validação e processamento da Avaliação em Rede;
13. resultados de Rede, escola, turma e estudante;
14. cálculo conhecido de `7 / 10 * 100 = 70,00%` no resultado consolidado e no dashboard;
15. exportação CSV em formato aberto;
16. exportação administrativa e presença das ações relevantes na auditoria.

A repetibilidade vem do banco novo por execução: o teste sempre começa em uma instância PostgreSQL vazia, aplica as mesmas migrations e recria a mesma massa pelas APIs. Nenhuma fixture é carregada automaticamente em produção.

### Fronteira do item 49 - backup e recuperação

Backup e recuperação pertencem à operação do PostgreSQL e dos serviços na arquitetura real de VPS/EasyPanel, não a um endpoint de negócio do KRINO. Por isso o teste E2E do repositório não simula `pg_dump`, restore ou snapshot e não cria workflow de infraestrutura. Na demonstração do item 49 deve ser apresentado o mecanismo operacional configurado no ambiente de produção/homologação e uma recuperação controlada conforme o procedimento da infraestrutura. Isso não altera a massa fictícia nem relaxa os demais critérios automatizados.

## Restrição da POC

Não depender de slides, capturas, vídeos ou protótipos estáticos para item essencial. A Comissão pode solicitar criação/alteração de dados, consultas, geração de documentos e relatórios em tempo real.
