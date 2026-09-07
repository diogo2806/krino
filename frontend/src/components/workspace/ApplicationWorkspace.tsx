import { Activity, BarChart3, BookOpen, Bus, ClipboardCheck, LifeBuoy, MessagesSquare, ScanLine, School, ShieldCheck, UsersRound } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { AccessControlPage } from '../access-control/AccessControlPage';
import { AdminWorkspace } from '../admin/AdminWorkspace';
import { NetworkAssessmentPage } from '../assessment/NetworkAssessmentPage';
import { Button } from '../button/Button';
import { DiaryPage } from '../diario/DiaryPage';
import { FamilyCommunicationPage } from '../family/FamilyCommunicationPage';
import { FamilyPortalPage } from '../family/FamilyPortalPage';
import { ApplicationShell, type ApplicationShellNavigationItem } from '../layout/ApplicationShell';
import { MonitoringPage } from '../monitoring/MonitoringPage';
import { ReportsPage } from '../report/ReportsPage';
import { SecretariaEscolarPage } from '../secretaria/SecretariaEscolarPage';
import { StateMessage } from '../state/StateMessage';
import { SupportPage } from '../support/SupportPage';
import { UniversityTransportPage } from '../transport/UniversityTransportPage';
import type { AccessContext } from './types';

type ApplicationWorkspaceProps = { onLogout: () => void; };
type Module = 'secretaria' | 'diario' | 'monitoramento' | 'avaliacoes' | 'relatorios' | 'acesso' | 'familias' | 'portal-responsavel' | 'transporte' | 'suporte' | 'admin';

function hasTransportAccess(context: AccessContext) {
  return context.permissions.some((permission) => permission.startsWith('TRANSPORT_REQUEST_'))
    || context.networkPermissions.some((permission) => permission.startsWith('TRANSPORT_REVIEW_') || permission === 'TRANSPORT_CARD_ART_WRITE');
}

function hasAdminAccess(context: AccessContext) {
  return context.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ', 'AUDIT_READ', 'DATA_EXPORT'].includes(permission));
}

export function ApplicationWorkspace({ onLogout }: ApplicationWorkspaceProps) {
  const [context, setContext] = useState<AccessContext>();
  const [module, setModule] = useState<Module>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadContext = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const next = await apiRequest<AccessContext>('/auth/access-context');
      setContext(next);
      const canSecretaria = next.permissions.some((permission) => permission.startsWith('SCHOOL_'));
      const canDiary = next.permissions.some((permission) => permission.startsWith('DIARY_'));
      const canMonitoring = next.permissions.some((permission) => permission.startsWith('MONITORING_'));
      const canAssessment = next.permissions.some((permission) => permission.startsWith('ASSESSMENT_'));
      const canReports = next.permissions.includes('REPORT_READ');
      const canAccessControl = next.permissions.some((permission) => permission.startsWith('ACCESS_'));
      const canFamilyCommunication = next.permissions.some((permission) => permission.startsWith('FAMILY_COMMUNICATION_'));
      const canFamilyPortal = next.permissions.includes('STUDENT_LINKED_READ');
      const canTransport = hasTransportAccess(next);
      const canSupport = next.permissions.includes('SUPPORT_TICKET_READ');
      const canAdmin = hasAdminAccess(next);
      const valid = (current?: Module) => current && ((current === 'secretaria' && canSecretaria) || (current === 'diario' && canDiary) || (current === 'monitoramento' && canMonitoring) || (current === 'avaliacoes' && canAssessment) || (current === 'relatorios' && canReports) || (current === 'acesso' && canAccessControl) || (current === 'familias' && canFamilyCommunication) || (current === 'portal-responsavel' && canFamilyPortal) || (current === 'transporte' && canTransport) || (current === 'suporte' && canSupport) || (current === 'admin' && canAdmin));
      const preferred: Module | undefined = canFamilyPortal ? 'portal-responsavel' : next.permissions.includes('TRANSPORT_REQUEST_READ') ? 'transporte' : next.permissions.includes('DIARY_EDIT') ? 'diario' : next.permissions.includes('ACCESS_CONTROL_WRITE') ? 'acesso' : canSecretaria ? 'secretaria' : canDiary ? 'diario' : canMonitoring ? 'monitoramento' : canAssessment ? 'avaliacoes' : canReports ? 'relatorios' : canAccessControl ? 'acesso' : canFamilyCommunication ? 'familias' : canTransport ? 'transporte' : canSupport ? 'suporte' : canAdmin ? 'admin' : undefined;
      setModule((current) => valid(current) ? current : preferred);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onLogout(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar seu contexto de acesso.');
    } finally { setLoading(false); }
  }, [onLogout]);

  useEffect(() => { void loadContext(); }, [loadContext]);

  if (loading) return <main className="app-page"><StateMessage title="Carregando seu acesso" message="Aguarde enquanto as permissões são verificadas." /></main>;
  if (error) return <main className="app-page"><StateMessage kind="error" title="Não foi possível abrir o KRINO" message={error} /><Button type="button" onClick={() => void loadContext()}>Tentar novamente</Button></main>;
  if (!context || !module) return <main className="app-page"><StateMessage title="Nenhum módulo disponível" message="Sua conta está ativa, mas ainda não possui permissão para um módulo do sistema." /><Button type="button" variant="ghost" onClick={onLogout}>Sair</Button></main>;

  const canSecretaria = context.permissions.some((permission) => permission.startsWith('SCHOOL_'));
  const canDiary = context.permissions.some((permission) => permission.startsWith('DIARY_'));
  const canMonitoring = context.permissions.some((permission) => permission.startsWith('MONITORING_'));
  const canAssessment = context.permissions.some((permission) => permission.startsWith('ASSESSMENT_'));
  const canReports = context.permissions.includes('REPORT_READ');
  const canAccessControl = context.permissions.some((permission) => permission.startsWith('ACCESS_'));
  const canFamilyCommunication = context.permissions.some((permission) => permission.startsWith('FAMILY_COMMUNICATION_'));
  const canFamilyPortal = context.permissions.includes('STUDENT_LINKED_READ');
  const canTransport = hasTransportAccess(context);
  const canSupport = context.permissions.includes('SUPPORT_TICKET_READ');
  const canAdmin = hasAdminAccess(context);

  const navigationItems: ApplicationShellNavigationItem[] = [];
  if (canSecretaria) navigationItems.push({ id: 'secretaria', label: 'Secretaria Escolar', icon: <School aria-hidden="true" size={18} />, active: module === 'secretaria', onSelect: () => setModule('secretaria') });
  if (canDiary) navigationItems.push({ id: 'diario', label: 'Diário de Classe', icon: <BookOpen aria-hidden="true" size={18} />, active: module === 'diario', onSelect: () => setModule('diario') });
  if (canMonitoring) navigationItems.push({ id: 'monitoramento', label: 'Monitoramento', icon: <Activity aria-hidden="true" size={18} />, active: module === 'monitoramento', onSelect: () => setModule('monitoramento') });
  if (canAssessment) navigationItems.push({ id: 'avaliacoes', label: 'Avaliações em Rede', icon: <ClipboardCheck aria-hidden="true" size={18} />, active: module === 'avaliacoes', onSelect: () => setModule('avaliacoes') });
  if (canReports) navigationItems.push({ id: 'relatorios', label: 'Relatórios e Indicadores', icon: <BarChart3 aria-hidden="true" size={18} />, active: module === 'relatorios', onSelect: () => setModule('relatorios') });
  if (canAccessControl) navigationItems.push({ id: 'acesso', label: 'Entrada e Saída', icon: <ScanLine aria-hidden="true" size={18} />, active: module === 'acesso', onSelect: () => setModule('acesso') });
  if (canFamilyCommunication) navigationItems.push({ id: 'familias', label: 'Comunicação com Famílias', icon: <MessagesSquare aria-hidden="true" size={18} />, active: module === 'familias', onSelect: () => setModule('familias') });
  if (canFamilyPortal) navigationItems.push({ id: 'portal-responsavel', label: 'Portal do Responsável', icon: <UsersRound aria-hidden="true" size={18} />, active: module === 'portal-responsavel', onSelect: () => setModule('portal-responsavel') });
  if (canTransport) navigationItems.push({ id: 'transporte', label: 'Transporte Universitário', icon: <Bus aria-hidden="true" size={18} />, active: module === 'transporte', onSelect: () => setModule('transporte') });
  if (canSupport) navigationItems.push({ id: 'suporte', label: 'Suporte e Chamados', icon: <LifeBuoy aria-hidden="true" size={18} />, active: module === 'suporte', onSelect: () => setModule('suporte') });
  if (canAdmin) navigationItems.push({ id: 'admin', label: 'Administração', icon: <ShieldCheck aria-hidden="true" size={18} />, active: module === 'admin', onSelect: () => setModule('admin') });

  const activeContext = navigationItems.find((item) => item.active)?.label ?? 'Módulos do sistema';

  return (
    <ApplicationShell
      contextLabel={activeContext}
      userName={context.displayName || context.username}
      navigationItems={navigationItems}
      onLogout={onLogout}
    >
      {module === 'secretaria' ? <SecretariaEscolarPage context={context} onUnauthorized={onLogout} />
        : module === 'diario' ? <DiaryPage context={context} onUnauthorized={onLogout} />
        : module === 'monitoramento' ? <MonitoringPage context={context} onUnauthorized={onLogout} />
        : module === 'avaliacoes' ? <NetworkAssessmentPage context={context} onUnauthorized={onLogout} />
        : module === 'relatorios' ? <ReportsPage context={context} onUnauthorized={onLogout} />
        : module === 'acesso' ? <AccessControlPage context={context} onUnauthorized={onLogout} />
        : module === 'familias' ? <FamilyCommunicationPage context={context} onUnauthorized={onLogout} />
        : module === 'portal-responsavel' ? <FamilyPortalPage context={context} onUnauthorized={onLogout} />
        : module === 'transporte' ? <UniversityTransportPage context={context} onUnauthorized={onLogout} />
        : module === 'suporte' ? <SupportPage context={context} onUnauthorized={onLogout} />
        : <AdminWorkspace context={context} onLogout={onLogout} />}
    </ApplicationShell>
  );
}
