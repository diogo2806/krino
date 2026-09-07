# 06 - Relatórios, dashboards e indicadores

## Documentos escolares

O módulo de Secretaria deve emitir, no mínimo, os documentos descritos em `01-requisitos-funcionais.md`, incluindo histórico, ficha individual, declarações, atas, listas e planilhas de apoio.

## Monitoramento Pedagógico

O Monitoramento Pedagógico consolida fontes de resultados por meio do contrato `PedagogicalMetricProvider`. A fonte inicialmente implementada é `INTERNAL_DIARY`, baseada nas avaliações e notas persistidas no Diário de Classe. Novas fontes, como Avaliação em Rede, podem ser integradas como novos provedores sem duplicar escolas, turmas ou estudantes.

Segmentações implementadas para o monitoramento:

1. Rede/Município, quando a conta possui permissão municipal;
2. unidade escolar;
3. turma;
4. estudante vinculado por matrícula à turma/ano selecionados;
5. período 1 a 4;
6. fonte de resultados.

### Cobertura de lançamentos internos

Fonte: matrículas do ano letivo (`student_enrollment`) e notas registradas (`diary_assessment_grade`).

Fórmula:

```text
Cobertura (%) = (estudantes_com_resultado / estudantes_no_escopo) * 100
```

Memória de cálculo de exemplo:

```text
Estudantes matriculados no escopo/ano: 20 estudantes
Estudantes com pelo menos uma nota registrada: 18 estudantes
Cobertura = (18 / 20) * 100
Cobertura = 90,00%
```

Unidade: percentual. O resultado é arredondado para 2 casas decimais com `HALF_UP`. Se a quantidade de estudantes no escopo for zero, o percentual não é calculado e a interface apresenta `Sem base`.

Na visão individual, `estudantes_no_escopo` vale 1 quando existe matrícula do estudante na turma/ano selecionados. A API rejeita combinações estudante/turma/ano sem matrícula correspondente.

### Aproveitamento observado das avaliações internas

Fonte: notas (`diary_assessment_grade.score`) e pontuação máxima informada para cada avaliação (`diary_assessment.max_score`). Entram na fórmula somente lançamentos em que a nota e a pontuação máxima estejam informadas.

Fórmula:

```text
Aproveitamento observado (%) = (soma_das_notas / soma_das_pontuacoes_maximas_correspondentes) * 100
```

Memória de cálculo de exemplo:

```text
Avaliação: pontuação máxima por estudante = 10 pontos
Notas válidas: 8 pontos, 6 pontos e 9 pontos
Soma das notas = 8 + 6 + 9 = 23 pontos
Soma das pontuações máximas = 10 + 10 + 10 = 30 pontos
Aproveitamento observado = (23 / 30) * 100
Aproveitamento observado = 76,67%
```

Unidades de entrada: pontos. Unidade de saída: percentual. O resultado é arredondado para 2 casas decimais com `HALF_UP`. Quando a soma das pontuações máximas válidas for zero, o percentual não é calculado e a interface apresenta `Sem base`.

Essa métrica representa desempenho observado na fonte interna e não corresponde, substitui ou simula automaticamente IDEB ou IDEPE.

### Evolução e comparação

- evolução: calcula as mesmas métricas separadamente para os períodos 1, 2, 3 e 4;
- visão de Rede: compara unidades escolares autorizadas;
- visão de unidade escolar: compara as turmas do ano letivo;
- visão de turma: compara os estudantes matriculados no ano;
- visão de estudante: consolida somente os resultados daquele estudante na turma/ano selecionados;
- os cards e gráficos respeitam ano, período, escopo e fonte selecionados;
- ausência de dados não é apresentada como zero quando não existe base de cálculo.

### IDEB e IDEPE

Os documentos-fonte não detalham fórmula oficial suficiente para o KRINO calcular IDEB/IDEPE de forma autônoma. Por isso, `pedagogical_indicator_record` mantém três tipos de registro:

- `OBSERVED_RESULT`: resultado observado proveniente de referência documentada, classificado como `DOCUMENTED_REFERENCE`;
- `SIMULATION`: valor de cenário informado, obrigatoriamente classificado como `NON_OFFICIAL`;
- `PROJECTION`: valor projetado informado, obrigatoriamente classificado como `NON_OFFICIAL`.

Os registros podem ser associados aos níveis `NETWORK`, `SCHOOL`, `CLASS` e `STUDENT`. Para turma e estudante, a API valida ano letivo, unidade escolar e matrícula antes da persistência. Cada registro preserva indicador, ano, nível, valor, identificação do cenário/referência, origem dos dados, premissas, usuário e data/hora.

RF-038 é atendido pela estrutura de simulação por estudante, turma, escola e Município, sempre marcada como não oficial. RF-039 é atendido pelo registro/consulta de resultados observados e pela possibilidade de registrar projeções para o ano de referência desejado. O sistema não executa fórmula oficial de IDEB/IDEPE nesta implementação.

## Relatórios da Avaliação em Rede

- **REL-001** Percentual da consolidação coletiva das habilidades avaliadas por escola.
- **REL-002** Desempenho por habilidades/descritores e classificação de desempenho por nível por escola.
- **REL-003** Percentual de respostas marcadas em cada alternativa de cada questão.
- **REL-004** Percentual de acerto por questão.
- **REL-005** Percentual de acerto por descritor/habilidade.
- **REL-006** Identificação de questões mais complexas e menos complexas.
- **REL-007** Análise por componente curricular Matemática.
- **REL-008** Análise por componente curricular Língua Portuguesa.
- **REL-009** Respostas de cada estudante por questão, com indicação visual de acerto.
- **REL-010** Visão consolidada do Município com análises pedagógicas úteis a planos de intervenção.
- **REL-011** Relatório Individual de Intervenção Pedagógica / perfil do estudante.
- **REL-012** Percentual de estudantes participantes das avaliações diagnósticas e/ou formativas da Rede.

### Implementação e fonte dos dados

Os relatórios da Avaliação em Rede usam o processamento concluído mais recente de `network_assessment_processing_run` para a avaliação selecionada. Os resultados consolidados vêm de `network_assessment_result`, `network_assessment_result_skill`, `network_assessment_answer_sheet` e `network_assessment_answer`, vinculados às atribuições reais de `network_assessment_assignment`.

A migration `V12__reports_and_result_source.sql` registra `answer_sheet_id` em `network_assessment_result`, permitindo que relatórios por alternativa, questão e resposta individual usem exatamente a folha de respostas válida que originou o resultado processado.

As segmentações aplicadas no backend respeitam as permissões `REPORT_READ` e `REPORT_EXPORT` no nível municipal ou escolar. Turma e estudante são sempre filtrados dentro do escopo autorizado.

### Fórmulas da Avaliação em Rede

Todos os percentuais diretamente derivados usam duas casas decimais e arredondamento `HALF_UP`. Quando a base é zero, o resultado percentual é `null` e a interface apresenta `Sem base`.

#### Percentual de participação

```text
Percentual de participação (%) = (participantes / estudantes_esperados) * 100
```

Exemplo:

```text
Estudantes esperados: 10
Participantes: 8
Percentual de participação = (8 / 10) * 100 = 80,00%
```

`estudantes_esperados` corresponde aos estudantes distintos atribuídos à avaliação no escopo. `participantes` corresponde aos estudantes distintos que possuem resultado no processamento concluído mais recente.

#### Percentual geral de acertos

```text
Percentual geral de acertos (%) = (soma_dos_acertos / soma_da_base_de_questoes_processadas) * 100
```

Exemplo:

```text
Acertos: 150
Base de questões processadas: 200
Percentual geral de acertos = (150 / 200) * 100 = 75,00%
```

#### Percentual de acerto por escola, habilidade/descritor ou componente curricular

```text
Percentual de acerto (%) = (acertos / base_de_questoes) * 100
```

A base de questões é sempre exibida junto do percentual nos relatórios detalhados e no CSV.

#### Percentual de respostas por alternativa

```text
Percentual de respostas (%) = (respostas_na_alternativa / total_de_respostas_da_questao) * 100
```

Respostas sem alternativa marcada são agrupadas como `SEM_RESPOSTA` e participam da base total da questão, pois existem como resposta persistida na folha processada.

#### Percentual de acerto por questão

```text
Percentual de acerto por questão (%) = (respostas_corretas / respostas_processadas_da_questao) * 100
```

A complexidade é relativa ao conjunto filtrado: a menor taxa de acerto é rotulada `Maior dificuldade relativa`, a maior taxa de acerto é `Menor dificuldade relativa` e os demais valores são `Intermediária`. Quando todas as questões possuem a mesma taxa de acerto, não há extremos relativos e todas ficam como `Intermediária`.

### Níveis de desempenho

Cada Avaliação em Rede pode ter faixas próprias em `network_assessment_performance_level`. Cada faixa contém nome, percentual mínimo e percentual máximo. Faixas devem estar entre 0% e 100%, o mínimo não pode superar o máximo e as faixas não podem se sobrepor.

A classificação é aplicada aos relatórios por habilidade/descritor e ao perfil individual. Sem base, a classificação é `Sem base`; com percentual calculado mas sem faixa correspondente, a classificação é `Não parametrizada`.

### Relatório Individual de Intervenção Pedagógica

O perfil individual apresenta percentual geral de acertos e nível de desempenho. As habilidades são ordenadas pelo percentual de acerto para destacar até três prioridades relativas de atenção e até três pontos fortes relativos. Esses grupos são comparações internas do próprio conjunto de habilidades processadas e não substituem diagnóstico pedagógico profissional.

### Exportação

A exportação usa CSV UTF-8 (`text/csv; charset=UTF-8`) e está disponível para:

- habilidades por escola;
- respostas por alternativa;
- acerto por questão;
- acerto por habilidade/descritor;
- análise por componente curricular;
- participação;
- respostas do estudante;
- intervenção pedagógica.

O CSV contém cabeçalhos legíveis e as bases de cálculo correspondentes. Na visão municipal, participação é exportada por escola; na visão de uma escola, por turma, mantendo o mesmo nível de detalhamento exibido na interface.

## Segmentações mínimas

Sempre que aplicável, a plataforma permite segmentação/consulta por:

1. Município/Rede;
2. unidade escolar;
3. turma;
4. estudante;
5. etapa/avaliação;
6. componente curricular da avaliação selecionada;
7. habilidade/descritor nos relatórios específicos.

## Dashboards

- Dashboard municipal.
- Dashboard por escola.
- Dashboard por turma.
- Dashboard de avaliação em rede.
- Dashboard/indicadores de monitoramento pedagógico.
- Indicadores de evolução ao longo das etapas/períodos quando houver dados comparáveis.

Os dashboards da Avaliação em Rede exibem somente cards e gráficos. Tabelas ficam nos relatórios detalhados. A tela informa explicitamente o nível de análise selecionado e diferencia carregamento, ausência de avaliação, ausência de dados, falta de estudante nos relatórios individuais, falta de permissão e falha técnica.

Não há fórmula específica de IDEB/IDEPE detalhada nos documentos anexos. A implementação de simulação permanece documentada separadamente e não é tratada como cálculo oficial.
