import { MessageSquarePlus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { MetricCard } from '../chart/MetricCard';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from '../workspace/types';
import { FamilyConversationDialog } from './FamilyConversationDialog';
import { NewFamilyConversationDialog } from './NewFamilyConversationDialog';
import type { AccessNotification, Announcement, Conversation, LinkedStudent, ReportCard } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type Tab = 'report' | 'attendance' | 'messages' | 'announcements' | 'notifications';

const manualSections = [
  { title: 'Finalidade', content: 'Consultar a vida escolar dos estudantes legalmente vinculados à sua conta, incluindo boletim, frequência, mensagens, comunicados e notificações de entrada e saída.' },
  { title: 'Campos e filtros', content: 'Estudante define de quem serão exibidas as informações. Ano letivo e período filtram boletim e frequência. Quando houver mais de um estudante vinculado, altere a seleção sem sair da tela.' },
  { title: 'Botões e ações', content: 'Nova mensagem inicia uma conversa com a escola do estudante selecionado. Abrir conversa permite consultar o histórico e responder enquanto a conversa estiver aberta.' },
  { title: 'Regras', content: 'A conta só consulta estudantes vinculados pela Administração. Notas, frequência e notificações reutilizam dados já persistidos nos módulos acadêmicos e de entrada/saída. Nenhum identificador técnico é exibido.' },
  { title: 'Permissões', content: 'O acesso exige STUDENT_LINKED_READ e vínculo individual com o estudante. A validação é feita no backend em todas as consultas e mensagens.' },
  { title: 'Fluxos', content: 'Selecione o estudante, consulte boletim/frequência, leia comunicados e notificações e use Mensagens quando precisar falar com a escola.' },
  { title: 'Mensagens e estados', content: 'Sem vínculo, sem lançamentos, carregamento, erro e conversa encerrada possuem estados próprios. Quando ainda não houver notas ou frequência no período, a tela informa isso sem apresentar zero artificial.' },
];

export function FamilyPortalPage({ context, onUnauthorized }: Props) {
  const currentYear = new Date().getFullYear();
  const [students, setStudents] = useState<LinkedStudent[]>([]); const [studentId, setStudentId] = useState(''); const [year, setYear] = useState(currentYear.toString()); const [period, setPeriod] = useState('1'); const [tab, setTab] = useState<Tab>('report');
  const [report, setReport] = useState<ReportCard>(); const [notifications, setNotifications] = useState<AccessNotification[]>([]); const [announcements, setAnnouncements] = useState<Announcement[]>([]); const [conversations, setConversations] = useState<Conversation[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [newOpen, setNewOpen] = useState(false); const [conversation, setConversation] = useState<Conversation>();

  const selectedStudent = students.find((item) => item.id.toString() === studentId);
  const loadStudents = useCallback(async () => {
    try {
      const next = await apiRequest<LinkedStudent[]>('/family-portal/students'); setStudents(next); setStudentId((current) => current && next.some((item) => item.id.toString() === current) ? current : next[0]?.id.toString() ?? '');
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os estudantes vinculados.'); }
    finally { setLoading(false); }
  }, [onUnauthorized]);

  const loadStudentData = useCallback(async () => {
    if (!studentId) return;
    setLoading(true); setError('');
    try {
      const [nextReport, nextNotifications, nextAnnouncements, nextConversations] = await Promise.all([
        apiRequest<ReportCard>(`/family-portal/students/${studentId}/report-card?year=${year}&period=${period}`),
        apiRequest<AccessNotification[]>(`/family-portal/students/${studentId}/notifications`),
        apiRequest<Announcement[]>(`/family-portal/students/${studentId}/announcements`),
        apiRequest<Conversation[]>(`/family-portal/students/${studentId}/conversations`),
      ]);
      setReport(nextReport); setNotifications(nextNotifications); setAnnouncements(nextAnnouncements); setConversations(nextConversations);
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar as informações do estudante.'); }
    finally { setLoading(false); }
  }, [studentId, year, period, onUnauthorized]);

  useEffect(() => { void loadStudents(); }, [loadStudents]);
  useEffect(() => { if (studentId) void loadStudentData(); }, [studentId, year, period, loadStudentData]);

  const tabs = useMemo(() => [
    { value: 'report' as Tab, label: 'Boletim' }, { value: 'attendance' as Tab, label: 'Frequência' }, { value: 'messages' as Tab, label: 'Mensagens' }, { value: 'announcements' as Tab, label: 'Comunicados' }, { value: 'notifications' as Tab, label: 'Notificações' },
  ], []);

  return <main className="app-page"><PageHeader eyebrow="Família" title="Portal do Responsável" description={selectedStudent ? `Estudante selecionado: ${selectedStudent.name}.` : 'Acompanhe os estudantes vinculados à sua conta.'} manualSections={manualSections} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {students.length > 0 ? <FilterBar><SelectField name="familyStudent" label="Estudante" value={studentId} onChange={(event) => setStudentId(event.target.value)} options={students.map((item) => ({ value: item.id.toString(), label: `${item.name}${item.className ? ` · ${item.className}` : ''}` }))} /><SelectField name="familyYear" label="Ano letivo" value={year} onChange={(event) => setYear(event.target.value)} options={[currentYear - 2, currentYear - 1, currentYear, currentYear + 1].map((value) => ({ value: value.toString(), label: value.toString() }))} /><SelectField name="familyPeriod" label="Período" value={period} onChange={(event) => setPeriod(event.target.value)} options={[1,2,3,4].map((value) => ({ value: value.toString(), label: `${value}º período` }))} /></FilterBar> : null}
    {students.length === 0 && !loading ? <StateMessage title="Nenhum estudante vinculado" message="Sua conta ainda não possui estudante autorizado. Solicite à Administração da escola a validação do vínculo legal." /> : null}
    {students.length > 0 ? <SegmentedTabs label="Seções do Portal do Responsável" tabs={tabs} value={tab} onChange={setTab} /> : null}
    {loading ? <StateMessage title="Carregando informações" message="Aguarde enquanto os dados do estudante são consultados." /> : null}
    {!loading && studentId && tab === 'report' ? <section className="family-panel"><h2>Boletim</h2>{report && (report.components.length > 0 || report.assessments.length > 0) ? <><div className="family-result-grid">{report.components.map((item) => <article className="family-result-card" key={item.componentId}><strong>{item.componentName}</strong><span>Nota consolidada: {item.grade == null ? 'Ainda não informada' : item.grade.toLocaleString('pt-BR')}</span><span>Faltas: {item.absences}</span></article>)}</div>{report.assessments.length ? <div className="family-assessment-list">{report.assessments.map((item, index) => <article key={`${item.title}-${index}`}><strong>{item.componentName} · {item.title}</strong><span>{item.score == null ? 'Sem nota' : `${item.score.toLocaleString('pt-BR')}${item.maxScore != null ? ` de ${item.maxScore.toLocaleString('pt-BR')}` : ''}`}</span><small>{new Date(item.assessmentDate).toLocaleDateString('pt-BR')}</small></article>)}</div> : null}</> : <StateMessage title="Sem lançamentos no período" message="Ainda não existem notas ou avaliações disponíveis para o ano e período selecionados." />}</section> : null}
    {!loading && studentId && tab === 'attendance' ? <section className="family-panel"><h2>Frequência</h2>{report && report.totalClasses > 0 ? <><div className="metric-grid"><MetricCard label="Aulas consideradas" value={report.totalClasses.toLocaleString('pt-BR')} detail="Resultados consolidados do período" /><MetricCard label="Faltas" value={report.totalAbsences.toLocaleString('pt-BR')} detail="Faltas consolidadas" /><MetricCard label="Frequência" value={report.attendancePercent == null ? 'Sem base' : `${report.attendancePercent.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`} detail="(aulas - faltas) ÷ aulas × 100" /></div><div className="family-result-grid">{report.components.map((item) => <article className="family-result-card" key={item.componentId}><strong>{item.componentName}</strong><span>{item.attendancePercent == null ? 'Sem base de frequência' : `${item.attendancePercent.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}% de frequência`}</span><span>{item.absences} falta(s) em {item.classesCount} aula(s)</span></article>)}</div></> : <StateMessage title="Sem frequência consolidada" message="Ainda não existem aulas/faltas consolidadas para o período selecionado." />}</section> : null}
    {!loading && studentId && tab === 'messages' ? <section className="family-panel"><div className="family-panel__heading"><div><h2>Mensagens</h2><p className="muted">Converse com a escola sobre o estudante selecionado.</p></div><Button type="button" variant="primary" onClick={() => setNewOpen(true)}><MessageSquarePlus aria-hidden="true" size={18} />Nova mensagem</Button></div>{conversations.length === 0 ? <StateMessage title="Nenhuma conversa" message="Você ainda não possui conversas para este estudante." /> : <div className="family-card-list">{conversations.map((item) => <button className="family-conversation-card" type="button" key={item.id} onClick={() => setConversation(item)}><strong>{item.subject}</strong><span>{item.schoolName}</span><p>{item.lastMessage}</p><small>{item.status === 'OPEN' ? 'Conversa aberta' : 'Conversa encerrada'} · {new Date(item.updatedAt).toLocaleString('pt-BR')}</small></button>)}</div>}</section> : null}
    {!loading && studentId && tab === 'announcements' ? <section className="family-panel"><h2>Comunicados</h2>{announcements.length === 0 ? <StateMessage title="Nenhum comunicado" message="Não existem comunicados disponíveis para este estudante no momento." /> : <div className="family-card-list">{announcements.map((item) => <article className="family-info-card" key={item.id}><strong>{item.title}</strong><span>{item.schoolName}</span><p>{item.body}</p><small>{new Date(item.publishedAt).toLocaleString('pt-BR')}</small></article>)}</div>}</section> : null}
    {!loading && studentId && tab === 'notifications' ? <section className="family-panel"><h2>Notificações</h2>{notifications.length === 0 ? <StateMessage title="Nenhuma notificação" message="Ainda não existem notificações de entrada ou saída disponíveis para este estudante." /> : <div className="family-card-list">{notifications.map((item) => <article className="family-info-card" key={item.id}><strong>{item.eventType === 'ENTRY' ? 'Entrada registrada' : 'Saída registrada'}</strong><p>{item.message}</p><small>Evento em {new Date(item.capturedAt).toLocaleString('pt-BR')}</small></article>)}</div>}</section> : null}
    <NewFamilyConversationDialog open={newOpen} student={selectedStudent} onClose={() => setNewOpen(false)} onSaved={async (item) => { await loadStudentData(); setConversation(item); }} />
    <FamilyConversationDialog open={Boolean(conversation)} conversation={conversation} onClose={() => { setConversation(undefined); void loadStudentData(); }} />
  </main>;
}
