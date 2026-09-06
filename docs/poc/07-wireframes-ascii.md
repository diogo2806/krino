# 07 - Wireframes ASCII

Os wireframes representam a arquitetura de informação esperada. Toda página nova do frontend deve exibir o botão **Manual da Tela** no header, com ícone `BookOpen`, `aria-label`, `title` e abertura em modal/dialog.

## Padrão de header

```text
+--------------------------------------------------------------------------------+
| KRINO > Contexto da Tela                     [BookOpen Manual da Tela] [Usuário]|
+--------------------------------------------------------------------------------+
| Título da tela                                                                |
| Texto curto de orientação                                                     |
+--------------------------------------------------------------------------------+
```

## Secretaria Escolar - Estudantes

```text
+--------------------------------------------------------------------------------+
| Estudantes                                      [Manual da Tela] [Novo estudante]|
+--------------------------------------------------------------------------------+
| Buscar [___________________] Escola [Todas v] Turma [Todas v] Situação [Ativo v]|
+--------------------------------------------------------------------------------+
| Nome                  Matrícula      Escola        Turma       Situação   Ações |
| Ana Souza             202600123      E.M. A        5º A        Ativo      [...] |
+--------------------------------------------------------------------------------+
| < Anterior                         1 2 3                         Próxima >        |
+--------------------------------------------------------------------------------+
```

## Diário de Classe

```text
+--------------------------------------------------------------------------------+
| Diário de Classe - 7º A / Matemática                       [Manual da Tela]     |
+--------------------------------------------------------------------------------+
| Data [05/09/2026 v]  Aula prevista: SIM  Professor: Maria Silva                |
| Conteúdo [______________________________________________________________]       |
+--------------------------------------------------------------------------------+
| Estudante                         Presença        Nota/atividade                |
| Aluno 1                           [Presente v]    [________]                    |
| Aluno 2                           [Ausente  v]    [________]                    |
+--------------------------------------------------------------------------------+
| [Salvar diário] [Planejamento] [Consultar currículo]                           |
+--------------------------------------------------------------------------------+
```

## Monitoramento Pedagógico

```text
+--------------------------------------------------------------------------------+
| Monitoramento Pedagógico                                    [Manual da Tela]   |
+--------------------------------------------------------------------------------+
| Período [3º trimestre v] Escola [Todas v] Turma [Todas v] Componente [Todos v] |
+--------------------------------------------------------------------------------+
| [Indicador desempenho] [Participação] [IDEB simulado] [Evolução]               |
|                                                                                |
|                    [ gráfico / série temporal ]                                |
|                                                                                |
| [Habilidades críticas] [Descritores] [Intervenções]                            |
+--------------------------------------------------------------------------------+
```

## Entrada e Saída

```text
+--------------------------------------------------------------------------------+
| Controle de Entrada e Saída                                  [Manual da Tela]   |
+--------------------------------------------------------------------------------+
| Status: ONLINE/OFFLINE        Dispositivo: Portaria 01                          |
|                                                                                |
|                 [ CÂMERA / LEITOR QR / CÓDIGO DE BARRAS ]                     |
|                                                                                |
| Último registro: Ana Souza - ENTRADA - 07:12                                  |
| Sincronizações pendentes: 0                                                    |
+--------------------------------------------------------------------------------+
| [Digitar código] [Histórico de registros]                                      |
+--------------------------------------------------------------------------------+
```

## Portal do Responsável

```text
+--------------------------------------------------------------------------------+
| Portal da Família                                             [Manual da Tela]  |
+--------------------------------------------------------------------------------+
| Estudante [Ana Souza v]                                                        |
| [Boletim] [Frequência] [Mensagens]                                             |
+--------------------------------------------------------------------------------+
| Bimestre        Português       Matemática       Faltas                        |
| 1º              8,0             7,5              2                             |
+--------------------------------------------------------------------------------+
```

## Transporte Universitário

```text
+--------------------------------------------------------------------------------+
| Transporte Universitário                                      [Manual da Tela] |
+--------------------------------------------------------------------------------+
| Minha solicitação: AJUSTE SOLICITADO                                           |
| Motivo: Atualize o comprovante de matrícula.                                   |
+--------------------------------------------------------------------------------+
| Curso [____________________] Instituição [____________________]                 |
| Comprovante [arquivo.pdf]     Foto [foto.jpg]                                  |
| Dias: [x] Seg [ ] Ter [x] Qua [ ] Qui [x] Sex                                  |
+--------------------------------------------------------------------------------+
| [Salvar] [Reenviar para análise]                                               |
+--------------------------------------------------------------------------------+
```

## Avaliação em Rede - Gestão

```text
+--------------------------------------------------------------------------------+
| Avaliações em Rede                                           [Manual da Tela]   |
+--------------------------------------------------------------------------------+
| [Nova avaliação] Etapa [Diagnóstica v] Ano/Série [5º v] Status [Todos v]       |
+--------------------------------------------------------------------------------+
| Avaliação       Escolas  Turmas  Estudantes  Processados  Status       Ações   |
| Diagnóstica 5º  26       40      900         900          Consolidada  [...]   |
+--------------------------------------------------------------------------------+
| [Gabaritos] [Listas] [Etiquetas] [Resultados] [Dashboard]                      |
+--------------------------------------------------------------------------------+
```

## Avaliação em Rede - Resultado

```text
+--------------------------------------------------------------------------------+
| Resultados - Diagnóstica 5º                                  [Manual da Tela]   |
+--------------------------------------------------------------------------------+
| Nível [Rede v] Escola [Todas v] Turma [Todas v] Estudante [Todos v]            |
| Componente [Matemática v] Habilidade [Todas v]                                 |
+--------------------------------------------------------------------------------+
| [Participação] [Média de acertos] [Habilidades críticas]                       |
|                                                                                |
|                        [ gráfico de desempenho ]                               |
|                                                                                |
| [Tabela por questão/habilidade] [Exportar]                                     |
+--------------------------------------------------------------------------------+
```

## Usuários e Perfis

```text
+--------------------------------------------------------------------------------+
| Usuários e Perfis                                            [Manual da Tela]   |
+--------------------------------------------------------------------------------+
| [Usuários] [Perfis] [Permissões] [Auditoria]                                   |
| Buscar [________________] Perfil [Todos v] Escola [Todas v] [Novo usuário]      |
+--------------------------------------------------------------------------------+
| Usuário                  Perfil               Escopo          Situação   Ações  |
+--------------------------------------------------------------------------------+
```
