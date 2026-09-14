import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import type { AssessmentView } from '../assessment/types';
import { Button } from '../button/Button';
import { MetricCard } from '../chart/MetricCard';
import { PageHeader } from '../layout/PageHeader';
import type { MonitoringSummary, SourceMetric } from '../monitoring/types';
import { StateMessage } from '../state/StateMessage';
import type { SupportReport } from '../support/types';
import type { AccessContext } from '../workspace/types';

export type OverviewTarget = 'secretaria' | 'monitoramento' | 'avaliacoes' | 'relatorios' | 'suporte';

type Props = {
  context: AccessContext;
  onUnauthorized: () => void;
  onNavigate: (target: OverviewTarget) => void;
};

const manualSections = [
  { title: 'Finalidade', content: 'Apresentar ao gestor municipal, em uma única visão, os principais sinais da Rede que exigem atenção, os resultados pedagógicos disponíveis e a próxima ação recomendada em cada área.' },
  { title: 'Indicadores', content: 'Resultado da Rede reutiliza a consolidação do Monitoramento Pedagógico. Cobertura mostra estudantes com resultado sobre a base da fonte selecionada. Aproveitamento observado usa a regra da própria fonte e não cria cálculo novo nesta tela.' },
  { title: 'Prioridades', content: 'Avaliações em Rede usam os estados reais do ciclo para destacar preparação, aplicação, processamento e resultados. Suporte usa o consolidado autorizado de criticidade e prazos. A ausência de dados não é transformada em alerta artificial.' },
  { title: 'Botões e ações', content: 'Ver prioridades abre o Monitoramento Pedagógico. Organizar, Processar, Acompanhar ou Ver avaliações abre Avaliações em Rede. Ver chamados aparece somente quando a conta também pode consultar os chamados. Os acessos rápidos levam aos módulos já existentes sem duplicar funções.' },
  { title: 'Regras e permissões', content: 'A Visão Geral aparece somente para contas com permissões municipais em mais de um domínio de gestão. Cada card é exibido somente quando a conta possui a permissão exigida pela API de origem.' },
  { title: 'Fluxo principal', content: 'Leia primeiro O que exige sua atenção, siga a ação contextual mais relevante e use Resultado da Rede para confirmar cobertura e desempenho. Os acessos rápidos servem apenas como apoio à navegação.' },
  { title: 'Mensagens e estados', content: 'Cada fonte carrega de forma independente. Se uma API falhar, as demais áreas continuam disponíveis e a falha é informada apenas no card correspondente. Sem base pedagógica, a tela mostra Sem base em vez de zero artificial.' },
];

const assessmentPriority: Record<AssessmentView['status'], number> = {
  APPLIED: 0,
  PROCESSING: 1,
  PREPARATION: 2,
  READY: 3,
  PROCESSED: 4,
  CLOSED: 5,
};

function formatPercent(value?: number | null) {
  return value == null ? 'Sem base' : `${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
}

function quantityLabel(value: number, singular: string, plural: string) {
  return `${value.toLocaleString('pt-BR')} ${value === 1 ? singular : plural}`;
}

function selectPedagogicalMetric(summary?: MonitoringSummary): SourceMetric | undefined {
  if (!summary?.sources.length) return undefined;
  return summary.sources.find((source) => source.sourceCode === 'NETWORK_ASSESSMENT' && source.studentsWithResults > 0)
    ?? summary.sources.find((source) => source.studentsWithResults > 0)
    ?? summary.sources[0];
}

function assessmentMessage(assessments: AssessmentView[]) {
  if (assessments.length === 0) return { text: 'Nenhuma avaliação cadastrada para o ano letivo atual.', action: 'Ver avaliações' };
  const sorted = [...assessments].sort((left, right) => assessmentPriority[left.status] - assessmentPriority[right.status]);
  const selected = sorted[0];
  const sameStatus = assessments.filter((assessment) => assessment.status === selected.status).length;
  const quantity = quantityLabel(sameStatus, 'avaliação', 'avaliações');

  switch (selected.status) {
    case 'APPLIED': return { text: `${quantity} com respostas recebidas aguardando processamento.`, action: 'Processar' };
    case 'PROCESSING': return { text: `${quantity} com processamento em andamento.`, action: 'Acompanhar' };
    case 'PREPARATION': return { text: `${quantity} em preparação, ainda exigindo configuração ou organização.`, action: 'Organizar' };
    case 'READY': return { text: `${quantity} ${sameStatus === 1 ? 'pronta' : 'prontas'} para aplicação.`, action: 'Ver avaliações' };
    case 'PROCESSED': return { text: 'As avaliações do ano estão processadas e os resultados disponíveis podem ser consultados.', action: 'Ver resultados' };
    default: return { text: 'As avaliações cadastradas para o ano estão encerradas.', action: 'Ver avaliações' };
  }
}

export function NetworkOverviewPage({ context, onUnauthorized, onNavigate }: Props) {
  const currentYear = new Date().getFullYear();
  const canMonitoring = context.networkPermissions.includes('MONITORING_READ') || context.networkPermissions.includes('MONITORING_MANAGE');
  const canAssessment = context.networkPermissions.includes('ASSESSMENT_READ');
  const canSupport = context.networkPermissions.includes('SUPPORT_REPORT_READ');
  const canOpenSupport = context.permissions.includes('SUPPORT_TICKET_READ');

  const [monitoring, setMonitoring] = useState<MonitoringSummary>();
  const [assessments, setAssessments] = useState<AssessmentView[]>([]);
  const [support, setSupport] = useState<SupportReport>();
  const [monitoringLoading, setMonitoringLoading] = useState(canMonitoring);
  const [assessmentLoading, setAssessmentLoading] = useState(canAssessment);
  const [supportLoading, setSupportLoading] = useState(canSupport);
  const [monitoringError, setMonitoringError] = useState('');
  const [assessmentError, setAssessmentError] = useState('');
  const [supportError, setSupportError] = useState('');

  const handleFailure = useCallback((exception: unknown, fallback: string, setError: (value: string) => void) => {
    if (exception instanceof ApiError && exception.status === 401) {
      onUnauthorized();
      return;
    }
    setError(exception instanceof Error ? exception.message : fallback);
  }, [onUnauthorized]);

  useEffect(() => {
    if (!canMonitoring) return;
    setMonitoringLoading(true);
    setMonitoringError('');
    apiRequest<MonitoringSummary>(`/pedagogical-monitoring/summary?year=${currentYear}`)
      .then(setMonitoring)
      .catch((exception) => handleFailure(exception, 'Não foi possível carregar o resultado pedagógico da Rede.', setMonitoringError))
      .finally(() => setMonitoringLoading(false));
  }, [canMonitoring, currentYear, handleFailure]);

  useEffect(() => {
    if (!canAssessment) return;
    setAssessmentLoading(true);
    setAssessmentError('');
    apiRequest<AssessmentView[]>(`/assessments?academicYear=${currentYear}`)
      .then(setAssessments)
      .catch((exception) => handleFailure(exception, 'Não foi possível carregar a situação das Avaliações em Rede.', setAssessmentError))
      .finally(() => setAssessmentLoading(false));
  }, [canAssessment, currentYear, handleFailure]);

  useEffect(() => {
    if (!canSupport) return;
    setSupportLoading(true);
    setSupportError('');
    apiRequest<SupportReport>('/support/reports')
      .then(setSupport)
      .catch((exception) => handleFailure(exception, 'Não foi possível carregar os indicadores de suporte.', setSupportError))
      .finally(() => setSupportLoading(false));
  }, [canSupport, handleFailure]);

  const pedagogicalMetric = useMemo(() => selectPedagogicalMetric(monitoring), [monitoring]);
  const assessmentSummary = useMemo(() => assessmentMessage(assessments), [assessments]);
  const supportDeadlineOccurrences = support ? support.totals.responseBreaches + support.totals.solutionBreaches : 0;
  const criticalSupport = support?.bySeverity.find((item) => item.severity === 'CRITICAL');
  const criticalActive = criticalSupport ? Math.max(criticalSupport.total - criticalSupport.completed, 0) : 0;

  return (
    <main className="app-page">
      <PageHeader
        eyebrow={`Rede municipal · Ano letivo ${currentYear}`}
        title="Visão Geral da Rede"
        description="Prioridades, resultados e próximas ações da gestão municipal em uma única visão."
        manualSections={manualSections}
      />

      <section aria-labelledby="overview-attention-title">
        <h2 id="overview-attention-title">O que exige sua atenção</h2>
        <div className="status-grid">
          {canMonitoring ? (
            <article className="status-card">
              <span className="status-card__label">Resultado pedagógico</span>
              {monitoringLoading ? <StateMessage title="Atualizando resultado" message="Aguarde enquanto os dados da Rede são consolidados." />
                : monitoringError ? <StateMessage kind="error" title="Resultado indisponível" message={monitoringError} />
                  : pedagogicalMetric ? <>
                    <strong className="status-card__value">Aproveitamento {formatPercent(pedagogicalMetric.achievementPercent)}</strong>
                    <span>Cobertura {formatPercent(pedagogicalMetric.coveragePercent)} · {pedagogicalMetric.sourceLabel}</span>
                    <div className="row-actions"><Button type="button" variant="ghost" onClick={() => onNavigate('monitoramento')}>Ver prioridades</Button></div>
                  </> : <StateMessage title="Sem dados pedagógicos" message="Ainda não há uma fonte com base disponível para o ano letivo atual." />}
            </article>
          ) : null}

          {canAssessment ? (
            <article className="status-card">
              <span className="status-card__label">Avaliações em Rede</span>
              {assessmentLoading ? <StateMessage title="Atualizando avaliações" message="Aguarde enquanto o ciclo do ano letivo é consultado." />
                : assessmentError ? <StateMessage kind="error" title="Avaliações indisponíveis" message={assessmentError} />
                  : <>
                    <strong className="status-card__value">{assessmentSummary.text}</strong>
                    <span>{quantityLabel(assessments.length, 'avaliação cadastrada', 'avaliações cadastradas')} no ano.</span>
                    <div className="row-actions"><Button type="button" variant="ghost" onClick={() => onNavigate('avaliacoes')}>{assessmentSummary.action}</Button></div>
                  </>}
            </article>
          ) : null}

          {canSupport ? (
            <article className="status-card">
              <span className="status-card__label">Suporte</span>
              {supportLoading ? <StateMessage title="Atualizando suporte" message="Aguarde enquanto os chamados são consolidados." />
                : supportError ? <StateMessage kind="error" title="Suporte indisponível" message={supportError} />
                  : support ? <>
                    <strong className="status-card__value">
                      {criticalActive > 0 || supportDeadlineOccurrences > 0
                        ? `${quantityLabel(criticalActive, 'chamado crítico ativo', 'chamados críticos ativos')} · ${quantityLabel(supportDeadlineOccurrences, 'ocorrência de prazo vencido', 'ocorrências de prazo vencido')}`
                        : 'Nenhum chamado crítico ativo ou prazo vencido no consolidado'}
                    </strong>
                    <span>{quantityLabel(support.totals.active, 'chamado em andamento', 'chamados em andamento')} · {quantityLabel(support.totals.responseBreaches, 'atraso de resposta', 'atrasos de resposta')} · {quantityLabel(support.totals.solutionBreaches, 'atraso de solução', 'atrasos de solução')}.</span>
                    {canOpenSupport ? <div className="row-actions"><Button type="button" variant="ghost" onClick={() => onNavigate('suporte')}>Ver chamados</Button></div> : null}
                  </> : <StateMessage title="Sem indicadores de suporte" message="Não há consolidado disponível para a Rede." />}
            </article>
          ) : null}
        </div>
      </section>

      {canMonitoring ? (
        <section aria-labelledby="overview-results-title">
          <h2 id="overview-results-title">Resultado da Rede</h2>
          {monitoringLoading ? <StateMessage title="Atualizando indicadores" message="Aguarde enquanto o resultado da Rede é consolidado." />
            : monitoringError ? <StateMessage kind="error" title="Indicadores indisponíveis" message="Os demais módulos continuam disponíveis pelos acessos rápidos." />
              : pedagogicalMetric ? <div className="metric-grid">
                <MetricCard label="Cobertura" value={formatPercent(pedagogicalMetric.coveragePercent)} detail={pedagogicalMetric.sourceLabel} />
                <MetricCard label="Aproveitamento observado" value={formatPercent(pedagogicalMetric.achievementPercent)} detail="Regra calculada pela fonte de origem" />
                <MetricCard label="Estudantes com resultado" value={pedagogicalMetric.studentsWithResults.toLocaleString('pt-BR')} detail={`${quantityLabel(pedagogicalMetric.totalStudents, 'estudante', 'estudantes')} na base da fonte`} />
                <MetricCard label="Avaliações com resultado" value={pedagogicalMetric.assessmentsWithResults.toLocaleString('pt-BR')} detail="Avaliações consideradas pela fonte selecionada" />
              </div> : <StateMessage title="Sem base pedagógica" message="Ainda não existem resultados suficientes para apresentar indicadores da Rede sem criar valores artificiais." />}
        </section>
      ) : null}

      <section className="content-panel" aria-labelledby="overview-shortcuts-title">
        <h2 id="overview-shortcuts-title">Acessos rápidos</h2>
        <div className="row-actions">
          {canMonitoring ? <Button type="button" variant="ghost" onClick={() => onNavigate('monitoramento')}>Monitoramento</Button> : null}
          {canAssessment ? <Button type="button" variant="ghost" onClick={() => onNavigate('avaliacoes')}>Avaliações em Rede</Button> : null}
          {context.permissions.includes('REPORT_READ') ? <Button type="button" variant="ghost" onClick={() => onNavigate('relatorios')}>Relatórios</Button> : null}
          {context.permissions.some((permission) => permission.startsWith('SCHOOL_')) ? <Button type="button" variant="ghost" onClick={() => onNavigate('secretaria')}>Secretaria Escolar</Button> : null}
          {canOpenSupport ? <Button type="button" variant="ghost" onClick={() => onNavigate('suporte')}>Suporte</Button> : null}
        </div>
      </section>
    </main>
  );
}
