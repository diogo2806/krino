import { Megaphone, MessageSquarePlus } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { FilterBar } from '../filter/FilterBar';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from '../workspace/types';
import { AnnouncementDialog } from './AnnouncementDialog';
import { StaffConversationDialog } from './StaffConversationDialog';
import type { Announcement, Conversation, FamilyClass, FamilySchool, FamilyStudentOption } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type Tab = 'messages' | 'announcements';

const manualSections = [
  { title: 'Finalidade', content: 'Centralizar a comunicação da escola com responsáveis legalmente vinculados aos estudantes, por conversas rastreáveis e comunicados segmentados.' },
  { title: 'Campos e filtros', content: 'Unidade escolar define o escopo. Buscar localiza conversas por estudante, responsável ou assunto. Comunicados podem ser destinados à escola inteira, uma turma ou um estudante.' },
  { title: 'Botões e ações', content: 'Nova conversa inicia contato com um responsável já vinculado. Abrir conversa permite responder. Novo comunicado publica uma informação para o público escolhido. Desativar comunicado interrompe sua exibição no portal.' },
  { title: 'Regras', content: 'Somente responsáveis vinculados ao estudante podem participar das conversas. Escola, turma e estudante são validados no backend. O sistema não cria vínculos legais por meio desta tela.' },
  { title: 'Permissões', content: 'FAMILY_COMMUNICATION_READ permite consulta. FAMILY_COMMUNICATION_WRITE permite iniciar/responder conversas, publicar e desativar comunicados no escopo autorizado.' },
  { title: 'Fluxos', content: 'Selecione a unidade, consulte ou inicie conversas e publique comunicados. Para criar vínculo de responsável, use Administração > Usuários e acessos > Vincular estudantes.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia ausência de unidades autorizadas, ausência de conversa/comunicado, erro, carregamento e acesso negado. Nenhum studentId ou código técnico é mostrado ao operador.' },
];

export function FamilyCommunicationPage({ context, onUnauthorized }: Props) {
  const [schools, setSchools] = useState<FamilySchool[]>([]); const [schoolId, setSchoolId] = useState(''); const [classes, setClasses] = useState<FamilyClass[]>([]); const [students, setStudents] = useState<FamilyStudentOption[]>([]); const [conversations, setConversations] = useState<Conversation[]>([]); const [announcements, setAnnouncements] = useState<Announcement[]>([]); const [search, setSearch] = useState(''); const [tab, setTab] = useState<Tab>('messages'); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [denied, setDenied] = useState(false); const [conversationOpen, setConversationOpen] = useState(false); const [conversation, setConversation] = useState<Conversation>(); const [announcementOpen, setAnnouncementOpen] = useState(false);

  const selectedSchool = schools.find((item) => item.id.toString() === schoolId);
  const schoolPermissions = selectedSchool ? context.schoolAccess.find((scope) => scope.schoolCode === selectedSchool.code)?.permissions ?? [] : [];
  const canWrite = context.networkPermissions.includes('FAMILY_COMMUNICATION_WRITE') || schoolPermissions.includes('FAMILY_COMMUNICATION_WRITE');

  const loadSchools = useCallback(async () => {
    try { const next = await apiRequest<FamilySchool[]>('/family-communication/schools'); setSchools(next); setSchoolId((current) => current && next.some((item) => item.id.toString() === current) ? current : next[0]?.id.toString() ?? ''); setDenied(false); }
    catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar as unidades autorizadas.'); }
    finally { setLoading(false); }
  }, [onUnauthorized]);

  const loadSchoolData = useCallback(async () => {
    if (!schoolId) return;
    setLoading(true); setError('');
    try {
      const [nextClasses, nextStudents, nextConversations, nextAnnouncements] = await Promise.all([
        apiRequest<FamilyClass[]>(`/family-communication/schools/${schoolId}/classes`),
        apiRequest<FamilyStudentOption[]>(`/family-communication/schools/${schoolId}/students`),
        apiRequest<Conversation[]>(`/family-communication/conversations?schoolId=${schoolId}${search.trim() ? `&search=${encodeURIComponent(search.trim())}` : ''}`),
        apiRequest<Announcement[]>(`/family-communication/announcements?schoolId=${schoolId}`),
      ]);
      setClasses(nextClasses); setStudents(nextStudents); setConversations(nextConversations); setAnnouncements(nextAnnouncements);
    } catch (exception) { if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; } if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; } setError(exception instanceof Error ? exception.message : 'Não foi possível carregar a comunicação com famílias.'); }
    finally { setLoading(false); }
  }, [schoolId, search, onUnauthorized]);

  useEffect(() => { void loadSchools(); }, [loadSchools]);
  useEffect(() => { if (schoolId) void loadSchoolData(); }, [schoolId, loadSchoolData]);

  const tabs = useMemo(() => [{ value: 'messages' as Tab, label: 'Mensagens' }, { value: 'announcements' as Tab, label: 'Comunicados' }], []);

  if (denied) return <main className="app-page"><PageHeader eyebrow="Família" title="Comunicação com Famílias" description="Mensagens e comunicados entre escola e responsáveis." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para consultar a comunicação com famílias." /></main>;

  return <main className="app-page"><PageHeader eyebrow="Família" title="Comunicação com Famílias" description="Mensagens rastreáveis e comunicados para responsáveis vinculados." manualSections={manualSections} actions={canWrite ? tab === 'messages' ? <Button type="button" variant="primary" onClick={() => { setConversation(undefined); setConversationOpen(true); }}><MessageSquarePlus aria-hidden="true" size={18} />Nova conversa</Button> : <Button type="button" variant="primary" onClick={() => setAnnouncementOpen(true)}><Megaphone aria-hidden="true" size={18} />Novo comunicado</Button> : undefined} />
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {schools.length ? <FilterBar><SelectField name="familySchool" label="Unidade escolar" value={schoolId} onChange={(event) => setSchoolId(event.target.value)} options={schools.map((item) => ({ value: item.id.toString(), label: item.name }))} />{tab === 'messages' ? <TextField name="familySearch" label="Buscar" placeholder="Estudante, responsável ou assunto" value={search} onChange={(event) => setSearch(event.target.value)} /> : null}</FilterBar> : null}
    {schools.length === 0 && !loading ? <StateMessage title="Nenhuma unidade autorizada" message="Sua conta não possui unidade escolar disponível para comunicação com famílias." /> : null}
    {schools.length ? <SegmentedTabs label="Seções da Comunicação com Famílias" tabs={tabs} value={tab} onChange={setTab} /> : null}
    {loading ? <StateMessage title="Carregando comunicação" message="Aguarde enquanto conversas e comunicados são consultados." /> : null}
    {!loading && tab === 'messages' ? <section className="family-panel"><h2>Mensagens</h2>{conversations.length === 0 ? <StateMessage title="Nenhuma conversa encontrada" message="Ajuste a busca ou inicie uma nova conversa com um responsável vinculado." /> : <div className="family-card-list">{conversations.map((item) => <button type="button" className="family-conversation-card" key={item.id} onClick={() => { setConversation(item); setConversationOpen(true); }}><strong>{item.studentName} · {item.guardianName}</strong><span>{item.subject}</span><p>{item.lastMessage}</p><small>{item.status === 'OPEN' ? 'Conversa aberta' : 'Conversa encerrada'} · {new Date(item.updatedAt).toLocaleString('pt-BR')}</small></button>)}</div>}</section> : null}
    {!loading && tab === 'announcements' ? <section className="family-panel"><h2>Comunicados</h2>{announcements.length === 0 ? <StateMessage title="Nenhum comunicado publicado" message="Ainda não existem comunicados para esta unidade escolar." /> : <div className="family-card-list">{announcements.map((item) => <article className="family-info-card" key={item.id}><div className="family-info-card__heading"><strong>{item.title}</strong><span className={item.active ? 'status-badge status-badge--active' : 'status-badge'}>{item.active ? 'Ativo' : 'Desativado'}</span></div><span>{item.audienceType === 'SCHOOL' ? 'Toda a escola' : item.audienceType === 'CLASS' ? `Turma: ${item.className}` : `Estudante: ${item.studentName}`}</span><p>{item.body}</p><small>{new Date(item.publishedAt).toLocaleString('pt-BR')}</small>{canWrite && item.active ? <Button type="button" variant="ghost" onClick={async () => { try { await apiRequest(`/family-communication/announcements/${item.id}`, { method: 'DELETE' }); await loadSchoolData(); } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível desativar o comunicado.'); } }}>Desativar comunicado</Button> : null}</article>)}</div>}</section> : null}
    <StaffConversationDialog open={conversationOpen} conversation={conversation} students={students} onClose={() => { setConversationOpen(false); setConversation(undefined); }} onSaved={loadSchoolData} />
    <AnnouncementDialog open={announcementOpen} schoolId={schoolId ? Number(schoolId) : undefined} classes={classes} students={students} onClose={() => setAnnouncementOpen(false)} onSaved={async () => { await loadSchoolData(); }} />
  </main>;
}
