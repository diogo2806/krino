# Frontend KRINO

Aplicação React + TypeScript + Vite, empacotada em Nginx para execução isolada no EasyPanel.

## Regras estruturais obrigatórias do projeto

1. Toda tela deve ser composta exclusivamente por componentes reutilizáveis de `frontend/src/components`.
2. Antes de criar componente, procurar e reutilizar/evoluir um componente existente com a mesma finalidade.
3. Estilos CSS/SCSS exclusivamente em `frontend/src/shared/styles`.
4. `frontend/src/shared/styles/index.css` é o único ponto de entrada global de estilos.
5. Não importar CSS/SCSS diretamente em páginas/componentes.
6. Não usar estilo inline quando uma classe puder representar o padrão.
7. Evitar seletores, tokens e componentes visuais duplicados/concorrentes.

## Manual da Tela

Toda página nova ou alterada deve exibir no **header** um botão/ícone de Manual da Tela com:

- ícone `BookOpen`;
- `aria-label`;
- `title`;
- abertura em modal/dialog;
- conteúdo específico e atualizado com: finalidade, campos, botões, filtros, ações, regras, permissões, fluxos e mensagens/estados possíveis.

A implementação-base reutilizável fica em `src/components/manual/ScreenManual.tsx`.

## Estados obrigatórios de UX

Toda tela que consulte dados deve prever:

- carregando;
- sucesso;
- vazio;
- erro;
- sem permissão quando aplicável;
- feedback de salvar/processar;
- confirmação para ação destrutiva quando aplicável.

## Grupos de telas

```text
Autenticação
Dashboard / indicadores
Secretaria Escolar
  - Estudantes
  - Professores/profissionais
  - Unidades escolares
  - Turmas
  - Matrículas/movimentações
  - Calendário
  - Horários
  - Documentos escolares
Diário de Classe
  - Frequência e conteúdo
  - Notas e rendimento
  - Planejamento
  - Currículo
Monitoramento Pedagógico
  - Rede
  - Escola
  - Turma
  - Estudante
  - Evolução por período
  - IDEB/IDEPE observado, simulação e projeção
Entrada e Saída
  - Leitura QR
  - Identificação manual
  - Registro de entrada/saída
  - Fila offline/sincronização
  - Histórico
  - Carteirinha QR
Portal do Responsável
Transporte Universitário
Avaliações em Rede
  - Avaliações
  - Organização por escola/turma/estudante
  - Gabaritos/importação
  - Processamento
  - Resultados
  - Relatórios/dashboards
Administração
  - Usuários
  - Perfis/permissões
  - Auditoria
  - Suporte
```

## Diário de Classe

A página `src/components/diario/DiaryPage.tsx` coordena o módulo sem concentrar os fluxos internos. As responsabilidades são separadas em componentes de domínio:

- `DiaryCreateDialog`: modalidade, componente, professor responsável e vigência;
- `ProfessionalUserLinkDialog`: vínculo entre conta de login e profissional da educação;
- `LessonEditor`: data/aula, conteúdo, observações e frequência;
- `AssessmentPanel`: avaliações e notas;
- `PlanningPanel`: planejamento por período e referências curriculares;
- `CurriculumPanel`: consulta e, quando permitido, cadastro de referências curriculares validadas.

A interface mostra de forma explícita quando o diário está disponível apenas para consulta. Bloqueios de calendário, horário, atribuição ou professor responsável usam a mensagem retornada pela API em linguagem orientativa. O conteúdo curricular não é preenchido com dados presumidos.

Os estilos do módulo ficam em `src/shared/styles/diary.css`, importado exclusivamente por `src/shared/styles/index.css`.

## Monitoramento Pedagógico

`src/components/monitoring/MonitoringPage.tsx` mantém o dashboard focado em cards e gráficos. Os filtros no topo seguem a hierarquia Rede → Escola → Turma → Estudante e também permitem selecionar ano, período e fonte de resultados.

Componentes reutilizáveis envolvidos:

- `MetricCard`: indicador resumido;
- `TrendLineChart`: evolução dos períodos 1 a 4;
- `ProgressBarChart`: comparação entre escolas, turmas ou estudantes;
- `IndicatorRecordDialog`: resultado observado, simulação ou projeção IDEB/IDEPE no escopo atual.

A tela sempre informa o nível atual. Ao selecionar estudante, o backend valida a matrícula correspondente antes de consolidar ou registrar cenário. Simulações e projeções exibem identificação explícita de conteúdo não oficial. Resultados observados mantêm a origem documentada.

Os estilos ficam em `src/shared/styles/monitoring.css`, importado somente por `src/shared/styles/index.css`.

## Entrada e Saída

`src/components/access-control/AccessControlPage.tsx` coordena identificação, registro, fila offline, sincronização, histórico e emissão de carteirinha. A página utiliza:

- `QrScanner`: leitura por câmera com `BarcodeDetector` quando suportado e orientação para código manual quando não houver suporte/permissão de câmera;
- `StudentAccessCardDialog`: visualização e impressão da carteirinha QR;
- `DataTable`: lista de eventos aguardando sincronização e histórico sincronizado;
- `ConfirmDialog`: limpeza explícita do cache offline apenas quando a fila estiver vazia;
- `PageHeader`: Manual da Tela no header e ações globais de sincronização/limpeza.

Estados e microcopy:

- `Online`: servidor disponível segundo o estado de conectividade do navegador;
- `Offline`: capturas ficam armazenadas no dispositivo;
- `Aguardando sincronização`: evento ainda não confirmado pelo backend;
- `Sincronizado`: evento recebido pelo servidor;
- falha de leitura de QR orienta reposicionamento da câmera ou uso da matrícula manual;
- estudante e turma são exibidos antes dos botões `Registrar entrada` e `Registrar saída`;
- identificadores técnicos como UUID do evento não são mostrados ao operador.

Persistência offline:

- cada evento recebe `crypto.randomUUID()` no momento da captura;
- fila e cache são persistidos em `localStorage` e separados pelo `username` autenticado, evitando exposição direta entre contas no mesmo navegador;
- o identificador do dispositivo é estável no navegador, mas não é exibido na interface;
- a fila preserva estudante, escola, turma, horário e origem identificados na captura;
- após resposta confirmada do backend, somente os UUIDs aceitos são removidos da fila;
- evento rejeitado permanece pendente, com a mensagem de erro apresentada ao operador;
- limpar dados offline remove somente fila/cache do usuário autenticado e fica desabilitado enquanto houver eventos pendentes;
- a sincronização automática é acionada quando o navegador volta ao estado online, mantendo também o botão `Sincronizar agora`.

A notificação ao responsável não é apresentada como SMS/e-mail. O backend disponibiliza uma notificação interna quando o evento chega ao servidor; o Portal do Responsável consome essa caixa respeitando o vínculo legal com o estudante.

Os estilos ficam em `src/shared/styles/access-control.css`, importado exclusivamente por `src/shared/styles/index.css`. A impressão da carteirinha é escopada ao modal aberto para não interferir em outros documentos imprimíveis do KRINO.

## UX Writing

- Usar linguagem educacional clara, evitando termos internos de banco/API.
- Botões devem descrever a ação: `Salvar diário`, `Registrar entrada`, `Registrar saída`, `Sincronizar agora`, `Solicitar ajuste`, `Aprovar solicitação`, `Processar gabaritos`, `Exportar dados`.
- Mensagens de erro devem informar o problema e a ação necessária.
- Validações de calendário/horário no diário devem explicar por que o lançamento foi bloqueado.
- Estado do transporte deve usar nomenclatura consistente em todas as telas.
- Resultados e dashboards devem deixar claro o nível de análise selecionado: Rede, Escola, Turma ou Estudante.

## API em runtime

O container gera `/config.js` na inicialização usando `API_URL`, por exemplo:

```text
API_URL=https://api.exemplo.gov.br/api
```

Isso evita recompilar o frontend para alterar o endereço do backend. O arquivo `public/config.js` usa `http://localhost:8080/api` somente para desenvolvimento local.

## Execução local

```bash
npm install
npm run dev
```

## Docker / EasyPanel

O serviço `krino-frontend` deve usar `frontend/Dockerfile`. O frontend não acessa PostgreSQL diretamente e não depende de `docker-compose.yml`.
