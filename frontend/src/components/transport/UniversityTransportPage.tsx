import { BadgeCheck, Download, Send, Settings2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { ApiError, apiBlob, apiRequest } from '../../shared/api/client';
import { Button } from '../button/Button';
import { SelectField } from '../form/SelectField';
import { TextField } from '../form/TextField';
import { PageHeader } from '../layout/PageHeader';
import { SegmentedTabs } from '../navigation/SegmentedTabs';
import { StateMessage } from '../state/StateMessage';
import type { AccessContext } from '../workspace/types';
import { TransportCardPanel } from './TransportCardPanel';
import { TransportDecisionDialog } from './TransportDecisionDialog';
import { TransportDocumentUploader } from './TransportDocumentUploader';
import { TransportRequestForm } from './TransportRequestForm';
import type { CardArt, TransportCard, TransportRequest, TransportRequestInput, TransportStatus } from './types';

type Props = { context: AccessContext; onUnauthorized: () => void; };
type Tab = 'student' | 'review';
type Decision = 'APPROVE' | 'ADJUST' | 'DENY';

const manualSections = [
  { title: 'Finalidade', content: 'Cadastrar e acompanhar solicitações de transporte intermunicipal para cursos profissionalizantes, técnicos ou universitários. A equipe autorizada da SEDUC analisa os pedidos e, após aprovação, a carteirinha pode ser emitida.' },
  { title: 'Campos', content: 'Nome completo, documento pessoal, data de nascimento, telefone, tipo de curso, curso, instituição e dias necessários identificam a solicitação. Foto e comprovante de matrícula são obrigatórios antes do envio.' },
  { title: 'Botões e filtros', content: 'Salvar solicitação grava o cadastro; Enviar solicitação encaminha para análise. Na análise, o filtro Status reduz a fila. Iniciar análise abre a etapa de decisão; depois disso, Aprovar, Solicitar ajuste e Negar registram o resultado. Configuração da arte define os textos e a cor de destaque da carteirinha.' },
  { title: 'Regras', content: 'O estudante só altera pedidos em rascunho ou quando houver ajuste solicitado. Foto e comprovante devem existir antes do envio e da aprovação. A SEDUC deve iniciar a análise antes de decidir. Negativa e solicitação de ajuste exigem motivo. A carteirinha só fica válida após aprovação, dentro da validade informada e com arte aprovada pela SEDUC.' },
  { title: 'Permissões', content: 'O estudante do transporte consulta e altera somente as próprias solicitações. A fila e as decisões exigem permissão municipal da SEDUC. A configuração da arte possui permissão municipal específica. Todas as autorizações também são verificadas no backend.' },
  { title: 'Fluxos', content: 'Estudante: salvar cadastro, enviar foto e comprovante, enviar solicitação, acompanhar a análise e corrigir quando solicitado. SEDUC: consultar fila, iniciar análise, conferir documentos, aprovar com validade, solicitar ajuste ou negar com motivo.' },
  { title: 'Mensagens e estados', content: 'A tela diferencia carregamento, ausência de solicitações, acesso não permitido, erro, rascunho, enviada, em análise, ajuste solicitado, aprovada e negada. Motivos de ajuste ou negativa ficam visíveis ao estudante.' },
];

const statusLabels: Record<TransportStatus, string> = {
  DRAFT: 'Rascunho', SUBMITTED: 'Enviada', UNDER_REVIEW: 'Em análise', ADJUSTMENT_REQUESTED: 'Ajuste solicitado', APPROVED: 'Aprovada', DENIED: 'Negada',
};

const statusOptions = [
  { value: '', label: 'Todos' },
  ...Object.entries(statusLabels).map(([value, label]) => ({ value, label })),
];

function courseTypeLabel(value: TransportRequest['courseType']) {
  return value === 'PROFESSIONALIZING' ? 'Profissionalizante' : value === 'TECHNICAL' ? 'Técnico' : 'Universitário';
}

export function UniversityTransportPage({ context, onUnauthorized }: Props) {
  const canSelf = context.permissions.includes('TRANSPORT_REQUEST_READ');
  const canSelfWrite = context.permissions.includes('TRANSPORT_REQUEST_WRITE');
  const canReview = context.networkPermissions.some((permission) => permission === 'TRANSPORT_REVIEW_READ' || permission === 'TRANSPORT_REVIEW_WRITE');
  const canReviewWrite = context.networkPermissions.includes('TRANSPORT_REVIEW_WRITE');
  const canArtWrite = context.networkPermissions.includes('TRANSPORT_CARD_ART_WRITE');
  const initialTab: Tab = canSelf ? 'student' : 'review';

  const [tab, setTab] = useState<Tab>(initialTab);
  const [requests, setRequests] = useState<TransportRequest[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [reviewStatus, setReviewStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [denied, setDenied] = useState(false);
  const [decision, setDecision] = useState<Decision>();
  const [card, setCard] = useState<TransportCard>();
  const [art, setArt] = useState<CardArt>();
  const [artName, setArtName] = useState('');
  const [artHeader, setArtHeader] = useState('');
  const [artFooter, setArtFooter] = useState('');
  const [artAccent, setArtAccent] = useState('#173B57');
  const [artApproved, setArtApproved] = useState(false);

  const selected = requests.find((item) => item.id === selectedId);

  const handleException = useCallback((exception: unknown, fallback: string) => {
    if (exception instanceof ApiError && exception.status === 401) { onUnauthorized(); return; }
    if (exception instanceof ApiError && exception.status === 403) { setDenied(true); return; }
    setError(exception instanceof Error ? exception.message : fallback);
  }, [onUnauthorized]);

  const loadStudent = useCallback(async () => {
    if (!canSelf) return;
    setLoading(true); setError('');
    try {
      const next = await apiRequest<TransportRequest[]>('/transport/requests');
      setRequests(next);
      setSelectedId((current) => current && next.some((item) => item.id === current) ? current : next[0]?.id);
      setDenied(false);
      const valid = next.some((item) => item.status === 'APPROVED' && item.validUntil && item.validUntil >= new Date().toISOString().slice(0, 10));
      if (valid) {
        try { setCard(await apiRequest<TransportCard>('/transport/card')); } catch { setCard(undefined); }
      } else setCard(undefined);
    } catch (exception) {
      handleException(exception, 'Não foi possível carregar suas solicitações de transporte.');
    } finally { setLoading(false); }
  }, [canSelf, handleException]);

  const loadReview = useCallback(async () => {
    if (!canReview) return;
    setLoading(true); setError('');
    try {
      const query = reviewStatus ? `?status=${encodeURIComponent(reviewStatus)}` : '';
      const next = await apiRequest<TransportRequest[]>(`/transport/admin/requests${query}`);
      setRequests(next);
      setSelectedId((current) => current && next.some((item) => item.id === current) ? current : next[0]?.id);
      const nextArt = await apiRequest<CardArt>('/transport/admin/card-art');
      setArt(nextArt); setArtName(nextArt.name); setArtHeader(nextArt.headerText); setArtFooter(nextArt.footerText ?? ''); setArtAccent(nextArt.accentColor); setArtApproved(nextArt.approved);
      setDenied(false);
    } catch (exception) {
      handleException(exception, 'Não foi possível carregar a fila de transporte.');
    } finally { setLoading(false); }
  }, [canReview, reviewStatus, handleException]);

  useEffect(() => { if (tab === 'student') void loadStudent(); else void loadReview(); }, [tab, loadStudent, loadReview]);

  const saveRequest = async (input: TransportRequestInput) => {
    setSaving(true); setError('');
    try {
      const editable = selected && (selected.status === 'DRAFT' || selected.status === 'ADJUSTMENT_REQUESTED');
      const saved = await apiRequest<TransportRequest>(editable ? `/transport/requests/${selected.id}` : '/transport/requests', {
        method: editable ? 'PUT' : 'POST', body: JSON.stringify(input),
      });
      await loadStudent();
      setSelectedId(saved.id);
    } catch (exception) { handleException(exception, 'Não foi possível salvar a solicitação.'); }
    finally { setSaving(false); }
  };

  const submitRequest = async () => {
    if (!selected) return;
    setSaving(true); setError('');
    try { await apiRequest(`/transport/requests/${selected.id}/submit`, { method: 'POST' }); await loadStudent(); }
    catch (exception) { handleException(exception, 'Não foi possível enviar a solicitação.'); }
    finally { setSaving(false); }
  };

  const startReview = async () => {
    if (!selected) return;
    setSaving(true); setError('');
    try { await apiRequest(`/transport/admin/requests/${selected.id}/start-review`, { method: 'POST' }); await loadReview(); }
    catch (exception) { handleException(exception, 'Não foi possível iniciar a análise.'); }
    finally { setSaving(false); }
  };

  const confirmDecision = async (payload: { reason?: string; validUntil?: string }) => {
    if (!selected || !decision) return;
    setSaving(true); setError('');
    const endpoint = decision === 'APPROVE' ? 'approve' : decision === 'ADJUST' ? 'request-adjustment' : 'deny';
    const body = decision === 'APPROVE' ? { validUntil: payload.validUntil } : { reason: payload.reason };
    try {
      await apiRequest(`/transport/admin/requests/${selected.id}/${endpoint}`, { method: 'POST', body: JSON.stringify(body) });
      setDecision(undefined); await loadReview();
      if (decision === 'APPROVE') setCard(await apiRequest<TransportCard>(`/transport/admin/requests/${selected.id}/card`));
    } catch (exception) { handleException(exception, 'Não foi possível registrar a decisão.'); }
    finally { setSaving(false); }
  };

  const saveArt = async (event: FormEvent) => {
    event.preventDefault(); setSaving(true); setError('');
    try {
      const next = await apiRequest<CardArt>('/transport/admin/card-art', { method: 'PUT', body: JSON.stringify({ name: artName, headerText: artHeader, footerText: artFooter, accentColor: artAccent, approved: artApproved }) });
      setArt(next);
    } catch (exception) { handleException(exception, 'Não foi possível salvar a arte da carteirinha.'); }
    finally { setSaving(false); }
  };

  const downloadDocument = async (type: 'PHOTO' | 'ENROLLMENT_PROOF') => {
    if (!selected) return;
    try {
      const blob = await apiBlob(`/transport/admin/requests/${selected.id}/documents/${type}`);
      const extension = blob.type === 'application/pdf' ? 'pdf' : blob.type === 'image/png' ? 'png' : blob.type === 'image/webp' ? 'webp' : 'jpg';
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a'); link.href = url; link.download = `${type === 'PHOTO' ? 'foto' : 'comprovante'}-${selected.id}.${extension}`; link.click(); URL.revokeObjectURL(url);
    } catch (exception) { handleException(exception, 'Não foi possível baixar o documento.'); }
  };

  const tabs = useMemo(() => {
    const result = [] as Array<{ value: Tab; label: string }>;
    if (canSelf) result.push({ value: 'student', label: 'Minhas solicitações' });
    if (canReview) result.push({ value: 'review', label: 'Solicitações da SEDUC' });
    return result;
  }, [canSelf, canReview]);

  if (!canSelf && !canReview) return <main className="app-page"><PageHeader eyebrow="Transporte" title="Transporte Universitário" description="Solicitações e carteirinhas do transporte intermunicipal." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta não possui permissão para acessar o módulo de Transporte Universitário." /></main>;
  if (denied) return <main className="app-page"><PageHeader eyebrow="Transporte" title="Transporte Universitário" description="Solicitações e carteirinhas do transporte intermunicipal." manualSections={manualSections} /><StateMessage title="Acesso não permitido" message="Sua conta perdeu a autorização necessária para esta operação." /></main>;

  return <main className="app-page transport-page">
    <PageHeader eyebrow="Transporte" title="Transporte Universitário" description={tab === 'student' ? `Solicitações de ${context.displayName}.` : 'Análise de solicitações pela Secretaria de Educação.'} manualSections={manualSections} />
    {tabs.length > 1 ? <SegmentedTabs label="Áreas do Transporte Universitário" tabs={tabs} value={tab} onChange={(value) => { setTab(value); setRequests([]); setSelectedId(undefined); setCard(undefined); setError(''); }} /> : null}
    {error ? <StateMessage kind="error" title="Não foi possível concluir a operação" message={error} /> : null}
    {loading ? <StateMessage title="Carregando transporte" message="Aguarde enquanto as informações são consultadas." /> : null}

    {!loading && tab === 'student' ? <>
      {requests.length > 1 ? <SelectField name="transportOwnRequest" label="Solicitação" value={selectedId?.toString() ?? ''} onChange={(event) => setSelectedId(Number(event.target.value))} options={requests.map((item) => ({ value: item.id.toString(), label: `#${item.id} · ${statusLabels[item.status]} · ${item.courseName}` }))} /> : null}
      {!selected && canSelfWrite ? <section className="transport-panel"><div className="transport-section-heading"><div><h2>Nova solicitação</h2><p className="muted">Preencha o cadastro para iniciar o pedido de transporte.</p></div></div><TransportRequestForm defaultName={context.displayName} saving={saving} onSave={saveRequest} /></section> : null}
      {selected ? <>
        <section className="transport-status-panel"><div><span className={`status-badge status-badge--${selected.status.toLowerCase()}`}>{statusLabels[selected.status]}</span><h2>{selected.courseName}</h2><p>{selected.institutionName} · {courseTypeLabel(selected.courseType)}</p></div>{selected.reviewReason ? <div className="transport-reason"><strong>{selected.status === 'DENIED' ? 'Motivo da negativa' : 'Ajuste solicitado'}</strong><p>{selected.reviewReason}</p></div> : null}</section>
        {canSelfWrite && (selected.status === 'DRAFT' || selected.status === 'ADJUSTMENT_REQUESTED') ? <section className="transport-panel"><div className="transport-section-heading"><div><h2>Dados da solicitação</h2><p className="muted">Revise os dados antes de enviar para a SEDUC.</p></div></div><TransportRequestForm request={selected} defaultName={context.displayName} saving={saving} onSave={saveRequest} /><TransportDocumentUploader requestId={selected.id} hasPhoto={selected.hasPhoto} hasEnrollmentProof={selected.hasEnrollmentProof} onUploaded={loadStudent} /><div className="transport-submit"><Button type="button" variant="primary" onClick={() => void submitRequest()} disabled={saving || !selected.hasPhoto || !selected.hasEnrollmentProof}><Send aria-hidden="true" size={18} />Enviar solicitação</Button></div></section> : <TransportRequestSummary request={selected} />}
        <TransportHistory request={selected} />
      </> : !canSelfWrite ? <StateMessage title="Nenhuma solicitação" message="Não existem solicitações disponíveis para sua conta." /> : null}
      {card ? <TransportCardPanel card={card} /> : null}
    </> : null}

    {!loading && tab === 'review' ? <>
      <div className="transport-review-toolbar"><SelectField name="transportStatusFilter" label="Status" value={reviewStatus} onChange={(event) => setReviewStatus(event.target.value)} options={statusOptions} /></div>
      {requests.length === 0 ? <StateMessage title="Nenhuma solicitação na fila" message="Não existem solicitações para o filtro selecionado." /> : <div className="transport-review-layout"><aside className="transport-request-list" aria-label="Solicitações de transporte">{requests.map((item) => <button className={selectedId === item.id ? 'transport-request-list__item transport-request-list__item--active' : 'transport-request-list__item'} type="button" key={item.id} onClick={() => { setSelectedId(item.id); setCard(undefined); }}><strong>{item.fullName}</strong><span>{item.courseName}</span><small>{statusLabels[item.status]}</small></button>)}</aside>{selected ? <section className="transport-panel transport-review-detail"><div className="transport-section-heading"><div><span className={`status-badge status-badge--${selected.status.toLowerCase()}`}>{statusLabels[selected.status]}</span><h2>{selected.fullName}</h2><p className="muted">{selected.courseName} · {selected.institutionName}</p></div></div><TransportRequestSummary request={selected} />
        <div className="transport-document-actions"><Button type="button" onClick={() => void downloadDocument('PHOTO')} disabled={!selected.hasPhoto}><Download aria-hidden="true" size={18} />Ver foto</Button><Button type="button" onClick={() => void downloadDocument('ENROLLMENT_PROOF')} disabled={!selected.hasEnrollmentProof}><Download aria-hidden="true" size={18} />Ver comprovante</Button></div>
        {canReviewWrite && selected.status === 'SUBMITTED' ? <div className="transport-actions"><Button type="button" variant="primary" onClick={() => void startReview()} disabled={saving}>Iniciar análise</Button></div> : null}
        {canReviewWrite && selected.status === 'UNDER_REVIEW' ? <div className="transport-actions"><Button type="button" variant="primary" onClick={() => setDecision('APPROVE')}><BadgeCheck aria-hidden="true" size={18} />Aprovar</Button><Button type="button" onClick={() => setDecision('ADJUST')}>Solicitar ajuste</Button><Button type="button" variant="danger" onClick={() => setDecision('DENY')}>Negar</Button></div> : null}
        {selected.status === 'APPROVED' ? <Button type="button" onClick={async () => { try { setCard(await apiRequest<TransportCard>(`/transport/admin/requests/${selected.id}/card`)); } catch (exception) { handleException(exception, 'Não foi possível carregar a carteirinha.'); } }}>Visualizar carteirinha</Button> : null}
        <TransportHistory request={selected} /></section> : null}</div>}
      {card ? <TransportCardPanel card={card} /> : null}
      {canArtWrite && art ? <section className="transport-panel"><div className="transport-section-heading"><div><h2>Arte da carteirinha</h2><p className="muted">Parametrize a identificação visual ativa e marque a arte como aprovada quando estiver pronta para emissão.</p></div><Settings2 aria-hidden="true" size={22} /></div><form className="form-stack" onSubmit={saveArt}><div className="form-grid"><TextField name="transportArtName" label="Nome da arte" value={artName} onChange={(event) => setArtName(event.target.value)} required maxLength={120} /><TextField name="transportArtHeader" label="Título da carteirinha" value={artHeader} onChange={(event) => setArtHeader(event.target.value)} required maxLength={180} /><TextField name="transportArtFooter" label="Rodapé" value={artFooter} onChange={(event) => setArtFooter(event.target.value)} maxLength={300} /><TextField name="transportArtAccent" label="Cor de destaque" type="color" value={artAccent} onChange={(event) => setArtAccent(event.target.value.toUpperCase())} required /></div><label className="check-field"><input type="checkbox" checked={artApproved} onChange={(event) => setArtApproved(event.target.checked)} /><span>Arte aprovada pela SEDUC</span></label><div className="transport-actions"><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Salvando...' : 'Salvar arte'}</Button></div></form></section> : null}
    </> : null}

    <TransportDecisionDialog open={Boolean(decision)} decision={decision ?? 'APPROVE'} saving={saving} onClose={() => setDecision(undefined)} onConfirm={confirmDecision} />
  </main>;
}

function TransportRequestSummary({ request }: { request: TransportRequest }) {
  return <div className="transport-summary-grid">
    <div><span>Nome</span><strong>{request.fullName}</strong></div><div><span>Documento pessoal</span><strong>{request.personalDocument}</strong></div><div><span>Data de nascimento</span><strong>{new Date(`${request.birthDate}T12:00:00`).toLocaleDateString('pt-BR')}</strong></div><div><span>Telefone</span><strong>{request.phone || 'Não informado'}</strong></div><div><span>Tipo de curso</span><strong>{courseTypeLabel(request.courseType)}</strong></div><div><span>Curso</span><strong>{request.courseName}</strong></div><div><span>Instituição</span><strong>{request.institutionName}</strong></div><div><span>Dias solicitados</span><strong>{request.days.map((day) => ({ MONDAY: 'Seg', TUESDAY: 'Ter', WEDNESDAY: 'Qua', THURSDAY: 'Qui', FRIDAY: 'Sex', SATURDAY: 'Sáb', SUNDAY: 'Dom' }[day])).join(', ')}</strong></div><div><span>Foto</span><strong>{request.hasPhoto ? 'Enviada' : 'Pendente'}</strong></div><div><span>Comprovante</span><strong>{request.hasEnrollmentProof ? 'Enviado' : 'Pendente'}</strong></div>{request.validUntil ? <div><span>Validade</span><strong>{new Date(`${request.validUntil}T12:00:00`).toLocaleDateString('pt-BR')}</strong></div> : null}
  </div>;
}

function TransportHistory({ request }: { request: TransportRequest }) {
  return <section className="transport-history" aria-labelledby={`transport-history-${request.id}`}><h2 id={`transport-history-${request.id}`}>Histórico da solicitação</h2>{request.history.length === 0 ? <StateMessage title="Sem histórico" message="Nenhuma movimentação foi registrada para esta solicitação." /> : <ol>{request.history.map((item, index) => <li key={`${item.createdAt}-${index}`}><div><strong>{statusLabels[item.status]}</strong><span>{new Date(item.createdAt).toLocaleString('pt-BR')} · {item.actorName}</span></div>{item.reason ? <p>{item.reason}</p> : null}</li>)}</ol>}</section>;
}
