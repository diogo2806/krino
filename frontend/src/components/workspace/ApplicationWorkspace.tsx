import { Activity, BookOpen, Bus, LogOut, MessagesSquare, ScanLine, School, ShieldCheck, UsersRound } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, apiRequest } from '../../shared/api/client';
import { AccessControlPage } from '../access-control/AccessControlPage';
import { UsersAccessPage } from '../admin/UsersAccessPage';
import { Button } from '../button/Button';
import { DiaryPage } from '../diario/DiaryPage';
import { FamilyCommunicationPage } from '../family/FamilyCommunicationPage';
import { FamilyPortalPage } from '../family/FamilyPortalPage';
import { MonitoringPage } from '../monitoring/MonitoringPage';
import { SecretariaEscolarPage } from '../secretaria/SecretariaEscolarPage';
import { StateMessage } from '../state/StateMessage';
import { UniversityTransportPage } from '../transport/UniversityTransportPage';
import type { AccessContext } from './types';

type ApplicationWorkspaceProps = { onLogout: () => void; };
type Module = 'secretaria' | 'diario' | 'monitoramento' | 'acesso' | 'familias' | 'portal-responsavel' | 'transporte' | 'admin';

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
      const canAccessControl = next.permissions.some((permission) => permission.startsWith('ACCESS_'));
      const canFamilyCommunication = next.permissions.some((permission) => permission.startsWith('FAMILY_COMMUNICATION_'));
      const canFamilyPortal = next.permissions.includes('STUDENT_LINKED_READ');
      const canTransport = next.permissions.some((permission) => permission.startsWith('TRANSPORT_'));
      const canAdmin = next.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ'].includes(permission));
      const valid = (current?: Module) => current && ((current === 'secretaria' && canSecretaria) || (current === 'diario' && canDiary) || (current === 'monitoramento' && canMonitoring) || (current === 'acesso' && canAccessControl) || (current === 'familias' && canFamilyCommunication) || (current === 'portal-responsavel' && canFamilyPortal) || (current === 'transporte' && canTransport) || (current === 'admin' && canAdmin));
      const preferred: Module | undefined = canFamilyPortal ? 'portal-responsavel' : next.permissions.includes('TRANSPORT_REQUEST_READ') ? 'transporte' : next.permissions.includes('DIARY_EDIT') ? 'diario' : next.permissions.includes('ACCESS_CONTROL_WRITE') ? 'acesso' : canSecretaria ? 'secretaria' : canDiary ? 'diario' : canMonitoring ? 'monitoramento' : canAccessControl ? 'acesso' : canFamilyCommunication ? 'familias' : canTransport ? 'transporte' : canAdmin ? 'admin' : undefined;
      setModule((current) => valid(current) ? current : preferred);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 401) { onLogout(); return; }
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar seu contexto de acesso.');
    } finally { setLoading(false); }
  }, [onLogout]);

  useEffect(() => { void loadContext(); }, [loadContext]);

  if (loading) return <main className="app-page"><StateMessage title="Carregando seu acesso" message="Aguarde enquanto as permissões são verificadas." /></main>;
  if (error) return <main className="app-page"><StateMessage kind="error" title="Não foi possível abrir o KRINO" message={error} /><Button type="button" onClick={() => void loadContext()}>Tentar novamente</Button></main>;
  if (!context || !module) return <main className="app-page"><StateMessage title="Nenhum módulo disponível" message="Sua conta está ativa, mas ainda não possui permissão para um módulo do sistema." /><Button type="button" variant="ghost" onClick={onLogout}><LogOut aria-hidden="true" size={18} />Sair</Button></main>;

  const canSecretaria = context.permissions.some((permission) => permission.startsWith('SCHOOL_'));
  const canDiary = context.permissions.some((permission) => permission.startsWith('DIARY_'));
  const canMonitoring = context.permissions.some((permission) => permission.startsWith('MONITORING_'));
  const canAccessControl = context.permissions.some((permission) => permission.startsWith('ACCESS_'));
  const canFamilyCommunication = context.permissions.some((permission) => permission.startsWith('FAMILY_COMMUNICATION_'));
  const canFamilyPortal = context.permissions.includes('STUDENT_LINKED_READ');
  const canTransport = context.permissions.some((permission) => permission.startsWith('TRANSPORT_'));
  const canAdmin = context.networkPermissions.some((permission) => ['USER_READ', 'ROLE_READ'].includes(permission));

  return <><nav className="workspace-nav" aria-label="Módulos do KRINO"><div className="workspace-nav__modules">
    {canSecretaria ? <button className={module === 'secretaria' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('secretaria')}><School aria-hidden="true" size={18} />Secretaria Escolar</button> : null}
    {canDiary ? <button className={module === 'diario' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('diario')}><BookOpen aria-hidden="true" size={18} />Diário de Classe</button> : null}
    {canMonitoring ? <button className={module === 'monitoramento' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('monitoramento')}><Activity aria-hidden="true" size={18} />Monitoramento</button> : null}
    {canAccessControl ? <button className={module === 'acesso' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('acesso')}><ScanLine aria-hidden="true" size={18} />Entrada e Saída</button> : null}
    {canFamilyCommunication ? <button className={module === 'familias' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('familias')}><MessagesSquare aria-hidden="true" size={18} />Comunicação com Famílias</button> : null}
    {canFamilyPortal ? <button className={module === 'portal-responsavel' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('portal-responsavel')}><UsersRound aria-hidden="true" size={18} />Portal do Responsável</button> : null}
    {canTransport ? <button className={module === 'transporte' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('transporte')}><Bus aria-hidden="true" size={18} />Transporte Universitário</button> : null}
    {canAdmin ? <button className={module === 'admin' ? 'workspace-nav__item workspace-nav__item--active' : 'workspace-nav__item'} type="button" onClick={() => setModule('admin')}><ShieldCheck aria-hidden="true" size={18} />Administração</button> : null}
  </div>{module !== 'admin' ? <Button type="button" variant="ghost" onClick={onLogout}><LogOut aria-hidden="true" size={18} />Sair</Button> : null}</nav>
    {module === 'secretaria' ? <SecretariaEscolarPage context={context} onUnauthorized={onLogout} /> : module === 'diario' ? <DiaryPage context={context} onUnauthorized={onLogout} /> : module === 'monitoramento' ? <MonitoringPage context={context} onUnauthorized={onLogout} /> : module === 'acesso' ? <AccessControlPage context={context} onUnauthorized={onLogout} /> : module === 'familias' ? <FamilyCommunicationPage context={context} onUnauthorized={onLogout} /> : module === 'portal-responsavel' ? <FamilyPortalPage context={context} onUnauthorized={onLogout} /> : module === 'transporte' ? <UniversityTransportPage context={context} onUnauthorized={onLogout} /> : <UsersAccessPage onLogout={onLogout} />}
  </>;
}
