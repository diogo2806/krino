import { useEffect, useState } from 'react';
import { PageHeader } from '../layout/PageHeader';
import { StatusCard } from '../status/StatusCard';

type TechnicalHealth = { application: string; database: string; };
const manualSections = [
  { title: 'Finalidade', content: 'Validar tecnicamente se o frontend, a API e a conexão com o PostgreSQL estão disponíveis.' },
  { title: 'Campos e filtros', content: 'Esta tela técnica não possui campos nem filtros.' },
  { title: 'Ações', content: 'A verificação da API e do banco ocorre automaticamente ao carregar a tela.' },
  { title: 'Regras e permissões', content: 'A tela é temporária para validação da infraestrutura e não representa o dashboard funcional definitivo.' },
  { title: 'Fluxo principal', content: 'O frontend carrega, consulta o endpoint de saúde da API e apresenta o estado dos três serviços.' },
  { title: 'Mensagens e estados', content: 'VERIFICANDO indica consulta em andamento; OK indica serviço disponível; ERRO indica falha de comunicação ou indisponibilidade.' },
];

export function TechnicalStatusPage() {
  const [apiStatus, setApiStatus] = useState<'OK' | 'VERIFICANDO' | 'ERRO'>('VERIFICANDO');
  const [databaseStatus, setDatabaseStatus] = useState<'OK' | 'VERIFICANDO' | 'ERRO'>('VERIFICANDO');
  useEffect(() => {
    const apiUrl = window.__KRINO_CONFIG__?.apiUrl ?? 'http://localhost:8080/api';
    fetch(`${apiUrl.replace(/\/$/, '')}/health`).then(async (response) => {
      const body = (await response.json()) as TechnicalHealth;
      setApiStatus(body.application === 'UP' ? 'OK' : 'ERRO'); setDatabaseStatus(body.database === 'UP' ? 'OK' : 'ERRO');
    }).catch(() => { setApiStatus('ERRO'); setDatabaseStatus('ERRO'); });
  }, []);
  return <main className="technical-page"><PageHeader eyebrow="KRINO" title="Validação técnica" description="Estado mínimo da aplicação para implantação no EasyPanel." manualSections={manualSections} /><section className="status-grid" aria-label="Estado dos serviços"><StatusCard label="Frontend" status="OK" /><StatusCard label="API" status={apiStatus} /><StatusCard label="Banco conectado" status={databaseStatus} /></section></main>;
}
