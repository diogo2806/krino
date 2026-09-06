# 06 - Relatórios, dashboards e indicadores

## Documentos escolares

O módulo de Secretaria deve emitir, no mínimo, os documentos descritos em `01-requisitos-funcionais.md`, incluindo histórico, ficha individual, declarações, atas, listas e planilhas de apoio.

## Monitoramento Pedagógico

O Monitoramento Pedagógico consolida fontes de resultados por meio do contrato `PedagogicalMetricProvider`. A fonte inicialmente implementada é `INTERNAL_DIARY`, baseada nas avaliações e notas persistidas no Diário de Classe. Novas fontes, como Avaliação em Rede, podem ser integradas como novos provedores sem duplicar escolas, turmas ou estudantes.

Segmentações implementadas para o monitoramento:

1. Rede/Município, quando a conta possui permissão municipal;
2. unidade escolar;
3. turma;
4. período 1 a 4;
5. fonte de resultados.

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
- os cards e gráficos respeitam ano, período, escopo e fonte selecionados;
- ausência de dados não é apresentada como zero quando não existe base de cálculo.

### IDEB e IDEPE

Os documentos-fonte não detalham fórmula oficial suficiente para o KRINO calcular IDEB/IDEPE de forma autônoma. Por isso, `pedagogical_indicator_record` mantém três tipos de registro:

- `OBSERVED_RESULT`: resultado observado proveniente de referência documentada, classificado como `DOCUMENTED_REFERENCE`;
- `SIMULATION`: valor de cenário informado, obrigatoriamente classificado como `NON_OFFICIAL`;
- `PROJECTION`: valor projetado informado, obrigatoriamente classificado como `NON_OFFICIAL`.

Cada registro preserva indicador, ano, nível Rede/unidade, valor, identificação do cenário/referência, origem dos dados, premissas, usuário e data/hora. O sistema não executa fórmula oficial de IDEB/IDEPE nesta implementação.

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

## Segmentações mínimas

Sempre que aplicável, a plataforma deve permitir segmentação/consulta por:

1. Município/Rede;
2. unidade escolar;
3. turma;
4. estudante;
5. etapa/avaliação;
6. componente curricular;
7. habilidade/descritor.

## Dashboards

- Dashboard municipal.
- Dashboard por escola.
- Dashboard por turma.
- Dashboard de avaliação em rede.
- Dashboard/indicadores de monitoramento pedagógico.
- Indicadores de evolução ao longo das etapas/períodos quando houver dados comparáveis.

Não há fórmula específica de IDEB/IDEPE detalhada nos documentos anexos. A implementação de simulação deve ser documentada e validada com a Secretaria antes de ser tratada como cálculo oficial.
