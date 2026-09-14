# 00 - Visão geral do KRINO

## 1. Objetivo

O KRINO deve ser uma plataforma única e integrada de gestão educacional, em ambiente web, destinada à Rede Municipal de Ensino. Deve centralizar informações acadêmicas, administrativas, pedagógicas e gerenciais, compartilhar dados entre módulos e reduzir retrabalho e duplicidade de cadastros.

## 2. Escala de referência

A solução deve ser dimensionada, no mínimo, para:

- 5.523 estudantes da Rede Municipal de Ensino;
- 267 professores e profissionais da educação;
- 26 unidades escolares;
- 60 gestores escolares e usuários administrativos;
- 4.876 responsáveis legais com acesso ao Portal dos Pais;
- 290 estudantes vinculados ao transporte universitário.

Os quantitativos podem variar durante a execução e não devem ser tratados como limites rígidos de cadastro.

## 3. Módulos obrigatórios

1. Secretaria Escolar e Gestão Administrativa.
2. Diário de Classe Eletrônico.
3. Monitoramento Pedagógico.
4. Controle Eletrônico de Entrada, Saída e Frequência dos Estudantes.
5. Portal dos Pais e Responsáveis.
6. Gestão do Transporte Universitário.
7. Avaliação Educacional em Rede.
8. Relatórios gerenciais, acadêmicos e pedagógicos.
9. Dashboards e indicadores educacionais.
10. Administração, segurança, rastreabilidade, backup e suporte.

## 4. Premissas obrigatórias da solução

- Plataforma integrada e base de dados compartilhada entre módulos.
- Ambiente web e uso em computadores, tablets e dispositivos móveis conforme o módulo.
- Controle de acesso por perfil.
- Rastreabilidade de operações.
- Proteção de dados e aderência à LGPD.
- Backup automático e periódico com cópia em ambiente distinto da base principal.
- Portabilidade dos dados em formato aberto, estruturado, interoperável e legível.
- Suporte e manutenção corretiva, preventiva e evolutiva durante toda a vigência.
- Funcionamento do controle de entrada/saída também offline, com sincronização posterior.

## 5. Foco para a POC

A POC exige demonstração operacional. Slides, imagens, vídeos gravados, protótipos estáticos e promessas de desenvolvimento futuro não substituem a funcionalidade em execução. Portanto, o ambiente de demonstração deve possuir dados fictícios, fluxos completos e capacidade de criar, alterar, consultar, processar e emitir documentos/relatórios durante a sessão.

## 6. Visão inicial da gestão municipal

Contas com permissões municipais em mais de um domínio de gestão e acesso a pelo menos uma fonte consolidada de Monitoramento Pedagógico, Avaliações em Rede ou indicadores de Suporte devem iniciar na **Visão Geral da Rede**. Perfis especializados com um único fluxo principal continuam entrando diretamente no módulo operacional correspondente.

A Visão Geral da Rede deve:

- apresentar primeiro o que exige atenção e a próxima ação possível;
- reutilizar os resultados e regras dos módulos de origem, sem criar cálculos paralelos;
- mostrar o resultado pedagógico da Rede quando houver permissão municipal de Monitoramento;
- destacar o estado real do ciclo das Avaliações em Rede quando houver `ASSESSMENT_READ` municipal;
- mostrar indicadores de criticidade e prazo do Suporte quando houver `SUPPORT_REPORT_READ` municipal;
- ocultar fontes e atalhos sem permissão;
- manter cada fonte independente, para que a falha de uma API não inutilize as demais áreas;
- usar `Sem base` ou mensagem de ausência quando não houver dados suficientes, sem converter ausência em zero ou alerta artificial;
- encaminhar as ações para os módulos existentes, preservando as validações e permissões do fluxo de destino.

```text
+------------------------------------------------------------------------+
| KRINO · Visão Geral da Rede                               [Manual]      |
+------------------------------------------------------------------------+
| Rede municipal · Ano letivo atual                                     |
|                                                                        |
| O que exige sua atenção                                                |
| [ Resultado pedagógico ] [ Avaliações em Rede ] [ Suporte ]           |
|                                                                        |
| Resultado da Rede                                                      |
| [ Cobertura ] [ Aproveitamento ] [ Estudantes ] [ Avaliações ]        |
|                                                                        |
| Acessos rápidos                                                        |
| [Monitoramento] [Avaliações] [Relatórios] [Secretaria] [Suporte]      |
+------------------------------------------------------------------------+
```
