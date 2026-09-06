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
Entrada e Saída
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

## UX Writing

- Usar linguagem educacional clara, evitando termos internos de banco/API.
- Botões devem descrever a ação: `Salvar diário`, `Solicitar ajuste`, `Aprovar solicitação`, `Processar gabaritos`, `Exportar dados`.
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
