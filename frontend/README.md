# Frontend KRINO

Aplicação React + TypeScript + Vite, empacotada em Nginx para execução isolada no EasyPanel.

## Regras estruturais

1. Toda tela é composta por componentes reutilizáveis de `frontend/src/components`.
2. Antes de criar componente, deve-se procurar e reutilizar/evoluir o existente.
3. Estilos ficam exclusivamente em `frontend/src/shared/styles`.
4. `frontend/src/shared/styles/index.css` é o único ponto de entrada global.
5. Componentes e páginas não importam CSS/SCSS diretamente.
6. Evitar estilos inline e padrões visuais concorrentes.

## Manual da Tela

Toda página nova ou alterada deve exibir no header o componente reutilizável de Manual da Tela com `BookOpen`, `aria-label`, `title`, modal/dialog e conteúdo específico. A implementação-base fica em `src/components/manual/ScreenManual.tsx`.

## API em runtime

O container gera `/config.js` na inicialização usando `API_URL`, por exemplo `API_URL=https://api.exemplo.gov.br/api`. Isso evita recompilar o frontend para alterar o endereço do backend.

## Execução local

```bash
npm install
npm run dev
```

O arquivo `public/config.js` aponta localmente para `http://localhost:8080/api`.

## Docker / EasyPanel

O serviço `krino-frontend` deve usar `frontend/Dockerfile`. O frontend não acessa PostgreSQL diretamente e não depende de `docker-compose.yml`.
